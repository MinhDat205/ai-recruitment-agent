package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.recruitment.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// FR-H16 T10 - kho ung vien: P8 nhan theo tung dong (R-K1), P7 them don dang an danh -> 409 o buoc 6b, go van duoc (R-K2).
// @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlindReviewTalentPoolIntegrationTest extends BlindReviewIntegrationTestSupport {

    private static final String MSG_IDENTITY_MASKED =
            "Hồ sơ đang ở chế độ ẩn danh. Thao tác này mở lại khi gửi giấy mời phỏng vấn hoặc khi tắt chế độ ẩn danh của tin.";

    private void consentActive(UUID applicationId) {
        jdbcTemplate.update(
                "UPDATE job_applications SET talent_pool_consent = TRUE, talent_pool_consent_at = now() WHERE id = ?",
                applicationId);
        entityManager.clear();
    }

    private UUID pool(Hr hr) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO talent_pools (id, company_id, name) VALUES (?, ?, ?)", id, hr.companyId(),
                "Kho " + id.toString().substring(0, 8));
        return id;
    }

    private void entry(UUID poolId, UUID applicationId) {
        jdbcTemplate.update("INSERT INTO talent_pool_entries (pool_id, application_id) VALUES (?, ?)", poolId,
                applicationId);
    }

    private int activeEntries(UUID poolId, UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM talent_pool_entries WHERE pool_id = ? AND application_id = ? AND removed_at IS NULL",
                Integer.class, poolId, applicationId);
    }

    private MvcResult setPools(Hr hr, UUID applicationId, List<UUID> poolIds) throws Exception {
        return mockMvc.perform(put("/api/hr/applications/" + applicationId + "/talent-pools")
                        .header("Authorization", "Bearer " + hr.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("poolIds", poolIds))))
                .andReturn();
    }

    private static void assertIdentityMasked(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).get("error").asString()).isEqualTo("IDENTITY_MASKED");
        assertThat(body(result).get("message").asString()).isEqualTo(MSG_IDENTITY_MASKED);
    }

    // ---- P8 ----

    @Test
    void poolEntriesAreMaskedPerRow() throws Exception {
        Hr hr = hr();
        UUID enabledJob = job(hr, true);
        UUID disabledJob = job(hr, false);
        App masked = application(enabledJob, candidate("Quốc Huy", "0900000111"), null);
        App invited = application(enabledJob, candidate("Trần Bảo Ngọc", "0900000222"), null);
        App otherJob = application(disabledJob, candidate("Lê Thị Lan", "0900000333"), null);
        invite(invited);
        UUID poolId = pool(hr);
        for (App app : List.of(masked, invited, otherJob)) {
            consentActive(app.id());
            entry(poolId, app.id());
        }

        MvcResult result = getAs(hr.token(), "/api/hr/talent-pools/" + poolId + "/entries?size=50");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Map<String, String> nameById = new java.util.HashMap<>();
        for (JsonNode node : body(result).get("items")) {
            nameById.put(node.get("applicationId").asString(), node.get("candidateName").asString());
        }
        assertThat(nameById)
                .containsEntry(masked.id().toString(), CandidateAlias.of(masked.id()))
                .containsEntry(invited.id().toString(), "Trần Bảo Ngọc")
                .containsEntry(otherJob.id().toString(), "Lê Thị Lan");
        assertNoLeak(text(result), masked.candidate());
    }

    // ---- P7 ----

    @Test
    void addingMaskedApplicationIsRejectedWithoutWriting() throws Exception {
        Hr hr = hr();
        App masked = application(job(hr, true), candidate("Quốc Huy", "0900000111"), null);
        consentActive(masked.id());
        UUID poolId = pool(hr);

        assertIdentityMasked(setPools(hr, masked.id(), List.of(poolId)));
        assertThat(activeEntries(poolId, masked.id())).isZero();
    }

    @Test
    void identityMaskedIsCheckedBeforeConsent() throws Exception {
        // Buoc 6b truoc buoc 7: don an danh va dong y NOT_GIVEN -> IDENTITY_MASKED, khong phai TALENT_POOL_CONSENT_REQUIRED.
        Hr hr = hr();
        App masked = application(job(hr, true), candidate("Quốc Huy", "0900000111"), null);
        UUID poolId = pool(hr);

        assertIdentityMasked(setPools(hr, masked.id(), List.of(poolId)));
    }

    @Test
    void poolChecksStillComeBeforeIdentityMasked() throws Exception {
        // Buoc 5 (kho khong ton tai -> 404) van truoc buoc 6b.
        Hr hr = hr();
        App masked = application(job(hr, true), candidate("Quốc Huy", "0900000111"), null);
        consentActive(masked.id());

        MvcResult result = setPools(hr, masked.id(), List.of(UUID.randomUUID()));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void removingMaskedApplicationIsAllowed() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, false);
        App app = application(jobId, candidate("Quốc Huy", "0900000111"), null);
        consentActive(app.id());
        UUID poolId = pool(hr);
        entry(poolId, app.id());
        setBlindReview(jobId, true);

        MvcResult result = setPools(hr, app.id(), List.of());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(activeEntries(poolId, app.id())).isZero();
    }

    @Test
    void keepingExistingEntryOfMaskedApplicationIsAllowed() throws Exception {
        // Gui lai dung danh sach dang co (khong THEM gi) -> khong bi chan.
        Hr hr = hr();
        UUID jobId = job(hr, false);
        App app = application(jobId, candidate("Quốc Huy", "0900000111"), null);
        consentActive(app.id());
        UUID poolId = pool(hr);
        entry(poolId, app.id());
        setBlindReview(jobId, true);

        assertThat(setPools(hr, app.id(), List.of(poolId)).getResponse().getStatus()).isEqualTo(200);
        assertThat(activeEntries(poolId, app.id())).isEqualTo(1);
    }

    @Test
    void addingAfterInvitationOrWhenJobDisabledSucceeds() throws Exception {
        Hr hr = hr();
        App invited = application(job(hr, true), candidate("Quốc Huy", "0900000111"), null);
        invite(invited);
        consentActive(invited.id());
        App disabled = application(job(hr, false), candidate("Lê Thị Lan", "0900000333"), null);
        consentActive(disabled.id());
        UUID poolId = pool(hr);

        assertThat(setPools(hr, invited.id(), List.of(poolId)).getResponse().getStatus()).isEqualTo(200);
        assertThat(setPools(hr, disabled.id(), List.of(poolId)).getResponse().getStatus()).isEqualTo(200);
        assertThat(activeEntries(poolId, invited.id())).isEqualTo(1);
        assertThat(activeEntries(poolId, disabled.id())).isEqualTo(1);
    }
}
