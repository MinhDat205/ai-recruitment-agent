# FR-H12 — Giao diện

> Trạng thái: ĐÃ DUYỆT (13/10/2026).

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 1h (vùng chạm), 3 (Tabs, Dialog, nút), 4 (không phán quyết; dữ
liệu thiếu), 5 (rỗng/tải/lỗi), 6 (tương phản). Mã `R-…`, `I1`–`I4`, `Q…`, `B…` là của `REQUIREMENT.md` cùng thư mục.
Khung trang hồ sơ đơn (FR-H09) và trang chi tiết đơn (FR-U08) không chép lại, chỉ chốt phần FR-H12 thêm hoặc đổi.

## 1. Trạng thái

> Trạng thái: ĐÃ DUYỆT (13/10/2026).

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| `/hr/applications/:id` | ★Tab "Giấy mời phỏng vấn" (`?tab=invitation`, Q7): giấy mời hiệu lực, nút "Gửi lại giấy mời", giấy mời trước đó. Hộp thoại "Mời phỏng vấn" đổi phần lịch thành danh sách khung giờ; ★hộp thoại "Gửi lại giấy mời" | Hiện có + ★tab mới | FR-H12 (sửa dòng UI_GUIDE mục 7 ở đợt cuối, REQUIREMENT D3) |
| `/candidate/applications/:id` | Khối "Giấy mời phỏng vấn" có sẵn ở tab "Thông tin đơn" đổi sang danh sách khung giờ chỉ đọc | Hiện có | FR-U08; FR-H12 (D3) |
| `/candidate/notifications`, chuông thông báo | Thêm một loại thông báo (một dòng), không đổi bố cục | Hiện có | FR-C03; FR-H12 |

Thứ tự tab của trang hồ sơ đơn sau FR-H12 (đã chốt — REQUIREMENT Q7, S7): **CV & điểm → Giải thích → Sàng lọc → Giấy
mời phỏng vấn → Lịch sử → Trao đổi**. FR-H12 chỉ chèn một tab, không đổi thứ tự tương đối của năm tab hiện có (`HrApplicationDetailPage.tsx:21`,
`:141-147`), tab mặc định vẫn `cv`; giá trị `?tab=` lạ vẫn về `cv`. Tab hiện ở **mọi** trạng thái đơn (đơn chưa có giấy
mời → câu trạng thái rỗng, 4c-3) — không ẩn/hiện tab theo trạng thái để `?tab=invitation` luôn mở được.

Thanh thao tác FR-H07 (`ApplicationActionBar.tsx`) **không đổi** (Q7): "Mời phỏng vấn" vẫn ở đó, không thêm "Gửi lại".

## 3. Điểm vào

- HR, gửi lần đầu: nút "Mời phỏng vấn" ở thanh thao tác đầu trang (`ApplicationActionBar.tsx:41-45`, không đổi) khi
  đơn `PENDING`.
- HR, xem và gửi lại: bấm tab "Giấy mời phỏng vấn", hoặc mở `/hr/applications/{id}?tab=invitation`. Nút "Gửi lại giấy
  mời" nằm trong tab, chỉ khi đơn `INTERVIEW_INVITED`.
- Ứng viên: `/candidate/applications/{id}`, hoặc bấm thông báo "Nhà tuyển dụng đã gửi lại giấy mời phỏng vấn" (link của
  thông báo, REQUIREMENT R-N2).

## 4. Bố cục (khung ASCII)

### 4a. Desktop (≥ `lg`) — hộp thoại "Mời phỏng vấn" (danh sách khung giờ)

`Dialog` `sm:max-w-2xl` (rộng hơn `sm:max-w-lg` hiện có, `InterviewInvitationDialog.tsx:114`, để hai ô của một khung
nằm cùng hàng). Phần cảnh báo tên công ty, tiêu đề, nội dung thư giữ nguyên như hiện có; chỉ khối "Khung giờ phỏng vấn"
thay hai ô "Ngày giờ phỏng vấn"/"Địa điểm (tuỳ chọn)" (`:141-151`).

