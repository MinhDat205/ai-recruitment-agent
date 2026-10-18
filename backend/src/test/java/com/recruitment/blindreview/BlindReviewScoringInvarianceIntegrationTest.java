package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.scoring.AggregationOrchestrator;
import com.recruitment.scoring.CriterionScore;
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.ScoreExplanationOrchestrator;
import com.recruitment.scoring.ScoringRun;
import com.recruitment.scoring.ScoringRunOrchestrator;
import com.recruitment.scoring.ScoringRunStatus;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

// FR-H16 T16 - bat bien cham diem (R-A2, R-A3): bat/tat an danh KHONG doi dau vao D2 (raw_text), dau vao D4
// (criterion_scores), noi dung luu trong criterion_scores, tong diem, hang. Chay pipeline THAT D2 -> D3 -> D4 qua cac
// orchestrator (processOne), ChatModel mock (LlmTestConfiguration, default-answer throw) - bat prompt THAT da gui.
//
// KHONG @Transactional toan lop (CLAUDE.md muc 3c): orchestrator tu mo transaction ngan; hai luot cham cua cung mot don
// phai co created_at khac nhau de danh sach theo Job lay dung luot DONE moi nhat.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlindReviewScoringInvarianceIntegrationTest extends BlindReviewIntegrationTestSupport {

    private static final String CV_EMAIL = "quochuy.t16@test.local";
    private static final String QUOTE = "Có 5 năm kinh nghiệm Java tại Quốc Huy Studio.";
    private static final String RAW_TEXT = "QUỐC HUY\nEmail: " + CV_EMAIL + "\n" + QUOTE + "\nTiếng Anh giao tiếp tốt.";
    private static final String REASONING = "Quốc Huy có 5 năm Java, liên hệ " + CV_EMAIL + ".";
    private static final String D2_JSON =
            """
            {"score": 4, "reasoning": "%s",
             "evidence": [{"quote": "%s", "section": "experience"}]}
            """.formatted(REASONING, QUOTE);
    private static final String D4_JSON =
            """
            {"summary": "Quốc Huy phù hợp với vị trí",
             "strengths": [{"criterionName": "Kinh nghiem Java", "point": "Quốc Huy mạnh Java"}],
             "weaknesses": [{"criterionName": "Tieng Anh", "point": "Cần cải thiện"}]}
            """;

    @Autowired
    private ScoringRunOrchestrator scoringRunOrchestrator;

    @Autowired
    private AggregationOrchestrator aggregationOrchestrator;

    @Autowired
    private ScoreExplanationOrchestrator scoreExplanationOrchestrator;

    @Autowired
    private ChatModel chatModel;

    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private static ChatResponse fakeResponse(String text) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .metadata(ChatResponseMetadata.builder()
                        .model("claude-sonnet-4-6")
                        .usage(new DefaultUsage(100, 50))
                        .build())
                .build();
    }

    private static RubricSnapshot snapshot() {
        return new RubricSnapshot("Rubric T16", List.of(
                new RubricSnapshot.CriterionSnapshot(
                        UUID.randomUUID(), "Kinh nghiem Java", "So nam lam Java", new BigDecimal("60"), 5, List.of()),
                new RubricSnapshot.CriterionSnapshot(
                        UUID.randomUUID(), "Tieng Anh", "Muc do giao tiep", new BigDecimal("40"), 5, List.of())));
    }

    private record RoundResult(UUID runId, List<Prompt> d2Prompts, List<Prompt> d4Prompts) {
    }

    // Mot luot cham day du D2 -> D3 -> D4 cho don, voi cung mot rubric_snapshot (cung criterionId de so sanh dung).
    private RoundResult score(UUID applicationId, RubricSnapshot snapshot) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.PENDING);
        run.setRubricSnapshot(snapshot);
        UUID runId = scoringRunRepository.saveAndFlush(run).getId();

        Mockito.clearInvocations(chatModel);
        doReturn(fakeResponse(D2_JSON)).when(chatModel).call(any(Prompt.class));
        scoringRunOrchestrator.processOne(runId);
        List<Prompt> d2 = captured(2);

        aggregationOrchestrator.processOne(runId);

        Mockito.clearInvocations(chatModel);
        doReturn(fakeResponse(D4_JSON)).when(chatModel).call(any(Prompt.class));
        scoreExplanationOrchestrator.processOne(runId);
        List<Prompt> d4 = captured(1);
        return new RoundResult(runId, d2, d4);
    }

    private List<Prompt> captured(int expectedCalls) {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel, Mockito.times(expectedCalls)).call(captor.capture());
        return captor.getAllValues();
    }

    private List<CriterionScore> storedScores(UUID runId) {
        return criterionScoreRepository.findByScoringRunId(runId).stream()
                .sorted(Comparator.comparing(CriterionScore::getCriterionNameSnapshot))
                .toList();
    }

    private ScoringRun storedRun(UUID runId) {
        entityManager.clear();
        return scoringRunRepository.findById(runId).orElseThrow();
    }

    private JsonNode listRow(Hr hr, UUID jobId, UUID applicationId) throws Exception {
        for (JsonNode node : getJson(hr.token(), "/api/hr/jobs/" + jobId + "/applications")) {
            if (node.get("id").asString().equals(applicationId.toString())) {
                return node;
            }
        }
        throw new AssertionError("Khong co dong " + applicationId);
    }

    @Test
    void togglingBlindReviewDoesNotChangeScoringInputsStoredScoresOrRank() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, false);
        Candidate huy = candidate("Quốc Huy", "0900000111");
        App app = application(jobId, huy, null);
        // Mot don thu hai cung Job de hang co y nghia (1 vs 2), cham diem dung san.
        App other = application(jobId, candidate("Trần Bảo Ngọc", "0900000222"), null);
        doneRun(other.id(), new BigDecimal("50.00"), List.of(new Criterion("Kinh nghiem Java", new BigDecimal("100"),
                new BigDecimal("2.50"), "Trần Bảo Ngọc có 2 năm Java.", "Trần Bảo Ngọc")));
        parsedData(app.resumeId(), RAW_TEXT, """
                {"contact": {"fullName": "QUỐC HUY", "email": "%s"}, "education": [], "experience": [], "skills": [],
                 "certifications": [], "projects": []}
                """.formatted(CV_EMAIL));
        RubricSnapshot snapshot = snapshot();

        // Luot 1 - Job TAT.
        RoundResult off = score(app.id(), snapshot);
        JsonNode rowOff = listRow(hr, jobId, app.id());

        // Luot 2 - Job BAT, don dang an danh (chua co lich su INTERVIEW_INVITED).
        setBlindReview(jobId, true);
        assertThat(getJson(hr.token(), "/api/hr/applications/" + app.id()).get("identityMasked").asBoolean()).isTrue();
        RoundResult on = score(app.id(), snapshot);
        JsonNode rowOn = listRow(hr, jobId, app.id());

        // D2: system + user message cua TUNG tieu chi giong het nhau, va chua ho ten/email nguyen van trong raw_text.
        assertThat(on.d2Prompts()).hasSize(2);
        for (int i = 0; i < 2; i++) {
            String userOff = off.d2Prompts().get(i).getUserMessage().getText();
            String userOn = on.d2Prompts().get(i).getUserMessage().getText();
            assertThat(userOn).as("user message tieu chi %d", i).isEqualTo(userOff);
            assertThat(on.d2Prompts().get(i).getSystemMessage().getText())
                    .isEqualTo(off.d2Prompts().get(i).getSystemMessage().getText());
            assertThat(userOn).contains("QUỐC HUY").contains(CV_EMAIL).contains(QUOTE).doesNotContain(HIDDEN);
        }

        // criterion_scores luu NGUYEN VAN (co ho ten), giong het giua hai luot.
        List<CriterionScore> scoresOff = storedScores(off.runId());
        List<CriterionScore> scoresOn = storedScores(on.runId());
        assertThat(scoresOn).hasSize(2);
        for (int i = 0; i < 2; i++) {
            assertThat(scoresOn.get(i).getReasoning()).isEqualTo(REASONING).isEqualTo(scoresOff.get(i).getReasoning());
            assertThat(scoresOn.get(i).getEvidence().getFirst().quote()).isEqualTo(QUOTE);
            assertThat(scoresOn.get(i).getEvidence()).isEqualTo(scoresOff.get(i).getEvidence());
            assertThat(scoresOn.get(i).getScore()).isEqualByComparingTo(scoresOff.get(i).getScore());
        }
        String storedReasoning = jdbcTemplate.queryForObject(
                "SELECT reasoning FROM criterion_scores WHERE scoring_run_id = ? LIMIT 1", String.class, on.runId());
        assertThat(storedReasoning).contains("Quốc Huy").contains(CV_EMAIL);

        // D4: dau vao giong het (doc tu criterion_scores - khong doc raw_text/data).
        assertThat(on.d4Prompts().getFirst().getUserMessage().getText())
                .isEqualTo(off.d4Prompts().getFirst().getUserMessage().getText());

        // D3: tong diem bang nhau; danh sach theo Job: totalScore va rank bang nhau khi tat va bat.
        ScoringRun runOff = storedRun(off.runId());
        ScoringRun runOn = storedRun(on.runId());
        assertThat(runOn.getStatus()).isEqualTo(ScoringRunStatus.DONE);
        assertThat(runOff.getStatus()).isEqualTo(ScoringRunStatus.DONE);
        assertThat(runOn.getTotalScore()).isNotNull().isEqualByComparingTo(runOff.getTotalScore());
        assertThat(rowOn.get("totalScore").decimalValue()).isEqualByComparingTo(rowOff.get("totalScore").decimalValue());
        assertThat(rowOn.get("rank").asInt()).isEqualTo(rowOff.get("rank").asInt()).isEqualTo(1);

        // Che chi o response HR: luot 2 dang hien voi nhan va chuoi da che, DB van nguyen van (tren).
        assertThat(rowOff.get("identityMasked").asBoolean()).isFalse();
        assertThat(rowOff.get("criterionScores").get(0).get("reasoning").asString()).isEqualTo(REASONING);
        assertThat(rowOn.get("identityMasked").asBoolean()).isTrue();
        assertThat(rowOn.get("candidateName").asString()).isEqualTo(CandidateAlias.of(app.id()));
        assertThat(rowOn.get("criterionScores").get(0).get("reasoning").asString())
                .isEqualTo(HIDDEN + " có 5 năm Java, liên hệ " + HIDDEN + ".");
    }
}
