# FR-U09 — Giao diện

> Trạng thái: ĐÃ HOÀN THÀNH (12/10/2026). Duyệt: 11/10/2026.

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 1d (thang chữ), 1h (vùng chạm), 2 (điều hướng), 3 (component),
4 (ràng buộc, ô consent), 5 (rỗng/tải/lỗi), 6 (tương phản). Mã quy tắc `R-…` là của `REQUIREMENT.md` cùng thư mục.
File này không chép lại quy tắc chung, chỉ chốt cách áp cho hai màn hình bị mở rộng.

## 1. Trạng thái

> Trạng thái: ĐÃ HOÀN THÀNH (12/10/2026). Duyệt: 11/10/2026.

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| `/jobs/:id/apply` | Nộp đơn ứng tuyển — thêm khối "Câu hỏi sàng lọc", ô đồng ý lưu hồ sơ; màn thành công thêm liên kết "Xem đơn vừa nộp" | Hiện có | FR-U02, FR-U09 |
| ★`/candidate/applications/:id` | Chi tiết đơn ứng tuyển — tab "Thông tin đơn" thêm khối "Câu trả lời sàng lọc" và "Lưu hồ sơ cho đợt tuyển sau" (kèm nút "Rút đồng ý"); không thêm tab | ★Mới (FR-U08) | FR-U08; FR-U09 |
| ★`/hr/applications/:id` | Tab "Sàng lọc" — **không đổi giao diện**; phần danh sách dùng component chung (R-C5) | ★Mới (FR-H09) | FR-H10 |

Không route mới. Hai dòng UI_GUIDE mục 7 của hai route đầu sửa theo REQUIREMENT mục 10 (D4, D5).

## 3. Điểm vào

- `/jobs/:id/apply`: nút "Ứng tuyển" ở `/jobs/:id` (như hiện tại, `ApplyButton.tsx`). Route nằm trong
  `ProtectedRoute(CANDIDATE)` (`App.tsx:52-57`).
- `/candidate/applications/:id`: liên kết "Xem chi tiết" ở danh sách đơn, thông báo đổi trạng thái (FR-U08), và
  **mới**: liên kết "Xem đơn vừa nộp" ở màn nộp đơn thành công (Q18).
- Rời màn thành công: "Xem đơn vừa nộp" hoặc "Về trang danh sách việc làm" (có sẵn).

## 4. Bố cục (khung ASCII)

Khung trang nộp đơn giữ nguyên (`JobApplyPage.tsx:35`: `max-w-[720px] px-4 py-8 md:px-6`, nền `m3-surface-container`),
form trong **một** thẻ trắng `bg-m3-surface` `border-m3-outline-variant` `p-6` (`JobApplyForm.tsx:87`). Mọi phần mới
nằm trong thẻ trắng đó nên dùng được `m3-on-surface-variant`, `m3-error` (UI_GUIDE mục 6).

### 4a. Desktop (≥ `lg`) — form nộp đơn, tin có 3 câu hỏi

```
Ứng tuyển vị trí
Chuyên viên Chăm sóc khách hàng
┌──────────────────────────────────────────────────────────────────┐
│ Chọn CV                                                          │
│ [ cv-mau-quoc-huy.docx                                       ▾ ] │
│                                                                  │
│ Thư giới thiệu (tuỳ chọn)                                        │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │ Giới thiệu ngắn gọn về bản thân và lý do bạn phù hợp …       │ │
│ └──────────────────────────────────────────────────────────────┘ │
│ ──────────────────────────────────────────────────────────────── │
│ Câu hỏi sàng lọc                                                 │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │ (i) Câu trả lời giúp nhà tuyển dụng có thêm thông tin tham   │ │
│ │ khảo. Hệ thống không dùng câu trả lời để chấm điểm hay xếp   │ │
│ │ hạng hồ sơ.                                                  │ │
│ └──────────────────────────────────────────────────────────────┘ │
│                                                                  │
│ 1. Bạn có thể bắt đầu đi làm từ khi nào?                         │
│    Trả lời ngắn · Bắt buộc                                       │
│    ┌──────────────────────────────────────────────────────────┐  │
│    │                                                          │  │
│    └──────────────────────────────────────────────────────────┘  │
│    0/500 ký tự                                                   │
│                                                                  │
│ 2. Bạn có sẵn sàng làm ca tối (18:00–22:00) không?               │
│    Có/Không · Không bắt buộc                                     │
│    (●) Có    ( ) Không                    Bỏ chọn                │
│                                                                  │
│ 3. Mức lương mong muốn của bạn?                                  │
│    Một lựa chọn · Không bắt buộc                                 │
│    ( ) Dưới 10 triệu                                             │
│    ( ) 10–14 triệu                                               │
│    ( ) Trên 14 triệu                                             │
│ ──────────────────────────────────────────────────────────────── │
│ [✓] Tôi đồng ý cho phép CV của tôi được hệ thống AI phân tích và │
│     chấm điểm để hỗ trợ nhà tuyển dụng đánh giá hồ sơ.           │
│                                                                  │
│ [ ] Cho phép công ty lưu hồ sơ của tôi để liên hệ cho các vị trí │
│     sau này.                                                     │
│     Không bắt buộc và không ảnh hưởng tới việc xét tuyển đơn     │
│     này. Bạn có thể rút đồng ý bất cứ lúc nào ở trang chi tiết   │
│     đơn ứng tuyển.                                               │
│                                                                  │
│ (câu lỗi chung, nếu có)                                          │
│ [ Nộp đơn ]                                                      │
└──────────────────────────────────────────────────────────────────┘
```

