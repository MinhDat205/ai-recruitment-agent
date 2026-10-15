package com.recruitment.aicontext;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

// FR-C08 T12a (khuon ConversationContextSignatureTest): ngu canh hoi dap CV KHONG THE chua diem/rubric/giai thich/ghi
// chu... vi KIEU du lieu khong co cho de chua chung (R-K1-1), va bo gom khong phu thuoc package chua du lieu do (R-K1-3).
class CvQaContextSignatureTest {

    private static final Pattern FORBIDDEN_NAME = Pattern.compile(
            "(?i).*(score|rubric|criterion|weight|explanation|evaluation|note|rank|email|phone|history|message"
                    + "|invitation|screening).*");

    // R-K1-3 - danh sach cam day du.
    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            "com.recruitment.scoring",
            "com.recruitment.rubric",
            "com.recruitment.ai",
            "com.recruitment.messaging",
            "com.recruitment.interviewinvitation",
            "com.recruitment.screening",
            "com.recruitment.notification",
            "com.recruitment.user",
            "com.recruitment.company");

    private record Component(String name, Class<?> type) {
    }

    private static List<Component> components(Class<?> recordType) {
        List<Component> components = new ArrayList<>();
        for (RecordComponent component : recordType.getRecordComponents()) {
            components.add(new Component(component.getName(), component.getType()));
        }
        return components;
    }

    @Test
    void cvQaContext_hasExactlyTheSpecifiedComponents_nameTypeAndOrder() {
        assertThat(components(CvQaContext.class))
                .containsExactly(
                        new Component("viewer", ContextViewer.class),
                        new Component("resumeText", String.class),
                        new Component("job", CvQaContext.JobPosting.class));
        assertThat(components(CvQaContext.JobPosting.class))
                .containsExactly(
                        new Component("title", String.class),
                        new Component("description", String.class),
                        new Component("requirements", String.class));
    }

    @Test
    void cvQaContext_noForbiddenComponentNames() {
        List<String> violations = new ArrayList<>();
        for (Class<?> recordType : List.of(CvQaContext.class, CvQaContext.JobPosting.class)) {
            for (RecordComponent component : recordType.getRecordComponents()) {
                if (FORBIDDEN_NAME.matcher(component.getName()).matches()) {
                    violations.add(recordType.getSimpleName() + "." + component.getName());
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void assembler_constructorAndFields_doNotDependOnForbiddenPackages() {
        List<String> violations = new ArrayList<>();
        for (Constructor<?> constructor : CvQaContextAssembler.class.getDeclaredConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                checkPackage("tham so constructor " + parameter.getName(), parameter, violations);
            }
        }
        for (Field field : CvQaContextAssembler.class.getDeclaredFields()) {
            checkPackage("field " + field.getName(), field.getType(), violations);
        }
        assertThat(violations).isEmpty();
    }

    // R-K1-2 - viewer = HR co dinh: forHrApplication KHONG nhan tham so vai tro.
    @Test
    void forHrApplication_takesOnlyApplicationId_noContextViewerParameter() throws Exception {
        Method method = CvQaContextAssembler.class.getMethod("forHrApplication", UUID.class);
        assertThat(method.getParameterTypes()).containsExactly(UUID.class);
        for (Method declared : CvQaContextAssembler.class.getDeclaredMethods()) {
            if (declared.getName().equals("forHrApplication")) {
                assertThat(declared.getParameterTypes()).doesNotContain(ContextViewer.class);
            }
        }
    }

    // R-K1-2 - transaction chi doc (ngan, dong truoc khi goi AI).
    @Test
    void assembler_publicMethods_areReadOnlyTransactional() throws Exception {
        for (String name : List.of("forHrApplication", "forCandidateResume")) {
            Transactional transactional =
                    CvQaContextAssembler.class.getMethod(name, UUID.class).getAnnotation(Transactional.class);
            assertThat(transactional).as(name).isNotNull();
            assertThat(transactional.readOnly()).as(name).isTrue();
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
