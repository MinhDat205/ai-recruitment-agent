# FR-H10 — Giao diện

> Trạng thái: ĐÃ DUYỆT 10/10/2026.

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 3 (Tabs, Dialog, Segmented button), 4 (không phán quyết;
dữ liệu thiếu), 5 (rỗng/tải/lỗi), 6 (tương phản). Mã `R-…`, `S1`–`S6`, `Q…` là của `REQUIREMENT.md` cùng thư mục.
Khung trang sửa Job (FR-H02) và trang hồ sơ đơn (FR-H09) không chép lại, chỉ chốt phần FR-H10 thêm vào.

## 1. Trạng thái

> Trạng thái: ĐÃ DUYỆT 10/10/2026.

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| `/hr/jobs/:id/edit` | ★Tab "Câu hỏi sàng lọc" (`?tab=screening`), đặt sau "Rubric chấm điểm", trước "Ứng viên" | Hiện có + ★tab mới | FR-H10 (đã có trong UI_GUIDE mục 7) |
| `/hr/applications/:id` | ★Tab "Sàng lọc" (`?tab=screening`), đặt ngay sau "Giải thích", trước "Lịch sử" (Q6) | Hiện có + ★tab mới | FR-H10 (sửa dòng UI_GUIDE mục 7 ở đợt cuối, REQUIREMENT D3) |

Thứ tự tab của trang hồ sơ đơn sau FR-H10: **CV & điểm → Giải thích → Sàng lọc → Lịch sử → Trao đổi**. Thứ tự hiện có
trong code là CV & điểm → Giải thích → Lịch sử → Trao đổi (`HrApplicationDetailPage.tsx:18`, `:139-142`); FR-H10 chỉ
chèn "Sàng lọc", không đổi thứ tự tương đối của bốn tab cũ, tab mặc định vẫn `cv`. Sàng lọc là tab riêng, không nhúng
vào tab "CV & điểm" (REQUIREMENT M2).

Không route mới. `/hr/jobs/new` không đổi (Q3). Cả hai trang thêm `'screening'` vào `VALID_TABS`; giá trị lạ vẫn
về tab mặc định như hiện tại (`job` ở trang sửa Job, `cv` ở trang hồ sơ đơn).

## 3. Điểm vào

- Trang sửa Job: bấm tab "Câu hỏi sàng lọc", hoặc mở `/hr/jobs/{id}/edit?tab=screening`. Không thêm lối vào từ
  wizard tạo Job; banner "Tin đã được tạo ở trạng thái Nháp…" (`HrJobEditPage.tsx`) giữ nguyên chữ.
- Trang hồ sơ đơn: bấm tab "Sàng lọc", hoặc mở `/hr/applications/{id}?tab=screening`. Không đổi thông báo nào.

## 4. Bố cục (khung ASCII)

### 4a. Desktop (≥ `lg`) — tab "Câu hỏi sàng lọc", Job có câu hỏi

Nằm trong `Card` sẵn có của trang sửa Job (`max-w-5xl`, nền thẻ `m3-surface`).

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Sửa tin tuyển dụng                                                       │
│ [Thông tin tin tuyển dụng][Mẫu giấy mời][Rubric chấm điểm]               │
│ [Câu hỏi sàng lọc][Ứng viên]                                             │
├──────────────────────────────────────────────────────────────────────────┤
│ Câu hỏi sàng lọc                                   [+ Thêm câu hỏi]      │
│ 3/10 câu hỏi                                                             │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ (i) Không hỏi thông tin nhân thân như tuổi, giới tính, tình trạng    │ │
│ │     hôn nhân, tôn giáo, sức khoẻ, dân tộc, ảnh.                      │ │
│ │     Câu trả lời chỉ để bạn tham khảo: hệ thống không tự loại ứng     │ │
│ │     viên, không dùng câu trả lời để chấm điểm hay xếp hạng.          │ │
│ │     Thay đổi chỉ áp dụng cho đơn nộp sau; đơn đã nộp giữ nguyên câu  │ │
│ │     hỏi và câu trả lời lúc nộp.                                      │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ 1. Bạn có thể bắt đầu đi làm từ khi nào?        [↓] [Sửa] [Xoá]      │ │
│ │    Trả lời ngắn · Bắt buộc                                           │ │
│ ├──────────────────────────────────────────────────────────────────────┤ │
│ │ 2. Bạn sẵn sàng làm ca tối không?           [↑] [↓] [Sửa] [Xoá]      │ │
│ │    Có/Không · Không bắt buộc                                         │ │
│ ├──────────────────────────────────────────────────────────────────────┤ │
│ │ 3. Mức lương mong muốn?                     [↑]     [Sửa] [Xoá]      │ │
│ │    Một lựa chọn · Không bắt buộc                                     │ │
│ │    Dưới 10 triệu · 10–15 triệu · Trên 15 triệu                       │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

