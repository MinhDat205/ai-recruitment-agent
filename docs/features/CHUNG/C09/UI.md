# FR-C09 — Giao diện

> Trạng thái: ĐÃ DUYỆT (30/10/2026).

- Nhóm: Chung
- Tóm tắt: Ứng viên có đơn bị từ chối ở vòng hồ sơ được gửi một yêu cầu xem xét lại trong 7 ngày; HR mời phỏng vấn hoặc giữ quyết định từ chối
- Phụ thuộc: FR-H07, FR-H12, FR-U08, FR-H09, FR-C03, FR-H16

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 1h (vùng chạm), 3 (Tabs, Dialog, nút, badge trạng thái), 4 (không
phán quyết; ẩn danh; dữ liệu thiếu), 5 (rỗng/tải/lỗi), 6 (tương phản). Mã `R-…`, `C1`, `H1`, `H2`, `I2`, `Q…` là của
`REQUIREMENT.md` cùng thư mục. Khung trang FR-U08, FR-H09 và trang sửa tin không chép lại, chỉ chốt phần FR-C09 thêm.

## 1. Trạng thái

> Trạng thái: ĐÃ DUYỆT (30/10/2026).

## 2. Route (lấy từ mục 7 UI_GUIDE)

Không route mới. Cả bốn là **màn hình cũ bị mở rộng**.

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| `/candidate/applications` | Danh sách đơn: chữ "Đang chờ xem xét lại" cạnh badge "Bị từ chối" | Hiện có | FR-U03; FR-C09 |
| `/candidate/applications/:id` | ★Khối "Xem xét lại" ở tab "Thông tin đơn" + hộp thoại gửi; chữ "Đang chờ xem xét lại" ở đầu trang | Hiện có | FR-U08; FR-C09 |
| `/hr/jobs/:id/edit` | ★Tab "Yêu cầu xem xét lại" (`?tab=reconsideration`) | Hiện có + ★tab mới | FR-H02 …; FR-C09 |
| `/hr/applications/:id` | ★Khối "Yêu cầu xem xét lại" dưới thanh thao tác; câu thanh thao tác đổi khi có yêu cầu `OPEN` | Hiện có | FR-H09; FR-C09 |
| `/candidate/notifications`, `/hr/notifications`, chuông | Hai loại thông báo mới, một dòng, không đổi bố cục | Hiện có | FR-C03; FR-C09 |

Dòng của bốn route trong `UI_GUIDE.md` mục 7 sửa ở đợt cuối (REQUIREMENT mục 10), theo tiền lệ FR-H12 (tab mới trên
route có sẵn).

Thứ tự tab trang sửa tin sau FR-C09: **Thông tin tin tuyển dụng → Mẫu giấy mời phỏng vấn → Rubric chấm điểm → Câu hỏi
sàng lọc → Ứng viên → Yêu cầu xem xét lại**. Chỉ thêm tab cuối, không đổi năm tab cũ (`HrJobEditPage.tsx:480`,
`:509-515`); `?tab=` lạ vẫn về `job`. Tab hiện ở **mọi** trạng thái tin, kể cả khi chưa có yêu cầu nào.

## 3. Điểm vào

- Ứng viên: `/candidate/applications/{id}` từ "Xem chi tiết" ở danh sách, hoặc thông báo `RECONSIDERATION_DECLINED` /
  thông báo đổi trạng thái (link của thông báo).
- HR, tab: `/hr/jobs/{id}/edit?tab=reconsideration` (bấm tab ở trang sửa tin).
- HR, hồ sơ đơn: `/hr/applications/{id}` từ "Mở hồ sơ" ở tab, từ tab "Ứng viên", hoặc thông báo
  `RECONSIDERATION_REQUESTED` (đường duy nhất khi tin đã bị gỡ — REQUIREMENT Q2).

## 4. Bố cục (khung ASCII)

### 4a. Đầu trang và danh sách phía ứng viên

Badge giữ đúng `ApplicationStatusBadge` `REJECTED` (UI_GUIDE mục 3 — không thêm màu, không badge thứ 6). Khi
`reconsideration.state = OPEN` (chi tiết) hoặc `reconsiderationStatus = OPEN` (danh sách), thêm **chữ** ngay sau badge:

```
│ Nhân viên Hành chính văn phòng                                           │
│ Công ty TNHH ABC                                                         │
│ [Bị từ chối] · Đang chờ xem xét lại          Nộp ngày 20/10/2026         │
```

Danh sách (một dòng đơn):

```
│ Nhân viên Hành chính văn phòng · Công ty TNHH ABC                        │
│ [Bị từ chối] · Đang chờ xem xét lại   Nộp ngày 20/10/2026  [Xem chi tiết]│
```

