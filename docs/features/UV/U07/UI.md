# FR-U07 — Giao diện

## 1. Trạng thái

ĐÃ HOÀN THÀNH (02/10/2026). Duyệt: 02/10/2026. Đặc tả chức năng: `REQUIREMENT.md` cùng thư mục —
mọi mã quy tắc (R-F, R-N, R-S, R-W, R-T, R-O, R-Q, R-U, R-L) tham chiếu từ file đó.

**Phạm vi MD3**: màn hình danh sách việc làm (`/`, `/candidate`) làm lại **toàn bộ** theo MD3 trong
FR này (R-L1/R-L3) — khác C05 (chỉ đổi thành phần mới, phần còn lại để `refactor/ui-md3-legacy`).
Sau FR này, `refactor/ui-md3-legacy` (Phase 2.1b) coi `PublicJobListPage`/`CandidateJobListPage`/
`JobCard` là "đã làm ở FR-U07", bỏ qua. `PublicJobDetailPage` chỉ sửa đúng phần chọn layout (R-L2) và
màu chữ lương (R-L1b — cùng đổi `text-accent-dark` → `text-m3-tertiary` như `JobCard`), phần còn lại
của trang vẫn để Phase 2.1b xử lý MD3.

## 2. Route

Không có route mới (R-L2 cố ý không tạo `/candidate/jobs/:id`). Các route chạm tới:

| Route | Màn hình | Phần U07 sửa |
|---|---|---|
| `/` | Trang Việc làm (khách/ứng viên chưa đăng nhập) | Toàn bộ: thanh lọc mới, danh sách, `JobCard` |
| `/candidate` | Trang Việc làm (ứng viên đã đăng nhập) | Toàn bộ, giống `/` + `CandidateLayout` |
| `/jobs/:id` | Chi tiết tin | Chọn layout theo vai trò (R-L2) + màu chữ lương (R-L1b); nội dung còn lại giữ nguyên |

Khi code (đợt cuối — mục 9 REQUIREMENT.md): bổ sung "FR-U07" vào cột FR của **hai dòng** `/` và
`/jobs/:id` trong `docs/UI_GUIDE.md` mục 7 — dòng `/candidate` đã có sẵn "FR-U07 (bộ lọc)" từ trước,
không cần sửa.

## 3. Điểm vào

- Thanh lọc nằm ngay trên danh sách, thay vị trí `HeroSearch` hiện tại.
- Trang chi tiết: vào từ card ở danh sách, hoặc dán URL `/jobs/:id` trực tiếp.

## 4. Bố cục

### 4a. Thanh lọc — Desktop (≥ `md`)

Thanh lọc **tự xuống dòng (wrap)** khi không đủ chỗ ngang — **không cuộn ngang ở bất kỳ khổ nào**
(thống nhất với mục 8, không phân biệt desktop/medium):

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ [Search] Từ khoá... [Tìm kiếm]  [Ngành nghề ▾]  [Tỉnh/thành ▾]  [Lương: Từ__ Đến__ ▾]│
│ [Hình thức ▾]  [Thời gian đăng ▾]                              [Xoá bộ lọc]          │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ Tìm thấy 12 việc làm                                   Sắp xếp: [Mới nhất ▾]        │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

(Hàng 1-2 là một khối `flex-wrap` — xuống dòng tự nhiên theo chiều rộng thật của viewport, mẫu trên
chỉ minh hoạ nội dung, không phải đúng 2 hàng cố định.)

**Thời điểm áp dụng từng điều kiện (R-U6 ở REQUIREMENT.md) — khác nhau theo loại control:**
- Combobox "Ngành nghề"/"Tỉnh/thành", popover "Hình thức", `<select>` "Thời gian đăng", `<select>`
  "Sắp xếp": **áp dụng ngay khi đổi**, không cần nút riêng.
- Ô "Từ khoá": **chỉ áp dụng khi nhấn Enter hoặc bấm nút "Tìm kiếm"** cạnh ô — không áp dụng theo
  từng phím gõ (giữ đúng hành vi hiện có của `HeroSearch.tsx`).