- Khối lưu ý luôn hiện, kể cả khi danh sách rỗng (R-P1). Không có nút đóng.
- Bốn nút của mỗi câu đều là icon-only có `aria-label` (khung trên ghi chữ cho dễ đọc): ↑ `ArrowUp`, ↓ `ArrowDown`,
  Sửa `Pencil`, Xoá `Trash2` — cùng kiểu nút của tab Rubric. Câu đầu **không render** ↑, câu cuối không render ↓
  (giữ chỗ trống để cột nút thẳng hàng). Bấm ↑/↓ → gọi S5 với toàn bộ thứ tự mới.
- Phương án của Một lựa chọn hiện một dòng, ngăn bằng " · ", tự xuống dòng.
- "3/10 câu hỏi": mẫu số lấy `maxQuestions` từ S1.

### 4b. Desktop — rỗng (Job chưa có câu hỏi)

```
│ Câu hỏi sàng lọc                                   [+ Thêm câu hỏi]      │
│ 0/10 câu hỏi                                                             │
│ ┌ (khối lưu ý như 4a) ┐                                                  │
│                                                                          │
│ Tin này chưa có câu hỏi sàng lọc. Ứng viên nộp đơn sẽ không phải trả     │
│ lời câu nào.                                                             │
│ [+ Thêm câu hỏi đầu tiên]                                                │
```

### 4c. Hộp thoại "Thêm câu hỏi" / "Sửa câu hỏi"

`Dialog` `sm:max-w-md`, cùng khổ với `CriterionFormDialog.tsx:141`.

```
┌──────────────────────────────────────────────────────┐
│ Thêm câu hỏi                                     [X] │
│ Câu trả lời chỉ để bạn tham khảo, không dùng để chấm │
│ điểm hay xếp hạng. Không hỏi thông tin nhân thân.    │
│                                                      │
│ Nội dung câu hỏi                                     │
│ ┌──────────────────────────────────────────────────┐ │
│ │                                                  │ │
│ └──────────────────────────────────────────────────┘ │
│                                         0/300 ký tự  │
│ Loại câu hỏi                                         │
│ [✓ Trả lời ngắn][  Một lựa chọn ][  Có/Không  ]      │
│                                                      │
│ (chỉ khi Một lựa chọn)                               │
│ Các phương án (2–10)                                 │
│ ┌─────────────────────────────────────────┐ [Xoá]    │
│ │ Dưới 10 triệu                           │          │
│ └─────────────────────────────────────────┘          │
│ ┌─────────────────────────────────────────┐ [Xoá]    │
│ │ 10–15 triệu                             │          │
│ └─────────────────────────────────────────┘          │
│ [+ Thêm phương án]                                   │
│                                                      │
│ [ ] Bắt buộc trả lời                                 │
│                                                      │
│ (lỗi backend nếu có)                                 │
│                              [Huỷ]  [Lưu câu hỏi]    │
└──────────────────────────────────────────────────────┘
```

- Mặc định khi thêm: loại "Trả lời ngắn", ô "Bắt buộc trả lời" **không tick** (R-Q5).
- Chuyển sang "Một lựa chọn" lần đầu: tạo sẵn 2 ô phương án trống. Chuyển khỏi "Một lựa chọn": ẩn phần phương án
  và **không gửi** `options`; quay lại thì hiện lại các ô đã gõ trong phiên hộp thoại.
- Nút "Xoá" phương án là icon `X` có `aria-label`, khoá khi chỉ còn 2 ô; "Thêm phương án" khoá khi đủ 10.
- Kiểm ở frontend (Zod) đúng giới hạn R-Q3, R-Q4 để báo sớm, **trên giá trị đã bỏ khoảng trắng hai đầu** (R-Q4b);
  bộ đếm "{n}/300 ký tự" cũng đếm sau khi bỏ khoảng trắng. Backend vẫn là chốt chặn: `400 INVALID_SCREENING_QUESTION`
  hiện câu `message` của backend ở cuối hộp thoại (backend không trả tên trường).

