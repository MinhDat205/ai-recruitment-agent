---
name: srs-guard
description: Soát codebase tìm vi phạm các nguyên tắc thiết kế bắt buộc trong SRS của dự án AI Recruitment Agent (AI không được gán nhãn đậu/rớt, backend tính tổng điểm, không hard delete, consent bắt buộc). Dùng khi người dùng yêu cầu "kiểm tra vi phạm SRS", "soát ranh giới AI", "review trước khi merge", hoặc trước khi merge một nhánh thuộc Phase D và E.
---

# Soát vi phạm nguyên tắc SRS

Bảy nguyên tắc dưới đây là ràng buộc cứng của dự án. Vi phạm là lỗi nghiêm trọng, không phải góp ý phong cách.

Với mỗi mục: chạy lệnh tìm kiếm, đọc kết quả, đánh giá **có vi phạm thật không** (có thể là dương tính giả),
rồi báo cáo theo mẫu ở cuối.

## 1. AI không được gán nhãn đậu/rớt (FR-H05, FR-H07)

Tìm cột, field, biến, enum có tên gợi ý phán quyết:

```bash
rg -i "verdict|isQualified|is_qualified|passed|rejected_by_ai|recommendation_label|suitability" --type java --type ts --type sql
```

Vi phạm nếu: tồn tại trường lưu kết luận đạt/không đạt do hệ thống sinh ra.
Không vi phạm nếu: đó là `job_applications.status` do HR đổi tay.

## 2. AI không tính tổng điểm (FR-H04 vs FR-H05)

```bash
rg "total_score|totalScore" backend/src/main/java/com/recruitment/ai/
rg "ScoreAggregator" backend/src/main/java/com/recruitment/ai/
```

Vi phạm nếu: package `ai/` ghi vào `total_score`, hoặc import `ScoreAggregator`.
Tổng điểm chỉ được tính trong `scoring/ScoreAggregator.java` bằng Java thuần.

## 3. Chấm từng tiêu chí riêng lẻ (FR-H04)

Đọc `backend/src/main/java/com/recruitment/ai/criterion/`.

Vi phạm nếu: một lần gọi LLM trả về điểm của nhiều tiêu chí cùng lúc.
Đúng: mỗi tiêu chí một lần gọi, input là CV JSON + đúng một tiêu chí.

## 4. Không có ngưỡng phân loại (FR-H05)

```bash
rg -i "if.*score.*>=|threshold|nguong|phan loai" --type java --type ts
```

Vi phạm nếu: có ngưỡng chia ứng viên thành nhóm, hoặc frontend đổi màu điểm theo ngưỡng
(ví dụ điểm cao màu xanh, điểm thấp màu đỏ).
Đúng: thanh tiến trình đơn sắc, số điểm không đổi màu.

## 5. Mọi điểm phải có evidence (FR-H06)

```bash
rg "evidence" backend/src/main/java/com/recruitment/
```

Vi phạm nếu: `criterion_scores.evidence` được ghi mảng rỗng, hoặc UI hiển thị điểm mà không mở ra
được đoạn trích từ CV.

## 6. Không hard delete (FR-U06)

```bash
rg "\.delete\(|deleteById|DELETE FROM" --type java --type sql
```

Vi phạm nếu: xoá bản ghi `job_applications`, `resumes`, `scoring_runs`, `criterion_scores`.
Đúng: đổi `status` sang `WITHDRAWN`, hoặc set `deleted_at` với `jobs`.
Dương tính giả thường gặp: xoá `rubric_criteria` khi HR sửa rubric — cái này được phép.

## 7. Consent bắt buộc, không tick sẵn (FR-U02)

```bash
rg -i "ai_consent|aiConsent|consent" --type java --type ts --type tsx
```

Vi phạm nếu: frontend đặt `defaultChecked` / `checked={true}` cho checkbox consent,
hoặc backend cho tạo đơn với `ai_consent = false`.

## 8. Snapshot rubric không bị bỏ (FR-H08)

```bash
rg "weight_snapshot|weightSnapshot|rubric_snapshot|rubricSnapshot" --type java
```

Vi phạm nếu: code đọc trọng số trực tiếp từ `rubric_criteria` khi hiển thị lịch sử đánh giá,
thay vì đọc `weight_snapshot` đã lưu. Sửa trọng số sau này sẽ làm hỏng toàn bộ lịch sử.

---

## Mẫu báo cáo

```
## Kết quả soát SRS — nhánh <tên nhánh>

### Vi phạm (phải sửa trước khi merge)
- [Nguyên tắc N] <mô tả> — `<file>:<dòng>`
  Cách sửa: <đề xuất cụ thể>

### Cần xem lại (nghi ngờ, cần người quyết định)
- ...

### Đã kiểm tra, không vi phạm
- Nguyên tắc 1, 2, 4, 6 ✓
```

Nếu không tìm thấy vi phạm nào, nói rõ đã kiểm tra hết 8 mục và liệt kê từng mục — đừng chỉ nói
"không có vấn đề gì".
