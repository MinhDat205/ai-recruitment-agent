# Chức năng bổ sung — thứ tự thực hiện và mô tả định hướng

> Đọc file này ở ĐẦU mỗi phiên làm một FR bổ sung, TRƯỚC khi đọc REQUIREMENT.md của FR đó.
> Cập nhật: 29/09/2026.

## 0. Cách dùng file này

- File này cho biết **toàn cảnh**: 21 FR bổ sung, thứ tự làm, FR nào dựa vào FR nào, và mỗi FR định làm gì. Mục đích: khi làm một FR, biết nó nằm ở đâu trong tổng thể, không làm lấn sang FR khác, không làm thiếu phần mà FR sau cần.
- File này là **mô tả định hướng**, KHÔNG phải đặc tả để code. Nguồn sự thật khi code là `docs/features/<nhóm>/<mã>/REQUIREMENT.md` và `UI.md` ở trạng thái `ĐÃ DUYỆT`.
- **Mâu thuẫn giữa file này và REQUIREMENT.md/UI.md đã duyệt → REQUIREMENT.md/UI.md thắng.** Báo ra chỗ mâu thuẫn, không tự sửa file này cho khớp.
- Chưa có REQUIREMENT.md đã duyệt → KHÔNG code, dù file này đã mô tả khá chi tiết (CLAUDE.md §6).
- Luật chung (CLAUDE.md), nguyên tắc bổ sung (SRS.md), giao diện và bản đồ màn hình (UI_GUIDE.md) không chép lại ở đây; mục 1–2 chỉ tóm tắt để đọc nhanh.

## 1. Nguyên tắc áp dụng cho mọi FR bổ sung

- AI không tự gửi, tự đăng hay tự quyết định thay người dùng. Mọi nội dung AI sinh ra là bản nháp hoặc gợi ý, chỉ có hiệu lực khi người dùng bấm xác nhận.
- Không lọc, không hỏi, không suy đoán thông tin nhân thân (tuổi, giới tính, ảnh, tình trạng hôn nhân, tôn giáo…) ở bất kỳ chức năng nào.
- Không gán nhãn Phù hợp/Không phù hợp, không dùng ngưỡng để loại. Xếp hạng ứng viên chỉ có một nguồn duy nhất là tổng điểm rubric (FR-H05).
- Dữ liệu thiếu phải hiển thị rõ là thiếu, không được âm thầm loại khỏi kết quả.
- Nội dung đã gửi cho người khác (tin nhắn, giấy mời, câu trả lời sàng lọc) được lưu nguyên văn, không bị thay đổi khi nguồn gốc (mẫu, câu hỏi) được sửa sau đó.
- HR chỉ thao tác với CV đã được gửi tới công ty mình. Hệ thống không có kho CV công khai.

Chi tiết: `docs/SRS.md` mục "Nguyên tắc bổ sung cho các chức năng mới"; `CLAUDE.md` §2, §7.

## 2. Thành phần dùng chung K1–K4

Đầy đủ ở `CLAUDE.md` §3d. Không cài lại riêng trong từng FR; FR cần mà thành phần chưa có thì dừng lại hỏi.

| Mã | Thành phần | Xây lần đầu ở | Dùng bởi |
|---|---|---|---|
| K1 | Bộ gom ngữ cảnh CV theo vai trò | FR-C07 | C07, C08, H13, H15 |
| K2 | Bộ kiểm tra trích dẫn nguyên văn | FR-C08 | FR-H04, C08, H13 |
| K3 | Gọi AI đồng bộ có giới hạn thời gian | FR-C07 | C07, C08, H11, H13, H15, U12, U13 |
| K4 | Siêu dữ liệu kết quả AI | FR-H11 | H11, H13, H15 |

## 3. Thứ tự thực hiện

Làm tuần tự từ trên xuống. Mỗi FR chỉ bắt đầu khi mọi mục trong cột "Phụ thuộc" đã "Đã hoàn thành" ở mục 0 của `docs/SRS.md`.

**Bước chuẩn bị (bắt buộc trước FR đầu tiên có giao diện):** `chore/ui-md3-foundation` — khai token vai trò MD3 (tiền tố `m3-`), không sửa màn hình cũ.

### Phase 2.1 — Nền dữ liệu & gợi ý việc làm

| # | Mã | Tên | Nhánh | Phụ thuộc |
|---|---|---|---|---|
| 1 | FR-C05 | Danh mục dùng chung và chuẩn hoá dữ liệu | `feat/fr-c05-catalog` | FR-H02, FR-C04 |
| 2 | FR-U07 | Bộ lọc tìm việc nâng cao | `feat/fr-u07-job-filter` | FR-C02, FR-C05 |
| 3 | FR-U14 | Hồ sơ nghề nghiệp và mong muốn công việc | `feat/fr-u14-career-profile` | FR-U01, FR-C05, FR-U04 |
| 4 | FR-U15 | Gợi ý việc làm theo hồ sơ (mở rộng FR-U04) | `feat/fr-u15-profile-recommend` | FR-U14, FR-U07, FR-C05, FR-U04 |

**Mốc 2.1b (bắt buộc trước Phase 2.2):** `refactor/ui-md3-legacy` — đồng bộ giao diện cũ theo MD3, chi tiết ở docs/ROADMAP.md.

**Xong phase khi:** Tin/hồ sơ thiếu dữ liệu chuẩn hoá vẫn hiện kèm nhãn, không bị loại âm thầm; ứng viên chưa có CV nhưng đã khai hồ sơ vẫn nhận gợi ý.

### Phase 2.2 — Hồ sơ đơn & trao đổi

| # | Mã | Tên | Nhánh | Phụ thuộc |
|---|---|---|---|---|
| 5 | FR-H09 | Trang hồ sơ đơn ứng tuyển | `feat/fr-h09-application-detail` | FR-H04, FR-H05, FR-H06, FR-H07 |
| 6 | FR-U08 | Trang chi tiết đơn ứng tuyển | `feat/fr-u08-application-detail` | FR-U03, FR-U06 |
| 7 | FR-C06 | Nhắn tin theo đơn ứng tuyển | `feat/fr-c06-messaging` | FR-C03, FR-U01, FR-H09, FR-U08 |
| 8 | FR-C07 | AI soạn nháp tin nhắn | `feat/fr-c07-ai-draft` | FR-C06 |

**Xong phase khi:** Gọi API đơn/cuộc trao đổi không thuộc về mình bị chặn (kiểm bằng curl); có test chứng minh ngữ cảnh gửi AI soạn nháp không chứa điểm/rubric.

### Phase 2.3 — Sàng lọc & tạo tin

| # | Mã | Tên | Nhánh | Phụ thuộc |
|---|---|---|---|---|
| 9 | FR-H10 | Câu hỏi sàng lọc theo Job | `feat/fr-h10-screening` | FR-H02 |
| 10 | FR-U09 | Trả lời câu hỏi sàng lọc và đồng ý lưu hồ sơ khi nộp đơn | `feat/fr-u09-screening-answer` | FR-U02, FR-H10 |
| 11 | FR-H11 | AI tạo tin tuyển dụng từ ngôn ngữ tự nhiên | `feat/fr-h11-ai-job-draft` | FR-H02, FR-H03, FR-C05, FR-H10 |

**Xong phase khi:** Câu trả lời sàng lọc không làm đổi điểm hay thứ hạng; tin tạo bằng AI luôn ở DRAFT; trọng số rubric gợi ý để trống.

### Phase 2.4 — Lịch phỏng vấn

| # | Mã | Tên | Nhánh | Phụ thuộc |
|---|---|---|---|---|
| 12 | FR-H12 | Giấy mời phỏng vấn nhiều khung giờ | `feat/fr-h12-multi-slot` | FR-H07, FR-C03 |
| 13 | FR-U10 | Chọn khung giờ phỏng vấn | `feat/fr-u10-pick-slot` | FR-H12, FR-U08 |

**Xong phase khi:** Đơn chuyển INTERVIEW_INVITED ngay khi gửi giấy mời, không phụ thuộc việc chọn giờ; giấy mời đã gửi không đổi khi sửa mẫu.

### Phase 2.5 — AI hỗ trợ đánh giá & kho ứng viên

