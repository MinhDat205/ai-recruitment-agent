# FR-C05 — Giao diện

## 1. Trạng thái

ĐÃ HOÀN THÀNH (01/10/2026). Duyệt: 30/09/2026. Đặc tả chức năng: `REQUIREMENT.md` cùng thư mục.

**Ngoại lệ phạm vi MD3 (đã chốt).** C05 sửa 5 màn hình cũ. Chỉ **thành phần mới hoặc thành phần bị
thay** (combobox danh mục, nhãn "Chưa chuẩn hoá", khung cảnh báo, mục "Tổng quan nghề nghiệp", nút
và trạng thái trích xuất lại) dùng token `m3-*`. Phần còn lại của các màn hình này giữ nguyên
token cũ và để `refactor/ui-md3-legacy` (Phase 2.1b) xử lý; khi làm nhánh đó, các màn hình này KHÔNG
được coi là "đã làm lại hoàn toàn ở FR-C05".

## 2. Route

Không có route mới. Các route chạm tới (UI_GUIDE mục 7):

| Route | Màn hình | Phần C05 sửa |
|---|---|---|
| `/hr/jobs/new` | Tạo tin | Hai ô Ngành nghề, Tỉnh/thành |
| `/hr/jobs/:id/edit` (tab "Thông tin tin tuyển dụng") | Sửa tin | Hai ô trên + cảnh báo Job chưa chuẩn hoá |
| `/hr/jobs` | Tin tuyển dụng | Cột Địa điểm; lỗi khi mở tin |
| `/`, `/candidate`, `/candidate/dashboard` (khối gợi ý) | Thẻ việc làm (`JobCard`) | Nhãn ngành nghề, tỉnh/thành |
| `/jobs/:id` | Chi tiết tin | Nhãn tỉnh/thành |
| `/candidate/profile` | Hồ sơ và CV — danh sách CV + dialog "Dữ liệu đã trích xuất" | Nút cập nhật trích xuất; mục Tổng quan nghề nghiệp |

Khi code: bổ sung "FR-C05" vào cột FR của các dòng trên trong UI_GUIDE mục 7 (cùng đợt, sau khi đặc
tả này được duyệt).

## 3. Điểm vào

- Form Job: nút "Tạo tin mới" ở `/hr/jobs` (`HrJobListPage.tsx:64`); vào trang sửa bằng link tiêu đề
  tin ở từng dòng (`HrJobListPage.tsx:102`).
- Lỗi mở tin: nút "Mở tin" (từ DRAFT) / "Mở lại" (từ PAUSED, CLOSED) ở `JobRowActions` (danh sách
  `/hr/jobs`, nhãn ở `jobLabels.ts:17-23`) — cả hai đều có thể nhận 409.
- Trích xuất lại: danh sách CV ở `/candidate/profile`.
- Tổng quan nghề nghiệp: nút "Xem dữ liệu đã trích xuất" ở danh sách CV.

## 4. Bố cục

### 4a. Form Job — hàng Ngành nghề / Tỉnh/thành (thay hàng "Danh mục / Địa điểm" hiện có)

Hiện tại: hai `<Input>` tự do (`HrJobCreatePage.tsx:245-250`, `HrJobEditPage.tsx:196-201`). Giữ nguyên
lưới `sm:grid-cols-2`.

```
Ngành nghề                              Tỉnh/thành
┌──────────────────────────────── ▾ ┐  ┌──────────────────────────────── ▾ ┐
│ Chọn ngành nghề                    │  │ Chọn tỉnh/thành                    │
└────────────────────────────────────┘  └────────────────────────────────────┘
Bắt buộc khi mở tin.                    Bắt buộc khi mở tin, trừ hình thức Làm từ xa.
```

Mở combobox (popover, rộng bằng ô):

```
┌────────────────────────────────────┐
│ [Search] Tìm tỉnh/thành...         │
├────────────────────────────────────┤
│ Hà Nội                             │
│ TP. Hồ Chí Minh              ✓     │
│ Hải Phòng                          │
│ ...  (cuộn, cao tối đa 320px)      │
├────────────────────────────────────┤
│ Bỏ chọn                            │
└────────────────────────────────────┘
```

