package com.recruitment.storage;

import java.util.Optional;

// FR-C06 R-C1 - MOT cho nhan dang loai tep bang magic bytes cho 5 loai, dung chung cho CV (FR-U01), logo
// (FR-H01) va tep dinh kem tin nhan (FR-C06). Chu ky chuyen NGUYEN tu ResumeService/CompanyOwnerService cu.
// Chi doc noi dung - phan mo rong va Content-Type do client gui KHONG duoc dung de quyet dinh (R-F2).
//
// Gioi han da biet (R-F6b): chu ky DOCX la chu ky ZIP chung (PK\x03\x04) - moi tep ZIP (.zip, .xlsx, .jar)
// deu nhan dang thanh DOCX. Khong mo ZIP de kiem cau truc ben trong.
public final class FileSignatures {

    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] DOCX_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_SIGNATURE = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_SIGNATURE = {0x57, 0x45, 0x42, 0x50};

    private FileSignatures() {}

    public static Optional<DetectedFileType> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (matchesAt(content, 0, PDF_SIGNATURE)) {
            return Optional.of(DetectedFileType.PDF);
        }
        if (matchesAt(content, 0, DOCX_SIGNATURE)) {
            return Optional.of(DetectedFileType.DOCX);
        }
        if (matchesAt(content, 0, PNG_SIGNATURE)) {
            return Optional.of(DetectedFileType.PNG);
        }
        if (matchesAt(content, 0, JPEG_SIGNATURE)) {
            return Optional.of(DetectedFileType.JPEG);
        }
        if (matchesAt(content, 0, RIFF_SIGNATURE) && matchesAt(content, 8, WEBP_SIGNATURE)) {
            return Optional.of(DetectedFileType.WEBP);
        }
        return Optional.empty();
    }

    private static boolean matchesAt(byte[] content, int offset, byte[] signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
