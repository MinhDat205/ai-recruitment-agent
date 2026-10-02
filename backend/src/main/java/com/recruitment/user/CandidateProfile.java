package com.recruitment.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

// user_id la UUID thuong, khong dung @OneToOne/@JoinColumn de tranh lazy-loading
// ngoai transaction (open-in-view: false). FK/uniqueness da co o DB.
@Entity
@Table(name = "candidate_profiles")
@Getter
@Setter
@NoArgsConstructor
public class CandidateProfile {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    private String headline;

    private String location;

    @Column(name = "current_title")
    private String currentTitle;

    @Column(name = "years_experience")
    private BigDecimal yearsExperience;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    // FR-U14 V10 - 4 cot mang Postgres (text[]), lan dau tien du an dung kieu nay qua Hibernate
    // (xac nhan bang javap tren hibernate-core-7.4.1.Final: SqlTypes.ARRAY ton tai). BAT BUOC dung
    // String[] (KHONG phai List<String>) - da kiem chung thuc te bang BackendApplicationTests tren
    // Testcontainers: voi List<String>, Hibernate 7.4.1 tu suy ra kieu JDBC mong doi la jsonb (du da
    // khai @JdbcTypeCode(SqlTypes.ARRAY)), lam ddl-auto: validate bao loi "wrong column type...
    // expecting jsonb" ngay luc khoi dong - doi sang String[] thi validate qua dung nhu du kien.
    // Mac dinh mang rong, KHONG null, khop DEFAULT '{}' cua cot. Dot 1 chi anh xa, CHUA dedupe/
    // validate ma (R-F2/R-F3/R-M1/R-M2/R-K1/R-K2 - dot 2).
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "desired_industry_codes", nullable = false)
    private String[] desiredIndustryCodes = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "desired_location_codes", nullable = false)
    private String[] desiredLocationCodes = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "desired_work_modes", nullable = false)
    private String[] desiredWorkModes = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private String[] skills = new String[0];

    // Don vi VND (NUMERIC(14,2)), cung kieu cot voi jobs.salary_min - request/response dung don vi
    // trieu VND (R-S1), quy doi o CandidateProfileService.
    @Column(name = "desired_salary_min")
    private BigDecimal desiredSalaryMin;

    private String bio;

    // NULL = chua qua man onboarding (R-O). Dot 1 khong co logic nao set cot nay (R-O3 - dot 2);
    // V10 da backfill now() cho ho so cu.
    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    // Ten model embedding (FR-U04), KHONG phai kieu vector - ghi o dot 3 (R-E7). Dot 1 luon null.
    @Column(name = "embedding_model")
    private String embeddingModel;

    // KHONG map cot embedding (vector(1536)) - tien le ResumeParsedData.java/JobEmbedding.java: Spring
    // AI cung khong bind kieu vector qua Hibernate, chi doc/ghi qua native query (muc 0.a).

    public CandidateProfile(UUID userId) {
        this.userId = userId;
    }
}
