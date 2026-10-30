# FR-U16 — Giao diện

> Trạng thái: ĐÃ DUYỆT (30/10/2026).

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 2 (điều hướng), 3 (component), 4 (ràng buộc), 5 (rỗng/tải/lỗi),
6 (tương phản). Mã quy tắc `R-…` là của `REQUIREMENT.md` cùng thư mục. File này không chép lại quy tắc chung, chỉ chốt
cách áp cho FR-U16.

- Nhóm: Ứng viên
- Tóm tắt: Ứng viên lưu tin để xem lại ở trang "Tin đã lưu", chỉ ứng tuyển được tin còn mở và bỏ lưu bất kỳ lúc nào
- Phụ thuộc: FR-C02, FR-U02, FR-U07, FR-U08

## 1. Trạng thái

> Trạng thái: ĐÃ DUYỆT (30/10/2026).

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| ★`/candidate/saved-jobs` | "Tin đã lưu" — danh sách tin đã lưu, nhãn tình trạng, Ứng tuyển / Đã ứng tuyển / nút khoá, Bỏ lưu | ★Mới | FR-U16 |
| `/candidate` | "Việc làm" — thêm nút Lưu trên mọi thẻ (danh sách, tìm bằng mô tả, gợi ý) | Hiện có | FR-U07, FR-U13, FR-U15; FR-U16 |
| `/candidate/dashboard` | "Bảng tin" — thêm nút Lưu trên thẻ gợi ý (dùng chung `RecommendedJobs`) | Hiện có | FR-U15; FR-U16 |
| `/` | Việc làm công khai — nút Lưu cho khách (bấm → đăng nhập); HR không thấy | Hiện có | FR-C02, FR-U07; FR-U16 |
| `/jobs/:id` | Chi tiết tin — nút "Lưu tin" cạnh "Ứng tuyển" | Hiện có | FR-C02; FR-U16 |
| `/candidate/applications/:id` | Không đổi giao diện; nhãn tình trạng tin dùng component chung (R-A5) | Hiện có | FR-U08 |

Route mới nằm trong nhóm `ProtectedRoute(CANDIDATE)` + `RequireCandidateProfileOnboarding` (`App.tsx:67-91`). Thời điểm
sửa bảng mục 7 UI_GUIDE: trong commit duyệt đặc tả (REQUIREMENT mục 11, Q6).

## 3. Điểm vào

- Menu ứng viên: mục mới **"Tin đã lưu"**, đặt **ngay sau "Việc làm"** trong `NAV_ITEMS` (`CandidateLayout.tsx:21-44`),
  cùng hiện ở thanh ngang (`≥ lg`) và sheet (`< lg`, B9 — UI_GUIDE mục 2). Sáng khi `path.startsWith('/candidate/saved-jobs')`.
- Mở thẳng URL (dán link, F5): trang tự tải.
- Từ trang Tin đã lưu đi ra: tiêu đề tin (chỉ khi `OPEN`) → `/jobs/:id`; "Ứng tuyển" → `/jobs/:id/apply`; "Xem đơn" →
  `/candidate/applications/:id`; câu rỗng → "Tìm việc làm" (`/candidate`).
- Không có điểm vào từ thông báo (không thêm loại thông báo).

## 4. Bố cục (khung ASCII)

### 4a. Thẻ việc làm có nút Lưu (`JobCard`, R-B3)

Giữ nguyên bố cục thẻ của UI_GUIDE mục 3 và `JobCard.tsx:70-139`; chỉ thêm **một** nút biểu tượng ở góc dưới bên phải.

```
┌─────────────────────────────────────────────────────────┐
│ ┌────┐  Chuyên viên phân tích dữ liệu   Hạn nộp: 30/11  │
│ │LOGO│  Công ty TNHH ABC                                │
│ │80px│  15.000.000 - 25.000.000 VND                     │
│ └────┘  [Hồ Chí Minh] [Công nghệ thông tin]             │
│         Khớp mong muốn: Hồ Chí Minh · CNTT  [Bookmark]  │
└─────────────────────────────────────────────────────────┘
          ▲ tiêu đề là <Link>, lớp phủ after:inset-0       ▲ <button> 40×40, anh em của <Link>
```

