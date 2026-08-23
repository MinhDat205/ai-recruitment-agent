/fr-implement

Làm nhánh `chore/hardening`. Đây là nhánh dọn nợ kỹ thuật, KHÔNG gắn mã FR nào, KHÔNG thêm tính năng mới cho người dùng cuối.

## Nguồn công việc

Toàn bộ danh sách nợ đã được ghi sẵn trong `ROADMAP.md`, mục "Hoàn thiện trước bảo vệ" → gạch đầu dòng `chore/hardening`. Đọc kỹ mục đó trước khi lập kế hoạch. Phạm vi gốc trong `PHASES.md` (mục "Sau Phase F") là: rate limit, xử lý lỗi LLM, theo dõi chi phí token, bảo mật file CV.

## Đợt 1 — Khảo sát, chưa viết code

Khảo sát và báo cáo lại cho tôi trước khi vào Plan Mode:

1. `ApplicationStatusService.changeStatus` (E1) — đọc nguyên văn method, xác nhận đúng là đọc `getStatus()` rồi `save()` không có điều kiện.
2. `ScoringRunRepository.finishAggregation` (D3) — đọc nguyên văn `@Query`, đây là khuôn mẫu sẽ áp dụng lại ở Đợt 2.
3. `ScoringRunRepository.findByApplicationIdOrderByCreatedAtDesc` (D2) và `findLatestDoneByApplicationIdIn` (D3) — liệt kê mọi nơi trong codebase đang gọi hai method này.
4. `ResumeParsingScheduler` / `ScoringScheduler` (hoặc tên tương đương) — cách claim bản ghi hiện tại, cột nào đánh dấu đang xử lý, có timestamp bắt đầu không.
5. Ràng buộc `uq_scoring_run_in_progress` trong migration V4 — điều kiện chính xác.
6. `ResumeParsingErrorCode`, `CriterionScoringErrorCode`, `ScoringRunErrorCode`, interface `common/FormattedErrorCode` — so sánh chữ ký.
7. Mọi nơi bắt exception quanh lời gọi `ChatClient`/`ChatModel` trong D1, D2, D4, F1, F2 — hiện đang bắt loại exception gì, map sang error code gì.
8. Cấu hình timeout hiện tại của Anthropic và OpenAI client trong `application.yml`.
9. Toàn bộ chuỗi hiển thị cho người dùng viết tiếng Việt KHÔNG DẤU trong backend (bắt đầu từ `JsonAuthenticationEntryPoint`, `GlobalExceptionHandler`, các entry point/handler khác trong filter chain).
10. Backend đã có dependency nào phục vụ rate limit chưa (Bucket4j, Resilience4j...). Nếu chưa có thì đề xuất một lựa chọn duy nhất kèm lý do.

Sau khảo sát, vào Plan Mode và trình plan cho 5 đợt dưới đây. Chờ tôi duyệt.

## Đợt 2 — Tính đúng đắn dữ liệu

- Sửa `ApplicationStatusService.changeStatus`: đổi sang `UPDATE job_applications SET status = :new WHERE id = :id AND status = :old`, kiểm số dòng ảnh hưởng, 0 dòng thì ném lỗi nghiệp vụ rõ ràng (409). Dùng đúng khuôn mẫu `ScoringRunRepository.finishAggregation` của D3. Đây là lỗi lost-update thật, hai tab HR hoặc double-click đều có thể ghi đè nhau và để lại hai dòng `application_status_history` mâu thuẫn.
- Thêm khóa cuối duy nhất `, id DESC` cho `findByApplicationIdOrderByCreatedAtDesc` và cho `DISTINCT ON` trong `findLatestDoneByApplicationIdIn`.
- Đảo thứ tự `requireOwnCompany` chạy TRƯỚC khi tra tài nguyên ở `ApplicationStatusService.loadOwnedApplication` (E1) và `ApplicationOwnerService.loadOwnedJob` (D3), để HR chưa tạo hồ sơ công ty nhận đúng `COMPANY_NOT_FOUND` thay vì `APPLICATION_NOT_FOUND`/`JOB_NOT_FOUND`. `ScoringRunAuditService` (F3) đã đúng thứ tự, dùng nó làm mẫu.