- Thứ tự cố định: Chọn CV → Thư giới thiệu → **Câu hỏi sàng lọc** (chỉ khi tin có ≥ 1 câu) → ô AI consent → **ô
  đồng ý lưu hồ sơ** → câu lỗi chung → nút Nộp. Phần cũ giữ nguyên chữ, thứ tự, class (R-C6).
- Hai vạch ngăn `border-m3-outline-variant` chỉ có khi khối câu hỏi hiện.
- Mỗi câu là một `<fieldset>` có `<legend>` = "{n}. {nội dung câu hỏi}"; dòng loại · bắt buộc là chữ thường cùng màu
  (không `*` đỏ, không badge).
- Trả lời ngắn: `Textarea` 3 dòng, bộ đếm "{n}/500 ký tự" đếm theo code point **sau khi bỏ khoảng trắng hai đầu**
  (cùng cách `countChars` của `screeningLabels.ts:91-93`).
- Một lựa chọn / Có–Không: `<input type="radio">` gốc trong `<label>` (R-C7), phương án theo đúng thứ tự HR nhập;
  Có/Không xếp ngang ở ≥ `sm`. "Bỏ chọn" (nút chữ) chỉ hiện ở câu **không bắt buộc** khi đã chọn — để quay về
  "không trả lời".
- Tin 0 câu hỏi: không có khối, không có vạch ngăn — form giống hệt hiện tại cộng ô đồng ý lưu hồ sơ.

### 4b. Form — các trạng thái của khối câu hỏi

```
Đang tải F1      Câu hỏi sàng lọc
                 ░░░░░░░░░░░░░░░░░░░░░░░░░      (2 khối skeleton)
                 ░░░░░░░░░░░░
                 [ Nộp đơn ] (khoá)

Lỗi tải F1       Câu hỏi sàng lọc
                 (!) Không tải được câu hỏi sàng lọc của tin này.
                 [ Thử lại ]
                 …
                 Cần tải được câu hỏi sàng lọc trước khi nộp đơn.
                 [ Nộp đơn ] (khoá)

Sau 409          Câu hỏi sàng lọc
                 ┌──────────────────────────────────────────────┐
                 │ (!) Nhà tuyển dụng vừa cập nhật câu hỏi sàng │
                 │ lọc. Vui lòng kiểm tra lại câu trả lời rồi   │
                 │ nộp lại.                                     │
                 └──────────────────────────────────────────────┘
                 (bộ câu hỏi mới; câu còn nguyên giữ câu trả lời — R-F5)

Lỗi Zod          2. Bạn có sẵn sàng làm ca tối … ?
                    Có/Không · Bắt buộc
                    ( ) Có    ( ) Không
                    Vui lòng trả lời câu hỏi bắt buộc này.
```

- "Lỗi tải F1" áp cho lỗi mạng/5xx. F1 trả 404 (tin vừa đóng/hết hạn sau khi trang đã tải tin): khối hiện "Tin
  tuyển dụng này hiện không nhận đơn." và nút Nộp khoá; không thay cả trang.
- Thông báo 409 nằm **đầu khối câu hỏi** (không nằm ở dòng lỗi chung), viền `border-m3-outline` nền `m3-surface`,
  icon `AlertCircle` `text-m3-error`, chữ `text-m3-on-surface`. Biến mất khi ứng viên nộp lại.

### 4c. Màn nộp đơn thành công

```
┌──────────────────────────────────────────────────────────────────┐
│ Nộp đơn thành công.                                              │
│ Hồ sơ của bạn đã được gửi tới nhà tuyển dụng. Bạn có thể theo    │
│ dõi trạng thái ứng tuyển sau.                                    │
│ Xem đơn vừa nộp →        Về trang danh sách việc làm             │
└──────────────────────────────────────────────────────────────────┘
```

Giữ nguyên khung, nền, hai câu cũ (`JobApplyForm.tsx:61-73`); thêm liên kết "Xem đơn vừa nộp →" tới
`/candidate/applications/{id}` (`id` từ response F2) **trước** liên kết cũ. Cả hai liên kết dùng
`text-m3-on-primary-container` (nền `m3-primary-container`, 6.23:1).

### 4d. Điện thoại (375px) — form nộp đơn

Chiều rộng nội dung ≈ 375 − 2×16 (`px-4`) − 2×24 (`p-6` của thẻ) ≈ 295px.

