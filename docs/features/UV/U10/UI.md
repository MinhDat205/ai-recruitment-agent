# FR-U10 — Giao diện

> Trạng thái: ĐÃ HOÀN THÀNH (14/10/2026). Duyệt: 14/10/2026.

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 1h (vùng chạm), 3 (Dialog, nút), 4 (không phán quyết; dữ liệu
thiếu), 5 (rỗng/tải/lỗi), 6 (tương phản, viền thành phần điều khiển). Mã `R-…`, `C1`, `Q…`, `O…` là của `REQUIREMENT.md`
cùng thư mục. Khung trang chi tiết đơn (FR-U08), khối giấy mời chỉ đọc và tab "Giấy mời phỏng vấn" phía HR (FR-H12) không
chép lại, chỉ chốt phần FR-U10 thêm hoặc đổi.

## 1. Trạng thái

> Trạng thái: ĐÃ HOÀN THÀNH (14/10/2026). Duyệt: 14/10/2026. Điểm O1–O6 nhắc trong file này đã chốt ở `REQUIREMENT.md` mục 11.3.

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| `/candidate/applications/:id` | Khối "Giấy mời phỏng vấn" ở tab "Thông tin đơn": thêm phần chọn khung (radio + nút "Xác nhận khung giờ") và ★hộp xác nhận; thay câu hướng dẫn Q8 của H12 | Hiện có | FR-U08; FR-H12; FR-U10 (đã có ở cột FR của UI_GUIDE mục 7; sửa chữ ở đợt cuối, REQUIREMENT D3) |
| `/hr/applications/:id?tab=invitation` | Tab "Giấy mời phỏng vấn": thêm một dòng "Ứng viên đã chọn lúc …"; sửa một câu trong hộp thoại "Gửi lại giấy mời" | Hiện có | FR-H12; FR-U10 (D3) |
| `/hr/notifications`, chuông thông báo HR | Thêm một loại thông báo (một dòng), không đổi bố cục | Hiện có | FR-C03; FR-U10 |

Không route mới, không tab mới. Thứ tự khối trong tab "Thông tin đơn" (FR-U08 UI.md 4a) và thứ tự tab của trang HR (FR-H12
UI.md mục 2) không đổi.

## 3. Điểm vào

- Ứng viên: "Đơn ứng tuyển" → "Xem chi tiết"; hoặc bấm thông báo "Cập nhật đơn ứng tuyển … Đã mời phỏng vấn" / "Nhà tuyển
  dụng đã gửi lại giấy mời phỏng vấn" (cả hai đã trỏ `/candidate/applications/{id}`). Khối giấy mời ở **đầu** tab "Thông
  tin đơn" (tab mặc định).
- HR: bấm thông báo "Ứng viên đã chọn lịch phỏng vấn" → `/hr/applications/{id}?tab=invitation`; hoặc tự mở tab "Giấy mời
  phỏng vấn".

## 4. Bố cục (khung ASCII)

### 4a. Desktop (≥ `lg`) — khối giấy mời phía ứng viên, chọn được

Đơn `INTERVIEW_INVITED`, giấy mời hiệu lực chưa chốt, còn ít nhất một khung chưa qua (R-U1). Tiêu đề khối và thư mời giữ
như H12 4d; danh sách khung chỉ đọc (`InterviewSlotList`) được thay bằng phần chọn:

```
│ Giấy mời phỏng vấn                                                       │
│ Chọn một khung giờ phù hợp rồi bấm "Xác nhận khung giờ". Nếu không khung │
│ nào phù hợp, hãy nhắn cho nhà tuyển dụng qua tab Trao đổi.               │
│                                                                          │
│ Khung giờ (giờ Việt Nam)                                                 │
│ ( ) [lịch] 09:00 13/10/2026   Đã qua                          (khoá)     │
│            Địa điểm: Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng        │
│ (•) [lịch] 09:00 23/10/2026                                              │
│            Địa điểm: Phòng họp Sông Hàn, tầng 5                          │
│ ( ) [lịch] 14:00 24/10/2026                                              │
│            Địa điểm: https://meet.example.com/lan-u10-test               │
│ (lỗi "Vui lòng chọn một khung giờ." nếu bấm khi chưa chọn — O4)          │
│                                                                          │
│ [Xác nhận khung giờ]                                                     │
│                                                                          │
│ Thư mời phỏng vấn vị trí Nhân viên Hành chính văn phòng - …              │
│ Kính gửi Lê Thị Lan, ...                                                 │
```

