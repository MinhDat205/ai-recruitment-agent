package com.recruitment.jobrecommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobEmbeddingStateService;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeEmbeddingStateService;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Fixture ghi embedding qua JobEmbeddingStateService/ResumeEmbeddingStateService (KHONG goi
// jobEmbeddingRepository.upsertEmbedding/resumeParsedDataRepository.updateEmbedding truc tiep) -
// hai StateService nay la noi @Transactional nam that trong production (xem
// JobEmbeddingOrchestrator/ResumeEmbeddingOrchestrator), moi loi goi tu mo va tu commit mot
// transaction rieng. Nho vay khong can @Transactional bao ngoai cho phan setup, va hai lan goi
// cacheService.refreshOne() trong cung mot test (refreshOne_calledAgainAfterJobClosed...) khong bi
// vo tinh long chung mot transaction voi phan setup hay voi nhau.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class JobRecommendationCacheServiceTest {

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
    private JobRecommendationRepository jobRecommendationRepository;

    @Autowired
    private JobRecommendationCacheService cacheService;

    // Cung ky thuat vector don vi voi JobEmbeddingRepositoryTest - xem comment o do.
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

    private UUID createOpenJobWithEmbedding(String title, double similarity) {
        UUID companyId = hrAndCompany();
        Job job = new Job();
        job.setCompanyId(companyId);
        job.setCreatedBy(companyRepository.findById(companyId).orElseThrow().getOwnerId());
        job.setTitle(title);
        job.setDescription("Mo ta cong viec test");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        UUID jobId = jobRepository.save(job).getId();
        // Goi qua JobEmbeddingStateService (KHONG goi jobEmbeddingRepository.upsertEmbedding truc
        // tiep) - StateService la noi @Transactional nam that, dung duong ma production di (xem
        // JobEmbeddingOrchestrator.processOne goi JobEmbeddingStateService.save). Method nay tu mo va
        // tu commit mot transaction rieng cua chinh no - khong can @Transactional tren test method
        // nao boc ngoai, khong can TransactionTemplate thu cong.
        jobEmbeddingStateService.save(jobId, vectorWithSimilarity(similarity), "text-embedding-3-small");
        return jobId;
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

    private UUID candidateWithEmbeddedPrimaryResume() {
        User candidate = new User();
        candidate.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Ung Vien Test");
        UUID candidateId = userRepository.save(candidate).getId();

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
        // Goi qua ResumeEmbeddingStateService (KHONG goi resumeParsedDataRepository.updateEmbedding
        // truc tiep) - StateService la noi @Transactional nam that, dung duong ma production di (xem
        // ResumeEmbeddingOrchestrator.processOne goi ResumeEmbeddingStateService.save). Method nay tu
        // mo va tu commit mot transaction rieng cua chinh no - khong can @Transactional tren test
        // method nao boc ngoai, khong can TransactionTemplate thu cong.
        resumeEmbeddingStateService.save(parsedDataId, RESUME_VECTOR);

        return candidateId;
    }

    @Test
    @Transactional
    void refreshOne_jobsAboveAndBelowThreshold_onlyAboveThresholdCached() {
        UUID candidateId = candidateWithEmbeddedPrimaryResume();
        UUID jobAboveId = createOpenJobWithEmbedding("Vi tri tren nguong", 0.55);
        UUID jobBelowId = createOpenJobWithEmbedding("Vi tri duoi nguong", 0.25);

        cacheService.refreshOne(candidateId);

        // contains/doesNotContain (khong phai containsExactly - loi that gap trong Dot 5): DB
        // Testcontainers dung chung giua toan bo suite co the con job OPEN tu cac file test KHAC
        // (vd JobEmbeddingOrchestratorTest) voi embedding vector cung huong voi RESUME_VECTOR (chi
        // khac 0 o truc 0) - cosine similarity CHI phu thuoc huong, khong phu thuoc do lon, nen
        // nhung job do co the "giong 100%" mot cach tinh co du khong lien quan gi ve mat nghiep vu.
        // containsExactly gia dinh sai la job_recommendations chi chua dung nhung gi test nay tao ra.
        List<UUID> cachedJobIds = jobRecommendationRepository
                .findByCandidateIdOrderBySimilarityScoreDescJobIdAsc(candidateId)
                .stream()
                .map(JobRecommendation::getJobId)
                .toList();
        assertThat(cachedJobIds).contains(jobAboveId);
        assertThat(cachedJobIds).doesNotContain(jobBelowId);
    }

    @Test
    void refreshOne_candidateWithoutPrimaryResume_doesNothing() {
        User candidate = new User();
        candidate.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Ung Vien Khong Co CV");
        UUID candidateId = userRepository.save(candidate).getId();

        cacheService.refreshOne(candidateId);

        assertThat(jobRecommendationRepository.findByCandidateIdOrderBySimilarityScoreDescJobIdAsc(candidateId))
                .isEmpty();
    }

    // Kich ban chinh cua muc "Xoa-roi-chen": job dang trong cache bi HR dong sau do - lan
    // refreshOne KE TIEP phai tu dong don job do khoi cache, khong con logic "tim dong loi thoi"
    // rieng nao ca (Plan Mode F1 muc E).
    //
    // KHONG @Transactional o day (khac hau het test khac trong file) - day la diem khac biet co
    // chu dich, khong phai bo sot. refreshOne() trong production luon chay tu JobRecommendationCacheScheduler,
    // MOI lan goi la MOT transaction rieng biet, tu commit that khi ket thuc (xem
    // JobRecommendationCacheService - @Transactional tren refreshOne). Neu boc test nay bang
    // @Transactional o muc method, hai lan goi cacheService.refreshOne() se LONG vao CHUNG mot
    // transaction (propagation REQUIRED), tao ra dieu kien khong bao gio xay ra trong production:
    // delete/insert cua lan goi truoc chua chac da flush truoc khi lan goi sau doc lai, tuy thuoc
    // thoi diem auto-flush khong on dinh cua Hibernate voi native query - da gap loi that
    // (DataIntegrityViolationException vi pham uq_reco) khi chay full suite du test nay tu chay rieng
    // le van xanh. Bo @Transactional here de moi refreshOne() tu commit that vao Postgres
    // Testcontainers, dung hanh vi production - candidateId/jobId/resumeId deu la UUID ngau nhien
    // moi sinh nen khong anh huong file test khac (khong roll back nhung cung khong ai doc lai
    // candidateId nay ngoai chinh test nay).
    @Test
    void refreshOne_calledAgainAfterJobClosed_removesClosedJobFromCache() {
        UUID candidateId = candidateWithEmbeddedPrimaryResume();
        UUID jobId = createOpenJobWithEmbedding("Vi tri se dong", 0.6);
        cacheService.refreshOne(candidateId);
        assertThat(cachedJobIds(candidateId)).contains(jobId);

        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.CLOSED);
        jobRepository.save(job);

        cacheService.refreshOne(candidateId);

        // doesNotContain (khong phai isEmpty() - cung ly do voi test tren): cache co the KHONG rong
        // sau lan refresh thu hai neu co job rac tu file test khac trung huong voi RESUME_VECTOR -
        // dieu do khong lien quan den dieu test nay thuc su can kiem: job VUA DONG phai bien mat.
        assertThat(cachedJobIds(candidateId)).doesNotContain(jobId);
    }

    private List<UUID> cachedJobIds(UUID candidateId) {
        return jobRecommendationRepository
                .findByCandidateIdOrderBySimilarityScoreDescJobIdAsc(candidateId)
                .stream()
                .map(JobRecommendation::getJobId)
                .toList();
    }

    // Bang chung duy nhat trong CI cho tieu chi nghiem thu chinh cua PHASES.md F1: "ung vien nganh
    // IT khong nhan goi y viec ke toan". Hai con so 0.49 va 0.355 KHONG phai tuy chon - lay truc
    // tiep tu do thuc nghiem that (2 CV x 7 job, Postgres that, OpenAI text-embedding-3-small that,
    // chay ngoai phien implement, xem JobRecommendationCacheService.MIN_SIMILARITY_SCORE):
    // - 0.49 ~ similarity giua cv-hai-cot va job "Senior Java Backend Developer" (nhom IT, do thuc
    //   te trong khoang 0.4307-0.4952 - lay can tren cua khoang do).
    // - 0.355 ~ similarity giua cv-hai-cot va job nganh ke toan (do thuc te 0.3553).
    // Test nay MO PHONG LAI dung hai khoang cach do bang vector nhan tao (khong goi OpenAI that -
    // xem ky thuat vectorWithSimilarity o tren), de kiem chung NGUONG 0.40 that su tach duoc hai
    // nhom nay trong code, khong chi tren giay. Chat luong sinh embedding that (co phan biet dung
    // nganh nghe hay khong) da duoc xac nhan boi chinh phep do thuc nghiem noi tren, nam ngoai pham
    // vi mot test tu dong co the kiem (khong the goi OpenAI that trong test - xem CLAUDE.md muc 7).
    @Test
    @Transactional
    void refreshOne_candidateITResume_doesNotRecommendAccountingJob() {
        UUID candidateId = candidateWithEmbeddedPrimaryResume();
        UUID itJobId = createOpenJobWithEmbedding("Senior Java Backend Developer", 0.49);
        UUID accountingJobId = createOpenJobWithEmbedding("Ke toan tong hop", 0.355);

        cacheService.refreshOne(candidateId);

        List<UUID> cachedJobIds = jobRecommendationRepository
                .findByCandidateIdOrderBySimilarityScoreDescJobIdAsc(candidateId)
                .stream()
                .map(JobRecommendation::getJobId)
                .toList();
        assertThat(cachedJobIds).contains(itJobId);
        assertThat(cachedJobIds).doesNotContain(accountingJobId);
    }
}
