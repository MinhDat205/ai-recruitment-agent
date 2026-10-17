package com.recruitment.common.exception;

// FR-U12 R-F11 - form CV builder khong hop le (400 INVALID_CV_BUILDER_FORM). Khuon InvalidCvQaQuestionException: moi loai
// loi mot factory, cau ghep tu mau co dinh o muc 4.4 + nhan o lay tu CvBuilderField - KHONG nhan String tu do, nen cau loi
// khong the chua gia tri nguoi dung da go. Handler o GlobalExceptionHandler (dot 4).
// Mau "{O}: có một từ quá dài..." (R-F12) them o dot 3 cung kiem glyph.
public class InvalidCvBuilderFormException extends RuntimeException {

    private InvalidCvBuilderFormException(String message) {
        super(message);
    }

    // R-W1 - template null.
    public static InvalidCvBuilderFormException templateRequired() {
        return new InvalidCvBuilderFormException("Vui lòng chọn mẫu trình bày.");
    }

    // O bat buoc null sau R-F3, hoac phan tu null trong danh sach.
    public static InvalidCvBuilderFormException required(CvBuilderField.Path path) {
        return new InvalidCvBuilderFormException(path.subject() + " là bắt buộc.");
    }

    public static InvalidCvBuilderFormException tooLong(CvBuilderField.Path path, int maxLength) {
        return new InvalidCvBuilderFormException(path.subject() + " tối đa " + maxLength + " ký tự.");
    }

    public static InvalidCvBuilderFormException tooManyItems(CvBuilderField.Path path, int maxItems) {
        return new InvalidCvBuilderFormException(path.subject() + " tối đa " + maxItems + " mục.");
    }

    // R-F4 - ky tu dieu khien/dinh dang (dot 3: ca ky tu khong co glyph).
    public static InvalidCvBuilderFormException unsupportedCharacter(CvBuilderField.Path path) {
        return new InvalidCvBuilderFormException(path.subject() + " có ký tự không hỗ trợ trong CV.");
    }

    // R-F6.
    public static InvalidCvBuilderFormException invalidDate(CvBuilderField.Path path) {
        return new InvalidCvBuilderFormException(
                path.subject() + " phải có dạng MM/YYYY hoặc YYYY, năm từ 1950 đến 2100.");
    }

    // R-F7 - path la muc (vd EXPERIENCE.at(2) -> "Kinh nghiệm 2").
    public static InvalidCvBuilderFormException startAfterEnd(CvBuilderField.Path item) {
        return new InvalidCvBuilderFormException(
                item.subject() + ": thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc.");
    }

    // R-F8 - path la muc kinh nghiem.
    public static InvalidCvBuilderFormException currentWithEndDate(CvBuilderField.Path item) {
        return new InvalidCvBuilderFormException(item.subject() + ": đang làm thì không nhập thời gian kết thúc.");
    }

    // R-F9 - path la muc hoc van (EDUCATION.at(i) -> "Học vấn i").
    public static InvalidCvBuilderFormException invalidGpa(CvBuilderField.Path item) {
        return new InvalidCvBuilderFormException(item.subject() + ": GPA phải từ 0 đến 10, tối đa 2 chữ số thập phân.");
    }

    public static InvalidCvBuilderFormException invalidEmail() {
        return new InvalidCvBuilderFormException("Email không đúng định dạng.");
    }

    public static InvalidCvBuilderFormException unknownIndustry() {
        return new InvalidCvBuilderFormException("Ngành nghề không có trong danh mục.");
    }
}
