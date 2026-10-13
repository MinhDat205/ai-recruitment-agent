package com.recruitment.jobdraft;

import com.recruitment.aigeneration.AiGenerationStamp;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

// FR-H11 R-K4-6 - mot dong K4 cua tin tao bang AI: phan goi y nao da duoc dung khi luu tin, model/prompt/thoi diem sinh
// lay tu token da ky (R-K4-2). BAT BIEN: @Immutable, khong setter, khong updated_at, khong endpoint sua/xoa. Moi tin toi
// da mot dong moi phan (uq_job_ai_generation_part). Ung vien khong bao gio doc bang nay.
@Entity
@Immutable
@Table(name = "job_ai_generations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobAiGeneration {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private JobDraftPart part;

    @Embedded
    private AiGenerationStamp stamp;

    // KHONG @Generated: Hibernate 7.4.1 doc lai cot generated sau INSERT qua EntityEntryImpl.postUpdate -> dat khoa WRITE
    // -> UnsupportedLockAttemptException voi entity @Immutable. Cung khuon ApplicationScreeningAnswer (FR-U09): DB tu
    // dien DEFAULT now(), gia tri chi co khi doc lai tu DB.
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public JobAiGeneration(UUID jobId, JobDraftPart part, AiGenerationStamp stamp) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.part = Objects.requireNonNull(part, "part");
        this.stamp = Objects.requireNonNull(stamp, "stamp");
    }
}