### 4d. Desktop — tab "Sàng lọc" ở trang hồ sơ đơn, `state = CAPTURED`

```
│ [CV & điểm][Giải thích][Sàng lọc][Lịch sử][Trao đổi]                     │
├──────────────────────────────────────────────────────────────────────────┤
│ Câu trả lời sàng lọc                                                     │
│ Câu hỏi và câu trả lời được lưu nguyên văn lúc nộp đơn                   │
│ (17:45 28/08/2026). Chỉ để tham khảo, không dùng để chấm điểm hay        │
│ xếp hạng.                                                                │
│                                                                          │
│ 1. Bạn có thể bắt đầu đi làm từ khi nào?                                 │
│    Trả lời ngắn · Bắt buộc                                               │
│    Đầu tháng 11, sau khi bàn giao công việc hiện tại.                    │
│ ──────────────────────────────────────────────────────────────────────── │
│ 2. Bạn sẵn sàng làm ca tối không?                                        │
│    Có/Không · Không bắt buộc                                             │
│    Có                                                                    │
│ ──────────────────────────────────────────────────────────────────────── │
│ 3. Mức lương mong muốn?                                                  │
│    Một lựa chọn · Không bắt buộc                                         │
│    Ứng viên không trả lời câu này.                                       │
```

- Câu hỏi: `text-m3-title-md`; dòng loại + bắt buộc: `text-m3-body-sm` `m3-on-surface-variant`; câu trả lời:
  `text-m3-body-md` `m3-on-surface`, `whitespace-pre-wrap break-words`.
- "Có", "Không", chữ phương án đã chọn và câu trả lời ngắn dùng **cùng** kiểu chữ, cùng màu (R-N4).
- "Ứng viên không trả lời câu này." dùng `m3-on-surface-variant` — dữ liệu thiếu hiện rõ (UI_GUIDE mục 4).
- Không hiện danh sách phương án gốc, không đối chiếu với câu hỏi hiện tại của Job.

### 4e. Tab "Sàng lọc" — hai trạng thái rỗng

```
NO_QUESTIONS   Tin tuyển dụng chưa có câu hỏi sàng lọc vào thời điểm ứng viên nộp đơn
               (17:45 28/08/2026).

NOT_CAPTURED   Đơn này được nộp trước khi hệ thống ghi nhận câu trả lời sàng lọc, nên
               không có câu trả lời để hiển thị.
```

Không có nút hành động. Đây là **ngoại lệ có chủ đích** so với quy tắc "một dòng chữ + một hành động gợi ý" của
UI_GUIDE mục 5: với đơn đã nộp không có hành động hợp lệ nào (HR không bổ sung hay sửa được câu trả lời của ứng
viên). Theo tiền lệ R-T8 của FR-H09 (`../H09/REQUIREMENT.md` mục 3.4, `../H09/UI.md` mục 6): câu trạng thái tab
Giải thích cũng không kèm nút. Không hiện bộ câu hỏi **hiện tại** của Job ở đây.

### 4f. Điện thoại (375px) — tab "Câu hỏi sàng lọc"

Chiều rộng nội dung ≈ 240px (375 − rail 80 − `main p-4` 2×16 − padding thẻ; như FR-H09 UI.md mục 4d).

```
┌────────────────────────────┐
│ [Rubric chấm điểm][Câu h…→ │  (TabsList cuộn ngang)
├────────────────────────────┤
│ Câu hỏi sàng lọc           │
│ 3/10 câu hỏi               │
│ ┌────────────────────────┐ │
│ │  + Thêm câu hỏi        │ │
│ └────────────────────────┘ │
│ (i) Không hỏi thông tin    │
│ nhân thân như tuổi, …      │
│ ────────────────────────── │
│ 1. Bạn có thể bắt đầu đi   │
│    làm từ khi nào?         │
│    Trả lời ngắn · Bắt buộc │
│    [↓]  [Sửa]  [Xoá]       │
│ ────────────────────────── │
│ 2. Bạn sẵn sàng làm ca tối │
│    không?                  │
│    Có/Không · Không bắt…   │
│    [↑] [↓] [Sửa] [Xoá]     │
└────────────────────────────┘
```

Hàng nút của mỗi câu xuống dưới nội dung; mỗi nút cao tối thiểu 48px (UI_GUIDE mục 1h). Hộp thoại chiếm gần hết
chiều ngang; segmented button "Loại câu hỏi" xếp **dọc** ba nút dưới `sm`.

