package com.recruitment.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// scoring_run_id la UUID thuong (khong @ManyToOne), cung ly do voi ScoringRun/CriterionScore - tranh
// lazy-loading ngoai transaction. UNIQUE tren cot nay (V1) la CHOT CHAN THAT cho "mot lot cham dung
// mot bao cao" (Dot 3 se dua vao no thay vi tu claim - xem ke hoach D4 muc 4b cua D3, cung tinh
// than: khong claim rieng, de UNIQUE tu chan ghi trung).
//
// Bang nay CHI chua bao cao THAT, ghi DUNG MOT lan khi da xong - khong co cot status/error (khac
// scoring_runs). Moi trang thai "dang thu"/"da thu that bai" song o bang RIENG
// (score_explanation_attempts, V5) - xem ScoreExplanationAttempt va comment trong migration V5.
@Entity
@Table(name = "score_explanations")
@Getter
@Setter
@NoArgsConstructor
public class ScoreExplanation {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "scoring_run_id", nullable = false, unique = true)
    private UUID scoringRunId;

    // LLM sinh (Dot 2) - tom tat toan bo lot cham, KHONG duoc chua trich dan nguyen van tu CV (Q2).
    @Column(nullable = false)
    private String summary;

    // LLM sinh (Dot 2), moi phan tu gan voi DUNG mot criterionName thuoc tap tieu chi da cham (Q3).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ExplanationPoint> strengths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ExplanationPoint> weaknesses;

    // Java thuan tinh o ScoreExplanationOrchestrator (Dot 3): criterionName co score > 0. KHONG do
    // LLM sinh - xem CriterionScoreExplanationInput va Q3 (ke hoach D4).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "met_criteria", nullable = false)
    private List<String> metCriteria;

    // Java thuan tinh o ScoreExplanationOrchestrator (Dot 3): criterionName co score = 0 (dung luat
    // D2 "evidence rong <=> score = 0").
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_criteria", nullable = false)
    private List<String> missingCriteria;

    @Column(nullable = false)
    private String model;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    // DB tu dien gia tri qua DEFAULT now() luc INSERT - app khong bao gio tu set, giong tien le
    // createdAt o ScoringRun/CriterionScore.
    @Generated(event = EventType.INSERT)
    @Column(name = "generated_at", insertable = false, updatable = false)
    private Instant generatedAt;
}
