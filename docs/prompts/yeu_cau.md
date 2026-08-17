/fr-implement

Làm FR-H05 (Tổng hợp điểm & Xếp hạng ứng viên), nhánh feat/fr-h05-aggregate, xếp chồng trên
feat/fr-h04-scoring.

ĐỢT 0 CHỈ LẬP KẾ HOẠCH. Không sửa file, không viết code. Kết thúc bằng bản kế hoạch + trả
lời 6 quyết định thiết kế bên dưới, rồi DỪNG chờ tôi duyệt.

## Đọc trước khi lập kế hoạch

Ngoài thứ tự bắt buộc của skill fr-implement, đọc thêm — D3 ăn thẳng vào output của D2:
- docs/walkthrough/feat-fr-h04-scoring.md — toàn bộ. Đây là tài liệu D2 vừa sinh, giải
  thích mọi quyết định mà D3 phải tôn trọng.
- CLAUDE.md mục 2b — bảng ngữ nghĩa scoring_runs.finished_at. D3 là bên tiêu thụ hợp đồng
  này, đọc kỹ.
- backend/src/main/java/com/recruitment/scoring/ — toàn bộ package: ScoringRun,
  CriterionScore, hai repository, ScoringRunStateService, ScoringRunOrchestrator,
  ScoringRunService, RubricSnapshot.
- backend/src/main/java/com/recruitment/jobapplication/ApplicationOwnerService.java và
  ApplicationOwnerController.java — D3 mở rộng endpoint đã có ở đây, KHÔNG tạo route mới.
- frontend/src/features/scoring/ — tab "Ứng viên" của D2, D3 bổ sung cột vào đây.
- V1__init_schema.sql: scoring_runs (cột total_score, index idx_scoring_total),
  criterion_scores.

## Phạm vi

Đúng FR-H05. KHÔNG làm hộ D4 (không sinh báo cáo giải thích, không hiển thị evidence).

Ràng buộc cứng nhất của nhánh này: **Java thuần, KHÔNG gọi LLM ở bất kỳ đâu.** Tổng điểm là
phép cộng có trọng số xác định, kiểm chứng được bằng tay. Nhờ LLM tính "cho nhanh" là vi
phạm trực tiếp ranh giới FR-H04/FR-H05.

Công thức theo PHASES: total = Σ (score_i / max_score_i × weight_i), chuẩn hoá về thang 100.

## Ràng buộc đã chốt từ D2 — không được đi ngược

1. D3 nhặt lượt cần tổng hợp bằng:
   status = 'RUNNING' AND finished_at IS NOT NULL AND total_score IS NULL.
   Lượt FAILED không bao giờ được đụng tới, dù có bao nhiêu dòng criterion_scores.

2. Entity ScoringRun hiện CỐ Ý không map cột total_score (rào chắn cấu trúc để D2 không lỡ
   tay ghi). D3 là nhánh được phép thêm field đó. Comment trong ScoringRun.java đã ghi rõ
   điều này — đọc trước khi sửa.

3. Đọc weight_snapshot / max_score_snapshot từ criterion_scores, TUYỆT ĐỐI không query
   rubric_criteria sống. HR sửa trọng số sau này không được làm đổi tổng điểm đã tính.

