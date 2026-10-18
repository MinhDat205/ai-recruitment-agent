# FR-U12 — Giao diện

> Trạng thái: ĐÃ DUYỆT (17/10/2026). Đi cùng `REQUIREMENT.md` (cùng trạng thái). Quy tắc nghiệp vụ, mã lỗi, DTO:
> `REQUIREMENT.md`; file này chỉ nói về màn hình.

Nguồn: `docs/UI_GUIDE.md` (token mục 1, component mục 3, ràng buộc mục 4 "Nội dung do AI tạo", trạng thái mục 5, tiếp cận mục
6, bản đồ mục 7). Một trang dùng cho cả tạo và sửa (`CvBuilderPage`, `mode = 'create' | 'edit'`).

## 1. Trạng thái

> Trạng thái: ĐÃ DUYỆT (17/10/2026).

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Thay đổi |
|---|---|---|
| ★`/candidate/resumes/new` | Tạo CV | Route mới, đã có trong bản đồ (`UI_GUIDE.md:342`); đặt trong nhóm `ProtectedRoute(CANDIDATE)` + `RequireCandidateProfileOnboarding` (`App.tsx:66-87`), **trước** route `:id/...` |
| ★`/candidate/resumes/:id/edit` | Sửa CV (lưu thành CV mới) | Route mới, đã có trong bản đồ; cùng nhóm |
| `/candidate/profile` | "Hồ sơ và CV" — màn hình có sẵn, mở rộng | Nút "Tạo CV" ở thẻ "CV của tôi"; nút "Sửa CV" ở dòng CV `fromCvBuilder`; câu rỗng mới |

Sửa `UI_GUIDE.md` mục 7 dòng `/candidate/profile` ở đợt cuối (REQUIREMENT mục 10, D4).

## 3. Điểm vào

- `/candidate/profile` → thẻ "CV của tôi" → nút **"Tạo CV"** (icon `FilePlus2`, `variant="default"`, `size="sm"`), đặt ở
  hàng đầu thẻ, trên vùng tải lên (`CandidateProfilePage.tsx:394-396`).
- Danh sách CV → nút **"Sửa CV"** (icon `PencilLine`, `variant="outline"`, `size="sm"`) chỉ khi `resume.fromCvBuilder`, đặt
  **trước** "Xem dữ liệu đã trích xuất" (`ResumeList.tsx:235`).
- Câu rỗng của danh sách đổi thành `emptyResumeList` (mục 7) — có nhắc tới "Tạo CV".
- Không có điểm vào nào khác (không từ trang nộp đơn, không từ bảng tin).

## 4. Bố cục (khung ASCII)

### 4a. Desktop (≥ `lg`) — `/candidate/resumes/new`

