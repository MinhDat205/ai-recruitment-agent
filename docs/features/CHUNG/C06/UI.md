# FR-C06 — Giao diện

> Trạng thái: ĐÃ DUYỆT 10/10/2026

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 2 (điều hướng), 3 (component), 4 (ràng buộc), 5 (rỗng/
tải/lỗi), 6 (tương phản). Mã quy tắc `R-…` và mã endpoint `M1`–`M5` là của `REQUIREMENT.md` cùng thư mục. File
này không chép lại quy tắc chung, chỉ chốt cách áp cho các màn hình của FR-C06.

## 1. Trạng thái

> Trạng thái: ĐÃ DUYỆT 10/10/2026

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| ★`/hr/applications/:id` | **Màn hình có sẵn bị mở rộng** (không phải màn hình mới của FR-C06): thêm tab "Trao đổi" vào cuối; 3 tab cũ và thanh thao tác không đổi | ★Mới ở UI_GUIDE (của FR-H09, đã hoàn thành) | FR-C06 (R-P3) |
| ★`/candidate/applications/:id` | **Màn hình có sẵn bị mở rộng** (không phải màn hình mới của FR-C06): thêm tab "Trao đổi" vào cuối; 2 tab cũ và nút Rút đơn không đổi | ★Mới ở UI_GUIDE (của FR-U08, đã hoàn thành) | FR-C06 (R-P3) |
| ★`/hr/messages` | "Tin nhắn" — hộp thư phía HR | ★Mới | FR-C06 |
| ★`/candidate/messages` | "Tin nhắn" — hộp thư phía ứng viên | ★Mới | FR-C06 |
| Mọi trang HR và ứng viên | Thanh điều hướng thêm mục "Tin nhắn" | Hiện có | FR-C06 |

- Cả 4 route đã có trong bảng UI_GUIDE mục 7 (dòng 341, 342, 357, 359); không thêm route ngoài bảng.
- Tab mới có khoá `messages`: `?tab=messages`. Cách đọc `?tab=` giữ nguyên (`HrApplicationDetailPage.tsx:16-21`,
  `CandidateApplicationDetailPage.tsx:16-21`): chỉ thêm `messages` vào `VALID_TABS`; giá trị lạ vẫn về tab mặc
  định cũ (`cv` / `info`).
- `/hr/messages` nằm trong nhóm `ProtectedRoute(HR)` + `RequireCompany` (`App.tsx:83-98`); `/candidate/messages`
  nằm trong nhóm `ProtectedRoute(CANDIDATE)` + `RequireCandidateProfileOnboarding` (`App.tsx:60-79`).
- Hộp thư có `?page=` (bắt đầu từ 0, giá trị lạ → 0).

## 3. Điểm vào

- **Tab "Trao đổi":** bấm tab ở trang đơn; bấm một dòng ở hộp thư; bấm thông báo `NEW_MESSAGE` ở chuông
  (`link` có sẵn `?tab=messages`, R-N3); bấm liên kết trong email; mở thẳng URL.
- **Hộp thư:** mục "Tin nhắn" ở thanh điều hướng (HR: sau "Ứng viên"; ứng viên: sau "Đơn ứng tuyển"); mở thẳng
  URL.
- Trang "Xem tất cả" thông báo chưa điều hướng theo `link` (nợ có sẵn, ngoài phạm vi).
- Rời tab: đổi tab khác hoặc rời trang thì dừng tự tải lại; nội dung đang soạn dở **không** được giữ.

## 4. Bố cục (khung ASCII)

### 4a. Tab "Trao đổi" — desktop (≥ `lg`), đơn gửi được tin

Nằm trong `TabsContent value="messages"` của thẻ trang có sẵn (`Card` trắng `max-w-5xl`), cùng `p-4 sm:p-6`
như các tab cũ. Ví dụ là phía HR; phía ứng viên giống hệt, chỉ khác nhãn bên kia ("Nhà tuyển dụng").