Test bắt buộc đợt này:
- Test hai lần gọi `changeStatus` tuần tự từ cùng một trạng thái gốc: lần hai phải thất bại, không phải last-write-wins.
- Chạy lại toàn bộ test của D2, D3, D4 vì đổi `ORDER BY` có thể vỡ kỳ vọng thứ tự đã ghi trong test cũ. Báo cáo chính xác test nào vỡ và vì sao.

## Đợt 3 — Phục hồi luồng nền

Vấn đề: JVM restart giữa chừng làm `resumes.parse_status = PROCESSING` và `scoring_runs` ở `RUNNING` kẹt vĩnh viễn. Riêng `scoring_runs` còn bị `uq_scoring_run_in_progress` chặn cứng, HR không tạo được lượt chấm mới cho đơn đó.

- Thêm stale-claim reaper cho D1 và D2: quét bản ghi đang ở trạng thái xử lý quá `app.hardening.stale-timeout` (mặc định 15 phút, đọc từ config, không hardcode), chuyển sang `FAILED` kèm error code riêng nói rõ nguyên nhân là timeout/khởi động lại, không phải lỗi nội dung CV.
- Reaper phải dựa vào một timestamp thật của lần claim. Nếu bảng chưa có cột phù hợp thì dùng cột sẵn có (`started_at`/`updated_at`) — KHÔNG tự thêm cột vào entity nếu schema chưa có; nếu bắt buộc phải thêm cột thì viết migration Flyway mới và báo cho tôi trước.
- Thêm đường thử lại cho `resumes.parse_status = FAILED`: endpoint cho chính ứng viên sở hữu CV, đưa bản ghi về `PENDING` để poller nhặt lại. Không cho phép thử lại khi đang `PENDING`/`PROCESSING`/`DONE`. Frontend: nút "Phân tích lại" trên danh sách CV, chỉ hiện khi `FAILED`.
- D3 (`AggregationScheduler`) cố ý không claim — KHÔNG đụng vào, xem walkthrough `fr-h05-aggregate` mục 4b.

## Đợt 4 — Xử lý lỗi LLM

- Đặt timeout tường minh cho cả Anthropic client và OpenAI client trong `application.yml`, đọc từ config.
- Phân loại exception từ SDK thành hai nhóm: tạm thời (timeout, 429, 5xx → được retry theo backoff) và vĩnh viễn (401, 400, output không hợp lệ sau số lần thử tối đa → `FAILED` ngay, không retry).
- `ResumeParsingErrorCode.LLM_TIMEOUT` hiện không có đường code nào tạo ra được. Kiểm bằng SDK thật xem exception timeout là loại gì, rồi HOẶC map đúng, HOẶC xóa hẳn mã lỗi này. Không để nguyên trạng.
- `ResumeParsingErrorCode implements FormattedErrorCode` cho nhất quán với D2.
- Áp dụng cách phân loại này nhất quán cho cả D1, D2, D4, F1, F2.

Cố ý KHÔNG làm: tầng tổng hợp/cảnh báo chi phí token. Cột `token_usage` vẫn ghi đầy đủ như hiện tại, chỉ không có gì đọc từ đó. Lý do đã ghi trong ROADMAP.

## Đợt 5 — Rate limit

- Áp rate limit cho hai nhóm endpoint: (a) xác thực — `POST /api/auth/login`, `POST /api/auth/register/**`, tính theo IP, chống dò mật khẩu; (b) endpoint kích hoạt lời gọi LLM tốn tiền — upload CV, tạo lượt chấm điểm, tạo yêu cầu gợi ý cải thiện CV, tính theo user id.
- Vượt hạn mức trả **429** kèm header `Retry-After`, body theo đúng khuôn error response chung của dự án.
- Mọi hạn mức đọc từ `application.yml`, không hardcode trong code.
- In-memory là đủ (đồ án chạy một instance). Ghi rõ trong walkthrough rằng triển khai đa instance sẽ cần backend chia sẻ.

## Đợt 6 — Nhất quán & giao diện

