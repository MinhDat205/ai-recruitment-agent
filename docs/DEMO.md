# Kịch bản demo trước hội đồng

Buổi demo khoảng **30 phút**, **gọi AI thật** (Anthropic cho LLM, OpenAI cho embedding), chạy trên bộ dữ liệu demo
(`db/seed/`, 2 công ty, 12 tin đang mở, 9 ứng viên). Thời lượng và kết quả trong tài liệu này lấy từ lượt tập dượt
qua API ngày 21/10/2026 (mục 6). Kết quả của AI có thể khác đôi chút giữa các lần chạy; phần cần nói không phụ thuộc
vào con số cụ thể.

Chỉ **hai tài khoản** đăng nhập: **cửa sổ thường = HR** (`hr@demo.local`), **cửa sổ ẩn danh = ứng viên Phạm Quốc
Bảo** (`pham.quoc.bao@demo.local`). Không đăng xuất lần nào trong buổi demo.

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

- [ ] Tài khoản Anthropic/OpenAI còn hạn mức. Một lượt chạy trọn kịch bản tốn khoảng 15 lượt gọi Anthropic và 2 lượt
  OpenAI (mục 6).
- [ ] `cd frontend; npm install` đã chạy.
- [ ] Tập một lượt theo mục 4. Tập xong **phải nạp lại dữ liệu**, vì lượt tập làm đổi trạng thái (Phạm Quốc Bảo đã
  nộp lại tin QA, đã được mời phỏng vấn, có tin nhắn).

### Sáng ngày demo

- [ ] Dừng backend nếu đang chạy, rồi **nạp lại toàn chuỗi mục 1** của [db/seed/README.md](../db/seed/README.md)
  (reset → tầng 1 → 2 → 1b → 2b → 3 → `install-demo-files.ps1`). Nạp sáng ngày demo để hạn nộp và ngày đăng tin (tính
  theo lúc nạp) còn ở tương lai/gần đây — tin "đã hết hạn nộp" của tầng 3 có hạn là **hôm qua** tính theo lúc nạp.
- [ ] **KHÔNG** nạp bộ test (`seed-test.sql`, mục 8 của README seed): tin của công ty test sẽ hiện trên bảng việc làm
  và trong kết quả tìm việc.
- [ ] Khởi động backend bằng **lệnh demo** (mục 2), chờ dòng `Started BackendApplication`. Khởi động frontend.
- [ ] Mở sẵn MailHog http://localhost:8025 ở một tab.
- [ ] Cửa sổ thường: đăng nhập `hr@demo.local`. Cửa sổ ẩn danh: đăng nhập `pham.quoc.bao@demo.local`. Giữ nguyên hai
  phiên này đến hết buổi, không đăng xuất.
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

Trên dữ liệu demo vừa nạp, job nền chỉ tự gọi AI **một lần**: 12 tin đang mở, 4 hồ sơ và 8 CV chính đã có embedding
sẵn (đo khi tập 21/10/2026: số embedding trước và sau lượt tập giống nhau); riêng tin đã hết hạn nộp của tầng 3 (FR-U16)
vẫn ở trạng thái `OPEN` và chưa có embedding, nên `JobEmbeddingScheduler` gọi OpenAI một lần cho tin này lúc khởi động.

## 3. Tài khoản dùng trong demo

Mật khẩu chung `Demo1234` (công khai có chủ đích, chỉ cho máy demo cục bộ).

| Tài khoản | Cửa sổ | Vai trò trong kịch bản |
|---|---|---|
| `hr@demo.local` | Thường | HR Công ty Cổ phần Công nghệ Hoàn Mỹ: chấm điểm, hỏi đáp CV, mời phỏng vấn, soạn nháp, tạo tin bằng AI |
| `pham.quoc.bao@demo.local` | Ẩn danh | Ứng viên QA: tìm việc bằng mô tả, lưu tin, nộp lại tin QA ở chu kỳ 2, chọn khung giờ, đọc tin nhắn, gợi ý diễn đạt CV |

Các ứng viên khác (Nguyễn Hải Sơn, Lê Văn Đức, Trần Minh Hoàng, Võ Thanh Tùng, Đỗ Khánh Linh…) chỉ là dữ liệu cho xếp
hạng, so sánh và ẩn danh; không đăng nhập trong buổi demo.

