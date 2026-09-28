# Hướng dẫn giao diện — AI Recruitment Agent

Material Design 3 (MD3) là hệ thống thiết kế (vai trò màu, thang chữ, hình khối, độ nổi, trạng
thái tương tác, loại component); bố cục job board Việt Nam và mật độ thông tin cao được giữ
nguyên. Phong cách tham chiếu: job board Việt Nam (CareerViet, VietnamWorks, TopCV) — nền sáng,
mật độ thông tin cao, bố cục dạng thẻ, xanh dương làm màu thương hiệu.

> **Ranh giới:** mô phỏng *quy ước bố cục và hệ màu*, không sao chép logo, tên thương hiệu,
> hình ảnh hay file CSS của bất kỳ trang nào. Dự án dùng tên và logo riêng.

---

## 0. Phạm vi áp dụng

Bắt buộc cho mọi màn hình của 21 FR bổ sung. Màn hình cũ giữ nguyên tới nhánh
`refactor/ui-md3-legacy`; khi làm FR mới, không tự sửa màn hình cũ trừ khi REQUIREMENT.md/UI.md
đã duyệt yêu cầu.

---

## 1. Token

### 1a. Token hiện có

Khai báo một lần trong khối `@theme` GỐC của `frontend/src/index.css` (dòng 7-40), mọi component
dùng qua biến — không hardcode hex. Bảng dưới chép đúng từng dòng của khối đó:

| Token (biến CSS) | Class Tailwind | Giá trị | Dùng cho |
|---|---|---|---|
| `--color-brand` | `bg-brand`, `text-brand` | `#0078C9` | Màu chính: header, link, nút chính |
| `--color-brand-dark` | `hover:bg-brand-dark` | `#1E5C8B` | Hover, trạng thái active |
| `--color-brand-light` | `bg-brand-light` | `#E6F2FA` | Nền nhạt cho khối liên quan brand |
| `--color-accent` | `bg-accent` | `#1AC639` | Badge "Mới". Thực tế `bg-accent` đi qua `var(--accent)` của shadcn (xem 1b). Nút Ứng tuyển hiện dùng màu này nhưng không đạt tương phản (mục 6) — màn hình mới dùng `m3-tertiary`. |
| `--color-accent-dark` | `text-accent-dark` | `#008C45` | Chữ nhấn (lương), không dùng làm nền nút |
| `--color-warning` | `text-warning`, `bg-warning` | `#FF5B00` | Hạn nộp gần, tin gấp |
| `--color-danger` | `text-danger`, `bg-danger` | `#E11B3E` | Từ chối, lỗi |
| `--color-ink` | `text-ink` | `#1F2937` | Chữ chính |
| `--color-ink-muted` | `text-ink-muted` | `#6B7280` | Chữ phụ, meta |
| `--color-line` | `border-line` | `#E7E7E9` | Viền thẻ, đường phân cách |
| `--color-surface` | `bg-surface` | `#FFFFFF` | Nền thẻ |
| `--color-canvas` | `bg-canvas` | `#F0F0F0` | Nền trang |
| `--color-status-pending` | `bg-status-pending` | `#F3F4F6` | Badge "Chờ duyệt" — nền |
| `--color-status-pending-text` | `text-status-pending-text` | `#4B5563` | Badge "Chờ duyệt" — chữ |
| `--color-status-invited` | `bg-status-invited` | `#E6F2FA` | Badge "Đã mời phỏng vấn" — nền |
| `--color-status-invited-text` | `text-status-invited-text` | `#0078C9` | Badge "Đã mời phỏng vấn" — chữ |
| `--color-status-hired` | `bg-status-hired` | `#E8F8EC` | Badge "Trúng tuyển" — nền |
| `--color-status-hired-text` | `text-status-hired-text` | `#008C45` | Badge "Trúng tuyển" — chữ |
| `--color-status-rejected` | `bg-status-rejected` | `#FDECEF` | Badge "Bị từ chối" — nền |
| `--color-status-rejected-text` | `text-status-rejected-text` | `#E11B3E` | Badge "Bị từ chối" — chữ |
| `--color-status-withdrawn` | `bg-status-withdrawn` | `#F3F4F6` | Badge "Đã rút đơn" — nền |
| `--color-status-withdrawn-text` | `text-status-withdrawn-text` | `#9CA3AF` | Badge "Đã rút đơn" — chữ |
| `--radius-card` | `rounded-(--radius-card)` | `8px` | Bo góc thẻ |
| `--radius-badge` | `rounded-(--radius-badge)` | `4px` | Bo góc badge, tag |
| `--font-sans` | (mặc định `font-sans`) | `"Be Vietnam Pro", "Inter", system-ui, ...` | Font toàn app |

