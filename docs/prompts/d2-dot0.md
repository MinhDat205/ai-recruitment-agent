/fr-implement

Làm FR-H04 (AI Rubric Scoring), nhánh feat/fr-h04-scoring, xếp chồng trên feat/fr-c04-parsing.

LƯỢT 0 NÀY CHỈ LẬP KẾ HOẠCH. Không sửa file, không viết code. Kết thúc bằng bản kế
hoạch + phần trả lời 6 quyết định thiết kế bên dưới, rồi DỪNG chờ tôi duyệt.

## Đọc trước khi lập kế hoạch

Ngoài thứ tự bắt buộc của skill fr-implement, đọc thêm — đây là các file D2 phải khớp
hoặc phải sửa, đừng đoán nội dung:
- backend/src/main/java/com/recruitment/resume/ResumeParsingOrchestrator.java,
  ResumeParsingStateService.java, ResumeParsingScheduler.java, ResumeParsingService.java
  → D2 lặp lại đúng kiến trúc này cho scoring, không phát minh kiểu khác.
- backend/src/main/java/com/recruitment/resume/ResumeParsedPayload.java,
  ResumeParsedData.java, ResumeParsedDataRepository.java → nguồn dữ liệu vào của D2.
- backend/src/main/java/com/recruitment/rubric/ (Rubric, RubricCriterion,
  ScaleLevelDescription, RubricCriterionRepository, RubricOwnerService)
- backend/src/main/java/com/recruitment/job/JobOwnerService.java — riêng method changeStatus
  và phần kiểm rubric đủ 100%.
- backend/src/main/java/com/recruitment/jobapplication/ (JobApplication, repository, service)
- backend/src/test/java/com/recruitment/resume/LlmTestConfiguration.java và
  backend/src/test/resources/application-test.yml
- .claude/skills/srs-guard/SKILL.md — mục 2, 3, 5 nói thẳng nhánh này sẽ bị soát ở đâu.
- V1__init_schema.sql: hai bảng scoring_runs và criterion_scores, đọc cả comment trong file.

## Phạm vi

Đúng một mã FR: FR-H04. KHÔNG làm hộ D3 (không tính total_score, không xếp hạng, không
tạo ScoreAggregator) và KHÔNG làm hộ D4 (không sinh báo cáo giải thích).

Ngoại lệ duy nhất, và nó bắt buộc: khi tạo lượt chấm ĐẦU TIÊN cho một job, phải set
rubrics.is_locked = true. Lý do: JobOwnerService.changeStatus đang dựa vào cờ này để bỏ
qua kiểm rubric đủ 100% khi HR mở lại tin; hiện không có đường code nào trong ứng dụng
set cờ đó. Nếu D2 không cài, HR có job đã chấm sẽ kẹt vĩnh viễn không mở lại được chu kỳ
tuyển dụng mới. Đây không phải "tiện tay làm thêm", đây là mảnh còn thiếu của chính D2
(PHASES B3: "Khoá rubric khi đã có lượt chấm đầu tiên").

## Ràng buộc kiến trúc — không được đi chệch

1. Chia package theo tính năng, và bám đúng đường dẫn mà srs-guard sẽ soát:
   - com.recruitment.scoring — entity ScoringRun, CriterionScore, repository, state service,
     orchestrator, scheduler, service HR, controller, DTO.
   - com.recruitment.ai.criterion — riêng phần gọi LLM: service chấm MỘT tiêu chí, record
     schema output, mã lỗi chuẩn hoá.
   - Package ai/ TUYỆT ĐỐI không ghi vào total_score, không import gì thuộc D3.

2. Gọi LLM RIÊNG CHO TỪNG TIÊU CHÍ. Một lần gọi = CV JSON + đúng MỘT tiêu chí + thang
   điểm của tiêu chí đó. Cấm gộp nhiều tiêu chí vào một lần gọi để tiết kiệm token —
   đây là vi phạm trực tiếp nguyên tắc của FR-H04, không phải tối ưu.

3. Transaction: lặp đúng mẫu của D1. Orchestrator KHÔNG @Transactional. Mọi method ghi DB
   nằm ở bean riêng (ví dụ ScoringRunStateService) và được inject vào — self-invocation
   không đi qua proxy Spring. Không bao giờ giữ transaction bao quanh lời gọi LLM: một
   lượt chấm có N tiêu chí là N lời gọi, giữ connection suốt N lần là cạn pool chắc chắn.

4. Claim bản ghi bằng UPDATE có điều kiện (WHERE id = ? AND status = 'PENDING'), kiểm số
   dòng ảnh hưởng, @Modifying(clearAutomatically = true). Không SELECT FOR UPDATE SKIP LOCKED.

5. Prompt đặt ở backend/src/main/resources/ai/prompt/, đánh version trong tên file
   (đề xuất criterion-score-v1.st). Hằng số PROMPT_VERSION khai một chỗ duy nhất, gắn liền
   tên file — giống hệt cách ResumeParsingService đang làm.