```
┌──────────────────────────────────────────────────────────────────────┐
│ Mời phỏng vấn — Quốc Huy                                         [X] │
│ Nội dung đã render sẵn từ mẫu giấy mời của tin tuyển dụng này. Bạn   │
│ có thể sửa lại trước khi gửi.                                        │
│                                                                      │
│ Khung giờ phỏng vấn                                                  │
│ Thêm 1–5 khung giờ. Nếu chỉ có một khung, khung đó được coi là lịch  │
│ đã chốt. Giờ theo múi giờ của máy bạn.                               │
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ Khung giờ 1                                                  [X] │ │
│ │ Thời điểm bắt đầu        Địa điểm hoặc link                      │ │
│ │ [20/10/2026 09:00   ]    [Tầng 3, 45 Bạch Đằng, Đà Nẵng       ]  │ │
│ ├──────────────────────────────────────────────────────────────────┤ │
│ │ Khung giờ 2                                                  [X] │ │
│ │ [20/10/2026 09:00   ]    [                                    ]  │ │
│ │ Khung giờ này trùng thời điểm với khung giờ 1.                   │ │
│ │ Vui lòng nhập địa điểm hoặc link phỏng vấn                       │ │
│ └──────────────────────────────────────────────────────────────────┘ │
│ [+ Thêm khung giờ]                                                   │
│                                                                      │
│ Tiêu đề thư mời                                                      │
│ [Thư mời phỏng vấn vị trí ...                                     ]  │
│ Nội dung thư mời                                                     │
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ Kính gửi Quốc Huy, ...                                           │ │
│ └──────────────────────────────────────────────────────────────────┘ │
│ (lỗi backend nếu có)                                                 │
│                                            [Huỷ]  [Gửi lời mời]      │
└──────────────────────────────────────────────────────────────────────┘
```

- Mở hộp thoại: một khung trống. "Thêm khung giờ" thêm một khung trống ở cuối, đặt focus vào ô thời điểm của khung mới;
  khoá khi đủ `maxSlots` (lấy từ preview), câu lý do hiện thành chữ ngay dưới nút.
- Nút xoá khung là icon-only `X`, có `aria-label`; **không render** khi chỉ còn một khung. Xoá xong, các khung sau đánh
  số lại; focus về nút xoá của khung đứng trước (hoặc ô thời điểm của khung 1 nếu không còn nút xoá).
- Ô thời điểm giữ `type="datetime-local"`, quy đổi sang ISO UTC như hiện nay (`buildScheduledAtIso`, `:42-44`). Ô địa
  điểm là `Input` một dòng, **bắt buộc** (Q9).
- Lỗi Zod hiện **dưới từng khung**, gắn với ô tương ứng. Lỗi backend `INVALID_INTERVIEW_SCHEDULE` có câu nêu số khung
  (REQUIREMENT R-S8) hiện ở cuối hộp thoại như lỗi hiện có (`:165-169`); backend không trả tên trường.
- Thứ tự khung là thứ tự HR nhập; giao diện không tự sắp xếp.

### 4b. Hộp thoại "Gửi lại giấy mời"

Cùng component và bố cục 4a, khác ở đầu hộp thoại và nút:

```
┌──────────────────────────────────────────────────────────────────────┐
│ Gửi lại giấy mời — Quốc Huy                                      [X] │
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ (i) Giấy mời mới sẽ thay giấy mời hiện tại. Giấy mời hiện tại    │ │
│ │     vẫn được lưu nguyên văn ở mục "Giấy mời trước đó". Trạng     │ │
│ │     thái đơn không đổi. Ứng viên sẽ nhận thông báo và email kèm  │ │
│ │     các khung giờ mới.                                           │ │
│ │     Ứng viên đã chốt khung 09:00 20/10/2026. Khung này sẽ không  │ │
│ │     còn hiệu lực sau khi gửi lại.          (chỉ khi đã có khung) │ │
│ └──────────────────────────────────────────────────────────────────┘ │
│ Khung giờ phỏng vấn ... (như 4a, một khung trống)                    │
│ Tiêu đề thư mời / Nội dung thư mời (điền sẵn từ giấy mời hiện tại)   │
│                                         [Huỷ]  [Gửi lại giấy mời]    │
└──────────────────────────────────────────────────────────────────────┘
```

- Điền sẵn tiêu đề và nội dung **từ giấy mời hiệu lực** (dữ liệu I1 đã có ở tab 4c), không render lại mẫu, không gọi
  preview (Q6). Khung giờ: một khung trống. `maxSlots` lấy từ I1.
- Không có cảnh báo tên công ty (cảnh báo đó thuộc bản render từ mẫu).

### 4c. Desktop — tab "Giấy mời phỏng vấn"

**4c-1.** Giấy mời nhiều khung, chưa chốt, đơn `INTERVIEW_INVITED`, có một giấy mời trước đó:

```
│ [CV & điểm][Giải thích][Sàng lọc][Giấy mời phỏng vấn][Lịch sử][Trao đổi] │
├──────────────────────────────────────────────────────────────────────────┤
│ Giấy mời hiện tại                                 [Gửi lại giấy mời]     │
│ Gửi lúc 09:00 06/10/2026                                                 │
│ Ứng viên chưa chọn khung giờ. Muốn chốt ngay một khung, hãy gửi lại      │
│ giấy mời chỉ với khung đó.                                               │
│                                                                          │
│ Khung giờ (giờ Việt Nam)                                                 │
│ 1. 10:00 18/10/2026 · Phỏng vấn trực tuyến qua Google Meet (đường …)     │
│ 2. 09:00 19/10/2026 · Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng       │
│ 3. 14:00 19/10/2026 · https://meet.example.com/abc-defg-hij              │
│                                                                          │
│ Thư mời phỏng vấn vị trí Chuyên viên Chăm sóc khách hàng - Công ty …     │
│ Kính gửi Trần Bảo Ngọc,                                                  │
│ Cảm ơn bạn đã ứng tuyển ...                                              │
│                                                                          │
│ [v] Giấy mời trước đó (1)                                                │
```