```
┌──────────────┬────────────┬──────────┬──────────┐
│ CV & điểm    │ Giải thích │ Lịch sử  │ Trao đổi │
└──────────────┴────────────┴──────────┴──────────┘
├──────────────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Ứng viên · 09:12 08/10/2026                                          │ │
│ │ ┌──────────────────────────────────────────┐                         │ │
│ │ │ Chào anh/chị, em muốn hỏi về tiến độ     │                         │ │
│ │ │ xử lý hồ sơ ạ.                           │                         │ │
│ │ └──────────────────────────────────────────┘                         │ │
│ │                                                                      │ │
│ │                                          Bạn · 10:30 08/10/2026      │ │
│ │                         ┌──────────────────────────────────────────┐ │ │
│ │                         │ Chào bạn, hồ sơ đang được xem xét.       │ │ │
│ │                         │ Bạn gửi giúp bản scan bằng cấp nhé.      │ │ │
│ │                         └──────────────────────────────────────────┘ │ │
│ │                                                                      │ │
│ │ Ứng viên · 14:05 08/10/2026                                          │ │
│ │ ┌──────────────────────────────────────────┐                         │ │
│ │ │ Em gửi ạ.                                │                         │ │
│ │ │ ┌──────────────────────────────────────┐ │                         │ │
│ │ │ │ [FileText] bang-cap.pdf · 1.2 MB  [↓]│ │                         │ │
│ │ │ └──────────────────────────────────────┘ │                         │ │
│ │ └──────────────────────────────────────────┘                         │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Nhập tin nhắn…                                                       │ │
│ │                                                                      │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ [Paperclip] Đính kèm tệp    PDF, DOCX, PNG, JPEG, WEBP · tối đa 5MB      │
│                                                    0/4000       [ Gửi ]  │
└──────────────────────────────────────────────────────────────────────────┘
```

- **Vùng tin:** khung có viền `m3-outline-variant`, cao tối đa `60vh`, cuộn dọc **bên trong khung**; tin cũ ở
  trên, mới ở dưới. Tin của mình căn phải, tin bên kia căn trái; mỗi bong bóng rộng tối đa 75% khung.
- **Dòng trên mỗi bong bóng** (nằm ngoài bong bóng, trên nền thẻ trắng): nhãn người gửi + thời điểm.
- **Tệp:** thẻ tệp nằm trong bong bóng, dưới phần chữ (nếu có); tin chỉ có tệp thì bong bóng chỉ chứa thẻ tệp.
- **Khung soạn:** dưới vùng tin, không `fixed`/`sticky`.

Khung soạn khi đã chọn tệp:

```
│ [Paperclip] Đính kèm tệp    PDF, DOCX, PNG, JPEG, WEBP · tối đa 5MB      │
│ ┌────────────────────────────────────────┐                               │
│ │ [FileText] bang-cap.pdf · 1.2 MB   [X] │                               │
│ └────────────────────────────────────────┘                               │
│                                                    0/4000       [ Gửi ]  │
```

Khung soạn khi có lỗi (chữ đang nhập và tệp đã chọn còn nguyên):

```
│ [AlertCircle] Tệp đính kèm vượt quá 5MB                                  │
│                                                    0/4000       [ Gửi ]  │
```

### 4b. Tab "Trao đổi" — trạng thái khác

```
Rỗng, gửi được (R-P2):
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │        Chưa có tin nhắn nào. Hãy gửi tin đầu tiên ở bên dưới.        │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ (khung soạn như 4a)                                                      │

Đơn đã rút, có tin (canSend = false):
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ … các tin …                                                          │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ Đơn đã rút, cuộc trao đổi chỉ còn xem.                                   │
│ (không có khung soạn)                                                    │

Đơn đã rút, chưa có tin:
│ Chưa có tin nhắn nào.                                                    │
│ Đơn đã rút, cuộc trao đổi chỉ còn xem.                                   │

Quá 200 tin (olderMessagesHidden = true) — dòng đầu vùng tin:
│ │ Chỉ hiển thị 200 tin gần nhất.                                       │ │

Đã dừng tự cập nhật (R-A2) — dòng giữa vùng tin và khung soạn:
│ Đã tạm dừng tự cập nhật.  [Tải lại]                                      │
```

### 4c. Tab "Trao đổi" — điện thoại (375px)

Chiều rộng nội dung: phía ứng viên ≈ 311px (như FR-U08 UI.md 4d); phía HR hẹp hơn 80px do rail
(`HrLayout.tsx:35`, như FR-H09 UI.md 4d).

