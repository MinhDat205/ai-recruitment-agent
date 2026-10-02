# FR-U14 — Hồ sơ nghề nghiệp và mong muốn công việc

> Trạng thái: ĐÃ DUYỆT (02/10/2026).

- Nhóm: Ứng viên
- Tóm tắt: Sau đăng ký hoặc ở trang hồ sơ: khai chức danh, ngành, khu vực, lương, kỹ năng mong muốn; điền nhanh từ CV
- Phụ thuộc: FR-U01, FR-C05, FR-U04
- Nhánh: `feat/fr-u14-career-profile`
- Mở rộng: FR-U01 (hồ sơ ứng viên, `candidate_profiles`), FR-C05 (danh mục ngành/khu vực, `CatalogRegistry`), FR-U04 (hạ tầng embedding, `EmbeddingService`)

## 0. Đã kiểm trước khi viết đặc tả (CLAUDE.md §6 — đọc code trước khi đoán)

- **(a) Tiền lệ ánh xạ cột mảng Postgres (`text[]`) qua Hibernate — chưa có, phải tự xác minh.**
  Grep `SqlTypes.ARRAY`/`text\[\]` trong `backend/src/main/java` không ra kết quả nào ngoài các cột
  `@JdbcTypeCode(SqlTypes.JSON)` (JSONB) — dự án **chưa từng** map cột mảng Postgres qua JPA.
  Xác nhận phiên bản Hibernate đang dùng thật (không đoán): `.\mvnw.cmd dependency:tree` →
  `org.hibernate.orm:hibernate-core:jar:7.4.1.Final:compile` (Spring Boot 4.1.0). `javap -public` trên
  `hibernate-core-7.4.1.Final.jar` (`org.hibernate.type.SqlTypes`) xác nhận hằng số `ARRAY` tồn tại.
  Cách map đúng cho Hibernate 7.x + PostgreSQLDialect: `@JdbcTypeCode(SqlTypes.ARRAY)` trên field
  `List<String>`, cột Flyway khai kiểu `text[]`. Đây là **lần đầu tiên** dự án dùng kiểu này — PHẢI có
  test xác nhận entity đọc/ghi được và qua `ddl-auto: validate` ngay ở đợt code đầu tiên (không suy ra
  "chắc chắn chạy được" chỉ từ javap — cùng tinh thần C05 bắt buộc kiểm Java migration V9 ở đợt đầu).

  **Riêng cột `embedding vector(1536)`: KHÔNG áp dụng cách trên, phải đi theo tiền lệ khác.** Đọc
  [`ResumeParsedData.java:19-21`](../../../../backend/src/main/java/com/recruitment/resume/ResumeParsedData.java)
  và [`JobEmbedding.java:16-23`](../../../../backend/src/main/java/com/recruitment/job/JobEmbedding.java):
  dự án đã xác minh bằng bytecode thật của `spring-ai-pgvector-store-2.0.0.jar` rằng ngay cả Spring AI
  cũng không bind kiểu `vector` qua Hibernate (dùng `JdbcTemplate` riêng) — Hibernate không có
  `JdbcType`/`UserType` đăng ký sẵn cho `vector`, tự thêm một cái là chưa kiểm chứng được. Vì vậy
  **entity `CandidateProfile` KHÔNG map field `embedding`**; đọc/ghi hoàn toàn qua native query
  (`@Query(nativeQuery = true)`) với `CAST(:embeddingText AS vector)`, đúng khuôn
  `ResumeParsedDataRepository.updateEmbedding`/`findIdsNeedingEmbedding`. Cột `embedding_model
  VARCHAR` thì map bình thường (không phải kiểu `vector`, không có rào cản) — giống
  `JobEmbedding.model` (dòng 38-39 file trên).

- **(b) `ResumeEmbeddingScheduler` xử lý lỗi gọi embedding ở lượt poll kế tiếp ra sao — đã đọc code,
  không đoán.** `ResumeEmbeddingOrchestrator.processOne`
  ([`ResumeEmbeddingOrchestrator.java:67-74`](../../../../backend/src/main/java/com/recruitment/resume/ResumeEmbeddingOrchestrator.java)):
  khi `embeddingService.embed(text)` ném `RuntimeException` → **không ghi gì cả**
  (`catch (RuntimeException e) { log.debug(...); return; }`). Cột `embedding` vẫn `NULL`, điều kiện
  quét `findIdsNeedingEmbedding` (`WHERE rpd.embedding IS NULL`) vẫn đúng → lượt poll kế tiếp
  (`app.resume-embedding.poll-interval-ms`, mặc định 5000ms,
  [`ResumeEmbeddingScheduler.java:39`](../../../../backend/src/main/java/com/recruitment/resume/ResumeEmbeddingScheduler.java))
  **tự nhiên thử lại vô hạn** — không đếm số lần, không backoff, không mã lỗi nào được lưu. U14 áp
  dụng đúng hành vi này cho scheduler mới (mục 3.9), không tự thêm cơ chế đếm lần/backoff mà FR-U04
  chưa có cho CV/Job.

- **(c) Nhãn hiện tại trên `CandidateProfilePage.tsx` — xác nhận `headline` = "Chức danh mong
  muốn".** Đọc trực tiếp file (dòng 111, 117, 121, 128, 138): `headline` → **"Chức danh mong muốn"**
  (đúng giả định — field này đã mang nghĩa "mong muốn" từ FR-U01, không phải "hiện tại");
  `currentTitle` → "Vị trí hiện tại"; `location` → "Khu vực"; `yearsExperience` → "Số năm kinh
  nghiệm"; `dateOfBirth` → "Ngày sinh". Kết luận: **không tạo cột `desired_title` mới** (mục 3.6
  R-G1) — `headline` đã đúng chỗ.

- **(d) API đăng ký ứng viên có trả phiên đăng nhập không — đã kiểm, KHÔNG.**
  `POST /api/auth/register/candidate`
  ([`AuthController.java:30-33`](../../../../backend/src/main/java/com/recruitment/auth/AuthController.java))
  trả `UserResponse` ([`UserResponse.java:8-17`](../../../../backend/src/main/java/com/recruitment/auth/dto/UserResponse.java))
  — không có `accessToken`/`refreshToken`. Chỉ `POST /api/auth/login`/`refresh` trả `AuthResponse`
  ([`AuthResponse.java:3-7`](../../../../backend/src/main/java/com/recruitment/auth/dto/AuthResponse.java),
  có `accessToken`/`refreshToken`/`tokenType`/`expiresInMs`). `RegisterForm.tsx:58` vì vậy điều hướng
  về `/login` (không có token để tự đăng nhập). Kết luận: **R-O1 KHÔNG được đổi luồng đăng ký** —
  `RegisterForm.tsx` giữ nguyên 100%, màn onboarding chỉ xuất hiện ở lần đăng nhập đầu tiên qua wrapper
  R-O2 (ứng viên đăng nhập xong → vào `/candidate` như luồng hiện có → wrapper phát hiện cờ `null` và
  tự chuyển sang `/candidate/onboarding`).

## 1. Mục đích

Thu thập thông tin nghề nghiệp và mong muốn công việc ngay từ đầu, để hệ thống gợi ý được việc làm
(FR-U15) kể cả khi ứng viên chưa tải CV. Mở rộng hồ sơ `candidate_profiles` đã có từ FR-U01, tái dùng
danh mục ngành nghề/tỉnh thành (FR-C05) và hạ tầng embedding (FR-U04).

