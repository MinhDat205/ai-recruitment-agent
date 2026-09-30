# FR-C05 — Danh mục dùng chung và chuẩn hoá dữ liệu

> Trạng thái: ĐÃ DUYỆT (30/09/2026).

- Nhóm: Chung
- Tóm tắt: Danh mục ngành nghề, tỉnh/thành cố định; chuẩn hoá Job và CV; backend tự tính số năm kinh nghiệm
- Phụ thuộc: FR-H02, FR-C04
- Nhánh: `feat/fr-c05-catalog` (tách từ `fix/hr-company-onboarding`)
- Mở rộng: FR-H02 (ngành nghề/tỉnh thành theo mã), FR-C04 (schema trích xuất `resume-parse-v2`)

## 1. Mục đích

Hiện `jobs.category`/`jobs.location` là chuỗi tự do (`V1__init_schema.sql:68-69`), nên lọc chỉ là
so chuỗi `ILIKE` (`JobRepository.java:23-24`). C05 tạo nền dữ liệu chuẩn cho U07, U14, U15, H11,
H13, H14, H15:

- Hai danh mục cố định (ngành nghề, tỉnh/thành) có mã ổn định.
- Job lưu ngành nghề, tỉnh/thành theo mã; dữ liệu cũ không khớp được giữ nguyên văn.
- CV trích xuất theo schema v2 có chức danh hiện tại (nguyên văn), ngành nghề và khu vực theo mã.
- Backend tự tính số tháng kinh nghiệm bằng thuật toán xác định. AI không tính.

## 2. Luồng người dùng

**HR — tạo/sửa Job** (`/hr/jobs/new`, `/hr/jobs/:id/edit`)
1. Chọn Ngành nghề và Tỉnh/thành từ danh sách có ô tìm kiếm, thay cho ô nhập tự do.
2. Lưu nháp (DRAFT) được khi còn để trống cả hai.
3. Mở tin (chuyển sang OPEN): hệ thống chặn nếu thiếu mã theo quy tắc R-J3, báo lỗi tiếng Việt.
4. Job cũ có giá trị "chưa chuẩn hoá": form hiện giá trị cũ nguyên văn kèm lời nhắc chọn lại. HR
   vẫn lưu được các sửa đổi khác; chỉ bị chặn khi mở tin.

**Ứng viên — trích xuất lại CV cũ** (`/candidate/profile`, danh sách CV)
1. CV đã trích xuất theo schema v1 (`prompt_version = resume-parse-v1`, trạng thái DONE) có nút
   "Cập nhật dữ liệu trích xuất".
2. Bấm → yêu cầu chạy nền. Trong lúc chờ, CV vẫn DONE, vẫn dùng được để ứng tuyển, dữ liệu v1 vẫn
   xem được.
3. Thành công → dữ liệu v2 thay thế tại chỗ; nút biến mất. Thất bại → dữ liệu v1 giữ nguyên, hiện
   lỗi và cho bấm lại.

**Hệ thống**
- Migration nạp danh mục và chuyển dữ liệu Job cũ sang mã (một lần).
- Job nền tính số tháng kinh nghiệm cho mọi CV đã trích xuất chưa được tính (kể cả v1), không gọi
  LLM.
- CV tải lên mới được trích xuất bằng `resume-parse-v2`.

## 3. Quy tắc nghiệp vụ

### 3.1 Danh mục tỉnh/thành

- **R-P1.** Dùng **34 đơn vị hành chính cấp tỉnh hiện hành** (sau sắp xếp 01/07/2025). Lý do: bộ lọc
  U07/H15 phải khớp địa giới đang có hiệu lực; dữ liệu cũ mang tên tỉnh đã sáp nhập được quy về đơn
  vị mới bằng bảng bí danh (R-M3), không mất thông tin vì giá trị gốc vẫn lưu nguyên văn.
- **R-P2.** Mã là chuỗi chữ do dự án tự đặt, không dùng mã số hành chính nhà nước. Mã **không bao giờ
  đổi hay bị xoá**; chỉ nhãn hiển thị được sửa (qua migration mới).
- **R-P3.** Danh mục KHÔNG có mục "Toàn quốc", "Làm từ xa", "Nước ngoài" — đó không phải tỉnh. Job
  làm từ xa dùng `work_mode = 'REMOTE'` (đã có, `V1__init_schema.sql:71`).
- **R-P4.** Danh sách (thứ tự hiển thị: 6 thành phố trước, 28 tỉnh sau, theo thứ tự chữ cái):

