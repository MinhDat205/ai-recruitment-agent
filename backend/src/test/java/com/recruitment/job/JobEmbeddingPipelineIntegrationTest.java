package com.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.dto.JobRequest;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

// Tich hop pipeline sinh embedding cho job: JobOwnerService.update() (hook xoa embedding cu) +
// logic quet cua JobEmbeddingScheduler. Ba diem can hieu dung ve pham vi test nay:
//
// 1. Test nay KHONG cham class JobEmbeddingScheduler. Bean do bi tat trong profile test qua
//    app.job-embedding.enabled=false (application-test.yml) - dung quy uoc chung cua ca du an tu D1
//    (ResumeParsingScheduler, ScoringRunScheduler, CvImprovementScheduler... tat ca deu tat kieu nay
//    trong test, tranh scheduler tu tick gay nhieu cac @SpringBootTest khac dung chung context
//    cache). Autowire JobEmbeddingScheduler truc tiep se nem UnsatisfiedDependencyException vi bean
//    khong ton tai trong context - da gap loi nay that khi viet ban dau, sua bang cach bo autowire.
//
// 2. pollOnce() (private, ben duoi) lap lai DUNG logic cua
//    JobEmbeddingScheduler.pollJobsNeedingEmbedding(): goi findOpenJobIdsNeedingEmbedding roi lap
//    qua orchestrator.processOne() cho tung id. Day la cach duy nhat kiem duoc hanh vi "quet" ma
//    khong can bean scheduler that.
//
// 3. Gioi han da biet, AP DUNG CHUNG CHO MOI SCHEDULER TRONG DU AN (D1/D2/D3/D4/F2 va F1 deu vay,
//    khong phai thieu sot rieng cua F1): hai thu KHONG duoc test tu dong nao phu - "@Scheduled chay
//    dung chu ky" (fixedDelayString co doc dung gia tri config khong) va "@ConditionalOnProperty
//    bat/tat dung" (bean co thuc su bien mat khoi context khi enabled=false khong, hay chi la gia
//    dinh). Ca hai deu chi duoc tin tuong qua doc code, chua co test nao xac nhan thuc nghiem. Ghi
//    vao walkthrough o dot cuoi F1.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class JobEmbeddingPipelineIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobEmbeddingRepository jobEmbeddingRepository;

    @Autowired
    private JobEmbeddingOrchestrator orchestrator;

    @Autowired
    private JobOwnerService jobOwnerService;

    @Autowired
    private EmbeddingModel embeddingModel;

    private UUID hrId;
    private UUID companyId;

    @BeforeEach
    void setUpHrAndCompanyAndMock() {
        Mockito.reset(embeddingModel);
        doReturn(fakeResponse(1536, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        User hr = new User();
        hr.setEmail("hr-" + UUID.randomUUID() + "@example.com");
        hr.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        hr.setRole(Role.HR);
        hr.setFullName("Nha Tuyen Dung Test");
        hrId = userRepository.save(hr).getId();

        Company company = new Company();
        company.setOwnerId(hrId);
        company.setName("Cong ty Test " + UUID.randomUUID());
        companyId = companyRepository.save(company).getId();
    }

    // vector[0] = 0.1f (KHONG de vector toan so 0) - cung ly do voi JobEmbeddingOrchestratorTest:
    // vector 0 gay NaN o cosine distance, Postgres coi NaN >= x la true, job vector suy bien se
    // vuot moi nguong similarity. File nay cung KHONG @Transactional nen se ghi that vao Postgres
    // Testcontainers dung chung - da gay loi that o JobRecommendationCacheServiceTest trong full
    // suite (xem yeu cau review sau Dot 5).
    private EmbeddingResponse fakeResponse(int dimensions, String model) {
        float[] vector = new float[dimensions];
        if (dimensions > 0) {
            vector[0] = 0.1f;
        }
        Embedding embedding = new Embedding(vector, 0);
        return new EmbeddingResponse(List.of(embedding), new EmbeddingResponseMetadata(model, new DefaultUsage(10, 0)));
    }

    private UUID createOpenJob(String title, String description, String categoryCode) {
        Job job = new Job();
        job.setCompanyId(companyId);
        job.setCreatedBy(hrId);
        job.setTitle(title);
        job.setDescription(description);
        job.setCategoryCode(categoryCode);
        job.setLocationCode("HA_NOI");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        return jobRepository.save(job).getId();
    }

    private JobRequest sameRequestExcept(Job job, String title, String description, String categoryCode) {
        return new JobRequest(
                title,
                description,
                job.getRequirements(),
                categoryCode,
                job.getLocationCode(),
                job.getEmploymentType(),
                job.getWorkMode(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.getDeadline());
    }

    // findByJobId (khong phai findAll().stream().filter() - xem yeu cau review Dot 3): scope dung
    // theo jobId cua chinh test nay, khong quet toan bang job_embeddings dung chung giua 56 file test.
    private boolean hasEmbedding(UUID jobId) {
        return jobEmbeddingRepository.findByJobId(jobId).isPresent();
    }

    // Thay cho jobEmbeddingScheduler.pollJobsNeedingEmbedding() (bean bi tat trong test, xem comment
    // dau class) - lap lai dung logic do: query chon lo roi goi orchestrator cho tung job.
    private void pollOnce() {
        List<UUID> jobIds = jobEmbeddingRepository.findOpenJobIdsNeedingEmbedding(PageRequest.of(0, 10));
        for (UUID jobId : jobIds) {
            orchestrator.processOne(jobId);
        }
    }

    @Test
    void pollJobsNeedingEmbedding_openJobWithoutEmbedding_createsEmbedding() {
        UUID jobId = createOpenJob("Java Developer", "Phat trien he thong backend", "IT_SOFTWARE");

        pollOnce();

        assertThat(hasEmbedding(jobId)).isTrue();
    }

    @Test
    void update_descriptionChanged_deletesEmbeddingSoNextPollRegenerates() {
        UUID jobId = createOpenJob("Java Developer", "Mo ta cu", "IT_SOFTWARE");
        pollOnce();
        assertThat(hasEmbedding(jobId)).isTrue();

        Job job = jobRepository.findById(jobId).orElseThrow();
        jobOwnerService.update(
                hrId, jobId, sameRequestExcept(job, job.getTitle(), "Mo ta MOI hoan toan khac", job.getCategoryCode()));

        assertThat(hasEmbedding(jobId)).isFalse();

        pollOnce();
        assertThat(hasEmbedding(jobId)).isTrue();
    }

    @Test
    void update_categoryChanged_deletesEmbeddingSoNextPollRegenerates() {
        UUID jobId = createOpenJob("Java Developer", "Mo ta khong doi", "IT_SOFTWARE");
        pollOnce();
        assertThat(hasEmbedding(jobId)).isTrue();

        Job job = jobRepository.findById(jobId).orElseThrow();
        jobOwnerService.update(
                hrId, jobId, sameRequestExcept(job, job.getTitle(), job.getDescription(), "ACCOUNTING_AUDIT"));

        assertThat(hasEmbedding(jobId)).isFalse();

        pollOnce();
        assertThat(hasEmbedding(jobId)).isTrue();
    }

    @Test
    void update_unrelatedFieldChanged_doesNotDeleteEmbedding() {
        UUID jobId = createOpenJob("Java Developer", "Mo ta khong doi", "IT_SOFTWARE");
        pollOnce();
        assertThat(hasEmbedding(jobId)).isTrue();

        Job job = jobRepository.findById(jobId).orElseThrow();
        JobRequest sameTextDifferentLocation = new JobRequest(
                job.getTitle(),
                job.getDescription(),
                job.getRequirements(),
                job.getCategoryCode(),
                "DA_NANG",
                job.getEmploymentType(),
                job.getWorkMode(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.getDeadline());
        jobOwnerService.update(hrId, jobId, sameTextDifferentLocation);

        assertThat(hasEmbedding(jobId)).isTrue();
    }
}
