package com.recruitment.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

// application_id la UUID thuong, khong dung @ManyToOne - tranh lazy-loading ngoai transaction,
// giong ly do trong JobApplication.java/ResumeParsedData.java.
//
// KHONG map cot total_score: day la viec cua D3 (FR-H05), khong phai D2 (FR-H04). Giong het ly do
// ResumeParsedData khong map cot embedding (viec cua F1) - ddl-auto: validate khong doi hoi entity
// map het moi cot cua bang, chi can cot da map khop dung kieu. Khong map o day la rao chan cau
// truc: khong co field nao de code D2 lo tay ghi total_score, thay vi chi dua vao ky luat "dung
// ghi". D3 se tu them field nay vao entity khi can.
@Entity
@Table(name = "scoring_runs")
@Getter
@Setter
@NoArgsConstructor
public class ScoringRun {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScoringRunStatus status;

    // Chup toan bo rubric (ten, tieu chi, trong so, thang diem) tai thoi diem tao luot cham - xem
    // RubricSnapshotMapper. Cot DB khong NOT NULL nhung app luon dien day du luc tao luot cham
    // (Q6/Dot 2 cua ke hoach D2).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rubric_snapshot")
    private RubricSnapshot rubricSnapshot;

    private String model;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "token_usage")
    private Integer tokenUsage;

    @Column(name = "error_message")
    private String errorMessage;

    // Khong @Generated - code phai tu ghi ca hai cot nay. claim() (Dot 4) ghi startedAt luc
    // PENDING->RUNNING. finishedAt la moc "D2 da cham xong toan bo tieu chi" theo Q1 cua ke hoach
    // D2 - dung cho CA case thanh cong (status van RUNNING, cho D3) LAN case FAILED, khong chi
    // danh rieng cho FAILED.
    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
