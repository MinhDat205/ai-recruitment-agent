package com.recruitment.company;

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

// companies KHONG co cot deleted_at - khong co soft delete cho company.
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
public class Company {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(name = "logo_url")
    private String logoUrl;

    private String description;

    @Column(name = "company_size")
    private String companySize;

    private String industry;

    private String website;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String address;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    // Ten khong bat dau bang "get" co chu dich: tranh Hibernate hieu nham la property can map
    // va tranh Jackson tu serialize ngoai y muon. Dung updatedAt lam moc cache-bust - don gian,
    // du de trinh duyet khong hien nham logo cu tu cache sau khi HR doi logo; danh doi la sua
    // field khac (vd ten cong ty) cung lam URL doi, gay tai lai anh khong can thiet nhung khong
    // sai du lieu.
    public String logoUrlWithCacheBust() {
        if (logoUrl == null) {
            return null;
        }
        // updatedAt la @Generated (DB tra ve sau flush) - co the chua duoc nap neu entity nay
        // chi ton tai trong first-level cache va chua thuc su flush (vd fixture test dung
        // save() ben trong mot @Transactional bao ngoai, chua chac da flush truoc luc doc lai).
        // Trong request HTTP that, transaction service da commit truoc khi build response nen
        // updatedAt luon co gia tri - day chi la phong ve, khong phai bo qua loi thuc.
        if (updatedAt == null) {
            return logoUrl;
        }
        return logoUrl + "?v=" + updatedAt.toEpochMilli();
    }
}
