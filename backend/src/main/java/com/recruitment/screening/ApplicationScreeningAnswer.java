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
import org.hibernate.type.SqlTypes;

// FR-H10 - mot dong ban chup cau hoi + cau tra loi cua mot don (R-S1). BAT BIEN (R-S2): @Immutable, khong setter,
// khong updated_at. FR-H10 CHI DOC (R-B2) - duong ghi thuoc FR-U09. Hien thi chi dung cot *_snapshot, KHONG doc lai
// cau hoi goc qua question_id (R-S3).
@Entity
@Immutable
@Table(name = "application_screening_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationScreeningAnswer {

    @Id
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
}
