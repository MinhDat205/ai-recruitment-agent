package com.recruitment.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

// FR-H10 - mot dong ban chup cau hoi + cau tra loi cua mot don (R-S1). BAT BIEN (R-S2): @Immutable, khong setter,
// khong updated_at. FR-H10 CHI DOC (R-B2) - duong ghi thuoc FR-U09. Hien thi chi dung cot *_snapshot, KHONG doc lai
// cau hoi goc qua question_id (R-S3).
//
// FR-U09 R-S3 - duong ghi DUY NHAT la factory capture(...) (goi tu ScreeningCaptureService.persist). Hibernate 7.4.1 chi
// kiem tinh bat bien o buoc flush/UPDATE (DefaultFlushEntityEventListener), khong o persist/INSERT (L1 cua FR-U09).
@Entity
@Immutable
@Table(name = "application_screening_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationScreeningAnswer {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    // Chi de truy vet, co the tro toi cau da xoa mem hoac NULL (R-S3).
    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type_snapshot", nullable = false)
    private ScreeningQuestionType questionTypeSnapshot;

    @Column(name = "question_text_snapshot", nullable = false)
    private String questionTextSnapshot;

    // NULL voi SHORT_TEXT/YES_NO (chk_screening_answer_options).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_snapshot")
    private List<String> optionsSnapshot;

    @Column(name = "required_snapshot", nullable = false)
    private boolean requiredSnapshot;

    // NULL = khong tra loi, hoac cau YES_NO (chk_screening_answer_shape).
    @Column(name = "answer_text")
    private String answerText;

    // NULL = khong tra loi, hoac cau khong phai YES_NO.
    @Column(name = "answer_yes_no")
    private Boolean answerYesNo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // FR-U09 R-S1 - chep TOAN BO cau hoi dang hoat dong luc nop vao cot *_snapshot, cung cau tra loi da chuan hoa
    // (R-V4, R-V5). answerText/answerYesNo null = khong tra loi.
    static ApplicationScreeningAnswer capture(
            UUID applicationId, ScreeningQuestion question, String answerText, Boolean answerYesNo) {
        ApplicationScreeningAnswer answer = new ApplicationScreeningAnswer();
        answer.applicationId = applicationId;
        answer.questionId = question.getId();
        answer.displayOrder = question.getDisplayOrder();
        answer.questionTypeSnapshot = question.getQuestionType();
        answer.questionTextSnapshot = question.getQuestionText();
        answer.optionsSnapshot = question.getOptions() == null ? null : List.copyOf(question.getOptions());
        answer.requiredSnapshot = question.isRequired();
        answer.answerText = answerText;
        answer.answerYesNo = answerYesNo;
        return answer;
    }
}