| # | Mã | Tên | Nhánh | Phụ thuộc |
|---|---|---|---|---|
| 14 | FR-C08 | Hỏi đáp CV có trích dẫn | `feat/fr-c08-cv-qa` | FR-C04, FR-H09 |
| 15 | FR-H13 | Tạo câu hỏi phỏng vấn riêng cho từng CV | `feat/fr-h13-interview-questions` | FR-H09 |
| 16 | FR-H14 | So sánh song song 2–3 ứng viên | `feat/fr-h14-compare` | FR-H05, FR-H06, FR-H09 |
| 17 | FR-H15 | Kho ứng viên (ghi chú, AI tóm tắt, bộ lọc) | `feat/fr-h15-talent-pool` | FR-H09, FR-C05, FR-U09 |

**Xong phase khi:** Mọi trích dẫn hiển thị đều đã qua K2; FR-H04 chuyển sang dùng K2 mà toàn bộ test cũ vẫn pass; backend chặn việc thêm đơn chưa đồng ý vào kho.

### Phase 2.6 — Tiện ích ứng viên & ẩn danh

| # | Mã | Tên | Nhánh | Phụ thuộc |
|---|---|---|---|---|
| 18 | FR-U11 | Thống kê hoạt động ứng tuyển cá nhân | `feat/fr-u11-stats` | FR-U03 |
| 19 | FR-U12 | Tạo CV (CV builder) | `feat/fr-u12-cv-builder` | FR-U01, FR-C04 |
| 20 | FR-U13 | Tìm kiếm việc làm bằng ngôn ngữ tự nhiên | `feat/fr-u13-nl-search` | FR-U07, FR-U15, FR-C05 |
| 21 | FR-H16 | Chế độ ẩn danh khi duyệt hồ sơ | `feat/fr-h16-blind` | FR-H09, FR-H14, FR-C08 |

**Xong phase khi:** Bật ẩn danh không đổi đầu vào chấm điểm; CV builder không thêm nội dung ngoài dữ liệu ứng viên nhập.

## 4. Mô tả từng FR (theo thứ tự thực hiện)

### 1. FR-C05 — Danh mục dùng chung và chuẩn hoá dữ liệu

- **Phase:** 2.1 · **Nhánh:** `feat/fr-c05-catalog` · **Phía sử dụng:** Hệ thống (HR và Ứng viên dùng gián tiếp)
- **Đặc tả chi tiết:** `docs/features/CHUNG/C05/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-H02, FR-C04
- **Mở rộng chức năng hiện có:** FR-H02 (ngành nghề/địa điểm chọn theo danh mục), FR-C04 (schema trích xuất phiên bản 2)
- **Màn hình (UI_GUIDE mục 7):** Không có màn hình riêng. Mở rộng màn hình cũ: form tạo/sửa Job (`/hr/jobs/new`, `/hr/jobs/:id/edit`), danh sách Job HR (`/hr/jobs`), thẻ việc làm `JobCard` (`/`, `/candidate`, khối gợi ý ở `/candidate/dashboard`), chi tiết tin (`/jobs/:id`), danh sách CV và dialog "Dữ liệu đã trích xuất" (`/candidate/profile`). Danh mục được dùng tiếp ở bộ lọc của U07, H15.

**Mục đích**

Tạo nền dữ liệu chuẩn để lọc chính xác việc làm (U07) và hồ sơ trong kho (H15). Hiện ngành nghề và địa điểm của Job là chuỗi tự do nên lọc chỉ là so chuỗi.

**Người dùng thao tác**

HR chọn ngành nghề và tỉnh/thành từ danh sách khi tạo/sửa Job, thay cho ô nhập tự do. Ứng viên chọn từ cùng danh sách khi lọc việc làm.

**Hệ thống**

- Danh mục ngành nghề và tỉnh/thành cố định, nạp bằng migration.
- Chuyển dữ liệu jobs.category và jobs.location cũ sang mã danh mục. Giá trị không khớp được giữ nguyên văn và đánh dấu "chưa chuẩn hoá".
- Mở rộng schema trích xuất CV (phiên bản 2): thêm chức danh hiện tại (giữ nguyên văn, không quy về mã), ngành nghề và khu vực theo mã danh mục.
- Backend tự tính tổng số năm kinh nghiệm từ các mốc thời gian ở mục kinh nghiệm: xử lý "Hiện tại/Present", gộp các khoảng trùng nhau, bỏ qua mốc không đọc được. Kết quả lưu thành trường riêng. Tính cho cả CV đã trích xuất theo schema cũ (job nền, không gọi AI).
- Cung cấp cách trích xuất lại cho CV đã trích xuất theo schema cũ.

**AI**

Chỉ tham gia ở bước trích xuất CV (FR-C04): gán ngành nghề vào mã danh mục; trích nguyên văn chức danh hiện tại và địa danh khu vực (backend ánh xạ địa danh sang mã); không chắc chắn thì để trống. AI không tính số năm kinh nghiệm.

**Kết quả**

Job và CV đã trích xuất có ngành nghề, khu vực theo danh mục; CV có số năm kinh nghiệm được tính xác định.

**Lưu ý**

CV trích xuất theo schema cũ vẫn đọc được; chức danh, ngành nghề, khu vực để trống và hiển thị "chưa có dữ liệu" cho tới khi trích xuất lại (số năm kinh nghiệm vẫn được tính ngay). Không suy ra thông tin nhân thân từ CV.

**Phạm vi**

Danh mục cố định, chưa có màn hình quản trị danh mục.

### 2. FR-U07 — Bộ lọc tìm việc nâng cao

- **Phase:** 2.1 · **Nhánh:** `feat/fr-u07-job-filter` · **Phía sử dụng:** Ứng viên
- **Đặc tả chi tiết:** `docs/features/UV/U07/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-C02, FR-C05
- **Màn hình (UI_GUIDE mục 7):** `/` và `/candidate` (trang Việc làm); `/jobs/:id` (chi tiết tin — chỉ chọn layout theo vai trò và màu chữ lương, nội dung còn lại giữ nguyên).

**Mục đích**

Giúp ứng viên thu hẹp nhanh danh sách tin tuyển dụng thay vì cuộn qua toàn bộ danh sách công khai.

**Người dùng thao tác**

Ứng viên vào trang danh sách việc làm → chọn bộ lọc: từ khoá, ngành nghề, tỉnh/thành, khoảng lương, hình thức làm việc, thời gian đăng (24 giờ, 7 ngày, 30 ngày) → chọn sắp xếp (mới nhất, lương cao nhất) → xem danh sách đã lọc.

**Hệ thống**

Đọc dữ liệu Job công khai (FR-C02), dùng danh mục C05. Kết hợp nhiều điều kiện cùng lúc; giữ trạng thái bộ lọc trên URL để chia sẻ/lưu lại. Khi lọc theo lương, tin không công bố lương vẫn hiển thị với nhãn "Thoả thuận" (có tuỳ chọn ẩn); tin có ngành nghề chưa chuẩn hoá hiển thị nhãn tương ứng.

**AI**

Không dùng AI. Lọc là so khớp xác định trên dữ liệu có sẵn.

**Kết quả**

Danh sách việc làm thoả toàn bộ điều kiện lọc, có phân trang, giữ thứ tự sắp xếp ứng viên chọn.

**Lưu ý**

Không gán nhãn hay tự xếp loại "phù hợp/không phù hợp" thay ứng viên. Ứng viên đã đăng nhập phải giữ thanh điều hướng của mình trên trang danh sách và chi tiết việc làm (sửa lỗi trang chi tiết Job đang dùng layout công khai).

**Phạm vi**

Lọc trên việc làm công khai đang ở trạng thái OPEN.

### 3. FR-U14 — Hồ sơ nghề nghiệp và mong muốn công việc

