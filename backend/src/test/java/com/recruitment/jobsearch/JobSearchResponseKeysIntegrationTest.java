package com.recruitment.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.job.Job;
import com.recruitment.job.dto.JobSummaryResponse;
import java.lang.reflect.RecordComponent;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-U13 T16 (khuon CvQaResponseKeysIntegrationTest) - ca hai nhanh xep: goc DUNG 3 khoa; interpretation DUNG 7 khoa; moi
// items[i] DUNG 3 khoa; job = khoa JobSummaryResponse co san; khong khoa cam muc 4.5 o moi cap (so nguyen ten khoa, duyet
// de quy).
class JobSearchResponseKeysIntegrationTest extends JobSearchIntegrationTestSupport {

    private static final List<String> ROOT_KEYS = List.of("interpretation", "ranking", "items");

    private static final List<String> INTERPRETATION_KEYS = List.of(
            "locationCodes", "industryCodes", "salaryMin", "salaryMax", "workModes", "description",
            "personalInfoIgnored");

    private static final List<String> ITEM_KEYS = List.of("job", "matchedConditions", "rankedByDescription");

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "score", "similarity", "distance", "relevance", "embedding", "vector", "rank", "model", "prompt",
            "promptVersion", "sourceText", "personalInfoTexts", "query", "verdict", "label", "isQualified", "passed",
            "recommendation");

    @Test
    void descriptionAndNewestBranches_haveExactKeys_noForbiddenKeys() throws Exception {
        Candidate c = registerCandidate("u13-t16");
        Job embeddedJob = newJob("co embedding", NOW.minus(1, ChronoUnit.DAYS));
        embeddedJob.setLocationCode("HA_NOI");
        embeddedJob.setWorkMode("REMOTE");
        salary(embeddedJob, 10_000_000L, 20_000_000L, "VND");
        embedded(save(embeddedJob), 0.8);
        Job plainJob = newJob("chua embedding", NOW.minus(2, ChronoUnit.DAYS));
        plainJob.setLocationCode("HA_NOI");
        plainJob.setWorkMode("REMOTE");
        save(plainJob);

        stubOutputs(payload()
                .location("HA_NOI", "ở Hà Nội")
                .salary(15, null, "lương trên 15 triệu")
                .workMode("REMOTE", "từ xa")
                .personalInfo("nữ")
                .json());
        stubEmbedding();
        MvcResult description = search(c.token(), "kế toán nữ lương trên 15 triệu ở Hà Nội từ xa");

        stubOutputs(payload().location("HA_NOI", "ở Hà Nội").workMode("REMOTE", "từ xa").json());
        MvcResult newest = search(c.token(), "ở Hà Nội từ xa");

        assertStatus(description, 200);
        assertStatus(newest, 200);
        assertThat(body(description).get("ranking").asString()).isEqualTo("DESCRIPTION");
        assertThat(body(newest).get("ranking").asString()).isEqualTo("NEWEST");

        List<String> jobKeys = Arrays.stream(JobSummaryResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        for (MvcResult result : List.of(description, newest)) {
            JsonNode root = body(result);
            assertThat(root.propertyNames()).containsExactlyInAnyOrderElementsOf(ROOT_KEYS);
            assertThat(root.get("interpretation").propertyNames())
                    .containsExactlyInAnyOrderElementsOf(INTERPRETATION_KEYS);
            // Doi chung: co phan tu items de kiem khoa ben trong.
            assertThat(root.get("items").size()).isEqualTo(2);
            for (JsonNode item : root.get("items")) {
                assertThat(item.propertyNames()).containsExactlyInAnyOrderElementsOf(ITEM_KEYS);
                assertThat(item.get("job").propertyNames()).containsExactlyInAnyOrderElementsOf(jobKeys);
            }
            assertThat(forbiddenKeys(root)).isEmpty();
        }
    }

    private static List<String> forbiddenKeys(JsonNode node) {
        List<String> found = new ArrayList<>();
        collect(node, found);
        return found;
    }

    private static void collect(JsonNode node, List<String> found) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (FORBIDDEN_KEYS.contains(entry.getKey())) {
                    found.add(entry.getKey());
                }
                collect(entry.getValue(), found);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                collect(element, found);
            }
        }
    }
}
