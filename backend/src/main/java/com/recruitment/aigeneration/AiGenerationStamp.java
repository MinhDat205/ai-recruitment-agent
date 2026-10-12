package com.recruitment.aigeneration;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

// K4 (FR-H11 R-K4-1) - sieu du lieu cua MOT ket qua AI duoc luu: model, phien ban prompt, thoi diem sinh. Dung chung cho
// moi FR luu ket qua AI (H11, H13, H15): bang cua FR do nhung record nay (3 cot ai_model, ai_prompt_version,
// ai_generated_at) - K4 khong biet FR nao dung no, FR sau khong phai sua file nay.
//
// Gia tri KHONG BAO GIO lay tu request cua client: hoac do backend dung ngay luc sinh (cung request), hoac lay tu token
// da ky (AiGenerationTokenCodec) khi sinh va luu o hai request khac nhau (R-K4-2).
@Embeddable
public record AiGenerationStamp(
        @Column(name = "ai_model", nullable = false, updatable = false, length = 100) String model,
        @Column(name = "ai_prompt_version", nullable = false, updatable = false, length = 100) String promptVersion,
        @Column(name = "ai_generated_at", nullable = false, updatable = false) Instant generatedAt) {

    public AiGenerationStamp {
        requireNotBlank(model, "model");
        requireNotBlank(promptVersion, "promptVersion");
        Objects.requireNonNull(generatedAt, "generatedAt");
    }

    private static void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " khong duoc rong");
        }
    }
}