```
┌──────────────────────────┐
│ Chọn CV                  │
│ [ cv-mau-quoc-huy… ▾ ]   │
│ Thư giới thiệu (tuỳ chọn)│
│ ┌──────────────────────┐ │
│ └──────────────────────┘ │
│ ──────────────────────── │
│ Câu hỏi sàng lọc         │
│ ┌──────────────────────┐ │
│ │(i) Câu trả lời giúp  │ │
│ │nhà tuyển dụng có …   │ │
│ └──────────────────────┘ │
│ 1. Bạn có thể bắt đầu đi │
│    làm từ khi nào?       │
│    Trả lời ngắn · Bắt    │
│    buộc                  │
│    ┌──────────────────┐  │
│    └──────────────────┘  │
│    0/500 ký tự           │
│ 2. Bạn có sẵn sàng làm   │
│    ca tối … không?       │
│    Có/Không · Không bắt  │
│    buộc                  │
│    ( ) Có                │
│    ( ) Không             │
│    Bỏ chọn               │
│ 3. Mức lương mong muốn   │
│    của bạn?              │
│    ( ) Dưới 10 triệu     │
│    ( ) 10–14 triệu       │
│    ( ) Trên 14 triệu     │
│ ──────────────────────── │
│ [ ] Tôi đồng ý cho phép  │
│     CV của tôi …         │
│ [ ] Cho phép công ty lưu │
│     hồ sơ của tôi …      │
│     Không bắt buộc …     │
│ [       Nộp đơn        ] │
└──────────────────────────┘
```

Mỗi dòng phương án (cả `<label>`) cao tối thiểu 48px dưới `sm` (UI_GUIDE mục 1h); Có/Không xếp dọc dưới `sm`. Nút
"Nộp đơn" giữ đúng class hiện có `w-fit` (`JobApplyForm.tsx:146`, R-C6) — khung trên chỉ minh hoạ chiều ngang.

### 4e. Desktop — trang chi tiết đơn, tab "Thông tin đơn" (đơn có bản chụp, đang đồng ý)

Hai khối mới chèn **sau** "Hồ sơ đã nộp", **trước** "CV đã trích xuất" (Q12), cùng kiểu `section` + `h2` +
`divide-y` của các khối FR-U08 (`ApplicationInfoTab.tsx:12-17`, `SubmittedResumeSection.tsx:46-49`).

```
│ Hồ sơ đã nộp                                                             │
│ [FileText] cv-mau-quoc-huy.docx  [Đã trích xuất]        [↓ Tải CV gốc]   │
│ Thư giới thiệu …                                                         │
│ ──────────────────────────────────────────────────────────────────────── │
│ Câu trả lời sàng lọc                                                     │
│ Câu hỏi và câu trả lời được lưu nguyên văn lúc bạn nộp đơn               │
│ (00:35 06/10/2026). Nhà tuyển dụng chỉ dùng để tham khảo, không dùng để  │
│ chấm điểm hay xếp hạng.                                                  │
│                                                                          │
│ 1. Bạn có thể bắt đầu đi làm từ khi nào?                                 │
│    Trả lời ngắn · Bắt buộc                                               │
│    Đầu tháng 11/2026, sau khi bàn giao công việc hiện tại.               │
│ ──────────────────────────────────────────────────────────────────────── │
│ 2. Bạn có sẵn sàng làm ca tối (18:00–22:00) không?                       │
│    Có/Không · Không bắt buộc                                             │
│    Không                                                                 │
│ ──────────────────────────────────────────────────────────────────────── │
│ 3. Mức lương mong muốn của bạn?                                          │
│    Một lựa chọn · Không bắt buộc                                         │
│    10–14 triệu                                                           │
│ ──────────────────────────────────────────────────────────────────────── │
│ Lưu hồ sơ cho đợt tuyển sau                                              │
│ Bạn đã đồng ý cho Công ty TNHH Thử Nghiệm Ánh Dương lưu hồ sơ để liên hệ │
│ cho các vị trí sau này (00:35 06/10/2026).                               │
│ Rút đồng ý không ảnh hưởng tới trạng thái hay việc xét tuyển của đơn     │
│ này. Sau khi rút, bạn không thể đồng ý lại cho đơn này.                  │
│ [ Rút đồng ý ]                                                           │
│ ──────────────────────────────────────────────────────────────────────── │
│ CV đã trích xuất                                                         │
│ …                                                                        │
```

- Danh sách câu trả lời là **cùng** component `ScreeningAnswerList` với tab "Sàng lọc" phía HR (R-C5), khác chuỗi:
  câu bỏ trống "Bạn không trả lời câu này." thay vì "Ứng viên không trả lời câu này.".
- Không hiện phương án còn lại của câu một lựa chọn, không đánh dấu, không màu (R-N3).
- Nút "Rút đồng ý" `variant="outline"`, không đỏ — cùng mức nhấn với "Rút đơn" (FR-U08 UI.md 5c).

### 4f. Hai khối — các trạng thái khác

