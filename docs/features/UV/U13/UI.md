# FR-U13 — Giao diện

> Trạng thái: ĐÃ DUYỆT 18/10/2026. Đi cùng `REQUIREMENT.md` (cùng trạng thái). Quy tắc nghiệp vụ, mã lỗi, DTO:
> `REQUIREMENT.md`; file này chỉ nói về màn hình.

Nguồn: `docs/UI_GUIDE.md` (token mục 1, component mục 3 — Search bar, assist chip trung tính, Linear progress; ràng buộc mục 4
"Nội dung do AI tạo", "Lý do gợi ý/khớp", "Dữ liệu thiếu"; trạng thái mục 5; tiếp cận mục 6; bản đồ mục 7). Không có route mới:
mở rộng màn hình "Việc làm" có sẵn.

## 1. Trạng thái

> Trạng thái: ĐÃ DUYỆT 18/10/2026.

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Thay đổi |
|---|---|---|
| `/candidate` | "Việc làm" — **màn hình có sẵn, mở rộng** (không phải ★Mới) | Bản đồ đã ghi "FR-U13 (ô tìm bằng mô tả)" (`UI_GUIDE.md:340`). Thêm bộ chọn chế độ "Tìm theo từ khoá" / "Tìm bằng mô tả" trên `JobBoard`, chỉ khi `CandidateJobListPage` truyền prop |
| `/` | "Việc làm" công khai | **Không đổi** — `PublicJobListPage` không truyền prop (REQUIREMENT N6) |

Sửa `UI_GUIDE.md` mục 7 dòng `/candidate` ở đợt cuối (REQUIREMENT mục 10, D4).

## 3. Điểm vào

- `/candidate` → bộ chọn chế độ đầu thanh tìm kiếm → **"Tìm bằng mô tả"**. Mặc định luôn là "Tìm theo từ khoá" khi vào trang
  hoặc tải lại (chế độ và câu không lên URL — REQUIREMENT Q-T6).
- Không có điểm vào nào khác (không ở `/candidate/dashboard`, không ở trang công khai, không ở header).
- Lối ra: bấm/xoá một thẻ điều kiện, hoặc nút **"Sửa điều kiện bằng bộ lọc"** → chế độ "Tìm theo từ khoá" với URL bộ lọc U07
  (REQUIREMENT Q-T5); hoặc tự chọn lại "Tìm theo từ khoá" (URL bộ lọc giữ như trước khi vào chế độ mô tả).

## 4. Bố cục (khung ASCII)

### 4a. Desktop (≥ `lg`) — chế độ mô tả, có kết quả

```
┌ CandidateLayout ─────────────────────────────────────────────────────────────────────────────┐
│ max-w-[1200px]                                                                                 │
│ ┌ thanh tìm kiếm (rounded-m3-sm border outline-variant bg-m3-surface p-4) ──────────────────┐ │
│ │ Cách tìm việc:  [ Tìm theo từ khoá ][■ Tìm bằng mô tả ]   (segmented, radio thuần)        │ │
│ │ Mô tả công việc bạn muốn tìm                                                               │ │
│ │ [🔍 content marketing lương trên 15 triệu ở Hà Nội, không yêu cầu kinh nghiệm ] [ Tìm ]    │ │
│ │ Ví dụ: content marketing lương trên 15 triệu ở Hà Nội…                         74/300      │ │
│ │ Mỗi lần tìm dùng một lượt AI.                                                              │ │
│ └────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                                │
│ ┌ khối AI (surface-container-high + viền outline-variant, rounded-m3-sm p-4) ───────────────┐ │
│ │ ✦ Do AI tạo                                                                                │ │
│ │ Hệ thống hiểu yêu cầu của bạn                                       (title-md, on-surface) │ │
│ │ [Hà Nội ✕] [Marketing - Truyền thông ✕] [Lương từ 15 triệu ✕]          (chip trung tính)   │ │
│ │ Xếp theo mô tả: “content marketing, không yêu cầu kinh nghiệm”                             │ │
│ │ ⓘ Đã bỏ qua thông tin nhân thân trong mô tả…                         (chỉ khi có)          │ │
│ │ Kiểm tra lại các điều kiện; bấm vào một điều kiện để sửa bằng bộ lọc.                      │ │
│ │                                                        [ Sửa điều kiện bằng bộ lọc ]       │ │
│ └────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                                │
│ Kết quả tìm theo mô tả                                    Tìm thấy 23 việc làm (tối đa 50)     │
│ ┌ JobCard ──────────────────────────────┐ ┌ JobCard ──────────────────────────────┐           │
│ │ … Khớp điều kiện: Hà Nội · Marketing  │ │ …                                     │  (2 cột)  │
│ └───────────────────────────────────────┘ └───────────────────────────────────────┘           │
│ ── Các việc làm dưới đây chưa được hệ thống phân tích nội dung … (chỉ khi có) ──             │
│ ┌ JobCard ┐ ┌ JobCard ┐                                                                        │
│                                 [ ‹ ] 1 2 3 [ › ]   (Pagination có sẵn, chia ở trình duyệt)    │
└────────────────────────────────────────────────────────────────────────────────────────────────┘
```

