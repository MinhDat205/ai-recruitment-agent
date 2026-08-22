package com.recruitment.resume;

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

// KHONG unique tren resumeId - V1 (idx_cv_suggest_resume(resume_id, generated_at DESC)) co y thiet
// ke bang nay luu NHIEU ban ghi lich su cho cung mot resume (index composite kem generated_at DESC
// chi co y nghia khi cho phep nhieu hang). Chong goi LLM trung dua vao
// uq_cv_improvement_request_active (V6, bang cv_improvement_requests) o tang hang doi, KHONG phai
// unique o bang nay - xem CvImprovementSuggestionRepository.findFirstByResumeIdOrderByGeneratedAtDescIdDesc
// de lay dung ban moi nhat.
@Entity
@Table(name = "cv_improvement_suggestions")
@Getter
@Setter
@NoArgsConstructor
public class CvImprovementSuggestion {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_keywords", nullable = false)
    private List<String> missingKeywords;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "section_suggestions", nullable = false)
    private List<CvImprovementSectionSuggestion> sectionSuggestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "learning_path", nullable = false)
    private List<CvImprovementLearningPathItem> learningPath;

    @Column(nullable = false)
    private String model;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Generated(event = EventType.INSERT)
    @Column(name = "generated_at", insertable = false, updatable = false)
    private Instant generatedAt;
}