```
Khối "Câu trả lời sàng lọc"
  state = NO_QUESTIONS | NOT_CAPTURED   → không render khối, không vạch ngăn (R-D2, Q20 — ngoại lệ có chủ đích với UI_GUIDE mục 4)
  Đang tải F3                           → tiêu đề + 3 khối skeleton
  Lỗi F3 (mạng/5xx)                     → (!) Không tải được câu trả lời sàng lọc, vui lòng thử lại.  [ Thử lại ]

Khối "Lưu hồ sơ cho đợt tuyển sau" (luôn render, từ E1 — không gọi API riêng)
  NOT_GIVEN   Bạn không đồng ý cho công ty lưu hồ sơ khi nộp đơn này.
  REVOKED     Bạn đã rút đồng ý lưu hồ sơ (09:12 11/10/2026). Công ty không được lưu hồ sơ từ đơn này cho
              các vị trí sau.
  ACTIVE      4e
```

`NOT_GIVEN`, `REVOKED`: không nút, không dòng chữ thay thế khác.

### 4g. Hộp xác nhận "Rút đồng ý"

```
┌──────────────────────────────────────────────┐
│ Rút đồng ý lưu hồ sơ?                        │
│ Công ty TNHH Thử Nghiệm Ánh Dương sẽ không   │
│ được lưu hồ sơ của bạn từ đơn ứng tuyển này  │
│ để liên hệ cho các vị trí sau. Bạn không thể │
│ đồng ý lại cho đơn này. Trạng thái và việc   │
│ xét tuyển của đơn không thay đổi.            │
│                                              │
│ (câu lỗi, nếu có)                            │
│                    [ Huỷ ] [ Xác nhận rút đồng ý ] │
└──────────────────────────────────────────────┘
```

Cùng cấu trúc `WithdrawApplicationDialog.tsx:40-63` (`DialogContent sm:max-w-md`, `DialogHeader`, câu lỗi
`text-m3-error`, `DialogFooter`); tên công ty `font-medium text-m3-on-surface` như tên tin ở hộp rút đơn.

### 4h. Điện thoại (375px) — hai khối ở trang chi tiết

Chiều rộng nội dung ≈ 311px (FR-U08 UI.md 4d).

```
┌──────────────────────────────┐
│ Câu trả lời sàng lọc         │
│ Câu hỏi và câu trả lời được  │
│ lưu nguyên văn lúc bạn nộp   │
│ đơn (00:35 06/10/2026). …    │
│ 1. Bạn có thể bắt đầu đi làm │
│    từ khi nào?               │
│    Trả lời ngắn · Bắt buộc   │
│    Đầu tháng 11/2026, sau    │
│    khi bàn giao công việc …  │
│ ──────────────────────────── │
│ …                            │
│ ──────────────────────────── │
│ Lưu hồ sơ cho đợt tuyển sau  │
│ Bạn đã đồng ý cho Công ty    │
│ TNHH Thử Nghiệm Ánh Dương …  │
│ ┌──────────────────────────┐ │
│ │        Rút đồng ý        │ │
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

Nút "Rút đồng ý" `w-full` dưới `sm`, `w-auto` từ `sm` (cùng cách nút "Tải CV gốc", `SubmittedResumeSection.tsx:62`),
**trong luồng trang**, không `fixed`/`sticky`. Hộp xác nhận gần hết chiều ngang (mặc định của `Dialog`).

## 5. Component

### 5a. File mới (chốt tên — `REQUIREMENT.md` mục 7.3 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/features/candidateScreening/types.ts` | `ScreeningForm` (F1: `jobId`, `revision`, `questions`), `ScreeningAnswerInput`; import kiểu `ScreeningQuestionType` (`:3`), `ScreeningQuestionResponse` (`:14`), `ScreeningCaptureState` (`:32`), `ApplicationScreeningItem` (`:35`), `ApplicationScreeningResponse` (`:46`) từ `features/screening/types.ts` (chỉ kiểu) |
| `frontend/src/features/candidateScreening/api.ts` | Gọi F1, F3 (`/candidates/...`) |
| `frontend/src/features/candidateScreening/queries.ts` | `useScreeningFormQuery(jobId)` khoá `['candidate-screening-form', jobId]`; `useMyApplicationScreeningQuery(applicationId)` khoá `['candidate-application-screening', applicationId]`; không thử lại với 4xx (cùng `retryUnlessClientError` của `candidateApplicationDetail/queries.ts:13-21`) |
| `frontend/src/features/candidateScreening/candidateScreeningLabels.ts` | Mọi chuỗi mục 7 của phía ứng viên |
| `frontend/src/features/candidateScreening/applyFormSchema.ts` | Dựng schema Zod theo bộ câu hỏi; chuyển giá trị form → `answers` của F2 (bỏ khoảng trắng hai đầu, bỏ câu trống không bắt buộc); `mergeAnswersAfterReload` (R-F5) |
| `frontend/src/features/candidateScreening/ScreeningQuestionsFieldset.tsx` | Khối "Câu hỏi sàng lọc" trong form: tiêu đề, lưu ý, danh sách câu, trạng thái tải/lỗi/409 (4a, 4b) |
| `frontend/src/features/candidateScreening/ScreeningQuestionField.tsx` | Một câu theo loại: `Textarea` + bộ đếm / radio phương án / radio Có–Không, "Bỏ chọn", lỗi dưới câu |
| `frontend/src/features/screening/ScreeningAnswerList.tsx` | Danh sách `<ol>` bản chụp **dùng chung** HR và ứng viên; nhận `items` + `texts { yes, no, noAnswer }`; không gọi API, không import gì của HR (R-C5) |
| `frontend/src/features/candidateApplicationDetail/ScreeningAnswersSection.tsx` | Khối "Câu trả lời sàng lọc" (gọi F3, R-D2, 4e, 4f) |
| `frontend/src/features/candidateApplicationDetail/TalentPoolConsentSection.tsx` | Khối "Lưu hồ sơ cho đợt tuyển sau" (đọc `detail.talentPoolConsent`, 4e, 4f) + nút + hộp xác nhận |
| `frontend/src/features/candidateApplicationDetail/RevokeTalentPoolConsentDialog.tsx` | Hộp xác nhận rút đồng ý (4g) |

