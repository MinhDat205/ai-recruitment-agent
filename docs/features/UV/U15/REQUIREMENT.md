# FR-U15 — Gợi ý việc làm theo hồ sơ (mở rộng FR-U04)

> Trạng thái: ĐÃ DUYỆT 2026-10-03.

- Nhóm: Ứng viên
- Tóm tắt: Khối "Gợi ý cho bạn" trên trang Việc làm, tính trực tiếp mỗi lần gọi (không bộ đệm), dựa trên mong muốn + CV (hoặc hồ sơ nghề nghiệp nếu chưa có CV); thay hẳn cơ chế cache của FR-U04
- Phụ thuộc: FR-U14, FR-U07, FR-C05, FR-U04 — cả 4 đều **Đã hoàn thành** (`docs/SRS.md` mục 0)
- Nhánh: `feat/fr-u15-profile-recommend`
- Mở rộng: FR-U04 (gỡ bộ đệm `job_recommendations`, thay bằng tính trực tiếp), FR-U07 (mở rộng `PublicJobSearchCriteria`/`PUBLIC_JOB_FILTER_WHERE` sang nhiều mã ngành/khu vực, dùng lại cho cả `GET /api/public/jobs` và gợi ý), FR-U14 (đọc mong muốn + embedding hồ sơ)

## 0. Đã kiểm trước khi viết đặc tả (CLAUDE.md §6 — đọc code trước khi đoán)

### (a) 7 test cũ của FR-U04 — tên, ý nghĩa, vị trí

| # | File:dòng | Tên test | Ý nghĩa |
|---|---|---|---|
| 1 | `JobRecommendationCacheServiceTest.java:163` | `refreshOne_jobsAboveAndBelowThreshold_onlyAboveThresholdCached` | Job trên ngưỡng 0.40 được cache, job dưới ngưỡng bị loại (nhánh CV) |
| 2 | `JobRecommendationCacheServiceTest.java:186` | `refreshOne_candidateWithoutPrimaryResume_doesNothing` | Ứng viên không có CV chính → không ghi gì vào cache |
| 3 | `JobRecommendationCacheServiceTest.java:218` | `refreshOne_calledAgainAfterJobClosed_removesClosedJobFromCache` | Job đang trong cache bị HR đóng → lượt làm mới kế tiếp tự loại job đó |
| 4 | `JobRecommendationCacheServiceTest.java:258` | `refreshOne_candidateITResume_doesNotRecommendAccountingJob` | CV ngành IT không gợi ý job Kế toán (ngưỡng 0.40 tách đúng hai nhóm bằng vector mô phỏng thực nghiệm 0.49/0.355) |
| 5 | `JobRecommendationCandidateControllerIntegrationTest.java:155` | `getRecommendations_noCache_returnsEmptyList` | Chưa có cache → API trả danh sách rỗng, không lỗi |
| 6 | `JobRecommendationCandidateControllerIntegrationTest.java:168` | `getRecommendations_hasCachedRecommendations_returnsOrderedWithoutSimilarityScoreField` (phần **thứ tự**) | Job similarity cao hơn xuất hiện trước job similarity thấp hơn trong JSON |
| 7 | `JobRecommendationCandidateControllerIntegrationTest.java:168` | `getRecommendations_hasCachedRecommendations_returnsOrderedWithoutSimilarityScoreField` (phần **không lộ điểm**) | JSON trả về không chứa `similarityScore`/`matchScore` ở bất kỳ dạng nào |

Test #6 và #7 là **hai khẳng định trong cùng một method** — bảng đối chiếu ở mục 7 giữ nguyên cách gộp này ở test mới.

### (b) Mọi chỗ ngoài code Java nhắc tới `job_recommendations`

| File:dòng | Nội dung | Xử lý khi code |
|---|---|---|
| `backend/src/main/resources/db/migration/V1__init_schema.sql:313,322` | `CREATE TABLE job_recommendations` + `CREATE INDEX idx_reco_candidate` | Không sửa V1 (bất biến) — xoá bằng V11 (mục 4) |
| `db/seed/reset-demo-db.sql:98` | `job_recommendations` trong danh sách bảng `TRUNCATE` khi reset demo | Xoá tên bảng khỏi danh sách (đợt 8) |
| `db/seed/export-ai-output.ps1:47` | `'job_recommendations'` trong danh sách bảng export sang `seed-demo-ai-output.sql` | Xoá khỏi danh sách (đợt 8) |
| `db/seed/seed-demo-ai-output.sql:431,455` | Khối `COPY`/`INSERT` rỗng (0 dòng) cho `job_recommendations` | Xoá khối này **bằng tay trực tiếp trong file** (đợt 8) — **không** chạy lại `export-ai-output.ps1` để tránh rủi ro đổi cả phần dump còn lại không liên quan |
| `db/seed/README.md:168,196,202,229` | Bảng số dòng `job_recommendations = 28`, ghi chú R-D1 của FR-U07, câu lệnh kiểm `SELECT count(*) FROM job_recommendations` | Viết lại mục 6 — bảng không còn tồn tại, không còn số dòng để đo (đợt 8) |
| `docs/features/UV/U07/REQUIREMENT.md:27,304` | Lịch sử quyết định R-D1 (chuyển 6 job DRAFT→OPEN để đo cache) | **Không sửa** — tài liệu đã `ĐÃ HOÀN THÀNH`, ghi lại việc đã xảy ra ở thời điểm đó, không mô tả trạng thái hiện tại |
| `docs/walkthrough/chore-seed-demo.md:96` | Giải thích ca demo ngưỡng 0.40 bằng cache rỗng | **Không sửa** — walkthrough lịch sử |
| `docs/walkthrough/fr-c05-catalog.md:244` | Ghi chú 6 job DRAFT làm cache luôn về 0 | **Không sửa** — walkthrough lịch sử |
| `docs/walkthrough/fr-u04-recommend.md:60,181,388` | Bảng file code, sequence diagram, bảng đối chiếu PHASES.md của F1 | **Không sửa** — walkthrough lịch sử của nhánh đã xong; FR-U15 viết walkthrough riêng ở đợt cuối |
| `docs/walkthrough/fr-u07-job-filter.md:252` | Số đo thật `job_recommendations = 28` sau khi 6 job demo chuyển OPEN | **Không sửa** — walkthrough lịch sử |
| `docs/ROADMAP.md:182,546,549` | 3 chỗ nhắc nợ kỹ thuật/số đo liên quan `job_recommendations` | Cập nhật ở đợt cuối (ngoài phạm vi 2 file đặc tả này) khi tick ROADMAP, không sửa bây giờ |

Ngoài phạm vi dự án: `cot.txt` (file chưa track trong git, ở thư mục gốc) có đoạn liệt kê schema `job_recommendations` (dòng 91-96) — đây là file scratch của người dùng, không thuộc tài liệu dự án, **không đụng tới**.

### (c) Chữ ký `searchByCriteria` và mọi nơi gọi

- `JobPublicService.java:146-186` — method **package-private** (không `public`):
  ```java
  Page<Job> searchByCriteria(
          String titlePattern, String locationPattern, String categoryPattern,
          PublicJobSearchCriteria criteria, JobSortOption sort, Pageable pageable)
  ```
  Gọi `jobRepository.searchPublicJobsSortedByNewest(...)` hoặc `...SortedBySalaryDesc(...)` (12 tham số mỗi bên, `JobRepository.java:82-120`) theo `switch` trên `sort` — đúng R-O4 (không nối chuỗi).
