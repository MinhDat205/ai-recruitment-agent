# FR-U14 — Giao diện

## 1. Trạng thái

ĐÃ DUYỆT (02/10/2026). Đặc tả chức năng: `REQUIREMENT.md` cùng thư mục — mọi mã quy tắc (R-F, R-S,
R-M, R-K, R-B, R-G, R-O, R-A, R-E, R-P, R-V) tham chiếu từ file đó.

**Phạm vi MD3**: toàn bộ màn hình `/candidate/onboarding` (★Mới) và **toàn bộ** hai card mới/sửa trên
`/candidate/profile` ("Thông tin cơ bản", "Nghề nghiệp và mong muốn công việc") dùng token `m3-*`. Phần "CV của tôi"
(`ResumeUploadDropzone`, `ResumeList`) trên cùng trang **giữ nguyên** token cũ (`brand`, `ink`...) cho
`refactor/ui-md3-legacy` — U14 không chạm phần đó.

## 2. Route

| Route | Màn hình | Hiện có / ★Mới | Phần U14 sửa |
|---|---|---|---|
| ★`/candidate/onboarding` | "Hoàn thiện hồ sơ" | ★Mới | Toàn bộ màn hình |
| `/candidate/profile` | "Hồ sơ và CV" | Hiện có | Tách card "Thông tin cá nhân" cũ thành 2 card: "Thông tin cơ bản" (giữ 3 field cũ) + "Nghề nghiệp và mong muốn công việc" (★Mới, các field U14); thêm nút "Điền từ CV" |
| `/register` | Đăng ký | Hiện có | **Không sửa code** — xác nhận ở REQUIREMENT.md mục 0.d: đăng ký không trả phiên đăng nhập nên vẫn về `/login` như hiện tại (R-O1). Route này chỉ liên quan U14 vì màn "Hoàn thiện hồ sơ" xuất hiện ở lần đăng nhập ĐẦU TIÊN sau đó, không phải ngay sau đăng ký |

**Đã xác nhận lúc viết đặc tả này:** `docs/UI_GUIDE.md` mục 7 đã có sẵn cả 3 dòng trên gắn "FR-U14" —
[dòng 310](../../../UI_GUIDE.md) (`/candidate/profile`, cột FR có "FR-U14 (mục mong muốn công việc)")
và [dòng 319](../../../UI_GUIDE.md) (★`/candidate/onboarding`, cột FR "FR-U14") **đúng, không cần
sửa**. Riêng [dòng 302](../../../UI_GUIDE.md) (`/register`) cột "Màn hình" đang ghi "Đăng ký (xong →
★`/candidate/onboarding`)" — mô tả này SAI với R-O1 đã chốt lại (đăng ký vẫn về `/login`, không đi
thẳng `/candidate/onboarding`). **Việc cần làm ở đợt cuối:** sửa lại cột "Màn hình" của dòng `/register`
trong `docs/UI_GUIDE.md` mục 7 cho khớp (ví dụ bỏ phần "(xong → ★.../onboarding)", giữ nguyên cột FR
"FR-C01, ★FR-U14" vì `/register` vẫn là điểm khởi đầu gián tiếp của luồng U14).

## 3. Điểm vào

- `/candidate/onboarding`: **không** vào ngay sau đăng ký (đăng ký vẫn về `/login` như hiện tại, mục
  0.d REQUIREMENT.md). Tự động xuất hiện ở lần ứng viên đăng nhập xong và vào `/candidate/*` **đầu
  tiên** sau khi tài khoản được tạo — do wrapper `RequireCandidateProfileOnboarding` phát hiện
  `onboardingCompletedAt === null` và điều hướng tới (R-O1/R-O2). Không có menu/link tĩnh nào dẫn tới
  màn này — sau khi đã hoàn thiện/bỏ qua vẫn vào được bằng cách dán URL trực tiếp, chỉ không còn bị
  **tự động** điều hướng tới nữa.
- `/candidate/profile`: từ thanh điều hướng `CandidateLayout` (đã có từ FR-U01), hoặc bị điều hướng
  tới đây **gián tiếp** nếu đang ở `/candidate/*` khác mà cờ `onboardingCompletedAt` còn `null` thì bị
  `RequireCandidateProfileOnboarding` đưa về `/candidate/onboarding` trước (không phải về
  `/candidate/profile` trực tiếp).

