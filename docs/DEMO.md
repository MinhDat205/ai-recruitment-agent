# Kịch bản demo trước hội đồng

Buổi demo khoảng **30 phút**, **gọi AI thật** (Anthropic cho LLM, OpenAI cho embedding), chạy trên bộ dữ liệu demo
(`db/seed/`, 2 công ty, 12 tin đang mở, 9 ứng viên). Thời lượng và kết quả trong tài liệu này lấy từ lượt tập dượt
qua API ngày 20/10/2026 (mục 6). Kết quả của AI có thể khác đôi chút giữa các lần chạy; phần cần nói không phụ thuộc
vào con số cụ thể.

Hai cửa sổ trình duyệt: **cửa sổ thường = HR**, **cửa sổ ẩn danh = ứng viên**.

## 1. Chuẩn bị

### Hôm trước

- [ ] Docker Desktop chạy; `docker compose up -d` lên đủ `recruitment-db`, `recruitment-mail`, `recruitment-storage`.
- [ ] `backend\.env` có đủ 3 biến (lệnh dưới chỉ in tên biến, không in giá trị):

  ```powershell
  foreach ($n in 'ANTHROPIC_API_KEY', 'OPENAI_API_KEY', 'JWT_SECRET') { if (Select-String -Path backend\.env -Pattern "^$n=.+" -Quiet) { "${n}: có" } else { "${n}: THIẾU" } }
  ```

- [ ] Mạng tới API AI:

  ```powershell
  foreach ($h in 'api.anthropic.com', 'api.openai.com') { $r = Test-NetConnection $h -Port 443 -WarningAction SilentlyContinue; "${h}:443 $($r.TcpTestSucceeded)" }
  ```

- [ ] Tài khoản Anthropic/OpenAI còn hạn mức. Một lượt chạy trọn kịch bản tốn khoảng 17 lượt gọi Anthropic và 2–3
  lượt OpenAI (mục 6).
- [ ] `cd frontend; npm install` đã chạy.
- [ ] Tập một lượt theo mục 4. Tập xong **phải nạp lại dữ liệu**, vì lượt tập làm đổi trạng thái (Phạm Quốc Bảo đã
  nộp đơn, Nguyễn Hải Sơn đã được mời).

### Sáng ngày demo

- [ ] Dừng backend nếu đang chạy, rồi **nạp lại toàn chuỗi mục 1** của [db/seed/README.md](../db/seed/README.md)
  (reset → tầng 1 → 2 → 1b → 2b → `install-demo-files.ps1`). Nạp sáng ngày demo để khung giờ phỏng vấn có sẵn và
  ngày đăng tin (tính theo lúc nạp) còn ở tương lai/gần đây.
- [ ] **KHÔNG** nạp bộ test (`seed-test.sql`, mục 8 của README seed): tin của công ty test sẽ hiện trên bảng việc làm
  và trong kết quả tìm việc.
- [ ] Khởi động backend bằng **lệnh demo** (mục 2), chờ dòng `Started BackendApplication`. Khởi động frontend.
- [ ] Mở sẵn MailHog http://localhost:8025 ở một tab.
- [ ] Cửa sổ thường: đăng nhập `hr@demo.local`. Cửa sổ ẩn danh: đăng nhập `pham.quoc.bao@demo.local`.
- [ ] Tắt thông báo hệ điều hành (Focus assist / Do not disturb), đóng ứng dụng chat, phóng to trình duyệt
  110–125% cho máy chiếu.

## 2. Lệnh chạy (PowerShell 5.1, từ gốc repo)

