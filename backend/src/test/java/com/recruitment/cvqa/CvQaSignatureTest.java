package com.recruitment.cvqa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.ai.cvqa.CvQaService;
import com.recruitment.cvqa.dto.CvQaRequest;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// FR-C08 T17 - khong transaction quanh loi goi AI (R-A1, CLAUDE.md muc 3c): CvQaFacade, CvQaService, CvQaHrController
// khong @Transactional (ca hai loai annotation, cap lop va cap method); goi facade ben trong TransactionTemplate ->
// IllegalStateException cua K3, 0 lan goi ChatModel. Kem R-K1-4: tham so constructor/field khong thuoc scoring/, rubric/.
// Phia ung vien (CvQaCandidateController) them o dot 4.
class CvQaSignatureTest extends CvQaIntegrationTestSupport {

    private static final List<Class<?>> CHECKED = List.of(CvQaFacade.class, CvQaService.class, CvQaHrController.class);

    private static final List<String> FORBIDDEN_PACKAGES = List.of("com.recruitment.scoring", "com.recruitment.rubric");

    @Autowired
    CvQaFacade facade;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void facadeServiceAndHrController_areNotTransactional() {
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

    @Test
    void facadeServiceAndHrController_doNotDependOnScoringOrRubric() {
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

    // Du lieu hop le, cau hoi khong bi chan -> qua quyen, R-H1, R-G, K1 (transaction chi doc tham gia transaction ngoai)
    // roi toi K3, K3 tu choi vi dang co transaction.
    @Test
    void facadeInsideTransaction_throwsIllegalStateFromK3_noModelCall() throws Exception {
        Fixture f = createApplicationWithText("c08-t17-tx", RAW_TEXT);
        stubDefaultAnswer();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                        facade.askAsHr(f.hrUserId(), f.applicationId(), new CvQaRequest(DEFAULT_QUESTION))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R-K3-4");
        assertNoModelCall();
    }

    private static void checkPackage(String where, Class<?> type, List<String> violations) {
        for (String forbidden : FORBIDDEN_PACKAGES) {
            if (type.getPackageName().equals(forbidden) || type.getPackageName().startsWith(forbidden + ".")) {
                violations.add(where + ": thuoc " + forbidden);
            }
        }
    }
}
