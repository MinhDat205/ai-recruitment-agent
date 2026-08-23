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

    // --- Test moi cho Dot 5: findTopMatchingJobs ---

    // Vector don vi tren truc 0: [s, sqrt(1-s^2), 0, 0, ...]. Ca hai vector deu don vi va cac thanh
    // phan con lai bang 0 nen tich vo huong = s - cosine similarity voi vector goc [1,0,0,...]
    // CHINH XAC bang s. Cach dung de dung mot nguong similarity chinh xac trong test, khong phu
    // thuoc OpenAI that (da kiem chung nguong 0.40 bang du lieu that ngoai phien - xem
    // JobRecommendationCacheService.MIN_SIMILARITY_SCORE).
    private static float[] vectorWithSimilarity(double s) {
        float[] v = new float[1536];
        v[0] = (float) s;
        v[1] = (float) Math.sqrt(1 - s * s);
        return v;
    }

    private static final String QUERY_VECTOR_AXIS0 = EmbeddingTextFormat.toVectorText(vectorWithSimilarity(1.0));

    // Bien duoi nguong 0.40 (0.39) - tranh dung chinh xac 0.40 vi sai so lam tron dau phay dong cua
    // phep tinh can bac hai/cosine co the lech vai phan trieu, khien test flaky (xem giai thich dau
    // file Dot 5).
    @Test
    @Transactional
    void findTopMatchingJobs_justBelowThreshold_isExcluded() {
        UUID jobId = createJob(JobStatus.OPEN);
        jobEmbeddingRepository.upsertEmbedding(
                jobId, EmbeddingTextFormat.toVectorText(vectorWithSimilarity(0.39)), "text-embedding-3-small");

        List<JobMatchView> matches = jobEmbeddingRepository.findTopMatchingJobs(QUERY_VECTOR_AXIS0, 0.40, 10);

        assertThat(matches).extracting(JobMatchView::getJobId).doesNotContain(jobId);
    }

    // Bien tren nguong 0.40 (0.41) - cung ly do tranh flaky nhu tren.
    @Test
    @Transactional
    void findTopMatchingJobs_justAboveThreshold_isIncluded() {
        UUID jobId = createJob(JobStatus.OPEN);
        jobEmbeddingRepository.upsertEmbedding(
                jobId, EmbeddingTextFormat.toVectorText(vectorWithSimilarity(0.41)), "text-embedding-3-small");

        List<JobMatchView> matches = jobEmbeddingRepository.findTopMatchingJobs(QUERY_VECTOR_AXIS0, 0.40, 10);

        assertThat(matches).extracting(JobMatchView::getJobId).containsExactly(jobId);
    }

    @Test
    @Transactional
    void findTopMatchingJobs_multipleAboveThreshold_orderedBySimilarityDescending() {
        UUID lowerId = createJob(JobStatus.OPEN);
        jobEmbeddingRepository.upsertEmbedding(
                lowerId, EmbeddingTextFormat.toVectorText(vectorWithSimilarity(0.5)), "text-embedding-3-small");
        UUID higherId = createJob(JobStatus.OPEN);
        jobEmbeddingRepository.upsertEmbedding(
                higherId, EmbeddingTextFormat.toVectorText(vectorWithSimilarity(0.8)), "text-embedding-3-small");

        List<JobMatchView> matches = jobEmbeddingRepository.findTopMatchingJobs(QUERY_VECTOR_AXIS0, 0.40, 10);

        assertThat(matches).extracting(JobMatchView::getJobId).containsExactly(higherId, lowerId);
    }

    // Case am quan trong: job CLOSED co similarity rat cao (0.9) van khong bao gio duoc goi y - loc
    // status='OPEN' phai dung du khi similarity cao.
    @Test
    @Transactional
    void findTopMatchingJobs_closedJobAboveThreshold_isExcluded() {
        UUID closedJobId = createJob(JobStatus.CLOSED);
        jobEmbeddingRepository.upsertEmbedding(
                closedJobId, EmbeddingTextFormat.toVectorText(vectorWithSimilarity(0.9)), "text-embedding-3-small");

        List<JobMatchView> matches = jobEmbeddingRepository.findTopMatchingJobs(QUERY_VECTOR_AXIS0, 0.40, 10);

        assertThat(matches).isEmpty();
    }

    @Test
    @Transactional
    void findTopMatchingJobs_moreMatchesThanTopN_limitsResultSize() {
        for (int i = 0; i < 3; i++) {
            UUID jobId = createJob(JobStatus.OPEN);
            jobEmbeddingRepository.upsertEmbedding(
                    jobId, EmbeddingTextFormat.toVectorText(vectorWithSimilarity(0.6)), "text-embedding-3-small");
        }

        List<JobMatchView> matches = jobEmbeddingRepository.findTopMatchingJobs(QUERY_VECTOR_AXIS0, 0.40, 2);

        assertThat(matches).hasSize(2);
    }

    // Bang chung phong thu chieu sau hoat dong doc lap voi guard o EmbeddingService (Dot 5, sau
    // review): ghi embedding vector toan so 0 THANG qua upsertEmbedding, BO QUA EmbeddingService -
    // mo phong dung tinh huong du lieu CU da nam trong job_embeddings TRUOC KHI
    // EmbeddingErrorCode.ZERO_VECTOR ton tai (guard o tang service khong the hoi to sua du lieu da
    // ghi). Neu khong co dieu kien "< 'Infinity'::float8" trong WHERE, job nay se vuot moi nguong
    // similarity (da kiem chung thuc nghiem tren Postgres 17 that: 'NaN'::float8 >= 0.4 tra ve true)
    // va lam ProxyProjectionFactory nem ConversionFailedException khi convert Double NaN sang
    // BigDecimal (loi that da gap trong Dot 5 khi chay full suite).
    @Test
    @Transactional
    void findTopMatchingJobs_jobWithZeroVector_isExcluded() {
        UUID jobId = createJob(JobStatus.OPEN);
        jobEmbeddingRepository.upsertEmbedding(
                jobId, EmbeddingTextFormat.toVectorText(new float[1536]), "text-embedding-3-small");

        List<JobMatchView> matches = jobEmbeddingRepository.findTopMatchingJobs(QUERY_VECTOR_AXIS0, 0.40, 10);

        assertThat(matches).extracting(JobMatchView::getJobId).doesNotContain(jobId);
    }
}
