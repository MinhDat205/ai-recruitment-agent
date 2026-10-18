package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// FR-H16 T6 (E1-E4, sang loc) va T7 (tai CV goc) - trang ho so don phia HR (R-N2, R-N3, R-M4, R-V1..R-V4). @Transactional:
// moi @Test rollback rieng; khong so sanh thoi diem giua cac ban ghi.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlindReviewApplicationDetailIntegrationTest extends BlindReviewIntegrationTestSupport {

    private static final String CRITERION = "Kinh nghiem Java";
    private static final String COVER_LETTER = "Kính gửi anh chị, em là Quốc Huy, rất mong được phỏng vấn.";
    private static final String CV_EMAIL = "quochuy.cv@test.local";
    private static final String CV_PHONE = "0900 000 111";
    private static final String CV_ADDRESS = "Quận Hải Châu, Đà Nẵng";
    private static final String CV_LINKEDIN = "linkedin.com/in/quochuy-h16";

    private record Scenario(Hr hr, UUID jobId, App app) {
    }

    // Don cua Quoc Huy: thu gioi thieu, CV da trich xuat (contact day du; kinh nghiem chua dia chi; currentTitle/
    // locationText chua ho ten/dia chi), ket qua cham + giai thich chua danh tinh, ban chup sang loc ba loai cau.
    private Scenario scenario(boolean blindReview) throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, blindReview);
        Candidate huy = withContact(candidate("Quốc Huy", "0900000111"), "QUỐC HUY", CV_EMAIL, CV_PHONE, CV_ADDRESS,
                CV_LINKEDIN);
        App app = application(jobId, huy, COVER_LETTER);
        parsedData(app.resumeId(), "QUỐC HUY\nSenior Java", """
                {"contact": {"fullName": "QUỐC HUY", "email": "%s", "phone": "%s", "address": "%s", "linkedin": "%s"},
                 "education": [{"school": "Đại học Bách khoa", "degree": "Kỹ sư", "major": "CNTT",
                                "startDate": "2012", "endDate": "2017", "gpa": 3.2}],
                 "experience": [{"company": "Quốc Huy Studio", "title": "Senior Java", "startDate": "2019",
                                 "endDate": "Hiện tại", "description": "Làm việc tại %s, liên hệ %s"}],
                 "skills": ["Java", "Spring Boot"],
                 "certifications": [{"name": "OCP Java - Quốc Huy", "issuer": "Oracle", "issueDate": "2020"}],
                 "projects": [{"name": "Hệ thống của QUỐC HUY", "description": "Dự án cá nhân", "technologies": ["Java"]}],
                 "currentTitle": "Kỹ sư Java - Quốc Huy", "industryCode": "IT_SOFTWARE", "locationText": "%s"}
                """.formatted(CV_EMAIL, CV_PHONE, CV_ADDRESS, CV_LINKEDIN, CV_ADDRESS, CV_EMAIL, CV_ADDRESS));
        UUID run = doneRun(app.id(), new BigDecimal("8.00"), List.of(new Criterion(CRITERION, new BigDecimal("100"),
                new BigDecimal("8.00"), "QUỐC HUY có 5 năm Java tại " + CV_ADDRESS + ".",
                "Senior Java - liên hệ " + CV_EMAIL)));
        explanation(run, CRITERION, "Quốc Huy phù hợp.", "Liên hệ " + CV_LINKEDIN + " cho thấy dự án.", "quốc huy thiếu Kafka.");
        screeningCaptured(app.id());
        screeningText(app.id(), 1, "Giới thiệu ngắn về bạn", "Em là Quốc Huy, có thể đi làm ngay.");
        screeningChoice(app.id(), 2, "Khu vực làm việc", List.of(CV_ADDRESS, "Hà Nội"), CV_ADDRESS);
        screeningYesNo(app.id(), 3, "Sẵn sàng làm ca tối?", false);
        return new Scenario(hr, jobId, app);
    }

    private String path(Scenario s, String suffix) {
        return "/api/hr/applications/" + s.app().id() + suffix;
    }

    // ---- E1 ----

    @Test
    void detailOfMaskedApplicationHidesIdentity() throws Exception {
        Scenario s = scenario(true);

        MvcResult result = getAs(s.hr().token(), path(s, ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode e1 = body(result);
        assertThat(e1.get("candidateName").asString()).isEqualTo(CandidateAlias.of(s.app().id()));
        assertThat(e1.get("identityMasked").asBoolean()).isTrue();
        assertThat(e1.get("coverLetter").isNull()).isTrue();
        assertThat(e1.get("resumeFileName").isNull()).isTrue();
        assertThat(e1.get("resumeParseStatus").asString()).isEqualTo("DONE");
        assertNoLeak(text(result), s.app().candidate());
    }

    @Test
    void detailWhenJobDisabledIsUnchanged() throws Exception {
        Scenario s = scenario(false);

        JsonNode e1 = getJson(s.hr().token(), path(s, ""));

        assertThat(e1.get("candidateName").asString()).isEqualTo("Quốc Huy");
        assertThat(e1.get("identityMasked").asBoolean()).isFalse();
        assertThat(e1.get("coverLetter").asString()).isEqualTo(COVER_LETTER);
        assertThat(e1.get("resumeFileName").asString()).isEqualTo(s.app().resumeFileName());
    }

    @Test
    void detailAfterInvitationShowsIdentity() throws Exception {
        Scenario s = scenario(true);
        invite(s.app());

        JsonNode e1 = getJson(s.hr().token(), path(s, ""));

        assertThat(e1.get("candidateName").asString()).isEqualTo("Quốc Huy");
        assertThat(e1.get("identityMasked").asBoolean()).isFalse();
        assertThat(e1.get("coverLetter").asString()).isEqualTo(COVER_LETTER);
    }

    // ---- E2 ----

    @Test
    void parsedDataOfMaskedApplicationIsMasked() throws Exception {
        Scenario s = scenario(true);

        MvcResult result = getAs(s.hr().token(), path(s, "/resume/parsed"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode e2 = body(result);
        JsonNode data = e2.get("data");
        assertThat(data.get("contact").isNull()).isTrue();
        assertThat(data.get("experience").get(0).get("company").asString()).isEqualTo(HIDDEN + " Studio");
        assertThat(data.get("experience").get(0).get("description").asString())
                .isEqualTo("Làm việc tại " + HIDDEN + ", liên hệ " + HIDDEN);
        assertThat(data.get("experience").get(0).get("title").asString()).isEqualTo("Senior Java");
        assertThat(data.get("education").get(0).get("school").asString()).isEqualTo("Đại học Bách khoa");
        assertThat(data.get("certifications").get(0).get("name").asString()).isEqualTo("OCP Java - " + HIDDEN);
        assertThat(data.get("projects").get(0).get("name").asString()).isEqualTo("Hệ thống của " + HIDDEN);
        assertThat(data.get("skills").get(1).asString()).isEqualTo("Spring Boot");
        assertThat(data.get("currentTitle").asString()).isEqualTo("Kỹ sư Java - " + HIDDEN);
        assertThat(data.get("locationText").asString()).isEqualTo(HIDDEN);
        assertThat(data.get("industryCode").asString()).isEqualTo("IT_SOFTWARE");
        assertThat(e2.get("currentTitle").asString()).isEqualTo("Kỹ sư Java - " + HIDDEN);
        assertThat(e2.get("locationText").asString()).isEqualTo(HIDDEN);
        assertNoLeak(text(result), s.app().candidate());
    }

    @Test
    void parsedDataNonTextFieldsEqualDisabledJob() throws Exception {
        Scenario s = scenario(false);
        JsonNode off = getJson(s.hr().token(), path(s, "/resume/parsed"));
        setBlindReview(s.jobId(), true);
        JsonNode on = getJson(s.hr().token(), path(s, "/resume/parsed"));

        assertThat(off.get("data").get("contact").get("email").asString()).isEqualTo(CV_EMAIL);
        for (String field : List.of("resumeId", "parsedAt", "schemaVersion", "industry", "location", "experience")) {
            assertThat(on.get(field)).as(field).isEqualTo(off.get(field));
        }
        assertThat(on.get("industry").get("code").asString()).isEqualTo("IT_SOFTWARE");
        assertThat(on.get("experience").get("months").asInt()).isEqualTo(60);
    }

    @Test
    void candidateSeesOwnParsedDataVerbatim() throws Exception {
        Scenario s = scenario(true);

        JsonNode own = getJson(s.app().candidate().token(), "/api/candidates/resumes/" + s.app().resumeId() + "/parsed");

        assertThat(own.get("data").get("contact").get("fullName").asString()).isEqualTo("QUỐC HUY");
        assertThat(own.get("currentTitle").asString()).isEqualTo("Kỹ sư Java - Quốc Huy");
        assertThat(own.get("locationText").asString()).isEqualTo(CV_ADDRESS);
    }

    // ---- E3, E4 ----

    @Test
    void scoresAndExplanationOfMaskedApplicationAreMasked() throws Exception {
        Scenario s = scenario(true);

        MvcResult scores = getAs(s.hr().token(), path(s, "/scores"));
        MvcResult explanation = getAs(s.hr().token(), path(s, "/explanation"));

        JsonNode criterion = body(scores).get("criterionScores").get(0);
        assertThat(criterion.get("reasoning").asString()).isEqualTo(HIDDEN + " có 5 năm Java tại " + HIDDEN + ".");
        assertThat(criterion.get("evidence").get(0).get("quote").asString()).isEqualTo("Senior Java - liên hệ " + HIDDEN);
        assertThat(body(scores).get("totalScore").decimalValue()).isEqualByComparingTo("8.00");
        assertThat(body(scores).get("rank").asInt()).isEqualTo(1);
        JsonNode e4 = body(explanation).get("explanation");
        assertThat(e4.get("summary").asString()).isEqualTo(HIDDEN + " phù hợp.");
        assertThat(e4.get("strengths").get(0).get("point").asString()).isEqualTo("Liên hệ " + HIDDEN + " cho thấy dự án.");
        assertThat(e4.get("weaknesses").get(0).get("point").asString()).isEqualTo(HIDDEN + " thiếu Kafka.");
        assertNoLeak(text(scores), s.app().candidate());
        assertNoLeak(text(explanation), s.app().candidate());
    }

    @Test
    void scoresAndExplanationWhenJobDisabledAreVerbatim() throws Exception {
        Scenario s = scenario(false);

        JsonNode scores = getJson(s.hr().token(), path(s, "/scores"));
        JsonNode explanation = getJson(s.hr().token(), path(s, "/explanation"));

        assertThat(scores.get("criterionScores").get(0).get("reasoning").asString())
                .isEqualTo("QUỐC HUY có 5 năm Java tại " + CV_ADDRESS + ".");
        assertThat(explanation.get("explanation").get("summary").asString()).isEqualTo("Quốc Huy phù hợp.");
    }

    // ---- Sang loc ----

    @Test
    void screeningMasksShortTextOnly() throws Exception {
        Scenario s = scenario(true);

        MvcResult result = getAs(s.hr().token(), path(s, "/screening-answers"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode items = body(result).get("items");
        assertThat(items.get(0).get("questionType").asString()).isEqualTo("SHORT_TEXT");
        assertThat(items.get(0).get("answerText").asString()).isEqualTo("Em là " + HIDDEN + ", có thể đi làm ngay.");
        // SINGLE_CHOICE: phuong an do HR dat - giu nguyen ke ca khi trung gia tri nguon (H14 R-B3).
        assertThat(items.get(1).get("answerText").asString()).isEqualTo(CV_ADDRESS);
        assertThat(items.get(1).get("options").get(0).asString()).isEqualTo(CV_ADDRESS);
        assertThat(items.get(2).get("answerYesNo").asBoolean()).isFalse();
        assertThat(items.get(2).get("answerText").isNull()).isTrue();
    }

    @Test
    void screeningWhenJobDisabledIsVerbatim() throws Exception {
        Scenario s = scenario(false);

        JsonNode items = getJson(s.hr().token(), path(s, "/screening-answers")).get("items");

        assertThat(items.get(0).get("answerText").asString()).isEqualTo("Em là Quốc Huy, có thể đi làm ngay.");
    }

    // ---- T7: tai CV goc ----

    @Test
    void downloadOfMaskedApplicationIsConflict() throws Exception {
        Scenario s = scenario(true);

        MvcResult result = getAs(s.hr().token(), path(s, "/resume/download"));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).get("error").asString()).isEqualTo("IDENTITY_MASKED");
        assertThat(body(result).get("message").asString())
                .isEqualTo("Hồ sơ đang ở chế độ ẩn danh. Thao tác này mở lại khi gửi giấy mời phỏng vấn hoặc khi tắt chế"
                        + " độ ẩn danh của tin.");
        assertThat(result.getResponse().getHeader("Content-Disposition")).isNull();
        assertNoLeak(text(result), s.app().candidate());
    }

    @Test
    void downloadWhenJobDisabledSucceeds() throws Exception {
        Scenario s = scenario(false);

        MvcResult result = getAs(s.hr().token(), path(s, "/resume/download"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment");
    }

    @Test
    void downloadAfterInvitationSucceeds() throws Exception {
        Scenario s = scenario(true);
        invite(s.app());

        MvcResult result = getAs(s.hr().token(), path(s, "/resume/download"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void downloadOfOtherCompanyStaysForbiddenEvenWhenMasked() throws Exception {
        // Quyen so huu kiem TRUOC an danh: HR cong ty khac van nhan 403, khong biet don dang an danh.
        Scenario s = scenario(true);
        Hr other = hr();

        assertThat(getAs(other.token(), path(s, "/resume/download")).getResponse().getStatus()).isEqualTo(403);
    }
}
