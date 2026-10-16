# FR-H15 — Giao diện

> Trạng thái: ĐÃ DUYỆT 16/10/2026
>
> Làm rõ sau Plan Mode 16/10/2026: L3 (mục 5a thêm `talentPoolLabels.ts`) — chi tiết ở `REQUIREMENT.md` mục 11.

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 1d (thang chữ), 1h (vùng chạm), 2 (layout HR), 3 (Button, Card,
Table, Checkbox, Dialog, badge trạng thái), 5 (rỗng/tải/lỗi), 6 (khả năng tiếp cận). Mã `R-…`, `P1`–`P9`, `[Q…]` là của
`REQUIREMENT.md` cùng thư mục.

## 1. Trạng thái

> Trạng thái: ĐÃ DUYỆT 16/10/2026

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| ★`/hr/talent-pools` | Danh sách kho | ★Mới | FR-H15 |
| ★`/hr/talent-pools/:id` | Chi tiết kho — danh sách hồ sơ đã lưu | ★Mới | FR-H15 |
| `/hr/applications/:id` | Trang hồ sơ đơn: header thêm nút "Lưu vào kho" + hộp thoại. Tab, thanh thao tác FR-H07 giữ nguyên | Hiện có | FR-H09; FR-H15 |

Hai route mới đặt trong nhóm `ProtectedRoute(HR) + RequireCompany` (`App.tsx:89-106`), nằm trong `HrLayout`; mục điều
hướng đang chọn là "Kho ứng viên".

## 3. Điểm vào

- Mục điều hướng mới "Kho ứng viên" (nhãn ngắn trên rail "Kho", icon `Archive` của `lucide-react`), đặt sau "Ứng viên",
  trước "Tin nhắn", sửa `NAV_ITEMS` ở `HrLayout.tsx:20-27`. Logic chọn mục đang chọn (`:52-54`) đã khớp tiền tố
  `/hr/talent-pools/`, không sửa.
- Nút "Lưu vào kho" ở header trang hồ sơ đơn.
- Liên kết "Tạo kho" trong hộp thoại khi công ty chưa có kho nào.

Không thêm điểm vào nào khác (không ở danh sách theo Job, `/hr/candidates`, trang so sánh, Dashboard).

### 3a. Kiểm `HrLayout` ở 375px với 6 mục

Cơ chế hiện có (`HrLayout.tsx:31-73`): **không** có thanh dưới đáy hay menu thu gọn. `< lg` là rail dọc cố định
`w-20` (80px), mỗi mục xếp dọc icon 20px + nhãn ngắn `text-xs`, `py-2`, `gap-1` giữa các mục; `aside` nằm trong `flex
min-h-screen`, không `sticky`/`fixed`.

- Chiều ngang: thêm mục **không đổi** bề rộng rail; nội dung chính vẫn còn 375 − 80 = 295px như mọi trang HR hiện có.
  Nhãn "Kho" (3 ký tự) ngắn hơn "Ứng viên", "Tin nhắn" đang có nên không xuống dòng.
- Chiều dọc: mỗi mục cao khoảng 56px (8 + 20 + 4 + 16 + 8) ⇒ 6 mục + khoảng cách ≈ 360px, cộng logo 64px ≈ 430px — vừa
  màn 375×667. Màn thấp hơn: `aside` dài theo trang (`min-h-screen`, không `overflow-hidden`) nên mục cuối không bị cắt.
- Kết luận: **không vỡ bố cục**; không đề xuất đổi cơ chế điều hướng chung. Soát tay ở REQUIREMENT 7.4 nhóm B xác nhận.

## 4. Bố cục (khung ASCII)

### 4a. Desktop (≥ `lg`) — `/hr/talent-pools`