Chữ "Đang chờ xem xét lại" chỉ hiện với `OPEN`. `ACCEPTED`/`DECLINED` không thêm chữ ở danh sách (đơn đã có trạng thái
mới hoặc đã có kết quả).

### 4b. Khối "Xem xét lại" — tab "Thông tin đơn" (FR-U08)

Vị trí: **đầu** tab "Thông tin đơn", trên khối "Giấy mời phỏng vấn". Không render khi `reconsideration` là `null`.
Một thẻ viền `m3-outline-variant`, tiêu đề `h2` "Xem xét lại".

`AVAILABLE`:

```
│ Xem xét lại                                                              │
│ Bạn có thể đề nghị nhà tuyển dụng xem xét lại đơn này một lần.           │
│ Hạn chót: 15:42 29/10/2026.                                              │
│ [Yêu cầu xem xét lại]                                                    │
```

`OPEN`:

```
│ Xem xét lại                                                              │
│ Đang chờ nhà tuyển dụng xem xét lại. Gửi lúc 09:10 24/10/2026.           │
│ Lý do bạn đã gửi                                                         │
│ Tôi có 2 năm làm hành chính ở công ty XYZ, CV                            │
│ có ghi ở mục Kinh nghiệm.                                                │
```

`DECLINED`:

```
│ Xem xét lại                                                              │
│ Nhà tuyển dụng đã xem xét lại và giữ quyết định từ chối lúc 10:00        │
│ 25/10/2026.                                                              │
│ Phản hồi của nhà tuyển dụng                                              │
│ Cảm ơn bạn. Vị trí này cần kinh nghiệm dùng phần mềm kế toán ...         │
│ Lý do bạn đã gửi                                                         │
│ Tôi có 2 năm làm hành chính ...                                          │
```

Không phản hồi: thay khối "Phản hồi…" bằng câu "Nhà tuyển dụng không gửi kèm phản hồi."

`ACCEPTED`: một câu "Nhà tuyển dụng đã xem xét lại và gửi giấy mời phỏng vấn lúc {t}. Xem giấy mời bên dưới." + "Lý do
bạn đã gửi". `EXPIRED`: "Đã quá hạn gửi yêu cầu xem xét lại (hạn chót {t})." `JOB_REMOVED`: "Tin tuyển dụng đã bị gỡ nên
không gửi được yêu cầu xem xét lại." — hai trạng thái này không có nút.

- Lý do không nhập (`reason` `null`): bỏ cả nhãn "Lý do bạn đã gửi" (ứng viên biết mình không nhập; không phải dữ liệu
  thiếu của hệ thống).
- Mọi văn bản `whitespace-pre-wrap break-words`, hiện **đầy đủ**, không cắt.
- Thời điểm dùng `formatDateTimeVi` (`lib/date.ts`), như "Nộp ngày".
- Nút "Yêu cầu xem xét lại" `variant="outline"`, `w-full sm:w-auto`.

### 4c. Hộp thoại "Yêu cầu xem xét lại" (ứng viên)

`Dialog` `sm:max-w-lg`.

```
┌──────────────────────────────────────────────────────────────┐
│ Yêu cầu xem xét lại                                      [X] │
│ Nhà tuyển dụng sẽ nhận được yêu cầu và quyết định mời phỏng  │
│ vấn hoặc giữ quyết định từ chối.                             │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ (i) Mỗi đơn chỉ gửi được một lần. Sau khi gửi, bạn không │ │
│ │     sửa hay huỷ được yêu cầu. Hạn chót: 15:42 29/10/2026.│ │
│ └──────────────────────────────────────────────────────────┘ │
│ Lý do (không bắt buộc)                                       │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │                                                          │ │
│ └──────────────────────────────────────────────────────────┘ │
│ Nhà tuyển dụng sẽ đọc nguyên văn nội dung này.      0/1000   │
│ (lỗi backend nếu có)                                         │
│                               [Huỷ]  [Gửi yêu cầu]           │
└──────────────────────────────────────────────────────────────┘
```

- `Textarea` 5 dòng; bộ đếm `{n}/1000` đếm `String.length` sau khi đổi CRLF → LF (cùng cách backend, REQUIREMENT
  R-V2), như `MessageComposer.tsx:74`, `:264`. Vượt 1000 → nút gửi khoá, câu lỗi dưới ô.
- Bấm "Gửi yêu cầu" khi ô trống vẫn gửi (`reason` `null`).
- Thành công: đóng hộp thoại, làm mới E1 + danh sách đơn; focus về tiêu đề khối "Xem xét lại".

### 4d. Không đổi