Khi đã có khung chốt, dòng "Ứng viên chưa chọn khung giờ…" **không hiện**; khung chốt có dấu bên cạnh. Khung dữ liệu cũ
không có địa điểm ghi "Chưa có dữ liệu" (Q9):

```
│ 1. 09:00 13/10/2026 · Tầng 3, 45 Bạch Đằng, …   [✓ Đã chốt]              │
│ 1. 09:00 25/07/2026 · Chưa có dữ liệu           [✓ Đã chốt]              │
```

**4c-2.** Mở "Giấy mời trước đó":

```
│ [^] Giấy mời trước đó (1)                                                │
│   ┌────────────────────────────────────────────────────────────────────┐ │
│   │ Gửi lúc 00:40 06/10/2026 · Đã được thay bằng giấy mời gửi lúc      │ │
│   │ 09:00 06/10/2026                                                   │ │
│   │ Khung giờ (giờ Việt Nam)                                           │ │
│   │ 1. 09:00 13/10/2026 · Tầng 3, 45 Bạch Đằng, …   [✓ Đã chốt]        │ │
│   │ Thư mời phỏng vấn vị trí Nhân viên Hành chính văn phòng - …        │ │
│   │ Kính gửi Quốc Huy, …                                               │ │
│   └────────────────────────────────────────────────────────────────────┘ │
```

**4c-3.** Đơn chưa có giấy mời (I1 trả danh sách rỗng):

```
│ [CV & điểm][Giải thích][Sàng lọc][Giấy mời phỏng vấn][Lịch sử][Trao đổi] │
├──────────────────────────────────────────────────────────────────────────┤
│ Chưa gửi giấy mời phỏng vấn cho đơn này. Dùng nút "Mời phỏng vấn" ở đầu  │
│ trang để gửi.                                       (đơn PENDING)        │
│                                                                          │
│ Đơn này chưa từng được mời phỏng vấn.     (đơn REJECTED/WITHDRAWN)       │
```

- Không lặp nút "Mời phỏng vấn" trong tab (nút ở thanh thao tác, Q7). Câu trạng thái rỗng không kèm nút — ngoại lệ có
  chủ đích như FR-H10 UI.md 4e: hành động hợp lệ đã có ở thanh thao tác (đơn `PENDING`) hoặc không còn hành động nào.
- Nút "Gửi lại giấy mời" chỉ khi đơn `INTERVIEW_INVITED`; `variant="outline"`.
- Giấy mời cũ: theo thứ tự I1 (mới bị thay gần nhất trước), mỗi giấy mời một thẻ viền `m3-outline-variant`; mặc định
  **thu gọn**. Không có nút thao tác nào trong thẻ cũ.
- Nội dung thư hiện **đầy đủ**, `whitespace-pre-wrap break-words` (như `InterviewInvitationDetails.tsx:61`).
- Giờ khung hiện theo giờ Việt Nam (`Asia/Ho_Chi_Minh`, như `InterviewInvitationDetails.tsx:7-16` và email R-N3), ghi
  rõ "(giờ Việt Nam)"; "Gửi lúc" dùng `formatDateTimeVi` như "Nộp ngày" ở đầu trang.
- Địa điểm dạng URL hiện **chữ thường**, không thành liên kết (B4).

### 4d. Desktop — khối "Giấy mời phỏng vấn" phía ứng viên (FR-U08)

Giữ vị trí và tiêu đề khối (`InvitationSection.tsx:31-45`); phần thân (`InterviewInvitationDetails`) đổi. Chưa chốt:

```
│ Giấy mời phỏng vấn                                                       │
│ Hãy nhắn cho nhà tuyển dụng qua tab Trao đổi để xác nhận khung giờ phù   │
│ hợp.                                                                     │
│                                                                          │
│ Khung giờ (giờ Việt Nam)                                                 │
│ [lịch] 10:00 18/10/2026                                                  │
│        Địa điểm: Phỏng vấn trực tuyến qua Google Meet (đường dẫn gửi …)  │
│ [lịch] 09:00 19/10/2026                                                  │
│        Địa điểm: Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng            │
│ [lịch] 14:00 19/10/2026                                                  │
│        Địa điểm: https://meet.example.com/abc-defg-hij                   │
│                                                                          │
│ Thư mời phỏng vấn vị trí ...                                             │
│ Kính gửi Trần Bảo Ngọc, ...                                              │
```

