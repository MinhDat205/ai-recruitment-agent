package com.recruitment.messaging;

import com.recruitment.storage.DetectedFileType;
import java.util.Set;
import org.springframework.http.MediaType;

// Loai tep dinh kem da nhan dang bang magic bytes (R-F2) - khop CHECK cua cot attachment_type (V12).
// storedExtension = duoi chuan khi luu va khi ep duoi ten hien thi (R-F5, R-F6); acceptedNameExtensions = cac
// duoi ten (khong phan biet hoa/thuong) coi la KHOP loai, khong can noi them duoi (R-F6).
public enum AttachmentType {
    PDF("pdf", Set.of("pdf"), "application/pdf"),
    DOCX("docx", Set.of("docx"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PNG("png", Set.of("png"), "image/png"),
    JPEG("jpg", Set.of("jpg", "jpeg"), "image/jpeg"),
    WEBP("webp", Set.of("webp"), "image/webp");

    private final String storedExtension;
    private final Set<String> acceptedNameExtensions;
    private final MediaType mediaType;

    AttachmentType(String storedExtension, Set<String> acceptedNameExtensions, String mediaType) {
        this.storedExtension = storedExtension;
        this.acceptedNameExtensions = acceptedNameExtensions;
        this.mediaType = MediaType.parseMediaType(mediaType);
    }

    public String storedExtension() {
        return storedExtension;
    }

    public boolean acceptsNameExtension(String extension) {
        return acceptedNameExtensions.contains(extension);
    }

    // R-F7 - Content-Type khi tai ve theo loai da nhan dang luc gui, khong theo ten tep.
    public MediaType mediaType() {
        return mediaType;
    }

    // R-C1 - anh xa tu kieu dung chung cua storage/; ca 5 loai deu nhan duoc lam tep dinh kem.
    static AttachmentType from(DetectedFileType type) {
        return switch (type) {
            case PDF -> PDF;
            case DOCX -> DOCX;
            case PNG -> PNG;
            case JPEG -> JPEG;
            case WEBP -> WEBP;
        };
    }
}
