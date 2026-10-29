# Kế hoạch tăng tốc chấm điểm và nút chấm tất cả

Oct 28, 2026 · @huy

## Phạm vi và quyết định

Làm 3 hạng mục, chia **2 nhánh** từ `main` (`bee3c79`): A + B là tối ưu, không thêm chức năng; nút chấm tất cả là chức năng mới nên cần đặc tả được duyệt trước khi code. Kế hoạch dựa trên code đọc ở `bee3c79`; Claude Code vẫn phải đối chiếu lại ở Plan Mode.

| Hạng mục | Nhánh | Đổi ở đâu | Cần sửa/viết đặc tả |
| --- | --- | --- | --- |
| A. Tăng số luồng scheduler | `perf/scoring-latency` | `application.yml` + 1 chỗ chặn race embedding | Không |
| B. Rút chu kỳ quét 5 s → 1 s | `perf/scoring-latency` | `application.yml`, 3 khoá | Không |
| Nút chấm tất cả CV trong tin | `feat/fr-h04-bulk-scoring` | API mới + `ApplicationsTab.tsx` | Có: đặc tả mở rộng FR-H04 (REQUIREMENT.md + UI.md) + một câu trong SRS.md |

**Không làm** trong đợt này: frontend tự tải lại giải thích (E; HR vẫn bấm "Tải lại" ở tab Giải thích theo R-T14 của FR-H09), chấm song song tiêu chí (C), đánh thức job bằng sự kiện (D), prompt caching (F), đổi prompt hay `PROMPT_VERSION`, đổi seed tầng 2/2b. Không đổi luật AI: vẫn mỗi tiêu chí một lần gọi, evidence qua K2.

**Kỳ vọng**: lượt chấm một đơn từ \~70–93 s xuống khoảng 50–60 s (bỏ phần chờ luồng và phần lớn nhịp quét; \~44 s chấm tiêu chí không đổi). Con số phải đo lại sau khi làm.

## A. Tăng số luồng scheduler

Thêm `spring.task.scheduling.pool.size: 6` vào `application.yml`. Hiện 14 phương thức `@Scheduled` dùng chung 1 luồng mặc định, nên một lần gọi LLM ở job này chặn mọi job khác.

**Vì sao 6:** đủ để chấm, tổng hợp, giải thích, trích xuất CV, embedding và gửi mail chạy cùng lúc; reaper và các job ngắn dùng chung phần còn lại. Không để lớn hơn vì pool kết nối Hikari mặc định là 10 và còn phải phục vụ request HTTP (dù job nền không giữ kết nối trong lúc chờ LLM).

**Vì sao an toàn** (đã kiểm trong code):

- Mỗi phương thức `fixedDelay` vẫn không tự chạy chồng lên chính nó khi có nhiều luồng. Các orchestrator không claim (embedding, giải thích, mail) dựa vào đúng điều này, nên không bị ảnh hưởng.
- Chấm và trích xuất đã claim bằng `UPDATE` có điều kiện; tổng hợp ghi bằng `UPDATE ... WHERE total_score IS NULL`; giải thích có UNIQUE `scoring_run_id`; tính kinh nghiệm ghi có điều kiện `experience_computed_at IS NULL`.
- Không bật `spring.threads.virtual.enabled` (đã grep), nên `pool.size` có hiệu lực.

**Một race mới phải chặn:** khi job trích xuất lại (FR-C05) và job embedding CV chạy trên hai luồng, job embedding có thể đọc `data` cũ, trong lúc đó trích xuất lại ghi `data` mới và đặt `embedding = NULL`, rồi job embedding ghi đè vector của bản cũ (`updateEmbedding` hiện chỉ có `WHERE id = :id`). CV đó sẽ giữ embedding sai vĩnh viễn. Với 1 luồng điều này không xảy ra.

Cách sửa đề xuất: `ResumeEmbeddingOrchestrator` giữ `parsed_at` đã đọc, `updateEmbedding` thêm `AND parsed_at = :readParsedAt` (trích xuất lại đã cập nhật `parsed_at = now()` trong `touchAfterReparse`). Rowcount 0 thì bỏ qua, nhịp sau tự embed lại. Claude Code phải rà thêm các cặp job khác cùng ghi một bảng ở Plan Mode.

**Kiểm thử**