- Trong chế độ mô tả: **ẩn** `RecommendedJobs`, `JobFilterBar` (gồm cả sắp xếp, bộ đếm U07) và tiêu đề "Việc làm đang tuyển";
  bộ chọn chế độ nằm trong khung thanh tìm kiếm.
- Câu ngăn cách (`rankedByDescription` đổi từ `true` sang `false`) là một hàng `col-span-full` giữa lưới, chỉ ở trang chứa ranh
  giới; trang chỉ có việc `false` thì câu ở đầu trang. Nhánh `ranking = NEWEST` không có câu ngăn cách (câu ở khối AI đã nói rõ).

### 4b. Chế độ từ khoá (mặc định) — chỉ thêm bộ chọn

```
┌ thanh tìm kiếm ───────────────────────────────────────────────────────────────────────────┐
│ Cách tìm việc:  [■ Tìm theo từ khoá ][ Tìm bằng mô tả ]                                     │
│ (JobFilterBar U07 như cũ, không đổi một dòng)                                                │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
ⓘ Đã chuyển sang lọc thường, bỏ thứ tự theo mô tả.      (chỉ sau khi sửa/xoá thẻ — mục 6)
(Gợi ý cho bạn — theo điều kiện U15 có sẵn; danh sách U07)
```

Bộ chọn nằm **trên** `JobFilterBar`, ngoài component đó (`JobFilterBar.tsx` không sửa — REQUIREMENT 7.3).

### 4c. Chế độ mô tả — trước khi tìm / đang tìm / lỗi

```
┌ thanh tìm kiếm ──────────────────────────────────────────────┐
│ [ Tìm theo từ khoá ][■ Tìm bằng mô tả ]                        │
│ [🔍 Mô tả công việc bạn muốn tìm…                ] [ Tìm ]     │
│ ⚠ Vui lòng nhập mô tả công việc bạn muốn tìm.  (lỗi client)    │
└───────────────────────────────────────────────────────────────┘
Trước khi tìm:  câu giới thiệu `nlIntro` (text-sm on-surface), không danh sách.
Đang tìm:       ▬▬▬▬▬ LinearProgress "Đang phân tích mô tả và tìm việc làm"; nút "Đang tìm…" khoá.
Lỗi:            ⚠ {message backend hoặc nlFailedFallback}   [ Thử lại ]
```

### 4d. Điện thoại (375px)

```
┌ 375px ────────────────────────────────┐
│ [Từ khoá        ][■ Mô tả          ]   │  (segmented w-full, nhãn ngắn — mục 7)
│ Mô tả công việc bạn muốn tìm           │
│ [                                  ]   │  (w-full)
│ [               Tìm                ]   │  (w-full)
│ 74/300                                 │
│ ┌ khối AI ───────────────────────────┐ │
│ │ ✦ Do AI tạo                         │ │
│ │ [Hà Nội ✕] [Lương từ 15 triệu ✕]    │ │  (chip xuống dòng)
│ │ Xếp theo mô tả: “…”                 │ │
│ │ [  Sửa điều kiện bằng bộ lọc     ]  │ │  (w-full)
│ └─────────────────────────────────────┘ │
│ ┌ JobCard (1 cột) ┐                     │
└────────────────────────────────────────┘
```

