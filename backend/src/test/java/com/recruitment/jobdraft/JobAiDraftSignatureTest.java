package com.recruitment.jobdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.ai.jobdraft.CriteriaDraftPayload;
import com.recruitment.ai.jobdraft.IndustryOption;
import com.recruitment.ai.jobdraft.JobAiDraftResult;
import com.recruitment.ai.jobdraft.JobAiDraftService;
import com.recruitment.ai.jobdraft.JobDraftPayload;
import com.recruitment.ai.jobdraft.QuestionsDraftPayload;
import com.recruitment.jobdraft.dto.JobAiDraftRequest;
import com.recruitment.jobdraft.dto.JobAiDraftResponse;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import org.springframework.transaction.annotation.Transactional;

// FR-H11 T6 (a) - khong trong so/diem/nhan o lop kieu du lieu: moi record cua ai/jobdraft va DTO cua J1 (de quy qua
// thanh phan va tham so generic) khong co thanh phan khop regex cam (tru JobDraft.missingFields - do backend tinh).
// T21 - ranh gioi phu thuoc: JobAiDraftService/JobAiDraftFacade khong phu thuoc scoring/resume/jobapplication;
// ai/jobdraft khong import jobdraft/, catalog/; ca hai lop khong @Transactional (R-K3H-3).
class JobAiDraftSignatureTest {

    private static final Pattern FORBIDDEN_COMPONENT = Pattern.compile(
            "(?i)weight|score|rank|priority|verdict|label|passed|recommendation|qualified|missing");
    private static final String ALLOWED = "com.recruitment.jobdraft.dto.JobDraft.missingFields";

    private static final List<Class<?>> ROOTS = List.of(
            // ai/jobdraft
            JobDraftPayload.class, CriteriaDraftPayload.class, QuestionsDraftPayload.class, IndustryOption.class,
            JobAiDraftResult.class,
            // jobdraft/dto cua J1
            JobAiDraftRequest.class, JobAiDraftResponse.class);

    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            "com.recruitment.scoring", "com.recruitment.resume", "com.recruitment.jobapplication");

    @Test
    void recordComponents_haveNoWeightScoreOrLabelNames() {
        Set<Class<?>> visited = new HashSet<>();
        List<String> violations = new ArrayList<>();
        for (Class<?> root : ROOTS) {
            visit(root, visited, violations);
        }
        // Dam bao de quy da di toi cac record long ben trong (PartResult, JobDraft, CriterionSuggestion...).
        assertThat(visited.stream().map(Class::getSimpleName))
                .contains("PartResult", "JobDraft", "MissingField", "CriteriaDraft", "CriterionSuggestion",
                        "QuestionsDraft", "QuestionSuggestion", "CriterionDraft", "QuestionDraft");
        assertThat(violations).isEmpty();
    }

    private static void visit(Class<?> type, Set<Class<?>> visited, List<String> violations) {
        if (!type.isRecord() || !visited.add(type)) {
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            String qualified = type.getName().replace('$', '.') + "." + component.getName();
            if (FORBIDDEN_COMPONENT.matcher(component.getName()).find() && !ALLOWED.equals(qualified)) {
                violations.add(qualified);
            }
            visitType(component.getGenericType(), visited, violations);
        }
    }

    private static void visitType(Type type, Set<Class<?>> visited, List<String> violations) {
        if (type instanceof Class<?> raw) {
            visit(raw, visited, violations);
        } else if (type instanceof ParameterizedType parameterized) {
            visitType(parameterized.getRawType(), visited, violations);
            for (Type argument : parameterized.getActualTypeArguments()) {
                visitType(argument, visited, violations);
            }
        }
    }

    // T21 - tham so constructor va field.
    @Test
    void serviceAndFacade_doNotDependOnScoringResumeJobApplication() {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : List.of(JobAiDraftService.class, JobAiDraftFacade.class)) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    checkPackage(type.getSimpleName() + " tham so " + parameter.getName(), parameter, violations);
                }
            }
            for (Field field : type.getDeclaredFields()) {
                checkPackage(type.getSimpleName() + " field " + field.getName(), field.getType(), violations);
            }
        }
        assertThat(violations).isEmpty();
    }

    // T21 - kiem tinh import tren ma nguon (thu muc lam viec cua surefire = backend/).
    @Test
    void aiJobdraftPackage_doesNotImportJobdraftOrCatalog() throws IOException {
        Pattern forbidden = Pattern.compile("^import com\\.recruitment\\.(jobdraft|catalog|scoring|resume|jobapplication)\\.");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/recruitment/ai/jobdraft"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (forbidden.matcher(line).find()) {
                        violations.add(file.getFileName() + ": " + line);
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    // R-K3H-3 - facade va service khong @Transactional (ca lop lan method).
    @Test
    void serviceAndFacade_areNotTransactional() {
        for (Class<?> type : List.of(JobAiDraftService.class, JobAiDraftFacade.class)) {
            assertThat(type.isAnnotationPresent(Transactional.class)).as(type.getSimpleName()).isFalse();
            assertThat(type.isAnnotationPresent(jakarta.transaction.Transactional.class)).as(type.getSimpleName())
                    .isFalse();
            for (Method method : type.getDeclaredMethods()) {
                assertThat(method.isAnnotationPresent(Transactional.class))
                        .as(type.getSimpleName() + "." + method.getName()).isFalse();
                assertThat(method.isAnnotationPresent(jakarta.transaction.Transactional.class))
                        .as(type.getSimpleName() + "." + method.getName()).isFalse();
            }
        }
    }

    private static void checkPackage(String where, Class<?> type, List<String> violations) {
        for (String forbidden : FORBIDDEN_PACKAGES) {
            if (type.getPackageName().equals(forbidden) || type.getPackageName().startsWith(forbidden + ".")) {
                violations.add(where + ": thuoc " + forbidden);
            }
        }
    }
}
