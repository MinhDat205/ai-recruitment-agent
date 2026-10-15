package com.recruitment.cvqa;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.ai.cvqa.CvQaRefusalReason;
import com.recruitment.aicontext.ContextViewer;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// FR-C08 T8a (unit, khong Spring): bo chan tu khoa R-G2/R-G3/R-G5. Danh sach cum tu chep NGUYEN VAN bang R-G3 cua
// dac ta - khong doc lai hang cua CvQaQuestionGuard de test khong tu khang dinh chinh no.
class CvQaQuestionGuardTest {

    private static final List<String> PERSONAL_INFO = List.of(
            "tuổi", "năm sinh", "ngày sinh", "sinh năm", "giới tính", "nam hay nữ", "hôn nhân", "kết hôn",
            "lập gia đình", "độc thân", "ly hôn", "có con", "con nhỏ", "mang thai", "có thai", "có bầu", "tôn giáo",
            "theo đạo", "dân tộc", "quê quán", "quê ở", "chiều cao", "cân nặng", "ngoại hình", "sức khỏe", "sức khoẻ",
            "bệnh lý", "tiền sử bệnh", "khuyết tật", "đảng viên", "xu hướng tính dục");

    private static final List<String> HIRING_JUDGEMENT = List.of(
            "có nên tuyển", "nên tuyển", "có nên mời", "nên loại", "loại ứng viên", "đạt yêu cầu", "có phù hợp",
            "phù hợp không", "có đủ năng lực", "chấm điểm", "cho điểm", "xếp hạng", "so với ứng viên khác",
            "tốt hơn ứng viên");

    private static final List<String> CV_IMPROVEMENT = List.of(
            "nên bổ sung", "nên thêm gì", "nên học", "cần học", "cải thiện cv", "nên sửa cv", "thiếu kỹ năng gì");

    private final CvQaQuestionGuard guard = new CvQaQuestionGuard();

    private Optional<CvQaRefusalReason> hr(String question) {
        return guard.check(question, ContextViewer.HR);
    }

    private Optional<CvQaRefusalReason> candidate(String question) {
        return guard.check(question, ContextViewer.CANDIDATE);
    }

    static Stream<String> personalInfoKeywords() {
        return PERSONAL_INFO.stream();
    }

    static Stream<String> hiringJudgementKeywords() {
        return HIRING_JUDGEMENT.stream();
    }

    static Stream<String> cvImprovementKeywords() {
        return CV_IMPROVEMENT.stream();
    }

    // R-G3 - danh sach trong code trung bang dac ta, dung thu tu.
    @Test
    void keywordLists_matchSpecTableExactly() {
        assertThat(CvQaQuestionGuard.PERSONAL_INFO_KEYWORDS).containsExactlyElementsOf(PERSONAL_INFO);
        assertThat(CvQaQuestionGuard.HIRING_JUDGEMENT_KEYWORDS).containsExactlyElementsOf(HIRING_JUDGEMENT);
        assertThat(CvQaQuestionGuard.CV_IMPROVEMENT_KEYWORDS).containsExactlyElementsOf(CV_IMPROVEMENT);
    }

    // ---- moi cum, dung phia ap dung -> dung ly do ----

    @ParameterizedTest
    @MethodSource("personalInfoKeywords")
    void personalInfoKeyword_hr_refusedPersonalInfo(String keyword) {
        assertThat(hr("Cho tôi biết " + keyword + " của người này?")).contains(CvQaRefusalReason.PERSONAL_INFO);
    }

