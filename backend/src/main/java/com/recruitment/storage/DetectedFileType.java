package com.recruitment.storage;

// FR-C06 R-C1 - loai tep nhan dang bang magic bytes (FileSignatures). storage/ khong import resume/,
// company/, messaging/: moi noi goi tu anh xa sang kieu cua minh (ResumeFileType, duoi logo, AttachmentType).
public enum DetectedFileType {
    PDF("pdf"),
    DOCX("docx"),
    PNG("png"),
    // "jpg" khop duoi logo da luu truoc nay (CompanyOwnerService) - R-F5.
    JPEG("jpg"),
    WEBP("webp");

    private final String extension;

    DetectedFileType(String extension) {
        this.extension = extension;
    }

    // Duoi chuan dung khi dat ten tep luu tru (R-F5), khong co dau cham.
    public String extension() {
        return extension;
    }
}
