package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.cvbuilder.dto.CvBuilderRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-U12 T13 (R-B2) - B2 doc lai form da luu: CV dung tu form cua minh -> 200; CV tai len -> 409; CV nguoi khac / id
// khong ton tai -> 404 cung ma; HR -> 403; khong token -> 401.
class CvBuilderReadIntegrationTest extends CvBuilderIntegrationTestSupport {

    private static final String NOT_FROM_BUILDER_MESSAGE =
            "CV này được tải lên từ file nên không sửa được bằng công cụ tạo CV.";

    @Test
    void t13_ownBuiltCv_returnsStoredFormTemplateVersionAndCreatedAt() throws Exception {
        Candidate c = registerCandidate("u12-t13");
        UUID resumeId = createCvOk(c.token(), new CvBuilderRequest(CvTemplate.MODERN, "Bản đọc lại", sampleForm()));

        MvcResult result = getDocument(c.token(), resumeId);

        assertStatus(result, 200);
        JsonNode root = body(result);
        assertThat(root.get("resumeId").asString()).isEqualTo(resumeId.toString());
        assertThat(root.get("template").asString()).isEqualTo("MODERN");
        assertThat(root.get("versionLabel").asString()).isEqualTo("Bản đọc lại");
        String storedForm = jdbcTemplate.queryForObject(
                "SELECT form_data::text FROM cv_builder_documents WHERE resume_id = ?", String.class, resumeId);
        assertThat(JSON.treeToValue(root.get("form"), CvBuilderForm.class))
                .isEqualTo(JSON.readValue(storedForm, CvBuilderForm.class))
                .isEqualTo(sampleForm());
        Timestamp createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM cv_builder_documents WHERE resume_id = ?", Timestamp.class, resumeId);
        assertThat(Instant.parse(root.get("createdAt").asString())).isEqualTo(createdAt.toInstant());
        assertNoAiCall();
    }

    @Test
    void t13_ownUploadedCv_409NotFromBuilder() throws Exception {
        Candidate c = registerCandidate("u12-t13-up");
        UUID uploaded = uploadResume(c.token());

        assertError(getDocument(c.token(), uploaded), 409, "CV_NOT_FROM_BUILDER", NOT_FROM_BUILDER_MESSAGE);
    }

    @Test
    void t13_otherCandidatesCv_andMissingId_404SameCode() throws Exception {
        Candidate owner = registerCandidate("u12-t13-owner");
        Candidate other = registerCandidate("u12-t13-other");
        UUID resumeId = createCvOk(owner.token(), sampleRequest(CvTemplate.CLASSIC));

        MvcResult foreign = getDocument(other.token(), resumeId);
        MvcResult missing = getDocument(other.token(), UUID.randomUUID());

        assertStatus(foreign, 404);
        assertStatus(missing, 404);
        assertThat(body(foreign).get("error").asString()).isEqualTo("RESUME_NOT_FOUND");
        assertThat(body(missing).get("error").asString()).isEqualTo("RESUME_NOT_FOUND");
    }

    @Test
    void t13_hrToken_403_noToken_401() throws Exception {
        Candidate c = registerCandidate("u12-t13-rbac");
        UUID resumeId = createCvOk(c.token(), sampleRequest(CvTemplate.CLASSIC));
        String hrToken = register("hr", uniqueEmail("u12-t13-hr"));

        assertStatus(getDocument(hrToken, resumeId), 403);
        assertStatus(getDocument(null, resumeId), 401);
    }
}