```
┌ CandidateLayout ───────────────────────────────────────────────────────────────────────┐
│ ← Về Hồ sơ và CV                                                                         │
│ Tạo CV                                                    (text-m3-headline-sm)          │
│ Điền thông tin theo từng mục. Hệ thống tạo file PDF từ đúng nội dung bạn nhập.          │
│                                                                                          │
│ ┌ Card max-w-3xl ── Điền sẵn ──────────────────────────────────────────────────────────┐ │
│ │ Điền sẵn từ CV đã trích xuất (không bắt buộc)                                         │ │
│ │ [ Chọn CV ▾ cv-mau-quoc-huy.docx ]   [ Điền sẵn ]                                     │ │
│ └───────────────────────────────────────────────────────────────────────────────────────┘ │
│ ┌ Card ── Mẫu trình bày ────────────────────────────────────────────────────────────────┐ │
│ │ (●) Cổ điển — Tên căn trái, tiêu đề mục có đường kẻ                                   │ │
│ │ ( ) Hiện đại — Tên căn giữa, tiêu đề mục màu xanh đậm                                 │ │
│ └───────────────────────────────────────────────────────────────────────────────────────┘ │
│ ┌ Card ── Thông tin liên hệ ───────────────────────────────────────────────────────────┐ │
│ │ Họ và tên *        [                                   ]                              │ │
│ │ Email              [                ]  Điện thoại [              ]                    │ │
│ │ Địa chỉ            [                                   ]                              │ │
│ │ LinkedIn           [                                   ]                              │ │
│ └───────────────────────────────────────────────────────────────────────────────────────┘ │
│ ┌ Card ── Thông tin chung ─────────────────────────────────────────────────────────────┐ │
│ │ Chức danh hiện tại [                 ]  Khu vực [              ]                      │ │
│ │ Ngành nghề         [ Chọn ngành ▾ ]  ⓘ Dùng để gợi ý việc làm, không in lên CV.       │ │
│ └───────────────────────────────────────────────────────────────────────────────────────┘ │
│ ┌ Card ── Kinh nghiệm làm việc ────────────────────────────────────────────────────────┐ │
│ │ ┌ Kinh nghiệm 1 ─────────────────────────────────────────── [↑] [↓] [Xoá] ──────────┐ │ │
│ │ │ Tên công ty * [            ]  Chức danh [            ]                             │ │ │
│ │ │ Bắt đầu [MM/YYYY]  Kết thúc [MM/YYYY]  [✓] Đang làm ở đây                          │ │ │
│ │ │ Mô tả                                                                              │ │ │
│ │ │ ┌──────────────────────────────────────────────────────────────────────────────┐ │ │ │
│ │ │ │ Xử lý trung bình 40 yêu cầu mỗi ngày…                                         │ │ │ │
│ │ │ └──────────────────────────────────────────────────────────────────────────────┘ │ │ │
│ │ │ 312/2 000                                            [✦ Gợi ý diễn đạt]          │ │ │
│ │ │ ▬▬▬▬▬▬ (LinearProgress — chỉ khi đang chờ)  AI đang diễn đạt lại đoạn mô tả…      │ │ │
│ │ │ ┌ khối AI (surface-container-high + viền outline-variant) ─────────────────────┐ │ │ │
│ │ │ │ ✦ Do AI tạo                                                                   │ │ │ │
│ │ │ │ Trung bình xử lý 40 yêu cầu mỗi ngày qua điện thoại và email; …               │ │ │ │
│ │ │ │ AI chỉ diễn đạt lại. Hãy kiểm tra số liệu, tên công ty, công nghệ trước khi   │ │ │ │
│ │ │ │ dùng.                                                                         │ │ │ │
│ │ │ │                                         [ Bỏ qua ]  [ Dùng gợi ý ]            │ │ │ │
│ │ │ └───────────────────────────────────────────────────────────────────────────────┘ │ │ │
│ │ └────────────────────────────────────────────────────────────────────────────────────┘ │ │
│ │ [+ Thêm kinh nghiệm]                                              1/15 mục            │ │
│ └───────────────────────────────────────────────────────────────────────────────────────┘ │
│ ┌ Card ── Học vấn ── (mỗi mục: Trường *, Bằng cấp, Chuyên ngành, Bắt đầu, Kết thúc, GPA) ┐ │
│ ┌ Card ── Dự án ── (mỗi mục: Tên dự án *, Mô tả + Gợi ý diễn đạt, Công nghệ [thẻ])      ┐ │
│ ┌ Card ── Kỹ năng ── [SkillTagInput]                                    3/50            ┐ │
│ ┌ Card ── Chứng chỉ ── (mỗi mục: Tên chứng chỉ *, Đơn vị cấp, Thời gian cấp)            ┐ │
│ ┌ Card ── Tên phiên bản ── [ Ví dụ: Bản CSKH 2026 ]  (không bắt buộc)                  ┐ │
│                                                                                          │
│ thanh thao tác (cuối form, trong luồng trang, KHÔNG fixed/sticky — REQUIREMENT 13.5 L1)  │
│ ⚠ Kinh nghiệm 2: Tên công ty là bắt buộc.                 [ Xem trước ]  [ Lưu CV ]      │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

- Thứ tự khối trên trang **trùng thứ tự in** của PDF (REQUIREMENT R-P6) sau hai thẻ "Điền sẵn", "Mẫu trình bày" — để ứng viên
  thấy đúng thứ tự trên file.
- Mỗi mục trong khối danh sách là một khung viền `outline-variant`, tiêu đề "{Khối} {i}"; nút lên/xuống/xoá là icon button có
  `aria-label`. Xoá mục không hỏi xác nhận (khôi phục bằng cách nhập lại — form chưa lưu).
- Khối danh sách rỗng hiện câu rỗng của khối (mục 7) + nút "Thêm …".

### 4b. Hộp thoại xem trước (≥ `md`)

```
┌ Dialog (max-w-4xl, cao 85vh) ─────────────────────────────────────────────┐
│ Xem trước CV                                                    [✕]       │
│ Bản xem trước chưa được lưu. Sửa form rồi bấm "Xem trước" để xem lại.     │
│ ┌ iframe src=blob:… (title="Bản xem trước CV") ────────────────────────┐ │
│ │                    [trang PDF]                                        │ │
│ └───────────────────────────────────────────────────────────────────────┘ │
│                                    [ Tải bản xem trước ]  [ Đóng ]        │
└───────────────────────────────────────────────────────────────────────────┘
```

### 4c. Sửa CV — `/candidate/resumes/:id/edit`

Như 4a, khác:
- Tiêu đề "Sửa CV"; dòng phụ: `{fileName} · {versionLabel}` của CV đang sửa + câu `editNotice` (mục 7).
- **Không** có thẻ "Điền sẵn".
- Nút lưu: "Lưu thành CV mới".

### 4d. Không sửa được

```
┌──────────────────────────────────────────────────────────────┐
│ CV này được tải lên từ file nên không sửa được bằng công cụ   │   (409 CV_NOT_FROM_BUILDER)
│ tạo CV.                                                       │
│ Không tìm thấy CV này.                                        │   (404)
│ ← Về Hồ sơ và CV                                              │
└──────────────────────────────────────────────────────────────┘
```

### 4e. Điện thoại (375px)

```
┌ 375px ───────────────────────────────┐
│ ← Về Hồ sơ và CV                      │
│ Tạo CV                                │
│ ┌ Mẫu trình bày ────────────────────┐ │
│ │ (●) Cổ điển                        │ │
│ │ ( ) Hiện đại                       │ │
│ └────────────────────────────────────┘ │
│ ┌ Thông tin liên hệ ────────────────┐ │
│ │ Họ và tên *                        │ │
│ │ [                                ] │ │  (mọi ô một cột, w-full)
│ │ Email                              │ │
│ │ [                                ] │ │
│ └────────────────────────────────────┘ │
│ ┌ Kinh nghiệm 1 ──────── [↑][↓][Xoá]┐ │
│ │ …                                  │ │
│ │ [     ✦ Gợi ý diễn đạt          ]  │ │  (w-full)
│ └────────────────────────────────────┘ │
│ (cuối form, trong luồng trang)        │
│ [          Xem trước               ]  │
│ [           Lưu CV                 ]  │
└───────────────────────────────────────┘
```

## 5. Component

### 5a. File mới (chốt tên — REQUIREMENT mục 9 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/features/cvBuilder/types.ts` | `CvTemplate`, `CvBuilderForm` (khớp REQUIREMENT R-F1), `CvBuilderRequest`, `CvBuilderDocumentResponse`, `CvRephraseKind`, `CvRephraseRequest`, `CvRephraseResponse` |
| `frontend/src/features/cvBuilder/api.ts` | `createBuilderResume(req)` (B1), `getBuilderDocument(resumeId)` (B2), `previewBuilderResume(req)` → `Blob` (B3, `responseType: 'blob'`), `rephraseDescription(req)` (B4) |
| `frontend/src/features/cvBuilder/cvBuilderLabels.ts` | Mọi chuỗi mục 7 + bảng nhãn ô (trùng nguyên văn REQUIREMENT 4.4) + hằng giới hạn R-F5 (một chỗ) |
| `frontend/src/features/cvBuilder/cvBuilderSchema.ts` | Zod: chuẩn hoá R-F3; NFC chỉ dùng khi đếm độ dài (để khớp backend), dữ liệu gửi đi giữ nguyên — backend chuẩn hoá; và kiểm R-F5–R-F10 với **đúng** câu lỗi backend; không kiểm glyph/từ quá dài (chỉ backend — lỗi trả về hiện ở thanh thao tác) |
| `frontend/src/features/cvBuilder/prefillFromParsed.ts` | Ánh xạ `ResumeParsedPayload` → form theo R-E2–R-E4; trả `{ form, droppedDates: number }` |
| `frontend/src/features/cvBuilder/CvBuilderFormView.tsx` | Toàn bộ form (React Hook Form + `useFieldArray`), nhận `mode`, `initial` |
| `frontend/src/features/cvBuilder/TemplatePicker.tsx` | `fieldset` + hai `input type="radio"` thuần (dự án chưa có `RadioGroup` trong `components/ui`), mỗi lựa chọn là một khung bấm được có viền `m3-outline`, lựa chọn đang chọn viền `m3-primary` 2px + chữ |
| `frontend/src/features/cvBuilder/ListSection.tsx` | Khung khối danh sách: tiêu đề, mục, nút thêm, bộ đếm "{n}/{max} mục", nút lên/xuống/xoá |
| `frontend/src/features/cvBuilder/MonthField.tsx` | Ô mốc thời gian (`Input` text, `inputMode="numeric"`, placeholder `MM/YYYY`) |
| `frontend/src/features/cvBuilder/RephraseAssist.tsx` | Nút "Gợi ý diễn đạt" + tiến trình + khối AI + "Dùng gợi ý"/"Bỏ qua" + lỗi/"Thử lại" cho **một** ô mô tả. Quy tắc: (1) mỗi request ghi lại nội dung ô lúc gửi; kết quả về mà nội dung ô đã khác → bỏ kết quả, không hiện gì; (2) mục bị xoá → bỏ kết quả; (3) cả trang chỉ một request gợi ý cùng lúc (các nút "Gợi ý diễn đạt" khác khoá, có `aria-disabled` + câu `rephraseBusyOther`); (4) "Dùng gợi ý" gọi `setValue(field, suggestion, { shouldDirty: true })` rồi ẩn khối; (5) gợi ý bằng đúng nội dung ô → câu `rephraseSame`, không có nút "Dùng gợi ý" |
| `frontend/src/features/cvBuilder/PreviewDialog.tsx` | Hộp thoại 4b; tạo/thu hồi `URL.createObjectURL` (thu hồi khi đóng/unmount) |
| `frontend/src/features/cvBuilder/useLeaveGuard.ts` | `beforeunload` khi form `isDirty` và chưa lưu xong (dự án dùng `BrowserRouter`, không có `useBlocker` — chỉ chặn tải lại/đóng tab, không chặn điều hướng nội bộ) |
| `frontend/src/pages/CvBuilderPage.tsx` | Trang 4a/4c/4d; `mode` theo route; chế độ sửa nạp B2 (`useQuery`) |