25 dòng, khớp chính xác khối `@theme` gốc (12 màu cơ bản + 10 màu trạng thái đơn + 2 bán kính bo
góc + 1 font). Dùng trong component: `bg-brand`, `text-ink-muted`, `border-line`,
`rounded-(--radius-card)`.

### 1b. Bẫy

- **Token mới phải khai trong khối `@theme` GỐC (dòng 7-40), không khai trong `@theme inline`**
  (khối shadcn sinh ra, dòng 67-107) — `@theme inline` nằm SAU nên đè mọi biến trùng tên (đã dính:
  `--font-sans`, `--color-accent`).
- **Tailwind v4 tham chiếu biến bằng ngoặc TRÒN**: `rounded-(--radius-card)`, KHÔNG phải
  `rounded-[--radius-card]` (cú pháp v3, sinh CSS không hợp lệ, âm thầm mất tác dụng).
- **`--accent` ở `:root` (dòng 122 `index.css`) đồng thời là màu tô dùng chung của component
  shadcn**, không riêng cho nút "Ứng tuyển" — ví dụ `components/ui/select.tsx:113` có
  `focus:bg-accent focus:text-accent-foreground`. Muốn đổi màu nút "Ứng tuyển" phải dùng token
  riêng `m3-tertiary` (mục 1c), KHÔNG sửa `--accent` — sửa `--accent` sẽ làm mọi dropdown/menu
  shadcn dùng `focus:bg-accent` đổi màu theo, không chỉ riêng nút Ứng tuyển.

### 1c. Token vai trò MD3 (đích — khai ở `chore/ui-md3-foundation`)

Chưa tồn tại trong `index.css` hiện tại. BẮT BUỘC dùng tiền tố `m3-` vì shadcn đã chiếm
`--color-primary/secondary/accent/muted/destructive`. Bảng ánh xạ:

| Token MD3 | Giá trị / ánh xạ |
|---|---|
| `m3-primary` | `#0078C9` (brand) |
| `m3-on-primary` | `#FFFFFF` |
| `m3-primary-container` | `#E6F2FA` (brand-light) |
| `m3-on-primary-container` | `#1E5C8B` (brand-dark) |
| `m3-tertiary` | sắc xanh cho hành động Ứng tuyển (phải đạt ≥4.5:1 với chữ trắng; `#1AC639` và `#008C45` đều không đạt — xem mục 6, kết quả V2) |
| `m3-error` | `#E11B3E` |
| `m3-surface` | `#FFFFFF` |
| `m3-surface-container` | `#F0F0F0` (canvas) |
| `m3-surface-container-high` | sắc xám nhạt dùng cho khối nội dung AI |
| `m3-on-surface` | `#1F2937` |
| `m3-on-surface-variant` | `#6B7280` |
| `m3-outline-variant` | `#E7E7E9` |

Không thêm màu thương hiệu mới.

### 1d. Thang chữ

Giữ các cỡ hiện có, gán tên vai trò MD3:

| Vai trò MD3 | Cỡ/line-height | Trọng lượng | Dùng cho |
|---|---|---|---|
| Headline Small | 24/32 | 600 | Tiêu đề trang |
| Title Large | 20/28 | 600 | Tiêu đề section |
| Title Medium | 16/24 | 500 | Tiêu đề card |
| Body Large | 16/24 | 400 | Nội dung dài (mô tả job) |
| Body Medium | 14/20 | 400 | Nội dung thường |
| Body Small | 13/18 | 400 | Meta (lệch MD3 12/16 có chủ đích để tiếng Việt có dấu dễ đọc) |
| Label Large | 14/20 | 500 | Nút |
| Label Medium | 12/16 | 500 | Chip, badge |

Font giữ Be Vietnam Pro/Inter (không dùng Roboto). Chỉ dùng hai trọng lượng: 400 và 500/600.

### 1e. Hình khối

Giữ giá trị hiện có, ánh xạ vào thang MD3:

- Extra-small `4px` — badge, tag, chip
- Small `8px` — card
- `6px` cho nút (giữ hiện trạng, không đổi sang dạng pill)
- Medium `12px` — dialog, menu
- Large `16px` — sheet

### 1f. Khoảng cách

Lưới 4px; chỉ dùng 4 / 8 / 12 / 16 / 24 / 32 / 48.

### 1g. Độ nổi