4. Endpoint danh sách: mở rộng GET /api/hr/jobs/{jobId}/applications đã có (thêm tham số
   sắp xếp và field điểm vào DTO). KHÔNG tạo /api/jobs/{id}/candidates như PHASES viết —
   D2 đã chốt đường /api/hr/** vì SecurityConfig gate theo tiền tố này; lý do đầy đủ trong
   walkthrough D2.

5. Cấm tuyệt đối: cột/field tên verdict, label, isQualified, passed, recommendation; ngưỡng
   phân loại; tô màu điểm theo ngưỡng ở frontend. srs-guard sẽ soát mục 1 và 4.

## Sáu quyết định thiết kế phải trả lời

Với mỗi mục: nêu phương án, chọn một, nói rõ đánh đổi.

Q1 — Tổng hợp chạy ở đâu?
Ba hướng: (a) poller riêng của D3 quét các lượt thoả điều kiện ở mục 1; (b) gọi thẳng từ
ScoringRunOrchestrator ngay sau khi ghi xong tiêu chí cuối (phải sửa code D2 đã merge);
(c) tính khi HR mở màn hình xếp hạng, không lưu. Nhớ rằng total_score là cột có thật trong
schema và index idx_scoring_total dựng sẵn cho status='DONE' — schema nói gì về ý định gốc?

Q2 — Trạng thái sau khi tổng hợp.
PHASES giao D3 "đổi status = DONE". Xác nhận D3 set DONE, và trả lời: sau khi DONE thì
finished_at có ý nghĩa gì (đã set từ D2), có set lại không? Điều kiện tiên quyết #5 của D2
(chặn tạo lượt mới khi đang chạy) có bị ảnh hưởng không — kiểm cả predicate của
uq_scoring_run_in_packet V4 xem status DONE có nằm trong đó không.

Q3 — Kiểm tính toàn vẹn trước khi cộng.
Nếu số dòng criterion_scores khác số tiêu chí trong rubric_snapshot của chính lượt đó thì
làm gì? Theo thiết kế D2 điều này không xảy ra được (một tiêu chí lỗi là cả lượt FAILED),
nhưng D3 có nên kiểm phòng thủ không, và nếu lệch thì xử lý ra sao — bỏ qua lượt đó, đánh
FAILED, hay vẫn cộng? Trả lời gắn với hậu quả: một total_score sai mà trông như đúng là
sai số nguy hiểm nhất trong hệ thống này.

Q4 — Làm tròn và kiểu số.
Cột total_score là NUMERIC(6,3). score là NUMERIC(5,2), weight NUMERIC(5,2),
max_score_snapshot là int. Nêu rõ: dùng BigDecimal ở mọi bước hay có bước nào dùng double;
chia với scale bao nhiêu và RoundingMode nào; làm tròn ở từng tiêu chí rồi cộng, hay cộng
xong mới làm tròn một lần (hai cách cho kết quả khác nhau). Nếu tổng weight_snapshot của
một lượt khác 100 (về lý thuyết không xảy ra vì kiểm lúc tạo lượt, nhưng snapshot là dữ
liệu cũ) thì chuẩn hoá thế nào?

Q5 — Xếp hạng: nguồn điểm và quy tắc hoà.
Một đơn ứng tuyển có thể có NHIỀU lượt chấm (D2 cho phép chấm lại). Bảng xếp hạng lấy điểm
của lượt nào — mới nhất, hay lượt DONE mới nhất? Đơn chưa chấm hoặc lượt FAILED hiển thị
thế nào, xếp ở đâu trong danh sách? Hai ứng viên cùng total_score thì thứ hạng bằng nhau
hay xếp tuần tự, và tiêu chí phụ để thứ tự ổn định qua các lần tải trang là gì? Rank tính
bằng SQL window function hay Java — nêu đánh đổi.

Q6 — Ranh giới frontend: điểm trần trụi.
PHASES giao D3 hiển thị "điểm từng tiêu chí dạng cột". Nhưng CLAUDE.md mục 8 ghi: "Mọi
điểm số hiển thị phải kèm được evidence khi người dùng mở rộng — không có điểm trần trụi",
mà hiển thị evidence là việc của D4 (chưa làm). Hai tài liệu mâu thuẫn. Đề xuất cách xử lý
và lập luận — nhớ rằng nguyên tắc Explainable AI là thứ hội đồng sẽ hỏi, còn câu chữ trong
PHASES chỉ là mô tả kỹ thuật.

## Test bắt buộc (nêu trong kế hoạch)

PHASES yêu cầu unit test ScoreAggregator cho ít nhất 4 trường hợp biên: trọng số bình
thường, tiêu chí điểm 0, số lẻ làm tròn, tiêu chí bị xoá (criterion_id NULL nhưng snapshot
vẫn đủ). Bổ sung: một test tính tay được — cho sẵn 3 tiêu chí với số cụ thể, khẳng định kết
quả bằng con số viết ra trong test, để hội đồng có thể tự kiểm bằng máy tính bỏ túi.

Và test đối chứng nguyên tắc: đổi trọng số rubric sau khi đã chấm → tính lại → total_score
của lượt cũ KHÔNG đổi (vì đọc snapshot).

## Chia đợt

Đề xuất 5-6 đợt. Đợt cuối là srs-guard + walkthrough + cập nhật ROADMAP. Tôi gửi từng đợt
một, đừng bắt đầu code khi chưa có prompt của đợt tương ứng.