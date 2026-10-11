package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-H10 T17 (R-Q11) - ca bon thao tac ghi tang jobs.screening_revision dung 1; thao tac that bai va GET khong doi.
// KHONG @Transactional toan lop: neu boc, service tham gia transaction cua test, loi o service khong rollback lan tang
// revision truoc do -> phan "that bai khong doi" se sai vi ly do khac. Moi request tu commit; du lieu ngau nhien rieng
// cua test, khong don sau test (L2, tien le ScoringRunOrchestratorTest).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScreeningQuestionRevisionIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String registerAndLoginHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Nha Tuyen Dung","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc
                .perform(post("/api/auth/register/hr").contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated());
        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult = mockMvc
                .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    private void createCompany(String token) throws Exception {
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cong ty " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());
    }

    private String createJob(String token) throws Exception {
        String body =
                """
                {
                  "job": {"title":"Nhan vien CSKH","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                  "interviewTemplate": {"subject":"Thu moi","body":"Kinh chao ung vien.","senderName":"Phong Nhan Su"}
                }
                """;
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private int httpStatus(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private MvcResult call(String method, String path, String token, String json) throws Exception {
        var builder = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
        builder = builder.header("Authorization", "Bearer " + token);
        if (json != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private int revisionInDb(String jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT screening_revision FROM jobs WHERE id = ?", Integer.class, UUID.fromString(jobId));
    }

    // Doc ca qua S1 lan truy van thang cot - hai nguon phai bang nhau.
    private int revision(String token, String jobId) throws Exception {
        MvcResult result = call("GET", url(jobId), token, null);
        assertThat(httpStatus(result)).isEqualTo(200);
        int fromApi = body(result).get("revision").asInt();
        assertThat(fromApi).isEqualTo(revisionInDb(jobId));
        return fromApi;
    }

    private static String url(String jobId) {
        return "/api/hr/jobs/" + jobId + "/screening-questions";
    }

    private static String question(String text) {
        return "{\"questionType\":\"SHORT_TEXT\",\"questionText\":\"" + text + "\"}";
    }

    @Test
    void t17_eachOfFourWritesIncrementsByOne_failuresAndReadsDoNot() throws Exception {
        String token = registerAndLoginHr("t17");
        createCompany(token);
        String jobId = createJob(token);
        String otherToken = registerAndLoginHr("t17-other");
        createCompany(otherToken);

        assertThat(revision(token, jobId)).isZero();

        // S2 them -> +1
        MvcResult added = call("POST", url(jobId), token, question("Cau A"));
        assertThat(httpStatus(added)).isEqualTo(201);
        String a = body(added).get("id").asString();
        assertThat(revision(token, jobId)).isEqualTo(1);
        String b = body(call("POST", url(jobId), token, question("Cau B"))).get("id").asString();
        assertThat(revision(token, jobId)).isEqualTo(2);

        // S3 sua -> +1
        assertThat(httpStatus(call("PUT", url(jobId) + "/" + a, token, question("Cau A da sua")))).isEqualTo(200);
        assertThat(revision(token, jobId)).isEqualTo(3);

        // S5 doi thu tu -> +1
        String order = JSON.writeValueAsString(Map.of("questionIds", List.of(b, a)));
        assertThat(httpStatus(call("PUT", url(jobId) + "/order", token, order))).isEqualTo(200);
        assertThat(revision(token, jobId)).isEqualTo(4);

        // S4 xoa -> +1
        assertThat(httpStatus(call("DELETE", url(jobId) + "/" + b, token, null))).isEqualTo(204);
        assertThat(revision(token, jobId)).isEqualTo(5);

        // That bai -> khong doi.
        assertThat(httpStatus(call("POST", url(jobId), token, question("")))).isEqualTo(400);
        assertThat(revision(token, jobId)).isEqualTo(5);
        assertThat(httpStatus(call("PUT", url(jobId) + "/" + UUID.randomUUID(), token, question("Cau X"))))
                .isEqualTo(404);
        assertThat(revision(token, jobId)).as("404 sau cau UPDATE phai rollback ca lan tang").isEqualTo(5);
        String badOrder = JSON.writeValueAsString(Map.of("questionIds", List.of(a, UUID.randomUUID().toString())));
        assertThat(httpStatus(call("PUT", url(jobId) + "/order", token, badOrder))).isEqualTo(400);
        assertThat(revision(token, jobId)).as("400 sau cau UPDATE phai rollback ca lan tang").isEqualTo(5);
        assertThat(httpStatus(call("POST", url(jobId), otherToken, question("Cau Y")))).isEqualTo(403);
        assertThat(revision(token, jobId)).isEqualTo(5);

        // Doc nhieu lan -> khong doi.
        for (int i = 0; i < 3; i++) {
            assertThat(revision(token, jobId)).isEqualTo(5);
        }
    }
}
