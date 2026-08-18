/fr-implement

Làm FR-H06 (AI Explainable Scoring — Giải thích điểm số), nhánh feat/fr-h06-explain, xếp
chồng trên feat/fr-h05-aggregate.

ĐỢT 0 CHỈ LẬP KẾ HOẠCH. Không sửa file, không viết code. Kết thúc bằng bản kế hoạch + trả
lời 6 quyết định thiết kế, rồi DỪNG chờ tôi duyệt.

## Đọc trước khi lập kế hoạch

Ngoài thứ tự bắt buộc của skill fr-implement:
- docs/walkthrough/feat-fr-h04-scoring.md và feat-fr-h05-aggregate.md — toàn bộ. D4 ăn vào
  output của cả hai.
- backend/src/main/java/com/recruitment/ai/criterion/ — toàn bộ. D4 lặp lại khuôn này cho
  một bài toán khác.
- backend/src/main/java/com/recruitment/scoring/ — ScoringRun, CriterionScore,
  AggregationOrchestrator, AggregationScheduler, ScoringRunStateService.
- backend/src/main/java/com/recruitment/jobapplication/ApplicationOwnerService.java — D4 mở
  rộng endpoint này, không tạo route mới.
- frontend/src/features/scoring/CriterionScoreBreakdown.tsx — chỗ D3 hiện reasoning, D4 sẽ
  thêm evidence vào đây.
- V1__init_schema.sql: bảng score_explanations (đọc cả comment).

## Phạm vi

Đúng FR-H06. Không đụng công thức tính điểm (D3), không đụng cách chấm (D2).

Ràng buộc cứng nhất của nhánh này, đọc kỹ PHASES mục D4: **mọi luận điểm phải dẫn được về
evidence đã lưu ở D2 — KHÔNG sinh evidence mới.** LLM ở D4 chỉ được tổng hợp lại những gì
đã có trong criterion_scores (score, reasoning, evidence), không được đọc lại CV để trích
thêm câu nào. Đây là ranh giới quan trọng nhất và srs-guard sẽ soát.

Cũng cấm: kết luận nên tuyển hay không, xếp loại, so sánh với ứng viên khác.

## Sáu quyết định thiết kế phải trả lời

Q1 — Input gửi LLM.
D2 gửi raw_text (quyết định Q3 của D2, vì evidence phải kiểm chứng được với văn bản LLM
thực sự nhìn thấy). D4 KHÔNG được đọc CV. Vậy input là gì: danh sách criterion_scores
(tên tiêu chí, điểm, thang, trọng số, reasoning, evidence) — có gửi cả evidence không, hay
chỉ reasoning? Cân nhắc: gửi evidence giúp báo cáo dẫn chiếu chính xác, nhưng cũng cho LLM
cơ hội chế biến lại câu trích. Chọn một, lập luận.

Q2 — Chống sinh evidence mới bằng cơ chế gì.
D2 kiểm quote là substring của raw_text. D4 không có raw_text để đối chiếu. Nếu báo cáo D4
chứa một câu trích, làm sao biết nó có thật trong evidence đã lưu hay do LLM bịa? Đề xuất
cơ chế cụ thể — có thể là: cấm hẳn trích dẫn trong output D4 (chỉ tham chiếu theo tên tiêu
chí), hoặc kiểm chéo với evidence đã lưu, hoặc cách khác. Chọn và nói rõ nó chặn được gì.

Q3 — Schema output và cột DB.
Bảng score_explanations có summary (TEXT NOT NULL), strengths, weaknesses, met_criteria,
missing_criteria (đều JSONB mặc định []), model, prompt_version. Thiết kế record schema cho
LLM tương ứng. Bốn mảng JSONB chứa gì — chuỗi thuần, hay object có cấu trúc (ví dụ gắn với
criterionName)? Nhớ ràng buộc: mọi luận điểm phải dẫn về được tiêu chí cụ thể.

Q4 — Thời điểm chạy.
score_explanations có UNIQUE trên scoring_run_id, tức mỗi lượt chấm đúng một báo cáo. Sinh
lúc nào: poller riêng nhặt lượt DONE chưa có explanation, hay HR bấm nút yêu cầu? Cân nhắc
chi phí LLM (mỗi lượt thêm một lời gọi) và trải nghiệm HR. Nhớ D3 đã có tiền lệ poller
riêng và lý do của nó.

Q5 — Lượt chấm FAILED và lượt chưa DONE.
Chỉ sinh giải thích cho lượt DONE, hay cả lượt có một phần criterion_scores? Trả lời gắn
với hệ quả: một báo cáo tổng kết dựa trên dữ liệu thiếu sẽ nói sai về ứng viên.

Q6 — Frontend: D3 đang hiện reasoning, D4 thêm gì.
CriterionScoreBreakdown hiện đang hiện reasoning kèm nhãn "Diễn giải của AI cho tiêu chí
này — không phải khuyến nghị tuyển dụng". PHASES mục D4 yêu cầu: mỗi tiêu chí là khối
gập/mở, mở ra thấy đoạn trích nguyên văn từ CV, trích dẫn có viền trái màu brand nền
brand-light, và "không điểm nào hiển thị mà không mở ra được evidence".
Báo cáo tổng (summary/strengths/weaknesses) hiển thị ở đâu — cùng chỗ hay khu vực riêng?
Nhãn Q6 của D3 có cần đổi không khi giờ đã có evidence thật? Nêu rõ.

## Chia đợt

Đề xuất 5-6 đợt. Đợt cuối là srs-guard + walkthrough + cập nhật ROADMAP. Tôi gửi từng đợt
một, đừng bắt đầu code khi chưa có prompt của đợt tương ứng.