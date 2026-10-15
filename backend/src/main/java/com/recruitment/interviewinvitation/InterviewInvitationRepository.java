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

    // FR-U10 R-W1 buoc 4 - ung vien chot MOT khung bang MOT cau UPDATE co dieu kien (SQL nguyen van REQUIREMENT R-W1).
    // Chay SAU khi da khoa hang don (JobApplicationRepository.lockIfInterviewInvited). Dieu kien nguyen tu tren hang:
    // giay moi hieu luc + chua chot (bam dup / hai tab -> luot sau danh gia lai WHERE, 0 dong) + khung thuoc CHINH giay moi
    // nay (s.invitation_id = i.id - slotId la / cua giay moi cu / cua don khac chi lam 0 dong, KHONG cham FK ghep
    // fk_invitation_confirmed_slot -> khong 500, R-W4) + khung chua qua (strict >, R-C6). 0 dong -> service doc lai de tra
    // dung ma (R-W5).
    // clearAutomatically = true: cau UPDATE chay ngoai ORM, buoc 5 phai doc lai giay moi/khung tu DB (entity con trong
    // persistence context se tra ban cu). flushAutomatically = true: hien KHONG bat buoc (truoc cau nay khong co ghi ORM
    // nao), giu phong ho nhu updateStatusIfCurrent (JobApplicationRepository) - mo ta L7.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE interview_invitations i
                    SET confirmed_slot_id = :slotId, confirmed_at = :now
                    WHERE i.application_id = :applicationId
                      AND i.superseded_at IS NULL
                      AND i.confirmed_slot_id IS NULL
                      AND EXISTS (SELECT 1 FROM interview_invitation_slots s
                                  WHERE s.id = :slotId AND s.invitation_id = i.id AND s.scheduled_at > :now)
                    """,
            nativeQuery = true)
    int confirmSlotIfAvailable(
            @Param("applicationId") UUID applicationId, @Param("slotId") UUID slotId, @Param("now") Instant now);
}