Tab "Lịch sử" phía ứng viên và HR tự hiện thêm dòng `Bị từ chối → Đã mời phỏng vấn` từ dữ liệu sẵn có — không sửa
component timeline. Khối giấy mời FR-H12/U10 hiện như mọi đơn được mời.

### 4e. Tab "Yêu cầu xem xét lại" — trang sửa tin (HR)

Nhãn tab: "Yêu cầu xem xét lại" + ` ({openCount})` khi `openCount > 0`. Trong tab: tiêu đề, bộ chọn hai giá trị
(Segmented button, UI_GUIDE mục 3) "Đang chờ" (mặc định) / "Tất cả", rồi danh sách thẻ — mỗi yêu cầu một thẻ (văn bản
dài, không ép vào bảng).

```
│ Yêu cầu xem xét lại                         [ Đang chờ | Tất cả ]        │
│ Ứng viên bị từ chối ở vòng hồ sơ có thể gửi một yêu cầu trong 7 ngày.    │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Trần Bảo Ngọc                                       [Đang chờ]       │ │
│ │ Nộp đơn 18/10/2026 · Bị từ chối 22/10/2026 · Gửi yêu cầu 24/10/2026  │ │
│ │ Lý do                                                                │ │
│ │ Tôi có 2 năm làm hành chính ở công ty XYZ, CV có ghi ở mục ...       │ │
│ │ [Mở hồ sơ]          [Mời phỏng vấn]  [Giữ quyết định từ chối]        │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Ứng viên #3FA91C02                                  [Đang chờ]       │ │
│ │ Nộp đơn …                                                            │ │
│ │ Lý do                                                                │ │
│ │ Tôi là [đã ẩn], email [đã ẩn] ...                                    │ │
```

Bộ lọc "Tất cả" — yêu cầu đã quyết, không có hai nút:

```
│ │ Lê Minh                                            [Đã giữ từ chối]  │ │
│ │ … · Quyết định 25/10/2026                                            │ │
│ │ Lý do …                                                              │ │
│ │ Phản hồi đã gửi ứng viên                                             │ │
│ │ Cảm ơn bạn. Vị trí này cần …                                         │ │
│ │ [Mở hồ sơ]                                                           │ │
```

- Nhãn trạng thái yêu cầu: "Đang chờ" / "Đã mời phỏng vấn" / "Đã giữ từ chối" — chip **trung tính** (5d), luôn là chữ.
- Lý do `null`: "Ứng viên không ghi lý do." Phản hồi `null` (đã `DECLINED`): "Không gửi kèm phản hồi."
- "Mở hồ sơ" là liên kết `/hr/applications/{applicationId}`; "Mời phỏng vấn" mở `InterviewInvitationDialog`
  (`mode='invite'`, `application = {id, candidateName}`, `jobId`, `invalidateQueryKeys` gồm khoá H1 + khoá danh sách tab
  "Ứng viên") — **không** dựng hộp giấy mời thứ hai. "Giữ quyết định từ chối" mở hộp thoại 4g.
- Hai nút cùng `variant="outline"`, cùng mức nhấn mạnh (như thanh thao tác FR-H07) — không nút nào là "chính".
- Đơn ẩn danh: tên là "Ứng viên #…", lý do đã che sẵn ở backend; không thêm banner riêng trong tab (banner ẩn danh là
  của trang hồ sơ đơn).
- Rỗng: "Đang chờ" → "Không có yêu cầu xem xét lại nào đang chờ."; "Tất cả" → "Tin này chưa có yêu cầu xem xét lại
  nào." Không kèm nút (không có hành động hợp lệ — ngoại lệ có chủ đích như FR-H12 UI.md 4c-3).

### 4f. Khối "Yêu cầu xem xét lại" — hồ sơ đơn (FR-H09)

Vị trí: ngay dưới thanh thao tác ở đầu trang (`ApplicationDetailHeader`), trên `TabsList`. Chỉ render khi
`reconsideration` khác `null`. Nội dung = một thẻ như 4e (bỏ "Mở hồ sơ"), dùng **chung** component.

Thanh thao tác (`ApplicationActionBar.tsx:67-73`): đơn `REJECTED` có yêu cầu `OPEN` → câu "Ứng viên đã gửi yêu cầu xem
xét lại. Xử lý ở khối bên dưới." thay "Đơn đã có kết quả cuối. Không còn thao tác nào." (REQUIREMENT M3). Không thêm nút
vào thanh thao tác (Q8).

