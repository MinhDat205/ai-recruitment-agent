package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// FR-U12 T26 (R-W2, R-W4 - L11): ResumeParsingStateService.insertBuiltResume + CvBuilderStateService.create, goi thang
// bean (khong HTTP). KHONG @Transactional o lop/method (khuon ResumeParsingStateServiceTest): create() phai mo va commit/
// rollback transaction CUA NO de test quan sat duoc trang thai sau rollback. CvBuilderDocumentRepository la
// @MockitoSpyBean (goi that, tru ca rollback) -> lop nay co Spring context rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CvBuilderStateServiceTest {

    @Autowired
    private CvBuilderStateService stateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @MockitoSpyBean
    private CvBuilderDocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetSpy() {
        reset(documentRepository);
    }

    private UUID createCandidate() {
        User user = new User();
        user.setEmail("u12-state-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(Role.CANDIDATE);
        user.setFullName("Ung Vien U12");
        return userRepository.save(user).getId();
    }

    private static CvBuilderForm sampleForm() {
        return new CvBuilderForm(
                new CvBuilderForm.Contact("Nguyễn Văn A", "a@example.com", null, null, null),
                "Backend Developer",
                "Hà Nội",
                "IT_SOFTWARE",
                List.of(),
                List.of(
                        new CvBuilderForm.Experience("Cty A", "Dev", "01/2020", "06/2020", null, "Viết API"),
                        new CvBuilderForm.Experience("Cty B", "Dev", "2019", null, null, null)),
                List.of("Java"),
                List.of(),
                List.of());
    }

    private UUID create(UUID candidateId, String rawText) {
        return stateService.create(
                candidateId,
                CvTemplate.MODERN,
                sampleForm(),
                "Bản 1",
                "resumes/" + UUID.randomUUID() + ".pdf",
                "CV Nguyễn Văn A.pdf",
                12345L,
                rawText);
    }

    private long countFor(String table, String column, UUID id) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Long.class, id);
    }

    @Test
    void create_writesDoneResumeParsedDataAndDocument() {
        UUID candidateId = createCandidate();

        UUID resumeId = create(candidateId, "Nguyễn Văn A\r\nBackend Developer");

        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(resume.getCandidateId()).isEqualTo(candidateId);
        assertThat(resume.getParseStatus()).isEqualTo(ParseStatus.DONE);
        assertThat(resume.getFileType()).isEqualTo(ResumeFileType.PDF);
        assertThat(resume.getFileSize()).isEqualTo(12345L);
        assertThat(resume.getFileName()).isEqualTo("CV Nguyễn Văn A.pdf");
        assertThat(resume.getVersionLabel()).isEqualTo("Bản 1");
        assertThat(resume.getFileUrl()).startsWith("resumes/").endsWith(".pdf");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT parse_error, claimed_at, next_attempt_at, attempt_count FROM resumes WHERE id = ?", resumeId);
        assertThat(row.get("parse_error")).isNull();
        assertThat(row.get("claimed_at")).isNull();
        assertThat(row.get("next_attempt_at")).isNull();
        assertThat(((Number) row.get("attempt_count")).intValue()).isZero();

        ResumeParsedData parsed = resumeParsedDataRepository.findByResumeId(resumeId).orElseThrow();
        assertThat(parsed.getRawText()).isEqualTo("Nguyễn Văn A\r\nBackend Developer");
        assertThat(parsed.getModel()).isEqualTo("cv-builder");
        assertThat(parsed.getPromptVersion()).isEqualTo("cv-builder-v1");
        assertThat(parsed.getTokenUsage()).isNull();
        ResumeParsedPayload data = parsed.getData();
        assertThat(data.contact().fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(data.currentTitle()).isEqualTo("Backend Developer");
        assertThat(data.locationText()).isEqualTo("Hà Nội");
        assertThat(data.industryCode()).isEqualTo("IT_SOFTWARE");
        assertThat(data.skills()).containsExactly("Java");
        assertThat(data.experience()).extracting(ResumeParsedPayload.Experience::company).containsExactly("Cty A", "Cty B");
        // Sau enricher (R-M3): cot truy van + kinh nghiem da tinh cung lan ghi.
        assertThat(parsed.getIndustryCode()).isEqualTo("IT_SOFTWARE");
        assertThat(parsed.getRegionCode()).isEqualTo("HA_NOI");
        assertThat(parsed.getExperienceMonths()).isEqualTo(6);
        assertThat(parsed.getExperienceEntriesCounted()).isEqualTo(1);
        assertThat(parsed.getExperienceEntriesSkipped()).isEqualTo(1);
        assertThat(parsed.getExperienceComputedAt()).isNotNull();

        CvBuilderDocument document = documentRepository.findByResumeId(resumeId).orElseThrow();
        assertThat(document.getTemplate()).isEqualTo(CvTemplate.MODERN);
        assertThat(document.getFormData()).isEqualTo(sampleForm());
        assertThat(document.getCreatedAt()).isNotNull();
    }

    // R-W4: chi CV dau tien cua ung vien tu thanh CV chinh.
    @Test
    void create_firstResumeIsPrimary_secondIsNot() {
        UUID candidateId = createCandidate();

        UUID first = create(candidateId, "raw 1");
        UUID second = create(candidateId, "raw 2");

        assertThat(resumeRepository.findById(first).orElseThrow().isPrimary()).isTrue();
        assertThat(resumeRepository.findById(second).orElseThrow().isPrimary()).isFalse();
        assertThat(resumeRepository.findById(first).orElseThrow().isPrimary()).as("CV cu giu co").isTrue();
    }

    // Ung vien da co CV (tai len) -> CV dung tu form khong phai CV chinh.
    @Test
    void create_candidateWithUploadedResume_notPrimary() {
        UUID candidateId = createCandidate();
        Resume uploaded = new Resume();
        uploaded.setCandidateId(candidateId);
        uploaded.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        uploaded.setFileName("cv.pdf");
        uploaded.setFileType(ResumeFileType.PDF);
        uploaded.setPrimary(true);
        uploaded.setParseStatus(ParseStatus.PENDING);
        resumeRepository.saveAndFlush(uploaded);

        UUID built = create(candidateId, "raw");

        assertThat(resumeRepository.findById(built).orElseThrow().isPrimary()).isFalse();
    }

    // R-W2: saveAndFlush cua cv_builder_documents nem -> rollback ca resumes va resume_parsed_data.
    @Test
    void create_documentSaveFails_noRowsLeft() {
        UUID candidateId = createCandidate();
        doThrow(new DataIntegrityViolationException("gia lap loi ghi cv_builder_documents"))
                .when(documentRepository)
                .saveAndFlush(any());

        assertThatThrownBy(() -> create(candidateId, "raw")).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countFor("resumes", "candidate_id", candidateId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM resume_parsed_data d JOIN resumes r ON r.id = d.resume_id "
                                + "WHERE r.candidate_id = ?",
                        Long.class,
                        candidateId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM cv_builder_documents d JOIN resumes r ON r.id = d.resume_id "
                                + "WHERE r.candidate_id = ?",
                        Long.class,
                        candidateId))
                .isZero();
        // Lan sau (khong loi) van la CV dau tien -> CV chinh: lan loi khong de lai dau vet.
        reset(documentRepository);
        UUID resumeId = create(candidateId, "raw");
        assertThat(resumeRepository.findById(resumeId).orElseThrow().isPrimary()).isTrue();
    }
}
