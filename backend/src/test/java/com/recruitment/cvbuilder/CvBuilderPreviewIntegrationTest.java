package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.cvbuilder.dto.CvBuilderRequest;
import com.recruitment.resume.PdfBoxPoiTextExtractor;
import com.recruitment.resume.ResumeFileType;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MvcResult;

// FR-U12 T14 (R-B3) - B3 xem truoc: tra PDF, KHONG luu file, KHONG ghi DB, khong goi AI; van ban trich tu bytes B3 bang
// raw_text cua B1 cung form + mau; form loi -> cung ma/cau nhu B1.
class CvBuilderPreviewIntegrationTest extends CvBuilderIntegrationTestSupport {

    private final PdfBoxPoiTextExtractor extractor = new PdfBoxPoiTextExtractor();

    @ParameterizedTest
    @EnumSource(CvTemplate.class)
    void t14_returnsPdf_noStoreHeaders_noRowsNoFiles_noAiCall(CvTemplate template) throws Exception {
        Candidate c = registerCandidate("u12-t14");
        long resumes = countRows("resumes");
        long parsed = countRows("resume_parsed_data");
        long documents = countRows("cv_builder_documents");
        long files = countFiles("");

        MvcResult result = preview(c.token(), sampleRequest(template));

        assertStatus(result, 200);
        assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .isEqualTo("inline; filename=\"xem-truoc-cv.pdf\"");
        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(countRows("resumes")).isEqualTo(resumes);
        assertThat(countRows("resume_parsed_data")).isEqualTo(parsed);
        assertThat(countRows("cv_builder_documents")).isEqualTo(documents);
        assertThat(countFiles("")).isEqualTo(files);
        assertNoAiCall();
    }

    @ParameterizedTest
    @EnumSource(CvTemplate.class)
    void t14_previewTextEqualsRawTextOfCreatedCv(CvTemplate template) throws Exception {
        Candidate c = registerCandidate("u12-t14-text");
        CvBuilderRequest request = sampleRequest(template);

        byte[] pdf = preview(c.token(), request).getResponse().getContentAsByteArray();
        UUID resumeId = createCvOk(c.token(), request);

        String rawText = jdbcTemplate.queryForObject(
                "SELECT raw_text FROM resume_parsed_data WHERE resume_id = ?", String.class, resumeId);
        assertThat(extractor.extract(new ByteArrayInputStream(pdf), ResumeFileType.PDF)).isEqualTo(rawText);
    }

    @Test
    void t14_invalidForm_sameCodeAndMessageAsCreate() throws Exception {
        Candidate c = registerCandidate("u12-t14-bad");
        CvBuilderForm form = sampleForm();
        CvBuilderForm tooLongName = new CvBuilderForm(
                new CvBuilderForm.Contact("a ".repeat(50) + "b", null, null, null, null),
                form.currentTitle(), form.locationText(), form.industryCode(), form.education(), form.experience(),
                form.skills(), form.certifications(), form.projects());
        CvBuilderRequest request = new CvBuilderRequest(CvTemplate.CLASSIC, null, tooLongName);
        long files = countFiles("");

        MvcResult previewResult = preview(c.token(), request);
        MvcResult createResult = createCv(c.token(), request);

        assertError(previewResult, 400, "INVALID_CV_BUILDER_FORM", "Họ và tên tối đa 100 ký tự.");
        assertError(createResult, 400, "INVALID_CV_BUILDER_FORM", "Họ và tên tối đa 100 ký tự.");
        assertThat(countFiles("")).isEqualTo(files);
        assertNoAiCall();
    }

    @Test
    void t14_emptyForm_sameCodeAndMessageAsCreate() throws Exception {
        Candidate c = registerCandidate("u12-t14-empty");
        CvBuilderRequest request = new CvBuilderRequest(CvTemplate.MODERN, null,
                new CvBuilderForm(new CvBuilderForm.Contact("A", null, null, null, null), null, null, null,
                        null, null, null, null, null));
        String message =
                "CV cần có họ và tên và ít nhất một mục ở học vấn, kinh nghiệm, kỹ năng, chứng chỉ hoặc dự án.";

        assertError(preview(c.token(), request), 400, "CV_BUILDER_EMPTY", message);
        assertError(createCv(c.token(), request), 400, "CV_BUILDER_EMPTY", message);
    }
}