(`[Bookmark]` là tên icon `lucide-react`, không emoji — UI_GUIDE mục 3.)

- Cấu trúc: `<article class="relative …viền, nền, hover cũ…">` → tiêu đề `<Link class="after:absolute after:inset-0">`
  (cả thẻ vẫn bấm được); nút Lưu `relative z-10` nằm trong cột nội dung, hàng cuối, căn phải (`self-end`). Viền
  `hover:border-m3-primary` và `hover:shadow-sm` chuyển từ `<Link>` sang `<article>`; vòng focus của liên kết hiện trên
  cả thẻ bằng `has-[a:focus-visible]:ring-2` (hoặc `focus-within` — Plan Mode chọn, kết quả: khi Tab vào tiêu đề, cả thẻ có
  vòng focus như trước).
- Nút ở hàng cuối, không chen vào hàng tiêu đề/hạn nộp — hạn nộp giữ góc phải trên như đã chốt ở FR-U07.
- HR: không render nút; hàng cuối không để khoảng trống chờ.

### 4b. Trang chi tiết tin `/jobs/:id`

```
│ Mô tả công việc …                                        │
│ Yêu cầu …                                                │
│                                                          │
│ [ Ứng tuyển ]  [Bookmark Lưu tin]                        │   ← chưa lưu
│ [ Ứng tuyển ]  [BookmarkCheck Đã lưu]                    │   ← đã lưu (aria-pressed=true)
```

Nút "Lưu tin" đặt **ngay sau** `ApplyButton` (`PublicJobDetailPage.tsx:93-95`), cùng hàng (`flex flex-wrap gap-3`),
có chữ (không phải icon-only), `variant="outline"`, cao 44px như `ApplyButton` (`h-11`).

### 4c. Trang "Tin đã lưu" — desktop (≥ `lg`)

