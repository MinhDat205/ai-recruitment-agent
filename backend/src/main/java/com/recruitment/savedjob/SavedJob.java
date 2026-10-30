package com.recruitment.savedjob;

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
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

// FR-U16 (V20). candidate_id/job_id la UUID thuong, khong @ManyToOne (cung quy uoc JobApplication). L4: saved_at do
// DEFAULT now() cua DB gan (R-S8), danh dau @Generated nhu changed_at cua ApplicationStatusHistory - khong tu set trong
// code. saved_at/deleted_at chi doc qua entity: luu lai va bo luu ghi bang cau native co dieu kien (REQUIREMENT muc
// 4.4), entity chi dung de INSERT dong moi.
@Entity
@Table(name = "saved_jobs")
@Getter
@Setter
@NoArgsConstructor
public class SavedJob {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Generated(event = EventType.INSERT)
    @Column(name = "saved_at", insertable = false, updatable = false)
    private Instant savedAt;

    @Column(name = "deleted_at", insertable = false, updatable = false)
    private Instant deletedAt;
}
