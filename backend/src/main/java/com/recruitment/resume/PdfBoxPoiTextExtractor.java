package com.recruitment.resume;

import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

@Service
public class PdfBoxPoiTextExtractor implements TextExtractor {

    @Override
    public String extract(InputStream content, ResumeFileType fileType) {
        return switch (fileType) {
            case PDF -> extractPdf(content);
            case DOCX -> extractDocx(content);
        };
    }

    // PDF khong co text layer (ban scan) tra ve chuoi rong sau trim() - day la hanh vi DUNG
    // (khong OCR theo quyet dinh da chot cua D1), khong phai loi. Chi file hong/khong mo duoc
    // (bao gom PDF co mat khau - InvalidPasswordException extends IOException) moi nem
    // ResumeExtractionException.
    private String extractPdf(InputStream content) {
        byte[] bytes;
        try {
            bytes = content.readAllBytes();
        } catch (IOException e) {
            throw new ResumeExtractionException("Khong doc duoc noi dung file PDF", e);
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document).trim();
        } catch (IOException e) {
            throw new ResumeExtractionException("Khong mo duoc file PDF", e);
        }
    }

    private String extractDocx(InputStream content) {
        try (XWPFDocument document = new XWPFDocument(content);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText().trim();
        } catch (IOException e) {
            throw new ResumeExtractionException("Khong mo duoc file DOCX", e);
        }
    }
}
