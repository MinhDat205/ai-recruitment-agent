# FR-U07 — Bộ lọc tìm việc nâng cao

> Trạng thái: ĐÃ DUYỆT (02/10/2026).

- Nhóm: Ứng viên
- Tóm tắt: Lọc theo ngành nghề, khu vực, lương, hình thức, thời gian đăng; giữ bộ lọc trên URL
- Phụ thuộc: FR-C02, FR-C05
- Nhánh: `feat/fr-u07-job-filter`
- Mở rộng: FR-C02 (tham số tìm kiếm công khai, trang danh sách/chi tiết Job), FR-C05 (tái dùng danh mục ngành nghề/tỉnh thành, `CatalogCombobox`, nhãn "Chưa chuẩn hoá")

## 0. Đã kiểm trước khi viết đặc tả (CLAUDE.md §6 — đọc code trước khi đoán)

- **(a) `published_at` có được ghi khi mở tin, có bị ghi đè khi mở lại không?** Có.
  [`JobOwnerService.java:178-180`](../../../../backend/src/main/java/com/recruitment/job/JobOwnerService.java):
  `if (newStatus == JobStatus.OPEN && job.getPublishedAt() == null) { job.setPublishedAt(Instant.now()); }`
  — chỉ ghi lần đầu mở tin (DRAFT/PAUSED/CLOSED → OPEN), **không ghi đè** ở các lần mở lại sau đó.
  Không có đường nào khác ghi `published_at`. Kết luận: dùng được `COALESCE(published_at, created_at)`
  làm "thời gian đăng" (R-T1) — `published_at` ổn định, không nhảy mỗi lần HR tạm dừng rồi mở lại.
- **(b) Có cho nhập `salary_currency` khác VND không?** Có, về mặt dữ liệu. `JobRequest.java:20`:
  `@Size(min = 3, max = 3) String salaryCurrency` — chỉ ép đúng 3 ký tự, không ép danh sách đóng
  (không `@Pattern`, không enum). `HrJobCreatePage.tsx:363`/`HrJobEditPage.tsx:317` là `<Input>` tự do,
  placeholder "VND" chỉ là gợi ý. `JobOwnerService.java:268` mặc định `"VND"` khi HR để trống, nhưng
  không chặn HR tự gõ mã khác. Kết luận: R-S5 phải xử lý tin tiền tệ khác VND tường minh, không giả
  định mọi tin đều VND.
- **(c) Có test/tài liệu nào phụ thuộc 6 job demo ở DRAFT không?** Có hai nơi, cả hai phải cập nhật ở
  đợt seed (đợt 5):
  - `db/seed/README.md` mục 6 (dòng 143-173): bảng số dòng ghi cứng `job_recommendations = 0` và giải
    thích "6 job... ở trạng thái DRAFT... nên khi backend đang chạy, gợi ý luôn về 0" — số này sẽ SAI
    sau khi R-D1 (đợt 5) chuyển 6 job sang OPEN, bắt buộc viết lại đoạn này.
  - `docs/walkthrough/chore-seed-demo.md` dòng 41-48 mô tả **quy trình một lần đã xảy ra trong quá
    khứ** (tạo job DRAFT ở bước 2, HR tự mở OPEN qua UI ở bước 3) để sinh ra `seed-demo-ai-output.sql`
    — tài liệu lịch sử, **không sửa** (walkthrough chỉ ghi lại việc đã xảy ra, không mô tả trạng thái
    hiện tại của file SQL). Không tìm thấy test backend nào assert `jobs.status = 'DRAFT'` trên dữ liệu
    seed.
  - `docs/ROADMAP.md` (nợ kỹ thuật của `feat/fr-c05-catalog`) đã ghi sẵn hướng sửa đúng như R-D1; mục
    đó được tick xong ở đợt cuối của nhánh này (không sửa ROADMAP ở bước viết đặc tả).

## 1. Mục đích

Giúp ứng viên thu hẹp nhanh danh sách tin tuyển dụng công khai (FR-C02) bằng nhiều điều kiện kết hợp
cùng lúc, thay vì chỉ có một ô tìm kiếm tự do. Tái dùng danh mục ngành nghề/tỉnh thành đã chuẩn hoá ở
FR-C05 để lọc theo **mã**, chính xác hơn so khớp chuỗi.

## 2. Luồng người dùng

**Ứng viên (đã đăng nhập hoặc khách)** — `/` (khách/ứng viên) và `/candidate` (ứng viên đã đăng nhập)
1. Mở trang Việc làm → thấy thanh lọc: từ khoá, ngành nghề (combobox danh mục), tỉnh/thành (combobox
   danh mục), khoảng lương (triệu VNĐ), hình thức làm việc (chọn nhiều), thời gian đăng (24 giờ/7
   ngày/30 ngày/mọi thời điểm), sắp xếp (mới nhất/lương cao nhất).
2. Chọn một hoặc nhiều điều kiện → danh sách lọc ngay, URL cập nhật theo, trang về lại trang 1.
3. Có thể dán/chia sẻ URL đã lọc; mở lại URL đó → thanh lọc tự điền đúng theo URL.
4. Bấm "Xoá bộ lọc" → về trạng thái mặc định (không điều kiện, sắp xếp Mới nhất).
5. Mở chi tiết một tin (`/jobs/:id`) → nếu đang đăng nhập là ứng viên, vẫn thấy thanh điều hướng của
   khu vực ứng viên (không rơi về điều hướng khách).

**Hệ thống**
- Đọc dữ liệu Job công khai (FR-C02: `status='OPEN'`, chưa xoá mềm, còn hạn hoặc không có hạn), dùng
  danh mục FR-C05 để validate mã và hiển thị nhãn.
- Kết hợp mọi điều kiện đang áp dụng bằng AND (trừ quy tắc riêng ở R-N cho mã NULL).
- Tin thiếu/chưa chuẩn hoá hiển thị kèm nhãn, không bị loại âm thầm (nguyên tắc SRS mục 2).

