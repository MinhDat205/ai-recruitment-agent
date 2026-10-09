package com.recruitment.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

// FR-C06 T22 - test don vi FileSignatures (R-C1). Test co san cua FR-U01 (ResumeIntegrationTest...) va FR-H01
// (CompanyOwnerIntegrationTest) la cong an toan cho viec chuyen ResumeService/CompanyOwnerService sang day.
class FileSignaturesTest {

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] withTail(byte[] head) {
        byte[] result = Arrays.copyOf(head, head.length + 16);
        Arrays.fill(result, head.length, result.length, (byte) 0x41);
        return result;
    }

    @Test
    void detectsEachOfTheFiveSignatures() {
        assertThat(FileSignatures.detect(withTail(bytes(0x25, 0x50, 0x44, 0x46)))).contains(DetectedFileType.PDF);
        assertThat(FileSignatures.detect(withTail(bytes(0x50, 0x4B, 0x03, 0x04)))).contains(DetectedFileType.DOCX);
        assertThat(FileSignatures.detect(withTail(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))))
                .contains(DetectedFileType.PNG);
        assertThat(FileSignatures.detect(withTail(bytes(0xFF, 0xD8, 0xFF)))).contains(DetectedFileType.JPEG);
        assertThat(FileSignatures.detect(
                        bytes(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50)))
                .contains(DetectedFileType.WEBP);
    }

    @Test
    void exactSignatureLengthIsEnough() {
        assertThat(FileSignatures.detect(bytes(0x25, 0x50, 0x44, 0x46))).contains(DetectedFileType.PDF);
        assertThat(FileSignatures.detect(bytes(0xFF, 0xD8, 0xFF))).contains(DetectedFileType.JPEG);
    }

    @Test
    void arraysShorterThanSignature_areNotDetected() {
        assertThat(FileSignatures.detect(bytes(0x25, 0x50, 0x44))).isEmpty();
        assertThat(FileSignatures.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A))).isEmpty();
        assertThat(FileSignatures.detect(bytes(0xFF, 0xD8))).isEmpty();
        // RIFF du 4 byte nhung khong du 12 byte de doc "WEBP" o offset 8.
        assertThat(FileSignatures.detect(bytes(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42)))
                .isEmpty();
    }

    @Test
    void emptyAndNullArrays_areNotDetected() {
        assertThat(FileSignatures.detect(new byte[0])).isEmpty();
        assertThat(FileSignatures.detect(null)).isEmpty();
    }

    @Test
    void riffWithoutWebpAtOffset8_isNotDetected() {
        // RIFF....WAVE (tep am thanh) - cung tien to RIFF nhung khong phai WEBP.
        assertThat(FileSignatures.detect(bytes(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45)))
                .isEmpty();
    }

    @Test
    void otherFormats_areNotDetected() {
        assertThat(FileSignatures.detect("GIF89a noi dung anh GIF".getBytes())).isEmpty();
        assertThat(FileSignatures.detect("Van ban thuan, khong phai PDF".getBytes())).isEmpty();
    }

    @Test
    void extensionsMatchStoredExtensionsOfResumeAndLogo() {
        assertThat(DetectedFileType.PDF.extension()).isEqualTo("pdf");
        assertThat(DetectedFileType.DOCX.extension()).isEqualTo("docx");
        assertThat(DetectedFileType.PNG.extension()).isEqualTo("png");
        assertThat(DetectedFileType.JPEG.extension()).isEqualTo("jpg");
        assertThat(DetectedFileType.WEBP.extension()).isEqualTo("webp");
    }
}
