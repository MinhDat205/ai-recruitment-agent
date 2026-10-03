package com.recruitment.jobrecommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobEmbeddingStateService;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeEmbeddingStateService;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.CandidateProfile;
import com.recruitment.user.CandidateProfileEmbeddingStateService;
import com.recruitment.user.CandidateProfileRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// FR-U15 - thay the JobRecommendationCacheServiceTest (da xoa o dot 4, xem bang doi chieu muc 7
// REQUIREMENT.md). KHONG con bo dem - goi truc tiep JobRecommendationCandidateService.
// getRecommendationsForCandidate(), tinh TRUC TIEP tu Postgres/Testcontainers moi lan goi.
//
// @Transactional cap CLASS (khac file cu - xem ly do o do): service moi la @Transactional
// (readOnly = true), KHONG ghi gi ca (khong con cache de ghi), nen khong co nguy co "hai lan goi
// long chung mot transaction gay loi INSERT/DELETE sai thu tu" nhu truoc - moi test tu rollback
// sach sau khi chay, giong het khuon JobPublicIntegrationTest.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobRecommendationCandidateServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobEmbeddingStateService jobEmbeddingStateService;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private ResumeEmbeddingStateService resumeEmbeddingStateService;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CandidateProfileEmbeddingStateService candidateProfileEmbeddingStateService;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private CatalogRegistry catalogRegistry;

    @Autowired
    private JobRecommendationCandidateService service;

    // Cung ky thuat vector don vi voi JobEmbeddingRepositoryTest/JobRecommendationCacheServiceTest
    // cu (da xoa) - xem comment o do, giu NGUYEN de doi chieu dung bang mục 7.
    private static float[] vectorWithSimilarity(double s) {
        float[] v = new float[1536];
        v[0] = (float) s;
        v[1] = (float) Math.sqrt(1 - s * s);
        return v;
    }

    private static final float[] RESUME_VECTOR = vectorWithSimilarity(1.0);

    private UUID hrAndCompany() {
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
        return company.getId();
    }

    private Job newOpenJob(String title) {
        UUID companyId = hrAndCompany();
        Job job = new Job();
        job.setCompanyId(companyId);
        job.setCreatedBy(companyRepository.findById(companyId).orElseThrow().getOwnerId());
        job.setTitle(title);
        job.setDescription("Mo ta cong viec test");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        return job;
    }

    private UUID createOpenJobWithEmbedding(String title, double similarity) {
        Job job = jobRepository.save(newOpenJob(title));
        // Goi qua JobEmbeddingStateService (KHONG goi jobEmbeddingRepository.upsertEmbedding truc
        // tiep) - StateService la noi @Transactional nam that, dung duong ma production di.
        jobEmbeddingStateService.save(job.getId(), vectorWithSimilarity(similarity), "text-embedding-3-small");
        return job.getId();
    }

    private UUID createOpenJobWithFields(
            String title, String categoryCode, String locationCode, String workMode, BigDecimal salaryMin,
            BigDecimal salaryMax, String salaryCurrency) {
        Job job = newOpenJob(title);
        job.setCategoryCode(categoryCode);
        job.setLocationCode(locationCode);
        job.setWorkMode(workMode);
        job.setSalaryMin(salaryMin);
        job.setSalaryMax(salaryMax);
        job.setSalaryCurrency(salaryCurrency);
        return jobRepository.save(job).getId();
    }

    private ResumeParsedPayload samplePayload() {
        return new ResumeParsedPayload(
                new ResumeParsedPayload.Contact("Nguyen Van Test", "test@example.com", null, null, null),
                List.of(),
                List.of(),
                List.of("Java"),
                List.of(),
                List.of(), null, null, null);
    }

    private UUID createCandidate() {
        User candidate = new User();
        candidate.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Ung Vien Test");
        return userRepository.save(candidate).getId();
    }

    // Resume DONE + embedding that qua ResumeEmbeddingStateService - CV "san sang" (R-V1).
    private void attachPrimaryResumeWithEmbedding(UUID candidateId, double similarity) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        UUID resumeId = resumeRepository.save(resume).getId();

        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText("Noi dung CV test");
        data.setData(samplePayload());
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion("resume-parse-v1");
        UUID parsedDataId = resumeParsedDataRepository.saveAndFlush(data).getId();
        resumeEmbeddingStateService.save(parsedDataId, vectorWithSimilarity(similarity));
    }

    // Resume co parseStatus truyen vao, KHONG co resume_parsed_data (embedding chua co) - dung cho
    // ca "CV dang cho" (R-V2, status khac FAILED) va "CV FAILED" (R-V1 coi nhu khong co CV).
    private UUID attachPrimaryResumeWithoutEmbedding(UUID candidateId, ParseStatus status) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(status);
        return resumeRepository.save(resume).getId();
    }

    // Tao/luu CandidateProfile voi mong muon + van ban dai dien (khong embedding) - dung chung cho
    // nhanh DESIRES (R-V5) va nhanh "ho so dang cho" (R-V4, khi co goi them attachProfileWithEmbedding).
    private CandidateProfile createProfile(
            UUID candidateId, String[] industryCodes, String[] locationCodes, String[] workModes,
            BigDecimal desiredSalaryMinVnd, String headline, String[] skills, String bio) {
        CandidateProfile profile = new CandidateProfile(candidateId);
        profile.setDesiredIndustryCodes(industryCodes == null ? new String[0] : industryCodes);
        profile.setDesiredLocationCodes(locationCodes == null ? new String[0] : locationCodes);
        profile.setDesiredWorkModes(workModes == null ? new String[0] : workModes);
        profile.setSkills(new String[0]);
        profile.setDesiredSalaryMin(desiredSalaryMinVnd);
        profile.setHeadline(headline);
        if (skills != null) {
            profile.setSkills(skills);
        }
        profile.setBio(bio);
        return candidateProfileRepository.saveAndFlush(profile);
    }

    // Ghi embedding THAT cho ho so qua CandidateProfileEmbeddingStateService (R-E5 - dieu kien theo
    // updated_at doc NGAY SAU KHI save() o tren, dung duong ma production di).
    private void embedProfile(CandidateProfile savedProfile, double similarity) {
        candidateProfileEmbeddingStateService.save(
                savedProfile.getId(), vectorWithSimilarity(similarity), "text-embedding-3-small", savedProfile.getUpdatedAt());
    }

    // Resume hop le de qua FK job_applications.resume_id - noi dung khong quan trong.
    private UUID createPlainResume(UUID candidateId) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(false);
        resume.setParseStatus(ParseStatus.DONE);
        return resumeRepository.save(resume).getId();
    }

    private void createApplication(UUID jobId, UUID candidateId, UUID resumeId, int cycle, ApplicationStatus status) {
        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setCandidateId(candidateId);
        application.setResumeId(resumeId);
        application.setRecruitmentCycle(cycle);
        application.setStatus(status);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        jobApplicationRepository.save(application);
    }

    private List<UUID> jobIdsOf(JobRecommendationResponse response) {
        return response.items().stream().map(item -> item.job().id()).toList();
    }

    // ===================================================================================
    // Bang doi chieu test cu -> test moi (muc 7 REQUIREMENT.md, 4/7 dong thuoc file nay)
    // ===================================================================================

    @Test
    void getRecommendations_cvBranch_jobsAboveAndBelowThreshold_onlyAboveThresholdIncluded() {
        UUID candidateId = createCandidate();
        attachPrimaryResumeWithEmbedding(candidateId, 1.0);
        UUID jobAboveId = createOpenJobWithEmbedding("Vi tri tren nguong", 0.55);
        UUID jobBelowId = createOpenJobWithEmbedding("Vi tri duoi nguong", 0.25);

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(response.status()).isEqualTo(JobRecommendationStatus.READY);
        assertThat(response.source()).isEqualTo(JobRecommendationSource.CV);
        // contains/doesNotContain (khong containsExactly): DB Testcontainers dung chung ca suite co
        // the con job OPEN tu file test KHAC trung huong voi RESUME_VECTOR - cung ly do voi test cu.
        assertThat(jobIdsOf(response)).contains(jobAboveId);
        assertThat(jobIdsOf(response)).doesNotContain(jobBelowId);
    }

    @Test
    void getRecommendations_noCvNoProfileNoDesires_returnsNoData() {
        UUID candidateId = createCandidate();

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(response.status()).isEqualTo(JobRecommendationStatus.NO_DATA);
        assertThat(response.source()).isNull();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void getRecommendations_cvBranch_jobClosedBetweenCalls_excludedImmediately() {
        UUID candidateId = createCandidate();
        attachPrimaryResumeWithEmbedding(candidateId, 1.0);
        UUID jobId = createOpenJobWithEmbedding("Vi tri se dong", 0.6);

        assertThat(jobIdsOf(service.getRecommendationsForCandidate(candidateId))).contains(jobId);

        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.CLOSED);
        jobRepository.save(job);

        // Khong con cache nen khong can "lot lam moi ke tiep" - goi lai NGAY la thay ngay (R-G1).
        assertThat(jobIdsOf(service.getRecommendationsForCandidate(candidateId))).doesNotContain(jobId);
    }

    // Bang chung cho tieu chi nghiem thu cua F1/FR-U04 (ke thua nguyen ven tu test cu da xoa): "ung
    // vien nganh IT khong nhan goi y viec ke toan". Hai con so 0.49/0.355 giu NGUYEN, lay truc tiep
    // tu do thuc nghiem that (2 CV x 7 job, Postgres/OpenAI text-embedding-3-small that) - xem
    // JobRecommendationCandidateService.MIN_SIMILARITY_SCORE.
    @Test
    void getRecommendations_cvBranch_doesNotRecommendJobOutsideSimilarityThreshold() {
        UUID candidateId = createCandidate();
        attachPrimaryResumeWithEmbedding(candidateId, 1.0);
        UUID itJobId = createOpenJobWithEmbedding("Senior Java Backend Developer", 0.49);
        UUID accountingJobId = createOpenJobWithEmbedding("Ke toan tong hop", 0.355);

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(jobIdsOf(response)).contains(itJobId);
        assertThat(jobIdsOf(response)).doesNotContain(accountingJobId);
    }

    // ===================================================================================
    // Test moi can them (muc 7 REQUIREMENT.md, danh so 1-10 + test dot 3) - cac so khac (11-15)
    // nam o JobPublicIntegrationTest/JobRecommendationCandidateControllerIntegrationTest.
    // ===================================================================================

    // #1 - Nhanh ho so (R-V6.2): khong ap nguong 0.40.
    @Test
    void getRecommendations_profileBranch_doesNotApplyThreshold() {
        UUID candidateId = createCandidate();
        CandidateProfile profile =
                createProfile(candidateId, null, null, null, null, "Ky su phan mem", new String[] {"Java"}, null);
        embedProfile(profile, 1.0);
        UUID belowThresholdJobId = createOpenJobWithEmbedding("Vi tri duoi nguong nhung van vao", 0.25);

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(response.source()).isEqualTo(JobRecommendationSource.PROFILE);
        assertThat(jobIdsOf(response)).contains(belowThresholdJobId);
    }

    // #2 - Nhanh mong muon (R-V6.3): sap theo thoi gian dang, tie-break on dinh qua nhieu lan goi
    // (KHONG dung UUID.compareTo() de doan thu tu - doc lai tu chinh service, giong khuon
    // JobPublicIntegrationTest.list_sortNewest_tieBreaksById_stableAcrossRepeatedCalls).
    @Test
    void getRecommendations_desiresOnlyBranch_sortedWithStableTieBreak() {
        UUID candidateId = createCandidate();
        createProfile(candidateId, new String[] {"IT_SOFTWARE"}, null, null, null, null, null, null);
        createOpenJobWithFields("Desires Tie A", "IT_SOFTWARE", null, null, null, null, null);
        createOpenJobWithFields("Desires Tie B", "IT_SOFTWARE", null, null, null, null, null);
        createOpenJobWithFields("Desires Tie C", "IT_SOFTWARE", null, null, null, null, null);

        JobRecommendationResponse first = service.getRecommendationsForCandidate(candidateId);
        JobRecommendationResponse second = service.getRecommendationsForCandidate(candidateId);

        assertThat(first.source()).isEqualTo(JobRecommendationSource.DESIRES);
        assertThat(jobIdsOf(first)).hasSize(3);
        assertThat(jobIdsOf(second)).isEqualTo(jobIdsOf(first));
    }

    // #3a - CV dang cho (chua FAILED), khong mong muon -> PREPARING, source=CV.
    @Test
    void getRecommendations_cvPending_returnsPreparingWithSourceCv() {
        UUID candidateId = createCandidate();
        attachPrimaryResumeWithoutEmbedding(candidateId, ParseStatus.PROCESSING);

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(response.status()).isEqualTo(JobRecommendationStatus.PREPARING);
        assertThat(response.source()).isEqualTo(JobRecommendationSource.CV);
    }

    // #3b - Ho so dang cho (van ban dai dien khac rong, embedding chua co), khong CV, khong mong
    // muon -> PREPARING, source=PROFILE.
    @Test
    void getRecommendations_profilePending_returnsPreparingWithSourceProfile() {
        UUID candidateId = createCandidate();
        createProfile(candidateId, null, null, null, null, "Dang cho tinh embedding", null, null);

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(response.status()).isEqualTo(JobRecommendationStatus.PREPARING);
        assertThat(response.source()).isEqualTo(JobRecommendationSource.PROFILE);
    }

    // #4 - CV FAILED (coi nhu khong co CV, R-V1), khong mong muon, khong ho so -> NO_DATA (KHONG
    // phai PREPARING).
    @Test
    void getRecommendations_cvFailed_noDesiresNoProfile_returnsNoData() {
        UUID candidateId = createCandidate();
        attachPrimaryResumeWithoutEmbedding(candidateId, ParseStatus.FAILED);

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(response.status()).isEqualTo(JobRecommendationStatus.NO_DATA);
        assertThat(response.source()).isNull();
    }

    // #5 - CV dang cho (chua FAILED) NHUNG co mong muon -> roi thang vao DESIRES, KHONG PREPARING
    // (R-V6: nhanh 3 dung truoc nhanh 4).
    @Test
    void getRecommendations_cvPendingWithDesires_prefersDesiresOverPreparing() {
        UUID candidateId = createCandidate();
        attachPrimaryResumeWithoutEmbedding(candidateId, ParseStatus.PENDING);
        createProfile(candidateId, new String[] {"IT_SOFTWARE"}, null, null, null, null, null, null);

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(response.status()).isNotEqualTo(JobRecommendationStatus.PREPARING);
        assertThat(response.source()).isEqualTo(JobRecommendationSource.DESIRES);
    }

    // #6 - Ma NULL van vao goi y (R-N1): job category_code=NULL van xuat hien khi ung vien co khai
    // nganh khac.
    @Test
    void getRecommendations_desiresOnlyBranch_jobWithNullCategoryCodeStillIncluded() {
        UUID candidateId = createCandidate();
        createProfile(candidateId, new String[] {"IT_SOFTWARE"}, null, null, null, null, null, null);
        UUID nullCategoryJobId = createOpenJobWithFields("Null Category Job", null, null, null, null, null, null);

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(jobIdsOf(response)).contains(nullCategoryJobId);
    }

    // #7 - Loai tru don da nop trong CHINH chu ky hien tai (R-C1), bat ke trang thai (WITHDRAWN);
    // mo lai (chu ky tang) thi goi y lai duoc.
    @Test
    void getRecommendations_excludesJobWithWithdrawnApplicationInCurrentCycle_includesAgainAfterReopen() {
        UUID candidateId = createCandidate();
        createProfile(candidateId, new String[] {"IT_SOFTWARE"}, null, null, null, null, null, null);
        UUID jobId = createOpenJobWithFields("Vi tri da rut don", "IT_SOFTWARE", null, null, null, null, null);
        Job job = jobRepository.findById(jobId).orElseThrow();
        UUID resumeId = createPlainResume(candidateId);
        createApplication(jobId, candidateId, resumeId, job.getRecruitmentCycle(), ApplicationStatus.WITHDRAWN);

        JobRecommendationResponse beforeReopen = service.getRecommendationsForCandidate(candidateId);
        assertThat(jobIdsOf(beforeReopen)).doesNotContain(jobId);

        // Gia lap HR mo lai tin da dong: recruitment_cycle tang, don cu van thuoc chu ky CU.
        job.setRecruitmentCycle(job.getRecruitmentCycle() + 1);
        jobRepository.save(job);

        JobRecommendationResponse afterReopen = service.getRecommendationsForCandidate(candidateId);
        assertThat(jobIdsOf(afterReopen)).contains(jobId);
    }

    // #8 - matchedConditions: khop du 4 dieu kien, bien luong dung/lech 1 don vi, Thoa thuan/ngoai
    // te khong hien chip luong.
    @Test
    void getRecommendations_matchedConditions_allFourConditionsMatched() {
        UUID candidateId = createCandidate();
        createProfile(
                candidateId,
                new String[] {"IT_SOFTWARE"},
                new String[] {"HA_NOI"},
                new String[] {"ONSITE"},
                BigDecimal.valueOf(20_000_000),
                null,
                null,
                null);
        UUID jobId = createOpenJobWithFields(
                "Job Khop Du 4 Dieu Kien",
                "IT_SOFTWARE",
                "HA_NOI",
                "ONSITE",
                BigDecimal.valueOf(20_000_000),
                BigDecimal.valueOf(25_000_000),
                "VND");

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        JobRecommendationItemResponse item = response.items().stream()
                .filter(i -> i.job().id().equals(jobId))
                .findFirst()
                .orElseThrow();
        assertThat(item.matchedConditions())
                .containsExactly(
                        catalogRegistry.provinceLabel("HA_NOI"),
                        catalogRegistry.industryLabel("IT_SOFTWARE"),
                        "Tại văn phòng",
                        "Lương đạt mong muốn");
    }

    @Test
    void getRecommendations_matchedConditions_salaryBoundary_exactMatchIncluded_oneUnitBelowExcluded() {
        UUID candidateId = createCandidate();
        createProfile(
                candidateId,
                new String[] {"IT_SOFTWARE"},
                null,
                null,
                BigDecimal.valueOf(20_000_000),
                null,
                null,
                null);
        UUID exactMatchJobId = createOpenJobWithFields(
                "Luong Dung Bien", "IT_SOFTWARE", null, null, null, BigDecimal.valueOf(20_000_000), "VND");
        UUID belowJobId = createOpenJobWithFields(
                "Luong Lech 1 Don Vi", "IT_SOFTWARE", null, null, null, BigDecimal.valueOf(19_999_999), "VND");

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        List<String> exactMatchConditions = conditionsFor(response, exactMatchJobId);
        List<String> belowConditions = conditionsFor(response, belowJobId);
        assertThat(exactMatchConditions).contains("Lương đạt mong muốn");
        assertThat(belowConditions).doesNotContain("Lương đạt mong muốn");
    }

    @Test
    void getRecommendations_matchedConditions_thoaThuanAndForeignCurrency_noSalaryChip() {
        UUID candidateId = createCandidate();
        createProfile(
                candidateId,
                new String[] {"IT_SOFTWARE"},
                null,
                null,
                BigDecimal.valueOf(20_000_000),
                null,
                null,
                null);
        UUID thoaThuanJobId =
                createOpenJobWithFields("Luong Thoa Thuan", "IT_SOFTWARE", null, null, null, null, null);
        UUID foreignCurrencyJobId = createOpenJobWithFields(
                "Luong Ngoai Te", "IT_SOFTWARE", null, null, BigDecimal.valueOf(50_000_000), null, "USD");

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(conditionsFor(response, thoaThuanJobId)).doesNotContain("Lương đạt mong muốn");
        assertThat(conditionsFor(response, foreignCurrencyJobId)).doesNotContain("Lương đạt mong muốn");
    }

    private List<String> conditionsFor(JobRecommendationResponse response, UUID jobId) {
        return response.items().stream()
                .filter(i -> i.job().id().equals(jobId))
                .findFirst()
                .orElseThrow()
                .matchedConditions();
    }

    // #9 - Luong/hinh thuc KHONG loai tin (R-M4): job khong khop luong/hinh thuc mong muon van vao
    // items, chi thieu chip tuong ung.
    @Test
    void getRecommendations_salaryAndWorkModeMismatch_jobStillIncludedWithoutThoseChips() {
        UUID candidateId = createCandidate();
        createProfile(
                candidateId,
                new String[] {"IT_SOFTWARE"},
                null,
                new String[] {"REMOTE"},
                BigDecimal.valueOf(50_000_000),
                null,
                null,
                null);
        UUID mismatchJobId = createOpenJobWithFields(
                "Khong Khop Luong Hinh Thuc",
                "IT_SOFTWARE",
                null,
                "ONSITE",
                null,
                BigDecimal.valueOf(20_000_000),
                "VND");

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(jobIdsOf(response)).contains(mismatchJobId);
        List<String> conditions = conditionsFor(response, mismatchJobId);
        assertThat(conditions).doesNotContain("Tại văn phòng", "Lương đạt mong muốn");
    }

    // #10 - Gioi han 6 (R-L1): seed 7 job thoa dieu kien, chi tra dung 6.
    @Test
    void getRecommendations_moreThanLimitMatches_returnsExactlySixItems() {
        UUID candidateId = createCandidate();
        createProfile(candidateId, new String[] {"IT_SOFTWARE"}, null, null, null, null, null, null);
        for (int i = 0; i < 7; i++) {
            createOpenJobWithFields("Gioi Han 6 - " + i, "IT_SOFTWARE", null, null, null, null, null);
        }

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(response.items()).hasSize(6);
    }

    // Dot 3 (da duyet): ho so co embedding CU nhung van ban dai dien HIEN TAI rong (headline/
    // skills/bio deu rong) -> KHONG duoc coi la "ho so san sang" (R-V3), phai roi xuong nhanh ke
    // tiep (o day la NO_DATA vi khong CV, khong mong muon).
    @Test
    void getRecommendations_profileVectorPresentButRepresentativeTextEmpty_fallsThroughToNoData() {
        UUID candidateId = createCandidate();
        CandidateProfile profile = createProfile(candidateId, null, null, null, null, null, null, null);
        // Ghi embedding THAT du van ban dai dien dang rong - mo phong tinh huong "le ra khong xay
        // ra" (R-E2 phai dat embedding=NULL khi van ban rong) de kiem chung phong thu chieu sau.
        embedProfile(profile, 1.0);

        JobRecommendationResponse response = service.getRecommendationsForCandidate(candidateId);

        assertThat(response.source()).isNotEqualTo(JobRecommendationSource.PROFILE);
        assertThat(response.status()).isEqualTo(JobRecommendationStatus.NO_DATA);
    }

    // Phong thu HR (R-P1/R-P3, phan dich vu) - response khong chua field diem so nao, chi la kiem
    // tra kieu du lieu (JobSummaryResponse) khong co field similarityScore - bo sung cho test #13
    // (muc HTTP day du nam o JobRecommendationCandidateControllerIntegrationTest).
    @Test
    void getRecommendations_doesNotWriteAnyRowToDatabase_withinSingleCall() {
        UUID candidateId = createCandidate();
        attachPrimaryResumeWithEmbedding(candidateId, 1.0);
        createOpenJobWithEmbedding("Vi tri kiem khong ghi DB", 0.6);
        long jobCountBefore = jobRepository.count();

        service.getRecommendationsForCandidate(candidateId);
        service.getRecommendationsForCandidate(candidateId);

        assertThat(jobRepository.count()).isEqualTo(jobCountBefore);
    }
}
