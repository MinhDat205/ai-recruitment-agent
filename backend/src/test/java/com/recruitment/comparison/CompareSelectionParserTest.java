package com.recruitment.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.common.exception.InvalidCompareSelectionException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// FR-H14 T29 - CompareSelectionParser (don vi, khong Spring). Thu tu kiem R-S7: dinh dang -> trung -> so luong.
class CompareSelectionParserTest {

    private static final String MALFORMED = "Danh sách đơn cần so sánh không hợp lệ.";
    private static final String DUPLICATE = "Mỗi đơn chỉ được chọn một lần.";
    private static final String WRONG_COUNT = "Chọn từ 2 đến 3 đơn để so sánh.";

    private static final UUID A = UUID.fromString("3ec35ae2-c8ae-4100-a819-3b59f6948707");
    private static final UUID B = UUID.fromString("967d5c84-1519-4a70-8bed-6252781ac052");
    private static final UUID C = UUID.fromString("a611e169-fa6c-4b6f-a1fd-da36503ae0ac");
    private static final UUID D = UUID.fromString("1029b885-ddae-4417-8e0c-8284d6198f79");

    private static void assertRejected(String input, String message) {
        assertThatThrownBy(() -> CompareSelectionParser.parse(input))
                .isInstanceOf(InvalidCompareSelectionException.class)
                .hasMessage(message);
    }

    @Test
    void parse_nullEmptyOrBlank_isMalformed() {
        assertRejected(null, MALFORMED);
        assertRejected("", MALFORMED);
        assertRejected(" ", MALFORMED);
    }

    @Test
    void parse_notUuidOrEmptyElement_isMalformed() {
        assertRejected("a", MALFORMED);
        assertRejected("abc," + A, MALFORMED);
        assertRejected(A + ",," + B, MALFORMED);
        assertRejected(A + "," + B + ",", MALFORMED);   // dau phay cuoi
        assertRejected("," + A + "," + B, MALFORMED);   // dau phay dau
        assertRejected(A + ", ," + B, MALFORMED);       // phan tu chi co khoang trang
    }

    // Bien so luong: 1 (nguong-1) -> loi; 2, 3 -> hop le; 4 (nguong+1) -> loi.
    @Test
    void parse_countBoundaries() {
        assertRejected(A.toString(), WRONG_COUNT);
        assertThat(CompareSelectionParser.parse(A + "," + B)).containsExactly(A, B);
        assertThat(CompareSelectionParser.parse(A + "," + B + "," + C)).containsExactly(A, B, C);
        assertRejected(A + "," + B + "," + C + "," + D, WRONG_COUNT);
    }

    @Test
    void parse_keepsOrderOfIds() {
        assertThat(CompareSelectionParser.parse(C + "," + A + "," + B)).containsExactly(C, A, B);
    }

    @Test
    void parse_whitespaceAroundElements_isAccepted() {
        assertThat(CompareSelectionParser.parse(A + ", " + B)).containsExactly(A, B);
        assertThat(CompareSelectionParser.parse("  " + A + " ,\t" + B + "  ")).containsExactly(A, B);
    }

    @Test
    void parse_upperOrMixedCaseUuid_isAcceptedAndNormalized() {
        String upper = A.toString().toUpperCase(Locale.ROOT);
        List<UUID> ids = CompareSelectionParser.parse(upper + "," + B);
        assertThat(ids).containsExactly(A, B);
        assertThat(ids.getFirst().toString()).isEqualTo(A.toString());   // chu thuong khi ra JSON
    }

    // Trung so SAU khi parse: cung UUID khac hoa/thuong van la trung; trung kiem TRUOC so luong.
    @Test
    void parse_duplicates_reportedBeforeCount() {
        assertRejected(A + "," + A, DUPLICATE);
        assertRejected(A + "," + B + "," + A, DUPLICATE);
        assertRejected(A + "," + A.toString().toUpperCase(Locale.ROOT), DUPLICATE);
        assertRejected(A + "," + A + "," + B + "," + C, DUPLICATE);   // vua trung vua 4 phan tu -> cau trung
    }

    // Dinh dang kiem TRUOC trung va so luong.
    @Test
    void parse_malformedReportedBeforeDuplicateAndCount() {
        assertRejected(A + "," + A + ",xyz", MALFORMED);
        assertRejected(A + "," + B + "," + C + "," + D + ",xyz", MALFORMED);
    }
}
