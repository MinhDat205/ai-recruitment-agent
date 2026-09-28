---
name: fr-implement
description: Quy trình triển khai một mã yêu cầu chức năng (FR-C, FR-H, FR-U) của dự án AI Recruitment Agent, gồm cả 18 FR đã hoàn thành và 21 FR bổ sung đang đặc tả. Dùng khi người dùng nói "làm FR-xxx", "triển khai FR-xxx", "bắt đầu nhánh feat/fr-xxx", "bắt đầu feat/fr-u07-...", hoặc yêu cầu code một chức năng có mã FR.
---

# Triển khai một mã FR

## Bước 1 — Đọc trước khi lập kế hoạch

Đọc theo đúng thứ tự, không bỏ bước. Tách theo loại FR:

**FR đã hoàn thành** (FR-C01–C04, FR-H01–H08, FR-U01–U06) hoặc nhánh `fix/`/`chore/`:
1. `docs/SRS.md` — tìm đúng mã FR ở Mục 1/2/3. Đây là nguồn sự thật.

**FR bổ sung** (FR-C05–C08, FR-H09–H16, FR-U07–U15):
1. `docs/features/README.md` — vị trí của FR trong thứ tự thực hiện, FR trước/sau nó, phạm vi
   không được lấn sang.
2. `docs/features/<nhóm>/<mã>/REQUIREMENT.md` và `UI.md`.
   **CỔNG CHẶN**: nếu MỘT TRONG HAI file không có dòng trạng thái `ĐÃ DUYỆT <ngày>` — DỪNG NGAY,
   báo trạng thái hiện tại của cả hai file, KHÔNG lập kế hoạch tiếp.

Sau đó, cả hai loại đều đọc:

2. `CLAUDE.md` §2 (nguyên tắc bất di bất dịch), §3c (job nền/transaction), §3d (thành phần dùng
   chung K1-K4), §6 (quy trình chung + tiểu mục "Quy trình cho chức năng bổ sung"), §7 (ranh giới).
3. `docs/UI_GUIDE.md` nếu chức năng chạm frontend — bắt buộc đọc mục 7 "Bản đồ màn hình" (route
   phải khớp đúng bảng đó, không tự đặt route khác) và mục 8 nếu phải viết `UI.md` mới.
4. TOÀN BỘ file trong `backend/src/main/resources/db/migration/` (không chỉ file đầu tiên) — liệt
   kê thư mục để biết chính xác schema hiện tại và số hiệu đã dùng tới đâu.

Không còn `docs/PHASES.md` — bỏ qua mọi hướng dẫn cũ (kể cả từ phiên trước) nhắc tới file này.

## Bước 2 — Xác định phạm vi & phụ thuộc

Chỉ làm **đúng một mã FR**. Không làm trước việc của FR khác, kể cả khi thấy tiện.

Lấy danh sách phụ thuộc:
- FR bổ sung: dòng "Phụ thuộc" trong `REQUIREMENT.md`.
- FR cũ: không cần tra phụ thuộc.

Kiểm cột Trạng thái ở **mục 0** của `docs/SRS.md`: bất kỳ phụ thuộc nào chưa "Đã hoàn thành" —
DỪNG và báo, đừng tự làm luôn phần phụ thuộc.

Kiểm CLAUDE.md §3d: nếu FR này là nơi "Xây lần đầu" một thành phần K1-K4 — kế hoạch phải có đợt
riêng cho thành phần đó (không lồng vào đợt khác, vì thành phần dùng chung cần review kỹ hơn logic
riêng của một FR). Nếu FR "Dùng bởi" một K nào mà K đó CHƯA được FR nào xây xong — DỪNG và hỏi,
đừng tự cài một bản riêng cho FR này.

## Bước 3 — Chia thành các đợt nhỏ

Không triển khai toàn bộ một mã FR trong một lượt. Chia thành **5-7 đợt nhỏ**, mỗi đợt là một phần
việc trọn vẹn có thể review độc lập (ví dụ: entity+repository, rồi phần AI độc lập ở `ai/`, rồi
orchestrator+state service+scheduler, rồi endpoint+frontend, rồi soát+tài liệu).

