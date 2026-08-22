# Roadmap — tiến độ triển khai

Bảng theo dõi tiến độ. **Chi tiết từng nhánh xem `docs/PHASES.md`** (phạm vi, tiêu chí nghiệm thu,
lỗi AI hay mắc). **Quy trình làm việc xem `docs/WORKFLOW.md`.**

Nguyên tắc: **1 mã FR = 1 nhánh = 1 phiên Claude Code.** Xong nhánh nào chạy được nhánh đó rồi
mới sang nhánh kế. Không nhảy sang phần AI khi phần CRUD nền chưa chạy — AI không có dữ liệu vào
và không có chỗ ghi kết quả ra.

---

## Phase 0 — Khởi tạo ✅ HOÀN THÀNH

- [x] Repo, cấu trúc thư mục, `.gitattributes`, `.gitignore`, `.env.example`
- [x] Docker Compose: PostgreSQL 17 + pgvector, MailHog, MinIO
- [x] Schema đầy đủ 18 bảng qua Flyway (`V1__init_schema.sql`)
- [x] Backend Spring Boot 4.1 + Spring AI 2.0 + Java 25 — khởi động được
- [x] Frontend Vite + React 19 + Tailwind + design token
- [x] CI GitHub Actions, đã push lên GitHub
- [x] Tài liệu: SRS, TECH_STACK, UI_GUIDE, DOCKER, ONBOARDING, PHASES, WORKFLOW

**Đã đạt:** `docker compose up -d` + backend + frontend chạy được, database có 18 bảng.

---

## Phase A — Nền tảng tài khoản

- [x] **A1** `feat/fr-c01-auth` — FR-C01 · Đăng ký/đăng nhập 2 role, BCrypt, JWT, RBAC
- [x] **A2** `feat/fr-c02-public-browse` — FR-C02 · Trang công khai: danh sách job, chi tiết, hồ sơ doanh nghiệp
- [x] `chore/shadcn-setup` — cài shadcn/ui trước khi vào Phase B

**Xong khi:** ứng viên gọi API của HR → **403** (kiểm bằng curl, không qua UI).

## Phase B — HR dựng chiến dịch

- [X] **B1** `feat/fr-h01-company` — FR-H01 · Hồ sơ doanh nghiệp
- [X] **B2** `feat/fr-h02-jobs` — FR-H02 · CRUD tin tuyển dụng + mẫu giấy mời phỏng vấn
- [X] **B3** `feat/fr-h03-rubric` — FR-H03 · Tiêu chí + trọng số, thang điểm mặc định/tuỳ chọn
- [x] `fix/rubric-guard` — siết guard rubric đủ 100% cho mọi đường vào OPEN + map trùng tên tiêu chí sang 409 (sửa nợ kỹ thuật B2/B3, không gắn mã FR)

**Xong khi:** không lưu được rubric có tổng trọng số ≠ 100%, chặn ở cả UI lẫn API.

## Phase C — Ứng viên nộp đơn

- [x] **C1** `feat/fr-u01-resume` — FR-U01 · Hồ sơ cá nhân, upload nhiều phiên bản CV
- [x] **C2** `feat/fr-u02-apply` — FR-U02 · Ứng tuyển + consent bắt buộc + chống nộp trùng
- [x] `fix/public-header-auth` — sửa header công khai phản ánh trạng thái đăng nhập (không gắn mã FR)
- [x] **C3** `feat/fr-u03-tracking` — FR-U03 · Theo dõi 5 trạng thái + lịch sử
- [x] **C4** `feat/fr-u06-withdraw` — FR-U06 · Rút đơn (soft state)

**Xong khi:** nộp trùng job trong cùng chu kỳ bị chặn ở **tầng DB**, không chỉ ở UI.

## Phase D — AI đọc và chấm

- [X] **D1** `feat/fr-c04-parsing` — FR-C04 · Trích xuất CV → JSON, chạy nền, validate schema
- [X] **D2** `feat/fr-h04-scoring` — FR-H04 · Chấm **từng tiêu chí riêng** + evidence
  - Bắt buộc: khi tạo lượt chấm đầu tiên (`scoring_runs`) phải set `rubrics.is_locked = true`.
    Hiện chưa có đường nào trong ứng dụng đặt cờ này; guard mở lại tin ở
    `JobOwnerService.changeStatus` (nhánh `fix/rubric-guard`) dựa vào cờ đó để bỏ qua kiểm đủ
    100% — nếu D2 không cài, HR có job đã chấm sẽ kẹt không mở lại được chu kỳ tuyển dụng mới.