Khung trang như `/candidate/applications`: `mx-auto max-w-[1200px] px-4 py-8 md:px-6` trên nền `m3-surface-container`;
nội dung trong **một** `Card` trắng (`bg-m3-surface`).

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Tin đã lưu                                          Đang lưu 4/100 tin   │
├──────────────────────────────────────────────────────────────────────────┤
│ ┌────┐ Nhân viên Hành chính văn phòng            [Đang mở]               │
│ │LOGO│ Công ty TNHH Thử Nghiệm Ánh Dương                                 │
│ └────┘ 9.000.000 - 14.000.000 VND · Hà Nội · Hạn nộp: 21/11/2026         │
│        Đã lưu 10:15 30/10/2026                  [Ứng tuyển] [Bỏ lưu]     │
│ ──────────────────────────────────────────────────────────────────────── │
│ ┌────┐ Chuyên viên Tuyển dụng                    [Đã hết hạn nộp]        │
│ │LOGO│ Công ty TNHH Thử Nghiệm Ánh Dương          (tiêu đề không là link)│
│ └────┘ 12.000.000 - 18.000.000 VND · Hồ Chí Minh · Hạn nộp: 29/10/2026   │
│        Đã lưu 09:00 25/10/2026          [Ứng tuyển (khoá)] [Bỏ lưu]      │
│        Tin đã hết hạn nộp hồ sơ.                                          │
│ ──────────────────────────────────────────────────────────────────────── │
│ ┌────┐ Kế toán tổng hợp                          [Đang mở]               │
│ │LOGO│ Công ty …                                                         │
│ └────┘ …                                                                 │
│        Đã lưu 08:30 20/10/2026   Đã ứng tuyển [Chờ duyệt] Xem đơn →      │
│                                                           [Bỏ lưu]       │
│ ──────────────────────────────────────────────────────────────────────── │
│ ┌────┐ Chuyên viên Pháp chế                      [Không còn đăng]        │
│ │LOGO│ Công ty …                                                         │
│ └────┘ Đã lưu 07:00 18/10/2026                              [Bỏ lưu]     │
└──────────────────────────────────────────────────────────────────────────┘
```

- Mỗi tin là một hàng `<li>` trong `<ul>`, phân cách bằng `border-m3-outline-variant`. Không dùng `JobCard` (thẻ là liên
  kết toàn khối, còn ở đây tiêu đề có lúc không là liên kết và có nhiều nút).
- Dòng tóm tắt (R-V7): lương (`formatSalary`, thiếu → "Không công bố"), khu vực (`jobLocationText`, thiếu → "Chưa có dữ
  liệu"), "Hạn nộp: " + `formatDeadline` (null → "Không giới hạn"), nối bằng " · ". Tin `UNAVAILABLE` không có dòng này
  (R-V6).
- Nhãn tình trạng: `JobAvailabilityBadge` cạnh tiêu đề, mọi giá trị (kể cả `OPEN`).
- Câu lý do (tin không mở, chưa có đơn) nằm ngay dưới hàng nút, nối với nút khoá bằng `aria-describedby`.

### 4d. Trang "Tin đã lưu" — điện thoại (375px)

Chiều rộng nội dung ≈ 311px (375 − 2×16 khung − 2×16 padding `Card`, như FR-U08 UI.md 4d).

```
┌──────────────────────────────┐
│ Tin đã lưu                   │
│ Đang lưu 4/100 tin           │
├──────────────────────────────┤
│ ┌──┐ Nhân viên Hành chính    │
│ │  │ văn phòng               │
│ └──┘ [Đang mở]               │
│ Công ty TNHH Thử Nghiệm Ánh  │
│ Dương                        │
│ 9.000.000 - 14.000.000 VND   │
│ Hà Nội · Hạn nộp: 21/11/2026 │
│ Đã lưu 10:15 30/10/2026      │
│ ┌──────────────────────────┐ │
│ │        Ứng tuyển         │ │
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │         Bỏ lưu           │ │
│ └──────────────────────────┘ │
│ ──────────────────────────── │
│ …                            │
└──────────────────────────────┘
```

Logo thu về 48px; nút `w-full`, xếp dọc, **trong luồng trang** (không `fixed`/`sticky`).

### 4e. Hộp xác nhận bỏ lưu tin không còn mở (Q5)

```
┌──────────────────────────────────────────────┐
│ Bỏ lưu tin này?                              │
│                                              │
│ Tin "Chuyên viên Tuyển dụng" không còn nhận  │
│ hồ sơ. Sau khi bỏ lưu, bạn sẽ không lưu lại  │
│ được tin này.                                │
│                                              │
│                       [Huỷ]  [Bỏ lưu]        │
└──────────────────────────────────────────────┘
```

Chỉ hiện khi `availability ≠ OPEN` (ở trang Tin đã lưu). Tin `OPEN` bỏ lưu ngay, không hỏi. Trên thẻ việc làm luôn là tin
`OPEN` nên không có hộp này.

## 5. Component

### 5a. File mới (chốt tên — `REQUIREMENT.md` mục 7.3 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/features/savedJobs/api.ts` | Gọi E1–E4 |
| `frontend/src/features/savedJobs/queries.ts` | `useSavedJobIdsQuery` (khoá `['saved-job-ids']`, `enabled` chỉ khi `user.role === 'CANDIDATE'`), `useSavedJobsQuery` (`['saved-jobs']`), `useSaveJobMutation`, `useUnsaveJobMutation` (cập nhật lạc quan `['saved-job-ids']`; lỗi thì chỉ đưa riêng tin vừa bấm về trạng thái trước khi bấm, không trả ảnh chụp cả danh sách (B8); không refetch ids; chỉ đánh dấu cũ `['saved-jobs']` — R-B5) |
| `frontend/src/features/savedJobs/types.ts` | `SavedJob`, `SavedJobApplication` khớp DTO REQUIREMENT mục 4.3 |
| `frontend/src/features/savedJobs/SaveJobButton.tsx` | Nút bật/tắt dùng chung cho thẻ (`variant="icon"`) và trang chi tiết (`variant="labeled"`); xử lý vai trò R-B2, lỗi R-B6 |
| `frontend/src/features/savedJobs/SavedJobItem.tsx` | Một hàng của trang Tin đã lưu (mục 4c) + quy tắc nút R-V4 |
| `frontend/src/features/savedJobs/UnsaveConfirmDialog.tsx` | Hộp mục 4e |
| `frontend/src/features/savedJobs/savedJobTexts.ts` | Chuỗi mục 7 |
| `frontend/src/features/jobs/JobAvailabilityBadge.tsx` | Nhãn tình trạng tin dùng chung (R-A5) — chuyển nguyên `<span>` ở `JobSummarySection.tsx:37-39` |
| `frontend/src/features/jobs/jobAvailability.ts` | Kiểu `JobAvailability` + `JOB_AVAILABILITY_LABELS`, chuyển nguyên từ `candidateApplicationDetail/types.ts:6` và `candidateApplicationDetail/jobAvailabilityLabels.ts:5-11`; xoá file nhãn cũ |
| `frontend/src/pages/CandidateSavedJobsPage.tsx` | Trang `/candidate/saved-jobs` |

