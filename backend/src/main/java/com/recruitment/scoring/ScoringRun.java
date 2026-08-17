package com.recruitment.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
// total_score: D2 (FR-H04) CO Y KHONG map cot nay - do la rao chan cau truc de khong co field nao
// de code D2 lo tay ghi total_score, thay vi chi dua vao ky luat "dung ghi" (xem lich su git file
// nay). D3 (FR-H05) la nhanh duoc phep them, da them o duoi. Cot do BACKEND tinh bang
// ScoreAggregator (Java thuan, KHONG LLM - xem CLAUDE.md muc 7), ghi DUY NHAT qua
// ScoringRunStateService.finishAggregation() trong CUNG mot UPDATE co dieu kien voi status=DONE -
// khong noi nao khac trong code duoc set truc tiep field nay.
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

    // NUMERIC(6,3), NULL cho toi khi D3 tong hop xong - xem comment tren dau file.
    @Column(name = "total_score")
    private BigDecimal totalScore;

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