Dữ liệu dựng sẵn cho mạch này: tin **Kỹ sư Kiểm thử phần mềm (QA Engineer)** đã được HR mở lại sang **chu kỳ 2** và có
2 câu hỏi sàng lọc. Đơn đợt 1 của Bảo (bị từ chối, 83,50) và đơn đã rút của Lê Văn Đức nằm ở chu kỳ 1
([db/seed/README.md](../db/seed/README.md) mục 6b). Tầng 3 (FR-U16) cho Bảo sẵn **4 tin đã lưu**: 3 tin đang mở của
Công ty TNHH Dịch vụ Số Ngân Hà và tin "Nhân viên Hỗ trợ kỹ thuật (IT Helpdesk)" đã hết hạn nộp.

## 4. Kịch bản theo phút

Ký hiệu: **[AI]** = bước gọi AI thật; **CẮT ĐƯỢC** = bỏ khi lố giờ.

### 0–3' · Giới thiệu và nguyên tắc AI (không gọi AI)

Nói:
- "Hệ thống có hai vai trò, HR và ứng viên, 38 chức năng. Chúng em đặt bốn nguyên tắc cho AI, và kịch bản hôm nay
  sẽ cho thấy từng nguyên tắc trên màn hình."
- (1) AI chỉ chấm **từng tiêu chí** kèm trích dẫn nguyên văn từ CV; (2) **backend** tính tổng điểm và thứ hạng bằng
  công thức trọng số; (3) AI không gán nhãn Đạt/Không đạt, **HR quyết định**; (4) ứng viên phải **đồng ý** thì CV
  mới được AI phân tích.
- "Hôm nay chỉ có hai người dùng: một HR và một ứng viên. Chúng em đi trọn một vòng tuyển dụng của ứng viên này."

### 3–7' · Ứng viên tìm việc và nộp lại đơn (cửa sổ ẩn danh, Phạm Quốc Bảo)

1. Trang việc làm của ứng viên `/candidate` → chọn chế độ **"Tìm bằng mô tả"** → gõ:

   > việc kiểm thử phần mềm ngành công nghệ thông tin ở TP. Hồ Chí Minh, viết kiểm thử tự động bằng Selenium và Playwright

   **[AI]** ~6 s. Kết quả tập dượt: điều kiện "TP. Hồ Chí Minh · Công nghệ thông tin - Phần mềm", phần mô tả "việc
   kiểm thử phần mềm, viết kiểm thử tự động bằng Selenium và Playwright", 3 việc: **#1 Kỹ sư Kiểm thử phần mềm (QA
   Engineer)**, #2 Chuyên viên Phân tích dữ liệu, #3 Senior Java Backend Developer.

   Nói: "AI chỉ chỉ ra đoạn chữ nào là khu vực, ngành, lương, hình thức. Backend kiểm từng đoạn, rồi lọc bằng SQL. Phần
   còn lại của câu dùng để xếp theo độ gần nghĩa."
2. **CẮT ĐƯỢC** — câu A: `việc làm từ xa ngành công nghệ thông tin lương trên 25 triệu, làm CI/CD và tự động hoá kiểm
   thử` **[AI]** ~3 s → "Công nghệ thông tin - Phần mềm · Từ xa · Lương từ 25 triệu", 2 việc: #1 Kỹ sư DevOps, #2
   Chuyên viên Phân tích dữ liệu. Nói: "Câu này chỉ có **một mốc lương**; đây là lỗi chúng em đã sửa
   ([fix-u13-salary-parse](walkthrough/fix-u13-salary-parse.md))."
   - **CẮT ĐƯỢC** — lưu tin (FR-U16, không gọi AI): ở kết quả bước 1, bấm biểu tượng **Lưu** trên thẻ **Kỹ sư Kiểm thử
     phần mềm (QA Engineer)** (thẻ không mở trang tin) → menu **"Tin đã lưu"**: "Đang lưu 5/100 tin", tin QA ở trên
     cùng; tin **IT Helpdesk** hiện nhãn "Đã hết hạn nộp", nút Ứng tuyển khoá kèm câu lý do, không có liên kết sang tin.
     Nói: "Tin không còn mở vẫn nằm trong danh sách để ứng viên biết, nhưng không ứng tuyển được; tình trạng tin do
     backend tính, cùng một chỗ với trang chi tiết đơn. HR không biết ai đã lưu tin nào." Bước 4 có thể bấm **Ứng
     tuyển** ngay ở thẻ QA trong trang này.
