package com.recruitment.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

// Bang phu, doc lap hoan toan voi score_explanations (V5, xem comment day du trong migration) - chi
// theo doi so lan ScoreExplanationOrchestrator (Dot 3) thu tong hop bao cao THAT BAI cho MOT luot
// cham, khong bao gio chua noi dung bao cao that. attemptCount/lastError/lastAttemptedAt la field
// app TU GHI (khong @Generated) - duong ghi thuc su o Dot 3 la upsert nguyen tu qua
// ScoreExplanationAttemptRepository.recordFailedAttempt (xem repository), khong qua entity.save()
// truc tiep; entity nay chu yeu phuc vu doc (findByScoringRunId) va fixture test.
@Entity
@Table(name = "score_explanation_attempts")
@Getter
@Setter
@NoArgsConstructor
public class ScoreExplanationAttempt {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "scoring_run_id", nullable = false, unique = true)
    private UUID scoringRunId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    // formatted() cua ma loi chuan hoa (FormattedErrorCode) - cung quy uoc voi scoring_runs.error_message,
    // khong phai output tho cua LLM hay stack trace.
    @Column(name = "last_error", nullable = false)
    private String lastError;

    @Column(name = "last_attempted_at", nullable = false)
    private Instant lastAttemptedAt;
}
