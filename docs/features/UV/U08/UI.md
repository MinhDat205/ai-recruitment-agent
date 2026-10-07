# FR-U08 — Giao diện

Trạng thái: ĐÃ DUYỆT 07/10/2026.

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 2 (điều hướng), 3 (component), 4 (ràng buộc), 5 (rỗng/
tải/lỗi), 6 (tương phản). Mã quy tắc `R-…` là của `REQUIREMENT.md` cùng thư mục. File này không chép lại quy
tắc chung, chỉ chốt cách áp cho màn hình này.

## 1. Trạng thái

ĐÃ DUYỆT 07/10/2026.

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| ★`/candidate/applications/:id` | Chi tiết đơn ứng tuyển — tab "Thông tin đơn", "Lịch sử"; nút Rút đơn | ★Mới | FR-U08; FR-U03, FR-U06 |
| `/candidate/applications` | Bỏ hộp thoại lịch sử, hộp thoại giấy mời, nút Rút đơn; mỗi dòng một liên kết "Xem chi tiết" | Hiện có | FR-U03; FR-U08 (R-P3, R-E1) |
| `/jobs/:id` | Không đổi giao diện; chỉ dùng hàm `formatSalary` chung | Hiện có | FR-U08 (R-C4) |
| `/`, `/candidate` (thẻ việc làm `JobCard`) | Không đổi giao diện; chỉ dùng hàm `formatSalary` chung | Hiện có | FR-U08 (R-C4) |
| `/hr/jobs` | Không đổi giao diện (cột lương vẫn `—` khi không có lương); chỉ dùng hàm `formatSalary` chung | Hiện có | FR-U08 (R-C4) |

Query `?tab=info|history` (mặc định `info`, giá trị lạ → `info`), cùng cách đọc của trang hồ sơ đơn phía HR
(`HrApplicationDetailPage.tsx:16-21`). Đổi tab cập nhật URL bằng `replace`. Dòng UI_GUIDE mục 7 của hai route
đầu được sửa trong commit đặc tả sau khi duyệt (REQUIREMENT mục 10, D8–D9).

## 3. Điểm vào

- `/candidate/applications`: liên kết "Xem chi tiết" cuối mỗi dòng (R-E1).
- Chuông thông báo: thông báo đổi trạng thái **mới** mở `/candidate/applications/{id}` (R-N1); thông báo cũ vẫn
  mở `/candidate/applications` (R-N2). Trang "Xem tất cả" chưa điều hướng (nợ có sẵn, ngoài phạm vi).
- Mở thẳng URL (dán link, F5): trang tự tải, tab theo `?tab=`.
- Rời trang: liên kết "← Đơn ứng tuyển của tôi" về `/candidate/applications` (R-E2); nút quay lại của trình
  duyệt giữ nguyên hành vi. Mục "Đơn ứng tuyển" trên thanh điều hướng vẫn sáng (`CandidateLayout.tsx:35-36`).

## 4. Bố cục (khung ASCII)

Khung trang giống `/candidate/applications` (`CandidateApplicationsPage.tsx:145`): `mx-auto max-w-[1200px] px-4
py-8 md:px-6` trên nền `m3-surface-container`; toàn bộ nội dung trong **một** `Card` trắng (`bg-m3-surface`)
`mx-auto max-w-5xl` để dùng được `m3-primary`/`m3-on-surface-variant` (UI_GUIDE mục 6).

### 4a. Desktop (≥ `lg`) — tab "Thông tin đơn", đơn đã mời phỏng vấn (R-T6)

