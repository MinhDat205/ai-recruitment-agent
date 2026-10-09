package com.recruitment.messaging;

import java.util.List;
import java.util.UUID;
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

    // R-R2 - MOT cau UPDATE co dieu kien: chi tin cua ben kia (senderRole truyen vao la vai tro ben kia), chi
    // dong con read_at IS NULL - goi lai khong doi gi them. now() cua Postgres theo transaction.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE application_messages SET read_at = now()"
                    + " WHERE application_id = :applicationId AND sender_role = :senderRole AND read_at IS NULL",
            nativeQuery = true)
    int markReadFromSender(
            @Param("applicationId") UUID applicationId, @Param("senderRole") String senderRole);
}
