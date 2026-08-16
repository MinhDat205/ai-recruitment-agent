package com.recruitment.resume;

// Tin hieu "khong mo duoc file" cho tang goi (Luot 4) map sang ma loi EXTRACT_CORRUPT - KHONG
// mang theo message goc cua PDFBox/POI de tranh lo chi tiet noi bo ra ngoai. PDF co mat khau
// (PDFBox nem InvalidPasswordException, la mot IOException) cung roi vao day, khong tach ma loi
// rieng: tu goc nhin ung vien, "PDF co mat khau" va "PDF hong" deu dan toi cung mot hanh dong can
// lam - tai lai mot file doc duoc - va bo 5 ma loi da chot (EXTRACT_EMPTY/EXTRACT_CORRUPT/
// LLM_INVALID_JSON/LLM_TIMEOUT/LLM_ERROR) khong co cho cho mot bien the hiem gap cua "khong mo
// duoc file".
public class ResumeExtractionException extends RuntimeException {

    public ResumeExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
