package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import tools.jackson.databind.json.JsonMapper;

// FR-U08 T9 (REQUIREMENT.md muc 7.1) - CUNG mot don cua ung vien khac qua moi endpoint ung vien tren don.
// Hai quy uoc kiem quyen CUNG TON TAI co chu dich, test nay khoa ca hai:
//  - E1 (moi), /history, /withdraw: 404 APPLICATION_NOT_FOUND (che giau su ton tai, R-Q2).
//  - /interview-invitation: 403 - quy uoc CU co chu dich (InterviewInvitationService.getLatestInvitationForCandidate,
//    walkthrough candidate-view-invitation muc 4a), KHONG phai loi. Khong "sua cho dong bo" (R-Q3).
// Lop tu tao du lieu cua minh, @Transactional rollback moi test.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CandidateApplicationCrossEndpointAccessIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    // Prefix <= 27 ky tu ke ca hau to helper (xem uniqueEmail o ApplicationHrDetailControllerIntegrationTest).
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

    private String errorCode(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("error")
                .asString();
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "accessToken");
    }

    private String registerAndLoginHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc
                .perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Nha Tuyen Dung Test","phone":"0900000000"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String registerAndLoginCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Ung Vien Test"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String createOpenJob(String hrToken) throws Exception {
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cong ty %s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());
        MvcResult jobResult = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "job": {"title":"Job cheo","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                                  "interviewTemplate": {
                                    "subject":"Thu moi phong van",
                                    "body":"Kinh chao ung vien, chung toi moi ban tham gia phong van.",
                                    "senderName":"Phong Nhan Su"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = extractJsonField(jobResult.getResponse().getContentAsString(), "id");
        mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tieu chi","weight":100}
                                """))
                .andExpect(status().isCreated());
        mockMvc
                .perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OPEN"}
                                """))
                .andExpect(status().isOk());
        return jobId;
    }

    private String applyWithNewResume(String candidateToken, String jobId) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes());
        MvcResult resumeResult = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        String resumeId = extractJsonField(resumeResult.getResponse().getContentAsString(), "id");
        MvcResult applyResult = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":"%s","resumeId":"%s","aiConsent":true}
                                """.formatted(jobId, resumeId)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(applyResult.getResponse().getContentAsString(), "id");
    }

    private void sendInterviewInvitation(String hrToken, String applicationId) throws Exception {
        mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/interview-invitation")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"scheduledAt":"%s","location":"Van phong cong ty","subject":"Thu moi phong van","content":"Xin chao, moi ban tham gia phong van."}
                                """
                                        .formatted(Instant.now().plus(7, ChronoUnit.DAYS).toString())))
                .andExpect(status().isCreated());
    }

    // T9 - don cua ung vien khac (da duoc moi phong van: co giay moi, con rut duoc): E1/history/withdraw -> 404,
    // trang thai don khong doi; interview-invitation -> 403 (quy uoc cu).
    @Test
    void otherCandidate_sameApplicationAcrossEndpoints_404ExceptInvitation403() throws Exception {
        String hrToken = registerAndLoginHr("u08x-hr");
        String jobId = createOpenJob(hrToken);
        String ownerToken = registerAndLoginCandidate("u08x-owner");
        String applicationId = applyWithNewResume(ownerToken, jobId);
        sendInterviewInvitation(hrToken, applicationId);
        String otherToken = registerAndLoginCandidate("u08x-other");
        String base = "/api/candidates/applications/" + applicationId;

        MvcResult detail = mockMvc
                .perform(get(base).header("Authorization", "Bearer " + otherToken))
                .andReturn();
        MvcResult history = mockMvc
                .perform(get(base + "/history").header("Authorization", "Bearer " + otherToken))
                .andReturn();
        MvcResult withdraw = mockMvc
                .perform(patch(base + "/withdraw").header("Authorization", "Bearer " + otherToken))
                .andReturn();
        MvcResult invitation = mockMvc
                .perform(get(base + "/interview-invitation").header("Authorization", "Bearer " + otherToken))
                .andReturn();

        assertThat(detail.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(detail)).isEqualTo("APPLICATION_NOT_FOUND");
        assertThat(history.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(history)).isEqualTo("APPLICATION_NOT_FOUND");
        assertThat(withdraw.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(withdraw)).isEqualTo("APPLICATION_NOT_FOUND");
        assertThat(jobApplicationRepository.findById(UUID.fromString(applicationId)).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.INTERVIEW_INVITED);
        // Quy uoc cu co chu dich - xem comment dau lop.
        assertThat(invitation.getResponse().getStatus()).isEqualTo(403);

        // Doi chung: chinh chu don doc duoc 3 endpoint doc (khong phai 404/403 vi du lieu sai).
        mockMvc.perform(get(base).header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());
        mockMvc.perform(get(base + "/history").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(base + "/interview-invitation").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }
}