- **Phase:** 2.1 · **Nhánh:** `feat/fr-u14-career-profile` · **Phía sử dụng:** Ứng viên
- **Đặc tả chi tiết:** `docs/features/UV/U14/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-U01, FR-C05, FR-U04
- **Mở rộng chức năng hiện có:** FR-U01 (hồ sơ nghề nghiệp)
- **Màn hình (UI_GUIDE mục 7):** ★`/candidate/onboarding` (lần đăng nhập đầu tiên sau đăng ký, bỏ qua được) và `/candidate/profile`.

**Mục đích**

Thu thập thông tin nghề nghiệp và mong muốn công việc ngay từ đầu, để hệ thống gợi ý được việc làm kể cả khi ứng viên chưa tải CV.

**Người dùng thao tác**

- Đăng ký xong vẫn về trang đăng nhập như hiện tại (API đăng ký không trả phiên đăng nhập); ở lần đăng nhập đầu tiên sau đó, hệ thống tự chuyển tới màn Hoàn thiện hồ sơ (bỏ qua được) → điền chức danh mong muốn, ngành nghề mong muốn (tối đa 3), khu vực mong muốn (tối đa 3), hình thức làm việc, mức lương mong muốn tối thiểu, số năm kinh nghiệm, kỹ năng chính (dạng thẻ), giới thiệu ngắn → Lưu.
- Sửa bất kỳ lúc nào ở trang Hồ sơ cá nhân (FR-U01); dùng cùng một form.
- Tuỳ chọn bấm Điền từ CV để lấy sẵn chức danh, kỹ năng, số năm kinh nghiệm từ CV chính đã trích xuất rồi chỉnh lại.

**Hệ thống**

- Mở rộng hồ sơ ứng viên hiện có; ngành nghề và khu vực lưu theo mã danh mục C05. Form đăng ký giữ nguyên các trường bắt buộc hiện tại, không dài thêm.
- Điền từ CV là sao chép xác định từ CV đã trích xuất (FR-C04, C05), không gọi AI.
- Khi lưu, dựng văn bản đại diện (chức danh mong muốn, kỹ năng, giới thiệu) và tạo embedding bằng model của FR-U04, chạy nền theo cơ chế sẵn có.

**AI**

Không gọi LLM; chỉ dùng embedding.

**Kết quả**

Hồ sơ nghề nghiệp và mong muốn công việc làm đầu vào cho gợi ý việc làm (U15) và điền sẵn bộ lọc (U07).

**Lưu ý**

Mọi trường đều không bắt buộc. Mong muốn công việc (đặc biệt mức lương) chỉ dùng để gợi ý, không hiển thị cho HR. Không thêm trường giới tính, tình trạng hôn nhân hay thông tin nhân thân; ngày sinh sẵn có không được dùng cho gợi ý.

**Phạm vi**

Thông tin do ứng viên tự khai; chưa kiểm chứng với CV.

### 4. FR-U15 — Gợi ý việc làm theo hồ sơ (mở rộng FR-U04)

- **Phase:** 2.1 · **Nhánh:** `feat/fr-u15-profile-recommend` · **Phía sử dụng:** Ứng viên
- **Đặc tả chi tiết:** `docs/features/UV/U15/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-U14, FR-U07, FR-C05, FR-U04
- **Mở rộng chức năng hiện có:** FR-U04 (gợi ý khi chưa có CV, một nguồn gợi ý duy nhất)
- **Màn hình (UI_GUIDE mục 7):** Khối "Gợi ý cho bạn" ở `/candidate`; thay khối gợi ý hiện tại ở `/candidate/dashboard`.

**Mục đích**

Chủ động đưa một số việc làm phù hợp lên trang tìm việc, thay vì ứng viên phải tự tìm từ đầu.

**Người dùng thao tác**

- Ứng viên đã đăng nhập mở trang Việc làm → khối Gợi ý cho bạn (tối đa 6 việc) nằm trên danh sách; mỗi thẻ ghi các điều kiện khớp (ví dụ "Hà Nội · CNTT · Toàn thời gian · Lương đạt mong muốn").
- Bấm Xem tất cả → mở danh sách đầy đủ với bộ lọc U07 đã điền sẵn từ mong muốn, ứng viên sửa tiếp được.
- Chưa có hồ sơ và CV: khối hiển thị lời mời hoàn thiện hồ sơ (U14). Cùng danh sách gợi ý hiển thị ở trang Tổng quan, thay khối gợi ý hiện tại.

**Hệ thống**

- Điều kiện cứng lấy từ mong muốn: ngành nghề và khu vực (nếu ứng viên có khai), áp bằng đúng truy vấn của U07; chỉ lấy việc làm OPEN, còn hạn, chưa ứng tuyển trong chu kỳ hiện tại.
- Xếp hạng theo độ tương đồng giữa vector đại diện của ứng viên và embedding việc làm. Vector là embedding của CV chính nếu có (như FR-U04 hiện tại), nếu chưa có CV thì dùng embedding hồ sơ U14.
- Mức lương và hình thức làm việc không loại tin; chỉ hiện chip điều kiện khớp khi thực sự khớp, không khớp thì không hiện gì — tránh danh sách rỗng vì nhiều tin không công bố lương.
- Nhánh CV giữ ngưỡng tương đồng hiện có của FR-U04. Nhánh hồ sơ không áp ngưỡng: văn bản hồ sơ ngắn nên điểm tương đồng không cùng thang với CV mà ngưỡng đã được hiệu chỉnh; phạm vi đã được giới hạn bằng điều kiện cứng.
- Tính trực tiếp khi mở trang, dùng chung bộ máy "điều kiện cứng + xếp theo embedding" với U13. Bộ đệm và lịch làm mới của FR-U04 được thay bằng cách này để hệ thống chỉ còn một nguồn gợi ý.
- Không có kết quả thì hiển thị thông báo và đề nghị mở rộng mong muốn; hệ thống không tự nới điều kiện.

**AI**

Không gọi LLM; dùng embedding có sẵn của CV, hồ sơ và việc làm.

**Kết quả**

Danh sách vài việc làm liên quan xuất hiện ngay trên trang tìm việc, kèm điều kiện khớp để ứng viên hiểu vì sao được gợi ý.

**Lưu ý**

Không gắn nhãn "Phù hợp/Không phù hợp"; chỉ hiển thị điều kiện đã khớp. Gợi ý không ảnh hưởng tới cách HR nhìn thấy hay xếp hạng ứng viên.

**Phạm vi**

Chỉ cho ứng viên đã đăng nhập; chưa gửi email gợi ý định kỳ.

### 5. FR-H09 — Trang hồ sơ đơn ứng tuyển

- **Phase:** 2.2 · **Nhánh:** `feat/fr-h09-application-detail` · **Phía sử dụng:** HR
- **Đặc tả chi tiết:** `docs/features/HR/H09/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-H04, FR-H05, FR-H06, FR-H07
- **Màn hình (UI_GUIDE mục 7):** ★`/hr/applications/:id` — điểm vào từ tab Ứng viên của `/hr/jobs/:id/edit`, từ `/hr/candidates`, từ kho ứng viên và thông báo.

**Mục đích**

Gom mọi thông tin và thao tác về một đơn ứng tuyển vào một màn hình, làm điểm đặt cho các chức năng mới.

**Người dùng thao tác**

- Từ danh sách xếp hạng của Job, danh sách Ứng viên, Kho ứng viên hoặc thông báo → mở hồ sơ đơn.
- Các tab: CV & điểm (file CV, CV đã trích xuất, điểm từng tiêu chí, evidence), Giải thích (FR-H06), Sàng lọc (H10), Câu hỏi phỏng vấn (H13), Trao đổi (C06), Hỏi đáp CV (C08), Lịch sử trạng thái.
- Thanh thao tác: Mời phỏng vấn / Từ chối / Trúng tuyển (FR-H07), Thêm vào kho (H15).

**Hệ thống**

Bổ sung API chi tiết đơn phía HR, kiểm tra đơn thuộc Job của công ty HR. Mỗi tab tải dữ liệu riêng khi mở; tab chưa có dữ liệu hiển thị trạng thái rõ ràng (chưa chấm, trích xuất lỗi…).

**AI**

Không gọi AI mới; hiển thị kết quả đã có.

**Kết quả**

Một màn hình duy nhất cho mọi thao tác trên đơn.

**Lưu ý**

Các nút đổi trạng thái dùng đúng luồng và máy trạng thái của FR-H07, không tạo luồng song song. Nút Thêm vào kho bị khoá và ghi rõ lý do khi ứng viên không đồng ý lưu hồ sơ (U09).

**Phạm vi**

Xem và thao tác trên một đơn.

### 6. FR-U08 — Trang chi tiết đơn ứng tuyển

- **Phase:** 2.2 · **Nhánh:** `feat/fr-u08-application-detail` · **Phía sử dụng:** Ứng viên
- **Đặc tả chi tiết:** `docs/features/UV/U08/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-U03, FR-U06
- **Màn hình (UI_GUIDE mục 7):** ★`/candidate/applications/:id` — điểm vào từ `/candidate/applications` và thông báo.

