# FR-U15 — Giao diện

> Trạng thái: ĐÃ HOÀN THÀNH 2026-10-03.

Tham chiếu: `docs/UI_GUIDE.md` (token `m3-*`, mục 2 điều hướng, mục 3 component, mục 4 ràng buộc
riêng — đặc biệt "Lý do gợi ý/khớp (U13, U15): chỉ là chip trung tính liệt kê điều kiện đã khớp.
Không %, không thanh màu, không nhãn 'phù hợp'", `UI_GUIDE.md:241-242`).

## 1. Trạng thái

ĐÃ DUYỆT 2026-10-03.

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Tên | Cột FR | Ghi chú |
|---|---|---|---|
| `/candidate` | "Việc làm" | FR-U07, FR-U13, **FR-U15**, FR-C05 | Màn hình cũ (★ không phải mới) — FR-U15 **mở rộng**: thêm khối "Gợi ý cho bạn" trên danh sách, không đổi phần còn lại |
| `/candidate/dashboard` | "Bảng tin" | FR-U11, **FR-U15**, FR-C05 | Màn hình cũ — FR-U15 **thay nội dung** khối gợi ý hiện có (`CandidateHomePage.tsx`), không đổi 2 thẻ còn lại ("CV chính", "Đơn ứng tuyển") hay khối "Cải thiện CV" |

Cả hai route đã có sẵn trong bảng Bản đồ màn hình (`docs/UI_GUIDE.md:308-309`) — không cần sửa
bảng đó.

## 3. Điểm vào

- `/candidate`: ứng viên đăng nhập → điều hướng vào thẳng (route mặc định sau login,
  `ProtectedRoute`/`LoginForm.tsx` có sẵn, không đổi). Khối gợi ý là phần tử đầu tiên trong
  `JobBoard`, phía trên `JobFilterBar`.
- `/candidate/dashboard`: tab "Bảng tin" trong `CandidateLayout` (`NAV_ITEMS`, có sẵn, không đổi).
  Khối gợi ý nằm ở đúng vị trí khối "Việc làm phù hợp với bạn" hiện tại (`RecommendedJobs`, giữa
  3 thẻ tóm tắt và khối "Cải thiện CV").
- Từ thẻ gợi ý → bấm vào thẻ mở `/jobs/:id` (giống `JobCard` hiện có, không đổi hành vi click).
- Nút "Xem tất cả" (R-X3 REQUIREMENT.md — hành vi khác nhau theo trang):
  - Ở `/candidate`: **chỉ hiện khi** ứng viên có khai ngành hoặc khu vực mong muốn → điều hướng
    nội bộ tới `/candidate?categoryCode=...&locationCode=...`. Không khai gì → **nút không hiện**
    (không phải hiện rồi dẫn về chính trang đang đứng).
  - Ở `/candidate/dashboard`: **luôn hiện**. Có khai mong muốn → điều hướng tới
    `/candidate?categoryCode=...&locationCode=...`; không khai gì → điều hướng tới `/candidate`
    (không tham số).
  - Mã ngành/khu vực mong muốn lấy từ `useMyProfileQuery()` (R-X4 REQUIREMENT.md,
    `frontend/src/features/candidateProfile/queries.ts:14`), không phải từ response gợi ý.
- Trạng thái `NO_DATA` → liên kết "Hoàn thiện hồ sơ" mở `/candidate/profile`.

## 4. Bố cục (khung ASCII)

### 4a. `/candidate` — desktop (≥ `lg`), trạng thái `READY`

```
┌───────────────────────────────────────────────────────────────────────┐
│ Gợi ý cho bạn                                  Xem tất cả →            │
│                                                                         │
│ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐        │
│ │ (JobCard)        │ │ (JobCard)        │ │ (JobCard)        │        │
│ │ ...              │ │ ...              │ │ ...              │        │
│ │ Khớp mong muốn:  │ │ Khớp mong muốn:  │ │ Khớp mong muốn:  │        │
│ │ Hà Nội · CNTT ·  │ │ CNTT · Toàn thời │ │ Lương đạt mong   │        │
│ │ Toàn thời gian · │ │ gian             │ │ muốn             │        │
│ │ Lương đạt mong   │ │                  │ │                  │        │
│ │ muốn             │ │                  │ │                  │        │
│ └──────────────────┘ └──────────────────┘ └──────────────────┘        │
│ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐        │
│ │ (JobCard)        │ │ (JobCard)        │ │ (JobCard)        │        │
│ └──────────────────┘ └──────────────────┘ └──────────────────┘        │
└───────────────────────────────────────────────────────────────────────┘
┌───────────────────────────────────────────────────────────────────────┐
│ (JobFilterBar — không đổi, FR-U07, trừ phần category/location đổi      │
│  sang CatalogMultiCombobox, xem mục 4c)                                │
├───────────────────────────────────────────────────────────────────────┤
│ Việc làm đang tuyển                                                    │
│ (JobList — không đổi, FR-U07)                                          │
└───────────────────────────────────────────────────────────────────────┘
```

Lưới 3 cột `lg` (giống `RecommendedJobs` hiện có), tối đa 6 thẻ (2 dòng × 3 cột). Dòng văn bản điều
kiện khớp (một khối duy nhất, nhãn "Khớp mong muốn:" + các điều kiện nối bằng "·", tự xuống dòng)
nằm **dưới cùng** mỗi `JobCard` (phần mở rộng mới của `JobCard`, chỉ hiện khi `matchedConditions`
không rỗng — các nơi khác dùng `JobCard` không truyền prop này, không đổi giao diện).

### 4b. `/candidate` — mobile (`< sm`, 375px), trạng thái `READY`

```
┌─────────────────────────────┐
│ Gợi ý cho bạn                │
│                  Xem tất cả →│
│ ┌───────────────────────────┐│
│ │ (JobCard)                 ││
│ │ Khớp mong muốn: Hà Nội ·  ││
│ │ CNTT · Toàn thời gian ·   ││
│ │ Lương đạt mong muốn       ││
│ └───────────────────────────┘│
│ ┌───────────────────────────┐│
│ │ (JobCard)                 ││
│ └───────────────────────────┘│
│  (chỉ 3 thẻ đầu — thẻ 4-6 ẩn  │
│   bằng CSS dưới `sm`, R-L1)   │
├───────────────────────────────┤
│ [từ khoá............] [Tìm]  │
│ [Lọc (n)]         [Sắp xếp ▾]│
├───────────────────────────────┤
│ Việc làm đang tuyển            │
│ (JobList 1 cột)                │
└─────────────────────────────┘
```

1 cột, cùng khung `RecommendedJobs` hiện tại (`grid-cols-1` dưới `lg`).

### 4c. Thanh lọc sau khi đổi sang chọn nhiều (desktop, `>= sm`)

```
┌───────────────────────────────────────────────────────────────────────┐
│ [Tìm.......] [Ngành nghề (2) ▾] [Tỉnh/thành (1) ▾] [Lương ▾] [Hình   │
│ thức ▾] [Thời gian ▾]                              [Xoá bộ lọc]      │
└───────────────────────────────────────────────────────────────────────┘
```

`CatalogMultiCombobox` thay `CatalogCombobox`: trigger hiện "Ngành nghề (2)" khi đã chọn ≥1 (khác
placeholder tĩnh "Chọn ngành nghề" của combobox đơn trước đây); mở popover thấy checkbox nhiều
dòng + chip đã chọn dưới trigger (đúng hành vi sẵn có của `CatalogMultiCombobox`, FR-U14).

### 4d. Thanh lọc trong bottom sheet (mobile, `< sm`)

```
┌─────────────────────────────┐
│ Lọc việc làm              ✕ │
├─────────────────────────────┤
│ Ngành nghề                   │
│ [Ngành nghề (2) ▾]           │
│   [CNTT ✕] [Kế toán ✕]       │
│ Tỉnh/thành                   │
│ [Tỉnh/thành (1) ▾]           │
│   [Hà Nội ✕]                 │
│ Mức lương (triệu VNĐ)        │
│ ...                          │
├─────────────────────────────┤
│ [Xoá bộ lọc]    [Áp dụng]   │
└─────────────────────────────┘
```

Giữ nguyên cơ chế "chỉ ghi URL khi bấm Áp dụng" (R-U6 FR-U07) — `CatalogMultiCombobox` không tự
đóng popover sau mỗi lần chọn (khác combobox đơn), cho chọn tiếp nhiều ngành/khu vực trước khi bấm
"Áp dụng" của sheet.

### 4e. `/candidate/dashboard` — khối gợi ý (desktop)

Giống hệt 4a (dùng chung component `RecommendedJobs`), chỉ khác: không có khối `JobFilterBar`/
`JobList` bên dưới — khối gợi ý là một phần tử độc lập giữa 3 thẻ tóm tắt và khối "Cải thiện CV",
đúng vị trí khối "Việc làm phù hợp với bạn" hiện tại. **Nút "Xem tất cả" LUÔN hiện ở đây** (khác
`/candidate`, nơi nút bị ẩn khi ứng viên không khai mong muốn — R-X3 REQUIREMENT.md): có khai mong
muốn → điền sẵn `/candidate?categoryCode=...&locationCode=...`; không khai gì → mở `/candidate`
trần.

## 5. Component

- `RecommendedJobs` (giữ file `frontend/src/features/jobs/RecommendedJobs.tsx`, viết lại nội
  dung): đọc `status`/`source`/`items` từ `useJobRecommendationsQuery()` (kiểu trả về đổi theo
  response mới), tự chọn 1 trong 5 trạng thái hiển thị (mục 6) — không còn tự suy từ
  `useResumesQuery()` như hiện tại (bỏ `resolveEmptyReason`, `EMPTY_STATE_MESSAGE`, `EmptyReason`).
  Gọi thêm `useMyProfileQuery()` (R-X4 REQUIREMENT.md) để: (a) quyết định ẩn/hiện và điền sẵn nút
  "Xem tất cả" theo trang (R-X3); (b) quyết định có thêm câu "mở rộng mong muốn" vào thông báo
  `NO_RESULT` hay không (R-S5 REQUIREMENT.md, mục 6/7 dưới).
- `JobCard` (mở rộng, không file mới): thêm prop tuỳ chọn `matchedConditions?: string[]`. Khi có
  giá trị (không rỗng), render thêm **một khối văn bản duy nhất** (`<p>`, tự xuống dòng, KHÔNG phải
  nhiều chip riêng lẻ) dưới khối thẻ hiện có (tag/kỹ năng vẫn giữ vị trí cũ) — nhãn "Khớp mong
  muốn:" (đậm hơn một bậc, `font-medium`) nối với các điều kiện bằng " · " trong cùng một dòng. Nền
  `bg-m3-surface-container`, chữ `text-m3-on-surface` (**không phải** `m3-on-surface-variant` —
  UI_GUIDE mục 6 nói rõ `m3-on-surface-variant` trên nền `m3-surface-container` không đạt 4.5:1,
  phải dùng `m3-on-surface` trên nền này), khác `bg-m3-primary-container` của chip lọc/chip kỹ năng
  để phân biệt rõ đây là thông tin chỉ-đọc, không phải điều khiển. Không %, không màu theo mức độ,
  không dấu ✓ trước mỗi điều kiện.

### Bảng token dùng cho khối gợi ý

| Thành phần | Token nền | Token chữ | Ghi chú tương phản |
|---|---|---|---|
| Dòng văn bản điều kiện khớp | `bg-m3-surface-container` | `text-m3-on-surface` | `m3-on-surface` luôn dùng trên nền container (UI_GUIDE mục 6) — không dùng `m3-on-surface-variant` (dưới 4.5:1 trên nền này) |
| Tiêu đề khối "Gợi ý cho bạn" | — (nền `m3-surface` kế thừa từ `JobBoard`) | `text-m3-on-surface` | 14.68:1 trên `m3-surface` (UI_GUIDE:95) |
| Dòng nguồn ("Dựa trên...") | — | `text-m3-on-surface-variant` | Đặt trên `m3-surface` (nền trắng của trang), đạt 4.83:1 (UI_GUIDE:96) — hợp lệ ở vị trí này, khác dòng văn bản điều kiện khớp ở trên |
| Thông báo `NO_DATA`/`PREPARING`/`NO_RESULT`/lỗi tải | — | `text-m3-on-surface-variant` | Cùng lý do trên — đặt trực tiếp trên `m3-surface`, không đặt trong khối nền container nào khác |
| Liên kết hành động ("Hoàn thiện hồ sơ", "Chỉnh mong muốn", "Xem tất cả") | — | `text-m3-primary` (giữ màu liên kết hiện có của `RecommendedJobs`/`JobFilterBar`) | Theo đúng token liên kết đã dùng ở `JobFilterBar.tsx` (`text-m3-primary`), không tạo màu liên kết mới |
| `JobCardSkeleton` (trạng thái Đang tải) | Giữ nguyên component có sẵn, không đổi token | — | — |

Không dùng `m3-outline-variant` làm viền khối văn bản này (chỉ 1.23:1 so nền trắng, UI_GUIDE mục 6
— chỉ dùng để phân khối lớn, không phải viền một khối nhỏ); không cần viền vì đã phân biệt đủ bằng
nền `m3-surface-container` khác nền trắng của card.
- `JobBoard` (sửa, không file mới): thêm prop `showRecommendations?: boolean` (mặc định `false`).
  Khi `true` **và** đang ở trang 1, chưa có từ khoá, không có bộ lọc đang áp (R-L3
  `REQUIREMENT.md`) → render `<RecommendedJobs />` trước `<JobFilterBar />`.
- `CandidateJobListPage` (sửa 1 dòng): truyền `showRecommendations={true}` cho `JobBoard`.
- `PublicJobListPage`: **không sửa** — không truyền prop, giữ `false`.
- `CatalogMultiCombobox` (component có sẵn từ FR-U14, không sửa) thay `CatalogCombobox` trong
  `CategoryField`/`LocationField` của `JobFilterBar.tsx` (cả khối `>= sm` và `MobileFilterSheet`).
- `useJobFilters` (sửa): `categoryCode`/`locationCode` kiểu `string[]` thay `string | null`.

## 6. Trạng thái hiển thị (5 trạng thái, không tính "có dữ liệu" vì đó là render bình thường)

| # | Trạng thái | Khi nào | Hiển thị |
|---|---|---|---|
| 1 | **Đang tải** | `useJobRecommendationsQuery().isLoading` | Lưới skeleton (`JobCardSkeleton`, 3 ô — giữ nguyên `SKELETON_KEYS` hiện có) |
| 2 | **Lỗi tải** | `isError` | "Không tải được gợi ý việc làm." + nút "Thử lại" (giữ nguyên hành vi hiện có, `refetch()`) |
| 3 | **`NO_DATA`** | `status === 'NO_DATA'` | "Hãy hoàn thiện hồ sơ nghề nghiệp hoặc tải CV để nhận gợi ý việc làm phù hợp." + liên kết "Hoàn thiện hồ sơ" → `/candidate/profile` |
| 4 | **`PREPARING`** | `status === 'PREPARING'` | "Hệ thống đang phân tích [CV / hồ sơ] của bạn. Gợi ý sẽ xuất hiện sau khi phân tích hoàn tất." (chọn "CV" hay "hồ sơ" theo `source`) — **không** có liên kết hành động (khác `NO_DATA`), không có nút "Thử lại" (không phải lỗi) |
| 5 | **`NO_RESULT`** | `status === 'NO_RESULT'` | "Chưa có việc làm nào để gợi ý lúc này." — **chỉ khi** có khai ngành hoặc khu vực mong muốn (qua `useMyProfileQuery()`, không phụ thuộc `source`): thêm "Hãy thử mở rộng ngành nghề hoặc khu vực mong muốn." + liên kết "Chỉnh mong muốn" → `/candidate/profile` |

Ở trạng thái 1, 2: ẩn luôn toàn bộ khối gợi ý hay vẫn hiện khung rỗng? → giữ nguyên hành vi hiện có
của `RecommendedJobs` (luôn hiện khung `<section>` với tiêu đề "Gợi ý cho bạn", chỉ phần nội dung
bên trong đổi theo trạng thái) — nhất quán với cách FR-U04 đã làm, không ẩn hẳn khối khi đang
tải/lỗi để tránh layout nhảy.

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Tiêu đề khối | "Gợi ý cho bạn" |
| Nút "Xem tất cả" | "Xem tất cả" — ở `/candidate` chỉ hiện khi có khai ngành/khu vực mong muốn (R-X3), ở `/candidate/dashboard` luôn hiện |
| Dòng nguồn — `source=CV` | "Dựa trên CV chính của bạn" |
| Dòng nguồn — `source=PROFILE` | "Dựa trên hồ sơ nghề nghiệp của bạn" |
| Dòng nguồn — `source=DESIRES` | "Dựa trên ngành nghề và khu vực bạn chọn" |
| `NO_DATA` | "Hãy hoàn thiện hồ sơ nghề nghiệp hoặc tải CV để nhận gợi ý việc làm phù hợp." |
| `NO_DATA` — liên kết | "Hoàn thiện hồ sơ" |
| `PREPARING` — CV | "Hệ thống đang phân tích CV của bạn. Gợi ý sẽ xuất hiện sau khi phân tích hoàn tất." |
| `PREPARING` — hồ sơ | "Hệ thống đang phân tích hồ sơ của bạn. Gợi ý sẽ xuất hiện sau khi phân tích hoàn tất." |
| `NO_RESULT` — câu chính, **mọi nguồn** | "Chưa có việc làm nào để gợi ý lúc này." |
| `NO_RESULT` — câu phụ, **chỉ khi** ứng viên có khai ngành hoặc khu vực mong muốn (đọc qua `useMyProfileQuery()`, R-X4, không phụ thuộc `source`) | "Hãy thử mở rộng ngành nghề hoặc khu vực mong muốn." |
| `NO_RESULT` — liên kết (chỉ hiện cùng câu phụ trên) | "Chỉnh mong muốn" |
| Lỗi tải | "Không tải được gợi ý việc làm." |
| Nút thử lại | "Thử lại" |
| Dòng văn bản điều kiện khớp — nhãn | "Khớp mong muốn:" (cố định, đứng đầu dòng, đậm hơn một bậc) |
| Dòng văn bản điều kiện khớp — lương | "Lương đạt mong muốn" (cố định, không chèn số) |
| Dòng văn bản điều kiện khớp — khác | Nhãn thật của ngành/tỉnh/hình thức (tra qua `CatalogRegistry`/`WORK_MODE_LABELS`, không phải chuỗi cố định) |

Dòng "Dựa trên..." đặt ngay dưới tiêu đề khối, trên lưới thẻ — chỉ hiện khi `status ∈ {READY,
NO_RESULT, PREPARING}` (có `source`), ẩn khi `NO_DATA` (`source=null`).

**`NO_RESULT` không dùng `source` để quyết định câu phụ** (khác suy nghĩ ban đầu) — một ứng viên ở
nhánh `CV`/`PROFILE` nhưng **không** khai ngành/khu vực mong muốn vẫn có thể rơi vào `NO_RESULT`
(0 job thoả ngưỡng/điều kiện khác), và lời khuyên "mở rộng ngành/khu vực mong muốn" vô nghĩa nếu họ
chưa khai gì — câu phụ chỉ xuất hiện khi `desiredIndustries.length > 0 || desiredLocations.length
> 0` (đọc từ `useMyProfileQuery()`, cùng nguồn với R-X4), bất kể `source` là gì.

## 8. Responsive (compact / medium / expanded — theo UI_GUIDE mục 1)

- **Compact (< `sm`, giữ nguyên breakpoint Tailwind hiện có của dự án — không đổi sang 3 bậc MD3
  đầy đủ ở FR này)**: lưới 1 cột, **chỉ hiển thị 3 thẻ đầu** (thẻ thứ 4-6 vẫn có trong DOM/response
  nhưng ẩn bằng CSS, ví dụ `[&>*:nth-child(n+4)]:hidden` hoặc tương đương — R-L1 REQUIREMENT.md)
  để không đẩy ô tìm kiếm/thanh lọc xuống quá xa trên màn hình nhỏ; `CatalogMultiCombobox` trong
  bottom sheet.
- **Medium/Expanded (`>= sm`)**: lưới 3 cột (`lg:grid-cols-3`, giữ breakpoint hiện tại của
  `RecommendedJobs`, không đổi sang `md`), **hiển thị đủ 6 thẻ**; `CatalogMultiCombobox` trong
  thanh lọc ngang.
- Dòng văn bản điều kiện khớp trong `JobCard`: một khối `<p>` duy nhất, tự xuống dòng tự nhiên theo
  độ rộng (không `flex-wrap` vì không còn nhiều chip rời), không dùng `line-clamp`/`truncate` (khác
  tiêu đề job, vốn `line-clamp-2`).

## 9. Khả năng tiếp cận

- Khối gợi ý là `<section>` có `aria-label="Gợi ý việc làm cho bạn"` (tiêu đề `h2` hiện có đủ, chỉ
  thêm `aria-label` khi tiêu đề và nội dung không liền kề về DOM do skeleton/trạng thái rỗng chèn
  giữa).
- Dòng văn bản điều kiện khớp: một khối `<p>` duy nhất có `aria-label` mô tả gộp giữ nguyên ý nghĩa
  (ví dụ "Điều kiện khớp: Hà Nội, CNTT, Toàn thời gian, Lương đạt mong muốn") — nội dung hiển thị
  ("Khớp mong muốn: Hà Nội · CNTT · ...") và `aria-label` khác nhau về cách diễn đạt nhưng cùng ý
  nghĩa, tái dùng mẫu `aria-label` đã có ở `CatalogMultiCombobox` (nút "Bỏ chọn X").
- Trạng thái `PREPARING`/`NO_RESULT`/`NO_DATA`: vùng text bọc `aria-live="polite"` (giống
  `countText` của `JobFilterBar.tsx:90`) — chuyển trạng thái (ví dụ từ `PREPARING` sang `READY` khi
  ứng viên tải lại trang sau khi embedding xong) được thông báo, không chỉ đổi nội dung lặng lẽ.
- Liên kết "Xem tất cả"/"Hoàn thiện hồ sơ"/"Chỉnh mong muốn": `<Link>` thường (không `button`), giữ
  đúng ngữ nghĩa điều hướng, có `focus-visible:outline` theo token `m3-primary` (đồng bộ
  `FIELD_TRIGGER_CLASS` đã dùng trong `JobFilterBar`).
- `CatalogMultiCombobox` đã có sẵn đầy đủ `role="combobox"`/`listbox`/`aria-multiselectable`/điều
  hướng bàn phím (FR-U14) — không cần làm lại, chỉ đổi nơi dùng.

## 10. Không được làm

- Không hiện % độ khớp, thanh tiến trình, hay bất kỳ biểu diễn số nào của mức độ phù hợp
  (UI_GUIDE mục 4 "Lý do gợi ý/khớp" — chỉ chip trung tính).
- Không tô màu dòng văn bản điều kiện khớp theo mức độ (ví dụ xanh đậm cho "khớp nhiều", nhạt cho
  "khớp ít") — một màu token duy nhất, không phân cấp.
- Không có nhãn "Phù hợp cao"/"Rất phù hợp"/dấu ✓ cạnh job hay cạnh từng điều kiện trong dòng văn
  bản.
- Không hiện khối gợi ý ở `/` (trang công khai, khách/chưa đăng nhập) — `showRecommendations`
  luôn `false` ở `PublicJobListPage`.
- Không ẩn khối gợi ý bằng cách trả `items: []` lặng lẽ khi lỗi mạng — lỗi mạng phải vào trạng thái
  "Lỗi tải" (mục 6, #2), không lẫn với `NO_RESULT`/`NO_DATA` (đây là lỗi hạ tầng, không phải trạng
  thái dữ liệu hợp lệ).
- Không để khối gợi ý hiện cùng lúc với trạng thái "đang có bộ lọc/từ khoá/trang khác 1" ở
  `/candidate` (R-L3) — kiểm tra điều kiện này ở `JobBoard`, không chỉ ở `RecommendedJobs`.
- Không copy logo/tên thương hiệu của bất kỳ job board nào khi vẽ layout thẻ gợi ý — chỉ mô phỏng
  quy ước bố cục (CLAUDE.md §8).
- Không gắn icon `Sparkles` (hay nhãn "Do AI tạo") vào tiêu đề "Gợi ý cho bạn" — `UI_GUIDE.md:229-232`
  dành icon này **riêng** cho "Nội dung do AI tạo" (bản nháp tin nhắn, tin tuyển dụng nháp, tóm
  tắt, câu hỏi phỏng vấn, câu trả lời hỏi đáp CV, gợi ý diễn đạt). Gợi ý của FR-U15 không gọi LLM ở
  bất kỳ nhánh nào (kể cả nhánh CV/hồ sơ — chỉ đọc embedding có sẵn, không sinh nội dung), và nhánh
  `DESIRES` còn không liên quan AI ở bất kỳ mức nào — gắn `Sparkles` vào đây sẽ ngụ ý sai rằng kết
  quả do AI "quyết định/tạo ra".