- Level 0 = phẳng + viền `outline-variant`
- Level 1 = `shadow-sm` (card khi hover)
- Level 3 = `shadow-md` (dialog, menu, sheet)

### 1h. Trạng thái tương tác

Hover lớp phủ 8%, focus 10% + outline 2px brand (đang có), pressed 10%, disabled chữ 38% / nền
12%. Vùng chạm tối thiểu 48×48px trên màn hình < sm.

---

## 2. Layout & điều hướng

Giữ container 1200px, lưới 12 cột, gutter 24px, breakpoint Tailwind hiện có
(`sm 640` · `md 768` · `lg 1024` · `xl 1280`). Ánh xạ lớp kích thước cửa sổ MD3: `< sm` = compact,
`sm–lg` = medium, `≥ lg` = expanded.

- **HR**: navigation drawer 240px khi `≥ lg`; navigation rail (icon + nhãn ngắn) khi `< lg`.
- **Ứng viên**: thanh tab ngang khi `≥ md`; khi `< md` dùng nút menu mở sheet (khu vực ứng viên có
  quá 5 đích nên không dùng thanh điều hướng dưới đáy).
- Không dùng hero ở trang HR; không dùng sidebar ở trang ứng viên.

---

## 3. Component chuẩn (MD3 → shadcn đang có)

- **Nút**: Filled = Chính; Filled tonal (primary-container) = hành động phụ quan trọng; Outlined =
  Phụ; Text = hành động nhẹ trong thẻ/dialog; nút Ứng tuyển dùng `m3-tertiary`. Kích thước giữ
  40px, trong bảng 32px.
- **Card**: outlined (level 0) là mặc định. Giữ nguyên đặc tả card việc làm nhưng BỎ emoji trong sơ
  đồ (khung dưới đã căn lại cho thẳng cột, mở rộng theo dòng dài nhất):

```
┌─────────────────────────────────────────────────────────┐
│ ┌────┐  Chuyên viên phân tích dữ liệu        [Mới]      │
│ │LOGO│  CÔNG TY TNHH ABC                                │
│ │80px│  [Banknote] 15 - 25 triệu   [MapPin] Hồ Chí Minh │
│ └────┘  [Python] [SQL] [Remote]      Hạn: 30/09         │
└─────────────────────────────────────────────────────────┘
```

  Logo vuông 80px, bo 4px, viền `--color-line`, `object-fit: contain`. Tiêu đề `line-clamp-2`,
  hover đổi sang `--color-brand`. Lương màu `--color-accent-dark`. Tag: nền `--color-brand-light`,
  chữ `--color-brand`, bo 4px, 12px. Toàn thẻ là vùng bấm được, `hover:shadow-sm hover:border-brand`.
  Lưới: 2 cột `lg`, 1 cột `md` trở xuống. Icon dùng `lucide-react`: `Banknote` (lương), `MapPin`
  (địa điểm) — không dùng emoji.
- **Chip**: filter chip cho bộ lọc (U07, H15); input chip cho kỹ năng (U14); assist chip TRUNG TÍNH
  để hiện "điều kiện đã khớp" (U13, U15).
- **Tabs** (primary tabs) cho trang có tab (H09, U08, trang sửa Job); **Segmented button** cho lựa
  chọn 2–3 giá trị (giọng văn ở C07); **Dialog**; **Sheet**; **Snackbar** (toast) để xác nhận thao
  tác; **Linear progress** cho thao tác AI đồng bộ; **Search bar** (U13); **List** cho hộp thư
  (C06); **Badge số** cho mục chưa đọc.
- **Badge trạng thái đơn**: màn hình mới dùng lại `ApplicationStatusBadge` hiện có, KHÔNG tạo bộ
  màu mới. Bảng màu hiện tại (Trúng tuyển xanh lá, Bị từ chối đỏ — mục 1a `--color-status-hired`/
  `-rejected`) mâu thuẫn với nguyên tắc "trung tính"; đổi ở `refactor/ui-md3-legacy`.

---

## 4. Ràng buộc riêng của dự án

Đây là phần khác biệt so với một job board thông thường, và là phần dễ làm sai nhất.

**Màn hình xếp hạng ứng viên (FR-H05, FR-H07)**
- Hiển thị: thứ hạng, tổng điểm, điểm từng tiêu chí, nút mở giải thích.
- **Không** dùng thang màu đỏ-vàng-xanh cho tổng điểm. Không có nhãn "Phù hợp cao / Cần xem xét /
  Không phù hợp". Không có icon ✓ ✗. Những thứ này là phán quyết trá hình, vi phạm FR-H07.
