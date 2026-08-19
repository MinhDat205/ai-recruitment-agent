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

## Bước 3 — Chia thành các đợt nhỏ

Không triển khai toàn bộ một mã FR trong một lượt. Chia thành **5-7 đợt nhỏ**, mỗi đợt là một phần
việc trọn vẹn có thể review độc lập (ví dụ với FR-H04/D2: entity+repository, rồi
precondition+tạo bản ghi, rồi phần gọi LLM độc lập tầng `ai/`, rồi orchestrator+state
service+scheduler, rồi endpoint đọc+frontend, rồi soát+tài liệu).

Mỗi đợt kết thúc theo đúng trình tự: **dừng → báo cáo diff → chờ người dùng duyệt → mới commit.**
Không tự commit khi chưa được duyệt, kể cả khi `.\mvnw.cmd test` đã xanh.

- **Đợt đầu tiên luôn là Plan Mode** — trình bày các quyết định thiết kế cần chốt trước khi viết
  code (ví dụ: input gửi LLM là gì, khoá tài nguyên lúc nào, trạng thái nào coi là "xong"), nêu rõ
  phương án đã chọn/đã loại và lý do, chờ duyệt kế hoạch rồi mới bắt đầu đợt kế tiếp.
- **Đợt cuối cùng luôn là**: chạy skill `srs-guard`, chạy skill `walkthrough`, và cập nhật
  `docs/ROADMAP.md` (tick mã FR vừa xong, xoá ghi chú tạm nếu có, thêm nợ kỹ thuật phát hiện được
  trong lúc làm vào mục `chore/hardening` nếu có).

Người dùng gửi từng đợt một qua các lượt trò chuyện riêng; không tự ý làm trước đợt chưa được giao,
kể cả khi thấy tiện làm luôn.

## Bước 4 — Ràng buộc bắt buộc

**Schema**
- Schema đã đầy đủ trong `V1__init_schema.sql`. **Không tạo migration mới** trừ khi thật sự thiếu.
- Nếu buộc phải đổi schema: **kiểm số hiệu kế tiếp thật** bằng cách liệt kê thư mục
  `backend/src/main/resources/db/migration/` trước khi đặt tên file — số hiệu tăng dần theo các
  migration đã có, không phải luôn là `V2__`/`V3__` (repo có thể đã đi tới `V4__` hoặc xa hơn tuỳ
  nhánh đang ở đâu). **Không bao giờ sửa `V1__`** hay bất kỳ file migration nào đã tồn tại (Flyway
  lưu checksum, sửa file cũ làm hỏng mọi máy khác).
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

**Job nền và transaction** (xem CLAUDE.md mục 3c — đã lặp lại đúng hai lần ở D1 và D2, coi như quy
ước bắt buộc cho mọi job nền tiếp theo, không phải đặc thù riêng của hai nhánh đó)
- Method **ghi** của một job nền phải nằm ở một bean riêng (vd `*StateService`), được inject vào
  bean điều phối (`*Orchestrator`), không gọi qua self-invocation (`this.method()`) — self-invocation
  không đi qua proxy của Spring nên `@Transactional` trên method đó sẽ không mở transaction nào cả.
- **Không giữ transaction quanh lời gọi LLM.** Mẫu đúng: transaction ngắn để claim bản ghi → gọi
  LLM **ngoài** transaction → transaction ngắn khác để ghi kết quả. Gộp cả ba bước vào một
  `@Transactional` sẽ giữ connection suốt thời gian chờ LLM (có thể hàng chục giây) và cạn pool khi
  có nhiều bản ghi chờ xử lý cùng lúc.
- Claim bản ghi bằng `UPDATE` có điều kiện, kiểm tra số dòng ảnh hưởng (vd
  `UPDATE ... SET status = 'RUNNING' WHERE id = ? AND status = 'PENDING'`), **không** dùng
  `SELECT ... FOR UPDATE SKIP LOCKED` (giữ transaction mở trong lúc chờ LLM — cùng vấn đề với ý
  trên). `@Modifying(clearAutomatically = true)` là bắt buộc trên query claim này.

**Frontend**
- Dùng token màu trong `frontend/src/index.css` (`bg-brand`, `text-ink-muted`...).
  Không hardcode mã hex.
- Icon dùng `lucide-react`. Không dùng emoji trong UI.

## Bước 5 — Thứ tự triển khai

Backend: entity → repository → DTO → service → controller → cấu hình security nếu cần
Frontend: API client → hook/query → component → route

Test phải có **cả case dương lẫn case âm**, không chỉ test đường thành công. Với mọi ngưỡng số
(trọng số, giới hạn ký tự, số lần retry...), test đủ ba mốc biên: **ngưỡng−1, đúng ngưỡng,
ngưỡng+1** — không bỏ qua mốc nào chỉ vì "chắc chắn đúng". Nếu một mốc biên không dựng được vì bị
constraint ở DB chặn cứng trước khi tới được mốc đó (ví dụ trigger tổng trọng số > 100% chặn ngay
ở tầng DB, không có API nào tạo ra được dữ liệu vượt ngưỡng để làm fixture), **nói rõ lý do trong
báo cáo** thay vì bịa cách lách qua DB để ép test dựng được tình huống đó.

## Bước 6 — Trước khi báo hoàn thành

- Chạy `./mvnw test` (Windows: `.\mvnw.cmd test`), tự sửa lỗi trước khi báo.
- Đối chiếu lại từng gạch đầu dòng trong phần "Xong khi" của `docs/PHASES.md`.
- Liệt kê những gì **chưa** làm hoặc làm tạm, đừng im lặng bỏ qua.

Đây là bước cuối của MỘT đợt, không phải cuối cả mã FR — xem lại Bước 3 cho việc phải làm ở đợt
thật sự cuối cùng (`srs-guard`, `walkthrough`, cập nhật `docs/ROADMAP.md`).