```
│ Trần Bảo Ngọc  [Bị từ chối]                 Nộp ngày 18/10/2026          │
│ Ứng viên đã gửi yêu cầu xem xét lại. Xử lý ở khối bên dưới.              │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Yêu cầu xem xét lại                                 [Đang chờ]       │ │
│ │ Bị từ chối 22/10/2026 · Gửi yêu cầu 24/10/2026                       │ │
│ │ Lý do …                                                              │ │
│ │                     [Mời phỏng vấn]  [Giữ quyết định từ chối]        │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ [CV & điểm][Giải thích][Hỏi đáp CV][Sàng lọc][Giấy mời phỏng vấn]…       │
```

### 4g. Hộp thoại "Giữ quyết định từ chối" (HR)

```
┌──────────────────────────────────────────────────────────────┐
│ Giữ quyết định từ chối — Trần Bảo Ngọc                   [X] │
│ Đơn vẫn ở trạng thái Bị từ chối. Ứng viên sẽ nhận thông báo  │
│ về kết quả xem xét lại.                                      │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ (i) Phản hồi bên dưới được gửi cho ứng viên và hiện ở    │ │
│ │     trang chi tiết đơn của họ. Đây không phải ghi chú    │ │
│ │     nội bộ. Sau khi xác nhận không sửa được.             │ │
│ └──────────────────────────────────────────────────────────┘ │
│ Phản hồi cho ứng viên (không bắt buộc)                       │
│ ┌──────────────────────────────────────────────────────────┐ │
│ └──────────────────────────────────────────────────────────┘ │
│                                                     0/1000   │
│ (lỗi backend nếu có)                                         │
│                      [Huỷ]  [Giữ quyết định từ chối]         │
└──────────────────────────────────────────────────────────────┘
```

- Cùng quy tắc bộ đếm/khoá như 4c. Không có nút hay chữ "Do AI tạo" — không có soạn nháp AI (REQUIREMENT mục 5).
- Thành công: đóng hộp thoại; làm mới H1, E1 hồ sơ đơn; focus về tiêu đề khối/thẻ vừa đổi.

### 4h. Điện thoại (375px)

```
┌────────────────────────────┐
│ Xem xét lại                │
│ Bạn có thể đề nghị nhà     │
│ tuyển dụng xem xét lại đơn │
│ này một lần.               │
│ Hạn chót: 15:42 29/10/2026.│
│ ┌────────────────────────┐ │
│ │  Yêu cầu xem xét lại   │ │
│ └────────────────────────┘ │
└────────────────────────────┘
┌────────────────────────────┐
│ Trần Bảo Ngọc              │
│ [Đang chờ]                 │
│ Nộp đơn 18/10/2026         │
│ Bị từ chối 22/10/2026      │
│ Gửi yêu cầu 24/10/2026     │
│ Lý do                      │
│ Tôi có 2 năm làm …         │
│ ┌────────────────────────┐ │
│ │     Mời phỏng vấn      │ │
│ └────────────────────────┘ │
│ ┌────────────────────────┐ │
│ │ Giữ quyết định từ chối │ │
│ └────────────────────────┘ │
│ Mở hồ sơ                   │
└────────────────────────────┘
```

Ngày xếp dọc, nút `w-full` xếp dọc; chip trạng thái xuống dòng dưới tên. Hộp thoại gần hết chiều ngang, cuộn dọc bên
trong. `TabsList` trang sửa tin cuộn ngang như hiện có (6 tab).

## 5. Component

### 5a. File mới

| File | Vai trò |
|---|---|
| `frontend/src/features/reconsideration/types.ts` | Kiểu theo REQUIREMENT mục 4.4 |
| `frontend/src/features/reconsideration/api.ts`, `queries.ts` | C1, H1, H2; khoá H1 theo `jobId` + bộ lọc |
| `frontend/src/features/reconsideration/reconsiderationLabels.ts` | Chuỗi cố định mục 7; nhãn trạng thái yêu cầu; hàm đếm độ dài sau chuẩn hoá CRLF |
| `frontend/src/features/reconsideration/CandidateReconsiderationSection.tsx` | Khối 4b (6 `state`) + mở hộp thoại 4c |
| `frontend/src/features/reconsideration/ReconsiderationRequestDialog.tsx` | Hộp thoại 4c |
| `frontend/src/features/reconsideration/ReconsiderationCard.tsx` | Thẻ một yêu cầu (4e, 4f), prop ẩn/hiện "Mở hồ sơ" |
| `frontend/src/features/reconsideration/DeclineReconsiderationDialog.tsx` | Hộp thoại 4g |
| `frontend/src/features/reconsideration/ReconsiderationTab.tsx` | Tab 4e: bộ lọc, danh sách, rỗng/tải/lỗi |
| `frontend/src/features/reconsideration/PendingReconsiderationText.tsx` | Chữ " · Đang chờ xem xét lại" cạnh badge (4a) |
| `frontend/src/features/reconsideration/keys.ts` | (Đợt 6, 30/10) Khoá query H1, file không import gì — `applicationDetail/queries.ts` dùng được mà không vòng import với `reconsideration/queries.ts` |
| `frontend/src/features/reconsideration/useReconsiderationDialogs.tsx` | (Đợt 6, 30/10) Hai hộp thoại của thẻ (`InterviewInvitationDialog`, 4g) đặt ở nơi gọi (tab 4e, khối 4f) chứ không trong thẻ: bộ lọc "Đang chờ" làm thẻ biến mất ngay khi yêu cầu vừa quyết; file riêng vì `react-refresh/only-export-components` |