## 5. Component

### 5a. File mới (chốt tên — REQUIREMENT mục 9 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/features/jobSearch/types.ts` | `JobSearchRequest`, `JobSearchResponse`, `JobSearchInterpretation`, `JobSearchItem`, `JobSearchRanking` (khớp REQUIREMENT 4.5; `job` dùng `JobSummary` có sẵn) |
| `frontend/src/features/jobSearch/api.ts` | `searchJobsByDescription(req)` → `POST /candidates/job-search`, `timeout: JOB_SEARCH_REQUEST_TIMEOUT_MS = 40_000` (backend tệ nhất ≈ 35 s — REQUIREMENT O4; chỉ đặt cho request này, khuôn `features/cvBuilder/api.ts:11-13`) |
| `frontend/src/features/jobSearch/jobSearchLabels.ts` | Mọi chuỗi mục 7; hằng `JOB_SEARCH_MIN_LENGTH = 2`, `JOB_SEARCH_MAX_LENGTH = 300`, `JOB_SEARCH_PAGE_SIZE = 10`; hàm định dạng nhãn lương (mục 7) |
| `frontend/src/features/jobSearch/normalizeQuery.ts` | Chuẩn hoá để **đếm** độ dài giống backend: `normalize('NFC')`; gộp đúng tập khoảng trắng của Java `\s` — `/[ \t\n\x0B\f\r]+/g` — thành một dấu cách (**không** dùng `\s` của JS: nó khớp cả NBSP, U+FEFF, U+2028, U+3000 mà Java không gộp); bỏ ký tự ≤ U+0020 ở hai đầu như `String.trim()` của Java (**không** dùng `trim()` của JS) — REQUIREMENT mục 13, L7; dữ liệu gửi đi là chuỗi ứng viên gõ (backend chuẩn hoá lại) |
| `frontend/src/features/jobSearch/buildFilterParams.ts` | `interpretation` (+ điều kiện bị xoá, tuỳ chọn) → `URLSearchParams` của U07: `locationCode`, `categoryCode` (lặp), `salaryMin`, `salaryMax`, `workMode` (lặp); không `keyword`, không `sort`, không `page`. Mô tả **không** được chuyển (U07 không có) |
| `frontend/src/features/jobSearch/SearchModeSwitch.tsx` | `fieldset` + `legend` sr-only "Cách tìm việc" + hai `input type="radio"` thuần dạng segmented (dự án chưa có component segmented — khuôn `TemplatePicker` của U12) |
| `frontend/src/features/jobSearch/NaturalLanguageSearch.tsx` | Toàn bộ chế độ mô tả: ô nhập + nút Tìm + kiểm client + `useMutation` + trạng thái mục 6 + `InterpretationPanel` + `NaturalLanguageResults`. Quy tắc: (1) mỗi lần gửi ghi lại câu đã gửi; nút "Thử lại" gửi **đúng** câu đó; (2) đang chờ thì không gửi thêm (nút khoá, Enter bị bỏ qua); (3) kết quả về sau khi component unmount hoặc sau một lần gửi mới hơn → bỏ (so mã lượt gửi); (4) gửi mới → ẩn kết quả cũ ngay; (5) sửa ô sau khi có kết quả **không** xoá kết quả |
| `frontend/src/features/jobSearch/InterpretationPanel.tsx` | Khối AI 4a: nhãn "Do AI tạo" + `Sparkles`, tiêu đề, chip điều kiện (nhãn tỉnh/ngành tra từ `useCatalogsQuery` có sẵn — mã không còn trong danh mục thì hiện chính mã), dòng mô tả, câu nhân thân, câu nhắc, nút "Sửa điều kiện bằng bộ lọc". Mỗi chip = nút chính (sửa) + nút icon `X` (xoá); gọi `onEditConditions(removed?)` |
| `frontend/src/features/jobSearch/NaturalLanguageResults.tsx` | Tiêu đề kết quả + bộ đếm + lưới `JobCard` (`matchedConditions`, `matchedConditionsLabel="Khớp điều kiện:"`) + câu ngăn cách + `Pagination` có sẵn (trang giữ trong state, về trang 1 mỗi lần có kết quả mới, đổi trang cuộn lên tiêu đề kết quả) |