| Mã | Nhãn | Tỉnh/thành cũ nhập vào (bí danh bắt buộc) |
|---|---|---|
| `HA_NOI` | Hà Nội | — |
| `HO_CHI_MINH` | TP. Hồ Chí Minh | Bình Dương, Bà Rịa - Vũng Tàu |
| `HAI_PHONG` | Hải Phòng | Hải Dương |
| `DA_NANG` | Đà Nẵng | Quảng Nam |
| `CAN_THO` | Cần Thơ | Sóc Trăng, Hậu Giang |
| `HUE` | Huế | Thừa Thiên Huế |
| `AN_GIANG` | An Giang | Kiên Giang |
| `BAC_NINH` | Bắc Ninh | Bắc Giang |
| `CA_MAU` | Cà Mau | Bạc Liêu |
| `CAO_BANG` | Cao Bằng | — |
| `DAK_LAK` | Đắk Lắk | Phú Yên |
| `DIEN_BIEN` | Điện Biên | — |
| `DONG_NAI` | Đồng Nai | Bình Phước |
| `DONG_THAP` | Đồng Tháp | Tiền Giang |
| `GIA_LAI` | Gia Lai | Bình Định |
| `HA_TINH` | Hà Tĩnh | — |
| `HUNG_YEN` | Hưng Yên | Thái Bình |
| `KHANH_HOA` | Khánh Hòa | Ninh Thuận |
| `LAI_CHAU` | Lai Châu | — |
| `LAM_DONG` | Lâm Đồng | Đắk Nông, Bình Thuận |
| `LANG_SON` | Lạng Sơn | — |
| `LAO_CAI` | Lào Cai | Yên Bái |
| `NGHE_AN` | Nghệ An | — |
| `NINH_BINH` | Ninh Bình | Hà Nam, Nam Định |
| `PHU_THO` | Phú Thọ | Vĩnh Phúc, Hòa Bình |
| `QUANG_NGAI` | Quảng Ngãi | Kon Tum |
| `QUANG_NINH` | Quảng Ninh | — |
| `QUANG_TRI` | Quảng Trị | Quảng Bình |
| `SON_LA` | Sơn La | — |
| `TAY_NINH` | Tây Ninh | Long An |
| `THAI_NGUYEN` | Thái Nguyên | Bắc Kạn |
| `THANH_HOA` | Thanh Hóa | — |
| `TUYEN_QUANG` | Tuyên Quang | Hà Giang |
| `VINH_LONG` | Vĩnh Long | Bến Tre, Trà Vinh |

  33 nhãn giữ nguyên tên + 30 tên cũ ở cột phải (29 tỉnh đã nhập vào tỉnh khác và Thừa Thiên Huế) = 63 tên tỉnh/thành cũ. Bảng đã
  đối chiếu với Nghị quyết 202/2025/QH15; ghi nguồn này trong comment migration.
- **R-P5.** Bí danh cách viết thường gặp (ngoài 29 tên cũ), đã rà theo R-M4 (không bí danh thừa):
  `HO_CHI_MINH` ← "HCM", "TPHCM", "Sài Gòn", "Saigon", "Ho Chi Minh City", "Bà Rịa Vũng Tàu",
  "Vũng Tàu"; `HA_NOI` ← "Hanoi", "HN"; `DA_NANG` ← "Danang"; `DAK_LAK` ← "Daklak"; `HUE` ←
  "Thừa Thiên - Huế".
  Không cần bí danh (đã khớp qua R-M1/R-M2): "Hồ Chí Minh", "Thành phố Hồ Chí Minh" (→ khoá của
  nhãn "ho chi minh"); "TP.HCM", "TP HCM" (→ khoá "hcm"); biến thể dấu thanh Hòa/Hoà, Hóa/Hoá;
  "Bà Rịa-Vũng Tàu", "Thừa Thiên-Huế" (gạch nối, R-M1b).

### 3.2 Danh mục ngành nghề

- **R-I1.** Danh sách phẳng một cấp, 24 mục. Mã không đổi khi đổi nhãn (như R-P2). Nhãn dùng gạch
  nối ASCII `" - "` (không dùng gạch dài); đầu vào dùng gạch dài hoặc không cách vẫn khớp nhờ R-M1b.
  Bảng bí danh dưới đây đã rà theo R-M4: không khoá nào trỏ tới hai mã, không bí danh thừa.

| Mã | Nhãn | Bí danh |
|---|---|---|
| `IT_SOFTWARE` | Công nghệ thông tin - Phần mềm | Công nghệ thông tin, CNTT, IT, Phần mềm, IT - Phần mềm, Trí tuệ nhân tạo |
| `IT_HARDWARE_NETWORK` | Công nghệ thông tin - Phần cứng, Mạng | IT - Phần cứng, Mạng máy tính |
| `ACCOUNTING_AUDIT` | Kế toán - Kiểm toán | Kế toán, Kiểm toán |
| `FINANCE_BANKING` | Tài chính - Ngân hàng | Tài chính, Ngân hàng |
| `INSURANCE` | Bảo hiểm | — |
| `SALES` | Kinh doanh - Bán hàng | Kinh doanh, Bán hàng, Sales |
| `MARKETING_COMMUNICATIONS` | Marketing - Truyền thông | Marketing, Truyền thông, Quảng cáo |
| `CUSTOMER_SERVICE` | Chăm sóc khách hàng | Dịch vụ khách hàng |
| `HUMAN_RESOURCES` | Nhân sự | Hành chính nhân sự, HR |
| `ADMINISTRATION` | Hành chính - Văn phòng | Hành chính, Văn phòng, Thư ký |
| `LEGAL` | Pháp lý | Luật, Pháp chế |
| `DESIGN_CREATIVE` | Thiết kế - Mỹ thuật | Thiết kế |
| `EDUCATION_TRAINING` | Giáo dục - Đào tạo | Giáo dục, Đào tạo |
| `HEALTHCARE_PHARMA` | Y tế - Dược | Y tế, Dược |
| `ENGINEERING` | Kỹ thuật - Cơ khí - Điện | Cơ khí, Điện - Điện tử |
| `MANUFACTURING` | Sản xuất - Vận hành | Sản xuất |
| `CONSTRUCTION_ARCHITECTURE` | Xây dựng - Kiến trúc | Xây dựng, Kiến trúc |
| `REAL_ESTATE` | Bất động sản | — |
| `LOGISTICS_IMPORT_EXPORT` | Vận tải - Kho vận - Xuất nhập khẩu | Logistics, Xuất nhập khẩu |
| `HOSPITALITY_TOURISM` | Nhà hàng - Khách sạn - Du lịch | Du lịch, Khách sạn |
| `RETAIL_CONSUMER` | Bán lẻ - Hàng tiêu dùng | Bán lẻ |
| `AGRICULTURE` | Nông - Lâm - Ngư nghiệp | Nông nghiệp |
| `MEDIA_PUBLISHING` | Báo chí - Biên tập - Xuất bản | Báo chí, Biên tập |
| `OTHER` | Ngành khác | — |