- Việt hoá CÓ DẤU toàn bộ chuỗi hiển thị cho người dùng còn thiếu dấu ở backend (đúng quy ước CLAUDE.md mục 4). Bắt đầu từ `JsonAuthenticationEntryPoint.java:22`, rà hết `GlobalExceptionHandler` và các handler trong filter chain.
- `JobApplyForm.tsx` (C2): lọc bỏ CV có `parse_status = FAILED` khỏi danh sách chọn, kèm chú thích cho ứng viên biết vì sao CV đó không chọn được. Đơn nộp bằng CV hỏng thì HR không chấm điểm được, đơn nằm chết.
- Sửa cảnh báo `Select is changing from uncontrolled to controlled` (radix-ui) — tìm `Select` khởi tạo `value={undefined}`, sửa bằng giá trị khởi tạo hoặc `defaultValue`.
- Căn phải các cột số trong `CandidatesTable` và `JobPerformanceTable` (F3).

## Cố ý KHÔNG làm trong nhánh này — ghi rõ vào ROADMAP kèm lý do

- **Presigned URL cho file CV.** Luồng hiện tại stream file qua app server và kiểm quyền sở hữu ở mỗi request, chặt hơn presigned URL (URL rò rỉ trong thời gian TTL là truy cập được, không kiểm lại quyền). Presigned chỉ đáng làm khi cần giảm tải app server ở quy mô lớn — không phải vấn đề bảo mật ở quy mô đồ án. Đây là quyết định có chủ đích, không phải bỏ sót.
- Tầng tổng hợp chi phí token (lý do đã ghi sẵn trong ROADMAP).
- `ChatModel.getDefaultOptions()` deprecated — chờ nâng Spring AI, không sửa lẻ.
- 6 job seed thiếu rubric/interview_template — thuộc `chore/seed-demo`, xử lý bằng xoá mềm ở đó.
- Claim cho poller gửi email E2 và các scheduler embedding F1 — chỉ sai khi chạy đa instance, đồ án chạy một instance.
- Làm tròn tổng điểm 2 chữ số ở frontend so với scale 3 trong DB — đã có quyết định ở D3, giữ nguyên.

## Ràng buộc thực thi