- Test hiện tắt scheduler (`app.*.enabled: false` trong `application-test.yml`), nên đổi `pool.size` không làm đỏ test cũ. Vẫn chạy full `./mvnw test`.
- Test mới cho race: dựng tình huống `parsed_at` đổi giữa lúc đọc và lúc ghi → embedding vẫn `NULL` (case âm); `parsed_at` không đổi → ghi được (case dương).
- Soát tay: khởi động backend, log phải thấy tên luồng `scheduling-1` đến `scheduling-6` thay vì chỉ `scheduling-1`.
- Claude Code xác minh tên thuộc tính trên jar `spring-boot-autoconfigure` thật (`TaskSchedulingProperties`), không viết theo trí nhớ.

## B. Rút chu kỳ quét 5 s → 1 s

Đổi ba khoá trong `application.yml` từ `5000` xuống `1000`: `app.scoring.poll-interval-ms`, `app.aggregation.poll-interval-ms`, `app.explanation.poll-interval-ms`. Các job khác giữ 5 s vì không nằm trên đường HR đang chờ.

**Ảnh hưởng phụ**

- Thêm khoảng 3 truy vấn nhỏ mỗi giây khi rảnh; cả ba truy vấn đều có index (đã ghi plan trong `ScoringRunRepository`).
- Log sẽ ồn hơn vì `org.hibernate.SQL: DEBUG` đang bật. Quyết định: hạ về INFO trong commit A+B. Khi cần xem SQL thì bật tạm bằng tham số chạy --logging.level.org.hibernate.SQL=DEBUG, không sửa file cấu hình.
- Job giải thích thử lại ở nhịp kế tiếp, không có backoff. Với lỗi xảy ra ngay (ví dụ thiếu khoá API), 3 lần thử sẽ hết trong \~3 s thay vì \~15 s. Chấp nhận được vì trạng thái cuối vẫn là `FAILED` có mã lỗi.
- Backoff của chấm điểm (30 s / 120 s qua `next_attempt_at`) không đổi.

**Kiểm thử**: không có test nào đọc ba khoá này (scheduler tắt trong test), chỉ cần full `./mvnw test`. Đo bằng truy vấn ở mục thứ tự đợt.

## Nút chấm tất cả CV trong tin

Tab "Ứng viên" của tin có thêm nút "Chấm tất cả (N)", tạo một lượt chấm `PENDING` cho mỗi đơn đủ điều kiện trong một request. Request chỉ tạo bản ghi, không gọi AI; AI vẫn chạy qua job nền như cũ, nên không vi phạm §7 của `CLAUDE.md`. Phần này cần đặc tả mới được duyệt trước khi code.

**Đơn "có thể chấm" (đã chốt)**

| Điều kiện | Lý do |
| --- | --- |
| Đơn thuộc tin này, tin thuộc công ty của HR | Kiểm quyền sở hữu như `ScoringRunService.loadOwnedApplication` |
| Trạng thái đơn `PENDING` | Không tốn lượt AI cho đơn đã rút, đã từ chối, đã tuyển hay đã mời phỏng vấn |
| CV `parse_status = DONE` và có `resume_parsed_data` | Giống điều kiện nút đơn lẻ |
| Không có lượt đang chạy | Giống nút đơn lẻ; index `uq_scoring_run_in_progress` chặn race |
| Chưa có lượt `DONE` (chưa chấm, hoặc lượt gần nhất `FAILED`) | Nút đơn lẻ cho chấm lại; nút hàng loạt thì không, tránh chấm lại cả tin ngoài ý muốn |
| Đơn thuộc chu kỳ tuyển dụng hiện tại của tin (job\_applications.recruitment\_cycle = jobs.recruitment\_cycle) | Đã chốt. Đơn chu kỳ cũ không được xét, không tính vào N và không liệt kê trong skipped |

Rubric của tin phải đủ tổng trọng số 100; thiếu thì cả request trả 409 `RUBRIC_INCOMPLETE`, không tạo lượt nào. Mọi lượt dùng chung một `rubric_snapshot` chụp một lần.

**API đề xuất**: `POST /api/hr/jobs/{jobId}/scoring-runs/bulk`, không có body.

