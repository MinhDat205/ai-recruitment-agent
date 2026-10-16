# FR-H14 — Giao diện

> Trạng thái: ĐÃ DUYỆT 15/10/2026
>
> Mục 11 của `REQUIREMENT.md` đã chốt (Q1–Q11 và S1–S6); chỉ còn tên method trên `ApplicationOwnerService` và hình dạng
> chính xác của DTO để Plan Mode chốt.
>
> Làm rõ đợt 5 (16/10/2026): 4c sàng lọc liệt kê đủ cột; nền ô gộp dọc 4a.

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 1d (thang chữ), 1h (vùng chạm), 2 (layout HR), 3 (Table, Button,
Checkbox, Dialog, badge trạng thái), 4 (màn hình xếp hạng, giải thích điểm, nội dung AI, so sánh ứng viên, dữ liệu
thiếu), 5 (rỗng/tải/lỗi), 6 (tương phản, chữ trong hàng bảng). Mã `R-…`, `C1`, `Q…` là của `REQUIREMENT.md` cùng thư
mục; `[Q…]` là tham chiếu tới quyết định đã chốt 15/10/2026 ở REQUIREMENT mục 11.

## 1. Trạng thái

> Trạng thái: ĐÃ DUYỆT 15/10/2026
>
> Mục 11 của `REQUIREMENT.md` đã chốt (Q1–Q11 và S1–S6); chỉ còn tên method trên `ApplicationOwnerService` và hình dạng
> chính xác của DTO để Plan Mode chốt.

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| `/hr/jobs/:id/edit` (`?tab=applications`) | Tab "Ứng viên": **màn hình cũ được mở rộng** — thêm cột ô chọn đầu bảng và thanh chọn "Đã chọn {n}/{MAX_COMPARE} · Bỏ chọn · So sánh" phía trên bảng. Mọi cột, nút, sắp xếp, polling hiện có giữ nguyên (R-L4) | Hiện có | FR-H05, FR-H07; FR-H14 (sửa dòng UI_GUIDE mục 7 ở đợt cuối, REQUIREMENT D3) |
| ★`/hr/jobs/:id/compare?ids={uuid},{uuid}[,{uuid}]` | So sánh ứng viên: **màn hình mới** | ★Mới | FR-H14 |

Route `/hr/jobs/:id/compare` đã có sẵn trong bảng UI_GUIDE mục 7 (dòng `★/hr/jobs/:id/compare`), không thêm route nào
khác. Trang nằm trong `HrLayout` (drawer 240px ≥ `lg`, rail < `lg`); mục đang chọn của điều hướng là "Tin tuyển dụng".

## 3. Điểm vào

- Tab "Ứng viên" của `/hr/jobs/:id/edit` → tick 2–3 đơn → nút "So sánh" (điểm vào chính).
- Dán/mở lại URL `/hr/jobs/:id/compare?ids=…` (lịch sử trình duyệt, tải lại trang — R-U1).
- Từ trang hồ sơ đơn mở bằng "Mở hồ sơ", bấm Back của trình duyệt → quay về đúng URL so sánh.

Không thêm điểm vào nào khác (không nút "So sánh" ở `/hr/candidates`, trang hồ sơ đơn, hay Dashboard).

## 4. Bố cục (khung ASCII)

### 4a. Desktop (≥ `lg`) — trang so sánh, 3 cột

Vùng nội dung theo container trang HR hiện có (1200px, `p-6`). Bảng HTML thật (`<table>`), cột tiêu đề hàng rộng 176px,
ba cột dữ liệu chia đều phần còn lại. Không cuộn ngang. `<thead>` dính đầu khi cuộn dọc (`sticky top-0`) — Plan Mode kiểm
phần tử cuộn của `HrLayout`; không dính được thì bỏ, không bắt buộc.

