package com.recruitment.jobrecommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

// candidate_id/job_id/resume_id la UUID thuong, khong dung @ManyToOne - tranh lazy-loading ngoai
// transaction (open-in-view: false), cung ly do voi cac entity khac trong du an. Khac
// JobEmbedding/ResumeParsedData: similarity_score la NUMERIC thuong (khong phai vector), map binh
// thuong bang BigDecimal, khong can native query rieng de doc/ghi cot nay.
@Entity
@Table(name = "job_recommendations")
@Getter
@Setter
@NoArgsConstructor
public class JobRecommendation {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "similarity_score", nullable = false)
    private BigDecimal similarityScore;

    @Generated(event = EventType.INSERT)
    @Column(name = "generated_at", insertable = false, updatable = false)
    private Instant generatedAt;
}