Job có giá trị cũ chưa chuẩn hoá — dưới ô tương ứng:

```
┌──────────────────────────────── ▾ ┐
│ Chọn tỉnh/thành                    │
└────────────────────────────────────┘
[Chưa chuẩn hoá] Giá trị cũ: "Quận 1, HCM và Bình Dương"
Chọn lại từ danh mục trước khi mở tin.
```

### 4b. Sửa tin — cảnh báo Job đang mở nhưng chưa chuẩn hoá

Đặt đầu tab "Thông tin tin tuyển dụng", chỉ khi Job OPEN và thiếu điều kiện R-J3:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ [AlertTriangle] Tin đang mở nhưng ngành nghề hoặc tỉnh/thành chưa chọn   │
│ từ danh mục. Tin vẫn hiển thị bình thường; ứng viên lọc theo danh mục     │
│ có thể không thấy tin này. Chọn lại rồi lưu.                             │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4c. Danh sách Job HR — cột Địa điểm (`HrJobListPage.tsx:109`)

```
Địa điểm
TP. Hồ Chí Minh
Làm từ xa
Quận 1, HCM và Bình Dương  [Chưa chuẩn hoá]
Chưa có dữ liệu
```

Lỗi 409 khi bấm "Mở tin" hoặc "Mở lại" hiện ngay dưới nhóm nút của dòng, theo cơ chế sẵn có của
`JobRowActions` (`extractErrorMessage`).

### 4d. Thẻ việc làm và chi tiết tin (phía công khai/ứng viên)

Giữ nguyên bố cục `JobCard.tsx:40-48` và `PublicJobDetailPage.tsx:60-62`; chỉ đổi chuỗi hiển thị
theo mục 7. Không hiện nhãn "Chưa chuẩn hoá" ở phía công khai (xem mục 10).

**Ngoại lệ có chủ đích với UI_GUIDE mục 4 ("Dữ liệu thiếu: hiện nhãn Chưa có dữ liệu, không ẩn").**
Khi tin thiếu hẳn ngành nghề hoặc tỉnh/thành (không mã, không giá trị cũ, không phải REMOTE), thẻ
việc làm và trang chi tiết **giữ hành vi hiện tại: ẩn chip đó**. Lý do:
- Tin vẫn hiển thị trong danh sách, không bị loại — nguyên tắc "không âm thầm loại" vẫn được giữ.
- Chip "Chưa có dữ liệu" trên thẻ tin không có giá trị gì với ứng viên.
- Từ C05, không tin OPEN mới nào thiếu được hai trường này (R-J3), nên trường hợp này chỉ còn ở tin
  OPEN cũ trước C05.

### 4e. Danh sách CV (`ResumeList.tsx`, cột trạng thái + cột thao tác)

CV v1, chưa có yêu cầu:
```
[Đã xử lý xong]                         ... [RefreshCw] Cập nhật dữ liệu trích xuất
Dữ liệu trích xuất theo phiên bản cũ.
```
Đang chạy:
```
[Đã xử lý xong]                         ... [RefreshCw] Đang cập nhật…   (disabled)
▔▔▔▔▔▔▔▔▔▔ (linear progress không xác định, rộng bằng ô trạng thái)
```
Thất bại:
```
[Đã xử lý xong]                         ... [RefreshCw] Thử cập nhật lại
Cập nhật dữ liệu trích xuất thất bại: <câu lỗi chuẩn hoá>. Dữ liệu cũ vẫn được giữ.
```
CV v2: không có dòng phụ, không có nút.

### 4f. Dialog "Dữ liệu đã trích xuất" — mục mới đứng đầu, trước "Thông tin liên hệ"

```
Tổng quan nghề nghiệp
  Chức danh hiện tại   Senior Java Developer
  Ngành nghề           Công nghệ thông tin - Phần mềm
  Khu vực              TP. Hồ Chí Minh
  Kinh nghiệm          3,5 năm
                       Tính trên 4/5 mục kinh nghiệm, tính đến tháng 09/2026.
```

