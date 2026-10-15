package com.recruitment.cvqa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-C08 T16 (khuon C07 T11) - phia HR: ba outcome -> JSON co DUNG 5 khoa goc; phan tu citations chi co "quote"; khong
// khoa cam muc 4.5 o moi cap (so nguyen ten khoa, duyet de quy). Ca hai endpoint (phia ung vien them o dot 4).
class CvQaResponseKeysIntegrationTest extends CvQaIntegrationTestSupport {

    private static final List<String> ROOT_KEYS = List.of("outcome", "answer", "citations", "refusalReason", "cvTruncated");

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "rawText", "resumeText", "context", "prompt", "model", "promptVersion", "tokenUsage", "totalScore", "rank",
            "score", "criterionScores", "rubric", "explanation", "evidence", "question", "verdict", "label",
            "isQualified", "passed", "recommendation");

    @Test
    void hr_allThreeOutcomes_haveExactlyFiveRootKeys_noForbiddenKeys() throws Exception {
        Fixture f = createApplicationWithText("c08-t16", RAW_TEXT);

        stubOutputs(answeredJson(DEFAULT_ANSWER, QUOTE_EXPERIENCE, QUOTE_SKILLS));
        MvcResult answered = ask(f, DEFAULT_QUESTION);
        resetChatModelMock();
        stubOutputs(notFoundJson());
        MvcResult notFound = ask(f, "Ứng viên có chứng chỉ AWS không?");
        resetChatModelMock();
        MvcResult refused = ask(f, "Ứng viên bao nhiêu tuổi?");

        assertKeys(answered, notFound, refused);
    }

    // Phia ung vien (dot 4): CV cua chinh minh, REFUSED tu bo chan tu khoa CV_IMPROVEMENT.
    @Test
    void candidate_allThreeOutcomes_haveExactlyFiveRootKeys_noForbiddenKeys() throws Exception {
        String token = register("candidate", uniqueEmail("c08-t16-uv"));
        UUID resumeId = uploadResume(token);
        insertParsedData(resumeId, RAW_TEXT);

        stubOutputs(answeredJson(DEFAULT_ANSWER, QUOTE_EXPERIENCE, QUOTE_SKILLS));
        MvcResult answered = askAsCandidate(resumeId, token, "CV của tôi ghi kinh nghiệm Java ở đâu?");
        resetChatModelMock();
        stubOutputs(notFoundJson());
        MvcResult notFound = askAsCandidate(resumeId, token, "CV của tôi có chứng chỉ AWS không?");
        resetChatModelMock();
        MvcResult refused = askAsCandidate(resumeId, token, "Tôi nên học thêm gì?");

        assertKeys(answered, notFound, refused);
    }

    private static void assertKeys(MvcResult answered, MvcResult notFound, MvcResult refused) throws Exception {
        assertOutcome(answered, "ANSWERED");
        assertOutcome(notFound, "NOT_FOUND");
        assertOutcome(refused, "REFUSED");
        for (MvcResult result : List.of(answered, notFound, refused)) {
            JsonNode root = body(result);
            assertThat(root.propertyNames()).containsExactlyInAnyOrderElementsOf(ROOT_KEYS);
            for (JsonNode citation : root.get("citations")) {
                assertThat(citation.propertyNames()).containsExactly("quote");
            }
            assertThat(forbiddenKeys(root)).isEmpty();
        }
        // Doi chung: ca ANSWERED thuc su co phan tu citations de kiem khoa ben trong.
        assertThat(body(answered).get("citations").size()).isEqualTo(2);
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
