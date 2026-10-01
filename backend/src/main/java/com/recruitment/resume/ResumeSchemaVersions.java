package com.recruitment.resume;

// Phien ban schema cua mot ban ghi resume_parsed_data = cot prompt_version (FR-C05 R-C1, khong them
// cot rieng). Chuoi phien ban MOI NHAT khai o ResumeParsingService.PROMPT_VERSION (mot cho, gan ten
// file .st); o day chi giu ten phien ban CU can so sanh (dieu kien trich xuat lai, R-R2) va phep quy
// doi chuoi -> so cho API (schemaVersion 1|2).
public final class ResumeSchemaVersions {

    static final String V1_PROMPT_VERSION = "resume-parse-v1";

    private static final String PROMPT_VERSION_PREFIX = "resume-parse-v";

    private ResumeSchemaVersions() {
    }

    // "resume-parse-v1" -> 1, "resume-parse-v2" -> 2; null hoac chuoi khong dung dang -> null.
    public static Integer of(String promptVersion) {
        if (promptVersion == null || !promptVersion.startsWith(PROMPT_VERSION_PREFIX)) {
            return null;
        }
        try {
            return Integer.valueOf(promptVersion.substring(PROMPT_VERSION_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean isV1(String promptVersion) {
        return V1_PROMPT_VERSION.equals(promptVersion);
    }
}
