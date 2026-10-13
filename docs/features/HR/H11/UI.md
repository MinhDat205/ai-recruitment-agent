# FR-H11 — Giao diện

> Trạng thái: ĐÃ HOÀN THÀNH (13/10/2026). Duyệt: 13/10/2026.

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 1h (linear progress), 3 (component), 4 ("Nội dung do AI tạo"),
5 (thao tác AI đồng bộ), 6 (tương phản). Mã `R-…`, `J1` (sinh bản nháp — một request, chọn `parts`), `J2` (lưu nháp), `Q…` là của `REQUIREMENT.md` cùng thư mục. Ba bước thủ
công của wizard (Thông tin cơ bản, Lương & thời hạn, Mẫu giấy mời) là của FR-H02 — không chép lại, chỉ chốt phần FR-H11
thêm vào.

## 1. Trạng thái

> Trạng thái: ĐÃ HOÀN THÀNH (13/10/2026). Duyệt: 13/10/2026.

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| `/hr/jobs/new` | Tạo tin — **màn hình cũ bị mở rộng**: thêm khối "Tạo tin bằng AI" và chế độ AI của wizard (5 bước) | Hiện có (`UI_GUIDE.md:356`, cột FR đã ghi `★FR-H11`) | FR-H02, FR-H11, FR-C05 |
| `/hr/jobs/:id/edit?tab=rubric&created=1` | Đích sau khi "Lưu nháp" — **không sửa** | Hiện có | FR-H02, FR-H03 |

Không thêm route, không thêm query string cho chế độ AI (chế độ là state của trang; tải lại trang = về luồng thủ công,
mọi gợi ý mất — R-L, mục 6 REQUIREMENT). Cột FR dòng `/hr/jobs/new` sửa ở đợt cuối (REQUIREMENT D4).

## 3. Điểm vào

- Khối **"Tạo tin bằng AI"** phía trên wizard ở `/hr/jobs/new` (vào từ nút "Tạo tin" ở `/hr/jobs` như hiện nay). Là
  điểm vào duy nhất. Không có ở trang sửa tin (REQUIREMENT mục 6).
- Rời trang hoặc tải lại: mọi gợi ý, token và chữ chưa lưu mất; không hỏi (như luồng thủ công).

## 4. Bố cục (khung ASCII)

Trang giữ `HrLayout title="Tạo tin tuyển dụng"` và `Card` `max-w-2xl` của wizard; khối AI là một `section` riêng
**phía trên** `Card` wizard, cùng độ rộng. Không hộp thoại cho phần nhập mô tả, không lớp phủ — HR vẫn gõ được vào
biểu mẫu trong lúc chờ (UI_GUIDE mục 5).

### 4a. Mặc định — chưa dùng AI (desktop ≥ `lg`)

```
Tạo tin tuyển dụng
┌──────────────────────────────────────────────────────────────────────┐
│ [Sparkles] Tạo tin bằng AI                                           │
│ Mô tả vị trí bằng lời thường. AI gợi ý bản nháp tin, tiêu chí đánh   │
│ giá và câu hỏi sàng lọc; bạn xem và quyết định từng phần trước khi  │
│ lưu.                                           [ Tạo tin bằng AI ]   │
└──────────────────────────────────────────────────────────────────────┘
┌─ Tạo tin tuyển dụng ─────────────────────────────────────────────────┐
│ (1) Thông tin cơ bản ── (2) Lương & thời hạn ── (3) Mẫu giấy mời     │
│ … wizard thủ công 3 bước của FR-H02, không đổi …                     │
└──────────────────────────────────────────────────────────────────────┘
```

### 4b. Khối nhập mô tả mở

```
┌─ Tạo tin bằng AI ───────────────────────────────────────────── [X] ─┐
│ Mô tả vị trí cần tuyển                                              │
│ ┌─────────────────────────────────────────────────────────────────┐ │
│ │ Ví dụ: Cần tuyển 2 lập trình viên Java backend tại Đà Nẵng,     │ │
│ │ toàn thời gian, lương 15 - 20 triệu…                            │ │
│ └─────────────────────────────────────────────────────────────────┘ │
│ Nêu những gì bạn biết: vị trí, công việc, yêu cầu, mức lương, địa   │
│ điểm, hình thức làm việc, quyền lợi, hạn nộp. Thông tin không nêu   │
│ sẽ được để trống. Không nhập thông tin cá nhân của bất kỳ ai.       │
│                                                          0/3000     │
│ Mỗi lần tạo dùng 1 lượt gọi AI.            [ Tạo bản nháp ]         │
└─────────────────────────────────────────────────────────────────────┘
```

- [X] khi **chưa** tạo lần nào: đóng khối, về 4a. Sau khi đã tạo: [X] không có; khối thu gọn theo 4c-2.
- "Tạo bản nháp" khoá khi ô rỗng/chỉ khoảng trắng hoặc > 3000 (bộ đếm đổi màu lỗi).

### 4c. Đang tạo — một request, ba phần song song (R-N1, R-K3H-2)

```
┌─ Tạo tin bằng AI ───────────────────────────────────────────────────┐
│ (ô mô tả khoá)                                                      │
│ Bản nháp tin          ██████████░░░░░░░░░░░░░░░  Đang tạo…         │
│ Tiêu chí đánh giá     ██████████░░░░░░░░░░░░░░░  Đang tạo…         │
│ Câu hỏi sàng lọc      ██████████░░░░░░░░░░░░░░░  Đang tạo…         │
│                                     [ Tạo bản nháp ] khoá           │
└─────────────────────────────────────────────────────────────────────┘
┌─ Tạo tin tuyển dụng ─────────────────────────────────────────────────┐
│ (1) Thông tin cơ bản ─ (2) Lương & thời hạn ─ (3) Tiêu chí đánh giá  │
│ ─ (4) Câu hỏi sàng lọc ─ (5) Mẫu giấy mời                            │
│ … biểu mẫu vẫn gõ được …                                             │
└──────────────────────────────────────────────────────────────────────┘
```