6. Output LLM parse qua BeanOutputConverter theo record cố định, retry đúng 1 lần khi JSON
   hỏng, rồi mới FAILED. Dùng .call().responseEntity(converter), không dùng .entity().

7. Snapshot là bắt buộc, không phải dữ liệu thừa:
   - scoring_runs.rubric_snapshot: chụp toàn bộ rubric (tên tiêu chí, mô tả, trọng số,
     max_score, scale_description) tại thời điểm tạo lượt chấm.
   - criterion_scores: ghi weight_snapshot, max_score_snapshot, criterion_name_snapshot.
     Cấm đọc thẳng từ rubric_criteria lúc tổng hợp. HR sửa trọng số sau này không được
     làm đổi con số của lượt chấm cũ.

8. scoring_runs.total_score phải vẫn NULL sau khi D2 chạy xong. LLM không được chạm cột này.

9. error_message của scoring_runs: chỉ lưu mã lỗi đã chuẩn hoá kèm câu mô tả tiếng Việt cố
   định, theo đúng mẫu enum ResumeParsingErrorCode.formatted(). Không lưu stack trace,
   không lưu output thô của LLM, không lưu nội dung CV — dữ liệu cá nhân chỉ vào log.debug.

10. Test không được gọi LLM thật. Dùng lại mẫu LlmTestConfiguration (mock ChatModel,
    default-answer throw). Nếu cần bean này ngoài package resume thì báo tôi cách bạn định
    tổ chức lại, đừng nhân bản file.

## Sáu quyết định thiết kế phải trả lời trong kế hoạch

Đây là phần quan trọng nhất của lượt 0. Với mỗi mục: nêu các phương án, chọn một, và nói
rõ đánh đổi. Không tự chốt rồi code luôn ở lượt sau nếu tôi chưa duyệt.

Q1 — Trạng thái cuối của lượt chấm sau D2.
PHASES giao "đổi status = DONE" cho D3, nhưng CHECK constraint chỉ cho 4 giá trị
PENDING/RUNNING/DONE/FAILED. Vậy sau khi D2 chấm xong toàn bộ tiêu chí, status nên là gì?
Nếu để RUNNING thì D3 nhặt bằng tiêu chí nào, và một lượt chấm sẽ trông "treo" ra sao khi
D3 chưa tồn tại? Nếu đặt DONE thì có mâu thuẫn với mô tả D3 không? Đừng thêm giá trị mới
vào CHECK constraint bằng migration nếu chưa cần — nếu bạn cho rằng cần, phải lập luận.

Q2 — Evidence rỗng khi ứng viên thực sự không có gì cho tiêu chí đó.
PHASES yêu cầu "mỗi tiêu chí có ít nhất một evidence trích từ CV". Nhưng nếu tiêu chí là
"Kinh nghiệm Docker" và CV không hề nhắc Docker, LLM không thể trích được câu nào mà
không bịa. Bịa evidence là vi phạm nặng hơn evidence rỗng. Đề xuất quy tắc xử lý và nói
rõ nó đứng vững thế nào trước câu hỏi của hội đồng.

Q3 — Chống bịa evidence bằng kiểm chứng cơ học.
Có nên kiểm tra mỗi quote thực sự là substring của resume_parsed_data.raw_text (sau khi
chuẩn hoá khoảng trắng) không? Nếu có: quote không khớp thì xử lý ra sao — bỏ quote đó,
coi cả lần gọi là hỏng và retry, hay đánh FAILED cả lượt? Nêu rủi ro dương tính giả
(LLM sửa dấu, gộp dòng bị PDFBox tách, CV tiếng Việt có dấu).

Q4 — Một tiêu chí hỏng thì cả lượt hỏng, hay chấm được bao nhiêu ghi bấy nhiêu?
Nhớ rằng D3 sẽ cộng theo trọng số trên chính các dòng criterion_scores này. Trả lời gắn
với hệ quả đó, đừng trả lời chung chung.

Q5 — Ranh giới frontend của D2.
Nút "Chấm điểm hồ sơ" cần một danh sách đơn ứng tuyển của job, mà hiện KHÔNG có endpoint
HR nào liệt kê đơn theo job (đã kiểm: /api/hr/jobs/... chỉ có job, rubric, interview-template).
D3 mới là nhánh sở hữu danh sách xếp hạng (GET /api/jobs/{id}/candidates?sort=total_score).
Vậy D2 nên thêm danh sách tối thiểu không xếp hạng, hay lùi toàn bộ frontend sang D3?
Nêu rõ cái nào để lại nợ kỹ thuật ít hơn.

Q6 — Thời điểm và tính bất khả nghịch của việc khoá rubric.
Set rubrics.is_locked = true ở đúng thời điểm nào: lúc tạo bản ghi scoring_runs (status
PENDING), lúc orchestrator claim được lượt chấm (PENDING → RUNNING), hay sau khi ghi
xong dòng criterion_scores đầu tiên? Nêu đánh đổi của từng phương án.

