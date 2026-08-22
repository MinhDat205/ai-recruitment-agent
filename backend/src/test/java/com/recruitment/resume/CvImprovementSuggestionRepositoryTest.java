package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// @Transactional o MUC METHOD - bat buoc de KHONG commit vinh vien vao DB Testcontainers dung chung
// cho toan bo 362 test cua du an (vd test thong ke F3 dem tong so hang se bi lech neu file nay commit
// that). generated_at la @Generated(event = EventType.INSERT, insertable = false) - Java KHONG set
// duoc qua entity setter, DB tu dien qua DEFAULT now(). Trong CUNG mot transaction, now() la hang so
// (CLAUDE.md muc 3c) nen ca 3 hang seed se trung generated_at tuyet doi neu khong can thiep them.
// Backdate TUONG MINH bang native update sau khi saveAndFlush - dung ky thuat CLAUDE.md muc 3c da
// chi ro la phuong an con lai khi khong the tach lop khoi @Transactional (tach lop se commit vinh
// vien, dung bi tu choi o vong duyet truoc). Seed van qua repository (saveAndFlush) - native update
// CHI chinh lai dung cot generated_at khong the set duoc qua Java, khong phai cach seed thay the.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CvImprovementSuggestionRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private CvImprovementSuggestionRepository cvImprovementSuggestionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID createResume() {
        User candidate = new User();
        candidate.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Ung Vien Test");
        candidate = userRepository.save(candidate);

        Resume resume = new Resume();
        resume.setCandidateId(candidate.getId());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        return resumeRepository.save(resume).getId();
    }

    private UUID createSuggestion(UUID resumeId, List<String> missingKeywords) {
        CvImprovementSuggestion suggestion = new CvImprovementSuggestion();
        suggestion.setResumeId(resumeId);
        suggestion.setMissingKeywords(missingKeywords);
        suggestion.setSectionSuggestions(List.of());
        suggestion.setLearningPath(List.of());
        suggestion.setModel("claude-sonnet-4-6");
        suggestion.setPromptVersion("cv-improvement-v1");
        return cvImprovementSuggestionRepository.saveAndFlush(suggestion).getId();
    }

    // generated_at khong set duoc qua entity (xem comment dau file) - backdate truc tiep bang native
    // update, nam trong CUNG transaction voi phan con lai cua test nen se tu rollback.
    private void backdateGeneratedAt(UUID suggestionId, Instant generatedAt) {
        entityManager
                .createNativeQuery("UPDATE cv_improvement_suggestions SET generated_at = :generatedAt WHERE id = :id")
                .setParameter("generatedAt", generatedAt)
                .setParameter("id", suggestionId)
                .executeUpdate();
    }

    // Chung minh V6 KHONG them UNIQUE(resume_id) - nhieu hang cung resume_id la hop le (SUA 2), va
    // findFirstByResumeIdOrderByGeneratedAtDescIdDesc phai chon DUNG ban co generated_at MOI NHAT
    // (khac test findByStatusOrderByRequestedAtAscIdAsc_returnsOldestFirst cua
    // CvImprovementRequestRepositoryTest - test do kiem nhanh tiebreak id KHI generated_at trung,
    // test nay kiem nhanh generated_at DESC khi ba gia tri THAT SU khac nhau).
    @Test
    @Transactional
    void findFirstByResumeIdOrderByGeneratedAtDescIdDesc_multipleRowsSameResume_returnsNewest() {
        UUID resumeId = createResume();
        UUID oldestId = createSuggestion(resumeId, List.of("Docker"));
        UUID middleId = createSuggestion(resumeId, List.of("Kubernetes"));
        UUID newestId = createSuggestion(resumeId, List.of("Terraform"));

        Instant base = Instant.now();
        backdateGeneratedAt(oldestId, base.minusSeconds(20));
        backdateGeneratedAt(middleId, base.minusSeconds(10));
        backdateGeneratedAt(newestId, base);
        entityManager.clear();

        CvImprovementSuggestion latest = cvImprovementSuggestionRepository
                .findFirstByResumeIdOrderByGeneratedAtDescIdDesc(resumeId)
                .orElseThrow();

        assertThat(latest.getId()).isEqualTo(newestId);
    }
}