- Bấm "Tạo bản nháp" → **một** request J1 với `parts` đủ ba phần (1 lượt) → wizard chuyển sang **chế độ AI** ngay (5
  bước), giữ nguyên chữ HR đã gõ, giữ bước đang đứng.
- Mỗi phần **được yêu cầu** trong request đang chạy có một dòng "Đang tạo…" với linear progress; phần không có trong
  request giữ nguyên trạng thái cũ (vd "Tạo lại" chỉ phần tiêu chí → chỉ dòng "Tiêu chí đánh giá" chạy).
- Response về một lần, rồi mỗi dòng hiện trạng thái riêng theo ô của phần đó: `SUCCEEDED` → "[Check] Đã có gợi ý";
  `FAILED` → câu lỗi của phần đó + "Thử lại" (4i).
- Trong lúc một request J1 đang chạy: mọi nút sinh ("Tạo bản nháp", "Tạo lại tất cả", "Tạo lại" và "Thử lại" của từng
  phần) khoá — tại một thời điểm chỉ có một request J1. Biểu mẫu, nút "Giữ"/"Bỏ"/"Sửa" vẫn dùng được.

**4c-2. Sau khi tạo xong — khối thu gọn:**

```
┌─ Tạo tin bằng AI ───────────────────────────────────────────────────┐
│ Mô tả của bạn: "Cần tuyển 2 lập trình viên Java backend tại Đà…"     │
│ [ Sửa mô tả ]   [ Tạo lại tất cả ]                                   │
└─────────────────────────────────────────────────────────────────────┘
```

"Sửa mô tả" mở lại ô (4b, không [X]); "Tạo lại tất cả" gửi **một** request J1 với cả 3 phần và mô tả hiện tại (1
lượt), xử lý kết quả theo 4f.

### 4d. Bước 1 "Thông tin cơ bản" ở chế độ AI

```
┌─ Tạo tin tuyển dụng ─────────────────────────────────────────────────┐
│ (1)● Thông tin cơ bản ─ (2) ─ (3) ─ (4) ─ (5)                         │
│ ┌─ Cần bổ sung ───────────────────────────────────────────────────┐ │
│ │ Bắt buộc để mở tin: Tỉnh/thành                                   │ │
│ │ Nên có: Hạn ứng tuyển, Quyền lợi                                 │ │
│ │ Luôn do bạn nhập: trọng số tiêu chí, mẫu giấy mời phỏng vấn.     │ │
│ └─────────────────────────────────────────────────────────────────┘ │
│ ┌─ [Sparkles] Do AI tạo · Bản nháp tin ──────────────── [Tạo lại] ─┐ │
│ │ Tiêu đề: Lập trình viên Java Backend                              │ │
│ │ Ngành nghề: Công nghệ thông tin - Phần mềm                        │ │
│ │ Tỉnh/thành: Chưa có dữ liệu                                       │ │
│ │ Loại hình: Toàn thời gian · Hình thức: Tại văn phòng              │ │
│ │ Lương: 15.000.000 – 20.000.000 VND                                │ │
│ │ Hạn ứng tuyển: Chưa có dữ liệu                                    │ │
│ │ Mô tả: Phát triển API với Spring Boot, viết test, review code…    │ │
│ │ Yêu cầu: Tối thiểu 2 năm kinh nghiệm Java; biết PostgreSQL…       │ │
│ │ Quyền lợi: Chưa có dữ liệu                                        │ │
│ │                                     [ Điền vào biểu mẫu ]         │ │
│ └───────────────────────────────────────────────────────────────────┘ │
│ Tiêu đề tin tuyển dụng   [Sparkles] Do AI gợi ý                      │
│ [ Lập trình viên Java Backend                                     ]  │
│ Mô tả công việc          [Sparkles] Do AI gợi ý                      │
│ [ …                                                               ]  │
│ Yêu cầu ứng viên · Quyền lợi (ô mới, chỉ ở chế độ AI)                 │
│ Ngành nghề · Tỉnh/thành · Loại hình · Hình thức (như FR-H02)         │
│                                   [ Quay lại ]      [ Tiếp theo ]    │
└──────────────────────────────────────────────────────────────────────┘
```

- **Khối "Cần bổ sung"** (R-M): nằm đầu `Card` wizard ở **mọi** bước của chế độ AI, chỉ khi có mục. Ba dòng theo
  `need`: `SAVE` "Bắt buộc để lưu nháp", `OPEN` "Bắt buộc để mở tin", `OPTIONAL` "Nên có"; dòng cố định "Luôn do bạn
  nhập…" (R-M3). Một mục ẩn khi trường đó đã có giá trị (R-M4). Mỗi tên trường là nút chữ đưa tới bước chứa trường đó
  và focus ô.
- **"Tạo lại"** của khối "Bản nháp tin" → J1 với `parts = [JOB]` (1 lượt). Tương tự "Tạo lại" ở bước 3 → `[CRITERIA]`,
  bước 4 → `[QUESTIONS]`.
- **Khối "Bản nháp tin"** (UI_GUIDE mục 4): xem trước, chưa có hiệu lực. Giá trị `null` hiện "Chưa có dữ liệu" (không ẩn dòng —
  UI_GUIDE mục 4 "Dữ liệu thiếu"). Mã ngành/tỉnh hiện bằng nhãn lấy từ danh mục (`useCatalogsQuery`), lương định dạng như
  `features/jobs/formatSalary.ts`. Mô tả/yêu cầu/quyền lợi hiện tối đa 3 dòng + "Xem đầy đủ" (mở rộng tại chỗ).
- **"Điền vào biểu mẫu"**: ghi giá trị vào các trường của bước 1–2 (kể cả trường `null` → để trống, không xoá chữ HR đã
  gõ ở trường đó), rồi thu khối thành một dòng: "[Sparkles] Đã điền bản nháp tin vào biểu mẫu. Hãy đọc lại từng trường
  trước khi lưu. · [Tạo lại]". Lần đầu không có trường nào HR đã sửa thì không hỏi; có thì theo 4f.
- **Dấu "Do AI gợi ý"** cạnh nhãn của mỗi trường được điền từ AI, mất khi HR sửa giá trị trường đó (so với giá trị đã
  điền). Trường AI trả `null` không có dấu.
