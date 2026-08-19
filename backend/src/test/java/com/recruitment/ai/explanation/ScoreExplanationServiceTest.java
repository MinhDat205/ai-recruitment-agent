package com.recruitment.ai.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.scoring.CriterionScoreExplanationInput;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.junit.jupiter.api.Test;

// Test thuan Java, khong can Spring context - mau CriterionScoringServiceTest. Chi kiem "ranh gioi"
// qua chu ky method/constructor that (khong doan/mo ta suong): input CHI la List<CriterionScoreExplanationInput>
// (record thuan cua package scoring/, khong co evidence/rawText - xem Dot 1), khong co tham so/kieu
// tra ve nao thuoc package resume/ hay ten ScoreAggregator o bat ky cho nao lo ra trong chu ky cong
// khai cua class nay.
class ScoreExplanationServiceTest {

    @Test
    void explain_signatureTakesListOfCriterionScoreExplanationInput_elementTypeHasNoEvidenceOrRawText()
            throws NoSuchMethodException {
        Method explainMethod = ScoreExplanationService.class.getMethod("explain", java.util.List.class);

        Type genericParamType = explainMethod.getGenericParameterTypes()[0];
        assertThat(genericParamType).isInstanceOf(ParameterizedType.class);
        Type[] typeArgs = ((ParameterizedType) genericParamType).getActualTypeArguments();
        assertThat(typeArgs).hasSize(1);
        assertThat(typeArgs[0]).isEqualTo(CriterionScoreExplanationInput.class);
    }

    @Test
    void explain_returnTypeAndConstructorParams_doNotReferenceResumePackageOrScoreAggregator()
            throws NoSuchMethodException {
        Method explainMethod = ScoreExplanationService.class.getMethod("explain", java.util.List.class);
        Class<?> returnType = explainMethod.getReturnType();
        assertThat(returnType.getPackageName()).isEqualTo("com.recruitment.ai.explanation");
        assertThat(returnType.getSimpleName()).doesNotContain("ScoreAggregator");

        Constructor<?>[] constructors = ScoreExplanationService.class.getConstructors();
        assertThat(constructors).hasSize(1);
        for (Class<?> paramType : constructors[0].getParameterTypes()) {
            assertThat(paramType.getPackageName()).doesNotStartWith("com.recruitment.resume");
            assertThat(paramType.getSimpleName()).doesNotContain("ScoreAggregator");
        }
    }

    // CriterionScoreExplanationInput (Dot 1) da khong co field evidence - test nay xac nhan lai
    // dung tai diem D4 su dung no, chong hoi quy neu sau nay co ai lo them field evidence vao record
    // do de "tien" cho D4.
    @Test
    void criterionScoreExplanationInput_hasNoEvidenceField() {
        var fields = CriterionScoreExplanationInput.class.getRecordComponents();
        for (var field : fields) {
            assertThat(field.getName()).doesNotContainIgnoringCase("evidence");
        }
    }
}