### 5b. File có sẵn bị sửa

| File | Sửa gì |
|---|---|
| `frontend/src/App.tsx` | Hai route `/candidate/resumes/new`, `/candidate/resumes/:id/edit` → `CvBuilderPage`, đặt ngay **trước** route `improvement-suggestions` |
| `frontend/src/pages/CandidateProfilePage.tsx` | Nút "Tạo CV" trong `CardContent` của thẻ "CV của tôi" (mục 3); câu "đã lưu" đọc từ `location.state` (mục 6). Không đổi gì khác |
| `frontend/src/features/resumes/ResumeList.tsx` | Nút "Sửa CV" (mục 3); câu rỗng `emptyResumeList` |
| `frontend/src/features/resumes/types.ts` | `Resume.fromCvBuilder: boolean` |
| `frontend/src/features/candidateProfile/SkillTagInput.tsx` | (REQUIREMENT 13.5 L12) Thêm **2 prop tuỳ chọn**: `inputAriaLabel` (mặc định "Nhập kỹ năng mới") và `removeAriaLabel(item)` (mặc định "Xoá kỹ năng {x}"). Không đổi gì khác — U14 không truyền hai prop này nên hành vi giữ nguyên |

### 5c. Dùng lại có sẵn

`Button`, `Input`, `Textarea`, `Label`, `Card`, `Dialog`, `Select`, `Checkbox` (`components/ui` — không thêm component mới),
`CandidateLayout`,
`LinearProgress` (`features/resumes/LinearProgress.tsx`), `SkillTagInput` (`features/candidateProfile/SkillTagInput.tsx` — dùng
cho cả Kỹ năng và Công nghệ), `useResumesQuery`, `useCatalogsQuery`. Dự án **không** có toast/snackbar: mọi thông báo xác nhận là câu tại chỗ
`role="status"` (mục 6). Icon lucide: `FilePlus2`,
`PencilLine`, `Sparkles`, `Info`, `AlertCircle`, `ArrowLeft`, `ArrowUp`, `ArrowDown`, `Trash2`, `Plus`, `Eye`. Không thêm thư viện.

