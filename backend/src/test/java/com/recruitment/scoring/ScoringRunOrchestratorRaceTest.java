package com.recruitment.scoring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.ai.criterion.CriterionScorePayload;
import com.recruitment.ai.criterion.CriterionScoringErrorCode;
import com.recruitment.ai.criterion.CriterionScoringFailedException;
import com.recruitment.ai.criterion.CriterionScoringResult;
import com.recruitment.ai.criterion.CriterionScoringService;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Stubber;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

// Dot 4g (chore/hardening) - test THUAN Mockito (khac ScoringRunOrchestratorTest, @SpringBootTest
// that voi DB that): mo phong CHINH XAC lat cat vi pham uq_score_per_criterion GIUA vong lap
// doProcess() ma khong can dung that hai luong dong thoi (kich ban that su can stale-claim reaper -
// Dot 4h, chua lam trong nhip nay) - mock stateService.recordCriterionScore de nem
// DataIntegrityViolationException dung nhu trong that (PSQLException that co getConstraint(),
// khong doan chuoi message).
@ExtendWith(MockitoExtension.class)
class ScoringRunOrchestratorRaceTest {

    @Mock
    private ScoringRunStateService stateService;

    @Mock
    private ScoringRunRepository scoringRunRepository;

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Mock
    private CriterionScoringService criterionScoringService;

    @Mock
    private CriterionScoreRepository criterionScoreRepository;

    private ScoringRunOrchestrator newOrchestrator() {
        return new ScoringRunOrchestrator(
                stateService,
                scoringRunRepository,
                jobApplicationRepository,
                resumeParsedDataRepository,
                criterionScoringService,
                criterionScoreRepository);
    }

    private RubricSnapshot.CriterionSnapshot criterion(String name) {
        return new RubricSnapshot.CriterionSnapshot(UUID.randomUUID(), name, null, new BigDecimal("25"), 5, null);
    }

    private CriterionScoringResult resultFor(double score) {
        return new CriterionScoringResult(
                new CriterionScorePayload(
                        score,
                        "ly do gia lap",
                        List.of(new CriterionScorePayload.EvidenceQuote("trich dan", "experience"))),
                "claude-sonnet-4-6",
                100,
                "criterion-score-v1");
    }

