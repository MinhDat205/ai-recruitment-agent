# FR-H16 — Giao diện

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 1h (vùng chạm), 2 (layout HR), 3 (Button, Card, Dialog, badge trung
tính), 5 (rỗng/tải/lỗi), 6 (khả năng tiếp cận). Mã `R-…`, `B1`, `[Q…]` là của `REQUIREMENT.md` cùng thư mục.

**Nguyên tắc của cả màn:** frontend **không** tự che, không tự dựng nhãn, không lọc chuỗi. Nhãn "Ứng viên #…" và "[đã ẩn]"
đến từ API (R-N, R-M). Frontend chỉ dựa vào `identityMasked` (danh sách theo Job, E1) để khoá nút và đổi câu, và vào
`blindReviewEnabled` (Job) để vẽ khối bật/tắt.

## 1. Trạng thái

> Trạng thái: ĐÃ DUYỆT 18/10/2026

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | Thay đổi của H16 |
|---|---|---|---|
| `/hr/jobs/:id/edit` | Tab "Thông tin tin tuyển dụng" | Hiện có | Khối "Chế độ ẩn danh" (4a) |
| `/hr/jobs/:id/edit?tab=applications` | Tab "Ứng viên" | Hiện có | Dòng thông tin khi Job bật; khoá "Xem CV gốc" của dòng ẩn danh (4b) |
| `/hr/applications/:id` | Trang hồ sơ đơn | Hiện có | Badge "Ẩn danh"; khối Hồ sơ đã nộp, thư giới thiệu, liên hệ; tệp đính kèm; hộp thoại giấy mời; nút "Lưu vào kho" (4c–4f) |
| `/hr/jobs/:id/compare`, `/hr/candidates`, `/hr/messages`, `/hr/talent-pools/:id`, chuông thông báo | Như hiện có | Hiện có | **Không sửa code**: nhãn và chuỗi đã che đến từ API |

Không thêm route.

## 3. Điểm vào

- Bật/tắt: chỉ ở khối 4a của tab "Thông tin tin tuyển dụng". Không có điểm vào nào khác (không ở danh sách Job, không ở
  trang hồ sơ đơn).

## 4. Bố cục (khung ASCII)

### 4a. Khối "Chế độ ẩn danh" — tab "Thông tin tin tuyển dụng"

Card outlined **riêng**, đặt **dưới** form tin (sau `CardFooter` "Lưu thay đổi"), không nằm trong `<form>`; bấm nút không
submit form tin, không làm form "dirty".

```
┌ Card outlined ───────────────────────────────────────────────────────────┐
│ Chế độ ẩn danh                                                           │  title-md
│ Đang tắt. HR thấy họ tên, liên hệ, CV gốc và thư giới thiệu của ứng viên. │  body-md on-surface-variant
│ Khi bật: hồ sơ hiện dạng "Ứng viên #…", không tải được CV gốc, thư giới   │
│ thiệu và thông tin liên hệ được ẩn. Hồ sơ tự hiện danh tính khi bạn gửi   │
│ giấy mời phỏng vấn.                                                      │
│                                                       [Bật ẩn danh]      │  Outlined
└──────────────────────────────────────────────────────────────────────────┘

Khi đang bật: câu đầu "Đang bật." + câu "Đã đổi lúc {thời điểm}" (nếu có changedAt) + nút [Tắt ẩn danh].
```

Bấm nút → Dialog xác nhận:

```
┌ Dialog ─────────────────────────────────────────────────┐
│ Bật chế độ ẩn danh cho tin này?                         │
│ Áp dụng ngay cho mọi hồ sơ chưa được mời phỏng vấn.     │
│ Thông báo đã nhận trước đó không thay đổi.              │
│                                  [Huỷ]  [Bật ẩn danh]   │  Text / Filled
└─────────────────────────────────────────────────────────┘

Tắt: "Tắt chế độ ẩn danh cho tin này?" / "Họ tên và thông tin liên hệ của mọi hồ sơ sẽ hiện lại. Việc bật/tắt được ghi
lại." / [Huỷ] [Tắt ẩn danh].
```

Thành công → đóng hộp thoại, câu trạng thái đổi, `aria-live="polite"`; invalidate query Job và mọi query danh sách đơn của
Job (nhãn đổi ngay khi HR chuyển tab).

### 4b. Tab "Ứng viên" khi Job bật