### 5d. Bảng token

| Phần | Token / class | Ghi chú |
|---|---|---|
| Tiêu đề trang | `text-m3-headline-sm text-m3-on-surface` | Trên nền trang |
| Câu giới thiệu | `text-sm text-m3-on-surface` | Trên nền trang (`surface-container`) → không dùng `on-surface-variant` |
| Thẻ khối | `Card` outlined mặc định, `max-w-3xl` | |
| Tiêu đề khối | `CardTitle text-m3-title-md` | |
| Khung mục | `rounded-m3-sm border border-m3-outline-variant p-3`, tiêu đề `text-sm font-medium text-m3-on-surface` | Viền phân khối, không phải viền điều khiển |
| Ô nhập | `Input`/`Textarea` (viền `--input` → `m3-outline`), `min-h-10` | ≥ 3:1 (UI_GUIDE mục 6) |
| Nhãn ô bắt buộc | chữ thật " *" + `aria-required="true"` | Không chỉ bằng màu |
| Bộ đếm, câu gợi ý dưới ô | `text-sm text-m3-on-surface-variant`; vượt giới hạn → `text-m3-error` | Trên `m3-surface` (thẻ trắng) |
| Lỗi dưới ô | icon `AlertCircle text-m3-error` + chữ `text-sm text-m3-error`, `role="alert"` | Trên `m3-surface` (4.74:1) |
| Nút "Gợi ý diễn đạt" | `Button variant="outline" size="sm"` + `Sparkles`, `min-h-10`, `w-full sm:w-auto` | |
| Khối AI | `rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-3` | Viền bắt buộc (UI_GUIDE mục 4) |
| Chữ trong khối AI | **chỉ** `text-m3-on-surface` (nhãn "Do AI tạo", gợi ý, câu nhắc) | `on-surface-variant` không đạt trên nền này |
| Nút trong khối AI | "Dùng gợi ý" `Button` mặc định; "Bỏ qua" `variant="outline"` | |
| Thanh thao tác | Khối cuối form, trong luồng trang, **không** `fixed`/`sticky` (REQUIREMENT 13.5 L1 — cùng quy ước U08, U09, U10, H09, H10, H11, H12, H14, C06): `flex flex-col gap-3 sm:flex-row sm:items-center` | Trên nền trang. Lỗi server hiện bên trái/trên nút (icon `AlertCircle` `m3-error` + chữ `text-m3-on-surface`, `role="alert"`, `tabIndex={-1}`) |
| Nút "Lưu CV" / "Lưu thành CV mới" | `Button` mặc định (`m3-primary`) | |
| Nút "Xem trước" | `variant="outline"` + `Eye` | |
| Câu "không in lên CV" ở ô ngành | icon `Info` + `text-sm text-m3-on-surface-variant` | |
| Khối 4d | `text-sm text-m3-on-surface` + `Link` `text-m3-on-primary-container` | Trên nền trang |

