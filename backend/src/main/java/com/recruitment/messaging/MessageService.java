package com.recruitment.messaging;

import com.recruitment.common.dto.PageResponse;
import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.ConversationReadOnlyException;
import com.recruitment.common.exception.InvalidMessageAttachmentException;
import com.recruitment.common.exception.MessageAttachmentNotFoundException;
import com.recruitment.common.exception.MessageEmptyException;
import com.recruitment.common.exception.MessageNotFoundException;
import com.recruitment.common.exception.MessageTooLongException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.HrApplicationAccess;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.messaging.dto.ConversationCandidateResponse;
import com.recruitment.messaging.dto.ConversationHrResponse;
import com.recruitment.messaging.dto.MessageResponse;
import com.recruitment.messaging.dto.MessageThreadResponse;
import com.recruitment.storage.FileSignatures;
import com.recruitment.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// FR-C06 - cuoc trao doi theo don (M1-M5). Kiem quyen DUNG LAI hai quy uoc co san, khong viet ban thu hai:
// phia HR HrApplicationAccess.loadOwned (khong co cong ty 404, don khong ton tai 404, don cong ty khac 403 -
// R-Q1); phia ung vien findByIdAndCandidateId (don khong ton tai va don cua nguoi khac CUNG 404 - R-Q2).
// Vai tro nguoi goi do controller quyet dinh theo duong dan, khong nhan tu request (R-Q3).
@Service
public class MessageService {

    static final int MAX_BODY_LENGTH = 4000;
    static final int THREAD_LIMIT = 200;
    static final long MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024;
    static final int MAX_FILE_NAME_CODE_POINTS = 255;
    static final String FALLBACK_FILE_NAME = "tep-dinh-kem";
    static final String ATTACHMENT_SUBDIRECTORY = "message-attachments";

    private static final int INBOX_DEFAULT_SIZE = 20;
    private static final int INBOX_MAX_SIZE = 50;

    private final HrApplicationAccess hrApplicationAccess;
    private final JobApplicationRepository jobApplicationRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationMessageRepository messageRepository;
    private final StorageService storageService;