```
┌ khối thông tin trung tính ──────────────────────────────────────────────┐
│ ⓘ Tin này đang bật chế độ ẩn danh. Hồ sơ chưa được mời phỏng vấn hiện   │
│   dạng "Ứng viên #…".                                                    │
└──────────────────────────────────────────────────────────────────────────┘
│ ☐ │ Ứng viên #00000001 │ … │ [Xem CV gốc] (khoá) [Xem hồ sơ] │
│ ☐ │ Trần Bảo Ngọc      │ … │ [Xem CV gốc]        [Xem hồ sơ] │
```

- Nguồn câu thông tin: `blindReviewEnabled` của Job (query sẵn có của trang sửa Job; `ApplicationsTab` chỉ nhận `jobId` —
  `ApplicationsTab.tsx:344` — nên tab tự gọi hook query Job sẵn có, không thêm prop).
- Dòng `identityMasked = true`: "Xem CV gốc" `disabled` + `title`/`aria-describedby` câu lý do (mục 7), theo đúng cơ chế
  `resumeDisabledReason` hiện có (`ApplicationsTab.tsx:173`, `:258-259`; hàm `resumeDownloadDisabledReason` thêm nhánh
  `identityMasked`).
- Ô chọn so sánh, nút "Xem hồ sơ": không đổi.

### 4c. Header trang hồ sơ đơn

```
Ứng viên #00000001  [Ẩn danh]  [Chờ duyệt]          [Mời phỏng vấn] [Từ chối]  [Lưu vào kho](khoá)
Nộp ngày 05/10/2026
```

- Badge "Ẩn danh": cùng kiểu badge trạng thái trung tính (UI_GUIDE mục 3), chỉ khi `identityMasked`.
- "Lưu vào kho": `identityMasked` → `disabled` + câu lý do (mục 7) bằng `aria-describedby`; không mở hộp thoại (R-K2 là
  chốt chặn thật).

### 4d. Tab "CV & điểm" khi `identityMasked`

```
Hồ sơ đã nộp
┌ khối thông tin trung tính ──────────────────────────────────────────────┐
│ Chế độ ẩn danh: không tải được CV gốc, tên tệp và thư giới thiệu được ẩn. │
└──────────────────────────────────────────────────────────────────────────┘
[ParseStatusBadge]                               (không có nút "Xem CV gốc", không có tên tệp, không có CoverLetterBlock)

Điểm theo rubric                                 (không đổi — chuỗi "[đã ẩn]" nếu có đến từ API)

CV đã trích xuất
  Liên hệ: "Thông tin liên hệ được ẩn trong chế độ ẩn danh."   (thay câu "Không trích xuất được…")
  Học vấn / Kinh nghiệm / …                       (không đổi)
```

`ResumeParsedDataView` dùng chung với phía ứng viên: thêm prop tuỳ chọn `contactMasked?: boolean` (mặc định `false`); phía
ứng viên không truyền → hành vi không đổi.

### 4e. Tab "Trao đổi"

- Tin của ứng viên có `attachment` mà `fileName = null`: chip tệp hiện "Tệp đính kèm (đã ẩn tên)" + loại + dung lượng,
  **không** có hành động tải; `title` câu lý do (mục 7).
- Tin, nhãn người gửi, khung soạn, nút "Soạn bằng AI": không đổi.

### 4f. Hộp thoại "Mời phỏng vấn" (FR-H12) khi đơn đang ẩn danh

```
┌ Dialog ─────────────────────────────────────────────┐
│ Mời phỏng vấn — Ứng viên #00000001                  │  (`invitationLabels.ts:5`, tên = nhãn từ API)
│ ┌ khối thông tin trung tính ───────────────────────┐ │
│ │ Họ tên ứng viên sẽ được điền vào chỗ             │ │
│ │ {{candidateName}} khi gửi. Hồ sơ hiện danh tính  │ │
│ │ sau khi gửi giấy mời.                            │ │
│ └──────────────────────────────────────────────────┘ │
│ Tiêu đề  [Thư mời phỏng vấn — {{candidateName}}]    │
│ Nội dung [Chào {{candidateName}}, …]                │
│ Khung giờ …                                          │
│                                    [Huỷ]  [Gửi]      │
└──────────────────────────────────────────────────────┘
```