Đã chốt (giấy mời một khung, hoặc FR-U10 về sau): không có câu hướng dẫn; mọi khung vẫn hiện, khung chốt có dấu "Đã
chốt":

```
│ Giấy mời phỏng vấn                                                       │
│ Khung giờ (giờ Việt Nam)                                                 │
│ [lịch] 09:00 25/07/2026   [✓ Đã chốt]                                    │
│        Địa điểm: Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng            │
│ Thư mời phỏng vấn vị trí ...                                             │
```

- Không nút, không ô chọn, không radio — phần chọn là của FR-U10.
- Câu hướng dẫn (Q8) chỉ hiện khi `confirmedAt` là `null`. Giữ tiền tố "Địa điểm: " có sẵn; khung dữ liệu cũ không có
  địa điểm hiện "Địa điểm: Chưa có dữ liệu" (Q9 — trước đây dòng này bị bỏ, nay hiện rõ là thiếu theo UI_GUIDE mục 4).
- Quy tắc gọi API, ẩn khối khi 404, lỗi + "Thử lại" của `InvitationSection` giữ nguyên (R-T3…R-T7 của FR-U08).

### 4e. Điện thoại (375px) — hộp thoại

Chiều rộng nội dung ≈ 240–300px. Hộp thoại gần hết chiều ngang, cuộn dọc bên trong.

```
┌──────────────────────────────┐
│ Mời phỏng vấn — Quốc Huy [X] │
│ Khung giờ phỏng vấn          │
│ Thêm 1–5 khung giờ. …        │
│ ┌──────────────────────────┐ │
│ │ Khung giờ 1          [X] │ │
│ │ Thời điểm bắt đầu        │ │
│ │ [20/10/2026 09:00      ] │ │
│ │ Địa điểm hoặc link       │ │
│ │ [Tầng 3, 45 Bạch Đằng  ] │ │
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │   + Thêm khung giờ       │ │
│ └──────────────────────────┘ │
│ Tiêu đề thư mời …            │
│ ┌──────────┐ ┌─────────────┐ │
│ │   Huỷ    │ │ Gửi lời mời │ │
│ └──────────┘ └─────────────┘ │
└──────────────────────────────┘
```

Hai ô của một khung xếp dọc; nút "Thêm khung giờ" `w-full`; nút xoá khung có vùng chạm ≥ 48px.

### 4f. Điện thoại (375px) — tab "Giấy mời phỏng vấn"

`TabsList` cuộn ngang như hiện có (`HrApplicationDetailPage.tsx:141`); "Giấy mời phỏng vấn" là tab thứ 4, có thể nằm
ngoài khung ở 375px — chấp nhận như "Lịch sử"/"Trao đổi" hiện nay.

```
┌────────────────────────────┐
│ ┌─────────┬──────────┬────┐
│ │CV & điểm│Giải thích│Sà→ │  (cuộn ngang: … Giấy mời phỏng vấn · Lịch sử · Trao đổi)
│ └─────────┴──────────┴────┘
├────────────────────────────┤
│ Giấy mời hiện tại          │
│ Gửi lúc 09:00 06/10/2026   │
│ ┌────────────────────────┐ │
│ │   Gửi lại giấy mời     │ │
│ └────────────────────────┘ │
│ Ứng viên chưa chọn khung   │
│ giờ. Muốn chốt ngay một    │
│ khung, hãy gửi lại giấy    │
│ mời chỉ với khung đó.      │
│ Khung giờ (giờ Việt Nam)   │
│ 1. 10:00 18/10/2026        │
│    Phỏng vấn trực tuyến    │
│    qua Google Meet …       │
│ 2. 09:00 19/10/2026        │
│    Tầng 3, 45 Bạch Đằng, … │
│ 3. 14:00 19/10/2026        │
│    https://meet.example.co │
│    m/abc-defg-hij          │
│ Thư mời phỏng vấn …        │
│ [v] Giấy mời trước đó (1)  │
└────────────────────────────┘
```

Nút "Gửi lại giấy mời" xuống dưới dòng "Gửi lúc", `w-full`. Mỗi khung: thời điểm một dòng, địa điểm và dấu "Đã chốt"
xuống dòng dưới (không dùng " · " ở compact); link dài ngắt bằng `break-all`. Phía ứng viên ở 375px giữ đúng bố cục 4d
(vốn đã một cột).

## 5. Component