### 5b. File có sẵn bị sửa

| File | Thay đổi |
|---|---|
| `frontend/src/pages/HrJobEditPage.tsx` | `VALID_TABS` thêm `'reconsideration'` (cuối); thêm `TabsTrigger`/`TabsContent` |
| `frontend/src/pages/CandidateApplicationsPage.tsx` | Chữ 4a sau `ApplicationStatusBadge` (`:56`) |
| `frontend/src/features/candidateApplicationDetail/CandidateApplicationHeader.tsx` | Chữ 4a |
| `frontend/src/features/candidateApplicationDetail/ApplicationInfoTab.tsx` | Khối 4b ở đầu tab |
| `frontend/src/features/applicationDetail/ApplicationDetailHeader.tsx` | Khối 4f dưới thanh thao tác |
| `frontend/src/features/applicationDetail/ApplicationActionBar.tsx` | Câu thay khi `REJECTED` + yêu cầu `OPEN` (4f); **không** đổi nút |
| `frontend/src/features/applications/types.ts`, `features/candidateApplicationDetail/types.ts`, `features/applicationDetail/types.ts` | Trường `reconsiderationStatus` / `reconsideration` |
| `frontend/src/features/applicationDetail/queries.ts` | `applicationDetailInvalidateKeys` thêm khoá H1 của tin |
| `frontend/src/features/interviewinvitation/queries.ts` | (L1, Plan Mode 30/10) `useSendInterviewInvitationMutation.onError` (`:73-78`): ngoài làm mới I1 khi 409 như cũ, khi 409 (mọi mã) hoặc 400 `INVALID_APPLICATION_STATUS_TRANSITION` thì làm mới thêm `extraInvalidateKeys` — để đạt mục 6 dòng "Mời phỏng vấn" lỗi. FR-H12 chỉ thêm việc làm mới, không đổi giao diện |
| `frontend/src/features/notifications/types.ts` | Thêm `'RECONSIDERATION_REQUESTED'`, `'RECONSIDERATION_DECLINED'` |
| `frontend/src/features/applications/queries.ts` | (Đợt 5, 30/10) `MY_APPLICATIONS_QUERY_KEY` đổi từ khai cục bộ sang `export`, để `features/reconsideration/queries.ts` làm mới danh sách đơn sau C1 — không khai khoá thứ hai |

Không sửa: `InterviewInvitationDialog.tsx` (dùng nguyên — REQUIREMENT a24; việc làm mới khi lỗi đặt ở
`interviewinvitation/queries.ts`, không ở hộp thoại), `ApplicationStatusBadge`,
`NotificationDropdown.tsx`, `NotificationList.tsx`, timeline lịch sử, `components/ui`.

### 5c. Dùng lại có sẵn

| Có sẵn | Cách dùng |
|---|---|
| `Dialog`, `Button`, `Textarea`, `Label`, `Tabs` | Như các hộp thoại/tab hiện có |
| `InterviewInvitationDialog` | "Mời phỏng vấn" ở tab và khối 4f |
| Khối lưu ý trung tính kiểu FR-H12 4b (`role="note"`, icon `Info`) | 4c, 4g; viết riêng, không import file `interviewinvitation` |
| `formatDateTimeVi` (`lib/date.ts`) | Mọi thời điểm |
| `extractErrorMessage` (`lib/httpError`) | Đọc `message` backend, fallback mục 7 |
| `retryUnlessClientError` | Hook H1 không thử lại với 4xx |

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Thẻ khối 4b, thẻ yêu cầu 4e/4f | `bg-m3-surface` | viền `border-m3-outline-variant` | Phân khối |
| Tiêu đề khối, tên ứng viên | — | `text-m3-on-surface`, `font-medium` | — |
| Ngày tháng, nhãn "Lý do", "Phản hồi…" | — | `text-m3-on-surface-variant` | Chỉ trên `m3-surface` (4.83:1) |
| Văn bản lý do/phản hồi | — | `text-m3-on-surface` | `whitespace-pre-wrap break-words` |
| Chữ "Đang chờ xem xét lại" cạnh badge | — | `text-m3-on-surface-variant` (trong thẻ trắng) | Không màu, không icon cảnh báo |
| Chip trạng thái yêu cầu (3 giá trị) | `bg-m3-surface-container` | `text-m3-on-surface` | **Một** kiểu trung tính cho cả ba; không xanh cho "Đã mời", không đỏ cho "Đã giữ từ chối" |
| Khối lưu ý 4c, 4g | `bg-m3-surface-container` | `text-m3-on-surface`; viền `border-m3-outline-variant`; icon `Info` `text-m3-on-surface` | Không `m3-warning`/`m3-error` |
| Nút "Yêu cầu xem xét lại", "Mời phỏng vấn", "Giữ quyết định từ chối" | — | `variant="outline"` | Cùng mức nhấn mạnh |
| Nút gửi trong hộp thoại ("Gửi yêu cầu", "Giữ quyết định từ chối") | — | mặc định (filled) | Hành động chính của hộp thoại |
| Lỗi dưới ô, lỗi backend trong hộp thoại | `m3-surface` | `text-m3-error` | 4.74:1 |
| Lỗi tải H1 | — | Icon `AlertCircle` `text-m3-error` + chữ `text-m3-on-surface` | Như FR-H12 |
| Câu rỗng của tab | — | `text-m3-on-surface-variant` | Trong thẻ trắng (UI_GUIDE mục 5) |