3. Mục **Đơn ứng tuyển** của Bảo: đơn đợt 1 vào tin QA ở trạng thái **"Bị từ chối"**.

   Nói: "Đợt 1 ứng viên bị từ chối. HR mở lại tin sang chu kỳ 2, nên ứng viên được nộp lại; ràng buộc 'một đơn mỗi
   chu kỳ' nằm ở DB. Danh sách của HR giữ lịch sử cả hai đợt."
4. Mở **Kỹ sư Kiểm thử phần mềm (QA Engineer)** → ứng tuyển (`/jobs/:id/apply`): chọn CV chính; chỉ cho hội đồng nút
   **"Nộp đơn"** chưa bấm được khi chưa tick ô "Tôi đồng ý cho phép CV của tôi được hệ thống AI phân tích và chấm
   điểm…" → tick.
5. Trả lời 2 câu sàng lọc: "Bạn có thể làm việc tại văn phòng ở TP. Hồ Chí Minh không?" → **Có**; "Bạn đã dùng công cụ
   kiểm thử tự động nào trong dự án thực tế?" → `Selenium WebDriver, Playwright và REST Assured trong dự án thương mại
   điện tử.` → **"Nộp đơn"**.

   Nói: "Đồng ý cho AI phân tích là bắt buộc ở cả giao diện, API (`@AssertTrue`) và ràng buộc DB."

### 7–9' · HR thấy đơn mới và bấm chấm (cửa sổ thường)

1. Chuông thông báo → đơn mới của Phạm Quốc Bảo (thông báo đến trong vài giây; email về MailHog sau khoảng 25 s).
2. Mở đơn `/hr/applications/:id` → tab **"Sàng lọc"**: hai câu trả lời vừa nhập. Nói: "Câu trả lời sàng lọc chỉ để HR
   đọc, không đưa vào chấm điểm."
3. Tab **"CV & điểm"** → **"Chấm điểm hồ sơ"** **[AI, job nền]**.

   Nói: "Lượt chấm chạy nền, không chặn giao diện. Mỗi tiêu chí là một lần gọi AI riêng."

   Thời gian đo khi tập 21/10: **~70 s** từ lúc bấm tới lúc có giải thích (job nhận việc ~4 s → chấm 4 tiêu chí ~44 s →
   giải thích ~21 s). Sau `perf/scoring-latency` (đo 30/10) vẫn **~60–70 s** một lượt: job nhận việc < 1 s, gần như toàn
   bộ thời gian là AI chấm từng tiêu chí và viết giải thích (mục 6). Trang tự cập nhật.

### 9–13' · Trong lúc chờ: hỏi đáp CV, chu kỳ tuyển dụng, xếp hạng (HR)

1. Cùng đơn của Bảo → tab **"Hỏi đáp CV"** **[AI]** (hỏi đáp chỉ cần văn bản CV, không phải chờ chấm xong):

   | Câu hỏi | Kết quả tập dượt |
   |---|---|
   | `Ứng viên dùng công cụ kiểm thử tự động nào?` | Selenium WebDriver, Playwright, REST Assured, JUnit 5, TestNG; kèm Postman, JMeter, Jenkins. 5 trích dẫn, 7,8 s |
   | `Bộ kiểm thử tự động phủ bao nhiêu kịch bản?` | "hơn 200 kịch bản nghiệp vụ chính", 1 trích dẫn, 3,4 s |
   | `Ứng viên từng quản lý đội nhóm bao nhiêu người?` | **"Không tìm thấy căn cứ"**, 1,3 s |

   Nói: "Câu trả lời là lời của AI, nhưng mọi trích dẫn đi kèm đều qua cùng bộ kiểm nguyên văn với phần chấm điểm. CV
   không ghi thì hệ thống trả lời là không tìm thấy, không đoán."
2. Danh sách tin → **Kỹ sư Kiểm thử phần mềm (QA Engineer)** → tab **"Ứng viên"**: hai dòng Phạm Quốc Bảo (chu kỳ 1
   bị từ chối 83,50; chu kỳ 2 đang chấm) và Lê Văn Đức (chu kỳ 1, đã rút).

   Nói: "Đây là điểm khoe chu kỳ tuyển dụng: đơn cũ không bị xoá hay ghi đè, HR thấy cả lịch sử."
3. **CẮT ĐƯỢC** — Tin **Kỹ sư DevOps** → tab "Ứng viên": Nguyễn Hải Sơn 81,00 hạng 1, Lê Văn Đức 59,50, Trần Minh
   Hoàng 35,00. Mở đơn **Trần Minh Hoàng** (CV backend Java nộp vào DevOps): 3 tiêu chí 0 điểm, hiện "Không tìm thấy
   trích dẫn nào trong CV cho tiêu chí này." kèm lý giải.

   Nói: "CV không có thông tin thì AI cho 0 điểm và nói rõ là không tìm thấy, không bịa ra bằng chứng."

### 13–16' · Kết quả chấm của Bảo và so sánh (HR, không gọi AI)

1. Quay lại đơn Bảo → **"CV & điểm"** → mở rộng một tiêu chí: điểm, lý giải, các đoạn trích dẫn. Bấm **"Xem CV gốc"**
   để đối chiếu một đoạn trích trên PDF. Kết quả tập dượt:

   | Tiêu chí | Trọng số | Điểm /5 | Số trích dẫn |
   |---|---|---|---|
   | Kinh nghiệm kiểm thử phần mềm | 35 | 4 | 7 |
   | Kỹ năng kiểm thử tự động & viết test case | 30 | 5 | 6 |
   | Hiểu biết quy trình QA/CI | 20 | 4,5 | 4 |
   | Kỹ năng mềm & giao tiếp | 15 | 2 | 1 |

   Tổng **82,00**, hạng **2/3** của tin (hạng tính trên mọi chu kỳ; đơn đợt 1 83,50 đứng hạng 1). Tính tay trước hội
   đồng: 4/5×35 + 5/5×30 + 4,5/5×20 + 2/5×15 = 28 + 30 + 18 + 6 = **82**.

   Nói: "AI chỉ cho từng con số trên thang 5. Tổng và hạng là phép tính của backend, ai cũng tính lại được. Mỗi trích
   dẫn đã được backend so khớp nguyên văn với văn bản CV; chỉ cần một trích dẫn không khớp là cả lượt chấm bị dừng."
2. Tab **"Giải thích"**: khối "Do AI tạo", tóm tắt điểm mạnh/thiếu; không có nhãn Đạt/Không đạt. Không đọc to phần
   tóm tắt; chỉ chỉ khối "Do AI tạo" và việc không có nhãn Đạt/Không đạt.
3. Tin **Kỹ sư DevOps** → tab "Ứng viên" → tick **Sơn, Đức, Hoàng** → **"So sánh"** (`/hr/jobs/<id>/compare`): 3 cột,
   điểm từng tiêu chí, evidence nguyên văn, badge trung tính. Bước này không gọi AI.
4. **CẮT ĐƯỢC** — Ở tab "Ứng viên" của tin QA, tick đơn chu kỳ 2 của Bảo: ô của đơn chu kỳ 1 bị khoá, lý do "Khác chu
   kỳ tuyển dụng với đơn đã chọn." Hai đơn khác chu kỳ không so sánh được với nhau.

### 16–18' · HR quyết định mời phỏng vấn (HR, đơn Bảo, tab "Giấy mời phỏng vấn") (không gọi AI)

**"Thêm khung giờ"** ×3: ví dụ 3 ngày tới 09:00 (văn phòng), 4 ngày tới 14:00 (`Phỏng vấn trực tuyến qua Google
Meet`), 5 ngày tới 10:00 (văn phòng) → **"Gửi lời mời"**. Trạng thái đơn chuyển "Đã mời phỏng vấn".

Nói: "Người quyết định mời là HR. AI không đề xuất ai đậu ai rớt."

### 18–20' · Ứng viên chọn khung giờ (cửa sổ ẩn danh, Bảo) (không gọi AI)

Chuông thông báo → đơn Kỹ sư Kiểm thử phần mềm (QA Engineer) → chọn khung 2 → **"Xác nhận khung giờ"**.

### 20–23' · Nhắc lịch bằng AI và nhắn tin (HR ↔ Bảo) [AI]

1. HR: chuông "Ứng viên đã chọn lịch phỏng vấn" → đơn Bảo → tab **"Trao đổi"** → **"Soạn bằng AI"** → tình huống
   **Nhắc lịch phỏng vấn**, giọng **Trang trọng** → **"Tạo bản nháp"** (~7 s).

   Kết quả tập dượt: bản nháp ghi đúng khung vừa chốt ("Thời gian: 14:00, ngày 25/10/2026 · Hình thức: Phỏng vấn trực
   tuyến qua Google Meet").
2. **"Dùng bản nháp"** → sửa nếu muốn → **"Gửi"**. Cửa sổ ẩn danh (Bảo) tự tải lại hoặc bấm lại tab "Trao đổi" để
   thấy tin.

   Nói: "Tình huống 'Nhắc lịch' chỉ mở khi ứng viên đã chốt khung. AI chỉ soạn nháp; người gửi vẫn là HR."
3. **CẮT ĐƯỢC** — Bảo trả lời một câu ngắn trong tab "Trao đổi"; HR thấy chuông tin nhắn mới.

### 23–25' · AI tạo bản nháp tin tuyển dụng (HR) [AI] — CẮT ĐƯỢC

`/hr/jobs/new` → **"Tạo tin bằng AI"** → dán mô tả:

> Cần tuyển Lập trình viên Mobile (Flutter) tại TP. Hồ Chí Minh, làm việc kết hợp, lương 20–30 triệu. Yêu cầu ít nhất
> 2 năm kinh nghiệm Flutter/Dart, đã phát hành ứng dụng lên App Store hoặc Google Play, biết REST API và Git. Hạn nộp
> 30/11/2026.

→ **"Tạo bản nháp"** (~6 s, 3 phần chạy song song) → **"Điền vào biểu mẫu"**. Kết quả tập dượt: tiêu đề "Lập trình
viên Mobile (Flutter)", tiêu chí gợi ý chỉ có tên và mô tả, câu hỏi sàng lọc gợi ý.

Chỉ cho hội đồng: **ô trọng số của tiêu chí để trống**, HR tự nhập. **Không bấm "Lưu nháp"**.

### 25–26' · Ẩn danh và tổng quan (HR) (không gọi AI) — CẮT ĐƯỢC

1. Tin **Senior Java Backend Developer** → tab "Ứng viên": tin đã bật chấm ẩn danh từ trước. Đơn chưa mời hiện
   "Ứng viên #…", "Xem CV gốc" bị khoá; đơn của Võ Thanh Tùng đã được mời nên hiện tên thật.
2. Muốn khoe hộp thoại thì mở công tắc ẩn danh của một tin khác rồi bấm **Huỷ**. **Không xác nhận**, để dữ liệu không
   đổi.
3. Trang tổng quan `/hr`: phễu trạng thái.

### 26–27' · CV builder và gợi ý diễn đạt (cửa sổ ẩn danh, Bảo) [AI] — CẮT ĐƯỢC

Trang hồ sơ → **"Tạo CV"** (`/candidate/resumes/new`) → khối "Điền sẵn từ CV đã trích xuất": chọn CV chính → **"Điền sẵn"** (không gọi AI) → ở mục
kinh nghiệm đầu tiên bấm **"Gợi ý diễn đạt"** (~2 s) → **"Dùng gợi ý"**. **Không lưu.**

Nói: "Gợi ý chỉ áp lên đoạn ứng viên đang gõ, chỉ có hiệu lực khi ứng viên bấm dùng. CV lưu ra là file PDF, và văn bản
dùng để chấm lấy từ chính file đó."

### 27–30' · Hỏi đáp

Phần còn thời gian dùng cho câu hỏi (mục 7). Nếu dư giờ: câu C của "Tìm bằng mô tả" `tìm việc ở Đà Nẵng` → phần còn
lại "tìm việc" là từ đệm, nên kết quả xếp theo tin mới nhất (không embedding).

## 5. Dự phòng

| Sự cố | Làm gì |
|---|---|
| AI/mạng lỗi ở bước chấm của Bảo | Mở **đơn đợt 1 của Bảo** trên cùng tin QA (83,50, có điểm, evidence, giải thích). So sánh và xếp hạng dùng tin DevOps: Sơn 81,00 / Đức 59,50 / Hoàng 35,00 |
| Chấm lâu hơn dự kiến | Tiếp tục mục 9–13' (hỏi đáp CV, tab Ứng viên của QA, DevOps, ca 0 điểm). Nói về luồng: job nền nhận việc bằng `UPDATE` có điều kiện, gọi AI ngoài transaction, mỗi tiêu chí một lượt gọi, xong tiêu chí cuối mới tổng hợp. Trang tự cập nhật; sau 10 phút thì có nút "Tải lại" |
| Lượt chấm FAILED, lỗi "Minh chứng AI trích dẫn không khớp với nội dung gốc của CV" | Nói: "Đây là chốt chặn chạy đúng thiết kế: AI trích một đoạn không có nguyên văn trong CV nên cả lượt bị huỷ, không có điểm nào được tính." Bấm "Chấm điểm hồ sơ" để tạo lượt mới, hoặc chuyển sang đơn đợt 1 của Bảo |
| Bước giấy mời/chọn giờ trục trặc | Từ phía HR mở đơn **Chuyên viên Marketing** của Đỗ Khánh Linh, tab "Giấy mời phỏng vấn": giấy mời 3 khung giờ chưa chọn. Không đăng nhập tài khoản Linh |
| "Nhắc lịch phỏng vấn" chưa mở được (đơn Bảo chưa chốt khung) | Từ phía HR mở đơn **Nhân viên kinh doanh qua điện thoại** của Võ Thanh Tùng (đã chốt khung) → tab "Trao đổi" → "Soạn bằng AI" → Nhắc lịch phỏng vấn |
| AI lỗi ở hỏi đáp CV, soạn nháp, tạo tin, tìm bằng mô tả, gợi ý diễn đạt | Giao diện hiện thông báo và nút **"Thử lại"**. Thử lại một lần; lỗi tiếp thì nói "đây là đường lỗi đã thiết kế: có thời gian chờ tối đa, lỗi trả mã chuẩn hoá, không lưu kết quả hỏng", rồi đi tiếp. Tìm bằng mô tả lỗi thì dùng bộ lọc thường (TP. Hồ Chí Minh + Công nghệ thông tin - Phần mềm) |
| HTTP 429 — "Bạn đã gửi quá nhiều yêu cầu, vui lòng thử lại sau ít phút" | Hạn mức AI đồng bộ theo người dùng: 5 lượt, hồi 2 lượt/phút, dùng chung cho tìm bằng mô tả, hỏi đáp CV, soạn nháp, tạo tin, gợi ý diễn đạt. Kịch bản dùng đúng 5 lượt phía HR (hỏi đáp 3, soạn nháp 1, tạo tin 1) và 2–3 lượt phía Bảo. Chờ 30–60 s rồi bấm lại; tránh bấm "Thử lại" liên tục |

## 6. Số đo lượt tập dượt (21/10/2026, gọi qua API, dữ liệu demo vừa nạp)

| Bước | Thời gian | Kết quả |
|---|---|---|
| Tìm bằng mô tả, câu QA | 6,2 s | TP. Hồ Chí Minh + Công nghệ thông tin; 3 việc, QA Engineer #1 |
| Tìm bằng mô tả, câu A | 3,2 s | Công nghệ thông tin + Từ xa + từ 25 triệu (một mốc); DevOps #1, Phân tích dữ liệu #2 |
| Nộp lại tin QA (chu kỳ 2) | 0,1 s | Đơn PENDING chu kỳ 2, 2 câu trả lời sàng lọc |
| Chấm điểm (bấm → có giải thích) | 69 s | DONE sau 48 s; tổng 82,00, hạng 2/3; 4 tiêu chí; 0 lần thử lại do lỗi tạm thời |
| Hỏi đáp CV ×3 (trong lúc chấm) | 7,8 / 3,4 / 1,3 s | Trả lời (5 trích dẫn) / Trả lời (1) / Không tìm thấy căn cứ |
| Gửi giấy mời 3 khung · chọn khung 2 | < 0,1 s mỗi bước | Đơn Bảo chuyển "Đã mời phỏng vấn", khung 2 đã chốt |
| Soạn nháp "Nhắc lịch phỏng vấn" | 6,6 s | Nháp ghi đúng khung đã chốt (14:00, 25/10/2026, Google Meet) |
| Gửi tin nhắn · ứng viên đọc | < 0,1 s | Bảo thấy 1 tin |
| Tạo tin bằng AI | 5,9 s | 3/3 phần thành công, tiêu chí không có trọng số |
| Gợi ý diễn đạt | 1,9 s | Một câu diễn đạt lại đoạn đã gõ |

Lượt gọi thật: **Anthropic 15** (tìm việc 2, chấm 4 + giải thích 1, hỏi đáp 3, soạn nháp 1, tạo tin 3, gợi ý diễn đạt 1).
Các lần gọi đồng bộ không có lần thử lại nào (log DEBUG của `SyncAiCaller` trống). Lần thử lại khi JSON hỏng ở bước
chấm chỉ ghi ở DEBUG của `CriterionScoringService`, logger này không bật nên 15 là số tối thiểu. **OpenAI 2**
(embedding phần mô tả của câu QA và câu A; job nền 0). MailHog nhận **5 thư**: 3 thư cho HR (đơn mới, chấm xong, ứng
viên chọn lịch), 2 thư cho Bảo (giấy mời, tin nhắn mới). Log backend 0 dòng ERROR.

So với đợt 1 cùng CV, cùng rubric (83,50): tiêu chí 1 thấp hơn 0,5, tiêu chí 3 cao hơn 0,5; hai tiêu chí còn lại giữ
nguyên.

Một lượt demo thật (bỏ câu A) tốn khoảng **14 lượt Anthropic và 1 lượt OpenAI**.

**Trước/sau `perf/scoring-latency`** (AI thật, dữ liệu demo vừa nạp, tin Kỹ sư DevOps, 3 đơn; trung vị 3 lượt, giây;
trước: 29/10 trên `main`, sau: 30/10)

| Kịch bản | Chờ nhận việc | Chấm tiêu chí | Tổng hợp + giải thích | Tổng | Lượt cuối |
|---|---|---|---|---|---|
| Đơn lẻ · trước → sau | 1,2 → 0,2 | 45,5 → 40,4 | 17,6 → 17,9 | 69,2 → 58,4 | — |
| Dồn 3 đơn trong 1 s · trước → sau | 57,9 → 47,8 | 44,9 → 42,0 | 75,8 → 18,2 | 178,6 → 111,5 | 193,2 → 139,1 |

Cách đọc: kịch bản dồn đơn giảm thật vì tổng hợp và giải thích không còn xếp hàng sau vòng chấm trên một luồng duy
nhất. Kịch bản đơn lẻ gần như không đổi — với 1 luồng, job tổng hợp/giải thích đã quá giờ nên chạy ngay khi luồng
rảnh, không có nhịp chờ để cắt; chênh tổng chủ yếu do thời gian AI chấm tiêu chí, phần cải thiện cấu trúc chỉ là chờ
nhận việc từ ~1 s xuống ≤ 0,3 s. Khi dồn đơn, chờ nhận việc vẫn cộng dồn thời gian chấm các lượt trước vì vòng chấm xử
lý tuần tự (giới hạn đã biết).

## 7. Câu hỏi hội đồng hay hỏi

**Vì sao AI không tính tổng điểm và không xếp hạng?**
Tổng điểm là căn cứ ra quyết định, nên phải tái lập được và giải thích được. Backend tính
Σ(điểm/điểm tối đa × trọng số) / Σ trọng số × 100 bằng một hàm thuần, không gọi AI
([ScoreAggregator.java](../backend/src/main/java/com/recruitment/scoring/ScoreAggregator.java)). Package `ai/` bị cấm
import lớp này ([CLAUDE.md](../CLAUDE.md) §7). Trọng số do HR đặt; mỗi lượt chấm chụp lại rubric (`rubric_snapshot`)
nên điểm cũ không đổi khi rubric đổi.

**Vì sao điểm đợt 2 khác đợt 1 dù cùng CV, cùng rubric?**
Mô hình ngôn ngữ không tất định: hai lượt chấm cùng đầu vào có thể lệch nhau nửa điểm ở một tiêu chí (lượt tập: 82,00
so với 83,50). Vì vậy mỗi điểm khác 0 phải kèm trích dẫn nguyên văn từ CV để HR kiểm được, và mỗi lượt chấm lưu riêng
`rubric_snapshot`, model và phiên bản prompt, nên hai lượt luôn đối chiếu được với nhau. Điểm chỉ là căn cứ; người
quyết định mời hay từ chối là HR.

**Evidence được kiểm nguyên văn thế nào?**
AI phải trả trích dẫn kèm mỗi điểm. Backend chuẩn hoá khoảng trắng rồi kiểm trích dẫn có nằm nguyên văn trong văn bản
CV hay không ([VerbatimQuoteVerifier.java](../backend/src/main/java/com/recruitment/ai/citation/VerbatimQuoteVerifier.java)).
Một trích dẫn không khớp thì cả lượt chấm thất bại với mã `EVIDENCE_NOT_VERIFIED`. Điểm khác 0 bắt buộc phải có
trích dẫn; điểm 0 được phép không có, kèm lý giải. Hỏi đáp CV dùng chung bộ kiểm này.

**Phần giải thích của AI có được kiểm như điểm số không?**
Không hoàn toàn. Điểm từng tiêu chí và các trích dẫn đã qua bộ kiểm nguyên văn (K2). Phần tóm tắt điểm mạnh/thiếu là
diễn giải của AI dựa trên kết quả chấm, không có văn bản CV để đối chiếu nên không kiểm từng con số
([ScoreExplanationService.java](../backend/src/main/java/com/recruitment/ai/explanation/ScoreExplanationService.java)).
Lượt tập 21/10/2026 từng ghi "khoảng 4 năm kinh nghiệm" trong khi CV và dữ liệu trích xuất cho khoảng 6 năm (76 tháng).
Vì vậy giao diện gắn nhãn "Do AI tạo", và quyết định thuộc về HR.

**Ứng viên bị từ chối có nộp lại được không?**
Trong cùng một chu kỳ tuyển dụng thì không: ràng buộc `uq_application_per_cycle` (tin, ứng viên, chu kỳ) nằm ở DB.
Khi HR đóng tin rồi mở lại, chu kỳ tăng lên và ứng viên nộp lại được; đơn đợt trước giữ nguyên, không bị xoá hay ghi
đè ([JobOwnerService.java](../backend/src/main/java/com/recruitment/job/JobOwnerService.java)).

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
Lượt tập dượt trọn kịch bản: 15 lượt Anthropic, 2 lượt OpenAI (mục 6). Chấm một đơn 4 tiêu chí tốn 5 lượt (4 tiêu chí
+ 1 giải thích). Dữ liệu demo có sẵn điểm và embedding do AI thật sinh một lần rồi lưu lại, nên nạp dữ liệu không tốn
lượt gọi nào ([db/seed/README.md](../db/seed/README.md) mục 3).

**Vì sao chấm điểm chạy job nền thay vì gọi ngay trong request?**
Một lượt chấm cần nhiều lần gọi AI, mất khoảng 1 phút. Giữ request và connection DB suốt thời gian đó sẽ cạn pool khi
nhiều đơn cùng chờ. Job nền nhận bản ghi bằng `UPDATE` có điều kiện, gọi AI ngoài transaction, thử lại khi lỗi tạm thời
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
- Tóm tắt giải thích (FR-H06) không qua bộ kiểm nguyên văn, có thể ghi sai số liệu so với CV
  ([ROADMAP](ROADMAP.md), Phase D, mục D4).
- Hạng trong tab "Ứng viên" tính trên mọi chu kỳ của tin ([ROADMAP](ROADMAP.md) `feat/fr-h14-compare`, M1).
- Danh sách đầy đủ: [README](../README.md) mục "Giới hạn đã biết".