## 4. Bố cục

### 4a. `/candidate/onboarding` — Desktop (≥ `md`)

```
┌─────────────────────────────────────────────────────────────────┐
│ Hoàn thiện hồ sơ                                                  │
│ Giúp chúng tôi gợi ý việc làm phù hợp hơn. Bạn có thể bỏ qua và   │
│ điền sau ở trang Hồ sơ.                                           │
├─────────────────────────────────────────────────────────────────┤
│ Chức danh mong muốn                           [FileInput] Điền từ CV│
│ [vd. Backend Developer________________________]                   │
│                                                                    │
│ Ngành nghề mong muốn (0/3)        Khu vực mong muốn (0/3)         │
│ [Chọn ngành nghề             ▾]   [Chọn khu vực             ▾]   │
│                                                                    │
│ Hình thức làm việc mong muốn       Mức lương mong muốn (triệu VNĐ)│
│ ☐ Tại văn phòng ☐ Kết hợp ☐ Từ xa  [______] trở lên               │
│                                                                    │
│ Số năm kinh nghiệm                                                 │
│ [____] năm                                                         │
│                                                                    │
│ Kỹ năng chính (0/20)                                               │
│ [Nhập kỹ năng, Enter hoặc dấu phẩy để thêm________________]       │
│                                                                    │
│ Giới thiệu ngắn                                          0/500    │
│ [________________________________________________________]       │
│ [________________________________________________________]       │
├─────────────────────────────────────────────────────────────────┤
│ [Bỏ qua]                                               [Lưu]      │
└─────────────────────────────────────────────────────────────────┘
```

Sau khi chọn: ngành/khu vực hiện dạng chip ngay dưới combobox tương ứng; kỹ năng hiện dạng chip ngay
trong/dưới ô nhập (xem 4c). Nút "Điền từ CV" nằm cùng hàng với nhãn "Chức danh mong muốn", icon lucide
`FileInput` (**cố ý không dùng `Sparkles`**: UI_GUIDE mục 4 dành icon `Sparkles` + khối
`m3-surface-container-high` + nhãn "Do AI tạo" riêng cho nội dung AI SINH RA — "Điền từ CV" là sao
chép xác định, không gọi AI (R-A), dùng icon khác để không gây hiểu lầm đây là nội dung AI tạo).

### 4b. `/candidate/onboarding` — 375px

Một cột, mọi field full-width, thứ tự giữ nguyên như 4a:

```
┌──────────────────────────────────┐
│ Hoàn thiện hồ sơ                  │
│ Giúp gợi ý việc làm phù hợp hơn.  │
│ Có thể bỏ qua.                    │
├──────────────────────────────────┤
│ Chức danh mong muốn                │
│ [FileInput] Điền từ CV             │
│ [________________________]        │
│ Ngành nghề mong muốn (0/3)        │
│ [Chọn ngành nghề          ▾]     │
│ Khu vực mong muốn (0/3)           │
│ [Chọn khu vực             ▾]     │
│ Hình thức làm việc mong muốn      │
│ ☐ Tại văn phòng                   │
│ ☐ Kết hợp                         │
│ ☐ Từ xa                           │
│ Mức lương mong muốn (triệu VNĐ)   │
│ [________] trở lên                │
│ Số năm kinh nghiệm                │
│ [____] năm                        │
│ Kỹ năng chính (0/20)              │
│ [________________________]        │
│ Giới thiệu ngắn           0/500   │
│ [________________________]        │
├──────────────────────────────────┤
│ [Bỏ qua]              [Lưu]      │
└──────────────────────────────────┘
```

Nút "Điền từ CV" xuống dòng riêng dưới nhãn (không đủ chỗ cùng hàng ở 375px).

### 4c. Combobox chọn nhiều (`CatalogMultiCombobox`) và ô nhập kỹ năng (`SkillTagInput`)

Giữ nguyên cơ chế Popover + ô tìm kiếm của `CatalogCombobox` (C05) ở **mọi khổ màn hình** (quyết định
đã chốt — không làm bottom sheet riêng cho mobile như U07 đã làm cho thanh lọc):