## 6. Trạng thái hiển thị

| Trạng thái | Hiển thị |
|---|---|
| Đang tải (sửa) | Skeleton 4 thẻ `bg-m3-surface-container animate-pulse`; không form |
| Đang tải danh sách CV (thẻ Điền sẵn) | `Select` khoá, chữ "Đang tải danh sách CV…" |
| Không có CV nào `DONE` | Thẻ Điền sẵn hiện `prefillNone`, không `Select` |
| Không tìm thấy (sửa) | 404 → `resumeNotFound` (4d) |
| Không sửa được (sửa) | 409 `CV_NOT_FROM_BUILDER` → câu backend (4d) |
| Rỗng | Form trống, mẫu `CLASSIC` chọn sẵn; mỗi khối danh sách hiện câu rỗng của khối |
| Đang điền sẵn | Nút "Điền sẵn" khoá + "Đang điền…"; lỗi tải → `prefillFailed` dưới `Select` |
| Điền sẵn xong | Câu `prefillDone` (`role="status"`) trong thẻ Điền sẵn; có mốc bị bỏ → câu `prefillDateNotice` ở đầu thẻ Điền sẵn (giữ tới khi điền sẵn lần khác hoặc rời trang) |
| Kiểm phía client lỗi | Bấm "Lưu"/"Xem trước": lỗi dưới từng ô sai, focus ô lỗi đầu tiên, không gọi API; form rỗng → câu `CV_BUILDER_EMPTY` ở thanh thao tác |
| Lỗi từ server (400/409/500) | Câu `message` của backend ở thanh thao tác cuối form (`role="alert"`); cuộn tới thanh thao tác (`scrollIntoView({ block: 'center' })`) và focus khối lỗi; không xoá dữ liệu form. 409 `CV_BUILDER_CONCURRENT_CREATE` → thêm liên kết "Về Hồ sơ và CV" |
| Đang lưu | Hai nút khoá; nút lưu đổi chữ "Đang lưu…"; ô vẫn hiện (không khoá cả trang) |
| Lưu xong | `navigate('/candidate/profile', { state: { cvBuilderSaved: 'create' \| 'edit' } })`; danh sách CV tải lại (`invalidateQueries` khoá danh sách CV); trang hồ sơ đọc `location.state` và hiện câu `savedCreate`/`savedEdit` (`role="status"`, `text-sm text-m3-on-surface-variant` + icon `Info`) ngay trên `ResumeList` trong thẻ "CV của tôi", rồi `navigate(location.pathname, { replace: true, state: null })` để tải lại trang không hiện lại câu |
| Đang dựng xem trước | Nút "Xem trước" khoá + "Đang tạo bản xem trước…"; lỗi → câu ở thanh thao tác |
| AI đang xử lý (gợi ý) | Nút "Gợi ý diễn đạt" của ô đó khoá + `LinearProgress` nhãn `rephraseProgress`; ô mô tả **vẫn gõ được** (gõ thì kết quả về bị bỏ — 5a); nút gợi ý ở ô khác khoá |
| Gợi ý xong | Khối AI 4a; gợi ý bằng nội dung ô → câu `rephraseSame` thay cho nút "Dùng gợi ý" |
| Lỗi gợi ý | Dưới nút: câu `message` backend nếu có `{error, message}` (400, 429, 502, 503, 504), ngược lại `rephraseFailedFallback`; nút "Thử lại" gửi lại **đúng nội dung lúc gửi** — nội dung ô đã đổi thì "Thử lại" ẩn |
| Không có quyền (403) | Không xảy ra qua giao diện (route chỉ cho ứng viên); 403 từ API → câu dự phòng |