    // doProcess() doc JobApplication roi resume_parsed_data.raw_text TRUOC vong lap tieu chi - phai
    // stub ca hai de tranh NullPointerException khong lien quan gi den kich ban dang test.
    private void stubRawTextLookup(UUID applicationId) {
        UUID resumeId = UUID.randomUUID();
        JobApplication application = new JobApplication();
        application.setId(applicationId);
        application.setResumeId(resumeId);
        ResumeParsedData parsedData = new ResumeParsedData();
        parsedData.setResumeId(resumeId);
        parsedData.setRawText("Nguyen Van A - kinh nghiem gia lap");
        when(jobApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(resumeParsedDataRepository.findByResumeId(resumeId)).thenReturn(Optional.of(parsedData));
    }

    private DataIntegrityViolationException uniqueViolation(String constraintName) {
        ServerErrorMessage serverErrorMessage = mock(ServerErrorMessage.class);
        when(serverErrorMessage.getConstraint()).thenReturn(constraintName);
        PSQLException psqlException = new PSQLException(serverErrorMessage, true);
        return new DataIntegrityViolationException("vi pham rang buoc", psqlException);
    }

    // Dot 4g - worker "thua" mot cuoc dua vo hai (kich ban that: worker zombie song sot qua
    // stale-timeout trong khi luot da bi reap va claim lai boi worker khac, Dot 4h). Vi pham DUNG
    // uq_score_per_criterion o tieu chi thu 2/4 - PHAI bo qua tieu chi do (KHONG markFailed), tiep
    // tuc cham tieu chi 3 va 4, cuoi cung van markFinished binh thuong.
    @Test
    void doProcess_uniqueScorePerCriterionViolationOnSecondCriterion_skipsItAndContinuesToFinish() {
        UUID scoringRunId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        ScoringRun run = new ScoringRun();
        run.setId(scoringRunId);
        run.setApplicationId(applicationId);
        run.setRubricSnapshot(new RubricSnapshot(
                "Rubric",
                List.of(criterion("Java"), criterion("Docker"), criterion("Tieng Anh"), criterion("Giao tiep"))));

        when(scoringRunRepository.findById(scoringRunId)).thenReturn(Optional.of(run));
        when(criterionScoreRepository.findByScoringRunId(scoringRunId)).thenReturn(List.of());
        when(stateService.claim(scoringRunId)).thenReturn(true);
        stubRawTextLookup(applicationId);
        when(criterionScoringService.score(any(), any())).thenReturn(resultFor(4.0));
        // recordCriterionScore: OK cho Java, VI PHAM cho Docker, OK cho Tieng Anh + Giao tiep.
        // QUAN TRONG: tinh san DataIntegrityViolationException truoc, KHONG goi uniqueViolation(...)
        // long ben trong chuoi doNothing()...doThrow() - mot when(...).thenReturn(...) LONG (o day
        // la when(serverErrorMessage.getConstraint())) xen giua mot chuoi stubbing CHUA hoan tat se
        // lam Mockito bao "UnfinishedStubbing", du code doc len co ve dung thu tu.
        DataIntegrityViolationException uniqueViolation = uniqueViolation("uq_score_per_criterion");
        Stubber stubber = doNothing().doThrow(uniqueViolation).doNothing().doNothing();
        stubber.when(stateService).recordCriterionScore(eq(scoringRunId), any(), any());

        newOrchestrator().processOne(scoringRunId);

        verify(criterionScoringService, times(4)).score(any(), any());
        verify(stateService, times(4)).recordCriterionScore(eq(scoringRunId), any(), any());
        verify(stateService, never()).markFailed(any(), any());
        verify(stateService, never()).markTemporaryFailure(any(), any());
        verify(stateService, times(1)).markFinished(scoringRunId);
    }

    // Doi chung: vi pham MOT rang buoc KHAC (khong phai uq_score_per_criterion) KHONG duoc nuot -
    // phai roi thang ra ngoai doProcess(), de luoi an toan cua processOne() bat va markFailed nhu cu
    // (UNEXPECTED_ERROR). Chung minh isUniqueViolation khop DUNG theo TEN rang buoc, khong phai
    // "bat moi DataIntegrityViolationException roi coi la an toan".
    @Test
    void doProcess_dataIntegrityViolationWithDifferentConstraint_propagatesAndOuterSafetyNetMarksFailed() {
        UUID scoringRunId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        ScoringRun run = new ScoringRun();
        run.setId(scoringRunId);
        run.setApplicationId(applicationId);
        run.setRubricSnapshot(new RubricSnapshot("Rubric", List.of(criterion("Java"), criterion("Docker"))));

        when(scoringRunRepository.findById(scoringRunId)).thenReturn(Optional.of(run));
        when(criterionScoreRepository.findByScoringRunId(scoringRunId)).thenReturn(List.of());
        when(stateService.claim(scoringRunId)).thenReturn(true);
        stubRawTextLookup(applicationId);
        when(criterionScoringService.score(any(), any())).thenReturn(resultFor(4.0));
        DataIntegrityViolationException otherViolation = uniqueViolation("uq_company_per_owner");
        doThrow(otherViolation).when(stateService).recordCriterionScore(eq(scoringRunId), any(), any());

        newOrchestrator().processOne(scoringRunId);

        // Chi mot lan goi score() (tieu chi Java) - vi pham xay ra ngay lan ghi dau tien, vong lap
        // DUNG lai (khong sang Docker), dung khac voi nhanh uq_score_per_criterion (tiep tuc vong lap).
        verify(criterionScoringService, times(1)).score(any(), any());
        verify(stateService, never()).markFinished(any());
        verify(stateService, times(1)).markFailed(scoringRunId, ScoringRunErrorCode.UNEXPECTED_ERROR);
    }

    // Dot 4e - loi CriterionScoringFailedException voi ma LLM_TEMPORARILY_UNAVAILABLE phai re sang
    // markTemporaryFailure, KHONG markFailed - bang chung duong re o D2 chay dung, doc lap voi D1.
    @Test
    void doProcess_temporaryLlmErrorFromCriterionScoring_routesToMarkTemporaryFailureNotMarkFailed() {
        UUID scoringRunId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        ScoringRun run = new ScoringRun();
        run.setId(scoringRunId);
        run.setApplicationId(applicationId);
        run.setRubricSnapshot(new RubricSnapshot("Rubric", List.of(criterion("Java"), criterion("Docker"))));

        when(scoringRunRepository.findById(scoringRunId)).thenReturn(Optional.of(run));
        when(criterionScoreRepository.findByScoringRunId(scoringRunId)).thenReturn(List.of());
        when(stateService.claim(scoringRunId)).thenReturn(true);
        stubRawTextLookup(applicationId);
        when(criterionScoringService.score(any(), any()))
                .thenThrow(new CriterionScoringFailedException(CriterionScoringErrorCode.LLM_TEMPORARILY_UNAVAILABLE));

        newOrchestrator().processOne(scoringRunId);

        verify(stateService, times(1))
                .markTemporaryFailure(scoringRunId, CriterionScoringErrorCode.LLM_TEMPORARILY_UNAVAILABLE);
        verify(stateService, never()).markFailed(any(), any());
        verify(stateService, never()).markFinished(any());
    }
}