Mỗi đợt kết thúc theo đúng trình tự: **dừng → báo cáo diff → chờ người dùng duyệt → mới commit.**
Không tự commit khi chưa được duyệt, kể cả khi test đã xanh.

**Đợt đầu tiên luôn là Plan Mode**, ngoài các quyết định thiết kế thường lệ (input gửi LLM là gì,
khoá tài nguyên lúc nào, trạng thái nào coi là "xong"...), bắt buộc có thêm 2 phần:

- **"Đối chiếu đặc tả với code"**: liệt kê MỌI chỗ `REQUIREMENT.md`/`UI.md` lệch với code/schema
  thật (tên bảng/cột/enum/endpoint không tồn tại, hành vi mô tả khác hành vi hiện có...). Có lệch
  thì DỪNG và báo — không tự "sửa cho khớp" theo hướng nào, không tự chọn cách hiểu.
- **"Kế hoạch nghiệm thu"**: mỗi gạch đầu dòng trong mục "Xong khi" của `REQUIREMENT.md` (hoặc tiêu
  chí tương ứng ở `docs/SRS.md` nếu là FR cũ) ánh xạ tới một test tự động cụ thể hoặc một bước test
  tay cụ thể; mỗi mục "AI hay làm sai" của `REQUIREMENT.md` ghi rõ kế hoạch tránh đúng lỗi đó bằng
  cách nào.

**Đợt cuối cùng luôn là**: chạy skill `srs-guard`, chạy skill `walkthrough`, và:
- FR bổ sung: đổi dòng trạng thái ở `REQUIREMENT.md` **và** `UI.md` thành `ĐÃ HOÀN THÀNH <ngày>`;
  đổi cột Trạng thái của mã đó ở **mục 0** `docs/SRS.md` thành "Đã hoàn thành"; tick dòng tương ứng
  ở `docs/ROADMAP.md` mục "Giai đoạn 2"; ghi nợ kỹ thuật phát hiện được NGAY DƯỚI dòng đó (không
  dồn vào mục `chore/hardening` — mục đó chỉ dành cho các FR cũ).
  FR cũ: tick/ghi chú như quy ước sẵn có ở `docs/ROADMAP.md`.
- Nếu trong lúc code, đặc tả đã duyệt phải sửa lại và được duyệt lại — ghi rõ ngày duyệt lại đó
  trong walkthrough (mục "Lệch so với đặc tả").

Người dùng gửi từng đợt một qua các lượt trò chuyện riêng; không tự ý làm trước đợt chưa được giao.

## Bước 4 — Ràng buộc bắt buộc

**Schema**
- FR bổ sung thường CẦN migration mới (khác phần lớn FR cũ, vốn đã có đủ schema từ trước). Trước
  khi đặt tên file: liệt kê thư mục `backend/src/main/resources/db/migration/` thật, lấy số hiệu
  **lớn nhất hiện có + 1** — không đoán, không giả định trước một số cụ thể.
- Mỗi FR tối đa MỘT file migration, trừ khi có lý do cụ thể ghi rõ trong kế hoạch (ví dụ tách bảng
  danh mục và bảng liên kết vì thứ tự áp dụng).
- Danh mục dữ liệu cố định (ví dụ FR-C05 — ngành nghề, tỉnh/thành) seed ngay trong chính migration
  đó bằng `INSERT`, không viết script seed riêng.
- **Không bao giờ sửa file migration đã tồn tại.** Entity phải khớp chính xác cột đã có,
  `ddl-auto: validate` chặn khởi động nếu lệch — đây là tính năng, không phải lỗi cần né.

**Bảo mật**
- Kiểm tra quyền ở tầng API, không chỉ ẩn nút ở UI.
- Kiểm tra cả **vai trò** lẫn **quyền sở hữu bản ghi**. `@PreAuthorize("hasRole('HR')")` không ngăn
  HR A sửa dữ liệu của HR B.
- Không bao giờ trả `password_hash` hoặc thông tin nội bộ trong DTO.