```
┌──────────────────────────────────────────────────────────────────────────┐
│ ← Đơn ứng tuyển của tôi                                                  │
│                                                                          │
│ Nhân viên Hành chính văn phòng                          [Rút đơn]        │
│ Công ty TNHH Thử Nghiệm Ánh Dương                                        │
│ [Đã mời phỏng vấn]  Nộp ngày 00:36 06/10/2026                            │
│                                                                          │
│ ┌──────────────┬──────────┐                                              │
│ │ Thông tin đơn│ Lịch sử  │                                              │
│ └──────────────┴──────────┘                                              │
├──────────────────────────────────────────────────────────────────────────┤
│ Giấy mời phỏng vấn                                                       │
│ [CalendarClock] 09:00 14/10/2026                                         │
│ Địa điểm: Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng                   │
│ Thư mời phỏng vấn vị trí Nhân viên Hành chính văn phòng - Công ty …      │
│ Kính gửi Quốc Huy,                                                       │
│                                                                          │
│ Cảm ơn bạn đã ứng tuyển … (nội dung nguyên văn, giữ xuống dòng)          │
│ ──────────────────────────────────────────────────────────────────────── │
│ Tin tuyển dụng                                    [Đang mở]              │
│ Ngành nghề   Hành chính - Văn phòng     Hình thức  Toàn thời gian        │
│ Khu vực      Hà Nội                     Làm việc   Kết hợp               │
│ Mức lương    Không công bố              Hạn nộp    21/11/2026            │
│ Thông tin tin tuyển dụng là bản hiện tại, có thể đã thay đổi sau khi     │
│ bạn nộp đơn.                                                             │
│ Xem tin tuyển dụng →                                                     │
│ ──────────────────────────────────────────────────────────────────────── │
│ Hồ sơ đã nộp                                                             │
│ [FileText] cv-quoc-huy.docx   [Đã trích xuất]        [↓ Tải CV gốc]      │
│                                                                          │
│ Thư giới thiệu                       (chỉ khi có — CoverLetterBlock)     │
│ …                                                                        │
│ ──────────────────────────────────────────────────────────────────────── │
│ CV đã trích xuất                                                         │
│ (Tổng quan nghề nghiệp · Liên hệ · Học vấn · Kinh nghiệm · Kỹ năng ·     │
│  Chứng chỉ · Dự án — ResumeParsedDataView)                               │
└──────────────────────────────────────────────────────────────────────────┘
```

Thứ tự bốn khối trong tab là cố định: **Giấy mời phỏng vấn** (chỉ khi có, R-T3…R-T6) → **Tin tuyển dụng** →
**Hồ sơ đã nộp** → **CV đã trích xuất**. Giấy mời đặt đầu vì là thông tin ứng viên cần hành động (giờ, địa điểm).
Khối dài nhất (CV đã trích xuất) đặt cuối.

Khối "Tin tuyển dụng" khi tin không còn mở (R-D5):

```
│ Tin tuyển dụng                                    [Đã đóng]              │
│ Ngành nghề   Nhân sự                    Hình thức  Toàn thời gian        │
│ …                                                                        │
│ Thông tin tin tuyển dụng là bản hiện tại, có thể đã thay đổi sau khi     │
│ bạn nộp đơn.                                                             │
│ (không có liên kết "Xem tin tuyển dụng")                                 │
```

Khối "CV đã trích xuất" ở các trạng thái khác:

```
R-T9   CV đang chờ trích xuất. Nội dung sẽ hiện khi hệ thống trích xuất xong.
R-T10  Trích xuất CV thất bại.
       EXTRACT_EMPTY: …(câu mô tả đã chuẩn hoá)…
       Bạn có thể tải lên CV khác hoặc thử lại ở trang Hồ sơ và CV.
```

("Hồ sơ và CV" là liên kết chữ tới `/candidate/profile`, không phải nút gọi AI — R-P4.)

### 4b. Desktop — tab "Lịch sử"

```
├──────────────────────────────────────────────────────────────────────────┤
│ ┃ Nộp đơn → [Chờ duyệt]                                                  │
│ ┃ 00:36 06/10/2026                                                       │
│ ┃                                                                        │
│ ┃ [Chờ duyệt] → [Đã mời phỏng vấn]                                       │
│ ┃ 00:40 06/10/2026                                                       │
```

`ApplicationHistoryTimeline` nguyên trạng (R-C5).

