package com.recruitment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-C06 dot 3 (muc 12 L1) - T16 (hop thu), T25 (tin khong con mo), phan M5 cua T4. KHONG @Transactional cap
// lop (CLAUDE.md muc 3c): hop thu sap theo thoi diem tin moi nhat - boc ca lop thi moi tin cung now() va thu
// tu khong xac dinh. Moi test tu tao HR/ung vien/cong ty rieng (email duy nhat) nen du lieu cac test khong dung
// nhau du khong rollback.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationInboxIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String HR_INBOX = "/api/hr/messages/conversations";
    private static final String CANDIDATE_INBOX = "/api/candidates/messages/conversations";
    private static final byte[] PDF = Arrays.copyOf(new byte[] {0x25, 0x50, 0x44, 0x46}, 40);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ApplicationMessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

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

    private String registerAndLoginHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Nha Tuyen Dung Test","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc
                .perform(post("/api/auth/register/hr").contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated());
        return login(email);
    }

    private record Candidate(String token, UUID userId, String fullName) {}

    private Candidate registerCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String fullName = uniqueName("Ung Vien");
        String registerBody = """
                {"email":"%s","password":"password123","fullName":"%s"}
                """.formatted(email, fullName);
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());
        return new Candidate(login(email), userRepository.findByEmail(email).orElseThrow().getId(), fullName);
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

    private record Company(String hrToken, String name) {}

    private Company createCompanyWithHr(String prefix) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        String name = uniqueName("Cong ty " + prefix);
        createCompany(hrToken, name);
        return new Company(hrToken, name);
    }

    private record OpenJob(String id, String title) {}

    private OpenJob createOpenJob(String hrToken, String prefix) throws Exception {
        String title = uniqueName("Job " + prefix);
        String jobId = createJob(hrToken, title);
        addCriterion(hrToken, jobId);
        openJob(hrToken, jobId);
        return new OpenJob(jobId, title);
    }

    private static String hrPath(String applicationId) {
        return "/api/hr/applications/" + applicationId + "/messages";
    }

    private static String candidatePath(String applicationId) {
        return "/api/candidates/applications/" + applicationId + "/messages";
    }

    private void sendText(String token, String path, String text) throws Exception {
        mockMvc.perform(multipart(path).param("body", text).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void sendFileOnly(String token, String path) throws Exception {
        mockMvc.perform(multipart(path)
                        .file(new MockMultipartFile("file", "bang-cap.pdf", "application/pdf", PDF))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private JsonNode inbox(String token, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return body(result);
    }

    private List<String> applicationIds(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("applicationId").asString()));
        return ids;
    }

    private JsonNode item(JsonNode page, String applicationId) {
        for (JsonNode item : page.get("items")) {
            if (item.get("applicationId").asString().equals(applicationId)) {
                return item;
            }
        }
        throw new AssertionError("Khong co don " + applicationId + " trong hop thu");
    }

    private void setStatus(String applicationId, ApplicationStatus status) {
        JobApplication application =
                jobApplicationRepository.findById(UUID.fromString(applicationId)).orElseThrow();
        application.setStatus(status);
        jobApplicationRepository.saveAndFlush(application);
    }

    // ---- T16 - hop thu ----

    @Test
    void hrInbox_listsOnlyOwnApplicationsWithMessages_newestFirst_withCorrectFields() throws Exception {
        Company company = createCompanyWithHr("c06-t16-hr");
        OpenJob job = createOpenJob(company.hrToken(), "c06-t16-hr");
        Candidate silent = registerCandidate("c06-t16-silent");
        Candidate first = registerCandidate("c06-t16-first");
        Candidate second = registerCandidate("c06-t16-second");
        apply(silent.token(), job.id(), uploadResume(silent.token()));
        String appFirst = apply(first.token(), job.id(), uploadResume(first.token()));
        String appSecond = apply(second.token(), job.id(), uploadResume(second.token()));

        // Don cua cong ty khac co tin - HR nay KHONG duoc thay.
        Company otherCompany = createCompanyWithHr("c06-t16-oth");
        OpenJob otherJob = createOpenJob(otherCompany.hrToken(), "c06-t16-oth");
        String appOther = apply(first.token(), otherJob.id(), uploadResume(first.token()));
        sendText(first.token(), candidatePath(appOther), "Tin gui cong ty khac");

        String longText = "Em xin gửi thêm thông tin về kinh nghiệm   làm việc\n\n" + "x".repeat(150);
        sendText(first.token(), candidatePath(appFirst), "Tin 1 của ứng viên");
        sendText(first.token(), candidatePath(appFirst), longText);
        sendText(company.hrToken(), hrPath(appSecond), "HR nhắn trước");
        sendFileOnly(company.hrToken(), hrPath(appSecond));

        JsonNode page = inbox(company.hrToken(), HR_INBOX);
        assertThat(applicationIds(page)).containsExactly(appSecond, appFirst);
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);

        JsonNode rowFirst = item(page, appFirst);
        assertThat(rowFirst.get("candidateName").asString()).isEqualTo(first.fullName());
        assertThat(rowFirst.get("jobId").asString()).isEqualTo(job.id());
        assertThat(rowFirst.get("jobTitle").asString()).isEqualTo(job.title());
        assertThat(rowFirst.get("applicationStatus").asString()).isEqualTo("PENDING");
        assertThat(rowFirst.get("unreadCount").asInt()).isEqualTo(2);
        assertThat(rowFirst.get("lastMessageMine").asBoolean()).isFalse();
        assertThat(rowFirst.get("lastMessageHasAttachment").asBoolean()).isFalse();
        assertThat(rowFirst.get("lastMessageExcerpt").asString()).isEqualTo(MessageExcerpt.of(longText));
        assertThat(rowFirst.get("lastMessageExcerpt").asString()).endsWith("…").doesNotContain("\n");

        JsonNode rowSecond = item(page, appSecond);
        assertThat(rowSecond.get("unreadCount").asInt()).isZero();
        assertThat(rowSecond.get("lastMessageMine").asBoolean()).isTrue();
        assertThat(rowSecond.get("lastMessageHasAttachment").asBoolean()).isTrue();
        assertThat(rowSecond.get("lastMessageExcerpt").isNull()).isTrue();
    }

    @Test
    void candidateInbox_listsOnlyOwnApplications_withCompanyName() throws Exception {
        Company companyA = createCompanyWithHr("c06-t16-ca");
        Company companyB = createCompanyWithHr("c06-t16-cb");
        Candidate me = registerCandidate("c06-t16-me");
        Candidate someoneElse = registerCandidate("c06-t16-else");
        String resume = uploadResume(me.token());
        String appA = apply(me.token(), createOpenJob(companyA.hrToken(), "c06-t16-ca").id(), resume);
        String appB = apply(me.token(), createOpenJob(companyB.hrToken(), "c06-t16-cb").id(), resume);
        String appElse = apply(someoneElse.token(), createOpenJob(companyA.hrToken(), "c06-t16-ca2").id(),
                uploadResume(someoneElse.token()));
        sendText(someoneElse.token(), candidatePath(appElse), "Tin cua ung vien khac");

        sendText(companyA.hrToken(), hrPath(appA), "Công ty A nhắn");
        sendText(me.token(), candidatePath(appB), "Tôi nhắn công ty B");

        JsonNode page = inbox(me.token(), CANDIDATE_INBOX);
        assertThat(applicationIds(page)).containsExactly(appB, appA);
        assertThat(item(page, appA).get("companyName").asString()).isEqualTo(companyA.name());
        assertThat(item(page, appA).get("unreadCount").asInt()).isEqualTo(1);
        assertThat(item(page, appA).get("lastMessageMine").asBoolean()).isFalse();
        assertThat(item(page, appB).get("companyName").asString()).isEqualTo(companyB.name());
        assertThat(item(page, appB).get("lastMessageMine").asBoolean()).isTrue();
        assertThat(item(page, appB).get("unreadCount").asInt()).isZero();
    }

    // R-I4 - GET hop thu KHONG danh dau da doc; chi M3 lam viec do.
    @Test
    void readingInboxDoesNotMarkMessagesRead() throws Exception {
        Company company = createCompanyWithHr("c06-t16-ro");
        Candidate candidate = registerCandidate("c06-t16-ro");
        String app = apply(candidate.token(), createOpenJob(company.hrToken(), "c06-t16-ro").id(),
                uploadResume(candidate.token()));
        sendText(company.hrToken(), hrPath(app), "Tin chưa đọc");

        assertThat(item(inbox(candidate.token(), CANDIDATE_INBOX), app).get("unreadCount").asInt()).isEqualTo(1);
        assertThat(item(inbox(candidate.token(), CANDIDATE_INBOX), app).get("unreadCount").asInt()).isEqualTo(1);

        mockMvc.perform(patch(candidatePath(app) + "/read").header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNoContent());
        assertThat(item(inbox(candidate.token(), CANDIDATE_INBOX), app).get("unreadCount").asInt()).isZero();
    }

    // Muc 12 L6 - trung thoi diem tin moi nhat -> applicationId giam dan.
    @Test
    void sameLastMessageTime_isOrderedByApplicationIdDescending() throws Exception {
        Company company = createCompanyWithHr("c06-t16-tie");
        OpenJob job = createOpenJob(company.hrToken(), "c06-t16-tie");
        Candidate a = registerCandidate("c06-t16-tie-a");
        Candidate b = registerCandidate("c06-t16-tie-b");
        String appA = apply(a.token(), job.id(), uploadResume(a.token()));
        String appB = apply(b.token(), job.id(), uploadResume(b.token()));

        // saveAll trong MOT transaction -> hai tin cung created_at (now() theo transaction).
        ApplicationMessage toA = new ApplicationMessage();
        toA.setApplicationId(UUID.fromString(appA));
        toA.setSenderId(a.userId());
        toA.setSenderRole(MessageSenderRole.CANDIDATE);
        toA.setBody("Tin A");
        ApplicationMessage toB = new ApplicationMessage();
        toB.setApplicationId(UUID.fromString(appB));
        toB.setSenderId(b.userId());
        toB.setSenderRole(MessageSenderRole.CANDIDATE);
        toB.setBody("Tin B");
        messageRepository.saveAll(List.of(toA, toB));

        JsonNode page = inbox(company.hrToken(), HR_INBOX);
        assertThat(item(page, appA).get("lastMessageAt").asString())
                .isEqualTo(item(page, appB).get("lastMessageAt").asString());
        // KHONG dung UUID.compareTo cua Java (so sanh co dau theo long); Postgres so sanh uuid theo byte khong dau
        // = thu tu chuoi hex chu thuong, nen sap theo chuoi de khop ORDER BY a.id DESC.
        List<String> expected = new ArrayList<>(List.of(appA, appB));
        expected.sort((x, y) -> y.compareTo(x));
        assertThat(applicationIds(page)).containsExactlyElementsOf(expected);
    }

    @Test
    void withdrawnApplicationWithMessages_staysInInbox() throws Exception {
        Company company = createCompanyWithHr("c06-t16-wd");
        Candidate candidate = registerCandidate("c06-t16-wd");
        String app = apply(candidate.token(), createOpenJob(company.hrToken(), "c06-t16-wd").id(),
                uploadResume(candidate.token()));
        sendText(candidate.token(), candidatePath(app), "Tin trước khi rút");
        setStatus(app, ApplicationStatus.WITHDRAWN);

        assertThat(item(inbox(company.hrToken(), HR_INBOX), app).get("applicationStatus").asString())
                .isEqualTo("WITHDRAWN");
        assertThat(item(inbox(candidate.token(), CANDIDATE_INBOX), app).get("applicationStatus").asString())
                .isEqualTo("WITHDRAWN");
    }

    // R-I1 - size toi da 50: 51 -> 50; mac dinh 20.
    @Test
    void pageSizeIsCappedAt50_andDefaultsTo20() throws Exception {
        String hrToken = createCompanyWithHr("c06-t16-size").hrToken();

        MvcResult capped = mockMvc
                .perform(get(HR_INBOX).param("size", "51").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(capped).get("size").asInt()).isEqualTo(50);

        MvcResult atMax = mockMvc
                .perform(get(HR_INBOX).param("size", "50").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(atMax).get("size").asInt()).isEqualTo(50);

        MvcResult belowMax = mockMvc
                .perform(get(HR_INBOX).param("size", "49").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(belowMax).get("size").asInt()).isEqualTo(49);

        assertThat(inbox(hrToken, HR_INBOX).get("size").asInt()).isEqualTo(20);
        assertThat(inbox(hrToken, HR_INBOX).get("items")).isEmpty();
    }

    // ---- T25 - tin tuyen dung khong con mo (R-Q5) ----

    @Test
    void closedAndSoftDeletedJobs_stillAllowReadingSendingAndListing() throws Exception {
        Company company = createCompanyWithHr("c06-t25");
        Candidate candidate = registerCandidate("c06-t25");
        String resume = uploadResume(candidate.token());
        OpenJob closedJob = createOpenJob(company.hrToken(), "c06-t25-closed");
        OpenJob deletedJob = createOpenJob(company.hrToken(), "c06-t25-deleted");
        String appClosed = apply(candidate.token(), closedJob.id(), resume);
        String appDeleted = apply(candidate.token(), deletedJob.id(), resume);

        Job closed = jobRepository.findById(UUID.fromString(closedJob.id())).orElseThrow();
        closed.setStatus(JobStatus.CLOSED);
        jobRepository.saveAndFlush(closed);
        Job deleted = jobRepository.findById(UUID.fromString(deletedJob.id())).orElseThrow();
        deleted.setDeletedAt(Instant.now());
        jobRepository.saveAndFlush(deleted);

        for (String app : List.of(appClosed, appDeleted)) {
            sendText(company.hrToken(), hrPath(app), "HR nhắn");
            sendText(candidate.token(), candidatePath(app), "Ứng viên trả lời");
            mockMvc.perform(get(hrPath(app)).header("Authorization", "Bearer " + company.hrToken()))
                    .andExpect(status().isOk());
            mockMvc.perform(get(candidatePath(app)).header("Authorization", "Bearer " + candidate.token()))
                    .andExpect(status().isOk());
        }

        assertThat(applicationIds(inbox(company.hrToken(), HR_INBOX))).contains(appClosed, appDeleted);
        assertThat(applicationIds(inbox(candidate.token(), CANDIDATE_INBOX))).contains(appClosed, appDeleted);
    }

    // ---- T4 (phan M5) + R-Q6 ----

    @Test
    void inboxRejectsWrongRoleAndMissingToken_andHrWithoutCompanyIsNotFound() throws Exception {
        String hrToken = createCompanyWithHr("c06-t4-m5").hrToken();
        Candidate candidate = registerCandidate("c06-t4-m5");

        assertThat(mockMvc.perform(get(HR_INBOX).header("Authorization", "Bearer " + candidate.token()))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(get(CANDIDATE_INBOX).header("Authorization", "Bearer " + hrToken))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(403);
        for (String path : List.of(HR_INBOX, CANDIDATE_INBOX)) {
            MvcResult noToken = mockMvc.perform(get(path)).andReturn();
            assertThat(noToken.getResponse().getStatus()).isEqualTo(401);
            assertThat(body(noToken).get("error").asString()).isEqualTo("UNAUTHENTICATED");
        }

        String hrWithoutCompany = registerAndLoginHr("c06-t4-m5-nocomp");
        MvcResult noCompany = mockMvc.perform(get(HR_INBOX).header("Authorization", "Bearer " + hrWithoutCompany))
                .andReturn();
        assertThat(noCompany.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(noCompany).get("error").asString()).isEqualTo("COMPANY_NOT_FOUND");
    }
}
