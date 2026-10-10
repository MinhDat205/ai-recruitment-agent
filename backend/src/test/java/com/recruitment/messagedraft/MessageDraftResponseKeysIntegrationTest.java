package com.recruitment.messagedraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-C07 T11 (lop 3 - response): JSON A2 co DUNG MOT khoa "draft"; JSON A1 chi co khoa o muc 4.4; khong khoa cam nao
// (so nguyen ten khoa, duyet de quy) - ca hai phia.
class MessageDraftResponseKeysIntegrationTest extends MessageDraftIntegrationTestSupport {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "totalScore", "rank", "criterionScores", "score", "rubric", "explanation", "evidence", "model",
            "promptVersion", "context", "prompt", "verdict", "label", "isQualified", "passed", "recommendation");

    private static final Set<String> SCENARIO_OPTION_KEYS = Set.of("scenario", "available", "unavailableReason");

    @Test
    void a2_hasExactlyOneKeyDraft_bothSides() throws Exception {
        Fixture f = createApplication("c07-t11-a2");
        stubDraft(DEFAULT_DRAFT);

        MvcResult hr = postDraft(Side.HR, f.applicationId(), f.hrToken(), "THANK_FOR_APPLYING", "FORMAL");
        MvcResult candidate = postDraft(Side.CANDIDATE, f.applicationId(), f.candidateToken(), "ASK_PROGRESS", "FORMAL");

        for (MvcResult result : List.of(hr, candidate)) {
            assertDraft(result, DEFAULT_DRAFT);
            assertThat(body(result).propertyNames()).containsExactly("draft");
            assertThat(forbiddenKeys(body(result))).isEmpty();
        }
    }

    @Test
    void a1_hasOnlySpecifiedKeys_bothSides() throws Exception {
        Fixture f = createApplication("c07-t11-a1");

        for (Side side : Side.values()) {
            MvcResult result = getScenarios(side, f.applicationId(), f.token(side));
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            JsonNode root = body(result);
            assertThat(root.propertyNames()).containsExactly("scenarios");
            assertThat(root.get("scenarios").isArray()).isTrue();
            assertThat(root.get("scenarios").size()).isPositive();
            for (JsonNode option : root.get("scenarios")) {
                assertThat(option.propertyNames()).containsExactlyInAnyOrderElementsOf(SCENARIO_OPTION_KEYS);
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
