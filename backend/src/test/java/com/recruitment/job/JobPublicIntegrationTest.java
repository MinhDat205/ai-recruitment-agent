package com.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

// @Transactional: moi @Test rollback rieng sau khi chay, tranh du lieu seed cua test nay
// (vi du 15 job cho phan trang) lam sai lech phep dem cua test khac trong cung container.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JobPublicIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private long countOccurrences(String text, String needle) {
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(text);
        long count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private long extractJsonNumber(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return Long.parseLong(matcher.group(1));
    }

    private User createHrUser() {
        User user = new User();
        user.setEmail(uniqueEmail("hr"));
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNO");
        user.setRole(Role.HR);
        user.setFullName("HR Test");
        user.setActive(true);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private Company createCompany(UUID ownerId) {
        Company company = new Company();
        company.setOwnerId(ownerId);
        company.setName("Cong ty Test " + UUID.randomUUID());
        company.setLogoUrl("https://example.com/logo.png");
        company.setDescription("Mo ta cong ty test");
        company.setContactEmail("contact@example.com");
        return companyRepository.save(company);
    }

    // Fixture nay co y bo qua JobOwnerService (goi thang JobRepository) nen tao ra Job KHONG
    // kem Rubric/InterviewTemplate - trang thai nay khong the sinh ra tu production (xem
    // JobOwnerService.create). Chi phuc vu test doc cong khai (A2). Nhanh nao can Job day du
    // (co rubric/template) thi tao qua API /api/hr/jobs, dung copy ham nay.
    private Job createJob(
            UUID companyId,
            UUID createdBy,
            JobStatus status,
            Instant deletedAt,
            String title,
            String location,
            String category) {
        Job job = new Job();
        job.setCompanyId(companyId);
        job.setCreatedBy(createdBy);
        job.setTitle(title);
        job.setDescription("Mo ta cong viec cho " + title);
        job.setCategory(category);
        job.setLocation(location);
        job.setStatus(status);
        job.setRecruitmentCycle(1);
        job.setDeletedAt(deletedAt);
        return jobRepository.save(job);
    }

    @Test
    void publicJobList_accessibleWithoutToken_returns200() throws Exception {
        mockMvc.perform(get("/api/public/jobs")).andExpect(status().isOk());
    }

    @Test
    void list_excludesDraftPausedClosedAndDeletedJobs() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        createJob(company.getId(), hr.getId(), JobStatus.DRAFT, null, "Draft Job Should Hide", "Hanoi", "IT");
        createJob(company.getId(), hr.getId(), JobStatus.PAUSED, null, "Paused Job Should Hide", "Hanoi", "IT");
        createJob(company.getId(), hr.getId(), JobStatus.CLOSED, null, "Closed Job Should Hide", "Hanoi", "IT");
        createJob(
                company.getId(),
                hr.getId(),
                JobStatus.OPEN,
                Instant.now(),
                "Deleted Open Job Should Hide",
                "Hanoi",
                "IT");
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Visible Open Job", "Hanoi", "IT");

        MvcResult result = mockMvc.perform(get("/api/public/jobs").param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();

        assertThat(body).contains("Visible Open Job");
        assertThat(body).doesNotContain("Draft Job Should Hide");
        assertThat(body).doesNotContain("Paused Job Should Hide");
        assertThat(body).doesNotContain("Closed Job Should Hide");
        assertThat(body).doesNotContain("Deleted Open Job Should Hide");
    }

    @Test
    void list_isPaginated_respectsPageAndSize() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String marker = "PaginationMarker" + UUID.randomUUID().toString().replace("-", "");
        for (int i = 0; i < 15; i++) {
            createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, marker + "-" + i, "Hanoi", "IT");
        }

        MvcResult firstPage = mockMvc.perform(get("/api/public/jobs")
                        .param("keyword", marker)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn();
        String firstBody = firstPage.getResponse().getContentAsString();
        assertThat(extractJsonNumber(firstBody, "totalElements")).isEqualTo(15);
        assertThat(extractJsonNumber(firstBody, "totalPages")).isEqualTo(2);
        assertThat(countOccurrences(firstBody, marker)).isEqualTo(10);

        MvcResult secondPage = mockMvc.perform(get("/api/public/jobs")
                        .param("keyword", marker)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn();
        String secondBody = secondPage.getResponse().getContentAsString();
        assertThat(countOccurrences(secondBody, marker)).isEqualTo(5);
    }

    @Test
    void list_filtersByKeyword_caseInsensitiveSubstring() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Senior Backend Engineer " + unique, "Hanoi", "IT");
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Frontend Developer " + unique, "Hanoi", "IT");

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("keyword", "BACK")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Senior Backend Engineer " + unique);
        assertThat(body).doesNotContain("Frontend Developer " + unique);
    }

    @Test
    void list_filtersByLocation() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Job HN " + unique, "Ha Noi", "IT");
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Job HCM " + unique, "Ho Chi Minh", "IT");

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("location", "ha noi")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Job HN " + unique);
        assertThat(body).doesNotContain("Job HCM " + unique);
    }

    @Test
    void list_filtersByCategory() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Job IT " + unique, "Hanoi", "Cong nghe thong tin");
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Job Sales " + unique, "Hanoi", "Kinh doanh");

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("category", "cong nghe")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Job IT " + unique);
        assertThat(body).doesNotContain("Job Sales " + unique);
    }

    @Test
    void detail_openJob_returnsCompanyInfo_withoutCreatedByOrOwnerId() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        Job job = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Open Job Detail", "Hanoi", "IT");

        MvcResult result = mockMvc.perform(get("/api/public/jobs/" + job.getId()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"company\"");
        assertThat(body.toLowerCase())
                .doesNotContain("createdby")
                .doesNotContain("created_by")
                .doesNotContain("ownerid")
                .doesNotContain("owner_id");
    }

    @Test
    void detail_draftJob_returns404() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        Job job = createJob(company.getId(), hr.getId(), JobStatus.DRAFT, null, "Draft Job Detail", "Hanoi", "IT");

        mockMvc.perform(get("/api/public/jobs/" + job.getId())).andExpect(status().isNotFound());
    }

    @Test
    void detail_deletedJob_returns404() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        Job job = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, Instant.now(), "Deleted Job Detail", "Hanoi", "IT");

        mockMvc.perform(get("/api/public/jobs/" + job.getId())).andExpect(status().isNotFound());
    }

    @Test
    void detail_nonexistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/public/jobs/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void companyDetail_returnsPublicFields_withoutOwnerId() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());

        MvcResult result = mockMvc.perform(get("/api/public/companies/" + company.getId()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("contactEmail");
        assertThat(body.toLowerCase()).doesNotContain("ownerid").doesNotContain("owner_id");
    }

    @Test
    void companyDetail_nonexistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/public/companies/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