- 200, trả `{ created: number, skipped: [{ applicationId, reason }] }`, `reason` là mã chuẩn hoá (`RESUME_NOT_PARSED`, `RUN_IN_PROGRESS`, `ALREADY_SCORED`, `NOT_PENDING`).
- 404 tin không tồn tại, 403 tin của công ty khác, 409 rubric chưa đủ.
- Trần 50 đơn mỗi request (đã chốt; quy mô demo không chạm tới, trần chỉ để chặn chi phí). Vượt trần thì tạo 50 đơn nộp sớm nhất và báo số còn lại.
- Không bọc cả request trong một transaction: mỗi đơn tạo qua `ScoringRunStateService.create` riêng; vi phạm `uq_scoring_run_in_progress` (HR bấm nút đơn lẻ cùng lúc) tính là `RUN_IN_PROGRESS`, không làm hỏng các đơn khác.
- Rate limit: thêm mẫu đường dẫn vào nhóm `llm-action` của `RateLimitFilter`. Nhóm này đếm theo request nên 1 lần bấm tốn 1 lượt dù tạo nhiều lượt chấm; trần 50 đơn là chốt chặn chi phí thật.
- Code đặt trong `scoring/` (cùng package với `ScoringRunService`), tái dùng các hàm kiểm tra sẵn có thay vì viết lại.

**Giao diện** (`scoring/ApplicationsTab.tsx`):

- Nút "Chấm tất cả (N)" cạnh ô "Sắp xếp theo"; N đếm từ danh sách đang hiển thị theo cùng quy tắc trên (thêm hàm vào `scoringRules.ts`, dùng chung với backend về ý nghĩa). N = 0 thì khoá, kèm câu lý do qua `aria-describedby` (theo mẫu FR-H16).
- Bấm thì mở hộp xác nhận: số đơn sẽ chấm, số lượt gọi AI ước tính (N × (số tiêu chí + 1)), thời gian ước tính (\~1 phút mỗi đơn vì các lượt chạy lần lượt).
- Xong thì toast "Đã xếp N đơn vào hàng chấm" + số đơn bỏ qua; làm mới danh sách, polling hiện có tự bật.
- Không tô màu hay gắn nhãn theo kết quả (quy tắc §8 `CLAUDE.md`).

**Giới hạn cần biết**

- `ScoringRunScheduler` xử lý các lượt **lần lượt** trong một phương thức; phương án A không làm các lượt chạy song song. 20 đơn × \~45–60 s ≈ 15–20 phút.
- Polling danh sách theo tin dừng sau 10 phút (`MAX_POLL_DURATION_MS`); với lô lớn, HR sẽ thấy nút "Tải lại". Đề xuất giữ nguyên, ghi rõ trong hộp xác nhận.
- Mỗi lượt xong tạo một thông báo `SCORING_FINISHED`, nên chuông sẽ nhận N thông báo.

**Kiểm thử backend** (Testcontainers, mock `ChatModel`, có case dương và âm):

- Tạo đúng số lượt cho đơn đủ điều kiện; mỗi loại đơn bị loại trả đúng `reason`.
- Rubric thiếu trọng số → 409, không tạo lượt nào.
- Quyền: không đăng nhập 401, ứng viên 403, HR công ty khác 403, tin không tồn tại 404.
- Biên trần: 49, 50, 51 đơn đủ điều kiện.
- Gọi hai lần liên tiếp: lần hai tạo 0, tất cả `RUN_IN_PROGRESS`.
- Tin bật ẩn danh (H16): vẫn chấm bình thường, phản hồi không chứa tên ứng viên.

**Kiểm thử frontend**: `npm run build` + `npm run lint`; soát tay với tin có đơn đã chấm, chưa chấm, đã rút, CV chưa trích xuất xong.

## Thứ tự đợt, commit và cách đo

Làm nhánh `perf/scoring-latency` trước, merge, đo; sau đó mới mở `feat/fr-h04-bulk-scoring`, để số đo của hai việc không lẫn vào nhau. Mỗi đợt: dừng → báo cáo diff → bạn duyệt → một commit một dòng.

**Nhánh 1 — `perf/scoring-latency`**