- Biết đơn ẩn danh bằng: hộp thoại mở từ trang hồ sơ đơn (E1 `identityMasked`), tab Ứng viên (dòng `identityMasked`) hoặc
  trang so sánh. Trang so sánh không có `identityMasked`: hiện khối thông tin khi `content` hoặc `subject` của preview còn
  chuỗi `{{candidateName}}` — một điều kiện duy nhất cho cả ba điểm vào, prop không đổi.
- Không chặn HR xoá chỗ `{{candidateName}}`.
- Lỗi 400 `INVITATION_RENDERED_TOO_LONG`: hiện câu backend trong hộp thoại, giữ nội dung đã nhập.
- Gửi thành công → invalidate E1, danh sách đơn của Job, hộp thư — tên thật hiện ngay.

## 5. Component

### 5a. File mới

| File | Vai trò |
|---|---|
| `frontend/src/features/blindReview/BlindReviewCard.tsx` | Khối 4a + hộp thoại xác nhận |
| `frontend/src/features/blindReview/api.ts`, `queries.ts` | B1; mutation invalidate `hrJobDetailKey(id)`, `HR_JOBS_KEY` (`features/jobs/ownerQueries.ts:20`, `:26` — hiện không export: export hai khoá này, không khai lại) và `hrApplicationsKeyPrefix(jobId)` (`features/scoring/queries.ts:186`) |
| `frontend/src/features/blindReview/blindReviewLabels.ts` | Chuỗi mục 7 (mẫu `talentPoolLabels.ts`) |

### 5b. File có sẵn bị sửa

| File | Thay đổi |
|---|---|
| `frontend/src/pages/HrJobEditPage.tsx` | Gắn `BlindReviewCard` dưới form trong `JobInfoTab` |
| `frontend/src/features/jobs/ownerTypes.ts` (`JobOwnerResponse`, `:3`) | `blindReviewEnabled: boolean` |
| `frontend/src/features/scoring/ApplicationsTab.tsx` + kiểu dòng (`features/scoring/types.ts`, `ApplicationHrListItem`, `:72`) | `identityMasked`; khối thông tin 4b; lý do khoá "Xem CV gốc" |
| `frontend/src/features/applicationDetail/ApplicationDetailHeader.tsx` + kiểu E1 (`features/applicationDetail/types.ts`, `ApplicationHrDetail`, `:8`) | `identityMasked`; badge; khoá "Lưu vào kho" |
| `frontend/src/features/applicationDetail/CvAndScoreTab.tsx` | Khối 4d |
| `frontend/src/features/resumes/ResumeParsedDataView.tsx` | Prop `contactMasked` |
| `frontend/src/features/messages/MessageBubble.tsx` + kiểu tin (`features/messages/types.ts`, `MessageAttachment`, `:12`) | `fileName: string \| null`; chip 4e |
| `frontend/src/features/interviewinvitation/InterviewInvitationDialog.tsx` | Khối thông tin 4f; hiện lỗi 400 mới |

Người code đối chiếu tên file kiểu ở Plan Mode; không đổi file nào khác.

### 5c. Dùng lại có sẵn

`components/ui/` (`card`, `button`, `dialog`), badge trạng thái trung tính, khối thông tin trung tính kiểu FR-H15 4d,
`formatDateTimeVi` (`lib/date`). Không thêm Switch (a21), không thêm thư viện.

### 5d. Bảng token

| Phần tử | Token |
|---|---|
| Card 4a | nền `m3-surface`, viền `m3-outline-variant` |
| Khối thông tin (4b, 4d, 4f) | nền `m3-surface-container`, viền `m3-outline-variant`, chữ `m3-on-surface` — trung tính |
| Badge "Ẩn danh" | Bảng trung tính của badge trạng thái — không đỏ/vàng/xanh |
| Chữ phụ ("Đã đổi lúc…", chip tệp) | `text-sm text-m3-on-surface-variant` |

## 6. Trạng thái hiển thị

