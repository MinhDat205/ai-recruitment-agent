---
name: srs-guard
description: Soát codebase tìm vi phạm các nguyên tắc thiết kế bắt buộc trong SRS của dự án AI Recruitment Agent (AI không được gán nhãn đậu/rớt, backend tính tổng điểm, không hard delete, consent bắt buộc). Dùng khi người dùng yêu cầu "kiểm tra vi phạm SRS", "soát ranh giới AI", "review trước khi merge", hoặc trước khi merge một nhánh thuộc Phase D và E.
---

# Soát vi phạm nguyên tắc SRS

Chín nguyên tắc dưới đây là ràng buộc cứng của dự án. Vi phạm là lỗi nghiêm trọng, không phải góp ý phong cách.

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
Đúng: mỗi tiêu chí một lần gọi (`CriterionScoringService.score(criterion, rawText)` nhận đúng MỘT
`RubricSnapshot.CriterionSnapshot`), input là `raw_text` của CV + đúng một tiêu chí — **không phải
CV JSON** (`resume_parsed_data.data`). D2 cố ý không dùng CV JSON làm input chấm điểm: nếu D1 đã
diễn giải lại nội dung khi trích xuất, evidence trích từ JSON đó sẽ là lời của LLM chứ không phải
lời trong CV gốc, làm sụp nguyên tắc evidence phải kiểm chứng được (xem CLAUDE.md mục 2). Kiểm
chứng evidence vì vậy cũng luôn thực hiện với `raw_text` đầy đủ, không phải bản đã cắt ngưỡng hay
CV JSON.

## 4. Không có ngưỡng phân loại (FR-H05)

```bash
rg -i "if.*score.*>=|threshold|nguong|phan loai" --type java --type ts
```

Vi phạm nếu: có ngưỡng chia ứng viên thành nhóm, hoặc frontend đổi màu điểm theo ngưỡng
(ví dụ điểm cao màu xanh, điểm thấp màu đỏ).
Đúng: thanh tiến trình đơn sắc, số điểm không đổi màu.

**Soát frontend cụ thể hơn** (đây là chỗ vi phạm khó thấy nhất, vì nó trông như thiết kế đẹp):
tìm mọi component badge/màu liên quan tới điểm hoặc tiến độ chấm (ví dụ
`*StatusBadge.tsx`, hoặc bất kỳ nơi nào render điểm số), rồi đọc trực tiếp biểu thức **chọn màu**
(class/token màu, không phải toàn bộ component) — kiểm xem biểu thức đó có nhận `score`,
`criteriaScored`/`criteriaTotal`, hay bất kỳ giá trị số nào liên quan tới điểm làm tham số quyết
định màu hay không. Nếu chọn màu chỉ phụ thuộc **trạng thái xử lý** (PENDING/RUNNING/FAILED...) thì
không vi phạm, kể cả khi cùng chỗ đó có hiển thị con số.

Dương tính giả hay gặp: một badge có label dạng "Đã chấm 3/5 tiêu chí" — con số `3/5` xuất hiện
trong **chuỗi text**, không phải trong biểu thức chọn `className`/token màu. Phải tách riêng hai
việc: đọc số liệu để hiển thị (được phép) và dùng số liệu để chọn màu (cấm) — đọc đúng dòng code
chọn màu, đừng kết luận vi phạm chỉ vì thấy biến điểm số xuất hiện gần đó.

## 5. Mọi điểm phải có evidence (FR-H06)

```bash
rg "evidence" backend/src/main/java/com/recruitment/
```

Vi phạm nếu: `criterion_scores.evidence` được ghi mảng rỗng **khi `score` khác 0**, hoặc UI hiển
thị điểm mà không mở ra được đoạn trích từ CV.

**Không vi phạm:** evidence rỗng khi `score = 0` — đây là trường hợp hợp lệ đã chốt ở D2 (CV thực
sự không có thông tin liên quan tới tiêu chí thì phải chấm đúng 0 điểm và để evidence rỗng, không
được bịa). Quy tắc chính xác là **evidence rỗng CHỈ hợp lệ khi và chỉ khi `score = 0`** — rỗng mà
`score` khác 0 mới là vi phạm.