## 7. Nội dung chữ (tiếng Việt có dấu)

Tất cả ở `cvBuilderLabels.ts`. `{n}`, `{max}`, `{i}` là số.

| Khoá | Chuỗi |
|---|---|
| `createButton` | Tạo CV |
| `editButton` | Sửa CV |
| `emptyResumeList` | Chưa có CV nào. Tải lên CV đầu tiên hoặc bấm "Tạo CV" để tạo bằng công cụ. |
| `backToProfile` | Về Hồ sơ và CV |
| `pageTitleCreate` | Tạo CV |
| `pageTitleEdit` | Sửa CV |
| `intro` | Điền thông tin theo từng mục. Hệ thống tạo file PDF từ đúng nội dung bạn nhập. |
| `editNotice` | Lưu sẽ tạo một CV mới. CV này và các đơn đã nộp bằng nó giữ nguyên. |
| `prefillTitle` | Điền sẵn từ CV đã trích xuất (không bắt buộc) |
| `prefillSelectPlaceholder` | Chọn CV |
| `prefillButton` | Điền sẵn |
| `prefillRunning` | Đang điền… |
| `prefillNone` | Bạn chưa có CV nào đã xử lý xong để điền sẵn. |
| `prefillConfirm` | Thay toàn bộ nội dung đang nhập bằng dữ liệu từ CV đã chọn? |
| `prefillConfirmOk` | Thay nội dung |
| `prefillConfirmCancel` | Giữ nội dung hiện tại |
| `prefillDone` | Đã điền sẵn từ CV đã chọn. Hãy kiểm tra lại từng mục. |
| `prefillDateNotice` | Một số mốc thời gian trong CV đã chọn không có dạng MM/YYYY hoặc YYYY nên chưa được điền. |
| `prefillFailed` | Không tải được dữ liệu của CV đã chọn, vui lòng thử lại. |
| `templateTitle` | Mẫu trình bày |
| `templateClassic` | Cổ điển — Tên căn trái, tiêu đề mục có đường kẻ |
| `templateModern` | Hiện đại — Tên căn giữa, tiêu đề mục màu xanh đậm |
| `sectionContact` | Thông tin liên hệ |
| `sectionGeneral` | Thông tin chung |
| `sectionExperience` | Kinh nghiệm làm việc |
| `sectionEducation` | Học vấn |
| `sectionProjects` | Dự án |
| `sectionSkills` | Kỹ năng |
| `sectionCertifications` | Chứng chỉ |
| `sectionVersion` | Tên phiên bản |
| `industryHint` | Dùng để gợi ý việc làm, không in lên CV. |
| `locationHint` | Ví dụ: Hà Nội, TP. Hồ Chí Minh. |
| `dateHint` | MM/YYYY hoặc YYYY |
| `currentLabel` | Đang làm ở đây |
| `versionPlaceholder` | Ví dụ: Bản CSKH 2026 |
| `addExperience` / `addEducation` / `addProject` / `addCertification` | Thêm kinh nghiệm / Thêm học vấn / Thêm dự án / Thêm chứng chỉ |
| `itemCounter` | {n}/{max} mục |
| `charCounter` | {n}/{max} |
| `moveUp` / `moveDown` / `removeItem` (aria-label) | Đưa {khối} {i} lên / Đưa {khối} {i} xuống / Xoá {khối} {i} |
| `emptyExperience` | Chưa có kinh nghiệm nào. |
| `emptyEducation` | Chưa có học vấn nào. |
| `emptyProjects` | Chưa có dự án nào. |
| `emptyCertifications` | Chưa có chứng chỉ nào. |
| `emptySkills` | Chưa có kỹ năng nào. |
| `skillsPlaceholder` | Nhập kỹ năng rồi nhấn Enter |
| `technologiesPlaceholder` | Nhập công nghệ rồi nhấn Enter |
| `skillsMaxReached` | Đã đạt tối đa 50 kỹ năng. |
| `technologiesMaxReached` | Đã đạt tối đa 20 công nghệ. |
| `technologiesInputLabel` (aria-label) | Nhập công nghệ mới |
| `technologiesRemove` (aria-label) | Xoá công nghệ {x} |
| `rephraseButton` | Gợi ý diễn đạt |
| `rephraseProgress` | Đang tạo gợi ý diễn đạt |
| `rephraseHint` | AI đang diễn đạt lại đoạn mô tả… |
| `aiBadge` | Do AI tạo |
| `rephraseCaution` | AI chỉ diễn đạt lại. Hãy kiểm tra số liệu, tên công ty, công nghệ trước khi dùng. |
| `rephraseUse` | Dùng gợi ý |
| `rephraseDismiss` | Bỏ qua |
| `rephraseSame` | AI không đề xuất cách diễn đạt khác. |
| `rephraseBusyOther` | Đang tạo gợi ý cho một đoạn khác. |
| `rephraseFailedFallback` | Chưa tạo được gợi ý diễn đạt, vui lòng thử lại. |
| `rephraseTooShort` | Đoạn mô tả cần ít nhất 20 ký tự để gợi ý diễn đạt. |
| `rephraseTooLong` | Gợi ý diễn đạt nhận đoạn mô tả tối đa 800 ký tự. |
| `rephraseEmpty` | Vui lòng nhập đoạn mô tả trước khi xin gợi ý. |
| `retry` | Thử lại |
| `previewButton` | Xem trước |
| `previewRunning` | Đang tạo bản xem trước… |
| `previewTitle` | Xem trước CV |
| `previewNotice` | Bản xem trước chưa được lưu. Sửa form rồi bấm "Xem trước" để xem lại. |
| `previewFrameTitle` | Bản xem trước CV |
| `previewDownload` | Tải bản xem trước |
| `previewClose` | Đóng |
| `previewOpenCompact` | Bản xem trước đã được tải về máy. |
| `saveCreate` | Lưu CV |
| `saveEdit` | Lưu thành CV mới |
| `saving` | Đang lưu… |
| `savedCreate` | Đã tạo CV. |
| `savedEdit` | Đã lưu thành một CV mới. CV cũ vẫn được giữ nguyên. |
| `saveFailedFallback` | Chưa lưu được CV, vui lòng thử lại. |
| `leaveWarning` | (trình duyệt tự hiện câu của nó cho `beforeunload` — không đặt chuỗi riêng) |
| `resumeNotFound` | Không tìm thấy CV này. |
| Nhãn ô | Đúng bảng nhãn ô của REQUIREMENT 4.4 ("Họ và tên", "Email", "Điện thoại", "Địa chỉ", "LinkedIn", "Chức danh hiện tại", "Khu vực", "Ngành nghề", "Trường", "Bằng cấp", "Chuyên ngành", "Thời gian bắt đầu", "Thời gian kết thúc", "GPA", "Tên công ty", "Chức danh", "Mô tả", "Kỹ năng", "Tên chứng chỉ", "Đơn vị cấp", "Thời gian cấp", "Tên dự án", "Công nghệ", "Tên phiên bản") |
| Câu lỗi kiểm form | Đúng mẫu câu `INVALID_CV_BUILDER_FORM` / `CV_BUILDER_EMPTY` của REQUIREMENT 4.4 (frontend và backend cùng câu) |