### 4c. Đầu trang ở trạng thái cuối (R-T16)

```
│ Chuyên viên Tuyển dụng                                                   │
│ Công ty TNHH Thử Nghiệm Ánh Dương                                        │
│ [Trúng tuyển]  Nộp ngày 10:00 15/07/2026                                 │
```

Không nút, không dòng chữ thay thế.

### 4d. Điện thoại (375px) — tab "Thông tin đơn"

Chiều rộng nội dung: 375 − 2×16 (`px-4` của khung trang) − 2×16 (padding `Card`, `--card-spacing:--spacing(4)`,
`components/ui/card.tsx:15`) ≈ 311px. `CandidateLayout` không có rail bên (menu điện thoại là sheet,
`CandidateLayout.tsx:100`).

```
┌──────────────────────────────┐
│ ← Đơn ứng tuyển của tôi      │
│ Nhân viên Hành chính văn     │
│ phòng                        │
│ Công ty TNHH Thử Nghiệm Ánh  │
│ Dương                        │
│ [Đã mời phỏng vấn]           │
│ Nộp ngày 00:36 06/10/2026    │
│ ┌──────────────────────────┐ │
│ │         Rút đơn          │ │
│ └──────────────────────────┘ │
│ ┌──────────────┬─────────┐   │
│ │ Thông tin đơn│ Lịch sử │   │
│ └──────────────┴─────────┘   │
├──────────────────────────────┤
│ Giấy mời phỏng vấn           │
│ 09:00 14/10/2026             │
│ Địa điểm: Tầng 3, 45 Bạch    │
│ Đằng, Quận Hải Châu, Đà Nẵng │
│ (tiêu đề, nội dung xuống     │
│  dòng tự do)                 │
│ ──────────────────────────── │
│ Tin tuyển dụng               │
│ [Đang mở]                    │
│ Ngành nghề                   │
│ Hành chính - Văn phòng       │
│ Khu vực                      │
│ Hà Nội                       │
│ … (nhãn trên, giá trị dưới)  │
│ Thông tin tin tuyển dụng là  │
│ bản hiện tại, …              │
│ Xem tin tuyển dụng →         │
│ ──────────────────────────── │
│ Hồ sơ đã nộp                 │
│ cv-quoc-huy.docx             │
│ [Đã trích xuất]              │
│ ┌──────────────────────────┐ │
│ │      ↓ Tải CV gốc        │ │
│ └──────────────────────────┘ │
│ ──────────────────────────── │
│ CV đã trích xuất             │
│ …                            │
└──────────────────────────────┘
```

Nút "Rút đơn" ở điện thoại nằm **trong luồng trang** ngay dưới ngày nộp, `w-full`. **Không** `fixed`/`sticky`.

## 5. Component