- Danh sách là `<fieldset>` (legend "Khung giờ (giờ Việt Nam)") chứa các `<input type="radio" name=…>` gốc (không có
  component radio trong `components/ui` — REQUIREMENT a24; **không** thêm component vào `components/ui`). Mỗi khung là
  một `<label>` bao cả radio, thời điểm và dòng địa điểm — bấm vào bất kỳ chỗ nào của dòng đều chọn được.
- Thứ tự khung = thứ tự HR gửi (`displayOrder`), không sắp lại.
- Khung đã qua (`scheduledAt <= giờ máy` lúc render): radio `disabled`, chữ "Đã qua" bên cạnh thời điểm; dòng vẫn hiện đủ
  thời điểm + địa điểm, **không** làm mờ (opacity), không gạch ngang.
- Không chọn sẵn radio nào.
- Nút "Xác nhận khung giờ": nút chính (filled, `bg-m3-primary`), **không** khoá khi chưa chọn (O4). Bấm khi chưa chọn → câu
  lỗi dưới danh sách, focus vào radio đầu tiên chọn được; không mở hộp thoại, không gọi API. Bấm khi đã chọn → mở hộp xác
  nhận 4c.
- Câu hướng dẫn thay câu Q8 của H12 (R-U2).

### 4b. Khối giấy mời phía ứng viên — các trạng thái khác

**4b-1. Đã chọn** (giấy mời ≥ 2 khung, đã chốt — R-U3):

```
│ Giấy mời phỏng vấn                                                       │
│ Bạn đã chọn khung giờ này lúc 10:15 14/10/2026. Muốn đổi lịch, hãy nhắn  │
│ cho nhà tuyển dụng qua tab Trao đổi.                                     │
│                                                                          │
│ Khung giờ (giờ Việt Nam)                                                 │
│ [lịch] 09:00 13/10/2026                                                  │
│        Địa điểm: Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng            │
│ [lịch] 09:00 23/10/2026   [✓ Đã chốt]                                    │
│        Địa điểm: Phòng họp Sông Hàn, tầng 5                              │
│ [lịch] 14:00 24/10/2026                                                  │
│        Địa điểm: https://meet.example.com/lan-u10-test                   │
│ Thư mời phỏng vấn vị trí ...                                             │
```

Danh sách chỉ đọc `InterviewSlotList` `variant="candidate"` của H12, không đổi. "lúc {t}" dùng `formatDateTimeVi` (như
"Gửi lúc" của H12). Câu "Bạn đã chọn …" chỉ hiện khi đơn `INTERVIEW_INVITED`; đơn `HIRED`/`REJECTED` thì danh sách chỉ đọc,
không câu (cùng quy tắc 4b-4).

**4b-2. Giấy mời 1 khung** (tự chốt khi gửi): giữ nguyên H12 4d — không câu, không radio, dấu "Đã chốt".

**4b-3. Chưa chốt, mọi khung đã qua** (Q6) — đơn `INTERVIEW_INVITED`:

```
│ Giấy mời phỏng vấn                                                       │
│ Các khung giờ trong giấy mời đã qua. Hãy nhắn cho nhà tuyển dụng qua tab │
│ Trao đổi để sắp xếp lịch khác.                                           │
│                                                                          │
│ Khung giờ (giờ Việt Nam)                                                 │
│ [lịch] 09:00 13/10/2026                                                  │
│        Địa điểm: ...                                                     │
│ ...                                                                      │
```

Danh sách chỉ đọc (`InterviewSlotList`), không radio, không nút.

**4b-4. Chưa chốt, đơn không `INTERVIEW_INVITED`** (`HIRED`/`REJECTED` — O1): danh sách chỉ đọc, **không** câu nào. Đơn
`PENDING`/`WITHDRAWN` không hiện khối (FR-U08 R-T3, giữ).

### 4c. Hộp xác nhận

