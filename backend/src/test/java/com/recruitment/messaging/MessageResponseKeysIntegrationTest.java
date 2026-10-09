package com.recruitment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-C06 T6 (dot 3, muc 12 L1) - JSON cua M1, M2, M5 hai phia: duyet de quy MOI khoa, so khop NGUYEN TEN khoa
// (khong theo chuoi con) voi danh sach cam o REQUIREMENT muc 4.4. Response phia ung vien cam them
// candidateName/hrName, va KHONG chua ho ten hay email cua HR o bat ky gia tri chuoi nao. Khong bo khoa nao
// khoi danh sach de test xanh.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MessageResponseKeysIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final byte[] PDF = Arrays.copyOf(new byte[] {0x25, 0x50, 0x44, 0x46}, 40);

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "senderId",
            "readAt",
            "attachmentKey",
            "fileUrl",
            "candidateId",
            "ownerId",
            "email",
            "totalScore",
            "rank",
            "criterionScores",
            "explanation",
            "scoringRunId",
            "verdict",
            "label",
            "isQualified",
            "passed",
            "recommendation");

    private static final Set<String> CANDIDATE_EXTRA_FORBIDDEN_KEYS = Set.of("candidateName", "hrName");

    @Autowired
    private MockMvc mockMvc;

    // ---- helper (chep theo ApplicationCandidateDetailControllerIntegrationTest) ----

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String login(String email) throws Exception {
        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult = mockMvc
                .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    private String registerHr(String email, String fullName) throws Exception {
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"%s","phone":"0900000000"}
                """
                        .formatted(email, fullName);
        mockMvc
                .perform(post("/api/auth/register/hr").contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String registerAndLoginCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody = """
                {"email":"%s","password":"password123","fullName":"Ung Vien Test"}
                """.formatted(email);
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());
        return login(email);
    }

    private void createCompany(String token, String name) throws Exception {
        String body = """
                {"name":"%s"}
                """.formatted(name);
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private String createJob(String token, String title) throws Exception {
        String body =
                """
                {
                  "job": {"title":"%s","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                  "interviewTemplate": {
                    "subject":"Thu moi phong van vi tri %s",
                    "body":"Kinh chao ung vien, chung toi moi ban tham gia phong van.",
                    "senderName":"Phong Nhan Su"
                  }
                }
                """
                        .formatted(title, title);
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private void addCriterion(String token, String jobId) throws Exception {
        mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tieu chi","weight":100}
                                """))
                .andExpect(status().isCreated());
    }

    private void openJob(String token, String jobId) throws Exception {
        mockMvc
                .perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OPEN"}
                                """))
                .andExpect(status().isOk());
    }

    private String uploadResume(String candidateToken) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes());
        MvcResult result = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private String apply(String candidateToken, String jobId, String resumeId) throws Exception {
        String body = """
                {"jobId":"%s","resumeId":"%s","aiConsent":true}
                """.formatted(jobId, resumeId);
        MvcResult result = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    // ---- duyet JSON ----

    private static void collectKeys(JsonNode node, Set<String> keys) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                keys.add(field.getKey());
                collectKeys(field.getValue(), keys);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectKeys(child, keys));
        }
    }

    private static void collectStrings(JsonNode node, List<String> values) {
        if (node.isString()) {
            values.add(node.asString());
        } else if (node.isObject()) {
            node.properties().forEach(field -> collectStrings(field.getValue(), values));
        } else if (node.isArray()) {
            node.forEach(child -> collectStrings(child, values));
        }
    }

    private static Set<String> keysOf(JsonNode node) {
        Set<String> keys = new HashSet<>();
        collectKeys(node, keys);
        return keys;
    }

    private static List<String> stringsOf(JsonNode node) {
        List<String> values = new ArrayList<>();
        collectStrings(node, values);
        return values;
    }

    private JsonNode call(MvcResult result, int expectedStatus) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return body(result);
    }

    @Test
    void m1M2M5_containNoForbiddenKeys_andCandidateSideNeverSeesHrIdentity() throws Exception {
        String hrEmail = uniqueEmail("c06-t6-hr");
        String hrFullName = uniqueName("Nguoi Tuyen Dung An");
        String hrToken = registerHr(hrEmail, hrFullName);
        createCompany(hrToken, uniqueName("Cong ty c06-t6"));
        String jobId = createJob(hrToken, uniqueName("Job c06-t6"));
        addCriterion(hrToken, jobId);
        openJob(hrToken, jobId);
        String candidateToken = registerAndLoginCandidate("c06-t6-cand");
        String applicationId = apply(candidateToken, jobId, uploadResume(candidateToken));
        String hrPath = "/api/hr/applications/" + applicationId + "/messages";
        String candidatePath = "/api/candidates/applications/" + applicationId + "/messages";

        // M2 hai phia, co ca tin co tep de phu khoa trong object attachment.
        JsonNode hrSent = call(mockMvc.perform(multipart(hrPath)
                        .file(new MockMultipartFile("file", "bang-cap.pdf", "application/pdf", PDF))
                        .param("body", "Chào bạn, gửi bạn tài liệu.")
                        .header("Authorization", "Bearer " + hrToken))
                .andReturn(), 201);
        JsonNode candidateSent = call(mockMvc.perform(multipart(candidatePath)
                        .file(new MockMultipartFile("file", "cccd.pdf", "application/pdf", PDF))
                        .param("body", "Em gửi ạ.")
                        .header("Authorization", "Bearer " + candidateToken))
                .andReturn(), 201);

        JsonNode hrThread = call(mockMvc.perform(get(hrPath).header("Authorization", "Bearer " + hrToken))
                .andReturn(), 200);
        JsonNode candidateThread = call(mockMvc.perform(get(candidatePath).header("Authorization", "Bearer " + candidateToken))
                .andReturn(), 200);
        JsonNode hrInbox = call(mockMvc.perform(get("/api/hr/messages/conversations")
                        .header("Authorization", "Bearer " + hrToken))
                .andReturn(), 200);
        JsonNode candidateInbox = call(mockMvc.perform(get("/api/candidates/messages/conversations")
                        .header("Authorization", "Bearer " + candidateToken))
                .andReturn(), 200);

        // Dam bao response co du lieu that (khong pass vi rong).
        assertThat(hrThread.get("messages")).hasSize(2);
        assertThat(candidateThread.get("messages").get(0).get("attachment").isObject()).isTrue();
        assertThat(candidateInbox.get("items")).hasSize(1);
        assertThat(hrInbox.get("items")).hasSize(1);

        for (JsonNode response : List.of(hrSent, candidateSent, hrThread, candidateThread, hrInbox, candidateInbox)) {
            assertThat(keysOf(response)).doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
        }
        for (JsonNode response : List.of(candidateSent, candidateThread, candidateInbox)) {
            assertThat(keysOf(response)).doesNotContainAnyElementsOf(CANDIDATE_EXTRA_FORBIDDEN_KEYS);
            for (String value : stringsOf(response)) {
                assertThat(value).doesNotContain(hrFullName).doesNotContain(hrEmail);
            }
        }
    }
}