### 5a. File mới (chốt tên — `REQUIREMENT.md` mục 7.3 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/pages/CandidateApplicationDetailPage.tsx` | Trang; đọc `:id`, `?tab=`; gọi E1; render đầu trang + `Tabs`; R-T1/R-T2. Tab "Lịch sử" là hàm cục bộ `HistoryTab` trong chính file này (gọi `useApplicationHistoryQuery` + `ApplicationHistoryTimeline`), không có file riêng |
| `frontend/src/features/candidateApplicationDetail/api.ts` | Gọi E1 |
| `frontend/src/features/candidateApplicationDetail/queries.ts` | Hook E1 (khoá theo `applicationId`, không thử lại với 4xx); hàm trả danh sách khoá cần làm mới sau rút đơn |
| `frontend/src/features/candidateApplicationDetail/types.ts` | Kiểu `ApplicationCandidateDetail`, `JobAvailability` |
| `frontend/src/features/candidateApplicationDetail/jobAvailabilityLabels.ts` | Nhãn tiếng Việt của 5 giá trị `JobAvailability` (R-D4) |
| `frontend/src/features/candidateApplicationDetail/CandidateApplicationHeader.tsx` | Liên kết quay lại, tên tin (h1), công ty, badge, ngày nộp, nút Rút đơn + `WithdrawApplicationDialog` |
| `frontend/src/features/candidateApplicationDetail/ApplicationInfoTab.tsx` | Bốn khối của tab "Thông tin đơn" theo thứ tự mục 4a |
| `frontend/src/features/candidateApplicationDetail/InvitationSection.tsx` | R-T3…R-T7: quyết định có gọi API, ẩn khối khi 404, dùng `InterviewInvitationDetails` |
| `frontend/src/features/candidateApplicationDetail/JobSummarySection.tsx` | R-T8, R-D3…R-D6, R-G4 |
| `frontend/src/features/candidateApplicationDetail/SubmittedResumeSection.tsx` | Tệp CV + badge + "Tải CV gốc" (R-T13) + `CoverLetterBlock` (R-T12) |
| `frontend/src/features/candidateApplicationDetail/ParsedResumeSection.tsx` | R-T9…R-T11 |
| `frontend/src/features/interviewinvitation/InterviewInvitationDetails.tsx` | Phần thân giấy mời, tách từ `CandidateApplicationsPage.tsx:51-93` (R-C2) |
| `frontend/src/features/applications/WithdrawApplicationDialog.tsx` | Hộp xác nhận rút đơn, tách từ `CandidateApplicationsPage.tsx:221-244` (R-C3) |
| `frontend/src/features/jobs/formatSalary.ts` | Hàm `formatSalary` **duy nhất**, thân chuyển từ `PublicJobDetailPage.tsx:12-27`; thay cả ba bản cục bộ ở `JobCard.tsx:8-19`, `PublicJobDetailPage.tsx:12-27`, `HrJobListPage.tsx:20-30` (R-C4, REQUIREMENT mục 0.h) |

Thư mục `candidateApplicationDetail` theo kiểu camelCase của thư mục tính năng mới nhất (`applicationDetail`,
`candidateProfile`); tách khỏi `applicationDetail` (của HR) để lệnh tìm ở REQUIREMENT 7.3 chặn được việc import
nhầm phía HR.

### 5b. Dùng lại có sẵn (không viết lại)

| Có sẵn | Cách dùng ở FR-U08 |
|---|---|
| `Tabs`, `TabsList`, `TabsTrigger`, `TabsContent` | Primary tabs, **cùng cấu hình** trang hồ sơ đơn HR (`HrApplicationDetailPage.tsx:134`: `TabsList` `w-fit max-w-full justify-start overflow-x-auto`) |
| `Card`, `Button`, `Dialog` | Như trang danh sách |
| `ApplicationStatusBadge` | Badge đầu trang (bảng trung tính UI_GUIDE mục 3) |
| `ApplicationHistoryTimeline` + `useApplicationHistoryQuery` | Tab "Lịch sử" (cùng cách `CandidateApplicationsPage.tsx:117-120`) |
| `useInterviewInvitationQuery` (`features/interviewinvitation/queries.ts`) | `InvitationSection`, `enabled` theo R-T3/R-T4 |
| `useWithdrawApplicationMutation` | Mở rộng `onSuccess`/`onError` làm mới E1, lịch sử, danh sách (R-W3, R-W4) — không tạo mutation thứ hai |
| `ParseStatusBadge` | Cạnh tên tệp |
| `downloadResumeRequest` (`features/resumes/api.ts:40`) | "Tải CV gốc" với `resume.id` của E1; kích hoạt tải bằng blob như `ResumeList.tsx:123-140` |
| `useResumeParsedDataQuery(resumeId, enabled)` (`features/resumes/queries.ts:74`) | `enabled` = `parseStatus === 'DONE'`; trả `null` khi 404 (`api.ts:48-58`) → câu có sẵn "Chưa có dữ liệu trích xuất cho CV này." (`ResumeParsedDataDialog.tsx:26`) |
| `ResumeParsedDataView`, `ResumeParsedDataSkeleton` | Khối CV đã trích xuất |
| `CoverLetterBlock` (`features/applicationDetail/CoverLetterBlock.tsx`) | Import nguyên trạng, không di chuyển file (file không import gì của HR) |
| `jobCategoryText`, `jobLocationText` (`features/jobs/catalogDisplay.ts`) | Ngành nghề, khu vực (R-D6) |
| `EMPLOYMENT_TYPE_LABELS`, `WORK_MODE_LABELS` (`features/jobs/jobLabels.ts:29-43`) | Hình thức, chế độ làm việc |
| `formatDeadline`, `formatDateTimeVi` (`lib/date.ts`) | Hạn nộp, ngày nộp |
| `extractErrorMessage` (`lib/httpError`) | Câu lỗi trong hộp rút đơn |