```
[Ngành nghề mong muốn (2/3)              ▾]
[CNTT - Phần mềm ×] [Kế toán - Kiểm toán ×]
```

Mở popover: mỗi dòng danh mục có checkbox nhỏ bên trái (khác `CatalogCombobox` chỉ có icon `Check` khi
chọn — ở đây cần thấy rõ nhiều dòng có thể cùng đang chọn). Khi đã chọn đủ 3: các dòng CHƯA chọn hiện
mờ (`opacity-38`), không bấm được, kèm dòng chữ nhỏ cuối danh sách "Đã chọn tối đa 3 — bỏ một mục để
chọn mục khác."

Ô nhập kỹ năng:
```
Kỹ năng chính (3/20)
┌─────────────────────────────────────────────────┐
│ [Java ×] [Spring Boot ×] [SQL ×] Nhập rồi Enter… │
└─────────────────────────────────────────────────┘
```
Enter hoặc dấu phẩy khi đang gõ → thêm thẻ (xoá input); Backspace khi input rỗng → xoá thẻ cuối cùng.
Đủ 20 thẻ → input bị khoá, placeholder đổi thành "Đã đạt tối đa 20 kỹ năng."

### 4d. `/candidate/profile` — Desktop (≥ `md`)

```
┌─ Thông tin cơ bản ────────────────────────────────────────┐
│ Vị trí hiện tại        Nơi ở hiện tại     Ngày sinh          │
│ [____________]         [____________]    [__________]       │
└─────────────────────────────────────────────────────────┘
┌─ Nghề nghiệp và mong muốn công việc ──────────────────────────────────────┐
│ (đúng nhóm field 4a, KHÔNG có tiêu đề trang/dòng giới thiệu)│
└─────────────────────────────────────────────────────────┘
                                                      [Lưu]
┌─ CV của tôi ──────────────────────────────────────────────┐
│ (giữ nguyên, token cũ, ngoài phạm vi U14)                  │
└─────────────────────────────────────────────────────────┘
```

Hai card "Thông tin cơ bản" + "Nghề nghiệp và mong muốn công việc" nằm trong **một `<form>`** với **một** nút "Lưu"
chung đặt dưới card thứ hai (không phải mỗi card một `CardFooter`/nút riêng — khác bố cục 2-card hiện
tại của `CandidateProfilePage.tsx` chỉ có 1 card form + 1 card CV). Card "CV của tôi" nằm ngoài `<form>`
này, giữ nguyên vị trí/hành vi hiện có.

### 4e. `/candidate/profile` — 375px

Ba card xếp dọc full-width theo đúng thứ tự 4d; field trong mỗi card xếp 1 cột như 4b.

## 5. Component (UI_GUIDE mục 3) và token `m3-*`

| Token | Dùng cho |
|---|---|
| `m3-on-surface` | Nhãn field, tiêu đề card, chữ chip |
| `m3-on-surface-variant` | Chữ phụ (bộ đếm "0/500", "0/3", "0/20"), placeholder |
| `m3-outline` | Viền input text/number, viền trigger combobox (đồng bộ C05/U07) |
| `m3-primary` | Nền nút "Lưu" (Filled — hành động chính của form, UI_GUIDE mục 3); `m3-on-primary` là màu chữ trên nút. `m3-tertiary` KHÔNG dùng ở đây — token đó dành riêng cho nút "Ứng tuyển" (UI_GUIDE mục 1c), không phải mọi nút Filled. Viền focus cũng dùng `m3-primary` |
| `m3-primary-container` / `m3-on-primary-container` | Nền/chữ chip đã chọn (ngành, khu vực, kỹ năng) |
| `m3-surface` | Nền input, nền popover |
| `m3-surface-container-high` | **KHÔNG dùng cho khối "Điền từ CV"** — mục 4a đã nêu rõ đây không phải nội dung AI tạo |
| `m3-outline-variant` | Viền phân card, viền dòng "Bỏ chọn"/dòng mờ khi đạt tối đa 3, viền nút "Bỏ qua" (Outlined — hành động phụ, UI_GUIDE mục 3) |
| `m3-error` | Dòng lỗi Bean Validation (bio > 500, lương ngoài khoảng) |
| `shadow-m3-3` | Popover `CatalogMultiCombobox` |
| `text-m3-label-md` | Nhãn trên mỗi chip (dùng cỡ chữ chip chuẩn MD3) |
| `text-m3-body-md` / `text-m3-body-sm` | Giá trị nhập / chữ phụ (bộ đếm, chú thích nút khoá) |

