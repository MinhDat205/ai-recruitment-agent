package com.recruitment.messaging;

import com.recruitment.messaging.dto.MessageResponse;
import com.recruitment.messaging.dto.MessageThreadResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// FR-C06 M1-M3 phia ung vien. /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o filter
// chain; quyen so huu don kiem o MessageService qua findByIdAndCandidateId - don cua nguoi khac va don khong
// ton tai cung 404 (R-Q2). Vai tro nguoi gui = CANDIDATE suy tu duong dan nay.
@RestController
@RequestMapping("/api/candidates/applications/{applicationId}/messages")
public class MessageCandidateController {

    private final MessageService messageService;

    public MessageCandidateController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public MessageThreadResponse getThread(Authentication authentication, @PathVariable UUID applicationId) {
        return messageService.getThreadAsCandidate(UUID.fromString(authentication.getName()), applicationId);
    }

    // Khong khai consumes - xem MessageHrController.send.
    @PostMapping
    public ResponseEntity<MessageResponse> send(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @RequestParam(value = "body", required = false) String body,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        MessageResponse response = messageService.sendAsCandidate(
                UUID.fromString(authentication.getName()), applicationId, body, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/read")
    public ResponseEntity<Void> markRead(Authentication authentication, @PathVariable UUID applicationId) {
        messageService.markReadAsCandidate(UUID.fromString(authentication.getName()), applicationId);
        return ResponseEntity.noContent().build();
    }

    // M4 - xem MessageHrController.downloadAttachment.
    @GetMapping("/{messageId}/attachment")
    public ResponseEntity<Resource> downloadAttachment(
            Authentication authentication, @PathVariable UUID applicationId, @PathVariable UUID messageId) {
        AttachmentDownload download = messageService.downloadAttachmentAsCandidate(
                UUID.fromString(authentication.getName()), applicationId, messageId);
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(download.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }
}
