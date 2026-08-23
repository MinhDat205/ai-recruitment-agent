package com.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
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

// Mock o tang EmbeddingModel (khong mock EmbeddingService truc tiep) - dung tien le
// ResumeParsingOrchestratorTest/CvImprovementOrchestratorTest: mock model that thap nhat, de
// EmbeddingService that chay nguyen trong test.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class JobEmbeddingOrchestratorTest {

    @Autowired
    private JobEmbeddingOrchestrator orchestrator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobEmbeddingRepository jobEmbeddingRepository;

    @Autowired
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void resetEmbeddingModelMock() {
        Mockito.reset(embeddingModel);
    }

    // vector[0] = 0.1f (KHONG de vector toan so 0) - vector 0 co norm bang 0, cosine distance voi
    // bat ky vector nao khac la NaN; Postgres coi NaN LON HON moi so khac khi so sanh thu tu
    // (>= 0.4 tra ve true), khien job co vector suy bien vuot moi nguong similarity va loi vao ket
    // qua goi y cho MOI ung vien. Test o day KHONG @Transactional (orchestrator tu commit qua
    // stateService), nen vector 0 se ton tai THAT trong Postgres Testcontainers dung chung sau khi
    // test ket thuc - gay loi NaN o JobRecommendationCacheServiceTest chay sau trong cung full suite
    // (da gap loi nay that, xem yeu cau review sau Dot 5).
    private EmbeddingResponse fakeResponse(int dimensions, String model) {
        float[] vector = new float[dimensions];
        if (dimensions > 0) {
            vector[0] = 0.1f;
        }
        Embedding embedding = new Embedding(vector, 0);
        return new EmbeddingResponse(List.of(embedding), new EmbeddingResponseMetadata(model, new DefaultUsage(10, 0)));
    }

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

    private UUID createJob(JobStatus status) {
        UUID companyId = hrAndCompany();
        Job job = new Job();
        job.setCompanyId(companyId);
        job.setCreatedBy(companyRepository.findById(companyId).orElseThrow().getOwnerId());
        job.setTitle("Senior Java Developer");
        job.setDescription("Phat trien he thong backend bang Java, Spring Boot, PostgreSQL");
        job.setCategory("Cong nghe thong tin");
        job.setStatus(status);
        job.setRecruitmentCycle(1);
        job = jobRepository.save(job);
        return job.getId();
    }

    // findByJobId (khong phai findAll().stream().filter() - xem yeu cau review Dot 3): scope dung
    // theo jobId cua chinh test nay, khong quet toan bang job_embeddings dung chung giua 56 file test.
    private boolean hasEmbedding(UUID jobId) {
        return jobEmbeddingRepository.findByJobId(jobId).isPresent();
    }

    @Test
    void processOne_openJob_savesEmbeddingWithJobIdAndModel() {
        UUID jobId = createJob(JobStatus.OPEN);
        doReturn(fakeResponse(1536, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        orchestrator.processOne(jobId);

        JobEmbedding saved = jobEmbeddingRepository.findByJobId(jobId).orElseThrow();
        assertThat(saved.getModel()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void processOne_embeddingApiThrows_writesNothingAndCanRetryNextPoll() {
        UUID jobId = createJob(JobStatus.OPEN);
        doThrow(new RuntimeException("loi mang gia lap")).when(embeddingModel).embedForResponse(anyList());

        orchestrator.processOne(jobId);

        List<UUID> stillNeeding = jobEmbeddingRepository.findOpenJobIdsNeedingEmbedding(PageRequest.of(0, 10));
        assertThat(stillNeeding).contains(jobId);
    }

    @Test
    void processOne_draftJob_doesNotSaveEmbedding() {
        UUID jobId = createJob(JobStatus.DRAFT);
        doReturn(fakeResponse(1536, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        orchestrator.processOne(jobId);

        assertThat(hasEmbedding(jobId)).isFalse();
    }

    @Test
    void processOne_pausedJob_doesNotSaveEmbedding() {
        UUID jobId = createJob(JobStatus.PAUSED);
        doReturn(fakeResponse(1536, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        orchestrator.processOne(jobId);

        assertThat(hasEmbedding(jobId)).isFalse();
    }

    @Test
    void processOne_closedJob_doesNotSaveEmbedding() {
        UUID jobId = createJob(JobStatus.CLOSED);
        doReturn(fakeResponse(1536, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        orchestrator.processOne(jobId);

        assertThat(hasEmbedding(jobId)).isFalse();
    }
}