- **Nơi gọi `searchByCriteria`**: chỉ một chỗ — `JobPublicService.search(...)` (13-tham số, dòng 83-141), chính là điểm vào của `GET /api/public/jobs` qua `JobPublicController.java:28-56`.
- **`PublicJobSearchCriteria`** (`PublicJobSearchCriteria.java:12-24`): record package-private, field `categoryCode`/`locationCode` là **một String**, không phải danh sách.
- **`searchOpenJobsNewest`** (`JobRepository.java:127-130`, default method) — **không** đi qua
  `searchByCriteria`, gọi trực tiếp `searchPublicJobsSortedByNewest` với toàn bộ tham số là "không
  lọc" (`null...null, false, false, List.of("__NONE__"), null`). Dùng bởi **duy nhất**
  `CvImprovementOrchestrator.java:75` (`MARKET_TREND_JOB_LIMIT` job OPEN mới nhất, không lọc gì —
  văn bản xu hướng thị trường cho FR-U05). **Đã chốt (sửa so với bản trước)**: câu "FR-U15 không
  đụng `searchOpenJobsNewest`" ở bản trước là **sai** — `searchPublicJobsSortedByNewest` tăng từ 12
  lên 14 tham số (R-H2) buộc `searchOpenJobsNewest` phải sửa CƠ HỌC theo (gọi với 2 tham số "không
  lọc" mới — `categoryCodesPresent=false`, `locationCodesPresent=false` — thay cho `categoryCode=
  null`/`locationCode=null` cũ). Chữ ký của `searchOpenJobsNewest()` (không tham số, trả
  `Page<Job>`) **không đổi**, hành vi (trả mọi job OPEN, không lọc gì) **không đổi**. **`CvImprovementOrchestrator`
  không cần sửa gì** — nó chỉ gọi `searchOpenJobsNewest()`, không biết/không phụ thuộc số tham số
  bên trong.
- **`JobPublicServiceTest.java:45,60`** gọi trực tiếp overload 5-tham số (`search(keyword, location, category, page, size)`) — overload này **không đổi chữ ký**, chỉ truyền `null` cho mọi tham số mới (bao gồm hai danh sách mã mới thay cho hai String cũ).
- **Đã chốt (trước đây là "Cần người dùng quyết định" #1, xem mục "Đã chốt" cuối file)**:
  **3 lời gọi** (không phải 2) khớp đúng 12 tham số hiện tại —
  `JobPublicServiceTest.java:39-41` (`when(...)` của `search_oversizedSize_isClampedToMax`),
  `JobPublicServiceTest.java:54-56` (`when(jobRepository.searchPublicJobsSortedByNewest(isNull(),
  any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), any(), any()))`) và
  `JobPublicServiceTest.java:62-65` (`verify(jobRepository).searchPublicJobsSortedByNewest(...)`,
  cùng 12 matcher, của `search_blankKeyword_passesNullPattern`) — **cả ba lời gọi** (2 stub + 1
  verify) dùng **đúng 12 matcher** cho 12 tham số hiện tại. Mở rộng `categoryCode`/`locationCode`
  từ String sang danh sách theo đúng khuôn `workMode` (cần thêm cờ `...CodesPresent` để tránh
  `IN ()` rỗng, xem mục 4) làm số tham số của phương thức này tăng từ 12 lên 14 → **cả ba lời gọi**
  không còn khớp số lượng tham số. **Đã được duyệt sửa CƠ HỌC**: thêm đúng 2 `anyBoolean()` vào cả
  ba lời gọi (ở vị trí `categoryCodesPresent`/`locationCodesPresent`), **giữ nguyên `isNull()` ở
  vị trí `titlePattern`** (chỉ áp dụng cho 2 lời gọi có `isNull()`), **không đổi bất kỳ
  `assertThat(...)` nào** trong 2 test này (3 lời gọi, R-H7, mục 3.2).

## 1. Mục đích

Chủ động đưa một số việc làm phù hợp lên trang Việc làm, thay vì ứng viên phải tự tìm từ đầu. Mở
rộng FR-U04: gợi ý được cả khi ứng viên chưa có CV (dựa trên hồ sơ nghề nghiệp FR-U14), và hệ thống
chỉ còn **một nguồn gợi ý duy nhất** — gỡ bộ đệm `job_recommendations` của FR-U04, tính trực tiếp
mỗi lần ứng viên mở trang.

## 2. Luồng người dùng

**Ứng viên đã đăng nhập** — `/candidate` (trang Việc làm)
1. Mở trang → khối "Gợi ý cho bạn" (tối đa 6 việc) nằm **trên** danh sách tìm kiếm chính, chỉ hiện
   khi đang ở trang 1, chưa gõ từ khoá, chưa áp bộ lọc nào (mục 3.7 R-L).
2. Mỗi thẻ gợi ý ghi các điều kiện đã khớp trong một dòng văn bản trung tính duy nhất, nhãn "Khớp
   mong muốn:" + các điều kiện nối bằng "·" (ví dụ "Khớp mong muốn: Hà Nội · CNTT · Toàn thời gian
   · Lương đạt mong muốn").
3. Bấm "Xem tất cả" → mở `/candidate` không khối gợi ý, bộ lọc U07 điền sẵn ngành + khu vực mong
   muốn nếu có khai (mục 3.10 R-X), ứng viên sửa tiếp được. Không khai ngành lẫn khu vực nào →
   **ẩn hẳn nút này** ở `/candidate` (R-X3).
4. Chưa có hồ sơ, CV, hay mong muốn nào → khối hiện lời mời hoàn thiện hồ sơ, dẫn tới
   `/candidate/profile`.

**Ứng viên đã đăng nhập** — `/candidate/dashboard` (Bảng tin)
1. Khối gợi ý giống hệt nội dung/logic trên (tối đa 6 việc, dùng lại cùng component
   `RecommendedJobs`). **Luôn** có nút "Xem tất cả" (khác `/candidate` — ở đây không ẩn): có khai
   mong muốn → điền sẵn bộ lọc như trên; không khai gì → mở `/candidate` trần, không tham số lọc
   (R-X3).

**Hệ thống**
- Mỗi lần gọi API: xác định nguồn dữ liệu theo thứ tự ưu tiên (mục 3.5 R-V), lọc cứng theo ngành/
  khu vực mong muốn (mục 3.2 R-H) và loại bỏ job đã có đơn trong chu kỳ hiện tại (mục 3.4 R-C), xếp
  hạng, trả về kèm trạng thái + nguồn tường minh (mục 3.6 R-S).
- Không gọi LLM/embedding đồng bộ ở bất kỳ bước nào (chỉ đọc vector đã có sẵn).

## 3. Quy tắc nghiệp vụ

### 3.1 Gỡ bộ đệm FR-U04 (R-G)

- **R-G1.** Xoá hẳn: entity `JobRecommendation.java`, repository `JobRecommendationRepository.java`,
  `JobRecommendationCacheService.java`, `JobRecommendationCacheScheduler.java`, cấu hình
  `app.job-recommendation.*` ở `application.yml`/`application-test.yml`. Migration `V11` (mục 4)
  `DROP TABLE job_recommendations`.
- **R-G2.** 2 file test cũ (`JobRecommendationCacheServiceTest.java`,
  `JobRecommendationCandidateControllerIntegrationTest.java`) bị xoá, thay bằng test mới theo đúng
  bảng đối chiếu ở mục 7 — **đây là ngoại lệ đã duyệt** của quy tắc "không xoá/nới test" (CLAUDE.md
  §6), áp dụng **chỉ cho 7 ý nghĩa đã liệt kê ở mục 0.a**. Không nhân cơ hội này xoá hay nới bất kỳ
  test nào khác trong dự án.
- **R-G3.** Giữ nguyên route `GET /api/candidates/job-recommendations` và tên file
  `JobRecommendationCandidateController.java` — chỉ đổi nội dung bên trong (mục 3.6 R-S, mục 4).
  `JobRecommendationCandidateService.java` viết lại hoàn toàn (không còn đọc cache, tính trực
  tiếp) — **giữ nguyên tên file này** (đã chốt, không đổi tên dù vai trò đổi từ "đọc cache" sang
  "tính trực tiếp"). Lớp test mới tương ứng đặt tên `JobRecommendationCandidateServiceTest` (mục
  7, bảng đối chiếu).

### 3.2 Điều kiện cứng — mở rộng nhiều mã ngành/khu vực (R-H)

- **R-H1.** Mở rộng `PublicJobSearchCriteria` (`PublicJobSearchCriteria.java`): `categoryCode`/
  `locationCode` (String) → `categoryCodes`/`locationCodes` (`List<String>`). Giữ nguyên các field
  khác (salary/workModes/hideUnlisted/sinceTimestamp).
- **R-H2.** `PUBLIC_JOB_FILTER_WHERE` (`JobRepository.java:44-73`) đổi hai điều kiện categoryCode/
  locationCode sang dạng danh sách, theo đúng khuôn `workMode` đã có (cờ "Present" + sentinel khi
  rỗng, vì Postgres không nhận `IN ()` rỗng):
  ```sql
  AND (:categoryCodesPresent = FALSE OR j.category_code IN :categoryCodeParams OR j.category_code IS NULL)
  AND (:locationCodesPresent = FALSE OR j.location_code IN :locationCodeParams OR j.location_code IS NULL)
  ```
  Giữ nguyên `OR j.category_code IS NULL`/`OR j.location_code IS NULL` ở cuối mỗi điều kiện — R-N
  (mục 3.3 dưới) không đổi, chỉ đổi cách so khớp phần "có giá trị" từ `=` sang `IN`.
- **R-H3.** `JobPublicController`/`JobPublicService.search(...)`: `categoryCode`/`locationCode` đổi
  từ `String` sang `List<String>` (lặp tham số, giống `workMode` — `@RequestParam(required = false)
  List<String> categoryCode`). **Một giá trị vẫn hợp lệ như cũ** (`?categoryCode=A` → danh sách 1
  phần tử, hành vi y hệt trước khi đổi).
- **R-H4.** Sau khi khử trùng (dedupe, cùng nguyên tắc R-F2 của FR-U14 — theo đúng giá trị mã, giữ
  thứ tự xuất hiện đầu tiên), mỗi danh sách tối đa **3** mã. Vượt quá → 400 `INVALID_JOB_FILTER`,
  hai factory mới trên `InvalidJobFilterException`:
  - `tooManyCategoryCodes()` → "Chỉ được chọn tối đa 3 ngành nghề."
  - `tooManyLocationCodes()` → "Chỉ được chọn tối đa 3 khu vực."
- **R-H5.** Mỗi mã trong danh sách vẫn validate qua `CatalogRegistry.isIndustry`/`isProvince` (lặp
  qua từng phần tử) — mã lạ → 400 `INVALID_CATALOG_CODE` (giữ nguyên hành vi).
- **R-H6.** `PublicJobSearchCriteria`/`PUBLIC_JOB_FILTER_WHERE` sau khi mở rộng được **FR-U15 dùng
  lại trực tiếp** cho câu truy vấn gợi ý (mục 3.5 R-V) — không viết điều kiện cứng riêng.
- **R-H7. Đã chốt — sửa cơ học 3 lời gọi trong 2 test `JobPublicServiceTest` (không phải "sửa
  khẳng định").** Việc tăng tham số `searchPublicJobsSortedByNewest`/`SortedBySalaryDesc` từ 12
  lên 14 (R-H2) làm `JobPublicServiceTest.java:39-41` (`when(...)` của
  `search_oversizedSize_isClampedToMax`), `:54-56` (`when(...)` của
  `search_blankKeyword_passesNullPattern`) và `:62-65` (`verify(...)` cùng test) không còn khớp số
  lượng matcher. Sửa bằng cách thêm đúng 2 `anyBoolean()` vào mỗi lời gọi (vị trí
  `categoryCodesPresent`/`locationCodesPresent`), **giữ nguyên `isNull()`** ở vị trí
  `titlePattern` (chỗ nào đang có), **không đổi bất kỳ `assertThat(...)`** nào trong file này hay
  bất kỳ test khác. Nếu đợt 1 làm đỏ bất kỳ test U07 nào KHÁC ngoài đúng 3 lời gọi này vì lý do
  khác (không phải đổi số lượng tham số thuần tuý) → vẫn **DỪNG và báo**, không tự sửa.

### 3.3 Mã NULL vẫn hiện (R-N — kế thừa FR-U07, không đổi ý nghĩa)

- **R-N1.** Tin `category_code IS NULL`/`location_code IS NULL` (chưa chuẩn hoá, FR-C05 R-J2) vẫn
  lọt qua điều kiện cứng ở cả `GET /api/public/jobs` và gợi ý U15 — không âm thầm loại dữ liệu
  thiếu (SRS mục 2). Nhãn "Chưa chuẩn hoá" (`UnnormalizedBadge`) **không** áp dụng cho khối gợi ý
  U15 (phạm vi nhãn đó chỉ ở danh sách tìm kiếm có lọc chủ động, FR-U07 R-N3) — thẻ gợi ý chỉ hiện
  điều kiện ĐÃ khớp (mục 3.8 R-M), không hiện nhãn "thiếu dữ liệu".

### 3.4 Loại trừ đơn đã nộp trong chu kỳ hiện tại (R-C)

- **R-C1.** Một job bị loại khỏi gợi ý nếu tồn tại `job_applications` có cùng `job_id`, cùng
  `candidate_id`, và `recruitment_cycle` bằng `recruitment_cycle` **hiện tại** của job đó — **bất
  kể trạng thái đơn**, kể cả `WITHDRAWN`. Xác nhận bằng code thật: `ApplicationService.withdraw`
  (`ApplicationService.java:89-110`) chỉ đổi `status`, không đổi `recruitment_cycle`, không xoá
  hàng — đúng ngữ nghĩa ràng buộc `uq_application_per_cycle UNIQUE (job_id, candidate_id,
  recruitment_cycle)` (`V1__init_schema.sql:222`): một khi đã có đơn (dù đã rút) trong chu kỳ đó,
  không nộp lại được chu kỳ đó nữa, nên gợi ý cũng không nên đề xuất lại.
  ```sql
  AND NOT EXISTS (
      SELECT 1 FROM job_applications ja
      WHERE ja.job_id = j.id AND ja.candidate_id = :candidateId
        AND ja.recruitment_cycle = j.recruitment_cycle
  )
  ```
- **R-C2.** Điều kiện này **chỉ** áp dụng cho truy vấn gợi ý — không đụng `GET /api/public/jobs`
  (ứng viên tìm kiếm chủ động vẫn thấy mọi job OPEN, kể cả job đã ứng tuyển).

### 3.5 Chọn nguồn vector — thứ tự ưu tiên (R-V)

- **R-V1. "CV sẵn sàng"** = ứng viên có `resumes.is_primary = true`, `parse_status <> 'FAILED'`, và
  `resume_parsed_data.embedding IS NOT NULL`. `FAILED` bị loại vì không bao giờ tự có embedding
  (không có job nền thử lại tự động cho `FAILED` theo mục 0.b FR-U14 — hành vi tương tự được áp
  dụng ở đây: `FAILED` coi như "không có CV" để chuyển sang nhánh tiếp theo, không phải "đang chờ").
- **R-V2. "CV đang chờ"** = có `resumes.is_primary = true`, `parse_status <> 'FAILED'`, nhưng
  `resume_parsed_data.embedding IS NULL` (đang parse, hoặc đã `DONE` nhưng
  `ResumeEmbeddingScheduler` chưa kịp xử lý — cửa sổ thời gian đã ghi nợ ở ROADMAP
  `fix/candidate-empty-states`).
- **R-V3. "Hồ sơ sẵn sàng"** = `candidate_profiles` có văn bản đại diện khác rỗng (headline/skills/
  bio, đúng điều kiện quét R-E4 của FR-U14) **và** `embedding IS NOT NULL`.
- **R-V4. "Hồ sơ đang chờ"** = văn bản đại diện khác rỗng nhưng `embedding IS NULL` (U14 R-E6: luôn
  tự thử lại vô hạn ở lượt poll kế tiếp, không có trạng thái lỗi vĩnh viễn như CV `FAILED`).
- **R-V5. "Có mong muốn"** = `desired_industry_codes` hoặc `desired_location_codes` khác mảng rỗng.
- **R-V6. Thứ tự chọn nguồn cho MỖI lần gọi API** (không cache, tính lại từ đầu mỗi lần):
  1. CV sẵn sàng (R-V1) → nguồn `CV`, chạy biến thể (i) (mục 4), áp ngưỡng `MIN_SIMILARITY_SCORE`.
  2. Khác (1), Hồ sơ sẵn sàng (R-V3) → nguồn `PROFILE`, chạy biến thể (ii), **không** áp ngưỡng.
  3. Khác (1)(2), Có mong muốn (R-V5) → nguồn `DESIRES`, chạy biến thể (iii) (sắp theo thời gian
     đăng, không xếp theo embedding).
  4. Khác (1)(2)(3), nhưng CV đang chờ (R-V2) hoặc Hồ sơ đang chờ (R-V4) → trạng thái `PREPARING`
     (mục 3.6 R-S), nguồn báo cáo = `CV` nếu đang chờ CV, ngược lại `PROFILE`. **Đã chốt**: khi
     **cả hai** đang chờ cùng lúc (CV đang chờ VÀ hồ sơ đang chờ) → vẫn báo `source=CV` (ưu tiên
     CV trong thông báo, không báo cả hai, không ưu tiên hồ sơ).
  5. Còn lại (không CV, không hồ sơ, không mong muốn, không gì đang chờ) → trạng thái `NO_DATA`.
- **R-V7.** Ở cả ba biến thể (i)(ii)(iii): điều kiện cứng = ngành **và** khu vực mong muốn (R-H) nếu
  ứng viên có khai; không khai trường nào thì không lọc theo trường đó (danh sách rỗng → cờ
  `...CodesPresent = false`, bỏ qua điều kiện, mục 3.2 R-H2).
- **R-V8.** Không gọi `EmbeddingModel`/LLM ở bất kỳ bước nào — toàn bộ R-V1 đến R-V6 chỉ đọc cột đã
  có sẵn (`resume_parsed_data.embedding`, `candidate_profiles.embedding`), đúng ranh giới CLAUDE.md
  §7 (FR-U15 không thuộc danh sách ngoại lệ K3).
- **R-V9.** Giữ nguyên hằng số `MIN_SIMILARITY_SCORE = 0.40` cho nhánh CV (R-V6.1) — **không sửa**
  cách lọc theo ngưỡng tuyệt đối, dù ROADMAP đã ghi nợ ngưỡng này gần như không lọc được ở quy mô
  dữ liệu hiện tại. Nhánh hồ sơ (R-V6.2) **không** áp ngưỡng nào — văn bản hồ sơ ngắn hơn CV, điểm
  tương đồng không cùng thang đo, phạm vi đã được giới hạn đủ bởi điều kiện cứng R-H.

### 3.6 Trạng thái & nguồn trả về (R-S)

- **R-S1.** API trả `status` tường minh — frontend **không** tự suy từ `parseStatus` hay trường
  nào khác:
  | Status | Khi nào | Hành động gợi ý ở UI |
  |---|---|---|
  | `READY` | Có kết quả (≥1 job sau khi áp R-H/R-C) | Hiện danh sách |
  | `NO_DATA` | R-V6 nhánh 5 | Mời hoàn thiện hồ sơ, dẫn `/candidate/profile` |
  | `PREPARING` | R-V6 nhánh 4 | Thông báo đang xử lý, không có hành động |
  | `NO_RESULT` | Có nguồn (R-V6 nhánh 1-3) nhưng 0 job thoả | Thông báo; chỉ đề nghị mở rộng mong muốn khi ứng viên có khai ngành hoặc khu vực (R-S5); không tự nới điều kiện |
- **R-S2.** `source` ∈ {`CV`, `PROFILE`, `DESIRES`, `null`} — `null` chỉ khi `status = NO_DATA`. Ở
  mọi status khác, `source` luôn có giá trị (kể cả `PREPARING`, R-V6.4), để frontend ghi đúng một
  trong ba dòng: "Dựa trên CV chính của bạn" / "Dựa trên hồ sơ nghề nghiệp của bạn" / "Dựa trên
  ngành nghề và khu vực bạn chọn" (UI.md mục 7).
- **R-S3.** Response **không** có bất kỳ field điểm tương đồng nào (`similarityScore`/`matchScore`
  hay tên khác) — giữ nguyên nguyên tắc đã kiểm chứng ở test #7 (mục 0.a), đúng SRS mục 2 "AI
  không gán nhãn/xếp hạng hiển thị cho người dùng thấy điểm số nội bộ".
- **R-S4.** Tính **trực tiếp mỗi lần gọi** `GET /api/candidates/job-recommendations` — không ghi gì
  vào DB (không bảng cache nào thay thế `job_recommendations`), không `@Transactional` ghi (chỉ đọc).
- **R-S5. Nội dung thông báo `NO_RESULT` phụ thuộc mong muốn, không phụ thuộc `source`.** API
  **không** thêm field nào cho việc này (nội dung chữ là frontend tự ghép, UI.md mục 7) — frontend
  dùng **cùng** `useMyProfileQuery()` đã đọc ở R-X4 để biết ứng viên có khai ngành/khu vực hay
  không, và quyết định có thêm câu "mở rộng mong muốn" hay không, **độc lập** với `source` trả về
  (một ứng viên ở nhánh `CV`/`PROFILE` nhưng không khai mong muốn nào vẫn có thể rơi vào
  `NO_RESULT` — câu khuyên "mở rộng ngành/khu vực" không có ý nghĩa với họ, xem UI.md mục 6/7).

### 3.7 Giới hạn số lượng & vị trí hiển thị (R-L)

- **R-L1.** Tối đa **6** việc, cả ở `/candidate` và `/candidate/dashboard` — `LIMIT 6` ngay trong
  câu truy vấn (mục 4), không cắt ở tầng Java. **Dưới `sm`** (mobile): API vẫn trả đủ tối đa 6,
  nhưng **chỉ hiển thị 3 thẻ đầu** — ẩn thẻ thứ 4-6 bằng CSS (ví dụ class ẩn theo
  `nth-child`/breakpoint, không gọi API với limit khác) để không đẩy ô tìm kiếm/thanh lọc xuống quá
  xa trên màn hình nhỏ (UI.md mục 4b, mục 8). **Từ `sm` trở lên**: hiển thị đủ 6.
- **R-L2.** Dùng chung **một component** `RecommendedJobs` (giữ tên file hiện có,
  `frontend/src/features/jobs/RecommendedJobs.tsx`) cho cả hai trang.
- **R-L3.** Khối chỉ hiện ở `/candidate` khi: đang ở trang 1 (`filters.page === 0`), chưa có từ
  khoá (`filters.keyword === ''`), và `hasActiveFilters === false` (không có bất kỳ điều kiện lọc
  U07 nào đang áp — `useJobFilters().hasActiveFilters`). Có lọc/từ khoá/trang khác 1 → **ẩn hẳn**
  khối, không hiện rút gọn.
- **R-L4.** `JobBoard` (dùng chung `/` và `/candidate`, `JobBoard.tsx:11`) nhận prop mới
  `showRecommendations?: boolean` (mặc định `false`). `CandidateJobListPage.tsx` truyền
  `showRecommendations={true}`; `PublicJobListPage.tsx` **không** truyền (giữ `false`) — trang công
  khai/khách không có khối gợi ý (SRS mục "Chỉ cho ứng viên đã đăng nhập").
- **R-L5.** `/candidate/dashboard` (`CandidateHomePage.tsx`) **chỉ thay nội dung khối gợi ý hiện
  tại** (gọi lại `RecommendedJobs`, không còn điều kiện `showLoading`/`showEmpty` tự suy từ
  `resumes` như hiện tại — mục 3.6 R-S1 đã có status tường minh) — không sửa phần còn lại của trang
  (thẻ "CV chính"/"Đơn ứng tuyển", khối "Cải thiện CV") và không sửa MD3 cho các phần khác (để
  `refactor/ui-md3-legacy`, Phase 2.1b).

### 3.8 Điều kiện khớp hiển thị trên thẻ (R-M)

- **R-M1.** Backend tính, trả trong response (`matchedConditions: string[]`) — không để frontend
  tự so sánh job với mong muốn.
- **R-M2.** Mỗi job, tính độc lập 4 điều kiện, chỉ đưa vào danh sách khi **khớp**:
  - **Khu vực**: `job.location_code` nằm trong `desired_location_codes` → nhãn tỉnh/thành (tra qua
    `CatalogRegistry`, giống `JobCatalogFields`).
  - **Ngành**: `job.category_code` nằm trong `desired_industry_codes` → nhãn ngành.
  - **Hình thức**: `job.work_mode` nằm trong `desired_work_modes` → nhãn hình thức
    (`WORK_MODE_LABELS`).
  - **Lương**: `desired_salary_min` có khai (khác NULL) **và** job dùng VND (`UPPER(TRIM(COALESCE(
    salary_currency,'VND'))) = 'VND'`, cùng chuẩn hoá với FR-U07 R-S5) **và**
    `COALESCE(salary_max, salary_min) >= desired_salary_min` → nhãn cố định "Lương đạt mong muốn".
    Tin Thoả thuận (cả hai cột NULL) hoặc ngoại tệ → **không** hiện điều kiện lương (không đủ dữ
    liệu để so, không phải "không khớp").
- **R-M3.** Không khớp hoặc ứng viên không khai trường đó → **không hiện gì** cho điều kiện đó
  (không có mục "Không khớp Khu vực" hay tương tự trong dòng văn bản) — chỉ liệt kê điều kiện ĐÃ
  khớp.
- **R-M4.** Lương và hình thức **không bao giờ loại job** khỏi kết quả — chỉ ảnh hưởng
  `matchedConditions`, không ảnh hưởng tập job trả về (khác ngành/khu vực, là điều kiện cứng R-H).
- **R-M5.** Thứ tự các điều kiện trong dòng văn bản trên thẻ cố định: Khu vực · Ngành · Hình thức ·
  Lương (khớp đúng ví dụ README "Khớp mong muốn: Hà Nội · CNTT · Toàn thời gian · Lương đạt mong
  muốn") — điều kiện nào không khớp bị bỏ qua, không để trống vị trí.

### 3.9 Mở rộng bộ lọc U07 sang chọn nhiều (R-F)

- **R-F1.** `useJobFilters.ts`: `categoryCode`/`locationCode` đổi từ `string | null` sang
  `string[]` (mảng rỗng = không lọc). Đọc/ghi URL bằng tham số lặp (`searchParams.getAll(...)`/
  `append`), giữ tương thích URL cũ (một giá trị `?categoryCode=A` → mảng `['A']`).
- **R-F2.** `JobFilterBar.tsx`: `CategoryField`/`LocationField` đổi từ `CatalogCombobox` (chọn 1)
  sang `CatalogMultiCombobox` (`max={3}`, component có sẵn từ FR-U14,
  `frontend/src/features/catalog/CatalogMultiCombobox.tsx`) — áp dụng ở **cả** thanh lọc desktop
  (`>= sm`) và bottom sheet mobile (`MobileFilterSheet`, `< sm`).
- **R-F3.** Validate client-side giống `max` của `CatalogMultiCombobox` (tự chặn chọn quá 3, không
  cần gửi lên API mới biết sai) — backend vẫn validate lại độc lập (R-H4), không tin riêng client.
- **R-F4.** `activeFilterCount`/`hasActiveFilters` (`useJobFilters.ts`) đổi điều kiện từ
  `categoryCode != null` sang `categoryCode.length > 0` (tương tự cho location).

### 3.10 "Xem tất cả" — điền sẵn bộ lọc (R-X)

- **R-X1.** Liên kết "Xem tất cả" trỏ tới `/candidate` kèm `categoryCode`/`locationCode` lấy
  **trực tiếp** từ `desired_industry_codes`/`desired_location_codes` của ứng viên (tối đa 3 mỗi
  loại, đúng khớp sau khi R-F mở rộng — không còn giới hạn 1 giá trị như trước khi có R-F).
- **R-X2.** **Không** điền `salaryMin`/`salaryMax`/`workMode` — README đã chốt rõ "KHÔNG điền lương
  và hình thức" (khác với điều kiện cứng, lương/hình thức không loại tin ở U07 cũng không nên áp
  đặt sẵn làm ứng viên tưởng nhầm là điều kiện loại).
- **R-X3. Hiển thị nút khác nhau theo trang (đã chốt).**
  - **Ở `/candidate`**: ứng viên không khai cả ngành lẫn khu vực → **ẨN HẲN nút "Xem tất cả"**
    (không hiện nút mà bấm vào không có gì xảy ra/dẫn về chính trang đang đứng — khối gợi ý đã
    hiển thị ngay trên chính trang Việc làm, "xem tất cả" vô nghĩa khi không có gì để điền sẵn
    khác với trang hiện tại).
  - **Ở `/candidate/dashboard`**: **LUÔN** hiện nút "Xem tất cả" (khác `/candidate` — trang này
    không có bộ lọc riêng nên nút luôn có nghĩa, dẫn sang `/candidate`). Có khai mong muốn → điền
    sẵn theo R-X1; không khai gì → mở `/candidate` không tham số lọc nào (danh sách đầy đủ, mặc
    định NEWEST).
- **R-X4. Nguồn dữ liệu mong muốn ở frontend.** Dùng lại hook đã có từ FR-U14 —
  `useMyProfileQuery()` (`frontend/src/features/candidateProfile/queries.ts:14`) — đọc
  `desiredIndustries`/`desiredLocations` (`CatalogResponse.Item[]`, field `.code`) từ response đó
  để build URL "Xem tất cả" và để quyết định ẩn/hiện nút (R-X3). **Không** thêm field mong muốn
  nào vào response của `GET /api/candidates/job-recommendations` (R-S2 không đổi) — tránh hai nguồn
  sự thật cho cùng một dữ liệu. `RecommendedJobs` gọi thêm `useMyProfileQuery()` song song với
  `useJobRecommendationsQuery()`.

### 3.11 Chống lộ cho HR (R-P)

- **R-P1.** Không thêm field nào của mục 3.6/3.8 (`matchedConditions`, `status`, `source`) vào bất
  kỳ response/entity nào mà controller gắn `hasRole("HR")` đọc được — các field này chỉ tồn tại ở
  `GET /api/candidates/job-recommendations`.
- **R-P2.** `GET /api/candidates/job-recommendations` giữ nguyên bị chặn `hasRole("CANDIDATE")` ở
  tầng filter chain (`SecurityConfig`, không đổi) — HR gọi → 403.
- **R-P3.** Gợi ý **không** đọc/ghi bất kỳ bảng nào mà HR dùng để xếp hạng/chấm điểm
  (`scoring_runs`, `criterion_scores`, `job_applications.status`...) ngoài việc **đọc** (không ghi)
  `job_applications` để loại trừ (R-C) — không có đường nào gợi ý làm thay đổi dữ liệu phía HR.

## 4. Dữ liệu & quyền truy cập

### Migration `V11__drop_job_recommendations.sql` (SQL dự kiến)

```sql
-- FR-U15: go bo dem FR-U04 (job_recommendations), thay bang tinh truc tiep moi lan goi API,
-- de he thong chi con MOT nguon goi y duy nhat (dung chung may "dieu kien cung + vector" voi
-- FR-U13 sau nay). Index idx_reco_candidate bi xoa cung bang, khong can lenh rieng.
DROP TABLE job_recommendations;
```

Không sửa V1–V10.

### Câu truy vấn gợi ý (SQL dự kiến — 3 biến thể, dùng chung `PUBLIC_JOB_FILTER_WHERE` đã mở rộng ở R-H2)

**`PUBLIC_JOB_FILTER_WHERE` sau khi mở rộng** (R-H2, dùng chung cho `GET /api/public/jobs` và cả
hai câu truy vấn gợi ý dưới đây — FR-U15 gọi với `titlePattern`/`locationPattern`/`categoryPattern`
= `null` và `salaryMinVnd`/`salaryMaxVnd`/`hideUnlisted`/`workModesPresent`/`sinceTimestamp` =
"không lọc", đúng cách `searchOpenJobsNewest` đã làm cho `CvImprovementOrchestrator`):

```sql
WHERE j.status = 'OPEN' AND j.deleted_at IS NULL
  AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
  AND (CAST(:titlePattern AS text) IS NULL OR j.title ILIKE CAST(:titlePattern AS text))
  AND (CAST(:locationPattern AS text) IS NULL OR j.location ILIKE CAST(:locationPattern AS text)
       OR EXISTS (SELECT 1 FROM catalog_provinces cp WHERE cp.code = j.location_code
                  AND cp.label ILIKE CAST(:locationPattern AS text)))
  AND (CAST(:categoryPattern AS text) IS NULL OR j.category ILIKE CAST(:categoryPattern AS text)
       OR EXISTS (SELECT 1 FROM catalog_industries ci WHERE ci.code = j.category_code
                  AND ci.label ILIKE CAST(:categoryPattern AS text)))
  AND (:categoryCodesPresent = FALSE OR j.category_code IN :categoryCodeParams OR j.category_code IS NULL)
  AND (:locationCodesPresent = FALSE OR j.location_code IN :locationCodeParams OR j.location_code IS NULL)
  AND (
        (UPPER(TRIM(COALESCE(j.salary_currency, 'VND'))) = 'VND'
          AND (CAST(:salaryMinVnd AS numeric) IS NULL OR j.salary_max IS NULL
               OR j.salary_max >= CAST(:salaryMinVnd AS numeric))
          AND (CAST(:salaryMaxVnd AS numeric) IS NULL OR j.salary_min IS NULL
               OR j.salary_min <= CAST(:salaryMaxVnd AS numeric)))
        OR UPPER(TRIM(COALESCE(j.salary_currency, 'VND'))) <> 'VND'
      )
  AND NOT (:hideUnlisted = TRUE AND j.salary_min IS NULL AND j.salary_max IS NULL)
  AND (:workModesPresent = FALSE OR j.work_mode IN :workModeParams)
  AND (CAST(:sinceTimestamp AS timestamptz) IS NULL
       OR COALESCE(j.published_at, j.created_at) >= CAST(:sinceTimestamp AS timestamptz))