**Ranh giới AI/Backend** (xem CLAUDE.md §2, §3d, §7 — không chép lại, chỉ checklist khi code):
- Gọi LLM/embedding đồng bộ trong request người dùng CHỈ khi FR nằm trong danh sách ngoại lệ K3
  (§7) và đủ 6 điều kiện bắt buộc ghi ở đó — FR ngoài danh sách muốn gọi đồng bộ thì dừng lại hỏi.
- Mọi ngữ cảnh CV/đơn gửi AI của FR bổ sung đi qua K1 (cả phía HR lẫn ứng viên); với vai trò ứng
  viên, K1 loại bỏ điểm/rubric/giải thích/ghi chú — chặn bằng code, có test.
- Mọi trích dẫn CV AI trả về (không riêng FR-H04) phải qua bộ kiểm K2.
- Kết quả AI được lưu phải có đủ siêu dữ liệu K4 (model, phiên bản prompt, thời điểm sinh).
- AI không tự gửi/đăng/quyết định — nội dung AI chỉ có hiệu lực sau thao tác xác nhận của người
  dùng.
- Không lọc/hỏi/đưa thông tin nhân thân (ngày sinh, giới tính, hôn nhân, tôn giáo...) vào truy vấn
  lọc hay prompt.
- Package `ai/` không import `scoring/ScoreAggregator`. Không tạo field `verdict`/`label`/
  `isQualified`/`passed`/`recommendation`. Không thêm ngưỡng phân loại/tô màu theo điểm.

**Job nền và transaction** — xem CLAUDE.md §3c, áp dụng cho MỌI job nền mới, không chỉ các nhánh
đã dùng nó trước đây.

**Frontend** — nguồn duy nhất là `docs/UI_GUIDE.md`, không chép lại ở đây. 5 điểm dễ sai nhất khi
code FR mới:
- Token mới khai trong khối `@theme` GỐC của `index.css`, không khai trong `@theme inline`.
- Tailwind v4 tham chiếu biến bằng `rounded-(--radius-card)`, không phải `rounded-[--radius-card]`.
- Màn hình mới dùng token vai trò `m3-*` (UI_GUIDE mục 1c) — không dùng `bg-accent` cho nút Ứng
  tuyển hay bất kỳ hành động chính nào (đó là token dùng chung với component shadcn khác, đổi sẽ
  vỡ chỗ khác).
- Route lấy đúng từ bản đồ màn hình UI_GUIDE mục 7 — không tự thêm/đổi route lúc code; muốn đổi
  phải sửa bảng đó trong đặc tả đã duyệt trước, không tự thêm khi đang code.
- Không tự sửa màn hình cũ trừ khi `REQUIREMENT.md`/`UI.md` đã duyệt yêu cầu rõ.

## Bước 5 — Thứ tự triển khai

Backend: entity → repository → DTO → service → controller → cấu hình security nếu cần
Frontend: API client → hook/query → component → route

Test phải có **cả case dương lẫn case âm**, không chỉ test đường thành công. Với mọi ngưỡng số
(trọng số, giới hạn ký tự, số lần retry...), test đủ ba mốc biên: **ngưỡng−1, đúng ngưỡng,
ngưỡng+1** — không bỏ qua mốc nào chỉ vì "chắc chắn đúng". Nếu một mốc biên không dựng được vì bị
constraint DB chặn cứng trước khi tới được mốc đó, **nói rõ lý do trong báo cáo** thay vì bịa cách
lách qua DB để ép test dựng được tình huống đó.

## Bước 6 — Trước khi báo hoàn thành

- Chạy `./mvnw test` (Windows: `.\mvnw.cmd test`) và/hoặc `npm run build` + `npm run lint`, tự sửa
  lỗi trước khi báo.
- Đối chiếu lại từng gạch đầu dòng "Xong khi" — lấy từ `REQUIREMENT.md` (FR bổ sung) hoặc mô tả
  tương ứng ở `docs/SRS.md`/tiêu chí đã có (FR cũ).
- Liệt kê những gì **chưa** làm hoặc làm tạm, đừng im lặng bỏ qua.

Đây là bước cuối của MỘT đợt, không phải cuối cả mã FR — xem lại Bước 3 cho việc phải làm ở đợt
thật sự cuối cùng (`srs-guard`, `walkthrough`, cập nhật trạng thái + `docs/ROADMAP.md`).