```
┌──────────────────────────────┐
│ (TabsList cuộn ngang)        │
├──────────────────────────────┤
│ ┌──────────────────────────┐ │
│ │ Nhà tuyển dụng           │ │
│ │ 10:30 08/10/2026         │ │
│ │ ┌──────────────────────┐ │ │
│ │ │ Chào bạn, hồ sơ đang │ │ │
│ │ │ được xem xét.        │ │ │
│ │ └──────────────────────┘ │ │
│ │                      Bạn │ │
│ │         14:05 08/10/2026 │ │
│ │   ┌────────────────────┐ │ │
│ │   │ Em gửi ạ.          │ │ │
│ │   │ ┌────────────────┐ │ │ │
│ │   │ │ bang-cap.pdf   │ │ │ │
│ │   │ │ 1.2 MB     [↓] │ │ │ │
│ │   │ └────────────────┘ │ │ │
│ │   └────────────────────┘ │ │
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ Nhập tin nhắn…           │ │
│ └──────────────────────────┘ │
│ [Paperclip] Đính kèm tệp     │
│ PDF, DOCX, PNG, JPEG, WEBP · │
│ tối đa 5MB                   │
│ 0/4000                       │
│ ┌──────────────────────────┐ │
│ │           Gửi            │ │
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

Bong bóng rộng tối đa 85% khung; nhãn người gửi và thời điểm xuống hai dòng; nút "Gửi" `w-full`.

### 4d. Hộp thư — desktop

Phía HR: trong `HrLayout title="Tin nhắn"`, một `Card` trắng `mx-auto max-w-5xl`. Phía ứng viên: khung trang
như `CandidateNotificationsPage.tsx:7-8` (`mx-auto max-w-[1200px] px-4 py-8 md:px-6`), `h1` "Tin nhắn", rồi
một `Card` trắng.

```
Phía HR
┌──────────────────────────────────────────────────────────────────────────┐
│ Tin nhắn                                                                 │
├──────────────────────────────────────────────────────────────────────────┤
│ Trần Bảo Ngọc                     [Đã mời phỏng vấn]   09:12 08/10/2026  │
│ Chuyên viên Chăm sóc khách hàng                                          │
│ Em muốn hỏi thêm về lịch phỏng vấn ạ.                              [ 1 ] │
│ ──────────────────────────────────────────────────────────────────────── │
│ Quốc Huy                          [Đã mời phỏng vấn]   16:40 07/10/2026  │
│ Nhân viên Hành chính văn phòng                                           │
│ Bạn: Hẹn gặp bạn vào buổi phỏng vấn nhé.                                 │
│ ──────────────────────────────────────────────────────────────────────── │
│ Quốc Huy                          [Đã rút đơn]         11:02 05/10/2026  │
│ Chuyên viên Pháp chế                                                     │
│ Bạn: [Paperclip] Tệp đính kèm                                            │
├──────────────────────────────────────────────────────────────────────────┤
│                         [<]  Trang 1 / 2  [>]                            │
└──────────────────────────────────────────────────────────────────────────┘