Kiểm phía client trước khi gọi API (không gọi khi sai): R-F5–R-F10 cho "Lưu"/"Xem trước"; độ dài đoạn 20–800 cho "Gợi ý diễn
đạt" (`rephraseTooShort`/`rephraseTooLong`/`rephraseEmpty` dưới nút, focus vào ô mô tả). Các nút **không** khoá vì lý do này (chỉ
khoá khi đang chờ) — người dùng bàn phím/đọc màn hình biết vì sao không gửi được.

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Cỡ | Khác biệt |
|---|---|
| Compact (< 600px) | Mọi ô một cột `w-full`; nút "Gợi ý diễn đạt", "Thêm …", hai nút thanh thao tác `w-full` xếp dọc; nút lên/xuống/xoá giữ icon 40×40. "Xem trước" **không** mở hộp thoại (trình duyệt điện thoại thường không hiện PDF trong `iframe`): tải file `xem-truoc-cv.pdf` về máy + câu `previewOpenCompact` (`role="status"`) ở thanh thao tác |
| Medium (600–1023px) | Ô ngắn (Email/Điện thoại, Bắt đầu/Kết thúc) hai cột; hộp thoại xem trước `max-w-[90vw]` |
| Expanded (≥ 1024px) | Như 4a; nội dung `max-w-3xl` giữa trang |