## 3. Quy tắc nghiệp vụ

### 3.1 Tham số lọc và validate (R-F)

- **R-F1.** Giữ nguyên ba tham số cũ `keyword`, `category`, `location` và hành vi `ILIKE` hiện có
  (`JobRepository.java:19-56`) — API cũ không đổi hành vi, test cũ không sửa. Frontend mới (R-U, mục
  3.8) **không gửi** `category`/`location` nữa, chỉ gửi `categoryCode`/`locationCode`.
- **R-F2.** Thêm tham số mới: `categoryCode`, `locationCode`, `salaryMin`, `salaryMax`, `hideUnlisted`,
  `workMode` (lặp tham số, 0..nhiều giá trị), `postedWithin`, `sort`. Tham số mới **không ảnh hưởng**
  tới truy vấn khi không truyền (giữ hành vi mặc định hiện có cho các URL cũ không có tham số mới).
- **R-F3.** Mọi điều kiện lọc đang truyền kết hợp bằng **AND**: một tin chỉ vào kết quả khi thoả hết
  các điều kiện đang áp dụng (ngoại lệ R-N, R-S4 nêu rõ bên dưới).
- **R-F4.** `categoryCode`/`locationCode` không có trong danh mục → **400 `INVALID_CATALOG_CODE`**,
  tái dùng nguyên `InvalidCatalogCodeException`/`CatalogRegistry.isIndustry`/`isProvince`
  (`backend/src/main/java/com/recruitment/common/exception/InvalidCatalogCodeException.java`,
  `backend/src/main/java/com/recruitment/catalog/CatalogRegistry.java`) — không viết lại validate.
- **R-F5.** `workMode` có giá trị ngoài tập `{ONSITE, HYBRID, REMOTE}`, hoặc `sort` ngoài
  `{NEWEST, SALARY_DESC}`, hoặc `postedWithin` ngoài `{LAST_24H, LAST_7D, LAST_30D}` → **400
  `INVALID_JOB_FILTER`** (exception mới, cùng khuôn `InvalidCatalogCodeException`: một class, nhiều
  factory method theo từng loại lỗi, message tiếng Việt cố định).
- **R-F6.** `page`/`size` giữ nguyên hành vi clamp hiện có (`JobPublicService.java:83-92`).

### 3.2 Ngành nghề / tỉnh thành — mã NULL vẫn hiện (R-N)

- **R-N1.** Khi `categoryCode` được truyền: điều kiện khớp ngành là
  `job.category_code = :categoryCode OR job.category_code IS NULL` (không phải so bằng tuyệt đối).
  Lý do: `category_code IS NULL` nghĩa là hệ thống không xác định được tin này thuộc ngành nào (C05
  R-J2) — loại nó khỏi kết quả lọc là một dạng "âm thầm loại" dữ liệu thiếu, vi phạm nguyên tắc SRS
  mục 2 ("Dữ liệu thiếu phải hiển thị rõ là thiếu, không âm thầm loại"). Áp dụng AND với các điều kiện
  lọc khác đang bật (R-F3) — không loại trừ các điều kiện đó.
- **R-N2.** Tương tự R-N1 cho `locationCode`:
  `job.location_code = :locationCode OR job.location_code IS NULL`.
- **R-N3.** Tin lọt vào kết quả theo R-N1/R-N2 nhờ `category_code`/`location_code = NULL` **không
  tách nhóm riêng, không đổi vị trí sắp xếp** — xếp lẫn theo đúng thứ tự sắp xếp đang chọn (R-O), chỉ
  khác ở chỗ card hiển thị nhãn "Chưa chuẩn hoá" (tái dùng `UnnormalizedBadge`
  — `features/jobs/UnnormalizedBadge.tsx:3-9`, hiện chỉ dùng ở màn hình HR, dòng 1 ghi "CHỈ phía HR"
  nhưng đó là quy ước bằng lời, component không có rào kỹ thuật nào chặn import ở `JobCard`; và
  `isCategoryUnnormalized`/`isLocationUnnormalized` — `features/jobs/catalogDisplay.ts:35-41`). C05
  UI.md mục 10 đã cố ý **không** hiện nhãn này ở phía công khai; U07 **bật lại** cho đúng kết quả lọc
  (chỉ hiện khi có lọc theo ngành/tỉnh đang áp dụng và tin đó rơi vào nhánh "chưa chuẩn hoá" — không
  hiện tràn lan ở danh sách không lọc, giữ đúng phạm vi C05 đã chốt cho trường hợp không lọc).
- **R-N4.** Tin "thiếu hẳn" (cả mã và giá trị cũ đều NULL, C05 R-J2) cũng lọt qua R-N1/R-N2.
  **Khi đang lọc theo ngành hoặc tỉnh** (đúng field đang thiếu): hiện nhãn "Chưa chuẩn hoá"
  (`UnnormalizedBadge`, R-N3) **không kèm dòng "Giá trị cũ"** (không có gì để hiện) — để người dùng
  hiểu vì sao tin này xuất hiện trong kết quả dù trông như không khớp field đó, tránh tưởng nhầm là
  lỗi hệ thống. **Khi không lọc theo ngành/tỉnh**: giữ đúng hành vi sẵn có của `JobCard` (ẩn chip đó
  hoàn toàn, C05 UI.md mục 4d) — không hiện nhãn "Chưa chuẩn hoá" tràn lan ở danh sách không lọc,
  nhất quán với R-N3.