- Mỗi đợt: in TOÀN BỘ nội dung file ra chat trước khi ghi ra disk, chờ tôi duyệt.
- Chạy targeted test sau mỗi đợt, full test suite sau Đợt 6. Đợt 2 bắt buộc chạy full suite ngay vì đụng `ORDER BY` của D2/D3/D4.
- Không tự thêm cột vào entity nếu schema chưa có. Cần cột mới thì viết migration Flyway và hỏi tôi trước.
- Trước khi commit phải chạy `/srs-guard` rồi `/walkthrough`, sau đó cập nhật `ROADMAP.md` (đánh dấu `chore/hardening` xong, chuyển các mục cố ý bỏ sang phần ghi chú riêng để không mất dấu vết).
- Môi trường PowerShell 5.1: dùng `;` thay `&&`, `Select-String` thay `grep`, `Select-Object -Last` thay `tail`. Env var set inline cùng lệnh `.\mvnw.cmd`. File tạm đặt ở `C:\Users\PC\AppData\Local\Temp\`.
- Cuối mỗi đợt, show `git diff` đầy đủ trước khi tôi duyệt commit.

## Báo cáo — soát trước khi code Đợt 4 (Plan Mode, chưa code)

### Câu A — `criterion_scores` có ràng buộc chống ghi trùng ở tầng DB không?

Đã đọc `V1__init_schema.sql`, khối `criterion_scores` (dòng 278-294):

```sql
CREATE TABLE criterion_scores (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scoring_run_id         UUID NOT NULL REFERENCES scoring_runs(id) ON DELETE CASCADE,
    criterion_id           UUID REFERENCES rubric_criteria(id) ON DELETE SET NULL,
    criterion_name_snapshot VARCHAR(200) NOT NULL,
    weight_snapshot        NUMERIC(5,2)  NOT NULL,
    max_score_snapshot     INT           NOT NULL,
    score                  NUMERIC(5,2)  NOT NULL CHECK (score >= 0),
    reasoning              TEXT NOT NULL,
    evidence               JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_score_per_criterion UNIQUE (scoring_run_id, criterion_name_snapshot)
);
```

**Có sẵn — `uq_score_per_criterion UNIQUE (scoring_run_id, criterion_name_snapshot)`, dòng 290, đã
tồn tại từ V1, không cần migration mới.** Kịch bản worker A (zombie, sống qua `stale-timeout`) và
worker B (claim lại sau khi reaper đưa lượt về `PENDING`) cùng `recordCriterionScore` cho cùng một
tiêu chí của cùng một lượt: INSERT thứ hai vi phạm đúng ràng buộc này, Postgres từ chối — không thể
có hai dòng `criterion_scores` trùng `(scoring_run_id, criterion_name_snapshot)`, nên D3 không bao
giờ tính tổng trên dữ liệu đếm hai lần. `Set<String> alreadyScored` ở mục 4g (đọc trong Java trước
vòng lặp) chỉ là tối ưu tránh gọi LLM thừa — đúng vai trò "early-check thân thiện" như
`existsBy...` mà CLAUDE.md mô tả cho các ràng buộc "chỉ một X" khác trong dự án
(`uq_application_per_cycle`, `uq_scoring_run_in_progress`); chốt chặn thật đã nằm sẵn ở DB, không
phải lỗ hổng mới của nhánh này.

**Điểm phụ phát hiện khi soát theo hướng này — cách xử lý hiện tại khi va ràng buộc:**
`recordCriterionScore` gọi `criterionScoreRepository.save(criterionScore)` (không `saveAndFlush`) —
vi phạm nổi lên khi transaction ngắn của chính method này commit (không có thao tác nào chạy giữa
đó và lúc method trả về, nên không khác biệt hành vi so với flush ngay). Exception
(`DataIntegrityViolationException`) bật ngược lên `ScoringRunOrchestrator.doProcess`, không khớp
nhánh `catch (CriterionScoringFailedException e)` nên rơi thẳng vào catch-all
`catch (RuntimeException e)` của `processOne` → `stateService.markFailed(scoringRunId, ScoringRunErrorCode.UNEXPECTED_ERROR)`.

Hệ quả: **worker thua cuộc trong race này sẽ làm CẢ LƯỢT CHẤM thành `FAILED` với mã lỗi chung chung
`UNEXPECTED_ERROR`**, dù bản chất tình huống là vô hại (một worker khác đã ghi đúng tiêu chí đó
trước, dữ liệu không sai) — không phải lỗi nội dung CV hay lỗi LLM thật. Đây không phải lỗ hổng dữ
liệu (DB đã chặn đúng), nhưng là một trải nghiệm/nhãn lỗi chưa chính xác: HR sẽ thấy một lượt chấm
`FAILED` gây hiểu lầm trong đúng tình huống hiếm mà retry/reaper vừa tạo ra (hai worker cùng sống
sót qua một lần claim).

**Câu hỏi cần bạn quyết định trước khi code Đợt 4:** giữ nguyên hành vi này (chấp nhận `FAILED`
"giả" trong trường hợp hiếm — HR tự tạo lại lượt chấm mới, đơn giản, không thêm code), hay bắt riêng
`DataIntegrityViolationException` chứa `uq_score_per_criterion` ở `ScoringRunOrchestrator.doProcess`
để coi đó là "đã có worker khác ghi, tiếp tục vòng lặp thay vì markFailed" (phức tạp hơn, đụng vào
luồng xử lý lỗi chính của D2)? Đề xuất giữ nguyên (phương án 1) vì tình huống chỉ xảy ra khi ĐỒNG
THỜI: (a) một worker "zombie" sống sót qua hết `stale-timeout-ms`, VÀ (b) một tiêu chí cụ thể bị hai
lần gọi LLM trùng thời điểm — xác suất thấp, hậu quả chỉ là một lượt `FAILED` mà HR tạo lại được
ngay (không mất dữ liệu, không tính sai điểm), trong khi phương án 2 thêm độ phức tạp cho một tình
huống hiếm và đã có `docs/ROADMAP.md` mục `chore/hardening` ghi nhận giới hạn "zombie cùng lần
claim" là nợ đã biết (xem Việc 3, mục 4e trong plan). Chờ bạn chốt trước khi trình lại plan Đợt 4.