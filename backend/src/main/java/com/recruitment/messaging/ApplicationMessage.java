package com.recruitment.messaging;

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
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

// FR-C06 - mot tin nhan cua cuoc trao doi gan voi MOT don (R-P1). application_id/sender_id la UUID thuong,
// khong @ManyToOne (quy uoc voi JobApplication/Notification). Khong co updated_at/deleted_at (R-P5): cot
// duy nhat doi sau khi tao la read_at, va chi doi qua cau UPDATE co dieu kien o repository (R-R2).
@Entity
@Table(name = "application_messages")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationMessage {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false)
    private MessageSenderRole senderRole;

    // Nguyen van sau R-M2 (chi doi CRLF/CR -> LF); null khi tin chi co tep.
    private String body;

    @Column(name = "attachment_key")
    private String attachmentKey;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    private AttachmentType attachmentType;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    @Column(name = "read_at", insertable = false, updatable = false)
    private Instant readAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