- Điểm hiển thị dạng số và thanh tiến trình đơn sắc (`--color-brand`), không đổi màu theo ngưỡng.

**Màn hình giải thích điểm (FR-H06)**
- Mỗi tiêu chí là một khối gập/mở được. Mở ra phải thấy đoạn trích nguyên văn từ CV làm evidence.
- Trích dẫn hiển thị với viền trái `--color-brand`, nền `--color-brand-light`, font chữ thường.
- Không có điểm nào hiển thị mà không mở ra được evidence.

**Ô consent khi ứng tuyển (FR-U02)**
- Checkbox không tick sẵn. Nút "Nộp đơn" disabled cho tới khi tick.
- Nội dung ghi rõ CV sẽ được AI phân tích và chấm điểm để hỗ trợ HR đánh giá.

**Nội dung do AI tạo** (bản nháp tin nhắn, tin tuyển dụng nháp, tóm tắt, câu hỏi phỏng vấn, câu trả
lời hỏi đáp CV, gợi ý diễn đạt): luôn có nhãn "Do AI tạo" + icon (lucide `Sparkles`), nằm trong
khối `m3-surface-container-high`, và chỉ có hiệu lực sau khi người dùng bấm (Dùng bản nháp / Lưu /
Bỏ qua). Không có luồng tự gửi.

