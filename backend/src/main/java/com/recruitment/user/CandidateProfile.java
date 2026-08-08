package com.recruitment.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

// user_id la UUID thuong, khong dung @OneToOne/@JoinColumn de tranh lazy-loading
// ngoai transaction (open-in-view: false). FK/uniqueness da co o DB.
@Entity
@Table(name = "candidate_profiles")
@Getter
@Setter
@NoArgsConstructor
public class CandidateProfile {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    private String headline;

    private String location;

    @Column(name = "current_title")
    private String currentTitle;

    @Column(name = "years_experience")
    private BigDecimal yearsExperience;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public CandidateProfile(UUID userId) {
        this.userId = userId;
    }
}