Phía ứng viên — dòng 1 là TÊN CÔNG TY, không có tên HR
│ Công ty TNHH Thử Nghiệm Ánh Dương [Đã mời phỏng vấn]   16:40 07/10/2026  │
│ Nhân viên Hành chính văn phòng                                           │
│ Hẹn gặp bạn vào buổi phỏng vấn nhé.                                [ 1 ] │
```

Mỗi dòng là **một** liên kết phủ cả dòng, tới `/hr/applications/{id}?tab=messages` hoặc
`/candidate/applications/{id}?tab=messages`. Dòng có tin chưa đọc: tên bên kia đậm hơn + badge số ở cuối dòng
ba.

### 4e. Hộp thư — điện thoại (375px)

```
┌──────────────────────────────┐
│ Tin nhắn                     │
├──────────────────────────────┤
│ Trần Bảo Ngọc          [ 1 ] │
│ Chuyên viên Chăm sóc khách   │
│ hàng                         │
│ [Đã mời phỏng vấn]           │
│ Em muốn hỏi thêm về lịch     │
│ phỏng vấn ạ.                 │
│ 09:12 08/10/2026             │
│ ──────────────────────────── │
│ …                            │
└──────────────────────────────┘
```

### 4f. Thanh điều hướng

```
HR (≥ lg, drawer)        HR (< lg, rail)       Ứng viên (≥ md, tab ngang)
┌──────────────────┐     ┌────────┐            Việc làm · Bảng tin · Hồ sơ và CV · Đơn ứng tuyển · Tin nhắn
│ Dashboard        │     │ [icon] │
│ Tin tuyển dụng   │     │ Tin    │            Ứng viên (< md, sheet): thêm dòng "Tin nhắn" sau "Đơn ứng tuyển"
│ Ứng viên         │     │ nhắn   │
│ Tin nhắn         │     └────────┘
│ Hồ sơ công ty    │
└──────────────────┘
```

## 5. Component

### 5a. File mới (chốt tên — `REQUIREMENT.md` mục 7.3 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/features/messages/types.ts` | Kiểu `MessageThread`, `Message`, `MessageAttachment`, `ConversationHr`, `ConversationCandidate`, `MessageSide = 'hr' \| 'candidate'` |
| `frontend/src/features/messages/api.ts` | Gọi M1–M5; mọi hàm nhận `side` để chọn tiền tố `/hr/…` hoặc `/candidates/…`. M2 gửi `FormData` |
| `frontend/src/features/messages/queries.ts` | Hook M1 (tải lại 10 giây), M5 (30 giây), mutation M2, M3; khoá query theo `side` + `applicationId`; không thử lại với 4xx |
| `frontend/src/features/messages/MessagesTab.tsx` | Tab "Trao đổi": props `side`, `applicationId`, `onReadOnlyConflict`. Ghép vùng tin + khung soạn + các trạng thái 4b; gọi M3 theo R-A3 |
| `frontend/src/features/messages/MessageList.tsx` | Vùng tin cuộn bên trong; tự cuộn xuống cuối (mục 6) |
| `frontend/src/features/messages/MessageBubble.tsx` | Một tin: nhãn người gửi, thời điểm, chữ, thẻ tệp |
| `frontend/src/features/messages/AttachmentChip.tsx` | Thẻ tệp: icon, tên (đã ép đuôi theo R-F6, do backend trả), dung lượng, nút tải (M4, tải dạng blob — **chép mẫu** của `features/scoring/downloadApplicationResume.ts:14-24`, **không import** file đó vì R-C2 cấm `features/messages` import `features/scoring`) hoặc nút bỏ chọn khi ở khung soạn |
| `frontend/src/features/messages/MessageComposer.tsx` | Ô nhập, chọn tệp, đếm ký tự, nút Gửi, câu lỗi |
| `frontend/src/features/messages/ConversationList.tsx` | Danh sách hộp thư dùng chung hai phía: props `side`; phân trang |
| `frontend/src/features/messages/messageRules.ts` | Hằng số `MAX_BODY_LENGTH = 4000`, `MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024`, danh sách đuôi tệp cho `accept` |
| `frontend/src/pages/HrMessagesPage.tsx` | `HrLayout` + `Card` + `ConversationList side="hr"` |
| `frontend/src/pages/CandidateMessagesPage.tsx` | `CandidateLayout` + `h1` + `Card` + `ConversationList side="candidate"` |
| `frontend/src/lib/fileSize.ts` | `formatFileSize`, chuyển từ `ResumeList.tsx:24-35` (R-C3) |
| `frontend/src/lib/useStallGuardedPolling.ts` | Chuyển từ `features/notifications/queries.ts:26-44` (R-C4) |

### 5b. File có sẵn bị sửa

| File | Sửa gì |
|---|---|
| `pages/HrApplicationDetailPage.tsx` | `VALID_TABS` thêm `messages`; thêm `TabsTrigger` "Trao đổi" cuối `TabsList` (`:135-139`) và `TabsContent` chứa `MessagesTab side="hr"`. `onReadOnlyConflict` làm mới E1 (`applicationDetailKey`) |
| `pages/CandidateApplicationDetailPage.tsx` | Tương tự với `side="candidate"` (`:142-145`); `onReadOnlyConflict` làm mới E1 (`candidateApplicationDetailKey`) |
| `components/layout/HrLayout.tsx` | `NAV_ITEMS` (`:20-25`) thêm `{ label: 'Tin nhắn', shortLabel: 'Tin nhắn', to: '/hr/messages', icon: MessageSquare }` sau "Ứng viên" |
| `components/layout/CandidateLayout.tsx` | `NAV_ITEMS` (`:21-38`) thêm `{ label: 'Tin nhắn', to: '/candidate/messages', isActive: startsWith('/candidate/messages') }` sau "Đơn ứng tuyển" |
| `App.tsx` | Hai route mới trong đúng hai nhóm ở mục 2 |
| `features/notifications/types.ts` | Thêm `'NEW_MESSAGE'` |
| `features/notifications/queries.ts` | Dùng `useStallGuardedPolling` từ `lib/` |
| `features/resumes/ResumeList.tsx` | Dùng `formatFileSize` từ `lib/` |

