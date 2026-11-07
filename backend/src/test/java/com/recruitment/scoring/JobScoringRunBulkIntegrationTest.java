package com.recruitment.scoring;

import static com.recruitment.scoring.BulkScoringSkipReason.ALREADY_SCORED;
import static com.recruitment.scoring.BulkScoringSkipReason.NOT_PENDING;
import static com.recruitment.scoring.BulkScoringSkipReason.RESUME_NOT_PARSED;
import static com.recruitment.scoring.BulkScoringSkipReason.RUN_IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recruitment.job.JobStatus;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-H04 mo rong - REQUIREMENT muc 7.1: T1-T9, T12-T16, T19 (T10, T11 o JobScoringRunBulkRaceIntegrationTest, L7;
// T17 o RateLimitFilterTest). Khong spy nen dung lai context da cache cua lop cung cau hinh.
class JobScoringRunBulkIntegrationTest extends JobScoringRunBulkIntegrationTestSupport {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    // ---- T1 ----

    @Test
    void t01_threeEligible_createsThreePendingRunsWithSameSnapshot() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, JobStatus.OPEN);
        UUID rubricId = rubric(jobId);
        // Tao nguoc display_order de kiem snapshot sap theo display_order chu khong theo thu tu tao.
        criterion(rubricId, "Tieu chi B", "40", 2);
        criterion(rubricId, "Tieu chi A", "60", 1);
        List<UUID> apps = List.of(eligible(jobId, 1), eligible(jobId, 2), eligible(jobId, 3));

        JsonNode body = bulkOk(hr.token(), jobId);

        assertThat(body.get("created").asInt()).isEqualTo(3);
        assertThat(body.get("remaining").asInt()).isZero();
        assertThat(skipped(body)).isEmpty();
        List<RubricSnapshot> snapshots = new ArrayList<>();
        for (UUID app : apps) {
            List<ScoringRun> runs = scoringRunRepository.findByApplicationIdOrderByCreatedAtDesc(app);
            assertThat(runs).hasSize(1);
            assertThat(runs.get(0).getStatus()).isEqualTo(ScoringRunStatus.PENDING);
            snapshots.add(runs.get(0).getRubricSnapshot());
        }
        assertThat(snapshots.get(1)).isEqualTo(snapshots.get(0));
        assertThat(snapshots.get(2)).isEqualTo(snapshots.get(0));
        assertThat(snapshots.get(0).criteria())
                .extracting(RubricSnapshot.CriterionSnapshot::name)
                .containsExactly("Tieu chi A", "Tieu chi B");
        assertThat(rubricLocked(rubricId)).isTrue();
    }

    // ---- T2 ----

    @Test
    void t02_eachSkipReason_isReportedWithItsCode() throws Exception {
        JobFixture f = readyJob();
        UUID invited = application(f.jobId(), "INTERVIEW_INVITED", 1, appliedAt(1), "DONE", true);
        UUID rejected = application(f.jobId(), "REJECTED", 1, appliedAt(2), "DONE", true);
        UUID hired = application(f.jobId(), "HIRED", 1, appliedAt(3), "DONE", true);
        UUID withdrawn = application(f.jobId(), "WITHDRAWN", 1, appliedAt(4), "DONE", true);
        UUID cvPending = application(f.jobId(), "PENDING", 1, appliedAt(5), "PENDING", false);
        UUID cvDoneNoData = application(f.jobId(), "PENDING", 1, appliedAt(6), "DONE", false);
        UUID latestPending = eligible(f.jobId(), 7);
        run(latestPending, "PENDING", appliedAt(100), false);
        UUID runningNotFinished = eligible(f.jobId(), 8);
        run(runningNotFinished, "RUNNING", appliedAt(100), false);
        // R-E4: RUNNING cho D3 (finished_at khac NULL) van la "dang chay" voi bulk.
        UUID runningWaitingAggregation = eligible(f.jobId(), 9);
        run(runningWaitingAggregation, "RUNNING", appliedAt(100), true);
        UUID done = eligible(f.jobId(), 10);
        run(done, "DONE", appliedAt(100), true);
        UUID doneThenFailed = eligible(f.jobId(), 11);
        run(doneThenFailed, "DONE", appliedAt(100), true);
        run(doneThenFailed, "FAILED", appliedAt(200), true);
        UUID ok = eligible(f.jobId(), 12);
        List<UUID> ineligible = List.of(invited, rejected, hired, withdrawn, cvPending, cvDoneNoData, latestPending,
                runningNotFinished, runningWaitingAggregation, done, doneThenFailed);
        List<Long> runsBefore = ineligible.stream().map(this::runCount).toList();

        JsonNode body = bulkOk(f.hr().token(), f.jobId());

        assertThat(body.get("created").asInt()).isEqualTo(1);
        assertThat(skipped(body)).containsExactly(
                row(invited, NOT_PENDING),
                row(rejected, NOT_PENDING),
                row(hired, NOT_PENDING),
                row(withdrawn, NOT_PENDING),
                row(cvPending, RESUME_NOT_PARSED),
                row(cvDoneNoData, RESUME_NOT_PARSED),
                row(latestPending, RUN_IN_PROGRESS),
                row(runningNotFinished, RUN_IN_PROGRESS),
                row(runningWaitingAggregation, RUN_IN_PROGRESS),
                row(done, ALREADY_SCORED),
                row(doneThenFailed, ALREADY_SCORED));
        assertThat(ineligible.stream().map(this::runCount).toList()).isEqualTo(runsBefore);
        assertThat(pendingRunCount(ok)).isEqualTo(1);
    }

    // ---- T3 ----

    @Test
    void t03_onlyFailedRuns_areEligibleAgain() throws Exception {
        JobFixture f = readyJob();
        UUID oneFailed = eligible(f.jobId(), 1);
        run(oneFailed, "FAILED", appliedAt(100), true);
        UUID twoFailed = eligible(f.jobId(), 2);
        run(twoFailed, "FAILED", appliedAt(100), true);
        run(twoFailed, "FAILED", appliedAt(200), true);

        JsonNode body = bulkOk(f.hr().token(), f.jobId());

        assertThat(body.get("created").asInt()).isEqualTo(2);
        assertThat(skipped(body)).isEmpty();
        assertThat(pendingRunCount(oneFailed)).isEqualTo(1);
        assertThat(pendingRunCount(twoFailed)).isEqualTo(1);
    }

    // ---- T4 ----

    @Test
    void t04_multipleReasons_pickHighestPriority() throws Exception {
        JobFixture f = readyJob();
        UUID rejectedCvPending = application(f.jobId(), "REJECTED", 1, appliedAt(1), "PENDING", false);
        UUID pendingCvPendingWithDone = application(f.jobId(), "PENDING", 1, appliedAt(2), "PENDING", false);
        run(pendingCvPendingWithDone, "DONE", appliedAt(100), true);
        UUID doneThenPending = eligible(f.jobId(), 3);
        run(doneThenPending, "DONE", appliedAt(100), true);
        run(doneThenPending, "PENDING", appliedAt(200), false);

        JsonNode body = bulkOk(f.hr().token(), f.jobId());

        assertThat(body.get("created").asInt()).isZero();
        assertThat(skipped(body)).containsExactly(
                row(rejectedCvPending, NOT_PENDING),
                row(pendingCvPendingWithDone, RESUME_NOT_PARSED),
                row(doneThenPending, RUN_IN_PROGRESS));
    }

    // ---- T5 ----

    @Test
    void t05_rubricNotHundred_returns409SameMessageAsSingle() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, JobStatus.OPEN);
        UUID rubricId = rubric(jobId);
        criterion(rubricId, "Kinh nghiem Java", "60", 1);
        criterion(rubricId, "Ky nang giao tiep", "39.99", 2);
        UUID app = eligible(jobId, 1);

        MvcResult bulkResult = bulk(hr.token(), jobId);
        MvcResult singleResult = mockMvc
                .perform(post("/api/hr/applications/" + app + "/scoring-runs")
                        .header("Authorization", "Bearer " + hr.token()))
                .andReturn();

        assertThat(bulkResult.getResponse().getStatus()).isEqualTo(409);
        JsonNode bulkError = JSON.readTree(text(bulkResult));
        assertThat(bulkError.get("error").asString()).isEqualTo("RUBRIC_INCOMPLETE");
        assertThat(singleResult.getResponse().getStatus()).isEqualTo(409);
        assertThat(bulkError.get("message").asString())
                .isEqualTo(JSON.readTree(text(singleResult)).get("message").asString());
        assertThat(runCount(app)).isZero();
        assertThat(rubricLocked(rubricId)).isFalse();

        // Rubric khong co tieu chi nao: tong 0 -> cung nhanh 409.
        UUID emptyJobId = job(hr, JobStatus.OPEN);
        UUID emptyRubricId = rubric(emptyJobId);
        UUID emptyApp = eligible(emptyJobId, 1);
        MvcResult emptyResult = bulk(hr.token(), emptyJobId);
        assertThat(emptyResult.getResponse().getStatus()).isEqualTo(409);
        assertThat(JSON.readTree(text(emptyResult)).get("error").asString()).isEqualTo("RUBRIC_INCOMPLETE");
        assertThat(runCount(emptyApp)).isZero();
        assertThat(rubricLocked(emptyRubricId)).isFalse();

        // Bien tren: 100.00 (scale khac 100) -> 200.
        UUID fullJobId = job(hr, JobStatus.OPEN);
        UUID fullRubricId = rubric(fullJobId);
        criterion(fullRubricId, "Kinh nghiem Java", "60.00", 1);
        criterion(fullRubricId, "Ky nang giao tiep", "40.00", 2);
        UUID fullApp = eligible(fullJobId, 1);
        assertThat(bulkOk(hr.token(), fullJobId).get("created").asInt()).isEqualTo(1);
        assertThat(pendingRunCount(fullApp)).isEqualTo(1);
    }

    // ---- T6 ----

    @Test
    void t06_accessRules() throws Exception {
        JobFixture f = readyJob();
        UUID app = eligible(f.jobId(), 1);

        MvcResult noToken = bulk(null, f.jobId());
        assertThat(noToken.getResponse().getStatus()).isEqualTo(401);
        assertThat(text(noToken)).contains("UNAUTHENTICATED");

        assertThat(bulk(candidateToken(), f.jobId()).getResponse().getStatus()).isEqualTo(403);

        assertThat(bulk(hr().token(), f.jobId()).getResponse().getStatus()).isEqualTo(403);

        MvcResult unknown = bulk(f.hr().token(), UUID.randomUUID());
        assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
        assertThat(text(unknown)).contains("JOB_NOT_FOUND");

        // loadOwned bao JOB_NOT_FOUND truoc COMPANY_NOT_FOUND nen dung tin TON TAI.
        MvcResult noCompany = bulk(hrWithoutCompanyToken(), f.jobId());
        assertThat(noCompany.getResponse().getStatus()).isEqualTo(404);
        assertThat(text(noCompany)).contains("COMPANY_NOT_FOUND");

        jdbcTemplate.update("UPDATE jobs SET deleted_at = now() WHERE id = ?", f.jobId());
        MvcResult deleted = bulk(f.hr().token(), f.jobId());
        assertThat(deleted.getResponse().getStatus()).isEqualTo(404);
        assertThat(text(deleted)).contains("JOB_NOT_FOUND");

        assertThat(runCount(app)).isZero();
        assertThat(rubricLocked(f.rubricId())).isFalse();
    }

    // ---- T7 ----

    @Test
    void t07_cap_49_50_51() throws Exception {
        Hr hr = hr();
        for (int count : new int[] {49, 50, 51}) {
            JobFixture f = readyJob(hr, JobStatus.OPEN);
            List<UUID> apps = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                apps.add(eligible(f.jobId(), i));
            }

            JsonNode body = bulkOk(hr.token(), f.jobId());

            int expectedCreated = Math.min(count, ScoringRunService.MAX_BULK_SCORING_RUNS);
            assertThat(body.get("created").asInt()).as("created voi %d don", count).isEqualTo(expectedCreated);
            assertThat(body.get("remaining").asInt()).as("remaining voi %d don", count).isEqualTo(count - expectedCreated);
            assertThat(skipped(body)).as("skipped voi %d don", count).isEmpty();
            for (int i = 0; i < count; i++) {
                assertThat(runCount(apps.get(i))).as("don thu %d / %d", i, count).isEqualTo(i < expectedCreated ? 1 : 0);
            }
        }
    }

    // ---- T8 ----

    @Test
    void t08_secondCall_reportsRunInProgress() throws Exception {
        JobFixture f = readyJob();
        List<UUID> apps = List.of(eligible(f.jobId(), 1), eligible(f.jobId(), 2), eligible(f.jobId(), 3));

        assertThat(bulkOk(f.hr().token(), f.jobId()).get("created").asInt()).isEqualTo(3);
        JsonNode second = bulkOk(f.hr().token(), f.jobId());

        assertThat(second.get("created").asInt()).isZero();
        assertThat(second.get("remaining").asInt()).isZero();
        assertThat(skipped(second)).containsExactly(
                row(apps.get(0), RUN_IN_PROGRESS), row(apps.get(1), RUN_IN_PROGRESS), row(apps.get(2), RUN_IN_PROGRESS));
        assertThat(apps.stream().mapToLong(this::runCount).sum()).isEqualTo(3);
    }

    // ---- T9 ----

    @Test
    void t09_fiftyOne_secondCallTakesTheRest() throws Exception {
        JobFixture f = readyJob();
        List<UUID> apps = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            apps.add(eligible(f.jobId(), i));
        }

        JsonNode first = bulkOk(f.hr().token(), f.jobId());
        assertThat(first.get("created").asInt()).isEqualTo(50);
        assertThat(first.get("remaining").asInt()).isEqualTo(1);

        JsonNode second = bulkOk(f.hr().token(), f.jobId());
        assertThat(second.get("created").asInt()).isEqualTo(1);
        assertThat(second.get("remaining").asInt()).isZero();
        assertThat(skipped(second)).containsExactlyElementsOf(
                apps.subList(0, 50).stream().map(app -> row(app, RUN_IN_PROGRESS)).toList());
        assertThat(pendingRunCount(apps.get(50))).isEqualTo(1);
        assertThat(apps.stream().mapToLong(this::runCount).sum()).isEqualTo(51);
    }

    // ---- T12 ----

    @Test
    void t12_oldCycle_isIgnored() throws Exception {
        JobFixture f = readyJob();
        UUID cycleOne = eligible(f.jobId(), 1);
        jdbcTemplate.update("UPDATE jobs SET recruitment_cycle = 2 WHERE id = ?", f.jobId());
        UUID cycleTwo = application(f.jobId(), "PENDING", 2, appliedAt(2), "DONE", true);

        JsonNode body = bulkOk(f.hr().token(), f.jobId());

        assertThat(body.get("created").asInt()).isEqualTo(1);
        assertThat(body.get("remaining").asInt()).isZero();
        assertThat(skipped(body)).isEmpty();
        assertThat(runCount(cycleOne)).isZero();
        assertThat(pendingRunCount(cycleTwo)).isEqualTo(1);
    }

    // ---- T13 ----

    @Test
    void t13_blindReview_responseHasOnlyAllowedKeys() throws Exception {
        JobFixture f = readyJob();
        jdbcTemplate.update("UPDATE jobs SET blind_review_enabled = TRUE WHERE id = ?", f.jobId());
        UUID a = eligible(f.jobId(), 1);
        UUID b = eligible(f.jobId(), 2);
        UUID withdrawn = application(f.jobId(), "WITHDRAWN", 1, appliedAt(3), "DONE", true);

        MvcResult result = bulk(f.hr().token(), f.jobId());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String json = text(result);
        JsonNode body = JSON.readTree(json);

        assertThat(body.get("created").asInt()).isEqualTo(2);
        assertThat(skipped(body)).containsExactly(row(withdrawn, NOT_PENDING));
        Set<String> keys = new HashSet<>();
        collectKeys(body, keys);
        assertThat(keys).containsExactlyInAnyOrder("created", "remaining", "skipped", "applicationId", "reason");
        for (UUID app : List.of(a, b, withdrawn)) {
            UUID candidateId = jdbcTemplate.queryForObject(
                    "SELECT candidate_id FROM job_applications WHERE id = ?", UUID.class, app);
            String fullName = jdbcTemplate.queryForObject(
                    "SELECT full_name FROM users WHERE id = ?", String.class, candidateId);
            assertThat(json).doesNotContain(fullName).doesNotContain(emailOf(candidateId));
        }
    }

    private static void collectKeys(JsonNode node, Set<String> keys) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                keys.add(entry.getKey());
                collectKeys(entry.getValue(), keys);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectKeys(child, keys));
        }
    }

    // ---- T14 ----

    @Test
    void t14_pausedAndClosedJob_stillCreates() throws Exception {
        Hr hr = hr();
        for (JobStatus status : List.of(JobStatus.PAUSED, JobStatus.CLOSED)) {
            JobFixture f = readyJob(hr, status);
            UUID app = eligible(f.jobId(), 1);

            JsonNode body = bulkOk(hr.token(), f.jobId());

            assertThat(body.get("created").asInt()).as("tin %s", status).isEqualTo(1);
            assertThat(pendingRunCount(app)).as("tin %s", status).isEqualTo(1);
        }
    }

    // ---- T15 ----

    @Test
    void t15_noEligible_doesNotLockRubric() throws Exception {
        JobFixture f = readyJob();
        UUID withdrawn = application(f.jobId(), "WITHDRAWN", 1, appliedAt(1), "DONE", true);

        JsonNode body = bulkOk(f.hr().token(), f.jobId());

        assertThat(body.get("created").asInt()).isZero();
        assertThat(body.get("remaining").asInt()).isZero();
        assertThat(skipped(body)).containsExactly(row(withdrawn, NOT_PENDING));
        assertThat(rubricLocked(f.rubricId())).isFalse();
    }

    // ---- T16 ----

    @Test
    void t16_statementCountDoesNotGrow() throws Exception {
        Hr hr = hr();
        JobFixture small = readyJob(hr, JobStatus.OPEN);
        JobFixture large = readyJob(hr, JobStatus.OPEN);
        for (int i = 0; i < 3; i++) {
            run(eligible(small.jobId(), i), "DONE", appliedAt(100), true);
        }
        for (int i = 0; i < 20; i++) {
            run(eligible(large.jobId(), i), "DONE", appliedAt(100), true);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        JsonNode smallBody = bulkOk(hr.token(), small.jobId());
        long n3 = statistics.getPrepareStatementCount();
        statistics.clear();
        JsonNode largeBody = bulkOk(hr.token(), large.jobId());
        long n20 = statistics.getPrepareStatementCount();

        assertThat(smallBody.get("created").asInt()).isZero();
        assertThat(largeBody.get("created").asInt()).isZero();
        assertThat(n20).as("So cau lenh: tin 3 don = %d, tin 20 don = %d", n3, n20).isEqualTo(n3);
        System.out.printf("FR-H04 bulk T16: n3=%d, n20=%d%n", n3, n20);
    }

    // ---- T19 ----

    @Test
    void t19_tiedAppliedAt_ordersByIdString() throws Exception {
        JobFixture f = readyJob();
        // 49 id bat dau bang 1..7 (bit dau 0 -> long duong), mot id "ffffffff-..." (long am) va mot id "00000000-...":
        // theo chuoi "ffffffff-..." lon nhat, theo UUID.compareTo no lai nho nhat -> hai cach sap chon hai don khac nhau.
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 49; i++) {
            String random = UUID.randomUUID().toString();
            ids.add(UUID.fromString(ThreadLocalRandom.current().nextInt(1, 8) + random.substring(1)));
        }
        UUID stringMax = UUID.fromString("ffffffff-ffff-4fff-8fff-" + UUID.randomUUID().toString().substring(24));
        ids.add(stringMax);
        ids.add(UUID.fromString("00000000-0000-4000-8000-" + UUID.randomUUID().toString().substring(24)));
        Instant sameAppliedAt = appliedAt(1);
        for (UUID id : ids) {
            application(id, f.jobId(), "PENDING", 1, sameAppliedAt, "DONE", true);
        }
        UUID compareToMax = ids.stream().max(Comparator.naturalOrder()).orElseThrow();
        assertThat(compareToMax).as("du lieu phai phan biet duoc hai cach sap").isNotEqualTo(stringMax);
        List<UUID> postgresOrder = jdbcTemplate.queryForList(
                "SELECT id FROM job_applications WHERE job_id = ? ORDER BY applied_at, id", UUID.class, f.jobId());

        JsonNode body = bulkOk(f.hr().token(), f.jobId());

        assertThat(body.get("created").asInt()).isEqualTo(50);
        assertThat(body.get("remaining").asInt()).isEqualTo(1);
        assertThat(postgresOrder.get(50)).isEqualTo(stringMax);
        assertThat(runCount(stringMax)).isZero();
        assertThat(runCount(compareToMax)).isEqualTo(1);
    }
}