Backend — mọi job nền bật (cần cho chấm điểm và giải thích), thư gửi về MailHog, không ra SMTP thật:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.mail.host=localhost --spring.mail.port=1025 --spring.mail.username= --spring.mail.password= --spring.mail.properties.mail.smtp.auth=false --spring.mail.properties.mail.smtp.starttls.enable=false --logging.level.com.recruitment.ai.sync.SyncAiCaller=DEBUG"
```

Logger DEBUG của `SyncAiCaller` chỉ ghi khi một lần gọi AI đồng bộ phải thử lại; log có thể chứa output của AI, không
chia sẻ log ra ngoài.

Frontend (cửa sổ PowerShell khác):

```powershell
cd frontend
npm run dev
```

| Dịch vụ | Địa chỉ |
|---|---|
| Frontend | http://localhost:5173 |
| API | http://localhost:8080 |
| MailHog | http://localhost:8025 |

Trên dữ liệu demo vừa nạp, job nền **không** tự gọi AI: 12 tin, 4 hồ sơ và 8 CV chính đã có embedding sẵn (đo khi
tập: số embedding trước và sau lượt tập giống nhau).

## 3. Tài khoản dùng trong demo

Mật khẩu chung `Demo1234` (công khai có chủ đích, chỉ cho máy demo cục bộ).

| Tài khoản | Cửa sổ | Vai trò trong kịch bản |
|---|---|---|
| `hr@demo.local` | Thường | HR Công ty Cổ phần Công nghệ Hoàn Mỹ: chấm điểm, hỏi đáp CV, mời phỏng vấn, soạn nháp, tạo tin bằng AI |
| `pham.quoc.bao@demo.local` | Ẩn danh | Ứng viên QA: tìm việc bằng mô tả, nộp đơn Kỹ sư DevOps |
| `nguyen.hai.son@demo.local` | Ẩn danh | Ứng viên DevOps hạng 1: chọn khung giờ phỏng vấn |
| `bui.ngoc.mai@demo.local` | Ẩn danh | Dự phòng: CV builder và gợi ý diễn đạt |
| `do.khanh.linh@demo.local` | Ẩn danh | Dự phòng: có sẵn giấy mời 3 khung giờ chưa chọn |

## 4. Kịch bản theo phút

Ký hiệu: **[AI]** = bước gọi AI thật; **CẮT ĐƯỢC** = bỏ khi lố giờ.

### 0–3' · Giới thiệu và nguyên tắc AI (không gọi AI)

Nói:
- "Hệ thống có hai vai trò, HR và ứng viên, 37 chức năng. Chúng em đặt bốn nguyên tắc cho AI, và kịch bản hôm nay
  sẽ cho thấy từng nguyên tắc trên màn hình."
- (1) AI chỉ chấm **từng tiêu chí** kèm trích dẫn nguyên văn từ CV; (2) **backend** tính tổng điểm và thứ hạng bằng
  công thức trọng số; (3) AI không gán nhãn Đạt/Không đạt, **HR quyết định**; (4) ứng viên phải **đồng ý** thì CV
  mới được AI phân tích.

### 3–7' · Ứng viên tìm việc và nộp đơn (cửa sổ ẩn danh, Phạm Quốc Bảo)

1. Trang việc làm của ứng viên `/candidate` → chọn chế độ **"Tìm bằng mô tả"** → gõ câu A:

   > việc làm từ xa ngành công nghệ thông tin lương trên 25 triệu, làm CI/CD và tự động hoá kiểm thử

   **[AI]** ~3 s. Kết quả tập dượt: điều kiện "Công nghệ thông tin - Phần mềm · Từ xa · Lương từ 25 triệu", phần mô tả
   "làm CI/CD và tự động hoá kiểm thử", 2 việc: **#1 Kỹ sư DevOps**, #2 Chuyên viên Phân tích dữ liệu.

   Nói: "AI chỉ chỉ ra đoạn chữ nào là khu vực, ngành, lương, hình thức. Backend kiểm từng đoạn, rồi lọc bằng SQL. Phần
   còn lại của câu dùng để xếp theo độ gần nghĩa. Câu này chỉ có **một mốc lương**; đây là lỗi chúng em đã sửa
   ([fix-u13-salary-parse](walkthrough/fix-u13-salary-parse.md))."

2. **CẮT ĐƯỢC** — câu B: `lập trình viên frontend React ở Hà Nội, lương trên 20 triệu` → 3 việc ở Hà Nội, Frontend
   (React) đứng đầu. Chỉ ra: "lập trình viên" là chức danh nên AI **không** tự suy ra ngành.
3. Mở **Kỹ sư DevOps** → ứng tuyển (`/jobs/:id/apply`): chọn CV chính; chỉ cho hội đồng nút **"Nộp đơn"** chưa bấm được
   khi chưa tick ô "Tôi đồng ý cho phép CV của tôi được hệ thống AI phân tích và chấm điểm…" → tick.
4. Trả lời 2 câu sàng lọc: "Bạn có thể bắt đầu làm việc trong vòng 2 tuần không?" → **Có**; "Bạn đã vận hành cụm
   Kubernetes ở quy mô bao nhiêu node?" → `Chưa vận hành Kubernetes trong môi trường thực tế.` → **"Nộp đơn"**.

   Nói: "Đồng ý cho AI phân tích là bắt buộc ở cả giao diện, API (`@AssertTrue`) và ràng buộc DB."

### 7–9' · HR thấy đơn mới và bấm chấm (cửa sổ thường)

1. Chuông thông báo → đơn mới của Phạm Quốc Bảo (thông báo đến trong vài giây; email về MailHog sau khoảng 25 s).
2. Mở đơn `/hr/applications/:id` → tab **"Sàng lọc"**: hai câu trả lời vừa nhập. Nói: "Câu trả lời sàng lọc chỉ để HR
   đọc, không đưa vào chấm điểm."
3. Tab **"CV & điểm"** → **"Chấm điểm hồ sơ"** **[AI, job nền]**.

   Nói: "Lượt chấm chạy nền, không chặn giao diện. Mỗi tiêu chí là một lần gọi AI riêng."

   Thời gian đo khi tập: **~1,5 phút** từ lúc bấm tới lúc có giải thích (chờ job nhận việc 25 s → chấm 5 tiêu chí
   44 s → giải thích 24 s). Trang tự cập nhật.

### 9–13' · Trong lúc chờ: xếp hạng và evidence (HR, không gọi AI)

1. Danh sách tin → **Kỹ sư DevOps** → tab **"Ứng viên"** (`/hr/jobs/<id>/edit?tab=applications`): Nguyễn Hải Sơn
   81,00 hạng 1, Lê Văn Đức 59,50, Trần Minh Hoàng 35,00; đơn của Bảo đang chấm.
2. Mở đơn **Nguyễn Hải Sơn** → **"CV & điểm"** → mở rộng một tiêu chí: điểm, lý giải, các đoạn trích dẫn. Bấm **"Xem
   CV gốc"** để đối chiếu một đoạn trích trên PDF.

   Nói: "Mỗi trích dẫn đã được backend so khớp nguyên văn với văn bản CV. Chỉ cần một trích dẫn không khớp là cả lượt
   chấm bị dừng, không có điểm."
3. Mở đơn **Trần Minh Hoàng** (CV backend Java nộp vào DevOps): 3 tiêu chí 0 điểm, hiện "Không tìm thấy trích dẫn nào
   trong CV cho tiêu chí này." kèm lý giải.

   Nói: "CV không có thông tin thì AI cho 0 điểm và nói rõ là không tìm thấy, không bịa ra bằng chứng."

### 13–15' · Kết quả chấm của Bảo và so sánh (HR, không gọi AI)

1. Quay lại đơn Bảo. Kết quả tập dượt:

   | Tiêu chí | Trọng số | Điểm /5 | Số trích dẫn |
   |---|---|---|---|
   | Kinh nghiệm vận hành hệ thống/CI-CD | 35 | 1,5 | 2 |
   | Kỹ năng Kubernetes/Docker & hạ tầng cloud | 30 | 0 | 0 |
   | Kỹ năng scripting/tự động hoá | 20 | 1,5 | 4 |
   | Kỹ năng giao tiếp & phối hợp xử lý sự cố | 10 | 1 | 1 |
   | Viết tài liệu vận hành | 5 | 0 | 0 |

   Tổng **18,50**, hạng **4/4**. Tính tay trước hội đồng: 1,5/5×35 + 1,5/5×20 + 1/5×10 = 10,5 + 6 + 2 = **18,5**.

   Nói: "AI chỉ cho từng con số trên thang 5. Tổng và hạng là phép tính của backend, ai cũng tính lại được."
2. Tab **"Giải thích"**: khối "Do AI tạo", tóm tắt điểm mạnh/thiếu; không có nhãn Đạt/Không đạt.
3. Tab "Ứng viên" của tin → tick **Sơn, Bảo, Hoàng** → **"So sánh"** (`/hr/jobs/<id>/compare`): 3 cột, điểm từng
   tiêu chí, evidence nguyên văn, badge trung tính. Bước này không gọi AI.

### 15–17' · Hỏi đáp CV (HR, đơn Nguyễn Hải Sơn, tab "Hỏi đáp CV") [AI]

| Câu hỏi | Kết quả tập dượt |
|---|---|
| `Ứng viên đã dùng công cụ nào để xây dựng pipeline CI/CD?` | Trả lời GitLab CI và ArgoCD (kỹ năng có thêm Jenkins), 2 trích dẫn, 5,2 s |
| `Cụm Kubernetes ứng viên quản trị phục vụ bao nhiêu dịch vụ?` | "hơn 40 dịch vụ nội bộ", 1 trích dẫn, 2,6 s |
| `Ứng viên từng quản lý đội nhóm bao nhiêu người?` | **"Không tìm thấy căn cứ"**, 3,2 s |

Nói: "Câu trả lời là lời của AI, nhưng mọi trích dẫn đi kèm đều qua cùng bộ kiểm nguyên văn với phần chấm điểm. CV
không ghi thì hệ thống trả lời là không tìm thấy, không đoán."

### 17–19' · HR quyết định mời phỏng vấn (HR, đơn Sơn, tab "Giấy mời phỏng vấn") (không gọi AI)

**"Thêm khung giờ"** ×3: ví dụ 3 ngày tới 09:00 (văn phòng), 4 ngày tới 14:00 (`Phỏng vấn trực tuyến qua Google
Meet`), 5 ngày tới 10:00 (văn phòng) → **"Gửi lời mời"**. Trạng thái đơn chuyển "Đã mời phỏng vấn".

Nói: "Người quyết định mời là HR. AI không đề xuất ai đậu ai rớt."

### 19–21' · Ứng viên chọn khung giờ (cửa sổ ẩn danh: đăng xuất Bảo → đăng nhập Nguyễn Hải Sơn) (không gọi AI)

Chuông thông báo → đơn Kỹ sư DevOps → chọn khung 2 → **"Xác nhận khung giờ"**.

### 21–24' · Nhắc lịch bằng AI và nhắn tin (HR ↔ Sơn) [AI]

1. HR: chuông "Ứng viên đã chọn lịch phỏng vấn" → đơn Sơn → tab **"Trao đổi"** → **"Soạn bằng AI"** → tình huống
   **Nhắc lịch phỏng vấn**, giọng **Trang trọng** → **"Tạo bản nháp"** (~6 s).

   Kết quả tập dượt: bản nháp ghi đúng khung vừa chốt ("Thời gian: 14:00, ngày 24/10/2026 · Hình thức: Phỏng vấn trực
   tuyến qua Google Meet").
2. **"Dùng bản nháp"** → sửa nếu muốn → **"Gửi"**. Cửa sổ ẩn danh (Sơn) tự tải lại hoặc bấm lại tab "Trao đổi" để
   thấy tin.

   Nói: "Tình huống 'Nhắc lịch' chỉ mở khi ứng viên đã chốt khung. AI chỉ soạn nháp; người gửi vẫn là HR."

### 24–26' · AI tạo bản nháp tin tuyển dụng (HR) [AI] — CẮT ĐƯỢC

`/hr/jobs/new` → **"Tạo tin bằng AI"** → dán mô tả:

> Cần tuyển Lập trình viên Mobile (Flutter) tại TP. Hồ Chí Minh, làm việc kết hợp, lương 20–30 triệu. Yêu cầu ít nhất
> 2 năm kinh nghiệm Flutter/Dart, đã phát hành ứng dụng lên App Store hoặc Google Play, biết REST API và Git. Hạn nộp
> 30/11/2026.

→ **"Tạo bản nháp"** (~6 s, 3 phần chạy song song) → **"Điền vào biểu mẫu"**. Kết quả tập dượt: tiêu đề "Lập trình
viên Mobile (Flutter)", 4 tiêu chí gợi ý.

Chỉ cho hội đồng: **ô trọng số của tiêu chí để trống**, HR tự nhập. **Không bấm "Lưu nháp"**.

### 26–27' · Ẩn danh và tổng quan (HR) (không gọi AI) — CẮT ĐƯỢC

1. Tin **Senior Java Backend Developer** → tab "Ứng viên": tin đã bật chấm ẩn danh từ trước. Đơn chưa mời hiện
   "Ứng viên #…", "Xem CV gốc" bị khoá; đơn của Võ Thanh Tùng đã được mời nên hiện tên thật.
2. Muốn khoe hộp thoại thì mở công tắc ẩn danh của một tin khác rồi bấm **Huỷ**. **Không xác nhận**, để dữ liệu không
   đổi.
3. Trang tổng quan `/hr`: phễu trạng thái.

### 27–30' · Hỏi đáp và dự phòng

Phần còn thời gian dùng cho câu hỏi (mục 5). Nếu dư giờ:
- câu C của "Tìm bằng mô tả": `tìm việc ở Đà Nẵng` → phần còn lại "tìm việc" là từ đệm, nên kết quả xếp theo tin
  mới nhất (không embedding);
- CV builder của Bùi Ngọc Mai: "Sửa CV" → ở một mục kinh nghiệm bấm **"Gợi ý diễn đạt"** → **"Dùng gợi ý"**. Không
  lưu.

## 5. Dự phòng

| Sự cố | Làm gì |
|---|---|
| AI/mạng lỗi ở bước chấm của Bảo | Chuyển sang đơn đã có điểm: Sơn 81,00 / Đức 59,50 / Hoàng 35,00 (Kỹ sư DevOps), Hoàng 67,50 (Java). Phần so sánh dùng Sơn / Đức / Hoàng |
| AI lỗi ở hỏi đáp CV, soạn nháp, tạo tin, tìm bằng mô tả | Giao diện hiện thông báo và nút **"Thử lại"**. Thử lại một lần; lỗi tiếp thì nói "đây là đường lỗi đã thiết kế: có thời gian chờ tối đa, lỗi trả mã chuẩn hoá, không lưu kết quả hỏng", rồi đi tiếp. Tìm bằng mô tả lỗi thì dùng bộ lọc thường |
| Bước giấy mời/chọn giờ trục trặc | Đăng nhập `do.khanh.linh@demo.local`: đơn Chuyên viên Marketing có sẵn giấy mời 3 khung giờ chưa chọn |
| Chấm lâu hơn dự kiến | Tiếp tục mục 9–13' (xếp hạng, evidence, ca 0 điểm). Nói về luồng: job nền nhận việc bằng `UPDATE` có điều kiện, gọi AI ngoài transaction, mỗi tiêu chí một lượt gọi, xong tiêu chí cuối mới tổng hợp. Trang tự cập nhật; sau 10 phút thì có nút "Tải lại" |
| HTTP 429 — "Bạn đã gửi quá nhiều yêu cầu, vui lòng thử lại sau ít phút" | Hạn mức AI đồng bộ theo người dùng: 5 lượt, hồi 2 lượt/phút, dùng chung cho tìm bằng mô tả, hỏi đáp CV, soạn nháp, tạo tin, gợi ý diễn đạt. Kịch bản dùng đúng 5 lượt phía HR (hỏi đáp 3, soạn nháp 1, tạo tin 1). Chờ 30–60 s rồi bấm lại; tránh bấm "Thử lại" liên tục |
| Lượt chấm FAILED, lỗi "Minh chứng AI trích dẫn không khớp với nội dung gốc của CV" | Nói: "Đây là chốt chặn chạy đúng thiết kế: AI trích một đoạn không có nguyên văn trong CV nên cả lượt bị huỷ, không có điểm nào được tính." Bấm "Chấm điểm hồ sơ" để tạo lượt mới, hoặc chuyển sang đơn đã có điểm |

## 6. Số đo lượt tập dượt (20/10/2026, gọi qua API, dữ liệu demo vừa nạp)

| Bước | Thời gian | Kết quả |
|---|---|---|
| Tìm bằng mô tả, câu A (chạy 2 lần) | 3,2 s | Cả 2 lần: Kỹ sư DevOps #1, Phân tích dữ liệu #2; lương 25 triệu chỉ một mốc |
| Tìm bằng mô tả, câu B | ~2–4 s | Frontend (React) #1, UI/UX #2, Kỹ sư DevOps #3 |
| Tìm bằng mô tả, câu C | 2,2 s | Đà Nẵng, không mô tả → xếp mới nhất, 2 việc |
| Nộp đơn | 0,2 s | Đơn PENDING, 2 câu trả lời sàng lọc |
| Chấm điểm (bấm → có giải thích) | ~93 s | Tổng 18,50, hạng 4/4, 5 tiêu chí, 0 lần thử lại do lỗi tạm thời |
| Hỏi đáp CV ×3 | 5,2 / 2,6 / 3,2 s | Trả lời / Trả lời / Không tìm thấy căn cứ |
| Gửi giấy mời 3 khung · chọn khung | < 0,1 s mỗi bước | Đơn Sơn chuyển "Đã mời phỏng vấn", khung 2 đã chốt |
| Soạn nháp "Nhắc lịch phỏng vấn" | 6,1 s | Nháp ghi đúng khung đã chốt |
| Tạo tin bằng AI | 6,0 s | 3/3 phần thành công, tiêu chí không có trọng số |
| Gợi ý diễn đạt | 2,6 s | Một câu diễn đạt lại đoạn đã gõ |

Lượt gọi thật: **Anthropic 18** (tìm việc 4, chấm 5 + giải thích 1, hỏi đáp 3, soạn nháp 1, tạo tin 3, gợi ý diễn đạt 1).
Các lần gọi đồng bộ không có lần thử lại nào (log DEBUG của `SyncAiCaller` trống). Lần thử lại khi JSON hỏng ở bước
chấm chỉ ghi ở DEBUG của `CriterionScoringService`, logger này không bật nên 18 là số tối thiểu. **OpenAI 3**
(embedding phần mô tả của câu A ×2 và câu B; job nền 0). MailHog nhận **4 thư**: 3 thư cho
HR (đơn mới, chấm xong, ứng viên chọn lịch), 1 thư cho Sơn (giấy mời). Log backend 0 dòng ERROR.

Một lượt demo thật (câu A chạy một lần) tốn khoảng **17 lượt Anthropic và 2–3 lượt OpenAI**.

## 7. Câu hỏi hội đồng hay hỏi

**Vì sao AI không tính tổng điểm và không xếp hạng?**
Tổng điểm là căn cứ ra quyết định, nên phải tái lập được và giải thích được. Backend tính
Σ(điểm/điểm tối đa × trọng số) / Σ trọng số × 100 bằng một hàm thuần, không gọi AI
([ScoreAggregator.java](../backend/src/main/java/com/recruitment/scoring/ScoreAggregator.java)). Package `ai/` bị cấm
import lớp này ([CLAUDE.md](../CLAUDE.md) §7). Trọng số do HR đặt; mỗi lượt chấm chụp lại rubric (`rubric_snapshot`)
nên điểm cũ không đổi khi rubric đổi.

**Evidence được kiểm nguyên văn thế nào?**
AI phải trả trích dẫn kèm mỗi điểm. Backend chuẩn hoá khoảng trắng rồi kiểm trích dẫn có nằm nguyên văn trong văn bản
CV hay không ([VerbatimQuoteVerifier.java](../backend/src/main/java/com/recruitment/ai/citation/VerbatimQuoteVerifier.java)).
Một trích dẫn không khớp thì cả lượt chấm thất bại với mã `EVIDENCE_NOT_VERIFIED`. Điểm khác 0 bắt buộc phải có
trích dẫn; điểm 0 được phép không có, kèm lý giải. Hỏi đáp CV dùng chung bộ kiểm này.

**Dữ liệu cá nhân và sự đồng ý?**
Ứng viên phải tick đồng ý mới nộp được đơn. Ràng buộc này được kiểm ở giao diện, ở API (`@AssertTrue` trong
`ApplicationCreateRequest`) và ở DB (`chk_consent_true`,
[V1__init_schema.sql](../backend/src/main/resources/db/migration/V1__init_schema.sql)). Cột lỗi chỉ lưu mã lỗi chuẩn
hoá; nội dung CV và output thô của AI không ghi vào DB hay log mức thường ([CLAUDE.md](../CLAUDE.md) §4). Ứng viên
không bao giờ thấy điểm, rubric hay giải thích của AI: phần này chặn bằng kiểu dữ liệu, không bằng lời dặn trong
prompt ([CLAUDE.md](../CLAUDE.md) §3d, K1). Rút đơn chỉ đổi trạng thái, không xoá.

**Ẩn danh hoạt động thế nào, giới hạn ở đâu?**
Khi tin bật ẩn danh, mọi màn hình của HR (danh sách, hồ sơ, so sánh, tin nhắn, thông báo) hiện "Ứng viên #…" thay cho
tên, khoá tải CV gốc, không hiện thư giới thiệu. Đầu vào chấm điểm không đổi. Ẩn danh tự hết khi HR mời phỏng vấn.
Giới hạn: hệ thống chỉ che đúng các giá trị danh tính đã biết (họ tên, email, SĐT…); biến thể viết khác, hoặc danh
tính suy ra được từ nội dung CV, thì không bị che ([H16 REQUIREMENT](features/HR/H16/REQUIREMENT.md),
[ROADMAP](ROADMAP.md) `feat/fr-h16-blind`).

**Chi phí, số lượt gọi AI?**
Lượt tập dượt trọn kịch bản: khoảng 18 lượt Anthropic, 3 lượt OpenAI (mục 6). Chấm một đơn 5 tiêu chí tốn 6 lượt (5 tiêu chí
+ 1 giải thích), khoảng 15 000 token. Dữ liệu demo có sẵn điểm và embedding do AI thật sinh một lần rồi lưu lại, nên
nạp dữ liệu không tốn lượt gọi nào ([db/seed/README.md](../db/seed/README.md) mục 3).

**Vì sao chấm điểm chạy job nền thay vì gọi ngay trong request?**
Một lượt chấm cần nhiều lần gọi AI, mất 1–2 phút. Giữ request và connection DB suốt thời gian đó sẽ cạn pool khi nhiều
đơn cùng chờ. Job nền nhận bản ghi bằng `UPDATE` có điều kiện, gọi AI ngoài transaction, thử lại khi lỗi tạm thời
([CLAUDE.md](../CLAUDE.md) §3c). Chỉ 5 chức năng cần phản hồi tức thì (tìm bằng mô tả, hỏi đáp CV, soạn nháp, tạo tin,
gợi ý diễn đạt) mới gọi đồng bộ, kèm thời gian chờ tối đa và hạn mức theo người dùng ([CLAUDE.md](../CLAUDE.md) §7).

**Kiểm thử thế nào?**
Backend có 2088 test xanh ở lần chạy full suite gần nhất (20/10/2026,
[fix-u13-salary-parse](walkthrough/fix-u13-salary-parse.md) mục 6). Test dùng JUnit 5 + Testcontainers với PostgreSQL
thật, không dùng H2. Test không gọi API AI thật: `ChatModel`/`EmbeddingModel` được mock, mock mặc định ném lỗi để test
nào quên khai báo thì đỏ ngay. Frontend được kiểm bằng `npm run build` + `npm run lint`; mỗi FR có bảng soát tay trong
REQUIREMENT.

**Giới hạn đã biết?**
- Schema JSON trích xuất CV đánh dấu mọi field là bắt buộc, có thể đẩy AI điền giá trị cho mục CV không có
  ([ROADMAP](ROADMAP.md), Phase D, mục D1).
- Ngưỡng tương đồng của gợi ý việc làm (0,40) loại được rất ít; giá trị nằm ở thứ tự xếp hạng
  ([SRS](SRS.md), FR-U04; [chore-seed-final](walkthrough/chore-seed-final.md) L43).
- Một trích dẫn không khớp làm hỏng cả lượt chấm; HR phải chấm lại.
- Danh sách đầy đủ: [README](../README.md) mục "Giới hạn đã biết".
