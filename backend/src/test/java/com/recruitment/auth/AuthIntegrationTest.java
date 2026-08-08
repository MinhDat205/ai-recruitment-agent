package com.recruitment.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.user.UserRepository;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    // Khong dung ObjectMapper (Jackson 2 va Jackson 3 cung ton tai trong classpath du an,
    // tranh mo ho ve version) - request/response deu la JSON phang, du de dung String/regex.
    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    private MvcResult registerCandidate(String email, String password) throws Exception {
        String body =
                """
                {"email":"%s","password":"%s","fullName":"Nguyen Van A","phone":"0900000000"}
                """
                        .formatted(email, password);
        return mockMvc
                .perform(post("/api/auth/register/candidate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private MvcResult registerHr(String email, String password) throws Exception {
        String body =
                """
                {"email":"%s","password":"%s","fullName":"Tran Thi B","phone":"0911111111"}
                """
                        .formatted(email, password);
        return mockMvc
                .perform(post("/api/auth/register/hr").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private String login(String email, String password) throws Exception {
        String body =
                """
                {"email":"%s","password":"%s"}
                """
                        .formatted(email, password);
        MvcResult result =
                mockMvc
                        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isOk())
                        .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void candidateRegisterLoginMe_returnsCorrectRole() throws Exception {
        String email = uniqueEmail("candidate");
        MvcResult registerResult = registerCandidate(email, "password123");
        assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);

        String loginBody = login(email, "password123");
        String accessToken = extractJsonField(loginBody, "accessToken");
        assertThat(accessToken).isNotBlank();

        MvcResult meResult =
                mockMvc
                        .perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isOk())
                        .andReturn();
        String meBody = meResult.getResponse().getContentAsString();
        assertThat(extractJsonField(meBody, "role")).isEqualTo("CANDIDATE");
        assertThat(meBody).contains("createdAt");
        assertThat(meBody).doesNotContain("\"createdAt\":null");
    }

    @Test
    void hrRegisterLoginMe_returnsCorrectRole() throws Exception {
        String email = uniqueEmail("hr");
        registerHr(email, "password123");

        String loginBody = login(email, "password123");
        String accessToken = extractJsonField(loginBody, "accessToken");

        MvcResult meResult =
                mockMvc
                        .perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(extractJsonField(meResult.getResponse().getContentAsString(), "role")).isEqualTo("HR");
    }

    @Test
    void hrPing_rolePlusAnonymousMatrix_isCorrect() throws Exception {
        String hrEmail = uniqueEmail("hr-ping");
        registerHr(hrEmail, "password123");
        String hrToken = extractJsonField(login(hrEmail, "password123"), "accessToken");

        String candidateEmail = uniqueEmail("candidate-ping");
        registerCandidate(candidateEmail, "password123");
        String candidateToken = extractJsonField(login(candidateEmail, "password123"), "accessToken");

        mockMvc
                .perform(get("/api/hr/ping").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk());

        mockMvc
                .perform(get("/api/hr/ping").header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/hr/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordIsHashedWithBCrypt() throws Exception {
        String email = uniqueEmail("hash");
        registerCandidate(email, "password123");

        String hash = userRepository.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2a$");
    }

    @Test
    void duplicateEmail_returnsConflict() throws Exception {
        String email = uniqueEmail("dup");
        MvcResult first = registerCandidate(email, "password123");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        MvcResult second = registerCandidate(email, "password123");
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void responseBody_neverContainsPasswordHash() throws Exception {
        String email = uniqueEmail("nohash");
        MvcResult registerResult = registerCandidate(email, "password123");
        String registerBody = registerResult.getResponse().getContentAsString();
        assertThat(registerBody.toLowerCase()).doesNotContain("passwordhash").doesNotContain("password_hash");

        String accessToken = extractJsonField(login(email, "password123"), "accessToken");
        MvcResult meResult =
                mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken)).andReturn();
        String meBody = meResult.getResponse().getContentAsString();
        assertThat(meBody.toLowerCase()).doesNotContain("passwordhash").doesNotContain("password_hash");
    }

    @Test
    void wrongPassword_returnsUnauthorized() throws Exception {
        String email = uniqueEmail("wrongpass");
        registerCandidate(email, "password123");

        String body =
                """
                {"email":"%s","password":"wrong-password"}
                """
                        .formatted(email);
        mockMvc
                .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithAccessToken_isRejected() throws Exception {
        String email = uniqueEmail("refresh");
        registerCandidate(email, "password123");
        String accessToken = extractJsonField(login(email, "password123"), "accessToken");

        String body = """
                {"refreshToken":"%s"}
                """.formatted(accessToken);
        mockMvc
                .perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithRefreshToken_issuesNewAccessToken() throws Exception {
        String email = uniqueEmail("refresh-ok");
        registerCandidate(email, "password123");
        String loginBody = login(email, "password123");
        String refreshToken = extractJsonField(loginBody, "refreshToken");

        String body = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);
        MvcResult result =
                mockMvc
                        .perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(extractJsonField(result.getResponse().getContentAsString(), "accessToken")).isNotBlank();
    }
}
