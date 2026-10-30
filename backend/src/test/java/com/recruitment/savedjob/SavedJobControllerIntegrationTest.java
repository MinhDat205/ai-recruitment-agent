package com.recruitment.savedjob;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-U16 T1-T4, T6-T9, T11-T14 va B7 (REQUIREMENT muc 7.1). T5/T10 o SavedJobOrderingIntegrationTest, T15/T16 o
// SavedJobConcurrencyIntegrationTest, T20 o SavedJobHrPrivacyIntegrationTest, T21 o SavedJobSourceGuardTest.
class SavedJobControllerIntegrationTest extends SavedJobIntegrationTestSupport {

    private static final String NOT_SAVEABLE_BODY =
            "{\"error\":\"JOB_NOT_SAVEABLE\",\"message\":\"Tin tuyển dụng này hiện không nhận lưu vì không còn mở.\"}";

    // Muc 4.3 - so NGUYEN TEN KHOA (bang chinh xac) o moi cap. Khong bo khoa nao de test xanh.
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "saved",
            "isSaved",
            "savedCount",
            "score",
            "matchScore",
            "similarityScore",
            "rank",
            "totalScore",
            "recommendation",
            "verdict",
            "label",
            "isQualified",
            "passed",
            "candidateId",
            "deletedAt",
            "description",
            "requirements",
            "recruitmentCycle");

    private static final Set<String> AVAILABILITY_VALUES = Set.of("OPEN", "EXPIRED", "PAUSED", "CLOSED", "UNAVAILABLE");

    private Map<String, JsonNode> listByJobId(String token) throws Exception {
        MvcResult result = getList(token);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Map<String, JsonNode> byId = new HashMap<>();
        for (JsonNode item : body(result).values()) {
            byId.put(item.get("jobId").asString(), item);
        }
        return byId;
    }

    // ---- T1 ----
    @Test
    void t1_saveOpenJob_returns204_listedInIds_oneActiveRow() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        UUID jobId = insertOpenJob(hr);

        assertThat(putSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);

        assertThat(idsOf(getIds(candidate.token()))).containsExactly(jobId.toString());
        assertThat(countRows(candidate.id(), jobId)).isEqualTo(1L);
        assertThat(row(candidate.id(), jobId).deletedAt()).isNull();
    }

    // ---- T2 ----
    @Test
    void t2_notOpenJobs_andUnknownUuid_return409SameBody_noRow() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        List<UUID> jobIds = List.of(
                insertJob(hr, "OPEN", -1, false), // EXPIRED
                insertJob(hr, "PAUSED", null, false),
                insertJob(hr, "CLOSED", null, false),
                insertJob(hr, "DRAFT", null, false),
                insertJob(hr, "OPEN", null, true), // OPEN da xoa mem
                UUID.randomUUID()); // khong ton tai

        for (UUID jobId : jobIds) {
            MvcResult result = putSave(candidate.token(), jobId);
            assertThat(result.getResponse().getStatus()).as("jobId=%s", jobId).isEqualTo(409);
            assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo(NOT_SAVEABLE_BODY);
        }
        assertThat(countActive(candidate.id())).isZero();
    }

    // ---- T3 - bien han nop theo CURRENT_DATE cua DB ----
    @Test
    void t3_deadlineBoundary_yesterday409_today204_tomorrow204() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");

        assertThat(putSave(candidate.token(), insertJob(hr, "OPEN", -1, false)).getResponse().getStatus())
                .isEqualTo(409);
        assertThat(putSave(candidate.token(), insertJob(hr, "OPEN", 0, false)).getResponse().getStatus())
                .isEqualTo(204);
        assertThat(putSave(candidate.token(), insertJob(hr, "OPEN", 1, false)).getResponse().getStatus())
                .isEqualTo(204);
        assertThat(countActive(candidate.id())).isEqualTo(2L);
    }

    // ---- T4 ----
    @Test
    void t4_saveTwice_bothReturn204_oneRow_savedAtUnchanged() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        UUID jobId = insertOpenJob(hr);

        assertThat(putSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);
        Row first = row(candidate.id(), jobId);
        assertThat(putSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);
        Row second = row(candidate.id(), jobId);

        assertThat(countRows(candidate.id(), jobId)).isEqualTo(1L);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.savedAt()).isEqualTo(first.savedAt());
    }

    // ---- T6 ----
    @Test
    void t6_deleteNeverSaved_returns204_noRow() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        UUID jobId = insertOpenJob(hr);

        assertThat(deleteSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);
        assertThat(deleteSave(candidate.token(), UUID.randomUUID()).getResponse().getStatus()).isEqualTo(204);
        assertThat(countRows(candidate.id(), jobId)).isZero();
    }

    @Test
    void t6_deleteTwice_bothReturn204_deletedAtKeptFromFirst() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        UUID jobId = insertOpenJob(hr);
        putSave(candidate.token(), jobId);

        assertThat(deleteSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);
        Row afterFirst = row(candidate.id(), jobId);
        assertThat(deleteSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);
        Row afterSecond = row(candidate.id(), jobId);

        assertThat(afterFirst.deletedAt()).isNotNull();
        assertThat(afterSecond.deletedAt()).isEqualTo(afterFirst.deletedAt());
        assertThat(countRows(candidate.id(), jobId)).isEqualTo(1L);
    }

    // R-S7 - bo luu khong can tin con mo.
    @Test
    void t6_deleteSavedJobThatIsNowClosedOrDeleted_returns204_rowSoftDeleted() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        UUID closed = insertOpenJob(hr);
        UUID removed = insertOpenJob(hr);
        putSave(candidate.token(), closed);
        putSave(candidate.token(), removed);
        setJobColumn(closed, "status = 'CLOSED'");
        setJobColumn(removed, "deleted_at = now()");

        assertThat(deleteSave(candidate.token(), closed).getResponse().getStatus()).isEqualTo(204);
        assertThat(deleteSave(candidate.token(), removed).getResponse().getStatus()).isEqualTo(204);

        assertThat(row(candidate.id(), closed).deletedAt()).isNotNull();
        assertThat(row(candidate.id(), removed).deletedAt()).isNotNull();
    }

    // ---- T7 - bien tran 99/100/101 ----
    @Test
    void t7_limitBoundaries() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        List<UUID> preSaved = insertOpenJobs(hr, SavedJobService.MAX_ACTIVE_SAVED_JOBS - 1);
        insertActiveSaves(candidate.id(), preSaved);
        UUID hundredth = insertOpenJob(hr);
        UUID hundredFirst = insertOpenJob(hr);

        // 99 + tin moi -> 204 (100).
        assertThat(putSave(candidate.token(), hundredth).getResponse().getStatus()).isEqualTo(204);
        assertThat(countActive(candidate.id())).isEqualTo(100L);

        // 100 + tin moi -> 409, khong co dong cua tin do, van 100.
        MvcResult over = putSave(candidate.token(), hundredFirst);
        assertThat(over.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(over).get("error").asString()).isEqualTo("SAVED_JOB_LIMIT_REACHED");
        assertThat(body(over).get("message").asString())
                .isEqualTo("Bạn đã lưu tối đa 100 tin. Hãy bỏ lưu bớt để lưu tin mới.");
        assertThat(countRows(candidate.id(), hundredFirst)).isZero();
        assertThat(countActive(candidate.id())).isEqualTo(100L);

        // 100 + tin dang luu -> 204, van 100.
        assertThat(putSave(candidate.token(), hundredth).getResponse().getStatus()).isEqualTo(204);
        assertThat(countActive(candidate.id())).isEqualTo(100L);

        // 100 + luu lai mot tin da bo (sau khi bo mot tin khac thi con 99 -> luu lai lam no 100; them tin nua -> 409).
        assertThat(deleteSave(candidate.token(), preSaved.get(0)).getResponse().getStatus()).isEqualTo(204);
        assertThat(putSave(candidate.token(), hundredFirst).getResponse().getStatus()).isEqualTo(204);
        assertThat(countActive(candidate.id())).isEqualTo(100L);
        MvcResult reactivateOver = putSave(candidate.token(), preSaved.get(0));
        assertThat(reactivateOver.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(reactivateOver).get("error").asString()).isEqualTo("SAVED_JOB_LIMIT_REACHED");
        assertThat(row(candidate.id(), preSaved.get(0)).deletedAt()).isNotNull();
        assertThat(countActive(candidate.id())).isEqualTo(100L);
    }

    // ---- T8 - tran dem ca tin khong con mo ----
    @Test
    void t8_limitCountsJobsNoLongerOpen() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        List<UUID> preSaved = insertOpenJobs(hr, SavedJobService.MAX_ACTIVE_SAVED_JOBS);
        insertActiveSaves(candidate.id(), preSaved);
        for (UUID jobId : preSaved.subList(0, 30)) {
            setJobColumn(jobId, "status = 'CLOSED'");
        }

        MvcResult result = putSave(candidate.token(), insertOpenJob(hr));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).get("error").asString()).isEqualTo("SAVED_JOB_LIMIT_REACHED");
        assertThat(countActive(candidate.id())).isEqualTo(100L);
    }

    // ---- T9 - quyen ----
    @Test
    void t9_noToken401_hrToken403_forAllEndpoints() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        UUID jobId = insertOpenJob(hr);

        for (String token : new String[] {null, hr.token()}) {
            int expected = token == null ? 401 : 403;
            assertThat(putSave(token, jobId).getResponse().getStatus()).isEqualTo(expected);
            assertThat(deleteSave(token, jobId).getResponse().getStatus()).isEqualTo(expected);
            assertThat(getList(token).getResponse().getStatus()).isEqualTo(expected);
            assertThat(getIds(token).getResponse().getStatus()).isEqualTo(expected);
        }
        MvcResult unauthenticated = putSave(null, jobId);
        assertThat(body(unauthenticated).get("error").asString()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void t9_otherCandidate_cannotSeeOrRemoveSaves() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account a = registerCandidate("u16c-a");
        Account b = registerCandidate("u16c-b");
        UUID jobId = insertOpenJob(hr);
        putSave(a.token(), jobId);
        Row before = row(a.id(), jobId);

        assertThat(body(getList(b.token())).size()).isZero();
        assertThat(idsOf(getIds(b.token()))).isEmpty();
        assertThat(deleteSave(b.token(), jobId).getResponse().getStatus()).isEqualTo(204);

        assertThat(row(a.id(), jobId)).isEqualTo(before);
        assertThat(idsOf(getIds(a.token()))).containsExactly(jobId.toString());
    }

    // ---- T11 - availability ----
    @Test
    void t11_availabilityOfEachSavedJob() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        UUID open = insertOpenJob(hr);
        UUID expired = insertOpenJob(hr);
        UUID paused = insertOpenJob(hr);
        UUID closed = insertOpenJob(hr);
        UUID softDeleted = insertOpenJob(hr);
        UUID draft = insertOpenJob(hr);
        for (UUID jobId : List.of(open, expired, paused, closed, softDeleted, draft)) {
            assertThat(putSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);
        }
        setJobColumn(expired, "deadline = CURRENT_DATE - 1");
        setJobColumn(paused, "status = 'PAUSED'");
        setJobColumn(closed, "status = 'CLOSED'");
        setJobColumn(softDeleted, "deleted_at = now()");
        setJobColumn(draft, "status = 'DRAFT'");

        Map<String, JsonNode> byId = listByJobId(candidate.token());

        assertThat(byId.get(open.toString()).get("availability").asString()).isEqualTo("OPEN");
        assertThat(byId.get(expired.toString()).get("availability").asString()).isEqualTo("EXPIRED");
        assertThat(byId.get(paused.toString()).get("availability").asString()).isEqualTo("PAUSED");
        assertThat(byId.get(closed.toString()).get("availability").asString()).isEqualTo("CLOSED");
        assertThat(byId.get(softDeleted.toString()).get("availability").asString()).isEqualTo("UNAVAILABLE");
        assertThat(byId.get(draft.toString()).get("availability").asString()).isEqualTo("UNAVAILABLE");
        assertThat(byId.values()).allSatisfy(item ->
                assertThat(AVAILABILITY_VALUES).contains(item.get("availability").asString()));
    }

    // ---- T12 - summary ----
    @Test
    void t12_summaryNullForUnavailable_fullForClosed() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        UUID removed = insertOpenJob(hr);
        UUID closed = insertOpenJob(hr);
        putSave(candidate.token(), removed);
        putSave(candidate.token(), closed);
        setJobColumn(removed, "deleted_at = now()");
        setJobColumn(closed, "status = 'CLOSED'");
        String companyName =
                jdbcTemplate.queryForObject("SELECT name FROM companies WHERE id = ?", String.class, hr.companyId());

        Map<String, JsonNode> byId = listByJobId(candidate.token());

        JsonNode removedItem = byId.get(removed.toString());
        assertThat(removedItem.get("summary").isNull()).isTrue();
        assertThat(removedItem.get("title").asString()).isEqualTo("Tin U16 " + removed);
        assertThat(removedItem.get("company").get("name").asString()).isEqualTo(companyName);

        JsonNode summary = byId.get(closed.toString()).get("summary");
        assertThat(summary.size()).isEqualTo(16);
        assertThat(summary.get("id").asString()).isEqualTo(closed.toString());
        assertThat(summary.get("title").asString()).isEqualTo("Tin U16 " + closed);
        assertThat(summary.get("categoryCode").asString()).isEqualTo("IT_SOFTWARE");
        assertThat(summary.get("locationCode").asString()).isEqualTo("HA_NOI");
        assertThat(summary.get("salaryCurrency").asString()).isEqualTo("VND");
        assertThat(summary.get("company").get("id").asString()).isEqualTo(hr.companyId().toString());
    }

    // ---- T13 - don o chu ky hien tai ----
    @Test
    void t13_currentCycleApplication() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        UUID noApplication = insertOpenJob(hr);
        UUID pending = insertOpenJob(hr);
        UUID withdrawn = insertOpenJob(hr);
        UUID oldCycle = insertOpenJob(hr);
        for (UUID jobId : List.of(noApplication, pending, withdrawn, oldCycle)) {
            putSave(candidate.token(), jobId);
        }
        UUID resumeId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO resumes (id, candidate_id, file_url, file_name, file_type, parse_status) "
                        + "VALUES (?, ?, ?, 'cv.pdf', 'PDF', 'DONE')",
                resumeId,
                candidate.id(),
                "resumes/" + resumeId + ".pdf");
        UUID pendingApp = insertApplication(pending, candidate.id(), resumeId, "PENDING");
        UUID withdrawnApp = insertApplication(withdrawn, candidate.id(), resumeId, "WITHDRAWN");
        insertApplication(oldCycle, candidate.id(), resumeId, "PENDING");
        setJobColumn(oldCycle, "recruitment_cycle = 2");

        Map<String, JsonNode> byId = listByJobId(candidate.token());

        assertThat(byId.get(noApplication.toString()).get("application").isNull()).isTrue();
        assertThat(byId.get(pending.toString()).get("application").get("id").asString())
                .isEqualTo(pendingApp.toString());
        assertThat(byId.get(pending.toString()).get("application").get("status").asString()).isEqualTo("PENDING");
        assertThat(byId.get(withdrawn.toString()).get("application").get("id").asString())
                .isEqualTo(withdrawnApp.toString());
        assertThat(byId.get(withdrawn.toString()).get("application").get("status").asString())
                .isEqualTo("WITHDRAWN");
        assertThat(byId.get(oldCycle.toString()).get("application").isNull()).isTrue();
    }

    private UUID insertApplication(UUID jobId, UUID candidateId, UUID resumeId, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_applications
                    (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent, ai_consent_at)
                VALUES (?, ?, ?, ?, 1, ?, TRUE, now())
                """,
                id,
                jobId,
                candidateId,
                resumeId,
                status);
        return id;
    }

    // ---- T14 - khoa cam ----
    @Test
    void t14_responsesHaveNoForbiddenKeys() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        UUID open = insertOpenJob(hr);
        UUID removed = insertOpenJob(hr);
        putSave(candidate.token(), open);
        putSave(candidate.token(), removed);
        setJobColumn(removed, "deleted_at = now()");

        List<String> keys = new ArrayList<>();
        collectKeys(body(getList(candidate.token())), keys);
        collectKeys(body(getIds(candidate.token())), keys);

        // Doi chung helper: khoa hop le PHAI co mat (categoryLabel khong bi coi la "label").
        assertThat(keys).contains("jobId", "availability", "summary", "categoryLabel");
        assertThat(keys).doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
    }

    // ---- B7 - tin dang luu nhung khong con mo -> 409, dong khong doi ----
    @Test
    void b7_putSavedJobThatIsNowClosed_returns409_rowUnchanged() throws Exception {
        HrAccount hr = registerHrWithCompany("u16c-hr");
        Account candidate = registerCandidate("u16c-cand");
        UUID jobId = insertOpenJob(hr);
        assertThat(putSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);
        Row before = row(candidate.id(), jobId);
        setJobColumn(jobId, "status = 'CLOSED'");

        MvcResult result = putSave(candidate.token(), jobId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).get("error").asString()).isEqualTo("JOB_NOT_SAVEABLE");
        assertThat(row(candidate.id(), jobId)).isEqualTo(before);
        assertThat(before.deletedAt()).isNull();
    }
}