- Popover "Lương": có nút **"Áp dụng"** riêng ở cuối popover (cạnh checkbox "Ẩn tin không công bố
  lương") — chỉ ghi vào URL khi bấm nút này, không áp dụng theo từng phím gõ số.
- "Ngành nghề"/"Tỉnh/thành": `CatalogCombobox` tái dùng từ C05 (`features/catalog/CatalogCombobox`),
  thêm biến thể cho phép "Tất cả" (bỏ chọn = không lọc, đã có mục "Bỏ chọn" ở combobox C05).
- "Hình thức": popover 3 checkbox (Tại văn phòng / Kết hợp / Từ xa), trigger hiện số lượng đã chọn
  (ví dụ "Hình thức (2)") khi > 0.
- "Thời gian đăng": `<select>` đơn giản (không cần combobox tìm kiếm, chỉ 4 lựa chọn).
- "Lương": hai `<Input type="number">` nhỏ cạnh nhau trong một popover, đơn vị "triệu" ghi cố định
  bên phải mỗi ô; có checkbox "Ẩn tin không công bố lương" và nút "Áp dụng" ở cuối popover.
- "Sắp xếp": `<select>` riêng, tách khỏi khối lọc (không nằm trong popover nào) — luôn hiện, không
  thu vào sheet ở mobile (mục 8).
- "Xoá bộ lọc": chỉ hiện (không disabled, ẩn hẳn) khi có ≥ 1 điều kiện lọc đang áp dụng (không tính
  `sort`/`page`).

### 4b. Thanh lọc — 375px

Thu thành một nút "Lọc" (có badge số điều kiện đang áp) mở **bottom sheet**; "Sắp xếp" và ô từ khoá
đứng ngoài, ngay trên danh sách:

```
┌──────────────────────────────────┐
│ [Search] Từ khoá...               │
├──────────────────────────────────┤
│ [Filter] Lọc (3)      [Mới nhất ▾]│
├──────────────────────────────────┤
│ Tìm thấy 12 việc làm               │
└──────────────────────────────────┘
```

Bottom sheet (trượt từ dưới, cao tối đa 85vh, cuộn trong):

```
┌──────────────────────────────────┐
│ Lọc việc làm                 [X] │
├──────────────────────────────────┤
│ Ngành nghề                        │
│ [Chọn ngành nghề            ▾]   │
│ Tỉnh/thành                        │
│ [Chọn tỉnh/thành             ▾]   │
│ Mức lương (triệu VNĐ)             │
│ [Từ___] [Đến___]                  │
│ ☐ Ẩn tin không công bố lương      │
│ Hình thức làm việc                │
│ ☐ Tại văn phòng ☐ Kết hợp ☐ Từ xa │
│ Thời gian đăng                    │
│ [Mọi thời điểm              ▾]   │
├──────────────────────────────────┤
│ [Xoá bộ lọc]         [Áp dụng]   │
└──────────────────────────────────┘
```

- Sheet chỉ ghi vào URL khi bấm "Áp dụng" (tránh query lại liên tục khi đang gõ/chọn trong sheet);
  "Xoá bộ lọc" trong sheet xoá hết rồi đóng sheet luôn.
- Bấm ra ngoài sheet hoặc `[X]` → đóng, **không** áp dụng thay đổi chưa bấm "Áp dụng".

### 4c. Thẻ việc làm (`JobCard`) — sửa 3 lỗi ROADMAP Phase 2.1b

```
┌───────────────────────────────────────────┐
│ ┌────┐  Senior Java Backend Developer      │  ← hạn nộp góc phải, cùng hàng tiêu đề
│ │Logo│                  Hạn nộp: 25/11/2026│
│ └────┘  Công ty ABC                        │
│         25 - 35 triệu                      │
│         [TP. Hồ Chí Minh] [CNTT - Phần mềm]│
└───────────────────────────────────────────┘
```

- Logo trống (không có `logoUrl`): ô vuông nền `bg-m3-surface-container`, luôn hiện icon lucide
  `Building2`. **Điều chỉnh sau soát tay 02/10/2026**: bỏ phương án 2 ký tự đầu tên công ty — tên
  công ty nào cũng bắt đầu bằng "Công ty" nên chữ viết tắt luôn ra "CÔ", vô nghĩa với mọi tin.
- Lương: `text-m3-tertiary` (thay `text-accent-dark`).
- Hạn nộp: luôn có nhãn "Hạn nộp: " trước ngày (`dd/MM/yyyy`) — ngày đứng một mình dễ bị hiểu nhầm là
  ngày đăng tin. `text-m3-on-surface-variant text-m3-body-sm`, đặt góc phải trên (cùng hàng tiêu đề,
  dùng `flex justify-between` ở hàng đầu card).
- Tin "chưa chuẩn hoá" đang lọt vào kết quả theo R-N (chỉ khi đang có lọc ngành/tỉnh): thêm nhãn nhỏ
  dưới badge ngành/tỉnh — xem mẫu 4d.
- Tin lương ngoại tệ khi đang lọc theo lương (R-S5): thêm chữ nhỏ cạnh số lương — xem mẫu 4d.

### 4d. JobCard — nhãn phụ khi có bộ lọc đang áp dụng

Tin "chưa chuẩn hoá" còn giá trị cũ (R-N3):
```
25 - 35 triệu USD  (không áp dụng bộ lọc lương)
[Chưa chuẩn hoá] Giá trị cũ: "Quận 1, HCM"
```

Tin "thiếu hẳn" — không mã, không giá trị cũ (R-N4, chỉ khi đang lọc đúng field đó): chỉ nhãn, không
có dòng "Giá trị cũ" (không có gì để hiện):
```
[Chưa chuẩn hoá]
```

**Ngoại lệ (bổ sung đã duyệt 02/10/2026):** tin `work_mode = 'REMOTE'` thuộc nhánh "thiếu hẳn" ở
trường tỉnh/thành không hiện nhãn "Chưa chuẩn hoá" này khi lọc theo tỉnh/thành (REMOTE không cần
tỉnh/thành là thiết kế hợp lệ của C05, không phải dữ liệu thiếu) — tin vẫn nằm trong kết quả như
bình thường, chỉ không gắn nhãn; REMOTE còn giá trị cũ vẫn hiện nhãn kèm "Giá trị cũ" như mẫu trên.

Các dòng/nhãn trên chỉ hiện đúng lúc điều kiện lọc tương ứng đang áp dụng (lọc lương / lọc ngành hoặc
tỉnh) và tin đó thuộc đúng nhánh ngoại lệ (R-S5 / R-N1-R-N4) — không hiện khi không lọc.

## 5. Component (UI_GUIDE mục 3) và token `m3-*`

| Token | Dùng cho |
|---|---|
| `m3-tertiary` (`#007A3D`, 5.45:1, đã chốt ở `chore/ui-md3-foundation`) | Màu chữ lương trên `JobCard` (R-L1) **và trên `PublicJobDetailPage`** (R-L1b) — cùng một token cho cả hai nơi |
| `m3-on-surface` | Tiêu đề tin, nhãn chọn trong thanh lọc |
| `m3-on-surface-variant` | Hạn nộp, nhãn phụ, chữ "không áp dụng bộ lọc lương" |
| `m3-surface-container` | Nền ô logo trống (icon `Building2`, điều chỉnh sau soát tay 02/10/2026) |
| `m3-outline` | Viền input lương, viền trigger combobox/select trong thanh lọc (đồng bộ combobox C05) |
| `m3-surface` | Nền thanh lọc, nền bottom sheet |
| `m3-surface-container-high` | Nền nhãn "Chưa chuẩn hoá" (tái dùng nguyên từ C05, không đổi màu) |
| `m3-primary/8` | Hover/trạng thái chọn trong popover hình thức làm việc, mục đang chọn trong `<select>` thời gian đăng/sắp xếp |
| `shadow-m3-3` | Bottom sheet, popover lương/hình thức (cùng cấp dialog/menu theo UI_GUIDE mục Elevation) |
| `text-m3-label-md` / `text-m3-body-sm` / `text-m3-body-md` | Nhãn ô / chữ phụ (hạn nộp, số kết quả) / giá trị chính |

Component mới:
- **Thanh lọc** (`JobFilterBar`, tên gợi ý): bọc `CatalogCombobox` ×2 (ngành, tỉnh — thêm prop cho
  phép "Tất cả"), 2 `<Input type="number">` (lương), popover checkbox (hình thức), `<select>` (thời
  gian đăng, sắp xếp). KHÔNG thêm thư viện mới.
- **Bottom sheet lọc** (mobile): dựng từ `components/ui/popover.tsx` hoặc dialog có sẵn, định vị cố
  định đáy màn hình, không cần thư viện sheet riêng.
- **Danh sách + phân trang + đếm kết quả**: trích từ `JobList.tsx` hiện có, dùng chung cho cả hai
  trang (R-L3).

## 6. Trạng thái hiển thị

| Chỗ | Đang tải | Lỗi | Rỗng |
|---|---|---|---|
| Combobox ngành/tỉnh trong thanh lọc | Giống C05: trigger disabled + "Đang tải danh mục…" | Dòng lỗi + "Thử lại" (giống C05) | "Không có mục phù hợp." khi gõ tìm không ra |
| Danh sách việc làm | Skeleton card (giữ cơ chế hiện có của `JobList`) | Thông báo lỗi tải danh sách + nút "Thử lại" | "Không tìm thấy việc làm phù hợp với bộ lọc hiện tại." + nút "Xoá bộ lọc" (chỉ hiện nút khi đang có lọc) |
| Ô lương nhập sai (`salaryMin > salaryMax` hoặc âm) | — | Dòng lỗi đỏ dưới hai ô: "Lương tối thiểu phải nhỏ hơn hoặc bằng lương tối đa." / "Lương phải là số không âm." | — |
| URL có tham số lạ/hỏng | — | Không hiện lỗi — âm thầm bỏ tham số đó (R-U4), danh sách vẫn tải với các tham số còn hợp lệ | — |
| Bottom sheet (mobile) | — | Lỗi tải danh mục hiện ngay trong sheet, không chặn đóng sheet | — |
| Không có quyền | N/A — `GET /api/public/jobs` công khai, không cần đăng nhập, không có khái niệm "không có quyền" ở màn hình này | | |

## 7. Nội dung chữ

| Khoá | Chuỗi |
|---|---|
| Ô từ khoá | "Tìm theo tên vị trí tuyển dụng..." (chỉ khớp `jobs.title`, không khớp tên công ty —
`JobRepository.java:25`: `j.title ILIKE CAST(:titlePattern AS text)`, không có điều kiện nào khớp
tên công ty; giữ nguyên phạm vi `keyword` hiện có, không mở rộng) |
| Nhãn ô | "Ngành nghề", "Tỉnh/thành", "Mức lương (triệu VNĐ)", "Hình thức làm việc", "Thời gian đăng", "Sắp xếp" |
| Placeholder ngành/tỉnh | "Chọn ngành nghề", "Chọn tỉnh/thành" (giống C05) |
| Lương | "Từ", "Đến", đơn vị hiện cố định "triệu" cạnh mỗi ô |
| Checkbox ẩn thoả thuận | "Ẩn tin không công bố lương" |
| Hình thức làm việc | "Tại văn phòng", "Kết hợp", "Từ xa" (đúng `WORK_MODE_LABELS` hiện có, `jobLabels.ts:40-42`) |
| Thời gian đăng | "Mọi thời điểm" (mặc định), "24 giờ qua", "7 ngày qua", "30 ngày qua" |
| Sắp xếp | "Mới nhất" (mặc định), "Lương cao nhất" |
| Nút | "Tìm kiếm" (cạnh ô từ khoá, desktop); "Lọc" (trigger mobile, kèm số trong ngoặc khi >0); "Xoá bộ lọc"; "Áp dụng" (cuối popover lương trên desktop — mục 4a; và cuối bottom sheet trên mobile — mục 4b, cùng nhãn) |
| Số kết quả | "Tìm thấy {n} việc làm" — `n` là tổng số kết quả toàn bộ (`totalElements`), không phải số tin trên trang hiện tại |
| Rỗng | "Không tìm thấy việc làm phù hợp với bộ lọc hiện tại." |
| Lỗi lương | "Lương tối thiểu phải nhỏ hơn hoặc bằng lương tối đa." / "Lương phải là số không âm." |
| Lương Thoả thuận | "Thoả thuận" (đã có từ trước) |
| Ngoại tệ khi đang lọc lương | "(không áp dụng bộ lọc lương)" |
| Chưa chuẩn hoá trên card (khi đang lọc) | Còn giá trị cũ (R-N3): "Chưa chuẩn hoá" + "Giá trị cũ: \"{text}\"" (đúng nhãn đã có ở C05). Thiếu hẳn, không có giá trị cũ (R-N4): chỉ "Chưa chuẩn hoá", không có dòng "Giá trị cũ" |
| Logo trống | Không có chữ — luôn icon `Building2` (điều chỉnh sau soát tay 02/10/2026, bỏ phương án chữ viết tắt) |

## 8. Responsive

- **Compact (< `sm`)** và **375px cụ thể**: theo mục 4b — nút "Lọc" mở bottom sheet; "Sắp xếp" và ô
  từ khoá luôn hiện ngoài sheet; `JobCard` xếp 1 cột, vùng chạm nút/checkbox ≥ 48×48px (UI_GUIDE mục
  Elevation/chạm).
- **Medium (`sm`–`lg`)**: thanh lọc `flex-wrap` xuống nhiều hàng hơn (ít chỗ ngang hơn Expanded) —
  cùng cơ chế wrap của mục 4a, không cuộn ngang.
- **Expanded (≥ `lg`)**: thanh lọc wrap ít hàng hơn (thường đủ 1-2 hàng như mẫu mục 4a) nhờ nhiều chỗ
  ngang hơn — vẫn cùng cơ chế `flex-wrap`, không phải một bố cục cố định khác.
- `JobCard` giữ lưới hiện có của `JobList` (không đổi số cột theo breakpoint ngoài phần đã có).

## 9. Khả năng tiếp cận

- Combobox ngành/tỉnh giữ nguyên hành vi ARIA đã có ở C05 (`role="combobox"`, `listbox`/`option`,
  ↑/↓/Enter/Esc).
- Popover hình thức làm việc: mỗi checkbox có `<label>` bọc, trigger có `aria-expanded`.
- Bottom sheet: `role="dialog"` `aria-modal="true"`, focus chuyển vào sheet khi mở, trả focus về nút
  "Lọc" khi đóng; Esc đóng sheet (không áp dụng thay đổi chưa "Áp dụng", đúng mục 4b).
- Thông báo "Tìm thấy {n} việc làm" đặt trong vùng `aria-live="polite"` để người dùng dùng trình
  đọc màn hình biết kết quả đã đổi sau khi lọc, không cần focus lại danh sách.
- Lỗi ô lương gắn `aria-describedby` vào đúng input (giống mẫu lỗi form hiện có của dự án).
- Icon `Building2` trên ô logo trống có `aria-hidden="true"` (điều chỉnh sau soát tay 02/10/2026:
  icon luôn hiện khi không có `logoUrl`, không còn nhánh chữ viết tắt thay thế).

## 10. Không được làm

- Không màu đỏ/vàng/xanh theo mức lương hay theo "mức độ khớp bộ lọc" — chỉ `m3-tertiary` cố định cho
  mọi giá trị lương, không phân biệt cao/thấp.
- Không gán nhãn "Phù hợp"/"Không phù hợp"/icon tích-xanh theo bộ lọc.
- Không hiện nhãn "Chưa chuẩn hoá" khi KHÔNG có lọc ngành/tỉnh đang áp dụng (giữ đúng phạm vi C05 đã
  chốt cho trường hợp danh sách không lọc — R-N3).
- Không áp dụng thay đổi trong bottom sheet trước khi bấm "Áp dụng" (tránh gọi API liên tục khi đang
  chọn nhiều ô trong sheet). Tương tự trên desktop: không áp dụng ô lương theo từng phím gõ số trước
  khi bấm "Áp dụng" trong popover lương; không áp dụng ô từ khoá theo từng phím gõ trước khi Enter/bấm
  "Tìm kiếm" (R-U6).
- Không cuộn ngang thanh lọc ở bất kỳ khổ màn hình nào — luôn `flex-wrap` (mục 4a, mục 8).
- Không tạo route `/candidate/jobs/:id` riêng (R-L2).
- Không thêm thư viện UI mới cho bottom sheet/combobox/select.
- Không hiện số ngày đăng cụ thể ("đăng 3 ngày trước") trên card — đặc tả này không thêm field
  `createdAt` vào response (mục 4 REQUIREMENT.md), chỉ dùng thời gian đăng để lọc/sắp xếp ở backend.