CV v1 (thêm dòng ghi chú dưới tiêu đề mục):
```
Tổng quan nghề nghiệp
  Dữ liệu trích xuất theo phiên bản cũ — cập nhật ở danh sách CV để có đủ các mục dưới đây.
  Chức danh hiện tại   Chưa có dữ liệu
  Ngành nghề           Chưa có dữ liệu
  Khu vực              Chưa có dữ liệu
  Kinh nghiệm          2,0 năm
                       Tính trên 2/2 mục kinh nghiệm, tính đến tháng 09/2026.
```

## 5. Component (UI_GUIDE mục 3)

- **Combobox danh mục** (mới): dựng từ `components/ui/popover.tsx` + `Input` tìm kiếm + danh sách
  `role="listbox"`. KHÔNG thêm thư viện mới (dự án chưa có `command`/cmdk). Tìm kiếm phía client
  trên nhãn, không phân biệt hoa/thường và dấu (cùng quy tắc R-M1). Có mục "Bỏ chọn". Dùng chung
  một component cho cả hai danh mục; dữ liệu từ `GET /api/public/catalogs` (TanStack Query, `staleTime`
  dài vì danh mục cố định).
  - Trigger: cao 40px (48px khi compact), `rounded-m3-xs`, `text-m3-body-md`, `text-m3-on-surface`,
    `border border-m3-outline` (token mới, mục 9).
  - Popover: `bg-m3-surface`, `shadow-m3-3`, `rounded-m3-md`; mục đang chọn/hover
    `bg-m3-primary/8`; dấu chọn icon lucide `Check`.
- **Nhãn "Chưa chuẩn hoá"** (chỉ phía HR): `rounded-m3-xs bg-m3-surface-container
  text-m3-on-surface text-m3-label-md`, kèm chữ; màu trung tính, không đỏ/vàng.
- **Khung cảnh báo 4b**: `border border-m3-outline-variant bg-m3-surface-container-high
  text-m3-on-surface rounded-m3-sm`, icon lucide `AlertTriangle` màu `text-m3-on-surface`.
- **Nút cập nhật trích xuất**: Outlined, `size="sm"` như các nút cạnh bên; icon lucide `RefreshCw`.
  Linear progress `bg-m3-primary` trên nền `bg-m3-primary-container` khi đang chạy.
- **Mục Tổng quan nghề nghiệp**: danh sách cặp nhãn–giá trị (`dl`), nhãn `text-m3-on-surface-variant
  text-m3-body-sm` (trên nền trắng), giá trị `text-m3-on-surface text-m3-body-md`.
- Không phải nội dung AI tạo theo UI_GUIDE mục 4 (là dữ liệu trích xuất), nên KHÔNG dùng nhãn "Do AI
  tạo" hay khối `m3-surface-container-high` cho mục Tổng quan.

## 6. Trạng thái hiển thị

| Chỗ | Đang tải | Lỗi | Rỗng / thiếu dữ liệu |
|---|---|---|---|
| Combobox | Trigger disabled + chữ "Đang tải danh mục…" | Trigger disabled + dòng lỗi dưới ô kèm nút "Thử lại"; form vẫn lưu nháp được (hai ô tuỳ chọn ở DRAFT) | Tìm không ra: "Không có mục phù hợp." |
| Form sửa Job | Giữ skeleton hiện có | — | Job chưa chuẩn hoá: mục 4a; thiếu hẳn: ô trống bình thường |
| Mở tin / Mở lại (409) | Nút disabled khi đang gửi (có sẵn) | Thông điệp 409 dưới nhóm nút | — |
| Cột Địa điểm HR | — | — | "Chưa có dữ liệu" (chữ `text-ink-muted` theo bảng cũ) |
| Danh sách CV — trích xuất lại | Linear progress + nút disabled; tự tải lại bằng `refetchInterval` sẵn có của danh sách CV (`features/resumes/queries.ts:54`), mở rộng điều kiện để hỏi tiếp khi yêu cầu trích xuất lại đang PENDING/RUNNING | Dòng lỗi mục 4e + nút "Thử cập nhật lại" | — |
| Gửi yêu cầu trích xuất lại | Nút disabled | 409/429: hiện thông điệp backend dưới dòng CV | — |
| Tổng quan nghề nghiệp | Skeleton hiện có của dialog | Thông báo lỗi hiện có của dialog | Từng dòng thiếu: "Chưa có dữ liệu" (không ẩn dòng) |
| Kinh nghiệm | — | — | `experience` null (chưa tính): "Đang tính…"; `months` null: "Chưa có dữ liệu" + "Không đọc được mốc thời gian ở X mục kinh nghiệm." (X = số mục bỏ qua; 0 mục → chỉ "Chưa có dữ liệu") |
| Khu vực | — | — | Có `locationText` nhưng không có mã: hiện nguyên văn + " (chưa khớp danh mục)" |
| Không có quyền / không tồn tại | Giữ hành vi hiện có ("Không tìm thấy") | | |