### 4g. Điện thoại (375px) — tab "Sàng lọc"

Chiều rộng nội dung ≈ 240px như 4f. `TabsList` cuộn ngang như hiện có (`HrApplicationDetailPage.tsx:138`); "Sàng
lọc" là tab thứ 3, có thể nằm một phần ngoài khung ở 375px — chấp nhận như "Lịch sử"/"Trao đổi" hiện nay.

```
┌────────────────────────────┐
│ ← Chuyên viên Chăm sóc…    │
│ Quốc Huy                   │
│ [Chờ duyệt]                │
│ Nộp ngày 09:00 05/10/2026  │
│ (thanh thao tác xếp dọc)   │
│ ┌─────────┬──────────┬─────┐
│ │CV & điểm│Giải thích│Sàng→│  (cuộn ngang: … Sàng lọc · Lịch sử · Trao đổi)
│ └─────────┴──────────┴─────┘
├────────────────────────────┤
│ Câu trả lời sàng lọc       │
│ Câu hỏi và câu trả lời     │
│ được lưu nguyên văn lúc    │
│ nộp đơn (09:00 05/10/2026).│
│ Chỉ để tham khảo, không    │
│ dùng để chấm điểm hay xếp  │
│ hạng.                      │
│                            │
│ 1. Bạn có thể bắt đầu đi   │
│    làm từ khi nào?         │
│    Trả lời ngắn · Bắt buộc │
│    Đầu tháng 11, sau khi   │
│    bàn giao công việc hiện │
│    tại.                    │
│ ────────────────────────── │
│ 2. Bạn sẵn sàng làm ca tối │
│    không?                  │
│    Có/Không · Không bắt…   │
│    Có                      │
│ ────────────────────────── │
│ 3. Mức lương mong muốn?    │
│    Một lựa chọn · Không…   │
│    Ứng viên không trả lời  │
│    câu này.                │
└────────────────────────────┘
```

Một cột, câu hỏi và câu trả lời tự xuống dòng (`break-words`); không cuộn ngang toàn trang. Hai trạng thái rỗng
(4e) hiện thành đoạn chữ xuống dòng tự nhiên ở cùng vị trí.

## 5. Component

### 5a. File mới (chốt tên — `REQUIREMENT.md` mục 7.3 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/features/screening/types.ts` | Kiểu của S1–S6, `ScreeningQuestionType`, `ScreeningCaptureState` |
| `frontend/src/features/screening/api.ts` | Gọi S1–S6 |
| `frontend/src/features/screening/queries.ts` | Hook TanStack Query: danh sách theo `jobId` (S1), 4 mutation S2–S5 (làm mới S1 sau khi xong), bản chụp theo `applicationId` (S6) |
| `frontend/src/features/screening/screeningLabels.ts` | Nhãn tiếng Việt của loại câu hỏi, chuỗi cố định mục 7 |
| `frontend/src/features/screening/ScreeningQuestionsTab.tsx` | Tab ở trang sửa Job: tiêu đề, bộ đếm, khối lưu ý, danh sách/rỗng (4a, 4b) |
| `frontend/src/features/screening/ScreeningNotice.tsx` | Khối lưu ý (R-P1), dùng ở tab và (bản rút gọn) ở hộp thoại |
| `frontend/src/features/screening/ScreeningQuestionRow.tsx` | Một câu: nội dung, loại, bắt buộc, phương án, nút ↑/↓/Sửa/Xoá + hộp xác nhận xoá |
| `frontend/src/features/screening/ScreeningQuestionFormDialog.tsx` | Hộp thoại thêm/sửa (4c), React Hook Form + Zod |
| `frontend/src/features/screening/QuestionTypeSegmentedControl.tsx` | Segmented button 3 lựa chọn |
| `frontend/src/features/applicationDetail/ScreeningTab.tsx` | Tab "Sàng lọc" ở trang hồ sơ đơn (4d, 4e), dùng hook S6 của `features/screening` |

### 5b. File có sẵn bị sửa

| File | Thay đổi |
|---|---|
| `frontend/src/pages/HrJobEditPage.tsx` | `VALID_TABS` thêm `'screening'`; thêm `TabsTrigger`/`TabsContent` giữa "Rubric chấm điểm" và "Ứng viên"; `TabsList` thêm `max-w-full justify-start overflow-x-auto overflow-y-hidden` (cùng chuỗi class với `HrApplicationDetailPage.tsx:138`) để 5 tab không làm cuộn ngang cả trang ở 375px |
| `frontend/src/pages/HrApplicationDetailPage.tsx` | `VALID_TABS` thành `['cv','explanation','screening','history','messages']`; thêm `TabsTrigger`/`TabsContent` "Sàng lọc" giữa "Giải thích" và "Lịch sử" (Q6); không đổi vị trí bốn tab cũ |