## 2. Luồng người dùng

**Ứng viên — lần đầu sau đăng ký** — `/candidate/onboarding`
1. Đăng ký xong → **luồng đăng ký không đổi**: vẫn về `/login` kèm `justRegistered` như hiện tại
   (`POST /api/auth/register/candidate` không trả phiên đăng nhập — xem mục 0.d). Ứng viên tự đăng
   nhập → `ProtectedRoute`/điều hướng theo role đưa vào `/candidate` như cũ → wrapper R-O2 phát hiện
   `onboardingCompletedAt == null` ngay trong lần vào `/candidate/*` ĐẦU TIÊN này và đưa sang
   `/candidate/onboarding` (R-O1).
2. Thấy form "Hoàn thiện hồ sơ": chức danh mong muốn, ngành nghề mong muốn (≤3), khu vực mong muốn
   (≤3), hình thức làm việc (nhiều), mức lương mong muốn tối thiểu, số năm kinh nghiệm, kỹ năng
   chính (thẻ), giới thiệu ngắn.
3. Tuỳ chọn bấm "Điền từ CV" (chỉ khi đã có CV chính đã phân tích xong) → các ô chức danh/kỹ
   năng/số năm kinh nghiệm được điền sẵn, chỉnh tiếp được.
4. Bấm "Lưu" → ghi hồ sơ, đánh dấu đã hoàn thiện, chuyển tới `/candidate` (trang Việc làm).
5. Hoặc bấm "Bỏ qua" → chỉ đánh dấu đã qua màn này (không lưu field nào), chuyển tới `/candidate`.
6. Lần đăng nhập sau: không hiện lại màn này (R-O2).

**Ứng viên — sửa bất kỳ lúc nào** — `/candidate/profile`
1. Mở trang "Hồ sơ và CV" → card "Thông tin cơ bản" (field FR-U01 cũ) và card "Nghề nghiệp và mong muốn công việc"
   (field mới của U14), cùng một form, cùng nút "Lưu".
2. Sửa field nào đó → bấm "Lưu" → ghi lại toàn bộ hồ sơ (PUT thay thế, không phải patch từng field).
3. Bấm "Điền từ CV" ở card "Nghề nghiệp và mong muốn công việc" → như bước 3 ở trên.

**Hệ thống**
- Validate mã ngành/khu vực qua `CatalogRegistry` (FR-C05), mã sai → 400.
- Lưu xong: nếu văn bản đại diện (R-E1) đổi, đặt `embedding = NULL`, `embedding_model = NULL`; job
  nền (theo khuôn FR-U04) tính lại.
- Không gọi LLM ở bất kỳ bước nào.

## 3. Quy tắc nghiệp vụ

### 3.1 Ngành nghề / khu vực mong muốn (R-F)

- **R-F1.** `desiredIndustryCodes`, `desiredLocationCodes`: mỗi mã phải tồn tại trong danh mục tương
  ứng (`CatalogRegistry.isIndustry`/`isProvince`, FR-C05) — sai → 400 `INVALID_CATALOG_CODE`, tái
  dùng nguyên `InvalidCatalogCodeException.industry()`/`province()`
  ([`InvalidCatalogCodeException.java`](../../../../backend/src/main/java/com/recruitment/common/exception/InvalidCatalogCodeException.java))
  — không viết exception mới cho validate MÃ, chỉ validate SỐ LƯỢNG dùng exception mới (R-F3).
- **R-F2.** Backend tự khử trùng (dedupe) theo đúng giá trị mã (mã là hằng số viết hoa cố định như
  `IT_SOFTWARE`, không cần chuẩn hoá hoa/thường), giữ thứ tự xuất hiện đầu tiên, **TRƯỚC KHI** kiểm số
  lượng R-F3 — gửi `["IT_SOFTWARE","IT_SOFTWARE","SALES"]` phải được chấp nhận (sau dedupe còn 2 mã,
  hợp lệ), không bị từ chối vì độ dài mảng thô là 3.
- **R-F3.** Sau dedupe, mỗi mảng tối đa **3** phần tử — vượt quá → 400, exception mới
  `InvalidProfileFieldException` (mục 3.11 nêu khuôn chung).
- **R-F4.** Không bắt buộc — mảng rỗng (`'{}'`) là giá trị mặc định hợp lệ.

### 3.2 Lương mong muốn tối thiểu (R-S)

- **R-S1.** Input: số nguyên **triệu VNĐ**, `0 ≤ giá trị ≤ 1000` (đồng bộ đơn vị với FR-U07 R-S1).
  Backend quy đổi `× 1.000.000` trước khi lưu vào `desired_salary_min NUMERIC(14,2)` (đơn vị VND,
  cùng kiểu cột với `jobs.salary_min`/`salary_max` —
  [`V1__init_schema.sql:72-73`](../../../../backend/src/main/resources/db/migration/V1__init_schema.sql)).
- **R-S2.** Ngoài khoảng `[0, 1000]` hoặc không phải số nguyên → 400 Bean Validation (`@Min(0)
  @Max(1000)` trên field request `desiredSalaryMinMillions`).
- **R-S3.** Không bắt buộc — để trống (`NULL`) nghĩa là không có ngưỡng lương mong muốn, không phải
  "lương bằng 0".

### 3.3 Hình thức làm việc mong muốn (R-M)

- **R-M1.** `desiredWorkModes`: mảng nhiều giá trị trong `{ONSITE, HYBRID, REMOTE}` (đúng 3 giá trị
  `WORK_MODE_LABELS`,
  [`jobLabels.ts:39-43`](../../../../frontend/src/features/jobs/jobLabels.ts)). Giá trị ngoài tập này
  → 400, `InvalidProfileFieldException` (mục 3.11).
- **R-M2.** Dedupe trước khi lưu (cùng nguyên tắc R-F2); không giới hạn số lượng ngoài việc chỉ có 3
  giá trị khả dĩ (chọn cả 3 vẫn hợp lệ).
- **R-M3.** Không bắt buộc — mảng rỗng nghĩa là không có ưu tiên hình thức làm việc.

### 3.4 Kỹ năng chính (R-K)

- **R-K1.** `skills`: mảng tối đa **20** thẻ. Mỗi thẻ sau khi cắt khoảng trắng hai đầu còn **1–50 ký
  tự**. Vượt quá 20 thẻ hoặc có thẻ sai độ dài → 400, `InvalidProfileFieldException` (mục 3.11).
- **R-K2.** Khử trùng **không phân biệt hoa/thường** (so khớp theo bản đã hạ chữ thường), nhưng **lưu
  đúng cách viết của lần xuất hiện ĐẦU TIÊN** — ví dụ nhập `["Java", "SQL", "java"]` → lưu `["Java",
  "SQL"]`. Giữ **thứ tự nhập** (không sắp xếp lại theo alphabet).
- **R-K3.** Không bắt buộc — mảng rỗng là hợp lệ.

### 3.5 Giới thiệu ngắn (R-B)

- **R-B1.** `bio`: tối đa **500 ký tự** (`char_length`). Vượt quá → 400 Bean Validation (`@Size(max =
  500)`).
