package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// FR-H16 T5 - danh sach theo Job GET /api/hr/jobs/{jobId}/applications (R-N2, R-N3, R-M4, R-A2). @Transactional: moi
// @Test rollback rieng; khong so sanh thoi diem giua cac ban ghi.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlindReviewApplicationListIntegrationTest extends BlindReviewIntegrationTestSupport {

    private static final String CRITERION = "Kinh nghiem Java";

    private record Scenario(Hr hr, UUID jobId, App masked, App invited) {
    }

    // Hai don cung Job: A (Quoc Huy, PENDING) va B (Tran Bao Ngoc, da moi). Chuoi cham diem cua moi don chua ho ten,
    // email cua CHINH don do.
    private Scenario scenario(boolean blindReview) throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, blindReview);
        Candidate huy = candidate("Quốc Huy", "0900000111");
        App a = application(jobId, huy, "Thư giới thiệu của Quốc Huy");
        UUID runA = doneRun(a.id(), new BigDecimal("8.00"), List.of(new Criterion(CRITERION, new BigDecimal("100"),
                new BigDecimal("8.00"), "Ứng viên Quốc Huy (" + huy.email() + ") có 5 năm Java.",
                "QUỐC HUY - Senior Java, liên hệ " + huy.phone())));
        explanation(runA, CRITERION, "Quốc Huy phù hợp vị trí.", "Quốc Huy mạnh Java.", "quốc huy thiếu Kafka.");

        Candidate ngoc = candidate("Trần Bảo Ngọc", "0900000222");
        App b = application(jobId, ngoc, null);
        UUID runB = doneRun(b.id(), new BigDecimal("6.00"), List.of(new Criterion(CRITERION, new BigDecimal("100"),
                new BigDecimal("6.00"), "Trần Bảo Ngọc có 3 năm Java.", "Trần Bảo Ngọc - Java Developer")));
        explanation(runB, CRITERION, "Trần Bảo Ngọc khá phù hợp.", "Trần Bảo Ngọc biết Spring.", "Thiếu Kafka.");
        invite(b);
        return new Scenario(hr, jobId, a, b);
    }

    private MvcResult list(Scenario s) throws Exception {
        return getAs(s.hr().token(), "/api/hr/jobs/" + s.jobId() + "/applications");
    }

    private static JsonNode row(JsonNode list, UUID applicationId) {
        for (JsonNode node : list) {
            if (node.get("id").asString().equals(applicationId.toString())) {
                return node;
            }
        }
        throw new AssertionError("Khong co dong " + applicationId);
    }

    @Test
    void maskedApplicationGetsAliasAndMaskedScoringText() throws Exception {
        Scenario s = scenario(true);

        MvcResult result = list(s);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode a = row(body(result), s.masked().id());
        assertThat(a.get("candidateName").asString()).isEqualTo(CandidateAlias.of(s.masked().id()));
        assertThat(a.get("identityMasked").asBoolean()).isTrue();
        JsonNode criterion = a.get("criterionScores").get(0);
        assertThat(criterion.get("criterionNameSnapshot").asString()).isEqualTo(CRITERION);
        assertThat(criterion.get("reasoning").asString()).isEqualTo("Ứng viên " + HIDDEN + " (" + HIDDEN + ") có 5 năm Java.");
        assertThat(criterion.get("evidence").get(0).get("quote").asString())
                .isEqualTo(HIDDEN + " - Senior Java, liên hệ " + HIDDEN);
        assertThat(criterion.get("evidence").get(0).get("section").asString()).isEqualTo("experience");
        JsonNode explanation = a.get("explanation");
        assertThat(explanation.get("summary").asString()).isEqualTo(HIDDEN + " phù hợp vị trí.");
        assertThat(explanation.get("strengths").get(0).get("point").asString()).isEqualTo(HIDDEN + " mạnh Java.");
        assertThat(explanation.get("strengths").get(0).get("criterionName").asString()).isEqualTo(CRITERION);
        assertThat(explanation.get("weaknesses").get(0).get("point").asString()).isEqualTo(HIDDEN + " thiếu Kafka.");
        assertThat(explanation.get("metCriteria").get(0).asString()).isEqualTo(CRITERION);
        assertNoLeak(text(result), s.masked().candidate());
    }

    @Test
    void invitedApplicationOfSameJobShowsRealNameAndVerbatimText() throws Exception {
        Scenario s = scenario(true);

        JsonNode b = row(body(list(s)), s.invited().id());

        assertThat(b.get("candidateName").asString()).isEqualTo("Trần Bảo Ngọc");
        assertThat(b.get("identityMasked").asBoolean()).isFalse();
        assertThat(b.get("criterionScores").get(0).get("reasoning").asString()).isEqualTo("Trần Bảo Ngọc có 3 năm Java.");
        assertThat(b.get("explanation").get("summary").asString()).isEqualTo("Trần Bảo Ngọc khá phù hợp.");
    }

    @Test
    void jobDisabledShowsEveryoneVerbatim() throws Exception {
        Scenario s = scenario(false);

        String json = text(list(s));
        JsonNode a = row(JSON.readTree(json), s.masked().id());

        assertThat(a.get("candidateName").asString()).isEqualTo("Quốc Huy");
        assertThat(a.get("identityMasked").asBoolean()).isFalse();
        assertThat(a.get("criterionScores").get(0).get("reasoning").asString())
                .isEqualTo("Ứng viên Quốc Huy (" + s.masked().candidate().email() + ") có 5 năm Java.");
        assertThat(a.get("explanation").get("summary").asString()).isEqualTo("Quốc Huy phù hợp vị trí.");
        assertThat(json).doesNotContain(HIDDEN);
    }

    @Test
    void totalScoreAndRankAreIdenticalWhenToggled() throws Exception {
        // R-A2 - bat/tat khong doi diem, hang, thu tu.
        Scenario s = scenario(false);
        JsonNode off = body(list(s));
        setBlindReview(s.jobId(), true);
        JsonNode on = body(list(s));

        Map<String, String> offScores = scoresAndRanks(off);
        assertThat(scoresAndRanks(on)).isEqualTo(offScores);
        assertThat(ids(on)).containsExactlyElementsOf(ids(off));
        assertThat(row(on, s.masked().id()).get("identityMasked").asBoolean()).isTrue();
    }

    @Test
    void appliedAtSortAlsoMasks() throws Exception {
        Scenario s = scenario(true);

        MvcResult result = getAs(s.hr().token(), "/api/hr/jobs/" + s.jobId() + "/applications?sort=APPLIED_AT_DESC");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(row(body(result), s.masked().id()).get("candidateName").asString())
                .isEqualTo(CandidateAlias.of(s.masked().id()));
        assertNoLeak(text(result), s.masked().candidate());
    }

    private static Map<String, String> scoresAndRanks(JsonNode list) {
        Map<String, String> result = new HashMap<>();
        for (JsonNode node : list) {
            result.put(node.get("id").asString(), node.get("totalScore").asString() + "#" + node.get("rank").asString());
        }
        return result;
    }

    private static List<String> ids(JsonNode list) {
        List<String> result = new java.util.ArrayList<>();
        for (JsonNode node : list) {
            result.add(node.get("id").asString());
        }
        return result;
    }
}
