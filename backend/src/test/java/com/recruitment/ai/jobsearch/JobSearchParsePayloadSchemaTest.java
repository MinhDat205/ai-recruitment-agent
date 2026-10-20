package com.recruitment.ai.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-U13 L57 (don vi, khong Spring) - khoa schema {format} THAT ma K3 chen vao prompt: BeanOutputConverter danh dau moi
// field la required tru field @Nullable. salary va hai moc luong phai KHONG required (cau chi co mot moc khong bi ep dien
// moc gia); cac field con lai giu required nhu cu. Kem prompt v2 doi cau "salary = null".
class JobSearchParsePayloadSchemaTest {

    private static final BeanOutputConverter<JobSearchParsePayload> CONVERTER =
            new BeanOutputConverter<>(JobSearchParsePayload.class);

    // Format = huong dan chu (co chu "```json" trong cau huong dan) + schema boc trong ```{ ... }``` -> cat tu "```{".
    private static JsonNode schema() {
        String format = CONVERTER.getFormat();
        String json = format.substring(format.indexOf("```{") + 3, format.lastIndexOf("```"));
        return JsonMapper.builder().build().readTree(json);
    }

    private static List<String> required(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.path("required").forEach(item -> names.add(item.asString()));
        return names;
    }

    @Test
    void rootRequired_excludesSalary_keepsListsRequired() {
        JsonNode root = schema();
        assertThat(root.path("properties").has("salary")).isTrue();
        assertThat(required(root))
                .doesNotContain("salary")
                .containsExactlyInAnyOrder("industries", "locations", "personalInfoTexts", "workModes");
    }

    @Test
    void salaryRequired_onlySourceText() {
        JsonNode salary = schema().path("properties").path("salary");
        assertThat(salary.path("properties").has("minMillionVnd")).isTrue();
        assertThat(salary.path("properties").has("maxMillionVnd")).isTrue();
        assertThat(required(salary)).containsExactly("sourceText");
    }

    // Doi chung: CodeCondition khong gan @Nullable -> van required ca hai field.
    @Test
    void codeConditionRequired_unchanged() {
        JsonNode codeCondition = schema().path("$defs").path("CodeCondition");
        assertThat(required(codeCondition)).containsExactlyInAnyOrder("code", "sourceText");
    }

    // AI bo truong moc con lai (dung prompt v2) -> moc do null; bo truong salary -> salary null. Khong phai output hong.
    @Test
    void missingBoundOrSalary_deserializesToNull() {
        JobSearchParsePayload onlyMin = CONVERTER.convert(
                "{\"salary\":{\"minMillionVnd\":15,\"sourceText\":\"lương trên 15 triệu\"}}");
        assertThat(onlyMin.salary().minMillionVnd()).isEqualTo(15);
        assertThat(onlyMin.salary().maxMillionVnd()).isNull();

        JobSearchParsePayload noSalary = CONVERTER.convert("{\"locations\":[]}");
        assertThat(noSalary.salary()).isNull();
    }

    @Test
    void promptV2_replacesSalaryNullSentence() throws IOException {
        assertThat(JobSearchParseService.PROMPT_VERSION).isEqualTo("job-search-parse-v2");
        String prompt = new ClassPathResource("ai/prompt/" + JobSearchParseService.PROMPT_VERSION + ".st")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(prompt)
                .contains("không có lương thì bỏ trường salary; chỉ có một mốc thì bỏ trường mốc còn lại.")
                .doesNotContain("salary = null")
                .contains("{format}");
    }
}