Component mới:
- **`CatalogMultiCombobox`** (`features/catalog/`): dựng từ hạ tầng Popover + ô tìm kiếm của
  `CatalogCombobox` (C05, `features/catalog/CatalogCombobox.tsx`), đổi `value: string | null` /
  `onChange: (code: string | null) => void` thành `value: string[]` / `onChange: (codes: string[]) =>
  void`, thêm prop `max: number` (= 3). Mỗi dòng danh mục có checkbox, không đóng popover khi chọn
  (khác `CatalogCombobox` đóng ngay sau khi chọn 1 giá trị). Hiện chip đã chọn dưới trigger, mỗi chip
  có nút "×" gọi `onChange` bỏ đúng mã đó.
- **`SkillTagInput`** (`features/candidateProfile/`, tên gợi ý): input text thường + xử lý
  `onKeyDown` (Enter/`,` → thêm thẻ nếu còn chỗ và độ dài hợp lệ; Backspace khi rỗng → bỏ thẻ cuối).
  Không thêm thư viện (không dùng `react-tag-input` hay tương đương).
- **`CareerPreferencesFields`** (`features/candidateProfile/`, tên gợi ý): nhóm field dùng chung giữa
  `/candidate/onboarding` và card "Nghề nghiệp và mong muốn công việc" của `/candidate/profile` — gồm chức danh mong
  muốn (`headline`), 2 `CatalogMultiCombobox`, popover/checkbox hình thức làm việc (tái dùng cấu trúc
  `WorkModeField` của `JobFilterBar.tsx:338`, chỉ đổi nơi lưu trạng thái), ô lương, ô số năm kinh
  nghiệm, `SkillTagInput`, `<textarea>` giới thiệu ngắn, nút "Điền từ CV".

## 6. Trạng thái hiển thị

| Chỗ | Đang tải | Lỗi | Rỗng/Khác |
|---|---|---|---|
| `/candidate/onboarding`, `/candidate/profile` (tải hồ sơ) | Giống `CandidateProfilePage` hiện có: "Đang tải..." | "Không tải được hồ sơ, vui lòng thử lại." | — |
| `CatalogMultiCombobox` | Trigger disabled + "Đang tải danh mục…" (giống C05) | Dòng lỗi + "Thử lại" (giống C05) | "Không có mục phù hợp." khi gõ tìm không ra |
| Nút "Điền từ CV" | — | Gọi API lỗi (mạng/500) → `Snackbar`/dòng chữ đỏ nhỏ cạnh nút: "Không điền được, vui lòng thử lại." | Chưa có CV chính đã phân tích xong → nút **disabled**, chú thích dưới nút: "Chưa có CV chính đã phân tích xong để điền tự động." (409 `NO_PRIMARY_RESUME_PARSED` không bao giờ thực sự xảy ra vì nút đã bị khoá trước — chú thích tĩnh dựa trên trạng thái CV chính đọc từ `useMyProfileQuery`/danh sách CV, không chờ gọi API rồi mới biết) |
| `CatalogMultiCombobox` đạt tối đa 3 | — | — | Dòng chữ cuối popover: "Đã chọn tối đa 3 — bỏ một mục để chọn mục khác." (không phải lỗi, không màu đỏ) |
| `SkillTagInput` đạt tối đa 20 | — | — | Input khoá, placeholder "Đã đạt tối đa 20 kỹ năng." |
| Lỗi lưu (400 Bean Validation / `INVALID_PROFILE_FIELD` / `INVALID_CATALOG_CODE`) | — | Dòng lỗi đỏ dưới field tương ứng (Bean Validation field-level) hoặc dòng lỗi chung cuối form (`INVALID_PROFILE_FIELD`/`INVALID_CATALOG_CODE`, lấy từ `data.message`, cùng cơ chế `extractErrorMessage` đã có ở `CandidateProfilePage.tsx:30-38`) | — |
| Bấm "Bỏ qua" lỗi (mạng/500) | — | Dòng lỗi nhỏ cạnh nút "Bỏ qua": "Không bỏ qua được, vui lòng thử lại." (không điều hướng đi) | — |
| Không có quyền | N/A — toàn bộ endpoint yêu cầu `hasRole("CANDIDATE")` đã đăng nhập, không có khái niệm ẩn danh ở 2 màn này | | |

