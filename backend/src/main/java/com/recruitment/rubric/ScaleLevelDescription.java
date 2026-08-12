package com.recruitment.rubric;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

// Cau truc co dinh cho scale_description (JSONB) thay vi Map tu do: moi phan tu la mo ta HR
// viet cho MOT muc diem cua tieu chi (vd level=1 "Chua co kinh nghiem", level=5 "Chuyen gia").
// D2 (FR-H04 scoring) doc List<ScaleLevelDescription> nay va serialize thang vao prompt LLM
// dang liet ke theo level, vi du:
//   Thang diem rieng cho tieu chi nay:
//   - Muc 1: Chua co kinh nghiem
//   - Muc 5: Chuyen gia
// Neu scale_description = NULL, D2 dung mot doan mo ta thang mac dinh dung chung cho moi tieu
// chi khong khai bao rieng (FR-H03: "NULL => he thong dung thang diem mac dinh dung chung").
public record ScaleLevelDescription(@Positive int level, @NotBlank String description) {
}
