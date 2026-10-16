package com.recruitment.talentpool;

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

// FR-H15 - mot kho ung vien cua cong ty (R-K1). Xoa mem bang deleted_at (R-K5): kho da xoa khong truy cap duoc, muc cua
// no giu nguyen. name luu ban da strip (TalentPoolNameRules); ten trung chot o uq_talent_pool_name_per_company (V17).
@Entity
@Table(name = "talent_pools")
@Getter
@Setter
@NoArgsConstructor
public class TalentPool {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // Trigger trg_talent_pools_updated_at (V17) cap nhat - entity chi doc.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