```
┌ HrLayout ───────────────────────────────────────────────────────────────┐
│ Kho ứng viên                                              [+ Tạo kho]   │  h1 title-lg; nút Filled
│                                                                         │
│ ┌ Card outlined ──────────────────────────────────────────────────────┐ │
│ │ Backend tiềm năng                                          3 hồ sơ  │ │  cả dòng là liên kết
│ ├─────────────────────────────────────────────────────────────────────┤ │
│ │ Thực tập 2027                                              0 hồ sơ  │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

Danh sách là một Card chứa các dòng (List MD3), sắp theo tên (R-K7), không phân trang. Đổi tên/xoá đặt ở trang chi tiết.

### 4b. Desktop — `/hr/talent-pools/:id`

```
┌ HrLayout ───────────────────────────────────────────────────────────────┐
│ ← Kho ứng viên                                                          │  liên kết về danh sách
│ Backend tiềm năng                              [Đổi tên]  [Xoá kho]     │  h1; hai nút Outlined
│ 3 hồ sơ                                                                 │
│                                                                         │
│ ┌ Table ──────────────────────────────────────────────────────────────┐ │
│ │ Ứng viên      │ Tin tuyển dụng         │ Trạng thái │ Nộp  │ Thêm   │ Thao tác                         │
│ │ Nguyễn Văn A  │ Kỹ sư DevOps           │ [Bị từ chối]│ 02/10│ 16/10 │ [Mở hồ sơ] [Trao đổi] [Gỡ khỏi kho] │
│ │ Trần Thị B    │ Senior Java            │ [Chờ duyệt]│ 01/10│ 15/10 │ …                                  │
│ │               │ Tin đã xoá             │            │      │       │                                    │
│ └─────────────────────────────────────────────────────────────────────┘ │
│                        [<]  Trang 1 / 2  [>]                            │  Pagination có sẵn
└─────────────────────────────────────────────────────────────────────────┘
```

- Tên ứng viên là liên kết sang `/hr/applications/:id`. "Tin đã xoá" là dòng phụ dưới tên tin khi `jobDeleted` (R-M8).
- Không cột điểm, hạng, evidence (R-A2, `[Q12]`).
- Nút trong bảng cao 32px (UI_GUIDE mục 3): "Mở hồ sơ", "Trao đổi" là Text button; "Gỡ khỏi kho" là Text button.

### 4c. Điện thoại (375px, < `md`) — `/hr/talent-pools/:id`

```
┌ rail ┬ 295px ─────────────────────────┐
│      │ ← Kho ứng viên                 │
│      │ Backend tiềm năng              │
│      │ 3 hồ sơ                        │
│      │ [Đổi tên]        [Xoá kho]     │  hai nút chia đôi, cao 48px
│      │ ┌ Card ──────────────────────┐ │
│      │ │ Nguyễn Văn A               │ │
│      │ │ Kỹ sư DevOps               │ │
│      │ │ [Bị từ chối]               │ │
│      │ │ Nộp 02/10 · Thêm 16/10     │ │
│      │ │ [Mở hồ sơ] [Trao đổi]      │ │
│      │ │ [Gỡ khỏi kho]              │ │  nút xuống dòng khi thiếu chỗ
│      │ └────────────────────────────┘ │
│      │ [<]  Trang 1 / 2  [>]          │
└──────┴────────────────────────────────┘
```

`/hr/talent-pools` ở 375px: giữ Card danh sách như 4a, nút "Tạo kho" xuống dòng dưới tiêu đề, `w-full`.

### 4d. Header trang hồ sơ đơn + hộp thoại "Lưu vào kho"

Nút "Lưu vào kho" (Outlined, icon `Archive`) đặt **trong cụm bên phải cùng `ApplicationActionBar`, sau thanh thao tác**
(`ApplicationDetailHeader.tsx:46`), tách khỏi các nút quyết định H07 bằng khoảng cách `gap-2`. Không đổi thứ tự hay kiểu
nút của H07. `< sm`: nút `w-full` như các nút H07.

```
┌ Dialog "Lưu vào kho" ─────────────────────────┐
│ Lưu hồ sơ của Nguyễn Văn A vào kho            │
│                                               │
│ [x] Backend tiềm năng                         │  Checkbox, vùng chạm 48px
│ [ ] Thực tập 2027                             │
│                                               │
│                         [Huỷ]  [Lưu]          │  Text / Filled
└───────────────────────────────────────────────┘