### 5c. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Nền trang | `bg-m3-surface-container` | — | Như trang danh sách |
| Thẻ chứa trang | `bg-m3-surface` | — | |
| Liên kết "← Đơn ứng tuyển của tôi", "Xem tin tuyển dụng →", "Hồ sơ và CV" | (thẻ trắng) | `text-m3-primary` | 4.64:1 chỉ trên `m3-surface` |
| Tên tin (h1) | — | `text-m3-on-surface`, `text-m3-title-lg` | |
| Tên công ty, ngày nộp, nhãn trái của bảng tóm tắt tin, chú thích "bản hiện tại" | — | `text-m3-on-surface-variant` | Chỉ trên `m3-surface` (4.83:1) |
| Giá trị của bảng tóm tắt tin, nội dung giấy mời, câu trạng thái R-T9/R-T10, `parseError` | — | `text-m3-on-surface` | Không dùng `m3-error` cho R-T10 (trạng thái xử lý, không phải lỗi thao tác) |
| Nhãn tình trạng tin (mọi giá trị) | `bg-m3-surface` | `border border-m3-outline text-m3-on-surface` | **Một** kiểu cho cả 5 giá trị (R-G1); không xanh/đỏ |
| Icon giờ phỏng vấn `CalendarClock` | — | `text-m3-primary` | Như hiện có |
| Phân khối giữa các khối của tab | — | `border-m3-outline-variant` | Chỉ để phân khối |
| Nút "Rút đơn", "Tải CV gốc", "Thử lại" | — | `variant="outline"` | Rút đơn không đỏ, không nổi hơn nút khác |
| Lỗi tải trang / lỗi tải CV gốc / lỗi khối giấy mời | — | Icon `AlertCircle` `text-m3-error` + chữ `text-m3-on-surface` | Như trang hồ sơ đơn HR |
| Câu lỗi trong hộp rút đơn | — | `text-m3-error` (trên `Dialog` nền trắng) | Giữ nguyên như hiện có (R-C3) |

## 6. Trạng thái hiển thị

