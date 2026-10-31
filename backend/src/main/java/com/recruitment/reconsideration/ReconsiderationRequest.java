package com.recruitment.reconsideration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;
import org.hibernate.generator.EventType;

// FR-C09 (V21). Entity CHI DE DOC: tao yeu cau la mot cau INSERT ... SELECT co dieu kien (R-T3), quyet dinh la UPDATE
// co dieu kien status = 'OPEN' (R-S3, R-D2) - deu native o ReconsiderationRequestRepository, khong doc-roi-save (hai tab
// se ghi de nhau). application_id/decided_by la UUID thuong, khong @ManyToOne (cung quy uoc JobApplication).
// requested_at do DEFAULT now() cua DB gan (khuon changed_at cua ApplicationStatusHistory).
@Entity
@Immutable
@Table(name = "application_reconsideration_requests")
@Getter
@NoArgsConstructor
public class ReconsiderationRequest {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReconsiderationStatus status;

    @Column(name = "hr_response")
    private String hrResponse;

    @Generated(event = EventType.INSERT)
    @Column(name = "requested_at", insertable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;
}