Khi consentState ≠ ACTIVE:
┌ Dialog "Lưu vào kho" ─────────────────────────┐
│ ┌ khối thông tin trung tính ────────────────┐ │
│ │ Ứng viên chưa đồng ý cho công ty lưu hồ   │ │
│ │ sơ khi nộp đơn này nên không thể thêm vào │ │
│ │ kho.                                      │ │
│ └───────────────────────────────────────────┘ │
│ [ ] Backend tiềm năng   (ô khoá)              │
│ [ ] Thực tập 2027       (ô khoá)              │
│                                    [Đóng]     │
└───────────────────────────────────────────────┘
```

- Mở hộp thoại → gọi P6. Ô nào `contains = true` tick sẵn.
- `ACTIVE`: mọi ô bật; "Lưu" gửi P7 với tập đang tick; nút "Lưu" khoá khi tập không đổi so với lúc mở.
- Không `ACTIVE`: P6 lọc đồng ý (R-M6) nên mọi ô `contains = false`; **mọi ô khoá, chỉ có nút "Đóng"**. Mục lệch (nếu
  có) không hiện ở giao diện nào vì P6 và P8 đều lọc đồng ý; chỉ gỡ được qua API (P7, P9 — R-M5).
- Công ty chưa có kho: "Chưa có kho ứng viên nào." + liên kết "Tạo kho" → `/hr/talent-pools`.
- Lưu thành công → đóng hộp thoại, câu "Đã cập nhật kho ứng viên." cạnh nút (`aria-live="polite"`), focus về nút "Lưu vào
  kho". Dự án chưa có snackbar nên không thêm thư viện.

### 4e. Hộp thoại tạo / đổi tên / xoá / gỡ

- "Tạo kho" / "Đổi tên kho": một ô `Input` "Tên kho" (đổi tên điền sẵn tên hiện tại), bộ đếm "n/100", nút "Lưu" khoá khi
  sau `trim` rỗng hoặc > 100 ký tự. Lỗi 400/409 hiện dưới ô, hộp thoại không đóng. Zod chỉ báo sớm; backend là nguồn đúng.
- "Xoá kho": "Xoá kho “{tên}”? Các hồ sơ trong kho không bị xoá, chỉ không còn nằm trong kho này." → [Huỷ] [Xoá kho].
  Thành công → về `/hr/talent-pools`.
- "Gỡ khỏi kho": "Gỡ hồ sơ của {tên ứng viên} khỏi kho “{tên kho}”?" → [Huỷ] [Gỡ]. Thành công → dòng biến mất; trang
  rỗng sau khi gỡ (không phải trang 1) → lùi một trang.

## 5. Component

### 5a. File mới

| File | Vai trò |
|---|---|
| `frontend/src/pages/HrTalentPoolListPage.tsx` | `/hr/talent-pools` |
| `frontend/src/pages/HrTalentPoolDetailPage.tsx` | `/hr/talent-pools/:id` (`?page=` trên URL) |
| `frontend/src/features/talentPools/api.ts`, `queries.ts`, `types.ts` | Gọi P1–P9; khoá query `['talentPools']`, `['talentPools', id]`, `['talentPools', id, 'entries', page]`, `['applicationTalentPools', applicationId]` |
| `frontend/src/features/talentPools/TalentPoolNameDialog.tsx` | Tạo / đổi tên (React Hook Form + Zod) |
| `frontend/src/features/talentPools/ConfirmTalentPoolDialog.tsx` | Xác nhận xoá kho / gỡ hồ sơ |
| `frontend/src/features/talentPools/TalentPoolEntryList.tsx` | Bảng (≥ `md`) / thẻ (< `md`) |
| `frontend/src/features/talentPools/talentPoolLabels.ts` | Chuỗi mục 7 (L3), mẫu `features/screening/screeningLabels.ts` |
| `frontend/src/features/talentPools/SaveToTalentPoolDialog.tsx` | Hộp thoại 4d |

### 5b. File có sẵn bị sửa

| File | Thay đổi |
|---|---|
| `frontend/src/App.tsx` | Hai route trong nhóm `RequireCompany` |
| `frontend/src/components/layout/HrLayout.tsx` | Thêm một mục `NAV_ITEMS`; sửa comment khối `:14-19` (6 mục) |
| `frontend/src/features/applicationDetail/ApplicationDetailHeader.tsx` | Thêm nút "Lưu vào kho" + `SaveToTalentPoolDialog` |

### 5c. Dùng lại có sẵn

`ApplicationStatusBadge` (`features/applications/ApplicationStatusBadge.tsx`), `Pagination`
(`features/jobs/Pagination.tsx`), `components/ui/` (`button`, `card`, `table`, `checkbox`, `dialog`, `input`, `label`),
`formatDateTimeVi` (`lib/date`). Màn hình CV, đánh giá AI, trao đổi, soạn nháp: **không** vẽ lại — điều hướng sang
`/hr/applications/:id` (tab `cv` mặc định) và `/hr/applications/:id?tab=messages`.

### 5d. Bảng token

| Phần tử | Token |
|---|---|
| Nền trang / Card | `m3-surface-container` / `m3-surface`, viền `m3-outline-variant` |
| Tiêu đề trang | `text-m3-title-lg text-m3-on-surface` |
| Chữ phụ ("3 hồ sơ", ngày, "Tin đã xoá") | `text-sm text-m3-on-surface-variant` |
| Liên kết (tên ứng viên, "← Kho ứng viên") | `text-m3-primary` |
| Khối thông tin đồng ý (4d) | nền `m3-surface-container`, viền `m3-outline-variant`, chữ `m3-on-surface` — trung tính, không đỏ/vàng |
| Mục điều hướng đang chọn | Có sẵn: `bg-m3-primary-container text-m3-on-primary-container` |
| Badge trạng thái đơn | Bảng trung tính có sẵn (UI_GUIDE mục 3) |

## 6. Trạng thái hiển thị

| Màn | Đang tải | Rỗng | Lỗi |
|---|---|---|---|
| Danh sách kho | Skeleton 3 dòng trong Card | "Chưa có kho ứng viên nào." + nút "Tạo kho" | "Không tải được danh sách kho." + [Thử lại] |
| Chi tiết kho | Skeleton tiêu đề + 3 dòng | "Kho này chưa có hồ sơ nào. Mở trang hồ sơ đơn và bấm “Lưu vào kho” để thêm." | 403/404: "Không tìm thấy kho ứng viên." + liên kết "Về danh sách kho" (cùng một câu, UI_GUIDE mục 5); lỗi khác: câu chung + [Thử lại] |
| Hộp thoại lưu | Skeleton 2 dòng ô chọn; nút "Lưu" khoá | "Chưa có kho ứng viên nào." + "Tạo kho" | P6 lỗi: "Không tải được danh sách kho." + [Thử lại]; P7 lỗi: câu backend trong hộp thoại, giữ lựa chọn |
| Thao tác ghi | Nút đang gửi bị khoá, nhãn "Đang lưu…" | — | Câu backend tại chỗ |

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Mục điều hướng | "Kho ứng viên" / rail "Kho" |
| Nút | "Tạo kho", "Đổi tên", "Xoá kho", "Lưu vào kho", "Lưu", "Huỷ", "Đóng", "Mở hồ sơ", "Trao đổi", "Gỡ khỏi kho", "Gỡ" |
| Số hồ sơ | "{n} hồ sơ" |
| Cột bảng | "Ứng viên", "Tin tuyển dụng", "Trạng thái", "Ngày nộp", "Ngày thêm", "Thao tác" |
| Tin đã xoá | "Tin đã xoá" |
| Hộp thoại lưu — tiêu đề | "Lưu hồ sơ của {tên ứng viên} vào kho" |
| `NOT_GIVEN` | "Ứng viên chưa đồng ý cho công ty lưu hồ sơ khi nộp đơn này nên không thể thêm vào kho." |
| `REVOKED` | "Ứng viên đã rút đồng ý lưu hồ sơ của đơn này nên không thể thêm vào kho." |
| Lưu xong | "Đã cập nhật kho ứng viên." |
| Lỗi mã backend | Hiện nguyên câu ở REQUIREMENT mục 4.3 |

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Danh sách kho | Chi tiết kho | Hộp thoại |
|---|---|---|---|
| < `sm` (375px) | Card dòng; "Tạo kho" `w-full` dưới tiêu đề | Thẻ (4c); nút Đổi tên/Xoá chia đôi; nút cao 48px | Rộng gần hết màn, ô chọn 48px |
| `sm`–`md` | Như trên, nút cạnh tiêu đề | Thẻ | Như dialog mặc định |
| ≥ `md` | Như 4a | Bảng (4b); bảng bọc `overflow-x-auto` riêng, không cuộn ngang `<body>` | Như dialog mặc định |

## 9. Khả năng tiếp cận

- `h1` duy nhất mỗi trang (tên kho / "Kho ứng viên").
- Mỗi nút trong dòng có `aria-label` kèm tên ứng viên ("Gỡ hồ sơ của Nguyễn Văn A khỏi kho").
- Ô chọn trong hộp thoại có `Label` gắn tên kho; ô bị khoá có `aria-describedby` trỏ tới khối thông tin đồng ý.
- Hộp thoại đóng → focus về nút đã mở; xoá kho → focus về `h1` của danh sách.
- Tương phản theo token có sẵn (UI_GUIDE mục 6).

## 10. Không được làm

- Vẽ lại CV, điểm, evidence, giải thích, tin nhắn trong trang kho (R-A2).
- Hiện tổng điểm, hạng hay màu gợi ý tốt–xấu ở danh sách hồ sơ.
- Thêm ô tìm kiếm, bộ lọc, ghi chú, thẻ (REQUIREMENT mục 6).
- Đổi cơ chế điều hướng chung của `HrLayout` (thanh dưới, menu thu gọn).
- Đặt "Lưu vào kho" lẫn vào `ApplicationActionBar` hay đổi thứ tự/kiểu nút H07.
- Thêm thư viện snackbar/toast.