`Dialog` `sm:max-w-md`, cùng cấu trúc `RevokeTalentPoolConsentDialog.tsx` (REQUIREMENT a25; viết riêng, không import).

```
┌───────────────────────────────────────────────────────┐
│ Xác nhận khung giờ phỏng vấn                      [X] │
│ Bạn chọn khung giờ:                                   │
│   09:00 23/10/2026 (giờ Việt Nam)                     │
│   Địa điểm: Phòng họp Sông Hàn, tầng 5                │
│ Sau khi xác nhận, bạn không tự đổi được khung giờ.    │
│ Muốn đổi lịch, bạn cần nhắn cho nhà tuyển dụng qua    │
│ tab Trao đổi.                                         │
│ (lỗi backend nếu có)                                  │
│                                   [Huỷ]  [Xác nhận]   │
└───────────────────────────────────────────────────────┘
```

- Đang gửi: cả hai nút khoá, nhãn "Đang xác nhận..."; đóng hộp thoại (Esc, X, bấm ngoài) bị chặn khi đang gửi; không gọi
  mutate lần hai.
- Thành công: đóng hộp thoại; I4 tải lại (mutation làm mới khoá `['interview-invitation', id]`); khối chuyển sang 4b-1.
  Focus về tiêu đề khối "Giấy mời phỏng vấn" (thêm `tabIndex={-1}` cho `h2` ở `InvitationSection`); vùng `aria-live` của
  khối (có sẵn, `InvitationSection.tsx:36`) đọc câu 4b-1.
- Lỗi 409/404/400: câu `message` của backend trong hộp thoại (`extractErrorMessage`, fallback mục 7), hộp thoại **không**
  tự đóng. Mutation đã làm mới I4 (mọi 409, 404 `INTERVIEW_INVITATION_NOT_FOUND`) và khoá trang chi tiết đơn
  (`candidateApplicationDetailKey`) với `SLOT_SELECTION_NOT_ALLOWED` (trạng thái đơn đã đổi). Người dùng bấm "Huỷ"/đóng →
  thấy trạng thái mới nhất.
- 403: câu fallback "Không tìm thấy" như quy ước trang (UI_GUIDE mục 5) — thực tế không xảy ra vì trang đã chặn đơn người
  khác.

### 4d. Desktop — tab "Giấy mời phỏng vấn" phía HR (Q9)

Giấy mời hiệu lực ≥ 2 khung đã chốt (thay vị trí câu "Ứng viên chưa chọn khung giờ…" của H12 4c-1):

```
│ Giấy mời hiện tại                                 [Gửi lại giấy mời]     │
│ Gửi lúc 09:00 09/10/2026                                                 │
│ Ứng viên đã chọn lúc 10:15 14/10/2026                                    │
│                                                                          │
│ Khung giờ (giờ Việt Nam)                                                 │
│ 1. 09:00 13/10/2026 · Tầng 3, 45 Bạch Đằng, …                            │
│ 2. 09:00 23/10/2026 · Phòng họp Sông Hàn, tầng 5        [✓ Đã chốt]      │
│ 3. 14:00 24/10/2026 · https://meet.example.com/lan-u10-test              │
```

Thẻ "Giấy mời trước đó" (H12 4c-2): thêm cùng dòng ngay dưới dòng "Gửi lúc … · Đã được thay bằng …" khi giấy mời đó ≥ 2
khung đã chốt (O2). Giấy mời 1 khung: không dòng này (giữ H12). Chưa chốt: câu "Ứng viên chưa chọn khung giờ…" của H12 giữ
nguyên.

Hộp thoại "Gửi lại giấy mời" (H12 4b): chỉ đổi câu lưu ý khi đã có khung chốt (R-H2, mục 7).

### 4e. Điện thoại (375px) — khối ứng viên, chọn được