### 5b. File sửa

| File | Sửa |
|---|---|
| `features/jobs/JobCard.tsx` | Cấu trúc R-B3 (mục 4a) + `SaveJobButton variant="icon"`; mọi phần hiển thị cũ giữ nguyên |
| `pages/PublicJobDetailPage.tsx` | `SaveJobButton variant="labeled"` sau `ApplyButton` |
| `components/layout/CandidateLayout.tsx` | Mục "Tin đã lưu" (mục 3) |
| `App.tsx` | Route `/candidate/saved-jobs` |
| `features/candidateApplicationDetail/JobSummarySection.tsx`, `types.ts` | Import `JobAvailabilityBadge`/`JobAvailability` từ `features/jobs/`; giao diện FR-U08 không đổi |

### 5c. Dùng lại có sẵn (không viết lại)

| Có sẵn | Cách dùng |
|---|---|
| `useAuth` (`features/auth/useAuth`) | Vai trò cho R-B2, như `ApplyButton.tsx:5-24` |
| `ApplicationStatusBadge` (`features/applications/`) | Badge đơn ở "Đã ứng tuyển" |
| `formatSalary`, `jobLocationText`, `formatDeadline`, `formatDateTimeVi` | Dòng tóm tắt, "Đã lưu {thời điểm}" |
| `Card`, `Button`, `Dialog` (`components/ui`) | Khung trang, nút, hộp xác nhận — không sửa file trong `components/ui` |
| `extractErrorMessage` (`lib/httpError`) | Câu lỗi 409 từ backend (R-B6) |
| Icon `Bookmark` (chưa lưu, viền) / `BookmarkCheck` (đã lưu) của `lucide-react`; `Building2` cho ô logo trống | Như `JobCard` |

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Nút Lưu trên thẻ, chưa lưu | trong suốt | icon `text-m3-on-surface-variant`, viền `border-m3-outline` | 40×40, bo `--radius-badge`; viền ≥ 3:1 (UI_GUIDE mục 6, `m3-outline` 4.83:1) |
| Nút Lưu trên thẻ, đã lưu | `bg-m3-primary-container` | icon đặc `text-m3-on-primary-container` | 6.23:1; khác trạng thái bằng icon đặc + `aria-pressed`, không chỉ bằng màu |
| Nút "Lưu tin"/"Đã lưu" ở `/jobs/:id` | — | `variant="outline"`; đã lưu thêm icon đặc | Không cạnh tranh màu với "Ứng tuyển" (`m3-tertiary`) |
| Nhãn tình trạng tin (5 giá trị) | `bg-m3-surface` | `border border-m3-outline text-m3-on-surface` | **Một** kiểu (R-A5) |
| Nút "Ứng tuyển" (tin mở) | `bg-m3-tertiary` | `text-m3-on-tertiary` | Cùng kiểu `ApplyButton` (5.45:1) |
| Nút "Ứng tuyển" khoá | — | `disabled` của `Button` (`variant="outline"`) | Không đỏ; lý do bằng chữ ngay dưới |
| Nút "Bỏ lưu" | — | `variant="outline"` | Không đỏ (thao tác đảo được với tin mở) |
| Tiêu đề trang (h1), tên tin | — | `text-m3-on-surface` | |
| Tên tin là liên kết (`OPEN`) | (thẻ trắng) | `text-m3-on-surface hover:text-m3-primary` | Như `JobCard` |
| Tên công ty, "Đã lưu …", số đếm, câu lý do | — | `text-m3-on-surface-variant` | Chỉ trên `m3-surface` (4.83:1) |
| Lương | — | `text-m3-tertiary` | Như `JobCard` |
| "Xem đơn →", "Tìm việc làm" | (thẻ trắng) | `text-m3-primary` | 4.64:1 chỉ trên `m3-surface` |
| Câu lỗi dưới nút (409/mạng) | — | icon `AlertCircle` `text-m3-error` + chữ `text-m3-on-surface` | Trên thẻ việc làm nền `m3-surface` |
| Phân cách hàng | — | `border-m3-outline-variant` | Chỉ để phân khối |