**Trích dẫn từ CV** (C08, H13) dùng đúng kiểu hiển thị evidence của FR-H06 (mục "Màn hình giải
thích điểm" ở trên).

**Lý do gợi ý/khớp** (U13, U15): chỉ là chip trung tính liệt kê điều kiện đã khớp. Không %, không
thanh màu, không nhãn "phù hợp".

**So sánh ứng viên** (H14): không đánh dấu người thắng; khác biệt chỉ tô nền trung tính.

**Ẩn danh** (H16): hiển thị "Ứng viên #<mã>" + banner "Đang ở chế độ ẩn danh".

**Dữ liệu thiếu**: hiện nhãn "Chưa có dữ liệu", không ẩn mục đó.

**Mức lương mong muốn của ứng viên** (U14) chỉ hiển thị cho chính ứng viên.

---

## 5. Trạng thái rỗng, tải, lỗi

- Mọi danh sách phải có empty state: một dòng chữ `--color-ink-muted` + một hành động gợi ý.
- Card đang tải dùng skeleton xám, không dùng spinner toàn trang.
- Job nền (parse CV, chấm điểm) hiển thị trạng thái theo `parse_status` / `scoring_runs.status`,
  không để người dùng nhìn màn hình trắng chờ.
- **Thao tác AI đồng bộ** (K3): nút bị khoá + linear progress ngay tại chỗ; hết thời gian chờ hoặc
  lỗi thì hiện thông báo tiếng Việt kèm nút Thử lại; không chặn cả trang.
- **Không có quyền / không tồn tại (403/404)**: cùng một thông điệp "Không tìm thấy", không để lộ
  bản ghi có tồn tại hay không.

---

## 6. Khả năng tiếp cận

- Tỉ lệ tương phản tối thiểu 4.5:1 cho chữ. `--color-warning` trên nền trắng **không đạt** — chỉ
  dùng làm màu nền badge với chữ trắng, không dùng làm màu chữ.
- **Kết quả đo (V2)**: chữ trắng trên `--color-accent` (`#1AC639`) đạt **2.29:1**; trên
  `--color-accent-dark` (`#008C45`) đạt **4.34:1**. **Cả hai đều dưới 4.5:1** — nút "Ứng tuyển"
  hiện tại (`ApplyButton.tsx`, `bg-accent` + chữ trắng) không đạt tiếp cận. Xử lý bằng token
  `m3-tertiary` (mục 1c) khi làm `chore/ui-md3-foundation`.
- Mọi icon-only button phải có `aria-label`.
- Không truyền đạt thông tin chỉ bằng màu — badge trạng thái luôn kèm chữ.

---

## 7. Bản đồ màn hình

Quy tắc: route mới dùng tiền tố `/candidate/` hoặc `/hr/`. Mỗi UI.md phải chỉ ra route của mình
trong bảng này. Muốn thêm hoặc đổi route thì sửa bảng này trong cùng đặc tả được duyệt, không tự
thêm lúc code.

### Công khai

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| `/` | Việc làm | Hiện có | FR-C02 |
| `/jobs/:id` | Chi tiết tin tuyển dụng | Hiện có | FR-C02 |
| `/companies/:id` | Hồ sơ doanh nghiệp | Hiện có | FR-C02, FR-H01 |
| `/login` | Đăng nhập | Hiện có | FR-C01 |
| `/register` | Đăng ký (xong → ★`/candidate/onboarding`) | Hiện có | FR-C01, ★FR-U14 |

### Ứng viên

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| `/candidate` | "Việc làm" | Hiện có | FR-U07 (bộ lọc), FR-U13 (ô tìm bằng mô tả), FR-U15 (khối Gợi ý cho bạn) |
| `/candidate/dashboard` | "Bảng tin" | Hiện có | FR-U11 (thống kê), FR-U15 (gợi ý) |
| `/candidate/profile` | "Hồ sơ và CV" | Hiện có | FR-U01, FR-U14 (mục mong muốn công việc) |
| ★`/candidate/resumes/new`, ★`/candidate/resumes/:id/edit` | CV builder | ★Mới | FR-U12 |
| `/candidate/resumes/:id/improvement-suggestions` | Gợi ý cải thiện CV | Hiện có | FR-U05 |
| ★`/candidate/resumes/:id/qa` | Hỏi đáp CV | ★Mới | FR-C08 |
| `/candidate/applications` | "Đơn ứng tuyển" | Hiện có | FR-U03 |
| ★`/candidate/applications/:id` | Chi tiết đơn ứng tuyển | ★Mới | FR-U08; FR-U10 (chọn giờ); FR-U09 (câu trả lời); FR-C06 (trao đổi); rút đồng ý |
| ★`/candidate/messages` | "Tin nhắn" | ★Mới | FR-C06 (hộp thư) |
| `/candidate/notifications` | Thông báo | Hiện có | FR-C03 |
| `/jobs/:id/apply` | Nộp đơn ứng tuyển | Hiện có | FR-U02, FR-U09 |
| ★`/candidate/onboarding` | Khai hồ sơ nghề nghiệp (bỏ qua được) | ★Mới | FR-U14 |

### HR

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| `/hr` | "Dashboard" | Hiện có | FR-H08 |
| `/hr/jobs` | "Tin tuyển dụng" | Hiện có | FR-H02 |
| `/hr/jobs/new` | Tạo tin (nút "Tạo tin bằng AI") | Hiện có | FR-H02, ★FR-H11 |
| `/hr/jobs/:id/edit` | Sửa tin — tab "Thông tin tin tuyển dụng" (bật ẩn danh), "Mẫu giấy mời phỏng vấn", "Rubric chấm điểm", "Ứng viên" (chọn 2–3 đơn → So sánh) + ★tab "Câu hỏi sàng lọc" | Hiện có + ★tab mới | FR-H02, FR-H03, FR-H07; ★FR-H16, ★FR-H10, ★FR-H14 |
| ★`/hr/jobs/:id/compare` | So sánh ứng viên | ★Mới | FR-H14 |
| `/hr/candidates` | "Ứng viên" | Hiện có | FR-H08 |
| ★`/hr/applications/:id` | Trang hồ sơ đơn ứng tuyển — tab CV & điểm, Giải thích, Sàng lọc, Câu hỏi phỏng vấn, Trao đổi, Hỏi đáp CV, Lịch sử; thao tác: quyết định, giấy mời nhiều khung giờ, Thêm vào kho | ★Mới | FR-H09; FR-H04, FR-H05, FR-H06, FR-H07, FR-H13, FR-C06, FR-C08, FR-H12, FR-H15 |
| ★`/hr/talent-pools`, ★`/hr/talent-pools/:id` | "Kho ứng viên" | ★Mới | FR-H15 |
| ★`/hr/messages` | "Tin nhắn" | ★Mới | FR-C06 |
| `/hr/company` | "Hồ sơ công ty" | Hiện có | FR-H01 |
| `/hr/notifications` | Thông báo (vào từ chuông ở topbar, không nằm trong sidebar) | Hiện có | FR-C03 |

---

## 8. Mẫu UI.md

Các mục bắt buộc:

1. Trạng thái
2. Route (lấy từ mục 7)
3. Điểm vào (từ đâu tới màn hình này)
4. Bố cục (khung ASCII)
5. Component (theo mục 3)
6. Trạng thái hiển thị (đang tải / rỗng / lỗi / không có quyền / thiếu dữ liệu / AI đang xử lý)
7. Nội dung chữ (nhãn, thông báo lỗi tiếng Việt có dấu)
8. Responsive (compact / medium / expanded)
9. Khả năng tiếp cận
10. Không được làm