### 5b. File có sẵn bị sửa

| File | Sửa gì |
|---|---|
| `frontend/src/features/jobs/JobBoard.tsx` | Prop `naturalLanguageSearch?: boolean` (mặc định `false`). Khi `true`: render `SearchModeSwitch` trên `JobFilterBar`; state `mode: 'keyword' \| 'description'` (mặc định `'keyword'`, không lên URL); `mode = 'description'` → render `NaturalLanguageSearch` thay cho `RecommendedJobs` + `JobFilterBar` + tiêu đề + `JobList`; nhận `onEditConditions` → `setSearchParams(buildFilterParams(...))` (một lần ghi — một mục lịch sử), `mode = 'keyword'`, bật câu `switchedNotice`. `useJobsQuery` chỉ bật khi `mode = 'keyword'` (không gọi `/public/jobs` thừa ở chế độ mô tả). Khi `false`: hành vi **y hệt** hiện tại |
| `frontend/src/pages/CandidateJobListPage.tsx` | `<JobBoard showRecommendations naturalLanguageSearch />` |
| `frontend/src/features/jobs/JobCard.tsx` | Prop tuỳ chọn `matchedConditionsLabel?: string` (mặc định "Khớp mong muốn:"; dùng cho cả chữ hiện và `aria-label` — "Điều kiện khớp: …" giữ nguyên). U15 không truyền → không đổi |

`useJobFilters.ts`, `JobFilterBar.tsx`, `RecommendedJobs.tsx`, `JobList.tsx`, `PublicJobListPage.tsx`, `components/ui/*`
**không** sửa (REQUIREMENT 7.3).

### 5c. Dùng lại có sẵn

`Input`, `Label`, `Button` (`components/ui` — không thêm component mới), `CandidateLayout`, `JobCard`, `JobCardSkeleton`,
`Pagination` (`features/jobs/Pagination.tsx`), `LinearProgress` (`features/resumes/LinearProgress.tsx`), `useCatalogsQuery`,
`WORK_MODE_LABELS` (`features/jobs/jobLabels.ts:39-43`), `http` (`lib/http.ts`), TanStack Query `useMutation`. Icon lucide:
`Search`, `Sparkles`, `X`, `Info`, `AlertCircle`, `SlidersHorizontal`, `Check`. Không thêm thư viện.

### 5d. Bảng token

| Phần | Token / class | Ghi chú |
|---|---|---|
| Khung thanh tìm kiếm | như `JobFilterBar` (`rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-4`) | Cùng khung để hai chế độ trông liền |
| Segmented | mỗi lựa chọn `min-h-10 border border-m3-outline px-4 text-m3-label-lg text-m3-on-surface`; đang chọn `bg-m3-primary-container text-m3-on-primary-container` (6.23:1 — UI_GUIDE `:200`; theme không có token `secondary-container`) + icon `Check` 18px trước chữ (không chỉ bằng màu); bo `rounded-m3-button` hai đầu | Trên `m3-surface` |
| Ô mô tả | khuôn `KeywordField` của `JobFilterBar.tsx:135-145` (`h-12 sm:h-10`, viền `m3-outline`, icon `Search`) | ≥ 3:1 viền điều khiển |
| Nút "Tìm" | khuôn nút "Tìm kiếm" của `JobFilterBar.tsx:147-152` (`bg-m3-primary text-m3-on-primary`) | |
| Câu gợi ý / bộ đếm dưới ô | `text-m3-body-sm text-m3-on-surface-variant`; vượt 300 → `text-m3-error` | Trên `m3-surface` |
| Lỗi dưới ô | icon `AlertCircle text-m3-error` + chữ `text-sm text-m3-error`, `role="alert"` | Trên `m3-surface` |
| Câu giới thiệu, câu báo chuyển chế độ, tiêu đề kết quả | `text-sm text-m3-on-surface` (+ icon `Info` cho câu báo) | Trên nền trang `surface-container` → không dùng `on-surface-variant` |
| Khối AI | `rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-4` | Viền bắt buộc (UI_GUIDE `:255-256`) |
| Chữ trong khối AI | **chỉ** `text-m3-on-surface` (nhãn "Do AI tạo", tiêu đề, dòng mô tả, câu nhân thân, câu nhắc) | `on-surface-variant` không đạt trên nền này (UI_GUIDE `:257-258`) |
| Chip điều kiện (assist chip trung tính) | `inline-flex min-h-8 items-center gap-1 rounded-m3-sm border border-m3-outline bg-m3-surface pl-3 text-sm text-m3-on-surface`; nút xoá trong chip 32×32 (vùng chạm cả chip ≥ 40px chiều ngang) | **Một** kiểu cho mọi loại điều kiện; không màu theo loại hay theo "độ khớp" |
| Nút "Sửa điều kiện bằng bộ lọc" | `Button variant="outline" size="sm"` + `SlidersHorizontal` | Trong khối AI |
| Câu ngăn cách | `col-span-full border-t border-m3-outline-variant pt-3 text-sm text-m3-on-surface` | Không màu cảnh báo — chỉ là thông tin (UI_GUIDE `:270`) |
| Dòng "Khớp điều kiện" trên thẻ | như U15 (`JobCard.tsx:124-131`), chỉ đổi tiền tố | Trung tính, không %, không dấu ✓ |
| Lỗi tìm | icon `AlertCircle text-m3-error` + chữ `text-sm text-m3-on-surface`, `role="alert"`; nút "Thử lại" `variant="outline"` | Trên nền trang |