## 7. Nội dung chữ

| Khoá | Chuỗi |
|---|---|
| Nhãn ô | "Ngành nghề", "Tỉnh/thành" |
| Placeholder | "Chọn ngành nghề", "Chọn tỉnh/thành" |
| Ô tìm | "Tìm ngành nghề...", "Tìm tỉnh/thành..." |
| Mục bỏ chọn | "Bỏ chọn" |
| Gợi ý dưới ô | "Bắt buộc khi mở tin." / "Bắt buộc khi mở tin, trừ hình thức Làm từ xa." |
| Tìm không ra | "Không có mục phù hợp." |
| Lỗi tải danh mục | "Không tải được danh mục. Vui lòng thử lại." / nút "Thử lại" |
| Giá trị cũ | "Giá trị cũ: \"{text}\"" + "Chọn lại từ danh mục trước khi mở tin." |
| Nhãn trạng thái dữ liệu | "Chưa chuẩn hoá" |
| Cảnh báo Job đang mở | như khung 4b |
| Lỗi 409 mở tin | "Cần chọn ngành nghề và tỉnh/thành từ danh mục trước khi mở tin tuyển dụng." / REMOTE: "Cần chọn ngành nghề từ danh mục trước khi mở tin tuyển dụng." (thông điệp từ backend) |
| Lỗi 409 khi lưu tin đang mở | "Tin đang mở phải giữ ngành nghề và tỉnh/thành từ danh mục." / REMOTE: "Tin đang mở phải giữ ngành nghề từ danh mục." (thông điệp từ backend, hiện ở form sửa tin) |
| Job REMOTE không tỉnh | "Làm từ xa" |
| Thiếu dữ liệu | "Chưa có dữ liệu" |
| CV v1 | "Dữ liệu trích xuất theo phiên bản cũ." |
| Nút | "Cập nhật dữ liệu trích xuất" / "Đang cập nhật…" / "Thử cập nhật lại" |
| Lỗi trích xuất lại | "Cập nhật dữ liệu trích xuất thất bại: {errorMessage}. Dữ liệu cũ vẫn được giữ." |
| Lỗi 409 gửi yêu cầu trích xuất lại | "CV chưa phân tích xong, chưa thể cập nhật dữ liệu trích xuất." / "CV này đã có dữ liệu trích xuất mới nhất." / "CV đang được cập nhật dữ liệu trích xuất." (thông điệp từ backend, hiện dưới dòng CV) |
| Thành công (chỉ qua `aria-live`, mục 9) | "Đã cập nhật dữ liệu trích xuất." |
| Mục dialog | "Tổng quan nghề nghiệp"; "Chức danh hiện tại", "Ngành nghề", "Khu vực", "Kinh nghiệm" |
| Ghi chú v1 trong dialog | "Dữ liệu trích xuất theo phiên bản cũ — cập nhật ở danh sách CV để có đủ các mục dưới đây." |
| Kinh nghiệm | "{years} năm" (dấu phẩy thập phân kiểu Việt: "3,5 năm") |
| Cơ sở tính | "Tính trên {counted}/{counted+skipped} mục kinh nghiệm, tính đến tháng {MM/YYYY}." |
| Chưa tính | "Đang tính…" |
| Khu vực chưa khớp | "{locationText} (chưa khớp danh mục)" |

## 8. Responsive