## 7. Nội dung chữ

| Khoá | Chuỗi |
|---|---|
| Tiêu đề onboarding | "Hoàn thiện hồ sơ" |
| Mô tả onboarding | "Giúp chúng tôi gợi ý việc làm phù hợp hơn. Bạn có thể bỏ qua và điền sau ở trang Hồ sơ." |
| Tiêu đề card 1 (đổi từ "Thông tin cá nhân") | "Thông tin cơ bản" |
| Tiêu đề card 2 (★mới) | "Nghề nghiệp và mong muốn công việc" |
| Nhãn: `location` (đổi từ "Khu vực" — R-G3) | "Nơi ở hiện tại" (chỉ đổi chữ hiển thị, tránh nhầm với "Khu vực mong muốn" bên dưới; cột/field/API giữ nguyên) |
| Nhãn: chức danh mong muốn | "Chức danh mong muốn" (giữ nguyên nhãn đã có của `headline`) |
| Nhãn: ngành nghề mong muốn | "Ngành nghề mong muốn" + bộ đếm "(n/3)" |
| Nhãn: khu vực mong muốn | "Khu vực mong muốn" + bộ đếm "(n/3)" |
| Nhãn: hình thức làm việc | "Hình thức làm việc mong muốn" |
| Tuỳ chọn hình thức | "Tại văn phòng", "Kết hợp", "Từ xa" (đúng `WORK_MODE_LABELS`, `jobLabels.ts:40-42`) |
| Nhãn: lương | "Mức lương mong muốn (triệu VNĐ)", hậu tố ô nhập "trở lên" |
| Nhãn: số năm kinh nghiệm | "Số năm kinh nghiệm" (giữ nguyên nhãn đã có của `yearsExperience`) |
| Nhãn: kỹ năng | "Kỹ năng chính" + bộ đếm "(n/20)" |
| Placeholder ô kỹ năng | "Nhập kỹ năng, Enter hoặc dấu phẩy để thêm" |
| Kỹ năng đạt tối đa | "Đã đạt tối đa 20 kỹ năng." |
| Nhãn: giới thiệu ngắn | "Giới thiệu ngắn" + bộ đếm "{n}/500" |
| Nút | "Điền từ CV" (icon `FileInput`), "Bỏ qua", "Lưu" (đúng nhãn nút lưu hiện có) |
| Chú thích nút "Điền từ CV" bị khoá | "Chưa có CV chính đã phân tích xong để điền tự động." |
| Đạt tối đa ngành/khu vực trong popover | "Đã chọn tối đa 3 — bỏ một mục để chọn mục khác." |
| Lỗi validate (từ `InvalidProfileFieldException`, REQUIREMENT.md R-V2) | "Chỉ được chọn tối đa 3 ngành nghề mong muốn.", "Chỉ được chọn tối đa 3 khu vực mong muốn.", "Chỉ được nhập tối đa 20 kỹ năng.", "Mỗi kỹ năng phải có từ 1 đến 50 ký tự.", "Hình thức làm việc không hợp lệ." |
| Lỗi mã danh mục | "Ngành nghề không có trong danh mục." / "Tỉnh/thành không có trong danh mục." (đúng `InvalidCatalogCodeException`, C05) |
| Lỗi lương/bio (Bean Validation) | Thông điệp mặc định theo field (ví dụ "phải nhỏ hơn hoặc bằng 1000") — đặt message tuỳ chỉnh tiếng Việt trên annotation khi code (`@Min(0, message = "Lương phải là số không âm.")` v.v., không để message mặc định tiếng Anh) |
| Lỗi điền từ CV | "Không điền được, vui lòng thử lại." |
| Lỗi bỏ qua onboarding | "Không bỏ qua được, vui lòng thử lại." |
| Lỗi tải hồ sơ (giữ nguyên) | "Không tải được hồ sơ, vui lòng thử lại." |
| Lưu thành công (giữ nguyên) | "Đã lưu thay đổi" |