**Mục đích**

Gom mọi thông tin và thao tác của ứng viên trên một đơn vào một màn hình.

**Người dùng thao tác**

Từ Đơn của tôi hoặc thông báo → mở chi tiết đơn → xem thông tin Job, CV đã nộp, trạng thái và lịch sử (FR-U03), giấy mời và chọn khung giờ (U10), câu trả lời sàng lọc đã gửi (U09), trạng thái đồng ý lưu hồ sơ kèm nút Rút đồng ý, tab Trao đổi (C06), nút Rút đơn (FR-U06).

**Hệ thống**

- Bổ sung API chi tiết đơn phía ứng viên, kiểm tra quyền sở hữu.
- Không trả điểm, rubric, giải thích AI, ghi chú, câu hỏi phỏng vấn hay thông tin kho.
- Rút đồng ý: đặt lại cờ đồng ý lưu hồ sơ của đơn, ghi thời điểm, xoá đơn khỏi mọi kho ứng viên (H15). Rút đồng ý là một chiều, không bật lại được cho đơn đã nộp.

**AI**

Không dùng AI.

**Kết quả**

Một màn hình duy nhất cho mọi thao tác của ứng viên trên đơn.

**Lưu ý**

Ứng viên chỉ xem được đơn của chính mình. Rút đồng ý không ảnh hưởng trạng thái, điểm số hay việc xét tuyển của đơn.

**Phạm vi**

Xem và thao tác trên một đơn của chính ứng viên.

### 7. FR-C06 — Nhắn tin theo đơn ứng tuyển

- **Phase:** 2.2 · **Nhánh:** `feat/fr-c06-messaging` · **Phía sử dụng:** HR, Ứng viên
- **Đặc tả chi tiết:** `docs/features/CHUNG/C06/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-C03, FR-U01, FR-H09, FR-U08
- **Mở rộng chức năng hiện có:** FR-C03 (loại thông báo tin nhắn mới)
- **Màn hình (UI_GUIDE mục 7):** Tab Trao đổi ở ★`/hr/applications/:id` và ★`/candidate/applications/:id`; hộp thư ★`/hr/messages`, ★`/candidate/messages`.

**Mục đích**

Cho HR và ứng viên trao đổi trực tiếp quanh một đơn ứng tuyển cụ thể, thay vì liên lạc ngoài hệ thống.

**Người dùng thao tác**

- HR mở hồ sơ đơn (H09) → tab Trao đổi → nhập tin, tuỳ chọn đính kèm file → Gửi.
- Ứng viên mở chi tiết đơn (U08) → tab Trao đổi → đọc và trả lời.
- Cả hai bên có trang Tin nhắn liệt kê các cuộc trao đổi, đánh dấu cuộc có tin chưa đọc.

**Hệ thống**

- Mỗi đơn ứng tuyển có tối đa một cuộc trao đổi, tạo khi có tin đầu tiên; cả hai bên đều có thể bắt đầu.
- Chỉ HR sở hữu Job và ứng viên chủ đơn được đọc/gửi.
- Lưu nguyên văn tin, người gửi, thời điểm và trạng thái đã đọc.
- Đính kèm dùng lại cơ chế lưu file và kiểm định dạng bằng magic bytes của FR-U01 (PDF, DOCX, ảnh; có giới hạn dung lượng). File chỉ tải được khi đã đăng nhập và có quyền trên cuộc trao đổi.
- Mỗi tin mới phát thông báo cho bên nhận qua FR-C03 (web + email). Email chứa nội dung tin và liên kết, không đính kèm file.
- Giao diện tự tải lại định kỳ khi đang mở cuộc trao đổi.

**AI**

Không dùng AI. Phần soạn nháp bằng AI thuộc C07.

**Kết quả**

Lịch sử trao đổi đầy đủ gắn với đơn; bên nhận được thông báo khi có tin mới.

**Lưu ý**

Tin đã gửi không sửa, không xoá. Khi đơn ở trạng thái WITHDRAWN, cuộc trao đổi chỉ còn xem. Tin nhắn không thay thế thao tác đổi trạng thái ở FR-H07.

**Phạm vi**

Trao đổi một–một theo đơn; không thời gian thực; không nhắn tới ứng viên chưa nộp đơn; không gửi hàng loạt.

### 8. FR-C07 — AI soạn nháp tin nhắn

- **Phase:** 2.2 · **Nhánh:** `feat/fr-c07-ai-draft` · **Phía sử dụng:** HR, Ứng viên
- **Đặc tả chi tiết:** `docs/features/CHUNG/C07/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-C06
- **Thành phần dùng chung:** Xây lần đầu K1, K3.
- **Màn hình (UI_GUIDE mục 7):** Nằm trong khung soạn tin của C06, không có route riêng.

**Mục đích**

Giúp hai bên soạn nhanh tin nhắn/email nhất quán, không phải viết từ đầu.

**Người dùng thao tác**

- Trong khung soạn tin của C06 bấm Soạn bằng AI → chọn tình huống có sẵn hoặc tự mô tả mục đích → chọn giọng văn (trang trọng/thân thiện) → bản nháp được điền vào khung soạn → chỉnh sửa → bấm Gửi như tin thường.
- Tình huống phía HR: đề nghị bổ sung thông tin, nhắc lịch phỏng vấn, cảm ơn đã ứng tuyển, thông báo kết quả, tự mô tả.
- Tình huống phía Ứng viên: hỏi tiến độ, cảm ơn sau phỏng vấn, xin dời lịch phỏng vấn, tự mô tả.

**Hệ thống**

- Dựng ngữ cảnh qua K1, chỉ gồm dữ liệu được phép chia sẻ giữa hai bên: tên ứng viên, tên Job, tên công ty, trạng thái đơn hiện tại, lịch phỏng vấn đã gửi và vài tin gần nhất của cuộc trao đổi.
- Điểm số, rubric, giải thích AI, ghi chú nội bộ và câu hỏi phỏng vấn không được đưa vào ngữ cảnh.
- Gọi AI theo K3. Bản nháp không được lưu; chỉ tin đã gửi được lưu qua C06.

**AI**

Soạn bản nháp tiếng Việt theo tình huống, giọng văn và ngữ cảnh được cung cấp.

**Kết quả**

Bản nháp nằm trong khung soạn để người dùng duyệt và sửa.

**Lưu ý**

AI không tự gửi. Không bịa thông tin ngoài ngữ cảnh (ví dụ "hồ sơ đang được ưu tiên", mức lương, ngày giờ không có). Tình huống "thông báo kết quả" chỉ mở khi đơn đã ở HIRED hoặc REJECTED, không thay thế FR-H07.

**Phạm vi**

Soạn nháp trong cuộc trao đổi theo đơn; không soạn hàng loạt.

### 9. FR-H10 — Câu hỏi sàng lọc theo Job