## 6. Trạng thái hiển thị

| Trạng thái | Hiển thị |
|---|---|
| Chế độ từ khoá | Như hiện tại + bộ chọn chế độ |
| Chế độ mô tả, chưa tìm | Ô nhập trống (hoặc câu đã gõ trước đó trong phiên trang), `nlIntro`; không danh sách, không gọi API |
| Kiểm phía client lỗi | Bấm "Tìm"/Enter: câu `nlEmpty`/`nlTooShort`/`nlTooLong` dưới ô, focus vào ô, **không** gọi API. Nút "Tìm" **không** khoá vì lý do này (chỉ khoá khi đang chờ) |
| AI đang xử lý | Nút "Đang tìm…" khoá (`aria-disabled`), `LinearProgress` nhãn `nlProgress`, ô vẫn gõ được; kết quả cũ ẩn; khối kết quả `JobCardSkeleton` × 4 |
| Có kết quả, `ranking = DESCRIPTION` | Khối AI (4a) + danh sách; câu ngăn cách nếu có việc `rankedByDescription = false`; focus chuyển tới tiêu đề kết quả (`tabIndex={-1}`); `role="status"` đọc `nlResultCount` |
| Có kết quả, `ranking = NEWEST` | Khối AI: thay dòng "Xếp theo mô tả" bằng `nlNoDescription`; danh sách theo thời gian đăng |
| Không có điều kiện nào | Khối AI: `nlNoConditions` thay cho hàng chip; nút "Sửa điều kiện bằng bộ lọc" vẫn có (mở bộ lọc trống) |
| Có thông tin nhân thân | Câu `nlPersonalInfoIgnored` (icon `Info`) trong khối AI |
| Rỗng (`items = []`) | Khối AI vẫn hiện + `nlEmptyResults` + nút "Sửa điều kiện bằng bộ lọc" (cùng hành vi nút trong khối) |
| Thiếu dữ liệu (việc chưa có embedding) | Câu ngăn cách `nlUnrankedDivider` (mục 4a) — không ẩn các việc này |
| Lỗi 400 / 429 / 502 / 503 / 504 | Câu `message` của backend nếu body là `{error, message}`, ngược lại `nlFailedFallback`; nút "Thử lại" (gửi đúng câu lúc gửi); 400 kiểm đầu vào hiện dưới ô thay vì ở khối lỗi; không hiện mã lỗi thô |
| Không có quyền (401/403) | Không xảy ra qua giao diện (route chỉ cho ứng viên); nếu có → `nlFailedFallback` |
| Sau khi sửa/xoá thẻ | Chế độ từ khoá, URL bộ lọc mới, danh sách U07 tải theo URL; câu `switchedNotice` (`role="status"`, icon `Info`) ngay dưới thanh tìm kiếm; câu mất khi chọn lại "Tìm bằng mô tả" hoặc tải lại trang (state, không URL) |
| Chuyển chế độ thủ công | Mô tả → từ khoá: kết quả mô tả bị bỏ (không giữ khi quay lại), URL bộ lọc giữ như trước; từ khoá → mô tả: ô giữ câu đã gõ lần trước trong phiên trang, không tự tìm |
| Tải lại trang / rời trang | Về chế độ từ khoá; mất câu và kết quả; không hỏi xác nhận (không có dữ liệu cần lưu) |