```
┌──────────────────────────────┐
│ Giấy mời phỏng vấn           │
│ Chọn một khung giờ phù hợp   │
│ rồi bấm "Xác nhận khung      │
│ giờ". Nếu không khung nào …  │
│ Khung giờ (giờ Việt Nam)     │
│ ( ) 09:00 13/10/2026 Đã qua  │
│     Địa điểm: Tầng 3, 45 …   │
│ (•) 09:00 23/10/2026         │
│     Địa điểm: Phòng họp Sông │
│     Hàn, tầng 5              │
│ ( ) 14:00 24/10/2026         │
│     Địa điểm: https://meet.e │
│     xample.com/lan-u10-test  │
│ ┌──────────────────────────┐ │
│ │   Xác nhận khung giờ     │ │
│ └──────────────────────────┘ │
│ Thư mời phỏng vấn …          │
└──────────────────────────────┘
```

Hộp xác nhận gần hết chiều ngang, nút "Huỷ"/"Xác nhận" cùng hàng (như `RevokeTalentPoolConsentDialog`).

## 5. Component

### 5a. File mới (chốt tên — `REQUIREMENT.md` mục 9 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/features/interviewinvitation/InterviewSlotPicker.tsx` | Phần chọn 4a: `<fieldset>` radio, nhãn "Đã qua", lỗi "chưa chọn", nút "Xác nhận khung giờ", mở `ConfirmInterviewSlotDialog` |
| `frontend/src/features/interviewinvitation/ConfirmInterviewSlotDialog.tsx` | Hộp xác nhận 4c, gọi mutation C1 |

### 5b. File có sẵn bị sửa

| File | Thay đổi |
|---|---|
| `frontend/src/features/interviewinvitation/InterviewInvitationDetails.tsx` | Nhận thêm prop `status: ApplicationStatus`; chọn hiển thị theo bảng mục 6 (4a / 4b-1 / 4b-2 / 4b-3 / 4b-4) |
| `frontend/src/features/candidateApplicationDetail/InvitationSection.tsx` | Truyền `status` xuống `InterviewInvitationDetails`; `h2` thêm `tabIndex={-1}` + ref để nhận focus sau khi xác nhận. Quy tắc gọi API, ẩn khối khi 404, nút "Thử lại" giữ nguyên (FR-U08 R-T3…R-T7) |
| `frontend/src/features/interviewinvitation/api.ts` | `confirmInterviewSlotRequest(applicationId, slotId)` → `POST /candidates/applications/{id}/interview-invitation/confirm-slot` |
| `frontend/src/features/interviewinvitation/queries.ts` | `useConfirmInterviewSlotMutation`: thành công → `setQueryData` khoá `['interview-invitation', id]` bằng response (DTO I4) rồi `invalidateQueries` khoá đó; lỗi 409/404 → làm mới khoá đó; `SLOT_SELECTION_NOT_ALLOWED` → làm mới thêm `candidateApplicationDetailKey(id)` |
| `frontend/src/features/interviewinvitation/types.ts` | `ConfirmInterviewSlotRequest { slotId: string }` |
| `frontend/src/features/interviewinvitation/invitationLabels.ts` | Thêm khoá mục 7 (phía ứng viên, hộp xác nhận, dòng HR); thay `candidateUnconfirmed` (R-U2); sửa `resendConfirmedNotice` (R-H2). Không đổi khoá nào khác của H12 |
| `frontend/src/features/applicationDetail/InterviewInvitationTab.tsx` | Dòng "Ứng viên đã chọn lúc …" (4d) |
| `frontend/src/features/interviewinvitation/PreviousInvitationCard.tsx` | Dòng "Ứng viên đã chọn lúc …" trong thẻ cũ (4d, O2) |
| `frontend/src/features/notifications/types.ts` | Thêm `'INTERVIEW_SLOT_CONFIRMED'` vào `NotificationType` |

Không sửa: `InterviewSlotList.tsx` (dùng nguyên cho 4b), `ConfirmedSlotMark.tsx`, `InterviewInvitationDialog.tsx` (chỉ đổi
chuỗi qua `invitationLabels.ts`), `NotificationDropdown.tsx`, `NotificationList.tsx` (thông báo mới là một dòng như các
loại cũ), `ApplicationActionBar.tsx`, `features/messageDraft/*`, `components/ui`.

### 5c. Dùng lại có sẵn