### 5b. File có sẵn bị sửa

| File | Thay đổi |
|---|---|
| `frontend/src/features/applications/JobApplyForm.tsx` | Sang React Hook Form + Zod (R-C6): `resumeId`, `coverLetter`, `aiConsent`, `talentPoolConsent`, `answers`; gọi `useScreeningFormQuery(jobId)`; chèn `ScreeningQuestionsFieldset`; ô đồng ý lưu hồ sơ; xử lý 409 (R-F5); lỗi 400 dạng map → câu đầu tiên; màn thành công thêm "Xem đơn vừa nộp". Chuỗi, thứ tự, class của phần cũ **giữ nguyên** |
| `frontend/src/features/applications/types.ts` | `ApplicationCreateRequest` thêm `screeningRevision: number \| null`, `answers: ScreeningAnswerInput[]`, `talentPoolConsent: boolean` |
| `frontend/src/features/applicationDetail/ScreeningTab.tsx` | Danh sách `<ol>` (`:43-49`, `:58-84`) thay bằng `ScreeningAnswerList` với chuỗi HR hiện có (`SCREENING_ANSWER_TEXT`); HTML/class/chữ đầu ra **không đổi** |
| `frontend/src/features/candidateApplicationDetail/ApplicationInfoTab.tsx` | Chèn `ScreeningAnswersSection`, `TalentPoolConsentSection` giữa `SubmittedResumeSection` và `ParsedResumeSection` |
| `frontend/src/features/candidateApplicationDetail/types.ts` | `ApplicationCandidateDetail.talentPoolConsent: { state: 'NOT_GIVEN' \| 'ACTIVE' \| 'REVOKED'; consentedAt: string \| null; revokedAt: string \| null }` |
| `frontend/src/features/candidateApplicationDetail/api.ts`, `queries.ts` | Gọi F4; `useRevokeTalentPoolConsentMutation`: thành công → làm mới `candidateApplicationDetailKey(id)`; 409 → cũng làm mới E1 (cùng cách `useWithdrawApplicationMutation`, `features/applications/queries.ts:41-56`) |

Không sửa: `components/ui/*`, `JobApplyPage.tsx`, `CandidateApplicationDetailPage.tsx` (không thêm tab),
`CandidateApplicationHeader.tsx`, `features/screening/api.ts`/`queries.ts` (không thêm hàm `/candidates`),
`HrApplicationDetailPage.tsx`.

### 5c. Dùng lại có sẵn

