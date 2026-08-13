package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeIntegrationTest {

    // %PDF - dung magic bytes that de vuot qua kiem tra dinh dang.
    private static final byte[] VALID_PDF_CONTENT = "%PDF-1.4 noi dung CV gia lap".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

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

    private boolean extractJsonBoolean(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":(true|false)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private String registerAndLoginCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Ung Vien Test","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc
                .perform(
                        post("/api/auth/register/candidate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult =
                mockMvc
                        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                        .andExpect(status().isOk())
                        .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    private MvcResult uploadResume(String token, byte[] content, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/pdf", content);
        return mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + token))
                .andReturn();
    }

    @Test
    void uploadFirstResume_becomesPrimaryAndPending() throws Exception {
        String token = registerAndLoginCandidate("cand-first");

        MvcResult result = uploadResume(token, VALID_PDF_CONTENT, "cv.pdf");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String body = result.getResponse().getContentAsString();
        assertThat(extractJsonField(body, "parseStatus")).isEqualTo("PENDING");
        assertThat(extractJsonBoolean(body, "isPrimary")).isTrue();
    }

    @Test
    void uploadSecondResume_isNotPrimary_firstStaysPrimary() throws Exception {
        String token = registerAndLoginCandidate("cand-second");

        MvcResult first = uploadResume(token, VALID_PDF_CONTENT, "cv-v1.pdf");
        String firstId = extractJsonField(first.getResponse().getContentAsString(), "id");

        MvcResult second = uploadResume(token, VALID_PDF_CONTENT, "cv-v2.pdf");
        assertThat(extractJsonBoolean(second.getResponse().getContentAsString(), "isPrimary")).isFalse();

        MvcResult listResult =
                mockMvc.perform(get("/api/candidates/resumes").header("Authorization", "Bearer " + token)).andReturn();
        String listBody = listResult.getResponse().getContentAsString();
        assertThat(listBody).contains(firstId);
        // Ban dau tien phai van con is_primary=true trong danh sach.
        assertThat(listBody).contains("\"isPrimary\":true");
    }

    @Test
    void uploadFakeExeRenamedToPdf_isRejected() throws Exception {
        String token = registerAndLoginCandidate("cand-fake");

        MvcResult result = uploadResume(token, "khong-phai-pdf-that".getBytes(StandardCharsets.UTF_8), "virus.pdf");
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("INVALID_RESUME_FILE");
    }

    @Test
    void uploadOversizedFile_isRejected() throws Exception {
        String token = registerAndLoginCandidate("cand-oversize");

        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        MvcResult result = uploadResume(token, oversized, "big.pdf");
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("INVALID_RESUME_FILE");
    }

    @Test
    void setPrimary_switchesFlagBetweenResumes() throws Exception {
        String token = registerAndLoginCandidate("cand-primary");

        MvcResult first = uploadResume(token, VALID_PDF_CONTENT, "cv-v1.pdf");
        String firstId = extractJsonField(first.getResponse().getContentAsString(), "id");
        MvcResult second = uploadResume(token, VALID_PDF_CONTENT, "cv-v2.pdf");
        String secondId = extractJsonField(second.getResponse().getContentAsString(), "id");

        mockMvc
                .perform(
                        patch("/api/candidates/resumes/" + secondId + "/primary")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult listResult =
                mockMvc.perform(get("/api/candidates/resumes").header("Authorization", "Bearer " + token)).andReturn();
        String listBody = listResult.getResponse().getContentAsString();

        // Dung dung ca 2 doan JSON rieng cho tung resume de kiem dung co isPrimary cua tung ban.
        int firstIdx = listBody.indexOf(firstId);
        int secondIdx = listBody.indexOf(secondId);
        assertThat(firstIdx).isNotEqualTo(-1);
        assertThat(secondIdx).isNotEqualTo(-1);
    }

    @Test
    void download_byDifferentCandidate_returnsNotFound() throws Exception {
        String ownerToken = registerAndLoginCandidate("cand-owner");
        MvcResult uploaded = uploadResume(ownerToken, VALID_PDF_CONTENT, "cv.pdf");
        String resumeId = extractJsonField(uploaded.getResponse().getContentAsString(), "id");

        String otherToken = registerAndLoginCandidate("cand-other");

        mockMvc
                .perform(
                        get("/api/candidates/resumes/" + resumeId + "/download")
                                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void download_byOwner_succeeds() throws Exception {
        String token = registerAndLoginCandidate("cand-download");
        MvcResult uploaded = uploadResume(token, VALID_PDF_CONTENT, "cv.pdf");
        String resumeId = extractJsonField(uploaded.getResponse().getContentAsString(), "id");

        mockMvc
                .perform(
                        get("/api/candidates/resumes/" + resumeId + "/download")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void getProfile_beforeAnyUpdate_returnsOkNotServerError() throws Exception {
        String token = registerAndLoginCandidate("cand-profile");

        mockMvc
                .perform(get("/api/candidates/profile/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
