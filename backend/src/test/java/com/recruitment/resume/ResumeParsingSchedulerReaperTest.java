package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Dot 4h (chore/hardening) - test wiring DAY DU cua ResumeParsingScheduler.reapStaleClaims(), khac
// ResumeParsingStateServiceTest (chi kiem findStaleClaimIds()/markTemporaryFailure() rieng le). Bean
// ResumeParsingScheduler KHONG ton tai trong context test (tat qua app.resume-parsing.enabled=false,
// xem application-test.yml) - "new" mot instance rieng bang tay voi cac dependency THAT da autowired
// tu context, roi goi thang reapStaleClaims() - khong cho @Scheduled tick that, dung tinh than
// ScoringRunOrchestratorRaceTest (mock) nhung o day dung DB that vi can kiem hanh vi ghi that.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ResumeParsingSchedulerReaperTest {

    private static final long STALE_TIMEOUT_MS = 900_000; // 15 phut, giong mac dinh production

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsingOrchestrator orchestrator;

    @Autowired
    private ResumeParsingStateService stateService;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private ResumeParsingScheduler newScheduler() {
        return new ResumeParsingScheduler(resumeRepository, orchestrator, stateService, 10, STALE_TIMEOUT_MS);
    }

    private UUID createCandidate() {
        User user = new User();
        user.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(Role.CANDIDATE);
        user.setFullName("Ung Vien Test");
        return userRepository.save(user).getId();
    }

    private UUID createResume(ParseStatus status, Instant claimedAt) {
        Resume resume = new Resume();
        resume.setCandidateId(createCandidate());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(status);
        resume.setClaimedAt(claimedAt);
        return resumeRepository.save(resume).getId();
    }

    // Ban ghi claim qua lau (2 gio truoc, vuot xa 15 phut) - reapStaleClaims() phai dua ve PENDING
    // (con luot thu, max-attempts=3), KHONG FAILED thang, kem attempt_count=1 va ma loi
    // STALE_CLAIM_TIMEOUT rieng (khong lan voi LLM_TEMPORARILY_UNAVAILABLE).
    @Test
    void reapStaleClaims_processingClaimedTwoHoursAgo_returnsToPendingWithStaleClaimTimeout() {
        UUID resumeId = createResume(ParseStatus.PROCESSING, Instant.now().minusSeconds(7200));

        newScheduler().reapStaleClaims();

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.PENDING);
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        assertThat(reloaded.getParseError()).isEqualTo(ResumeParsingErrorCode.STALE_CLAIM_TIMEOUT.formatted());
        assertThat(reloaded.getClaimedAt()).isNull();
        assertThat(reloaded.getNextAttemptAt()).isNotNull();
    }

    // Ban ghi vua claim (claimed_at = now(), duoi nguong 15 phut) - reapStaleClaims() KHONG duoc
    // dong toi, du dang PROCESSING.
    @Test
    void reapStaleClaims_recentlyClaimedProcessing_isNotTouched() {
        UUID resumeId = createResume(ParseStatus.PROCESSING, Instant.now());

        newScheduler().reapStaleClaims();

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.PROCESSING);
        assertThat(reloaded.getAttemptCount()).isZero();
        assertThat(reloaded.getParseError()).isNull();
    }

    // Ban ghi khong o PROCESSING (vd DONE) du claimed_at cu - khong nam trong dieu kien quet, khong
    // bi dong toi.
    @Test
    void reapStaleClaims_doneResumeWithOldClaimedAt_isNotTouched() {
        UUID resumeId = createResume(ParseStatus.DONE, Instant.now().minusSeconds(7200));

        newScheduler().reapStaleClaims();

        entityManager.clear();
        Resume reloaded = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(reloaded.getParseStatus()).isEqualTo(ParseStatus.DONE);
    }
}