Quy tắc này được thực thi ở **tầng Java**, không chỉ nằm trong prompt — kiểm ở
`CriterionScoringService.validate()` (`if (evidence.isEmpty() && score != 0) throw
CriterionScoringErrorCode.EVIDENCE_MISSING_WITH_NONZERO_SCORE`). Đường ghi DB duy nhất
(`ScoringRunStateService.recordCriterionScore`) chỉ nhận kết quả đã qua `validate()` — soát ở đây
là soát code Java thật, không phải suy luận qua nội dung prompt.

## 6. Không hard delete (FR-U06)

```bash
rg "\.delete\(|deleteById|DELETE FROM" --type java --type sql
```

Vi phạm nếu: xoá bản ghi `job_applications`, `resumes`, `scoring_runs`, `criterion_scores`.
Đúng: đổi `status` sang `WITHDRAWN`, hoặc set `deleted_at` với `jobs`.
Dương tính giả thường gặp: xoá `rubric_criteria` khi HR sửa rubric — cái này được phép.

## 7. Consent bắt buộc, không tick sẵn (FR-U02)

```bash
rg -i "ai_consent|aiConsent|consent" --type java --type ts
```

(`--type ts` của ripgrep đã gồm cả `.tsx` — xác nhận bằng `rg --type-list | grep '^ts:'`. Không có
type `tsx` riêng; thêm `--type tsx` sẽ làm lệnh báo lỗi "unrecognized file type".)

Vi phạm nếu: frontend đặt `defaultChecked` / `checked={true}` cho checkbox consent,
hoặc backend cho tạo đơn với `ai_consent = false`.

## 8. Snapshot rubric không bị bỏ (FR-H08)

```bash
rg "weight_snapshot|weightSnapshot|rubric_snapshot|rubricSnapshot" --type java
```

Vi phạm nếu: code đọc trọng số trực tiếp từ `rubric_criteria` khi hiển thị lịch sử đánh giá,
thay vì đọc `weight_snapshot` đã lưu. Sửa trọng số sau này sẽ làm hỏng toàn bộ lịch sử.

## 9. Ràng buộc duy nhất phải có chốt chặn ở DB, không chỉ SELECT-trước-INSERT

```bash
rg -i "existsBy|findBy.*IsNull|SELECT.*COUNT" --type java -g "*Service.java" -g "*StateService.java"
```

Vi phạm nếu: một ràng buộc "chỉ được có một X đang hoạt động" (một đơn ứng tuyển đang PENDING cho
mỗi chu kỳ, một lượt chấm đang chạy cho mỗi đơn...) chỉ được kiểm bằng cách SELECT/`existsBy...`
rồi mới INSERT, **không có** unique index/constraint nào ở DB đứng sau làm chốt chặn cuối cùng.
SELECT-trước-INSERT ở tầng service luôn có khe hở race condition (hai request đến gần như đồng
thời đều thấy "chưa tồn tại" trước khi cả hai cùng INSERT).

Đúng: SELECT-trước-INSERT chỉ đóng vai trò trả lỗi sớm, thân thiện (409 có message rõ ràng) —
chốt chặn THẬT SỰ phải là một partial unique index hoặc constraint ở DB, bắt được cả trường hợp
race mà tầng Java bỏ lọt. Hai tiền lệ đã có trong dự án:
- `uq_application_per_cycle` (C2, `ApplicationService.apply`) — mỗi ứng viên chỉ một đơn PENDING
  cho mỗi `(job_id, recruitment_cycle)`.
- `uq_scoring_run_in_progress` (V4, D2, `ScoringRunService.requireNoRunInProgress` +
  `ScoringRunStateService.create`) — mỗi đơn chỉ một lượt chấm đang PENDING/RUNNING với
  `finished_at IS NULL`.

Cả hai đều dùng `saveAndFlush` để buộc INSERT chạy ngay trong phạm vi request, giúp
`DataIntegrityViolationException` của index nổi lên và được `GlobalExceptionHandler` bắt thành
409 xác định — không phải để tầng Java "đoán trước" race, mà để DB tự bắt và Java dịch lỗi đó
sang response thân thiện.

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

Nếu không tìm thấy vi phạm nào, nói rõ đã kiểm tra hết 9 mục và liệt kê từng mục — đừng chỉ nói
"không có vấn đề gì".