## 6. Trạng thái hiển thị

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| `reconsideration` `null` | Khối 4b, 4f | Không render |
| `AVAILABLE` / `EXPIRED` / `JOB_REMOVED` / `OPEN` / `ACCEPTED` / `DECLINED` | Khối 4b | Theo 4b |
| Đang gửi C1 / H2 | Hộp thoại | Nút gửi khoá, nhãn "Đang gửi..."; ô nhập khoá |
| Lỗi 409 C1 (`ALREADY_REQUESTED`, `NOT_ELIGIBLE`, `JOB_REMOVED`, `DEADLINE_PASSED`) | Hộp thoại 4c | Câu backend ở cuối hộp thoại; làm mới E1 (khối đổi đúng trạng thái sau khi đóng) |
| Lỗi 400 `RECONSIDERATION_TEXT_TOO_LONG` | Hộp thoại | Câu backend (frontend đã chặn trước, chỉ xảy ra khi lệch cách đếm) |
| Lỗi 409 `RECONSIDERATION_NOT_OPEN` (H2) | Hộp thoại 4g | Câu backend; làm mới H1 + E1 hồ sơ đơn |
| Lỗi 409 `RECONSIDERATION_NOT_OPEN` khi "Mời phỏng vấn" (thua tranh chấp — REQUIREMENT R-D3) hoặc 400 `INVALID_APPLICATION_STATUS_TRANSITION` (yêu cầu đã quyết xong ở tab khác trước khi bấm gửi) | Hộp giấy mời FR-H12 | Câu backend ở cuối hộp thoại như lỗi hiện có; làm mới H1 + E1 hồ sơ đơn — qua `onError` của `useSendInterviewInvitationMutation` (`interviewinvitation/queries.ts`, mục 5b, L1): 409 (mọi mã) hoặc 400 `INVALID_APPLICATION_STATUS_TRANSITION` → làm mới thêm `extraInvalidateKeys` |
| Thanh thao tác H09, đơn `REJECTED` + yêu cầu `OPEN` | Đầu trang hồ sơ đơn | "Ứng viên đã gửi yêu cầu xem xét lại. Xử lý ở khối bên dưới." — không có câu "Không còn thao tác nào", không nút (REQUIREMENT R-D7) |
| Thanh thao tác H09, đơn `REJECTED` không có yêu cầu / yêu cầu `ACCEPTED`/`DECLINED` | Đầu trang hồ sơ đơn | Giữ câu cũ "Đơn đã có kết quả cuối. Không còn thao tác nào." |
| Gửi giấy mời thành công từ tab/khối | Trang | Đóng hộp thoại; H1, E1 hồ sơ đơn, danh sách "Ứng viên" tải lại; thẻ rời bộ lọc "Đang chờ"; số trên tab giảm |
| Đang tải H1 | Tab | 3 thẻ skeleton xám |
| Lỗi tải H1 | Tab | Icon `AlertCircle` + "Không tải được yêu cầu xem xét lại, vui lòng thử lại." + nút "Thử lại" |
| H1 rỗng | Tab | Câu rỗng 4e theo bộ lọc |
| Đơn ẩn danh | Tab, khối 4f | Tên "Ứng viên #…", lý do/phản hồi đã che (backend) |
| Lý do / phản hồi `null` | 4b, 4e | Theo câu ở 4b, 4e |
| 403/404 cả trang | Trang | Theo FR-U08 / FR-H09 / trang sửa tin hiện có; không gọi C1/H1 |

