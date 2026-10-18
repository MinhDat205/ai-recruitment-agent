package com.recruitment.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.ai.jobsearch.JobSearchParseService;
import com.recruitment.ai.sync.SyncAiCaller;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;

// FR-U13 T18 (khuon CvQaSignatureTest) - chu ky + phu thuoc, KHONG Spring context: ai/jobsearch khong import jobsearch/,
// job/, resume/, catalog/ (muc 4.2); constructor JobSearchParseService chi nhan K3 + bean ChatClient + resource;
// SyncAiCaller giu dung constructor 3 tham so. Phan "file K3 cu va test cua C07/H11/C08/U12 khong doi so voi main" chay
// tay bang git o kiem tinh 7.1 (L33 - CI khong co ref main). Duong dan nguon tinh tu thu muc lam viec cua Surefire
// (backend/).
class JobSearchSignatureTest {

    private static final Path AI_JOBSEARCH_SOURCES = Path.of("src/main/java/com/recruitment/ai/jobsearch");

    private static final Pattern FORBIDDEN_IMPORT =
            Pattern.compile("^import\\s+(static\\s+)?com\\.recruitment\\.(jobsearch|job|resume|catalog)\\.");
    private static final Pattern K3_IMPORT = Pattern.compile("^import\\s+com\\.recruitment\\.ai\\.sync\\.");

    @Test
    void aiJobSearch_doesNotImportJobSearchJobResumeOrCatalog() throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.list(AI_JOBSEARCH_SOURCES)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        // Doi chung duong: dung thu muc, co file, co import K3.
        assertThat(sources).hasSizeGreaterThanOrEqualTo(3);

        List<String> violations = new ArrayList<>();
        int k3Imports = 0;
        for (Path source : sources) {
            for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (FORBIDDEN_IMPORT.matcher(trimmed).find()) {
                    violations.add(source.getFileName() + ": " + trimmed);
                }
                if (K3_IMPORT.matcher(trimmed).find()) {
                    k3Imports++;
                }
            }
        }
        assertThat(violations).isEmpty();
        assertThat(k3Imports).isGreaterThanOrEqualTo(1);
    }

    @Test
    void jobSearchParseService_constructorTakesOnlyChatClientResourceAndK3() {
        Constructor<?>[] constructors = JobSearchParseService.class.getDeclaredConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .containsExactly(ChatClient.class, Resource.class, SyncAiCaller.class);
    }

    @Test
    void syncAiCaller_keepsSingleThreeArgumentConstructor() {
        Constructor<?>[] constructors = SyncAiCaller.class.getDeclaredConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .containsExactly(ExecutorService.class, long.class, String.class);
    }
}