## 7. Nội dung chữ (tiếng Việt có dấu)

Tất cả ở `jobSearchLabels.ts` (trừ chuỗi đã có ở `jobLabels.ts`). `{n}`, `{min}`, `{max}` là số.

| Khoá | Chuỗi |
|---|---|
| `modeLegend` (sr-only) | Cách tìm việc |
| `modeKeyword` / `modeKeywordShort` (< 600px) | Tìm theo từ khoá / Từ khoá |
| `modeDescription` / `modeDescriptionShort` (< 600px) | Tìm bằng mô tả / Mô tả |
| `nlInputLabel` | Mô tả công việc bạn muốn tìm |
| `nlPlaceholder` | Mô tả công việc bạn muốn tìm… |
| `nlHint` | Ví dụ: content marketing lương trên 15 triệu ở Hà Nội, không yêu cầu kinh nghiệm. |
| `nlCounter` | {n}/300 |
| `nlQuotaNote` | Mỗi lần tìm dùng một lượt AI. |
| `nlIntro` | Mô tả bằng lời công việc bạn muốn. Hệ thống tách điều kiện lọc (khu vực, ngành nghề, lương, hình thức làm việc) và xếp việc làm theo phần mô tả còn lại. |
| `nlSubmit` | Tìm |
| `nlSubmitting` | Đang tìm… |
| `nlProgress` | Đang phân tích mô tả và tìm việc làm |
| `nlEmpty` | Vui lòng nhập mô tả công việc bạn muốn tìm. |
| `nlTooShort` | Mô tả cần ít nhất 2 ký tự. |
| `nlTooLong` | Mô tả tìm việc tối đa 300 ký tự. |
| `aiBadge` | Do AI tạo |
| `interpretationTitle` | Hệ thống hiểu yêu cầu của bạn |
| `salaryFrom` / `salaryTo` / `salaryRange` | Lương từ {min} triệu / Lương đến {max} triệu / Lương {min}–{max} triệu |
| `descriptionLine` | Xếp theo mô tả: “{mô tả}” |
| `nlNoDescription` | Không còn phần mô tả để xếp theo độ liên quan; việc làm được sắp theo thời gian đăng mới nhất. |
| `nlNoConditions` | Không tách được điều kiện lọc nào từ mô tả. |
| `nlPersonalInfoIgnored` | Đã bỏ qua thông tin nhân thân trong mô tả. Hệ thống không lọc hay xếp việc làm theo các thông tin này. |
| `interpretationCaution` | Kiểm tra lại các điều kiện; bấm vào một điều kiện để sửa bằng bộ lọc. |
| `editConditions` | Sửa điều kiện bằng bộ lọc |
| `chipEditAria` (aria-label) | Sửa điều kiện: {nhãn} |
| `chipRemoveAria` (aria-label) | Xoá điều kiện: {nhãn} |
| `resultsTitle` | Kết quả tìm theo mô tả |
| `nlResultCount` | Tìm thấy {n} việc làm (tối đa 50) |
| `matchedPrefix` | Khớp điều kiện: |
| `nlUnrankedDivider` | Các việc làm dưới đây chưa được hệ thống phân tích nội dung nên chưa xếp theo mô tả; sắp theo thời gian đăng mới nhất. |
| `nlEmptyResults` | Không tìm thấy việc làm nào khớp các điều kiện đã hiểu. |
| `nlFailedFallback` | Chưa tìm được việc làm theo mô tả, vui lòng thử lại. |
| `retry` | Thử lại |
| `switchedNotice` | Đã chuyển sang lọc thường, bỏ thứ tự theo mô tả. |
| Câu lỗi 400/429/5xx | Đúng `message` backend (REQUIREMENT 4.4) — frontend không tự viết lại |
| Nhãn chip khu vực/ngành | Nhãn danh mục C05 (`useCatalogsQuery`); hình thức: `WORK_MODE_LABELS` có sẵn |