| Có sẵn | Cách dùng |
|---|---|
| `Label`, `Checkbox`, `Textarea`, `Select`, `Button`, `Dialog` | Như hiện có |
| `SCREENING_QUESTION_TYPE_LABELS`, `SCREENING_TEXT.required/notRequired`, `countChars` (`features/screening/screeningLabels.ts`) | Dòng "loại · bắt buộc", bộ đếm ký tự — **chỉ import**, không sửa file |
| `formatDateTimeVi` (`lib/date.ts`) | Thời điểm chụp, thời điểm đồng ý/rút |
| `extractErrorMessage` (`lib/httpError.ts`) | Câu lỗi hộp rút đồng ý; form dùng thêm một hàm cục bộ cho dạng map (không sửa `lib/httpError.ts`) |
| Mẫu `WithdrawApplicationDialog` | Cấu trúc hộp xác nhận (không import, không sửa) |
| Mẫu `section` + `h2` + `useId` của `SubmittedResumeSection` | Hai khối mới |
| Mẫu khối lưu ý `ScreeningNotice` (role="note", icon `Info`) | Cùng token; viết bản riêng một dòng cho ứng viên, **không** import (nội dung của `ScreeningNotice` là cho HR) |

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Thẻ form, trang chi tiết | `bg-m3-surface` | viền `border-m3-outline-variant` | Có sẵn |
| Tiêu đề khối "Câu hỏi sàng lọc", "Câu trả lời sàng lọc", "Lưu hồ sơ cho đợt tuyển sau" | — | `text-m3-on-surface`, `text-m3-title-md` | `h2` |
| Lưu ý trung tính ở form | `bg-m3-surface-container` | `text-m3-on-surface`; viền `border-m3-outline-variant`; icon `Info` `text-m3-on-surface` | Không `m3-warning`/`m3-error`; chữ phụ không đặt trên nền này (UI_GUIDE mục 6) |
| Nội dung câu hỏi (legend) | — | `text-m3-on-surface`, `text-m3-title-md` | |
| Dòng "loại · bắt buộc", bộ đếm ký tự, câu phụ của ô đồng ý, chú thích đầu khối câu trả lời, "Bạn không trả lời câu này." | — | `text-m3-on-surface-variant`, `text-m3-body-sm` | Chỉ trên `m3-surface` (4.83:1) |
| Nhãn phương án, câu trả lời (mọi loại), câu trạng thái khối lưu hồ sơ | — | `text-m3-on-surface`, `text-m3-body-md` | **Một** màu cho Có, Không, phương án, trả lời ngắn (R-N3) |
| Ô radio | — | `accent-m3-primary`; viền mặc định của trình duyệt | Lựa chọn nhận ra bằng chấm tròn, không chỉ bằng màu |
| "Bỏ chọn" | — | `variant="link"` `text-m3-primary` | Trên `m3-surface` (4.64:1) |
| Lỗi dưới câu (Zod), câu lỗi chung của form, câu lỗi trong hộp rút đồng ý | — | `text-m3-error` | Trên nền trắng (4.74:1), như hiện có (`JobApplyForm.tsx:137`) |
| Thông báo 409 đầu khối câu hỏi | `bg-m3-surface` | viền `border-m3-outline`; icon `AlertCircle` `text-m3-error`; chữ `text-m3-on-surface` | `role="alert"` |
| Lỗi tải F1 / F3 | — | Icon `AlertCircle` `text-m3-error` + chữ `text-m3-on-surface` | Như FR-U08 UI.md 5c |
| Nút "Thử lại", "Rút đồng ý" | — | `variant="outline"` | "Rút đồng ý" không đỏ |
| Liên kết "Xem đơn vừa nộp →" | (`bg-m3-primary-container` của màn thành công) | `text-m3-on-primary-container` | 6.23:1, như liên kết có sẵn cạnh nó |
| Skeleton | `bg-m3-surface-container` | — | Như các khối FR-U08 |

## 6. Trạng thái hiển thị

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Đang tải F1 | Form | Khối câu hỏi: tiêu đề + 2 skeleton; nút "Nộp đơn" khoá |
| F1 lỗi mạng/5xx | Form | 4b "Lỗi tải F1": câu lỗi + "Thử lại"; nút Nộp khoá + câu lý do thành chữ, gắn `aria-describedby` |
| F1 trả 404 | Form | Khối: "Tin tuyển dụng này hiện không nhận đơn."; nút Nộp khoá |
| F1 trả 0 câu | Form | Không render khối, không vạch ngăn; vẫn gửi `screeningRevision` = `revision` |
| F1 trả ≥ 1 câu | Form | 4a |
| Lỗi Zod khi bấm Nộp | Form | Câu lỗi dưới từng câu vi phạm; focus vào trường lỗi đầu tiên (RHF `shouldFocusError`); không gửi request |
| Đang nộp | Form | Nút "Đang nộp đơn..." khoá (có sẵn) |
| 409 `SCREENING_QUESTIONS_CHANGED` | Form | Thông báo đầu khối câu hỏi (4b); tải lại F1; giữ/xoá câu trả lời theo R-F5; focus vào tiêu đề khối câu hỏi |
| 400 `INVALID_SCREENING_ANSWER` | Form | Câu `message` của backend ở dòng lỗi chung (frontend đã chặn bằng Zod, đây là chốt chặn) |
| 400 dạng map (Bean Validation) | Form | Câu đầu tiên của map ở dòng lỗi chung (R-C6) |
| 404 / 409 khác (`JOB_NOT_FOUND`, `RESUME_NOT_FOUND`, `APPLICATION_DUPLICATE`) | Form | Câu `message` của backend ở dòng lỗi chung (như hiện có) |
| Nộp thành công | Form | 4c |
| Đang tải F3 | Khối câu trả lời | Tiêu đề + 3 skeleton |
| F3 lỗi mạng/5xx | Khối câu trả lời | Câu lỗi + "Thử lại" |
| F3 `CAPTURED` | Khối câu trả lời | 4e |
| F3 `NO_QUESTIONS` / `NOT_CAPTURED` | Khối câu trả lời | Không render khối (R-D2, Q20 — ngoại lệ có chủ đích với UI_GUIDE mục 4) |
| Câu không bắt buộc bỏ trống | Khối câu trả lời | "Bạn không trả lời câu này." — không ẩn câu |
| E1 `ACTIVE` / `NOT_GIVEN` / `REVOKED` | Khối lưu hồ sơ | 4e / 4f |
| Đang rút đồng ý | Hộp xác nhận | Hai nút khoá, nhãn "Đang rút đồng ý..." |
| F4 lỗi (409, mạng) | Hộp xác nhận | `message` của backend, fallback "Rút đồng ý thất bại, vui lòng thử lại."; 409 làm mới E1 |
| F4 thành công | Khối lưu hồ sơ | Hộp đóng; khối thành `REVOKED`; badge trạng thái đơn không đổi; thông báo `aria-live` (mục 9) |
| 400/403/404 của E1 | Cả trang | Theo R-T1 của FR-U08, không gọi F3 |

