package com.recruitment.savedjob;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

// FR-U16 T5, T10 - so thoi gian saved_at/thu tu. Lop cha KHONG @Transactional: moi request MockMvc tu commit nen now()
// cua moi lan luu khac nhau (CLAUDE.md muc 3c). Truong hop can saved_at bang nhau thi backdate tuong minh bang SQL.
class SavedJobOrderingIntegrationTest extends SavedJobIntegrationTestSupport {

    private List<String> listedJobIds(String token) throws Exception {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : body(getList(token)).values()) {
            ids.add(item.get("jobId").asString());
        }
        return ids;
    }

    // ---- T5 ----
    @Test
    void t5_saveUnsaveSave_reusesSameRow_savedAtMovesForward() throws Exception {
        HrAccount hr = registerHrWithCompany("u16o-hr");
        Account candidate = registerCandidate("u16o-cand");
        UUID jobId = insertOpenJob(hr);

        assertThat(putSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);
        Row first = row(candidate.id(), jobId);
        assertThat(deleteSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);
        assertThat(row(candidate.id(), jobId).deletedAt()).isNotNull();
        assertThat(putSave(candidate.token(), jobId).getResponse().getStatus()).isEqualTo(204);
        Row again = row(candidate.id(), jobId);

        assertThat(countRows(candidate.id(), jobId)).isEqualTo(1L);
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.deletedAt()).isNull();
        assertThat(again.savedAt()).isAfter(first.savedAt());
    }

    // ---- T10 ----
    @Test
    void t10_listNewestSavedFirst() throws Exception {
        HrAccount hr = registerHrWithCompany("u16o-hr");
        Account candidate = registerCandidate("u16o-cand");
        UUID j1 = insertOpenJob(hr);
        UUID j2 = insertOpenJob(hr);
        UUID j3 = insertOpenJob(hr);
        putSave(candidate.token(), j1);
        putSave(candidate.token(), j2);
        putSave(candidate.token(), j3);

        List<String> expected = List.of(j3.toString(), j2.toString(), j1.toString());
        assertThat(listedJobIds(candidate.token())).containsExactlyElementsOf(expected);
        assertThat(idsOf(getIds(candidate.token()))).containsExactlyElementsOf(expected);
    }

    // T10 bien: saved_at bang nhau -> khoa cuoi id DESC (R-V1), o ca E3 va E4.
    @Test
    void t10_equalSavedAt_tieBrokenByIdDesc() throws Exception {
        HrAccount hr = registerHrWithCompany("u16o-hr");
        Account candidate = registerCandidate("u16o-cand");
        UUID j1 = insertOpenJob(hr);
        UUID j2 = insertOpenJob(hr);
        putSave(candidate.token(), j1);
        putSave(candidate.token(), j2);
        jdbcTemplate.update(
                "UPDATE saved_jobs SET saved_at = TIMESTAMPTZ '2026-10-01 08:00:00+00' WHERE candidate_id = ?",
                candidate.id());
        List<String> expected = jdbcTemplate.queryForList(
                "SELECT job_id::text FROM saved_jobs WHERE candidate_id = ? ORDER BY id DESC", String.class,
                candidate.id());

        assertThat(listedJobIds(candidate.token())).containsExactlyElementsOf(expected);
        assertThat(idsOf(getIds(candidate.token()))).containsExactlyElementsOf(expected);
    }
}
