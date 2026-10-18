package com.recruitment.common.exception;

// FR-U12 muc 4.4 - bang nhan o cua form CV builder, khai MOT cho (frontend dung dung cac nhan nay - UI.md muc 7). Dat o
// common/exception (L6): common/ khong import cvbuilder/, tien le AiSyncErrorCode. Moi o thuoc mot khoi danh sach thi
// mang khoi cha (block) de ghep "{Khoi} {i}: {O}" (R-F11).
public enum CvBuilderField {
    FULL_NAME(null, "Họ và tên"),
    EMAIL(null, "Email"),
    PHONE(null, "Điện thoại"),
    ADDRESS(null, "Địa chỉ"),
    LINKEDIN(null, "LinkedIn"),
    CURRENT_TITLE(null, "Chức danh hiện tại"),
    LOCATION_TEXT(null, "Khu vực"),
    INDUSTRY_CODE(null, "Ngành nghề"),

    EDUCATION(null, "Học vấn"),
    EDUCATION_SCHOOL(EDUCATION, "Trường"),
    EDUCATION_DEGREE(EDUCATION, "Bằng cấp"),
    EDUCATION_MAJOR(EDUCATION, "Chuyên ngành"),
    EDUCATION_START_DATE(EDUCATION, "Thời gian bắt đầu"),
    EDUCATION_END_DATE(EDUCATION, "Thời gian kết thúc"),
    EDUCATION_GPA(EDUCATION, "GPA"),

    EXPERIENCE(null, "Kinh nghiệm"),
    EXPERIENCE_COMPANY(EXPERIENCE, "Tên công ty"),
    EXPERIENCE_TITLE(EXPERIENCE, "Chức danh"),
    EXPERIENCE_START_DATE(EXPERIENCE, "Thời gian bắt đầu"),
    EXPERIENCE_END_DATE(EXPERIENCE, "Thời gian kết thúc"),
    EXPERIENCE_DESCRIPTION(EXPERIENCE, "Mô tả"),

    SKILLS(null, "Kỹ năng"),

    CERTIFICATIONS(null, "Chứng chỉ"),
    CERTIFICATION_NAME(CERTIFICATIONS, "Tên chứng chỉ"),
    CERTIFICATION_ISSUER(CERTIFICATIONS, "Đơn vị cấp"),
    CERTIFICATION_ISSUE_DATE(CERTIFICATIONS, "Thời gian cấp"),

    PROJECTS(null, "Dự án"),
    PROJECT_NAME(PROJECTS, "Tên dự án"),
    PROJECT_DESCRIPTION(PROJECTS, "Mô tả"),
    PROJECT_TECHNOLOGIES(PROJECTS, "Công nghệ"),

    VERSION_LABEL(null, "Tên phiên bản");

    private final CvBuilderField block;
    private final String displayName;

    CvBuilderField(CvBuilderField block, String displayName) {
        this.block = block;
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    // O khong nam trong danh sach, hoac ca khoi (vd "Học vấn tối đa 10 mục.").
    public Path at() {
        return new Path(this, null, null);
    }

    // Muc thu itemNumber (1-based) cua khoi, hoac o cua muc do; voi SKILLS la phan tu thu itemNumber.
    public Path at(int itemNumber) {
        return new Path(this, itemNumber, null);
    }

    // Phan tu thu elementNumber cua o danh sach trong muc (chi PROJECT_TECHNOLOGIES).
    public Path at(int itemNumber, int elementNumber) {
        return new Path(this, itemNumber, elementNumber);
    }

    // Vi tri cua mot o trong form; subject() la phan "{O}"/"{Muc}" cua cau loi R-F11. Chi ghep tu nhan co dinh va so
    // thu tu - khong bao gio chua gia tri nguoi dung da go.
    public record Path(CvBuilderField field, Integer itemNumber, Integer elementNumber) {

        public Path {
            if (field == null) {
                throw new IllegalArgumentException("field null");
            }
            if (field.block != null && itemNumber == null) {
                throw new IllegalArgumentException("O trong khoi danh sach can so thu tu muc: " + field);
            }
            if (elementNumber != null && itemNumber == null) {
                throw new IllegalArgumentException("elementNumber can itemNumber: " + field);
            }
            if ((itemNumber != null && itemNumber < 1) || (elementNumber != null && elementNumber < 1)) {
                throw new IllegalArgumentException("So thu tu bat dau tu 1: " + field);
            }
        }

        public String subject() {
            if (field.block == null) {
                return itemNumber == null ? field.displayName : field.displayName + " " + itemNumber;
            }
            String inItem = field.block.displayName + " " + itemNumber + ": " + field.displayName;
            return elementNumber == null ? inItem : inItem + " " + elementNumber;
        }
    }
}
