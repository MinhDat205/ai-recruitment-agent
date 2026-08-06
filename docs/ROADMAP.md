# Roadmap triển khai — chia theo mã FR

Nguyên tắc: **1 phase = 1 nhánh git = 1 phiên Claude Code**. Xong phase nào chạy được phase đó rồi mới sang phase kế.
Không nhảy sang phần AI khi phần CRUD nền chưa chạy — AI phụ thuộc dữ liệu đã parse.

---

## Phase 0 — Móng (chưa có gì thông minh)

- [ ] Khởi tạo repo, cấu trúc thư mục, lint/format, biến môi trường mẫu (`.env.example`)
- [ ] Docker compose: database + (tuỳ chọn) vector store
- [ ] Schema DB bản đầu: `users`, `companies`, `jobs`, `rubric_criteria`, `applications`, `resumes`, `scores`, `notifications`
- [ ] Seed dữ liệu giả: 1 HR, 3 ứng viên, 2 job

**Tiêu chí xong:** `docker compose up` + `npm run dev` lên được, mở trang trắng không lỗi.

## Phase 1 — FR-C01, FR-C02: Tài khoản & duyệt công khai

- [ ] Đăng ký / đăng nhập tách 2 role, hash mật khẩu (bcrypt/Argon2), phiên JWT hoặc session
- [ ] RBAC ở tầng API (middleware/guard), không chỉ ẩn UI
- [ ] Trang công khai: danh sách job, chi tiết job, hồ sơ doanh nghiệp — xem không cần login
- [ ] Chặn hành động (ứng tuyển, tạo tin) khi chưa login

**Tiêu chí xong:** ứng viên gọi API của HR → 403.

## Phase 2 — FR-H01, FR-H02, FR-H03: Doanh nghiệp, tin tuyển dụng, rubric

- [ ] CRUD hồ sơ doanh nghiệp
- [ ] CRUD job posting (tạo / sửa / tạm dừng / xoá)
- [ ] Mỗi job bắt buộc gắn 1 bộ rubric
- [ ] Rubric: thêm tiêu chí + trọng số, **validate tổng = 100%**
- [ ] Thang điểm 1–5 mặc định dùng chung; HR tuỳ chọn ghi đè mô tả riêng
- [ ] Mẫu giấy mời phỏng vấn gắn theo job (để trống ô ngày giờ)

**Tiêu chí xong:** không thể lưu rubric có tổng trọng số ≠ 100%.

## Phase 3 — FR-U01, FR-U02, FR-U03: Ứng viên nộp đơn

- [ ] Hồ sơ cá nhân, upload nhiều phiên bản CV (PDF/DOCX), lưu file
- [ ] Tìm kiếm job theo từ khoá / địa điểm / danh mục
- [ ] Ứng tuyển: **unique constraint** (ứng viên, job, chu kỳ) — chỉ 1 CV/vị trí
- [ ] Checkbox consent bắt buộc, lưu lại thời điểm đồng ý
- [ ] Trang theo dõi trạng thái + lịch sử ứng tuyển

**Tiêu chí xong:** nộp trùng job → bị chặn ở tầng DB, không chỉ ở UI.

## Phase 4 — FR-C04: AI Resume Parsing

- [ ] Trích text từ PDF/DOCX
- [ ] Prompt parsing → JSON có schema: contact, education, experience, skills, certifications, projects
- [ ] Validate schema, retry 1 lần, lưu bản raw + bản đã parse
- [ ] Chạy nền (queue/job) — không chặn request upload
- [ ] Test với 5 CV thật đủ dạng (1 cột, 2 cột, tiếng Việt, tiếng Anh, scan)

**Tiêu chí xong:** upload CV → vài giây sau có JSON đúng trong DB.

## Phase 5 — FR-H04, FR-H05: Chấm điểm & xếp hạng

- [ ] Prompt scoring: input = CV JSON + 1 tiêu chí → output = điểm + evidence trích dẫn
- [ ] Chấm **từng tiêu chí riêng**, không gộp 1 lần gọi trả cả bảng điểm
- [ ] Backend tính tổng có trọng số + sắp xếp — code thuần, viết unit test
- [ ] Lưu lịch sử đánh giá (phiên bản prompt, model, thời điểm) phục vụ audit

**Tiêu chí xong:** unit test tổng điểm pass; đổi trọng số → thứ hạng đổi theo đúng công thức.

## Phase 6 — FR-H06, FR-H07, FR-C03: Giải thích, pipeline, thông báo

- [ ] Báo cáo giải thích bằng ngôn ngữ tự nhiên, mọi luận điểm có evidence
- [ ] Màn hình pipeline: danh sách đã xếp hạng → mở hồ sơ → Mời phỏng vấn / Từ chối
- [ ] Render giấy mời từ mẫu + tên ứng viên, HR điền ngày giờ, sửa được trước khi gửi
- [ ] Xác nhận kết quả cuối: Trúng tuyển / Bị từ chối
- [ ] Notification: web + email theo sự kiện đổi trạng thái

**Tiêu chí xong:** không tồn tại bất kỳ đường code nào tự động chuyển trạng thái đậu/rớt.

## Phase 7 — FR-U04, FR-U05, FR-U06: Tính năng cho ứng viên

- [ ] Embedding cho CV và JD, cosine similarity, gợi ý job trên bảng tin
- [ ] Gợi ý cải thiện CV: từ khoá thiếu, đoạn cần sửa, lộ trình học
- [ ] Rút đơn → chuyển `WITHDRAWN`, giữ nguyên điểm & lịch sử

**Tiêu chí xong:** rút đơn xong, thống kê ở FR-H08 vẫn đếm đúng.

## Phase 8 — FR-H08: Dashboard & audit

- [ ] Thống kê: tổng hồ sơ, tỷ lệ chuyển đổi giữa các vòng, hiệu suất từng chiến dịch
- [ ] Lọc/sắp xếp theo khoảng tổng điểm hoặc theo điểm 1 tiêu chí cụ thể
- [ ] Trang tra cứu toàn bộ lịch sử đánh giá AI của 1 ứng viên

## Phase 9 — Hoàn thiện

- [ ] Rate limit, xử lý lỗi LLM (timeout, quota), chi phí token
- [ ] Bảo mật file CV (signed URL, không public bucket)
- [ ] README + hướng dẫn cài đặt + demo script cho buổi bảo vệ