Không sửa `components/ui`, `JobApplyForm.tsx`, `HrJobCreatePage.tsx`, `ApplicationsTab.tsx`, `CandidatesTable.tsx`.

### 5c. Dùng lại có sẵn

| Có sẵn | Cách dùng |
|---|---|
| `Tabs`, `Card`, `Button`, `Dialog`, `Input`, `Textarea`, `Checkbox`, `Label` | Như các tab hiện có |
| Mẫu hộp thoại + hộp xác nhận xoá của `features/rubric` (`CriterionFormDialog.tsx`, `CriterionRow.tsx:96-127`) | Cùng cấu trúc, cùng kiểu nút; **không** import hay sửa file rubric |
| Mẫu `ToneSegmentedControl.tsx` (FR-C07) | Cùng cách làm (`role="radiogroup"`, `role="radio"`, roving tabindex, icon `Check` cho lựa chọn đang chọn); viết component riêng, **không** sửa component của FR-C07 |
| `extractErrorMessage` theo kiểu `HrJobEditPage.tsx:33` | Đọc `message` của backend, fallback câu mục 7 |

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Khối lưu ý (R-P1) | `bg-m3-surface-container` | `text-m3-on-surface`; viền `border-m3-outline-variant`; icon `Info` `text-m3-on-surface` | Trung tính, **không** dùng `m3-warning`/`m3-error`; chữ phụ không đặt trên nền này (UI_GUIDE mục 6) |
| Danh sách câu hỏi | `bg-m3-surface` | viền `border-m3-outline-variant`, ngăn dòng cùng màu | Chỉ để phân khối |
| Số thứ tự + nội dung câu | — | `text-m3-on-surface`, `text-m3-title-md` | — |
| Dòng "loại · bắt buộc", bộ đếm "3/10", "0/300 ký tự", phương án, câu lý do khoá nút | — | `text-m3-on-surface-variant` | Chỉ trên `m3-surface` (4.83:1) |
| "Bắt buộc" / "Không bắt buộc" | — | Cùng một màu, chữ thường | Không badge màu, không dấu `*` đỏ |
| Nút "Thêm câu hỏi" | `bg-m3-primary` | `text-m3-on-primary`, icon `Plus` (`Button` mặc định) | Như nút "Thêm tiêu chí" (`RubricTab.tsx:97-98`) |
| Nút ↑/↓/Sửa | — | Icon-only `variant="outline"` `size="icon-sm"` (`ArrowUp`, `ArrowDown`, `Pencil`), có `aria-label` | Như nút Sửa tiêu chí (`CriterionRow.tsx:87-95`); dưới `sm` vùng chạm ≥ 48px |
| Nút Xoá | — | Icon-only `Trash2` `text-m3-error`, viền `border-m3-error`, `aria-label` | Như nút Xoá tiêu chí (`CriterionRow.tsx:98-106`) |
| Segmented "Loại câu hỏi" | chọn: `bg-m3-primary-container` | chọn: `text-m3-on-primary-container` + icon `Check`; chưa chọn: `bg-m3-surface` `text-m3-on-surface`; viền `border-m3-outline` | Như `ToneSegmentedControl` |
| Câu trả lời ở tab Sàng lọc (mọi loại) | — | `text-m3-on-surface`, `text-m3-body-md` | **Một** màu cho Có, Không, chữ phương án, câu trả lời ngắn |
| "Ứng viên không trả lời câu này.", câu chú thích đầu tab | — | `text-m3-on-surface-variant` | Thẻ trắng |
| Câu trạng thái `NO_QUESTIONS`/`NOT_CAPTURED` | — | `text-m3-on-surface` | Như R-T8 của FR-H09 |
| Lỗi tải / lỗi lưu | — | Icon `AlertCircle` `text-m3-error` + chữ `text-m3-on-surface` | Như FR-H09 UI.md mục 5d |
| Lỗi validation dưới trường trong hộp thoại | `m3-surface` | `text-m3-error` | 4.74:1 trên nền trắng của `Dialog` |

