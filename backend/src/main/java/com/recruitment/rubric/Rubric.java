package com.recruitment.rubric;

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

// Moi job bat buoc co dung mot rubric (jobs.id UNIQUE trong bang rubrics).
// Nhanh nay chi tao ban ghi rong khi tao Job - tieu chi (rubric_criteria) do B3 dam nhan.
@Entity
@Table(name = "rubrics")
@Getter
@Setter
@NoArgsConstructor
public class Rubric {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    private String name;

    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
