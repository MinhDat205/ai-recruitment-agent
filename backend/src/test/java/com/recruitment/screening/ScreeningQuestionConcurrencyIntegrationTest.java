package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.common.exception.ScreeningQuestionLimitReachedException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.screening.dto.ScreeningQuestionRequest;
import com.recruitment.screening.dto.ScreeningQuestionResponse;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// FR-H10 T5 (R-Q10) - hai request them cau song song khi Job co 9 cau: dung mot thanh cong.
// KHONG @Transactional toan lop (CLAUDE.md §3c): transaction boc toan lop lam hai luong khong thay du lieu cua nhau.
// Du lieu chuan bi qua repository/service tu commit; du lieu ngau nhien rieng, chi khang dinh tren Job cua test,
// khong don sau test (L2, tien le ScoringRunOrchestratorTest/ScoreExplanationOrchestratorTest). Goi thang bean service
// (proxy transaction that) tu hai luong - MockMvc khong can cho phan nay vi kiem quyen da co o T8.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ScreeningQuestionConcurrencyIntegrationTest {

    @Autowired
    private ScreeningQuestionOwnerService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static ScreeningQuestionRequest question(String text) {
        return new ScreeningQuestionRequest(ScreeningQuestionType.SHORT_TEXT, text, null, null);
    }

    @Test
    void t5_twoConcurrentAddsAtNineQuestions_exactlyOneSucceeds() throws Exception {
        User hr = new User();
        hr.setEmail("t5-hr-" + UUID.randomUUID() + "@example.com");
        hr.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        hr.setRole(Role.HR);
        hr.setFullName("Nha Tuyen Dung Test");
        hr = userRepository.save(hr);
        UUID ownerId = hr.getId();

        Company company = new Company();
        company.setOwnerId(ownerId);
        company.setName("Cong ty Test " + UUID.randomUUID());
        company = companyRepository.save(company);

        Job job = new Job();
        job.setCompanyId(company.getId());
        job.setCreatedBy(ownerId);
        job.setTitle("Nhan vien CSKH");
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.DRAFT);
        job.setRecruitmentCycle(1);
        UUID jobId = jobRepository.save(job).getId();

        for (int i = 1; i <= 9; i++) {
            service.add(ownerId, jobId, question("Cau " + i));
        }
        assertThat(revision(jobId)).isEqualTo(9);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ScreeningQuestionResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String text = "Cau song song " + i;
                Callable<ScreeningQuestionResponse> task = () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return service.add(ownerId, jobId, question(text));
                };
                futures.add(executor.submit(task));
            }

            int succeeded = 0;
            int limitReached = 0;
            for (Future<ScreeningQuestionResponse> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(ScreeningQuestionLimitReachedException.class);
                    limitReached++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(limitReached).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        Integer active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_screening_questions WHERE job_id = ? AND deleted_at IS NULL",
                Integer.class,
                jobId);
        Integer distinctOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT display_order) FROM job_screening_questions WHERE job_id = ? AND deleted_at IS NULL",
                Integer.class,
                jobId);
        assertThat(active).isEqualTo(10);
        assertThat(distinctOrders).as("display_order khong trung").isEqualTo(10);
        assertThat(revision(jobId)).as("chi lan thanh cong duoc tang, lan bi chan da rollback").isEqualTo(10);
    }

    private int revision(UUID jobId) {
        return jdbcTemplate.queryForObject("SELECT screening_revision FROM jobs WHERE id = ?", Integer.class, jobId);
    }
}
