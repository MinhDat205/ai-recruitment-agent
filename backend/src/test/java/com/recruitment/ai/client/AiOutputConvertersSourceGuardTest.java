package com.recruitment.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// fix/llm-json-control-chars (khuon JobSearchSignatureTest) - quet nguon, KHONG Spring context: moi converter output AI
// phai tao qua AiOutputConverters.forType(...). Mot "new BeanOutputConverter<>(...)" o noi khac dung mapper mac dinh,
// tu choi TAB/CR/LF tho trong chuoi JSON -> LLM_INVALID_JSON voi CV DOCX co tab stop. Bo qua dong comment. Duong dan
// nguon tinh tu thu muc lam viec cua Surefire (backend/).
class AiOutputConvertersSourceGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Path ALLOWED_FILE =
            MAIN_SOURCES.resolve("com/recruitment/ai/client/AiOutputConverters.java");

    // Bat ca ten day du (new org.springframework.ai.converter.BeanOutputConverter<>(...)), kieu long nhau
    // (<List<Foo>>) va raw type (new BeanOutputConverter(...)).
    private static final Pattern DIRECT_CONSTRUCTION =
            Pattern.compile("new\\s+(?:[\\w$]+\\.)*BeanOutputConverter\\s*(?:<.*?>)?\\s*\\(");

    @Test
    void beanOutputConverter_isConstructedOnlyInAiOutputConverters() throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        // Doi chung duong: dung thu muc, quet du nhieu file, va chinh noi duoc phep co dung mot lan tao.
        assertThat(sources).hasSizeGreaterThan(100).contains(ALLOWED_FILE);
        assertThat(countDirectConstructions(ALLOWED_FILE)).isEqualTo(1);

        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            if (source.equals(ALLOWED_FILE)) {
                continue;
            }
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (!isComment(trimmed) && DIRECT_CONSTRUCTION.matcher(trimmed).find()) {
                    violations.add(MAIN_SOURCES.relativize(source) + ":" + (i + 1) + ": " + trimmed);
                }
            }
        }
        assertThat(violations)
                .as("Tao converter output AI qua AiOutputConverters.forType(...), khong new BeanOutputConverter")
                .isEmpty();
    }

    private static long countDirectConstructions(Path source) throws IOException {
        return Files.readAllLines(source, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !isComment(line))
                .filter(line -> DIRECT_CONSTRUCTION.matcher(line).find())
                .count();
    }

    private static boolean isComment(String trimmed) {
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }
}