Ba tình huống bắt buộc phải trả lời:
1. Lượt chấm kết thúc FAILED (LLM lỗi, evidence không kiểm chứng được). Rubric có được
   mở khoá lại không? Nếu KHÔNG mở: HR bị chặn sửa tiêu chí chỉ vì một lần gọi API hỏng,
   trong khi chưa có dòng criterion_scores nào để bảo vệ — có chấp nhận được không?
   Nếu CÓ mở: viết rõ điều kiện an toàn (ví dụ chỉ mở khi rubric này chưa từng có lượt
   chấm nào ghi được criterion_scores), vì mở nhầm là hỏng nguyên tắc snapshot của FR-H04.
2. Chấm lượt thứ hai cho cùng job (HR chấm thêm ứng viên mới). Thao tác set cờ phải
   idempotent, không được lỗi vì rubric đã khoá sẵn.
3. Rubric đã khoá thì RubricOwnerService.requireNotLocked chặn HR sửa tiêu chí —
   đây là hành vi ĐÚNG theo FR-H03, không phải bug cần né. Xác nhận bạn hiểu điều này
   và không tự nới lỏng requireNotLocked.

Việc khoá là một câu ghi DB, phải nằm trong bean ghi có @Transactional (ScoringRunStateService
hoặc tương đương), không đặt trong orchestrator. Nêu rõ nó cùng transaction với câu ghi
nào — nếu tạo scoring_runs thành công mà set is_locked thất bại (hoặc ngược lại), hệ
thống rơi vào trạng thái nào?

Cuối cùng: test bắt buộc có ít nhất case dương (chấm lần đầu → is_locked chuyển true),
case âm (rubric đã khoá sẵn → tạo lượt chấm vẫn thành công, không ném lỗi), và một test
đi qua JobOwnerService.changeStatus chứng minh job đã chấm mở lại được OPEN mà không bị
guard 100% chặn — đây chính là lý do tồn tại của cả yêu cầu này.

## Điều kiện tiên quyết phải kiểm ở tầng service (nêu trong kế hoạch, kèm mã lỗi và câu tiếng Việt)

Trước khi tạo được một lượt chấm:
- Đơn ứng tuyển tồn tại VÀ thuộc job của công ty do HR đang đăng nhập sở hữu — kiểm cả
  vai trò lẫn quyền sở hữu bản ghi, theo mẫu loadOwnedRubric trong RubricOwnerService.
- ai_consent = true (DB đã có chk_consent_true nhưng service vẫn phải kiểm và trả lỗi dễ hiểu).
- CV của đơn đã parse xong: resumes.parse_status = DONE và tồn tại resume_parsed_data.
- Rubric của job có ít nhất một tiêu chí và tổng trọng số = 100%.
- Không có lượt chấm nào của chính đơn này đang PENDING/RUNNING.

Đề xuất tên exception + mã lỗi + câu tiếng Việt CÓ DẤU cho từng trường hợp, và mã HTTP
tương ứng trong GlobalExceptionHandler (409 hay 422 — chọn và giải thích). Tái sử dụng
RubricIncompleteException đã có nếu phù hợp, đừng tạo lớp trùng nghĩa.

## Chia đợt

Đề xuất cách chia thành 6 đợt nhỏ, mỗi đợt là một đơn vị review được và chạy mvn test
xanh được. Với mỗi đợt ghi rõ: file tạo mới, file sửa, test kèm theo (phải có CẢ case
dương và case âm; có ngưỡng số thì test biên ngưỡng−1 / đúng ngưỡng / ngưỡng+1).
Đợt cuối dành cho srs-guard + walkthrough + cập nhật ROADMAP.md và CLAUDE.md.

Tôi sẽ gửi từng đợt một, không gửi trước. Đừng bắt đầu code khi tôi chưa gửi prompt của
đợt tương ứng.

## Quy ước khi làm việc với tôi

- Trao đổi với tôi: tiếng Việt có dấu. Comment trong code: tiếng Việt không dấu. Chuỗi
  hiển thị cho người dùng: tiếng Việt có dấu. Tên class/method/biến/test: tiếng Anh.
- Cần signature thư viện thì javap trên jar thật trong ~/.m2, trích dẫn bằng chứng trong
  báo cáo. Không viết theo trí nhớ — tutorial Spring AI 1.x không dùng được trên bản 2.0.
- Validate bằng .\mvnw.cmd test chạy ĐẦY ĐỦ, không phải mvn compile, không chỉ class mới.
- Khi tôi bảo xem file, dán thẳng nội dung vào chat trong code block.
- Nếu yêu cầu của tôi mâu thuẫn với docs/SRS.md hoặc tôi nói sai về code hiện có, nói
  thẳng ra thay vì im lặng làm theo.
- Mỗi đợt kết thúc bằng: dừng → báo cáo diff → chờ tôi duyệt. Không tự commit.