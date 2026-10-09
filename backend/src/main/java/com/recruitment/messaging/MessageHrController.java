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

// FR-C06 M1-M3 phia HR. /api/hr/** da bi SecurityConfig chan hasRole("HR") o filter chain; quyen so huu
// don kiem o MessageService qua HrApplicationAccess (R-Q1). Vai tro nguoi gui = HR suy tu duong dan nay.
@RestController
@RequestMapping("/api/hr/applications/{applicationId}/messages")
public class MessageHrController {

    private final MessageService messageService;

    public MessageHrController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public MessageThreadResponse getThread(Authentication authentication, @PathVariable UUID applicationId) {
        return messageService.getThreadAsHr(UUID.fromString(authentication.getName()), applicationId);
    }

    // Khong khai consumes: nhan ca multipart/form-data (giao dien) lan application/x-www-form-urlencoded (tin
    // chi co chu) - file=null voi request urlencoded (muc 4.3, T24).
    @PostMapping
    public ResponseEntity<MessageResponse> send(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @RequestParam(value = "body", required = false) String body,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        MessageResponse response =
                messageService.sendAsHr(UUID.fromString(authentication.getName()), applicationId, body, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/read")
    public ResponseEntity<Void> markRead(Authentication authentication, @PathVariable UUID applicationId) {
        messageService.markReadAsHr(UUID.fromString(authentication.getName()), applicationId);
        return ResponseEntity.noContent().build();
    }

    // M4 - mau ResumeHrController.download: luon attachment (khong inline), ten tep ma hoa UTF-8 (R-F7).
    @GetMapping("/{messageId}/attachment")
    public ResponseEntity<Resource> downloadAttachment(
            Authentication authentication, @PathVariable UUID applicationId, @PathVariable UUID messageId) {
        AttachmentDownload download = messageService.downloadAttachmentAsHr(
                UUID.fromString(authentication.getName()), applicationId, messageId);
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(download.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }
}
