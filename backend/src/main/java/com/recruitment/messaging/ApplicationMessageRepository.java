package com.recruitment.messaging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationMessageRepository extends JpaRepository<ApplicationMessage, UUID> {

    // R-M7 - lay N tin MOI NHAT (giam dan), noi goi dao lai thanh tang dan. Cung created_at thi theo id, dung
    // index idx_app_msg_thread (application_id, created_at, id).
    @Query("SELECT m FROM ApplicationMessage m WHERE m.applicationId = :applicationId"
            + " ORDER BY m.createdAt DESC, m.id DESC")
    List<ApplicationMessage> findLatest(@Param("applicationId") UUID applicationId, Pageable pageable);

    // R-M7/R-R1 - dem tren TOAN BO tin cua don (khong chi 200 tin duoc tra), chi tin cua BEN KIA.
    long countByApplicationIdAndSenderRoleAndReadAtIsNull(UUID applicationId, MessageSenderRole senderRole);

    // R-Q4 - nap tin bang CA messageId LAN applicationId: tin thuoc don khac (ke ca don nguoi goi co quyen)
    // cung rong -> 404 MESSAGE_NOT_FOUND. Khong co duong nao doc tep chi bang messageId.
    Optional<ApplicationMessage> findByIdAndApplicationId(UUID id, UUID applicationId);

    // R-R2 - MOT cau UPDATE co dieu kien: chi tin cua ben kia (senderRole truyen vao la vai tro ben kia), chi
    // dong con read_at IS NULL - goi lai khong doi gi them. now() cua Postgres theo transaction.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE application_messages SET read_at = now()"
                    + " WHERE application_id = :applicationId AND sender_role = :senderRole AND read_at IS NULL",
            nativeQuery = true)
    int markReadFromSender(
            @Param("applicationId") UUID applicationId, @Param("senderRole") String senderRole);

    // M5 phia HR (R-I1-R-I3, R-Q6) - moi don vao tin cua cong ty co IT NHAT mot tin (JOIN LATERAL lay tin moi
    // nhat - don chua co tin bi loai tu nhien). KHONG loc j.deleted_at/status tin, KHONG loc don WITHDRAWN (R-I3).
    // Sap tin moi nhat giam dan, trung thoi diem thi applicationId giam dan (muc 12 L6). otherRole = vai tro
    // ben kia ('CANDIDATE') de dem tin chua doc.
    @Query(
            value =
                    """
                    SELECT a.id AS applicationId, a.job_id AS jobId, j.title AS jobTitle,
                           u.full_name AS counterpartName, a.status AS applicationStatus,
                           lm.created_at AS lastMessageAt, lm.body AS lastBody, lm.sender_role AS lastSenderRole,
                           (lm.attachment_key IS NOT NULL) AS lastHasAttachment,
                           (SELECT count(*) FROM application_messages um
                            WHERE um.application_id = a.id AND um.sender_role = :otherRole
                              AND um.read_at IS NULL) AS unreadCount
                    FROM job_applications a
                    JOIN jobs j ON j.id = a.job_id
                    JOIN users u ON u.id = a.candidate_id
                    JOIN LATERAL (
                        SELECT m.created_at, m.body, m.sender_role, m.attachment_key FROM application_messages m
                        WHERE m.application_id = a.id
                        ORDER BY m.created_at DESC, m.id DESC LIMIT 1
                    ) lm ON true
                    WHERE j.company_id = :companyId
                    ORDER BY lm.created_at DESC, a.id DESC
                    """,
            countQuery =
                    """
                    SELECT count(*)
                    FROM job_applications a
                    JOIN jobs j ON j.id = a.job_id
                    WHERE j.company_id = :companyId
                      AND EXISTS (SELECT 1 FROM application_messages m WHERE m.application_id = a.id)
                    """,
            nativeQuery = true)
    Page<ConversationRow> findHrConversations(
            @Param("companyId") UUID companyId, @Param("otherRole") String otherRole, Pageable pageable);

    // M5 phia ung vien - nhu tren, nhung loc theo candidate_id va counterpartName la TEN CONG TY (R-I2): khong
    // JOIN users cua HR, nen ho ten/email HR khong the lot vao ket qua.
    @Query(
            value =
                    """
                    SELECT a.id AS applicationId, a.job_id AS jobId, j.title AS jobTitle,
                           c.name AS counterpartName, a.status AS applicationStatus,
                           lm.created_at AS lastMessageAt, lm.body AS lastBody, lm.sender_role AS lastSenderRole,
                           (lm.attachment_key IS NOT NULL) AS lastHasAttachment,
                           (SELECT count(*) FROM application_messages um
                            WHERE um.application_id = a.id AND um.sender_role = :otherRole
                              AND um.read_at IS NULL) AS unreadCount
                    FROM job_applications a
                    JOIN jobs j ON j.id = a.job_id
                    JOIN companies c ON c.id = j.company_id
                    JOIN LATERAL (
                        SELECT m.created_at, m.body, m.sender_role, m.attachment_key FROM application_messages m
                        WHERE m.application_id = a.id
                        ORDER BY m.created_at DESC, m.id DESC LIMIT 1
                    ) lm ON true
                    WHERE a.candidate_id = :candidateId
                    ORDER BY lm.created_at DESC, a.id DESC
                    """,
            countQuery =
                    """
                    SELECT count(*)
                    FROM job_applications a
                    WHERE a.candidate_id = :candidateId
                      AND EXISTS (SELECT 1 FROM application_messages m WHERE m.application_id = a.id)
                    """,
            nativeQuery = true)
    Page<ConversationRow> findCandidateConversations(
            @Param("candidateId") UUID candidateId, @Param("otherRole") String otherRole, Pageable pageable);
}