Không sửa: `components/ui/*`, `ApplicationDetailHeader`, `CandidateApplicationHeader`, các tab cũ,
`NotificationDropdown`, `NotificationList`.

### 5c. Dùng lại có sẵn (không viết lại)

| Có sẵn | Cách dùng |
|---|---|
| `Tabs`, `TabsList`, `TabsTrigger`, `TabsContent` | Cấu hình `TabsList` giữ nguyên (`w-fit max-w-full justify-start overflow-x-auto overflow-y-hidden`) |
| `Textarea` (`components/ui/textarea.tsx`) | Ô nhập tin |
| `Button`, `Card` | Nút "Gửi" kiểu Filled (mặc định); "Đính kèm tệp", "Tải lại", "Thử lại" `variant="outline"`/`ghost` cỡ `sm` |
| `ApplicationStatusBadge` | Badge trạng thái đơn ở hộp thư |
| `formatDateTimeVi` (`lib/date.ts`) | Thời điểm tin và thời điểm tin mới nhất |
| `extractErrorMessage` (`lib/httpError.ts`) | Câu lỗi gửi tin lấy từ `message` của backend |
| `Pagination` (`features/jobs/Pagination.tsx`) | Phân trang hộp thư |
| `lucide-react` | `Paperclip`, `FileText`, `Download`, `X`, `AlertCircle`, `MessageSquare` |

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Khung vùng tin | `bg-m3-surface` | `border border-m3-outline-variant` | Viền chỉ để phân khối |
| Bong bóng tin của mình | `bg-m3-primary-container` | `text-m3-on-primary-container` | 6.23:1 |
| Bong bóng tin bên kia | `bg-m3-surface-container` | `text-m3-on-surface` | Không dùng `m3-on-surface-variant` trong bong bóng này (4.24:1) |
| Nhãn người gửi + thời điểm | (thẻ trắng) | `text-m3-on-surface-variant`, `text-m3-label-md` | Nằm **ngoài** bong bóng, trên `m3-surface` (4.83:1) |
| Thẻ tệp | `bg-m3-surface` | `border border-m3-outline text-m3-on-surface`; dung lượng `text-m3-on-surface-variant` | Nền trắng trong cả hai loại bong bóng để chữ phụ đạt tương phản |
| Ô nhập | — | Viền mặc định của `Textarea` (`border-input` → `m3-outline`) | |
| Dòng gợi ý loại tệp, bộ đếm ký tự | (thẻ trắng) | `text-m3-on-surface-variant` | Bộ đếm vượt 4000: `text-m3-error` (trên `m3-surface`, 4.74:1) |
| Câu lỗi khung soạn, câu lỗi tải tệp | (thẻ trắng) | Icon `AlertCircle` `text-m3-error` + chữ `text-m3-on-surface` | Câu lỗi tải tệp đặt ngoài bong bóng để không cần cặp màu mới trên nền bong bóng |
| Câu trạng thái rỗng / chỉ còn xem / đã dừng / quá 200 tin | (thẻ trắng) | `text-m3-on-surface-variant` | |
| Dòng hộp thư | `bg-m3-surface`; hover `bg-m3-surface-container` | Tên bên kia `text-m3-on-surface` (`font-semibold` khi có tin chưa đọc, `font-medium` khi không); tên tin, đoạn trích, thời điểm `text-m3-on-surface` | Trong dòng có hover đổi nền nên **không** dùng `m3-on-surface-variant`/`m3-primary` (UI_GUIDE mục 6, quy tắc hàng bảng) |
| Badge số tin chưa đọc | `bg-m3-primary` | `text-m3-on-primary`, `rounded-(--radius-badge)` | Không dùng `m3-error` (đỏ) — tin chưa đọc không phải lỗi |
| Phân dòng hộp thư | — | `border-m3-outline-variant` | |