| Có sẵn | Cách dùng |
|---|---|
| `Dialog`, `Button` (`components/ui`) | Hộp xác nhận, nút |
| `InterviewSlotList`, `ConfirmedSlotMark` (H12) | 4b-1, 4b-3, 4b-4 |
| `formatSlotTime` (`invitationLabels.ts:69-75`) | Giờ khung, giờ Việt Nam |
| `formatDateTimeVi` (`lib/date.ts`) | "lúc {confirmedAt}" |
| `extractErrorMessage` (`lib/httpError`) | Câu backend trong hộp thoại, fallback mục 7 |
| Icon `CalendarClock` (lucide, như `InterviewSlotList.tsx:37`) | Đầu mỗi dòng khung |

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Câu hướng dẫn chọn, câu "Bạn đã chọn …", câu "Các khung giờ … đã qua", dòng HR "Ứng viên đã chọn lúc …" | — | `text-m3-on-surface` | Câu trạng thái, không phải lỗi (như câu Q8 của H12) |
| Radio | `m3-surface` | `accent-m3-primary`; viền mặc định của trình duyệt cho `<input type="radio">` | Không dùng `m3-outline-variant` làm viền (UI_GUIDE mục 6) |
| Dòng khung chọn được — hover | `hover:bg-m3-primary/8` | — | Lớp phủ 8% (UI_GUIDE 1h) |
| Dòng khung đang chọn | — | viền trái 2px `border-m3-primary` + radio đã chọn | Không đổi màu nền cả dòng sang xanh lá; tín hiệu chính là radio |
| Thời điểm khung | — | `text-m3-on-surface`, `font-medium` | Như H12 |
| Địa điểm | — | `text-m3-on-surface-variant` | Chỉ trên `m3-surface` (4.83:1) |
| Chữ "Đã qua" | — | `text-m3-on-surface-variant` | Chữ, không màu cảnh báo/đỏ; không opacity |
| Radio khoá (khung đã qua) | — | `disabled` của trình duyệt | Chữ của dòng giữ nguyên màu — không làm mờ cả dòng |
| Lỗi "Vui lòng chọn một khung giờ." | `m3-surface` | `text-m3-error` | 4.74:1 trên nền trắng |
| Nút "Xác nhận khung giờ", "Xác nhận" | `bg-m3-primary` | `text-m3-on-primary` | Nút chính |
| Nút "Huỷ" | — | `variant="outline"` | — |
| Câu lỗi trong hộp thoại | `m3-surface` | `text-m3-error` | Như `RevokeTalentPoolConsentDialog.tsx:72` |
| Dấu "Đã chốt" | `bg-m3-primary-container` | `text-m3-on-primary-container` + icon `Check` | Dùng nguyên `ConfirmedSlotMark` của H12 |

## 6. Trạng thái hiển thị

Điều kiện tính từ `status` (trang chi tiết đơn) và I4 (`confirmedAt`, `slots`); "còn khung chưa qua" = có `slot` với
`new Date(scheduledAt).getTime() > Date.now()` lúc render.

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Đang tải I4 | Khối ứng viên | Câu có sẵn "Đang tải giấy mời..." (giữ) |
| 404 / lỗi khác | Khối ứng viên | Giữ FR-U08 R-T5 (ẩn khối), R-T7 (lỗi + "Thử lại") |
| `INTERVIEW_INVITED`, chưa chốt, còn khung chưa qua | Khối ứng viên | 4a |
| `INTERVIEW_INVITED`, chưa chốt, mọi khung đã qua | Khối ứng viên | 4b-3 |
| Chưa chốt, đơn `HIRED`/`REJECTED` | Khối ứng viên | 4b-4 (O1) |
| Đã chốt, ≥ 2 khung, `INTERVIEW_INVITED` | Khối ứng viên | 4b-1 |
| Đã chốt, ≥ 2 khung, `HIRED`/`REJECTED` | Khối ứng viên | Danh sách chỉ đọc, dấu "Đã chốt", không câu |
| Đã chốt, 1 khung | Khối ứng viên | 4b-2 (H12 4d) |
| Bấm "Xác nhận khung giờ" khi chưa chọn | Phần chọn | Câu lỗi dưới danh sách; focus radio đầu tiên chọn được |
| Hộp xác nhận mở / đang gửi / lỗi / thành công | Hộp thoại | Mục 4c |
| 409 `INTERVIEW_SLOT_PASSED` (giờ máy lệch) | Hộp thoại | Câu backend; đóng hộp thoại thấy I4 mới (khung đó đã thành "Đã qua" nếu giờ máy đúng) |
| 409 `INTERVIEW_INVITATION_CONFLICT` (HR vừa gửi lại) | Hộp thoại | Câu backend; đóng hộp thoại thấy giấy mời mới |
| 409 `INTERVIEW_SLOT_ALREADY_CONFIRMED` (tab khác vừa chọn) | Hộp thoại | Câu backend; đóng hộp thoại thấy 4b-1 |
| 409 `SLOT_SELECTION_NOT_ALLOWED` (HR vừa đổi trạng thái / đã rút ở tab khác) | Hộp thoại | Câu backend; đóng hộp thoại thấy badge trạng thái mới, khối theo bảng này |
| HR: hiệu lực ≥ 2 khung đã chốt | Tab HR | 4d, dòng "Ứng viên đã chọn lúc …" |
| HR: hiệu lực chưa chốt / 1 khung | Tab HR | Giữ H12 |
| HR: thông báo mới | Chuông | Một dòng như các loại cũ |