- **Phase:** 2.3 · **Nhánh:** `feat/fr-h10-screening` · **Phía sử dụng:** HR
- **Đặc tả chi tiết:** `docs/features/HR/H10/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-H02
- **Mở rộng chức năng hiện có:** FR-H02 (thêm tab câu hỏi sàng lọc)
- **Màn hình (UI_GUIDE mục 7):** ★Tab "Câu hỏi sàng lọc" ở `/hr/jobs/:id/edit`; câu trả lời xem ở tab Sàng lọc của ★`/hr/applications/:id`.

**Mục đích**

Thu thập sớm các thông tin CV thường không có (thời gian đi làm được, mức lương mong muốn, sẵn sàng làm ca…).

**Người dùng thao tác**

Khi tạo/sửa Job → tab Câu hỏi sàng lọc → thêm câu hỏi (trả lời ngắn, một lựa chọn, có/không) → đánh dấu câu bắt buộc → sắp xếp thứ tự. HR xem câu trả lời ở tab Sàng lọc của hồ sơ đơn (H09).

**Hệ thống**

Lưu bộ câu hỏi theo Job. Khi ứng viên nộp đơn (U09), hệ thống chụp lại nội dung câu hỏi cùng câu trả lời theo đơn, nên HR sửa câu hỏi sau đó không làm đổi đơn cũ. Bộ câu hỏi đã sửa chỉ áp dụng cho đơn nộp sau.

**AI**

Không dùng AI. Gợi ý câu hỏi nằm ở H11.

**Kết quả**

Mỗi đơn ứng tuyển kèm câu trả lời sàng lọc, hiển thị cạnh điểm số và CV.

**Lưu ý**

Không có cơ chế tự động loại ứng viên theo câu trả lời. Câu trả lời không đưa vào chấm điểm AI (FR-H04) và không ảnh hưởng tổng điểm, xếp hạng. Không hỏi thông tin nhạy cảm (tuổi, hôn nhân, tôn giáo, sức khoẻ…); form hiển thị lưu ý này.

**Phạm vi**

Câu hỏi dạng văn bản ngắn, một lựa chọn, có/không; chưa có bài test chấm tự động hay tải file đính kèm.

### 10. FR-U09 — Trả lời câu hỏi sàng lọc và đồng ý lưu hồ sơ khi nộp đơn

- **Phase:** 2.3 · **Nhánh:** `feat/fr-u09-screening-answer` · **Phía sử dụng:** Ứng viên
- **Đặc tả chi tiết:** `docs/features/UV/U09/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-U02, FR-H10
- **Mở rộng chức năng hiện có:** FR-U02 (câu trả lời sàng lọc, đồng ý lưu hồ sơ)
- **Màn hình (UI_GUIDE mục 7):** Form nộp đơn `/jobs/:id/apply`; xem lại ở ★`/candidate/applications/:id`.

**Mục đích**

Là phía ứng viên của H10 và H15: ứng viên trả lời câu hỏi sàng lọc và tự quyết định có cho công ty lưu hồ sơ cho các đợt tuyển sau hay không.

**Người dùng thao tác**

Ở form nộp đơn (FR-U02) → trả lời các câu hỏi sàng lọc của Job (H10) → tuỳ chọn tick "Cho phép công ty lưu hồ sơ của tôi để liên hệ cho các vị trí sau này" → Nộp đơn.

**Hệ thống**

- Chặn nộp đơn khi thiếu câu bắt buộc, kiểm tra ở backend chứ không chỉ ở frontend.
- Lưu câu trả lời kèm bản chụp câu hỏi trong cùng giao dịch nộp đơn.
- Thêm cờ đồng ý lưu hồ sơ và thời điểm đồng ý vào đơn ứng tuyển, tách biệt với đồng ý cho AI chấm điểm (bắt buộc, đã có).
- Đơn đã có trước đây mặc định là không đồng ý, kể cả dữ liệu seed demo.

**AI**

Không dùng AI.

**Kết quả**

Đơn ứng tuyển kèm câu trả lời sàng lọc và lựa chọn đồng ý lưu hồ sơ.

**Lưu ý**

Ô đồng ý lưu hồ sơ mặc định không tick và không bắt buộc; không tick vẫn nộp đơn bình thường và không ảnh hưởng điểm, xếp hạng hay việc xét tuyển. Đồng ý tính theo từng đơn: hai đơn vào cùng công ty có thể có lựa chọn khác nhau.

**Phạm vi**

Áp dụng cho đơn nộp sau khi triển khai; không hỏi lại đồng ý cho đơn cũ.

### 11. FR-H11 — AI tạo tin tuyển dụng từ ngôn ngữ tự nhiên

- **Phase:** 2.3 · **Nhánh:** `feat/fr-h11-ai-job-draft` · **Phía sử dụng:** HR
- **Đặc tả chi tiết:** `docs/features/HR/H11/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-H02, FR-H03, FR-C05, FR-H10
- **Mở rộng chức năng hiện có:** FR-H02 (tạo tin bằng AI)
- **Thành phần dùng chung:** Xây lần đầu K4.
- **Màn hình (UI_GUIDE mục 7):** Nút "Tạo tin bằng AI" ở `/hr/jobs/new`.

**Mục đích**

Rút ngắn thời gian đăng tin: HR mô tả bằng lời thường, hệ thống dựng thành bài đăng có cấu trúc.

**Người dùng thao tác**

HR bấm Tạo tin bằng AI → nhập mô tả tự do (vị trí, công việc, mức lương, địa chỉ…) → xem form tạo Job đã điền sẵn, danh sách trường còn thiếu, gợi ý tiêu chí rubric và gợi ý câu hỏi sàng lọc → sửa, giữ hoặc bỏ từng gợi ý → Lưu nháp.

**Hệ thống**

- Gọi AI theo K3; đổ kết quả vào form tạo Job của FR-H02. Không lưu gì cho tới khi HR bấm Lưu.
- Ánh xạ ngành nghề, địa điểm sang danh mục C05.
- Tiêu chí rubric gợi ý được tạo với trọng số để trống; HR phải tự nhập. Ràng buộc tổng trọng số 100% khi mở tin vẫn áp dụng.
- Lưu siêu dữ liệu K4 cho tin được tạo bằng AI.

**AI**

Tách mô tả thành tiêu đề, mô tả, yêu cầu, quyền lợi, lương, địa điểm, hình thức làm việc, ngành nghề; viết lại mô tả cho rõ ràng; gợi ý tên và mô tả tiêu chí rubric; gợi ý câu hỏi sàng lọc.

**Kết quả**

Tin tuyển dụng ở trạng thái Nháp, kèm danh sách trường còn thiếu cần HR bổ sung.

**Lưu ý**

AI không tự đăng tin. AI không bịa thông tin HR không nêu (ví dụ mức lương); trường thiếu để trống và nhắc HR. AI không gợi ý trọng số. Không gợi ý tiêu chí hay câu hỏi mang tính phân biệt đối xử.

**Phạm vi**

Tạo mới tin tuyển dụng; chưa hỗ trợ sửa tin đã đăng bằng ngôn ngữ tự nhiên.

### 12. FR-H12 — Giấy mời phỏng vấn nhiều khung giờ

- **Phase:** 2.4 · **Nhánh:** `feat/fr-h12-multi-slot` · **Phía sử dụng:** HR
- **Đặc tả chi tiết:** `docs/features/HR/H12/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-H07, FR-C03
- **Mở rộng chức năng hiện có:** FR-H07 (giấy mời nhiều khung giờ, gửi lại giấy mời), FR-C03 (thông báo ứng viên đã chốt lịch)
- **Màn hình (UI_GUIDE mục 7):** Hộp thoại mời phỏng vấn ở ★`/hr/applications/:id`.

**Mục đích**

Cho ứng viên chọn lịch phù hợp, giảm trao đổi qua lại để chốt giờ phỏng vấn.

**Người dùng thao tác**

Khi Mời phỏng vấn (FR-H07) → thêm 1–5 khung giờ, mỗi khung có thời điểm và địa chỉ hoặc link phòng họp → Gửi. HR xem khung giờ ứng viên đã chọn ở hồ sơ đơn (H09).

**Hệ thống**

- Mở rộng giấy mời thành danh sách khung giờ. Mẫu giấy mời (FR-H02) giữ nguyên, chỉ thêm chỗ chèn danh sách khung giờ khi render.
- Kiểm tra khung giờ không nằm trong quá khứ và không trùng nhau trong cùng giấy mời. Giấy mời chỉ có một khung giờ được coi là đã chốt, giữ tương thích luồng hiện tại.
- Lưu nguyên văn giấy mời đã gửi. Khi ứng viên chọn khung giờ (U10), hệ thống ghi nhận và thông báo cho HR qua FR-C03.
- Bổ sung thao tác Gửi lại giấy mời khi đơn đang ở INTERVIEW_INVITED: tạo giấy mời mới, không đổi trạng thái đơn; giấy mời cũ giữ nguyên trong lịch sử.

**AI**

Không dùng AI.

**Kết quả**