Theo field backend trả về (REQUIREMENT mục 3.4); dưới đây là cách hiện.

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Đang tải E1 | Cả thẻ | Skeleton xám: 1 dòng liên kết, 1 dòng tên tin, 1 dòng badge, 2 ô tab; không spinner toàn trang |
| **R-T2** lỗi mạng/5xx ở E1 | Cả thẻ | "Không tải được đơn ứng tuyển." + nút "Thử lại" |
| **R-T1** 400/403/404 ở E1 | Cả thẻ | "Không tìm thấy đơn ứng tuyển." + liên kết "Về danh sách đơn ứng tuyển"; cùng câu cho cả ba mã; không tab, không gọi API khác |
| **R-T3** đơn `PENDING`/`WITHDRAWN` | Khối giấy mời | Không render khối, không gọi API |
| Đang tải giấy mời | Khối giấy mời | Tiêu đề "Giấy mời phỏng vấn" + câu có sẵn "Đang tải giấy mời..." |
| **R-T5** 404 giấy mời | Khối giấy mời | Không render gì (cả tiêu đề) |
| **R-T6** có giấy mời | Khối giấy mời | `InterviewInvitationDetails` |
| **R-T7** lỗi khác | Khối giấy mời | Câu có sẵn "Không tải được giấy mời, vui lòng thử lại." + nút "Thử lại" |
| **R-T8** tin `OPEN` | Khối tin | Nhãn "Đang mở" + liên kết "Xem tin tuyển dụng →" (`/jobs/{jobId}`) |
| **R-D5** tin khác `OPEN` | Khối tin | Nhãn tương ứng (mục 7), không liên kết |
| Thiếu dữ liệu tóm tắt | Khối tin | Ô giá trị "Chưa có dữ liệu"; lương null → "Không công bố" (R-G4) |
| Hạn nộp null | Khối tin | "Không giới hạn" qua `formatDeadline` — không phải thiếu dữ liệu (R-G4) |
| **R-T9** CV `PENDING`/`PROCESSING` | Khối CV đã trích xuất | Câu chờ (mục 7) |
| **R-T10** CV `FAILED` | Khối CV đã trích xuất | "Trích xuất CV thất bại." + `parseError` nguyên văn + câu hướng dẫn có liên kết "Hồ sơ và CV"; **không** nút |
| **R-T11** CV `DONE` đang tải `/parsed` | Khối CV đã trích xuất | `ResumeParsedDataSkeleton` |
| **R-T11** `/parsed` trả dữ liệu | Khối CV đã trích xuất | `ResumeParsedDataView`; mục nào trống hiện câu trống có sẵn của view (vd "CV không có mục học vấn", `ResumeParsedDataView.tsx:47,63`) — không viết câu mới |
| **R-T11** `/parsed` trả `null` | Khối CV đã trích xuất | Câu có sẵn nguyên văn "Chưa có dữ liệu trích xuất cho CV này." (`ResumeParsedDataDialog.tsx:26`) |
| `/parsed` lỗi khác | Khối CV đã trích xuất | Icon `AlertCircle` + "Không tải được dữ liệu, vui lòng thử lại." + nút "Thử lại" |
| **R-T12** có thư | Khối Hồ sơ đã nộp | `CoverLetterBlock`; không có → không hiện tiêu đề |
| Đang tải CV gốc | Nút | Nút khoá, nhãn "Đang tải…" |
| **R-T13** tải CV gốc lỗi | Dưới nút | Icon `AlertCircle` + "Tải CV gốc thất bại, vui lòng thử lại." |
| **R-T14** | Tab Lịch sử | Đang tải / lỗi / "Chưa có lịch sử chuyển trạng thái." / dòng thời gian — theo `ApplicationHistoryTimeline` |
| **R-T15** | Đầu trang | Nút "Rút đơn" |
| **R-T16** | Đầu trang | Không nút, không dòng chữ thay thế |
| Đang rút đơn | Hộp thoại | Hành vi có sẵn: nút khoá, nhãn "Đang rút đơn..." |
| Rút đơn lỗi (409…) | Hộp thoại | Thông điệp backend, fallback "Rút đơn thất bại, vui lòng thử lại." (có sẵn); E1 được làm mới (R-W4) |
| Rút đơn thành công | Đầu trang | Hộp đóng; badge "Đã rút đơn"; nút biến mất; focus về badge (mục 9) |

