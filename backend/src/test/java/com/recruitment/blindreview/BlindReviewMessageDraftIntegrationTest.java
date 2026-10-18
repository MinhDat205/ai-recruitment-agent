package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.messagedraft.MessageDraftService;
import com.recruitment.resume.LlmTestConfiguration;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

// FR-H16 T14 - AI soan nhap C07 (R-C1..R-C3): K1 khong dua ho ten vao ngu canh HR cua don an danh, prompt v2, ban nhap tra
// HR qua bo che; phia ung vien khong doi (R-U1). KHONG @Transactional toan lop: K3 chan goi AI khi dang co transaction.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlindReviewMessageDraftIntegrationTest extends BlindReviewIntegrationTestSupport {

    @Autowired
    private ChatModel chatModel;

    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private void stubDraft(String draft) {
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(JSON.writeValueAsString(Map.of("draft", draft))))))
                .metadata(ChatResponseMetadata.builder().model("claude-sonnet-4-6").usage(new DefaultUsage(100, 50)).build())
                .build();
        doReturn(response).when(chatModel).call(any(Prompt.class));
    }

    private Prompt capturedPrompt() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel, Mockito.times(1)).call(captor.capture());
        return captor.getValue();
    }

    private MvcResult draft(String token, String path) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of(
                                "scenario", "CUSTOM", "tone", "FORMAL", "customPurpose", "Hỏi thêm về kinh nghiệm làm việc"))))
                .andReturn();
    }

    private record Scenario(Hr hr, UUID jobId, App app) {
    }

    private Scenario scenario(boolean blindReview) throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, blindReview);
        return new Scenario(hr, jobId, application(jobId, candidate("Quốc Huy", "0900000111"), null));
    }

    private static String hrPath(App app) {
        return "/api/hr/applications/" + app.id() + "/messages/ai-draft";
    }

    @Test
    void promptVersionIsV2() throws Exception {
        Field field = MessageDraftService.class.getDeclaredField("PROMPT_VERSION");
        field.setAccessible(true);
        assertThat(field.get(null)).isEqualTo("message-draft-v2");
    }

    @Test
    void hrDraftOfMaskedApplicationOmitsNameAndMasksDraft() throws Exception {
        Scenario s = scenario(true);
        stubDraft("Chào Quốc Huy,\nMời bạn bổ sung thông tin kinh nghiệm. Liên hệ 0900000111 nếu cần.");

        MvcResult result = draft(s.hr().token(), hrPath(s.app()));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Prompt prompt = capturedPrompt();
        String user = prompt.getUserMessage().getText();
        assertThat(user).contains("Họ tên ứng viên: (không cung cấp)\n").doesNotContain("Quốc Huy");
        assertNoLeak(user, s.app().candidate());
        assertThat(prompt.getSystemMessage().getText())
                .contains("nếu dòng \"Họ tên ứng viên\" là \"(không cung cấp)\" thì gọi ứng viên là \"Anh/Chị\"");
        assertThat(body(result).get("draft").asString())
                .isEqualTo("Chào " + HIDDEN + ",\nMời bạn bổ sung thông tin kinh nghiệm. Liên hệ " + HIDDEN + " nếu cần.");
        assertNoLeak(text(result), s.app().candidate());
    }

    @Test
    void candidateDraftOfSameMaskedApplicationIsUnchanged() throws Exception {
        Scenario s = scenario(true);
        stubDraft("Kính gửi Anh/Chị, em là Quốc Huy, muốn hỏi tiến độ hồ sơ.");

        MvcResult result = draft(s.app().candidate().token(),
                "/api/candidates/applications/" + s.app().id() + "/messages/ai-draft");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(capturedPrompt().getUserMessage().getText()).contains("Họ tên ứng viên: Quốc Huy\n");
        assertThat(body(result).get("draft").asString()).isEqualTo("Kính gửi Anh/Chị, em là Quốc Huy, muốn hỏi tiến độ hồ sơ.");
    }

    @Test
    void hrDraftWhenJobDisabledKeepsName() throws Exception {
        Scenario s = scenario(false);
        stubDraft("Chào Quốc Huy, mời bạn bổ sung thông tin.");

        MvcResult result = draft(s.hr().token(), hrPath(s.app()));

        assertThat(capturedPrompt().getUserMessage().getText()).contains("Họ tên ứng viên: Quốc Huy\n");
        assertThat(body(result).get("draft").asString()).isEqualTo("Chào Quốc Huy, mời bạn bổ sung thông tin.");
    }

    @Test
    void hrDraftAfterInvitationKeepsName() throws Exception {
        Scenario s = scenario(true);
        invite(s.app());
        stubDraft("Chào Quốc Huy, nhắc lịch phỏng vấn.");

        MvcResult result = draft(s.hr().token(), hrPath(s.app()));

        assertThat(capturedPrompt().getUserMessage().getText()).contains("Họ tên ứng viên: Quốc Huy\n");
        assertThat(body(result).get("draft").asString()).isEqualTo("Chào Quốc Huy, nhắc lịch phỏng vấn.");
    }
}
