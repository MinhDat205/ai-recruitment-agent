package com.recruitment.aicontext;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.messaging.MessageSenderRole;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

// FR-C07 T10 (lop 1 - chu ky, phan K1 cua dot 3): ngu canh gui AI KHONG THE chua diem/rubric/giai thich/ghi chu vi
// KIEU du lieu khong co cho de chua chung (R-K1-2), va bo gom ngu canh khong phu thuoc package chua cac du lieu do
// (R-K1-4). Phan MessageDraftService/MessageDraftFacade cua T10 lam o dot 4.
class ConversationContextSignatureTest {

    private static final Set<Class<?>> ALLOWED_LEAF_TYPES = Set.of(
            String.class,
            Instant.class,
            boolean.class,
            ApplicationStatus.class,
            MessageSenderRole.class,
            ContextViewer.class);

    private static final Pattern FORBIDDEN_NAME = Pattern.compile(
            "(?i).*(score|rubric|criterion|weight|explanation|evaluation|note|rank|question|email|phone).*");

    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            "com.recruitment.scoring",
            "com.recruitment.rubric",
            "com.recruitment.resume",
            "com.recruitment.ai",
            "com.recruitment.jobrecommendation");

    @Test
    void conversationContext_hasExactlyTheSpecifiedComponents() {
        assertThat(componentNames(ConversationContext.class))
                .containsExactly(
                        "viewer",
                        "candidateName",
                        "jobTitle",
                        "companyName",
                        "applicationStatus",
                        "interview",
                        "recentMessages");
        assertThat(componentNames(ConversationContext.InterviewSchedule.class))
                .containsExactly("scheduledAt", "location");
        assertThat(componentNames(ConversationContext.RecentMessage.class))
                .containsExactly("senderRole", "text", "hasAttachment");
    }

    @Test
    void conversationContext_recursively_onlyAllowedTypes_andNoForbiddenNames() {
        List<String> violations = new ArrayList<>();
        checkRecord(ConversationContext.class, violations);
        assertThat(violations).isEmpty();
    }

    @Test
    void assembler_constructorAndFields_doNotDependOnForbiddenPackages() {
        List<String> violations = new ArrayList<>();
        for (Constructor<?> constructor : ConversationContextAssembler.class.getDeclaredConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                checkPackage("tham so constructor " + parameter.getName(), parameter, violations);
            }
        }
        for (Field field : ConversationContextAssembler.class.getDeclaredFields()) {
            checkPackage("field " + field.getName(), field.getType(), violations);
        }
        assertThat(violations).isEmpty();
    }

    // R-K1-3 - transaction chi doc (ngan, dong truoc khi goi AI).
    @Test
    void assembler_forConversation_isReadOnlyTransactional() throws Exception {
        Method method = ConversationContextAssembler.class.getMethod(
                "forConversation", ContextViewer.class, java.util.UUID.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private static List<String> componentNames(Class<?> recordType) {
        List<String> names = new ArrayList<>();
        for (RecordComponent component : recordType.getRecordComponents()) {
            names.add(component.getName());
        }
        return names;
    }

    private static void checkRecord(Class<?> recordType, List<String> violations) {
        for (RecordComponent component : recordType.getRecordComponents()) {
            String where = recordType.getSimpleName() + "." + component.getName();
            if (FORBIDDEN_NAME.matcher(component.getName()).matches()) {
                violations.add(where + ": ten bi cam");
            }
            checkType(where, component.getType(), component.getGenericType(), violations);
        }
    }

    private static void checkType(String where, Class<?> type, Type genericType, List<String> violations) {
        if (ALLOWED_LEAF_TYPES.contains(type)) {
            return;
        }
        if (type == List.class) {
            if (!(genericType instanceof ParameterizedType parameterized)
                    || !(parameterized.getActualTypeArguments()[0] instanceof Class<?> element)) {
                violations.add(where + ": List khong ro kieu phan tu");
                return;
            }
            checkType(where + "[]", element, element, violations);
            return;
        }
        if (type.isRecord() && type.getPackageName().equals(ConversationContext.class.getPackageName())) {
            checkRecord(type, violations);
            return;
        }
        violations.add(where + ": kieu khong duoc phep " + type.getName());
    }

    private static void checkPackage(String where, Class<?> type, List<String> violations) {
        for (String forbidden : FORBIDDEN_PACKAGES) {
            if (type.getPackageName().equals(forbidden) || type.getPackageName().startsWith(forbidden + ".")) {
                violations.add(where + ": thuoc " + forbidden);
            }
        }
    }
}