Không có trạng thái "AI đang xử lý": trang không gọi AI.

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Liên kết quay lại | "← Đơn ứng tuyển của tôi" |
| Ngày nộp | "Nộp ngày {HH:mm dd/MM/yyyy}" (`formatDateTimeVi` — `toLocaleString('vi-VN')` đặt giờ trước ngày, giống cột "Ngày nộp" của trang danh sách). Thời gian giấy mời và mốc lịch sử dùng cùng tuỳ chọn định dạng nên cũng là "HH:mm dd/MM/yyyy" |
| Tab | "Thông tin đơn" · "Lịch sử" |
| Nút | "Rút đơn" |
| Hộp xác nhận rút đơn | Nguyên văn hiện có: tiêu đề "Rút đơn ứng tuyển?"; nội dung "Hành động này không thể hoàn tác. Sau khi rút, bạn sẽ không thể nộp lại đơn cho vị trí {jobTitle} trong đợt tuyển hiện tại."; nút "Huỷ" · "Xác nhận rút đơn" · "Đang rút đơn..."; lỗi fallback "Rút đơn thất bại, vui lòng thử lại." |
| Khối 1 | "Giấy mời phỏng vấn" |
| Nội dung giấy mời | Nguyên văn hiện có: "Địa điểm: " · "Đang tải giấy mời..." · "Không tải được giấy mời, vui lòng thử lại." |
| Khối 2 | "Tin tuyển dụng" |
| Nhãn tóm tắt | "Ngành nghề" · "Khu vực" · "Hình thức" · "Làm việc" · "Mức lương" · "Hạn nộp" |
| Lương null | "Không công bố" |
| Hạn nộp null | "Không giới hạn" (có sẵn trong `formatDeadline`, `lib/date.ts`) |
| Ô thiếu (ngành nghề, khu vực, hình thức, làm việc) | "Chưa có dữ liệu" |
| Tình trạng tin (đúng 5 giá trị, R-D4) | `OPEN` "Đang mở" · `EXPIRED` "Đã hết hạn nộp" · `PAUSED` "Tạm dừng" · `CLOSED` "Đã đóng" · `UNAVAILABLE` "Không còn đăng" (gộp tin đã xoá và tin đưa về nháp — ứng viên không cần biết HR đã xoá tin hay đưa về nháp) |
| Chú thích | "Thông tin tin tuyển dụng là bản hiện tại, có thể đã thay đổi sau khi bạn nộp đơn." |
| Liên kết tin | "Xem tin tuyển dụng →" |
| Khối 3 | "Hồ sơ đã nộp" |
| Nút tải | "Tải CV gốc" · khi đang tải "Đang tải…" |
| Lỗi tải | "Tải CV gốc thất bại, vui lòng thử lại." |
| Thư giới thiệu | "Thư giới thiệu" · "Xem thêm" · "Thu gọn" (có sẵn trong `CoverLetterBlock`) |
| Khối 4 | "CV đã trích xuất" |
| R-T9 | "CV đang chờ trích xuất. Nội dung sẽ hiện khi hệ thống trích xuất xong." |
| R-T10 | "Trích xuất CV thất bại." · "Bạn có thể tải lên CV khác hoặc thử lại ở trang " + liên kết "Hồ sơ và CV" + "." |
| R-T11 null | "Chưa có dữ liệu trích xuất cho CV này." (có sẵn, `ResumeParsedDataDialog.tsx:26`) |
| R-T11 mục trống | Câu trống có sẵn của `ResumeParsedDataView` (không viết câu mới) |
| Lỗi tải `/parsed` | "Không tải được dữ liệu, vui lòng thử lại." · nút "Thử lại" |
| R-T1 | "Không tìm thấy đơn ứng tuyển." · liên kết "Về danh sách đơn ứng tuyển" |
| R-T2 | "Không tải được đơn ứng tuyển." · nút "Thử lại" |
| Liên kết ở danh sách | "Xem chi tiết" |

Chuỗi có sẵn của `ApplicationHistoryTimeline`, `CoverLetterBlock`, `ResumeParsedDataView`, `ParseStatusBadge`,
`ApplicationStatusBadge` giữ nguyên, không sửa.

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Đầu trang | Tabs | Khối "Tin tuyển dụng" | Khối "Hồ sơ đã nộp" |
|---|---|---|---|---|
| Expanded (≥ `lg`) | Tên tin + công ty + badge + ngày bên trái, nút "Rút đơn" bên phải cùng hàng (`flex justify-between`) | `TabsList` `w-fit` | Lưới 2 cặp nhãn–giá trị mỗi hàng (`grid-cols-[auto_1fr_auto_1fr]`) | Tên tệp, badge, nút cùng hàng |
| Medium (`sm`–`lg`) | Nút "Rút đơn" xuống hàng dưới ngày nộp | Như trên | Lưới 1 cặp mỗi hàng (`grid-cols-[auto_1fr]`) | Như expanded, nút xuống dòng nếu thiếu chỗ |
| Compact (< `sm`, 375px) | Xếp dọc; nút `w-full`, **trong luồng trang** | `TabsList` `max-w-full overflow-x-auto`; không bẻ nhãn tab | Nhãn trên, giá trị dưới | Tên tệp, badge, nút `w-full` xếp dọc |