- [X] **D3** `feat/fr-h05-aggregate` — FR-H05 · Tổng hợp có trọng số + xếp hạng (Java thuần)
- [X] **D4** `feat/fr-h06-explain` — FR-H06 · Báo cáo giải thích, mọi luận điểm có evidence
  - Đã thêm nút "Xem CV gốc" cho HR (`/api/hr/applications/{id}/resume/download`) — không có mã FR
    nào giao việc này rõ ràng (đã đọc lại SRS/PHASES xác nhận khoảng trống), xếp vào D4 thay vì E1
    vì lý do: không có CV gốc thì không đối chiếu được evidence trong báo cáo AI với văn bản thật,
    đúng nguyên tắc Explainable AI của FR-H06. Chi tiết lập luận ở walkthrough `fr-h06-explain.md`
    mục 4h.

**Xong khi:** unit test `ScoreAggregator` pass; đổi trọng số → thứ hạng đổi đúng công thức; mở bất
kỳ tiêu chí nào cũng thấy evidence trích từ CV; không tồn tại cột/field nào tên `verdict`, `label`,
`isQualified`, `passed`.

## Phase E — Quyết định & thông báo

- [X] **E1** `feat/fr-h07-pipeline` — FR-H07 · Pipeline, mời phỏng vấn, xác nhận kết quả
  - Máy trạng thái (`PATCH /api/hr/applications/{id}/status`) chặn cứng đường tắt đặt thẳng
    `INTERVIEW_INVITED` — trạng thái này bắt buộc phải kèm lịch hẹn thật (đúng nghĩa "Đã mời phỏng
    vấn (có lịch hẹn)" trong SRS), chỉ đi được qua `POST .../interview-invitation`. Chi tiết lập
    luận ở walkthrough `fr-h07-pipeline.md` mục 4b.
  - Giấy mời phỏng vấn lưu nguyên văn nội dung HR đã gửi (`interview_invitations.rendered_content`),
    không render lại và không FK ngược về `interview_templates` — HR sửa mẫu sau này không làm đổi
    nội dung đã gửi, cùng tinh thần `rubric_snapshot` (D2). Chi tiết walkthrough mục 4c/4e.
- [x] **E2** `feat/fr-c03-notification` — FR-C03 · Thông báo web + email
  - Spring Events (`@TransactionalEventListener(AFTER_COMMIT)` cho 3 sự kiện publish trong transaction
    nghiệp vụ, `@EventListener` thường cho sự kiện chấm điểm xong publish ngoài transaction ở
    `AggregationOrchestrator`) + poller gửi email riêng, tách hoàn toàn khỏi transaction ghi chính.
    Chi tiết lập luận + lỗi thật gặp lúc chạy test (Spring chặn `@Transactional` mặc định trên
    method AFTER_COMMIT) ở walkthrough `fr-c03-notification.md` mục 4a/4b.

**Xong khi:** không tồn tại bất kỳ đường code nào tự động chuyển trạng thái đậu/rớt.

## Phase F — Gợi ý & thống kê

- [ ] **F1** `feat/fr-u04-recommend` — FR-U04 · Embedding + cosine similarity, gợi ý việc làm
- [x] **F2** `feat/fr-u05-cv-improve` — FR-U05 · Gợi ý cải thiện CV
  - Cố ý bỏ vế "kết quả đánh giá trước đó" của SRS FR-U05 — PHASES.md cấm lộ điểm/rubric/nhận xét
    nội bộ của HR cho ứng viên, hai văn bản mâu thuẫn nhau, ưu tiên PHASES.md. Ba lớp phòng thủ độc
    lập chống rò rỉ dữ liệu chấm điểm (chữ ký service, prompt gửi LLM, response trả ứng viên) — mỗi
    lớp có test riêng. Chi tiết lập luận đầy đủ ở walkthrough `fr-u05-cv-improve.md` mục 4a/4b.
- [x] **F3** `feat/fr-h08-dashboard` — FR-H08 · Dashboard, lọc theo điểm, tra cứu lịch sử đánh giá
  - Phễu chuyển đổi đếm theo `application_status_history` (đã từng đạt trạng thái), không đếm theo
    `job_applications.status` hiện tại — đơn được mời phỏng vấn rồi rút đơn vẫn tính vào "đã từng
    được mời". Nhánh lọc theo điểm tiêu chí dẫn dắt câu SQL từ `criterion_scores` để ép Postgres
    dùng đúng `idx_criterion_scores_filter` (dẫn dắt từ `scoring_runs` sẽ vô tình chọn index khác).
    Không có cột "Hạng" ở danh sách ứng viên toàn công ty — FR-H05 chỉ định nghĩa xếp hạng trong
    phạm vi một chiến dịch. Chi tiết đầy đủ + các quyết định gây tranh luận ở walkthrough
    `fr-h08-dashboard.md` mục 4.

**Xong khi:** đơn đã rút vẫn được đếm đúng trong thống kê.

---

## Hoàn thiện trước bảo vệ

- [ ] `chore/hardening` — rate limit, xử lý lỗi LLM (timeout/quota), presigned URL cho file CV
  - Presigned URL: từ D4 áp dụng cho **cả hai** đường tải file CV (ứng viên qua
    `ResumeCandidateController`, HR qua `ResumeHrController` mới) — cả hai đều stream file qua app
    server, không phải chỉ một. Chưa gây vấn đề ở quy mô hiện tại (`app.storage.type=local`, không
    S3/MinIO thật dù có chạy container MinIO trong `docker-compose`).
  - D4: `app.explanation.max-attempts=3` không có nút "thử lại ngay" riêng cho việc sinh báo cáo
    giải thích khi đã `FAILED` — HR phải tạo một lượt chấm điểm mới cho đơn đó để có cơ hội thử lại.
  - Cố ý không xây tầng tổng hợp/cảnh báo chi phí token — ngoài phạm vi đồ án, không phải bỏ sót.
    Cột `scoring_runs.token_usage`/`resume_parsed_data.token_usage` vẫn được ghi đầy đủ như hiện
    tại, chỉ không có gì đọc/tổng hợp từ đó.
  - ResumeParsingErrorCode.LLM_TIMEOUT hiện không có
   đường code nào tạo ra được — comment trong ResumeParsingService đã ghi nhận là chưa xác
   định được loại exception timeout thật từ SDK Anthropic (test chỉ mock ở tầng ChatModel).
   Cần kiểm bằng SDK thật rồi hoặc map đúng, hoặc xoá mã lỗi này.
  - Không có đường thử lại cho `resumes.parse_status = FAILED` do lỗi môi trường tạm thời (vd
    thiếu `ANTHROPIC_API_KEY` lúc chạy) — hiện ứng viên phải upload lại từ đầu.
  - Không có stale-claim reaper: một lượt chạy nền chết vì JVM restart giữa chừng (D1
    `resumes.parse_status = PROCESSING`, D2 `scoring_runs` ở `RUNNING`/`finished_at NULL`) sẽ kẹt
    vĩnh viễn; riêng D2 còn bị `uq_scoring_run_in_progress` (V4) chặn cứng, không tạo được lượt
    chấm mới cho đơn đó. D3 (tổng hợp điểm) **không** có khoản nợ tương tự — cố ý không claim (xem
    walkthrough `fr-h05-aggregate` mục 4b), nên một lượt tổng hợp dở dang khi JVM crash vẫn nằm
    trong phạm vi quét của `AggregationScheduler`, tự được thử lại ở nhịp poll kế tiếp.
  - Tổng điểm hiển thị ở frontend làm tròn 2 chữ số thập phân (`toFixed(2)`) trong khi cột
    `scoring_runs.total_score` lưu scale 3 (`NUMERIC(6,3)`) — chưa có yêu cầu rõ ràng về độ chính
    xác hiển thị, chọn 2 chữ số cho gọn mắt (D3, `ApplicationsTab.tsx`).
  - `ChatModel.getDefaultOptions()` đã deprecated ở Spring AI 2.0, đang dùng trong mock test của
    cả D1 và D2 — cần thay khi nâng phiên bản.
  - `ResumeParsingErrorCode` (D1) chưa implement `common/FormattedErrorCode` — `CriterionScoringErrorCode`
    và `ScoringRunErrorCode` (D2) đã implement. Không cấp bách: `ResumeParsingStateService.markFailed`
    đã nhận đúng kiểu enum `ResumeParsingErrorCode` (không nhận `String` tự do), nên không có lỗ hổng
    thực tế — chỉ lệch chuẩn interface chung. Hoãn vì sửa nó phải đụng code D1 đã merge và chạy lại
    toàn bộ test của nhánh khác, ngoài phạm vi D2.
  - Phát hiện khi kiểm thử Phase D bằng key thật (19/08/2026):
    - Form ứng tuyển (C2, `frontend/src/features/applications/JobApplyForm.tsx`) cho chọn cả CV có
      `parse_status = FAILED`. Đơn nộp bằng CV hỏng thì HR không bấm chấm điểm được, đơn nằm chết
      không xử lý được. Cần lọc bỏ CV `FAILED` khỏi danh sách chọn, hoặc chặn nộp kèm thông báo rõ.
  - E1: `ApplicationStatusService.changeStatus` (`backend/src/main/java/com/recruitment/jobapplication/ApplicationStatusService.java`)
    đọc `application.getStatus()` rồi `save()` mà không có `WHERE status = :oldStatus` hay
    `@Version` — hai request PATCH gần như đồng thời trên cùng một đơn (double-click, hai tab HR)
    đều có thể đọc cùng một trạng thái gốc, đều qua kiểm luồng, rồi cả hai đều ghi thành công
    (last-write-wins), có thể để lại hai dòng lịch sử mâu thuẫn cùng xuất phát từ một trạng thái.
    Phát hiện khi chạy `srs-guard` cho nhánh E1 (không phải vi phạm nào trong 9 mục của skill, chỉ
    là rủi ro cùng họ — không có ràng buộc "chỉ một X đang hoạt động" nào bị vi phạm theo đúng
    nghĩa hẹp). Cách sửa đề xuất: đổi sang `UPDATE job_applications SET status = :new WHERE id =
    :id AND status = :old`, kiểm số dòng ảnh hưởng — cùng khuôn mẫu
    `ScoringRunRepository.finishAggregation` (D3) đã dùng cho đúng vấn đề tương tự.
  - Phát hiện khi kiểm thử tay nhánh E1 bằng tài khoản thật (20/08/2026):
    - 6 job seed `10000000-0000-0000-0000-00000000000{1..6}` (`db/seed/dev-seed.sql`) không có rubric
      lẫn interview_template; job `11111111-1111-1111-1111-111111111111` có rubric nhưng thiếu
      template. Đây là dữ liệu tạo ngoài `JobOwnerService.create` nên thiếu các bất biến mà B2 đảm
      bảo (Job+Rubric+InterviewTemplate luôn tạo cùng nhau). Xử lý bằng xoá mềm khi làm
      `chore/seed-demo`, không vá bằng INSERT tay.
    - Console cảnh báo `Select is changing from uncontrolled to controlled` (radix-ui) — có `Select`
      khởi tạo `value={undefined}`. Sửa bằng giá trị khởi tạo hoặc `defaultValue`.
    - Message lỗi 401 `UNAUTHENTICATED` viết tiếng Việt không dấu ("Can dang nhap de truy cap tai
      nguyen nay", `JsonAuthenticationEntryPoint.java:22`) — không nhất quán với quy ước "chuỗi hiển
      thị cho người dùng: tiếng Việt có dấu" (CLAUDE.md mục 4). Rà lại các message tương tự còn
      thiếu dấu (`GlobalExceptionHandler` và các entry point/handler khác ở tầng filter chain).
    - Sidebar HR: mục "Ứng viên" và "Rubric" không có `to` trong `NAV_ITEMS`
      (`frontend/src/components/layout/HrLayout.tsx:15-16`) nên hiển thị mờ, không bấm được. Cố ý ở
      giai đoạn này (hai màn hình đó vào qua job, chưa có trang danh sách toàn cục). Quyết định ở F3
      (FR-H08, dashboard): trỏ về màn hình đó hoặc gỡ hẳn khỏi sidebar.
      **Đã xử lý ở F3**: "Ứng viên" trỏ `/hr/candidates` (trang mới); "Rubric" xóa hẳn khỏi
      `NAV_ITEMS` (rubric thuộc từng job, đã có tab riêng trong `HrJobEditPage`, đặt ở menu cấp cao
      là điều hướng cụt).
  - E2: poller gửi email không claim trước khi gửi — an toàn với một instance, sẽ gửi trùng nếu chạy
    đa instance. Xem walkthrough `fr-c03-notification.md` mục 4d/7.
  - Phát hiện khi làm F3 (FR-H08, `feat/fr-h08-dashboard`):
    - `ScoringRunRepository.findByApplicationIdOrderByCreatedAtDesc` (D2) — derived query `ORDER BY
      created_at DESC` thiếu khóa cuối duy nhất. Đã kiểm thực nghiệm trên Postgres 17 thật: hai lượt
      chấm cùng `created_at` (cùng transaction) có thể đổi thứ tự trả về giữa hai lần đọc dữ liệu
      không đổi, chỉ do khác vị trí vật lý trong heap/index. Đang được `ScoringRunService.listScoringRuns`
      (D2) dùng trực tiếp — sửa cần thêm `, id DESC` và chạy lại 19 test của D2 để xác nhận không vỡ
      kỳ vọng thứ tự.
    - `ScoringRunRepository.findLatestDoneByApplicationIdIn` (D3) — `DISTINCT ON (application_id)
      ORDER BY application_id, created_at DESC` cùng lỗi thiếu khóa cuối, ảnh hưởng nguồn điểm xếp
      hạng của D3/D4.
    - Pattern `requireOwnCompany` chạy **sau** khi tra tài nguyên (thay vì trước) ở
      `ApplicationStatusService.loadOwnedApplication` (E1) và `ApplicationOwnerService.loadOwnedJob`
      (D3) — HR chưa tạo hồ sơ công ty nhận nhầm lỗi 404 sai nguyên nhân (`APPLICATION_NOT_FOUND`/
      `JOB_NOT_FOUND` thay vì `COMPANY_NOT_FOUND`). F3 đã sửa đúng thứ tự này cho
      `ScoringRunAuditService` (file mới), không sửa hai file D3/E1 kia (ngoài phạm vi một mã FR).
    - Dropdown "Tin tuyển dụng" trong `CandidatesFilterBar` (F3) giới hạn 50 tin — trần
      `JobOwnerService.MAX_SIZE` ở backend, không phải lựa chọn tùy ý ở frontend. Công ty có hơn 50
      tin sẽ không lọc được tin cũ nhất qua dropdown này (đã có chú thích UI báo số lượng bị cắt bớt).
    - Cột số trong `CandidatesTable` và `JobPerformanceTable` (F3) căn trái theo mặc định — nên căn
      phải để dễ so sánh giá trị giữa các dòng.
  - Phát sinh khi làm F2 (FR-U05, `feat/fr-u05-cv-improve`):
    - `ApplicationHistoryEntryResponse.note` trả cho ứng viên (F3, endpoint `GET
      /api/candidates/applications/{id}/history`) — hiện an toàn vì cả 4 điểm ghi trong toàn bộ
      codebase đều truyền `null`, chưa có đường nào cho HR nhập `note` tự do. Khi cho HR nhập note
      phải bỏ field này khỏi DTO hoặc tách DTO riêng cho ứng viên. Chưa có integration test HTTP
      phủ endpoint này. Phát hiện khi khảo sát Plan Mode của F2, không phải lỗi do F2 gây ra.
    - Nút "Thử lại" khi trạng thái gợi ý cải thiện CV là `FAILED` chưa kiểm thử tay được với API
      key Anthropic thật — model gần như luôn trả JSON hợp lệ đúng schema, không có cách ép LLM
      thật trả lỗi một cách tin cậy để dựng thủ công tình huống này qua giao diện.
    - Chưa test race condition thật (hai request HTTP đồng thời thật sự) cho
      `uq_cv_improvement_request_active` — test hiện có gọi tuần tự, không phải song song thật.
    - Danh sách 20 job `OPEN` mới nhất gửi cho LLM không lọc theo lĩnh vực ở tầng SQL — việc lọc
      lĩnh vực hoàn toàn do LLM tự đọc và tự loại trong prompt, không dùng semantic search (F1/
      FR-U04 mới có hạ tầng embedding để lọc chính xác theo ngữ nghĩa). Nếu hệ thống có rất nhiều
      job đa dạng lĩnh vực, 20 tin mới nhất có thể không đủ đại diện cho lĩnh vực của một CV cụ thể.
- [ ] `chore/seed-demo` — dữ liệu demo: 1 HR, 2 job có rubric, 8 ứng viên với CV thật
- [ ] `docs/final` — README hoàn chỉnh, kịch bản demo, sơ đồ ER xuất từ database thật

---

## Ba nhánh cần review kỹ nhất

Đây là chỗ AI sẽ tự "tối ưu" theo hướng vi phạm nguyên tắc SRS. Đọc mục "AI hay làm sai"
trong `docs/PHASES.md` **trước** khi bắt đầu ba nhánh này:

| Nhánh | AI sẽ muốn làm | Vì sao sai |
|---|---|---|
| **D2** FR-H04 | Gộp tất cả tiêu chí vào một lần gọi LLM cho tiết kiệm token | Vi phạm nguyên tắc "chấm từng tiêu chí riêng lẻ", chất lượng chấm giảm rõ rệt |
| **D3** FR-H05 | Thêm ngưỡng phân loại, tô màu đỏ–vàng–xanh cho điểm | SRS cấm phân loại và gán nhãn — màu theo ngưỡng là phán quyết trá hình |
| **E1** FR-H07 | Thêm "tự động từ chối ứng viên dưới ngưỡng điểm" | Cấm tuyệt đối: quyết định luôn thuộc về HR (human-in-the-loop) |