Không cuộn ngang toàn trang ở 375px; vùng chạm ≥ 40px (`min-h-10`).

## 9. Khả năng tiếp cận

- Mọi ô có `Label` thật; ô bắt buộc có " *" bằng chữ + `aria-required`; ô lỗi `aria-invalid` + `aria-describedby` trỏ tới câu lỗi
  và bộ đếm.
- Mỗi khối là `section` có `aria-labelledby` trỏ tới tiêu đề khối; mỗi mục là `fieldset` có `legend` "{Khối} {i}".
- Nút icon (lên/xuống/xoá) có `aria-label` (mục 7); sau khi xoá, focus chuyển về nút "Thêm …" của khối.
- Lỗi thanh thao tác `role="alert"`; lỗi kiểm client: focus vào ô lỗi đầu tiên.
- Khối gợi ý: vùng `aria-live="polite"` để gợi ý được đọc khi xuất hiện; `LinearProgress` có `role="progressbar"` + nhãn (có sẵn);
  nhãn "Do AI tạo" là chữ thật.
- Chọn mẫu: `fieldset` + `legend` "Mẫu trình bày", radio thuần (phím mũi tên đổi lựa chọn theo hành vi mặc định của trình
  duyệt); mô tả mẫu là chữ, không chỉ hình.
- `iframe` xem trước có `title`; nút "Tải bản xem trước" là lối thay thế khi trình đọc màn hình không đọc được PDF trong khung.
- Icon trang trí `aria-hidden="true"`. Không truyền đạt gì chỉ bằng màu.

## 10. Không được làm

- Thêm ô ảnh, ngày sinh, tuổi, giới tính, tình trạng hôn nhân, tôn giáo, dân tộc, quốc tịch, chiều cao, cân nặng, sức khoẻ
  (REQUIREMENT R-F2); thêm ô ngoài schema R-F1 ("Mục tiêu nghề nghiệp", "Sở thích").
- Tự thay nội dung ô bằng gợi ý khi chưa bấm "Dùng gợi ý"; tự gọi gợi ý khi gõ hay khi rời ô; gợi ý cho cả CV một lần.
- Hiện gợi ý ngoài khối AI, thiếu nhãn "Do AI tạo", dùng `on-surface-variant` trong khối AI.
- Dựng bản xem trước bằng HTML/CSS ở frontend (REQUIREMENT Q-U8); tự gọi xem trước khi gõ.
- Lưu nháp form vào `localStorage`/`sessionStorage`/server; tự lưu.
- Hiện nút "Sửa CV" cho CV tải lên; cho sửa ghi đè CV cũ; gợi ý "đặt bản sửa làm CV chính" tự động.
- Gắn nhãn hay màu gợi ý đánh giá chất lượng CV ("CV tốt", thanh điểm hoàn thiện %, đỏ/xanh).
- Hiện mã lỗi thô thay cho câu tiếng Việt; hiện lại giá trị người dùng gõ trong câu lỗi server.
- Thêm component vào `components/ui`, thêm thư viện (PDF viewer, rich text editor, date picker).
- Dùng `input type="month"` (Safari desktop không hỗ trợ — rơi về ô chữ không có định dạng) thay cho `MonthField`.
- Thanh thao tác `fixed`/`sticky` (REQUIREMENT 13.5 L1).