### 5a. File mới (chốt tên — `REQUIREMENT.md` mục 7.3 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/features/interviewinvitation/InterviewSlotFields.tsx` | Danh sách khung giờ trong hộp thoại: `useFieldArray`, thêm/xoá, đánh số, lỗi từng khung (4a, 4e) |
| `frontend/src/features/interviewinvitation/InterviewSlotList.tsx` | Danh sách khung chỉ đọc + dấu "Đã chốt" + "Chưa có dữ liệu"; dùng chung cho tab HR (4c, 4f) và phía ứng viên (4d), prop chọn kiểu dòng |
| `frontend/src/features/interviewinvitation/ConfirmedSlotMark.tsx` | Dấu "Đã chốt" (icon `Check` + chữ) |
| `frontend/src/features/interviewinvitation/PreviousInvitationCard.tsx` | Thẻ một giấy mời cũ (4c-2) |
| `frontend/src/features/interviewinvitation/invitationLabels.ts` | Chuỗi cố định mục 7; hàm định dạng giờ Việt Nam dùng chung (chuyển `formatScheduledAt` của `InterviewInvitationDetails.tsx:7-16` ra đây) |
| `frontend/src/features/applicationDetail/InterviewInvitationTab.tsx` | Tab "Giấy mời phỏng vấn": gọi I1, giấy mời hiệu lực, nút "Gửi lại giấy mời" (mở `InterviewInvitationDialog` `mode='resend'`), giấy mời trước đó, câu rỗng 4c-3. Đặt ở `applicationDetail/` như `ScreeningTab.tsx` của FR-H10 |

### 5b. File có sẵn bị sửa

| File | Thay đổi |
|---|---|
| `frontend/src/pages/HrApplicationDetailPage.tsx` | `VALID_TABS` thành `['cv','explanation','screening','invitation','history','messages']` (`:21`); thêm `TabsTrigger`/`TabsContent` "Giấy mời phỏng vấn" giữa "Sàng lọc" và "Lịch sử"; không đổi vị trí năm tab cũ |
| `frontend/src/features/interviewinvitation/types.ts` | Kiểu mới theo REQUIREMENT mục 4.4: request `slots`; `InterviewInvitationResponse` mới; `InterviewInvitationList`; `maxSlots` ở preview; `CandidateInterviewInvitation` bỏ `scheduledAt`/`location` gốc, thêm `confirmedAt`, `slots` (có `id`) |
| `frontend/src/features/interviewinvitation/api.ts` | Thêm gọi I1, I3 |
| `frontend/src/features/interviewinvitation/queries.ts` | Hook I1 (khoá theo `applicationId`), mutation I3; hai mutation làm mới I1 + các khoá truyền vào |
| `frontend/src/features/interviewinvitation/InterviewInvitationDialog.tsx` | Prop `mode: 'invite' \| 'resend'` (+ giấy mời hiệu lực khi `resend`); Zod `slots`; `InterviewSlotFields`; 4a, 4b. Mặc định `mode='invite'` — nơi gọi hiện có (`ApplicationActionBar.tsx:76-81`) không phải sửa |
| `frontend/src/features/interviewinvitation/InterviewInvitationDetails.tsx` | Phần thân phía ứng viên theo 4d, dùng `InterviewSlotList` |
| `frontend/src/features/applicationDetail/queries.ts` | `applicationDetailInvalidateKeys` (`:39-49`) thêm khoá I1 |
| `frontend/src/features/notifications/types.ts` | Thêm `'INTERVIEW_INVITATION_RESENT'` vào `NotificationType` (`:1-6`) |

Không sửa: `ApplicationActionBar.tsx` (Q7), `InvitationSection.tsx`, `CvAndScoreTab.tsx`, `NotificationDropdown.tsx`,
`NotificationList.tsx` (thông báo mới là một dòng — hiện như các loại cũ, Q5), `features/messageDraft/*` (Q3),
`components/ui`.

### 5c. Dùng lại có sẵn