- **R-B2.** Không bắt buộc — `NULL`/rỗng hợp lệ. Không validate nội dung (không chặn từ khoá, không
  kiểm chính tả).

### 3.6 Trường dùng lại / giữ nguyên (R-G)

- **R-G1.** `headline` tiếp tục mang nghĩa "chức danh mong muốn" (đã đúng từ FR-U01, xác nhận ở mục
  0.c) — **KHÔNG thêm cột `desired_title` mới**. U14 chỉ thêm UI/luồng (màn onboarding, nút "Điền từ
  CV") cho field đã tồn tại.
- **R-G2.** `years_experience` tiếp tục mang nghĩa "số năm kinh nghiệm ứng viên tự khai" — **không
  tạo field "mong muốn" riêng cho kinh nghiệm** (kinh nghiệm là thực tế đã có, không phải điều ứng
  viên "mong muốn").
- **R-G3.** `current_title`, `location`, `date_of_birth` giữ nguyên nghĩa, cột, kiểu và hành vi hiện
  có của FR-U01 — U14 không đụng validate/API của 3 field này, chỉ đổi CHỖ HIỂN THỊ (cùng form, card
  "Thông tin cơ bản"). **Riêng nhãn hiển thị của `location`: đổi từ "Khu vực" sang "Nơi ở hiện tại"**
  (chỉ đổi chữ ở frontend, không đổi tên cột/field/API) — đứng cạnh "Khu vực mong muốn" (R-F) trong
  cùng trang dễ gây nhầm hai field là một nếu cùng giữ nhãn "Khu vực".
- **R-G4.** `date_of_birth` **không được đưa vào văn bản đại diện** (R-E1) hay bất kỳ tính toán nào
  liên quan embedding/gợi ý — ràng buộc SRS "ngày sinh sẵn có không được dùng cho gợi ý".

### 3.7 Onboarding (R-O)

- **R-O1. KHÔNG đổi luồng đăng ký** (xác nhận ở mục 0.d — `POST /api/auth/register/candidate` không
  trả phiên đăng nhập). `RegisterForm.tsx` giữ nguyên 100%: sau đăng ký vẫn điều hướng về `/login` kèm
  `state: { justRegistered: true }`, cho cả Ứng viên lẫn HR, như hiện tại. Màn "Hoàn thiện hồ sơ" chỉ
  xuất hiện ở **lần ứng viên tự đăng nhập đầu tiên sau đăng ký** — hoàn toàn do wrapper R-O2 phát hiện
  khi ứng viên vào `/candidate/*` (qua điều hướng role sẵn có ở `LoginForm.tsx`, không đổi), không có
  đường điều hướng riêng nào từ luồng đăng ký.
- **R-O2.** Wrapper mới `RequireCandidateProfileOnboarding` (theo mẫu
  [`RequireCompany.tsx`](../../../../frontend/src/features/companies/RequireCompany.tsx)) bọc nhóm
  route `/candidate/*` **trừ chính `/candidate/onboarding`**: đọc `useMyProfileQuery` (query đã có,
  không gọi API trùng).
  - **Đang tải** (`isLoading`) → hiện trạng thái đang tải (skeleton, đúng mẫu `RequireCompany.tsx`
    dòng 14-24) — **không render con, không điều hướng đi đâu cả** (tránh render `/candidate` một nhịp
    rồi mới nhảy sang onboarding, và tránh đọc `onboardingCompletedAt` khi dữ liệu chưa chắc mới nhất).
  - **Có dữ liệu, `onboardingCompletedAt === null`** → `<Navigate to="/candidate/onboarding" replace
    />`.
  - **Có dữ liệu, khác `null`** → render con bình thường.
  - **Lỗi tải** (mạng/500) → không chuyển hướng, để trang con tự xử lý lỗi (đúng nguyên tắc
    `RequireCompany`).
  - **Chống vòng lặp chuyển hướng**: sau khi `PUT /me` hoặc `PATCH /skip-onboarding` thành công ở
    trang `/candidate/onboarding`, bắt buộc gọi `queryClient.setQueryData` ghi thẳng response (đã có
    `onboardingCompletedAt` khác `null`) vào cache của `useMyProfileQuery`
    **TRƯỚC KHI** `navigate('/candidate')` — đúng mẫu đã có ở
    `useSaveProfileMutation`/`queries.ts:16-24` (`onSuccess: (data) => queryClient.setQueryData(...)`).
    Nếu chuyển hướng trước khi cache cập nhật, `RequireCandidateProfileOnboarding` ở `/candidate` có
    thể đọc lại cache CŨ (`onboardingCompletedAt` vẫn `null`) và bật lại vòng lặp redirect.
- **R-O3.** `onboarding_completed_at` được đặt `= now()` khi: (a) `PUT /api/candidates/profile/me`
  thành công **và cờ đang NULL** — service tự đặt cờ (nếu đang NULL) ngay trước khi áp field khác
  trong CÙNG transaction, không cần frontend gửi field này; (b) bấm "Bỏ qua" → gọi riêng `PATCH
  /api/candidates/profile/me/skip-onboarding`, chỉ đặt cờ, không đổi field nào khác, **idempotent**
  (gọi lại khi cờ đã có giá trị → vẫn 200, không lỗi, giá trị cờ không đổi).
- **R-O4.** Migration V10 gán `onboarding_completed_at = now()` cho **mọi hồ sơ đã tồn tại** (ứng
  viên cũ không bị bắt quay lại màn onboarding).
- **R-O5.** Form đăng ký (`RegisterForm.tsx`) **không đổi** — không thêm field nghề nghiệp nào, không
  đổi điều hướng sau khi đăng ký (R-O1).

### 3.8 Điền từ CV (R-A)

- **R-A1.** `GET /api/candidates/profile/me/autofill-from-resume`: tìm CV chính qua
  `ResumeRepository.findByCandidateIdAndIsPrimaryTrue` → không có, hoặc `parseStatus != DONE` → 409
  `NO_PRIMARY_RESUME_PARSED` ("Chưa có CV chính đã phân tích xong để điền tự động."). Có → đọc
  `ResumeParsedData` qua `findByResumeId` → trả:
  - `currentTitle` (nguyên văn từ CV, `ResumeParsedPayload.currentTitle`).
  - `skills` (`ResumeParsedPayload.skills`).
  - Số năm kinh nghiệm quy đổi từ `experienceMonths` (cột entity `ResumeParsedData`, **KHÔNG dùng
    field `experience.years` đã có ở `ResumeParsedDataResponse`** — field đó làm tròn theo R-E8 của
    C05 tới 1 chữ số thập phân; U14 cần làm tròn tới bước **0,5**, phải tự tính lại từ
    `experience_months` thô để tránh làm tròn hai lần sai số). `experience_months = NULL` → không trả
    field năm kinh nghiệm.
  - Không gọi AI — toàn bộ là đọc dữ liệu đã có, backend tự tính (giống tinh thần R-E9 của C05).
- **R-A2.** Frontend nhận kết quả → điền vào **form** (chưa lưu):
  - `currentTitle` (CV) → luôn điền vào ô "Vị trí hiện tại" (ghi đè giá trị đang có, vì là hành động
    người dùng chủ động bấm).
  - `currentTitle` (CV) → điền vào ô "Chức danh mong muốn" **CHỈ KHI ô đó đang trống** (không ghi đè
    chức danh mong muốn người dùng đã tự nhập).
  - `skills` (CV) → **gộp thêm** vào danh sách thẻ hiện có trong form (áp dụng dedupe + giới hạn 20
    của R-K ngay trong form).
  - Số năm kinh nghiệm → ghi đè ô "Số năm kinh nghiệm" nếu backend trả giá trị (khác thiếu field).
  - Người dùng chỉnh tiếp được mọi ô sau khi điền; chỉ ghi xuống DB khi bấm "Lưu" (không tự lưu).
- **R-A3.** Nút "Điền từ CV" bị khoá (disabled) khi chưa có CV chính đã phân tích xong, kèm chú thích
  lý do (UI.md mục 6).

### 3.9 Embedding hồ sơ (R-E)

- **R-E1.** Văn bản đại diện = ghép theo thứ tự: `headline` (chức danh mong muốn) → `skills` (nối
  bằng dấu phẩy) → `bio`. **KHÔNG gồm**: `dateOfBirth` (R-G4), `desiredSalaryMin`, `currentTitle`,
  `location`, `yearsExperience`, tên/email ứng viên. Phần tử trống (`null`/rỗng) bị bỏ qua khi ghép,
  không chèn chuỗi rỗng hay nhãn "chưa có". Hàm dựng text là **hàm thuần mới** (không có hàm tương tự
  để tái dùng — khác tiền lệ `buildResumeText` tái dùng được ở FR-U04 vì nguồn dữ liệu khác hẳn), đặt
  cạnh entity/service của package `user/`.
- **R-E2.** Cả 3 phần (headline/skills/bio) trống → văn bản đại diện rỗng → **không build, giữ
  `embedding = NULL`**, không gọi `EmbeddingService`, không đưa vào tập quét của scheduler (R-E4).
- **R-E3.** Khi `PUT /api/candidates/profile/me` ghi thành công: so văn bản đại diện MỚI (từ giá trị
  vừa ghi) với văn bản đại diện CŨ (dựng lại từ giá trị trước khi ghi, đọc trong cùng transaction) —
  khác nhau → đặt `embedding = NULL`, `embedding_model = NULL` (native UPDATE, cùng transaction ghi
  field khác). Giống nhau (ví dụ chỉ sửa `dateOfBirth`) → **không đụng `embedding`** (test đơn vị mục
  7.10 khẳng định đúng việc này).

  **Bẫy Hibernate khi ghép hai cách ghi trong cùng transaction (entity + native `@Modifying`):**
  `CandidateProfileRepository.save(profile)` chỉ đưa thay đổi field (`headline`...) vào persistence
  context, **chưa chắc đã flush xuống DB**. Câu native UPDATE đặt `embedding = NULL` dùng
  `@Modifying(clearAutomatically = true)` — `clearAutomatically` **xoá toàn bộ persistence context
  SAU KHI chạy** (`org.springframework.data.jpa.repository.Modifying`, xác nhận bằng `javap` trên
  `spring-data-jpa-4.1.0.jar`: hai thuộc tính `flushAutomatically()`/`clearAutomatically()` tách biệt,
  `flushAutomatically` mặc định `false`). Nếu gọi native UPDATE này TRƯỚC khi entity `profile` được
  flush, context bị xoá trước khi Hibernate kịp ghi `headline` xuống DB ở cuối transaction → **thay
  đổi `headline` bị mất**, chỉ `embedding = NULL` được ghi. Bắt buộc: `save()` bằng
  `candidateProfileRepository.saveAndFlush(profile)` (ép flush ngay) **TRƯỚC KHI** gọi native UPDATE
  đặt `embedding = NULL` — thứ tự trong service method: đọc văn bản cũ → set field mới lên entity →
  `saveAndFlush` → so văn bản mới/cũ → nếu khác, gọi native UPDATE đặt `embedding = NULL`.
- **R-E4.** Scheduler mới `CandidateProfileEmbeddingScheduler` theo đúng khuôn
  `ResumeEmbeddingScheduler`/`JobEmbeddingScheduler` (`@Scheduled(fixedDelayString =
  "${app.candidate-profile-embedding.poll-interval-ms:5000}")`, có `@ConditionalOnProperty` tắt được
  trong test, **không** `@Transactional`). Điều kiện quét (native query): `embedding IS NULL AND
  (headline IS NOT NULL AND headline <> '' OR cardinality(skills) > 0 OR (bio IS NOT NULL AND bio <>
  ''))` — loại hồ sơ rỗng khỏi tập quét ngay tại câu SQL, không quét rồi bỏ qua trong Java.
- **R-E5. Khác tiền lệ CV có chủ đích (sửa race đã ghi nợ ở ROADMAP, không lặp lại):** ghi kết quả
  bằng `UPDATE candidate_profiles SET embedding = CAST(:text AS vector), embedding_model = :model
  WHERE id = :id AND updated_at = :expectedUpdatedAt` — `expectedUpdatedAt` là giá trị `updated_at`
  đọc được NGAY TRƯỚC khi gọi `EmbeddingService` (ngoài transaction). Rowcount 0 (hồ sơ đã bị sửa/lưu
  lại giữa lúc build text và lúc ghi xong — `updated_at` đổi do trigger `set_updated_at`) → **bỏ qua,
  không ghi gì**, để lượt poll kế tiếp tự đọc lại giá trị mới nhất và tính lại.
  `@Modifying(clearAutomatically = true)`.
- **R-E6.** Lỗi khi gọi `EmbeddingService.embed` → xử lý đúng hành vi đã xác nhận ở mục 0.b: không ghi
  gì, `embedding` vẫn `NULL`, lượt poll kế tiếp tự thử lại vô hạn (không thêm cơ chế đếm lần/backoff
  riêng cho U14).
- **R-E7.** `embedding_model` lưu tên model dùng để sinh embedding (của FR-U04, không gọi LLM) — field
  JPA bình thường (không phải kiểu `vector`, mục 0.a), ghi cùng câu UPDATE ở R-E5.
- **R-E8.** Không đụng `resume_parsed_data.embedding`/`job_embeddings` — U14 chỉ thêm cột/scheduler
  mới cho `candidate_profiles`, không sửa luồng embedding CV/Job hiện có.

### 3.10 Chống lộ cho HR (R-P)

- **R-P1.** Không thêm field nào của mục 3.1–3.5 (`desired*`, `skills`, `bio`,
  `onboardingCompletedAt`, `embedding*`) vào `ApplicationHrListItemResponse`,
  `ApplicationSearchItemResponse`, hay bất kỳ response/entity nào mà controller gắn `hasRole("HR")`
  đọc được.
- **R-P2.** `GET/PUT/PATCH /api/candidates/profile/**` giữ nguyên bị chặn ở tầng filter chain
  `hasRole("CANDIDATE")` (`SecurityConfig`, đã có từ FR-U01) — không thêm đường vào nào cho HR.

### 3.11 Validate request chung (R-V)

- **R-V1.** `CandidateProfileRequest` thêm field mới + Bean Validation — **field cũ (`headline`,
  `location`, `currentTitle`, `yearsExperience`, `dateOfBirth`) giữ nguyên KHÔNG thêm validate** (ngoài
  phạm vi U14, tránh làm vỡ hành vi FR-U01 hiện có mà không có yêu cầu nào đòi hỏi):
  - `desiredIndustryCodes: List<String>` — không giới hạn kích thước ở annotation (R-F2/R-F3 kiểm sau
    dedupe ở service).
  - `desiredLocationCodes: List<String>` — tương tự.
  - `desiredWorkModes: List<String>` — tương tự (R-M1/R-M2 kiểm ở service).
  - `skills: List<String>` — tương tự (R-K1/R-K2 kiểm ở service, cần dedupe trước khi đếm).
  - `desiredSalaryMinMillions: Integer` — `@Min(0) @Max(1000)` (R-S2).
  - `bio: String` — `@Size(max = 500)` (R-B1).
  - **4 field `List<String>` ở trên: thiếu field (không gửi) hoặc gửi `null` → service coi là mảng
    rỗng** (`List.of()`), không NPE khi dedupe (R-F2/R-K2), không vi phạm `NOT NULL` khi ghi xuống cột
    `text[]` (mục 4, cột có `DEFAULT '{}'` nhưng request `null` tường minh vẫn phải được chặn ở
    service trước khi tới entity, không dựa vào default của cột). Request kiểu CŨ — chỉ gửi đúng 5
    field gốc của FR-U01 (`headline`, `location`, `currentTitle`, `yearsExperience`, `dateOfBirth`),
    4 field mảng + `desiredSalaryMinMillions`/`bio` đều vắng mặt trong JSON — vẫn phải trả **200**
    (tương thích ngược với client cũ/test cũ chưa biết field mới).
- **R-V2.** `InvalidProfileFieldException` (package `common/exception/`) — **cùng khuôn
  `InvalidJobFilterException`**
  ([`InvalidJobFilterException.java`](../../../../backend/src/main/java/com/recruitment/common/exception/InvalidJobFilterException.java)):
  một class, constructor private, factory method + message tiếng Việt cố định:
  - `tooManyDesiredIndustries()` → "Chỉ được chọn tối đa 3 ngành nghề mong muốn."
  - `tooManyDesiredLocations()` → "Chỉ được chọn tối đa 3 khu vực mong muốn."
  - `tooManySkills()` → "Chỉ được nhập tối đa 20 kỹ năng."
  - `invalidSkillLength()` → "Mỗi kỹ năng phải có từ 1 đến 50 ký tự."
  - `invalidWorkMode()` → "Hình thức làm việc không hợp lệ."

  `GlobalExceptionHandler` map **MỘT** mã lỗi duy nhất `INVALID_PROFILE_FIELD` (400) cho mọi factory —
  đúng cách `InvalidJobFilterException` hiện map một mã `INVALID_JOB_FILTER` cho 5 factory khác nhau,
  không phải một mã riêng cho mỗi factory.
- **R-V3.** `PUT /api/candidates/profile/me` là **thay thế toàn bộ** (không phải patch từng field) —
  frontend luôn tải dữ liệu hiện có (`useMyProfileQuery`) trước khi hiển thị form, và gửi đủ mọi field
  (kể cả field không đổi) khi submit, để không vô tình xoá field không hiển thị trên UI tại thời điểm
  gửi.

## 4. Dữ liệu & quyền truy cập

**Migration `V10__career_profile.sql`** (SQL dự kiến):

```sql
ALTER TABLE candidate_profiles
    ADD COLUMN desired_industry_codes text[] NOT NULL DEFAULT '{}',
    ADD COLUMN desired_location_codes text[] NOT NULL DEFAULT '{}',
    ADD COLUMN desired_work_modes text[] NOT NULL DEFAULT '{}',
    ADD COLUMN skills text[] NOT NULL DEFAULT '{}',
    ADD COLUMN desired_salary_min NUMERIC(14,2),
    ADD COLUMN bio TEXT,
    ADD COLUMN onboarding_completed_at TIMESTAMPTZ,
    ADD COLUMN embedding vector(1536),
    ADD COLUMN embedding_model VARCHAR(100);

ALTER TABLE candidate_profiles
    ADD CONSTRAINT chk_candidate_desired_industry_codes_max CHECK (cardinality(desired_industry_codes) <= 3),
    ADD CONSTRAINT chk_candidate_desired_location_codes_max CHECK (cardinality(desired_location_codes) <= 3),
    ADD CONSTRAINT chk_candidate_desired_work_modes_valid
        CHECK (desired_work_modes <@ ARRAY['ONSITE','HYBRID','REMOTE']::text[]),
    ADD CONSTRAINT chk_candidate_skills_max CHECK (cardinality(skills) <= 20),
    ADD CONSTRAINT chk_candidate_desired_salary_min_nonneg
        CHECK (desired_salary_min IS NULL OR desired_salary_min >= 0),
    ADD CONSTRAINT chk_candidate_bio_length
        CHECK (bio IS NULL OR char_length(bio) <= 500);

-- R-O4: ho so da ton tai coi nhu da qua man onboarding
UPDATE candidate_profiles SET onboarding_completed_at = now() WHERE onboarding_completed_at IS NULL;
```

Không tạo bảng con (đã chốt). Không FK cho phần tử trong mảng — mã ngành/khu vực chỉ validate ở
service qua `CatalogRegistry` (R-F1), đúng tinh thần "không tin AI/client, backend tự kiểm" nhưng ở
đây là validate request thường, không phải validate output AI.

**Entity `CandidateProfile`** — thêm field mới: `desiredIndustryCodes`/`desiredLocationCodes`/
`desiredWorkModes`/`skills` (`@JdbcTypeCode(SqlTypes.ARRAY) @Column(columnDefinition = "text[]")
List<String>`, mặc định `new ArrayList<>()`), `desiredSalaryMin` (`BigDecimal`), `bio` (`String`),
`onboardingCompletedAt` (`Instant`), `embeddingModel` (`String`). **KHÔNG map field `embedding`** (mục
0.a).

**API**

| Endpoint | Request | Response | Lỗi |
|---|---|---|---|
| `GET /api/candidates/profile/me` | — | `CandidateProfileResponse` mở rộng: `desiredIndustries: CatalogResponse.Item[]`, `desiredLocations: CatalogResponse.Item[]` (tra nhãn sẵn, giống `ResumeParsedDataResponse.industry`), `desiredWorkModes: String[]`, `skills: String[]`, `desiredSalaryMinMillions: Integer`, `bio: String`, `onboardingCompletedAt: Instant \| null` | — |
| `PUT /api/candidates/profile/me` | `CandidateProfileRequest` mở rộng (R-V1) | `CandidateProfileResponse` (như trên) | 400 `INVALID_CATALOG_CODE` (R-F1); 400 field errors — `Map<String,String>` (R-V1 Bean Validation, theo đúng `GlobalExceptionHandler.handleValidation` hiện có); 400 `INVALID_PROFILE_FIELD` (R-F3/R-K1/R-M1, R-V2) |
| `PATCH /api/candidates/profile/me/skip-onboarding` | — | `CandidateProfileResponse` | — (idempotent, luôn 200) |
| `GET /api/candidates/profile/me/autofill-from-resume` | — | `ResumeAutofillResponse(String currentTitle, List<String> skills, BigDecimal yearsExperience)` — field `null`/rỗng khi CV không có dữ liệu tương ứng | 409 `NO_PRIMARY_RESUME_PARSED` (R-A1) |

4 endpoint nằm trong `/api/candidates/profile/**`, giữ nguyên bị chặn `hasRole("CANDIDATE")` ở
`SecurityConfig` (R-P2) — không cần `@PreAuthorize` thêm, đúng comment đã có ở
[`ResumeCandidateController.java:25-28`](../../../../backend/src/main/java/com/recruitment/resume/ResumeCandidateController.java).

**Seed demo** (đợt 6):
- `db/seed/seed-demo-structural.sql` thêm dữ liệu `candidate_profiles` cho 8 ứng viên demo:
  `onboarding_completed_at` đặt sẵn (not NULL) cho cả 8; 3 người có `desired_industry_codes`/
  `desired_location_codes`/`desired_work_modes`/`skills`/`desired_salary_min`/`bio` đầy đủ, khác ngành
  nhau (khớp 3 ngành đã có job demo); 5 người còn lại giữ mảng rỗng/`NULL` (hồ sơ mong muốn trống,
  nhưng đã "qua" màn onboarding). `embedding`/`embedding_model` để `NULL` cho cả 8 — scheduler tính khi
  backend chạy với khoá OpenAI thật, theo đúng quy trình `db/seed/README.md`.
- Cập nhật `db/seed/README.md` theo số liệu thật sau khi nạp lại.

## 5. AI

Không gọi LLM ở bất kỳ bước nào của U14. Chỉ dùng `EmbeddingService`/model của FR-U04
(`text-embedding-3-small`) để sinh `embedding` cho hồ sơ (R-E). "Điền từ CV" là sao chép xác định từ
`resume_parsed_data` đã có (R-A), không gọi AI nào mới.

**Không thêm gate consent (FR-U02) cho việc sinh embedding hồ sơ** — áp dụng nguyên lập luận đã chốt ở
walkthrough `fr-u04-recommend.md` mục 4i cho embedding CV: consent FR-U02 bảo vệ ứng viên khỏi việc
bên thứ ba (HR) dùng AI ra quyết định về họ; embedding hồ sơ chỉ phục vụ chính ứng viên (đầu vào
FR-U15, không ai khác đọc được, không dẫn tới quyết định tuyển dụng nào) và không sinh nội dung mới —
nhẹ hơn cả trường hợp CV đã được chấp nhận không gate.

## 6. Ngoài phạm vi

- "Điền sẵn bộ lọc U07 từ mong muốn" — thuộc FR-U15, không phải U14.
- Tính gợi ý việc làm theo mong muốn/embedding hồ sơ — thuộc FR-U15.
- Dùng `dateOfBirth`/`desiredSalaryMin` cho bất kỳ xếp hạng/gợi ý nào (ngoài phạm vi, và `dateOfBirth`
  bị cấm tuyệt đối ở R-G4).
- Thêm trường giới tính, tình trạng hôn nhân, hoặc bất kỳ thông tin nhân thân khác.
- Màn hình quản trị/sửa danh mục ngành nghề, tỉnh/thành (đã xong ở FR-C05).
- Refactor toàn bộ `CandidateProfilePage` sang MD3 — chỉ phần card/field của U14 (UI.md mục 1).
- Gửi email/thông báo nhắc hoàn thiện hồ sơ nếu bỏ qua onboarding.
- Giới hạn "tối đa 3"/"tối đa 20"/"tối đa 1000 triệu" có thể chỉnh bằng cấu hình — là hằng số cố định
  trong code.

## 7. Xong khi

Tất cả lệnh sạch: `cd backend && ./mvnw test` (full suite, đúng một lần ở đợt cuối),
`cd frontend && npm run build`, `cd frontend && npm run lint`. Danh sách test cần thêm:

1. **Mảng mong muốn**: lưu 3 mã ngành hợp lệ → đọc lại đúng nhãn qua `CatalogRegistry`; mã lạ → 400
   `INVALID_CATALOG_CODE`; gửi 4 mã có 1 trùng (dedupe còn 3) → 200 (R-F2); gửi 4 mã KHÁC NHAU → 400
   `INVALID_PROFILE_FIELD` (biên: đúng 3 → 200, 4 → 400). Tương tự cho `desiredLocationCodes`.
2. **Hình thức làm việc**: giá trị hợp lệ nhiều phần tử → 200; giá trị ngoài tập → 400
   `INVALID_PROFILE_FIELD`; trùng lặp → dedupe, 200.
3. **Kỹ năng**: 20 thẻ hợp lệ → 200; 21 thẻ → 400 (biên 20/21); thẻ 50 ký tự → 200, thẻ 51 ký tự → 400
   (biên 50/51); thẻ rỗng sau trim → 400; `["Java","java"]` → lưu `["Java"]`, giữ thứ tự nhập.
4. **Lương mong muốn**: 0, 1000 → 200 (biên); -1, 1001 → 400; để trống → `NULL`, không phải 0.
5. **Bio**: 500 ký tự → 200; 501 → 400 (biên).
6. **Trường dùng lại (R-G)**: PUT chỉ gửi `headline` → không có cột `desired_title` nào được tạo/đụng;
   PUT `yearsExperience` không ảnh hưởng field mong muốn nào.
7. **Onboarding**: hồ sơ mới (từ `AuthService.registerCandidate`) có `onboardingCompletedAt = null`;
   `PUT` thành công lần đầu → cờ được đặt; gọi `PUT` lần 2 → cờ KHÔNG đổi giá trị cũ; `PATCH
   skip-onboarding` trên hồ sơ cờ đang `null` → đặt cờ, không đổi field khác; gọi `skip-onboarding`
   lần 2 (cờ đã có giá trị) → vẫn 200, giá trị cờ không đổi (idempotent).
8. **Điền từ CV**: không có CV chính DONE → 409 `NO_PRIMARY_RESUME_PARSED`; có CV chính DONE → trả
   đúng `currentTitle`/`skills`/năm kinh nghiệm quy đổi tới bước 0,5 (ca cụ thể: 7 tháng → 0,5 năm,
   không phải 0,6 như cách làm tròn 1 chữ số thập phân của C05 sẽ cho; 9 tháng → 1,0; vài mốc quanh
   biên làm tròn 0,25/0,75 theo bước 0,5); `experience_months = null` → không trả field năm kinh
   nghiệm.
9. **Chống lộ cho HR (R-P)**: tạo ứng viên có hồ sơ mong muốn đầy đủ với giá trị ĐÁNH DẤU riêng (ví dụ
   `bio = "MARKER_SHOULD_NOT_LEAK_TO_HR"`, `desiredSalaryMin` một số cụ thể dễ nhận); gọi mọi endpoint
   HR hiện có trả dữ liệu ứng viên (danh sách ứng viên công ty, danh sách đơn ứng tuyển của một job) →
   khẳng định response **không chứa** tên khoá (`desiredIndustryCodes`, `skills`, `bio`,
   `desiredSalaryMin`...) lẫn giá trị đánh dấu. Thêm test: HR gọi `GET/PUT/PATCH
   /api/candidates/profile/**` → 403.
10. **Embedding — văn bản đại diện (R-E1/R-E3)**: đổi `dateOfBirth` (và CHỈ field đó) → `embedding`/
    `embeddingModel` giữ nguyên giá trị cũ (không bị đặt `null`). Đổi `headline`/`skills`/`bio` →
    `embedding` bị đặt `null`. Hồ sơ cả 3 trường rỗng → không nằm trong tập quét của scheduler (R-E4,
    test điều kiện quét bằng câu SQL trực tiếp, không qua mock).
11. **Embedding — ghi thành công (R-E5)**: hồ sơ KHÔNG bị sửa giữa chừng → UPDATE có điều kiện ghi
    được: rowcount 1, `embedding` khác `NULL` và đúng vector trả về, `embeddingModel` đúng tên model.
    `expectedUpdatedAt` trong test phải đọc bằng CHÍNH truy vấn native đọc dữ liệu hồ sơ (giống cách
    orchestrator thật sẽ đọc), **không** tự tạo bằng `Instant.now()`/đồng hồ Java rồi giả định trùng
    khớp với giá trị Postgres đã lưu (lệch độ chính xác timestamp giữa Java và Postgres có thể làm
    test pass giả). Mock ở tầng `EmbeddingModel` — đúng tiền lệ `ResumeEmbeddingOrchestratorTest`
    ("mock model thật thấp nhất, để `EmbeddingService` thật chạy nguyên"), **không** mock
    `EmbeddingService` trực tiếp.
12. **Race ghi embedding (R-E5)** — ca thất bại: cùng cách mock ở `EmbeddingModel` như mục 11; mô
    phỏng hồ sơ bị sửa (đổi `updated_at`) GIỮA lúc đọc `expectedUpdatedAt` và lúc gọi `save` của state
    service → rowcount 0, `embedding` KHÔNG bị ghi đè bằng vector cũ (đối chứng trực tiếp với race đã
    ghi nợ ở `ResumeParsedDataRepository`).
13. **Mảng qua Hibernate (mục 0.a)**: lưu rồi đọc lại entity qua `JpaRepository` (không native) →
    `desiredIndustryCodes` v.v. đúng giá trị; context `@SpringBootTest` khởi động thành công (qua được
    `ddl-auto: validate`) là một phần bằng chứng.
14. **Danh sách null (R-V1)**: request gửi `null` tường minh cho `desiredIndustryCodes`/
    `desiredLocationCodes`/`desiredWorkModes`/`skills` → 200, lưu thành mảng rỗng, không 500 (không
    NPE khi dedupe, không vi phạm `NOT NULL` khi ghi `text[]`). Request kiểu CŨ — JSON chỉ có đúng 5
    field gốc FR-U01, hoàn toàn vắng mặt 4 field mảng + `desiredSalaryMinMillions`/`bio` — vẫn 200
    (tương thích ngược).
15. **Bẫy flush Hibernate (R-E3)**: `PUT` đổi `headline` (khác văn bản đại diện cũ, nên `embedding`
    phải bị đặt `null`) → đọc lại qua `GET /me` ngay sau đó, khẳng định **CẢ HAI** cùng đúng trong một
    lần đọc: `headline` đã là giá trị MỚI **VÀ** `embedding`/`embeddingModel` đã là `null` — phản
    chứng đúng bẫy `clearAutomatically` xoá persistence context trước khi entity kịp flush (nếu thiếu
    `saveAndFlush`, test này đỏ vì `headline` vẫn là giá trị cũ dù response trả 200).
16. **RBAC/sở hữu**: đã có sẵn ở `/api/candidates/profile/**` (FR-U01) — chỉ cần test bổ sung ở mục 9
    (HR → 403), không cần test RBAC mới khác.

## 8. AI hay làm sai (dành cho người code)

- Tạo cột `desired_title` mới trùng nghĩa với `headline` đã có — R-G1 đã chốt dùng lại `headline`,
  không tạo field song song.
- Đưa `dateOfBirth`/`desiredSalaryMin`/tên ứng viên vào văn bản đại diện (R-E1) — chỉ `headline` +
  `skills` + `bio`.
- Ghi `embedding` bằng UPDATE **không điều kiện** như tiền lệ `ResumeParsedDataRepository.
  updateEmbedding` — U14 PHẢI dùng điều kiện `updated_at = :expectedUpdatedAt` (R-E5), đây là điểm
  khác biệt CÓ CHỦ ĐÍCH với tiền lệ CV, không phải "cho nhất quán" rồi copy nguyên lỗi đã biết.
- Map field `embedding` vào entity `CandidateProfile` bằng `@JdbcTypeCode`/kiểu tự định nghĩa — tiền
  lệ dự án (xác nhận bằng bytecode `spring-ai-pgvector-store`) là KHÔNG map, chỉ qua native query (mục
  0.a).
- Gọi native UPDATE đặt `embedding = NULL` (`@Modifying(clearAutomatically = true)`) NGAY SAU
  `repository.save(profile)` mà không `saveAndFlush`/flush tường minh trước — `clearAutomatically` xoá
  persistence context, nếu thay đổi entity (`headline`...) chưa kịp flush xuống DB thì MẤT LUÔN, không
  phải lỗi ở lần chạy sau mà mất ngay trong chính request đó (R-E3, test mục 7.15).
- Đọc `expectedUpdatedAt` (R-E5) bằng `Instant.now()`/đồng hồ Java thay vì đọc lại chính giá trị
  `updated_at` đã lưu trong Postgres — hai nguồn thời gian khác độ chính xác, so sánh lệch sẽ khiến
  điều kiện `WHERE updated_at = :expectedUpdatedAt` không bao giờ khớp (rowcount luôn 0) hoặc khớp sai
  (test mục 7.11 sẽ lộ nếu mock sai tầng).
- Điều hướng `navigate('/candidate')` ở trang onboarding TRƯỚC khi `queryClient.setQueryData` ghi
  xong response mới vào cache `useMyProfileQuery` — `RequireCandidateProfileOnboarding` ở `/candidate`
  có thể đọc lại cache cũ (`onboardingCompletedAt` vẫn `null`) và đưa thẳng về lại
  `/candidate/onboarding`, tạo vòng lặp chuyển hướng (R-O2).
- Cho wrapper `RequireCandidateProfileOnboarding` render con hoặc điều hướng khi `useMyProfileQuery`
  còn `isLoading` — phải hiện trạng thái đang tải và KHÔNG quyết định gì cho tới khi có dữ liệu chắc
  chắn mới nhất (R-O2).
- Trả `desiredIndustryCodes`/`skills`/`bio`/`desiredSalaryMin`/`onboardingCompletedAt` trong bất kỳ
  DTO phía HR (R-P1) — kiểm bằng test mục 7.9, không chỉ tin bằng mắt.
- Kiểm số lượng mảng TRƯỚC khi dedupe — từ chối nhầm input hợp lệ có phần tử trùng (R-F2 phải dedupe
  trước khi đếm).
- Dùng field `experience.years` đã làm tròn (1 chữ số thập phân, R-E8 C05) để tính "số năm kinh
  nghiệm" điền từ CV — phải tính lại từ `experience_months` thô để làm tròn đúng bước 0,5 (R-A1),
  tránh làm tròn hai lần sai số.
- Sửa `V10__career_profile.sql` sau khi đã áp vào DB dev (đổi checksum Flyway, giống bài học V6/V7 ghi
  ở ROADMAP) — **KHÔNG chạy `./mvnw spring-boot:run` hay bất kỳ lệnh áp migration vào DB dev nào cho
  tới khi `./mvnw test` (đợt cuối) xanh hoàn toàn**; nếu phát hiện sai sau khi đã áp, sửa bằng migration
  V11 mới, không sửa V10.
- Tự thêm cơ chế đếm lần thử/backoff cho scheduler embedding hồ sơ — FR-U04 chưa có cơ chế này cho CV
  (mục 0.b), U14 không tự thêm một chuẩn mới riêng.
- Cho "Điền từ CV" tự lưu xuống DB ngay — chỉ điền vào form, chờ người dùng bấm "Lưu" (R-A2).
- Để "Bỏ qua" (`skip-onboarding`) vô tình ghi đè `embedding`/field khác — endpoint này chỉ đụng đúng
  một cột `onboarding_completed_at`.
- Tạo một mã lỗi `INVALID_PROFILE_FIELD` riêng cho mỗi factory method — đúng tiền lệ
  `InvalidJobFilterException` là MỘT mã lỗi chung, message khác nhau qua `ex.getMessage()` (R-V2).
- Thêm `employment_type`, bí danh tìm kiếm, hay bất kỳ field ngoài danh sách đã chốt "vì đang sửa file
  này".

## 9. Kế hoạch chia đợt

Mỗi đợt một commit; dừng → báo cáo diff → chờ duyệt → mới commit (CLAUDE.md §6). Giữa các đợt backend
chỉ `test-compile`; **KHÔNG chạy `spring-boot:run` hay bất kỳ lệnh chạm DB dev nào (kể cả chạy tay
ngoài quy trình đợt, kể cả do người dùng tự chạy để "xem thử") tới khi đợt test backend (đợt 4) xanh**
(mục 8, bài học V6/V7) — áp dụng cho CẢ người review, không chỉ người code; nếu cần xem thử giữa
chừng, dùng test (`mvnw test -Dtest=...`), không khởi động app trỏ vào DB dev. `mvnw test` full suite
chạy đúng một lần ở đợt cuối. Frontend mỗi đợt chạy đủ `npm run build` + `npm run lint`.

1. **Backend — migration + entity + kiểm ngay (mục 0.a).** `V10__career_profile.sql`; sửa
   `CandidateProfile` entity (field mảng qua `@JdbcTypeCode(SqlTypes.ARRAY)` + field thường, KHÔNG map
   `embedding`); `CandidateProfileRequest`/`Response` mở rộng; `InvalidProfileFieldException`.
   `.\mvnw.cmd -q test-compile` để kiểm biên dịch, SAU ĐÓ bắt buộc chạy riêng
   `.\mvnw.cmd test "-Dtest=BackendApplicationTests"` (lớp `@SpringBootTest` sẵn có, chỉ
   `contextLoads()`, không phải full suite) — xác nhận Flyway áp được V10 trên Testcontainers thật và
   `ddl-auto: validate` chấp nhận ánh xạ `text[]` mới, đúng tinh thần C05 bắt buộc kiểm Java migration
   V9 ngay ở đợt đầu. Lớp test này KHÔNG chạm DB dev (Testcontainers tự dựng Postgres riêng).
2. **Backend — service + validate + 2 endpoint mới.** `CandidateProfileService` (dedupe R-F2/R-K2,
   validate catalog R-F1, coi mảng `null` trong request là rỗng R-V1, set cờ onboarding R-O3, so văn
   bản đại diện + `saveAndFlush` trước native UPDATE R-E3), `skip-onboarding`, `autofill-from-resume`,
   `CatalogResponse.Item` cho desired industries/locations trong response. `.\mvnw.cmd -q test-compile`.
3. **Backend — embedding scheduler.** Hàm dựng văn bản đại diện thuần (R-E1),
   `CandidateProfileEmbeddingStateService` (native UPDATE có điều kiện, R-E5),
   `CandidateProfileEmbeddingOrchestrator`, `CandidateProfileEmbeddingScheduler` (theo khuôn
   `ResumeEmbeddingScheduler`). `.\mvnw.cmd -q test-compile`.
4. **Backend — test.** Toàn bộ nhóm test mục 7 (trừ RBAC đã có sẵn), gồm ca thành công + ca race của
   R-E5 (mục 7.11-12) và ca bẫy flush của R-E3 (mục 7.15). Chạy riêng các lớp test MỚI bằng tay (`mvnw
   test -Dtest=...`) để bắt sớm lỗi ánh xạ `text[]`/SQL — CHƯA chạy full suite ở đợt này.
5. **Frontend — component dùng chung + trang `/candidate/profile`.** `CatalogMultiCombobox`,
   `SkillTagInput`, `CareerPreferencesFields`, card "Thông tin cơ bản" (đổi nhãn `location` → "Nơi ở
   hiện tại", R-G3) + card "Nghề nghiệp và mong muốn công việc" trong `CandidateProfilePage`. `npm run
   build` + `npm run lint`.
6. **Frontend — trang `/candidate/onboarding` + wrapper.** Trang onboarding (tái dùng
   `CareerPreferencesFields` của đợt 5), `RequireCandidateProfileOnboarding` (R-O2, gồm xử lý trạng
   thái đang tải + ghi cache trước khi điều hướng), gắn wrapper vào nhóm route `/candidate/*`.
   **Không sửa `RegisterForm.tsx`** (R-O1 đã chốt không đổi). `npm run build` + `npm run lint`.
7. **Seed demo.** Cập nhật `seed-demo-structural.sql`, `db/seed/README.md`. Nạp lại theo
   `db/seed/README.md` mục 1 — **lần đầu tiên trong nhánh này được phép chạm DB dev**, chỉ sau khi đợt
   4 đã xanh.
8. **Đợt cuối.** `mvnw test` full suite + `npm run build` + `npm run lint`; soát tay desktop và 375px
   (ghi walkthrough) — **sửa `docs/UI_GUIDE.md` mục 7 dòng `/register`**: cột "Màn hình" đang ghi
   "Đăng ký (xong → ★`/candidate/onboarding`)" phải sửa lại cho khớp R-O1 (không còn "xong →" nữa, xem
   UI.md mục 2); dòng `/candidate/profile`/★`/candidate/onboarding` đã đúng sẵn, không cần sửa; skill
   `srs-guard`; viết `docs/walkthrough/fr-u14-career-profile.md`; đổi trạng thái `ĐÃ HOÀN THÀNH <ngày>`
   ở đây và ở dòng FR-U14 mục 0 `docs/SRS.md`; tick `docs/ROADMAP.md`.

## 10. Nợ kỹ thuật dự kiến

- Scheduler embedding hồ sơ không có claim/stale-reaper, giống đúng khoản nợ đã ghi ở FR-U04 cho
  CV/Job — an toàn 1 instance, có thể sinh embedding trùng ở đa instance (tốn API, không sai dữ liệu).
- `embedding_model` không có giá trị cho hồ sơ chưa được scheduler xử lý lần nào (`NULL` tới khi có
  embedding) — không phải lỗi, chỉ là thứ tự thời gian tự nhiên.
- Giới hạn "tối đa 3"/"tối đa 20"/"tối đa 1000 triệu" là hằng số cứng — đổi sau này cần sửa code +
  migration CHECK constraint, không có cấu hình.
- `CatalogMultiCombobox` kế thừa hạn chế tìm theo nhãn (không bí danh) của `CatalogCombobox` (C05).
