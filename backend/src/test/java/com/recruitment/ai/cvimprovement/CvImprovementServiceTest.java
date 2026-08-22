package com.recruitment.ai.cvimprovement;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

// Test thuan Java, khong can Spring context - mau ScoreExplanationServiceTest. Chi kiem "ranh gioi"
// qua chu ky method/constructor/field that: input CHI la hai String (resumeText, marketTrendText),
// khong co tham so constructor, field, hay kieu tra ve nao thuoc package scoring/ o bat ky cho nao
// lo ra trong chu ky cong khai cua class nay - dung ranh gioi kien truc F2 da chot (Plan Mode, rang
// buoc kien truc #1). Kiem theo PACKAGE (khong phai ten class cu the) de bat duoc CA truong hop ai
// do lo them mot repository/kieu MOI thuoc scoring/ ma khong phai ScoreAggregator.
class CvImprovementServiceTest {

    @Test
    void generate_signatureTakesResumeTextAndMarketTrendText_noReferenceToScoringPackage() throws NoSuchMethodException {
        Method generateMethod = CvImprovementService.class.getMethod("generate", String.class, String.class);

        assertThat(generateMethod.getParameterTypes()).containsExactly(String.class, String.class);
        assertThat(generateMethod.getReturnType().getPackageName()).isEqualTo("com.recruitment.ai.cvimprovement");

        Constructor<?>[] constructors = CvImprovementService.class.getConstructors();
        assertThat(constructors).hasSize(1);
        for (Class<?> paramType : constructors[0].getParameterTypes()) {
            assertThat(paramType.getPackageName()).doesNotStartWith("com.recruitment.scoring");
        }

        // Chan ca truong hop inject repository bang @Autowired field thay vi constructor -
        // constructor "sach" khong du neu field lai am tham giu mot kieu thuoc scoring/.
        for (Field field : CvImprovementService.class.getDeclaredFields()) {
            assertThat(field.getType().getPackageName()).doesNotStartWith("com.recruitment.scoring");
        }
    }
}