```

**Biến thể (i)/(ii) — xếp theo embedding** (một phương thức Java, cờ `applyThreshold` chọn giữa
nhánh CV và nhánh hồ sơ — tránh viết lặp hai query gần như giống nhau):

```sql
SELECT j.* FROM jobs j
JOIN job_embeddings je ON je.job_id = j.id
<PUBLIC_JOB_FILTER_WHERE ở trên>
  AND NOT EXISTS (
      SELECT 1 FROM job_applications ja
      WHERE ja.job_id = j.id AND ja.candidate_id = :candidateId
        AND ja.recruitment_cycle = j.recruitment_cycle
  )
  AND (1 - (je.embedding <=> CAST(:queryVector AS vector))) < 'Infinity'::float8
  AND (:applyThreshold = FALSE
       OR (1 - (je.embedding <=> CAST(:queryVector AS vector))) >= :minSimilarity)
ORDER BY je.embedding <=> CAST(:queryVector AS vector), j.id ASC
LIMIT :limit
```
- Nhánh CV (i): `applyThreshold = true`, `minSimilarity = 0.40` (R-V9), `queryVector` =
  `resume_parsed_data.embedding::text` của CV chính.
- Nhánh hồ sơ (ii): `applyThreshold = false` (tham số `minSimilarity` không dùng tới), `queryVector`
  = `candidate_profiles.embedding::text`.
- Cùng guard NaN (`< 'Infinity'::float8`) như `JobEmbeddingRepository.findTopMatchingJobs` — phòng
  thủ chiều sâu, lý do giữ nguyên như comment gốc.

**Biến thể (iii) — không vector, sắp theo thời gian đăng** (phương thức Java riêng, không JOIN
`job_embeddings`, bắt buộc có ít nhất một trong hai điều kiện cứng khác rỗng — nếu không sẽ trả về
mọi job OPEN mới nhất, không phải "gợi ý theo mong muốn"):

```sql
SELECT j.* FROM jobs j
<PUBLIC_JOB_FILTER_WHERE ở trên, cùng cách dùng>
  AND NOT EXISTS (
      SELECT 1 FROM job_applications ja
      WHERE ja.job_id = j.id AND ja.candidate_id = :candidateId
        AND ja.recruitment_cycle = j.recruitment_cycle
  )
