package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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

// FR-H16 T8 - /api/hr/candidates (che theo tung dong, Q4) va audit /api/hr/candidates/{id}/audit/scoring-runs (R-M4, L4).
// @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlindReviewSearchAuditIntegrationTest extends BlindReviewIntegrationTestSupport {

    private static final String CRITERION = "Kinh nghiem Java";

    private record Scenario(Hr hr, App masked, App visible) {
    }

    // Cung mot ung vien (Quoc Huy) nop vao hai Job cua cung cong ty: J-bat va J-tat.
    private Scenario scenario() throws Exception {
        Hr hr = hr();
        UUID enabledJob = job(hr, true);
        UUID disabledJob = job(hr, false);
        Candidate huy = candidate("Quốc Huy", "0900000111");
        App masked = application(enabledJob, huy, null);
        App visible = application(disabledJob, huy, null);
        for (App app : List.of(masked, visible)) {
            doneRun(app.id(), new BigDecimal("7.00"), List.of(new Criterion(CRITERION, new BigDecimal("100"),
                    new BigDecimal("7.00"), "Quốc Huy có 4 năm Java, SĐT " + huy.phone() + ".",
                    "QUỐC HUY - Java Developer")));
        }
        return new Scenario(hr, masked, visible);
    }

    private static JsonNode item(JsonNode page, UUID applicationId) {
        for (JsonNode node : page.get("items")) {
            if (node.get("id").asString().equals(applicationId.toString())) {
                return node;
            }
        }
        throw new AssertionError("Khong co dong " + applicationId);
    }

    @Test
    void candidateSearchMasksPerRowByJobOfEachApplication() throws Exception {
        Scenario s = scenario();

        JsonNode page = getJson(s.hr().token(), "/api/hr/candidates?size=50");

        JsonNode masked = item(page, s.masked().id());
        JsonNode visible = item(page, s.visible().id());
        assertThat(masked.get("candidateName").asString()).isEqualTo(CandidateAlias.of(s.masked().id()));
        assertThat(visible.get("candidateName").asString()).isEqualTo("Quốc Huy");
        assertNoLeak(masked.toString(), s.masked().candidate());
        assertThat(masked.get("totalScore").decimalValue()).isEqualByComparingTo(visible.get("totalScore").decimalValue());
    }

    @Test
    void candidateSearchFilteredByJobAndCriterionStillMasks() throws Exception {
        Scenario s = scenario();

        MvcResult result = mockMvc.perform(get("/api/hr/candidates")
                        .header("Authorization", "Bearer " + s.hr().token())
                        .param("size", "50")
                        .param("jobId", s.masked().jobId().toString())
                        .param("criterionName", CRITERION)
                        .param("minCriterionScore", "1"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode page = body(result);

        assertThat(page.get("items")).hasSize(1);
        assertThat(item(page, s.masked().id()).get("candidateName").asString())
                .isEqualTo(CandidateAlias.of(s.masked().id()));
    }

    @Test
    void candidateSearchAfterInvitationShowsRealName() throws Exception {
        Scenario s = scenario();
        invite(s.masked());

        JsonNode page = getJson(s.hr().token(), "/api/hr/candidates?size=50");

        assertThat(item(page, s.masked().id()).get("candidateName").asString()).isEqualTo("Quốc Huy");
    }

    @Test
    void auditOfMaskedApplicationMasksReasoningAndEvidence() throws Exception {
        Scenario s = scenario();

        MvcResult result = getAs(s.hr().token(), "/api/hr/candidates/" + s.masked().id() + "/audit/scoring-runs");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode criterion = body(result).get(0).get("criterionScores").get(0);
        assertThat(criterion.get("criterionNameSnapshot").asString()).isEqualTo(CRITERION);
        assertThat(criterion.get("reasoning").asString()).isEqualTo(HIDDEN + " có 4 năm Java, SĐT " + HIDDEN + ".");
        assertThat(criterion.get("evidence").get(0).get("quote").asString()).isEqualTo(HIDDEN + " - Java Developer");
        assertThat(criterion.get("evidence").get(0).get("section").asString()).isEqualTo("experience");
        assertNoLeak(text(result), s.masked().candidate());
        // DB giu nguyen van (R-A3).
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT cs.reasoning FROM criterion_scores cs JOIN scoring_runs sr ON sr.id = cs.scoring_run_id"
                                + " WHERE sr.application_id = ?",
                        String.class,
                        s.masked().id()))
                .startsWith("Quốc Huy");
    }

    @Test
    void auditOfApplicationInDisabledJobIsVerbatim() throws Exception {
        Scenario s = scenario();

        JsonNode criterion = getJson(s.hr().token(), "/api/hr/candidates/" + s.visible().id() + "/audit/scoring-runs")
                .get(0).get("criterionScores").get(0);

        assertThat(criterion.get("reasoning").asString()).isEqualTo("Quốc Huy có 4 năm Java, SĐT 0900000111.");
        assertThat(criterion.get("evidence").get(0).get("quote").asString()).isEqualTo("QUỐC HUY - Java Developer");
    }
}