    public MessageService(
            HrApplicationAccess hrApplicationAccess,
            JobApplicationRepository jobApplicationRepository,
            CompanyRepository companyRepository,
            ApplicationMessageRepository messageRepository,
            StorageService storageService) {
        this.hrApplicationAccess = hrApplicationAccess;
        this.jobApplicationRepository = jobApplicationRepository;
        this.companyRepository = companyRepository;
        this.messageRepository = messageRepository;
        this.storageService = storageService;
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

    // ---- M4 ----
    // R-Q4 - kiem quyen tren don TRUOC, roi moi nap tin bang ca messageId lan applicationId.

    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachmentAsHr(UUID ownerId, UUID applicationId, UUID messageId) {
        return downloadAttachment(loadForHr(ownerId, applicationId), messageId);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachmentAsCandidate(UUID candidateId, UUID applicationId, UUID messageId) {
        return downloadAttachment(loadForCandidate(candidateId, applicationId), messageId);
    }

    // ---- M5 ----
    // R-I4 - hop thu chi doc, KHONG danh dau da doc.

    @Transactional(readOnly = true)
    public PageResponse<ConversationHrResponse> listConversationsAsHr(UUID ownerId, Integer page, Integer size) {
        // R-Q6 - HR chua co cong ty -> 404 COMPANY_NOT_FOUND, cung cau voi HrApplicationAccess.
        Company company = companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
        return PageResponse.from(
                messageRepository.findHrConversations(
                        company.getId(), MessageSenderRole.CANDIDATE.name(), inboxPage(page, size)),
                row -> new ConversationHrResponse(
                        row.getApplicationId(),
                        row.getJobId(),
                        row.getJobTitle(),
                        row.getCounterpartName(),
                        ApplicationStatus.valueOf(row.getApplicationStatus()),
                        row.getLastMessageAt(),
                        MessageExcerpt.of(row.getLastBody()),
                        MessageSenderRole.HR.name().equals(row.getLastSenderRole()),
                        Boolean.TRUE.equals(row.getLastHasAttachment()),
                        row.getUnreadCount().intValue()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationCandidateResponse> listConversationsAsCandidate(
            UUID candidateId, Integer page, Integer size) {
        return PageResponse.from(
                messageRepository.findCandidateConversations(
                        candidateId, MessageSenderRole.HR.name(), inboxPage(page, size)),
                row -> new ConversationCandidateResponse(
                        row.getApplicationId(),
                        row.getJobId(),
                        row.getJobTitle(),
                        row.getCounterpartName(),
                        ApplicationStatus.valueOf(row.getApplicationStatus()),
                        row.getLastMessageAt(),
                        MessageExcerpt.of(row.getLastBody()),
                        MessageSenderRole.CANDIDATE.name().equals(row.getLastSenderRole()),
                        Boolean.TRUE.equals(row.getLastHasAttachment()),
                        row.getUnreadCount().intValue()));
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

    // R-I1 - phan trang nhu thong bao (NotificationService): mac dinh 20, toi da 50, trang am -> 0.
    private static PageRequest inboxPage(Integer page, Integer size) {
        int safePage = (page == null || page < 0) ? 0 : page;
        int safeSize = (size == null || size < 1) ? INBOX_DEFAULT_SIZE : Math.min(size, INBOX_MAX_SIZE);
        return PageRequest.of(safePage, safeSize);
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
    // (R-M1, R-M3) -> tep (R-F). Loi o buoc nao dung o buoc do - don WITHDRAWN khong luu gi, ke ca tep.
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
        // Co phan "file" trong request la co tep, ke ca 0 byte (R-F3 bao loi rieng cho tep trong).
        boolean hasFile = file != null;
        if (!hasText && !hasFile) {
            throw new MessageEmptyException();
        }
        if (hasText && body.length() > MAX_BODY_LENGTH) {
            throw new MessageTooLongException();
        }

        ApplicationMessage message = new ApplicationMessage();
        message.setApplicationId(application.getId());
        message.setSenderId(senderId);
        message.setSenderRole(senderRole);
        // R-M2 - chi gom khoang trang thi coi nhu khong co noi dung chu: luu NULL, khong luu chuoi trang.
        message.setBody(hasText ? body : null);
        if (hasFile) {
            attach(message, file);
        }
        return toResponse(messageRepository.saveAndFlush(message), senderRole);
    }

    // R-F2-R-F6, R-F9, muc 12 L4 - thu tu kiem: 0 byte -> qua 5MB -> sai loai. Loai CHI theo magic bytes, khong
    // theo duoi ten hay Content-Type cua client. Luu tep TRUOC, ghi DB sau (mau ResumeService.upload): ghi DB
    // loi thi tep mo coi nam lai - chap nhan, khong don.
    private void attach(ApplicationMessage message, MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidMessageAttachmentException("Tệp đính kèm đang trống");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new InvalidMessageAttachmentException("Tệp đính kèm vượt quá 5MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidMessageAttachmentException("Không đọc được tệp đính kèm");
        }
        AttachmentType type = FileSignatures.detect(content)
                .map(AttachmentType::from)
                .orElseThrow(() -> new InvalidMessageAttachmentException(
                        "Định dạng không hợp lệ, chỉ nhận PDF, DOCX, PNG, JPEG hoặc WEBP"));

        // R-F5 - ten luu = uuid + duoi chuan, KHONG dung ten goc lam duong dan.
        String storedName = UUID.randomUUID() + "." + type.storedExtension();
        storageService.store(ATTACHMENT_SUBDIRECTORY, storedName, new ByteArrayInputStream(content));

        message.setAttachmentKey(ATTACHMENT_SUBDIRECTORY + "/" + storedName);
        message.setAttachmentName(displayFileName(file.getOriginalFilename(), type));
        message.setAttachmentType(type);
        message.setAttachmentSize((long) content.length);
    }

    // R-F6 - ten hien thi: phan sau dau / hoac \ cuoi cung, bo ky tu dieu khien (Character.isISOControl - muc 12
    // L6), rong -> "tep-dinh-kem". Duoi ten (khong phan biet hoa/thuong) khong thuoc loai da nhan dang thi noi
    // them ".{duoi chuan}". Cuoi cung cat phan ten TRUOC duoi de ca ten <= 255 code point (cot VARCHAR(255) dem
    // ky tu, khong cat doi cap surrogate). Chi de hien thi va dat Content-Disposition, khong lam duong dan luu.
    static String displayFileName(String originalName, AttachmentType type) {
        String name = originalName == null ? "" : originalName;
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(lastSeparator + 1);
        name = name.codePoints()
                .filter(cp -> !Character.isISOControl(cp))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        if (name.isEmpty()) {
            name = FALLBACK_FILE_NAME;
        }

        String base;
        String suffix;
        int dot = name.lastIndexOf('.');
        String currentExtension = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (dot >= 0 && type.acceptsNameExtension(currentExtension)) {
            base = name.substring(0, dot);
            suffix = name.substring(dot);
        } else {
            base = name;
            suffix = "." + type.storedExtension();
        }

        int maxBaseCodePoints = MAX_FILE_NAME_CODE_POINTS - suffix.codePointCount(0, suffix.length());
        if (base.codePointCount(0, base.length()) > maxBaseCodePoints) {
            base = base.substring(0, base.offsetByCodePoints(0, maxBaseCodePoints));
        }
        return base + suffix;
    }

    private AttachmentDownload downloadAttachment(JobApplication application, UUID messageId) {
        ApplicationMessage message = messageRepository
                .findByIdAndApplicationId(messageId, application.getId())
                .orElseThrow(MessageNotFoundException::new);
        if (message.getAttachmentKey() == null) {
            throw new MessageAttachmentNotFoundException();
        }
        Resource resource = storageService
                .load(message.getAttachmentKey())
                .orElseThrow(MessageAttachmentNotFoundException::new);
        return new AttachmentDownload(
                resource, message.getAttachmentName(), message.getAttachmentType().mediaType());
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