Không có trạng thái "AI đang xử lý": FR-C09 không dùng AI.

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Chữ cạnh badge | "Đang chờ xem xét lại" (hiển thị " · Đang chờ xem xét lại") |
| Khối ứng viên — tiêu đề | "Xem xét lại" |
| `AVAILABLE` | "Bạn có thể đề nghị nhà tuyển dụng xem xét lại đơn này một lần." · "Hạn chót: {t}." · nút "Yêu cầu xem xét lại" |
| `EXPIRED` | "Đã quá hạn gửi yêu cầu xem xét lại (hạn chót {t})." |
| `JOB_REMOVED` | "Tin tuyển dụng đã bị gỡ nên không gửi được yêu cầu xem xét lại." |
| `OPEN` | "Đang chờ nhà tuyển dụng xem xét lại. Gửi lúc {t}." |
| `ACCEPTED` | "Nhà tuyển dụng đã xem xét lại và gửi giấy mời phỏng vấn lúc {t}. Xem giấy mời bên dưới." |
| `DECLINED` | "Nhà tuyển dụng đã xem xét lại và giữ quyết định từ chối lúc {t}." · "Phản hồi của nhà tuyển dụng" · không phản hồi: "Nhà tuyển dụng không gửi kèm phản hồi." |
| Lý do phía ứng viên | "Lý do bạn đã gửi" |
| Hộp thoại ứng viên | Tiêu đề "Yêu cầu xem xét lại" · mô tả "Nhà tuyển dụng sẽ nhận được yêu cầu và quyết định mời phỏng vấn hoặc giữ quyết định từ chối." · lưu ý "Mỗi đơn chỉ gửi được một lần. Sau khi gửi, bạn không sửa hay huỷ được yêu cầu. Hạn chót: {t}." · nhãn "Lý do (không bắt buộc)" · gợi ý "Nhà tuyển dụng sẽ đọc nguyên văn nội dung này." · nút "Huỷ", "Gửi yêu cầu", đang gửi "Đang gửi..." |
| Lỗi frontend | "Nội dung tối đa 1000 ký tự." |
| Lỗi backend (REQUIREMENT mục 4.3 — đúng từng chữ) | "Bạn đã gửi yêu cầu xem xét lại cho đơn này. Mỗi đơn chỉ gửi được một lần." · "Chỉ đơn bị từ chối ở vòng hồ sơ mới gửi được yêu cầu xem xét lại." · "Tin tuyển dụng của đơn này đã bị gỡ nên không gửi được yêu cầu xem xét lại." · "Đã quá hạn 7 ngày để gửi yêu cầu xem xét lại." · "Nội dung tối đa 1000 ký tự." · "Yêu cầu xem xét lại này không còn chờ xử lý. Vui lòng tải lại trang." |
| Lỗi fallback | Gửi: "Gửi yêu cầu thất bại, vui lòng thử lại." · Giữ từ chối: "Lưu quyết định thất bại, vui lòng thử lại." · Tải H1: "Không tải được yêu cầu xem xét lại, vui lòng thử lại." |
| Tab HR | Nhãn "Yêu cầu xem xét lại" / "Yêu cầu xem xét lại ({n})" · mô tả "Ứng viên bị từ chối ở vòng hồ sơ có thể gửi một yêu cầu trong 7 ngày." · bộ lọc "Đang chờ", "Tất cả" · rỗng "Không có yêu cầu xem xét lại nào đang chờ." / "Tin này chưa có yêu cầu xem xét lại nào." |
| Thẻ yêu cầu | "Nộp đơn {d}" · "Bị từ chối {d}" · "Gửi yêu cầu {d}" · "Quyết định {d}" · "Lý do" · "Ứng viên không ghi lý do." · "Phản hồi đã gửi ứng viên" · "Không gửi kèm phản hồi." · nút "Mở hồ sơ", "Mời phỏng vấn", "Giữ quyết định từ chối" · chip "Đang chờ" / "Đã mời phỏng vấn" / "Đã giữ từ chối" |
| Khối H09 | Tiêu đề "Yêu cầu xem xét lại" · câu thanh thao tác "Ứng viên đã gửi yêu cầu xem xét lại. Xử lý ở khối bên dưới." |
| Hộp thoại HR | Tiêu đề "Giữ quyết định từ chối — {tên hoặc nhãn}" · mô tả "Đơn vẫn ở trạng thái Bị từ chối. Ứng viên sẽ nhận thông báo về kết quả xem xét lại." · lưu ý "Phản hồi bên dưới được gửi cho ứng viên và hiện ở trang chi tiết đơn của họ. Đây không phải ghi chú nội bộ. Sau khi xác nhận không sửa được." · nhãn "Phản hồi cho ứng viên (không bắt buộc)" · nút "Huỷ", "Giữ quyết định từ chối" |
| Thông báo (backend, REQUIREMENT R-N2, R-N3) | HR: "Ứng viên yêu cầu xem xét lại" · ứng viên: "Kết quả yêu cầu xem xét lại" |

