package com.recruitment.savedjob;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// FR-U16 T21 (muc 6 "Quyen rieng tu", B2) - quet nguon, KHONG Spring context (khuon AiOutputConvertersSourceGuardTest).
// Du an chia goi theo tinh nang, khong co goi hr/: luat "chi savedjob/ tham chieu savedjob/ va bang saved_jobs" phu moi
// controller /api/hr/** (job/, dashboard/, jobapplication/...). Bo dong comment. Duong dan tinh tu thu muc lam viec
// cua Surefire (backend/).
class SavedJobSourceGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Path SAVED_JOB_PACKAGE = MAIN_SOURCES.resolve("com/recruitment/savedjob");
    private static final List<String> FORBIDDEN_OUTSIDE = List.of("com.recruitment.savedjob", "saved_jobs");

    @Test
    void onlySavedJobPackage_referencesSavedJobCodeOrTable() throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        // Doi chung duong: dung thu muc, quet du nhieu file, va goi savedjob/ that su dung bang saved_jobs.
        assertThat(sources).hasSizeGreaterThan(100);
        assertThat(sources.stream().filter(p -> p.startsWith(SAVED_JOB_PACKAGE)).anyMatch(p -> contains(p, "saved_jobs")))
                .isTrue();

        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            if (source.startsWith(SAVED_JOB_PACKAGE)) {
                continue;
            }
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (isComment(trimmed)) {
                    continue;
                }
                for (String forbidden : FORBIDDEN_OUTSIDE) {
                    if (trimmed.contains(forbidden)) {
                        violations.add(MAIN_SOURCES.relativize(source) + ":" + (i + 1) + ": " + trimmed);
                    }
                }
            }
        }
        assertThat(violations).as("Chi goi savedjob/ duoc biet ve tin da luu (khong lo cho HR)").isEmpty();
    }

    private static boolean contains(Path source, String text) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8).contains(text);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isComment(String trimmed) {
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }
}