| Có sẵn | Cách dùng |
|---|---|
| `Tabs`, `Dialog`, `Button`, `Input`, `Textarea`, `Label` | Như các tab và hộp thoại hiện có |
| `useFieldArray` (React Hook Form) | Danh sách khung; cùng thư viện form đang dùng (CLAUDE.md §3) |
| Mẫu khối lưu ý của FR-H10 (`ScreeningNotice.tsx`) | Cùng kiểu cho khối lưu ý 4b; viết riêng, **không** import file `screening` |
| `formatDateTimeVi` (`lib/date.ts`) | "Gửi lúc …" |
| `extractErrorMessage` (`lib/httpError`) | Đọc `message` của backend, fallback mục 7 |
| `retryUnlessClientError` theo kiểu `applicationDetail/queries.ts:53-61` | Hook I1 không thử lại với 4xx |

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Thẻ một khung trong hộp thoại | `bg-m3-surface` | viền `border-m3-outline-variant` | Chỉ để phân khối |
| "Khung giờ {n}", nhãn ô | — | `text-m3-on-surface` | — |
| Câu hướng dẫn dưới "Khung giờ phỏng vấn", câu lý do khoá nút thêm | — | `text-m3-on-surface-variant` | Trên nền trắng của `Dialog` (4.83:1) |
| Lỗi từng khung | `m3-surface` | `text-m3-error` | 4.74:1 trên nền trắng |
| Khối lưu ý 4b | `bg-m3-surface-container` | `text-m3-on-surface`; viền `border-m3-outline-variant`; icon `Info` `text-m3-on-surface` | Trung tính, như FR-H10; không `m3-warning`/`m3-error` |
| Dấu "Đã chốt" | `bg-m3-primary-container` | `text-m3-on-primary-container` (6.23:1) + icon `Check` | Cùng cặp màu với badge `INTERVIEW_INVITED`; **không** xanh lá, không đỏ cho khung chưa chốt |
| Câu "Ứng viên chưa chọn khung giờ…", câu hướng dẫn phía ứng viên, câu rỗng 4c-3 | — | `text-m3-on-surface` | Câu trạng thái, không phải lỗi |
| Thời điểm khung | — | `text-m3-on-surface`, `font-medium` | — |
| Địa điểm, "Gửi lúc …", dòng "Đã được thay bằng…" | — | `text-m3-on-surface-variant` | Chỉ trên `m3-surface` |
| "Chưa có dữ liệu" (địa điểm thiếu) | — | `text-m3-on-surface-variant` | Như dữ liệu thiếu ở các màn khác |
| Thẻ giấy mời cũ | `bg-m3-surface` | viền `border-m3-outline-variant` | Không làm mờ (opacity) nội dung — giữ tương phản |
| Nút "Gửi lại giấy mời", "Thêm khung giờ" | — | `variant="outline"` | Hành động phụ |
| Nút xoá khung | — | Icon-only `X` `variant="ghost"` `text-m3-on-surface`, `aria-label` | Không đỏ: xoá khung chưa gửi không mất dữ liệu đã lưu |
| Lỗi tải I1 | — | Icon `AlertCircle` `text-m3-error` + chữ `text-m3-on-surface` | Như FR-H09 UI.md mục 5d |

## 6. Trạng thái hiển thị

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Đang tải preview | Hộp thoại mời | Câu có sẵn "Đang tải nội dung mẫu..." (giữ) |
| Đang tải I1 | Tab | 3 khối skeleton xám; đầu trang, thanh thao tác vẫn dùng được |
| Lỗi tải I1 | Tab | Icon `AlertCircle` + "Không tải được giấy mời, vui lòng thử lại." + nút "Thử lại" |
| I1 rỗng, đơn `PENDING` | Tab | 4c-3 dòng 1 |
| I1 rỗng, đơn khác `PENDING` | Tab | 4c-3 dòng 2 |
| Hiệu lực, chưa chốt | Tab | 4c-1 + câu "Ứng viên chưa chọn khung giờ…" |
| Hiệu lực, đã chốt | Tab | 4c-1, dấu "Đã chốt" trên khung chốt |
| Đơn không `INTERVIEW_INVITED` (đã `HIRED`/`REJECTED`/`WITHDRAWN`) có giấy mời | Tab | Như trên nhưng không có nút "Gửi lại giấy mời"; không thêm câu giải thích |
| Có giấy mời cũ | Tab | "Giấy mời trước đó ({n})" thu gọn |
| Đang gửi (I2/I3) | Hộp thoại | Nút gửi khoá, nhãn "Đang gửi..."; nút "Thêm khung giờ", xoá khung khoá |
| Lỗi 400 `INVALID_INTERVIEW_SCHEDULE` | Hộp thoại | Câu `message` backend ở cuối hộp thoại (câu đã nêu số khung) |
| Lỗi 409 `INVITATION_RESEND_NOT_ALLOWED` | Hộp thoại gửi lại | Câu backend ở cuối hộp thoại; làm mới đầu trang + I1 (trạng thái đã đổi ở tab khác) |
| Lỗi 409 `INTERVIEW_INVITATION_CONFLICT` | Hộp thoại | Câu backend; làm mới I1 |
| Gửi thành công | Trang | Đóng hộp thoại; I1 tải lại. I2: badge trạng thái đổi như hiện nay, focus về badge (giữ); I3: focus về tiêu đề "Giấy mời hiện tại" |
| Ứng viên: chưa chốt | Khối ứng viên | 4d có câu hướng dẫn |
| Ứng viên: đã chốt | Khối ứng viên | 4d không câu hướng dẫn, dấu "Đã chốt" |
| Ứng viên: 404 / lỗi khác | Khối ứng viên | Giữ R-T5, R-T7 của FR-U08 |
| Thiếu địa điểm (dữ liệu cũ) | Mọi danh sách khung | "Chưa có dữ liệu" (HR); "Địa điểm: Chưa có dữ liệu" (ứng viên) |
| 403/404/400 của cả trang | Trang | Theo FR-H09 R-T13 / FR-U08, không gọi I1 |