- **Compact (< sm)**: hai combobox xếp dọc (lưới hiện có đã `sm:grid-cols-2`); trigger và mục danh
  sách cao 48px; popover rộng bằng ô, cao tối đa 60vh. Danh sách CV: nút "Cập nhật dữ liệu trích
  xuất" xuống dòng cùng các nút khác (theo cách bảng hiện tại co lại); dòng ghi chú v1 không bị cắt.
  Dialog Tổng quan: nhãn và giá trị xếp dọc.
- **Medium (sm–lg)** và **Expanded (≥ lg)**: như bố cục mục 4; dialog Tổng quan nhãn–giá trị hai cột.

## 9. Khả năng tiếp cận

- Combobox theo mẫu ARIA combobox: trigger `role="combobox"`, `aria-expanded`, `aria-controls`,
  `aria-labelledby` trỏ tới nhãn ô; danh sách `role="listbox"`, mục `role="option"` +
  `aria-selected`. Bàn phím: ↑/↓ di chuyển, Enter chọn, Esc đóng và trả focus về trigger, gõ để lọc.
- Gợi ý "Bắt buộc khi mở tin" và dòng "Giá trị cũ" nối vào ô bằng `aria-describedby`.
- "Chưa chuẩn hoá", "Chưa có dữ liệu", "Làm từ xa" là chữ, không chỉ bằng màu hay icon.
- Linear progress có `role="progressbar"` + `aria-label="Đang cập nhật dữ liệu trích xuất"`.
- Báo kết quả trích xuất lại: vùng `aria-live="polite"` trong dòng CV đọc "Đã cập nhật dữ liệu trích
  xuất." (thành công) hoặc câu lỗi mục 7 (thất bại); với người nhìn, thành công thể hiện bằng việc
  nút và dòng "phiên bản cũ" tự biến mất. KHÔNG xây component Snackbar trong C05 (dự án chưa có).
- Icon `AlertTriangle`, `RefreshCw`, `Check` có `aria-hidden="true"` (luôn đi kèm chữ).
- **Viền điều khiển**: UI_GUIDE mục 6 yêu cầu viền ô ≥ 3:1, màu chốt ở FR đầu tiên cần tới — là
  C05. Khai token `m3-outline` = `#6B7280` (cùng giá trị `m3-on-surface-variant`, 4.83:1 trên nền
  trắng, không thêm màu mới) trong khối `@theme` GỐC của `frontend/src/index.css`, và cập nhật UI_GUIDE
  mục 1c + mục 6 trong đợt code tương ứng. Chấp nhận lệch tạm: combobox có viền đậm hơn các `Input` cũ
  nằm cạnh trong cùng form cho tới Phase 2.1b.

## 10. Không được làm

- Không thêm lựa chọn "Toàn quốc", "Làm từ xa", "Nước ngoài" vào combobox tỉnh/thành.
- Không cho nhập tự do trong combobox (không có mục "Dùng giá trị “…”").
- Không tự xoá hay tự điền lại giá trị cũ khi mở form; không tự chọn mã "đoán" từ giá trị cũ.
- Không ẩn hay khoá nút "Mở tin"/"Mở lại" ở frontend thay cho guard backend (chỉ hiện lỗi backend trả về).
- C05 không hiện nhãn "Chưa chuẩn hoá" ở phía công khai/ứng viên; việc hiện nhãn trong kết quả lọc
  do FR-U07 quyết.
- Không dùng màu đỏ/vàng/xanh cho "Chưa chuẩn hoá" hay cho số năm kinh nghiệm; không thanh tiến trình
  hay thang màu cho kinh nghiệm.
- Không hiện "0 năm" khi không đọc được mốc nào; không tự quy đổi tháng → năm ở frontend.
- Không ẩn dòng thiếu dữ liệu trong mục Tổng quan nghề nghiệp.
- Không gắn nhãn "Do AI tạo" cho dữ liệu trích xuất; không hiện điểm hay nhận xét mức phù hợp.
- Không sửa phần còn lại của 5 màn hình cũ sang MD3 trong C05 (để Phase 2.1b).
- Không đổi ô tìm kiếm công khai (`HeroSearch.tsx`) — thuộc FR-U07.