## 6. Trạng thái hiển thị

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Khách | Nút Lưu (thẻ, `/jobs/:id`) | Trạng thái "chưa lưu"; bấm → `/login` với `state.from` (R-B2); không gọi API |
| HR | Nút Lưu | Không render (R-B2); không gửi request `saved-jobs` nào (B5) |
| Đang tải `GET /ids` | Nút Lưu | Nút khoá, icon viền; không skeleton riêng |
| Lỗi tải `GET /ids` | Nút Lưu | Nút khoá; `title`/`aria-describedby` "Không tải được trạng thái lưu, vui lòng tải lại trang." — không chặn phần còn lại của thẻ |
| Đang lưu / bỏ lưu | Nút Lưu | Trạng thái đổi **ngay** khi bấm (lạc quan, R-B5) ở mọi thẻ cùng tin; nút của chính tin đó khoá tới khi API trả; API lỗi → riêng tin đó trở lại trạng thái trước khi bấm (thẻ của tin khác giữ nguyên, B8) + câu lỗi |
| 409 `JOB_NOT_SAVEABLE` / `SAVED_JOB_LIMIT_REACHED` | Dưới nút | Câu backend trả về, `role="status"`; nút giữ trạng thái cũ (R-B6) |
| Lỗi mạng/5xx khi lưu | Dưới nút | "Không lưu được tin, vui lòng thử lại." |
| Đang tải danh sách | Trang Tin đã lưu | Skeleton xám 3 hàng (logo + 3 dòng), không spinner toàn trang |
| Lỗi tải danh sách | Trang | "Không tải được danh sách tin đã lưu." + nút "Thử lại" |
| Danh sách rỗng | Trang | "Bạn chưa lưu tin nào." + liên kết "Tìm việc làm" (`/candidate`) |
| Có dữ liệu | Trang | Hàng theo R-V4, thứ tự backend trả (R-V1); không sắp lại ở frontend |
| `application != null` | Hàng | "Đã ứng tuyển" + `ApplicationStatusBadge` + "Xem đơn →" (R-V4 dòng 1) |
| `OPEN`, chưa có đơn | Hàng | Tiêu đề là liên kết; nút "Ứng tuyển" |
| `EXPIRED`/`PAUSED`/`CLOSED`, chưa có đơn | Hàng | Tiêu đề không liên kết; nút "Ứng tuyển" khoá + câu lý do (mục 7) |
| `UNAVAILABLE` | Hàng | Chỉ logo, tên tin, công ty, nhãn "Không còn đăng", "Đã lưu …", khối đơn nếu có, "Bỏ lưu"; không dòng tóm tắt, không nút Ứng tuyển (R-V6) — câu lý do vẫn hiện nếu chưa có đơn |
| Thiếu dữ liệu tóm tắt | Hàng | Khu vực thiếu → "Chưa có dữ liệu"; lương null → "Không công bố"; hạn null → "Không giới hạn" (UI_GUIDE mục 4) |
| Bỏ lưu tin `OPEN` | Hàng | Bỏ ngay; hàng biến mất sau khi danh sách làm mới; số đếm giảm |
| Bỏ lưu tin không mở | Hàng | Hộp mục 4e trước; Huỷ → không gì đổi |
| Bỏ lưu lỗi | Hàng / hộp | "Không bỏ lưu được tin, vui lòng thử lại." |
| Không có quyền (HR mở URL) | Trang | `ProtectedRoute` xử lý như mọi route ứng viên (điều hướng theo vai trò hiện có) |