Không có trạng thái "AI đang xử lý": FR-H12 không gọi AI.

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Tab | "Giấy mời phỏng vấn" |
| Tiêu đề hộp thoại | "Mời phỏng vấn — {tên ứng viên}" (giữ) · "Gửi lại giấy mời — {tên ứng viên}" |
| Mô tả hộp thoại mời | Giữ nguyên câu hiện có |
| Khối khung giờ | Tiêu đề "Khung giờ phỏng vấn" · hướng dẫn "Thêm 1–{maxSlots} khung giờ. Nếu chỉ có một khung, khung đó được coi là lịch đã chốt. Giờ theo múi giờ của máy bạn." |
| Một khung | "Khung giờ {n}" · "Thời điểm bắt đầu" · "Địa điểm hoặc link" · `aria-label` nút xoá "Xoá khung giờ {n}" |
| Nút thêm | "Thêm khung giờ" · khi khoá: "Đã đủ {maxSlots} khung giờ." |
| Lỗi frontend (Zod) | "Vui lòng chọn thời điểm phỏng vấn" · "Thời điểm phỏng vấn phải ở trong tương lai" · "Khung giờ này trùng thời điểm với khung giờ {m}." · "Vui lòng nhập địa điểm hoặc link phỏng vấn" · "Địa điểm tối đa 500 ký tự" |
| Lỗi backend (REQUIREMENT R-S8, mục 4.3 — đúng từng chữ) | "Vui lòng thêm ít nhất một khung giờ phỏng vấn." · "Giấy mời có tối đa 5 khung giờ." · "Khung giờ {n}: vui lòng chọn thời điểm phỏng vấn." · "Khung giờ {n}: thời điểm phỏng vấn phải ở trong tương lai." · "Khung giờ {n} trùng thời điểm với khung giờ {m}." · "Khung giờ {n}: vui lòng nhập địa điểm hoặc link phỏng vấn." · "Khung giờ {n}: địa điểm tối đa 500 ký tự." · "Khung giờ {n}: địa điểm chỉ được nằm trên một dòng." · "Chỉ gửi lại giấy mời được khi đơn đang ở trạng thái Đã mời phỏng vấn." · "Giấy mời của đơn này vừa được thay đổi ở nơi khác. Vui lòng tải lại trang." |
| Lưu ý gửi lại (4b) | "Giấy mời mới sẽ thay giấy mời hiện tại. Giấy mời hiện tại vẫn được lưu nguyên văn ở mục "Giấy mời trước đó". Trạng thái đơn không đổi. Ứng viên sẽ nhận thông báo và email kèm các khung giờ mới." · khi đã chốt: "Ứng viên đã chốt khung {HH:mm dd/MM/yyyy}. Khung này sẽ không còn hiệu lực sau khi gửi lại." |
| Nút gửi | "Gửi lời mời" (giữ) · "Gửi lại giấy mời" · đang gửi "Đang gửi..." |
| Lỗi fallback | Gửi: "Gửi lời mời thất bại, vui lòng thử lại." (giữ) · Gửi lại: "Gửi lại giấy mời thất bại, vui lòng thử lại." · Tải I1: "Không tải được giấy mời, vui lòng thử lại." |
| Tab — giấy mời hiệu lực | Tiêu đề "Giấy mời hiện tại" · "Gửi lúc {HH:mm dd/MM/yyyy}" · "Ứng viên chưa chọn khung giờ. Muốn chốt ngay một khung, hãy gửi lại giấy mời chỉ với khung đó." · nút "Gửi lại giấy mời" |
| Tab — rỗng | `PENDING`: "Chưa gửi giấy mời phỏng vấn cho đơn này. Dùng nút "Mời phỏng vấn" ở đầu trang để gửi." · khác: "Đơn này chưa từng được mời phỏng vấn." |
| Danh sách khung | "Khung giờ (giờ Việt Nam)" · địa điểm thiếu "Chưa có dữ liệu" · dấu "Đã chốt" |
| Giấy mời cũ | Nút mở "Giấy mời trước đó ({n})" · "Gửi lúc {t} · Đã được thay bằng giấy mời gửi lúc {t2}" |
| Khối ứng viên | Tiêu đề "Giấy mời phỏng vấn" (giữ) · câu hướng dẫn khi chưa chốt "Hãy nhắn cho nhà tuyển dụng qua tab Trao đổi để xác nhận khung giờ phù hợp." (Q8) · "Địa điểm: " (giữ) · "Đang tải giấy mời..." (giữ) |
| Thông báo (backend, REQUIREMENT R-N2) | Tiêu đề "Nhà tuyển dụng đã gửi lại giấy mời phỏng vấn" · nội dung một dòng theo R-N2 |