- Ô **"Quyền lợi"** (`Textarea`, tuỳ chọn) chỉ có ở chế độ AI, ngay dưới "Yêu cầu ứng viên"; chú thích dưới ô: "Khi lưu,
  quyền lợi được đặt ở cuối phần mô tả công việc, sau dòng "Quyền lợi:"." (R-P7).

### 4e. Bước 3 "Tiêu chí đánh giá" (R-C, R-W)

```
┌─ Tạo tin tuyển dụng ─────────────────────────────────────────────────┐
│ (1) ─ (2) ─ (3)● Tiêu chí đánh giá ─ (4) ─ (5)                        │
│ Trọng số do bạn nhập. Có thể lưu nháp khi tổng chưa đủ 100%; cần đủ  │
│ 100% để mở tin.                                                      │
│ ┌─ [Sparkles] Do AI tạo · Gợi ý tiêu chí ───────────── [Tạo lại] ─┐ │
│ │ Kinh nghiệm Java backend                                         │ │
│ │ Số năm và mức độ làm việc với Java, Spring Boot trong dự án thật.│ │
│ │                                          [ Giữ ]  [ Bỏ ]         │ │
│ │ ──────────────────────────────────────────────────────────────── │ │
│ │ Cơ sở dữ liệu PostgreSQL                                         │ │
│ │ Thiết kế bảng, viết truy vấn, tối ưu chỉ mục.                    │ │
│ │                                          [ Giữ ]  [ Bỏ ]         │ │
│ │                                               [ Giữ tất cả ]     │ │
│ └──────────────────────────────────────────────────────────────────┘ │
│ Tiêu chí đã giữ (1)                                                  │
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ Viết test tự động        [Sparkles] Do AI gợi ý                   │ │
│ │ Mức độ viết unit test, integration test.                          │ │
│ │ Trọng số (%) [        ]                       [ Sửa ]  [ Bỏ ]     │ │
│ │ Vui lòng nhập trọng số.                                           │ │
│ └──────────────────────────────────────────────────────────────────┘ │
│ Tổng trọng số: 0/100                                                 │
│                                   [ Quay lại ]      [ Tiếp theo ]    │
└──────────────────────────────────────────────────────────────────────┘
```

- Gợi ý **chưa chọn** mặc định (Q12). "Giữ" chuyển gợi ý xuống "Tiêu chí đã giữ" (thẻ trắng — đã là của HR); "Bỏ" xoá
  khỏi danh sách gợi ý. "Giữ tất cả" giữ mọi gợi ý còn lại (khoá khi đủ 10 tiêu chí đã giữ — R-W3).
- Tiêu chí đã giữ: ô **"Trọng số (%)"** `Input type="number"` `min=0.01` `max=100` `step=0.01`, **trống**, không
  placeholder số (R-W1). "Sửa" mở sửa tại chỗ tên (≤ 200) + mô tả trong thẻ, nút "Xong"/"Huỷ". "Bỏ" xoá khỏi danh sách đã
  giữ (không quay lại phần gợi ý). Dấu "Do AI gợi ý" mất khi tên hoặc mô tả bị sửa.
- "Tổng trọng số: {n}/100" cập nhật theo ô đã nhập; > 100 → chữ lỗi "Tổng trọng số vượt quá 100%." và chặn lưu.
- Lỗi tại ô (trống, ≤ 0, > 100) chỉ hiện sau khi ô bị chạm hoặc sau lần bấm "Lưu nháp".
- 0 gợi ý: dòng "AI chưa gợi ý được tiêu chí nào từ mô tả này. Bạn có thể thêm tiêu chí ở trang sửa tin sau khi lưu."
  + "Tạo lại".
- Wizard **không** có nút "Thêm tiêu chí" tự gõ (REQUIREMENT mục 6).

### 4f. Tạo lại khi HR đã sửa (Q11)

**Bản nháp tin** — bấm "Điền vào biểu mẫu" của kết quả mới khi có trường HR đã sửa tay (khác giá trị AI đã điền, hoặc
HR tự gõ trước khi điền):

```
┌──────────────────────────────────────────────────────┐
│ Ghi đè các trường bạn đã sửa?                        │
│ Bạn đã sửa 2 trường: Tiêu đề, Lương tối thiểu.       │
│ Bản nháp mới sẽ điền vào các trường còn lại.         │
│                                                      │
│   [ Chỉ điền trường chưa sửa ]  [ Ghi đè tất cả ]    │
└──────────────────────────────────────────────────────┘
```

`Dialog` modal; focus mặc định "Chỉ điền trường chưa sửa"; `Esc` = đóng, không điền gì.

**Tiêu chí, câu hỏi** — "Tạo lại" của phần đó: thay **toàn bộ gợi ý chưa chọn** bằng gợi ý mới; tiêu chí/câu hỏi **đã
giữ** (kể cả đã sửa, đã nhập trọng số) giữ nguyên. Gợi ý mới trùng tên/nội dung (không phân biệt hoa thường) với mục đã
giữ thì không hiện. Không hỏi.

**"Tạo lại tất cả"** = **một** request J1 với cả ba phần (1 lượt), áp quy tắc trên cho từng phần; bản nháp tin mới
**không** tự điền — vẫn phải bấm "Điền vào biểu mẫu".

**Phần `FAILED` khi tạo lại:** phần đó **giữ nguyên gợi ý cũ** (nếu có) và hiện câu lỗi kèm dòng "Chưa tạo lại được
phần này; đang hiện gợi ý trước đó." + "Thử lại"; các phần `SUCCEEDED` trong cùng response cập nhật bình thường. Không
phần nào ở trạng thái lẫn cũ/mới mà không báo.

### 4g. Bước 4 "Câu hỏi sàng lọc" (R-Q)

