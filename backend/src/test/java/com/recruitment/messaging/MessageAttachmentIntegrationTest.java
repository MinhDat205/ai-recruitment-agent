package com.recruitment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.storage.LocalStorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-C06 dot 3 (muc 12 L1) - T10-T14, phan tep cua T9, phan M4 cua T2/T3. Tep nhi phan dung bang mang byte
// trong test, khong them fixture. @Transactional cap lop: dong DB rollback rieng tung @Test; tep ghi vao kho
// luu tru test (app.storage.local-path cua application-test.yml) KHONG rollback - vo hai, ten tep la uuid.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MessageAttachmentIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MISSING_ID = "00000000-0000-0000-0000-000000000000";
    private static final int FIVE_MB = 5 * 1024 * 1024;

    private static final byte[] PDF = content(new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34});
    private static final byte[] DOCX = content(new byte[] {0x50, 0x4B, 0x03, 0x04});
    private static final byte[] PNG = content(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    private static final byte[] JPEG = content(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
    private static final byte[] WEBP =
            content(new byte[] {0x52, 0x49, 0x46, 0x46, 0x10, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50});

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private LocalStorageService localStorageService;

    @PersistenceContext
    private EntityManager entityManager;

    // Chu ky + vai chuc byte noi dung gia lap.
    private static byte[] content(byte[] signature) {
        byte[] result = Arrays.copyOf(signature, signature.length + 32);
        Arrays.fill(result, signature.length, result.length, (byte) 0x41);
        return result;
    }

    private static byte[] pdfOfSize(int size) {
        byte[] result = new byte[size];
        System.arraycopy(PDF, 0, result, 0, PDF.length);
        return result;
    }

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

    private String createOpenJob(String hrToken, String prefix) throws Exception {
        String jobId = createJob(hrToken, uniqueName("Job " + prefix));
        addCriterion(hrToken, jobId);
        openJob(hrToken, jobId);
        return jobId;
    }

    private record Fixture(String hrToken, String candidateToken, String resumeId, String applicationId) {

        String hrPath() {
            return hrPath(applicationId);
        }

        String candidatePath() {
            return candidatePath(applicationId);
        }

        static String hrPath(String applicationId) {
            return "/api/hr/applications/" + applicationId + "/messages";
        }

        static String candidatePath(String applicationId) {
            return "/api/candidates/applications/" + applicationId + "/messages";
        }
    }

    private Fixture createApplication(String prefix) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        createCompany(hrToken, uniqueName("Cong ty " + prefix));
        String jobId = createOpenJob(hrToken, prefix);

        String candidateToken = registerAndLoginCandidate(prefix + "-cand");
        String resumeId = uploadResume(candidateToken);
        String applicationId = apply(candidateToken, jobId, resumeId);
        return new Fixture(hrToken, candidateToken, resumeId, applicationId);
    }

    private MvcResult sendFile(String token, String path, String fileName, String contentType, byte[] bytes, String text)
            throws Exception {
        var request = multipart(path)
                .file(new MockMultipartFile("file", fileName, contentType, bytes))
                .header("Authorization", "Bearer " + token);
        if (text != null) {
            request = request.param("body", text);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult sendText(String token, String path, String text) throws Exception {
        return mockMvc.perform(multipart(path).param("body", text).header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private MvcResult download(String token, String path, String messageId) throws Exception {
        var request = get(path + "/" + messageId + "/attachment");
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private void assertError(MvcResult result, int httpStatus, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(httpStatus);
        assertThat(body(result).get("error").asString()).isEqualTo(code);
    }

    private void assertAttachmentError(MvcResult result, String message) throws Exception {
        assertError(result, 400, "INVALID_MESSAGE_ATTACHMENT");
        assertThat(body(result).get("message").asString()).isEqualTo(message);
    }

    private long countMessages(String applicationId) {
        return entityManager
                .createQuery(
                        "SELECT COUNT(m) FROM ApplicationMessage m WHERE m.applicationId = :id", Long.class)
                .setParameter("id", UUID.fromString(applicationId))
                .getSingleResult();
    }

    private String attachmentKey(String messageId) {
        return entityManager
                .createQuery("SELECT m.attachmentKey FROM ApplicationMessage m WHERE m.id = :id", String.class)
                .setParameter("id", UUID.fromString(messageId))
                .getSingleResult();
    }

    private long countStoredAttachments() throws IOException {
        Path dir = localStorageService.getBasePath().resolve(MessageService.ATTACHMENT_SUBDIRECTORY);
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }

    private void setStatus(String applicationId, ApplicationStatus status) {
        JobApplication application =
                jobApplicationRepository.findById(UUID.fromString(applicationId)).orElseThrow();
        application.setStatus(status);
        jobApplicationRepository.saveAndFlush(application);
    }

    // ---- T10 - 5 loai hop le, ten co dau tieng Viet ----

    @Test
    void acceptsAllFiveTypes_keepsVietnameseNameTypeAndSize() throws Exception {
        Fixture f = createApplication("c06-t10-ok");
        Object[][] cases = {
            {"báo cáo năng lực.pdf", "application/pdf", PDF, "PDF"},
            {"hồ sơ bổ sung.docx", "application/octet-stream", DOCX, "DOCX"},
            {"ảnh chân dung.png", "image/png", PNG, "PNG"},
            {"ảnh thẻ.jpg", "image/jpeg", JPEG, "JPEG"},
            {"bằng cấp.webp", "image/webp", WEBP, "WEBP"},
        };
        for (Object[] c : cases) {
            MvcResult result = sendFile(f.hrToken(), f.hrPath(), (String) c[0], (String) c[1], (byte[]) c[2], "Kèm tệp");
            assertThat(result.getResponse().getStatus()).as("tep %s", c[0]).isEqualTo(201);
            JsonNode attachment = body(result).get("attachment");
            assertThat(attachment.get("fileType").asString()).isEqualTo(c[3]);
            assertThat(attachment.get("fileName").asString()).isEqualTo(c[0]);
            assertThat(attachment.get("fileSize").asLong()).isEqualTo(((byte[]) c[2]).length);
        }
        assertThat(countMessages(f.applicationId())).isEqualTo(5);
    }

    @Test
    void fileWithoutText_isAccepted_withNullBody() throws Exception {
        Fixture f = createApplication("c06-t10-nobody");

        MvcResult result = sendFile(f.candidateToken(), f.candidatePath(), "bang-cap.pdf", "application/pdf", PDF, null);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result).get("body").isNull()).isTrue();
        assertThat(body(result).get("attachment").get("fileType").asString()).isEqualTo("PDF");

        // Chu chi gom khoang trang + tep -> body NULL (R-M2).
        MvcResult blank = sendFile(f.candidateToken(), f.candidatePath(), "anh.png", "image/png", PNG, "   \n ");
        assertThat(blank.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(blank).get("body").isNull()).isTrue();
    }

    // ---- T11 - loai CHI theo magic bytes; ep duoi ten (R-F6, R-F6b) ----

    @Test
    void rejectsUnknownContent_regardlessOfNameAndContentType() throws Exception {
        Fixture f = createApplication("c06-t11-bad");
        String invalidType = "Định dạng không hợp lệ, chỉ nhận PDF, DOCX, PNG, JPEG hoặc WEBP";

        assertAttachmentError(
                sendFile(f.hrToken(), f.hrPath(), "anh.gif", "image/gif", "GIF89a noi dung".getBytes(), null),
                invalidType);
        assertAttachmentError(
                sendFile(f.hrToken(), f.hrPath(), "ghi-chu.txt", "text/plain", "Van ban thuan".getBytes(), "Kèm"),
                invalidType);
        // Ten .pdf + Content-Type application/pdf nhung noi dung van ban -> van bi tu choi.
        assertAttachmentError(
                sendFile(f.hrToken(), f.hrPath(), "a.pdf", "application/pdf", "Khong phai PDF".getBytes(), null),
                invalidType);
        assertThat(countMessages(f.applicationId())).isZero();
    }

    @Test
    void displayNameExtensionIsForcedToDetectedType() throws Exception {
        Fixture f = createApplication("c06-t11-name");

        JsonNode pdfAsTxt = body(sendFile(f.hrToken(), f.hrPath(), "a.txt", "text/plain", PDF, null)).get("attachment");
        assertThat(pdfAsTxt.get("fileType").asString()).isEqualTo("PDF");
        assertThat(pdfAsTxt.get("fileName").asString()).isEqualTo("a.txt.pdf");

        JsonNode zipAsHtml = body(sendFile(f.hrToken(), f.hrPath(), "x.html", "text/html", DOCX, null)).get("attachment");
        assertThat(zipAsHtml.get("fileType").asString()).isEqualTo("DOCX");
        assertThat(zipAsHtml.get("fileName").asString()).isEqualTo("x.html.docx");

        JsonNode upperJpeg = body(sendFile(f.hrToken(), f.hrPath(), "anh.JPEG", "image/jpeg", JPEG, null)).get("attachment");
        assertThat(upperJpeg.get("fileType").asString()).isEqualTo("JPEG");
        assertThat(upperJpeg.get("fileName").asString()).isEqualTo("anh.JPEG");

        JsonNode noExtension = body(sendFile(f.hrToken(), f.hrPath(), "bangcap", "application/pdf", PDF, null))
                .get("attachment");
        assertThat(noExtension.get("fileName").asString()).isEqualTo("bangcap.pdf");
    }

    @Test
    void emptyFile_isRejected_evenWithText() throws Exception {
        Fixture f = createApplication("c06-t11-empty");

        assertAttachmentError(
                sendFile(f.hrToken(), f.hrPath(), "rong.pdf", "application/pdf", new byte[0], null),
                "Tệp đính kèm đang trống");
        assertAttachmentError(
                sendFile(f.hrToken(), f.hrPath(), "rong.pdf", "application/pdf", new byte[0], "Có chữ"),
                "Tệp đính kèm đang trống");
        assertThat(countMessages(f.applicationId())).isZero();
    }

    // ---- T12 - nguong 5MB ----

    @Test
    void sizeBoundary_5MbMinus1And5MbAccepted_5MbPlus1Rejected() throws Exception {
        Fixture f = createApplication("c06-t12-size");

        assertThat(sendFile(f.hrToken(), f.hrPath(), "a.pdf", "application/pdf", pdfOfSize(FIVE_MB - 1), null)
                        .getResponse().getStatus())
                .isEqualTo(201);
        assertThat(sendFile(f.hrToken(), f.hrPath(), "b.pdf", "application/pdf", pdfOfSize(FIVE_MB), null)
                        .getResponse().getStatus())
                .isEqualTo(201);
        assertAttachmentError(
                sendFile(f.hrToken(), f.hrPath(), "c.pdf", "application/pdf", pdfOfSize(FIVE_MB + 1), null),
                "Tệp đính kèm vượt quá 5MB");
        assertThat(countMessages(f.applicationId())).isEqualTo(2);
    }

    // Muc 12 L4 - qua 5MB duoc bao TRUOC sai loai.
    @Test
    void oversizedFileOfWrongType_reportsSizeFirst() throws Exception {
        Fixture f = createApplication("c06-t12-order");
        assertAttachmentError(
                sendFile(f.hrToken(), f.hrPath(), "big.txt", "text/plain", new byte[FIVE_MB + 1], null),
                "Tệp đính kèm vượt quá 5MB");
    }

    // ---- T13 - tai tep (M4) ----

    @Test
    void senderAndRecipientDownloadSameBytes_asAttachmentWithDetectedContentType() throws Exception {
        Fixture f = createApplication("c06-t13-dl");
        MvcResult sent = sendFile(f.hrToken(), f.hrPath(), "ảnh thẻ.png", "application/octet-stream", PNG, null);
        String messageId = body(sent).get("id").asString();

        for (MvcResult result : new MvcResult[] {
            download(f.hrToken(), f.hrPath(), messageId), download(f.candidateToken(), f.candidatePath(), messageId)
        }) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PNG);
            assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
            assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                    .contains("UTF-8''%E1%BA%A3nh%20th%E1%BA%BB.png");
            assertThat(result.getResponse().getContentType()).isEqualTo("image/png");
        }
    }

    @Test
    void messageOfAnotherApplication_isMessageNotFound_evenIfCallerOwnsBoth() throws Exception {
        Fixture f = createApplication("c06-t13-other");
        String secondJob = createOpenJob(f.hrToken(), "c06-t13-other-2");
        String secondApplication = apply(f.candidateToken(), secondJob, f.resumeId());
        String messageInSecond = body(sendFile(
                        f.hrToken(), Fixture.hrPath(secondApplication), "a.pdf", "application/pdf", PDF, null))
                .get("id").asString();

        assertError(download(f.hrToken(), f.hrPath(), messageInSecond), 404, "MESSAGE_NOT_FOUND");
        assertError(download(f.candidateToken(), f.candidatePath(), messageInSecond), 404, "MESSAGE_NOT_FOUND");
        assertError(download(f.hrToken(), f.hrPath(), UUID.randomUUID().toString()), 404, "MESSAGE_NOT_FOUND");
    }

    @Test
    void messageWithoutFile_orFileMissingFromStorage_isAttachmentNotFound() throws Exception {
        Fixture f = createApplication("c06-t13-missing");
        String textOnly = body(sendText(f.hrToken(), f.hrPath(), "Chỉ có chữ")).get("id").asString();
        assertError(download(f.candidateToken(), f.candidatePath(), textOnly), 404, "MESSAGE_ATTACHMENT_NOT_FOUND");

        String withFile = body(sendFile(f.hrToken(), f.hrPath(), "a.pdf", "application/pdf", PDF, null))
                .get("id").asString();
        Files.delete(localStorageService.getBasePath().resolve(attachmentKey(withFile)));
        MvcResult afterDelete = download(f.candidateToken(), f.candidatePath(), withFile);
        assertError(afterDelete, 404, "MESSAGE_ATTACHMENT_NOT_FOUND");
        assertThat(body(afterDelete).get("message").asString()).isEqualTo("Không tìm thấy tệp đính kèm");
    }

    @Test
    void attachmentsAreNotServedAsStaticFiles() throws Exception {
        Fixture f = createApplication("c06-t13-static");
        String messageId = body(sendFile(f.hrToken(), f.hrPath(), "a.pdf", "application/pdf", PDF, null))
                .get("id").asString();
        String key = attachmentKey(messageId);

        MvcResult result = mockMvc
                .perform(get("/uploads/" + key).header("Authorization", "Bearer " + f.hrToken()))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(200);
    }

    // ---- T14 - ten tep khong lam duong dan luu ----

    @Test
    void pathPartsOfOriginalNameAreDropped_andStorageKeyIsUuidBased() throws Exception {
        Fixture f = createApplication("c06-t14-path");
        MvcResult result = sendFile(f.hrToken(), f.hrPath(), "..\\..\\x/../báo cáo.pdf", "application/pdf", PDF, null);

        String messageId = body(result).get("id").asString();
        assertThat(body(result).get("attachment").get("fileName").asString()).isEqualTo("báo cáo.pdf");
        String key = attachmentKey(messageId);
        assertThat(key).matches("message-attachments/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");
        assertThat(key).doesNotContain("báo").doesNotContain("..");
    }

    @Test
    void controlCharactersAreRemoved_emptyNameFallsBack_andLongNameIsCutTo255() throws Exception {
        Fixture f = createApplication("c06-t14-chars");

        JsonNode control = body(sendFile(f.hrToken(), f.hrPath(), "ba\u0007o\u0000 cao\u0085.pdf", "application/pdf", PDF, null))
                .get("attachment");
        assertThat(control.get("fileName").asString()).isEqualTo("bao cao.pdf");

        JsonNode onlyPath = body(sendFile(f.hrToken(), f.hrPath(), "thu-muc/", "application/pdf", PDF, null))
                .get("attachment");
        assertThat(onlyPath.get("fileName").asString()).isEqualTo("tep-dinh-kem.pdf");

        JsonNode longName = body(sendFile(f.hrToken(), f.hrPath(), "a".repeat(300) + ".pdf", "application/pdf", PDF, null))
                .get("attachment");
        assertThat(longName.get("fileName").asString()).hasSize(255).isEqualTo("a".repeat(251) + ".pdf");

        JsonNode longForced = body(sendFile(f.hrToken(), f.hrPath(), "b".repeat(300) + ".html", "text/html", PDF, null))
                .get("attachment");
        assertThat(longForced.get("fileName").asString()).hasSize(255).endsWith(".pdf");
    }

    // ---- T9 (phan tep) - don WITHDRAWN khong luu gi, ke ca tep ----

    @Test
    void withdrawnApplication_rejectsFile_andStoresNothing() throws Exception {
        Fixture f = createApplication("c06-t9-file");
        setStatus(f.applicationId(), ApplicationStatus.WITHDRAWN);
        long filesBefore = countStoredAttachments();

        assertError(sendFile(f.hrToken(), f.hrPath(), "a.pdf", "application/pdf", PDF, "Kèm"), 409, "CONVERSATION_READ_ONLY");
        assertError(sendFile(f.candidateToken(), f.candidatePath(), "a.pdf", "application/pdf", PDF, null),
                409, "CONVERSATION_READ_ONLY");

        assertThat(countMessages(f.applicationId())).isZero();
        assertThat(countStoredAttachments()).isEqualTo(filesBefore);
    }

    // ---- T2 / T3 (phan M4) ----

    @Test
    void hrSide_downloadIsForbiddenForOtherCompany_notFoundForMissingOrNoCompany() throws Exception {
        Fixture f = createApplication("c06-t2-m4");
        String messageId = body(sendFile(f.candidateToken(), f.candidatePath(), "a.pdf", "application/pdf", PDF, null))
                .get("id").asString();

        String otherHr = registerAndLoginHr("c06-t2-m4-other");
        createCompany(otherHr, uniqueName("Cong ty khac"));
        assertThat(download(otherHr, f.hrPath(), messageId).getResponse().getStatus()).isEqualTo(403);
        assertError(download(f.hrToken(), Fixture.hrPath(MISSING_ID), messageId), 404, "APPLICATION_NOT_FOUND");

        String hrWithoutCompany = registerAndLoginHr("c06-t2-m4-nocomp");
        assertError(download(hrWithoutCompany, f.hrPath(), messageId), 404, "COMPANY_NOT_FOUND");

        assertThat(download(f.candidateToken(), f.hrPath(), messageId).getResponse().getStatus()).isEqualTo(403);
        assertError(download(null, f.hrPath(), messageId), 401, "UNAUTHENTICATED");
    }

    @Test
    void candidateSide_downloadIsSameNotFoundForOthersAndMissingApplication() throws Exception {
        Fixture f = createApplication("c06-t3-m4");
        String messageId = body(sendFile(f.hrToken(), f.hrPath(), "a.pdf", "application/pdf", PDF, null))
                .get("id").asString();
        String otherCandidate = registerAndLoginCandidate("c06-t3-m4-other");

        assertError(download(otherCandidate, f.candidatePath(), messageId), 404, "APPLICATION_NOT_FOUND");
        assertError(download(otherCandidate, Fixture.candidatePath(MISSING_ID), messageId), 404, "APPLICATION_NOT_FOUND");
        assertThat(download(f.hrToken(), f.candidatePath(), messageId).getResponse().getStatus()).isEqualTo(403);
    }
}
