package com.recruitment.common.exception;

// FR-U07 R-F5/R-S3 - tham so loc Job cong khai sai: workMode/sort/postedWithin ngoai tap cho phep,
// hoac salaryMin/salaryMax am hay salaryMin > salaryMax. Cung khuon InvalidCatalogCodeException: mot
// class, nhieu factory method theo tung loai loi, thong diep tieng Viet co dinh.
public class InvalidJobFilterException extends RuntimeException {

    private InvalidJobFilterException(String message) {
        super(message);
    }

    public static InvalidJobFilterException invalidWorkMode() {
        return new InvalidJobFilterException("Hình thức làm việc không hợp lệ.");
    }

    public static InvalidJobFilterException invalidSort() {
        return new InvalidJobFilterException("Tiêu chí sắp xếp không hợp lệ.");
    }

    public static InvalidJobFilterException invalidPostedWithin() {
        return new InvalidJobFilterException("Khoảng thời gian đăng tin không hợp lệ.");
    }

    public static InvalidJobFilterException negativeSalary() {
        return new InvalidJobFilterException("Lương phải là số không âm.");
    }

    public static InvalidJobFilterException salaryMinGreaterThanMax() {
        return new InvalidJobFilterException("Lương tối thiểu phải nhỏ hơn hoặc bằng lương tối đa.");
    }

    // FR-U15 R-H4 - categoryCode/locationCode mo rong sang danh sach, sau khu trung van qua 3 ma.
    public static InvalidJobFilterException tooManyCategoryCodes() {
        return new InvalidJobFilterException("Chỉ được chọn tối đa 3 ngành nghề.");
    }

    public static InvalidJobFilterException tooManyLocationCodes() {
        return new InvalidJobFilterException("Chỉ được chọn tối đa 3 khu vực.");
    }
}
