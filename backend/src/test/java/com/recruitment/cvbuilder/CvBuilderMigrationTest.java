package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// FR-U12 T2 (V18, R-D1, R-D2). SQL thang qua JdbcTemplate de kiem rang buoc o DB (CHECK, UNIQUE, NOT NULL, DEFAULT); mot ca
// doc qua entity de kiem anh xa JSONB -> CvBuilderForm. Moi ca tu tao ung vien + CV rieng (UUID ngau nhien). Khong
// @Transactional: moi cau tu commit, loi rang buoc khong lam hong ca test khac.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CvBuilderMigrationTest {

    private static final String FORM_JSON = "{\"contact\":{\"fullName\":\"Nguyễn Văn A\"},\"skills\":[\"Java\"]}";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CvBuilderDocumentRepository documentRepository;

    private UUID createResume() {
        UUID candidateId = UUID.randomUUID();
        UUID resumeId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, role, full_name) VALUES (?, ?, 'x', 'CANDIDATE', 'UV V18')",
                candidateId,
                "uv-v18-" + candidateId + "@test.local");
        jdbcTemplate.update(
                "INSERT INTO resumes (id, candidate_id, file_url, file_name, file_type, parse_status) "
                        + "VALUES (?, ?, ?, 'cv.pdf', 'PDF', 'DONE')",
                resumeId,
                candidateId,
                "resumes/" + resumeId + ".pdf");
        return resumeId;
    }

    private int insertDocument(UUID resumeId, String template, String formJson) {
        return jdbcTemplate.update(
                "INSERT INTO cv_builder_documents (resume_id, template, form_data) VALUES (?, ?, CAST(? AS jsonb))",
                resumeId,
                template,
                formJson);
    }

    @Test
    void validInsert_bothTemplates_createdAtAndIdGenerated() {
        UUID classic = createResume();
        UUID modern = createResume();

        assertThat(insertDocument(classic, "CLASSIC", FORM_JSON)).isEqualTo(1);
        assertThat(insertDocument(modern, "MODERN", FORM_JSON)).isEqualTo(1);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, template, form_data ->> 'skills' AS skills, created_at FROM cv_builder_documents "
                        + "WHERE resume_id = ?",
                classic);
        assertThat(row.get("id")).isNotNull();
        assertThat(row.get("template")).isEqualTo("CLASSIC");
        assertThat(row.get("skills")).isEqualTo("[\"Java\"]");
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void unknownTemplate_violatesCheck() {
        UUID resumeId = createResume();

        assertThatThrownBy(() -> insertDocument(resumeId, "OTHER", FORM_JSON))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDocument(resumeId, "classic", FORM_JSON))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondRowForSameResume_violatesUnique() {
        UUID resumeId = createResume();
        insertDocument(resumeId, "CLASSIC", FORM_JSON);

        assertThatThrownBy(() -> insertDocument(resumeId, "MODERN", FORM_JSON))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM cv_builder_documents WHERE resume_id = ?", Long.class, resumeId))
                .isEqualTo(1L);
    }

    @Test
    void nullColumns_violateNotNull() {
        UUID resumeId = createResume();

        assertThatThrownBy(() -> insertDocument(resumeId, "CLASSIC", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDocument(resumeId, null, FORM_JSON))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDocument(null, "CLASSIC", FORM_JSON))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownResume_violatesForeignKey() {
        assertThatThrownBy(() -> insertDocument(UUID.randomUUID(), "CLASSIC", FORM_JSON))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Khong cot ten cam CLAUDE.md muc 7, khong updated_at/deleted_at (R-D1).
    @Test
    void columns_exactlyAsSpec() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'cv_builder_documents' "
                        + "ORDER BY ordinal_position",
                String.class);

        assertThat(columns).containsExactly("id", "resume_id", "template", "form_data", "created_at");
    }

    // R-D2 - doc qua entity: form_data JSONB -> CvBuilderForm (list vang mat -> List.of()), created_at do DB sinh.
    @Test
    void entity_readsJsonbIntoForm() {
        UUID resumeId = createResume();
        insertDocument(resumeId, "MODERN", FORM_JSON);

        CvBuilderDocument document = documentRepository.findByResumeId(resumeId).orElseThrow();

        assertThat(document.getTemplate()).isEqualTo(CvTemplate.MODERN);
        assertThat(document.getFormData().contact())
                .isEqualTo(new CvBuilderForm.Contact("Nguyễn Văn A", null, null, null, null));
        assertThat(document.getFormData().skills()).containsExactly("Java");
        assertThat(document.getFormData().education()).isEmpty();
        assertThat(document.getFormData().projects()).isEmpty();
        assertThat(document.getCreatedAt()).isNotNull();
        OffsetDateTime dbCreatedAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM cv_builder_documents WHERE resume_id = ?", OffsetDateTime.class, resumeId);
        assertThat(document.getCreatedAt()).isEqualTo(dbCreatedAt.toInstant());
    }
}