Giấy mời có nhiều lựa chọn thời gian/địa điểm; HR thấy khung giờ ứng viên đã xác nhận.

**Lưu ý**

Đơn vẫn chuyển sang INTERVIEW_INVITED ngay khi HR gửi giấy mời, không phụ thuộc việc ứng viên chọn giờ. Nội dung đã gửi không bị thay đổi khi HR sửa mẫu sau đó.

**Phạm vi**

Chưa đồng bộ Google Calendar/Outlook, chưa tự tạo link họp; khung giờ không được giữ chỗ giữa các ứng viên khác nhau.

### 13. FR-U10 — Chọn khung giờ phỏng vấn

- **Phase:** 2.4 · **Nhánh:** `feat/fr-u10-pick-slot` · **Phía sử dụng:** Ứng viên
- **Đặc tả chi tiết:** `docs/features/UV/U10/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-H12, FR-U08
- **Mở rộng chức năng hiện có:** FR-C03 (thông báo ứng viên đã chốt lịch)
- **Màn hình (UI_GUIDE mục 7):** ★`/candidate/applications/:id`.

**Mục đích**

Là phía ứng viên của H12: ứng viên tự chọn lịch phỏng vấn phù hợp.

**Người dùng thao tác**

Ứng viên mở chi tiết đơn (U08) hoặc thông báo giấy mời → xem các khung giờ → chọn một khung → Xác nhận. Nếu không khung nào phù hợp → nhắn HR qua C06 (có sẵn tình huống "xin dời lịch" ở C07).

**Hệ thống**

Chỉ chọn được khi đơn ở INTERVIEW_INVITED, giấy mời chưa chốt và khung giờ chưa qua. Chọn một lần; sau khi xác nhận không tự đổi được (muốn đổi thì trao đổi với HR để HR gửi lại giấy mời). Thông báo cho HR qua FR-C03.

**AI**

Không dùng AI.

**Kết quả**

Khung giờ đã chốt hiển thị cho cả ứng viên và HR.

**Lưu ý**

Việc chọn giờ không làm thay đổi trạng thái đơn.

**Phạm vi**

Chọn trong các khung HR đã đưa ra; chưa đồng bộ lịch cá nhân.

### 14. FR-C08 — Hỏi đáp CV có trích dẫn

- **Phase:** 2.5 · **Nhánh:** `feat/fr-c08-cv-qa` · **Phía sử dụng:** HR, Ứng viên
- **Đặc tả chi tiết:** `docs/features/CHUNG/C08/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-C04, FR-H09
- **Thành phần dùng chung:** Xây lần đầu K2 (tách từ FR-H04).
- **Màn hình (UI_GUIDE mục 7):** Tab Hỏi đáp CV ở ★`/hr/applications/:id`; ★`/candidate/resumes/:id/qa`.

**Mục đích**

Hỏi trực tiếp về nội dung một CV cụ thể và nhận câu trả lời có dẫn chứng, thay vì tự đọc dò toàn bộ hồ sơ.

**Người dùng thao tác**

- HR: tại hồ sơ đơn (H09) → tab Hỏi đáp CV → đặt câu hỏi (ví dụ "Ứng viên đã dùng Kubernetes trong dự án nào?") → đọc câu trả lời, bấm vào trích dẫn để xem đoạn tương ứng trong CV.
- Ứng viên: tại CV của tôi → chọn một CV → Hỏi đáp; tuỳ chọn chọn một Job đang mở để hỏi đối chiếu (ví dụ "JD yêu cầu gì mà CV của tôi chưa đề cập?").

**Hệ thống**

- Ngữ cảnh qua K1 theo vai trò. HR: CV của đơn, JD và rubric của Job đó. Ứng viên: CV của chính mình và JD của Job được chọn (nếu có), không bao giờ có điểm hay rubric.
- Kiểm tra quyền: HR chỉ hỏi CV của đơn thuộc Job công ty mình; ứng viên chỉ hỏi CV của mình.
- Mọi trích dẫn được kiểm tra qua K2; trích dẫn không khớp bị loại. Câu trả lời không còn trích dẫn hợp lệ hiển thị "Không tìm thấy căn cứ trong CV".
- Lưu lịch sử hỏi đáp theo người dùng và CV/đơn. Gọi AI theo K3.

**AI**

Trả lời chỉ dựa trên CV/JD được cung cấp, kèm trích dẫn nguyên văn; nói rõ "CV không đề cập" khi không có thông tin.

**Kết quả**

Câu trả lời có trích dẫn đã được kiểm chứng; lịch sử hỏi đáp mở lại được.

**Lưu ý**

- Từ chối câu hỏi mang tính phán quyết ("có nên tuyển không", "CV có đạt không", "có phù hợp không"), thay bằng đối chiếu từng yêu cầu với nội dung CV, không kết luận.
- Không trả lời hay suy đoán tuổi, giới tính, thông tin nhân thân.
- Phía Ứng viên, câu hỏi dạng "nên bổ sung kỹ năng gì" được chuyển tới FR-U05.
- Khi đơn đang ẩn danh (H16), trích dẫn hiển thị cho HR được che thông tin nhân thân.

**Phạm vi**

Một CV cho mỗi cuộc hỏi đáp; không hỏi trên danh sách CV, không truy vấn thống kê, không thao tác thay người dùng.

### 15. FR-H13 — Tạo câu hỏi phỏng vấn riêng cho từng CV

- **Phase:** 2.5 · **Nhánh:** `feat/fr-h13-interview-questions` · **Phía sử dụng:** HR
- **Đặc tả chi tiết:** `docs/features/HR/H13/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-H09
- **Màn hình (UI_GUIDE mục 7):** Tab Câu hỏi phỏng vấn ở ★`/hr/applications/:id`.

**Mục đích**

Giúp HR chuẩn bị phỏng vấn đúng trọng tâm, đào sâu điểm mạnh và khoảng trống của từng ứng viên.

**Người dùng thao tác**

HR mở hồ sơ đơn (H09) → tab Câu hỏi phỏng vấn → bấm Tạo câu hỏi → chọn số lượng → xem, sửa, xoá, thêm câu hỏi → Lưu.

**Hệ thống**

Dựng ngữ cảnh qua K1 gồm CV đã trích xuất, mô tả/yêu cầu Job, rubric và kết quả chấm (nếu có); gọi AI theo K3. Đoạn CV liên quan của mỗi câu hỏi được kiểm tra qua K2. Lưu bộ câu hỏi theo đơn kèm siêu dữ liệu K4. Chỉ tạo được khi CV đã trích xuất thành công.

**AI**

Sinh câu hỏi gắn với từng tiêu chí rubric; mỗi câu kèm lý do đặt câu hỏi và đoạn CV liên quan (ví dụ kiểm chứng một dự án, làm rõ một kỹ năng còn thiếu minh chứng).

**Kết quả**

Danh sách câu hỏi phỏng vấn theo tiêu chí, HR chỉnh sửa và lưu lại cho buổi phỏng vấn.

**Lưu ý**

Câu hỏi là dữ liệu nội bộ, không gửi cho ứng viên và không đưa vào ngữ cảnh của C07. Không sinh câu hỏi về thông tin cá nhân nhạy cảm. Không đánh giá hay dự đoán kết quả phỏng vấn.

**Phạm vi**

Tạo và lưu câu hỏi cho từng đơn.

### 16. FR-H14 — So sánh song song 2–3 ứng viên

- **Phase:** 2.5 · **Nhánh:** `feat/fr-h14-compare` · **Phía sử dụng:** HR
- **Đặc tả chi tiết:** `docs/features/HR/H14/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-H05, FR-H06, FR-H09
- **Màn hình (UI_GUIDE mục 7):** Chọn đơn ở tab Ứng viên của `/hr/jobs/:id/edit` → ★`/hr/jobs/:id/compare`.

**Mục đích**

Giúp HR đối chiếu trực quan các ứng viên của cùng một Job trên một màn hình.

**Người dùng thao tác**

Tại danh sách xếp hạng của một Job, HR tick chọn 2–3 đơn → bấm So sánh → xem bảng cột song song → bấm Mở hồ sơ hoặc Mời phỏng vấn trên cột của ứng viên muốn chọn.

**Hệ thống**