Không có trạng thái "AI đang xử lý": FR-U10 không gọi AI.

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Hướng dẫn chọn (thay `candidateUnconfirmed` của H12) | "Chọn một khung giờ phù hợp rồi bấm "Xác nhận khung giờ". Nếu không khung nào phù hợp, hãy nhắn cho nhà tuyển dụng qua tab Trao đổi." |
| Mọi khung đã qua (Q6) | "Các khung giờ trong giấy mời đã qua. Hãy nhắn cho nhà tuyển dụng qua tab Trao đổi để sắp xếp lịch khác." |
| Đã chọn (ứng viên) | "Bạn đã chọn khung giờ này lúc {formatDateTimeVi(confirmedAt)}. Muốn đổi lịch, hãy nhắn cho nhà tuyển dụng qua tab Trao đổi." |
| Khung đã qua | "Đã qua" |
| Nút chọn | "Xác nhận khung giờ" |
| Lỗi chưa chọn (frontend) | "Vui lòng chọn một khung giờ." (trùng câu Bean Validation của backend) |
| Hộp xác nhận | Tiêu đề "Xác nhận khung giờ phỏng vấn" · "Bạn chọn khung giờ:" · "{HH:mm dd/MM/yyyy} (giờ Việt Nam)" · "Địa điểm: {địa điểm \| Chưa có dữ liệu}" · "Sau khi xác nhận, bạn không tự đổi được khung giờ. Muốn đổi lịch, bạn cần nhắn cho nhà tuyển dụng qua tab Trao đổi." · nút "Huỷ" / "Xác nhận" · đang gửi "Đang xác nhận..." |
| Lỗi backend (REQUIREMENT mục 4.4 — đúng từng chữ) | "Chỉ chọn được khung giờ khi đơn đang ở trạng thái Đã mời phỏng vấn." · "Giấy mời này đã được chốt khung giờ. Muốn đổi lịch, hãy nhắn cho nhà tuyển dụng qua tab Trao đổi." · "Giấy mời của đơn này vừa được thay đổi ở nơi khác. Vui lòng tải lại trang." · "Khung giờ này đã qua. Vui lòng chọn khung giờ khác." |
| Lỗi fallback | "Xác nhận khung giờ thất bại, vui lòng thử lại." |
| Dòng HR (Q9) | "Ứng viên đã chọn lúc {formatDateTimeVi(confirmedAt)}" |
| Lưu ý gửi lại khi đã chốt (sửa `resendConfirmedNotice`, R-H2) | "Giấy mời hiện tại đã chốt khung {HH:mm dd/MM/yyyy}. Khung này sẽ không còn hiệu lực sau khi gửi lại." |
| Thông báo HR (backend, REQUIREMENT R-N3) | Tiêu đề "Ứng viên đã chọn lịch phỏng vấn" · nội dung một dòng theo R-N3 |

