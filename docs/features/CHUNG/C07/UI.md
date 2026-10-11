# FR-C07 — Giao diện

> Trạng thái: ĐÃ HOÀN THÀNH (10/10/2026). Duyệt: 10/10/2026.

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 3 (Segmented button, Linear progress, Dialog), 4 ("Nội dung do
AI tạo"), 5 (thao tác AI đồng bộ), 6 (tương phản). Mã `R-…`, `A1`/`A2`, `Q…` là của `REQUIREMENT.md` cùng thư mục.
Khung soạn, vùng tin, trạng thái tab "Trao đổi" là của FR-C06 (`../C06/UI.md`) — không chép lại, chỉ chốt phần
FR-C07 thêm vào.

## 1. Trạng thái

> Trạng thái: ĐÃ HOÀN THÀNH (10/10/2026). Duyệt: 10/10/2026.

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| ★`/hr/applications/:id?tab=messages` | Tab "Trao đổi" của FR-C06 — **mở rộng khung soạn**, không route mới | ★Mới ở UI_GUIDE (của FR-H09) | FR-C07 |
| ★`/candidate/applications/:id?tab=messages` | Như trên, phía ứng viên | ★Mới ở UI_GUIDE (của FR-U08) | FR-C07 |

Không thêm route, không thêm tab, không thêm mục điều hướng. Cột FR của hai dòng UI_GUIDE sửa ở đợt cuối
(REQUIREMENT D4).

## 3. Điểm vào

- Nút **"Soạn bằng AI"** trong khung soạn của tab "Trao đổi", cùng hàng với "Đính kèm tệp". Là điểm vào duy nhất.
- Khung soạn chỉ có khi `canSend = true` (C06) → đơn `WITHDRAWN` không có nút (không hiện nút khoá).
- Đổi tab/rời trang: khối soạn nháp đóng, bản nháp chưa dùng bị bỏ (R-D1); lời gọi đang chạy bị bỏ qua kết quả.

## 4. Bố cục (khung ASCII)

Một component dùng chung `MessageDraftPanel` cho cả hai phía (`side = 'hr' | 'candidate'`), nằm **trong**
`MessageComposer`, **phía trên** ô nhập tin. Khối mở ra/thu lại tại chỗ, đẩy ô nhập xuống — không hộp thoại, không
lớp phủ, nên người dùng vẫn gõ được vào ô nhập (R-K3, UI_GUIDE mục 5). Ví dụ là phía HR.

### 4a. Khung soạn khi khối đóng (chỉ thêm một nút so với C06)

```
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Nhập tin nhắn…                                                       │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ [Paperclip] Đính kèm tệp  [Sparkles] Soạn bằng AI   PDF, DOCX, PNG, …    │
│                                                    0/4000       [ Gửi ]  │
```

### 4b. Khối mở — chọn tình huống và giọng văn (desktop ≥ `lg`)

```
│ ┌─ Soạn bằng AI ──────────────────────────────────────────────── [X] ─┐ │
│ │ Tình huống                                                          │ │
│ │ (•) Đề nghị bổ sung thông tin                                       │ │
│ │ ( ) Nhắc lịch phỏng vấn                                [khoá]       │ │
│ │     Chỉ dùng được khi đơn đang chờ phỏng vấn và đã có giấy mời.     │ │
│ │ ( ) Cảm ơn đã ứng tuyển                                             │ │
│ │ ( ) Thông báo kết quả                                  [khoá]       │ │
│ │     Chỉ dùng được khi đơn đã có kết quả Trúng tuyển hoặc Bị từ chối.│ │
│ │ ( ) Tự mô tả                                                        │ │
│ │                                                                     │ │
│ │ Giọng văn   ┌──────────────────┬──────────────────┐                 │ │
│ │             │ [✓] Trang trọng  │    Thân thiện    │                 │ │
│ │             └──────────────────┴──────────────────┘                 │ │
│ │                                              [ Tạo bản nháp ]       │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Nhập tin nhắn…                                                       │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ [Paperclip] Đính kèm tệp  [Sparkles] Soạn bằng AI   …                    │
│                                                    0/4000       [ Gửi ]  │
```

Chọn "Tự mô tả" → hiện ngay dưới lựa chọn đó:

```
│ │ (•) Tự mô tả                                                        │ │
│ │     ┌───────────────────────────────────────────────────────────┐   │ │
│ │     │ Ví dụ: hỏi ứng viên có thể bắt đầu làm việc từ khi nào     │   │ │
│ │     └───────────────────────────────────────────────────────────┘   │ │
│ │                                                         0/500       │ │
```

Tình huống mặc định khi mở khối: tình huống **đầu tiên còn dùng được** theo thứ tự bảng R-S1; giọng văn mặc định
"Trang trọng". Khối giữ lựa chọn khi đóng/mở lại trong cùng lần xem tab.

### 4c. Đang soạn (R-K3, UI_GUIDE mục 5)

```
│ │ (các lựa chọn bị khoá)                                              │ │
│ │ ████████████████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │ │
│ │ Đang soạn bản nháp…                          [ Tạo bản nháp ] khoá  │ │
```

- Khoá: các lựa chọn tình huống, giọng văn, ô "Tự mô tả", nút "Tạo bản nháp".
- **Không** khoá: ô nhập tin, "Đính kèm tệp", "Gửi" của C06, nút [X] đóng khối.

### 4d. Có bản nháp — khối "Do AI tạo" (UI_GUIDE mục 4)

```
│ │ ┌──────────────────────────────────────────────────────────────────┐ │ │
│ │ │ [Sparkles] Do AI tạo                                             │ │ │
│ │ │ Chào bạn Quốc Huy,                                               │ │ │
│ │ │ Công ty TNHH Thử Nghiệm Ánh Dương xin nhắc lịch phỏng vấn vị trí │ │ │
│ │ │ Nhân viên Hành chính văn phòng vào lúc 09:00 17/10/2026 tại Tầng │ │ │
│ │ │ 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng. Bạn vui lòng mang theo  │ │ │
│ │ │ [giấy tờ cần mang]. …                                            │ │ │
│ │ └──────────────────────────────────────────────────────────────────┘ │ │
│ │ [ Dùng bản nháp ]  [ Tạo lại ]  [ Bỏ qua ]                           │ │
```

- Ví dụ dùng giấy mời A2 của seed test (`seed-test.sql:430-432`): địa điểm có thật trong ngữ cảnh nên AI dùng
  nguyên văn; giờ `scheduled_at` = ngày nạp seed + 7 ngày, 02:00 UTC → hiện **09:00** giờ Việt Nam (ngày trong ví dụ
  chỉ minh hoạ). Thứ không có trong ngữ cảnh (giấy tờ cần mang) mới thành chỗ trống `[…]` (R-D4).
- Bản nháp hiện văn bản thuần, giữ xuống dòng, không HTML (như C06 R-G1).
- Dưới khối AI vẫn giữ phần chọn tình huống/giọng văn (thu gọn thành một dòng tóm tắt "Nhắc lịch phỏng vấn ·
  Trang trọng · [Đổi]") để "Tạo lại" với lựa chọn khác.

### 4e. Sau "Dùng bản nháp"

```
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Chào bạn Quốc Huy,                                                   │ │
│ │ Công ty TNHH Thử Nghiệm Ánh Dương xin nhắc lịch phỏng vấn …          │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ [Sparkles] Bản nháp do AI soạn. Hãy đọc lại, thay các chỗ trong [ngoặc   │
│ vuông] và sửa nếu cần trước khi gửi.                                     │
│ [Paperclip] Đính kèm tệp  [Sparkles] Soạn bằng AI   …                    │
│                                                  412/4000       [ Gửi ]  │
```

- Khối soạn nháp đóng; bản nháp nằm trong ô nhập, sửa như chữ tự gõ. Focus về ô nhập, con trỏ ở đầu.
- Dòng nhắc hiện tới khi: gửi thành công, hoặc ô nhập bị xoá trắng. Dòng nhắc không chặn "Gửi" (Q12).
- Nút "Gửi" là nút của C06; **không** có nút "Gửi luôn" hay "Gửi bản nháp" ở bất kỳ đâu.

### 4f. Ô nhập đã có chữ khi bấm "Dùng bản nháp" (Q4)

`Dialog` (component có sẵn) nhỏ, modal:

```
┌────────────────────────────────────────────────┐
│ Thay nội dung đang soạn?                       │
│ Ô nhập đang có nội dung. Bản nháp sẽ thay thế  │
│ toàn bộ nội dung này.                          │
│                                                │
│   [ Giữ nội dung đang soạn ] [ Thay bằng bản nháp ] │
└────────────────────────────────────────────────┘
```

"Giữ nội dung đang soạn" đóng hộp thoại, bản nháp vẫn nằm trong khối AI. Tệp đã chọn không bị đụng trong mọi
trường hợp. Ô nhập chỉ có khoảng trắng coi là rỗng (không hỏi).

### 4g. Lỗi (UI_GUIDE mục 5)

```
│ │ [AlertCircle] AI phản hồi quá lâu, vui lòng thử lại.                │ │
│ │                                              [ Thử lại ]            │ │
```

Câu lỗi nằm trong khối soạn nháp, các lựa chọn mở lại; "Thử lại" gọi lại A2 với cùng lựa chọn. Ô nhập không bị đụng.

### 4h. Điện thoại (375px)

```
┌──────────────────────────────┐
│ ┌─ Soạn bằng AI ───────[X]─┐ │
│ │ Tình huống               │ │
│ │ (•) Đề nghị bổ sung      │ │
│ │     thông tin            │ │
│ │ ( ) Nhắc lịch phỏng vấn  │ │
│ │     Chỉ dùng được khi …  │ │
│ │ …                        │ │
│ │ Giọng văn                │ │
│ │ ┌───────────┬──────────┐ │ │
│ │ │[✓]Trang   │ Thân     │ │ │
│ │ │   trọng   │ thiện    │ │ │
│ │ └───────────┴──────────┘ │ │
│ │ ┌──────────────────────┐ │ │
│ │ │    Tạo bản nháp      │ │ │
│ │ └──────────────────────┘ │ │
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ Nhập tin nhắn…           │ │
│ └──────────────────────────┘ │
│ [Paperclip] Đính kèm tệp     │
│ [Sparkles] Soạn bằng AI      │
│ …                            │
└──────────────────────────────┘
```

Nút trong khối `w-full`, xếp dọc; "Dùng bản nháp" trên cùng. Không `fixed`/`sticky`.

## 5. Component

### 5a. File mới

| File | Vai trò |
|---|---|
| `frontend/src/features/messageDraft/types.ts` | `DraftScenario`, `DraftTone`, `DraftUnavailableReason`, `ScenarioOption`, `MessageDraftRequest`, `MessageDraftResponse` |
| `frontend/src/features/messageDraft/api.ts` | A1, A2 theo `side` (tiền tố `/hr/…` hoặc `/candidates/…`); A2 đặt `timeout: 35000` cho riêng request này |
| `frontend/src/features/messageDraft/queries.ts` | Query A1 (chỉ tải khi mở khối; không tải lại định kỳ; không thử lại với 4xx); mutation A2 (không tự thử lại) |
| `frontend/src/features/messageDraft/draftLabels.ts` | Nhãn tình huống, giọng văn, lý do khoá (mục 7) — một chỗ |
| `frontend/src/features/messageDraft/MessageDraftPanel.tsx` | Khối 4b–4d, 4g. Props: `side`, `applicationId`, `onUseDraft(draft: string)`, `onClose()`, `onReadOnlyConflict(message: string)` (A1/A2 trả 409 `CONVERSATION_READ_ONLY` — mục 6) |
| `frontend/src/features/messageDraft/ToneSegmentedControl.tsx` | Segmented button 2 lựa chọn (UI_GUIDE mục 3) dựng bằng `button` có sẵn, không sửa `components/ui` |
| `frontend/src/features/messageDraft/ReplaceDraftDialog.tsx` | Hộp thoại 4f |

### 5b. File có sẵn bị sửa

| File | Sửa gì |
|---|---|
| `frontend/src/features/messages/MessageComposer.tsx` | Thêm nút "Soạn bằng AI", state mở/đóng khối, render `MessageDraftPanel` trên ô nhập, nhận `onUseDraft` (ghi `text`, hỏi theo 4f), dòng nhắc 4e; truyền `onReadOnlyConflict` có sẵn của `MessageComposer` (`MessageComposer.tsx:30,35`) xuống `MessageDraftPanel` nguyên trạng — cùng đường xử lý 409 như khi gửi tin. **Không đổi** `submit`, kiểm tệp, bộ đếm, phím tắt, cách gọi M2. Sửa comment dòng 22-23 (bỏ ý "không có nút Soạn bằng AI") |

Không sửa: `MessagesTab`, `MessageList`, `MessageBubble`, `api.ts`/`queries.ts` của `features/messages`, hai trang đơn,
`components/ui/*`. `features/messageDraft` không import `features/applicationDetail`, `candidateApplicationDetail`,
`scoring`; `features/messages` import `features/messageDraft` (một chiều).

### 5c. Dùng lại có sẵn

| Có sẵn | Cách dùng |
|---|---|
| `Button` | "Soạn bằng AI" `variant="outline" size="sm"` (như "Đính kèm tệp"); "Tạo bản nháp", "Dùng bản nháp" Filled (mặc định) `size="sm"`; "Tạo lại", "Thử lại", "Giữ nội dung đang soạn" `variant="outline"`; "Bỏ qua", [X] `variant="ghost"` |
| `Textarea` | Ô "Tự mô tả" |
| `Dialog` | Hộp thoại 4f |
| `extractErrorMessage` (`lib/httpError.ts`) | Câu lỗi từ `message` của backend, fallback mục 7 |
| `lucide-react` | `Sparkles`, `X`, `Check`, `AlertCircle` |
| Radio | `<input type="radio">` gốc trong `fieldset`/`legend` (dự án chưa có component radio; không thêm vào `components/ui`) |

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Khung khối soạn nháp | `bg-m3-surface` | `border border-m3-outline-variant`, `rounded-m3-sm` | Chỉ phân khối; chữ `m3-on-surface`, chữ phụ `m3-on-surface-variant` (4.83:1) |
| Tiêu đề khối "Soạn bằng AI" | — | `text-m3-on-surface`, `text-m3-title-md` | |
| Lý do khoá dưới lựa chọn | — | `text-m3-on-surface-variant` | Trên `m3-surface`; lựa chọn bị khoá còn được nhận ra bằng chữ lý do, không chỉ bằng độ mờ |
| Nhãn lựa chọn bị khoá | — | `text-m3-on-surface-variant` | Không dùng `opacity` làm chữ dưới 4.5:1 |
| Segmented — đang chọn | `bg-m3-primary-container` | `text-m3-on-primary-container` (6.23:1), viền `border-m3-outline`, icon `Check` | Chọn được nhận ra bằng icon, không chỉ màu |
| Segmented — không chọn | `bg-m3-surface` | `text-m3-on-surface`, viền `border-m3-outline` | Viền điều khiển ≥ 3:1 |
| Thanh tiến trình | Rãnh `bg-m3-surface-container-highest` | Thanh `bg-m3-primary` rộng 1/3 + `animate-m3-linear-progress` + `motion-reduce:animate-none` | Linear progress không xác định theo UI_GUIDE mục 1h (token khai sẵn ở `@theme` của `index.css`, mẫu `features/resumes/LinearProgress.tsx`) — REQUIREMENT L12 |
| Khối "Do AI tạo" | `bg-m3-surface-container-high` | `border border-m3-outline-variant`; chữ **chỉ** `text-m3-on-surface` (13.06:1), nhãn "Do AI tạo" cũng `m3-on-surface` | UI_GUIDE mục 4: không dùng `m3-on-surface-variant` trong khối này |
| Dòng nhắc sau khi dùng nháp | (thẻ trắng) | Icon `Sparkles` + chữ `text-m3-on-surface-variant`, `text-sm` | |
| Câu lỗi | (thẻ trắng) | Icon `AlertCircle` `text-m3-error` + chữ `text-m3-on-surface` | Như câu lỗi khung soạn C06 |
| Bộ đếm "Tự mô tả" | — | `text-m3-on-surface-variant`; vượt 500 → `text-m3-error` | Trên `m3-surface` (4.74:1) |

Không màu đỏ/vàng/xanh nào cho tình huống hay bản nháp; không màu theo ngưỡng.

## 6. Trạng thái hiển thị

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Đơn `WITHDRAWN` | Tab | Không khung soạn → không nút (C06). Nếu khối đang mở mà A2 trả 409 `CONVERSATION_READ_ONLY`: xử lý như lỗi 409 khi gửi của C06 (gọi `onReadOnlyConflict`, khung soạn biến mất) |
| Đang tải A1 | Khối | Skeleton xám 4–5 dòng thay danh sách tình huống; nút "Tạo bản nháp" khoá |
| A1 lỗi mạng/5xx | Khối | `AlertCircle` + "Không tải được danh sách tình huống." + "Thử lại" |
| A1 400/403/404 | Khối | "Không tìm thấy cuộc trao đổi." (như C06 R-G4) |
| Tình huống bị khoá | Lựa chọn | Radio `disabled` + câu lý do (mục 7) ngay dưới nhãn |
| "Tự mô tả" rỗng hoặc > 500 | Khối | Nút "Tạo bản nháp" khoá; bộ đếm `{n}/500`, vượt thì đổi màu lỗi |
| Đang soạn | Khối | Mục 4c; `aria-busy` trên khối |
| Có bản nháp | Khối | Mục 4d |
| "Dùng bản nháp", ô nhập rỗng | Khung soạn | Mục 4e |
| "Dùng bản nháp", ô nhập có chữ | Hộp thoại | Mục 4f |
| Người dùng gõ trong lúc chờ | Khung soạn | Chữ giữ nguyên; khi bấm "Dùng bản nháp" mới hỏi theo 4f |
| Hết thời gian chờ (504) | Khối | Câu `AI_TIMEOUT` + "Thử lại" |
| Lỗi AI (502/503) | Khối | Câu của backend + "Thử lại" |
| Chạm hạn mức (429) | Khối | "Bạn đã gửi quá nhiều yêu cầu, vui lòng thử lại sau ít phút" (câu có sẵn) + "Thử lại" |
| 409 `DRAFT_SCENARIO_UNAVAILABLE` (trạng thái đơn đổi giữa chừng) | Khối | Câu của backend; tải lại A1 để cập nhật khoá |
| 400 | Khối | Câu của backend |
| Mạng lỗi / quá 35 s phía trình duyệt | Khối | "Chưa soạn được bản nháp, vui lòng thử lại." + "Thử lại" |
| Đóng khối khi đang soạn | Khối | Đóng ngay; kết quả về sau bị bỏ, không tự điền vào ô nhập |
| Gửi thành công (C06) | Khung soạn | Ô nhập trống, dòng nhắc 4e biến mất, khối soạn nháp đóng |

Không có trạng thái "thiếu dữ liệu" hiển thị riêng: thiếu lịch phỏng vấn thể hiện bằng tình huống bị khoá.

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Nút mở khối | "Soạn bằng AI" |
| Tiêu đề khối | "Soạn bằng AI" |
| Nút đóng khối (`aria-label`) | "Đóng soạn bằng AI" |
| Nhóm tình huống (`legend`) | "Tình huống" |
| `REQUEST_MORE_INFO` | "Đề nghị bổ sung thông tin" |
| `INTERVIEW_REMINDER` | "Nhắc lịch phỏng vấn" |
| `THANK_FOR_APPLYING` | "Cảm ơn đã ứng tuyển" |
| `RESULT_NOTICE` | "Thông báo kết quả" |
| `ASK_PROGRESS` | "Hỏi tiến độ" |
| `THANK_AFTER_INTERVIEW` | "Cảm ơn sau phỏng vấn" |
| `REQUEST_RESCHEDULE` | "Xin dời lịch phỏng vấn" |
| `CUSTOM` | "Tự mô tả" |
| Lý do khoá `RESULT_NOT_FINAL` | "Chỉ dùng được khi đơn đã có kết quả Trúng tuyển hoặc Bị từ chối." |
| Lý do khoá `NO_ACTIVE_INTERVIEW` | "Chỉ dùng được khi đơn đang chờ phỏng vấn và đã có giấy mời." |
| Lý do khoá `NO_INTERVIEW` | "Chỉ dùng được khi đơn đã có giấy mời phỏng vấn." |
| Ô "Tự mô tả" — nhãn ẩn | "Mô tả mục đích tin nhắn" |
| Ô "Tự mô tả" — placeholder phía HR | "Ví dụ: hỏi ứng viên có thể bắt đầu làm việc từ khi nào" |
| Ô "Tự mô tả" — placeholder phía ứng viên | "Ví dụ: hỏi công ty có cần bổ sung bằng cấp không" |
| Bộ đếm | "{n}/500" |
| Nhóm giọng văn | "Giọng văn" |
| `FORMAL` / `FRIENDLY` | "Trang trọng" / "Thân thiện" |
| Nút tạo | "Tạo bản nháp" |
| Đang soạn | "Đang soạn bản nháp…" |
| Nhãn khối AI | "Do AI tạo" |
| Dòng tóm tắt lựa chọn | "{tình huống} · {giọng văn}" · nút "Đổi" |
| Nút dùng | "Dùng bản nháp" |
| Nút tạo lại | "Tạo lại" |
| Nút bỏ | "Bỏ qua" |
| Dòng nhắc sau khi dùng | "Bản nháp do AI soạn. Hãy đọc lại, thay các chỗ trong [ngoặc vuông] và sửa nếu cần trước khi gửi." |
| Hộp thoại — tiêu đề | "Thay nội dung đang soạn?" |
| Hộp thoại — nội dung | "Ô nhập đang có nội dung. Bản nháp sẽ thay thế toàn bộ nội dung này." |
| Hộp thoại — nút | "Giữ nội dung đang soạn" · "Thay bằng bản nháp" |
| Nút thử lại | "Thử lại" |
| Lỗi tải A1 | "Không tải được danh sách tình huống." |
| A1 400/403/404 | "Không tìm thấy cuộc trao đổi." |
| Fallback lỗi A2 (mạng, không có `message`) | "Chưa soạn được bản nháp, vui lòng thử lại." |
| Backend 504 `AI_TIMEOUT` | "AI phản hồi quá lâu, vui lòng thử lại." |
| Backend 503 `AI_UNAVAILABLE` | "Dịch vụ AI đang bận hoặc tạm thời gián đoạn, vui lòng thử lại sau." |
| Backend 502 `AI_INVALID_OUTPUT` | "AI trả về kết quả không hợp lệ, vui lòng thử lại." |
| Backend 409 `DRAFT_SCENARIO_UNAVAILABLE` | "Tình huống này chưa dùng được với trạng thái hiện tại của đơn." |
| Backend 400 `INVALID_DRAFT_REQUEST` — tình huống | "Tình huống không hợp lệ." |
| Backend 400 `INVALID_DRAFT_REQUEST` — giọng văn | "Vui lòng chọn giọng văn." |
| Backend 400 `INVALID_DRAFT_REQUEST` — mục đích rỗng | "Vui lòng mô tả mục đích tin nhắn." |
| Backend 400 `INVALID_DRAFT_REQUEST` — mục đích dài | "Mô tả mục đích tối đa 500 ký tự." |
| Backend 400 `INVALID_DRAFT_REQUEST` — body hỏng | "Yêu cầu soạn nháp không hợp lệ." |
| Backend 409 `CONVERSATION_READ_ONLY` | Câu có sẵn của C06: "Đơn đã rút, không thể gửi thêm tin nhắn" |
| Backend 429 | Câu có sẵn: "Bạn đã gửi quá nhiều yêu cầu, vui lòng thử lại sau ít phút" |

Chuỗi của khung soạn C06 giữ nguyên.

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Khối soạn nháp | Segmented | Nút trong khối | Hàng nút của khung soạn |
|---|---|---|---|---|
| Expanded (≥ `lg`) | Rộng bằng khung soạn, trên ô nhập | Cạnh nhãn "Giọng văn", cùng hàng | Căn phải, cùng hàng | "Đính kèm tệp", "Soạn bằng AI", gợi ý tệp cùng hàng |
| Medium (`sm`–`lg`) | Như expanded | Như expanded | Như expanded | Gợi ý tệp xuống dòng nếu thiếu chỗ (như C06) |
| Compact (< `sm`, 375px) | Như mục 4h; khung trong ≈ 311px (ứng viên) / hẹp hơn 80px (HR, rail) | Xuống dòng dưới nhãn, hai nửa bằng nhau `w-full`, nhãn tự xuống dòng | `w-full`, xếp dọc | Hai nút xếp dọc, `self-start` |

- Không cuộn ngang ở 375px; bản nháp và lý do khoá tự ngắt (`break-words`).
- Khối AI không giới hạn chiều cao; bản nháp dài thì trang cuộn bình thường (không cuộn lồng).

## 9. Khả năng tiếp cận

- Nút "Soạn bằng AI" có `aria-expanded` và `aria-controls` trỏ tới khối.
- Khối là `section` có `aria-labelledby` = tiêu đề "Soạn bằng AI"; mở khối thì focus vào radio đang chọn; đóng
  khối thì focus về nút "Soạn bằng AI".
- Tình huống: `fieldset` + `legend` "Tình huống", radio gốc (phím mũi tên của trình duyệt); câu lý do khoá gắn
  bằng `aria-describedby`.
- Giọng văn: `role="radiogroup"` + `aria-label="Giọng văn"`, mỗi nút `role="radio"` + `aria-checked`; phím mũi tên
  đổi lựa chọn.
- Thanh tiến trình: `role="progressbar"` + `aria-label="Đang soạn bản nháp"` (không giá trị — dạng không xác định).
- Vùng kết quả/lỗi trong khối là `aria-live="polite"`; câu lỗi `role="alert"`.
- Khối "Do AI tạo": nhãn là chữ thật (icon `Sparkles` `aria-hidden`), không chỉ dựa vào màu nền.
- Hộp thoại 4f: focus vào "Giữ nội dung đang soạn" khi mở (lựa chọn không mất dữ liệu); `Esc` = giữ.
- Mọi nút chỉ có icon có `aria-label`. Vùng chạm ≥ 40px (trong khối) như UI_GUIDE mục 3.
- Tương phản: chỉ dùng các cặp ở bảng 5d, đều đã đo ở UI_GUIDE mục 1c/6; không có cặp mới cần đo.

## 10. Không được làm

- Không nút "Gửi luôn"/"Gửi bản nháp"; không tự gọi API gửi tin; không tự điền bản nháp vào ô nhập khi chưa bấm
  "Dùng bản nháp" (R-D2, UI_GUIDE mục 4).
- Không lưu bản nháp hay lựa chọn vào `localStorage`/`sessionStorage`/URL (R-D1).
- Không đánh dấu "Do AI tạo" trên tin đã gửi trong vùng tin (C06 R-G1, R-D3).
- Không hiện điểm, thứ hạng, tiêu chí, giải thích AI, nhận xét về hồ sơ ở bất kỳ đâu trong khối.
- Không chỉ ẩn "Thông báo kết quả" ở frontend rồi coi như đã chặn — backend chặn (R-S3); frontend hiện khoá **kèm
  lý do**, không ẩn lựa chọn.
- Không hiện nút "Soạn bằng AI" khoá ở đơn `WITHDRAWN` (không có khung soạn thì không có nút).
- Không hộp thoại modal cho phần chọn tình huống (sẽ khoá việc gõ tay); không khoá ô nhập/nút Gửi khi đang soạn.
- Không màu đỏ/vàng/xanh theo tình huống; không màu theo ngưỡng; không dùng `m3-on-surface-variant` trong khối AI.
- Không render bản nháp bằng HTML/Markdown; không tự biến URL thành liên kết.
- Không toast/snackbar (dự án chưa có) — kết quả và lỗi hiện tại chỗ.
- Không sửa `components/ui`; không viết hai bộ component cho hai phía.
- Không sao chép logo, tên thương hiệu hay CSS của sản phẩm trợ lý viết nào (CLAUDE.md §8).
