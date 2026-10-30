package com.recruitment.savedjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-U16 T20 (muc 6 "Quyen rieng tu", L8). Ba ung vien luu tin cua HR -> JSON cua danh sach tin, chi tiet tin va dashboard
// phia HR KHONG co khoa nao lien quan luot luu, va giong HET truoc khi ai luu (so toan bo, khong bo khoa nao).
class SavedJobHrPrivacyIntegrationTest extends SavedJobIntegrationTestSupport {

    private static final List<String> FORBIDDEN_SUBSTRINGS = List.of("saved", "bookmark", "favorite");

    private List<JsonNode> hrViews(HrAccount hr, UUID jobId) throws Exception {
        List<JsonNode> views = new ArrayList<>();
        for (String path : List.of("/api/hr/jobs", "/api/hr/jobs/" + jobId, "/api/hr/dashboard/stats")) {
            MvcResult result = call(get(path), hr.token());
            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
            views.add(body(result));
        }
        return views;
    }

    @Test
    void t20_hrJobAndDashboardJson_unchangedAfterSaves_noSaveRelatedKeys() throws Exception {
        HrAccount hr = registerHrWithCompany("u16p-hr");
        UUID jobId = insertOpenJob(hr);
        List<JsonNode> before = hrViews(hr, jobId);

        for (int i = 0; i < 3; i++) {
            Account candidate = registerCandidate("u16p-cand");
            assertThat(putSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM saved_jobs WHERE job_id = ? AND deleted_at IS NULL", Long.class, jobId))
                .isEqualTo(3L);

        List<JsonNode> after = hrViews(hr, jobId);

        assertThat(after).containsExactlyElementsOf(before);
        for (JsonNode view : after) {
            List<String> keys = new ArrayList<>();
            collectKeys(view, keys);
            assertThat(keys).isNotEmpty();
            assertThat(keys).noneMatch(key -> FORBIDDEN_SUBSTRINGS.stream()
                    .anyMatch(forbidden -> key.toLowerCase(Locale.ROOT).contains(forbidden)));
        }
    }
}