ORDER BY COALESCE(j.published_at, j.created_at) DESC, j.id DESC
LIMIT :limit
```
Tie-break `j.id DESC` (không phải `ASC`) — đúng theo tie-break thật của
`searchPublicJobsSortedByNewest` (`JobRepository.java:79`:
`"ORDER BY COALESCE(j.published_at, j.created_at) DESC, j.id DESC"`), vì biến thể (iii) xếp theo
cùng tiêu chí "mới nhất" với `sort=NEWEST` của FR-U07 — dùng cùng quy ước để hai nơi không lệch
thứ tự khi `COALESCE(published_at, created_at)` trùng nhau. Khác biến thể (i)/(ii) ở trên, vốn
`ORDER BY ... <=> ..., j.id ASC` theo đúng tie-break của `JobEmbeddingRepository.findTopMatchingJobs`
(`JobEmbeddingRepository.java:92`) — hai quy ước `ASC`/`DESC` khác nhau có chủ đích, mỗi biến thể
theo đúng tiền lệ của truy vấn gần nhất với nó, không tự ý thống nhất một chiều cho cả ba.

Hai phương thức Java mới đặt trong `JobRepository` (cùng file giữ `PUBLIC_JOB_FILTER_WHERE`, để
FR-U13 sau này gọi lại trực tiếp — không tạo abstraction/class riêng ngoài yêu cầu, mục 6 Ngoài
phạm vi).

### Đọc embedding hồ sơ theo `userId` (mới — chưa có)

`CandidateProfileRepository` **chưa có** method đọc `embedding::text` theo `userId` (khác
`ResumeParsedDataRepository.findEmbeddingTextByResumeId` đã có cho CV) — thêm mới:

```java
@Query(value = "SELECT embedding::text FROM candidate_profiles WHERE user_id = :userId AND embedding IS NOT NULL",
       nativeQuery = true)