## 6. Trạng thái hiển thị

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Đang tải M1 lần đầu | Tab | Skeleton xám: 3 bong bóng (trái, phải, trái); không spinner toàn trang; chưa hiện khung soạn |
| M1 lỗi mạng/5xx, chưa có dữ liệu | Tab | Icon `AlertCircle` + "Không tải được cuộc trao đổi." + nút "Thử lại" |
| M1 trả 400/403/404 (R-G4) | Tab | "Không tìm thấy cuộc trao đổi." — không khung soạn, dừng tự tải lại |
| Tải lại ngầm lỗi khi đã có dữ liệu | Tab | Giữ nguyên dữ liệu đang hiện, không báo lỗi; chu kỳ sau thử lại |
| Rỗng, `canSend = true` | Tab | Câu rỗng 4b + khung soạn |
| Rỗng, `canSend = false` | Tab | "Chưa có tin nhắn nào." + dòng chỉ còn xem |
| Có tin, `canSend = false` | Tab | Vùng tin + dòng chỉ còn xem; không khung soạn |
| `olderMessagesHidden = true` | Đầu vùng tin | "Chỉ hiển thị 200 tin gần nhất." |
| Đang gửi | Khung soạn | Nút "Gửi" khoá, nhãn "Đang gửi…"; ô nhập và nút đính kèm khoá |
| Gửi thành công | Khung soạn + vùng tin | Xoá chữ và tệp đã chọn; tin mới hiện ở cuối; vùng tin cuộn xuống cuối; focus về ô nhập |
| Gửi lỗi 400/429/mạng | Khung soạn | Câu lỗi (backend `message`, fallback mục 7); chữ và tệp còn nguyên |
| Gửi lỗi 409 `CONVERSATION_READ_ONLY` | Tab + đầu trang | Câu lỗi của backend; làm mới M1 (khung soạn biến mất) và E1 (badge đầu trang cập nhật) |
| Chọn tệp sai đuôi / quá 5MB / 0 byte | Khung soạn | Câu lỗi tương ứng (mục 7); tệp **không** được chọn; chữ còn nguyên |
| Nút "Gửi" khoá | Khung soạn | Khi chữ rỗng/chỉ khoảng trắng **và** không có tệp; khi chữ dài quá 4000; khi đang gửi |
| Đang tải tệp | Thẻ tệp | Nút tải khoá |
| Tải tệp lỗi | Dưới bong bóng, **ngoài** bong bóng (trên nền thẻ trắng) | Icon `AlertCircle` `text-m3-error` + "Tải tệp thất bại, vui lòng thử lại." `text-m3-on-surface` |
| Đã dừng tự cập nhật (R-A2) | Giữa vùng tin và khung soạn | "Đã tạm dừng tự cập nhật." + nút "Tải lại" |
| Tin mới tới khi đang ở cuối vùng tin (cách đáy ≤ 80px) | Vùng tin | Tự cuộn xuống cuối |
| Tin mới tới khi đang cuộn xem tin cũ | Vùng tin | **Không** tự cuộn; không chèn nút hay nhãn |
| Mở tab lần đầu | Vùng tin | Cuộn sẵn ở cuối (tin mới nhất) |
| Đang tải M5 | Hộp thư | Skeleton 3 dòng |
| M5 lỗi | Hộp thư | Icon `AlertCircle` + "Không tải được danh sách tin nhắn." + nút "Thử lại" |
| Hộp thư rỗng | Hộp thư | Phía ứng viên: "Bạn chưa có cuộc trao đổi nào." + liên kết "Xem đơn ứng tuyển" (`/candidate/applications`). Phía HR: "Chưa có cuộc trao đổi nào." + liên kết "Xem danh sách ứng viên" (`/hr/candidates`) |
| Dòng có tin chưa đọc | Hộp thư | Tên bên kia `font-semibold` + badge số |
| Hộp thư đã dừng tự cập nhật | Trên danh sách | "Đã tạm dừng tự cập nhật." + nút "Tải lại" |

