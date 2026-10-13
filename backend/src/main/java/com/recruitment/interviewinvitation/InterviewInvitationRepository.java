package com.recruitment.interviewinvitation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewInvitationRepository extends JpaRepository<InterviewInvitation, UUID> {

    // Chi con test cu dung (muc 4.2) - src/main KHONG chon giay moi theo created_at (R-A1).
    List<InterviewInvitation> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    // FR-H12 I1 (R-A2) - moi giay moi cua don; thu tu (hieu luc truoc, roi superseded_at giam dan) sap o service.
    List<InterviewInvitation> findByApplicationId(UUID applicationId);

    // FR-H12 R-A1 - giay moi hieu luc = dong superseded_at IS NULL (toi da mot, uq_invitation_active). KHONG xac dinh
    // bang created_at. Dung o I4 (va K1/C07 tu dot 4).
    Optional<InterviewInvitation> findByApplicationIdAndSupersededAtIsNull(UUID applicationId);

    // FR-H12 R-G3 buoc 5 - danh dau giay moi hieu luc la da bi thay. Mot cau UPDATE theo application_id, chay SAU khi
    // da khoa hang don (JobApplicationRepository.lockIfInterviewInvited) - khong danh dau theo id da doc truoc do (muc 8).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InterviewInvitation i SET i.supersededAt = :at WHERE i.applicationId = :applicationId AND i.supersededAt IS NULL")
    int markActiveSuperseded(@Param("applicationId") UUID applicationId, @Param("at") Instant at);
}
