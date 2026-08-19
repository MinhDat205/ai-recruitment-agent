package com.recruitment.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

// scoring_run_id/criterion_id la UUID thuong, khong dung @ManyToOne - cung ly do voi ScoringRun.
//
// criterion_id: FK toi rubric_criteria (ON DELETE SET NULL) nhung KHONG the INSERT tham chieu toi
// mot id da khong con ton tai (khac voi viec DB tu null hoa khi xoa SAU KHI da tham chieu) - noi
// ghi (Dot 4) phai kiem existsById() truoc khi set FK, null neu tieu chi goc da bi xoa giua luc
// snapshot va luc cham. Cac field snapshot ben duoi luon day du bat ke FK con song hay khong.
@Entity
@Table(name = "criterion_scores")
@Getter
@Setter
@NoArgsConstructor
public class CriterionScore {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "scoring_run_id", nullable = false)
    private UUID scoringRunId;

    @Column(name = "criterion_id")
    private UUID criterionId;

    @Column(name = "criterion_name_snapshot", nullable = false)
    private String criterionNameSnapshot;

    @Column(name = "weight_snapshot", nullable = false)
    private BigDecimal weightSnapshot;

    // int nguyen thuy (khong wrapper): cot NOT NULL va luon co gia tri lay tu snapshot ngay luc
    // insert, giong tien le RubricCriterion.maxScore/displayOrder - khac voi cac record JSON dung
    // cho output LLM (o do wrapper can thiet de phan biet "khong tim thay" voi "bang 0").
    @Column(name = "max_score_snapshot", nullable = false)
    private int maxScoreSnapshot;

    @Column(nullable = false)
    private BigDecimal score;

    @Column(nullable = false)
    private String reasoning;

    // JSONB NOT NULL DEFAULT '[]'::jsonb o DB, nhung Hibernate luon INSERT gia tri tuong minh (ke
    // ca list rong) nen DEFAULT khong bao gio duoc dung toi - giong ghi chu maxScore/displayOrder
    // trong RubricOwnerService.applyRequest. Noi ghi (Dot 4) phai tu dam bao khong bao gio truyen
    // null vao day.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<EvidenceEntry> evidence;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
