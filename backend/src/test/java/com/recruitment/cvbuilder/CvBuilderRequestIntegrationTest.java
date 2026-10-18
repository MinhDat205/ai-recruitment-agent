package com.recruitment.cvbuilder;

import com.recruitment.cvbuilder.dto.CvBuilderRequest;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// FR-U12 T6b (R-W1) - than request qua HTTP, ca B1 va B3: template null -> INVALID_CV_BUILDER_FORM; template la, JSON
// hong -> INVALID_CV_BUILDER_REQUEST (CvBuilderExceptionAdvice); form null -> CV_BUILDER_EMPTY.
class CvBuilderRequestIntegrationTest extends CvBuilderIntegrationTestSupport {

    private static final String UNREADABLE_MESSAGE = "Dữ liệu CV gửi lên không hợp lệ.";

    static List<String> paths() {
        return List.of(RESUMES_PATH, PREVIEW_PATH);
    }

    @ParameterizedTest
    @MethodSource("paths")
    void t6b_templateNull_400TemplateRequired(String path) throws Exception {
        Candidate c = registerCandidate("u12-t6b-null");

        assertError(postJson(path, c.token(), json(new CvBuilderRequest(null, null, sampleForm()))),
                400, "INVALID_CV_BUILDER_FORM", "Vui lòng chọn mẫu trình bày.");
        assertNoAiCall();
    }

    @ParameterizedTest
    @MethodSource("paths")
    void t6b_unknownTemplate_400InvalidRequest(String path) throws Exception {
        Candidate c = registerCandidate("u12-t6b-xyz");
        String body = json(sampleRequest(CvTemplate.CLASSIC)).replace("\"CLASSIC\"", "\"XYZ\"");

        assertError(postJson(path, c.token(), body), 400, "INVALID_CV_BUILDER_REQUEST", UNREADABLE_MESSAGE);
    }

    @ParameterizedTest
    @MethodSource("paths")
    void t6b_brokenJson_400InvalidRequest(String path) throws Exception {
        Candidate c = registerCandidate("u12-t6b-json");

        assertError(postJson(path, c.token(), "{\"template\":"), 400, "INVALID_CV_BUILDER_REQUEST", UNREADABLE_MESSAGE);
    }

    @ParameterizedTest
    @MethodSource("paths")
    void t6b_formNull_400Empty(String path) throws Exception {
        Candidate c = registerCandidate("u12-t6b-form");

        assertError(postJson(path, c.token(), json(new CvBuilderRequest(CvTemplate.MODERN, "Bản 1", null))),
                400, "CV_BUILDER_EMPTY",
                "CV cần có họ và tên và ít nhất một mục ở học vấn, kinh nghiệm, kỹ năng, chứng chỉ hoặc dự án.");
    }

    // Doi chung: cung duong dan, body hop le -> khong phai 400.
    @ParameterizedTest
    @MethodSource("paths")
    void t6b_validBody_notRejected(String path) throws Exception {
        Candidate c = registerCandidate("u12-t6b-ok");

        assertStatus(postJson(path, c.token(), json(sampleRequest(CvTemplate.CLASSIC))),
                path.equals(RESUMES_PATH) ? 201 : 200);
    }
}
