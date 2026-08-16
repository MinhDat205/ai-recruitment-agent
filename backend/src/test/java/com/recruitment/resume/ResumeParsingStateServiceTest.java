package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

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
}
