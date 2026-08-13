package com.recruitment.jobapplication;

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
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

// application_id/changed_by la UUID thuong, khong dung @ManyToOne (cung quy uoc voi
// JobApplication - tranh lazy-loading ngoai transaction). changed_by = NULL nghia la he thong tu
// doi (vd job dong tu dong), khong phai loi du lieu. changed_at CO DEFAULT now() o DB, danh dau
// @Generated giong het applied_at cua JobApplication - khong tu set trong code.
@Entity
@Table(name = "application_status_history")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationStatusHistory {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ApplicationStatus toStatus;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "note")
    private String note;

    @Generated(event = EventType.INSERT)
    @Column(name = "changed_at", insertable = false, updatable = false)
    private Instant changedAt;
}
