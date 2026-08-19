package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

// Test thuan Java, khong can Spring context - PdfBoxPoiTextExtractor khong co dependency nao
// ngoai PDFBox/POI.
class PdfBoxPoiTextExtractorTest {

    private final PdfBoxPoiTextExtractor extractor = new PdfBoxPoiTextExtractor();

    private InputStream fixture(String filename) {
        InputStream in = getClass().getResourceAsStream("/fixtures/resumes/" + filename);
        if (in == null) {
            throw new IllegalStateException("Khong tim thay fixture: " + filename);
        }
        return in;
    }

    @Test
    void extract_oneColumnPdf_containsExpectedText() throws IOException {
        String text;
        try (InputStream in = fixture("cv-mot-cot.pdf")) {
            text = extractor.extract(in, ResumeFileType.PDF);
        }
        assertThat(text).contains("Đại học Bách Khoa TPHCM").contains("Kubernetes");
    }

    // Hai cot la hai khoi text doc lap dat canh nhau (giong template CV that dung text box), nen
    // PDFBox tra text dan xen giua hai cot - "HOC VAN" cua cot phai xuat hien chen giua ten va
    // chuc danh cua cot trai. Day la hanh vi DUNG cua PDFBox voi PDF nhieu cot, khong phai loi
    // fixture - CHI assert contains, TUYET DOI khong assert thu tu dong. LLM o Luot 3 phai xu ly
    // duoc input lon xon nay.
    @Test
    void extract_twoColumnPdf_containsExpectedTextRegardlessOfOrder() throws IOException {
        String text;
        try (InputStream in = fixture("cv-hai-cot.pdf")) {
            text = extractor.extract(in, ResumeFileType.PDF);
        }
        assertThat(text).contains("Đại học Bách Khoa TPHCM").contains("Kubernetes");
    }

    @Test
    void extract_docx_containsExpectedText() throws IOException {
        String text;
        try (InputStream in = fixture("cv-mau.docx")) {
            text = extractor.extract(in, ResumeFileType.DOCX);
        }
        assertThat(text).contains("Đại học Bách Khoa TPHCM").contains("Kubernetes");
    }

    @Test
    void extract_corruptPdf_throwsResumeExtractionException() throws IOException {
        try (InputStream in = fixture("cv-hong.pdf")) {
            assertThrows(ResumeExtractionException.class, () -> extractor.extract(in, ResumeFileType.PDF));
        }
    }

    @Test
    void extract_scannedPdfWithoutTextLayer_returnsEmptyStringWithoutThrowing() throws IOException {
        String text;
        try (InputStream in = fixture("cv-scan.pdf")) {
            text = extractor.extract(in, ResumeFileType.PDF);
        }
        assertThat(text).isEmpty();
    }
}
