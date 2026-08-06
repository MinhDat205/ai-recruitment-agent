---
name: fr-implement
description: Quy trình triển khai một mã yêu cầu chức năng (FR-C, FR-H, FR-U) của dự án AI Recruitment Agent. Dùng khi người dùng nói "làm FR-xxx", "triển khai FR-xxx", "bắt đầu nhánh feat/fr-xxx", hoặc yêu cầu code một chức năng có mã FR.
---

# Triển khai một mã FR

## Bước 1 — Đọc trước khi lập kế hoạch

Đọc theo đúng thứ tự này, không bỏ bước:

1. `docs/SRS.md` — tìm đúng mã FR được yêu cầu. Đây là nguồn sự thật. Chú ý cột "Nguyên tắc".
2. `docs/PHASES.md` — tìm mục tương ứng (A1, B2, D3...). Đọc kỹ hai phần:
   - **"Xong khi"** — đây là tiêu chí nghiệm thu, kế hoạch phải đạt được hết
   - **"AI hay làm sai"** — đọc và tránh đúng những lỗi đó
3. `CLAUDE.md` — mục "Ranh giới không được vượt" và mục "Giao diện"
4. `backend/src/main/resources/db/migration/V1__init_schema.sql` — các bảng liên quan.
   Ghi chú thiết kế ở cuối file giải thích vì sao một số cột trông thừa nhưng bắt buộc.

Nếu chức năng chạm tới frontend, đọc thêm `docs/UI_GUIDE.md`.

## Bước 2 — Xác định phạm vi

Chỉ làm **đúng một mã FR**. Không làm trước việc của FR khác, kể cả khi thấy tiện.

Nếu phát hiện FR này phụ thuộc một FR chưa làm, **dừng lại và báo**, đừng tự làm luôn phần phụ thuộc.
Bảng phụ thuộc ở cuối `docs/PHASES.md`.

## Bước 3 — Ràng buộc bắt buộc

**Schema**
- Schema đã đầy đủ trong `V1__init_schema.sql`. **Không tạo migration mới** trừ khi thật sự thiếu.
- Nếu buộc phải đổi schema: tạo file `V2__`, `V3__`... **Không bao giờ sửa `V1__`**
  (Flyway lưu checksum, sửa file cũ làm hỏng mọi máy khác).
- Entity phải khớp chính xác cột đã có. `ddl-auto: validate` sẽ chặn app khởi động nếu lệch — đó là
  tính năng, không phải lỗi cần né bằng cách đổi sang `update`.

**Bảo mật**
- Kiểm tra quyền ở tầng API, không chỉ ẩn nút ở UI.
- Kiểm tra cả **vai trò** lẫn **quyền sở hữu bản ghi**. `@PreAuthorize("hasRole('HR')")` không ngăn
  HR A sửa dữ liệu của HR B.
- Không bao giờ trả `password_hash` hoặc thông tin nội bộ trong DTO.

**Ranh giới AI/Backend** (xem `CLAUDE.md`)
- Package `ai/` không được import `scoring/ScoreAggregator`.
- Không tạo cột hay field tên `verdict`, `label`, `isQualified`, `passed`, `recommendation`.
- Không thêm ngưỡng phân loại, không tô màu điểm theo ngưỡng.

**Frontend**
- Dùng token màu trong `frontend/src/index.css` (`bg-brand`, `text-ink-muted`...).
  Không hardcode mã hex.
- Icon dùng `lucide-react`. Không dùng emoji trong UI.

## Bước 4 — Thứ tự triển khai

Backend: entity → repository → DTO → service → controller → cấu hình security nếu cần
Frontend: API client → hook/query → component → route

Viết test cho phần logic nghiệp vụ thuần (không phụ thuộc LLM hay HTTP), đặc biệt là các phép tính.

## Bước 5 — Trước khi báo hoàn thành

- Chạy `./mvnw test` (Windows: `.\mvnw.cmd test`), tự sửa lỗi trước khi báo.
- Đối chiếu lại từng gạch đầu dòng trong phần "Xong khi" của `docs/PHASES.md`.
- Liệt kê những gì **chưa** làm hoặc làm tạm, đừng im lặng bỏ qua.

Sau đó nhắc người dùng chạy skill `walkthrough` để sinh tài liệu giải thích cho nhánh này.
