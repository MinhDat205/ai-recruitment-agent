package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.resume.ResumeParsedPayload;
import java.io.IOException;
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

// FR-U12 T24 phan resume/ (R-S2: resume/ khong import cvbuilder/), T25 (R-F2: khong truong nhan than; Contact trung
// khop ResumeParsedPayload.Contact). Test don vi, khong Spring. Phan ai/cvrephrase cua T24 them o dot 5.
class CvBuilderSignatureTest {

    private static final Path RESUME_SOURCES = Path.of("src/main/java/com/recruitment/resume");

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
        Pattern pattern = Pattern.compile(regex);
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(RESUME_SOURCES)) {
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
