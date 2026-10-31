package com.recruitment.reconsideration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.blindreview.CandidateAlias;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-C09 T18 (R-A1-R-A5) - don dang an danh (Job bat cong tac, chua tung moi): moi response phia HR co khoi yeu cau (H1
// OPEN/ALL, khoi o E1 FR-H09, response H2) tra nhan "Ung vien #...", reason VA hrResponse da che; chuoi JSON THO cua ca ba
// khong chua ho ten, email, so dien thoai o bat ky khoa nao; DB va phia ung vien giu nguyen van. IdentityMasker tra chuoi
// da chuan hoa NFC (Plan Mode L7) - van ban test la ASCII nen NFC khong doi gi.
class ReconsiderationBlindReviewIntegrationTest extends ReconsiderationIntegrationTestSupport {

    private static final String FULL_NAME = "Tran Thi Bich Ngoc";
    private static final String PHONE = "0912345678";
    private static final String MASK = "[đã ẩn]";

    private record MaskedFixture(Fixture f, String email) {}

    private MaskedFixture maskedOpenRequest(String prefix) throws Exception {
        HrAccount hr = registerHrWithCompany(prefix + "-hr");
        Account candidate = registerCandidate(prefix + "-c", FULL_NAME);
        jdbcTemplate.update("UPDATE users SET phone = ? WHERE id = ?", PHONE, candidate.id());
        String email = jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, candidate.id());
        UUID jobId = insertOpenJob(hr);
        insertTemplate(jobId);
        jdbcTemplate.update("UPDATE jobs SET blind_review_enabled = TRUE WHERE id = ?", jobId);
        Fixture f = rejectedAtScreening(hr, candidate, jobId);
        String reason = "Toi la " + FULL_NAME + ", email " + email + ", SDT " + PHONE + ". Xin xem lai.";
        assertThat(postReason(candidate.token(), f.applicationId(), reason).getResponse().getStatus()).isEqualTo(201);
        return new MaskedFixture(f, email);
    }

    private static String raw(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void assertNoIdentity(String rawJson, MaskedFixture m) {
        assertThat(rawJson).doesNotContain(FULL_NAME).doesNotContain(m.email()).doesNotContain(PHONE);
    }

    private static void assertMaskedBlock(JsonNode block, UUID applicationId) {
        assertThat(block.get("candidateName").asString()).isEqualTo(CandidateAlias.of(applicationId));
        assertThat(block.get("identityMasked").asBoolean()).isTrue();
        assertThat(block.get("reason").asString())
                .isEqualTo(Normalizer.normalize("Toi la " + MASK + ", email " + MASK + ", SDT " + MASK + ". Xin xem lai.",
                        Normalizer.Form.NFC));
    }

    @Test
    void t18_maskedApplication_everyHrResponseMasked_rawJsonClean() throws Exception {
        MaskedFixture m = maskedOpenRequest("c09-t18");
        Fixture f = m.f();

        // (a), (b) - H1 OPEN va khoi H09 truoc khi quyet.
        MvcResult openList = listH1(f.hr().token(), f.jobId(), null);
        MvcResult hrDetail = getHrDetail(f.hr().token(), f.applicationId());
        assertMaskedBlock(body(openList).get("items").get(0), f.applicationId());
        assertMaskedBlock(body(hrDetail).get("reconsideration"), f.applicationId());
        assertNoIdentity(raw(openList), m);
        assertNoIdentity(raw(hrDetail), m);

        // H2 voi phan hoi chua ho ten - response H2, H1 ALL, khoi H09 deu che ca hrResponse.
        MvcResult declined = declineWith(f.hr().token(), f.applicationId(), "Chao " + FULL_NAME + ", rat tiec.");
        MvcResult allList = listH1(f.hr().token(), f.jobId(), "ALL");
        MvcResult detailAfter = getHrDetail(f.hr().token(), f.applicationId());

        assertThat(declined.getResponse().getStatus()).isEqualTo(200);
        for (JsonNode block : new JsonNode[] {
            body(declined), body(allList).get("items").get(0), body(detailAfter).get("reconsideration")
        }) {
            assertMaskedBlock(block, f.applicationId());
            assertThat(block.get("hrResponse").asString()).isEqualTo("Chao " + MASK + ", rat tiec.");
        }
        assertNoIdentity(raw(declined), m);
        assertNoIdentity(raw(allList), m);
        assertNoIdentity(raw(detailAfter), m);

        // (c) DB nguyen van.
        Map<String, Object> row = requestRow(f.applicationId());
        assertThat((String) row.get("reason")).contains(FULL_NAME).contains(m.email()).contains(PHONE);
        assertThat(row.get("hr_response")).isEqualTo("Chao " + FULL_NAME + ", rat tiec.");

        // (d) Phia ung vien khong che (R-A5).
        JsonNode candidateBlock = body(getDetail(f.candidate().token(), f.applicationId())).get("reconsideration");
        assertThat(candidateBlock.get("reason").asString()).contains(FULL_NAME).contains(PHONE);
        assertThat(candidateBlock.get("hrResponse").asString()).isEqualTo("Chao " + FULL_NAME + ", rat tiec.");

        // (e) Thong bao RECONSIDERATION_REQUESTED khong chua ho ten (va khong chua ly do).
        String notificationBody = jdbcTemplate.queryForObject(
                "SELECT body FROM notifications WHERE type = 'RECONSIDERATION_REQUESTED' AND entity_id = ?",
                String.class,
                f.applicationId());
        assertThat(notificationBody).doesNotContain(FULL_NAME).doesNotContain("Xin xem lai");
    }

    // Ca duong: tat an danh cua Job -> H1 tra nguyen van.
    @Test
    void t18_blindReviewOff_returnsVerbatim() throws Exception {
        MaskedFixture m = maskedOpenRequest("c09-t18b");
        Fixture f = m.f();
        jdbcTemplate.update("UPDATE jobs SET blind_review_enabled = FALSE WHERE id = ?", f.jobId());

        JsonNode item = body(listH1(f.hr().token(), f.jobId(), null)).get("items").get(0);

        assertThat(item.get("candidateName").asString()).isEqualTo(FULL_NAME);
        assertThat(item.get("identityMasked").asBoolean()).isFalse();
        assertThat(item.get("reason").asString()).contains(FULL_NAME).contains(m.email()).contains(PHONE);
    }

    // Moi phong van don an danh: thu thay {{candidateName}} bang ho ten (FR-H16 R-I2), sau do H09 het an danh (R-A4).
    @Test
    void t18_inviteMaskedApplication_replacesPlaceholder_thenUnmasked() throws Exception {
        MaskedFixture m = maskedOpenRequest("c09-t18c");
        Fixture f = m.f();

        MvcResult result =
                sendInvitation(f.hr().token(), f.applicationId(), "Thu moi {{candidateName}}", "Kinh gui {{candidateName}}");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> invitation = jdbcTemplate.queryForMap(
                "SELECT subject, rendered_content FROM interview_invitations WHERE application_id = ?", f.applicationId());
        assertThat(invitation.get("subject")).isEqualTo("Thu moi " + FULL_NAME);
        assertThat(invitation.get("rendered_content")).isEqualTo("Kinh gui " + FULL_NAME);
        JsonNode detail = body(getHrDetail(f.hr().token(), f.applicationId()));
        assertThat(detail.get("identityMasked").asBoolean()).isFalse();
        assertThat(detail.get("candidateName").asString()).isEqualTo(FULL_NAME);
        assertThat(detail.get("reconsideration").get("status").asString()).isEqualTo("ACCEPTED");
        assertThat(detail.get("reconsideration").get("identityMasked").asBoolean()).isFalse();
    }
}
