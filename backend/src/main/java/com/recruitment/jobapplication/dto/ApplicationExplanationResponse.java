package com.recruitment.jobapplication.dto;

import java.time.Instant;
import java.util.UUID;

// FR-H09 E4 - GET /api/hr/applications/{id}/explanation: bao cao FR-H06 cua CUNG lot DONE ma E3 dang
// hien diem (R-D6) - scoringRunId trung voi E3, frontend khong ghep giai thich lot nay voi diem lot
// khac. explanationStatus giu dung quy tac danh sach theo Job: chi khac null khi co lot DONE ma chua
// co giai thich (PENDING, hoac FAILED khi da thu >= app.explanation.max-attempts). scoredAt =
// finished_at cua chinh lot do (trung scoredAt cua E3) - tab Giai thich hien ngay ma khong phai goi E3.
// Moi field null khi don chua co lot DONE nao. Day la bao cao mo ta, khong phai phan quyet - khong co field
// verdict/label/... (CLAUDE.md muc 7).
public record ApplicationExplanationResponse(
        UUID scoringRunId,
        Instant scoredAt,
        ApplicationHrListItemResponse.ExplanationStatus explanationStatus,
        ApplicationHrListItemResponse.Explanation explanation) {
}