## 6. Trạng thái hiển thị

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Đang tải S1 | Tab Câu hỏi sàng lọc | Khối lưu ý hiện ngay; danh sách là 3 dòng skeleton xám; nút "Thêm câu hỏi" khoá |
| Lỗi tải S1 | Tab Câu hỏi sàng lọc | Icon `AlertCircle` + "Không tải được câu hỏi sàng lọc." + nút "Thử lại" |
| 403/404 ở S1 | Tab Câu hỏi sàng lọc | Như các tab khác của trang sửa Job (không thêm xử lý riêng): câu lỗi tải + "Thử lại" |
| Rỗng (0 câu) | Tab Câu hỏi sàng lọc | 4b |
| Có câu | Tab Câu hỏi sàng lọc | 4a |
| Đủ `maxQuestions` | Tab Câu hỏi sàng lọc | Nút "Thêm câu hỏi" khoá + câu "Đã đủ 10 câu hỏi. Xoá bớt một câu để thêm câu mới." hiện thành chữ dưới tiêu đề, gắn `aria-describedby` |
| Đang lưu (S2/S3) | Hộp thoại | Nút "Lưu câu hỏi" khoá, nhãn "Đang lưu..." |
| Lỗi lưu 400 | Hộp thoại | Lỗi Zod dưới trường tương ứng; `INVALID_SCREENING_QUESTION` và `SCREENING_QUESTION_LIMIT_REACHED` → câu `message` của backend ở cuối hộp thoại |
| Đang xoá (S4) | Hộp xác nhận | Nút "Xoá" khoá, nhãn "Đang xoá..." |
| Đang đổi thứ tự (S5) | Danh sách | Mọi nút ↑/↓ khoá tới khi xong; **không** đổi thứ tự lạc quan trước khi backend trả về |
| Lỗi S4/S5 | Dưới danh sách | Icon `AlertCircle` + câu backend, fallback mục 7; S5 lỗi `INVALID_SCREENING_ORDER` → tải lại S1 (danh sách đã đổi ở tab khác) |
| Đang tải S6 | Tab Sàng lọc | Skeleton 3 khối câu hỏi; đầu trang, thanh thao tác vẫn dùng được |
| Lỗi tải S6 | Tab Sàng lọc | Icon `AlertCircle` + "Không tải được dữ liệu, vui lòng thử lại." + "Thử lại" (câu chung của FR-H09) |
| `CAPTURED` | Tab Sàng lọc | 4d |
| `NO_QUESTIONS` | Tab Sàng lọc | 4e dòng 1 |
| `NOT_CAPTURED` | Tab Sàng lọc | 4e dòng 2 |
| Câu không bắt buộc bỏ trống | Tab Sàng lọc | "Ứng viên không trả lời câu này." — không ẩn câu |
| 403/404/400 của cả trang hồ sơ đơn | Trang | Theo R-T13 của FR-H09, không gọi S6 |