Hiển thị theo hàng: tổng điểm, điểm từng tiêu chí, số năm kinh nghiệm (C05), kỹ năng, học vấn, tóm tắt điểm mạnh/yếu từ FR-H06, câu trả lời sàng lọc (H10); mở được evidence ở từng ô. Tối đa 3 đơn, chỉ các đơn cùng Job, cùng chu kỳ tuyển dụng và đã chấm xong. Nút Mời phỏng vấn mở đúng hộp thoại của FR-H07.

**AI**

Không chấm lại. Dùng kết quả đã có ở FR-H04, FR-H05, FR-H06.

**Kết quả**

Bảng so sánh song song và nút hành động dẫn sang luồng của FR-H07.

**Lưu ý**

Không đánh dấu "người thắng", không tô màu đỏ–vàng–xanh theo ngưỡng; chỉ làm nổi bật khác biệt bằng màu trung tính. Quyết định vẫn do HR bấm trên từng đơn.

**Phạm vi**

So sánh trong phạm vi một Job; chưa so sánh giữa các Job.

### 17. FR-H15 — Kho ứng viên (ghi chú, AI tóm tắt, bộ lọc)

- **Phase:** 2.5 · **Nhánh:** `feat/fr-h15-talent-pool` · **Phía sử dụng:** HR
- **Đặc tả chi tiết:** `docs/features/HR/H15/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-H09, FR-C05, FR-U09
- **Màn hình (UI_GUIDE mục 7):** ★`/hr/talent-pools`, ★`/hr/talent-pools/:id`; nút "Thêm vào kho" ở ★`/hr/applications/:id`.

**Mục đích**

Giúp HR giữ lại ứng viên tiềm năng đã nộp đơn để dùng cho các đợt tuyển sau, tổ chức theo từng mục đích và tìm lại nhanh.

**Người dùng thao tác**

- HR tạo một hoặc nhiều kho (ví dụ "Backend tiềm năng", "Thực tập 2027").
- Từ hồ sơ đơn (H09) bấm Thêm vào kho → chọn kho → nhập ghi chú → tuỳ chọn bấm AI tóm tắt → Lưu.
- Trang Kho ứng viên: chọn kho → lọc theo ngành nghề, chức danh, khu vực, số năm kinh nghiệm, kỹ năng, từ khoá trong ghi chú → sắp xếp → mở hồ sơ đơn.

**Hệ thống**

- Kho thuộc công ty. Mỗi mục trong kho trỏ tới một đơn ứng tuyển (qua đó tới CV, Job); một đơn có thể thuộc nhiều kho, không trùng trong cùng một kho.
- Chỉ thêm được đơn mà ứng viên đã đồng ý lưu hồ sơ (U09); kiểm tra ở backend.
- Bộ lọc là truy vấn xác định trên dữ liệu chuẩn hoá của C05. Mục thiếu dữ liệu cho điều kiện đang lọc hiển thị nhãn "thiếu dữ liệu", không bị loại âm thầm. Kết quả có phân trang và giữ thứ tự sắp xếp HR chọn.
- Khi ứng viên rút đồng ý (U08), mục bị xoá khỏi mọi kho kèm ghi chú và bản tóm tắt.
- Bản tóm tắt AI được dựng qua K1, gọi theo K3 và lưu kèm siêu dữ liệu K4.

**AI**

Tóm tắt ngắn gọn ứng viên (chuyên môn chính, kinh nghiệm, kỹ năng nổi bật) dựa trên CV đã trích xuất.

**Kết quả**

Các kho ứng viên của công ty, mỗi mục có ghi chú và (nếu HR chọn) bản tóm tắt AI; lọc được theo thuộc tính chuyên môn.

**Lưu ý**

Kho và ghi chú là dữ liệu nội bộ, ứng viên không xem được. Bản tóm tắt chỉ mô tả, không chấm hay xếp loại. Không lọc theo tuổi, giới tính, ảnh hay thông tin nhân thân. Bộ lọc sẵn có ở danh sách xếp hạng theo Job giữ nguyên, không thay đổi.

**Phạm vi**

Chỉ gồm đơn đã nộp vào công ty và có đồng ý lưu hồ sơ; không có kho CV công khai; không liên hệ hàng loạt.

### 18. FR-U11 — Thống kê hoạt động ứng tuyển cá nhân

- **Phase:** 2.6 · **Nhánh:** `feat/fr-u11-stats` · **Phía sử dụng:** Ứng viên
- **Đặc tả chi tiết:** `docs/features/UV/U11/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-U03
- **Màn hình (UI_GUIDE mục 7):** `/candidate/dashboard` (gộp vào Bảng tin hiện có).

**Mục đích**

Cho ứng viên cái nhìn tổng quan về hoạt động ứng tuyển của chính mình, thay vì mở từng đơn.

**Người dùng thao tác**

Ứng viên vào trang Tổng quan → xem tổng số đơn đã nộp, phân bố theo 5 trạng thái, tỷ lệ được mời phỏng vấn, biểu đồ số đơn theo tuần/tháng.

**Hệ thống**

Truy vấn tổng hợp trên đơn ứng tuyển và lịch sử trạng thái của ứng viên đang đăng nhập. Tỷ lệ được mời phỏng vấn tính theo việc đơn đã từng đạt INTERVIEW_INVITED trong lịch sử, nhất quán với FR-H08. Dùng lại thành phần biểu đồ của Dashboard HR (FR-H08) và gộp vào trang tổng quan hiện có của ứng viên.

**AI**

Không dùng AI.

**Kết quả**

Dashboard cá nhân với số đơn theo trạng thái và biểu đồ theo thời gian.

**Lưu ý**

Chỉ hiển thị dữ liệu của chính ứng viên, không so sánh hay xếp hạng với ứng viên khác.

**Phạm vi**

Thống kê trên đơn của chính ứng viên; chưa so sánh với dữ liệu thị trường.

### 19. FR-U12 — Tạo CV (CV builder)

- **Phase:** 2.6 · **Nhánh:** `feat/fr-u12-cv-builder` · **Phía sử dụng:** Ứng viên
- **Đặc tả chi tiết:** `docs/features/UV/U12/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-U01, FR-C04
- **Màn hình (UI_GUIDE mục 7):** ★`/candidate/resumes/new`, ★`/candidate/resumes/:id/edit`.

**Mục đích**

Cho ứng viên chưa có CV, hoặc CV chưa chỉn chu, một công cụ tạo CV ngay trong hệ thống.

**Người dùng thao tác**

- CV của tôi → Tạo CV → điền form theo khối (liên hệ, học vấn, kinh nghiệm, kỹ năng, chứng chỉ, dự án); tuỳ chọn điền sẵn từ một CV đã trích xuất → tuỳ chọn bấm Gợi ý diễn đạt cho từng đoạn → chọn mẫu trình bày → Xem trước → Lưu.
- Sửa CV đã tạo: mở lại form, chỉnh sửa, lưu thành một phiên bản CV mới.

**Hệ thống**

- Form theo đúng cấu trúc CV đã trích xuất của FR-C04. Backend sinh file PDF và lưu như một CV bình thường (FR-U01), dùng chung lưu trữ và giới hạn.
- Ghi thẳng CV đã trích xuất từ dữ liệu form, bỏ bước AI trích xuất; lưu dữ liệu form để sửa lại sau. Gợi ý việc làm (FR-U04), gợi ý cải thiện (FR-U05) và chấm điểm chạy như CV upload.
- Sửa CV tạo phiên bản mới, không ghi đè file cũ, nên đơn đã nộp luôn giữ đúng CV đã nộp.

**AI**

Tuỳ chọn: gợi ý diễn đạt lại một đoạn mô tả kinh nghiệm/dự án cho chuyên nghiệp hơn, gọi theo K3; ứng viên chấp nhận hoặc bỏ từng gợi ý.

**Kết quả**

CV hoàn chỉnh dạng PDF, được lưu và dùng như một CV upload bình thường.

**Lưu ý**

AI chỉ viết lại câu chữ, không thêm kinh nghiệm, kỹ năng, số liệu hay chứng chỉ không có trong nội dung ứng viên nhập. Mẫu CV không có trường ảnh, tuổi, giới tính, tình trạng hôn nhân. Số mẫu trình bày giới hạn ở 2 mẫu.

**Phạm vi**

Tạo và sửa CV tạo từ form; chưa hỗ trợ sửa trực tiếp file PDF/DOCX đã upload.

### 20. FR-U13 — Tìm kiếm việc làm bằng ngôn ngữ tự nhiên

- **Phase:** 2.6 · **Nhánh:** `feat/fr-u13-nl-search` · **Phía sử dụng:** Ứng viên
- **Đặc tả chi tiết:** `docs/features/UV/U13/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-U07, FR-U15, FR-C05
- **Màn hình (UI_GUIDE mục 7):** Ô tìm kiếm chế độ "Tìm bằng mô tả" ở `/candidate`.