0. **Đo mốc trước** trên `main`: chấm 2–3 đơn thật, chạy truy vấn đo bên dưới, lưu kết quả.
1. **Plan Mode**: Claude Code đối chiếu kế hoạch với code thật, xác minh tên thuộc tính trên jar, rà mọi cặp job cùng ghi một bảng. Không code.
2. **Đợt A+B**: `application.yml` + hạ log SQL về INFO + chặn race embedding + test race. Full `./mvnw test`. Commit: `perf: tang luong scheduler, rut chu ky quet cham diem`.
3. **Soát tay + đo mốc sau**: cùng số đơn, cùng rubric, chạy lại truy vấn; cập nhật số đo trong `docs/DEMO.md` và ghi một dòng vào `docs/ROADMAP.md`.

**Nhánh 2 — `feat/fr-h04-bulk-scoring`**

1. **Đặc tả**: mở rộng FR-H04 đặt ở docs/features/HR/H04/REQUIREMENT.md + UI.md (thư mục mới, vì FR-H04 gốc chỉ nằm trong SRS.md) và thêm một câu vào dòng FR-H04 của SRS.md trỏ tới đó, chạy `spec-review`, bạn duyệt (`ĐÃ DUYỆT <ngày>`).
2. **Plan Mode** đối chiếu đặc tả với code.
3. **Đợt backend**: service, controller, rate limit, test. Chạy các lớp test của đợt.
4. **Đợt frontend**: nút, hộp xác nhận, toast.
5. **Soát tay** (bạn làm).
6. **Đợt cuối**: full `./mvnw test`, `srs-guard`, `walkthrough`, cập nhật SRS, ROADMAP, UI\_GUIDE nếu cần, `docs/ER.md` không đổi (không có migration).

Không nhánh nào cần migration, nên seed tầng 2/2b và quy trình nạp demo giữ nguyên.

**Truy vấn đo** (chạy trước và sau, so theo trung vị):

```sql
SELECT sr.id,
       sr.started_at  - sr.created_at   AS cho_nhan_viec,
       sr.finished_at - sr.started_at   AS cham_tieu_chi,
       se.generated_at - sr.finished_at AS tong_hop_va_giai_thich,
       se.generated_at - sr.created_at  AS tong
FROM scoring_runs sr
LEFT JOIN score_explanations se ON se.scoring_run_id = sr.id
WHERE sr.status = 'DONE'
ORDER BY sr.created_at DESC
LIMIT 10;
```

Cột `cho_nhan_viec` và `tong_hop_va_giai_thich` là nơi A + B phải thấy giảm; `cham_tieu_chi` dự kiến không đổi.

## Rủi ro và câu hỏi còn mở

**Rủi ro**

| Rủi ro | Khả năng | Cách xử lý |
| --- | --- | --- |
| Race giữa hai job trước đây bị che bởi 1 luồng, ngoài cặp embedding – trích xuất lại | Thấp | Plan Mode rà toàn bộ 14 phương thức; chỉ đổi khi có bằng chứng |
| Nhiều lời gọi Anthropic đồng thời hơn (chấm + trích xuất + gợi ý CV cùng lúc) chạm giới hạn tốc độ | Thấp với quy mô demo | Đã có xử lý 429 là lỗi tạm thời + backoff |
| Nút chấm tất cả đốt nhiều token ngoài ý muốn | Trung bình | Hộp xác nhận ghi số lượt gọi AI, trần 50 đơn, bỏ qua đơn đã chấm |
| Thay đổi ngay trước buổi gặp thầy tuần 2 tháng 11 | Trung bình | Nhánh 1 nhỏ, làm trước; nhánh 2 chỉ merge khi soát tay xong, nếu không kịp thì để sau buổi gặp |
| Demo có gọi AI thật, số đo trong `docs/DEMO.md` lệch | Chắc chắn | Cập nhật số đo và câu nói trong kịch bản sau khi đo lại |

**Câu hỏi cần bạn chốt trước khi giao cho Claude Code**

- [x] Log SQL: hạ `org.hibernate.SQL` về `INFO` trong nhánh 1.
- [x] Nút chấm tất cả là phần mở rộng của FR-H04, nhánh `feat/fr-h04-bulk-scoring`.
- [x] Chỉ chấm đơn thuộc chu kỳ tuyển dụng hiện tại của tin.
- [x] Trần 50 đơn mỗi lần bấm.
- [x] Trạng thái đơn được chấm: chỉ `PENDING` chưa có lượt `DONE`.
- [ ] Báo với Huy sau khi chốt kế hoạch, trước khi merge nhánh 1 (đổi job embedding, liên quan U04).