| Màn | Đang tải | Lỗi | Thiếu dữ liệu |
|---|---|---|---|
| Khối 4a | Theo trạng thái tải của Job (khối chưa vẽ khi Job chưa có) | B1 lỗi: câu backend dưới nút, hộp thoại không đóng; nút đang gửi khoá, nhãn "Đang lưu…" | `changedAt = null` → không hiện câu "Đã đổi lúc" |
| Hồ sơ đơn | Không đổi | Tải CV/tệp trả 409 (dữ liệu đổi giữa chừng): hiện câu `IDENTITY_MASKED` tại chỗ như lỗi tải hiện có | Liên hệ bị ẩn ≠ không trích xuất được: dùng câu riêng (4d) |
| Hộp thoại giấy mời | Không đổi | 400 mới: câu backend | — |

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Tiêu đề khối | "Chế độ ẩn danh" |
| Trạng thái | "Đang tắt." / "Đang bật." |
| Mô tả khối | "Khi bật: hồ sơ hiện dạng “Ứng viên #…”, không tải được CV gốc, thư giới thiệu và thông tin liên hệ được ẩn. Hồ sơ tự hiện danh tính khi bạn gửi giấy mời phỏng vấn." |
| Đã đổi | "Đã đổi lúc {thời điểm}" |
| Nút | "Bật ẩn danh", "Tắt ẩn danh", "Huỷ" |
| Xác nhận bật | "Bật chế độ ẩn danh cho tin này?" / "Áp dụng ngay cho mọi hồ sơ chưa được mời phỏng vấn. Thông báo đã nhận trước đó không thay đổi." |
| Xác nhận tắt | "Tắt chế độ ẩn danh cho tin này?" / "Họ tên và thông tin liên hệ của mọi hồ sơ sẽ hiện lại. Việc bật/tắt được ghi lại." |
| Tab Ứng viên | "Tin này đang bật chế độ ẩn danh. Hồ sơ chưa được mời phỏng vấn hiện dạng “Ứng viên #…”." |
| Badge | "Ẩn danh" |
| Lý do khoá tải CV / tệp | "Không tải được trong chế độ ẩn danh." |
| Lý do khoá "Lưu vào kho" | "Chỉ lưu vào kho sau khi hồ sơ hiện danh tính (gửi giấy mời phỏng vấn hoặc tắt chế độ ẩn danh)." |
| Khối Hồ sơ đã nộp | "Chế độ ẩn danh: không tải được CV gốc, tên tệp và thư giới thiệu được ẩn." |
| Liên hệ bị ẩn | "Thông tin liên hệ được ẩn trong chế độ ẩn danh." |
| Chip tệp | "Tệp đính kèm (đã ẩn tên)" |
| Hộp thoại giấy mời | "Họ tên ứng viên sẽ được điền vào chỗ {{candidateName}} khi gửi. Hồ sơ hiện danh tính sau khi gửi giấy mời." |
| Lỗi mã backend | Nguyên câu ở REQUIREMENT mục 4.3 |

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Khối 4a | Header hồ sơ đơn | Tab Ứng viên |
|---|---|---|---|
| < `sm` (375px) | Nút `w-full` dưới mô tả, cao 48px | Badge "Ẩn danh" xuống dòng cùng badge trạng thái; "Lưu vào kho" `w-full` như hiện có | Khối thông tin co theo bề rộng, chữ xuống dòng |
| `sm`–`md` | Nút căn phải | Như hiện có | Như hiện có |
| ≥ `md` | Như 4a | Như 4c | Như 4b |

## 9. Khả năng tiếp cận

- Nút "Bật/Tắt ẩn danh" có nhãn đúng hành động; câu trạng thái trong vùng `aria-live="polite"`.
- Nút khoá (tải CV, "Lưu vào kho") dùng `disabled` + `aria-describedby` trỏ câu lý do, không chỉ `title`.
- `aria-label` có sẵn chứa `candidateName` (so sánh, kho, ô chọn) tự nhận nhãn từ API — không sửa.
- Hộp thoại đóng → focus về nút đã mở.

## 10. Không được làm

- Che, cắt, thay chuỗi hay dựng nhãn ở frontend (nguồn duy nhất là API).
- Ẩn nút tải nhưng vẫn gọi được endpoint "cho nhanh" — backend là chốt chặn (R-V1, R-T2, R-K2).
- Đặt khối bật/tắt bên trong form tin, hoặc gửi cờ qua `PUT /api/hr/jobs/{id}`.
- Dùng màu đỏ/vàng/xanh cho badge hay khối ẩn danh.
- Thêm nút "Hiện danh tính" cho từng hồ sơ `[Q11]`, hay màn xem lịch sử bật/tắt (R-B5).
- Thêm component Switch hay thư viện mới.