**Mục đích**

Ứng viên mô tả công việc mong muốn bằng lời thường và nhận danh sách việc làm liên quan, thay vì tự điền từng ô lọc.

**Người dùng thao tác**

Trên trang Việc làm, chuyển ô tìm kiếm sang chế độ Tìm bằng mô tả → nhập mô tả (ví dụ "content marketing lương trên 15 triệu ở Hà Nội, không yêu cầu kinh nghiệm") → Tìm → xem các điều kiện hệ thống đã hiểu (dạng thẻ, sửa hoặc xoá được) và danh sách việc làm.

**Hệ thống**

- Gọi AI một lần mỗi lượt tìm (K3) để tách điều kiện cứng (khu vực, ngành nghề, lương, hình thức làm việc, ánh xạ theo C05) và phần mô tả còn lại.
- Áp điều kiện cứng bằng đúng truy vấn của U07; phần mô tả còn lại được embedding bằng model của FR-U04 rồi xếp hạng bằng cùng bộ máy "điều kiện cứng + xếp theo embedding" của U15.
- Với mỗi kết quả, hiển thị các điều kiện đã khớp theo cách xác định, không gọi AI giải thích từng kết quả. Sửa một thẻ điều kiện sẽ chuyển sang bộ lọc U07.
- Chỉ dùng được khi đã đăng nhập; có giới hạn số lượt tìm.

**AI**

Hiểu yêu cầu tự do và tách thành điều kiện lọc có cấu trúc.

**Kết quả**

Danh sách việc làm sắp theo mức độ liên quan, kèm các điều kiện hệ thống đã hiểu để ứng viên kiểm tra và sửa.

**Lưu ý**

Không gán nhãn "Phù hợp/Không phù hợp", không dùng ngưỡng tương đồng để loại việc làm. Luôn hiển thị lại cách hệ thống hiểu câu truy vấn.

**Phạm vi**

Tìm trong việc làm công khai đang OPEN. Khác gợi ý việc làm theo CV (FR-U04): đây là tìm kiếm chủ động theo mô tả ứng viên nhập.

### 21. FR-H16 — Chế độ ẩn danh khi duyệt hồ sơ

- **Phase:** 2.6 · **Nhánh:** `feat/fr-h16-blind` · **Phía sử dụng:** HR
- **Đặc tả chi tiết:** `docs/features/HR/H16/REQUIREMENT.md`, `UI.md`
- **Phụ thuộc:** FR-H09, FR-H14, FR-C08
- **Màn hình (UI_GUIDE mục 7):** Bật ở tab Thông tin của `/hr/jobs/:id/edit`; áp lên tab Ứng viên, ★`/hr/applications/:id`, ★`/hr/jobs/:id/compare`, tab Hỏi đáp CV.

**Mục đích**

Giảm thiên lệch vô thức, giúp HR tập trung vào năng lực chuyên môn khi duyệt hồ sơ.

**Người dùng thao tác**

HR bật Ẩn danh cho một Job → ở danh sách xếp hạng, hồ sơ đơn (H09), bảng so sánh (H14) và hỏi đáp CV (C08) của Job đó, tên hiển thị dạng "Ứng viên #mã"; email, số điện thoại, địa chỉ, ngày sinh, LinkedIn bị ẩn; không tải được file CV gốc, chỉ xem CV đã trích xuất đã được che.

**Hệ thống**

- Che ở backend khi dựng dữ liệu trả cho HR, bằng một bộ che dùng chung; không che ở frontend.
- Nội dung văn bản (evidence, giải thích, trích dẫn C08, CV đã trích xuất) được che bằng cách thay đúng các giá trị liên hệ đã biết (họ tên, email, số điện thoại, địa chỉ lấy từ phần liên hệ của CV đã trích xuất và hồ sơ ứng viên).
- Tự bỏ ẩn danh cho đơn khi HR gửi giấy mời phỏng vấn. HR có thể tắt chủ động; hệ thống ghi lại người và thời điểm bật/tắt.
- Khi đơn đang ẩn danh: tab Trao đổi hiển thị tên đã che; nút Thêm vào kho chỉ mở sau khi bỏ ẩn danh.

**AI**

Không dùng AI.

**Kết quả**

Hồ sơ ứng viên chỉ còn thông tin chuyên môn trong giai đoạn sàng lọc.

**Lưu ý**

Ẩn danh chỉ áp dụng khi hiển thị; đầu vào chấm điểm AI (FR-H04) giữ nguyên. Cách thay chuỗi chỉ che được giá trị đã trích xuất đúng; thông tin viết khác dạng trong CV hoặc trong nội dung tin nhắn có thể không bị che. Đây là giới hạn đã biết.

**Phạm vi**

Bật theo Job; chưa ẩn danh trong email/tin nhắn gửi ứng viên; không dùng AI nhận diện thông tin nhân thân.

## 5. Ngoài phạm vi (đã chủ động loại, không làm lại)

| Nội dung | Lý do / thay thế |
|---|---|
| Kho CV công khai (HR 1, HR 7, nhóm "chưa ứng tuyển" ở HR 2) | Cần thêm cả một chức năng công khai hồ sơ và đồng ý riêng, rủi ro quyền riêng tư cao. HR chỉ làm việc với CV đã gửi tới công ty. |
| Tìm kiếm ứng viên bằng ngôn ngữ tự nhiên (HR 4) | Tạo ra cách xếp hạng thứ hai theo độ tương đồng, cạnh tranh với xếp hạng theo rubric (FR-H05). |
| Bộ lọc ứng viên là chức năng riêng (HR 1) | Gộp vào Kho ứng viên (H15), chỉ xuất hiện trong kho. Bộ lọc sẵn có ở danh sách xếp hạng theo Job giữ nguyên. |
| Chatbot hỏi trên cả danh sách CV (HR 9) | Ngữ cảnh quá lớn, dễ trộn trích dẫn giữa các CV. Chỉ hỏi trên một CV (C08). |
| Chat thời gian thực (HR 2) | Thay bằng tự tải lại định kỳ, tránh thêm hạ tầng WebSocket. |
| Kênh email nhanh tách riêng (HR 2, UV 2) | Email là tin nhắn trong cuộc trao đổi, được gửi thêm qua email bằng hạ tầng thông báo FR-C03 (C06, C07). |
| Chatbot trả lời "CV có phù hợp không", "nên bổ sung kỹ năng gì" (UV 6) | Câu thứ nhất là phán quyết, thay bằng đối chiếu từng yêu cầu. Câu thứ hai trùng FR-U05, được chuyển sang đó. |
| Không đưa thông tin nhân thân vào chấm AI (HR 11) | Sẽ phải đổi đầu vào chấm và kiểm tra evidence đã hoàn thành ở FR-H04. Ẩn danh chỉ áp dụng khi hiển thị. |
| AI nhận diện thông tin nhân thân (HR 11) | Thay bằng thay chuỗi xác định các giá trị liên hệ đã biết. |
| FR-U04 chỉ gợi ý khi đã có CV trích xuất, hiển thị riêng ở trang Tổng quan | Mở rộng thành U15: gợi ý được cả khi chưa có CV (dựa trên hồ sơ U14), hiển thị ở trang Việc làm và trang Tổng quan, dùng chung bộ máy với U13 để chỉ còn một nguồn gợi ý. |
| AI giải thích lý do phù hợp cho từng kết quả tìm kiếm (UV 3) | Mỗi kết quả tốn một lượt gọi AI. Thay bằng hiển thị điều kiện đã khớp. |