Kiểm phía client (trước khi gọi API): độ dài sau `normalizeQuery` 2–300 với **đúng** câu backend (`nlEmpty`/`nlTooShort`/
`nlTooLong`). Ký tự không hỗ trợ chỉ kiểm ở backend (câu 400 hiện dưới ô).

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Cỡ | Khác biệt |
|---|---|
| Compact (< 600px) | Segmented `w-full`, hai nửa bằng nhau, nhãn ngắn (`modeKeywordShort`/`modeDescriptionShort`); ô nhập và nút "Tìm" xếp dọc, `w-full`, `h-12`; chip xuống dòng; nút "Sửa điều kiện bằng bộ lọc" `w-full`; lưới 1 cột; chuyển sang từ khoá thì `JobFilterBar` dùng sheet "Lọc" có sẵn |
| Medium (600–1023px) | Ô nhập và nút một hàng; lưới 1 cột (như `JobList`) |
| Expanded (≥ 1024px) | Như 4a; lưới 2 cột (`lg:grid-cols-2`, như `JobList`) |

Không cuộn ngang toàn trang ở 375px; vùng chạm ≥ 40px (≥ 48px dưới `sm` như `JobFilterBar`).

## 9. Khả năng tiếp cận

- Bộ chọn chế độ: `fieldset` + `legend` "Cách tìm việc" (sr-only), radio thuần (phím mũi tên đổi lựa chọn); lựa chọn đang chọn
  có icon + chữ, không chỉ màu.
- Ô mô tả có `Label` thật (`nlInputLabel`, có thể sr-only khi đã có placeholder — vẫn phải có); lỗi: `aria-invalid` +
  `aria-describedby` trỏ tới câu lỗi và bộ đếm; Enter trong ô = bấm "Tìm".
- `LinearProgress` có `role="progressbar"` + nhãn (có sẵn); nút đang chờ dùng `aria-disabled`.
- Kết quả mới: vùng `role="status"` đọc `nlResultCount`; focus chuyển tới tiêu đề kết quả. Lỗi: `role="alert"`.
- Khối AI: nhãn "Do AI tạo" là chữ thật; chip là `button` có `aria-label` (`chipEditAria`/`chipRemoveAria`); sau khi xoá/sửa chip
  focus chuyển tới câu `switchedNotice` (`tabIndex={-1}`).
- Câu ngăn cách là chữ thật trong luồng đọc (không chỉ đường kẻ).
- Icon trang trí `aria-hidden="true"`. Không truyền đạt gì chỉ bằng màu.

## 10. Không được làm

- Hiện %, điểm, độ tương đồng, thanh mức độ, nhãn "phù hợp"/"rất phù hợp", màu theo mức khớp, dấu ✓/✗ (UI_GUIDE `:263-264`).
- Hiện cách hiểu của AI ngoài khối AI, thiếu nhãn "Do AI tạo", dùng `on-surface-variant` trong khối AI.
- Đưa câu mô tả hay chế độ lên URL, `localStorage`, `sessionStorage`; tự tìm khi gõ, khi chuyển chế độ hay khi tải trang (Q-T6).
- Tự gọi lại S1 sau khi sửa/xoá thẻ; lọc lại 50 kết quả ở trình duyệt thay cho chuyển sang U07 (Q-T5).
- Ẩn các việc chưa được phân tích nội dung, hoặc trộn chúng vào giữa danh sách không có câu ngăn cách (UI_GUIDE `:270`).
- Hiện thẻ điều kiện cho thông tin nhân thân; hiện lại đoạn chữ nhân thân trong khối AI.
- Bật chế độ mô tả ở `/` hay cho khách chưa đăng nhập.
- Gửi lại tự động khi lỗi; hiện mã lỗi thô thay cho câu tiếng Việt.
- Sửa `useJobFilters.ts`, `JobFilterBar.tsx`, `RecommendedJobs.tsx`; thêm component vào `components/ui`; thêm thư viện.
- Thanh tìm kiếm `fixed`/`sticky`.