Không có trạng thái "AI đang xử lý": FR-H10 không gọi AI.

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Tab trang sửa Job | "Câu hỏi sàng lọc" |
| Tab trang hồ sơ đơn | "Sàng lọc" |
| Tiêu đề tab soạn | "Câu hỏi sàng lọc" |
| Bộ đếm | "{n}/{maxQuestions} câu hỏi" |
| Lưu ý (R-P1), dòng 1 | "Không hỏi thông tin nhân thân như tuổi, giới tính, tình trạng hôn nhân, tôn giáo, sức khoẻ, dân tộc, ảnh." |
| Lưu ý, dòng 2 | "Câu trả lời chỉ để bạn tham khảo: hệ thống không tự loại ứng viên, không dùng câu trả lời để chấm điểm hay xếp hạng." |
| Lưu ý, dòng 3 | "Thay đổi chỉ áp dụng cho đơn nộp sau; đơn đã nộp giữ nguyên câu hỏi và câu trả lời lúc nộp." |
| Rỗng | "Tin này chưa có câu hỏi sàng lọc. Ứng viên nộp đơn sẽ không phải trả lời câu nào." · nút "Thêm câu hỏi đầu tiên" |
| Nút thêm | "Thêm câu hỏi" |
| Khoá khi đủ | "Đã đủ {maxQuestions} câu hỏi. Xoá bớt một câu để thêm câu mới." |
| Loại câu hỏi | "Trả lời ngắn" · "Một lựa chọn" · "Có/Không" |
| Bắt buộc | "Bắt buộc" · "Không bắt buộc" |
| Nút trong dòng (icon-only) | `aria-label` "Đưa câu {n} lên" · "Đưa câu {n} xuống" · "Sửa câu {n}" · "Xoá câu {n}" |
| Hộp xác nhận xoá | Tiêu đề "Xoá câu hỏi?" · nội dung "Câu hỏi sẽ không còn xuất hiện với ứng viên nộp đơn sau. Đơn đã nộp vẫn giữ nguyên câu hỏi và câu trả lời." · nút "Huỷ", "Xoá" (đang xoá "Đang xoá...") |
| Hộp thoại — tiêu đề | "Thêm câu hỏi" · "Sửa câu hỏi" |
| Hộp thoại — mô tả | "Câu trả lời chỉ để bạn tham khảo, không dùng để chấm điểm hay xếp hạng. Không hỏi thông tin nhân thân." |
| Hộp thoại — trường | "Nội dung câu hỏi" · "{n}/300 ký tự" · "Loại câu hỏi" · "Các phương án (2–10)" · "Thêm phương án" · `aria-label` "Xoá phương án {n}" · "Bắt buộc trả lời" |
| Hộp thoại — nút | "Huỷ" · "Lưu câu hỏi" (đang lưu "Đang lưu...") |
| Lỗi frontend | "Vui lòng nhập nội dung câu hỏi" · "Tối đa 300 ký tự" · "Cần ít nhất 2 phương án" · "Tối đa 10 phương án" · "Phương án không được để trống" · "Mỗi phương án tối đa 100 ký tự" · "Các phương án không được trùng nhau" |
| Lỗi backend `INVALID_SCREENING_QUESTION` (REQUIREMENT R-Q4b, đúng từng chữ) | "Vui lòng nhập nội dung câu hỏi." · "Nội dung câu hỏi tối đa 300 ký tự." · "Câu hỏi một lựa chọn cần từ 2 đến 10 phương án." · "Phương án không được để trống." · "Mỗi phương án tối đa 100 ký tự." · "Các phương án không được trùng nhau." · "Chỉ câu hỏi một lựa chọn mới có phương án." |
| Lỗi backend khác (câu chuẩn hoá) | `SCREENING_QUESTION_LIMIT_REACHED`: "Mỗi tin tuyển dụng có tối đa 10 câu hỏi sàng lọc." · `INVALID_SCREENING_ORDER`: "Danh sách câu hỏi đã thay đổi, vui lòng tải lại." · `SCREENING_QUESTION_NOT_FOUND`: "Không tìm thấy câu hỏi sàng lọc." |
| Lỗi fallback | Tải: "Không tải được câu hỏi sàng lọc." · Lưu: "Lưu câu hỏi thất bại, vui lòng thử lại." · Xoá: "Xoá câu hỏi thất bại, vui lòng thử lại." · Đổi thứ tự: "Đổi thứ tự thất bại, vui lòng thử lại." |
| Tab Sàng lọc — tiêu đề | "Câu trả lời sàng lọc" |
| Tab Sàng lọc — chú thích | "Câu hỏi và câu trả lời được lưu nguyên văn lúc nộp đơn ({HH:mm dd/MM/yyyy}). Chỉ để tham khảo, không dùng để chấm điểm hay xếp hạng." |
| Trả lời Có/Không | "Có" · "Không" |
| Không trả lời | "Ứng viên không trả lời câu này." |
| `NO_QUESTIONS` | "Tin tuyển dụng chưa có câu hỏi sàng lọc vào thời điểm ứng viên nộp đơn ({HH:mm dd/MM/yyyy})." |
| `NOT_CAPTURED` | "Đơn này được nộp trước khi hệ thống ghi nhận câu trả lời sàng lọc, nên không có câu trả lời để hiển thị." |

Ngày giờ định dạng `vi-VN` như "Nộp ngày" ở đầu trang hồ sơ đơn (FR-H09 UI.md mục 7): `formatDateTimeVi`,
`HH:mm dd/MM/yyyy`, giờ theo trình duyệt (L10 ở `REQUIREMENT.md` mục 12). Câu ở dòng lưu ý 1 có chữ
"tuổi" — hợp lệ (lời nhắc cho HR, không phải câu hỏi hay field); `srs-guard` nguyên tắc 13 lệnh 1 không khớp chữ
có dấu.

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Tab Câu hỏi sàng lọc | Hộp thoại | Tab Sàng lọc |
|---|---|---|---|
| Expanded (≥ `lg`) | Tiêu đề + nút "Thêm câu hỏi" cùng hàng; mỗi câu: nội dung trái, nhóm nút phải | `sm:max-w-md`; segmented ngang (xuống dòng nếu thiếu chỗ) | Một cột |
| Medium (`sm`–`lg`) | Như expanded; nhóm nút xuống dòng nếu thiếu chỗ | Như expanded | Một cột |
| Compact (< `sm`, 375px) | Nút "Thêm câu hỏi" `w-full` dưới tiêu đề; nhóm nút của mỗi câu xuống dưới nội dung, mỗi nút ≥ 48px | Gần hết chiều ngang, cuộn dọc bên trong; segmented xếp dọc; ô phương án + nút Xoá cùng hàng | Một cột, `break-words` |

