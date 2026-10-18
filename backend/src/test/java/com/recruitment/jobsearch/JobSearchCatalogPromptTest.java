package com.recruitment.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.catalog.CatalogEntry;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.resume.LlmTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// FR-U13 T6 - danh muc trong prompt (R-A2). Ca so dong/thu tu chay tren danh muc THAT (V8 qua CatalogRegistry cua
// context - 34 tinh/thanh, 24 nganh gom OTHER); ky vong dung tu registry, khong cung thu tu bi danh (phu thuoc
// collation - muc 13.5/L9). Ca dinh dang dong chay tren fixture tu dung.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class JobSearchCatalogPromptTest {

    @Autowired
    private CatalogRegistry catalogRegistry;

    @Autowired
    private JobSearchCatalogPrompt catalogPrompt;

    private static String expectedLine(CatalogEntry entry) {
        return entry.aliases().isEmpty()
                ? entry.code() + ": " + entry.label()
                : entry.code() + ": " + entry.label() + " (còn gọi: " + String.join(", ", entry.aliases()) + ")";
    }

    // ---- danh muc that ----

    @Test
    void provinceCatalog_34LinesInSortOrder_eachCodeLabelAndAliases() {
        List<String> lines = List.of(catalogPrompt.provinceCatalog().split("\n", -1));

        assertThat(lines).hasSize(34);
        assertThat(lines).containsExactlyElementsOf(
                catalogRegistry.provinces().stream().map(JobSearchCatalogPromptTest::expectedLine).toList());
        assertThat(lines.get(0)).startsWith("HA_NOI: Hà Nội (còn gọi: ").contains("Hanoi").contains("HN");
    }

    @Test
    void industryCatalog_23LinesInSortOrder_withoutOther() {
        List<String> lines = List.of(catalogPrompt.industryCatalog().split("\n", -1));
        List<CatalogEntry> expected = catalogRegistry.industries().stream()
                .filter(entry -> !"OTHER".equals(entry.code()))
                .toList();

        assertThat(catalogRegistry.industries()).hasSize(24);
        assertThat(lines).hasSize(23);
        assertThat(lines).containsExactlyElementsOf(
                expected.stream().map(JobSearchCatalogPromptTest::expectedLine).toList());
        assertThat(lines).noneMatch(line -> line.startsWith("OTHER:"));
        assertThat(catalogPrompt.industryCatalog()).doesNotContain("OTHER");
    }

    // Dung MOT lan: hai lan goi cung instance -> cung doi tuong (khong dung lai moi request).
    @Test
    void catalogs_builtOnce_sameInstanceOnRepeatedCalls() {
        assertThat(catalogPrompt.provinceCatalog()).isSameAs(catalogPrompt.provinceCatalog());
        assertThat(catalogPrompt.industryCatalog()).isSameAs(catalogPrompt.industryCatalog());
    }

    // ---- dinh dang dong (fixture) ----

    @Test
    void fixture_lineFormat_aliasesInRegistryOrder_noParenthesesWithoutAliases_otherDropped() {
        CatalogRegistry registry = new CatalogRegistry(
                List.of(
                        new CatalogEntry("IT_SOFTWARE", "Công nghệ thông tin - Phần mềm", List.of("IT", "CNTT")),
                        new CatalogEntry("OTHER", "Khác", List.of("Ngành khác")),
                        new CatalogEntry("LOGISTICS", "Vận tải - Kho vận", List.of())),
                List.of(
                        new CatalogEntry("HUE", "Huế", List.of()),
                        new CatalogEntry("HA_NOI", "Hà Nội", List.of("HN", "Hanoi"))));

        JobSearchCatalogPrompt prompt = new JobSearchCatalogPrompt(registry);

        assertThat(prompt.provinceCatalog()).isEqualTo("HUE: Huế\nHA_NOI: Hà Nội (còn gọi: HN, Hanoi)");
        assertThat(prompt.industryCatalog()).isEqualTo(
                "IT_SOFTWARE: Công nghệ thông tin - Phần mềm (còn gọi: IT, CNTT)\nLOGISTICS: Vận tải - Kho vận");
    }
}