```
 ← Quay lại danh sách ứng viên
 So sánh ứng viên                                                            [Tải lại]
 Kỹ sư DevOps · Chu kỳ tuyển dụng 1 · 3 đơn
 Bảng chỉ hiển thị dữ liệu đã có của từng đơn. Quyết định thuộc về bạn, trên từng hồ sơ.
┌──────────────────────────────────────────────────────────────────────────────────────┐
│ (i) Các đơn được chấm theo phiên bản rubric khác nhau. Hàng tiêu chí có lưu ý bên    │  ← chỉ khi
│     dưới được hiển thị theo đúng phiên bản của từng đơn, không quy đổi.              │    rubricVersionsDiffer
└──────────────────────────────────────────────────────────────────────────────────────┘
┌──────────────────┬──────────────────────┬──────────────────────┬──────────────────────┐
│                  │ Phạm Quốc Bảo        │ Lê Văn Đức           │ Trần Minh Hoàng      │
│                  │ [Chờ duyệt]          │ [Chờ duyệt]          │ [Đã rút đơn]         │
│                  │ Nộp 28/08/2026 17:48 │ Nộp 28/08/2026 18:53 │ Nộp 15/10/2026 09:10 │
│                  │ Chấm lúc 28/08 18:05 │ Chấm lúc 28/08 18:55 │ Chấm lúc 15/10 09:20 │
│                  │ Đang có lượt chấm    │                      │                      │
│                  │ mới; bảng hiện kết   │                      │                      │
│                  │ quả lượt hoàn tất…   │                      │                      │
│                  │ [Mở hồ sơ]           │ [Mở hồ sơ]           │ [Mở hồ sơ]           │
│                  │ [Mời phỏng vấn]      │ [Mời phỏng vấn]      │                      │
╞══════════════════╧══════════════════════╧══════════════════════╧══════════════════════╡
│ Điểm tổng hợp (Backend tính theo trọng số — FR-H05)                                  │
├──────────────────┬──────────────────────┬──────────────────────┬──────────────────────┤
│░Tổng điểm       ░│░81.00               ░│░59.50               ░│░70.00               ░│ ← nền trung
│░[Khác nhau]     ░│░                    ░│░                    ░│░                    ░│   tính CẢ hàng
│░Hạng (i)        ░│░1                   ░│░3                   ░│░2                   ░│
│░[Khác nhau]     ░│░                    ░│░                    ░│░                    ░│
╞══════════════════╧══════════════════════╧══════════════════════╧══════════════════════╡
│ Điểm theo tiêu chí                                                                   │
├──────────────────┬──────────────────────┬──────────────────────┬──────────────────────┤
│░Kinh nghiệm vận ░│░4/5 điểm            ░│░3/5 điểm            ░│░3/5 điểm            ░│
│░hành hệ thống/  ░│░Trọng số 35%        ░│░Trọng số 35%        ░│░Trọng số 35%        ░│
│░CI-CD           ░│░                    ░│░                    ░│░                    ░│
│░[Khác nhau]     ░│░                    ░│░                    ░│░                    ░│
│░[> Lý giải và   ░│░                    ░│░                    ░│░                    ░│
│░   trích dẫn]   ░│░                    ░│░                    ░│░                    ░│
├──────────────────┼──────────────────────┼──────────────────────┼──────────────────────┤
│ Kỹ năng mềm &    │ 4/5 điểm             │ 4/5 điểm             │ 4/5 điểm             │ ← bằng nhau:
│ giao tiếp        │ Trọng số 15%         │ Trọng số 15%         │ Trọng số 15%         │   không tô
│ [v Lý giải và    │ Diễn giải của AI cho │ Diễn giải của AI …   │ Diễn giải của AI …   │
│    trích dẫn]    │ tiêu chí này — không │ …                    │ …                    │
│                  │ phải khuyến nghị…    │                      │                      │
│                  │ <lý giải nguyên văn> │                      │                      │
│                  │ ┃ "5 năm vận hành    │ ┃ "Phối hợp với đội  │ Không tìm thấy trích │
│                  │ ┃ cụm Kubernetes…"   │ ┃ phát triển…"       │ dẫn nào trong CV cho │
│                  │ ┃ Nguồn: Kinh nghiệm │ ┃ Nguồn: Kinh nghiệm │ tiêu chí này.        │
├──────────────────┼──────────────────────┼──────────────────────┼──────────────────────┤
│ Viết tài liệu    │ Chưa có dữ liệu      │ 2/5 điểm             │ 3/5 điểm             │ ← R-C4
│ vận hành         │ Đơn này được chấm    │ Trọng số 5%          │ Trọng số 5%          │
│ Các đơn được     │ theo phiên bản rubric│                      │                      │
│ chấm theo phiên  │ không có tiêu chí    │                      │                      │
│ bản rubric khác  │ này.                 │                      │                      │
│ nhau ở tiêu chí  │                      │                      │                      │
│ này.             │                      │                      │                      │
╞══════════════════╧══════════════════════╧══════════════════════╧══════════════════════╡
│ Hồ sơ từ CV (dữ liệu trích xuất hiện tại của CV đã nộp)                              │
├──────────────────┬──────────────────────┬──────────────────────┬──────────────────────┤
│░Số năm kinh     ░│░4,5 năm (54 tháng)  ░│░Chưa có dữ liệu     ░│░2 năm (24 tháng)    ░│
│░nghiệm [Khác    ░│░                    ░│░                    ░│░                    ░│
│░nhau]           ░│░                    ░│░                    ░│░                    ░│
│ Kỹ năng          │ Docker · Kubernetes  │ Bash · Python ·      │ Chưa có dữ liệu      │
│                  │ · Terraform · AWS    │ Jenkins              │                      │
│ Học vấn          │ Kỹ sư · Kỹ thuật máy │ Cử nhân · CNTT ·     │ Chưa có dữ liệu      │
│                  │ tính · ĐH Bách khoa  │ ĐH Đà Nẵng ·         │                      │
│                  │ · 2014–2019          │ 2016–2020            │                      │
╞══════════════════╧══════════════════════╧══════════════════════╧══════════════════════╡
│ [✦ Do AI tạo] Tóm tắt điểm mạnh, điểm yếu (FR-H06)                                   │
│ Tổng hợp của AI dựa trên các tiêu chí đã chấm — không phải khuyến nghị tuyển dụng,  │
│ HR tự xem xét từng hồ sơ.                                                            │
├──────────────────┬──────────────────────┬──────────────────────┬──────────────────────┤
│ Tóm tắt          │ ┌──────────────────┐ │ ┌──────────────────┐ │ Báo cáo tổng hợp của │
│                  │ │ <summary>        │ │ │ <summary>        │ │ AI đang được xử lý.  │
│ Điểm mạnh        │ │ • Kinh nghiệm …  │ │ │ • …              │ │                      │
│                  │ │   — <point>      │ │ │                  │ │                      │
│ Điểm cần cải     │ │ • …              │ │ │ Không có mục nào.│ │                      │
│ thiện            │ └──────────────────┘ │ └──────────────────┘ │                      │
╞══════════════════╧══════════════════════╧══════════════════════╧══════════════════════╡
│ Câu trả lời sàng lọc (chỉ để tham khảo, không dùng để chấm điểm hay xếp hạng)        │
├──────────────────┬──────────────────────┬──────────────────────┬──────────────────────┤
│░Bạn có thể bắt  ░│░Có                  ░│░Không               ░│░Không có câu hỏi này░│
│░đầu trong 2 tuần░│░                    ░│░                    ░│░lúc ứng viên nộp đơn░│
│░không? (Có/Không)│░                    ░│░                    ░│░                    ░│
│░[Khác nhau]     ░│░                    ░│░                    ░│░                    ░│
│ Mức lương mong   │ 25 triệu             │ Ứng viên không trả   │ Không có câu hỏi …   │
│ muốn (Trả lời    │                      │ lời câu này.         │                      │
│ ngắn)            │                      │                      │                      │
└──────────────────┴──────────────────────┴──────────────────────┴──────────────────────┘
```

Tên, số và thời điểm trong khung chỉ để minh hoạ, không phải dữ liệu soát tay (dữ liệu soát tay ở REQUIREMENT 7.4).

- `░` = nền trung tính của hàng có cờ `valuesDiffer` (R-W2): **mọi ô của hàng**, kể cả ô tiêu đề hàng, cùng một nền; chữ
  "Khác nhau" là chip nhỏ dưới tên hàng. Không ô nào được tô riêng.
