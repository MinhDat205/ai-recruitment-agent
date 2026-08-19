package com.recruitment.resume;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// File rieng cho endpoint GET /{id}/parsed - tach khoi ResumeIntegrationTest vi can them ha tang
// mock LLM (LlmTestConfiguration) chi rieng nhom test nay dung toi, khong lam nang cac test CRUD
// don gian khac trong ResumeIntegrationTest.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeParsedDataEndpointTest {

    private static final String VALID_JSON =
            """
            {
              "contact": {"fullName": "Nguyen Van A", "email": "a@example.com", "phone": null, "address": null, "linkedin": null},
              "education": [],
              "experience": [],
              "skills": ["Java"],
              "certifications": [],
              "projects": []
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResumeParsingOrchestrator orchestrator;

    @Autowired
    private ChatModel chatModel;

    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    private String registerAndLoginCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Ung Vien Test","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc.perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    private byte[] readFixture(String filename) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/resumes/" + filename)) {
            return in.readAllBytes();
        }
    }

    private String uploadResumeAndGetId(String token, byte[] content, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/pdf", content);
        MvcResult result = mockMvc.perform(multipart("/api/candidates/resumes")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private ChatResponse fakeValidResponse() {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(VALID_JSON))))
                .metadata(ChatResponseMetadata.builder()
                        .model("claude-sonnet-4-6")
                        .usage(new DefaultUsage(100, 50))
                        .build())
                .build();
    }

    @Test
    void getParsedData_stillPending_returnsNotFoundWithCode() throws Exception {
        String token = registerAndLoginCandidate("cand-parsed-pending");
        // %PDF gia - du qua kiem tra magic bytes o upload, chua chay processOne() trong test nay
        // nen khong can noi dung PDF that.
        String resumeId =
                uploadResumeAndGetId(token, "%PDF-1.4 noi dung CV gia lap".getBytes(StandardCharsets.UTF_8), "cv.pdf");

        mockMvc.perform(get("/api/candidates/resumes/" + resumeId + "/parsed").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("RESUME_PARSED_DATA_NOT_FOUND")));
    }

    @Test
    void getParsedData_byDifferentCandidate_returnsNotFound() throws Exception {
        String ownerToken = registerAndLoginCandidate("cand-parsed-owner");
        String resumeId = uploadResumeAndGetId(
                ownerToken, "%PDF-1.4 noi dung CV gia lap".getBytes(StandardCharsets.UTF_8), "cv.pdf");

        String otherToken = registerAndLoginCandidate("cand-parsed-other");

        mockMvc.perform(get("/api/candidates/resumes/" + resumeId + "/parsed")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getParsedData_afterSuccessfulProcessing_returnsExtractedData() throws Exception {
        String token = registerAndLoginCandidate("cand-parsed-done");
        String resumeId = uploadResumeAndGetId(token, readFixture("cv-mot-cot.pdf"), "cv-mot-cot.pdf");

        doReturn(fakeValidResponse()).when(chatModel).call(any(Prompt.class));
        orchestrator.processOne(UUID.fromString(resumeId));

        mockMvc.perform(get("/api/candidates/resumes/" + resumeId + "/parsed").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nguyen Van A")));
    }
}
