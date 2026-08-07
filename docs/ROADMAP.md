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

- [ ] **A1** `feat/fr-c01-auth` — FR-C01 · Đăng ký/đăng nhập 2 role, BCrypt, JWT, RBAC
- [ ] **A2** `feat/fr-c02-public-browse` — FR-C02 · Trang công khai: danh sách job, chi tiết, hồ sơ doanh nghiệp

**Xong khi:** ứng viên gọi API của HR → **403** (kiểm bằng curl, không qua UI).

## Phase B — HR dựng chiến dịch

- [ ] **B1** `feat/fr-h01-company` — FR-H01 · Hồ sơ doanh nghiệp
- [ ] **B2** `feat/fr-h02-jobs` — FR-H02 · CRUD tin tuyển dụng + mẫu giấy mời phỏng vấn
- [ ] **B3** `feat/fr-h03-rubric` — FR-H03 · Tiêu chí + trọng số, thang điểm mặc định/tuỳ chọn

**Xong khi:** không lưu được rubric có tổng trọng số ≠ 100%, chặn ở cả UI lẫn API.

## Phase C — Ứng viên nộp đơn

- [ ] **C1** `feat/fr-u01-resume` — FR-U01 · Hồ sơ cá nhân, upload nhiều phiên bản CV
- [ ] **C2** `feat/fr-u02-apply` — FR-U02 · Ứng tuyển + consent bắt buộc + chống nộp trùng
- [ ] **C3** `feat/fr-u03-tracking` — FR-U03 · Theo dõi 5 trạng thái + lịch sử
- [ ] **C4** `feat/fr-u06-withdraw` — FR-U06 · Rút đơn (soft state)

**Xong khi:** nộp trùng job trong cùng chu kỳ bị chặn ở **tầng DB**, không chỉ ở UI.

## Phase D — AI đọc và chấm

- [ ] **D1** `feat/fr-c04-parsing` — FR-C04 · Trích xuất CV → JSON, chạy nền, validate schema
- [ ] **D2** `feat/fr-h04-scoring` — FR-H04 · Chấm **từng tiêu chí riêng** + evidence
- [ ] **D3** `feat/fr-h05-aggregate` — FR-H05 · Tổng hợp có trọng số + xếp hạng (Java thuần)
- [ ] **D4** `feat/fr-h06-explain` — FR-H06 · Báo cáo giải thích, mọi luận điểm có evidence

**Xong khi:** unit test `ScoreAggregator` pass; đổi trọng số → thứ hạng đổi đúng công thức;
không tồn tại cột/field nào tên `verdict`, `label`, `isQualified`, `passed`.

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

- [ ] `chore/hardening` — rate limit, xử lý lỗi LLM (timeout/quota), chi phí token, presigned URL cho file CV
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