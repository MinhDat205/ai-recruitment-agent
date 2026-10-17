package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.ai.cvrephrase.CvRephraseService;
import com.recruitment.ai.sync.SyncAiCaller;
import com.recruitment.resume.ResumeParsedPayload;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;

// FR-U12 T24 (R-S2: resume/ khong import cvbuilder/; muc 4.2: ai/cvrephrase/ khong import cvbuilder/, resume/;
// constructor CvRephraseService chi nhan K3 + bean ChatClient + resource prompt), T25 (R-F2: khong truong nhan than;
// Contact trung khop ResumeParsedPayload.Contact). Test don vi, khong Spring.
class CvBuilderSignatureTest {

    private static final Path RESUME_SOURCES = Path.of("src/main/java/com/recruitment/resume");
    private static final Path CV_REPHRASE_SOURCES = Path.of("src/main/java/com/recruitment/ai/cvrephrase");

    private static final Pattern PERSONAL_FIELD = Pattern.compile(
            "(?i).*(photo|avatar|image|birth|dob|age|gender|sex|marital|religion|ethnic|nationality|height|weight|health).*");

    @Test
    void t24_resumePackageDoesNotImportCvBuilder() throws IOException {
        List<String> cvBuilderImports = grepResumeSources("^import com\\.recruitment\\.cvbuilder\\.");
        // Doi chung duong: cung cach quet tim thay import co that trong resume/.
        List<String> storageImports = grepResumeSources("^import com\\.recruitment\\.storage\\.");

        assertThat(cvBuilderImports).isEmpty();
        assertThat(storageImports).isNotEmpty();
    }

    @Test
    void t24_cvRephrasePackageDoesNotImportCvBuilderOrResume() throws IOException {
        List<String> forbiddenImports =
                grepSources(CV_REPHRASE_SOURCES, "^import com\\.recruitment\\.(cvbuilder|resume)\\.");
        // Doi chung duong: cung cach quet tim thay import K3 co that trong ai/cvrephrase/.
        List<String> syncImports = grepSources(CV_REPHRASE_SOURCES, "^import com\\.recruitment\\.ai\\.sync\\.");

        assertThat(forbiddenImports).isEmpty();
        assertThat(syncImports).isNotEmpty();
    }

    // Khuon CvQaSignatureTest: moi constructor (ke ca khong public) chi nhan K3 + ChatClient + Resource.
    @Test
    void t24_cvRephraseServiceConstructor_onlyK3ChatClientAndResource() {
        Constructor<?>[] constructors = CvRephraseService.class.getDeclaredConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .containsExactlyInAnyOrder(ChatClient.class, Resource.class, SyncAiCaller.class);
        for (Field field : CvRephraseService.class.getDeclaredFields()) {
            assertThat(field.getType().getPackageName())
                    .as(field.getName())
                    .doesNotStartWith("com.recruitment.cvbuilder")
                    .doesNotStartWith("com.recruitment.resume");
        }
    }

    @Test
    void t25_formHasNoPersonalFields() {
        List<String> names = new ArrayList<>();
        collectComponentNames(CvBuilderForm.class, names, new HashSet<>());

        assertThat(names).filteredOn(name -> PERSONAL_FIELD.matcher(name).matches()).isEmpty();
        // Doi chung: duyet de quy da vao toi record long nhau.
        assertThat(names).contains("fullName", "school", "description", "issueDate", "technologies");
        // Doi chung cua mau: ten nhan than that su bi bat.
        assertThat(List.of("dateOfBirth", "gender", "avatarUrl"))
                .allMatch(name -> PERSONAL_FIELD.matcher(name).matches());
    }

    @Test
    void t25_contactEqualsParsedPayloadContact() {
        assertThat(signature(CvBuilderForm.Contact.class)).isEqualTo(signature(ResumeParsedPayload.Contact.class));
    }

    private static List<String> grepResumeSources(String regex) throws IOException {
        return grepSources(RESUME_SOURCES, regex);
    }

    private static List<String> grepSources(Path root, String regex) throws IOException {
        Pattern pattern = Pattern.compile(regex);
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (pattern.matcher(line).find()) {
                        hits.add(file.getFileName() + ": " + line);
                    }
                }
            }
        }
        return hits;
    }

    // Ten thanh phan cua record va moi record long trong kieu thanh phan (ke ca phan tu List<T>).
    private static void collectComponentNames(Class<?> type, List<String> names, Set<Class<?>> seen) {
        if (!type.isRecord() || !seen.add(type)) {
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            names.add(component.getName());
            collectComponentNames(component.getType(), names, seen);
            Type generic = component.getGenericType();
            if (generic instanceof ParameterizedType parameterized) {
                for (Type argument : parameterized.getActualTypeArguments()) {
                    if (argument instanceof Class<?> argumentClass) {
                        collectComponentNames(argumentClass, names, seen);
                    }
                }
            }
        }
    }

    private static List<String> signature(Class<?> record) {
        return Stream.of(record.getRecordComponents())
                .map(c -> c.getName() + ":" + c.getType().getName())
                .toList();
    }
}
