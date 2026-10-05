package com.recruitment.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.resume.ResumeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
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
import org.springframework.transaction.annotation.Transactional;

// FR-U14 dot 4. Mau ApplicationSearchControllerIntegrationTest (dang nhap that qua /api/auth, MockMvc
// that, khong mock lop service/controller) - tien le chung cua toan bo test file trong du an nay.
// @Transactional cap class: moi @Test rollback rieng, cac MockMvc call trong CUNG mot test tham gia
// CUNG mot transaction (dung thread) nen HR/ung vien thay du lieu cua nhau binh thuong trong pham vi
// mot test. KHONG dung cho test can phan biet THOI DIEM thuc giua nhieu ban ghi (CLAUDE.md muc 3c) -
// o day khong test nao so sanh updated_at/created_at giua cac ban ghi nen an toan.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CandidateProfileControllerIntegrationTest {

    private static final String PROFILE_ME = "/api/candidates/profile/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @PersistenceContext
    private EntityManager entityManager;

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

    private record CandidateSession(String token, UUID candidateId) {
    }

    private CandidateSession registerAndLoginCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Ung Vien Test","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());
        String token = login(email);
        UUID candidateId = userRepository.findByEmail(email).orElseThrow().getId();
        return new CandidateSession(token, candidateId);
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

    private String createOpenJob(String hrToken, String title) throws Exception {
        String jobId = createJob(hrToken, title);
        addCriterion(hrToken, jobId);
        openJob(hrToken, jobId);
        return jobId;
    }

    private String uploadResume(String candidateToken) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes());
        MvcResult result = mockMvc
                .perform(multipart("/api/candidates/resumes")
                        .file(file)
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private void apply(String candidateToken, String jobId, String resumeId) throws Exception {
        String body =
                """
                {"jobId":"%s","resumeId":"%s","aiConsent":true,"coverLetter":"Toi rat quan tam vi tri nay"}
                """
                        .formatted(jobId, resumeId);
        mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private void markResumeParsedDone(UUID resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setParseStatus(ParseStatus.DONE);
        resumeRepository.save(resume);
    }

    private MvcResult putProfile(String token, String body) throws Exception {
        return mockMvc
                .perform(put(PROFILE_ME)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult getProfile(String token) throws Exception {
        return mockMvc.perform(get(PROFILE_ME).header("Authorization", "Bearer " + token)).andReturn();
    }

    private String fullProfileBody(
            String desiredIndustryCodesJson,
            String desiredLocationCodesJson,
            String desiredWorkModesJson,
            String skillsJson,
            String desiredSalaryMinMillions,
            String bio) {
        return """
                {
                  "headline":"Chuc danh mong muon",
                  "location":"Ha Noi",
                  "currentTitle":"Backend Developer",
                  "yearsExperience": 2.5,
                  "dateOfBirth": "1995-01-01",
                  "desiredIndustryCodes": %s,
                  "desiredLocationCodes": %s,
                  "desiredWorkModes": %s,
                  "skills": %s,
                  "desiredSalaryMinMillions": %s,
                  "bio": %s
                }
                """
                .formatted(
                        desiredIndustryCodesJson,
                        desiredLocationCodesJson,
                        desiredWorkModesJson,
                        skillsJson,
                        desiredSalaryMinMillions,
                        bio);
    }

    private UUID candidateProfileIdOf(UUID userId) {
        return candidateProfileRepository.findByUserId(userId).orElseThrow().getId();
    }

    private boolean hasEmbedding(UUID profileId) {
        Object result = entityManager
                .createNativeQuery("SELECT (embedding IS NOT NULL) FROM candidate_profiles WHERE id = :id")
                .setParameter("id", profileId)
                .getSingleResult();
        return (Boolean) result;
    }

    // Gia lap "scheduler da chay truoc do" de kiem R-E3 dat lai NULL (hoac khong) dung voi mong doi,
    // thay vi chi kiem tren trang thai NULL ban dau (chua chung minh duoc gi vi NULL->NULL luon dung).
    private void setFakeEmbedding(UUID profileId) {
        entityManager
                .createNativeQuery("UPDATE candidate_profiles SET embedding = CAST('[0.1" + repeatZeros(1535)
                        + "]' AS vector), embedding_model = 'text-embedding-3-small' WHERE id = :id")
                .setParameter("id", profileId)
                .executeUpdate();
    }

    private UUID createPrimaryResumeWithParsedData(UUID candidateId, String currentTitle, List<String> skills,
            Integer experienceMonths) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        Resume savedResume = resumeRepository.save(resume);

        ResumeParsedPayload payload =
                new ResumeParsedPayload(null, List.of(), List.of(), skills, List.of(), List.of(), currentTitle, null, null);
        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(savedResume.getId());
        data.setRawText("Noi dung CV test");
        data.setData(payload);
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion("resume-parse-v1");
        data.setExperienceMonths(experienceMonths);
        // chk_parsed_experience_state/chk_parsed_experience_months (V8) - hoac CA BON cot experience_*
        // deu NULL (chua tinh), hoac experience_computed_at + hai bo dem deu co kem experience_months
        // >= 1. Khong the chi set rieng experience_months ma de 3 cot con lai NULL.
        if (experienceMonths != null) {
            data.setExperienceComputedAt(Instant.now());
            data.setExperienceEntriesCounted(1);
            data.setExperienceEntriesSkipped(0);
        }
        resumeParsedDataRepository.saveAndFlush(data);
        return savedResume.getId();
    }

    // ---- Nhom 1: mang mong muon - dedupe truoc khi dem, bien 3/4, ma la ----

    @Test
    void update_threeDistinctIndustryCodes_returns200() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-ind3");
        String body = fullProfileBody(
                "[\"IT_SOFTWARE\",\"ACCOUNTING_AUDIT\",\"SALES\"]", "[]", "[]", "[]", "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void update_fourDistinctIndustryCodes_returns400InvalidProfileField() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-ind4");
        String body = fullProfileBody(
                "[\"IT_SOFTWARE\",\"ACCOUNTING_AUDIT\",\"SALES\",\"MARKETING_COMMUNICATIONS\"]",
                "[]", "[]", "[]", "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_PROFILE_FIELD\"");
    }

    @Test
    void update_fourRawIndustryCodesWithOneDuplicate_dedupedBeforeCount_returns200() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-ind-dedup");
        String body = fullProfileBody(
                "[\"IT_SOFTWARE\",\"IT_SOFTWARE\",\"SALES\",\"MARKETING_COMMUNICATIONS\"]",
                "[]", "[]", "[]", "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void update_unknownIndustryCode_returns400InvalidCatalogCode() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-ind-bad");
        String body = fullProfileBody("[\"KHONG_TON_TAI\"]", "[]", "[]", "[]", "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_CATALOG_CODE\"");
    }

    @Test
    void update_threeDistinctLocationCodes_returns200() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-loc3");
        String body = fullProfileBody(
                "[]", "[\"HA_NOI\",\"HO_CHI_MINH\",\"DA_NANG\"]", "[]", "[]", "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void update_fourDistinctLocationCodes_returns400InvalidProfileField() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-loc4");
        String body = fullProfileBody(
                "[]", "[\"HA_NOI\",\"HO_CHI_MINH\",\"DA_NANG\",\"HAI_PHONG\"]", "[]", "[]", "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_PROFILE_FIELD\"");
    }

    // ---- Nhom 2: hinh thuc lam viec ----

    @Test
    void update_validWorkModes_returns200() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-wm-ok");
        String body = fullProfileBody("[]", "[]", "[\"ONSITE\",\"REMOTE\"]", "[]", "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void update_invalidWorkMode_returns400InvalidProfileField() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-wm-bad");
        String body = fullProfileBody("[]", "[]", "[\"FULL_REMOTE\"]", "[]", "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_PROFILE_FIELD\"");
    }

    // ---- Nhom 3: ky nang - bien 20/21, 50/51, rong sau trim, dedupe khong phan biet hoa/thuong ----

    private static String skillsArrayOf(int count, String prefix) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(prefix).append(i).append('"');
        }
        return sb.append(']').toString();
    }

    @Test
    void update_twentySkills_returns200() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-skill20");
        String body = fullProfileBody("[]", "[]", "[]", skillsArrayOf(20, "Skill"), "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void update_twentyOneSkills_returns400InvalidProfileField() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-skill21");
        String body = fullProfileBody("[]", "[]", "[]", skillsArrayOf(21, "Skill"), "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_PROFILE_FIELD\"");
    }

    @Test
    void update_skillExactly50Chars_returns200() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-skill50");
        String skill = "A".repeat(50);
        String body = fullProfileBody("[]", "[]", "[]", "[\"" + skill + "\"]", "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void update_skill51Chars_returns400InvalidProfileField() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-skill51");
        String skill = "A".repeat(51);
        String body = fullProfileBody("[]", "[]", "[]", "[\"" + skill + "\"]", "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_PROFILE_FIELD\"");
    }

    @Test
    void update_skillEmptyAfterTrim_returns400InvalidProfileField() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-skill-empty");
        String body = fullProfileBody("[]", "[]", "[]", "[\"   \"]", "null", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_PROFILE_FIELD\"");
    }

    @Test
    void update_skillsCaseInsensitiveDuplicate_keepsFirstCasingAndOrder() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-skill-case");
        String body = fullProfileBody("[]", "[]", "[]", "[\"Java\",\"SQL\",\"java\"]", "null", "null");

        putProfile(session.token(), body);
        MvcResult result = getProfile(session.token());

        assertThat(result.getResponse().getContentAsString()).contains("\"skills\":[\"Java\",\"SQL\"]");
    }

    // ---- Nhom 4: luong mong muon - 0, 1000, -1, 1001, de trong ----

    @Test
    void update_salaryZero_returns200() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-sal0");
        String body = fullProfileBody("[]", "[]", "[]", "[]", "0", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void update_salaryOneThousand_returns200() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-sal1000");
        String body = fullProfileBody("[]", "[]", "[]", "[]", "1000", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void update_salaryNegativeOne_returns400FieldError() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-sal-neg");
        String body = fullProfileBody("[]", "[]", "[]", "[]", "-1", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"desiredSalaryMinMillions\"");
    }

    @Test
    void update_salaryOneThousandOne_returns400FieldError() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-sal-over");
        String body = fullProfileBody("[]", "[]", "[]", "[]", "1001", "null");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"desiredSalaryMinMillions\"");
    }

    @Test
    void update_salaryOmitted_staysNull() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-sal-null");
        String body = fullProfileBody("[]", "[]", "[]", "[]", "null", "null");

        putProfile(session.token(), body);
        MvcResult result = getProfile(session.token());

        assertThat(result.getResponse().getContentAsString()).contains("\"desiredSalaryMinMillions\":null");
    }

    // ---- Nhom 5: gioi thieu ngan - 500/501 ----

    @Test
    void update_bio500Chars_returns200() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-bio500");
        String bio = "A".repeat(500);
        String body = fullProfileBody("[]", "[]", "[]", "[]", "null", "\"" + bio + "\"");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void update_bio501Chars_returns400FieldError() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-bio501");
        String bio = "A".repeat(501);
        String body = fullProfileBody("[]", "[]", "[]", "[]", "null", "\"" + bio + "\"");

        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"bio\"");
    }

    // ---- Nhom 6: truong dung lai (R-G) ----

    @Test
    void update_onlyHeadline_doesNotTouchDesiredFields() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-headline-only");
        String body = """
                {"headline":"Chuc danh mong muon"}
                """;

        MvcResult putResult = putProfile(session.token(), body);
        assertThat(putResult.getResponse().getStatus()).isEqualTo(200);

        MvcResult getResult = getProfile(session.token());
        String json = getResult.getResponse().getContentAsString();
        assertThat(json).contains("\"headline\":\"Chuc danh mong muon\"");
        assertThat(json).contains("\"desiredIndustries\":[]");
        assertThat(json).contains("\"skills\":[]");
    }

    // ---- Nhom 7: onboarding ----

    @Test
    void register_newProfile_onboardingCompletedAtIsNull() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-onb-new");

        MvcResult result = getProfile(session.token());

        assertThat(result.getResponse().getContentAsString()).contains("\"onboardingCompletedAt\":null");
    }

    @Test
    void update_firstSuccess_setsOnboardingFlag_secondUpdateDoesNotChangeIt() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-onb-flow");
        String body = fullProfileBody("[]", "[]", "[]", "[]", "null", "null");

        MvcResult firstResult = putProfile(session.token(), body);
        String firstJson = firstResult.getResponse().getContentAsString();
        assertThat(firstJson).doesNotContain("\"onboardingCompletedAt\":null");
        String firstValue = extractJsonField(firstJson, "onboardingCompletedAt");

        MvcResult secondResult = putProfile(session.token(), body);
        String secondValue = extractJsonField(secondResult.getResponse().getContentAsString(), "onboardingCompletedAt");

        // So khop CHINH XAC: CandidateProfileService.now() da cat ve micro giay ngay luc ghi
        // (clock.instant().truncatedTo(ChronoUnit.MICROS)) nen gia tri API tra ve luc nao cung bang
        // dung gia tri Postgres da luu, khong con lech do chinh xac giua hai lan doc.
        assertThat(secondValue).isEqualTo(firstValue);
    }

    @Test
    void skipOnboarding_onNullFlag_setsFlag_calledAgain_isIdempotent() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-onb-skip");

        MvcResult first = mockMvc
                .perform(patch(PROFILE_ME + "/skip-onboarding").header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andReturn();
        String firstValue = extractJsonField(first.getResponse().getContentAsString(), "onboardingCompletedAt");
        assertThat(firstValue).isNotBlank();

        MvcResult second = mockMvc
                .perform(patch(PROFILE_ME + "/skip-onboarding").header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andReturn();
        String secondValue = extractJsonField(second.getResponse().getContentAsString(), "onboardingCompletedAt");

        // So khop chinh xac - xem ly do o update_firstSuccess_setsOnboardingFlag_...
        assertThat(secondValue).isEqualTo(firstValue);
    }

    // ---- Nhom 8: dien tu CV ----

    @Test
    void autofill_noPrimaryResume_returns409() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-autofill-none");

        MvcResult result = mockMvc
                .perform(get(PROFILE_ME + "/autofill-from-resume").header("Authorization", "Bearer " + session.token()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"NO_PRIMARY_RESUME_PARSED\"");
    }

    @Test
    void autofill_sevenMonthsExperience_roundsToZeroPointFive() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-autofill-7m");
        createPrimaryResumeWithParsedData(session.candidateId(), "Backend Developer", List.of("Java", "SQL"), 7);

        MvcResult result = mockMvc
                .perform(get(PROFILE_ME + "/autofill-from-resume").header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"currentTitle\":\"Backend Developer\"");
        assertThat(body).contains("\"skills\":[\"Java\",\"SQL\"]");
        assertThat(body).contains("\"yearsExperience\":0.5");
    }

    @Test
    void autofill_nineMonthsExperience_roundsToOne() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-autofill-9m");
        createPrimaryResumeWithParsedData(session.candidateId(), "Backend Developer", List.of("Java"), 9);

        MvcResult result = mockMvc
                .perform(get(PROFILE_ME + "/autofill-from-resume").header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"yearsExperience\":1.0");
    }

    @Test
    void autofill_experienceMonthsNull_yearsExperienceIsNull() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-autofill-null");
        createPrimaryResumeWithParsedData(session.candidateId(), "Backend Developer", List.of("Java"), null);

        MvcResult result = mockMvc
                .perform(get(PROFILE_ME + "/autofill-from-resume").header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"yearsExperience\":null");
    }

    // ---- Nhom 9: chong lo cho HR + 403 ----

    @Test
    void hrEndpoints_doNotLeakCareerPreferenceFields() throws Exception {
        String hrToken = registerAndLoginHr("hr-leak");
        createCompany(hrToken, "Cong ty Leak Test " + UUID.randomUUID());
        String jobId = createOpenJob(hrToken, "Job Leak Test " + UUID.randomUUID());

        CandidateSession candidate = registerAndLoginCandidate("cand-leak");
        String marker = "MARKER_SHOULD_NOT_LEAK_TO_HR_" + UUID.randomUUID();
        String profileBody = fullProfileBody(
                "[\"IT_SOFTWARE\"]", "[\"HA_NOI\"]", "[\"ONSITE\"]", "[\"Java\"]", "37", "\"" + marker + "\"");
        putProfile(candidate.token(), profileBody);

        String resumeId = uploadResume(candidate.token());
        markResumeParsedDone(UUID.fromString(resumeId));
        apply(candidate.token(), jobId, resumeId);

        MvcResult ownerResult = mockMvc
                .perform(get("/api/hr/jobs/" + jobId + "/applications").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        String ownerBody = ownerResult.getResponse().getContentAsString();
        assertThat(ownerBody).doesNotContain(marker);
        assertThat(ownerBody).doesNotContain("desiredIndustryCodes");
        assertThat(ownerBody).doesNotContain("desiredSalaryMin");
        assertThat(ownerBody).doesNotContain("\"skills\"");
        assertThat(ownerBody).doesNotContain("\"bio\"");

        MvcResult searchResult = mockMvc
                .perform(get("/api/hr/candidates").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        String searchBody = searchResult.getResponse().getContentAsString();
        assertThat(searchBody).doesNotContain(marker);
        assertThat(searchBody).doesNotContain("desiredIndustryCodes");
        assertThat(searchBody).doesNotContain("desiredSalaryMin");
    }

    @Test
    void hrToken_callingCandidateProfileEndpoints_returns403() throws Exception {
        String hrToken = registerAndLoginHr("hr-forbidden");

        MvcResult getResult =
                mockMvc.perform(get(PROFILE_ME).header("Authorization", "Bearer " + hrToken)).andReturn();
        MvcResult putResult = mockMvc
                .perform(put(PROFILE_ME)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullProfileBody("[]", "[]", "[]", "[]", "null", "null")))
                .andReturn();
        MvcResult patchResult = mockMvc
                .perform(patch(PROFILE_ME + "/skip-onboarding").header("Authorization", "Bearer " + hrToken))
                .andReturn();

        assertThat(getResult.getResponse().getStatus()).isEqualTo(403);
        assertThat(putResult.getResponse().getStatus()).isEqualTo(403);
        assertThat(patchResult.getResponse().getStatus()).isEqualTo(403);
    }

    // ---- Nhom 10: embedding - van ban dai dien doi/khong doi (R-E3) ----

    @Test
    void update_onlyDateOfBirthChanges_embeddingNotTouched() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-emb-dob");
        UUID profileId = candidateProfileIdOf(session.candidateId());
        String bodyWithContent =
                fullProfileBody("[]", "[]", "[]", "[\"Java\"]", "null", "\"Gioi thieu ban dau\"");
        putProfile(session.token(), bodyWithContent);
        setFakeEmbedding(profileId);
        assertThat(hasEmbedding(profileId)).isTrue();

        String bodySameTextDifferentDob =
                fullProfileBody("[]", "[]", "[]", "[\"Java\"]", "null", "\"Gioi thieu ban dau\"")
                        .replace("\"dateOfBirth\": \"1995-01-01\"", "\"dateOfBirth\": \"1996-02-02\"");
        putProfile(session.token(), bodySameTextDifferentDob);

        // Van ban dai dien (headline+skills+bio) khong doi -> R-E3 KHONG dong gi den embedding, gia
        // tri gia lap o tren phai con nguyen (khac NULL).
        assertThat(hasEmbedding(profileId)).isTrue();
    }

    @Test
    void update_headlineChanges_embeddingSetNull() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-emb-headline");
        UUID profileId = candidateProfileIdOf(session.candidateId());
        putProfile(session.token(), fullProfileBody("[]", "[]", "[]", "[\"Java\"]", "null", "null"));
        setFakeEmbedding(profileId);
        assertThat(hasEmbedding(profileId)).isTrue();

        String changedHeadlineBody =
                """
                {
                  "headline":"Chuc danh MOI khac han",
                  "skills": ["Java"]
                }
                """;
        putProfile(session.token(), changedHeadlineBody);

        assertThat(hasEmbedding(profileId)).isFalse();
    }

    private static String repeatZeros(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(",0");
        }
        return sb.toString();
    }

    // ---- Nhom 14: request kieu cu (chi 5 field goc FR-U01) ----

    @Test
    void update_legacyRequestWithOnlyFiveOriginalFields_returns200AndArraysAreEmpty() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-legacy");
        String legacyBody =
                """
                {"headline":"Chuc danh cu","location":"Ha Noi","currentTitle":"Dev","yearsExperience":1.5,"dateOfBirth":"1990-01-01"}
                """;

        MvcResult putResult = putProfile(session.token(), legacyBody);
        assertThat(putResult.getResponse().getStatus()).isEqualTo(200);

        MvcResult getResult = getProfile(session.token());
        String json = getResult.getResponse().getContentAsString();
        assertThat(json).contains("\"desiredIndustries\":[]");
        assertThat(json).contains("\"desiredLocations\":[]");
        assertThat(json).contains("\"desiredWorkModes\":[]");
        assertThat(json).contains("\"skills\":[]");
        assertThat(json).contains("\"desiredSalaryMinMillions\":null");
    }

    // ---- Nhom 15: bay flush Hibernate (R-E3) ----

    @Test
    void update_changeHeadline_readBack_bothNewHeadlineAndNullEmbeddingArePresent() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-flush");
        UUID profileId = candidateProfileIdOf(session.candidateId());
        setFakeEmbedding(profileId);

        String body = """
                {"headline":"Chuc danh sau khi sua"}
                """;
        MvcResult result = putProfile(session.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"headline\":\"Chuc danh sau khi sua\"");
        assertThat(hasEmbedding(profileId)).isFalse();
    }
}
