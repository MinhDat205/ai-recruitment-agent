package com.recruitment.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// resume_id la UUID thuong, khong dung @OneToOne - tranh lazy-loading ngoai transaction, giong ly
// do candidate_id trong Resume.java. KHONG map cot embedding: D1 khong dung, de NULL, F1 moi can
// doc/ghi vector(1536) va luc do moi quyet dinh cach map trong JPA - ddl-auto: validate khong yeu
// cau entity map het moi cot cua bang, chi can cot da map khop dung kieu.
@Entity
@Table(name = "resume_parsed_data")
@Getter
@Setter
@NoArgsConstructor
public class ResumeParsedData {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "resume_id", nullable = false, unique = true)
    private UUID resumeId;

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private ResumeParsedPayload data;

    @Column(nullable = false)
    private String model;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "token_usage")
    private Integer tokenUsage;

    // parsed_at do DB sinh luc INSERT; trich xuat lai (FR-C05 R-R5) cap nhat bang native UPDATE
    // (ResumeParsedDataRepository.touchAfterReparse), khong qua entity.
    @Generated(event = EventType.INSERT)
    @Column(name = "parsed_at", insertable = false, updatable = false)
    private Instant parsedAt;

    // FR-C05 R-C4 - cot truy van, luon bang data.industryCode sau khi qua kiem (ResumeParsedDataEnricher).
    @Column(name = "industry_code")
    private String industryCode;

    @Column(name = "region_code")
    private String regionCode;

    // FR-C05 R-E7 - ca bon NULL = chua tinh (job nen ResumeExperienceScheduler se tinh); months NULL
    // khi da tinh ma khong co muc nao duoc tinh (KHONG luu 0). Rang buoc chot o DB:
    // chk_parsed_experience_state, chk_parsed_experience_months (V8).
    @Column(name = "experience_months")
    private Integer experienceMonths;

    @Column(name = "experience_entries_counted")
    private Integer experienceEntriesCounted;

    @Column(name = "experience_entries_skipped")
    private Integer experienceEntriesSkipped;

    @Column(name = "experience_computed_at")
    private Instant experienceComputedAt;
}
