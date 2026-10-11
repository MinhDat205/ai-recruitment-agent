package com.recruitment.messagedraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.ai.messagedraft.MessageDraftService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

// FR-C07 T10 (lop 1 - chu ky, phan MessageDraftService + MessageDraftFacade cua dot 4; phan record + assembler o
// aicontext/ConversationContextSignatureTest). Moi tham so constructor va field cua hai lop KHONG thuoc package chua
// diem/rubric/CV - du lieu do khong co duong nao vao prompt. Kem R-K3-4: hai lop khong @Transactional (loi goi K3 nam
// ngoai transaction).
class MessageDraftSignatureTest {

    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            "com.recruitment.scoring",
            "com.recruitment.rubric",
            "com.recruitment.resume");

    private static final List<Class<?>> CHECKED = List.of(MessageDraftService.class, MessageDraftFacade.class);

    @Test
    void serviceAndFacade_constructorParamsAndFields_doNotDependOnScoringRubricResume() {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : CHECKED) {
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

    @Test
    void serviceAndFacade_areNotTransactional() {
        for (Class<?> type : CHECKED) {
            assertThat(type.isAnnotationPresent(Transactional.class)).as(type.getSimpleName()).isFalse();
            assertThat(type.isAnnotationPresent(jakarta.transaction.Transactional.class))
                    .as(type.getSimpleName())
                    .isFalse();
            for (Method method : type.getDeclaredMethods()) {
                assertThat(method.isAnnotationPresent(Transactional.class))
                        .as(type.getSimpleName() + "." + method.getName())
                        .isFalse();
                assertThat(method.isAnnotationPresent(jakarta.transaction.Transactional.class))
                        .as(type.getSimpleName() + "." + method.getName())
                        .isFalse();
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