Không có trạng thái "AI đang xử lý": FR không gọi AI.

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Mục menu, tiêu đề trang (h1) | "Tin đã lưu" |
| Số đếm | "Đang lưu {n}/100 tin" |
| Nút thẻ — `aria-label` | Chưa lưu: "Lưu tin {title}" · Đã lưu: "Bỏ lưu tin {title}" |
| Nút trang chi tiết | Chưa lưu: "Lưu tin" · Đã lưu: "Đã lưu" (`aria-pressed`, `aria-label` "Bỏ lưu tin này" khi đã lưu) |
| Thời điểm lưu | "Đã lưu {HH:mm dd/MM/yyyy}" (`formatDateTimeVi`) |
| Nút | "Ứng tuyển" · "Bỏ lưu" |
| Đã ứng tuyển | "Đã ứng tuyển" · liên kết "Xem đơn →" |
| Lý do khoá — `EXPIRED` | "Tin đã hết hạn nộp hồ sơ." |
| Lý do khoá — `PAUSED` | "Nhà tuyển dụng đang tạm dừng nhận hồ sơ." |
| Lý do khoá — `CLOSED` | "Tin đã đóng, không nhận thêm hồ sơ." |
| Lý do khoá — `UNAVAILABLE` | "Tin không còn được đăng." |
| Nhãn tình trạng (5 giá trị) | Nguyên văn FR-U08: "Đang mở" · "Đã hết hạn nộp" · "Tạm dừng" · "Đã đóng" · "Không còn đăng" |
| Hộp xác nhận | Tiêu đề "Bỏ lưu tin này?" · nội dung "Tin \"{title}\" không còn nhận hồ sơ. Sau khi bỏ lưu, bạn sẽ không lưu lại được tin này." · nút "Huỷ" · "Bỏ lưu" · đang chạy "Đang bỏ lưu..." |
| Rỗng | "Bạn chưa lưu tin nào." · liên kết "Tìm việc làm" |
| Lỗi tải danh sách | "Không tải được danh sách tin đã lưu." · nút "Thử lại" |
| Lỗi tải trạng thái lưu | "Không tải được trạng thái lưu, vui lòng tải lại trang." |
| Lỗi lưu (mạng/5xx) | "Không lưu được tin, vui lòng thử lại." |
| Lỗi bỏ lưu (mạng/5xx) | "Không bỏ lưu được tin, vui lòng thử lại." |
| 409 từ backend (hiện nguyên văn) | `JOB_NOT_SAVEABLE`: "Tin tuyển dụng này hiện không nhận lưu vì không còn mở." · `SAVED_JOB_LIMIT_REACHED`: "Bạn đã lưu tối đa 100 tin. Hãy bỏ lưu bớt để lưu tin mới." |
| Khu vực thiếu | "Chưa có dữ liệu" |
| Lương null | "Không công bố" |

Chuỗi có sẵn của `ApplicationStatusBadge`, `formatDeadline` giữ nguyên.

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Thẻ việc làm | Trang Tin đã lưu | Menu ứng viên |
|---|---|---|---|
| Expanded (≥ `lg`) | Lưới 2 cột như cũ; nút Lưu 40×40 góc dưới phải | Hàng: logo 64px trái; nội dung giữa; nhóm nút phải cùng hàng "Đã lưu …" | Thanh ngang 6 mục |
| Medium (`sm`–`lg`) | 1 cột như cũ; nút như expanded | Nhóm nút xuống dòng dưới "Đã lưu …", căn trái | *(B9)* Nút menu mở sheet (`MobileNavSheet`), đủ 6 mục; không thanh ngang. Lý do: đo 30/10/2026 ở 768px, thanh ngang tràn 40px khi còn 5 mục (có từ trước FR-U16) và 127px khi thêm "Tin đã lưu" |
| Compact (< `sm`, 375px) | Nút Lưu giữ 40×40 (vùng chạm ≥ 40px, UI_GUIDE mục 3) | Mục 4d: logo 48px, nút `w-full` xếp dọc | Sheet (`MobileNavSheet`) |

