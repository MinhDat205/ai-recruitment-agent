package com.recruitment.cvbuilder;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

// FR-U12 R-D1, R-D2 - du lieu form cua mot CV dung bang CV builder (V18). Hang BAT BIEN: sua CV tao CV moi + hang moi
// (R-W6), khong co updated_at/deleted_at. resume_id la UUID thuong, khong @OneToOne - khuon ResumeParsedData.
@Entity
@Table(name = "cv_builder_documents")
@Getter
@Setter
@NoArgsConstructor
public class CvBuilderDocument {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "resume_id", nullable = false, unique = true)
    private UUID resumeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CvTemplate template;

    // Form DA chuan hoa (R-F3) - nguon de mo lai form khi sua CV (R-B2).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_data", nullable = false)
    private CvBuilderForm formData;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
