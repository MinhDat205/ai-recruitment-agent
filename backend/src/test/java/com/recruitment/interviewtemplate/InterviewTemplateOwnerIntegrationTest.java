package com.recruitment.interviewtemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

// Nhung test o day KHONG chi de chung minh code hom nay dung - chung la hang rao chan cho nhanh
// E1 (FR-H07) sau nay: "o trong" trong FR-H02 nghia la KHONG TON TAI field ngay gio o tang
// template (khac voi field nullable roi bo trong). Neu ai o E1 lo them mot field kieu
// Instant/LocalDate(Time) vao InterviewTemplate de "tien" luu lich hen ngay tai day, test
// reflection ben duoi se do ngay lap tuc thay vi de loi am tham lot qua review.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InterviewTemplateOwnerIntegrationTest {

    private static final Set<String> ALLOWED_TEMPORAL_FIELDS = Set.of("createdAt", "updatedAt");

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
        MvcResult loginResult =
                mockMvc
                        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                        .andExpect(status().isOk())
                        .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    private void createCompany(String token, String name) throws Exception {
        String body = """
                {"name":"%s"}
                """.formatted(name);
        mockMvc
                .perform(
                        post("/api/hr/companies")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated());
    }

    private String createJob(String token, String title) throws Exception {
        String body =
                """
                {
                  "job": {"title":"%s","description":"Mo ta cong viec"},
                  "interviewTemplate": {
                    "subject":"Thu moi phong van vi tri %s",
                    "body":"Kinh chao ung vien, chung toi moi ban tham gia phong van.",
                    "senderName":"Phong Nhan Su"
                  }
                }
                """
                        .formatted(title, title);
        MvcResult result = mockMvc
                .perform(
                        post("/api/hr/jobs")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    @Test
    void entity_hasNoDateTimeFieldOtherThanAuditTimestamps() {
        for (Field field : InterviewTemplate.class.getDeclaredFields()) {
            if (ALLOWED_TEMPORAL_FIELDS.contains(field.getName())) {
                continue;
            }
            Class<?> type = field.getType();
            boolean isTemporal = type == Instant.class
                    || type == LocalDate.class
                    || type == LocalDateTime.class
                    || type == LocalTime.class
                    || type == OffsetDateTime.class
                    || type == ZonedDateTime.class
                    || type == Date.class;
            assertThat(isTemporal)
                    .as(
                            "InterviewTemplate.%s la kieu thoi gian (%s) nhung khong nam trong danh sach cho phep"
                                    + " (%s). FR-H02 quy dinh khong co field ngay gio phong van o tang template -"
                                    + " neu day la lich hen cu the, no thuoc ve interview_invitations (E1), khong"
                                    + " phai interview_templates.",
                            field.getName(), type.getSimpleName(), ALLOWED_TEMPORAL_FIELDS)
                    .isFalse();
        }
    }

    @Test
    void getTemplate_responseNeverLeaksScheduleField() throws Exception {
        String token = registerAndLoginHr("hr-template-shape");
        createCompany(token, "Cong ty Template Shape");
        String jobId = createJob(token, "Job Template Shape");

        MvcResult result = mockMvc
                .perform(get("/api/hr/jobs/" + jobId + "/interview-template")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String bodyLower = result.getResponse().getContentAsString().toLowerCase();

        assertThat(bodyLower).doesNotContain("scheduled").doesNotContain("datetime").doesNotContain("interviewat");
    }

    @Test
    void hrA_updateHrBInterviewTemplate_returnsForbidden() throws Exception {
        String tokenA = registerAndLoginHr("hr-tpl-a");
        createCompany(tokenA, "Cong ty Template A");
        String jobIdA = createJob(tokenA, "Job Template A");

        String tokenB = registerAndLoginHr("hr-tpl-b");
        createCompany(tokenB, "Cong ty Template B");

        String updateBody =
                """
                {"subject":"Bi HR khac doi noi dung","body":"Noi dung moi","senderName":"Ke gia mao"}
                """;
        mockMvc
                .perform(
                        put("/api/hr/jobs/" + jobIdA + "/interview-template")
                                .header("Authorization", "Bearer " + tokenB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrA_getHrBInterviewTemplate_returnsForbidden() throws Exception {
        String tokenA = registerAndLoginHr("hr-tpl-get-a");
        createCompany(tokenA, "Cong ty Template Get A");
        String jobIdA = createJob(tokenA, "Job Template Get A");

        String tokenB = registerAndLoginHr("hr-tpl-get-b");
        createCompany(tokenB, "Cong ty Template Get B");

        mockMvc
                .perform(get("/api/hr/jobs/" + jobIdA + "/interview-template")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_changesContentAndPersists() throws Exception {
        String token = registerAndLoginHr("hr-tpl-update");
        createCompany(token, "Cong ty Template Update");
        String jobId = createJob(token, "Job Template Update");

        String updateBody =
                """
                {"subject":"Tieu de moi","body":"Noi dung moi","senderName":"Nguyen Van B","address":"Ha Noi"}
                """;
        mockMvc
                .perform(
                        put("/api/hr/jobs/" + jobId + "/interview-template")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                .andExpect(status().isOk());

        MvcResult result = mockMvc
                .perform(get("/api/hr/jobs/" + jobId + "/interview-template")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Tieu de moi").contains("Ha Noi");
    }
}
