package com.recruitment.blindreview;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// FR-H16 - repository duy nhat cua blindreview/. Ngoai luu vet (R-B3), giu cac cau doc cua BlindReviewResolver va
// IdentityMaskSource: hai lop do la noi goi DUY NHAT cua cac cau duoi (R-S2 - mot dinh nghia an danh duy nhat).
public interface JobBlindReviewChangeRepository extends JpaRepository<JobBlindReviewChange, UUID> {

    // changedAt cua BlindReviewResponse: dong luu vet moi nhat, null neu chua tung doi.
    @Query("SELECT MAX(c.changedAt) FROM JobBlindReviewChange c WHERE c.jobId = :jobId")
    Instant findLatestChangedAt(@Param("jobId") UUID jobId);

    // R-S1 - DINH NGHIA DUY NHAT cua "don dang an danh": Job cua don bat cong tac VA don chua tung co dong lich su
    // INTERVIEW_INVITED [Q10]. Mot cau cho ca tap id (khong N+1). Chi BlindReviewResolver goi.
    @Query(
            value =
                    """
                    SELECT ja.id FROM job_applications ja
                    JOIN jobs j ON j.id = ja.job_id
                    WHERE ja.id IN (:applicationIds)
                      AND j.blind_review_enabled = TRUE
                      AND NOT EXISTS (
                          SELECT 1 FROM application_status_history ash
                          WHERE ash.application_id = ja.id AND ash.to_status = 'INTERVIEW_INVITED')
                    """,
            nativeQuery = true)
    List<UUID> findMaskedApplicationIds(@Param("applicationIds") Collection<UUID> applicationIds);

    // R-M1 - users.full_name/email/phone cua ung vien theo don. Chi IdentityMaskSource goi.
    @Query(
            value =
                    """
                    SELECT ja.id AS applicationId, u.full_name AS fullName, u.email AS email, u.phone AS phone
                    FROM job_applications ja
                    JOIN users u ON u.id = ja.candidate_id
                    WHERE ja.id IN (:applicationIds)
                    """,
            nativeQuery = true)
    List<AccountIdentityRow> findAccountIdentities(@Param("applicationIds") Collection<UUID> applicationIds);

    // R-M1 - contact trong resume_parsed_data.data cua CV ma don da nop (neu da trich xuat). Chi IdentityMaskSource goi.
    @Query(
            value =
                    """
                    SELECT ja.id AS applicationId,
                           rpd.data -> 'contact' ->> 'fullName' AS fullName,
                           rpd.data -> 'contact' ->> 'email' AS email,
                           rpd.data -> 'contact' ->> 'phone' AS phone,
                           rpd.data -> 'contact' ->> 'address' AS address,
                           rpd.data -> 'contact' ->> 'linkedin' AS linkedin
                    FROM job_applications ja
                    JOIN resume_parsed_data rpd ON rpd.resume_id = ja.resume_id
                    WHERE ja.id IN (:applicationIds)
                    """,
            nativeQuery = true)
    List<ParsedContactRow> findParsedContacts(@Param("applicationIds") Collection<UUID> applicationIds);
}
