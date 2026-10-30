package com.recruitment.reconsideration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-C09 H1, H2, khoi o E1 cua FR-H09 - T14, T16, T17, T25 cua REQUIREMENT muc 7.1. Lop KHONG @Transactional.
class ReconsiderationHrControllerIntegrationTest extends ReconsiderationIntegrationTestSupport {

    private static final String NOT_OPEN_MESSAGE = "Yêu cầu xem xét lại này không còn chờ xử lý. Vui lòng tải lại trang.";

    private String registerHrWithoutCompany(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc
                .perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"HR C09\","
                                        + "\"phone\":\"0900000000\"}")
                                .formatted(email)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private static List<String> itemIds(JsonNode list) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : list.get("items").values()) {
            ids.add(item.get("applicationId").asString());
        }
        return ids;
    }

    // ---- T14: giu quyet dinh tu choi ----

    @Test
    void t14_declineWithResponse_storesVerbatim_applicationUnchanged() throws Exception {
        Fixture f = openRequest("c09-t14", "Ly do");
        long historyBefore = countHistory(f.applicationId());

        MvcResult result = declineWith(f.hr().token(), f.applicationId(), "  Cam on ban.\r\n<b>Giu nguyen</b> & het  ");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode block = body(result);
        assertThat(block.get("status").asString()).isEqualTo("DECLINED");
        assertThat(block.get("hrResponse").asString()).isEqualTo("  Cam on ban.\n<b>Giu nguyen</b> & het  ");
        assertThat(block.get("decidedAt").isNull()).isFalse();
        Map<String, Object> row = requestRow(f.applicationId());
        assertThat(row.get("status")).isEqualTo("DECLINED");
        assertThat(row.get("hr_response")).isEqualTo("  Cam on ban.\n<b>Giu nguyen</b> & het  ");
        assertThat(row.get("decided_by")).isEqualTo(f.hr().id());
        assertThat(applicationStatus(f.applicationId())).isEqualTo("REJECTED");
        assertThat(countHistory(f.applicationId())).isEqualTo(historyBefore);
    }

    @Test
    void t14_blankOrMissingResponse_storesNull() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t14b-hr");
        Account candidate = registerCandidate("c09-t14b-c");
        List<String> bodies = new ArrayList<>(List.of("{\"response\":\"  \\n \"}", "{\"response\":null}", "{}"));
        bodies.add(null);

        for (String body : bodies) {
            Fixture f = openRequest(hr, candidate, null);
            MvcResult result = decline(hr.token(), f.applicationId(), body);

            assertThat(result.getResponse().getStatus()).as(String.valueOf(body)).isEqualTo(200);
            assertThat(requestRow(f.applicationId()).get("hr_response")).as(String.valueOf(body)).isNull();
            assertThat(requestStatus(f.applicationId())).isEqualTo("DECLINED");
        }
    }

    @Test
    void t14_secondDecline_and_noRequest_return409NotOpen() throws Exception {
        Fixture f = openRequest("c09-t14c", null);
        Fixture none = rejectedAtScreening(f.hr(), f.candidate(), insertOpenJob(f.hr()));

        declineWith(f.hr().token(), f.applicationId(), "Lan mot");
        MvcResult second = declineWith(f.hr().token(), f.applicationId(), "Lan hai");
        MvcResult noRequest = declineWith(f.hr().token(), none.applicationId(), "x");

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(second)).isEqualTo("RECONSIDERATION_NOT_OPEN");
        assertThat(errorMessage(second)).isEqualTo(NOT_OPEN_MESSAGE);
        assertThat(requestRow(f.applicationId()).get("hr_response")).isEqualTo("Lan mot");
        assertThat(noRequest.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(noRequest)).isEqualTo("RECONSIDERATION_NOT_OPEN");
        assertThat(countRequests(none.applicationId())).isZero();
    }

    // Da ACCEPTED (duoc moi lai) -> H2 409.
    @Test
    void t14_declineAfterAccepted_returns409() throws Exception {
        Fixture f = openRequest("c09-t14d", null);
        assertThat(sendInvitation(f.hr().token(), f.applicationId()).getResponse().getStatus()).isEqualTo(201);

        MvcResult result = declineWith(f.hr().token(), f.applicationId(), "x");

        assertThat(errorCode(result)).isEqualTo("RECONSIDERATION_NOT_OPEN");
        assertThat(requestStatus(f.applicationId())).isEqualTo("ACCEPTED");
        assertThat(requestRow(f.applicationId()).get("hr_response")).isNull();
    }

    // Bien do dai phan hoi: 1000 hop le, 1001 -> 400 khong ghi; van ban kiem TRUOC UPDATE.
    @Test
    void t14_responseLengthBoundary() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t14e-hr");
        Account candidate = registerCandidate("c09-t14e-c");
        Fixture ok = openRequest(hr, candidate, null);
        Fixture tooLong = openRequest(hr, candidate, null);

        assertThat(declineWith(hr.token(), ok.applicationId(), "b".repeat(1000)).getResponse().getStatus())
                .isEqualTo(200);
        MvcResult result = declineWith(hr.token(), tooLong.applicationId(), "b".repeat(1001));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("RECONSIDERATION_TEXT_TOO_LONG");
        assertThat(requestStatus(tooLong.applicationId())).isEqualTo("OPEN");
        assertThat(requestRow(tooLong.applicationId()).get("hr_response")).isNull();
    }

    // ---- T16: quyen HR ----

    @Test
    void t16_otherCompany403_noCompany404_candidate403_unknown404() throws Exception {
        Fixture f = openRequest("c09-t16", null);
        HrAccount other = registerHrWithCompany("c09-t16-other");
        String noCompany = registerHrWithoutCompany("c09-t16-nocomp");

        assertThat(listH1(other.token(), f.jobId(), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(declineWith(other.token(), f.applicationId(), "x").getResponse().getStatus()).isEqualTo(403);

        MvcResult listNoCompany = listH1(noCompany, f.jobId(), null);
        MvcResult declineNoCompany = declineWith(noCompany, f.applicationId(), "x");
        assertThat(listNoCompany.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(listNoCompany)).isEqualTo("COMPANY_NOT_FOUND");
        assertThat(declineNoCompany.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(declineNoCompany)).isEqualTo("COMPANY_NOT_FOUND");

        assertThat(listH1(f.candidate().token(), f.jobId(), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(declineWith(f.candidate().token(), f.applicationId(), "x").getResponse().getStatus())
                .isEqualTo(403);

        assertThat(errorCode(listH1(f.hr().token(), UUID.randomUUID(), null))).isEqualTo("JOB_NOT_FOUND");
        assertThat(errorCode(declineWith(f.hr().token(), UUID.randomUUID(), "x"))).isEqualTo("APPLICATION_NOT_FOUND");

        assertThat(requestStatus(f.applicationId())).isEqualTo("OPEN");
    }

    // ---- T17: danh sach H1 ----

    @Test
    void t17_defaultOpenOnly_allIncludesDecided_openCountIndependent() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t17-hr");
        Account candidate = registerCandidate("c09-t17-c");
        UUID jobId = insertOpenJob(hr);
        insertTemplate(jobId);
        UUID open = rejectedAtScreening(hr, candidate, jobId).applicationId();
        insertRequest(open, "OPEN", null, null, null);
        // Cung tin, chu ky cu (uq_application_per_cycle theo chu ky): don ACCEPTED va DECLINED.
        jdbcTemplate.update("UPDATE jobs SET recruitment_cycle = 2 WHERE id = ?", jobId);
        Account c2 = registerCandidate("c09-t17-c2");
        Account c3 = registerCandidate("c09-t17-c3");
        UUID declined = rejectedAtScreening(hr, c2, jobId).applicationId();
        insertRequest(declined, "DECLINED", hr.id(), null, "Phan hoi");
        UUID accepted = rejectedAtScreening(hr, c3, jobId).applicationId();
        insertRequest(accepted, "ACCEPTED", hr.id(), null, null);

        JsonNode defaultList = body(listH1(hr.token(), jobId, null));
        JsonNode openList = body(listH1(hr.token(), jobId, "OPEN"));
        JsonNode oddList = body(listH1(hr.token(), jobId, "khong-hop-le"));
        JsonNode allList = body(listH1(hr.token(), jobId, "ALL"));
        JsonNode allLower = body(listH1(hr.token(), jobId, "all"));

        assertThat(itemIds(defaultList)).containsExactly(open.toString());
        assertThat(itemIds(openList)).containsExactly(open.toString());
        assertThat(itemIds(oddList)).containsExactly(open.toString());
        assertThat(itemIds(allList))
                .containsExactlyInAnyOrder(open.toString(), declined.toString(), accepted.toString());
        assertThat(itemIds(allLower)).hasSize(3);
        for (JsonNode list : List.of(defaultList, allList)) {
            assertThat(list.get("jobId").asString()).isEqualTo(jobId.toString());
            assertThat(list.get("openCount").asInt()).isEqualTo(1);
        }
        JsonNode first = defaultList.get("items").get(0);
        assertThat(first.get("candidateName").asString()).isEqualTo("Ung Vien C09");
        assertThat(first.get("identityMasked").asBoolean()).isFalse();
        assertThat(first.get("appliedAt").isNull()).isFalse();
        assertThat(first.get("rejectedAt").isNull()).isFalse();
        assertThat(first.get("requestedAt").isNull()).isFalse();
        assertThat(keysOf(first)).doesNotContain("decidedBy");
    }

    // Thu tu requested_at ASC, application_id ASC; hai yeu cau cung requested_at -> thu tu on dinh theo application_id.
    @Test
    void t17_orderByRequestedAtThenApplicationId_stable() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t17b-hr");
        UUID jobId = insertOpenJob(hr);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Account candidate = registerCandidate("c09-t17b-c" + i);
            UUID applicationId = rejectedAtScreening(hr, candidate, jobId).applicationId();
            insertRequest(applicationId, "OPEN", null, null, null);
            ids.add(applicationId);
        }
        // Hai yeu cau dau cung requested_at, yeu cau thu ba som hon ca hai.
        jdbcTemplate.update(
                "UPDATE application_reconsideration_requests SET requested_at = TIMESTAMPTZ '2026-10-20 09:00:00+07' "
                        + "WHERE application_id IN (?, ?)",
                ids.get(0),
                ids.get(1));
        jdbcTemplate.update(
                "UPDATE application_reconsideration_requests SET requested_at = TIMESTAMPTZ '2026-10-19 09:00:00+07' "
                        + "WHERE application_id = ?",
                ids.get(2));
        List<String> tied = new ArrayList<>(List.of(ids.get(0).toString(), ids.get(1).toString()));
        tied.sort(null);
        List<String> expected = new ArrayList<>();
        expected.add(ids.get(2).toString());
        expected.addAll(tied);

        for (int round = 0; round < 3; round++) {
            assertThat(itemIds(body(listH1(hr.token(), jobId, null)))).containsExactlyElementsOf(expected);
        }
    }

    // ---- T25: khoi o E1 cua FR-H09 (du lieu cho R-D7) ----

    @Test
    void t25_hrDetailBlock_followsRequestLifecycle() throws Exception {
        HrAccount hr = registerHrWithCompany("c09-t25-hr");
        Account candidate = registerCandidate("c09-t25-c");
        Fixture open = openRequest(hr, candidate, "Ly do mo");
        Fixture none = rejectedAtScreening(hr, candidate, insertOpenJob(hr));
        Fixture toDecline = openRequest(hr, candidate, null);

        JsonNode openDetail = body(getHrDetail(hr.token(), open.applicationId()));
        JsonNode noneDetail = body(getHrDetail(hr.token(), none.applicationId()));
        declineWith(hr.token(), toDecline.applicationId(), "Giu nguyen");
        JsonNode declinedDetail = body(getHrDetail(hr.token(), toDecline.applicationId()));
        sendInvitation(hr.token(), open.applicationId());
        JsonNode acceptedDetail = body(getHrDetail(hr.token(), open.applicationId()));

        assertThat(openDetail.get("status").asString()).isEqualTo("REJECTED");
        assertThat(openDetail.get("reconsideration").get("status").asString()).isEqualTo("OPEN");
        assertThat(openDetail.get("reconsideration").get("reason").asString()).isEqualTo("Ly do mo");
        assertThat(noneDetail.has("reconsideration")).isTrue();
        assertThat(noneDetail.get("reconsideration").isNull()).isTrue();
        assertThat(declinedDetail.get("status").asString()).isEqualTo("REJECTED");
        assertThat(declinedDetail.get("reconsideration").get("status").asString()).isEqualTo("DECLINED");
        assertThat(declinedDetail.get("reconsideration").get("hrResponse").asString()).isEqualTo("Giu nguyen");
        assertThat(acceptedDetail.get("status").asString()).isEqualTo("INTERVIEW_INVITED");
        assertThat(acceptedDetail.get("reconsideration").get("status").asString()).isEqualTo("ACCEPTED");
    }
}
