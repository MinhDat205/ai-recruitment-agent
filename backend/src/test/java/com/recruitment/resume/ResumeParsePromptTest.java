package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

// Test thuan Java, khong can Spring context - chi doc file prompt that tu classpath va kiem noi
// dung. Ly do can test nay: suite van xanh y het truoc/sau khi sua prompt, nghia la khong co gi
// bao ve file .st nay - ai do "gon hoa" prompt cho ngan se pha vi pham nguyen tac SRS ma CI van
// xanh.
class ResumeParsePromptTest {

    @Test
    void promptFile_containsAllRequiredConstraints() throws IOException {
        String prompt;
        String resourcePath = "/ai/prompt/" + ResumeParsingService.PROMPT_VERSION + ".st";
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Khong tim thay file prompt tren classpath: " + resourcePath);
            }
            prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // "never fabricate" - rang buoc SRS quan trong nhat cua D1: AI chi duoc doc va cau truc
        // hoa, khong duoc suy dien thong tin khong co trong CV. Mat cum nay = mat chan bia dat.
        assertThat(prompt).contains("never fabricate");

        // "close to verbatim" - field description phai lay nguyen van tu CV, khong duoc LLM tu
        // viet lai bang loi cua no. Day la nen mong de D2 sau nay trich evidence dung nguyen van
        // tu CV - neu D1 da dien giai lai noi dung o day, D2 khong con gi "nguyen van" de trich.
        assertThat(prompt).contains("close to verbatim");

        // "Do not summarize" - lenh cam truc tiep viec tom tat/dien giai lai, phong truong hop
        // LLM bo qua huong dan "verbatim" o tren neu chi doc luot qua prompt.
        assertThat(prompt).contains("Do not summarize");

        // "Preserve the original language" - cam dich CV tieng Viet sang tieng Anh. Neu D1 dich
        // "Ky su phan mem" thanh "Software Engineer", evidence o D2 se khong khop voi nguyen van
        // CV ma HR dang doc, mat kha nang kiem chung.
        assertThat(prompt).contains("Preserve the original language");

        // "do not convert it between scales" - cam LLM tu quy doi GPA giua cac thang diem (CV
        // Viet Nam dung ca thang 4.0 lan thang 10). gpa duoc khai la Double trong schema, neu LLM
        // tu quy doi de "gon" ve mot thang duy nhat, do la mot dang suy dien - vi pham chinh
        // nguyen tac cua prompt nay - va sai lech se chay xuong D2 lam evidence cho diem so.
        assertThat(prompt).contains("do not convert it between scales");

        // "{format}" - cho chen JSON schema tu BeanOutputConverter.getFormat(). Thieu placeholder
        // nay, LLM khong biet dung schema can tra ve, moi rang buoc con lai trong prompt tro thanh
        // vo nghia vi output khong con parse duoc theo ResumeParsedPayload.
        assertThat(prompt).contains("{format}");
    }

    // FR-C05 R-C1, R-C2, R-C5 va muc 5: prompt v2 giu luat v1 (test tren) va them rang buoc cho ba truong moi.
    @Test
    void promptV2_containsNewFieldRules() throws IOException {
        String prompt = readPrompt(ResumeParsingService.PROMPT_VERSION);

        assertThat(prompt).contains("{industries}");
        assertThat(prompt).contains("currentTitle").contains("industryCode").contains("locationText");
        assertThat(prompt).contains("copied verbatim");
        assertThat(prompt).contains("If you are not sure");
        assertThat(prompt).contains("do not convert an old");
        // locationText chi lay phan tinh/thanh, nguyen van (bo quan/huyen/phuong/duong) - de bo khop R-M3
        // nhan ra ma ma KHONG phai noi long bo khop (khong tach dau phay). Vi du tinh cu giu nguyen ten cu.
        assertThat(prompt).contains("Copy ONLY the province or city part of the address, verbatim");
        assertThat(prompt).contains("leave out the district, ward");
        assertThat(prompt).contains("\"Quận 7, TP. Hồ Chí Minh\" -> \"TP. Hồ Chí Minh\"");
        assertThat(prompt).contains("\"Thủ Dầu Một, Bình Dương\" -> \"Bình Dương\"");
        assertThat(prompt).contains("does not clearly name a province or");
        // Khong hardcode ma nganh trong file .st - danh sach truyen vao qua {industries}.
        assertThat(prompt).doesNotContain("IT_SOFTWARE").doesNotContain("OTHER");
        // R-C5: cam them truong nhan than.
        assertThat(prompt).contains("Do not add any other personal fields");
    }

    // R-C1: giu file v1 lam lich su.
    @Test
    void promptV1_fileStillPresent() throws IOException {
        assertThat(readPrompt(ResumeSchemaVersions.V1_PROMPT_VERSION)).contains("{format}");
    }

    private String readPrompt(String version) throws IOException {
        String resourcePath = "/ai/prompt/" + version + ".st";
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Khong tim thay file prompt tren classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
