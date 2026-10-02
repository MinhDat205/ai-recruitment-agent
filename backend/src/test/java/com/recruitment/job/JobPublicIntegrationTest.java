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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
//
// FR-U07: them FixedClockTestConfiguration (@Primary Clock.fixed) de nhom test postedWithin (muc
// 7.7) dieu khien duoc "bay gio" - khong anh huong cac test cu (created_at/deleted_at do Postgres
// tu sinh bang now(), khong doc Clock cua Java).
@Import({TestcontainersConfiguration.class, FixedClockTestConfiguration.class})
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

    // FR-U07 muc 7.2/7.8 - lay toan bo gia tri "title" theo DUNG thu tu xuat hien trong body JSON (=
    // dung thu tu phan tu cua mang ket qua). Dung de kiem "khong bi day xuong cuoi" (R-N3) va tie-break
    // id on dinh qua nhieu lan goi (R-O3) ma khong can tu doan thu tu UUID cua Postgres (Java
    // UUID.compareTo KHONG cung ngu nghia voi cach Postgres so sanh byte UUID).
    private List<String> extractTitlesInOrder(String body) {
        List<String> titles = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"title\":\"([^\"]*)\"").matcher(body);
        while (matcher.find()) {
            titles.add(matcher.group(1));
        }
        return titles;
    }

    private List<String> searchOrderedTitles(String keyword, String paramName, String paramValue) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param(paramName, paramValue)
                        .param("keyword", keyword)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        return extractTitlesInOrder(result.getResponse().getContentAsString());
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

    // FR-C05 R-J8 / muc 7.4: tim "Ho Chi Minh" ra ca Job da chuan hoa (nhan "TP. Ho Chi Minh") lan Job
    // chua chuan hoa (gia tri cu), khong ra Job tinh khac.
    @Test
    void list_filtersByLocation_matchesCatalogLabelOrLegacyValue() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Job normalized = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Job Code HCM " + unique, null,
                null);
        normalized.setLocationCode("HO_CHI_MINH");
        jobRepository.save(normalized);
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Job Legacy HCM " + unique,
                "Quận 1, Hồ Chí Minh", null);
        Job otherProvince = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Job Code HN " + unique,
                null, null);
        otherProvince.setLocationCode("HA_NOI");
        jobRepository.save(otherProvince);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("location", "Hồ Chí Minh")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("Job Code HCM " + unique);
        assertThat(body).contains("Job Legacy HCM " + unique);
        assertThat(body).doesNotContain("Job Code HN " + unique);
        assertThat(extractJsonNumber(body, "totalElements")).isEqualTo(2);
        assertThat(body).contains("\"locationLabel\":\"TP. Hồ Chí Minh\"");
        assertThat(body).contains("\"legacyLocation\":\"Quận 1, Hồ Chí Minh\"");
    }

    @Test
    void list_filtersByCategory_matchesCatalogLabel() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Job normalized = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Job Code IT " + unique, null,
                null);
        normalized.setCategoryCode("IT_SOFTWARE");
        jobRepository.save(normalized);
        Job sales = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Job Code Sales " + unique, null,
                null);
        sales.setCategoryCode("SALES");
        jobRepository.save(sales);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("category", "Phần mềm")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("Job Code IT " + unique);
        assertThat(body).doesNotContain("Job Code Sales " + unique);
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

    // =====================================================================================
    // FR-U07 - cac nhom test bo sung o muc 7 REQUIREMENT.md (khong sua test cu o tren).
    // =====================================================================================

    // ===== Muc 7.1 - Loc theo ma danh muc (R-F4) =====

    @Test
    void list_filtersByCategoryCode_returnsMatchingJobsOnly() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Job itJob = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "CatCode IT " + unique, null, null);
        itJob.setCategoryCode("IT_SOFTWARE");
        jobRepository.save(itJob);
        Job salesJob =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "CatCode Sales " + unique, null, null);
        salesJob.setCategoryCode("SALES");
        jobRepository.save(salesJob);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("categoryCode", "IT_SOFTWARE")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("CatCode IT " + unique);
        assertThat(body).doesNotContain("CatCode Sales " + unique);
    }

    @Test
    void list_filtersByLocationCode_returnsMatchingJobsOnly() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Job hcmJob = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "LocCode HCM " + unique, null, null);
        hcmJob.setLocationCode("HO_CHI_MINH");
        jobRepository.save(hcmJob);
        Job hnJob = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "LocCode HN " + unique, null, null);
        hnJob.setLocationCode("HA_NOI");
        jobRepository.save(hnJob);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("locationCode", "HO_CHI_MINH")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("LocCode HCM " + unique);
        assertThat(body).doesNotContain("LocCode HN " + unique);
    }

    @Test
    void list_invalidCategoryCode_returns400() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/public/jobs").param("categoryCode", "KHONG_TON_TAI"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_CATALOG_CODE\"");
    }

    @Test
    void list_invalidLocationCode_returns400() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/public/jobs").param("locationCode", "KHONG_TON_TAI"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_CATALOG_CODE\"");
    }

    // ===== Muc 7.2 - Ma NULL van hien, khong bi day xuong cuoi mot cach dac biet (R-N) =====

    @Test
    void list_filterByCategoryCode_includesJobsWithNullCategoryCode_notPushedToEnd() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);

        // Khop ma, nhung publishedAt CU hon - de kiem thu tu sap xep o duoi.
        Job matched =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "NullCat Matched " + unique, null, null);
        matched.setCategoryCode("IT_SOFTWARE");
        matched.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(10, ChronoUnit.DAYS));
        jobRepository.save(matched);

        // code NULL, con gia tri cu (R-N3) - publishedAt MOI hon.
        Job legacyOnly = createJob(
                company.getId(),
                hr.getId(),
                JobStatus.OPEN,
                null,
                "NullCat LegacyValue " + unique,
                null,
                "Cong nghe cu");
        legacyOnly.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(1, ChronoUnit.DAYS));
        jobRepository.save(legacyOnly);

        // code NULL, "thieu han" ca ma lan gia tri cu (R-N4).
        Job missingBoth =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "NullCat Missing " + unique, null, null);
        missingBoth.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(2, ChronoUnit.DAYS));
        jobRepository.save(missingBoth);

        // Nganh khac - phai bi loai han.
        Job otherCategory = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "NullCat OtherCat " + unique, null, null);
        otherCategory.setCategoryCode("SALES");
        jobRepository.save(otherCategory);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("categoryCode", "IT_SOFTWARE")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("NullCat Matched " + unique);
        assertThat(body).contains("NullCat LegacyValue " + unique);
        assertThat(body).contains("NullCat Missing " + unique);
        assertThat(body).doesNotContain("NullCat OtherCat " + unique);

        // Khong bi day xuong cuoi mot cach dac biet: sap NEWEST (mac dinh), hai job ma NULL (moi hon)
        // phai dung TRUOC job co ma khop nhung publishedAt cu hon - xep lan theo dung thu tu sap xep.
        List<String> titles = extractTitlesInOrder(body);
        int legacyIndex = titles.indexOf("NullCat LegacyValue " + unique);
        int missingIndex = titles.indexOf("NullCat Missing " + unique);
        int matchedIndex = titles.indexOf("NullCat Matched " + unique);
        assertThat(legacyIndex).isGreaterThanOrEqualTo(0);
        assertThat(missingIndex).isGreaterThanOrEqualTo(0);
        assertThat(legacyIndex).isLessThan(matchedIndex);
        assertThat(missingIndex).isLessThan(matchedIndex);
    }

    @Test
    void list_filterByLocationCode_includesJobsWithNullLocationCode() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);

        Job matched =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "NullLoc Matched " + unique, null, null);
        matched.setLocationCode("HO_CHI_MINH");
        jobRepository.save(matched);
        Job legacyOnly = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "NullLoc LegacyValue " + unique, "Quan 1 cu", null);
        jobRepository.save(legacyOnly);
        Job missingBoth =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "NullLoc Missing " + unique, null, null);
        jobRepository.save(missingBoth);
        Job otherProvince = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "NullLoc OtherProvince " + unique, null, null);
        otherProvince.setLocationCode("HA_NOI");
        jobRepository.save(otherProvince);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("locationCode", "HO_CHI_MINH")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("NullLoc Matched " + unique);
        assertThat(body).contains("NullLoc LegacyValue " + unique);
        assertThat(body).contains("NullLoc Missing " + unique);
        assertThat(body).doesNotContain("NullLoc OtherProvince " + unique);
    }

    // ===== Muc 7.3 - Loc luong: chong lap + Thoa thuan + hideUnlisted (R-S2/R-S4/R-S6) =====

    @Test
    void list_salaryRange_overlapIncluded_outOfRangeExcluded_boundaryAtExactEdgeIncluded() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);

        // Loc 20-30 trieu = 20_000_000 - 30_000_000 VND.
        Job overlap =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Salary Overlap " + unique, null, null);
        overlap.setSalaryMin(BigDecimal.valueOf(22_000_000));
        overlap.setSalaryMax(BigDecimal.valueOf(28_000_000));
        jobRepository.save(overlap);

        Job outOfRange = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "Salary OutOfRange " + unique, null, null);
        outOfRange.setSalaryMin(BigDecimal.valueOf(35_000_000));
        outOfRange.setSalaryMax(BigDecimal.valueOf(40_000_000));
        jobRepository.save(outOfRange);

        // Bien dung: salary_max == filterMin (20 trieu) -> vao.
        Job boundaryMax = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "Salary BoundaryMax " + unique, null, null);
        boundaryMax.setSalaryMin(BigDecimal.valueOf(10_000_000));
        boundaryMax.setSalaryMax(BigDecimal.valueOf(20_000_000));
        jobRepository.save(boundaryMax);

        // Lech 1 trieu -> loai.
        Job offByOneMax = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "Salary OffByOneMax " + unique, null, null);
        offByOneMax.setSalaryMin(BigDecimal.valueOf(10_000_000));
        offByOneMax.setSalaryMax(BigDecimal.valueOf(19_000_000));
        jobRepository.save(offByOneMax);

        // Bien dung: salary_min == filterMax (30 trieu) -> vao.
        Job boundaryMin = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "Salary BoundaryMin " + unique, null, null);
        boundaryMin.setSalaryMin(BigDecimal.valueOf(30_000_000));
        boundaryMin.setSalaryMax(BigDecimal.valueOf(40_000_000));
        jobRepository.save(boundaryMin);

        Job offByOneMin = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "Salary OffByOneMin " + unique, null, null);
        offByOneMin.setSalaryMin(BigDecimal.valueOf(31_000_000));
        offByOneMin.setSalaryMax(BigDecimal.valueOf(40_000_000));
        jobRepository.save(offByOneMin);

        // Thoa thuan - vao mac dinh khi khong hideUnlisted (R-S4).
        Job thoaThuan =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Salary ThoaThuan " + unique, null, null);
        jobRepository.save(thoaThuan);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("salaryMin", "20")
                        .param("salaryMax", "30")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Salary Overlap " + unique);
        assertThat(body).doesNotContain("Salary OutOfRange " + unique);
        assertThat(body).contains("Salary BoundaryMax " + unique);
        assertThat(body).doesNotContain("Salary OffByOneMax " + unique);
        assertThat(body).contains("Salary BoundaryMin " + unique);
        assertThat(body).doesNotContain("Salary OffByOneMin " + unique);
        assertThat(body).contains("Salary ThoaThuan " + unique);
    }

    @Test
    void list_salaryRange_hideUnlisted_excludesThoaThuanOnly() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);

        Job thoaThuan = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "HideUnlisted ThoaThuan " + unique, null, null);
        jobRepository.save(thoaThuan);
        Job withSalary = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "HideUnlisted WithSalary " + unique, null, null);
        withSalary.setSalaryMin(BigDecimal.valueOf(22_000_000));
        withSalary.setSalaryMax(BigDecimal.valueOf(28_000_000));
        jobRepository.save(withSalary);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("salaryMin", "20")
                        .param("salaryMax", "30")
                        .param("hideUnlisted", "true")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("HideUnlisted ThoaThuan " + unique);
        assertThat(body).contains("HideUnlisted WithSalary " + unique);
    }

    // R-S4 - hideUnlisted la tham so DOC LAP: khong kem salaryMin/salaryMax nao van phai loai dung
    // nhom Thoa thuan, khong anh huong job co luong VND binh thuong.
    @Test
    void list_hideUnlisted_withoutSalaryRange_excludesThoaThuanOnly_keepsVndJobs() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);

        Job thoaThuan = createJob(
                company.getId(),
                hr.getId(),
                JobStatus.OPEN,
                null,
                "HideUnlistedOnly ThoaThuan " + unique,
                null,
                null);
        jobRepository.save(thoaThuan);
        Job withSalary = createJob(
                company.getId(),
                hr.getId(),
                JobStatus.OPEN,
                null,
                "HideUnlistedOnly WithSalary " + unique,
                null,
                null);
        withSalary.setSalaryMin(BigDecimal.valueOf(10_000_000));
        withSalary.setSalaryMax(BigDecimal.valueOf(15_000_000));
        jobRepository.save(withSalary);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("hideUnlisted", "true")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("HideUnlistedOnly ThoaThuan " + unique);
        assertThat(body).contains("HideUnlistedOnly WithSalary " + unique);
    }

    // ===== Muc 7.4 - Tien te khac VND (R-S5) =====

    @Test
    void list_salaryFilter_foreignCurrencyJob_alwaysIncluded_notHiddenByHideUnlisted() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);

        Job usdJob =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Currency USD " + unique, null, null);
        usdJob.setSalaryMin(BigDecimal.valueOf(1000));
        usdJob.setSalaryMax(BigDecimal.valueOf(2000));
        usdJob.setSalaryCurrency("USD");
        jobRepository.save(usdJob);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("salaryMin", "20")
                        .param("salaryMax", "30")
                        .param("hideUnlisted", "true")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Currency USD " + unique);
    }

    // Chi kiem bien the CHU HOA/THUONG ('vnd') - bien the "co khoang trang" (' VND ') trong dac ta
    // KHONG dung duoc: cot salary_currency la VARCHAR(3) (V1__init_schema.sql:74) va
    // JobRequest.salaryCurrency bat buoc dung 3 ky tu (@Size(min=3,max=3)), trong khi ' VND ' dai 5
    // ky tu - khong co duong nao (API hay ghi thang qua repository trong test) tao ra duoc gia tri
    // nay trong DB. Bao lai o bao cao dot 2, khong tu "lach" bang cach nao khac.
    @Test
    void list_salaryFilter_lowerCaseCurrency_treatedAsVnd_filteredNormally() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);

        // Ngoai khoang loc 20-30 trieu (max = 15 trieu) - neu bi hieu nham la ngoai te thi se "luon
        // hien" sai; phai bi LOAI vi duoc chuan hoa ve VND roi so theo R-S2 binh thuong.
        Job lowerCase = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "Currency LowerCaseVnd " + unique, null, null);
        lowerCase.setSalaryMin(BigDecimal.valueOf(10_000_000));
        lowerCase.setSalaryMax(BigDecimal.valueOf(15_000_000));
        lowerCase.setSalaryCurrency("vnd");
        jobRepository.save(lowerCase);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("salaryMin", "20")
                        .param("salaryMax", "30")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Currency LowerCaseVnd " + unique);
    }

    // ===== Muc 7.5 - Validate salaryMin/salaryMax (R-S3) =====

    @Test
    void list_salaryMinGreaterThanMax_returns400() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/public/jobs").param("salaryMin", "30").param("salaryMax", "20"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_JOB_FILTER\"");
    }

    @Test
    void list_negativeSalary_returns400() throws Exception {
        MvcResult resultMin = mockMvc.perform(get("/api/public/jobs").param("salaryMin", "-1"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(resultMin.getResponse().getContentAsString()).contains("\"error\":\"INVALID_JOB_FILTER\"");

        MvcResult resultMax = mockMvc.perform(get("/api/public/jobs").param("salaryMax", "-5"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(resultMax.getResponse().getContentAsString()).contains("\"error\":\"INVALID_JOB_FILTER\"");
    }

    @Test
    void list_salaryMinEqualsSalaryMax_isValidBoundary_returns200() throws Exception {
        mockMvc.perform(get("/api/public/jobs").param("salaryMin", "20").param("salaryMax", "20"))
                .andExpect(status().isOk());
    }

    // ===== Muc 7.6 - workMode nhieu gia tri (R-W1, R-F5) =====

    @Test
    void list_filtersByWorkMode_multipleValues_excludesOthers() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);

        Job onsite =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "WorkMode Onsite " + unique, null, null);
        onsite.setWorkMode("ONSITE");
        jobRepository.save(onsite);
        Job hybrid =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "WorkMode Hybrid " + unique, null, null);
        hybrid.setWorkMode("HYBRID");
        jobRepository.save(hybrid);
        Job remote =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "WorkMode Remote " + unique, null, null);
        remote.setWorkMode("REMOTE");
        jobRepository.save(remote);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("workMode", "ONSITE")
                        .param("workMode", "REMOTE")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("WorkMode Onsite " + unique);
        assertThat(body).contains("WorkMode Remote " + unique);
        assertThat(body).doesNotContain("WorkMode Hybrid " + unique);
    }

    @Test
    void list_invalidWorkMode_returns400() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/public/jobs").param("workMode", "KHONG_HOP_LE"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_JOB_FILTER\"");
    }

    // ===== Muc 7.7 - 3 moc thoi gian postedWithin (R-T2, Clock co dinh - FixedClockTestConfiguration) =====

    @Test
    void list_postedWithin24h_includesAt23h_excludesAt25h() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Job within =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Posted23h " + unique, null, null);
        within.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(23, ChronoUnit.HOURS));
        jobRepository.save(within);
        Job outside =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Posted25h " + unique, null, null);
        outside.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(25, ChronoUnit.HOURS));
        jobRepository.save(outside);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("postedWithin", "LAST_24H")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Posted23h " + unique);
        assertThat(body).doesNotContain("Posted25h " + unique);
    }

    @Test
    void list_postedWithin7d_includesAt6d_excludesAt8d() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Job within = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Posted6d " + unique, null, null);
        within.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(6, ChronoUnit.DAYS));
        jobRepository.save(within);
        Job outside = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Posted8d " + unique, null, null);
        outside.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(8, ChronoUnit.DAYS));
        jobRepository.save(outside);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("postedWithin", "LAST_7D")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Posted6d " + unique);
        assertThat(body).doesNotContain("Posted8d " + unique);
    }

    @Test
    void list_postedWithin30d_includesAt29d_excludesAt31d() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Job within = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Posted29d " + unique, null, null);
        within.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(29, ChronoUnit.DAYS));
        jobRepository.save(within);
        Job outside = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Posted31d " + unique, null, null);
        outside.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(31, ChronoUnit.DAYS));
        jobRepository.save(outside);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("postedWithin", "LAST_30D")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Posted29d " + unique);
        assertThat(body).doesNotContain("Posted31d " + unique);
    }

    @Test
    void list_invalidPostedWithin_returns400() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/public/jobs").param("postedWithin", "KHONG_HOP_LE"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_JOB_FILTER\"");
    }

    // ===== Muc 7.8 - Sap xep: gia tri la (R-F5), tie-break id (R-O3/R-O4), dung thu tu COALESCE =====

    @Test
    void list_invalidSort_returns400() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/public/jobs").param("sort", "KHONG_HOP_LE"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"error\":\"INVALID_JOB_FILTER\"");
    }

    @Test
    void list_sortNewest_tieBreaksById_stableAcrossRepeatedCalls() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Instant tiedPublishedAt = FixedClockTestConfiguration.FIXED_NOW.minus(1, ChronoUnit.DAYS);

        Job jobA = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "TieNewest A " + unique, null, null);
        jobA.setPublishedAt(tiedPublishedAt);
        jobRepository.save(jobA);
        Job jobB = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "TieNewest B " + unique, null, null);
        jobB.setPublishedAt(tiedPublishedAt);
        jobRepository.save(jobB);
        Job jobC = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "TieNewest C " + unique, null, null);
        jobC.setPublishedAt(tiedPublishedAt);
        jobRepository.save(jobC);

        List<String> firstRun = searchOrderedTitles(unique, "sort", "NEWEST");
        List<String> secondRun = searchOrderedTitles(unique, "sort", "NEWEST");
        assertThat(firstRun).hasSize(3);
        assertThat(secondRun).isEqualTo(firstRun);
    }

    @Test
    void list_sortSalaryDesc_tieBreaksById_stableAcrossRepeatedCalls() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Instant tiedPublishedAt = FixedClockTestConfiguration.FIXED_NOW.minus(1, ChronoUnit.DAYS);

        Job jobA = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "TieSalary A " + unique, null, null);
        jobA.setPublishedAt(tiedPublishedAt);
        jobA.setSalaryMax(BigDecimal.valueOf(25_000_000));
        jobRepository.save(jobA);
        Job jobB = createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "TieSalary B " + unique, null, null);
        jobB.setPublishedAt(tiedPublishedAt);
        jobB.setSalaryMax(BigDecimal.valueOf(25_000_000));
        jobRepository.save(jobB);

        List<String> firstRun = searchOrderedTitles(unique, "sort", "SALARY_DESC");
        List<String> secondRun = searchOrderedTitles(unique, "sort", "SALARY_DESC");
        assertThat(firstRun).hasSize(2);
        assertThat(secondRun).isEqualTo(firstRun);
    }

    // R-O2 - COALESCE(salary_max, salary_min) DESC NULLS LAST: tin luong cao truoc tin luong thap;
    // tin chi co salary_min (salary_max NULL) xep theo salary_min; tin Thoa thuan (ca hai NULL) luon
    // cuoi cung, bat ke publishedAt.
    @Test
    void list_sortSalaryDesc_ordersByCoalescedSalaryDescending_thoaThuanLast() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);

        Job high =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "SortSalary High " + unique, null, null);
        high.setSalaryMin(BigDecimal.valueOf(40_000_000));
        high.setSalaryMax(BigDecimal.valueOf(50_000_000));
        jobRepository.save(high);

        Job minOnly = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "SortSalary MinOnly " + unique, null, null);
        minOnly.setSalaryMin(BigDecimal.valueOf(30_000_000));
        jobRepository.save(minOnly);

        Job low =
                createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "SortSalary Low " + unique, null, null);
        low.setSalaryMin(BigDecimal.valueOf(10_000_000));
        low.setSalaryMax(BigDecimal.valueOf(20_000_000));
        jobRepository.save(low);

        Job thoaThuan = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "SortSalary ThoaThuan " + unique, null, null);
        jobRepository.save(thoaThuan);

        List<String> order = searchOrderedTitles(unique, "sort", "SALARY_DESC");
        assertThat(order)
                .containsExactly(
                        "SortSalary High " + unique,
                        "SortSalary MinOnly " + unique,
                        "SortSalary Low " + unique,
                        "SortSalary ThoaThuan " + unique);
    }

    // ===== Muc 7.9 - Thoat ky tu wildcard %, _, \ trong toPattern =====
    // category/location dung CHUNG mot ham toPattern voi keyword (JobPublicService.toPattern) - kiem
    // qua keyword la du, khong lap lai cho tung tham so.

    @Test
    void list_keywordWithPercent_matchesLiteralPercentOnly() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        // Decoy KHONG co dau % (chi "100" roi toi ngay "Off") - neu % khong duoc thoat (wildcard "0
        // hoac nhieu ky tu bat ky"), pattern "100%Off" se khop ca truong hop "0 ky tu o giua" nay, lam
        // decoy bi tinh nham la khop. @Transactional rollback rieng tung test nen khong can nhung
        // "unique" vao chinh tu khoa tim kiem - chi can trong tieu de de de doc log khi test do.
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Percent100OffSale-" + unique, null, null);
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Percent100%OffSale-" + unique, null, null);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("keyword", "100%Off")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Percent100OffSale-" + unique);
        assertThat(body).contains("Percent100%OffSale-" + unique);
    }

    @Test
    void list_keywordWithUnderscore_matchesLiteralUnderscoreOnly() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        // Decoy thay _ bang mot ky tu thuong 'X' - neu _ khong duoc thoat (wildcard "1 ky tu bat ky"),
        // decoy nay se bi tinh la khop oan.
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "UnderscoreXKey" + unique, null, null);
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "Underscore_Key" + unique, null, null);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("keyword", "Underscore_Key" + unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("UnderscoreXKey" + unique);
        assertThat(body).contains("Underscore_Key" + unique);
    }

    @Test
    void list_keywordWithBackslash_matchesLiteralBackslash_noServerError() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        createJob(company.getId(), hr.getId(), JobStatus.OPEN, null, "PathLike C:\\Users\\" + unique, null, null);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("keyword", "C:\\Users\\" + unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // Body la JSON tho: JSON tu dong thoat moi `\` thanh `\\` trong chuoi - can doi chieu dung
        // dang da thoat nay, khong phai dang ky tu goc cua tieu de.
        assertThat(body).contains("PathLike C:\\\\Users\\\\" + unique);
    }

    // ===== Muc 7.10 - Ket hop nhieu dieu kien = giao dung cua tung dieu kien rieng le =====

    @Test
    void list_combinedFilters_returnsIntersectionOfIndividualConditions() throws Exception {
        User hr = createHrUser();
        Company company = createCompany(hr.getId());
        String unique = UUID.randomUUID().toString().substring(0, 8);

        Job matchesAll = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "Combined MatchesAll " + unique, null, null);
        matchesAll.setCategoryCode("IT_SOFTWARE");
        matchesAll.setLocationCode("HA_NOI");
        matchesAll.setSalaryMin(BigDecimal.valueOf(22_000_000));
        matchesAll.setSalaryMax(BigDecimal.valueOf(28_000_000));
        matchesAll.setWorkMode("ONSITE");
        matchesAll.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(2, ChronoUnit.DAYS));
        jobRepository.save(matchesAll);

        Job wrongWorkMode = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "Combined WrongWorkMode " + unique, null, null);
        wrongWorkMode.setCategoryCode("IT_SOFTWARE");
        wrongWorkMode.setLocationCode("HA_NOI");
        wrongWorkMode.setSalaryMin(BigDecimal.valueOf(22_000_000));
        wrongWorkMode.setSalaryMax(BigDecimal.valueOf(28_000_000));
        wrongWorkMode.setWorkMode("REMOTE");
        wrongWorkMode.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(2, ChronoUnit.DAYS));
        jobRepository.save(wrongWorkMode);

        Job tooOld = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "Combined TooOld " + unique, null, null);
        tooOld.setCategoryCode("IT_SOFTWARE");
        tooOld.setLocationCode("HA_NOI");
        tooOld.setSalaryMin(BigDecimal.valueOf(22_000_000));
        tooOld.setSalaryMax(BigDecimal.valueOf(28_000_000));
        tooOld.setWorkMode("ONSITE");
        tooOld.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(10, ChronoUnit.DAYS));
        jobRepository.save(tooOld);

        Job wrongCategory = createJob(
                company.getId(), hr.getId(), JobStatus.OPEN, null, "Combined WrongCategory " + unique, null, null);
        wrongCategory.setCategoryCode("SALES");
        wrongCategory.setLocationCode("HA_NOI");
        wrongCategory.setSalaryMin(BigDecimal.valueOf(22_000_000));
        wrongCategory.setSalaryMax(BigDecimal.valueOf(28_000_000));
        wrongCategory.setWorkMode("ONSITE");
        wrongCategory.setPublishedAt(FixedClockTestConfiguration.FIXED_NOW.minus(2, ChronoUnit.DAYS));
        jobRepository.save(wrongCategory);

        MvcResult result = mockMvc.perform(get("/api/public/jobs")
                        .param("categoryCode", "IT_SOFTWARE")
                        .param("locationCode", "HA_NOI")
                        .param("salaryMin", "20")
                        .param("workMode", "ONSITE")
                        .param("postedWithin", "LAST_7D")
                        .param("sort", "SALARY_DESC")
                        .param("keyword", unique)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Combined MatchesAll " + unique);
        assertThat(body).doesNotContain("Combined WrongWorkMode " + unique);
        assertThat(body).doesNotContain("Combined TooOld " + unique);
        assertThat(body).doesNotContain("Combined WrongCategory " + unique);
    }

    // ===== Muc 7.12 - RBAC/cong khai: tham so moi khong doi yeu cau xac thuc =====

    @Test
    void list_withAllNewFilterParams_accessibleWithoutToken_returns200() throws Exception {
        mockMvc.perform(get("/api/public/jobs")
                        .param("categoryCode", "IT_SOFTWARE")
                        .param("locationCode", "HA_NOI")
                        .param("salaryMin", "10")
                        .param("salaryMax", "50")
                        .param("hideUnlisted", "false")
                        .param("workMode", "ONSITE")
                        .param("postedWithin", "LAST_30D")
                        .param("sort", "SALARY_DESC"))
                .andExpect(status().isOk());
    }
}
