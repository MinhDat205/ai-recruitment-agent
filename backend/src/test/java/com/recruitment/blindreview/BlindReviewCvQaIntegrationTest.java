package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.LlmTestConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-H16 T15 - hoi dap CV C08 phia HR (R-Q1, R-A3): K2 kiem tren cau tra loi THO; che answer va citations[].quote SAU K2,
// chi o response HR. Trich dan khong khop CV van loi nhu cu (che khong lam K2 dat gia). Phia ung vien khong doi.
// KHONG @Transactional toan lop: K3 chan goi AI khi dang co transaction.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlindReviewCvQaIntegrationTest extends BlindReviewIntegrationTestSupport {

    private static final String QUOTE = "5 năm phát triển Java tại Quốc Huy Studio";
    private static final String RAW_TEXT = "QUỐC HUY\nKinh nghiệm: " + QUOTE + ".\nKỹ năng: Spring Boot, Docker.\n";
    private static final String ANSWER = "Quốc Huy có " + QUOTE + ".";
    private static final String QUESTION = "Ứng viên đã dùng Java ở đâu?";

    @Autowired
    private ChatModel chatModel;

    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private void stubAnswer(String answer, String quote) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answer", answer);
        body.put("citations", List.of(Map.of("quote", quote)));
        body.put("notFound", false);
        body.put("refused", null);
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(JSON.writeValueAsString(body)))))
                .metadata(ChatResponseMetadata.builder().model("claude-sonnet-4-6").usage(new DefaultUsage(100, 50)).build())
                .build();
        doReturn(response).when(chatModel).call(any(Prompt.class));
    }

    private MvcResult ask(String token, String path) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("question", QUESTION))))
                .andReturn();
    }

    private App app(boolean blindReview) throws Exception {
        UUID jobId = job(currentHr = hr(), blindReview);
        App app = application(jobId, withContact(candidate("Quốc Huy", "0900000111"), "QUỐC HUY", null, null, null, null),
                null);
        parsedData(app.resumeId(), RAW_TEXT, """
                {"contact": {"fullName": "QUỐC HUY"}, "education": [], "experience": [], "skills": [],
                 "certifications": [], "projects": []}
                """);
        return app;
    }

    private Hr currentHr;

    private static String hrPath(App app) {
        return "/api/hr/applications/" + app.id() + "/cv-qa";
    }

    @Test
    void hrAnswerOfMaskedApplicationIsMaskedAfterVerbatimCheck() throws Exception {
        App app = app(true);
        stubAnswer(ANSWER, QUOTE);

        MvcResult result = ask(currentHr.token(), hrPath(app));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.get("outcome").asString()).isEqualTo("ANSWERED");
        assertThat(json.get("answer").asString()).isEqualTo(HIDDEN + " có 5 năm phát triển Java tại " + HIDDEN + " Studio.");
        assertThat(json.get("citations").get(0).get("quote").asString())
                .isEqualTo("5 năm phát triển Java tại " + HIDDEN + " Studio");
        assertNoLeak(text(result), app.candidate());
        // Prompt gui AI van la raw_text NGUYEN VAN (khong che dau vao - muc 6).
        org.mockito.ArgumentCaptor<Prompt> captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel, Mockito.times(1)).call(captor.capture());
        assertThat(captor.getValue().getUserMessage().getText()).contains("QUỐC HUY").contains(QUOTE);
    }

    @Test
    void citationNotInCvStillFailsWhenMasked() throws Exception {
        // Trich dan khong khop raw_text: K2 loi (thu lai 1 lan roi 502) - bo che chay SAU K2 nen khong lam dat gia.
        App app = app(true);
        stubAnswer("[đã ẩn] có 5 năm Java.", "5 năm phát triển Java tại [đã ẩn] Studio");

        MvcResult result = ask(currentHr.token(), hrPath(app));

        assertThat(result.getResponse().getStatus()).isEqualTo(502);
        assertThat(body(result).get("error").asString()).isEqualTo("AI_INVALID_OUTPUT");
    }

    @Test
    void hrAnswerWhenJobDisabledIsVerbatim() throws Exception {
        App app = app(false);
        stubAnswer(ANSWER, QUOTE);

        JsonNode json = body(ask(currentHr.token(), hrPath(app)));

        assertThat(json.get("answer").asString()).isEqualTo(ANSWER);
        assertThat(json.get("citations").get(0).get("quote").asString()).isEqualTo(QUOTE);
    }

    @Test
    void candidateAnswerForOwnCvIsUnchanged() throws Exception {
        App app = app(true);
        stubAnswer(ANSWER, QUOTE);

        MvcResult result = ask(app.candidate().token(), "/api/candidates/resumes/" + app.resumeId() + "/cv-qa");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("answer").asString()).isEqualTo(ANSWER);
        assertThat(body(result).get("citations").get(0).get("quote").asString()).isEqualTo(QUOTE);
    }
}