- Không cuộn ngang toàn trang ở 375px; chỉ `TabsList` được cuộn ngang (cả hai trang).
- Không thanh thao tác `fixed`/`sticky`.

## 9. Khả năng tiếp cận

- Tiêu đề tab là `h2`; mỗi câu ở tab Sàng lọc là một mục của `<ol>` (thứ tự có nghĩa).
- Danh sách câu hỏi ở tab soạn là `<ol>`; nút ↑/↓ icon-only có `aria-label` mục 7. Sau khi đổi thứ tự xong, focus
  đặt vào **cùng nút** ở vị trí mới của câu vừa di chuyển (nếu nút đó không còn — câu thành đầu/cuối — đặt vào nút
  còn lại của câu đó); một vùng `aria-live="polite"` đọc "Đã chuyển câu "{nội dung rút gọn}" tới vị trí {n}." — một câu dùng cho cả hai chiều ↑/↓ (L9 ở
  `REQUIREMENT.md` mục 12)
- Segmented "Loại câu hỏi": `role="radiogroup"` có `aria-labelledby` trỏ nhãn "Loại câu hỏi"; lựa chọn nhận ra
  bằng icon `Check`, không chỉ bằng màu; phím mũi tên đổi lựa chọn (như `ToneSegmentedControl`).
- Bộ đếm ký tự gắn vào ô nội dung bằng `aria-describedby`; lỗi trường gắn `aria-invalid` + `aria-describedby`.
- Câu lý do khoá nút "Thêm câu hỏi" hiện thành chữ và gắn `aria-describedby` (không chỉ `title`, như FR-H09 UI.md
  mục 9).
- Khối lưu ý có `role="note"`; icon `Info` `aria-hidden="true"`.
- "Bắt buộc" là chữ, không chỉ là dấu `*`.
- Tương phản: chỉ dùng các cặp đã đo ở UI_GUIDE mục 1c/6 và bảng 5d; không có cặp mới.

## 10. Không được làm

- Không tô màu, icon ✓/✗, badge hay in đậm theo câu trả lời; "Có" và "Không" giống hệt nhau về hình thức (R-N4,
  CLAUDE.md §8).
- Không thêm cột, bộ lọc, tuỳ chọn sắp xếp hay biểu tượng theo câu trả lời ở tab Ứng viên, `/hr/candidates`,
  dashboard (R-N2).
- Không đặt tab "Sàng lọc" ở vị trí khác "sau Giải thích, trước Lịch sử"; không đổi thứ tự bốn tab hiện có (Q6).
- Không đặt câu trả lời sàng lọc vào tab "CV & điểm" cạnh điểm hay trong khối evidence; không dùng kiểu trích dẫn
  evidence hay khối "Do AI tạo" cho câu trả lời (là lời ứng viên, không phải AI).
- Không hiện bộ câu hỏi hiện tại của Job ở tab Sàng lọc, không đánh dấu "câu hỏi đã thay đổi" (R-S3).
- Không ẩn khối lưu ý nhân thân, không cho đóng (R-P1).
- Không dùng `m3-warning`/`m3-error` cho khối lưu ý; không dấu `*` đỏ cho câu bắt buộc.
- Không kéo-thả để sắp xếp (khó dùng bằng bàn phím và trên 375px); chỉ nút ↑/↓.
- Không đổi thứ tự lạc quan trước khi S5 trả về.
- Không thêm bước câu hỏi sàng lọc vào wizard `/hr/jobs/new` (Q3).
- Không render HTML/Markdown; không `dangerouslySetInnerHTML`.
- Không sửa `components/ui`, `ToneSegmentedControl`, file trong `features/rubric`.
- Không sao chép logo, tên thương hiệu hay CSS của trang tuyển dụng nào (CLAUDE.md §8).