- **R-I2.** `OTHER` chỉ để HR chọn khi không có mục phù hợp. AI không được trả `OTHER` (không có
  trong danh sách đưa vào prompt; backend coi `OTHER` từ AI là mã lạ → null).

### 3.3 Bộ khớp chuỗi → mã (MỘT hàm dùng chung)

- **R-M1. Chuẩn hoá** `normalize(s)`, gồm hai bước theo thứ tự:
  - **R-M1a** (chữ): null/rỗng → null; tách dấu Unicode (NFD) và bỏ dấu; `đ/Đ` → `d`; chữ thường;
    mọi chuỗi khoảng trắng liên tiếp → một dấu cách; cắt hai đầu.
  - **R-M1b** (gạch nối): đổi `–` (U+2013) và `—` (U+2014) thành `-`; mọi `-` cùng khoảng trắng hai
    bên (có hoặc không) → đúng `" - "`. Ví dụ "Kế toán – Kiểm toán", "Kế toán-Kiểm toán" → "ke toan
    - kiem toan". Bước này xác định, không phải so gần đúng.
- **R-M2. Riêng tỉnh/thành**, sau R-M1 bỏ MỘT tiền tố ở đầu nếu có, theo thứ tự: `"thanh pho "`,
  `"tp. "`, `"tp."`, `"tp "`, `"tinh "`; cắt hai đầu lần nữa. Ví dụ "TP.HCM" → "hcm", "Tỉnh Bình
  Dương" → "binh duong", nhãn "TP. Hồ Chí Minh" → "ho chi minh".
