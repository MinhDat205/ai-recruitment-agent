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

// FR-H15 - mot muc trong kho: lien ket (kho, don) (R-M1). Go = dat removed_at (R-M5), khong xoa hang.
// Them muc KHONG qua save() cua entity nay ma qua TalentPoolEntryRepository.insertIfAbsent (ON CONFLICT ... DO NOTHING,
// L1); go qua cac cau UPDATE co dieu kien cua repository. Entity chi de doc.
@Entity
@Table(name = "talent_pool_entries")
@Getter
@Setter
@NoArgsConstructor
public class TalentPoolEntry {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "pool_id", nullable = false)
    private UUID poolId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Generated(event = EventType.INSERT)
    @Column(name = "added_at", insertable = false, updatable = false)
    private Instant addedAt;

    // NULL = muc dang hieu luc.
    @Column(name = "removed_at")
    private Instant removedAt;
}