Không có trạng thái "AI đang xử lý": FR-C06 không gọi AI. Không có trạng thái "thiếu dữ liệu": mọi field hiển
thị đều bắt buộc có, trừ đoạn trích (tin chỉ có tệp → hiện "Tệp đính kèm").

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Tab | "Trao đổi" |
| Mục điều hướng / tiêu đề hộp thư | "Tin nhắn" (rail HR: "Tin nhắn") |
| Nhãn người gửi — tin của mình | "Bạn" |
| Nhãn người gửi — bên kia, phía HR xem | "Ứng viên" |
| Nhãn người gửi — bên kia, phía ứng viên xem | "Nhà tuyển dụng" |
| Thời điểm | `formatDateTimeVi` (giờ trước ngày, như các trang đơn) |
| Ô nhập (placeholder và nhãn ẩn) | "Nhập tin nhắn…" |
| Nút đính kèm | "Đính kèm tệp" |
| Gợi ý tệp | "PDF, DOCX, PNG, JPEG, WEBP · tối đa 5MB" |
| Bộ đếm | "{n}/4000" |
| Nút gửi | "Gửi" · khi đang gửi "Đang gửi…" |
| Rỗng, gửi được | "Chưa có tin nhắn nào. Hãy gửi tin đầu tiên ở bên dưới." |
| Rỗng, chỉ còn xem | "Chưa có tin nhắn nào." |
| Chỉ còn xem | "Đơn đã rút, cuộc trao đổi chỉ còn xem." |
| Quá 200 tin | "Chỉ hiển thị 200 tin gần nhất." |
| Đã dừng tự cập nhật | "Đã tạm dừng tự cập nhật." · nút "Tải lại" |
| Lỗi tải tab | "Không tải được cuộc trao đổi." · nút "Thử lại" |
| Không tìm thấy (tab) | "Không tìm thấy cuộc trao đổi." |
| Lỗi gửi (fallback khi backend không có `message`) | "Gửi tin nhắn thất bại, vui lòng thử lại." |
| Lỗi tệp — sai loại (kiểm ở trình duyệt theo đuôi, trùng câu backend) | "Định dạng không hợp lệ, chỉ nhận PDF, DOCX, PNG, JPEG hoặc WEBP" |
| Lỗi tệp — quá 5MB (trùng câu backend) | "Tệp đính kèm vượt quá 5MB" |
| Lỗi tệp — 0 byte (trùng câu backend) | "Tệp đính kèm đang trống" |
| Lỗi tải tệp | "Tải tệp thất bại, vui lòng thử lại." |
| Câu backend 400 `MESSAGE_EMPTY` | "Tin nhắn chưa có nội dung" |
| Câu backend 400 `MESSAGE_TOO_LONG` | "Tin nhắn vượt quá 4000 ký tự" |
| Câu backend 409 `CONVERSATION_READ_ONLY` | "Đơn đã rút, không thể gửi thêm tin nhắn" |
| Câu backend 404 `MESSAGE_NOT_FOUND` | "Không tìm thấy tin nhắn" |
| Câu backend 404 `MESSAGE_ATTACHMENT_NOT_FOUND` | "Không tìm thấy tệp đính kèm" |
| Hộp thư — tiền tố tin của mình | "Bạn: " |
| Hộp thư — tin chỉ có tệp | "Tệp đính kèm" (kèm icon `Paperclip`) |
| Hộp thư rỗng (ứng viên) | "Bạn chưa có cuộc trao đổi nào." · liên kết "Xem đơn ứng tuyển" |
| Hộp thư rỗng (HR) | "Chưa có cuộc trao đổi nào." · liên kết "Xem danh sách ứng viên" |
| Lỗi tải hộp thư | "Không tải được danh sách tin nhắn." · nút "Thử lại" |
| Thông báo ở chuông | Theo bảng R-N3 của REQUIREMENT (do backend sinh) |

Chuỗi có sẵn của `ApplicationStatusBadge`, `Pagination` và các tab cũ giữ nguyên.

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Vùng tin | Khung soạn | Hộp thư | Điều hướng |
|---|---|---|---|---|
| Expanded (≥ `lg`) | Bong bóng tối đa 75% khung; nhãn + thời điểm một dòng | Nút đính kèm + gợi ý cùng hàng; bộ đếm và nút "Gửi" căn phải cùng hàng | Dòng 1: tên bên kia, badge trạng thái, thời điểm cùng hàng | HR: drawer có nhãn đầy đủ. Ứng viên: 5 tab ngang |
| Medium (`sm`–`lg`) | Như expanded | Như expanded; gợi ý xuống dòng nếu thiếu chỗ | Như expanded; thời điểm xuống dòng nếu thiếu chỗ | HR: rail (icon + "Tin nhắn"). Ứng viên: tab ngang từ `md`, sheet dưới `md` |
| Compact (< `sm`, 375px) | Bong bóng tối đa 85%; nhãn và thời điểm hai dòng | Xếp dọc; nút "Gửi" `w-full`, **trong luồng trang** | Xếp dọc như 4e; badge số ở cuối dòng tên | HR: rail. Ứng viên: sheet |

- Không cuộn ngang toàn trang ở 375px; chỉ `TabsList` được cuộn ngang. Vùng tin chỉ cuộn dọc.
- Nội dung tin, tên tệp, tên tin, tên công ty, đoạn trích tự ngắt (`break-words`); tên tệp dài cắt bằng
  `truncate` trong thẻ tệp, tên đầy đủ ở `title`.
