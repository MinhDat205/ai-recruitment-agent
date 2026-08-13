package com.recruitment.rubric;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

@Entity
@Table(name = "rubric_criteria")
@Getter
@Setter
@NoArgsConstructor
public class RubricCriterion {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "rubric_id", nullable = false)
    private UUID rubricId;

    @Column(nullable = false)
    private String name;

    private String description;

    // HR khong bat buoc mo ta thang diem; NULL => AI cham theo thang mac dinh dung chung (FR-H03).
    // Cau truc co dinh (xem ScaleLevelDescription) chu khong phai JSON tu do, de D2 co the
    // serialize thang vao prompt LLM ma khong phai tu doan cau truc.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scale_description")
    private List<ScaleLevelDescription> scaleDescription;

    @Column(nullable = false)
    private BigDecimal weight;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