```
┌─ Tạo tin tuyển dụng ─────────────────────────────────────────────────┐
│ (1) ─ (2) ─ (3) ─ (4)● Câu hỏi sàng lọc ─ (5)                         │
│ [Info] (khối lưu ý của FR-H10 — ScreeningNotice, luôn hiện)          │
│ ┌─ [Sparkles] Do AI tạo · Gợi ý câu hỏi ────────────── [Tạo lại] ─┐ │
│ │ Có/Không · Bạn có thể làm việc tại văn phòng ở Đà Nẵng không?    │ │
│ │                                          [ Giữ ]  [ Bỏ ]         │ │
│ │ ──────────────────────────────────────────────────────────────── │ │
│ │ Một lựa chọn · Bạn có bao nhiêu năm kinh nghiệm Java?            │ │
│ │   • Dưới 1 năm  • 1–2 năm  • Trên 2 năm                          │ │
│ │                                          [ Giữ ]  [ Bỏ ]         │ │
│ │                                               [ Giữ tất cả ]     │ │
│ └──────────────────────────────────────────────────────────────────┘ │
│ Câu hỏi đã giữ (1/10)                                                │
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ 1. Trả lời ngắn · Bạn có thể bắt đầu làm việc từ khi nào?         │ │
│ │ [ ] Bắt buộc trả lời      [Sparkles] Do AI gợi ý  [ Sửa ] [ Bỏ ]  │ │
│ └──────────────────────────────────────────────────────────────────┘ │
│                                   [ Quay lại ]      [ Tiếp theo ]    │
└──────────────────────────────────────────────────────────────────────┘
```

- `ScreeningNotice` của FR-H10 (dùng lại nguyên) luôn hiện đầu bước (R-Q3).
- Gợi ý chưa chọn mặc định; "Giữ"/"Giữ tất cả" khoá khi đã giữ 10 câu, kèm câu lý do (R-Q4).
- Câu đã giữ: ô **"Bắt buộc trả lời"** không tick sẵn (R-Q1); "Sửa" mở sửa tại chỗ: loại (dùng lại
  `QuestionTypeSegmentedControl`), nội dung (≤ 300, bộ đếm), phương án (2–10, ≤ 100) — cùng giới hạn và câu lỗi của
  FR-H10 (`screeningLabels.ts`). Thứ tự câu = thứ tự giữ; không kéo thả (đổi thứ tự ở trang sửa tin sau khi lưu).
- 0 gợi ý: "AI chưa gợi ý được câu hỏi nào từ mô tả này. Câu hỏi sàng lọc là tuỳ chọn." + "Tạo lại".

### 4h. Bước 5 "Mẫu giấy mời phỏng vấn" và "Lưu nháp"

Nội dung bước giống bước 3 của luồng thủ công (FR-H02, HR tự điền — Q3), thêm một dòng đầu bước: "Mẫu giấy mời do bạn
tự điền; AI không soạn phần này." Nút cuối **"Lưu nháp"** (thay "Tạo tin tuyển dụng" của luồng thủ công):

```
│ … ô mẫu giấy mời …                                                   │
│ Tin sẽ được lưu ở trạng thái Nháp. Bạn mở tin ở trang sửa tin khi đã │
│ đủ 100% trọng số và đủ ngành nghề, tỉnh/thành.                       │
│                                   [ Quay lại ]      [ Lưu nháp ]     │
```

- Bấm "Lưu nháp": kiểm phía trình duyệt (Zod của FR-H02 + trọng số mọi tiêu chí đã giữ + tổng ≤ 100); lỗi thì chuyển
  tới bước đầu tiên có lỗi và focus ô lỗi. Hợp lệ → J2 (gửi token của phần đã dùng, lấy từ lần sinh gần nhất đã dùng của phần đó — R-K4-5) → thành công thì chuyển
  `/hr/jobs/{id}/edit?tab=rubric&created=1`.