Thời điểm dùng `formatDateTimeVi` (`HH:mm dd/MM/yyyy`, giờ trình duyệt) như "Nộp ngày"; ngày trên thẻ HR dùng phần
ngày của cùng hàm.

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Khối ứng viên 4b | Tab / khối HR 4e, 4f | Hộp thoại |
|---|---|---|---|
| Expanded (≥ `lg`) | Như 4b; nút `sm:w-auto` | Ngày trên một dòng ngăn " · "; "Mở hồ sơ" trái, hai nút phải cùng hàng | `sm:max-w-lg` |
| Medium (`sm`–`lg`) | Như expanded | Ngày tự xuống dòng; nút giữ cùng hàng | Như expanded |
| Compact (< `sm`, 375px) | Nút `w-full` | Theo 4h: ngày xếp dọc (bỏ " · "), nút `w-full` xếp dọc, chip dưới tên | Gần hết chiều ngang, cuộn dọc; nút Huỷ/Gửi cùng hàng |

- Không cuộn ngang toàn trang ở 375px; chỉ `TabsList` cuộn ngang. Văn bản dài `break-words`.
- Không thanh thao tác `fixed`/`sticky`.

## 9. Khả năng tiếp cận

- Khối 4b, thẻ 4e/4f: tiêu đề là `h2` (khối) / `h3` (thẻ trong tab), `tabIndex={-1}` để nhận focus sau thao tác.
- Hộp thoại: `Textarea` có `<Label>`; bộ đếm và câu lỗi gắn `aria-describedby`; vượt giới hạn → `aria-invalid`.
  Khối lưu ý `role="note"`, icon `Info` `aria-hidden="true"`.
- Bộ lọc tab là `role="radiogroup"` có `aria-label="Lọc yêu cầu"`; mỗi lựa chọn `role="radio"` + `aria-checked`;
  phím mũi tên chuyển lựa chọn.
- Chip trạng thái và chữ "Đang chờ xem xét lại" là chữ — không truyền thông tin chỉ bằng màu.
- Vùng chạm nút ≥ 48px ở compact (UI_GUIDE 1h).
- Kết quả thao tác đọc qua vùng `aria-live="polite"`: "Đã gửi yêu cầu xem xét lại.", "Đã giữ quyết định từ chối."
- Tương phản: chỉ các cặp ở UI_GUIDE mục 1c/6 và bảng 5d; không cặp mới.

## 10. Không được làm

- Không thêm badge/màu trạng thái đơn thứ 6; không đổi `ApplicationStatusBadge` (đơn vẫn `REJECTED`).
- Không tô xanh "Đã mời phỏng vấn", đỏ/cam "Đã giữ từ chối"/"Quá hạn"; không icon ✓ ✗ (CLAUDE.md §8).
- Không dựng hộp giấy mời thứ hai; "Mời phỏng vấn" luôn mở `InterviewInvitationDialog`.
- Không nút "Chấp nhận" riêng không kèm giấy mời; không nút huỷ/sửa yêu cầu (ứng viên) hay sửa phản hồi (HR).
- Không thêm nút vào thanh thao tác FR-H07 (Q8); không đổi chữ, vị trí, kiểu ba nút hiện có. Không để câu "Không còn
  thao tác nào" hiện cho đơn `REJECTED` có yêu cầu `OPEN` (R-D7).
- Không tự tính còn/hết hạn bằng đồng hồ trình duyệt; chỉ hiển thị `state` và `deadline` backend trả.
- Không hiện lý do trong thông báo; không hiện phản hồi trong chuông.
- Không gọi phản hồi là "ghi chú" hay đặt cạnh ghi chú nội bộ — nó là nội dung gửi ứng viên.
- Không nút/nhãn "Do AI tạo", không gợi ý AI chấp nhận/từ chối, không soạn nháp AI.
- Không render HTML/Markdown, không `dangerouslySetInnerHTML`; không cắt ngắn lý do/phản hồi.
- Không hiện điểm, rubric, giải thích AI hay ghi chú HR ở khối phía ứng viên.
- Không sửa `components/ui`, `InterviewInvitationDialog.tsx`, timeline lịch sử.
- Không sao chép logo, tên thương hiệu hay CSS của trang tuyển dụng nào (CLAUDE.md §8).