## 8. Responsive

- **Compact (< `sm`) và 375px cụ thể**: theo mục 4b/4e — 1 cột, field full-width, chip tự xuống dòng
  (`flex-wrap`), vùng chạm nút "×" trên chip và checkbox ≥ 48×48px.
- **Medium/Expanded (≥ `sm`)**: theo mục 4a/4d — nhóm field 2 cột (`grid sm:grid-cols-2`) cho cặp
  ngành/khu vực và cặp hình thức/lương, đúng khuôn `grid gap-4 sm:grid-cols-2` đã dùng ở
  `CandidateProfilePage.tsx:115,126`.
- `CatalogMultiCombobox` **không đổi cơ chế theo khổ màn hình** — luôn Popover (quyết định đã chốt,
  khác U07 chuyển sang bottom sheet ở mobile cho thanh lọc). Popover tự co theo
  `w-(--radix-popover-trigger-width)` (đúng `CatalogCombobox` hiện có), không cần biến thể riêng cho
  375px.

## 9. Khả năng tiếp cận

- `CatalogMultiCombobox`: giữ hành vi ARIA của `CatalogCombobox` (`role="combobox"`,
  `aria-expanded`, `listbox`/`option`, ↑/↓/Enter/Esc) — mỗi `option` thêm `aria-selected` phản ánh
  đúng trạng thái đã chọn (nhiều `option` có thể `aria-selected="true"` đồng thời, khác bản gốc chỉ 1).
  Dòng đã mờ khi đạt tối đa 3 có `aria-disabled="true"`. Mỗi chip là `<button>` với `aria-label="Bỏ
  chọn {label}"`.
- `SkillTagInput`: input có `aria-label="Nhập kỹ năng mới"`, vùng chip có `aria-live="polite"` để báo
  số lượng đổi ("Đã thêm {skill}, còn {n}/20") khi thêm/xoá thẻ qua bàn phím. Mỗi chip kỹ năng là
  `<button>` với `aria-label="Xoá kỹ năng {skill}"`.
- Bộ đếm ký tự "bio" (`{n}/500`) đặt trong `aria-live="polite"` để trình đọc màn hình biết khi gần
  chạm giới hạn.
- Nút "Điền từ CV" khi disabled: `aria-describedby` trỏ tới chú thích lý do (mục 6), không chỉ dựa vào
  thuộc tính `disabled` (một số trình đọc màn hình bỏ qua lý do nếu không có `aria-describedby`).
- Icon `FileInput` trên nút "Điền từ CV": `aria-hidden="true"` (nút đã có text "Điền từ CV").
- Mọi label field dùng `<Label htmlFor>` đúng mẫu hiện có của `CandidateProfilePage.tsx`.

## 10. Không được làm

- Không tạo bottom sheet/dialog riêng cho `CatalogMultiCombobox` ở 375px — dùng đúng Popover ở mọi
  khổ màn hình (quyết định đã chốt, khác cách U07 làm cho thanh lọc).
- Không gắn khối `m3-surface-container-high` + nhãn "Do AI tạo" cho nút/kết quả "Điền từ CV" — đây là
  sao chép xác định, không phải nội dung AI sinh ra (mục 4a).
- Không hiển thị bất kỳ field ở card "Nghề nghiệp và mong muốn công việc" (gồm cả số năm kinh nghiệm khi hiển thị
  trong nhóm này) trên bất kỳ màn hình phía HR.
- Không cho "Điền từ CV" tự lưu xuống DB — chỉ điền vào form (R-A2, REQUIREMENT.md).
- Không hiện lại `/candidate/onboarding` tự động sau khi đã lưu/bỏ qua (R-O2) — chỉ vào được bằng URL
  trực tiếp.
- Không thêm trường giới tính, tình trạng hôn nhân, ảnh đại diện, hay bất kỳ ô nhập thông tin nhân
  thân nào vào 1 trong 2 màn hình.
- Không dùng màu đỏ/vàng/xanh hay icon tích để gợi ý "mức độ phù hợp" cho bất kỳ field mong muốn —
  đây chỉ là form nhập liệu, không có khái niệm chấm điểm ở màn hình này.
- Không thêm thư viện UI mới cho combobox nhiều lựa chọn hay ô nhập thẻ.