- Đang lưu: nút "Đang lưu…" khoá. Lỗi J2: câu backend dưới nút (như lỗi tạo tin hiện nay); 400 có số thứ tự ("Tiêu chí
  2: …", "Câu hỏi 3: …") thì chuyển tới bước tương ứng.

### 4i. Lỗi (UI_GUIDE mục 5)

```
│ Bản nháp tin          [Check] Đã có gợi ý                                         │
│ Tiêu chí đánh giá     [AlertCircle] AI phản hồi quá lâu, vui lòng thử lại.   [ Thử lại ] │
│ Câu hỏi sàng lọc      [Check] Đã có gợi ý                                         │
```

Hai loại lỗi:

- **Lỗi của một phần** (response 200, ô của phần đó `FAILED`, `error` ∈ `AI_TIMEOUT`/`AI_UNAVAILABLE`/`AI_INVALID_OUTPUT`):
  câu lấy từ `message` của ô, hiện ở dòng của phần đó trong khối "Tạo tin bằng AI" và lặp lại ở đầu bước tương ứng
  (bước 1 / 3 / 4) thay cho khối gợi ý (hoặc trên gợi ý cũ — 4f). Phần khác trong cùng response **không** bị ảnh hưởng.
  "Thử lại" → J1 với `parts` chỉ gồm phần đó (1 lượt). Luôn có "Thử lại".
- **Lỗi của cả request** (429, 400, 404, lỗi mạng/quá 35 s): áp cho **mọi phần có trong request đó**, mỗi dòng hiện cùng
  câu; phần không có trong request giữ nguyên. "Thử lại" chỉ hiện với 429 và lỗi mạng/quá 35 s; bấm thì gửi lại đúng
  các phần của request đã lỗi. 400/404 chỉ hiện câu của backend.

### 4j. Điện thoại (375px)

```
┌──────────────────────────────┐
│ ┌─ Tạo tin bằng AI ────────┐ │
│ │ Mô tả của bạn: "Cần…"    │ │
│ │ ┌──────────────────────┐ │ │
│ │ │     Sửa mô tả        │ │ │
│ │ └──────────────────────┘ │ │
│ │ ┌──────────────────────┐ │ │
│ │ │   Tạo lại tất cả     │ │ │
│ │ └──────────────────────┘ │ │
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ Bước 3/5                 │ │
│ │ Tiêu chí đánh giá        │ │
│ │ ●─●─●─○─○                │ │
│ │ ┌─ Do AI tạo ──────────┐ │ │
│ │ │ Kinh nghiệm Java     │ │ │
│ │ │ backend              │ │ │
│ │ │ Số năm và mức độ…    │ │ │
│ │ │ ┌─────┐ ┌─────┐      │ │ │
│ │ │ │ Giữ │ │ Bỏ  │      │ │ │
│ │ │ └─────┘ └─────┘      │ │ │
│ │ │ [ Tạo lại ]          │ │ │
│ │ └──────────────────────┘ │ │
│ │ Tiêu chí đã giữ (1)      │ │
│ │ Viết test tự động        │ │
│ │ Trọng số (%)             │ │
│ │ [                     ]  │ │
│ │ ┌──────┐ ┌──────┐        │ │
│ │ │ Sửa  │ │ Bỏ   │        │ │
│ │ └──────┘ └──────┘        │ │
│ │ Tổng trọng số: 0/100     │ │
│ │ [Quay lại] [Tiếp theo]   │ │
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

Stepper ở compact chỉ hiện "Bước {n}/5" + tên bước hiện tại + chấm tiến trình (5 nhãn không vừa 343px). Không
`fixed`/`sticky`.

## 5. Component

### 5a. File mới

| File | Vai trò |
|---|---|
| `frontend/src/features/jobDraft/types.ts` | Kiểu request/response J1, J2 (mục 4.4 REQUIREMENT); `JobDraftPart`, `PartStatus`, `PartResult<T>`, `JobDraftField`, `FieldNeed`. `weight` **chỉ** có ở `CriterionInput` (request J2) |
| `frontend/src/features/jobDraft/api.ts` | **Một** hàm J1 (`POST /hr/job-ai-drafts`, nhận `description` + `parts`, `timeout: 35000`) và một hàm J2 (`POST /hr/job-ai-drafts/save`) |
| `frontend/src/features/jobDraft/queries.ts` | Một mutation J1 (không tự thử lại) + mutation J2; không query nào tải định kỳ |
| `frontend/src/features/jobDraft/jobDraftLabels.ts` | Nhãn trường thiếu, nhóm `need`, nhãn bước, câu lỗi fallback (mục 7) — một chỗ |
| `frontend/src/features/jobDraft/useJobAiDraft.ts` | State chế độ AI: mô tả, cờ "đang có request J1", trạng thái từng phần (đang chạy / thành công / lỗi + câu), kết quả **thành công gần nhất** của từng phần và token tương ứng, cờ "đã dùng" từng phần (R-K4-5), danh sách gợi ý/đã giữ, giá trị AI đã điền theo trường (để tính dấu "Do AI gợi ý" và hộp thoại 4f). Chỉ state React |
| `frontend/src/features/jobDraft/AiDraftInputPanel.tsx` | Khối 4a/4b/4c/4c-2, dòng trạng thái và lỗi từng phần (4i) |
| `frontend/src/features/jobDraft/PostingSuggestionBlock.tsx` | Khối "Bản nháp tin" 4d + dòng thu gọn sau khi điền |
| `frontend/src/features/jobDraft/MissingFieldsCard.tsx` | Khối "Cần bổ sung" (R-M) |
| `frontend/src/features/jobDraft/AiSuggestedMark.tsx` | Dấu "Do AI gợi ý" cạnh nhãn trường/thẻ |
| `frontend/src/features/jobDraft/CriteriaSuggestionStep.tsx` | Bước 3 (4e) |
| `frontend/src/features/jobDraft/ScreeningSuggestionStep.tsx` | Bước 4 (4g) |
| `frontend/src/features/jobDraft/OverwriteFieldsDialog.tsx` | Hộp thoại 4f |

### 5b. File có sẵn bị sửa

| File | Sửa gì |
|---|---|
| `frontend/src/pages/HrJobCreatePage.tsx` | Render `AiDraftInputPanel` trên `Card`; state chế độ (thủ công/AI); ở chế độ AI: 5 bước (chèn bước 3, 4), ô "Quyền lợi", dấu "Do AI gợi ý", khối "Cần bổ sung", nút "Lưu nháp" gọi J2. Luồng thủ công giữ nguyên 3 bước và `POST /api/hr/jobs`. **Sửa lỗi `Select` (REQUIREMENT mục 0.a9):** `onValueChange={(v) => { if (v) field.onChange(v) }}` cho hai ô Loại hình/Hình thức (`:305`, `:326`) — cả hai chế độ |

Không sửa: `HrJobEditPage`, `features/rubric/*`, `features/screening/*` (chỉ import), `features/jobs/*` (chỉ import),
`components/ui/*`. `features/jobDraft` không import `features/scoring`, `applicationDetail`.

### 5c. Dùng lại có sẵn

| Có sẵn | Cách dùng |
|---|---|
| `Button` | "Tạo tin bằng AI", "Tạo bản nháp", "Điền vào biểu mẫu", "Lưu nháp", "Giữ" Filled (mặc định); "Tạo lại", "Tạo lại tất cả", "Sửa mô tả", "Thử lại", "Giữ tất cả", "Sửa", "Chỉ điền trường chưa sửa" `variant="outline"`; "Bỏ", [X] `variant="ghost"`; mọi nút trong khối AI `min-h-10` (C07 L12) |
| `Textarea`, `Input`, `Label`, `Checkbox`, `Dialog`, `Card` | Ô mô tả, ô trọng số, "Bắt buộc trả lời", hộp thoại 4f |
| `CatalogField`, `useCatalogsQuery` | Ô ngành/tỉnh (như FR-H02) và nhãn trong khối "Bản nháp tin" |
| `EMPLOYMENT_TYPE_LABELS`, `WORK_MODE_LABELS` (`features/jobs/jobLabels.ts`), `formatSalary` | Hiển thị trong khối "Bản nháp tin" |
| `ScreeningNotice`, `QuestionTypeSegmentedControl`, `SCREENING_QUESTION_TYPE_LABELS`, giới hạn/câu lỗi (`features/screening/screeningLabels.ts`) | Bước 4 |
| `LinearProgress` (`features/resumes/LinearProgress.tsx`) | Tiến trình từng phần (`animate-m3-linear-progress` + `motion-reduce:animate-none` — C07 L12) |
| `extractErrorMessage` (`lib/httpError.ts`) | Câu lỗi từ `message` backend, fallback mục 7 |
| `lucide-react` | `Sparkles`, `X`, `Check`, `AlertCircle`, `Info` |

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Khối "Tạo tin bằng AI" (4a–4c) | `bg-m3-surface` | `border border-m3-outline-variant`, `rounded-m3-sm`; chữ `m3-on-surface`, chú thích `m3-on-surface-variant` (4.83:1) | Khối điều khiển, không phải nội dung AI |
| Khối "Do AI tạo" (bản nháp tin, gợi ý tiêu chí, gợi ý câu hỏi) | `bg-m3-surface-container-high` | `border border-m3-outline-variant`; chữ **chỉ** `text-m3-on-surface` (13.06:1), kể cả nhãn "Do AI tạo" và "Chưa có dữ liệu" | UI_GUIDE mục 4 |
| Thẻ tiêu chí/câu hỏi đã giữ | `bg-m3-surface` | `border border-m3-outline-variant`, chữ `m3-on-surface` | Đã là của HR — không nền AI |
| Dấu "Do AI gợi ý" | — | Icon `Sparkles` + chữ `text-m3-on-surface-variant`, `text-m3-label-md` | Trên `m3-surface`; là chữ thật, không chỉ icon |
| Khối "Cần bổ sung" | `bg-m3-surface-container` | `border border-m3-outline-variant`, chữ `m3-on-surface`; tên trường là nút chữ `text-m3-on-primary-container` | Trung tính — **không** `m3-warning`/`m3-error` (không phải lỗi) |
| Thanh tiến trình | Rãnh `bg-m3-surface-container-highest` | Thanh `bg-m3-primary` | UI_GUIDE 1h |
| Câu lỗi | — | Icon `AlertCircle` `text-m3-error` + chữ `text-m3-on-surface`; lỗi tại ô `text-m3-error` | Như form FR-H02 |
| Bộ đếm mô tả | — | `text-m3-on-surface-variant`; > 3000 → `text-m3-error` | |
| "Tổng trọng số" | — | `text-m3-on-surface`; > 100 → câu lỗi `text-m3-error` | **Không** xanh khi đủ 100, không thanh màu theo ngưỡng |

## 6. Trạng thái hiển thị

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Chưa dùng AI | Trang | 4a; wizard thủ công 3 bước |
| Mô tả rỗng / > 3000 | Khối AI | "Tạo bản nháp" khoá; bộ đếm `{n}/3000`, vượt thì màu lỗi |
| Đang tạo | Khối AI, bước tương ứng | 4c; `aria-busy` trên dòng của mỗi phần trong request; đầu bước 1/3/4 của phần đang chạy hiện skeleton xám 3 dòng (chưa có gợi ý) hoặc giữ gợi ý cũ kèm thanh tiến trình (đang tạo lại); mọi nút sinh khoá |
| Có bản nháp tin, chưa điền | Bước 1 | 4d (khối xem trước) |
| Đã điền | Bước 1 | Dòng thu gọn + dấu "Do AI gợi ý" |
| Thiếu dữ liệu | Mọi bước chế độ AI | Khối "Cần bổ sung"; trong khối xem trước giá trị thiếu là "Chưa có dữ liệu" |
| 0 gợi ý tiêu chí / câu hỏi | Bước 3 / 4 | Câu rỗng mục 4e/4g + "Tạo lại" |
| Đủ 10 câu đã giữ / 10 tiêu chí đã giữ | Bước 4 / 3 | "Giữ", "Giữ tất cả" khoá + câu lý do |
| Ô `FAILED` (`AI_TIMEOUT`/`AI_UNAVAILABLE`/`AI_INVALID_OUTPUT`) | Dòng của phần đó + bước | 4i, câu từ `message` của ô, "Thử lại" chỉ phần đó; phần khác cùng response hiện kết quả bình thường |
| Ô `FAILED` khi tạo lại | Dòng + bước | Giữ gợi ý cũ + "Chưa tạo lại được phần này; đang hiện gợi ý trước đó." (4f) |
| 429 | Mọi phần trong request đó | Câu có sẵn + "Thử lại"; gợi ý đã có của mọi phần giữ nguyên |
| 400 J1 | Mọi phần trong request đó | Câu backend (không "Thử lại") |
| 404 `COMPANY_NOT_FOUND` | Khối AI | Câu backend "HR chưa tạo hồ sơ công ty" + liên kết "Tạo hồ sơ công ty" → `/hr/company` |
| Mạng lỗi / quá 35 s | Mọi phần trong request đó | "Chưa tạo được gợi ý, vui lòng thử lại." + "Thử lại" |
| Đang lưu | Nút cuối | "Đang lưu…" khoá |
| Lỗi J2 | Dưới nút cuối | 4h |
| Không có quyền (vai trò khác) | — | Route HR có sẵn đã chặn (không đổi) |

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Tiêu đề khối, nút mở | "Tạo tin bằng AI" |
| Giới thiệu khối (4a) | "Mô tả vị trí bằng lời thường. AI gợi ý bản nháp tin, tiêu chí đánh giá và câu hỏi sàng lọc; bạn xem và quyết định từng phần trước khi lưu." |
| Nhãn ô mô tả | "Mô tả vị trí cần tuyển" |
| Placeholder | "Ví dụ: Cần tuyển 2 lập trình viên Java backend tại Đà Nẵng, toàn thời gian, lương 15 - 20 triệu…" |
| Chú thích ô | "Nêu những gì bạn biết: vị trí, công việc, yêu cầu, mức lương, địa điểm, hình thức làm việc, quyền lợi, hạn nộp. Thông tin không nêu sẽ được để trống. Không nhập thông tin cá nhân của bất kỳ ai." |
| Bộ đếm | "{n}/3000" |
| Ghi chú lượt | "Mỗi lần tạo dùng 1 lượt gọi AI." |
| Nút tạo | "Tạo bản nháp" |
| Nút đóng (`aria-label`) | "Đóng tạo tin bằng AI" |
| Ba phần | "Bản nháp tin" · "Tiêu chí đánh giá" · "Câu hỏi sàng lọc" |
| Đang tạo | "Đang tạo…" |
| Xong | "Đã có gợi ý" |
| Phần lỗi khi tạo lại | "Chưa tạo lại được phần này; đang hiện gợi ý trước đó." |
| Thu gọn | "Mô tả của bạn: "{60 ký tự đầu}…"" · "Sửa mô tả" · "Tạo lại tất cả" |
| Nhãn khối AI | "Do AI tạo" |
| Khối bản nháp tin | "Bản nháp tin" · nhãn dòng: "Tiêu đề", "Ngành nghề", "Tỉnh/thành", "Loại hình", "Hình thức", "Lương", "Hạn ứng tuyển", "Mô tả", "Yêu cầu", "Quyền lợi" · giá trị thiếu "Chưa có dữ liệu" · "Xem đầy đủ" / "Thu gọn" |
| Nút điền | "Điền vào biểu mẫu" |
| Sau khi điền | "Đã điền bản nháp tin vào biểu mẫu. Hãy đọc lại từng trường trước khi lưu." |
| Dấu trường | "Do AI gợi ý" |
| Ô quyền lợi | Nhãn "Quyền lợi" · chú thích "Khi lưu, quyền lợi được đặt ở cuối phần mô tả công việc, sau dòng "Quyền lợi:"." |
| Khối thiếu — tiêu đề | "Cần bổ sung" |
| `SAVE` / `OPEN` / `OPTIONAL` | "Bắt buộc để lưu nháp:" / "Bắt buộc để mở tin:" / "Nên có:" |
| Dòng cố định | "Luôn do bạn nhập: trọng số tiêu chí, mẫu giấy mời phỏng vấn." |
| `TITLE` … `BENEFITS` | "Tiêu đề", "Mô tả công việc", "Ngành nghề", "Tỉnh/thành", "Loại hình làm việc", "Hình thức làm việc", "Mức lương", "Hạn ứng tuyển", "Yêu cầu ứng viên", "Quyền lợi" |
| Bước (chế độ AI) | "Thông tin cơ bản", "Lương & thời hạn", "Tiêu chí đánh giá", "Câu hỏi sàng lọc", "Mẫu giấy mời phỏng vấn"; compact "Bước {n}/5" |
| Bước 3 — giới thiệu | "Trọng số do bạn nhập. Có thể lưu nháp khi tổng chưa đủ 100%; cần đủ 100% để mở tin." |
| Khối gợi ý tiêu chí | "Gợi ý tiêu chí" · "Giữ" · "Bỏ" · "Giữ tất cả" · "Tạo lại" |
| Danh sách đã giữ | "Tiêu chí đã giữ ({n})" · "Trọng số (%)" · "Sửa" · "Bỏ" · "Xong" · "Huỷ" |
| Tổng | "Tổng trọng số: {n}/100" |
| Lỗi trọng số | "Vui lòng nhập trọng số." · "Trọng số phải lớn hơn 0 và tối đa 100." · "Tổng trọng số vượt quá 100%." |
| Lỗi tên tiêu chí | "Vui lòng nhập tên tiêu chí." · "Tên tiêu chí tối đa 200 ký tự." · "Tên tiêu chí bị trùng." |
| Rỗng tiêu chí | "AI chưa gợi ý được tiêu chí nào từ mô tả này. Bạn có thể thêm tiêu chí ở trang sửa tin sau khi lưu." |
| Đủ 10 tiêu chí | "Đã giữ đủ 10 tiêu chí." |
| Khối gợi ý câu hỏi | "Gợi ý câu hỏi" · loại: nhãn có sẵn của FR-H10 |
| Danh sách câu đã giữ | "Câu hỏi đã giữ ({n}/10)" · "Bắt buộc trả lời" |
| Rỗng câu hỏi | "AI chưa gợi ý được câu hỏi nào từ mô tả này. Câu hỏi sàng lọc là tuỳ chọn." |
| Đủ 10 câu | "Đã giữ đủ 10 câu hỏi (tối đa của một tin)." |
| Bước 5 — dòng đầu | "Mẫu giấy mời do bạn tự điền; AI không soạn phần này." |
| Trước nút lưu | "Tin sẽ được lưu ở trạng thái Nháp. Bạn mở tin ở trang sửa tin khi đã đủ 100% trọng số và đủ ngành nghề, tỉnh/thành." |
| Nút lưu | "Lưu nháp" · đang lưu "Đang lưu…" |
| Hộp thoại 4f | Tiêu đề "Ghi đè các trường bạn đã sửa?" · nội dung "Bạn đã sửa {n} trường: {danh sách}. Bản nháp mới sẽ điền vào các trường còn lại." · nút "Chỉ điền trường chưa sửa" / "Ghi đè tất cả" |
| Thử lại | "Thử lại" |
| Fallback lỗi J1 | "Chưa tạo được gợi ý, vui lòng thử lại." |
| Fallback lỗi J2 | "Lưu nháp thất bại, vui lòng thử lại." |
| Backend 400 `INVALID_JOB_DRAFT_REQUEST` | "Vui lòng nhập mô tả vị trí cần tuyển." · "Mô tả tối đa 3000 ký tự." · "Vui lòng chọn ít nhất một phần cần tạo." · "Danh sách phần cần tạo không hợp lệ hoặc bị trùng." · body hỏng: "Yêu cầu tạo tin bằng AI không hợp lệ." |
| Backend 400 `INVALID_JOB_DRAFT_SAVE` | "Tối đa 10 tiêu chí." · "Tiêu chí {i}: vui lòng nhập tên tiêu chí." · "Tiêu chí {i}: tên tiêu chí tối đa 200 ký tự." · "Tiêu chí {i}: vui lòng nhập trọng số." · "Tiêu chí {i}: trọng số phải lớn hơn 0 và tối đa 100." · "Tiêu chí {i}: tên tiêu chí bị trùng." · "Tối đa 10 câu hỏi sàng lọc." · "Câu hỏi {i}: {câu của `ScreeningQuestionViolation`}" · "Câu hỏi {i}: vui lòng chọn loại câu hỏi." |
| Backend 400 `INVALID_AI_GENERATION_TOKEN` | "Thông tin phiên tạo bằng AI không hợp lệ hoặc đã hết hạn. Vui lòng tạo lại gợi ý." |
| Backend 400 `RUBRIC_WEIGHT_EXCEEDED` | Câu có sẵn của FR-H03 |
| Ô `FAILED` — `message` theo `error` | Ba câu có sẵn của `AiSyncErrorCode` (C07 UI.md mục 7): `AI_TIMEOUT`, `AI_UNAVAILABLE`, `AI_INVALID_OUTPUT` |
| Backend 429 | Câu có sẵn: "Bạn đã gửi quá nhiều yêu cầu, vui lòng thử lại sau ít phút" |
| Backend 404 `COMPANY_NOT_FOUND` | Câu có sẵn "HR chưa tạo hồ sơ công ty" · liên kết "Tạo hồ sơ công ty" |

Chuỗi của ba bước thủ công (FR-H02) giữ nguyên.

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Khối AI | Stepper | Thẻ gợi ý / đã giữ | Hàng nút |
|---|---|---|---|---|
| Expanded (≥ `lg`) | Rộng bằng `Card` wizard (`max-w-2xl`), phía trên | 5 bước một hàng, nhãn đầy đủ (có thể xuống 2 dòng như 4c) | Nút "Giữ"/"Bỏ" căn phải cùng hàng nội dung | Căn phải, cùng hàng |
| Medium (`sm`–`lg`, 768px) | Như expanded | 5 bước, nhãn rút gọn xuống dòng dưới số | Như expanded | Như expanded |
| Compact (< `sm`, 375px) | Như 4j; nút `w-full` xếp dọc | "Bước {n}/5" + tên bước + chấm | Nội dung trên, nút dưới, mỗi nút ≥ 48px cao | "Quay lại"/"Tiếp theo" cùng hàng chia đôi; "Lưu nháp" `w-full` |

- Không cuộn ngang ở 375px; chữ gợi ý, tên trường thiếu tự ngắt (`break-words`).
- Khối AI không giới hạn chiều cao, không cuộn lồng.

## 9. Khả năng tiếp cận

- Nút "Tạo tin bằng AI" có `aria-expanded` + `aria-controls` trỏ khối; khối là `section` có `aria-labelledby`. Mở khối →
  focus ô mô tả; đóng → focus về nút.
- Ba dòng trạng thái phần là danh sách; mỗi thanh tiến trình `role="progressbar"` + `aria-label="Đang tạo {tên phần}"`.
  Vùng trạng thái `aria-live="polite"`; câu lỗi `role="alert"`.
- Khối "Do AI tạo": nhãn là chữ thật, icon `aria-hidden`. Dấu "Do AI gợi ý" gắn vào ô bằng `aria-describedby` để trình
  đọc màn hình đọc "Do AI gợi ý" khi focus ô.
- Khối "Cần bổ sung" là `role="note"`; mỗi tên trường là `button` đưa focus tới ô tương ứng (đổi bước nếu cần).
- Nút "Giữ"/"Bỏ" có `aria-label` kèm tên tiêu chí/câu hỏi ("Giữ tiêu chí Kinh nghiệm Java backend"). Sau "Giữ", focus
  chuyển tới ô trọng số của tiêu chí vừa giữ (bước 3) hoặc tới thẻ câu vừa giữ (bước 4).
- Ô trọng số có `Label` thật "Trọng số (%) — {tên tiêu chí}" (ẩn phần tên bằng `sr-only`); lỗi gắn `aria-describedby`,
  `aria-invalid`.
- Hộp thoại 4f: focus vào "Chỉ điền trường chưa sửa" (lựa chọn không mất dữ liệu); `Esc` = đóng.
- Vùng chạm ≥ 40px trong khối AI (`min-h-10`), ≥ 48px ở compact (UI_GUIDE 1h).
- Tương phản: chỉ dùng các cặp ở bảng 5d, đều đã đo ở UI_GUIDE 1c/6; không cặp mới cần đo.

## 10. Không được làm

- Không điền sẵn hay gợi ý trọng số (số, placeholder số, chia đều, "khuyến nghị"); không hiển thị mức quan trọng của
  tiêu chí (R-W1).
- Không tự điền bản nháp tin vào biểu mẫu khi chưa bấm "Điền vào biểu mẫu"; không coi gợi ý tiêu chí/câu hỏi là đã giữ
  khi chưa bấm "Giữ" (UI_GUIDE mục 4, Q12).
- Không ghi đè trường HR đã sửa mà không hỏi (4f, Q11).
- Không lưu mô tả, gợi ý, token vào `localStorage`/`sessionStorage`/URL; không gọi J2 trước khi HR bấm "Lưu nháp".
- Không gửi ba request J1 riêng cho ba phần (mỗi lần tạo chỉ **một** request với `parts`); không gửi request J1 mới khi
  request trước chưa xong (R-N1, R-K3H-6).
- Không để lỗi của một phần xoá kết quả hay gợi ý của phần khác; không xoá gợi ý cũ của phần tạo lại thất bại (4f, 4i).
- Không nút "Đăng tin"/"Mở tin" ở trang tạo; không gọi API đổi trạng thái (R-L3).
- Không tick sẵn "Bắt buộc trả lời"; không ẩn lưu ý nhân thân ở bước 4 (R-Q1, R-Q3).
- Không màu đỏ/vàng/xanh cho tiêu chí, câu hỏi hay tổng trọng số; khối "Cần bổ sung" không dùng màu cảnh báo.
- Không dùng `m3-on-surface-variant` trong khối "Do AI tạo".
- Không hộp thoại modal cho phần nhập mô tả hay khi đang tạo (khoá việc gõ biểu mẫu).
- Không render gợi ý bằng HTML/Markdown; không `dangerouslySetInnerHTML`.
- Không toast/snackbar — kết quả và lỗi hiện tại chỗ.
- Không sửa `components/ui`; không sửa ba bước thủ công ngoài lỗi `Select` (mục 5b).
- Không sao chép logo, tên thương hiệu hay CSS của sản phẩm đăng tin tuyển dụng nào (CLAUDE.md §8).