Định dạng giờ khung: `HH:mm dd/MM/yyyy`, giờ Việt Nam (`Asia/Ho_Chi_Minh`) ở mọi nơi hiện khung (HR, ứng viên, email)
để hai bên và email thấy cùng một con số. "Gửi lúc" dùng `formatDateTimeVi` (giờ trình duyệt), như "Nộp ngày". Lệch
giữa giờ HR nhập (trình duyệt) và giờ hiển thị (Việt Nam) là giới hạn đã biết (REQUIREMENT B2).

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Hộp thoại | Tab HR | Khối ứng viên |
|---|---|---|---|
| Expanded (≥ `lg`) | `sm:max-w-2xl`; hai ô của khung cùng hàng (lưới 2 cột) | Tiêu đề + nút "Gửi lại" cùng hàng; khung một dòng "thời điểm · địa điểm · dấu" | Như 4d |
| Medium (`sm`–`lg`) | Như expanded | Như expanded; địa điểm tự xuống dòng | Như 4d |
| Compact (< `sm`, 375px) | Gần hết chiều ngang, cuộn dọc bên trong; hai ô xếp dọc; "Thêm khung giờ" `w-full`; nút Huỷ/Gửi cùng hàng | Nút "Gửi lại" `w-full` dưới "Gửi lúc"; thời điểm một dòng, địa điểm + dấu dòng dưới (4f) | Như 4d |

- Không cuộn ngang toàn trang ở 375px; chỉ `TabsList` cuộn ngang (6 tab). Địa điểm, nội dung thư dùng `break-words`;
  ô địa điểm dùng thêm `break-all` cho link không có khoảng trắng.
- Không thanh thao tác `fixed`/`sticky`.

## 9. Khả năng tiếp cận

- Danh sách khung trong hộp thoại là `<fieldset>`; mỗi khung là một `<fieldset>` con có `<legend>` "Khung giờ {n}".
  Lỗi khung gắn `aria-invalid` + `aria-describedby` vào đúng ô; ô địa điểm có `aria-required="true"`.
- Thêm khung: focus vào ô thời điểm của khung mới; vùng `aria-live="polite"` đọc "Đã thêm khung giờ {n}." Xoá khung: đọc
  "Đã xoá khung giờ {n}." và chuyển focus như mục 4a.
- Câu lý do khoá "Thêm khung giờ" hiện thành chữ, gắn `aria-describedby` (không chỉ `title`).
- Danh sách khung chỉ đọc là `<ol>` (thứ tự có nghĩa). Dấu "Đã chốt" là chữ kèm icon `Check` `aria-hidden="true"` — không
  truyền thông tin chỉ bằng màu.
- Tab: tiêu đề "Giấy mời hiện tại" là `h2` (`tabIndex={-1}` để nhận focus sau khi gửi lại); nút "Giấy mời trước đó" có
  `aria-expanded`, `aria-controls`.
- Gửi lần đầu: giữ hành vi hiện có (focus về badge trạng thái, `ApplicationDetailHeader.tsx:15-24`).
- Khối lưu ý 4b có `role="note"`; icon `Info` `aria-hidden="true"`.
- Tương phản: chỉ các cặp đã đo ở UI_GUIDE mục 1c/6 và bảng 5d; không có cặp mới.

## 10. Không được làm

- Không chèn khung giờ vào ô "Nội dung thư mời" hay tự thêm dòng giờ vào thư (REQUIREMENT R-C1).
- Không tô xanh khung đã chốt, đỏ/vàng khung chưa chốt; không icon ✗; không dùng màu làm tín hiệu duy nhất (CLAUDE.md §8).
- Không sửa thanh thao tác FR-H07: không thêm "Gửi lại giấy mời" vào đó, không đổi chữ, vị trí, kiểu ba nút hiện có (Q7).
- Không đặt giấy mời vào tab "CV & điểm"; không đổi vị trí năm tab hiện có; không ẩn tab "Giấy mời phỏng vấn" theo trạng
  thái đơn.
- Không lặp nút "Mời phỏng vấn" trong tab.
- Không nút/ô chọn khung phía ứng viên (FR-U10); không câu hứa hẹn "sẽ có chức năng chọn".
- Không hiện giấy mời cũ phía ứng viên.
- Không sửa được khung hay thư của giấy mời đã gửi (không nút Sửa, không chỉnh tại chỗ).
- Không sắp xếp lại khung theo thời gian ở giao diện (thứ tự HR nhập — R-S7).
- Không hardcode 5; dùng `maxSlots`.
- Không làm mờ (opacity) giấy mời cũ — giảm tương phản.
- Không biến địa điểm dạng URL thành thẻ `<a>` (B4: muốn bấm được phải kiểm scheme URL — ngoài phạm vi); không render
  HTML/Markdown; không `dangerouslySetInnerHTML`.
- Không hiện danh sách khung trong thông báo web (Q5); không sửa `NotificationDropdown.tsx`, `NotificationList.tsx`.
- Không sửa `components/ui`, `features/messageDraft/*`, `ApplicationActionBar.tsx`.
- Không sao chép logo, tên thương hiệu hay CSS của trang tuyển dụng nào (CLAUDE.md §8).