- **R-M3. Khớp**: dựng bảng tra {khoá → mã} từ nhãn ∪ bí danh của đúng danh mục; **khoá của nhãn và
  bí danh đi qua đúng các bước như đầu vào** (danh mục tỉnh: R-M1 + R-M2, kể cả nhãn; danh mục
  ngành: R-M1). Tra khoá của đầu vào: trúng → mã; trượt → null. **Không** so gần đúng, không
  `LIKE '%…%'`, không khoảng cách chuỗi, không tách chuỗi theo dấu phẩy. Chuỗi chứa hai tỉnh ("Hà
  Nội, TP. HCM") → null.
- **R-M4. Tính nhất quán của bảng tra** (kiểm bằng test, vì DB không tự tính được khoá chuẩn hoá):
  - Một khoá chuẩn hoá không được trỏ tới hai mã khác nhau trong cùng danh mục → test đỏ.
  - Bí danh có khoá trùng với nhãn hoặc bí danh khác **cùng mã** là thừa, phải xoá khỏi bảng → test
    cũng đỏ, để bảng gọn.
- **R-M5.** Bộ khớp là **class Java thuần, không phụ thuộc Spring** (không bean, không repository):
  nhận danh sách (nhãn, bí danh, mã) làm đầu vào, trả mã hoặc null. Dùng chung cho: (a) Java
  migration chuyển Job cũ (R-D2), (b) ánh xạ khu vực của CV (R-C3). Không viết lại logic chuẩn hoá
  bằng SQL (`unaccent`…) — hai bản cài đặt sẽ lệch nhau.

### 3.4 Job

- **R-J1. Lưu trữ**: thêm `jobs.category_code`, `jobs.location_code` (nullable, FK tới bảng danh
  mục). Giữ nguyên `jobs.category`, `jobs.location` làm giá trị cũ nguyên văn — không xoá, không
  sửa. Từ C05, API không ghi vào hai cột cũ nữa (Job mới có cột cũ = NULL).
- **R-J2. "Chưa chuẩn hoá"** (suy ra, không thêm cột cờ): `category_code IS NULL AND category IS
  NOT NULL` (tương tự cho location). "Thiếu" = cả mã lẫn giá trị cũ đều NULL.
- **R-J3. Điều kiện mở tin (OPEN)**: có `category_code`; và có `location_code` trừ khi `work_mode =
  'REMOTE'`. DRAFT/PAUSED/CLOSED không đòi hỏi gì.
- **R-J4. Guard áp cho MỌI đường vào/giữ OPEN** (bài học `fix/rubric-guard`). Đối chiếu code hiện
  tại:
  - `create` luôn tạo DRAFT (`JobOwnerService.java:70`) → không cần guard.
  - `changeStatus` sang OPEN từ DRAFT/PAUSED/CLOSED (`JobOwnerService.java:140-165`) → kiểm R-J3,
    cùng chỗ với kiểm rubric.
  - `update` khi Job đang OPEN (`JobOwnerService.java:118`) → chặn nếu trước khi sửa Job **đã thoả**
    R-J3 mà sau khi sửa **không thoả** (xoá mã, hoặc đổi REMOTE → ONSITE/HYBRID khi chưa có tỉnh).
    Job OPEN cũ vốn chưa thoả R-J3 vẫn lưu được các sửa đổi khác (R-J6).
  - Test phải liệt kê đủ các đường trên; thêm đường mới vào OPEN sau này phải đi qua cùng một hàm
    kiểm.
- **R-J5. Lỗi**: HTTP 409, mã `JOB_CATALOG_INCOMPLETE`, thông điệp theo ngữ cảnh:
  - Chuyển sang OPEN: "Cần chọn ngành nghề và tỉnh/thành từ danh mục trước khi mở tin tuyển dụng."
    (REMOTE: "Cần chọn ngành nghề từ danh mục trước khi mở tin tuyển dụng.")
  - `update` Job đang OPEN làm mất điều kiện R-J3: "Tin đang mở phải giữ ngành nghề và tỉnh/thành từ
    danh mục." (REMOTE: "Tin đang mở phải giữ ngành nghề từ danh mục.")
  Mã gửi lên không có trong danh mục → HTTP 400, mã `INVALID_CATALOG_CODE`, "Ngành nghề không có
  trong danh mục." / "Tỉnh/thành không có trong danh mục."
- **R-J6. Job cũ đang OPEN** mà chưa chuẩn hoá: giữ OPEN, không tự đóng/tạm dừng; HR thấy cảnh báo
  (UI.md). Nếu HR tạm dừng rồi mở lại → bị R-J3 chặn.
- **R-J7. Hiển thị**: có mã → nhãn của mã; chưa chuẩn hoá → giá trị cũ nguyên văn; không có tỉnh và
  `work_mode = 'REMOTE'` → "Làm từ xa". Chi tiết từng màn hình: UI.md.
- **R-J8. Tìm kiếm công khai FR-C02** giữ ô nhập tự do: tham số `category`/`location` so `ILIKE`
  với **nhãn của mã HOẶC giá trị cũ**. Bộ lọc theo mã là việc của U07.
- **R-J9. Embedding Job (F1)**: văn bản embed dùng nhãn của mã, không có thì dùng giá trị cũ. Đổi
  `category_code` qua form kích hoạt embed lại (thay cho so sánh `category` hiện ở
  `JobOwnerService.java:122-129`). Migration KHÔNG kích hoạt embed lại hàng loạt; lệch nhỏ về văn
  bản của Job đã migrate được chấp nhận.

### 3.5 Chuyển dữ liệu Job cũ (migration một lần)

- **R-D1.** Với mỗi Job (kể cả đã xoá mềm): `category_code = khớp(category)`,
  `location_code = khớp(location)` theo R-M3. Trượt → mã NULL, cột cũ giữ nguyên → "chưa chuẩn hoá".
- **R-D2.** Chạy đúng một lần, có phiên bản, trong Flyway. Vì R-M5 cấm viết lại bằng SQL, bước này
  là **Java migration của Flyway** (`BaseJavaMigration`, V9):
  - Gọi class bộ khớp thuần của R-M5.
  - Đọc nhãn/bí danh từ các bảng V8 vừa nạp qua JDBC connection của `Context`; **không** khai lại
    danh sách danh mục trong code migration.
  - Có test Testcontainers chứng minh V9 chạy trên Boot 4.1 ngay ở **đợt code đầu tiên**. Không chạy
    được thì dừng lại hỏi, không tự đổi sang cách khác.
- **R-D3.** Migration chỉ ghi log số lượng (khớp/trượt), không ghi nội dung.

### 3.6 Schema trích xuất CV phiên bản 2

- **R-C1.** Prompt mới `resume-parse-v2.st`, `PROMPT_VERSION = "resume-parse-v2"`. Giữ file v1 (lịch
  sử). Phiên bản schema của một bản ghi = `resume_parsed_data.prompt_version`; không thêm cột.
- **R-C2.** Trường mới trong JSON (thêm vào `ResumeParsedPayload`, v1 đọc vào thì là null nhờ
  `@JsonIgnoreProperties`):
  - `currentTitle` — chức danh của vị trí hiện tại, **nguyên văn** trong CV, không dịch, không quy về
    mã. Không có thì null.
  - `industryCode` — MỘT mã từ danh sách ngành nghề đưa trong prompt (trừ `OTHER`). Không chắc thì
    null.
  - `locationText` — địa danh cấp tỉnh/thành **nguyên văn** khi CV ghi rõ nơi ở hoặc nơi muốn làm
    việc. AI không tự quy đổi tên tỉnh cũ sang mới, không đoán từ tên trường/công ty.
- **R-C3. Backend không tin AI**:
  - `industryCode` không có trong danh mục hoặc là `OTHER` → ghi null (cả trong JSON và cột).
  - `locationText` → mã qua bộ khớp R-M3 (xử lý được tên tỉnh cũ). Trượt → mã null, giữ nguyên
    `locationText`.
  - C05 **không** kiểm `currentTitle`/`locationText` có nằm nguyên văn trong `raw_text` (không dùng
    K2). Lý do: ở C05 hai trường này chỉ hiển thị cho chính chủ CV, không làm căn cứ chấm điểm hay
    trích dẫn; `locationText` chỉ đi qua bộ khớp, không khớp thì mã null. Xem mục 6.
- **R-C4. Cột truy vấn** trên `resume_parsed_data`: `industry_code`, `region_code` (nullable, FK).
  Bất biến: `industry_code` bằng `data.industryCode` sau R-C3; cột là nguồn cho lọc (U07/H15).
- **R-C5.** Không thêm trường nhân thân nào (tuổi, giới tính, ngày sinh, ảnh, hôn nhân, tôn giáo…)
  vào schema hay prompt.
- **R-C6.** `buildResumeText` (F1 embed, F2 gợi ý — `CvImprovementOrchestrator.java:147`) thêm
  `currentTitle` (nội dung nguyên văn từ CV). KHÔNG thêm mã ngành, mã khu vực, số tháng kinh nghiệm
  (dữ liệu suy ra, không phải nội dung CV).
- **R-C7. D2/D4 không đổi**: chấm điểm chỉ đọc `raw_text` (`ScoringRunOrchestrator.java:73-76`),
  giải thích chỉ đọc `criterion_scores` (`ScoreExplanationService.java:23-27`).

### 3.7 Số tháng kinh nghiệm (backend, xác định)

- **R-E1. Đầu vào**: `data.experience[]` (có ở cả v1 và v2). Chỉ dùng `startDate`, `endDate`. Không
  dùng học vấn, dự án.
- **R-E2. Mốc tham chiếu** `refMonth` = tháng của thời điểm tính (`experience_computed_at`, múi giờ
  `Asia/Ho_Chi_Minh`). Lưu kèm; không tính lại theo ngày xem.
- **R-E3. Đọc một mốc** (sau khi chuẩn hoá theo **R-M1a**; KHÔNG áp R-M1b vì bước gạch nối sẽ biến
  `01-2020` thành `01 - 2020`) → (năm, tháng) hoặc "không đọc được":
  - `M/YYYY`, `MM/YYYY`, `MM-YYYY`, `MM.YYYY`
  - `YYYY-MM`, `YYYY/MM`
  - `DD/MM/YYYY`, `DD-MM-YYYY` (lấy tháng, năm)
  - `thang M/YYYY`, `thang M nam YYYY`
  - Tên tháng tiếng Anh viết tắt hoặc đầy đủ + năm: `Jan 2020`, `January 2020`
  - Tháng ngoài 1–12, năm 2 chữ số, chỉ có năm (`2019`), chuỗi khác → không đọc được.
- **R-E4. "Đang làm"**: `endDate` (sau R-M1a) thuộc {`hien tai`, `hien nay`, `nay`, `den nay`,
  `present`, `now`, `current`} → `refMonth`. `startDate` là các từ này → không đọc được.
- **R-E5. Một mục được tính** khi đọc được cả hai mốc và `start ≤ end ≤ refMonth`. Ngược lại (thiếu
  `endDate`, mốc không đọc được, bắt đầu sau kết thúc, mốc nằm sau `refMonth`) → **bỏ qua**, không
  quy ước tháng, không tự điền "hiện tại".
- **R-E6. Gộp**: mỗi mục tính là tập các tháng từ start tới end, **gồm cả hai đầu**
  (01/2020–06/2020 = 6 tháng). `experience_months` = số tháng của **hợp** các tập (trùng/lồng nhau
  chỉ tính một lần).
- **R-E7. Lưu**: `experience_months INT NULL`, `experience_entries_counted INT`,
  `experience_entries_skipped INT`, `experience_computed_at TIMESTAMPTZ`. Không có mục nào được tính
  (kể cả danh sách rỗng) → `experience_months = NULL`, **không lưu 0**.
- **R-E8. Quy đổi năm** (backend trả kèm, frontend không tự tính): `BigDecimal.valueOf(months)
  .divide(BigDecimal.valueOf(12), 1, RoundingMode.HALF_UP)`. **Không dùng `double`/`float`** ở bất kỳ
  bước nào — ca 17 và 18 (0,25 và 0,75) nằm đúng biên làm tròn.
- **R-E9. Khi nào tính**: (a) ngay khi ghi kết quả trích xuất v2 (trong cùng transaction `markDone`
  / ghi trích xuất lại); (b) job nền quét bản ghi có `experience_computed_at IS NULL` (toàn bộ CV v1
  hiện có), batch, không gọi LLM, theo mẫu claim bằng UPDATE có điều kiện (CLAUDE.md §3c).
- **R-E10. Ca kiểm thử đơn vị** (hàm thuần; `refMonth = 09/2026` trừ khi ghi khác):

| # | Đầu vào (start – end) | months | năm | counted/skipped |
|---|---|---|---|---|
| 1 | 01/2020 – 06/2020 | 6 | 0.5 | 1/0 |
| 2 | 05/2020 – 05/2020 | 1 | 0.1 | 1/0 |
| 3 | 01/2020 – 12/2020; 01/2021 – 12/2021 (liền kề) | 24 | 2.0 | 2/0 |
| 4 | 01/2020 – 12/2020; 07/2020 – 06/2021 (trùng) | 18 | 1.5 | 2/0 |
| 5 | 01/2019 – 12/2022; 03/2020 – 05/2020 (lồng) | 48 | 4.0 | 2/0 |
| 6 | 03/2024 – "Hiện tại" | 31 | 2.6 | 1/0 |
| 7 | 03/2024 – "Present" | 31 | 2.6 | 1/0 |
| 8 | 2019 – 2021; 01/2022 – 12/2022 | 12 | 1.0 | 1/1 |
| 9 | 2019 – 2021; "abc" – 06/2020 | null | — | 0/2 |
| 10 | (danh sách rỗng) | null | — | 0/0 |
| 11 | 01/2020 – null | null | — | 0/1 |
| 12 | 06/2021 – 01/2021 | null | — | 0/1 |
| 13 | 10/2026 – "Hiện tại" (sau refMonth) | null | — | 0/1 |
| 14 | 01/2026 – 12/2026 (end sau refMonth) | null | — | 0/1 |
| 15 | 13/2020 – 06/2021 | null | — | 0/1 |
| 16 | mỗi dạng ở R-E3 cho 01/2020, kết thúc 01/2020 | 1 | 0.1 | 1/0 |
| 17 | 01/2020 – 03/2020 (3 tháng, biên làm tròn 0.25) | 3 | 0.3 | 1/0 |
| 18 | 01/2020 – 09/2020 (0.75) | 9 | 0.8 | 1/0 |
| 19 | 01/2020 – 01/2021 (13 tháng) | 13 | 1.1 | 1/0 |
| 20 | 09/2026 – "Hiện tại" (đúng refMonth) | 1 | 0.1 | 1/0 |

### 3.8 Trích xuất lại CV v1

- **R-R1. Kích hoạt**: chỉ ứng viên sở hữu CV, qua `POST /api/candidates/resumes/{id}/reparse`.
  Không có đường chạy lệnh cho vận hành. Mỗi lần = 1 lượt gọi LLM (+ 1 lượt embedding nếu là CV
  chính, R-R5).
- **R-R2. Điều kiện**: CV của mình (không thì 404, cùng mẫu `downloadMine`); `parse_status = DONE`
  và `prompt_version = 'resume-parse-v1'` (không thì 409 "CV này đã có dữ liệu trích xuất mới nhất.");
  không có yêu cầu đang PENDING/RUNNING (409 "CV đang được cập nhật dữ liệu trích xuất.").
- **R-R3. Rate limit**: thêm đường dẫn vào nhóm `llm-action` theo userId sẵn có của `RateLimitFilter`
  (`RateLimitFilter.java:46-48, 138-140`).
- **R-R4. Trạng thái lưu riêng**: bảng `resume_reparse_requests` theo mẫu `cv_improvement_requests`
  (V6): `status` PENDING/RUNNING/DONE/FAILED, `error_message` (mã lỗi chuẩn hoá qua
  `FormattedErrorCode`, dùng lại `ResumeParsingErrorCode`), `attempt_count`, `next_attempt_at`,
  `claimed_at`, `requested_at`, `finished_at`. Partial unique index "tối đa một yêu cầu
  PENDING/RUNNING mỗi CV" là chốt chặn thật; kiểm ở service chỉ để trả 409 sớm. Lỗi tạm thời dùng
  cơ chế thử lại có backoff sẵn có (chore/hardening).
- **R-R5. Xử lý nền** (claim → gọi LLM ngoài transaction → ghi trong transaction ngắn):
  - Đầu vào là `raw_text` **đã lưu**, không đọc lại file, không chạy lại TextExtractor.
  - `resumes.parse_status` giữ **DONE** suốt quá trình (CV không biến khỏi form ứng tuyển, chấm điểm
    vẫn tạo được — `ScoringRunService.java:151`).
  - Thành công: **UPDATE tại chỗ** bản ghi `resume_parsed_data` (không INSERT — `resume_id` UNIQUE):
    `data`, `model`, `prompt_version`, `token_usage`, `parsed_at`, `industry_code`, `region_code`,
    các cột kinh nghiệm (tính lại, R-E9a), `embedding = NULL`. **`raw_text` không đổi.** Yêu cầu →
    DONE. Cùng một transaction.
  - Thất bại: yêu cầu → FAILED kèm mã lỗi; `resume_parsed_data` không bị chạm.
- **R-R6. Ảnh hưởng**:
  - `raw_text` không đổi → evidence đã kiểm của mọi lượt chấm cũ vẫn khớp; lượt chấm mới vẫn chấm
    trên cùng văn bản.
  - `scoring_runs`, `criterion_scores`, `score_explanations` không bị đọc/ghi.
  - Embedding CV (F1): đặt NULL để `ResumeEmbeddingScheduler` tính lại (chỉ CV chính). Trong lúc chờ,
    cache gợi ý cũ được giữ (`JobRecommendationCacheService.java:58-60` bỏ qua khi embedding NULL).
  - Gợi ý cải thiện CV (F2) đã sinh giữ nguyên làm lịch sử.

## 4. Dữ liệu & quyền truy cập

**Migration** (số hiện cao nhất: V7)
- `V8__catalogs.sql`: bảng `catalog_industries`, `catalog_provinces` (`code` PK, `label` NOT NULL
  UNIQUE, `sort_order`), `catalog_industry_aliases`, `catalog_province_aliases` (`alias_text`,
  `code` FK); nạp dữ liệu R-P4/P5, R-I1; thêm cột `jobs.category_code`, `jobs.location_code`; cột
  R-C4, R-E7 trên `resume_parsed_data`; bảng `resume_reparse_requests` + partial unique index.
- `V9__…` (Java migration, `BaseJavaMigration`): chuyển dữ liệu Job cũ (R-D1–D3), dùng bộ khớp
  thuần của R-M5, đọc danh mục qua JDBC của `Context`.

**API**
- `GET /api/public/catalogs` — công khai, không cần đăng nhập: `{ industries: [{code, label}], provinces:
  [{code, label}] }` theo `sort_order`. Không trả bí danh.
- Job (HR, `/api/hr/jobs`): request thay `category`/`location` bằng `categoryCode`/`locationCode`.
  Response (HR và công khai) có `categoryCode`, `categoryLabel`, `locationCode`, `locationLabel`, và
  `legacyCategory`, `legacyLocation` (giá trị cũ, chỉ khác null khi chưa chuẩn hoá). Quyền sở hữu
  Job giữ nguyên cơ chế hiện có.
- `GET /api/candidates/resumes/{id}/parsed` (chỉ chủ CV, như hiện tại): thêm `schemaVersion` (1|2),
  `currentTitle`, `industry {code,label}|null`, `location {code,label}|null`, `locationText`,
  `experience {months, years, countedEntries, skippedEntries, referenceMonth}|null` (null khi chưa
  tính; `months` null khi không có mục nào đọc được).
- `GET /api/candidates/resumes`: mỗi CV thêm `schemaVersion` (null khi chưa DONE) và `reparse
  {status, errorMessage}|null` (yêu cầu gần nhất).
- `POST /api/candidates/resumes/{id}/reparse` — CANDIDATE, R-R1–R3; trả 202 + CV.
- HR không có endpoint mới đọc dữ liệu CV ở C05 (HR hiện chỉ tải file gốc; H09/H14/H15 sẽ đọc).

**Seed demo**
- `db/seed/seed-demo-structural.sql` và `db/seed/dev-seed.sql`: ghi `category_code`/`location_code`,
  cột cũ để NULL (seed chạy SAU Flyway nên migration không chuyển được dữ liệu seed). Ánh xạ: "Công
  nghệ thông tin", "Trí tuệ nhân tạo" → `IT_SOFTWARE`; "Kế toán - Kiểm toán" → `ACCOUNTING_AUDIT`;
  "Marketing - Truyền thông" → `MARKETING_COMMUNICATIONS`; "Kinh doanh - Bán hàng" → `SALES`;
  "TP. Hồ Chí Minh" → `HO_CHI_MINH`; "Hà Nội" → `HA_NOI`; "Đà Nẵng" → `DA_NANG`.
- `db/seed/reset-demo-db.sql`: dọn thêm `resume_reparse_requests`.
- **Bước bắt buộc ở đợt cuối khi code — xuất lại `seed-demo-ai-output.sql` bằng v2**:
  1. Nạp demo đầy đủ theo `db/seed/README.md` (dữ liệu v1 hiện có).
  2. Chạy backend với khoá API thật; đăng nhập từng ứng viên demo, gọi `reparse` cho 9 CV (9 lượt
     gọi LLM + embedding cho CV chính).
  3. Kiểm `criterion_scores`/`score_explanations` trước–sau giống hệt (so bằng `md5` trên bản dump
     của hai bảng).
  4. Xuất lại bằng `export-ai-output.ps1` (cập nhật script cho cột mới; KHÔNG đưa
     `resume_reparse_requests` vào dump); cập nhật `db/seed/README.md` mục 6–7.
- Nhánh "chưa có dữ liệu" vẫn demo được: tải một CV mới rồi xem trước khi job nền tính, hoặc CV có
  mục kinh nghiệm không có ngày.

## 5. AI

- **Chỉ** tham gia ở bước trích xuất CV (FR-C04, job nền; trích xuất lại cũng là job nền). Không có
  lời gọi LLM đồng bộ mới.
- Prompt v2 giữ nguyên toàn bộ luật của v1 (không bịa, giữ nguyên văn, giữ ngôn ngữ gốc) và thêm:
  danh sách mã–nhãn ngành nghề (tham số trong file `.st`, không hardcode trong Java); luật "không
  chắc thì null"; `currentTitle`/`locationText` là chuỗi nguyên văn; không quy đổi địa danh.
- AI không tính số năm kinh nghiệm, không gán mã tỉnh/thành, không đánh giá mức phù hợp.
- Output parse qua `BeanOutputConverter`, thử lại 1 lần khi JSON hỏng, như v1.
- Test mock `ChatModel` với default-answer throw (CLAUDE.md §7).

## 6. Ngoài phạm vi

- Màn hình quản trị danh mục; thêm/sửa danh mục ngoài migration.
- Bộ lọc việc làm theo mã, chip lọc, giữ bộ lọc trên URL (FR-U07). Ô tìm kiếm công khai giữ nguyên.
- Bộ lọc/kho ứng viên phía HR (FR-H15); màn hình HR xem dữ liệu CV đã trích xuất (FR-H09).
- `candidate_profiles.location`, `current_title`, `years_experience` (ứng viên tự khai) — thuộc
  FR-U14, C05 không đụng.
- Danh mục cấp quận/huyện, phường/xã; danh mục chức danh; ngành nghề nhiều cấp; nhiều ngành cho một
  Job hoặc một CV.
- Tự động trích xuất lại hàng loạt; đường chạy lệnh cho vận hành.
- Tự chuẩn hoá lại Job/CV khi danh mục đổi sau này.
- Đổi thang tìm kiếm không dấu cho FR-C02.
- Kiểm nguyên văn `currentTitle` bằng K2 xem xét lại ở FR-H15, khi HR bắt đầu lọc theo chức danh.

## 7. Xong khi

Tất cả lệnh sạch: `cd backend && ./mvnw test` (full suite), `cd frontend && npm run build`,
`cd frontend && npm run lint`. Có test cho từng mục:

1. **Bộ khớp** (test đơn vị trên class thuần, không khởi động Spring): dương (nhãn, bí danh, tên tỉnh
   cũ "Bình Dương" → `HO_CHI_MINH`, "TP.HCM", "TP HCM", "Hồ Chí Minh", "Tỉnh Bình Dương",
   "  hà   NỘI ", "Khánh Hoà"/"Khánh Hòa", "Bà Rịa-Vũng Tàu" → `HO_CHI_MINH`; "Kế toán – Kiểm toán",
   "Kế toán-Kiểm toán" → `ACCOUNTING_AUDIT`); âm ("Hà Nội, TP. HCM", "Quận 1", "Ho Chi Min", chuỗi
   rỗng, null). R-M4 trên dữ liệu thật của V8: không khoá nào trỏ tới hai mã; không bí danh thừa;
   đủ 34 tỉnh, 63 tên cũ có trong bảng nhãn ∪ bí danh.
2. **Migration**: Testcontainers, chạy trên Boot 4.1 thật (đợt code đầu tiên, R-D2); dữ liệu trước
   V9 gồm Job khớp, Job trượt, Job xoá mềm → mã đúng, cột cũ không đổi.
3. **Guard OPEN**: DRAFT/PAUSED/CLOSED → OPEN thiếu ngành → 409; thiếu tỉnh ONSITE → 409; thiếu tỉnh
   REMOTE → 200; `update` Job đang OPEN xoá mã → 409; đổi REMOTE → ONSITE khi chưa có tỉnh → 409;
   Job OPEN cũ chưa chuẩn hoá sửa tiêu đề → 200 và vẫn OPEN; mã không có trong danh mục → 400.
4. **Tìm kiếm C02**: tìm `location=Hồ Chí Minh` ra cả Job có `HO_CHI_MINH` lẫn Job chưa chuẩn hoá
   mang "Quận 1, Hồ Chí Minh".
5. **Schema v2**: `industryCode` lạ/`OTHER` → null; `locationText` "Bình Dương" → `HO_CHI_MINH`;
   `locationText` không khớp → mã null, chuỗi giữ nguyên; bản ghi v1 đọc được, trường mới null.
6. **Kinh nghiệm**: 20 ca ở R-E10; job nền tính cho bản ghi v1 và không gọi `ChatModel`/
   `EmbeddingModel`; kết quả null không bị lưu thành 0.
7. **Trích xuất lại**: 404 CV người khác; 409 CV v2, CV chưa DONE, yêu cầu đang chạy; race hai yêu
   cầu → DB chặn một; thành công → `raw_text` byte-by-byte không đổi, `embedding` NULL,
   `parse_status` DONE suốt quá trình; thất bại → `data` v1 giữ nguyên; **`criterion_scores` (điểm,
   evidence) và `score_explanations` của lượt chấm cũ không đổi**.
8. **RBAC**: HR gọi `reparse` → 403; khách gọi `GET /api/public/catalogs` → 200.
9. **Bổ sung theo spec-review**:
   - R-J9: đổi `category_code` qua `update` → dòng `job_embeddings` của Job bị xoá (cơ chế hiện có
     `JobOwnerService.update` → `jobEmbeddingRepository.deleteByJobId`) để scheduler embed lại; không
     đổi `category_code` (và không đổi tiêu đề/mô tả) → dòng `job_embeddings` còn nguyên.
   - R-C6: `buildResumeText` với payload v2 có `currentTitle`; không chứa mã ngành, mã khu vực, số
     tháng kinh nghiệm (khẳng định bằng chuỗi cụ thể, ví dụ không có `IT_SOFTWARE`, `HO_CHI_MINH`).
   - R-C4: sau `markDone` và sau trích xuất lại, `industry_code` luôn bằng `data.industryCode`, kể
     cả khi AI trả mã lạ (cả hai null).
   - `GET /api/public/catalogs`: đủ 34 tỉnh và 24 ngành, đúng thứ tự `sort_order`, JSON không có trường bí
     danh.
   - `legacyCategory`/`legacyLocation`: khác null khi Job chưa chuẩn hoá; null khi đã có mã; null
     khi thiếu hẳn.
10. **Soát tay** ở khổ desktop và 375px (ghi kết quả vào walkthrough):
    - Combobox điều khiển được bằng ↑/↓, Enter, Esc; gõ "ho chi minh" (không dấu) ra
      "TP. Hồ Chí Minh".
    - Job cũ chưa chuẩn hoá hiện dòng "Giá trị cũ" dưới ô tương ứng.
    - Bấm "Mở tin" (DRAFT) và "Mở lại" (PAUSED) khi thiếu mã → hiện đúng thông điệp 409.
    - CV v1 hiện nút "Cập nhật dữ liệu trích xuất"; sau khi thành công, nút tự biến mất.
11. Seed demo nạp sạch theo `db/seed/README.md`; `SELECT count(*) FROM jobs WHERE deleted_at IS NULL
   AND category_code IS NULL` = 0 trên dữ liệu demo; dump AI output đã là `resume-parse-v2`.
12. Skill `srs-guard` không báo vi phạm.

## 8. AI hay làm sai (dành cho người code)

- Viết bộ chuẩn hoá lần hai bằng SQL (`unaccent`, `ILIKE '%…%'`) trong migration thay vì dùng một hàm
  Java (R-M5).
- "Cho tiện" so gần đúng/tách dấu phẩy khi khớp, biến "Quận 1, Hồ Chí Minh" thành `HO_CHI_MINH`.
- Thêm mục "Toàn quốc"/"Làm từ xa" vào danh mục tỉnh.
- Chỉ đặt guard OPEN ở `changeStatus` mà quên `update` khi đang OPEN; hoặc chỉ ẩn nút ở frontend.
- Xoá hay ghi đè cột `jobs.category`/`jobs.location` cũ trong migration.
- Tin mã AI trả về; để AI tự quy đổi tên tỉnh cũ; để AI trả `OTHER`.
- Để AI tính số năm kinh nghiệm, hoặc frontend tự quy đổi tháng → năm.
- Lưu `0` khi không đọc được mốc nào; tự điền "hiện tại" khi thiếu `endDate`; quy ước tháng 1/12
  cho mốc chỉ có năm; dùng ngày xem thay cho `experience_computed_at`.
- Trích xuất lại bằng cách đặt `parse_status = PENDING` (CV biến khỏi form ứng tuyển, chấm điểm bị
  chặn) hoặc INSERT bản ghi mới (vỡ UNIQUE `resume_id`).
- Đọc lại file và ghi đè `raw_text` khi trích xuất lại → evidence cũ không còn kiểm chứng được.
- Quên `embedding = NULL` sau trích xuất lại → gợi ý việc làm dựa trên dữ liệu cũ.
- Giữ transaction mở quanh lời gọi LLM khi trích xuất lại.
- Ghi `e.getMessage()` hoặc output thô LLM vào `error_message`.
- Thêm trường nhân thân vào schema v2 "vì CV có ghi".
- Quên thêm đường dẫn `reparse` vào `RateLimitFilter`.
- Cho bộ khớp phụ thuộc Spring (inject repository) khiến Java migration không dùng được; hoặc chép
  danh sách danh mục vào code V9 thay vì đọc từ bảng qua JDBC của `Context`.
- Dựng bảng tra mà quên cho nhãn tỉnh đi qua R-M2 ("TP. Hồ Chí Minh" không khớp "Thành phố Hồ Chí
  Minh").
- Áp R-M1b (gạch nối) khi đọc mốc thời gian, làm hỏng dạng `MM-YYYY`.
- Quy đổi tháng → năm bằng `double` rồi `Math.round`, sai ở biên 0,25/0,75.
- Tự thêm phép kiểm nguyên văn `currentTitle` bằng một bản sao logic của K2.
