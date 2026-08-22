package com.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.embedding.EmbeddingTextFormat;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// @Transactional o MUC METHOD cho MOI test trong file nay - can thiet cho upsertEmbedding/
// deleteByJobId (@Modifying can transaction dang mo de thuc thi) va de KHONG COMMIT VINH VIEN du
// lieu lam nhiem cac test khac trong CUNG mot Testcontainers dung chung. Dung tien le
// CvImprovementRequestRepositoryTest.claimForProcessing (xac minh thuc nghiem: goi @Modifying
// native query truc tiep tu test khong co @Transactional nem TransactionRequiredException).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class JobEmbeddingRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobEmbeddingRepository jobEmbeddingRepository;

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
        job.setTitle("Vi tri test " + UUID.randomUUID());
        job.setDescription("Mo ta cong viec test");
        job.setStatus(status);
        job.setRecruitmentCycle(1);
        job = jobRepository.save(job);
        return job.getId();
    }

    @Test
    @Transactional
    void findOpenJobIdsNeedingEmbedding_openJobWithoutEmbedding_isIncluded() {
        UUID jobId = createJob(JobStatus.OPEN);

        List<UUID> ids = jobEmbeddingRepository.findOpenJobIdsNeedingEmbedding(PageRequest.of(0, 10));

        assertThat(ids).contains(jobId);
    }

    @Test
    @Transactional
    void findOpenJobIdsNeedingEmbedding_openJobWithEmbedding_isExcluded() {
        UUID jobId = createJob(JobStatus.OPEN);
        jobEmbeddingRepository.upsertEmbedding(
                jobId, EmbeddingTextFormat.toVectorText(new float[1536]), "text-embedding-3-small");

        List<UUID> ids = jobEmbeddingRepository.findOpenJobIdsNeedingEmbedding(PageRequest.of(0, 10));

        assertThat(ids).doesNotContain(jobId);
    }

    // Case am quan trong nhat theo yeu cau test Dot 2: job DRAFT/PAUSED/CLOSED khong bao gio duoc
    // dua vao danh sach can sinh embedding, du chua co hang job_embeddings nao.
    @Test
    @Transactional
    void findOpenJobIdsNeedingEmbedding_draftPausedClosedJobs_areExcluded() {
        UUID draftJobId = createJob(JobStatus.DRAFT);
        UUID pausedJobId = createJob(JobStatus.PAUSED);
        UUID closedJobId = createJob(JobStatus.CLOSED);

        List<UUID> ids = jobEmbeddingRepository.findOpenJobIdsNeedingEmbedding(PageRequest.of(0, 100));

        assertThat(ids).doesNotContain(draftJobId, pausedJobId, closedJobId);
    }

    @Test
    @Transactional
    void upsertEmbedding_calledTwiceForSameJob_updatesRatherThanDuplicates() {
        UUID jobId = createJob(JobStatus.OPEN);

        jobEmbeddingRepository.upsertEmbedding(jobId, EmbeddingTextFormat.toVectorText(new float[1536]), "model-v1");
        jobEmbeddingRepository.upsertEmbedding(jobId, EmbeddingTextFormat.toVectorText(new float[1536]), "model-v2");

        // findByJobId (khong phai count()/findAll() toan bang - xem yeu cau review Dot 3): tra ve
        // Optional dung ban than da xac nhan KHONG bi trung hang (job_id UNIQUE o DB), khong can
        // dem toan bang moi biet "chi mot hang" - tranh quet toan bo job_embeddings dung chung giua
        // 56 file test.
        JobEmbedding saved = jobEmbeddingRepository.findByJobId(jobId).orElseThrow();
        assertThat(saved.getModel()).isEqualTo("model-v2");
    }

    @Test
    @Transactional
    void deleteByJobId_removesRowSoJobNeedsEmbeddingAgain() {
        UUID jobId = createJob(JobStatus.OPEN);
        jobEmbeddingRepository.upsertEmbedding(
                jobId, EmbeddingTextFormat.toVectorText(new float[1536]), "text-embedding-3-small");

        int deleted = jobEmbeddingRepository.deleteByJobId(jobId);

        assertThat(deleted).isEqualTo(1);
        List<UUID> ids = jobEmbeddingRepository.findOpenJobIdsNeedingEmbedding(PageRequest.of(0, 10));
        assertThat(ids).contains(jobId);
    }
}