- **R-N5.** Khi code: sửa lại comment dòng 1 của `UnnormalizedBadge.tsx` ("Nhan 'Chua chuan hoa'...
  CHI phia HR") cho khớp thực tế — từ đợt này component được `JobCard` (phía công khai/ứng viên)
  dùng lại, không còn "CHỈ phía HR". Không đổi phần code/markup của component (đã đúng token `m3-*`
  sẵn, R-L1 dùng lại y nguyên), chỉ sửa câu comment mô tả phạm vi dùng.

### 3.3 Lọc theo lương (R-S)

- **R-S1.** Đơn vị nhập: **triệu VNĐ** (số nguyên không âm). Backend quy đổi sang VNĐ (× 1.000.000)
  trước khi so với `salary_min`/`salary_max` (đơn vị VNĐ, `NUMERIC(14,2)`).
- **R-S2.** Quy tắc khớp: **chồng lấn khoảng** —
  `(salary_max IS NULL OR salary_max >= :filterMinVnd) AND (salary_min IS NULL OR salary_min <= :filterMaxVnd)`.
  Một đầu bộ lọc để trống coi là không giới hạn phía đó (chỉ truyền `salaryMin` → không chặn trên; chỉ
  truyền `salaryMax` → không chặn dưới).
- **R-S3.** `salaryMin > salaryMax` (khi cả hai cùng truyền), hoặc một trong hai âm → **400
  `INVALID_JOB_FILTER`**. Chặn ở cả UI (disable nút lọc / báo lỗi tại chỗ) và backend (R-F5 chung một
  exception).
- **R-S4. "Thoả thuận"**: tin có **cả** `salary_min` và `salary_max` là NULL. Khi có lọc theo lương
  (`salaryMin` hoặc `salaryMax` được truyền), tin Thoả thuận **vẫn vào kết quả theo mặc định** (không
  bị R-S2 loại — coi như luôn "không giới hạn" cả hai phía), trừ khi `hideUnlisted=true`.
  **`hideUnlisted` là tham số độc lập**, không phụ thuộc `salaryMin`/`salaryMax` có được truyền hay
  không: `hideUnlisted=true` **luôn** loại tin Thoả thuận khỏi kết quả, kể cả khi không truyền
  `salaryMin`/`salaryMax` (ví dụ ứng viên chỉ tick "Ẩn tin không công bố lương" mà không nhập khoảng
  lương nào) — điều kiện thêm vào câu truy vấn là `NOT (hideUnlisted AND salary_min IS NULL AND
  salary_max IS NULL)`, không gộp chung điều kiện với R-S2.
- **R-S5. Tiền tệ khác VND** (xác nhận ở mục 0.b — có thể xảy ra): tin có `salary_currency IS NOT NULL
  AND UPPER(TRIM(salary_currency)) <> 'VND'` — **so sánh không phân biệt hoa/thường và khoảng trắng**
  (HR có thể gõ tay `"vnd"`, `"Vnd"`, `" VND "`... đều phải coi là VND, vì `JobRequest.salaryCurrency`
  không ép chữ hoa hay cắt khoảng trắng ở tầng nhập liệu — mục 0.b) — và không phải Thoả thuận (vì
  Thoả thuận không có số để có tiền tệ) **không bị R-S2 so sánh, luôn hiện** khi có lọc theo lương —
  coi như một nhóm "không so được", tách biệt khỏi Thoả thuận. **Không bị ẩn bởi `hideUnlisted`**
  (checkbox ghi rõ "Ẩn tin không công bố lương" — tin ngoại tệ CÓ công bố lương, chỉ không so được
  theo thang VNĐ đang lọc). Card hiển thị nguyên số + mã tiền tệ như bình thường (`JobCard` đã có
  logic `currency` — `JobCard.tsx:10`), kèm chú thích nhỏ khi có lọc lương đang áp dụng (chữ, không
  màu — xem UI.md).
- **R-S6.** `hideUnlisted=true` chỉ ẩn đúng nhóm Thoả thuận (R-S4), không ẩn nhóm ngoại tệ (R-S5),
  không ẩn tin có lương VND không khớp khoảng lọc (nhóm đó đã bị loại bởi R-S2, không liên quan
  `hideUnlisted`).

### 3.4 Hình thức làm việc (R-W)

- **R-W1.** Lọc theo `work_mode`, chọn nhiều giá trị trong `{ONSITE, HYBRID, REMOTE}`: điều kiện
  `job.work_mode IN (:workModes)` (OR giữa các giá trị đã chọn, AND với các điều kiện lọc khác).
  Không truyền `workMode` → không lọc (mọi hình thức).
- **R-W2.** `employment_type` (loại hợp đồng) **ngoài phạm vi** U07 — không thêm tham số lọc.

### 3.5 Thời gian đăng tin (R-T)

- **R-T1.** "Thời gian đăng" = `COALESCE(published_at, created_at)` — xác nhận `published_at` ổn định
  sau khi mở tin lần đầu (mục 0.a). Dùng chung cho cả lọc `postedWithin` và sắp xếp "Mới nhất" (R-O1).
- **R-T2.** `postedWithin ∈ {LAST_24H, LAST_7D, LAST_30D}`: ngưỡng thời gian **tính ở tầng service
  (Java)** — `Instant since = clock.instant().minus(24|7×24|30×24, ChronoUnit.HOURS)` (dùng `Clock`
  inject được, cùng mẫu `ClockConfig` đã có từ C05, không `Instant.now()` trực tiếp để test điều khiển
  được thời gian) — rồi **truyền vào truy vấn dưới dạng tham số** (`:sinceTimestamp`), điều kiện
  `COALESCE(published_at, created_at) >= :sinceTimestamp`. **Không viết `NOW() - INTERVAL '...'` trong
  SQL** — ngưỡng phải do Java tính để test tự dựng được `Clock` cố định, không phụ thuộc đồng hồ thật
  của Postgres lúc chạy. Không truyền `postedWithin` (hoặc giá trị ngầm định "mọi thời điểm" ở frontend
  không gửi tham số) → không lọc theo thời gian (không bind `:sinceTimestamp`).

### 3.6 Sắp xếp (R-O)

- **R-O1. `sort=NEWEST`** (mặc định khi không truyền `sort`):
  `ORDER BY COALESCE(published_at, created_at) DESC, id DESC`.
- **R-O2. `sort=SALARY_DESC`**:
  `ORDER BY COALESCE(salary_max, salary_min) DESC NULLS LAST, COALESCE(published_at, created_at) DESC, id DESC`.
  Tin Thoả thuận (cả hai cột NULL) có `COALESCE(salary_max, salary_min) IS NULL` → `NULLS LAST` đẩy
  xuống cuối. Tin ngoại tệ (R-S5) **không loại khỏi sort** — vẫn sắp theo đúng số lưu trong
  `salary_max`/`salary_min` dù đơn vị tiền tệ khác nhau (xem nợ kỹ thuật mục 10, không quy đổi).
- **R-O3.** Mọi `ORDER BY` kết thúc bằng `id` (tie-break duy nhất) — áp dụng quy ước đã có ở
  `findJobPerformanceForCompany` (`JobRepository.java:136`), khác với `searchPublicJobs` hiện tại
  đang thiếu tie-break (`JobRepository.java:34`) — U07 phải sửa luôn chỗ thiếu này khi viết lại truy
  vấn, không chỉ thêm cho nhánh mới.
- **R-O4.** `ORDER BY` build bằng **lựa chọn một trong hai chuỗi SQL cố định viết sẵn trong Java**
  theo `switch` trên enum `sort`, **tuyệt đối không nối chuỗi từ tham số người dùng vào SQL** (chống
  SQL injection qua tham số sort). Không dùng `Pageable.getSort()` — lý do đã ghi ở
  `JobPublicService.java:40` (native query tự `ORDER BY`, `Sort` của Spring Data sẽ nối sai vị trí).

### 3.7 Truy vấn dùng chung cho U07 và U15 (R-Q)

- **R-Q1.** Các điều kiện "cứng" (R-N1/R-N2, R-S, R-W, R-T, cộng điều kiện cố định `status='OPEN' AND
  deleted_at IS NULL AND (deadline IS NULL OR deadline>=CURRENT_DATE)` đã có) gom vào **một record
  tiêu chí** (ví dụ `PublicJobSearchCriteria`) và **một phương thức truy vấn duy nhất** ở
  `JobRepository`/`JobPublicService`. `keyword`/`category`/`location` (R-F1, chỉ U07 dùng) và
  `sort`/phân trang (chỉ U07 có, U15 tự xếp theo embedding) **tách riêng khỏi** record tiêu chí này,
  để FR-U15 gọi lại đúng phần điều kiện cứng mà không phải mang theo phần tìm kiếm văn bản của U07.
  README mô tả U15: "áp bằng đúng truy vấn của U07; chỉ lấy việc làm OPEN, còn hạn" — U07 phải chuẩn
  bị sẵn điểm nối này, nhưng **không** code phần gọi của U15 (chưa tới lượt, FR-U15 đứng sau U07 ở
  Phase 2.1).
- **R-Q2.** Không lặp lại điều kiện cố định (OPEN/chưa xoá/còn hạn) ở hai nơi khác nhau trong code —
  chỉ định nghĩa một lần trong truy vấn dùng chung của R-Q1.

### 3.8 Trạng thái bộ lọc trên URL (R-U)

- **R-U1.** Toàn bộ điều kiện lọc + sắp xếp + trang hiện tại nằm trên URL (query string), là nguồn sự
  thật duy nhất của trạng thái bộ lọc — không giữ bản sao ở state cục bộ lệch khỏi URL.
- **R-U2. Đồng bộ hai chiều**: đổi URL (back/forward, dán link, sửa tay) → form bộ lọc tự đọc lại và
  hiển thị đúng; đổi form → URL cập nhật theo. Khác hành vi `HeroSearch.tsx` hiện tại (chỉ đọc URL một
  lần lúc khởi tạo state, không nghe URL đổi sau đó — `HeroSearch.tsx:7-9`).
- **R-U3.** Đổi bất kỳ điều kiện lọc hoặc sắp xếp nào → `page` reset về trang đầu (tham số `page` bị
  xoá khỏi URL hoặc đặt lại 1), giữ nguyên hành vi đã có ở `HeroSearch.tsx:11-18` cho 3 field cũ, áp
  dụng cho toàn bộ field mới.
- **R-U4.** Tham số URL không hợp lệ (mã danh mục lạ, `sort`/`workMode`/`postedWithin` giá trị lạ,
  `salaryMin`/`salaryMax` không phải số hoặc âm) → **frontend bỏ qua đúng tham số đó** khi đọc URL lúc
  tải trang (không gửi lên API, không hiện lỗi chặn trang) — validate chặt (400) chỉ áp dụng khi
  frontend **tự gửi** giá trị hợp lệ do chính nó tạo ra; không để một URL cũ/gõ tay làm vỡ trang.
- **R-U5.** Có nút "Xoá bộ lọc" (xoá hết tham số lọc, giữ lại trang 1, sắp xếp về mặc định NEWEST) và
  hiển thị số kết quả đang khớp — **tổng số kết quả toàn bộ (`totalElements` của `Page`), không phải
  số tin đang hiện trên trang hiện tại**.
- **R-U6. Thời điểm áp dụng từng điều kiện — Desktop**:
  - Combobox ngành/tỉnh, checkbox hình thức làm việc, `<select>` thời gian đăng, `<select>` sắp xếp:
    **áp dụng ngay khi đổi** (không cần nút riêng) — mỗi lần đổi gọi `setSearchParams` đúng một lần.
  - Ô từ khoá: **chỉ áp dụng khi nhấn Enter hoặc bấm nút tìm kiếm**, không áp dụng theo từng phím gõ
    (giữ đúng hành vi hiện có của `HeroSearch.tsx`, khác các control khác ở trên).
  - Popover lương (hai ô Từ/Đến + checkbox "Ẩn tin không công bố lương"): có nút "Áp dụng" riêng
    trong popover — chỉ ghi vào URL khi bấm nút này, không áp dụng theo từng phím gõ số (cùng cơ chế
    với bottom sheet mobile ở UI.md mục 4b, nhưng chỉ áp dụng cho khối lương; các control khác ở
    trên KHÔNG cần nút riêng).
  - **Mỗi lần áp dụng tạo đúng một mục lịch sử trình duyệt** (một lần gọi `setSearchParams`, không
    tách thành nhiều lần gọi cho nhiều tham số đổi cùng lúc — ví dụ "Xoá bộ lọc" xoá nhiều tham số
    cùng lúc vẫn chỉ tạo một mục lịch sử, không phải một mục cho mỗi tham số bị xoá).

### 3.9 Giao diện thẻ việc làm và trang chi tiết (R-L)

- **R-L1.** Sửa 3 lỗi `JobCard` đã ghi ở ROADMAP Phase 2.1b: màu chữ lương đạt ≥ 4.5:1 (dùng
  `text-m3-tertiary`, đã chốt `#007A3D` = 5.45:1 ở `chore/ui-md3-foundation`, thay `text-accent-dark`
  hiện tại 4.34:1 — `JobCard.tsx:39`); hạn nộp chuyển lên góc phải trên cùng của card; ô logo trống
  hiện chữ viết tắt (2 ký tự đầu tên công ty) hoặc icon lucide `Building2` khi không có `logoUrl`
  (thay khung `bg-canvas` trống hiện tại — `JobCard.tsx:29`).
- **R-L1b. Gộp thêm (phát hiện ở spec-review, cùng lỗi, không nằm trong 3 lỗi ROADMAP đã ghi).**
  `PublicJobDetailPage.tsx:83` dùng cùng `text-accent-dark` (4.34:1) cho lương — sửa luôn cùng
  `text-m3-tertiary` như R-L1, trong cùng đợt code vì FR này đã mở file này ra để sửa R-L2. Không mở
  rộng sang các chỗ dùng `text-accent-dark` khác ngoài hai nơi này (JobCard, PublicJobDetailPage) —
  chỗ khác không thuộc phạm vi bộ lọc tìm việc, để `refactor/ui-md3-legacy` xử lý nếu còn sót.
- **R-L2.** `PublicJobDetailPage` (`/jobs/:id`, dùng chung cho mọi vai trò — không tạo route riêng):
  đọc trạng thái đăng nhập; vai trò `CANDIDATE` → bọc `CandidateLayout`; mọi trường hợp khác (khách,
  `HR`, chưa đăng nhập) → giữ `PublicLayout` như hiện tại. Sửa tại chỗ cả 3 nhánh return (loading,
  error, success) đang bọc cứng `PublicLayout` (`PublicJobDetailPage.tsx:31,41,56`).
- **R-L3.** Gộp `PublicJobListPage`/`CandidateJobListPage` thành dùng **một component chung** (thanh
  lọc + danh sách + phân trang); hai trang chỉ còn khác nhau ở layout bọc ngoài (`PublicLayout` vs
  `CandidateLayout`). Khác với quyết định ban đầu của C05/A2 (giữ trùng lặp có chủ đích vì chỉ 3 field
  — `CandidateJobListPage.tsx:6-9`); nay số field lọc tăng lên ~8, ngưỡng trùng lặp không còn hợp lý.

### 3.10 Seed demo (R-D)

- **R-D1.** Chuyển 6 job demo trong `db/seed/seed-demo-structural.sql` từ `status='DRAFT'` sang
  `status='OPEN'`; rải `published_at` qua đủ ba phía của cả ba mốc `postedWithin` (ví dụ: có job
  `published_at` ở mỗi khoảng <24h, 24h-7d, 7d-30d, và >30d trước thời điểm seed) để soát tay được cả
  bộ lọc thời gian đăng và sắp xếp "Mới nhất"/"Lương cao nhất" có thứ tự rõ ràng, không trùng giây.
  Cập nhật `db/seed/README.md` mục 6 theo số liệu thật đo được sau khi nạp lại (không giữ số cũ gắn
  với lý do DRAFT ở mục 0.c). Không `UPDATE` tay trong DB — chỉ sửa file SQL seed, nạp lại từ đầu.

## 4. Dữ liệu & quyền truy cập

**Không thêm migration** — toàn bộ cột cần (`category_code`/`location_code`, `salary_min`/`salary_max`,
`work_mode`, `published_at`/`created_at`) đã có từ V1/V8; index `idx_jobs_category_code`/
`idx_jobs_location_code` đã có từ V8, comment sẵn "phục vụ bộ lọc theo mã của FR-U07".

**API** — `GET /api/public/jobs` mở rộng tham số (không đổi path):

| Tham số | Kiểu | Mặc định | Mô tả | Lỗi 400 |
|---|---|---|---|---|
| `keyword` | String | không lọc | giữ nguyên (ILIKE `title`) | — |
| `category` | String | không lọc | giữ nguyên, cũ (ILIKE nhãn mã hoặc giá trị cũ) — FE mới không gửi | — |
| `location` | String | không lọc | giữ nguyên, cũ | — |
| `categoryCode` | String | không lọc | mã ngành theo danh mục C05 (R-N1) | `INVALID_CATALOG_CODE` |
| `locationCode` | String | không lọc | mã tỉnh/thành theo danh mục C05 (R-N2) | `INVALID_CATALOG_CODE` |
| `salaryMin` | Integer (triệu VNĐ) | không lọc | R-S1, R-S2 | `INVALID_JOB_FILTER` (âm, hoặc > `salaryMax`) |
| `salaryMax` | Integer (triệu VNĐ) | không lọc | R-S1, R-S2 | `INVALID_JOB_FILTER` (âm, hoặc < `salaryMin`) |
| `hideUnlisted` | Boolean | `false` | R-S4, R-S6 | — |
| `workMode` | String, lặp tham số (0..n) | không lọc | tập con `{ONSITE,HYBRID,REMOTE}` (R-W1) | `INVALID_JOB_FILTER` (giá trị lạ) |
| `postedWithin` | String enum | không lọc | `{LAST_24H,LAST_7D,LAST_30D}` (R-T2) | `INVALID_JOB_FILTER` (giá trị lạ) |
| `sort` | String enum | `NEWEST` | `{NEWEST,SALARY_DESC}` (R-O1/R-O2) | `INVALID_JOB_FILTER` (giá trị lạ) |
| `page` | Integer | `0` | giữ nguyên clamp | — |
| `size` | Integer | `10` (tối đa `50`) | giữ nguyên clamp | — |

Response `JobSummaryResponse` **không đổi field** — không thêm `createdAt`; FE dùng `publishedAt` khi
có, và không cần `createdAt` để hiển thị vì "thời gian đăng" chỉ dùng để lọc/sắp xếp ở backend, không
hiển thị số ngày cụ thể trên card theo đặc tả này.

**Seed demo** (đợt 5, R-D1):
- `db/seed/seed-demo-structural.sql`: chuyển 6 job từ `status='DRAFT'` sang `status='OPEN'`
  (`published_at`, `deleted_at` giữ cú pháp cột hiện có). Mỗi job đã có `category_code`/
  `location_code` (từ C05) nên qua được guard R-J3 của C05 mà không cần sửa gì khác.
- `published_at` rải từ vài giờ trước tới hơn 30 ngày trước (ví dụ: 1 job ~3 giờ, 1 job ~2 ngày, 1 job
  ~6 ngày, 1 job ~10 ngày, 1 job ~20 ngày, 1 job ~35 ngày trước thời điểm seed) — đủ để soát tay cả 3
  mốc `postedWithin` và sắp xếp "Mới nhất" có thứ tự rõ ràng, không trùng giây.
- Cập nhật `db/seed/README.md` mục 6: xoá dòng `job_recommendations = 0` kèm giải thích DRAFT (mục
  0.c) — viết lại theo số liệu thật sau khi nạp lại với 6 job OPEN (số liệu chính xác đo ở đợt seed,
  không đoán trước trong đặc tả này).
- Không `UPDATE` tay trong DB — chỉ sửa file SQL seed, nạp lại từ đầu theo `db/seed/README.md` mục 1.

## 5. AI

Không dùng AI. Lọc và sắp xếp là so khớp xác định trên dữ liệu có sẵn (SRS mục "Nguyên tắc bổ sung":
không gán nhãn Phù hợp/Không phù hợp, không dùng ngưỡng để loại).

## 6. Ngoài phạm vi

- Combobox tìm theo bí danh ("HCM", tên tỉnh cũ) — nợ kỹ thuật kế thừa từ C05, **không làm trong
  U07** (API `/api/public/catalogs` không trả bí danh; muốn làm phải đổi API hoặc tìm kiếm server-side
  — vượt phạm vi một FR lọc).
- Lọc theo `employment_type` (loại hợp đồng).
- Quy đổi tỷ giá giữa các loại tiền tệ khi sắp xếp theo lương (R-O2) — sort theo đúng số lưu trong DB,
  không quy đổi.
- Chip lọc đã lưu/lưu bộ lọc yêu thích, gợi ý bộ lọc, lịch sử tìm kiếm.
- Gán nhãn "phù hợp"/"không phù hợp"/xếp loại theo mức độ phù hợp (SRS mục 2, CLAUDE.md §7).
- Sửa `HeroSearch.tsx` của trang khác ngoài phạm vi `/`, `/candidate` (không có trang khác dùng lại
  component này ngoài hai trang danh sách).
- FR-U13 (tìm bằng ngôn ngữ tự nhiên), FR-U15 (gợi ý theo hồ sơ) — chỉ chuẩn bị điểm nối ở R-Q1, không
  code phần gọi của hai FR đó.
- Lọc theo thông tin nhân thân (không áp dụng — không có trường nào liên quan trên Job).

## 7. Xong khi

Tất cả lệnh sạch: `cd backend && ./mvnw test` (full suite, chạy **đúng một lần** ở đợt cuối),
`cd frontend && npm run build`, `cd frontend && npm run lint`. Danh sách test cần thêm (bổ sung vào
`JobPublicIntegrationTest`/`JobPublicServiceTest`, không sửa test cũ):

1. **Lọc theo mã**: `categoryCode`/`locationCode` hợp lệ → đúng tập job khớp; mã lạ → 400
   `INVALID_CATALOG_CODE`.
2. **Mã NULL vẫn hiện (R-N)**: job `category_code=NULL` (có hoặc không có giá trị cũ) vẫn có trong
   kết quả khi lọc theo `categoryCode` khác; tương tự cho `locationCode`; vị trí trong kết quả tuân
   theo đúng thứ tự sắp xếp (không bị đẩy xuống cuối một cách đặc biệt).
3. **Lương chồng lấn + Thoả thuận + ẩn**: job lương giao với khoảng lọc → vào; job lương nằm hẳn
   ngoài khoảng → loại; job Thoả thuận → vào khi không `hideUnlisted`, loại khi `hideUnlisted=true`;
   biên đúng ngưỡng (`salary_max = filterMin`, `salary_min = filterMax` → vào, lệch 1 đơn vị → loại).
   **`hideUnlisted` độc lập (R-S4)**: `hideUnlisted=true` không kèm `salaryMin`/`salaryMax` nào → job
   Thoả thuận vẫn bị loại, job có lương (VND) vẫn vào đủ (không bị ảnh hưởng vì không có điều kiện
   khoảng lọc nào đang áp).
4. **Tiền tệ khác VND (R-S5)**: job `salary_currency='USD'` có lọc lương → vẫn hiện, không bị
   `hideUnlisted` ẩn. Job `salary_currency='vnd'` (chữ thường) hoặc `' VND '` (có khoảng trắng) có
   lọc lương → coi là VND, **bị R-S2 so sánh như bình thường** (không rơi vào nhóm ngoại tệ).
5. **`salaryMin > salaryMax`**: 400 `INVALID_JOB_FILTER`; giá trị âm: 400; `salaryMin = salaryMax`:
   200 (biên hợp lệ).
6. **`workMode` nhiều giá trị**: `workMode=ONSITE&workMode=REMOTE` → job `HYBRID` bị loại, hai hình
   thức còn lại vào đủ; giá trị lạ → 400.
7. **3 mốc thời gian** (dùng `Clock` cố định trong test — R-T2, không phụ thuộc giờ thật lúc chạy;
   khoảng cách an toàn, không test theo biên tuyệt đối 1 giây để tránh chập chờn): job
   `published_at` = 23 giờ trước → vào khi `postedWithin=LAST_24H`; 25 giờ trước → loại. Tương tự: 6
   ngày trước → vào khi `LAST_7D`, 8 ngày trước → loại; 29 ngày trước → vào khi `LAST_30D`, 31 ngày
   trước → loại.
8. **2 kiểu sắp xếp có tie-break**: `NEWEST` và `SALARY_DESC` với ≥2 job có `COALESCE` bằng nhau →
   thứ tự xác định theo `id`, chạy lại nhiều lần cho kết quả giống nhau.
9. **Thoát `%`/`_`**: `keyword`/`category`/`location`/giá trị dùng để build pattern chứa `%`, `_`,
   `\` → không làm sai kết quả (so khớp đúng ký tự, không bị hiểu là wildcard).
10. **Kết hợp nhiều điều kiện**: truyền đồng thời `categoryCode`+`locationCode`+`salaryMin`+`workMode`
    +`postedWithin`+`sort` → kết quả là giao đúng của từng điều kiện riêng lẻ.
11. **Test cũ giữ nguyên**: 14 case `JobPublicIntegrationTest` + 2 case `JobPublicServiceTest` hiện có
    pass không sửa đổi khẳng định.
12. **RBAC/công khai**: toàn bộ test trên gọi không cần token (API vẫn `permitAll`).
13. **Tương phản màu lương (R-L1/R-L1b, soát tay)**: đo tỷ lệ tương phản chữ lương trên nền card và
    trên nền trang chi tiết đạt ≥ 4.5:1 ở cả hai nơi dùng `text-m3-tertiary` (`JobCard`,
    `PublicJobDetailPage`) sau khi sửa — ghi số đo thật vào walkthrough (đợt cuối), không chỉ tin vào
    giá trị `#007A3D` đã tính sẵn ở `chore/ui-md3-foundation`.

## 8. AI hay làm sai (dành cho người code)

- Dùng `Pageable.getSort()`/truyền `Sort` vào `Pageable` của native query — Spring Data nối `ORDER BY`
  sai vị trí vào câu SQL đã tự viết `ORDER BY` (xem lý do tại `JobPublicService.java:40`).
- Nối chuỗi tham số `sort` từ người dùng trực tiếp vào câu SQL (SQL injection) thay vì chọn từ danh
  sách cố định viết sẵn trong Java (R-O4).
- Quên tie-break `id` ở `ORDER BY` mới (và quên sửa luôn chỗ cũ đang thiếu ở `searchPublicJobs`).
- Lọc `category_code = :categoryCode` bằng so bằng tuyệt đối, vô tình loại hết các job chưa chuẩn hoá
  (quên R-N1/R-N2 `OR ... IS NULL`).
- Coi mọi tin không có cả hai cột lương là lỗi dữ liệu và ẩn luôn, thay vì hiển thị "Thoả thuận" theo
  mặc định (R-S4).
- Giả định mọi tin đều `salary_currency='VND'`, so sánh số trực tiếp với tin ngoại tệ như cùng đơn vị
  (phải loại khỏi R-S2, không loại khỏi kết quả — R-S5).
- Tự quy đổi tỷ giá tiền tệ khi sắp xếp theo lương "cho hợp lý" — ngoài phạm vi (mục 6), chỉ ghi nợ.
- Thêm tham số lọc theo `employment_type` "tiện thể vì đang sửa file này" — ngoài phạm vi (R-W2).
- Đổi hành vi hoặc xoá tham số `category`/`location` cũ, làm vỡ URL/bookmark cũ hoặc test cũ (R-F1).
- `toPattern` escape thiếu một trong `%`, `_`, hoặc quên escape chính ký tự escape (`\`) trước — thứ
  tự escape sai làm hỏng cả hai.
- Gán nhãn "Phù hợp"/thêm điểm số/màu đỏ-vàng-xanh theo mức lương hay mức độ khớp bộ lọc.
- Để `HeroSearch`/thanh lọc mới chỉ đọc URL một lần lúc mount, không nghe URL đổi sau đó (lặp lại đúng
  hạn chế hiện có — R-U2).
- Tạo route `/candidate/jobs/:id` riêng cho trang chi tiết thay vì sửa tại chỗ `PublicJobDetailPage`
  theo role (R-L2) — đặc tả yêu cầu **không tạo route mới**.
- Viết `NOW() - INTERVAL` trong SQL cho `postedWithin` thay vì tính ngưỡng ở Java và bind tham số
  (R-T2) — làm test không điều khiển được thời gian, phải chờ đồng hồ thật.
- Viết test biên thời gian phụ thuộc đồng hồ thực ("đúng 24 giờ trước", lệch 1 giây) — test phải dùng
  `Clock` cố định (R-T2) và khoảng cách an toàn (mục 7.7: 23h/25h, 6 ngày/8 ngày, 29 ngày/31 ngày),
  không test đúng biên tuyệt đối.
- **R-O1 đổi thứ tự mặc định** của `GET /api/public/jobs` (không truyền `sort`) từ `ORDER BY
  created_at DESC` (hành vi hiện tại, `JobRepository.java:34`) sang `ORDER BY COALESCE(published_at,
  created_at) DESC, id DESC` — đây là thay đổi hành vi mặc định của một API đã có, không chỉ thêm tính
  năng mới. Nếu bất kỳ test cũ nào (trong 14 case `JobPublicIntegrationTest`/2 case
  `JobPublicServiceTest`) đang ngầm giả định thứ tự kết quả theo `created_at` mà chuyển đỏ sau khi đổi
  R-O1 → **DỪNG LẠI VÀ BÁO CÁO**, không tự sửa khẳng định (assertion) của test cũ để nó xanh trở lại —
  đúng người dùng xem lại xem đây có đúng là hệ quả chủ ý của R-O1 hay một thay đổi khác đã làm lố
  phạm vi.

## 9. Kế hoạch chia đợt code

Mỗi đợt một commit; dừng → báo cáo diff → chờ duyệt → mới commit (CLAUDE.md §6). CLAUDE.md §6: "Sau
khi code xong, tự chạy lint/test và tự sửa lỗi trước khi báo hoàn thành" — áp dụng ở cuối MỖI đợt có
sửa code, dùng đúng lệnh đã định nghĩa ở CLAUDE.md §5:

- **Backend** (đợt 1, 2): CLAUDE.md §5 quy định `cd backend && ./mvnw test` — "LUÔN chạy đầy đủ, không
  chỉ class mới" — nhưng theo yêu cầu đã chốt khi viết đặc tả này (kế thừa từ chỉ đạo gốc), **giữ
  ngoại lệ**: `mvnw test` full suite (Testcontainers, chậm) chỉ chạy **đúng một lần ở đợt cuối** (đợt
  6); đợt 1-2 chỉ chạy `.\mvnw.cmd -q test-compile` để tự kiểm biên dịch (**không phải** `compile` —
  `compile` không biên dịch thư mục test, trong khi đợt 2 thêm toàn bộ test mới cần biên dịch được).
  Có thể chạy riêng class test mới bằng tay để tự kiểm nhanh, không tính là lần full-suite bắt buộc.
- **Frontend** (đợt 3, 4): CLAUDE.md §5 quy định `cd frontend && npm run build` + `cd frontend && npm
  run lint` — "bắt buộc trước khi báo xong đợt" — **không có ngoại lệ**, chạy đầy đủ cả hai lệnh ở
  cuối MỖI đợt frontend (khác với backend ở trên).
- **Seed** (đợt 5): không có code Java/TypeScript để biên dịch/lint — xác nhận bằng cách nạp lại theo
  `db/seed/README.md` mục 1.

1. **Backend — tham số lọc + truy vấn dùng chung.** `PublicJobSearchCriteria`, viết lại
   `searchPublicJobs` (R-N, R-S, R-W, R-T, R-O, R-Q), exception `InvalidJobFilterException`, escape
   `toPattern` (R-F cuối). `.\mvnw.cmd -q test-compile`.
2. **Backend — test.** Toàn bộ 12 nhóm test ở mục 7 (trừ "test cũ giữ nguyên", đã có).
   `.\mvnw.cmd -q test-compile` ở đợt này (chưa chạy full suite).
3. **Frontend — thanh lọc mới + URL hai chiều.** Thay `HeroSearch.tsx`, 2 `CatalogCombobox` cho
   ngành/tỉnh, ô lương, chọn hình thức, chọn thời gian đăng, chọn sắp xếp, nút xoá bộ lọc, đếm kết quả
   (R-U). `npm run build` + `npm run lint`.
4. **Frontend — gộp 2 trang danh sách + sửa JobCard + layout chi tiết.** R-L1/R-L1b/R-L2/R-L3, bottom
   sheet 375px. `npm run build` + `npm run lint`.
5. **Seed demo.** Chuyển 6 job sang OPEN, rải `published_at`, cập nhật `db/seed/README.md` (mục 0.c,
   mục 4). Nạp lại theo `db/seed/README.md` mục 1 để xác nhận sạch.
6. **Đợt cuối.** `mvnw test` full suite + `npm run build` + `npm run lint`; soát tay desktop và 375px
   (ghi vào walkthrough, gồm số đo tương phản mục 7.13); sửa lỗi phát sinh; **cập nhật
   `docs/UI_GUIDE.md` mục 7 — thêm "FR-U07" vào cột FR của hai dòng `/` và `/jobs/:id` (dòng `/candidate`
   đã có sẵn từ trước), đúng tiền lệ C05 (UI.md C05 mục 2 cũng cập nhật ở đợt cuối, không phải lúc
   duyệt đặc tả)**; skill `srs-guard`; viết `docs/walkthrough/fr-u07-job-filter.md`; đổi trạng thái
   `ĐÃ HOÀN THÀNH <ngày>` ở đây và ở `docs/SRS.md`; tick `docs/ROADMAP.md` (gồm cả xoá dòng nợ kỹ thuật
   "6 job demo DRAFT" của `feat/fr-c05-catalog` vì đã sửa xong).

## 10. Nợ kỹ thuật dự kiến

- Combobox ngành/tỉnh trong thanh lọc mới vẫn chỉ tìm theo nhãn, không theo bí danh — kế thừa nguyên
  trạng từ C05 (mục 6).
- Sắp xếp "Lương cao nhất" không quy đổi tiền tệ — tin ngoại tệ sắp xếp theo đúng số lưu trong DB dù
  đơn vị khác VND (mục 6). Chưa có tin demo thật ở tiền tệ khác VND để minh hoạ bằng mắt, chỉ có test
  tự động dựng dữ liệu giả.
- `PublicJobSearchCriteria` (R-Q1) mới chỉ chuẩn bị điểm nối cho FR-U15 — FR-U15 khi tới lượt code
  (sau FR-U14) phải tự kiểm lại phần "điều kiện cứng" này còn đúng với mô tả `docs/features/README.md`
  mục "FR-U15" hay không, vì U07 chưa trông thấy đặc tả U15 đã duyệt để đối chiếu.
