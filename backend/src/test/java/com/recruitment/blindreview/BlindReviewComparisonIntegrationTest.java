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

// FR-H16 T9 - bang so sanh FR-H14 (H14 R-B1: diem che duy nhat la ApplicationComparisonService.compare). Hai don an danh +
// mot don da moi cung Job. @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlindReviewComparisonIntegrationTest extends BlindReviewIntegrationTestSupport {

    private static final String CRITERION = "Kinh nghiem Java";
    private static final String CHOICE = "Quận Hải Châu, Đà Nẵng";

    private record Scenario(Hr hr, UUID jobId, App huy, App lan, App ngoc) {
    }

    private App scoredApplication(UUID jobId, String fullName, String phone, String totalScore) throws Exception {
        Candidate candidate = withContact(candidate(fullName, phone), fullName.toUpperCase(), null, null, CHOICE, null);
        App app = application(jobId, candidate, null);
        parsedData(app.resumeId(), fullName, """
                {"contact": {"fullName": "%s", "address": "%s"},
                 "education": [{"school": "Đại học của %s", "degree": "Kỹ sư", "major": "CNTT"}],
                 "experience": [], "skills": ["Java", "Mentor cho %s"], "certifications": [], "projects": []}
                """.formatted(fullName.toUpperCase(), CHOICE, fullName, fullName));
        UUID run = doneRun(app.id(), new BigDecimal(totalScore), List.of(new Criterion(CRITERION, new BigDecimal("100"),
                new BigDecimal("5.00"), fullName + " có kinh nghiệm Java.", fullName.toUpperCase() + " - Java")));
        explanation(run, CRITERION, fullName + " phù hợp.", fullName + " mạnh Java.", "Thiếu Kafka.");
        screeningCaptured(app.id());
        screeningText(app.id(), 1, "Giới thiệu", "Em là " + fullName + ".");
        screeningChoice(app.id(), 2, "Khu vực", List.of(CHOICE, "Hà Nội"), CHOICE);
        screeningYesNo(app.id(), 3, "Làm ca tối?", true);
        return app;
    }

    private Scenario scenario(boolean blindReview) throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, blindReview);
        App huy = scoredApplication(jobId, "Quốc Huy", "0900000111", "80.00");
        App lan = scoredApplication(jobId, "Lê Thị Lan", "0900000333", "70.00");
        App ngoc = scoredApplication(jobId, "Trần Bảo Ngọc", "0900000222", "60.00");
        invite(ngoc);
        return new Scenario(hr, jobId, huy, lan, ngoc);
    }

    private MvcResult compare(Scenario s) throws Exception {
        return getAs(s.hr().token(), "/api/hr/jobs/" + s.jobId() + "/applications/compare?ids="
                + s.huy().id() + "," + s.lan().id() + "," + s.ngoc().id());
    }

    // Cot i cua mot hang sang loc theo loai cau (moi don co hang rieng vi question_id NULL - H14 R-Q2).
    private static JsonNode screeningCell(JsonNode response, int column, String questionType) {
        for (JsonNode row : response.get("screening").get("rows")) {
            JsonNode cell = row.get("cells").get(column);
            if (row.get("questionType").asString().equals(questionType) && cell.get("state").asString().equals("ANSWERED")) {
                return cell;
            }
        }
        throw new AssertionError("Khong co o " + questionType + " cot " + column);
    }

    @Test
    void columnsAreMaskedPerApplication() throws Exception {
        Scenario s = scenario(true);

        MvcResult result = compare(s);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        JsonNode columns = json.get("columns");
        assertThat(columns.get(0).get("candidateName").asString()).isEqualTo(CandidateAlias.of(s.huy().id()));
        assertThat(columns.get(1).get("candidateName").asString()).isEqualTo(CandidateAlias.of(s.lan().id()));
        assertThat(columns.get(2).get("candidateName").asString()).isEqualTo("Trần Bảo Ngọc");

        JsonNode cells = json.get("criteria").get(0).get("cells");
        assertThat(cells.get(0).get("reasoning").asString()).isEqualTo(HIDDEN + " có kinh nghiệm Java.");
        assertThat(cells.get(0).get("evidence").get(0).get("quote").asString()).isEqualTo(HIDDEN + " - Java");
        assertThat(cells.get(1).get("reasoning").asString()).isEqualTo(HIDDEN + " có kinh nghiệm Java.");
        assertThat(cells.get(2).get("reasoning").asString()).isEqualTo("Trần Bảo Ngọc có kinh nghiệm Java.");

        JsonNode explanations = json.get("explanations");
        assertThat(explanations.get(0).get("summary").asString()).isEqualTo(HIDDEN + " phù hợp.");
        assertThat(explanations.get(0).get("strengths").get(0).get("point").asString()).isEqualTo(HIDDEN + " mạnh Java.");
        assertThat(explanations.get(2).get("summary").asString()).isEqualTo("Trần Bảo Ngọc phù hợp.");

        JsonNode profiles = json.get("profiles");
        assertThat(profiles.get(0).get("skills").get(1).asString()).isEqualTo("Mentor cho " + HIDDEN);
        assertThat(profiles.get(0).get("skills").get(0).asString()).isEqualTo("Java");
        assertThat(profiles.get(0).get("education").get(0).get("school").asString()).isEqualTo("Đại học của " + HIDDEN);
        assertThat(profiles.get(2).get("skills").get(1).asString()).isEqualTo("Mentor cho Trần Bảo Ngọc");

        // Sang loc: SHORT_TEXT che; SINGLE_CHOICE (phuong an HR dat, trung dia chi nguon) va YES_NO giu nguyen.
        assertThat(screeningCell(json, 0, "SHORT_TEXT").get("answerText").asString()).isEqualTo("Em là " + HIDDEN + ".");
        assertThat(screeningCell(json, 0, "SINGLE_CHOICE").get("answerText").asString()).isEqualTo(CHOICE);
        assertThat(screeningCell(json, 0, "YES_NO").get("answerYesNo").asBoolean()).isTrue();
        assertThat(screeningCell(json, 2, "SHORT_TEXT").get("answerText").asString()).isEqualTo("Em là Trần Bảo Ngọc.");
    }

    @Test
    void responseDoesNotLeakMaskedIdentitiesOutsideChoiceOptions() throws Exception {
        Scenario s = scenario(true);

        JsonNode json = body(compare(s));
        // Phuong an SINGLE_CHOICE (CHOICE) la du lieu cua HR, giu nguyen co chu dich - bo ra truoc khi kiem khong lo.
        String withoutScreening = json.deepCopy().toString().replace(CHOICE, "");

        for (App masked : List.of(s.huy(), s.lan())) {
            for (String secret : masked.candidate().secrets()) {
                if (!secret.equals(CHOICE)) {
                    assertThat(withoutScreening.toLowerCase()).as(secret).doesNotContain(secret.toLowerCase());
                }
            }
        }
    }

    @Test
    void totalScoreAndRankEqualWhenToggled() throws Exception {
        Scenario s = scenario(false);
        JsonNode off = body(compare(s));
        setBlindReview(s.jobId(), true);
        JsonNode on = body(compare(s));

        assertThat(on.get("totalScore")).isEqualTo(off.get("totalScore"));
        assertThat(on.get("rank")).isEqualTo(off.get("rank"));
        assertThat(on.get("criteria").get(0).get("cells").get(0).get("score"))
                .isEqualTo(off.get("criteria").get(0).get("cells").get(0).get("score"));
        assertThat(off.get("columns").get(0).get("candidateName").asString()).isEqualTo("Quốc Huy");
        assertThat(off.get("criteria").get(0).get("cells").get(0).get("reasoning").asString())
                .isEqualTo("Quốc Huy có kinh nghiệm Java.");
    }
}