Không có trạng thái "AI đang xử lý": FR-U09 không gọi AI.

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Tiêu đề khối form | "Câu hỏi sàng lọc" |
| Lưu ý ở form | "Câu trả lời giúp nhà tuyển dụng có thêm thông tin tham khảo. Hệ thống không dùng câu trả lời để chấm điểm hay xếp hạng hồ sơ." |
| Loại câu, bắt buộc | Dùng lại nguyên văn `SCREENING_QUESTION_TYPE_LABELS` ("Trả lời ngắn" · "Một lựa chọn" · "Có/Không") và "Bắt buộc" · "Không bắt buộc" |
| Bộ đếm | "{n}/500 ký tự" |
| Có/Không | "Có" · "Không" |
| Bỏ chọn | "Bỏ chọn" · `aria-label` "Bỏ chọn câu trả lời của câu {n}" |
| Lỗi Zod | "Vui lòng trả lời câu hỏi bắt buộc này." · "Tối đa 500 ký tự." |
| Đang tải câu hỏi | (skeleton, không chữ) |
| Lỗi tải F1 | "Không tải được câu hỏi sàng lọc của tin này." · nút "Thử lại" |
| Lý do khoá nút Nộp | "Cần tải được câu hỏi sàng lọc trước khi nộp đơn." |
| F1 404 | "Tin tuyển dụng này hiện không nhận đơn." |
| 409 (backend, nguyên văn) | "Nhà tuyển dụng vừa cập nhật câu hỏi sàng lọc. Vui lòng kiểm tra lại câu trả lời rồi nộp lại." |
| Ô đồng ý lưu hồ sơ | "Cho phép công ty lưu hồ sơ của tôi để liên hệ cho các vị trí sau này." |
| Câu phụ ô đồng ý | "Không bắt buộc và không ảnh hưởng tới việc xét tuyển đơn này. Bạn có thể rút đồng ý bất cứ lúc nào ở trang chi tiết đơn ứng tuyển." |
| Màn thành công — liên kết mới | "Xem đơn vừa nộp →" |
| Phần cũ của form (giữ nguyên văn) | "Chọn CV" · "Thư giới thiệu (tuỳ chọn)" · ô AI consent · "Nộp đơn" · "Đang nộp đơn..." · "Nộp đơn thất bại, vui lòng thử lại." · "Nộp đơn thành công." · "Về trang danh sách việc làm" … (`JobApplyForm.tsx`) |
| Khối câu trả lời — tiêu đề | "Câu trả lời sàng lọc" |
| Khối câu trả lời — chú thích | "Câu hỏi và câu trả lời được lưu nguyên văn lúc bạn nộp đơn ({HH:mm dd/MM/yyyy}). Nhà tuyển dụng chỉ dùng để tham khảo, không dùng để chấm điểm hay xếp hạng." |
| Không trả lời | "Bạn không trả lời câu này." |
| Lỗi tải F3 | "Không tải được câu trả lời sàng lọc, vui lòng thử lại." · nút "Thử lại" |
| Khối lưu hồ sơ — tiêu đề | "Lưu hồ sơ cho đợt tuyển sau" |
| `ACTIVE` | "Bạn đã đồng ý cho {companyName} lưu hồ sơ để liên hệ cho các vị trí sau này ({HH:mm dd/MM/yyyy})." · "Rút đồng ý không ảnh hưởng tới trạng thái hay việc xét tuyển của đơn này. Sau khi rút, bạn không thể đồng ý lại cho đơn này." · nút "Rút đồng ý" |
| `NOT_GIVEN` | "Bạn không đồng ý cho công ty lưu hồ sơ khi nộp đơn này." |
| `REVOKED` | "Bạn đã rút đồng ý lưu hồ sơ ({HH:mm dd/MM/yyyy}). Công ty không được lưu hồ sơ từ đơn này cho các vị trí sau." |
| Hộp xác nhận | Tiêu đề "Rút đồng ý lưu hồ sơ?" · nội dung "{companyName} sẽ không được lưu hồ sơ của bạn từ đơn ứng tuyển này để liên hệ cho các vị trí sau. Bạn không thể đồng ý lại cho đơn này. Trạng thái và việc xét tuyển của đơn không thay đổi." · nút "Huỷ" · "Xác nhận rút đồng ý" (đang gửi "Đang rút đồng ý...") · lỗi fallback "Rút đồng ý thất bại, vui lòng thử lại." |
| 409 F4 (backend, nguyên văn) | "Đơn này hiện không có đồng ý lưu hồ sơ để rút." |
| Thông báo `aria-live` sau khi rút | "Đã rút đồng ý lưu hồ sơ." |

