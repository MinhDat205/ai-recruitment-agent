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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

// FR-H10 - mot cau hoi sang loc cua Job. Xoa mem bang deleted_at (R-Q8). Cau da xoa van con hang de ban chup cu
// tro toi qua question_id (R-S3) - ban chup KHONG doc entity nay de hien thi.
@Entity
@Table(name = "job_screening_questions")
@Getter
@Setter
@NoArgsConstructor
public class ScreeningQuestion {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private ScreeningQuestionType questionType;

    @Column(name = "question_text", nullable = false)
    private String questionText;

    // NULL voi SHORT_TEXT/YES_NO, 2-10 phan tu voi SINGLE_CHOICE (chk_screening_question_options).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options")
    private List<String> options;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
