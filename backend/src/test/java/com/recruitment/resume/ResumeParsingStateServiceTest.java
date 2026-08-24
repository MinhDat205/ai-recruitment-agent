package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.common.LlmRetryPolicy;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// KHONG @Transactional o class hay method nao trong file nay. Neu co, markDone() se THAM GIA
// (join) vao transaction cua chinh test thay vi mo transaction rieng cua no - UNIQUE violation luc
// do danh dau CA transaction bao trum la rollback-only, va test khong con quan sat duoc state SAU
// KHI transaction cua markDone() rollback xong (vi transaction cua test cung bi cuon theo).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ResumeParsingStateServiceTest {

    @Autowired
    private ResumeParsingStateService stateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private LlmRetryPolicy retryPolicy;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID createCandidate() {
        User user = new User();
        user.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(Role.CANDIDATE);
        user.setFullName("Ung Vien Test");
        return userRepository.save(user).getId();
    }

    private UUID createResume(ParseStatus status) {
        Resume resume = new Resume();
        resume.setCandidateId(createCandidate());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(status);
        return resumeRepository.save(resume).getId();
    }

    private ResumeParsedPayload samplePayload() {
        return new ResumeParsedPayload(
                new ResumeParsedPayload.Contact("Nguyen Van A", "a@example.com", null, null, null),
                List.of(),
                List.of(),
                List.of("Java"),
                List.of(),
                List.of());
    }

    @Test
    void claim_calledTwiceInARow_secondCallReturnsFalse() {
        UUID resumeId = createResume(ParseStatus.PENDING);

        boolean firstClaim = stateService.claim(resumeId);
        assertThat(firstClaim).isTrue();

        // Doc lai tu DB, khong tin object Java dang cam san trong tay - object do co the van phan
        // anh state cu trong persistence context du DB da doi.
        entityManager.clear();
        Resume afterFirstClaim = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(afterFirstClaim.getParseStatus()).isEqualTo(ParseStatus.PROCESSING);

        boolean secondClaim = stateService.claim(resumeId);
        assertThat(secondClaim).isFalse();
    }

    @Test
    void markDone_uniqueViolationOnSecondRow_rollsBackBothWrites() {
        UUID resumeId = createResume(ParseStatus.PROCESSING);
        // Insert san 1 hang resume_parsed_data cho resumeId nay - o ngoai bat ky @Transactional
        // nao cua test (test khong co transaction bao ngoai), nen saveAndFlush o day tu commit
        // ngay, dong vai tro "transaction rieng, da commit truoc" theo dung yeu cau.
        ResumeParsedData existing = new ResumeParsedData();
        existing.setResumeId(resumeId);
        existing.setRawText("da co du lieu roi");
        existing.setData(samplePayload());
        existing.setModel("claude-sonnet-4-6");
        existing.setPromptVersion("resume-parse-v1");
        resumeParsedDataRepository.saveAndFlush(existing);
        entityManager.clear();

        // markDone() se insert hang resume_parsed_data THU HAI cho cung resumeId -> vi pham
        // UNIQUE(resume_id) -> toan bo transaction cua markDone() rollback, KE CA cau
        // UPDATE resumes SET parse_status = DONE da chay truoc do trong cung transaction.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> stateService.markDone(
                        resumeId, "raw text moi", samplePayload(), "claude-sonnet-4-6", "resume-parse-v1", 100));

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        // Bang chung that cho viec @Transactional qua bean rieng hoat dong dung: parse_status VAN
        // LA PROCESSING, khong bi doi thanh DONE du cau UPDATE da chay truoc khi insert loi.
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.PROCESSING);
    }

    @Test
    void markFailed_setsStatusAndFormattedErrorCode() {
        UUID resumeId = createResume(ParseStatus.PROCESSING);

        stateService.markFailed(resumeId, ResumeParsingErrorCode.EXTRACT_EMPTY);

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(reloaded.getParseError()).isEqualTo(ResumeParsingErrorCode.EXTRACT_EMPTY.formatted());
    }

    // Dot 4e (chore/hardening) - loi LLM TAM THOI lan DAU (attempt_count 0 -> 1, con lan thu vi
    // max-attempts=3 trong application-test.yml): PHAI ve PENDING (khong phai FAILED) de scheduler
    // nhat lai, kem next_attempt_at trong TUONG LAI. Day la bang chung duong re chay duoc that (khac
    // voi chi map dung ma loi o Dot 4 nhip 1) - claimed_at PHAI reset ve NULL cho lan claim ke tiep.
    @Test
    void markTemporaryFailure_firstFailure_returnsToPendingWithAttemptCountOneAndFutureNextAttempt() {
        UUID resumeId = createResume(ParseStatus.PROCESSING);
        Instant before = Instant.now();

        stateService.markTemporaryFailure(resumeId, ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE);

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.PENDING);
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        assertThat(reloaded.getParseError()).isEqualTo(ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE.formatted());
        assertThat(reloaded.getClaimedAt()).isNull();
        assertThat(reloaded.getNextAttemptAt())
                .isAfter(before.plusMillis(retryPolicy.backoffMillisAfterAttempt(1) - 20));
    }

    // Bien duoi: lan thu THU HAI = max-attempts - 1 (2 trong 3) - VAN con mot lan thu nua, phai con
    // PENDING, KHONG duoc FAILED som.
    @Test
    void markTemporaryFailure_secondFailure_atMaxAttemptsMinusOne_stillPendingWithAttemptCountTwo() {
        UUID resumeId = createResume(ParseStatus.PROCESSING);
        stateService.markTemporaryFailure(resumeId, ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE);
        // Mo phong scheduler claim lai sau backoff (khong doi that, dat lai PROCESSING truc tiep -
        // khong di qua claimForProcessing() vi test nay khong kiem tra next_attempt_at, chi kiem
        // hanh vi markTemporaryFailure() o bien attempt thu hai).
        entityManager.clear();
        Resume afterFirst = resumeRepository.findById(resumeId).orElseThrow();
        afterFirst.setParseStatus(ParseStatus.PROCESSING);
        resumeRepository.saveAndFlush(afterFirst);
        entityManager.clear();

        stateService.markTemporaryFailure(resumeId, ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE);

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.PENDING);
        assertThat(reloaded.getAttemptCount()).isEqualTo(2);
        assertThat(reloaded.getNextAttemptAt()).isNotNull();
    }

    // Bien tren: lan thu THU BA = dung max-attempts (3) - HET luot thu, phai FAILED han voi
    // LLM_RETRY_EXHAUSTED (KHONG phai LLM_TEMPORARILY_UNAVAILABLE), next_attempt_at ve NULL.
    @Test
    void markTemporaryFailure_thirdFailure_atMaxAttempts_marksFailedWithRetryExhausted() {
        UUID resumeId = createResume(ParseStatus.PROCESSING);
        stateService.markTemporaryFailure(resumeId, ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE);
        entityManager.clear();
        resetToProcessing(resumeId);

        stateService.markTemporaryFailure(resumeId, ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE);
        entityManager.clear();
        resetToProcessing(resumeId);

        stateService.markTemporaryFailure(resumeId, ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE);

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(reloaded.getAttemptCount()).isEqualTo(3);
        assertThat(reloaded.getParseError()).isEqualTo(ResumeParsingErrorCode.LLM_RETRY_EXHAUSTED.formatted());
        assertThat(reloaded.getNextAttemptAt()).isNull();
    }

    private void resetToProcessing(UUID resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setParseStatus(ParseStatus.PROCESSING);
        resumeRepository.saveAndFlush(resume);
        entityManager.clear();
    }

    // Dot 4e - race: mot luong khac (vd reaper Dot 4h, hoac worker goc markDone) da doi ban ghi nay
    // (status khong con la PROCESSING) GIUA luc mot loi tam thoi khac xay ra va luc no goi
    // markTemporaryFailure. Mo phong bang cach doi status sang FAILED truc tiep truoc khi goi -
    // dieu kien "parse_status = 'PROCESSING'" trong UPDATE se khong khop, rowcount=0. PHAI tra ve
    // EM (khong nem exception), va KHONG duoc ghi de ban ghi da FAILED do luong kia.
    @Test
    void markTemporaryFailure_recordChangedByAnotherFlowBetweenReadAndWrite_noOpDoesNotOverwrite() {
        UUID resumeId = createResume(ParseStatus.PROCESSING);
        stateService.markFailed(resumeId, ResumeParsingErrorCode.EXTRACT_EMPTY);
        entityManager.clear();
        // resume gio da FAILED voi EXTRACT_EMPTY, attempt_count=0 - markTemporaryFailure() se doc
        // attempt_count=0 lam expectedCount, nhung dieu kien parse_status='PROCESSING' se khong con
        // khop (da la FAILED) -> rowcount 0.
        Resume beforeCall = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(beforeCall.getParseStatus()).isEqualTo(ParseStatus.FAILED);

        stateService.markTemporaryFailure(resumeId, ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE);

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(reloaded.getParseError()).isEqualTo(ResumeParsingErrorCode.EXTRACT_EMPTY.formatted());
        assertThat(reloaded.getAttemptCount()).isEqualTo(0);
    }

    // Dot 4f (chore/hardening) - claimForProcessing() gio them dieu kien next_attempt_at: mot ban
    // ghi PENDING nhung con dang cho backoff (moc trong TUONG LAI xa, +10 phut de tranh flaky do
    // timing) KHONG duoc claim, du dung parse_status.
    // @Transactional o muc method (khong phai lop) - claimForProcessing() la @Modifying, doi hoi
    // mot transaction dang mo de thuc thi executeUpdate() (tien le da co o ScoringRunRepositoryTest,
    // Dot 1: "claimForProcessing la @Modifying nen doi hoi mot transaction dang mo").
    @Test
    @Transactional
    void claimForProcessing_nextAttemptInFuture_doesNotClaimAndKeepsPending() {
        UUID resumeId = createResume(ParseStatus.PENDING);
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setNextAttemptAt(Instant.now().plusSeconds(600));
        resumeRepository.saveAndFlush(resume);
        entityManager.clear();

        int updated = resumeRepository.claimForProcessing(resumeId);

        assertThat(updated).isZero();
        entityManager.clear();
        assertThat(resumeRepository.findById(resumeId).orElseThrow().getParseStatus()).isEqualTo(ParseStatus.PENDING);
    }

    @Test
    @Transactional
    void claimForProcessing_nextAttemptInPast_claimsSuccessfully() {
        UUID resumeId = createResume(ParseStatus.PENDING);
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setNextAttemptAt(Instant.now().minusSeconds(60));
        resumeRepository.saveAndFlush(resume);
        entityManager.clear();

        int updated = resumeRepository.claimForProcessing(resumeId);

        assertThat(updated).isEqualTo(1);
        entityManager.clear();
        assertThat(resumeRepository.findById(resumeId).orElseThrow().getParseStatus())
                .isEqualTo(ParseStatus.PROCESSING);
    }

    // findReadyForProcessing la nguon quet cua ResumeParsingScheduler (Dot 4f) - phai loai ban ghi
    // con dang cho backoff ra khoi lo xu ly, neu khong no chiem cho cua cac ban ghi PENDING that su
    // san sang khi so luong PENDING vuot batchSize (dung loi Dot 2b da sua o ScoringRunRepository).
    @Test
    void findReadyForProcessing_pendingWithFutureNextAttempt_excludedFromBatch() {
        UUID readyId = createResume(ParseStatus.PENDING);
        UUID waitingId = createResume(ParseStatus.PENDING);
        Resume waiting = resumeRepository.findById(waitingId).orElseThrow();
        waiting.setNextAttemptAt(Instant.now().plusSeconds(600));
        resumeRepository.saveAndFlush(waiting);
        entityManager.clear();

        List<Resume> ready = resumeRepository.findReadyForProcessing(10);

        assertThat(ready).extracting(Resume::getId).contains(readyId).doesNotContain(waitingId);
    }

    // Dot 4h (chore/hardening) - danh sach nguon cua stale-claim reaper.
    @Test
    void findStaleClaimIds_processingClaimedBeforeThreshold_includesIt() {
        UUID resumeId = createResume(ParseStatus.PROCESSING);
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setClaimedAt(Instant.now().minusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        entityManager.clear();

        List<UUID> staleIds = stateService.findStaleClaimIds(Instant.now().minusSeconds(60));

        assertThat(staleIds).contains(resumeId);
    }

    @Test
    void findStaleClaimIds_claimedAfterThreshold_excluded() {
        UUID resumeId = createResume(ParseStatus.PROCESSING);
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setClaimedAt(Instant.now());
        resumeRepository.saveAndFlush(resume);
        entityManager.clear();

        List<UUID> staleIds = stateService.findStaleClaimIds(Instant.now().minusSeconds(600));

        assertThat(staleIds).doesNotContain(resumeId);
    }

    @Test
    void findStaleClaimIds_notProcessingStatus_excludedEvenIfClaimedAtOld() {
        UUID resumeId = createResume(ParseStatus.DONE);
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setClaimedAt(Instant.now().minusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        entityManager.clear();

        List<UUID> staleIds = stateService.findStaleClaimIds(Instant.now().minusSeconds(60));

        assertThat(staleIds).doesNotContain(resumeId);
    }

    // Dot 4h - kich ban "worker goc hoan thanh GIUA luc reaper doc va ghi": mo phong bang cach lay
    // id tu findStaleClaimIds() TRUOC (dai dien cho danh sach reaper da doc), roi cho worker goc
    // hoan thanh that su (markDone), CUOI CUNG moi goi markTemporaryFailure tren chinh id do (dai
    // dien buoc ghi cua reaper, dung id no da doc tu truoc, khong doc lai). parse_status luc nay la
    // DONE (khong con PROCESSING) nen dieu kien UPDATE khong khop - rowcount 0, KHONG duoc ghi de.
    @Test
    void markTemporaryFailure_calledWithStaleClaimIdAfterOriginalWorkerFinished_doesNotOverwriteDone() {
        UUID resumeId = createResume(ParseStatus.PROCESSING);
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setClaimedAt(Instant.now().minusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        entityManager.clear();
        List<UUID> staleIds = stateService.findStaleClaimIds(Instant.now().minusSeconds(60));
        assertThat(staleIds).contains(resumeId);

        stateService.markDone(resumeId, "raw text", samplePayload(), "claude-sonnet-4-6", "resume-parse-v1", 100);
        entityManager.clear();

        stateService.markTemporaryFailure(resumeId, ResumeParsingErrorCode.STALE_CLAIM_TIMEOUT);

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.DONE);
        assertThat(resumeParsedDataRepository.findByResumeId(resumeId)).isPresent();
    }

    // Muc 3b con sot (chore/hardening) - thu lai THU CONG. Dung het attempt_count/parse_error/
    // next_attempt_at truoc do (mo phong dung trang thai FAILED that su sau khi da het luot thu tu
    // dong o Dot 4) de xac nhan retry() reset SACH ca ba cot, khong chi doi parse_status.
    @Test
    void retry_failedResume_resetsToPendingAndClearsRetryState() {
        UUID resumeId = createResume(ParseStatus.FAILED);
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setAttemptCount(retryPolicy.maxAttempts());
        resume.setParseError(ResumeParsingErrorCode.LLM_RETRY_EXHAUSTED.formatted());
        resume.setNextAttemptAt(null);
        resume.setClaimedAt(Instant.now().minusSeconds(120));
        resumeRepository.saveAndFlush(resume);
        entityManager.clear();

        boolean retried = stateService.retry(resumeId);
        assertThat(retried).isTrue();

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.PENDING);
        assertThat(reloaded.getParseError()).isNull();
        assertThat(reloaded.getAttemptCount()).isZero();
        assertThat(reloaded.getNextAttemptAt()).isNull();
        assertThat(reloaded.getClaimedAt()).isNull();
    }

    // Ban ghi vua duoc reset ve PENDING/next_attempt_at NULL phai duoc scheduler nhat lai NGAY, khong
    // phai cho het mot chu ky backoff nao - xac nhan bang chinh truy van scheduler dung
    // (findReadyForProcessing), khong chi doc lai entity.
    @Test
    void retry_failedResume_becomesReadyForProcessingImmediately() {
        UUID resumeId = createResume(ParseStatus.FAILED);
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setAttemptCount(retryPolicy.maxAttempts());
        resume.setParseError(ResumeParsingErrorCode.LLM_RETRY_EXHAUSTED.formatted());
        resumeRepository.saveAndFlush(resume);
        entityManager.clear();

        assertThat(stateService.retry(resumeId)).isTrue();
        entityManager.clear();

        List<Resume> ready = resumeRepository.findReadyForProcessing(50);
        assertThat(ready).extracting(Resume::getId).contains(resumeId);
    }

    // FAILED la trang thai CUOI CUNG, chi co dung mot duong di vao no - dieu kien nguon cua
    // retryFailedResume KHONG can so khop them attempt_count nhu markTemporaryFailure. Kiem ca ba
    // trang thai con lai (PENDING/PROCESSING/DONE) deu la no-op an toan, khong ghi de gi.
    @Test
    void retry_pendingResume_returnsFalseAndDoesNotChangeRecord() {
        UUID resumeId = createResume(ParseStatus.PENDING);

        assertThat(stateService.retry(resumeId)).isFalse();

        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.PENDING);
    }

    @Test
    void retry_processingResume_returnsFalseAndDoesNotChangeRecord() {
        UUID resumeId = createResume(ParseStatus.PROCESSING);

        assertThat(stateService.retry(resumeId)).isFalse();

        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.PROCESSING);
    }

    @Test
    void retry_doneResume_returnsFalseAndDoesNotChangeRecord() {
        UUID resumeId = createResume(ParseStatus.DONE);

        assertThat(stateService.retry(resumeId)).isFalse();

        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.DONE);
    }
}