`{companyName}` = `detail.job.companyName` của E1 (tên công ty hiện tại). Ngày giờ: `formatDateTimeVi`, giờ theo
trình duyệt, `HH:mm dd/MM/yyyy` (cùng "Nộp ngày" ở đầu trang, L10 của FR-H10). Chuỗi tab "Sàng lọc" phía HR giữ
nguyên (`screeningLabels.ts:65-77`).

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Form — khối câu hỏi | Form — hai ô đồng ý | Trang chi tiết — hai khối |
|---|---|---|---|
| Expanded (≥ `lg`) | Một cột trong thẻ 720px; Có/Không ngang; phương án một lựa chọn dọc | Checkbox + nhãn cùng hàng, câu phụ thụt theo nhãn | Một cột; nút "Rút đồng ý" `w-auto` |
| Medium (`sm`–`lg`) | Như expanded | Như expanded | Như expanded |
| Compact (< `sm`, 375px) | Có/Không xếp dọc; mỗi dòng phương án cao ≥ 48px; "Bỏ chọn" xuống dòng riêng, vùng chạm ≥ 48px | Như expanded, chữ tự xuống dòng | Nút "Rút đồng ý" `w-full`, trong luồng trang |

- Không cuộn ngang toàn trang ở 375px. Nội dung câu hỏi, phương án, câu trả lời, tên công ty tự ngắt (`break-words`);
  câu trả lời ngắn giữ xuống dòng (`whitespace-pre-wrap`).
- Không thanh thao tác `fixed`/`sticky`.

## 9. Khả năng tiếp cận

- Form: mỗi câu là `<fieldset>` + `<legend>`; dòng loại · bắt buộc gắn bằng `aria-describedby`; "Bắt buộc" là chữ,
  không chỉ là dấu `*`. Lỗi dưới câu có `id`, `Textarea` có `aria-invalid` + `aria-describedby` trỏ bộ đếm và lỗi;
  `<fieldset>` có lỗi gắn `aria-describedby` tới lỗi.
- Radio gốc: phím mũi tên đổi lựa chọn theo trình duyệt; mỗi `<label>` bao trọn ô radio + chữ (vùng chạm ≥ 48px
  dưới `sm`).
- Khối lưu ý có `role="note"`; icon `Info`, `AlertCircle` `aria-hidden="true"`.
- Thông báo 409 `role="alert"`; sau 409 focus vào tiêu đề "Câu hỏi sàng lọc" (`tabIndex={-1}`).
- Nút "Nộp đơn" khoá vì F1 lỗi: câu lý do hiện thành chữ và gắn `aria-describedby` (không chỉ `title`).
- Trang chi tiết: tiêu đề hai khối là `h2`; danh sách câu trả lời là `<ol>` (giữ nguyên của `ScreeningAnswerList`).
- Sau khi rút đồng ý thành công: nút đã mở hộp thoại biến mất → focus vào câu trạng thái của khối (`tabIndex={-1}`),
  vùng `aria-live="polite"` đọc "Đã rút đồng ý lưu hồ sơ." (cùng cách trả focus về badge của FR-U08 UI.md mục 9).
- Tương phản: chỉ dùng các cặp đã đo ở UI_GUIDE mục 1c/6 và bảng 5d; không có cặp mới cần đo.

## 10. Không được làm

- Không tô màu, icon ✓/✗, badge hay in đậm theo câu trả lời hoặc theo trạng thái đồng ý; "Có" và "Không" giống hệt
  nhau về hình thức (R-N3, CLAUDE.md §8).
- Không dấu `*` đỏ cho câu bắt buộc; không `m3-warning`/`m3-error` cho khối lưu ý.
- Không tick sẵn ô đồng ý lưu hồ sơ; không để ô này chặn nút "Nộp đơn"; không gộp với ô AI consent (R-K1, UI_GUIDE
  mục 4 sau D3).
- Không nút/ô "Đồng ý lại" ở trang chi tiết (R-K4).
- Không đổi chữ, thứ tự, bố cục của phần cũ trong form (chọn CV, thư giới thiệu, ô AI consent, nút) (R-C6).
- Không thêm tab ở trang chi tiết đơn; không đổi thứ tự bốn khối cũ của tab "Thông tin đơn" (Q12).
- Không hiện bộ câu hỏi **hiện tại** của tin ở trang chi tiết, không đánh dấu "câu hỏi đã thay đổi" (R-S3 của H10).
- Không đổi giao diện tab "Sàng lọc" phía HR khi tách `ScreeningAnswerList` (R-C5).
- Không thêm RadioGroup hay file nào vào `components/ui` (R-C7); không kéo-thả.
- Không đặt hàm gọi `/api/candidates/...` trong `features/screening/` (kiểm tĩnh H10 7.3).
- Không render câu hỏi/câu trả lời bằng HTML; không trim hay cắt câu trả lời khi hiển thị.
- Không nút `fixed`/`sticky` ở đáy màn hình điện thoại.