- Tên ứng viên là chữ thường `text-m3-title-md` (không phải liên kết — liên kết là nút "Mở hồ sơ").
- Thứ tự cột = thứ tự `columns` backend trả (= thứ tự `ids`, R-O1). Giao diện không sắp lại.
- Hàng "Hạng" có icon `Info` cạnh nhãn và câu chú thích **hiện sẵn thành chữ** ngay dưới nhãn (`text-m3-body-sm`, không
  tooltip — tooltip không mở được bằng chạm và trình đọc màn hình dễ bỏ qua): "Hạng trong toàn bộ đơn của tin này (FR-H05),
  không phải hạng trong nhóm đang so sánh." `[Q2]`.
- Mở rộng tiêu chí **theo hàng**: nút "Lý giải và trích dẫn" ở ô tiêu đề hàng mở/đóng cùng lúc phần lý giải + evidence
  của **mọi** ô trong hàng, để đọc song song. Mặc định đóng. Phần mở rộng dùng đúng chữ và kiểu của FR-H06
  (`CriterionScoreBreakdown.tsx:58-87`): câu "Diễn giải của AI cho tiêu chí này — không phải khuyến nghị tuyển dụng",
  lý giải nguyên văn, khối trích dẫn viền trái `m3-primary`, nền `m3-primary-container`, dòng "Nguồn: {mục}".
- Ô tiêu chí khác phiên bản (R-C5): mỗi ô ghi tên tiêu chí của chính đơn đó **nếu khác** tên ở tiêu đề hàng (dòng chữ phụ
  "Tên trong rubric của đơn: …"), cùng trọng số/điểm tối đa của đơn đó.
- Kinh nghiệm: "{years} năm ({months} tháng)" — `years` backend quy đổi, dấu thập phân kiểu Việt (`4,5`); không tự tính.
- Kỹ năng: nối bằng " · ", nguyên văn, đủ danh sách (không cắt "+3"), `break-words`.
- Học vấn: mỗi mục một dòng, ghép "bằng cấp · chuyên ngành · trường · bắt đầu–kết thúc" từ các trường khác rỗng.
- Khối giải thích là **nội dung AI** (UI_GUIDE mục 4): mỗi ô có giải thích nằm trong khối AI
  (`bg-m3-surface-container-high` + viền); nhãn "Do AI tạo" + icon `Sparkles` đặt **một lần** ở tiêu đề nhóm. Chỉ đọc,
  không nút Dùng/Lưu/Bỏ qua (như FR-C08). Điểm mạnh/yếu ghi kèm tên tiêu chí (`criterionName`) như tab "Giải thích".