Optional<String> findEmbeddingTextByUserId(@Param("userId") UUID userId);
```

### API — `GET /api/public/jobs` (sau khi mở rộng R-H)

| Tham số | Kiểu | Thay đổi | Lỗi 400 mới |
|---|---|---|---|
| `categoryCode` | `String`, lặp 0..n (trước: 1 giá trị) | Mở rộng sang danh sách, 1 giá trị vẫn hợp lệ | `INVALID_JOB_FILTER` nếu > 3 sau dedupe (`tooManyCategoryCodes`) |
| `locationCode` | `String`, lặp 0..n (trước: 1 giá trị) | Mở rộng sang danh sách, 1 giá trị vẫn hợp lệ | `INVALID_JOB_FILTER` nếu > 3 sau dedupe (`tooManyLocationCodes`) |
| *(mọi tham số khác)* | — | Không đổi | — |

### API — `GET /api/candidates/job-recommendations` (nội dung đổi hoàn toàn, path giữ nguyên)

Request: không tham số (giữ nguyên — tính theo `candidateId` từ JWT).

Response:
```json
{
  "status": "READY | NO_DATA | PREPARING | NO_RESULT",
  "source": "CV | PROFILE | DESIRES | null",
  "items": [
    {
      "job": { /* JobSummaryResponse — không đổi */ },
      "matchedConditions": ["Hà Nội", "CNTT", "Toàn thời gian", "Lương đạt mong muốn"]
    }
  ]
}
```
- `items` rỗng khi `status` ∈ {`NO_DATA`, `PREPARING`, `NO_RESULT`}.
- **Không** có field điểm tương đồng nào (R-S3).
- 403 khi gọi bằng vai trò HR (R-P2, không đổi hành vi hiện có).

## 5. AI

Không gọi LLM ở bất kỳ bước nào. Chỉ đọc embedding đã có sẵn (`resume_parsed_data.embedding` của
FR-U04, `candidate_profiles.embedding` của FR-U14) — không sinh embedding mới trong lúc phục vụ
request (đúng nguyên tắc "AI hay làm sai" đã cảnh báo từ FR-U04: không gọi `EmbeddingModel` mỗi lần
tải trang).

## 6. Ngoài phạm vi

- Thiết kế API/giao diện cho FR-U13 (tìm việc bằng ngôn ngữ tự nhiên) — chỉ tách 2 phương thức
  truy vấn trong `JobRepository` (mục 4) để FR-U13 gọi lại được, không code phần gọi của FR-U13.
- Sửa ngưỡng `MIN_SIMILARITY_SCORE` hay cách lọc theo ngưỡng tuyệt đối (R-V9) — nợ kỹ thuật đã ghi
  ở ROADMAP, giữ nguyên nguyên trạng.
- Gửi email/thông báo gợi ý định kỳ (README đã chốt "chưa gửi email gợi ý định kỳ").
- Lọc theo `employment_type`, bí danh tìm kiếm ("HCM"...) — nợ kỹ thuật kế thừa từ FR-C05/FR-U07,
  không mở rộng thêm ở FR-U15.
- Quy đổi tỷ giá tiền tệ khi so sánh lương mong muốn (R-M2) — tin ngoại tệ không hiện điều kiện
  lương, không quy đổi "cho hợp lý".
- Đổi `/candidate/dashboard` ngoài khối gợi ý (R-L5), MD3 cho phần còn lại của `CandidateHomePage`.
- Màn hình quản trị/sửa ngưỡng, giới hạn 6/3/0.40 bằng cấu hình — hằng số cố định trong code.
- Gán nhãn "Phù hợp"/"Không phù hợp", thang điểm %, màu theo mức độ khớp (SRS mục 2, CLAUDE.md §7,
  UI_GUIDE mục 4 "Lý do gợi ý/khớp").

## 7. Xong khi

Tất cả lệnh sạch: `cd backend && ./mvnw test` (full suite, đúng một lần ở đợt cuối),
`cd frontend && npm run build`, `cd frontend && npm run lint` (không warning).

### Bảng đối chiếu test cũ → test mới (7 dòng, bắt buộc đủ)

| # | Test cũ | Test mới (file dự kiến) | Giữ nguyên ý nghĩa |
|---|---|---|---|
| 1 | `refreshOne_jobsAboveAndBelowThreshold_onlyAboveThresholdCached` | `JobRecommendationCandidateServiceTest.getRecommendations_cvBranch_jobsAboveAndBelowThreshold_onlyAboveThresholdIncluded` | Job trên/dưới ngưỡng 0.40 ở nhánh CV |
| 2 | `refreshOne_candidateWithoutPrimaryResume_doesNothing` | `JobRecommendationCandidateServiceTest.getRecommendations_noCvNoProfileNoDesires_returnsNoData` | Ứng viên hoàn toàn trống → không có gợi ý (mở rộng: `NO_DATA` tường minh thay cho "cache rỗng") |
| 3 | `refreshOne_calledAgainAfterJobClosed_removesClosedJobFromCache` | `JobRecommendationCandidateServiceTest.getRecommendations_cvBranch_jobClosedBetweenCalls_excludedImmediately` | Job đóng không còn được gợi ý — không cần "lượt làm mới kế tiếp" vì không còn cache, job đóng biến mất ngay ở lần gọi kế tiếp |
| 4 | `refreshOne_candidateITResume_doesNotRecommendAccountingJob` | `JobRecommendationCandidateServiceTest.getRecommendations_cvBranch_doesNotRecommendJobOutsideSimilarityThreshold` | Giữ nguyên kỹ thuật vector mô phỏng 0.49/0.355, CV IT không gợi ý job Kế toán |
| 5 | `getRecommendations_noCache_returnsEmptyList` (API) | `JobRecommendationCandidateControllerIntegrationTest.getRecommendations_noData_returnsEmptyItemsWithNoDataStatus` | Ứng viên mới, không dữ liệu → API trả rỗng, không lỗi (mở rộng: kèm `status=NO_DATA`) |
| 6 | `getRecommendations_hasCachedRecommendations_returnsOrderedWithoutSimilarityScoreField` (phần thứ tự) | `JobRecommendationCandidateControllerIntegrationTest.getRecommendations_cvBranch_returnsOrderedBySimilarityDesc` | Job similarity cao hơn đứng trước — seed bằng `JobEmbeddingStateService` thật (không ghi thẳng bảng cache vì bảng không còn tồn tại) |
| 7 | `getRecommendations_hasCachedRecommendations_returnsOrderedWithoutSimilarityScoreField` (phần không lộ điểm) | `JobRecommendationCandidateControllerIntegrationTest.getRecommendations_responseDoesNotContainSimilarityScoreField` | JSON không chứa `similarityScore`/`matchScore` |

Ngoài 7 dòng trên, **không xoá hay nới** bất kỳ test nào khác trong dự án (R-G2).

### Test mới cần thêm (hành vi mới, không nằm trong bảng đối chiếu)

1. Nhánh hồ sơ (R-V6.2): ứng viên không CV, có hồ sơ với embedding → gợi ý theo hồ sơ, **không**
   áp ngưỡng 0.40 (job similarity thấp hơn 0.40 vẫn vào, miễn nằm trong top theo `LIMIT`).
2. Nhánh mong muốn (R-V6.3): không CV, không hồ sơ có embedding, có khai ngành/khu vực → gợi ý sắp
   theo `COALESCE(published_at, created_at) DESC, id DESC`, có tie-break.
3. `PREPARING` (R-V6.4): CV đang `PENDING`/`PROCESSING` (chưa `FAILED`), không mong muốn → status
   `PREPARING`, `source=CV`. Tương tự cho hồ sơ đang chờ embedding → `source=PROFILE`.
4. CV `FAILED`, không mong muốn, không hồ sơ → `NO_DATA` (không phải `PREPARING` — R-V1 loại
   `FAILED`).
5. Có CV đang chờ (chưa `FAILED`) nhưng **có** khai mong muốn → rơi thẳng vào nhánh (iii)
   `DESIRES`, không `PREPARING` (R-V6 thứ tự: nhánh 3 đứng trước nhánh 4).
6. Mã NULL vẫn vào gợi ý (R-N1): job `category_code=NULL` vẫn xuất hiện khi ứng viên có khai
   `desired_industry_codes` khác.
7. Loại trừ đơn đã nộp (R-C1): đơn `WITHDRAWN` trong chu kỳ hiện tại của job → job đó vẫn bị loại
   khỏi gợi ý; đơn thuộc chu kỳ **cũ** (job đã CLOSED rồi mở lại, `recruitment_cycle` tăng) → job
   **được** gợi ý lại (chu kỳ mới, không trùng chu kỳ đơn cũ).
8. `matchedConditions` (R-M2): đủ 4 ca — khớp khu vực, khớp ngành, khớp hình thức, khớp lương (biên
   `salary_max = desired_salary_min` → khớp, lệch 1 đơn vị → không khớp); job Thoả thuận/ngoại tệ
   không hiện điều kiện lương dù ứng viên có khai mong muốn lương.
9. Lương/hình thức không loại tin (R-M4): job không khớp lương/hình thức mong muốn vẫn xuất hiện
   trong `items`, chỉ thiếu điều kiện tương ứng trong dòng văn bản đó.
10. Giới hạn 6 (R-L1): seed > 6 job đủ điều kiện → `items.size() == 6`.
11. Mở rộng U07 nhiều mã (R-H3/R-H4): `categoryCode=A&categoryCode=B&categoryCode=C` → 200, job
    khớp A hoặc B hoặc C được trả về; thêm mã thứ 4 → 400 `INVALID_JOB_FILTER`; trùng mã (dedupe
    còn ≤3) → 200 (test biên đúng 3/4, giống khuôn R-F3 của FR-U14).
12. Một giá trị vẫn hợp lệ (R-H3): `categoryCode=A` (không lặp) → hành vi giống hệt trước khi mở
    rộng — test hồi quy trực tiếp đối chiếu với hành vi cũ.
13. **Chống lộ cho HR (R-P1/R-P3)**:
    - Gọi `GET /api/hr/jobs/{jobId}/applications` (`ApplicationOwnerController.java:28-35`, trả
      `List<ApplicationHrListItemResponse>`) → JSON **không chứa** khoá `matchedConditions`.
    - Gọi `GET /api/hr/candidates` (`ApplicationSearchController.java:28-42`, trả
      `PageResponse<ApplicationSearchItemResponse>`) → JSON **không chứa** khoá `matchedConditions`.
    - Gọi `GET /api/candidates/job-recommendations` bằng token HR → **403**.
14. Không ghi DB (R-S4): gọi API gợi ý 2 lần liên tiếp, so `COUNT(*)`/checksum các bảng liên quan
    (`job_applications`, `candidate_profiles`, `resume_parsed_data`, `jobs`) trước và sau — không
    đổi; không có `@Transactional` ghi nào trong `JobRecommendationCandidateService`.
15. Test cũ U07 vẫn xanh (mục 0.c — xem "Đã chốt"): 14+2 case `JobPublicIntegrationTest`/
    `JobPublicServiceTest` hiện có chạy lại không đổi kết quả (ngoại trừ sửa cơ học 3 lời gọi arity đã
    chốt ở R-H7).

### Đo hiệu năng (không phải test tự động — tách khỏi danh sách đánh số trên)

`EXPLAIN ANALYZE` thật trên Postgres (Testcontainers ở đợt test, hoặc DB dev sau khi đợt test
backend đã xanh) cho cả 3 biến thể câu truy vấn gợi ý — ghi kết quả (planner chọn HNSW hay Seq
Scan, thời gian) vào walkthrough ở đợt cuối. Đo thật, không đoán (CLAUDE.md "kiểm chứng bằng
Postgres thật"). Lưu ý khi đọc kết quả: nếu planner chọn Index Scan qua HNSW, lọc theo điều kiện
cứng (R-H)/loại trừ chu kỳ (R-C)/ngưỡng (R-V9) xảy ra **sau** khi quét chỉ mục lấy top-k gần nhất —
có thể trả về **ít hơn** `LIMIT 6` dù còn job khác thoả toàn bộ điều kiện nhưng nằm ngoài top-k mà
chỉ mục đã quét (xem mục 10 "Nợ kỹ thuật dự kiến"). Ghi rõ trong walkthrough planner đã chọn gì ở
quy mô dữ liệu demo hiện tại.

### Soát tay ở đợt cuối (hành vi frontend, dự án không có hạ tầng test tự động cho frontend)

- **"Xem tất cả" điền sẵn** (R-X1/R-X2/R-X3/R-X4): ứng viên có 2 ngành + 1 khu vực mong muốn, mở
  `/candidate` → bấm "Xem tất cả" (ẩn, không áp dụng) / mở `/candidate/dashboard` → bấm "Xem tất
  cả" → URL `/candidate?categoryCode=...&categoryCode=...&locationCode=...`, không có `salaryMin`/
  `workMode`. Ứng viên không khai gì: ở `/candidate` nút bị ẩn (soát bằng mắt); ở
  `/candidate/dashboard` nút vẫn hiện, bấm vào → URL `/candidate` trần.
- Mobile (`< sm`): chỉ 3 thẻ đầu hiện, 3 thẻ còn lại bị ẩn CSS (R-L1) — soát bằng DevTools responsive
  375px, không chỉ tin vào code.

## 8. AI hay làm sai (dành cho người code)

- Giữ lại song song bộ đệm `job_recommendations` "cho an toàn" — README/quyết định đã chốt dứt
  điểm: gỡ hẳn, một nguồn gợi ý duy nhất (R-G1).
- Trả `similarityScore`/`matchScore` (hay bất kỳ tên field điểm số nào) trong response — R-S3, kiểm
  bằng test 7/mục 7.
- Để `desired_salary_min`/`desired_work_modes` loại job khỏi kết quả — chúng **chỉ** ảnh hưởng
  `matchedConditions` (R-M4), điều kiện cứng duy nhất là ngành/khu vực (R-H).
- Áp `MIN_SIMILARITY_SCORE` cho nhánh hồ sơ (R-V9) — chỉ nhánh CV mới có ngưỡng.
- Gọi `EmbeddingModel`/LLM đồng bộ ở bất kỳ bước nào của FR-U15 (R-V8) — FR-U15 không có trong
  danh sách ngoại lệ K3 ở CLAUDE.md §7.
- Làm đỏ test U07 hiện có (`JobPublicIntegrationTest`/`JobPublicServiceTest`) rồi tự sửa khẳng định
  cho xanh trở lại mà không báo. **Ngoại lệ duy nhất đã duyệt** (R-H7, mục 0.c "Đã chốt"): thêm
  đúng 2 `anyBoolean()` vào 3 lời gọi `when(...)`/`verify(...)` của
  `JobPublicServiceTest.java:39-41,54-56,62-65` theo đúng số tham số mới — không cần dừng hỏi cho
  riêng 3 chỗ này. **Mọi test U07 khác** (bất kỳ
  file nào, bất kỳ lý do nào ngoài đúng việc đổi số lượng tham số thuần tuý ở 3 chỗ trên) đỏ → vẫn
  **DỪNG và báo**, không tự sửa khẳng định.
- Sửa `V10__career_profile.sql` hoặc bất kỳ migration V1–V10 nào — chỉ thêm `V11` mới.
- Chạy `./mvnw spring-boot:run` hay bất kỳ lệnh chạm DB dev nào **trong khoảng từ đợt 4** (migration
  `V11` xuất hiện trong thư mục migration) **tới khi đợt 5 (test) đã xanh** — `V11` xoá bảng
  `job_recommendations` **không hoàn tác được** (không có dữ liệu nào trong bảng là "quan trọng" để
  mất, nhưng DROP TABLE chạy nhầm trên DB dev giữa lúc code chưa ổn định có thể buộc phải khôi phục
  từ seed/backup, tốn thời gian hơn hẳn so với đợi đợt test xanh). Trước đợt 4 (đợt 1-3), `V11` chưa
  tồn tại nên ràng buộc này chưa áp dụng.
- Dùng `UUID.compareTo()` của Java để dự đoán thứ tự `ORDER BY id` của Postgres — đọc lại từ
  repository thật (bài học đã ghi ở ROADMAP `chore/hardening`).
- Quên tie-break `id` ở `ORDER BY` mới của biến thể (iii) (R-O3 kiểu FR-U07), hoặc **dùng sai
  chiều** — biến thể (i)/(ii) dùng `j.id ASC` (theo tiền lệ `JobEmbeddingRepository.
  findTopMatchingJobs`), biến thể (iii) dùng `j.id DESC` (theo tiền lệ
  `searchPublicJobsSortedByNewest`) — **hai chiều khác nhau có chủ đích**, không tự ý đổi cho
  "thống nhất", xem SQL dự kiến đã ghi rõ ở mục 4.
- Thêm cột/field tên `matchScore`, `rank`, `recommendationScore`... ở bất kỳ entity/DTO nào —
  tương đương field điểm số bị cấm (CLAUDE.md §7).
- Cho `RecommendedJobs`/`JobBoard` tự gọi API gợi ý khi `showRecommendations=false` (trang công
  khai) — chỉ gọi khi prop là `true` (R-L4), tránh lộ dấu hiệu "đây là ứng viên đã đăng nhập" cho
  trang khách qua side-effect gọi API không cần thiết.

## 9. Kế hoạch chia đợt code

Mỗi đợt một commit; dừng → báo cáo diff → chờ duyệt → mới commit (CLAUDE.md §6). Giữa các đợt
backend chỉ `.\mvnw.cmd -q test-compile` (không phải `compile` — cần biên dịch cả thư mục test);
**đợt nào đổi `PUBLIC_JOB_FILTER_WHERE`/`PublicJobSearchCriteria` (đợt 1) phải chạy riêng các lớp
`JobPublicServiceTest`/`JobPublicIntegrationTest`/`JobCatalogGuardIntegrationTest` bằng tay** (`mvnw
test -Dtest=...`) ngay trong đợt đó — không đợi tới đợt test backend mới biết có vỡ hay không, đúng
tinh thần "đợt nào sửa chỗ nào, tự kiểm ngay chỗ đó" (giống cách FR-U14 bắt buộc kiểm riêng
`BackendApplicationTests` ở đợt 1). `mvnw test` full suite chạy đúng một lần ở đợt cuối. Frontend
mỗi đợt chạy đủ `npm run build` + `npm run lint` (không warning).

**Thứ tự đợt 1-4 đã sắp lại (so với bản trước) để không có đợt nào làm vỡ biên dịch**: bản trước đặt
"gỡ bộ đệm" ở đợt 2, nhưng `JobRecommendationCandidateService.java` (chưa viết lại) vẫn gọi
`JobRecommendationRepository`/`JobRecommendation` tới tận đợt 4 cũ — xoá hai lớp đó ở đợt 2 làm
`test-compile` vỡ ngay. Thứ tự đúng: viết hạ tầng truy vấn mới trước (đợt 2), viết lại service để
**không còn tham chiếu** bộ đệm cũ (đợt 3), rồi **mới** xoá bộ đệm (đợt 4) khi không còn ai gọi.

1. **Backend — mở rộng điều kiện cứng U07 (R-H).** `PublicJobSearchCriteria` (categoryCode/
   locationCode → danh sách), `PUBLIC_JOB_FILTER_WHERE` (cờ Present + `IN`, giữ `OR IS NULL`),
   `JobRepository.searchPublicJobsSortedByNewest`/`SortedBySalaryDesc`/`searchOpenJobsNewest` (14
   tham số — `searchOpenJobsNewest` sửa cơ học theo mục 0.c, không đổi hành vi),
   `JobPublicService.search`/`searchByCriteria`, `JobPublicController` (`List<String>` lặp tham
   số), 2 factory mới `InvalidJobFilterException.tooManyCategoryCodes()`/`tooManyLocationCodes()`,
   sửa cơ học 3 lời gọi arity ở `JobPublicServiceTest.java:39-41,54-56,62-65` (R-H7 — đã chốt,
   không cần hỏi lại). `test-compile`, sau đó chạy riêng `JobPublicServiceTest`/
   `JobPublicIntegrationTest`/`JobCatalogGuardIntegrationTest` — bất kỳ test U07 khác đỏ ngoài 3
   lời gọi đã chốt → DỪNG và báo.
2. **Backend — truy vấn gợi ý dùng chung (R-V, R-C).** 2 phương thức mới trong `JobRepository`
   (biến thể (i)/(ii) gộp cờ `applyThreshold`, tie-break `j.id ASC`; biến thể (iii) riêng,
   tie-break `j.id DESC` theo mục 4), NOT EXISTS chu kỳ (R-C1),
   `CandidateProfileRepository.findEmbeddingTextByUserId`. Chưa đụng tới
   `JobRecommendationCandidateService.java`/bộ đệm cũ ở đợt này. `test-compile`.
3. **Backend — chọn nguồn + DTO + controller (R-V6, R-S, R-M, R-X4 phần backend nếu cần).** Viết
   lại hoàn toàn `JobRecommendationCandidateService.java` (thứ tự ưu tiên R-V6, tính
   `matchedConditions` R-M2, trạng thái R-S1) — **sau đợt này, service không còn import/gọi
   `JobRecommendationRepository`/`JobRecommendation`/`JobRecommendationCacheService` nào nữa**. DTO
   response mới, `JobRecommendationCandidateController` (route giữ nguyên). `test-compile`.
4. **Backend — gỡ bộ đệm FR-U04 (R-G1/R-G2).** **Trước khi xoá**: grep lại
   `JobRecommendationCacheService`/`JobRecommendationCacheScheduler`/`JobRecommendationRepository`/
   `JobRecommendation` trong `backend/src/main/java` để xác nhận không còn nơi nào gọi ngoài chính
   4 lớp sắp xoá — kết quả grep tại thời điểm viết đặc tả này (phải lặp lại grep ở đầu đợt, không
   tin kết quả cũ): **không có lời gọi thật nào từ luồng tải CV hay bất kỳ package khác** — chỉ có
   một **comment** (không phải lời gọi) nhắc tên `JobRecommendationCacheService` tại
   `ResumeParsedDataRepository.java:51` (dòng comment mô tả mục đích của
   `findEmbeddingTextByResumeId`), cần sửa comment này cho khỏi nhắc tên lớp sắp xoá. Nội bộ 4 lớp
   gọi nhau: `JobRecommendationCandidateService.java:15,19,28` (sẽ không còn sau đợt 3),
   `JobRecommendationCacheService.java` (chính nó), `JobRecommendationCacheScheduler.java:23,24,
   28,29` (gọi cả `JobRecommendationRepository` và `JobRecommendationCacheService`). Sau khi xác
   nhận: xoá `JobRecommendation.java`, `JobRecommendationRepository.java`,
   `JobRecommendationCacheService.java`, `JobRecommendationCacheScheduler.java`, cấu hình
   `app.job-recommendation.*`; xoá 2 file test cũ (ngoại lệ đã duyệt). Migration
   `V11__drop_job_recommendations.sql`. `test-compile`, **sau đó chạy riêng
   `BackendApplicationTests`** (`mvnw test -Dtest=BackendApplicationTests`) để xác nhận `V11` áp
   được trên Testcontainers và `ddl-auto: validate` không còn mong đợi bảng `job_recommendations`
   (đúng tinh thần FR-U14/FR-C05 bắt buộc kiểm riêng migration mới ngay ở đợt tạo ra nó). **Từ đợt
   này tới khi đợt 5 xanh: không chạy `spring-boot:run`/chạm DB dev** (mục 8).
5. **Backend — test.** Toàn bộ 7 test đối chiếu (mục 7) + 15 test mới đánh số (mục 7) — **không
   gồm** đo `EXPLAIN ANALYZE` (không phải test, xem mục 7 "Đo hiệu năng") hay kiểm "Xem tất cả"
   (hành vi frontend, chuyển sang soát tay đợt cuối). Thực hiện đo `EXPLAIN ANALYZE` 3 biến thể ở
   đợt này (ghi tạm, chép vào walkthrough ở đợt cuối). Chạy riêng từng lớp test mới bằng tay —
   **chưa** chạy full suite.
6. **Frontend — bộ lọc chọn nhiều (R-F).** `useJobFilters` (`categoryCode`/`locationCode` →
   `string[]`), `JobFilterBar` (`CatalogMultiCombobox` thay `CatalogCombobox`, cả desktop và
   `MobileFilterSheet`). `npm run build` + `npm run lint`.
7. **Frontend — khối gợi ý (R-L, R-M, R-X).** Viết lại `RecommendedJobs.tsx` (5 trạng thái, dòng
   nguồn, "Xem tất cả" ẩn/hiện + điền sẵn theo R-X3/R-X4, 3 thẻ đầu trên mobile theo R-L1), mở
   rộng `JobCard` (`matchedConditions`), `JobBoard` (prop `showRecommendations`),
   `CandidateJobListPage` (truyền `true`), `CandidateHomePage` (gọi lại `RecommendedJobs`, không
   đổi phần khác — R-L5). `npm run build` + `npm run lint`.
8. **Seed demo.** Thêm 1 ứng viên mới (`candidate_profiles` đầy đủ, ngành khác 3 người đã có, khớp
   1 job demo, đã qua onboarding, **không** có `resumes`, `embedding` để `NULL`). Dọn tham chiếu
   `job_recommendations`: xoá tên bảng khỏi danh sách ở `db/seed/reset-demo-db.sql:98` và
   `db/seed/export-ai-output.ps1:47`; **xoá bằng tay** khối `COPY`/`INSERT` rỗng của
   `job_recommendations` ở `db/seed/seed-demo-ai-output.sql:431-458` **trực tiếp trong file này**
   (sửa text, không chạy lại `export-ai-output.ps1` — chạy lại có rủi ro đổi cả phần còn lại của
   dump không liên quan tới FR-U15); cập nhật `db/seed/README.md` mục 6. Nạp lại theo
   `db/seed/README.md` mục 1 để xác nhận sạch — lần tiếp theo trong nhánh này được phép chạm DB
   dev, chỉ sau khi đợt 5 đã xanh.
9. **Đợt cuối.** `mvnw test` full suite + `npm run build` + `npm run lint`; soát tay desktop và
   375px ở cả hai route theo danh sách "Soát tay ở đợt cuối" (mục 7, gồm "Xem tất cả" và 3-thẻ-
   mobile), ghi walkthrough (gồm số đo `EXPLAIN ANALYZE` đã đo ở đợt 5); skill `srs-guard`; viết
   `docs/walkthrough/fr-u15-profile-recommend.md`; đổi trạng thái `ĐÃ HOÀN THÀNH <ngày>` ở đây, ở
   dòng FR-U15 mục 0 `docs/SRS.md`, tick `docs/ROADMAP.md` (gồm xoá/cập nhật 3 dòng nợ kỹ thuật
   liên quan `job_recommendations` đã liệt kê ở mục 0.b).

## 10. Nợ kỹ thuật dự kiến

- **Nhãn hình thức làm việc bị chép tay, không dùng chung.** Backend chưa có bảng nhãn
  `ONSITE`/`HYBRID`/`REMOTE` trước FR-U15 — hằng số `WORK_MODE_LABELS` (private trong
  `JobRecommendationCandidateService.java`) sao y 3 chuỗi từ
  `frontend/src/features/jobs/jobLabels.ts:39-43`. Đổi chữ nhãn (ví dụ "Tại văn phòng" →
  "Làm tại văn phòng") phải sửa **hai nơi** — frontend và file service này — không có cơ chế nào
  tự nhắc nếu quên một bên. Khi có FR thứ hai cần nhãn này (ví dụ FR-U13), nên rút thành hằng số
  dùng chung ở backend (gợi ý package `catalog/`) rồi để frontend đọc qua API, không giữ hai bản
  chép tay song song mãi.
- `MIN_SIMILARITY_SCORE = 0.40` (nhánh CV) giữ nguyên nợ đã ghi ở ROADMAP (gần như không lọc được
  ở quy mô dữ liệu hiện tại) — không sửa trong phạm vi FR-U15 (R-V9, mục 6 Ngoài phạm vi).
  Nhánh hồ sơ không ngưỡng kế thừa cùng rủi ro ở mức nhẹ hơn (phạm vi đã giới hạn bởi điều kiện
  cứng R-H).
- Không có claim/stale-reaper nào cần cho FR-U15 (khác FR-U04/FR-U14) — không còn job nền
  (`JobRecommendationCacheScheduler` đã gỡ), tính trực tiếp mỗi request nên không có khái niệm
  "lượt chạy dở dang".
- Giới hạn "tối đa 6"/"tối đa 3 mã mỗi loại" là hằng số cứng trong code (giống tiền lệ FR-U14) —
  đổi sau này cần sửa code, không có cấu hình.
- `JobRepository` phình thêm 2 phương thức truy vấn mới cộng 2 phương thức sẵn có
  (`searchPublicJobsSortedByNewest`/`SortedBySalaryDesc`) dùng chung `PUBLIC_JOB_FILTER_WHERE` —
  khi FR-U13 tới lượt code (sau FR-U15), có thể cần tham số hoá thêm phần "tách câu truy vấn từ
  ngôn ngữ tự nhiên" nhưng hạ tầng điều kiện cứng + xếp theo embedding đã sẵn, không cần viết lại
  từ đầu (đúng mục đích R-H6/mục 6 "chuẩn bị điểm nối").
- `CatalogMultiCombobox`/`CatalogCombobox` kế thừa hạn chế tìm theo nhãn (không bí danh "HCM"...)
  từ FR-C05 — không mở rộng thêm ở FR-U15.
- Chưa đo hiệu năng ở quy mô dữ liệu lớn (chỉ đo trên Testcontainers/DB dev với dữ liệu demo nhỏ,
  mục 7 "Đo hiệu năng") — giống đúng giới hạn đã ghi ở FR-U04 cho `EXPLAIN ANALYZE` lúc đó.
- **Truy vấn vector (biến thể i/ii) kèm điều kiện lọc cứng + `LIMIT 6` có thể trả ít hơn 6 dù còn
  job thoả mãn.** Nếu planner Postgres chọn Index Scan qua HNSW cho `ORDER BY embedding <=> vector
  LIMIT :limit`, chỉ mục trả về top-k gần nhất theo khoảng cách TRƯỚC khi áp các điều kiện `WHERE`
  khác (điều kiện cứng R-H, loại trừ chu kỳ R-C, ngưỡng R-V9) — một job nằm ngoài top-k ban đầu
  nhưng lẽ ra thoả mọi điều kiện khác sẽ không được xét tới, dù vẫn còn job hợp lệ. Ở quy mô dữ
  liệu demo hiện tại (ít job), planner nhiều khả năng chọn Seq Scan (áp `WHERE` trước, quét hết) —
  **chưa** quan sát được ảnh hưởng này trong demo. Phép đo `EXPLAIN ANALYZE` (mục 7) phải ghi rõ
  planner thật đã chọn gì ở quy mô hiện tại, không chỉ ghi "đã đo", để làm chứng cứ cho nợ này nếu
  cần xử lý khi dữ liệu lớn hơn (ví dụ tăng số ứng viên lọc trước khi `ORDER BY`, hoặc tăng
  `ef_search` của HNSW — chưa cần quyết định bây giờ).
- **Các lớp test tích hợp tạo job `OPEN` không tự dọn (`ResumeEmbeddingOrchestratorTest`,
  `JobEmbeddingOrchestratorTest`, `JobEmbeddingPipelineIntegrationTest` — không `@Transactional`
  cấp class, ghi thật vào Postgres Testcontainers dùng chung, tồn tại suốt phần còn lại của full
  suite).** Trước FR-U15, `JobRecommendationCacheServiceTest` (đã xoá ở đợt 4) chịu được nhiễm này
  nhờ assertion khoan dung (`contains`/`doesNotContain`, không `containsExactly`) vì bảng cache cũ
  không có `LIMIT`. Truy vấn mới (`findRankedMatchesByVector`/`findRankedMatchesByDesiresOnly`) áp
  `RECOMMENDATION_LIMIT = 6` ngay trong câu query (top-N) — job rác đủ để lấp đầy `LIMIT` trước khi
  tới job test thực sự cần kiểm, đẩy hẳn job đó ra ngoài kết quả. `JobRecommendationCandidateServiceTest`
  và `JobRecommendationCandidateControllerIntegrationTest` phải tự soft-delete (`deleted_at = now()`)
  toàn bộ job `OPEN` còn sót ở `@BeforeEach` trước khi tạo fixture riêng — cách ly kiểu mới, chưa có
  tiền lệ trong dự án, không giải quyết gốc rễ (các lớp nguồn gây nhiễm vẫn không dọn).

---

## Đã chốt (trước đây là "Cần người dùng quyết định" — đã duyệt, không hỏi lại)

1. **Xung đột giữa "test U07 không sửa khẳng định" và việc mở rộng `categoryCode`/`locationCode`
   sang danh sách (R-H2).** **3 lời gọi** (không phải 2) khớp đúng 12 tham số hiện tại —
   `JobPublicServiceTest.java:39-41` (`when(...)` của `search_oversizedSize_isClampedToMax`),
   `:54-56` (`when(...)`) và `:62-65` (`verify(...)`, cả hai của
   `search_blankKeyword_passesNullPattern`); thêm cờ `categoryCodesPresent`/`locationCodesPresent`
   nâng tổng tham số lên 14. **Đã chốt**: sửa CƠ HỌC — thêm đúng 2 `anyBoolean()` vào cả ba lời
   gọi, **giữ nguyên `isNull()`** ở vị trí `titlePattern` (chỗ nào đang có), **không đổi bất kỳ
   `assertThat(...)` nào**. Không coi đây là "sửa khẳng định" bị cấm. Chi tiết ở R-H7 (mục 3.2) và
   mục 8. Mọi test U07 khác đỏ vì lý do khác vẫn phải DỪNG và báo như thường.
2. **`PREPARING` khi cả CV và hồ sơ cùng "đang chờ"** (mục 3.5 R-V6.4) — **đã chốt giữ như đã
   viết**: báo `source=CV` (ưu tiên CV trong thông báo).
3. **Tên file service/test** — **đã chốt**: giữ `JobRecommendationCandidateService.java` (không
   đổi tên dù vai trò đổi từ đọc cache sang tính trực tiếp). Lớp test mới đặt tên
   `JobRecommendationCandidateServiceTest` (đã sửa cả 4 dòng liên quan trong bảng đối chiếu mục 7).
4. **Câu mơ hồ ở `docs/features/README.md` mục FR-U15** (lương/hình thức "khớp hoặc không khớp") —
   **đã sửa trong commit đặc tả** (cùng commit chốt REQUIREMENT.md/UI.md này).
