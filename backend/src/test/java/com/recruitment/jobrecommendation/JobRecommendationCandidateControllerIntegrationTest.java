package com.recruitment.jobrecommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.math.BigDecimal;
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

// Khong @Transactional o class/method nao - dang ky MockMvc (dang nhap that qua /api/auth) + seed
// du lieu qua repository deu la CRUD thuong, tu commit binh thuong, mau ResumeIntegrationTest.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobRecommendationCandidateControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobRecommendationRepository jobRecommendationRepository;

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

    private record CandidateSession(String token, UUID candidateId) {
    }

    // Mau ResumeIntegrationTest.registerAndLoginCandidate, them buoc tra ve ca candidateId (lay qua
    // UserRepository.findByEmail sau khi dang ky - can UUID that de seed job_recommendations truc
    // tiep qua repository, khong di qua API tao goi y nao ca vi F1 khong co endpoint POST cho no).
    private CandidateSession registerAndLoginCandidate(String prefix) throws Exception {
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
        String token = extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
        UUID candidateId = userRepository.findByEmail(email).orElseThrow().getId();
        return new CandidateSession(token, candidateId);
    }

    private UUID createOpenJob(String title) {
        User hr = new User();
        hr.setEmail("hr-" + UUID.randomUUID() + "@example.com");
        hr.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        hr.setRole(Role.HR);
        hr.setFullName("Nha Tuyen Dung Test");
        hr = userRepository.save(hr);

        Company company = new Company();
        company.setOwnerId(hr.getId());
        company.setName("Cong ty Test " + UUID.randomUUID());
        company = companyRepository.save(company);

        Job job = new Job();
        job.setCompanyId(company.getId());
        job.setCreatedBy(hr.getId());
        job.setTitle(title);
        job.setDescription("Mo ta cong viec test");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        return jobRepository.save(job).getId();
    }

    // resume_id cua job_recommendations co FK toi resumes(id) - can mot Resume that (khong dung
    // UUID.randomUUID()), du test nay khong quan tam noi dung CV, chi can mot hang hop le de qua FK.
    private UUID createResumeFor(UUID candidateId) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        return resumeRepository.save(resume).getId();
    }

    private void seedRecommendation(UUID candidateId, UUID jobId, UUID resumeId, String similarity) {
        JobRecommendation recommendation = new JobRecommendation();
        recommendation.setCandidateId(candidateId);
        recommendation.setJobId(jobId);
        recommendation.setResumeId(resumeId);
        recommendation.setSimilarityScore(new BigDecimal(similarity));
        jobRecommendationRepository.save(recommendation);
    }

    @Test
    void getRecommendations_noCache_returnsEmptyList() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-norec");

        mockMvc
                .perform(get("/api/candidates/job-recommendations").header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // Test am quan trong nhat cua Dot 5 (muc G): JSON tra ve KHONG duoc chua similarityScore hay
    // matchScore o bat ky dang nao - hien thi con so do cho candidate la gan nhat voi dieu SRS cam
    // o phia HR (khong gan nhan/xep hang dua tren diem AI cho nguoi dung thay).
    @Test
    void getRecommendations_hasCachedRecommendations_returnsOrderedWithoutSimilarityScoreField() throws Exception {
        CandidateSession session = registerAndLoginCandidate("cand-withrec");
        UUID resumeId = createResumeFor(session.candidateId());

        UUID jobHighId = createOpenJob("Java Developer Diem Cao");
        UUID jobLowId = createOpenJob("Java Developer Diem Thap");
        seedRecommendation(session.candidateId(), jobHighId, resumeId, "0.80000");
        seedRecommendation(session.candidateId(), jobLowId, resumeId, "0.45000");

        MvcResult result = mockMvc
                .perform(get("/api/candidates/job-recommendations").header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("similarityScore");
        assertThat(body).doesNotContain("matchScore");
        int highIndex = body.indexOf(jobHighId.toString());
        int lowIndex = body.indexOf(jobLowId.toString());
        assertThat(highIndex).isGreaterThanOrEqualTo(0);
        assertThat(lowIndex).isGreaterThan(highIndex);
    }
}
