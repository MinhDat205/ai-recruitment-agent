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

- [ ] **E1** `feat/fr-h07-pipeline` — FR-H07 · Pipeline, mời phỏng vấn, xác nhận kết quả
- [ ] **E2** `feat/fr-c03-notification` — FR-C03 · Thông báo web + email

**Xong khi:** không tồn tại bất kỳ đường code nào tự động chuyển trạng thái đậu/rớt.

## Phase F — Gợi ý & thống kê

- [ ] **F1** `feat/fr-u04-recommend` — FR-U04 · Embedding + cosine similarity, gợi ý việc làm
- [ ] **F2** `feat/fr-u05-cv-improve` — FR-U05 · Gợi ý cải thiện CV
- [ ] **F3** `feat/fr-h08-dashboard` — FR-H08 · Dashboard, lọc theo điểm, tra cứu lịch sử đánh giá

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
