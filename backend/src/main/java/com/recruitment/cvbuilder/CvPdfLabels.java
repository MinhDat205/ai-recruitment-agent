package com.recruitment.cvbuilder;

import java.util.List;

// FR-U12 R-P5 (+ L4) - chu co dinh DUY NHAT cua mau ngoai gia tri form. Khai mot cho; renderer chi in cac hang nay.
// Khong "Tạo bằng …", khong so trang, khong ngay tao.
public final class CvPdfLabels {

    private CvPdfLabels() {}

    // Tieu de muc.
    public static final String EXPERIENCE = "Kinh nghiệm làm việc";
    public static final String EDUCATION = "Học vấn";
    public static final String PROJECTS = "Dự án";
    public static final String SKILLS = "Kỹ năng";
    public static final String CERTIFICATIONS = "Chứng chỉ";

    // Tien to.
    public static final String EMAIL = "Email: ";
    public static final String PHONE = "Điện thoại: ";
    public static final String ADDRESS = "Địa chỉ: ";
    public static final String LINKEDIN = "LinkedIn: ";
    public static final String LOCATION = "Khu vực: ";
    public static final String GPA = "GPA: ";
    public static final String TECHNOLOGIES = "Công nghệ: ";
    public static final String ISSUER = "Cấp bởi: ";

    // Noi moc (U+2013 co cach hai ben); "Hiện tại" dung chung hang cua mapper (L21).
    public static final String DATE_RANGE = " – ";
    public static final String CURRENT = CvBuilderPayloadMapper.CURRENT_END_DATE;

    // Phan cach: U+00B7 giua ky nang, giua degree va major, giua company va moc (L4b); ", " giua cong nghe (L4a).
    public static final String SEPARATOR = " · ";
    public static final String TECHNOLOGY_SEPARATOR = ", ";

    // Toan bo chu co dinh - T8 go lan luot de chung minh khong co chu thua.
    public static List<String> all() {
        return List.of(EXPERIENCE, EDUCATION, PROJECTS, SKILLS, CERTIFICATIONS,
                EMAIL, PHONE, ADDRESS, LINKEDIN, LOCATION, GPA, TECHNOLOGIES, ISSUER,
                DATE_RANGE, CURRENT, SEPARATOR, TECHNOLOGY_SEPARATOR);
    }
}