- **Thanh điều hướng ứng viên có 5 mục:** ở 768px, 1024px, 1280px không được tràn hay xuống dòng. Nếu tràn ở
  `md`–`lg` thì ẩn họ tên người dùng ở khoảng đó (`hidden lg:inline` cho `CandidateLayout.tsx:90`) — đây là
  thay đổi **duy nhất** được phép với phần còn lại của header; không đổi khoảng cách, cỡ chữ hay logo.
- Không thanh dưới đáy cố định ở bất kỳ khổ nào.

## 9. Khả năng tiếp cận

- Vùng tin: `role="log"`, `aria-live="polite"`, `aria-label="Nội dung trao đổi"`; cuộn được bằng bàn phím
  (`tabIndex={0}`).
- Mỗi tin là một `article`; nhãn người gửi và thời điểm là chữ thật, không chỉ dựa vào vị trí trái/phải hay màu
  bong bóng để biết ai gửi.
- Ô nhập có `label` ẩn "Nhập tin nhắn" (`sr-only`), không chỉ dựa vào placeholder. Bộ đếm và câu lỗi gắn vào ô
  nhập bằng `aria-describedby`; câu lỗi có `role="alert"`.
- `<input type="file">` ẩn bằng `sr-only` (không `display: none`), mở bằng nút "Đính kèm tệp" là `button` thật.
- Nút chỉ có icon phải có `aria-label`: tải tệp "Tải tệp {tên tệp}", bỏ tệp "Bỏ tệp đính kèm".
- Phím: `Enter` xuống dòng; `Ctrl+Enter` (và `Cmd+Enter`) gửi. Sau khi gửi thành công focus về ô nhập.
- Badge số tin chưa đọc có `aria-label="{n} tin chưa đọc"`; dòng có tin chưa đọc còn được phân biệt bằng độ
  đậm của tên, không chỉ bằng màu.
- Mỗi dòng hộp thư là một `Link` duy nhất có tên truy cập được ghép từ tên bên kia + tên tin; vùng chạm cao
  ≥ 48px.
- Tabs: Radix lo `role`/phím mũi tên — không tự viết lại. Một `h1` duy nhất mỗi trang (trang đơn: giữ `h1` có
  sẵn; hộp thư: "Tin nhắn").
- Tương phản: chỉ dùng các cặp trong bảng 5d, đều đã đo ở UI_GUIDE mục 1c/6; không có cặp mới cần đo.

## 10. Không được làm

- Không render nội dung tin bằng HTML; không tự biến URL thành liên kết; không khối "Do AI tạo" (R-G1).
- Không dựng nút "Soạn bằng AI", kể cả khoá hay ẩn sẵn (R-P6, thuộc FR-C07).
- Không hiện "Đã xem"/dấu tick đã đọc cho bên gửi (R-R5); không chỉ báo "đang nhập".
- Không hiện họ tên hay email của HR cho ứng viên — chỉ tên công ty và nhãn "Nhà tuyển dụng" (R-G2).
- Không hiện điểm số, thứ hạng, tiêu chí, giải thích AI ở tab "Trao đổi" hay hộp thư của bất kỳ phía nào.
- Không xem trước ảnh/PDF trong luồng tin; không mở tệp `inline` ở tab mới — chỉ tải về (R-F10).
- Không nút sửa/xoá/thu hồi tin (R-P5).
- Không số tin chưa đọc trên mục điều hướng "Tin nhắn" hay trên nhãn tab "Trao đổi" (REQUIREMENT mục 6).
- Không giao diện hai cột đọc tin ngay trong hộp thư (R-P4); không gọi M3 từ hộp thư (R-I4).
- Không WebSocket/SSE; không tải lại khi tab trình duyệt ẩn; không tải lại vô hạn (R-A).
- Không dùng màu đỏ cho badge tin chưa đọc; không màu xanh/đỏ cho badge trạng thái đơn.
- Không nút `fixed`/`sticky` ở đáy màn hình điện thoại.
- Không đổi tab mặc định, thứ tự hay nội dung các tab cũ của hai trang đơn (R-P3).
- Không viết hai bộ component riêng cho HR và ứng viên; `features/messages` không import từ
  `features/applicationDetail`, `features/candidateApplicationDetail`, `features/scoring` (R-C2).
- Không sửa file trong `components/ui` (R-C5).
- Không toast/snackbar (dự án chưa có component này) — lỗi và kết quả hiện tại chỗ.
- Không sao chép logo, tên thương hiệu hay CSS của ứng dụng nhắn tin hay trang tuyển dụng nào (CLAUDE.md §8).