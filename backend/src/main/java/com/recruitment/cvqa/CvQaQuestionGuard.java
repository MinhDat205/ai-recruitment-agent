package com.recruitment.cvqa;

import com.recruitment.ai.cvqa.CvQaRefusalReason;
import com.recruitment.aicontext.ContextViewer;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

// FR-C08 R-G - chan som bang tu khoa (lop 1 cua Q10), chay TRUOC K1/K3: khop -> REFUSED, 0 lan goi ChatModel, khong doc
// raw_text. Lop 2 la prompt cv-qa-v1.st. Gioi han da biet (R-G4): danh sach KHONG day du (khong bien the khong dau,
// tieng Anh, cach noi vong) va co the chan nham cau hop le chua cum tu (vd "tuoi nghe").
//
// Danh sach khai MOT CHO (R-G3) - sua danh sach = sua dac ta. Cum tu viet thuong, co dau.
@Component
public class CvQaQuestionGuard {

    // R-G2 - gop khoang trang bang Pattern bien dich san (khuon CatalogTextNormalizer), KHONG goi K2, KHONG
    // String.replaceAll (L10). UNICODE_CHARACTER_CLASS de \s khop ca khoang trang Unicode.
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    // R-G3 - CHI phia HR (S3, R-G5: ung vien duoc hoi CV cua minh co ghi thong tin nhan than nao).
    static final List<String> PERSONAL_INFO_KEYWORDS = List.of(
            "tuổi", "năm sinh", "ngày sinh", "sinh năm", "giới tính", "nam hay nữ", "hôn nhân", "kết hôn",
            "lập gia đình", "độc thân", "ly hôn", "có con", "con nhỏ", "mang thai", "có thai", "có bầu", "tôn giáo",
            "theo đạo", "dân tộc", "quê quán", "quê ở", "chiều cao", "cân nặng", "ngoại hình", "sức khỏe", "sức khoẻ",
            "bệnh lý", "tiền sử bệnh", "khuyết tật", "đảng viên", "xu hướng tính dục");

    // R-G3 - ca hai phia. Da bo "có đạt" (S2), giu "đạt yêu cầu".
    static final List<String> HIRING_JUDGEMENT_KEYWORDS = List.of(
            "có nên tuyển", "nên tuyển", "có nên mời", "nên loại", "loại ứng viên", "đạt yêu cầu", "có phù hợp",
            "phù hợp không", "có đủ năng lực", "chấm điểm", "cho điểm", "xếp hạng", "so với ứng viên khác",
            "tốt hơn ứng viên");

    // R-G3 - CHI phia ung vien (Q9: chi sang U05).
    static final List<String> CV_IMPROVEMENT_KEYWORDS = List.of(
            "nên bổ sung", "nên thêm gì", "nên học", "cần học", "cải thiện cv", "nên sửa cv", "thiếu kỹ năng gì");

    // question: da qua R-H1 (doi CRLF, trim, khac rong). Thu tu xet R-G3: PERSONAL_INFO (chi HR) -> HIRING_JUDGEMENT ->
    // CV_IMPROVEMENT (chi ung vien); khop dau tien quyet dinh ly do.
    public Optional<CvQaRefusalReason> check(String question, ContextViewer viewer) {
        String normalized = normalize(question);
        if (viewer == ContextViewer.HR && containsAny(normalized, PERSONAL_INFO_KEYWORDS)) {
            return Optional.of(CvQaRefusalReason.PERSONAL_INFO);
        }
        if (containsAny(normalized, HIRING_JUDGEMENT_KEYWORDS)) {
            return Optional.of(CvQaRefusalReason.HIRING_JUDGEMENT);
        }
        if (viewer == ContextViewer.CANDIDATE && containsAny(normalized, CV_IMPROVEMENT_KEYWORDS)) {
            return Optional.of(CvQaRefusalReason.CV_IMPROVEMENT);
        }
        return Optional.empty();
    }

    // R-G2 - NFC, chu thuong theo Locale.ROOT, gop khoang trang. KHONG bo dau: "tuoi" khong khop "tuổi".
    static String normalize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return WHITESPACE.matcher(normalized).replaceAll(" ").strip();
    }

    private static boolean containsAny(String normalizedQuestion, List<String> keywords) {
        for (String keyword : keywords) {
            if (containsPhrase(normalizedQuestion, normalize(keyword))) {
                return true;
            }
        }
        return false;
    }

    // R-G2 - cum khop khi bien hai ben la dau/cuoi chuoi hoac ky tu khong phai chu/so (Character.isLetterOrDigit).
    // Xet MOI vi tri xuat hien: lan dau nam giua tu ("abctuổi") khong chan lan sau dung bien.
    static boolean containsPhrase(String haystack, String phrase) {
        int from = 0;
        while (true) {
            int start = haystack.indexOf(phrase, from);
            if (start < 0) {
                return false;
            }
            int end = start + phrase.length();
            boolean leftBoundary = start == 0 || !Character.isLetterOrDigit(haystack.codePointBefore(start));
            boolean rightBoundary = end == haystack.length() || !Character.isLetterOrDigit(haystack.codePointAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            from = start + 1;
        }
    }
}
