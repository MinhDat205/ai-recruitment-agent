package com.recruitment.messaging;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.ConversationReadOnlyException;
import com.recruitment.common.exception.InvalidMessageAttachmentException;
import com.recruitment.common.exception.MessageEmptyException;
import com.recruitment.common.exception.MessageTooLongException;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.HrApplicationAccess;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.messaging.dto.MessageResponse;
import com.recruitment.messaging.dto.MessageThreadResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// FR-C06 - cuoc trao doi theo don (M1-M3). Kiem quyen DUNG LAI hai quy uoc co san, khong viet ban thu hai:
// phia HR HrApplicationAccess.loadOwned (khong co cong ty 404, don khong ton tai 404, don cong ty khac 403 -
// R-Q1); phia ung vien findByIdAndCandidateId (don khong ton tai va don cua nguoi khac CUNG 404 - R-Q2).
// Vai tro nguoi goi do controller quyet dinh theo duong dan, khong nhan tu request (R-Q3).
@Service
public class MessageService {

    static final int MAX_BODY_LENGTH = 4000;
    static final int THREAD_LIMIT = 200;

    private final HrApplicationAccess hrApplicationAccess;
    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationMessageRepository messageRepository;

    public MessageService(
            HrApplicationAccess hrApplicationAccess,
            JobApplicationRepository jobApplicationRepository,
            ApplicationMessageRepository messageRepository) {
        this.hrApplicationAccess = hrApplicationAccess;
        this.jobApplicationRepository = jobApplicationRepository;
        this.messageRepository = messageRepository;
    }

    // ---- M1 ----

    @Transactional(readOnly = true)
    public MessageThreadResponse getThreadAsHr(UUID ownerId, UUID applicationId) {
        return buildThread(loadForHr(ownerId, applicationId), MessageSenderRole.HR);
    }

    @Transactional(readOnly = true)
    public MessageThreadResponse getThreadAsCandidate(UUID candidateId, UUID applicationId) {
        return buildThread(loadForCandidate(candidateId, applicationId), MessageSenderRole.CANDIDATE);
    }

    // ---- M2 ----

    @Transactional
    public MessageResponse sendAsHr(UUID ownerId, UUID applicationId, String body, MultipartFile file) {
        return send(loadForHr(ownerId, applicationId), ownerId, MessageSenderRole.HR, body, file);
    }

    @Transactional
    public MessageResponse sendAsCandidate(UUID candidateId, UUID applicationId, String body, MultipartFile file) {
        return send(loadForCandidate(candidateId, applicationId), candidateId, MessageSenderRole.CANDIDATE, body, file);
    }

    // ---- M3 ----
    // Chi danh dau tin nhan. Danh dau thong bao NEW_MESSAGE (R-R3, muc 12 L2) them o dot 4.

    @Transactional
    public void markReadAsHr(UUID ownerId, UUID applicationId) {
        markRead(loadForHr(ownerId, applicationId), MessageSenderRole.HR);
    }

    @Transactional
    public void markReadAsCandidate(UUID candidateId, UUID applicationId) {
        markRead(loadForCandidate(candidateId, applicationId), MessageSenderRole.CANDIDATE);
    }

    // ---- noi bo ----

    private JobApplication loadForHr(UUID ownerId, UUID applicationId) {
        return hrApplicationAccess.loadOwned(ownerId, applicationId).application();
    }

    private JobApplication loadForCandidate(UUID candidateId, UUID applicationId) {
        return jobApplicationRepository
                .findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
    }

    // R-M7 - lay THREAD_LIMIT + 1 tin moi nhat de biet con tin cu hon hay khong, tra toi da THREAD_LIMIT
    // tin theo thu tu tang dan. unreadCount dem tren TOAN BO tin cua don (khong chi THREAD_LIMIT tin).
    private MessageThreadResponse buildThread(JobApplication application, MessageSenderRole callerRole) {
        List<ApplicationMessage> latest =
                messageRepository.findLatest(application.getId(), PageRequest.of(0, THREAD_LIMIT + 1));
        boolean olderMessagesHidden = latest.size() > THREAD_LIMIT;
        List<ApplicationMessage> shown =
                new ArrayList<>(olderMessagesHidden ? latest.subList(0, THREAD_LIMIT) : latest);
        Collections.reverse(shown);

        long unread = messageRepository.countByApplicationIdAndSenderRoleAndReadAtIsNull(
                application.getId(), callerRole.other());
        List<MessageResponse> messages =
                shown.stream().map(m -> toResponse(m, callerRole)).toList();
        return new MessageThreadResponse(canSend(application), olderMessagesHidden, (int) unread, messages);
    }

    // R-M5 - thu tu kiem: quyen (da xong o loadForHr/loadForCandidate) -> trang thai don (R-M4) -> noi dung
    // (R-M1, R-M3) -> tep (R-F). Loi o buoc nao dung o buoc do.
    private MessageResponse send(
            JobApplication application,
            UUID senderId,
            MessageSenderRole senderRole,
            String rawBody,
            MultipartFile file) {
        if (!canSend(application)) {
            throw new ConversationReadOnlyException();
        }

        String body = normalizeLineBreaks(rawBody);
        boolean hasText = body != null && !body.isBlank();
        boolean hasFile = file != null && !file.isEmpty();
        if (!hasText && !hasFile) {
            throw new MessageEmptyException();
        }
        if (hasText && body.length() > MAX_BODY_LENGTH) {
            throw new MessageTooLongException();
        }
        if (hasFile) {
            // TAM THOI o dot 2 (muc 12 L3): tep dinh kem lam o dot 3, dot 3 xoa han nhanh nay.
            throw new InvalidMessageAttachmentException(
                    "Định dạng không hợp lệ, chỉ nhận PDF, DOCX, PNG, JPEG hoặc WEBP");
        }

        ApplicationMessage message = new ApplicationMessage();
        message.setApplicationId(application.getId());
        message.setSenderId(senderId);
        message.setSenderRole(senderRole);
        // R-M2 - chi gom khoang trang thi coi nhu khong co noi dung chu: luu NULL, khong luu chuoi trang.
        message.setBody(hasText ? body : null);
        return toResponse(messageRepository.saveAndFlush(message), senderRole);
    }

    private void markRead(JobApplication application, MessageSenderRole callerRole) {
        // R-R4 - moi trang thai don, ke ca WITHDRAWN. Chi tin cua ben kia (R-R1).
        messageRepository.markReadFromSender(application.getId(), callerRole.other().name());
    }

    // R-M8 - backend tinh, frontend khong tu suy.
    private static boolean canSend(JobApplication application) {
        return application.getStatus() != ApplicationStatus.WITHDRAWN;
    }

    // R-M2 - chuan hoa DUY NHAT duoc phep: CRLF va CR -> LF. Khong trim, khong cat, khong loc ky tu.
    static String normalizeLineBreaks(String body) {
        if (body == null) {
            return null;
        }
        return body.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static MessageResponse toResponse(ApplicationMessage message, MessageSenderRole callerRole) {
        MessageResponse.Attachment attachment = message.getAttachmentKey() == null
                ? null
                : new MessageResponse.Attachment(
                        message.getAttachmentName(), message.getAttachmentType(), message.getAttachmentSize());
        return new MessageResponse(
                message.getId(),
                message.getSenderRole(),
                message.getSenderRole() == callerRole,
                message.getBody(),
                attachment,
                message.getCreatedAt());
    }
}