- Khối sàng lọc: mỗi hàng là một câu hỏi theo R-Q; nhãn hàng ghi câu hỏi nguyên văn + loại câu ("Có/Không", "Một lựa
  chọn", "Trả lời ngắn" — đúng nhãn `screeningLabels.ts:8-10`). Đơn `NOT_CAPTURED`/`NO_QUESTIONS`: câu trạng thái R-Q4 ở ô của cột đó, **gộp dọc** qua mọi hàng
  câu hỏi (`rowSpan`), hoặc khi không còn hàng nào (R-Q5) thì là một hàng "Trạng thái" duy nhất.
- Ô gộp dọc (`rowSpan`) ở nhóm sàng lọc và nhóm giải thích luôn nền `bg-m3-surface`, kể cả khi vắt qua hàng "Khác
  nhau"; thông tin khác biệt nằm ở chip trong tiêu đề hàng.
- 2 cột: cùng bố cục, hai cột dữ liệu chia đều.

### 4b. Desktop — tab "Ứng viên" của `/hr/jobs/:id/edit` (màn hình cũ mở rộng)

Phần **mới** là cột đầu và thanh chọn; phần còn lại giữ nguyên (`ApplicationsTab.tsx:241-310`).

```
 Sắp xếp theo [Tổng điểm v]
┌──────────────────────────────────────────────────────────────────────────────────────┐
│ Đã chọn 2/3      [Bỏ chọn]                                            [So sánh]      │  ← thanh chọn
└──────────────────────────────────────────────────────────────────────────────────────┘
┌────┬──────────────┬─────────────┬─────────────────┬──────────────┬──────┬───────────┬──────────────────┐
│Chọn│ Ứng viên     │ Ngày nộp    │ Trạng thái CV   │ Trạng thái   │ Hạng │ Tổng điểm │ Thao tác         │
│    │              │             │                 │ đơn          │      │           │                  │
├────┼──────────────┼─────────────┼─────────────────┼──────────────┼──────┼───────────┼──────────────────┤
│[x] │ Phạm Quốc Bảo│ 28/08 17:48 │ [Đã xử lý xong] │ [Chờ duyệt]  │ 1    │ 81.00     │ [Xem CV][Hồ sơ]… │
│[x] │ Lê Văn Đức   │ 28/08 18:53 │ [Đã xử lý xong] │ [Chờ duyệt]  │ 3    │ 59.50     │ …                │
│[ ] │ Trần M. Hoàng│ 15/10 09:10 │ [Đã xử lý xong] │ [Đã rút đơn] │ 2    │ 70.00     │ …                │
│[-] │ Nguyễn H. Sơn│ 15/10 10:02 │ [Đã xử lý xong] │ [Chờ duyệt]  │ —    │ —         │ …                │
│    │ Chưa có lượt chấm hoàn tất nên chưa so sánh được.                                                  │
└────┴──────────────┴─────────────┴─────────────────┴──────────────┴──────┴───────────┴──────────────────┘
```

Tên, số và thời điểm trong khung chỉ để minh hoạ, không phải dữ liệu soát tay (dữ liệu soát tay ở REQUIREMENT 7.4).

- `[-]` = ô khoá. Câu lý do hiện thành chữ ngay dưới tên ứng viên (`text-m3-body-sm`), gắn `aria-describedby` vào ô.
  Ba lý do (mục 7): chưa chấm xong; khác chu kỳ với đơn chọn đầu `[Q7]`; đã chọn đủ {MAX_COMPARE} `[Q7]`. Khi đã đủ {MAX_COMPARE}, câu "Đã chọn
  đủ {MAX_COMPARE} đơn." **không** lặp ở từng dòng — hiện một lần ở thanh chọn, ô khoá trỏ `aria-describedby` về câu đó.
- Thanh chọn luôn hiện khi danh sách có ≥ 1 đơn; "Đã chọn 0/{MAX_COMPARE}" khi chưa chọn; "Bỏ chọn" ẩn khi n = 0; "So sánh" khoá khi
  n < 2, kèm câu "Chọn ít nhất 2 đơn để so sánh." (chữ, không chỉ `title`).
- Thanh chọn nằm trong luồng trang (không `fixed`/`sticky`).

### 4c. Điện thoại (375px, < `md`) — trang so sánh xếp theo hàng

Dưới `md` **không** dùng bảng: mỗi hàng so sánh thành một khối, trong khối liệt kê giá trị của từng ứng viên theo thứ tự
cột, có số thứ tự và tên ứng viên. Không cuộn ngang. Đầu trang là các thẻ ứng viên (đầu cột của 4a).

```
┌───────────────────────────────┐
│ ← Quay lại danh sách ứng viên │
│ So sánh ứng viên              │
│ Kỹ sư DevOps · Chu kỳ 1 · 3 đơn│
│ Bảng chỉ hiển thị dữ liệu đã  │
│ có… Quyết định thuộc về bạn…  │
│ ┌───────────────────────────┐ │
│ │       Tải lại             │ │
│ └───────────────────────────┘ │
│ ┌───────────────────────────┐ │
│ │ 1 · Phạm Quốc Bảo         │ │
│ │ [Chờ duyệt]               │ │
│ │ Nộp 28/08/2026 17:48      │ │
│ │ Chấm lúc 28/08/2026 18:05 │ │
│ │ ┌───────────┐┌──────────┐ │ │
│ │ │ Mở hồ sơ  ││Mời phỏng │ │ │
│ │ └───────────┘│vấn       │ │ │
│ │              └──────────┘ │ │
│ └───────────────────────────┘ │
│ ┌ 2 · Lê Văn Đức … ┐          │
│ ┌ 3 · Trần Minh Hoàng … ┐     │
│                               │
│ Điểm tổng hợp                 │
│ ┌░░░░░░░░░░░░░░░░░░░░░░░░░░░┐ │
│ │░Tổng điểm   [Khác nhau]  ░│ │
│ │░1 · Phạm Quốc Bảo   81.00░│ │
│ │░2 · Lê Văn Đức      59.50░│ │
│ │░3 · Trần Minh Hoàng 70.00░│ │
│ └░░░░░░░░░░░░░░░░░░░░░░░░░░░┘ │
│ ┌ Hạng (i) [Khác nhau] … ┐    │
│                               │
│ Điểm theo tiêu chí            │
│ ┌░░░░░░░░░░░░░░░░░░░░░░░░░░░┐ │
│ │░Kinh nghiệm vận hành hệ   ░│ │
│ │░thống/CI-CD [Khác nhau]   ░│ │
│ │░1 · Phạm Quốc Bảo         ░│ │
│ │░    4/5 điểm · TS 35%     ░│ │
│ │░2 · Lê Văn Đức            ░│ │
│ │░    3/5 điểm · TS 35%     ░│ │
│ │░3 · Trần Minh Hoàng       ░│ │
│ │░    3/5 điểm · TS 35%     ░│ │
│ │░┌───────────────────────┐░│ │
│ │░│ > Lý giải và trích dẫn│░│ │
│ │░└───────────────────────┘░│ │
│ └░░░░░░░░░░░░░░░░░░░░░░░░░░░┘ │
│ … (mỗi tiêu chí một khối)     │
│                               │
│ Hồ sơ từ CV                   │
│ ┌ Số năm kinh nghiệm … ┐      │
│ ┌───────────────────────────┐ │
│ │ Kỹ năng                   │ │
│ │ 1 · Phạm Quốc Bảo         │ │
│ │     Docker · Kubernetes · │ │
│ │     Terraform · AWS       │ │
│ │ 2 · Lê Văn Đức            │ │
│ │     Bash · Python · …     │ │
│ │ 3 · Trần Minh Hoàng       │ │
│ │     Chưa có dữ liệu       │ │
│ └───────────────────────────┘ │
│ ┌ Học vấn … ┐                 │
│ [✦ Do AI tạo] Tóm tắt …       │
│ ┌ 1 · Phạm Quốc Bảo (khối AI)┐│
│ Câu trả lời sàng lọc …        │
└───────────────────────────────┘
```

Tên, số và thời điểm trong khung chỉ để minh hoạ, không phải dữ liệu soát tay (dữ liệu soát tay ở REQUIREMENT 7.4).

- Mở "Lý giải và trích dẫn" của một khối tiêu chí: phần lý giải + evidence hiện **dưới từng ứng viên** trong khối, theo
  thứ tự cột.
- "TS" là viết tắt chỉ trong khung ASCII; giao diện ghi đủ "Trọng số".
- Nút trong thẻ ứng viên: hai nút cùng hàng, mỗi nút `flex-1`, cao ≥ 48px (UI_GUIDE 1h); một nút thì `w-full`.
- Nền `░` của khối "Khác nhau" phủ **cả khối** (mọi ứng viên trong khối), không phủ riêng dòng của một ứng viên.
- Nhóm sàng lọc: mỗi khối câu hỏi liệt kê **đủ** mọi cột theo thứ tự `columns`, không ẩn cột nào. Ô của cột
  `NOT_CAPTURED` dùng câu ngắn "Không có câu trả lời được ghi nhận."; ô của cột `NO_QUESTIONS` dùng câu có sẵn "Không có
  câu hỏi này lúc ứng viên nộp đơn." Khối "Trạng thái" ở đầu nhóm giữ câu đầy đủ của `NOT_CAPTURED`/`NO_QUESTIONS` (R-Q4)
  cho các cột đó.

### 4d. Điện thoại (375px) — tab "Ứng viên"

Bảng hiện có giữ cách hiện ở 375px như hôm nay (bảng shadcn trong khung cuộn ngang của chính bảng —
`components/ui/table.tsx`); cột ô chọn ở **đầu** nên luôn nhìn thấy mà không cần cuộn. Vùng chạm ô chọn ≥ 48×48px (ô
16px nằm giữa một `label` 48px). Thanh chọn xếp dọc:

```
┌───────────────────────────────┐
│ Đã chọn 2/3                   │
│ ┌────────────┐┌─────────────┐ │
│ │  Bỏ chọn   ││  So sánh    │ │
│ └────────────┘└─────────────┘ │
└───────────────────────────────┘
```

## 5. Component

### 5a. File mới (chốt tên — `REQUIREMENT.md` mục 7.3 quét đúng các file này)

| File | Vai trò |
|---|---|
| `frontend/src/pages/HrJobComparePage.tsx` | Trang `/hr/jobs/:id/compare`: đọc `id`, `ids` (`useSearchParams`, giữ nguyên chuỗi — R-S9), gọi C1, đầu trang, nút "Tải lại", chọn bố cục bảng (≥ `md`) / theo hàng (< `md`), trạng thái mục 6 |
| `frontend/src/features/comparison/types.ts` | Kiểu khớp `ApplicationComparisonResponse` (REQUIREMENT mục 4.4) |
| `frontend/src/features/comparison/api.ts` | `getApplicationComparisonRequest(jobId, idsParam)` — truyền `ids` nguyên văn |
| `frontend/src/features/comparison/queries.ts` | `useApplicationComparisonQuery(jobId, idsParam)`; khoá `['hr-application-comparison', jobId, idsParam]`; `retry` không thử lại với 4xx (kiểu `applicationDetail/queries.ts:56-64`); **không** `refetchInterval` (R-R1); hàm `comparisonInvalidateKeys(...)` cho R-H3 |
| `frontend/src/features/comparison/compareLimits.ts` | `MAX_COMPARE = 3`, `MIN_COMPARE = 2` — hằng duy nhất, `ApplicationsTab` import |
| `frontend/src/features/comparison/comparisonLabels.ts` | Mọi chuỗi mục 7; hàm định dạng kinh nghiệm, học vấn |
| `frontend/src/features/comparison/ComparisonTable.tsx` | Bố cục bảng (4a): `<table>` thuần (không `TableRow` shadcn — tránh nền hover `#F8F8F8` đè nền "Khác nhau", UI_GUIDE mục 6) |
| `frontend/src/features/comparison/ComparisonStacked.tsx` | Bố cục theo hàng (4c) |
| `frontend/src/features/comparison/ComparisonColumnHeader.tsx` | Đầu cột / thẻ ứng viên: tên, `ApplicationStatusBadge`, ngày nộp, "Chấm lúc", ghi chú lượt mới, "Mở hồ sơ", "Mời phỏng vấn" (mở `InterviewInvitationDialog`) |
| `frontend/src/features/comparison/CriterionCompareCell.tsx` | Một ô tiêu chí: điểm/tối đa, trọng số, tên khác phiên bản, phần mở rộng (lý giải + evidence) |
| `frontend/src/features/comparison/DiffMark.tsx` | Chip "Khác nhau" (chữ, không icon màu) |
| `frontend/src/features/scoring/EvidenceQuoteList.tsx` | **Tách** phần hiện evidence + câu rỗng từ `CriterionScoreBreakdown.tsx:66-87` thành component dùng chung; `CriterionScoreBreakdown` dùng lại nó — giao diện trang hồ sơ đơn **không đổi** (một kiểu trích dẫn duy nhất) |

### 5b. File có sẵn bị sửa

| File | Thay đổi |
|---|---|
| `frontend/src/features/scoring/ApplicationsTab.tsx` | Cột "Chọn" đầu bảng (`Checkbox`), state lựa chọn giữ thứ tự tick theo `id` (R-L3), điều kiện khoá + câu lý do (R-L1, R-L2), thanh chọn (4b, 4d), `navigate('/hr/jobs/{jobId}/compare?ids=' + ids.join(','))`. Không đổi cột/nút/sắp xếp/polling hiện có |
| `frontend/src/features/scoring/types.ts` | `ApplicationHrListItem` thêm `recruitmentCycle: number` `[Q7]` |
| `frontend/src/features/scoring/CriterionScoreBreakdown.tsx` | Dùng `EvidenceQuoteList` (tách ra, không đổi giao diện) |
| `frontend/src/App.tsx` | Route `/hr/jobs/:id/compare` cạnh `/hr/jobs/:id/edit` (`:101`), cùng lớp bảo vệ vai trò HR |

Không sửa: `InterviewInvitationDialog.tsx` và cả `features/interviewinvitation/` (R-H2), `ApplicationActionBar.tsx`,
`HrJobEditPage.tsx` (tab đã nhận `?tab=applications` — `:470-482`), `components/ui/*` (`Checkbox` đã có —
`components/ui/checkbox.tsx`), `ExplanationReport.tsx`, trang hồ sơ đơn.

### 5c. Dùng lại có sẵn

| Có sẵn | Cách dùng |
|---|---|
| `InterviewInvitationDialog` | `application={{ id, candidateName }}`, `jobId`, `invalidateQueryKeys` = khoá C1 + `applicationDetailInvalidateKeys(id, jobId)` (đã gồm danh sách theo Job); `mode` mặc định `'invite'` |
| `ApplicationStatusBadge` | Badge trạng thái ở đầu cột (bảng màu trung tính UI_GUIDE mục 3) |
| `Checkbox`, `Button`, `Label` | Ô chọn, nút |
| `formatTotalScore`, `EMPTY_VALUE_PLACEHOLDER` (`lib/score.ts`) | Tổng điểm 2 chữ số như danh sách |
| `formatDateTimeVi` (`lib/date.ts`) | "Nộp …", "Chấm lúc …" |
| `SCREENING_ANSWER_TEXT` (`features/screening/screeningLabels.ts:65-77`) | Câu "Có"/"Không", không trả lời, `NOT_CAPTURED`, `NO_QUESTIONS` — dùng đúng câu của FR-H10/U09 |
| `extractErrorMessage` (`lib/httpError`) | Đọc `message` backend (R-S8), fallback mục 7 |

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Nền trang | `bg-m3-surface-container` | — | Như các trang HR |
| Bảng / khối hàng (4c) | `bg-m3-surface` | viền `border-m3-outline-variant` (phân khối) | Level 0 |
| Tiêu đề nhóm ("Điểm tổng hợp"…) | `bg-m3-surface` | `text-m3-on-surface` `text-m3-title-md` | — |
| Tên ứng viên | — | `text-m3-on-surface` `text-m3-title-md` | **Không** đổi kiểu theo điểm |
| Giá trị điểm, hạng, kinh nghiệm | — | `text-m3-on-surface` `text-m3-body-md` | Cùng kiểu mọi cột (R-W1); không `font-semibold` |
| Hàng/khối "Khác nhau" | `bg-m3-surface-container` (cả hàng) | Chữ **chỉ** `text-m3-on-surface` (`m3-on-surface-variant` trên nền này chỉ 4.24:1 — UI_GUIDE mục 6) | Trung tính; không `m3-primary-container` (dễ đọc thành "được chọn") |
| Chip "Khác nhau" | `bg-m3-surface` | `text-m3-on-surface` `text-m3-label-md`, viền `border-m3-outline` | Chữ, không icon; bo `rounded-m3-xs` |
| "Chưa có dữ liệu", câu trạng thái sàng lọc, "Ngày nộp", "Chấm lúc" | — | `text-m3-on-surface-variant` trên `m3-surface`; trong hàng "Khác nhau" đổi sang `text-m3-on-surface` | — |
| Ghi chú lượt mới (R-D10) | — | `text-m3-on-surface` `text-m3-body-sm` + icon `Info` `text-m3-on-surface` | Không `m3-warning` |
| Khối lưu ý rubric (4a) | `bg-m3-surface-container` | `text-m3-on-surface`; viền `border-m3-outline-variant`; icon `Info` | Như khối lưu ý FR-H10/H12; không vàng/đỏ |
| Trích dẫn evidence | `bg-m3-primary-container` | viền trái `border-m3-primary`; chữ `text-m3-on-surface` (kể cả "Nguồn") | Đúng UI_GUIDE mục 4 (qua `EvidenceQuoteList`) |
| Khối AI (giải thích) | `bg-m3-surface-container-high` | viền `border-m3-outline-variant` (bắt buộc); chữ chỉ `text-m3-on-surface` | UI_GUIDE mục 4 |
| Nhãn "Do AI tạo" | — | `text-m3-on-surface` + icon `Sparkles` `aria-hidden` | — |
| Nút "Mở hồ sơ", "Mời phỏng vấn", "Bỏ chọn", "Tải lại" | — | `variant="outline"` | Cùng mức nhấn mạnh — "Mời phỏng vấn" không nổi hơn ở cột nào |
| Nút "So sánh" | `bg-m3-primary` | `text-m3-on-primary` (Filled — hành động chính của thanh chọn) | — |
| Ô chọn | — | viền `border-m3-outline` (≥ 3:1, UI_GUIDE mục 6) | Khoá: theo 1h (38% / 12%) |
| Câu lý do khoá ô chọn | — | `text-m3-on-surface-variant` `text-m3-body-sm` (trong hàng bảng: 4.55:1 khi hover) | — |
| Lỗi tải / lỗi lựa chọn | — | icon `AlertCircle` `text-m3-error` + chữ `text-m3-on-surface` (nằm trên nền trang) | UI_GUIDE mục 6 |

Không có token mới; mọi cặp màu đã đo ở UI_GUIDE mục 1c/6.

## 6. Trạng thái hiển thị

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Đang tải C1 | Trang so sánh | Đầu trang (nút quay lại + tiêu đề "So sánh ứng viên") + skeleton xám: một hàng đầu cột và 6 hàng; không spinner toàn trang |
| Lỗi 400 `INVALID_COMPARE_SELECTION` | Trang so sánh | Icon `AlertCircle` + câu `message` backend (R-S8) + liên kết "Quay lại danh sách ứng viên" |
| Lỗi 409 `COMPARE_APPLICATION_NOT_SCORED` | Trang so sánh | Như trên + câu phụ "Bạn có thể chấm điểm hồ sơ ở tab Ứng viên rồi so sánh lại." |
| 403 / 404 (`JOB_NOT_FOUND`, Job công ty khác) | Trang so sánh | Một thông điệp "Không tìm thấy tin tuyển dụng." + liên kết "Về danh sách tin tuyển dụng" (`/hr/jobs`) — 403 và 404 cùng câu (UI_GUIDE mục 5) |
| 404 `COMPANY_NOT_FOUND` | Trang so sánh | Câu backend + liên kết tạo hồ sơ công ty như các trang HR khác |
| Lỗi mạng / 5xx | Trang so sánh | "Không tải được dữ liệu so sánh, vui lòng thử lại." + nút "Thử lại" |
| Có dữ liệu, 2 hoặc 3 cột | Trang so sánh | 4a / 4c |
| Thiếu dữ liệu trong ô | Mọi hàng | "Chưa có dữ liệu" (R-D5–R-D7, R-C4); không ẩn hàng, không ẩn cột |
| Giải thích chưa có | Ô giải thích | `PENDING`: "Báo cáo tổng hợp của AI đang được xử lý."; `FAILED`: "Không tạo được báo cáo tổng hợp của AI cho lượt chấm này." (chữ thường, không khối AI) |
| Lượt chấm mới đang chạy `[Q6]` | Đầu cột | Ghi chú R-D10; bảng **không** tự tải lại; nút "Tải lại" ở đầu trang |
| Rubric khác phiên bản | Đầu trang + hàng | Khối lưu ý 4a + câu ở hàng (R-C4, R-C5) |
| Đang mở hộp thoại mời | Trang so sánh | Hộp thoại có sẵn (trạng thái của FR-H12 UI.md mục 6); gửi xong C1 tải lại, focus về tên ứng viên của cột đó |
| "AI đang xử lý" | — | **Không có**: H14 không gọi AI |
| Tab "Ứng viên": 0 đơn | Tab | Giữ câu rỗng hiện có; không hiện thanh chọn |
| Tab "Ứng viên": không đơn nào chọn được | Tab | Thanh chọn "Đã chọn 0/{MAX_COMPARE}"; mọi ô khoá kèm lý do; "So sánh" khoá |

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Thanh chọn | "Đã chọn {n}/{MAX_COMPARE}" · nút "Bỏ chọn" · nút "So sánh" · khi n < 2: "Chọn ít nhất 2 đơn để so sánh." · khi đủ: "Đã chọn đủ {MAX_COMPARE} đơn." |
| Cột ô chọn | Tiêu đề cột "Chọn" · `aria-label` ô: "Chọn đơn của {tên ứng viên} để so sánh" |
| Lý do khoá ô | Chưa chấm xong: "Chưa có lượt chấm hoàn tất nên chưa so sánh được." · khác chu kỳ `[Q7]`: "Khác chu kỳ tuyển dụng với đơn đã chọn." · đủ {MAX_COMPARE}: dùng câu "Đã chọn đủ {MAX_COMPARE} đơn." ở thanh chọn |
| Đầu trang | Liên kết "Quay lại danh sách ứng viên" · tiêu đề "So sánh ứng viên" · dòng phụ "{tên tin} · Chu kỳ tuyển dụng {n} · {k} đơn" · câu "Bảng chỉ hiển thị dữ liệu đã có của từng đơn. Quyết định thuộc về bạn, trên từng hồ sơ." · nút "Tải lại" |
| Khối lưu ý rubric | "Các đơn được chấm theo phiên bản rubric khác nhau. Hàng tiêu chí có lưu ý bên dưới được hiển thị theo đúng phiên bản của từng đơn, không quy đổi." |
| Đầu cột | "Nộp {dd/MM/yyyy HH:mm}" · "Chấm lúc {dd/MM/yyyy HH:mm}" · ghi chú `[Q6]`: "Đang có lượt chấm mới; bảng đang hiện kết quả lượt hoàn tất lúc {t}." · nút "Mở hồ sơ" · nút "Mời phỏng vấn" · số thứ tự (4c): "{i} · {tên}" |
| Nhóm hàng | "Điểm tổng hợp" · "Điểm theo tiêu chí" · "Hồ sơ từ CV" + phụ "Dữ liệu trích xuất hiện tại của CV đã nộp" · "Tóm tắt điểm mạnh, điểm yếu" + nhãn "Do AI tạo" · "Câu trả lời sàng lọc" + phụ "Chỉ để tham khảo, không dùng để chấm điểm hay xếp hạng." |
| Hàng | "Tổng điểm" · "Hạng" + chú thích `[Q2]` "Hạng trong toàn bộ đơn của tin này (FR-H05), không phải hạng trong nhóm đang so sánh." · "Số năm kinh nghiệm" · "Kỹ năng" · "Học vấn" · "Tóm tắt" · "Điểm mạnh" · "Điểm cần cải thiện" (giữ `ExplanationReport.tsx:95-96`) |
| Chip | "Khác nhau" |
| Ô tiêu chí | "{score}/{max} điểm" · "Trọng số {w}%" · nút hàng "Lý giải và trích dẫn" · "Diễn giải của AI cho tiêu chí này — không phải khuyến nghị tuyển dụng" (giữ của FR-H06) · "Nguồn: {mục}" · "Không tìm thấy trích dẫn nào trong CV cho tiêu chí này." (giữ) · khác phiên bản: "Tên trong rubric của đơn: {tên}" · thiếu tiêu chí: "Chưa có dữ liệu" + "Đơn này được chấm theo phiên bản rubric không có tiêu chí này." · câu ở tiêu đề hàng lệch: "Các đơn được chấm theo phiên bản rubric khác nhau ở tiêu chí này." |
| Kinh nghiệm | "{years} năm ({months} tháng)" · thiếu: "Chưa có dữ liệu" |
| Dữ liệu thiếu | "Chưa có dữ liệu" (UI_GUIDE mục 4) |
| Giải thích (giữ câu của `ExplanationReport.tsx`) | Câu lưu ý dưới tiêu đề nhóm (`:86-87`): "Tổng hợp của AI dựa trên các tiêu chí đã chấm — không phải khuyến nghị tuyển dụng, HR tự xem xét từng hồ sơ." · `PENDING` (`:77`): "Báo cáo tổng hợp của AI đang được xử lý." · `FAILED` (`:78`): "Không tạo được báo cáo tổng hợp của AI cho lượt chấm này." · danh sách rỗng (`:14`): "Không có mục nào." · tiêu đề (`:95-96`): "Điểm mạnh" · "Điểm cần cải thiện" · mỗi điểm: "{criterionName} — {point}" (`:19`) |
| Sàng lọc (giữ câu FR-H10/U09) | "Có" · "Không" · "Ứng viên không trả lời câu này." · `NOT_CAPTURED`: "Đơn này được nộp trước khi hệ thống ghi nhận câu trả lời sàng lọc, nên không có câu trả lời để hiển thị." · `NO_QUESTIONS`: "Tin tuyển dụng chưa có câu hỏi sàng lọc vào thời điểm ứng viên nộp đơn ({t})." · **mới**: "Không có câu hỏi này lúc ứng viên nộp đơn." · **mới** (ô 4c của cột `NOT_CAPTURED`): "Không có câu trả lời được ghi nhận." · loại câu (giữ `screeningLabels.ts:8-10`): "Có/Không" · "Một lựa chọn" · "Trả lời ngắn" |
| Lỗi backend (REQUIREMENT R-S8 — đúng từng chữ) | "Danh sách đơn cần so sánh không hợp lệ." · "Mỗi đơn chỉ được chọn một lần." · "Chọn từ 2 đến 3 đơn để so sánh." · "Có đơn không thuộc tin tuyển dụng này." · "Chỉ so sánh được các đơn cùng một chu kỳ tuyển dụng." · "Có đơn chưa có lượt chấm điểm hoàn tất. Chỉ so sánh được các đơn đã chấm xong." |
| Lỗi frontend / fallback | Tải: "Không tải được dữ liệu so sánh, vui lòng thử lại." · nút "Thử lại" · 403/404: "Không tìm thấy tin tuyển dụng." · liên kết "Về danh sách tin tuyển dụng" · câu phụ 409: "Bạn có thể chấm điểm hồ sơ ở tab Ứng viên rồi so sánh lại." |

Định dạng số: tổng điểm `formatTotalScore` (2 chữ số, như danh sách); điểm tiêu chí và trọng số như
`CriterionScoreBreakdown` (giữ nguyên chuỗi số backend trả); năm kinh nghiệm theo `years` backend, hiện dấu thập phân
phẩy. Giờ: `formatDateTimeVi`.

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Trang so sánh | Tab "Ứng viên" |
|---|---|---|
| Expanded (≥ `lg`) | Bảng 4a; cột tiêu đề hàng 176px; 2–3 cột chia đều; đầu cột: hai nút cùng hàng | Như 4b |
| Medium (`md`–`lg`, gồm 768px) | Bảng 4a; cột tiêu đề hàng 128px; chữ tự xuống dòng (`break-words`); đầu cột: hai nút xếp dọc `w-full`; trích dẫn dài xuống dòng trong ô | Như 4b; thanh chọn cùng hàng |
| Compact (< `md`, gồm 375px) | Bố cục theo hàng 4c; không bảng; không cuộn ngang toàn trang | Bảng giữ cách cuộn ngang **trong khung bảng** như hiện nay, cột "Chọn" đầu tiên; thanh chọn 4d |

- Ở 375px: không phần tử nào rộng hơn khung; kỹ năng, học vấn, evidence, câu trả lời dùng `break-words`; chuỗi liền
  không khoảng trắng dùng thêm `break-all` ở ô câu trả lời ngắn sàng lọc.
- Đổi kích thước cửa sổ qua ngưỡng `md` giữ trạng thái mở rộng của từng hàng (state theo khoá hàng, không theo bố cục).
- Không thanh thao tác `fixed`/`sticky` (trừ `<thead>` 4a, nếu dùng được).

## 9. Khả năng tiếp cận

- Bảng 4a: `<table>` có `<caption className="sr-only">` "So sánh {k} ứng viên của tin {tên tin}"; đầu cột là
  `<th scope="col">` chứa tên ứng viên; tiêu đề hàng `<th scope="row">`; tiêu đề nhóm là hàng `<th colSpan scope="colgroup">`.
  Trình đọc màn hình đọc "Tổng điểm, Phạm Quốc Bảo, 81.00".
- Bố cục 4c: mỗi khối là `<section aria-labelledby>`; danh sách giá trị là `<dl>` (`<dt>` "{i} · {tên}", `<dd>` giá trị).
- "Khác nhau" là **chữ** trong tiêu đề hàng (được đọc), nền chỉ là tín hiệu phụ — không truyền thông tin chỉ bằng màu.
- Nút "Lý giải và trích dẫn": `aria-expanded`, `aria-controls` trỏ tới các vùng mở rộng của hàng.
- Nút "Mở hồ sơ"/"Mời phỏng vấn" có tên phân biệt cho trình đọc màn hình: `aria-label` "Mở hồ sơ của {tên}", "Mời
  {tên} phỏng vấn".
- Ô chọn: `aria-label` mục 7; khoá dùng `disabled` + `aria-describedby` trỏ câu lý do (không chỉ `title`). Vùng chạm ≥ 48px.
- Thanh chọn: "Đã chọn {n}/{MAX_COMPARE}" nằm trong vùng `aria-live="polite"`.
- Sau khi gửi lời mời: focus về tên ứng viên ở đầu cột đó (`tabIndex={-1}`).
- Trang lỗi: câu lỗi trong `role="alert"`; liên kết quay lại nhận focus được.
- Icon `Info`, `Sparkles`, `AlertCircle` đều `aria-hidden="true"` khi đi kèm chữ.
- Tương phản: chỉ các cặp ở bảng 5d (đã đo ở UI_GUIDE mục 1c/6); không cặp mới.

## 10. Không được làm

- Không đánh dấu người thắng: không nhãn "Cao nhất"/"Phù hợp nhất"/"Đề xuất", không cúp/sao/huy hiệu, không ✓ ✗, không
  ↑↓, không in đậm/đổi màu giá trị lớn nhất, không thanh tiến trình so tương đối giữa các cột (REQUIREMENT R-W1).
- Không tô màu đỏ–vàng–xanh theo ngưỡng ở bất kỳ ô nào (CLAUDE.md §8, UI_GUIDE mục 4).
- Không tô **ô** khác biệt; chỉ tô **cả hàng/cả khối** bằng nền trung tính (R-W2). Không dùng `m3-primary-container`,
  `m3-tertiary`, `m3-warning`, `m3-error` làm nền hàng khác biệt.
- Không sắp xếp lại cột (theo điểm, hạng, tên…), không kéo-thả đổi chỗ cột, không cột "ghim" người điểm cao (R-O1, R-O2).
- Không hiện "Tiêu chí đã đáp ứng"/"Tiêu chí còn thiếu" (`metCriteria`/`missingCriteria`) trên bảng (R-W4).
- Không hiện điểm trung bình, chênh lệch, phần trăm so với người cao nhất (R-A3).
- Không nút "Từ chối", "Trúng tuyển", "Gửi lại giấy mời", không thao tác áp lên nhiều đơn cùng lúc (R-H2, R-W5, `[Q9]`).
- Không viết hộp thoại mời mới, không sửa `InterviewInvitationDialog`/`ApplicationActionBar` (R-H2).
- Không gọi E3/E4/`/resume/parsed`/`/screening-answers` hay endpoint nào khác ngoài C1 để lấy dữ liệu bảng (R-B1).
- Không tự sửa `ids` trên URL (bỏ trùng, cắt bớt, sắp lại) (R-S9).
- Không tự tải lại định kỳ (R-R1).
- Không ẩn hàng/cột vì thiếu dữ liệu; luôn "Chưa có dữ liệu" (UI_GUIDE mục 4).
- Không dịch, viết lại, cắt ngắn ("+3 kỹ năng") nội dung lấy từ CV hay evidence (CLAUDE.md §2).
- Không hiện `gpa`, liên hệ (email, điện thoại, địa chỉ, LinkedIn), ảnh (`[Q10]`; nguyên tắc bổ sung về thông tin nhân thân).
- Không đổi cột, nút, sắp xếp, polling hiện có của tab "Ứng viên"; không biến ô chọn thành bộ lọc ẩn dòng (R-L4).
- Không render HTML/Markdown, không `dangerouslySetInnerHTML`.
- Không sửa `components/ui`.
- Không sao chép logo, tên thương hiệu hay CSS của trang tuyển dụng nào (CLAUDE.md §8).
