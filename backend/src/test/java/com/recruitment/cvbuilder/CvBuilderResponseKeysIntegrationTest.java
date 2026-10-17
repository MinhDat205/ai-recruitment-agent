package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-U12 T23 (muc 4.5) - B2 dung 5 khoa goc, form 9 khoa R-F1, contact 5 khoa; ResumeResponse dung 12 khoa (11 cu +
// fromCvBuilder) o B1 va danh sach CV; khong khoa cam o moi cap (duyet de quy). B4 them o dot 5.
class CvBuilderResponseKeysIntegrationTest extends CvBuilderIntegrationTestSupport {

    private static final List<String> DOCUMENT_KEYS = List.of("resumeId", "template", "versionLabel", "form", "createdAt");
    private static final List<String> FORM_KEYS = List.of("contact", "currentTitle", "locationText", "industryCode",
            "education", "experience", "skills", "certifications", "projects");
    private static final List<String> CONTACT_KEYS = List.of("fullName", "email", "phone", "address", "linkedin");
    private static final List<String> RESUME_KEYS = List.of("id", "fileName", "fileType", "fileSize", "versionLabel",
            "isPrimary", "parseStatus", "parseError", "uploadedAt", "schemaVersion", "reparse", "fromCvBuilder");

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "rawText", "raw_text", "model", "promptVersion", "tokenUsage", "prompt", "fileUrl", "photo", "avatar",
            "dateOfBirth", "birthDate", "age", "gender", "maritalStatus", "verdict", "label", "isQualified", "passed",
            "recommendation");

    @Test
    void t23_documentResponse_exactKeys_noForbiddenKeys() throws Exception {
        Candidate c = registerCandidate("u12-t23-b2");
        UUID resumeId = createCvOk(c.token(), sampleRequest(CvTemplate.CLASSIC));

        MvcResult result = getDocument(c.token(), resumeId);

        assertStatus(result, 200);
        JsonNode root = body(result);
        assertThat(root.propertyNames()).containsExactlyInAnyOrderElementsOf(DOCUMENT_KEYS);
        assertThat(root.get("form").propertyNames()).containsExactlyInAnyOrderElementsOf(FORM_KEYS);
        assertThat(root.get("form").get("contact").propertyNames()).containsExactlyInAnyOrderElementsOf(CONTACT_KEYS);
        assertThat(forbiddenKeys(root)).isEmpty();
        // Doi chung: cac khoi danh sach co phan tu de duyet khoa ben trong.
        assertThat(root.get("form").get("experience").size()).isEqualTo(2);
    }

    @Test
    void t23_resumeResponse_twelveKeys_inCreateAndList_noForbiddenKeys() throws Exception {
        Candidate c = registerCandidate("u12-t23-resume");
        uploadResume(c.token());

        MvcResult created = createCv(c.token(), sampleRequest(CvTemplate.MODERN));
        MvcResult list = mockMvc.perform(get("/api/candidates/resumes").header("Authorization", "Bearer " + c.token()))
                .andReturn();

        assertStatus(created, 201);
        assertThat(body(created).propertyNames()).containsExactlyInAnyOrderElementsOf(RESUME_KEYS);
        assertThat(forbiddenKeys(body(created))).isEmpty();
        assertStatus(list, 200);
        JsonNode items = body(list);
        assertThat(items.size()).isEqualTo(2);
        for (JsonNode item : items) {
            assertThat(item.propertyNames()).containsExactlyInAnyOrderElementsOf(RESUME_KEYS);
        }
        assertThat(forbiddenKeys(items)).isEmpty();
        // R-S2: CV tai len (PENDING) -> false; CV dung tu form -> true.
        assertThat(items.valueStream().map(item -> item.get("fromCvBuilder").asBoolean()).toList())
                .containsExactlyInAnyOrder(true, false);
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