- Không thanh dưới đáy cố định ở bất kỳ khổ nào.
- Không cuộn ngang toàn trang ở 375px; chỉ `TabsList` được cuộn ngang nếu cần.
- Tên tin, tên công ty, địa điểm giấy mời, nội dung giấy mời, thư giới thiệu, tên tệp tự ngắt từ dài (`break-words`).

## 9. Khả năng tiếp cận

- Một `h1` duy nhất là tên tin tuyển dụng; tiêu đề các khối là `h2`.
- Tabs: Radix lo `role="tablist"`/`tab`/`tabpanel` và phím mũi tên — không tự viết lại.
- Liên kết quay lại có `aria-label` "Quay lại danh sách đơn ứng tuyển của tôi".
- Nhãn tình trạng tin luôn là chữ; badge trạng thái đơn luôn có chữ (UI_GUIDE mục 6). Icon trang trí
  `aria-hidden="true"`.
- Sau khi rút đơn thành công, focus về badge trạng thái ở đầu trang (`tabIndex={-1}`), vì nút đã mở hộp thoại
  biến mất — cùng cách `ApplicationDetailHeader.tsx:17-24` phía HR.
- Câu lỗi tải CV gốc gắn vào nút bằng `aria-describedby`; khối giấy mời và khối CV đã trích xuất bọc
  `aria-live="polite"` khi chuyển từ đang tải sang có dữ liệu/lỗi.
- Tương phản: chỉ dùng các cặp đã đo ở UI_GUIDE mục 1c/6 và bảng 5c; không có cặp mới cần đo.

## 10. Không được làm

- Không hiện điểm số, thứ hạng, tiêu chí, giải thích AI, ghi chú nội bộ (R-G2).
- Không màu xanh/đỏ cho badge trạng thái đơn hay nhãn tình trạng tin; không icon ✓/✗ (R-G1, CLAUDE.md §8).
- Không nút "Thử lại"/"Trích xuất lại" CV (R-P4).
- Không câu "chưa có giấy mời" hay câu lỗi khi API giấy mời trả 404 (R-T5).
- Không hiện liên kết "Xem tin tuyển dụng" khi tình trạng khác `OPEN` (R-D5).
- Không dựng tab "Trao đổi", khối "Câu trả lời sàng lọc", nút "Rút đồng ý", vùng chọn khung giờ — kể cả rỗng
  hay khoá (R-P2).
- Không giữ hộp thoại lịch sử/giấy mời/nút Rút đơn ở trang danh sách (R-P3).
- Không render thư giới thiệu/giấy mời bằng HTML; không đặt trong khối "Do AI tạo" (R-G3).
- Không nút `fixed`/`sticky` ở đáy màn hình điện thoại.
- Không hiện khác nhau giữa 400, 403 và 404 của E1 (R-T1).
- Không sửa file trong `components/ui` (R-C1).
- Không đổi giao diện thẻ việc làm, `/jobs/:id`, `/hr/jobs` khi gộp `formatSalary`; không để lại bản cục bộ
  nào, không tạo bản mới cho trang này (R-C4).
- Không hiện nhãn khác nhau cho tin đã xoá và tin đưa về nháp — cả hai là "Không còn đăng" (R-D4).
- Không import gì từ `features/applicationDetail/{api,queries,types}`, `features/scoring` hay gọi `/api/hr/**`.
- Không sao chép logo, tên thương hiệu hay CSS của trang tuyển dụng nào (CLAUDE.md §8).