    // S3, R-G5 - phia ung vien KHONG chan nhan than.
    @ParameterizedTest
    @MethodSource("personalInfoKeywords")
    void personalInfoKeyword_candidate_notBlocked(String keyword) {
        assertThat(candidate("CV của tôi có ghi " + keyword + " không?")).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("hiringJudgementKeywords")
    void hiringJudgementKeyword_hr_refusedHiringJudgement(String keyword) {
        assertThat(hr("Hỏi: " + keyword + "?")).contains(CvQaRefusalReason.HIRING_JUDGEMENT);
    }

    @ParameterizedTest
    @MethodSource("hiringJudgementKeywords")
    void hiringJudgementKeyword_candidate_refusedHiringJudgement(String keyword) {
        assertThat(candidate("Hỏi: " + keyword + "?")).contains(CvQaRefusalReason.HIRING_JUDGEMENT);
    }

    @ParameterizedTest
    @MethodSource("cvImprovementKeywords")
    void cvImprovementKeyword_candidate_refusedCvImprovement(String keyword) {
        assertThat(candidate("Tôi " + keyword + " để tốt hơn?")).contains(CvQaRefusalReason.CV_IMPROVEMENT);
    }

    // CV_IMPROVEMENT chi ap phia ung vien: phia HR cau "nen hoc gi" di tiep toi AI (T8b).
    @ParameterizedTest
    @MethodSource("cvImprovementKeywords")
    void cvImprovementKeyword_hr_notBlocked(String keyword) {
        assertThat(hr("Tôi " + keyword + " để tốt hơn?")).isEmpty();
    }

    // ---- R-G2: cach khop ----

    @Test
    void uppercase_matchesAfterLowercasing() {
        assertThat(hr("TUỔI CỦA ỨNG VIÊN?")).contains(CvQaRefusalReason.PERSONAL_INFO);
        assertThat(candidate("CẢI THIỆN CV thế nào?")).contains(CvQaRefusalReason.CV_IMPROVEMENT);
    }

    @Test
    void wordBoundary_inSentenceMatches_insideWordDoesNot() {
        assertThat(hr("Ứng viên bao nhiêu tuổi?")).contains(CvQaRefusalReason.PERSONAL_INFO);
        assertThat(hr("abctuổixyz")).isEmpty();
        assertThat(hr("tuổi2")).isEmpty();
        assertThat(hr("(tuổi)")).contains(CvQaRefusalReason.PERSONAL_INFO);
        assertThat(hr("tuổi")).contains(CvQaRefusalReason.PERSONAL_INFO);
    }

    // Lan xuat hien dau nam giua tu khong chan lan sau dung bien.
    @Test
    void wordBoundary_laterOccurrenceWithBoundary_matches() {
        assertThat(hr("abctuổi và tuổi")).contains(CvQaRefusalReason.PERSONAL_INFO);
    }

    @Test
    void withoutDiacritics_doesNotMatch() {
        assertThat(hr("Ung vien bao nhieu tuoi?")).isEmpty();
        assertThat(hr("co nen tuyen khong?")).isEmpty();
    }

    // NFC - cau hoi go o dang to hop (NFD) van khop.
    @Test
    void nfdInput_normalizedToNfc_matches() {
        String nfd = Normalizer.normalize("Ứng viên bao nhiêu tuổi?", Normalizer.Form.NFD);
        assertThat(nfd).isNotEqualTo("Ứng viên bao nhiêu tuổi?");
        assertThat(hr(nfd)).contains(CvQaRefusalReason.PERSONAL_INFO);
    }

    // Gop khoang trang (tab, xuong dong, nhieu dau cach) truoc khi khop cum nhieu tu.
    @Test
    void collapsedWhitespace_multiWordPhraseMatches() {
        assertThat(hr("Năm\t  sinh\ncủa ứng viên?")).contains(CvQaRefusalReason.PERSONAL_INFO);
        assertThat(candidate("Có  nên\ntuyển tôi không?")).contains(CvQaRefusalReason.HIRING_JUDGEMENT);
    }

    @Test
    void ordinaryQuestion_notBlocked_bothSides() {
        assertThat(hr("Ứng viên đã dùng Docker ở đâu?")).isEmpty();
        assertThat(candidate("Ứng viên đã dùng Docker ở đâu?")).isEmpty();
    }

    // S2 - da bo "có đạt": cau hoi ve chung chi khong bi chan.
    @Test
    void certificateQuestionWithCoDat_notBlocked() {
        assertThat(hr("Ứng viên có đạt chứng chỉ IELTS không?")).isEmpty();
        assertThat(candidate("Ứng viên có đạt chứng chỉ IELTS không?")).isEmpty();
    }

    // S3 - cung cum "ngày sinh": ung vien di toi AI, HR bi chan.
    @Test
    void birthDateQuestion_candidateNotBlocked_hrPersonalInfo() {
        assertThat(candidate("CV của tôi có ghi ngày sinh không?")).isEmpty();
        assertThat(hr("CV của tôi có ghi ngày sinh không?")).contains(CvQaRefusalReason.PERSONAL_INFO);
    }

    // Thu tu R-G3: PERSONAL_INFO truoc HIRING_JUDGEMENT.
    @Test
    void hr_personalInfoAndHiringJudgement_personalInfoWins() {
        assertThat(hr("Với tuổi này có nên tuyển không?")).contains(CvQaRefusalReason.PERSONAL_INFO);
    }

    // Thu tu R-G3: HIRING_JUDGEMENT truoc CV_IMPROVEMENT.
    @Test
    void candidate_hiringJudgementAndCvImprovement_hiringJudgementWins() {
        assertThat(candidate("Tôi nên học gì để đạt yêu cầu?")).contains(CvQaRefusalReason.HIRING_JUDGEMENT);
    }
}