Định dạng giờ khung `HH:mm dd/MM/yyyy`, giờ Việt Nam (`formatSlotTime`) ở mọi nơi hiện khung — khớp email (R-N4). "lúc
{confirmedAt}" là thời điểm thao tác, dùng `formatDateTimeVi` (giờ trình duyệt) như "Gửi lúc" của H12.

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Khối ứng viên | Hộp xác nhận | Tab HR |
|---|---|---|---|
| Expanded (≥ `lg`) | 4a; nút "Xác nhận khung giờ" `w-auto` | `sm:max-w-md` | 4d |
| Medium (`sm`–`lg`) | Như expanded; địa điểm tự xuống dòng | Như expanded | Như H12 |
| Compact (< `sm`, 375px) | 4e; nút `w-full`; mỗi dòng khung (label) cao ≥ 48px (UI_GUIDE 1h) | Gần hết chiều ngang; hai nút cùng hàng | Như H12 4f; dòng "Ứng viên đã chọn lúc …" dưới "Gửi lúc" |

- Không cuộn ngang toàn trang ở 375px. Địa điểm `break-all` cho link không khoảng trắng (như `InterviewSlotList.tsx:41`).
- Không thanh thao tác `fixed`/`sticky`.

## 9. Khả năng tiếp cận

- Phần chọn: `<fieldset>` + `<legend>` "Khung giờ (giờ Việt Nam)"; radio gốc cùng `name`, điều hướng bằng phím mũi tên theo
  hành vi trình duyệt. Mỗi radio nằm trong `<label>` chứa thời điểm + địa điểm → tên truy cập đầy đủ.
- Khung đã qua: radio `disabled`; chữ "Đã qua" nằm trong `<label>` (đọc được), không chỉ bằng màu.
- Lỗi "chưa chọn": `role="alert"`, gắn `aria-describedby` vào `<fieldset>`; `aria-invalid` trên fieldset khi có lỗi.
- Hộp xác nhận: `DialogTitle`/`DialogDescription` (Radix tự gắn `aria-labelledby`/`aria-describedby`); focus đầu vào nút
  "Huỷ" (thao tác không đảo ngược — tránh Enter vô ý xác nhận); lỗi `role="alert"`.
- Sau khi xác nhận thành công: focus về `h2` "Giấy mời phỏng vấn" (`tabIndex={-1}`), vùng `aria-live="polite"` có sẵn đọc
  câu "Bạn đã chọn …".
- Dấu "Đã chốt": giữ `ConfirmedSlotMark` (chữ + icon `aria-hidden`).
- Tương phản: chỉ các cặp đã đo ở UI_GUIDE mục 1c/6 và bảng 5d; không cặp mới.

## 10. Không được làm

- Không chốt khung ngay khi bấm radio; luôn qua hộp xác nhận (Q11).
- Không nút "Đổi khung", "Huỷ lựa chọn"; không câu hứa hẹn "có thể đổi sau" (Q2).
- Không hiện phần chọn cho giấy mời 1 khung, giấy mời đã chốt, hay đơn không `INTERVIEW_INVITED` (R-U1).
- Không ẩn khung đã qua khỏi danh sách; không làm mờ (opacity), gạch ngang, hay tô đỏ/vàng khung đã qua.
- Không tô xanh lá khung đã chọn; không dùng màu làm tín hiệu duy nhất (CLAUDE.md §8).
- Không sắp xếp lại khung theo thời gian (thứ tự HR gửi — H12 R-S7).
- Không thêm component vào `components/ui`; không sửa `InterviewSlotList.tsx`, `NotificationDropdown.tsx`,
  `NotificationList.tsx`, `ApplicationActionBar.tsx`, `features/messageDraft/*`.
- Không sửa câu nào của H12 ngoài `candidateUnconfirmed` (thay theo Q8 của H12) và `resendConfirmedNotice` (R-H2).
- Không đổi badge trạng thái đơn hay thêm mốc vào tab "Lịch sử" khi chọn khung (R-S1).
- Không biến địa điểm dạng URL thành thẻ `<a>` (H12 B4); không render HTML; không `dangerouslySetInnerHTML`.
- Không hiện giấy mời cũ phía ứng viên (H12).
- Không sao chép logo, tên thương hiệu hay CSS của trang tuyển dụng nào (CLAUDE.md §8).