- Không thanh dưới đáy cố định ở bất kỳ khổ nào; không cuộn ngang toàn trang ở 375px.
- Tên tin, tên công ty tự ngắt từ dài (`break-words`); thẻ việc làm giữ `line-clamp-2` của tiêu đề.

## 9. Khả năng tiếp cận

- Nút Lưu là `<button type="button">` có `aria-pressed` (true/false) và `aria-label` chứa tên tin (mục 7) — thẻ có nhiều
  nút giống nhau trên một trang, nhãn chung chung "Lưu" không phân biệt được.
- Không lồng `<button>` trong `<a>` (R-B3). Thứ tự Tab trong thẻ: liên kết tiêu đề → nút Lưu.
- Trạng thái đã lưu không chỉ bằng màu: icon đặc + `aria-pressed`; ở `/jobs/:id` thêm chữ "Đã lưu" (UI_GUIDE mục 6).
- Icon trang trí `aria-hidden="true"`.
- Câu lỗi/kết quả dưới nút `role="status"`; nút "Ứng tuyển" khoá nối câu lý do bằng `aria-describedby`.
- Trang Tin đã lưu: một `h1`; danh sách là `<ul>`/`<li>`; tên tin trong mỗi hàng là `h2`.
- Sau khi bỏ lưu một hàng, focus chuyển về tiêu đề trang (`h1`, `tabIndex={-1}`) vì hàng chứa nút vừa bấm đã biến mất.
- Hộp xác nhận dùng `Dialog` (Radix lo focus trap, Esc).
- Tương phản: chỉ dùng các cặp đã đo ở UI_GUIDE mục 1c/6 và bảng 5d; không có cặp mới cần đo.

## 10. Không được làm

- Không lồng nút Lưu trong `<Link>` của thẻ; không `preventDefault` trên nút nằm trong thẻ `<a>` (R-B3).
- Không đổi vị trí hạn nộp, lương, tag, dòng điều kiện khớp của `JobCard` (mục 4a).
- Không thêm cờ `saved` vào kiểu `JobSummary`/`JobDetail` hay API công khai; trạng thái lưu chỉ từ `GET /saved-jobs/ids`
  (R-B4).
- Không gọi API trạng thái lưu theo từng thẻ; không refetch `['saved-job-ids']` sau mỗi lần bấm; không quên hoàn lại
  cache khi API lỗi; không hoàn lại bằng ảnh chụp cả danh sách ids — chỉ riêng tin vừa bấm (R-B4, R-B5, B8).
- Không gửi request `saved-jobs` khi khách hoặc HR xem trang (R-B2).
- Không màu xanh/đỏ cho nhãn tình trạng tin, nút Bỏ lưu hay nút khoá; không icon ✓/✗ (R-A5, R-G5).
- Không tạo bộ nhãn tình trạng thứ hai; dùng `JobAvailabilityBadge` (R-A5).
- Không liên kết sang `/jobs/:id` khi tình trạng khác `OPEN` (R-V5).
- Không hiện lương/ngành/khu vực/hạn nộp cho tin `UNAVAILABLE` (R-V6).
- Không dựng form ứng tuyển trong trang Tin đã lưu (R-V8).
- Không hiện điểm số, % phù hợp, thứ hạng, nhãn "phù hợp"; không sắp lại danh sách ở frontend (R-G2, R-V1).
- Không hiện số người đã lưu tin ở bất kỳ màn hình nào, kể cả phía HR (R-G3).
- Không nút `fixed`/`sticky` ở đáy màn hình điện thoại.
- Không sửa file trong `components/ui`.
- Không sao chép logo, tên thương hiệu hay CSS của trang tuyển dụng nào (CLAUDE.md §8).
