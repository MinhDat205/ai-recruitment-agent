# FR-U16 — Lưu tin tuyển dụng

> Trạng thái: ĐÃ DUYỆT (30/10/2026). Mọi quyết định ở mục 11 đã chốt 30/10/2026 (Q1–Q8; Q8 đổi FK sang
> `ON DELETE CASCADE`; bổ sung B1–B3 về `JobCard`, quyền riêng tư, không AI/không xếp hạng).
>
> Viết theo kế hoạch `docs/plans/2026-11-dot-bo-sung.md` (mục "FR-U16", R1–R10).

- Nhóm: Ứng viên
- Tóm tắt: Ứng viên lưu tin để xem lại ở trang "Tin đã lưu", chỉ ứng tuyển được tin còn mở và bỏ lưu bất kỳ lúc nào.
  Một bảng mới (V20), package `savedjob/`. **Không dùng AI, không xếp hạng, không tính điểm phù hợp.**
- Phụ thuộc: FR-C02, FR-U02, FR-U07, FR-U08 — cả bốn **Đã hoàn thành** (`docs/SRS.md` mục 0). Khớp danh sách phụ thuộc
  ở `docs/SRS.md:141`.
- Nhánh: `feat/fr-u16-saved-jobs` (tạo từ `main` tại `f0e6354`, 30/10/2026). Merge **trước** FR-C09 (V21).
- Mở rộng: FR-C02 (nút Lưu ở `/jobs/:id`), FR-U07 (nút Lưu trên thẻ việc làm ở danh sách), FR-U13 (thẻ kết quả tìm
  bằng mô tả), FR-U15 (thẻ gợi ý), FR-U08 (tách tình trạng tin thành thành phần dùng chung, hành vi không đổi) —
  khớp `docs/SRS.md:195-199`.

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

Đối chiếu trên `feat/fr-u16-saved-jobs` tại `f0e6354` (30/10/2026, cây làm việc sạch).

### (a) Thẻ tin và trang chi tiết tin phía ứng viên

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | Một component thẻ tin duy nhất | Đúng: `JobCard` | `features/jobs/JobCard.tsx:59-142` |
| a2 | Nơi dùng `JobCard` | **4 nơi, 4 màn hình** (kế hoạch kể 3) | Danh sách Việc làm `JobList.tsx:78` (dùng ở `/` qua `PublicJobListPage.tsx:7` và `/candidate` qua `CandidateJobListPage.tsx:12`); kết quả tìm bằng mô tả `NaturalLanguageResults.tsx:87-92`; khối gợi ý `RecommendedJobs.tsx:143`, khối này dùng ở cả `/candidate` và `/candidate/dashboard` (`CandidateHomePage.tsx:130`). Thêm nút vào `JobCard` thì Bảng tin cũng có nút — chấp nhận (R-B1) |
| a3 | Toàn thẻ là **một** `<Link>` | Đúng — **không** lồng được nút vào trong | `JobCard.tsx:71-74` (`<Link to=/jobs/:id className="flex …">` bọc cả thẻ). `<button>` trong `<a>` là HTML không hợp lệ (nội dung tương tác lồng nhau), bấm nút sẽ kích cả điều hướng. Phải đổi cấu trúc thẻ (R-B3) |
| a4 | DTO thẻ tin | `JobSummaryResponse` 16 field, không có cờ `saved` | `job/dto/JobSummaryResponse.java:8-24`; frontend `features/jobs/types.ts:7-24` |
| a5 | `JobSummaryResponse` dựng ở đâu | Chỉ `JobPublicService.toSummary` — **private** | `JobPublicService.java:311-330`. `getByIds` (`:262-275`) public nhưng **lọc tin mở** qua `findOpenJobsByIdIn` → không dùng được cho danh sách đã lưu (có tin đã đóng) |
| a6 | Trang chi tiết tin chỉ mở tin công khai | Đúng | `JobPublicService.getDetail` → `findOpenJobById` (`JobPublicService.java:250-254`); trang lỗi "Không tìm thấy tin tuyển dụng." (`PublicJobDetailPage.tsx:33-44`). Tin `EXPIRED`/`PAUSED`/`CLOSED`/nháp/xoá mềm mở `/jobs/:id` đều ra trang này |
| a7 | Chỗ đặt nút ở trang chi tiết | Cạnh `ApplyButton` | `PublicJobDetailPage.tsx:93-95` |
| a8 | Mẫu "HR ẩn, khách sang đăng nhập" | Có | `ApplyButton.tsx:9-24`: HR → `null`; khách → `navigate('/login', { state: { from: location } })` |
| a9 | Menu ứng viên | 5 mục, desktop + sheet điện thoại dùng chung `NAV_ITEMS` | `CandidateLayout.tsx:21-44`, desktop `:70-84`, sheet `:106-118` |
| a10 | Nhóm route ứng viên | `ProtectedRoute(CANDIDATE)` + `RequireCandidateProfileOnboarding` | `App.tsx:67-91` |
| a11 | Cache truy vấn bị xoá khi đổi tài khoản | Đúng | `AuthContext.tsx:30,45,62` (`queryClient.clear()`) — danh sách id đã lưu của ứng viên A không rò sang tài khoản B |

### (b) Tình trạng tin (FR-U08) — có sẵn gì để dùng lại

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| b1 | Điều kiện "tin mở" có **một** nguồn | `findOpenJobById`: `status = 'OPEN' AND deleted_at IS NULL AND (deadline IS NULL OR deadline >= CURRENT_DATE)` | `JobRepository.java:278-286`. Bản theo danh sách id, **cùng** điều kiện: `findOpenJobsByIdIn` (`:288-302`) |
| b2 | `availabilityOf` + `JobAvailability` | **private** trong `ApplicationService`; enum lồng trong DTO của U08 | `ApplicationService.java:262-278`; `ApplicationCandidateDetailResponse.java:52-58` (import ở `ApplicationService.java:15`) |
| b3 | `availabilityOf` gọi DB **mỗi tin một lần** | Đúng | `:268` gọi `findOpenJobById(job.getId())`. Dùng nguyên trạng cho danh sách 100 tin = 100 câu (N+1). Cần bản theo lô dùng `findOpenJobsByIdIn` (R-A2) |
| b4 | Thứ tự xét của U08 | Xoá mềm → `UNAVAILABLE`; mở → `OPEN`; `OPEN` không mở → `EXPIRED`; `PAUSED`; `CLOSED`; `DRAFT` → `UNAVAILABLE` | `ApplicationService.java:264-277`; U08 R-D4 |
| b5 | Tin xoá mềm vẫn đọc được | Đúng | `Job` không có `@SQLRestriction` (U08 mục 0.c); `findById` trả cả tin xoá mềm (`ApplicationService.java:182-187`) |
| b6 | Nhãn + kiểu trung tính phía frontend | Có, nhưng nằm trong thư mục của U08 và viết thẳng trong component | Nhãn `candidateApplicationDetail/jobAvailabilityLabels.ts:5-11`; kiểu `candidateApplicationDetail/types.ts:6`; thẻ nhãn viết thẳng ở `JobSummarySection.tsx:37-39` (`border border-m3-outline bg-m3-surface … text-m3-on-surface`) |
| b7 | Liên kết sang trang tin chỉ khi `OPEN` | Đúng (U08 R-D5) | `JobSummarySection.tsx:52-56` |
| b8 | Backend `apply` chặn tin không mở | Đúng | `ApplicationService.java:74-78` (`findOpenJobById` → 404 `JOB_NOT_FOUND`) |
| b9 | "Đơn ở chu kỳ hiện tại" | Tối đa **một** đơn mỗi (tin, ứng viên, chu kỳ) | `uq_application_per_cycle` (`V1__init_schema.sql:222`); cùng định nghĩa với FR-U15 R-C1 (đơn mọi trạng thái, kể cả `WITHDRAWN`) |

### (c) Quy ước API ứng viên

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| c1 | `/api/candidates/**` chỉ cho CANDIDATE ở filter chain | Đúng (HR → 403, không token → 401) | `SecurityConfig.java:55-56` |
| c2 | Lấy `candidateId` | `UUID.fromString(authentication.getName())`, không nhận từ path/body | `ApplicationCandidateController.java:38,44,52` |
| c3 | Bản ghi của người khác | 404 cùng mã với "không tồn tại" (U08 R-Q2) | `ApplicationService.java:178-180` |
| c4 | Phân trang | `PageResponse` có sẵn (`common/dto/PageResponse.java:7-14`); danh sách nhỏ của ứng viên trả `List` thẳng | `ApplicationCandidateController.java:42-46` (`List<ApplicationSummaryResponse>`) |
| c5 | Sắp xếp ổn định | Tiền lệ **chưa** có khoá cuối | `JobApplicationRepository.java:95` (`ORDER BY a.applied_at DESC`). FR-U16 kết thúc mọi `ORDER BY` bằng `id` (R-A5) |
| c6 | Lỗi 409 | Mỗi exception một handler, mã tiếng Anh + câu tiếng Việt cố định | `GlobalExceptionHandler.java:133-138` (mẫu); handler `DataIntegrityViolationException` chỉ dịch constraint đã biết, còn lại 500 (`:577-589`) |

### (d) Migration

- Cao nhất trên `main`: `V19__blind_review.sql` (không có V9 — khoảng trống có sẵn, không liên quan). **V20 còn trống.**
- **V21 đã dành cho FR-C09**: đặc tả trên nhánh `feat/fr-c09-reconsideration` (commit `b650047`) khai
  `V21__application_reconsideration_requests.sql` (REQUIREMENT FR-C09 mục 4.1) và ghi phải rebase sau khi FR-U16 merge.

### (e) Chỗ dọn dữ liệu có xoá `jobs` hoặc `users`

| Chỗ | Hiện trạng | Ảnh hưởng của `saved_jobs` (FK `ON DELETE CASCADE`, mục 4.1, Q8) |
|---|---|---|
| `db/seed/reset-test-data.sql:67-78` | Tập xoá: `t_users` (user test + dải `f2000000-`), `t_jobs` (tin của công ty test + dải `f2`) | Ứng viên demo có thể lưu tin test qua UI, ứng viên test có thể lưu tin demo → dòng `saved_jobs` trỏ vào cả hai tập |
| `db/seed/reset-test-data.sql:132-147` | Mục 5 xoá con của `jobs` rồi `DELETE FROM jobs` (`:142`); mục 6 `DELETE FROM users` (`:147`) | `:142`/`:147` tự xoá dòng `saved_jobs` con theo `CASCADE` → không cần câu xoá thêm |
| `db/seed/seed-demo-structural-1b.sql:87-106` | Xoá dải `d1b00000-%` "con trước cha", `DELETE FROM jobs` (`:104`), `DELETE FROM users` (`:106`) | Dòng lưu tin dải `d1b` của tầng seed demo mới mất theo `CASCADE` khi nạp lại 1b → không cần câu xoá thêm |
| `db/seed/reset-demo-db.sql:81-122` | Một câu `TRUNCATE … CASCADE` (`:92-122`), liệt kê tường minh (comment `:89-91`), tiền đề "Can DB o V19 tro len" (`:86`) | Không lỗi dù thiếu tên, nhưng vẫn phải liệt kê `saved_jobs` (quy ước liệt kê tường minh) và sửa tiền đề |

Quy ước FK đã kiểm (30/10/2026) trước khi chốt Q8: `CLAUDE.md` không có câu nào về `RESTRICT`/`CASCADE`; `docs/SRS.md`
không có (`:127` chỉ là "không dùng xoá cứng" khi rút đơn, FR-U06); migration V1–V19 dùng cả hai, `CASCADE` cho bảng phụ
thuộc không mang giá trị lưu vết (vd `notifications.user_id` `V1__init_schema.sql:342`, `job_recommendations`
`:315-316`, `job_ai_generations.job_id` `V15__job_ai_generations.sql:7`), `RESTRICT` cho dữ liệu quyết định/audit
(`job_applications` `V1__init_schema.sql:208-209`, `job_blind_review_changes.changed_by` `V19__blind_review.sql:10`).
Không có quy ước bắt buộc `RESTRICT`.
| `seed-test.sql`, `seed-demo-structural.sql`, `seed-demo-ai-output*.sql` | Không có `DELETE FROM jobs`/`users` (chỉ `ON CONFLICT`) | Không ảnh hưởng |

### (f) Trần 100 tin — chốt được ở đâu

- Postgres không có ràng buộc khai báo kiểu "tối đa N dòng mỗi khoá": `CHECK` không đếm được dòng khác; partial unique
  index chỉ chốt "tối đa **một**" (khuôn `uq_scoring_run_in_progress`, CLAUDE.md §4), không chốt "tối đa 100".
- Đếm rồi `INSERT` ở service, không khoá: hai request đồng thời khi đang có 99 tin đều đếm được 99 và cùng chèn → 101.
  `INSERT … SELECT … WHERE (SELECT count(*) …) < 100` cũng không an toàn ở mức cô lập `READ COMMITTED`.
- Cách chốt được, không cần trigger: **khoá hàng `users` của chính ứng viên** trong transaction ghi, rồi mới đếm. Tiền lệ
  khoá hàng cha trong transaction ghi: `ScreeningQuestionRepository.java:39-43` (`FOR SHARE` hàng `jobs`),
  `JobApplicationRepository.java:53-67`. FR-U16 cần khoá **loại trừ lẫn nhau** giữa hai request lưu nên dùng
  `FOR NO KEY UPDATE` (R-L3). Không dùng `FOR UPDATE`: nó xung đột với khoá `FOR KEY SHARE` mà mọi `INSERT` có FK trỏ tới
  `users` tự lấy (vd ứng viên đang nộp đơn).
- Khoá chỉ chặn hai request của **cùng** ứng viên; ứng viên khác không chờ nhau. Trigger đếm ở DB (chặn cả ghi tay) là
  phương án khác, nặng hơn — câu hỏi Q1, mục 11.

## 1. Mục đích

Cho ứng viên lưu tin để xem lại sau, thay vì phải tìm lại từ đầu. Trang "Tin đã lưu" cho biết tin nào còn nhận hồ sơ và
đưa ứng viên vào **đúng** luồng ứng tuyển hiện có; tin không còn mở thì nói rõ lý do.

FR-U16 **không dùng AI, không xếp hạng, không tính điểm phù hợp**. Danh sách đã lưu chỉ sắp theo
`saved_at DESC, id DESC` (R-V1).

## 2. Luồng người dùng

**Ứng viên đã đăng nhập**
1. Ở thẻ việc làm (`JobCard`: danh sách Việc làm, kết quả tìm bằng mô tả FR-U13, khối gợi ý FR-U15 ở Việc làm và Bảng
   tin) hoặc trang chi tiết `/jobs/:id`: bấm nút Lưu. Nút đổi sang trạng thái "Đã lưu"; bấm lần nữa là Bỏ lưu.
2. Mở "Tin đã lưu" (mục mới trong menu ứng viên, `/candidate/saved-jobs`): tin lưu gần nhất ở trên, kèm nhãn tình trạng
   tin và số tin đang lưu trên tổng 100.
3. Tin đang mở, chưa có đơn ở chu kỳ hiện tại: nút "Ứng tuyển" dẫn tới `/jobs/:id/apply` (luồng có sẵn: chọn CV, câu hỏi
   sàng lọc, consent).
4. Đã có đơn ở chu kỳ hiện tại của tin: thay nút bằng "Đã ứng tuyển" + liên kết "Xem đơn" tới
   `/candidate/applications/:id`.
5. Tin không còn mở (và chưa có đơn): nút "Ứng tuyển" bị khoá, kèm câu lý do theo tình trạng.
6. Bấm "Bỏ lưu" ở trang Tin đã lưu hoặc trên thẻ: tin rời khỏi danh sách.

**Khách (chưa đăng nhập)**
- Nút Lưu vẫn hiện trên thẻ và trang chi tiết; bấm → `/login` (giữ `state.from` như `ApplyButton`). Đăng nhập xong
  **không** tự lưu (mục 6).

**HR**
- Không thấy nút Lưu ở bất kỳ đâu; gọi API `/api/candidates/saved-jobs/**` → 403.

**Hệ thống**
- Không gọi LLM/embedding ở bất kỳ bước nào. Không đọc/ghi bảng chấm điểm.

## 3. Quy tắc nghiệp vụ

### 3.1 Lưu, bỏ lưu (R-S) — kế hoạch R1–R4

- **R-S1. Ai được lưu (R1).** Chỉ role CANDIDATE (filter chain, `SecurityConfig.java:55-56`). `candidateId` lấy từ JWT
  (mục 0.c2), không nhận từ path hay body.
- **R-S2. Tin được lưu (R2).** Chỉ tin mà `jobRepository.findOpenJobById(jobId)` trả kết quả — **đúng một** điều kiện với
  trang tin công khai và `apply` (mục 0.b1, b8). Không viết điều kiện ngày thứ hai.
- **R-S3. Tin không lưu được → 409 `JOB_NOT_SAVEABLE`.** Áp cho **mọi** trường hợp `findOpenJobById` rỗng: `EXPIRED`,
  `PAUSED`, `CLOSED`, nháp, xoá mềm, **và UUID không tồn tại**. Một mã, một câu, không phân biệt — để không lộ tin nháp
  có tồn tại hay không (UI_GUIDE mục 5). Câu cố định "Tin tuyển dụng này hiện không nhận lưu vì không còn mở.".
  Câu hỏi Q2 (mục 11) nếu muốn 404 cho UUID không tồn tại.
- **R-S4. Lưu lặp là idempotent (R3).** `PUT` tin đang lưu → 204, không tạo dòng mới, **không** đổi `saved_at`.
- **R-S5. Bỏ lưu là soft delete (R4).** `DELETE` đặt `deleted_at = now()` cho dòng đang lưu. Không có `DELETE FROM`
  (CLAUDE.md §4). Gọi khi tin chưa lưu, đã bỏ lưu, hay `jobId` không tồn tại → vẫn 204 (idempotent, không lộ gì).
- **R-S6. Lưu lại sau khi bỏ lưu (R4).** Dòng cũ được dùng lại: `deleted_at = NULL`, `saved_at = now()`. Mỗi cặp
  (ứng viên, tin) **đúng một dòng** — chốt bằng `UNIQUE (candidate_id, job_id)` ở DB (R-D1).
- **R-S7. Bỏ lưu không cần tin còn mở.** Tin đã đóng/gỡ vẫn bỏ lưu được (R8). Bỏ lưu rồi thì **không** lưu lại được nữa
  nếu tin không còn mở (R-S2) — giao diện nói rõ ở hộp xác nhận (UI.md mục 6, câu hỏi Q5).
- **R-S8. Thời điểm do DB gán.** `saved_at`/`deleted_at` lấy `now()` của Postgres trong câu SQL, không truyền `Instant`
  từ JVM — để thứ tự "lưu gần nhất ở trên" không lệch theo đồng hồ của từng instance.

### 3.2 Trần 100 tin (R-L) — kế hoạch R9

- **R-L1.** Tối đa **100** dòng đang lưu (`deleted_at IS NULL`) mỗi ứng viên, đếm **mọi** tin bất kể tình trạng (tin đã
  đóng vẫn chiếm chỗ cho tới khi bỏ lưu). Hằng số khai **một** chỗ (`SavedJobService.MAX_ACTIVE_SAVED_JOBS = 100`).
- **R-L2.** Vượt trần → 409 `SAVED_JOB_LIMIT_REACHED`, câu cố định "Bạn đã lưu tối đa 100 tin. Hãy bỏ lưu bớt để lưu tin
  mới.". Chỉ xét trần khi request **thêm** một dòng đang lưu (tạo mới hoặc lưu lại dòng đã bỏ); `PUT` tin đang lưu khi đã
  đủ 100 → 204 (R-S4).
- **R-L3. Chốt đồng thời.** Trong **một** transaction ghi: (1) kiểm R-S2; (2) khoá hàng
  `SELECT id FROM users WHERE id = :candidateId FOR NO KEY UPDATE`; (3) đọc dòng `(candidate_id, job_id)`; (4) đếm dòng
  đang lưu; (5) ghi. Hai request của cùng ứng viên chạy nối tiếp từ bước 2; ở `READ COMMITTED` câu sau bước chờ khoá
  thấy dòng request trước vừa commit. Lý do và phương án khác: mục 0.f.
- **R-L4.** Khoá ở R-L3 đồng thời chặn race "hai `PUT` cùng tin chưa lưu": request thứ hai thấy dòng của request thứ nhất
  ở bước 3 → nhánh idempotent (204). `UNIQUE (candidate_id, job_id)` vẫn là chốt chặn cuối ở DB; nếu vẫn vi phạm (không
  được xảy ra khi có khoá) thì để handler mặc định trả 500 — **không** thêm nhánh dịch `uq_saved_job_per_candidate`
  sang 204/409 để che lỗi khoá.

### 3.3 Tình trạng tin (R-A) — kế hoạch R5, R10

- **R-A1. Tách thành phần dùng chung.** `JobAvailability` (5 giá trị, giữ nguyên tên và thứ tự) chuyển từ enum lồng
  trong `ApplicationCandidateDetailResponse` sang `job/JobAvailability.java` (public). Logic `availabilityOf` chuyển sang
  bean `job/JobAvailabilityResolver` (public). `ApplicationService` gọi resolver thay cho method private. JSON của
  FR-U08 E1 **không đổi** (cùng tên khoá, cùng 5 chuỗi) — test FR-U08 hiện có (`ApplicationCandidateDetailControllerIntegrationTest`
  T6, `:418-471`) xanh mà không sửa.
- **R-A2. Bản theo lô, không N+1.** Resolver có hai method:
  - `JobAvailability of(Job job)` — cho FR-U08, hành vi y hệt `ApplicationService.java:264-277`.
  - `Map<UUID, JobAvailability> of(Collection<Job> jobs)` — cho FR-U16: **một** lời gọi `findOpenJobsByIdIn(ids)` cho cả
    tập rồi áp đúng thứ tự xét của R-D4 FR-U08. Tập rỗng → không gọi DB.
  Hai method dùng chung một hàm thuần "xét theo thứ tự" (nhận `Job` + cờ `isOpen`), để thứ tự xét chỉ viết một lần.
  Không viết điều kiện ngày ở Java; điều kiện "mở" chỉ nằm ở hai câu SQL có sẵn (mục 0.b1).
- **R-A3. Ý nghĩa 5 giá trị** giữ nguyên FR-U08 R-D4. `UNAVAILABLE` gộp tin xoá mềm và tin đưa về nháp.
- **R-A4. Frontend không tự suy** tình trạng từ `deadline`/ngày hiện tại; chỉ đọc `availability` từ API.
- **R-A5. Nhãn và kiểu (R10).** Một kiểu trung tính cho cả 5 giá trị (`border border-m3-outline bg-m3-surface
  text-m3-on-surface`), nhãn của FR-U08 nguyên văn. Tách thành component dùng chung `JobAvailabilityBadge` (UI.md mục 5);
  `JobSummarySection` của FR-U08 dùng component này, giao diện không đổi. Tài liệu FR-U08 (`ĐÃ HOÀN THÀNH`) vẫn ghi
  `candidateApplicationDetail/jobAvailabilityLabels.ts` (U08 UI.md mục 5a) và `ApplicationService.availabilityOf` — **không
  sửa** tài liệu đã xong; walkthrough của FR-U16 ghi lại việc chuyển file/method.

### 3.4 Danh sách "Tin đã lưu" (R-V) — kế hoạch R6–R8

- **R-V1. Thứ tự.** `ORDER BY s.saved_at DESC, s.id DESC` — khoá cuối `id` để thứ tự xác định khi `saved_at` trùng
  (memory dự án: mọi `ORDER BY` kết thúc bằng cột duy nhất). Không sắp theo điểm, độ phù hợp hay tiêu chí nào khác.
- **R-V2. Không phân trang.** Tối đa 100 dòng (R-L1) → trả `List` một lần, như `GET /api/candidates/applications`
  (mục 0.c4). Không tham số `page`/`size`.
- **R-V3. Đơn ở chu kỳ hiện tại (R7).** Mỗi dòng kèm đơn của chính ứng viên có `job_id` = tin, `recruitment_cycle` =
  chu kỳ **hiện tại** của tin, **mọi** trạng thái (kể cả `WITHDRAWN` — rút rồi cũng không nộp lại được chu kỳ đó,
  `uq_application_per_cycle`). Có đơn → khối `application {id, status}`; không có → `null`. Tin mở chu kỳ mới → đơn chu
  kỳ cũ không còn là "hiện tại" → nút Ứng tuyển hiện lại.
- **R-V4. Nút theo dòng, xét theo thứ tự:**

  | Điều kiện | Hiển thị |
  |---|---|
  | `application != null` | "Đã ứng tuyển" + `ApplicationStatusBadge` + liên kết "Xem đơn" (`/candidate/applications/{id}`), bất kể `availability` |
  | `availability = OPEN` | Nút "Ứng tuyển" → `/jobs/{jobId}/apply` |
  | khác | Nút "Ứng tuyển" bị khoá + câu lý do theo `availability` (UI.md mục 7) |

- **R-V5. Liên kết sang trang tin chỉ khi `OPEN`** (như FR-U08 R-D5). Kế hoạch R8 chỉ nói tin đã gỡ; nhưng
  `/jobs/:id` cũng trả "Không tìm thấy" cho `EXPIRED`/`PAUSED`/`CLOSED` (mục 0.a6), nên mọi giá trị khác `OPEN` đều
  không có liên kết.
- **R-V6. Tin đã gỡ (`UNAVAILABLE`, R8).** Chỉ hiện tên tin, tên công ty (+ logo), nhãn "Không còn đăng", ngày lưu,
  khối đơn (nếu có) và nút Bỏ lưu. **Không** trả lương, ngành nghề, khu vực, hạn nộp, hình thức (field `summary = null`,
  mục 4.3): nội dung tin nháp/đã xoá không còn công khai, HR có thể đã sửa sau khi gỡ.
- **R-V7. Tin `EXPIRED`/`PAUSED`/`CLOSED`.** Hiện đủ tóm tắt như thẻ (bản **hiện tại** của tin) + nhãn tình trạng; không
  liên kết (R-V5).
- **R-V8. Không thêm đường ứng tuyển (R6).** Trang chỉ điều hướng sang `/jobs/:id/apply`; không form, không gọi
  `POST /api/candidates/applications`. Backend `apply` vẫn chặn tin không mở (mục 0.b8) — nếu tin đóng giữa lúc tải
  trang và lúc bấm, trang nộp đơn tự báo lỗi như hiện tại.
- **R-V9. Số đếm.** Đầu trang hiện "Đang lưu {n}/100 tin", `n` = độ dài danh sách trả về.

### 3.5 Nút Lưu trên thẻ và trang chi tiết (R-B)

- **R-B1. Vị trí.** Mọi nơi dùng `JobCard` (mục 0.a2) và `/jobs/:id` (cạnh `ApplyButton`). Bảng tin có nút vì dùng
  chung `RecommendedJobs` — không thêm prop để tắt.
- **R-B2. Theo vai trò** (khuôn `ApplyButton`): HR → không render; khách → nút hiện ở trạng thái "chưa lưu", bấm sang
  `/login` với `state.from`; ứng viên → nút bật/tắt theo `GET /saved-jobs/ids`. *(B5)* Query ids chỉ chạy khi
  `AuthContext` có người dùng role `CANDIDATE` (`enabled: user?.role === 'CANDIDATE'`); khách và HR **không** gửi
  request nào tới `/api/candidates/saved-jobs/**`.
- **R-B3. Đổi cấu trúc `JobCard`.** Thẻ không còn là một `<Link>` bọc tất cả. Thẻ thành `<article>` có `relative`; tiêu
  đề là `<Link>` phủ toàn thẻ bằng lớp giả `after:absolute after:inset-0` (cả thẻ vẫn bấm được như cũ); nút Lưu là
  `<button>` anh em của `<Link>`, nằm trên lớp phủ (`relative z-10`). Giao diện hiện có (logo, tiêu đề, hạn nộp, lương,
  tag, nhãn "Chưa chuẩn hoá", dòng điều kiện khớp) giữ nguyên vị trí và chữ; chỉ thêm nút (UI.md mục 4a).
- **R-B4. Trạng thái nút lấy từ một nguồn.** Ứng viên: `useSavedJobIdsQuery` (một lần gọi `GET /saved-jobs/ids` cho cả
  trang, dùng chung giữa các thẻ qua cache TanStack Query). *(B6)* Mọi thẻ và trang chi tiết đọc **cùng một** query
  `['saved-job-ids']`; không gọi API theo từng thẻ. **Không** thêm cờ `saved` vào `JobSummaryResponse`,
  `JobDetailResponse` hay bất kỳ API `/api/public/**` nào (mục 4.4).
- **R-B5. Cập nhật lạc quan** *(B6, thay câu "không cập nhật lạc quan" của bản duyệt f514a03)*. Bấm Lưu/Bỏ lưu: hủy
  fetch đang chạy của `['saved-job-ids']`, ghi lại bản cũ, sửa cache ngay (thêm/bớt `jobId`) — mọi thẻ cùng tin đổi trạng
  thái tức thì. API lỗi → hoàn lại đúng bản cũ và báo lỗi (R-B6). API thành công → **không** refetch `['saved-job-ids']`;
  chỉ đánh dấu cũ `['saved-jobs']` (danh sách của trang Tin đã lưu). Trong lúc request của **chính** tin đó đang chạy, nút
  của tin đó khoá để không gửi hai request ngược chiều; tin khác bấm được bình thường.
- **R-B6. Lỗi.** 409 `JOB_NOT_SAVEABLE`/`SAVED_JOB_LIMIT_REACHED` hoặc lỗi mạng/5xx: hoàn lại cache (R-B5) rồi hiện câu
  backend trả về (409) hoặc câu cố định (mạng/5xx, UI.md mục 7) ngay dưới nút (`role="status"`). Không dùng snackbar
  (dự án chưa có, UI_GUIDE mục 3).

### 3.6 Ràng buộc chung (R-G)

- **R-G1. Không AI.** Không gọi `ChatModel`/`EmbeddingModel`, không thuộc ngoại lệ K3, không dùng K1–K4.
- **R-G2. Không xếp hạng, không điểm.** Không tính, lưu hay hiện điểm phù hợp, độ tương đồng, thứ hạng hay nhãn "phù hợp"
  cho tin đã lưu. Danh sách chỉ sắp theo thời điểm lưu (R-V1).
- **R-G3. Không lộ cho HR.** Không endpoint `/api/hr/**` nào đọc `saved_jobs`; không số đếm "bao nhiêu người đã lưu" ở
  bất kỳ đâu (mục 6).
- **R-G4. Không dùng tin đã lưu cho gợi ý.** FR-U04/U15/U13 không đọc `saved_jobs` (mục 6).
- **R-G5. Màu.** Nhãn tình trạng và nút Lưu không dùng cặp đỏ/xanh gợi ý tốt–xấu (CLAUDE.md §8). Trạng thái "Đã lưu"
  phân biệt bằng icon đặc + nhãn chữ/`aria-pressed`, không chỉ bằng màu (UI_GUIDE mục 6).

## 4. Dữ liệu & quyền truy cập

### 4.1 Migration `V20__saved_jobs.sql` (SQL dự kiến)

```sql
-- FR-U16: ung vien luu tin tuyen dung de xem lai. Moi cap (ung vien, tin) dung MOT dong: bo luu = dat deleted_at
-- (soft delete, khong DELETE FROM); luu lai = xoa deleted_at + cap nhat saved_at. Tran 100 tin dang luu chot o
-- service bang khoa hang users (REQUIREMENT R-L3) - Postgres khong co rang buoc khai bao "toi da N dong".
-- FK ON DELETE CASCADE: dau trang khong co gia tri luu vet, mat theo ban ghi cha la dung nghia (khac
-- job_applications va yeu cau xem xet lai FR-C09 la du lieu quyet dinh, giu RESTRICT). Ung dung khong xoa cung
-- users/jobs (soft delete); CASCADE chi co tac dung voi script don du lieu va thao tac tay.
CREATE TABLE saved_jobs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id       UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    saved_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT uq_saved_job_per_candidate UNIQUE (candidate_id, job_id)
);

-- Danh sach "Tin da luu" va dem tran: chi dong dang luu, moi luu gan nhat truoc.
CREATE INDEX idx_saved_jobs_candidate_active
    ON saved_jobs (candidate_id, saved_at DESC)
    WHERE deleted_at IS NULL;
```

Không sửa V1–V19. Không có cột `verdict`/`label`/`isQualified`/`passed`/`recommendation`, không cột điểm (CLAUDE.md §7).
Không thêm cột `role` hay CHECK role: chỉ ứng viên ghi được nhờ filter chain (R-S1), như `job_applications.candidate_id`.

### 4.2 Endpoint

Mọi endpoint ở `savedjob/SavedJobCandidateController`, `@RequestMapping("/api/candidates/saved-jobs")`. Lỗi chung:
401 `UNAUTHENTICATED` (không token), 403 (role HR, filter chain), 400 mặc định của Spring khi `jobId` không phải UUID.

| # | Endpoint | Thành công | Lỗi riêng |
|---|---|---|---|
| E1 | `PUT /api/candidates/saved-jobs/{jobId}` | 204, không body (tạo mới, lưu lại, hoặc đã lưu sẵn) | 409 `JOB_NOT_SAVEABLE` (R-S3); 409 `SAVED_JOB_LIMIT_REACHED` (R-L2) |
| E2 | `DELETE /api/candidates/saved-jobs/{jobId}` | 204, không body, mọi trường hợp (R-S5) | — |
| E3 | `GET /api/candidates/saved-jobs` | 200, `List<SavedJobResponse>` (R-V1, R-V2) | — |
| E4 | `GET /api/candidates/saved-jobs/ids` | 200, `List<UUID>` — `job_id` các dòng đang lưu, cùng thứ tự E3 | — |

Thứ tự kiểm ở E1: R-S2 (409 `JOB_NOT_SAVEABLE`) → khoá R-L3 → dòng hiện có → trần (409 `SAVED_JOB_LIMIT_REACHED`) → ghi.

Quyền sở hữu: không endpoint nào nhận id của dòng `saved_jobs`; mọi câu SQL lọc `candidate_id = :candidateId` lấy từ JWT,
nên không có đường đọc/sửa dòng của ứng viên khác (test T9).

### 4.3 DTO (E3)

```java
// FR-U16 E3 - mot dong "Tin da luu". KHONG them field nao khac; khong diem, khong hang, khong do phu hop (R-G2).
public record SavedJobResponse(
        UUID jobId,
        String title,                    // ban HIEN TAI cua tin
        CompanyRef company,              // ten + logo HIEN TAI (CompanyRef cua job/dto)
        Instant savedAt,
        JobAvailability availability,    // R-A, dung 5 gia tri
        JobSummaryResponse summary,      // null khi availability = UNAVAILABLE (R-V6); khac -> du 16 field nhu the tin
        CurrentApplication application) { // null khi chua co don o chu ky hien tai (R-V3)

    public record CurrentApplication(UUID id, ApplicationStatus status) {}
}
```

`summary` dựng bằng **chính** hàm dựng `JobSummaryResponse` của `JobPublicService` (mục 0.a5): tách `toSummary` thành
method public dùng chung (vd `JobSummaryMapper` trong `job/`, Plan Mode chốt tên), kết quả không đổi cho mọi nơi gọi cũ.
Không viết hàm dựng thứ hai.

Danh sách cấm trong JSON của E3 và E4 (so **nguyên tên khoá**, mọi cấp, như T4 của FR-U08): `saved`, `isSaved`,
`savedCount`, `score`, `matchScore`, `similarityScore`, `rank`, `totalScore`, `recommendation`, `verdict`, `label`,
`isQualified`, `passed`, `candidateId`, `deletedAt`, `description`, `requirements`, `recruitmentCycle`.

### 4.4 Câu truy vấn (SQL dự kiến, Plan Mode in ra chat để duyệt trước khi ghi — memory dự án)

**E3 — danh sách** (native, `savedjob/SavedJobRepository`):

```sql
SELECT s.id AS savedJobId, s.job_id AS jobId, s.saved_at AS savedAt,
       ja.id AS applicationId, ja.status AS applicationStatus
FROM saved_jobs s
JOIN jobs j ON j.id = s.job_id
LEFT JOIN job_applications ja
       ON ja.job_id = s.job_id
      AND ja.candidate_id = s.candidate_id
      AND ja.recruitment_cycle = j.recruitment_cycle
WHERE s.candidate_id = :candidateId AND s.deleted_at IS NULL
ORDER BY s.saved_at DESC, s.id DESC
```

`LEFT JOIN` ra tối đa một đơn mỗi dòng nhờ `uq_application_per_cycle`. Sau đó, **số câu cố định** bất kể số tin:
`jobRepository.findAllById(jobIds)` (gồm tin xoá mềm, mục 0.b5), `companyRepository.findByIdIn(companyIds)` (khuôn
`JobPublicService.loadCompanies`, `:277-281`), `JobAvailabilityResolver.of(jobs)` (một câu `findOpenJobsByIdIn`). Kế
hoạch ghi "một truy vấn"; thực tế là **4 câu cố định**, không N+1 — câu hỏi Q4 nếu cần gộp.

**E4 — id đang lưu:**

```sql
SELECT s.job_id FROM saved_jobs s
WHERE s.candidate_id = :candidateId AND s.deleted_at IS NULL
ORDER BY s.saved_at DESC, s.id DESC
```

**E1 — khoá, đếm, ghi:**

```sql
-- R-L3 buoc 2: khoa hang users cua chinh ung vien, giu toi het transaction.
SELECT id FROM users WHERE id = :candidateId FOR NO KEY UPDATE;

-- buoc 4: dem dong dang luu.
SELECT count(*) FROM saved_jobs WHERE candidate_id = :candidateId AND deleted_at IS NULL;

-- buoc 5a: luu lai dong da bo (@Modifying(clearAutomatically = true, flushAutomatically = true)).
UPDATE saved_jobs SET deleted_at = NULL, saved_at = now()
WHERE candidate_id = :candidateId AND job_id = :jobId AND deleted_at IS NOT NULL;

-- buoc 5b: tao moi - saveAndFlush entity SavedJob (saved_at do DEFAULT now() cua DB gan, insertable = false).
```

**E2 — bỏ lưu:**

```sql
UPDATE saved_jobs SET deleted_at = now()
WHERE candidate_id = :candidateId AND job_id = :jobId AND deleted_at IS NULL;
```

Không khoá ở E2: bỏ lưu chỉ làm giảm số dòng đang lưu, không vượt trần được. E2 chạy song song E1 cùng tin: kết quả là
một trong hai thứ tự tuần tự (UPDATE có điều kiện trên cùng một hàng tự nối tiếp ở Postgres).

### 4.5 Bảng chạm tới

| Bảng | Đọc | Ghi |
|---|---|---|
| `saved_jobs` (mới) | E1–E4 | E1, E2 |
| `users` | E1 (chỉ khoá hàng, không đọc cột nào khác) | — |
| `jobs`, `companies` | E1 (`findOpenJobById`), E3 | — |
| `job_applications` | E3 (`LEFT JOIN`, chỉ `id`, `status`) | — |

Không đọc `scoring_runs`, `criterion_scores`, `score_explanations`, `resume_parsed_data`, `job_embeddings`,
`candidate_profiles`.

## 5. AI

Không dùng AI. Không gọi LLM/embedding, không prompt, không thuộc danh sách ngoại lệ K3 (CLAUDE.md §7), không dùng K1–K4.
Không tính điểm phù hợp, không xếp hạng tin đã lưu. Danh sách đã lưu chỉ sắp theo `saved_at DESC, id DESC` (R-V1).

## 6. Ngoài phạm vi

- **Quyền riêng tư:** HR không biết ứng viên nào đã lưu tin của mình và không thấy số lượt lưu ở bất kỳ đâu (danh sách
  tin, chi tiết tin, hồ sơ đơn, dashboard FR-H08). Không endpoint `/api/hr/**` nào đọc bảng `saved_jobs`; không class
  nào ngoài `savedjob/` tham chiếu tới nó (T20, T21).
- Nhắc hạn nộp / thông báo khi tin đã lưu sắp hết hạn, bị đóng hay bị gỡ (không thêm loại thông báo FR-C03).
- Dùng tin đã lưu cho gợi ý FR-U04/U15 hay xếp kết quả FR-U13.
- Thư mục, nhãn, ghi chú, chia sẻ tin đã lưu; lưu bộ lọc tìm kiếm.
- Tự lưu tin sau khi khách đăng nhập xong (khách bấm Lưu chỉ được đưa sang `/login`).
- Ẩn tin đã ứng tuyển khỏi danh sách đã lưu (vẫn hiện, kèm "Đã ứng tuyển").
- Phân trang danh sách đã lưu (R-V2), tìm/lọc trong danh sách đã lưu.
- Cờ `saved` trong API công khai của tin (R-B4).
- Trigger DB chặn trần 100 (câu hỏi Q1).
- Sửa thứ tự không ổn định của `findSummariesByCandidateId` (`JobApplicationRepository.java:95`) — có sẵn, không thuộc
  FR này.

## 7. Xong khi

### 7.1 Test backend (Testcontainers, Postgres thật)

Lớp test tự tạo dữ liệu của mình (người dùng email duy nhất, prefix ≤ 27 ký tự; công ty, tin, CV, đơn), không đọc seed.
Các lớp cần so thời gian (`saved_at`) hoặc chạy đồng thời **không** bọc `@Transactional` cấp lớp (CLAUDE.md §3c: `now()`
theo transaction).

| # | Test | Loại |
|---|---|---|
| T1 | E1 tin `OPEN` (hạn `NULL`) → 204; E4 chứa `jobId`; DB có đúng 1 dòng, `deleted_at IS NULL` | dương |
| T2 | E1 với tin `EXPIRED` (hạn = ngày DB − 1), `PAUSED`, `CLOSED`, `DRAFT`, `OPEN` đã xoá mềm, UUID không tồn tại → mỗi ca 409 `JOB_NOT_SAVEABLE`, **cùng** body; DB 0 dòng | âm |
| T3 | Biên hạn nộp theo `CURRENT_DATE` của DB (đọc bằng `SELECT CURRENT_DATE`, **không** `LocalDate.now()`): hạn = hôm qua → 409; = hôm nay → 204; = ngày mai → 204 | biên |
| T4 | Idempotent: E1 hai lần liên tiếp → cả hai 204; 1 dòng; `saved_at` lần hai **bằng** lần một | dương |
| T5 | Bỏ lưu rồi lưu lại: E1 → E2 → E1 → vẫn 1 dòng (cùng `id`), `deleted_at IS NULL`, `saved_at` mới **lớn hơn** cũ (lớp không `@Transactional`) | dương |
| T6 | E2 idempotent: E2 khi chưa từng lưu → 204, 0 dòng; E2 hai lần sau khi lưu → cả hai 204, dòng có `deleted_at` không đổi ở lần hai; E2 với tin đã `CLOSED`/xoá mềm đang lưu → 204, dòng có `deleted_at` (R-S7) | dương + âm |
| T7 | Trần (biên): 99 dòng đang lưu + E1 tin mới → 204 (100); 100 + E1 tin mới → 409 `SAVED_JOB_LIMIT_REACHED`, vẫn 100 dòng đang lưu, không có dòng của tin đó; 100 + E1 tin đã đang lưu → 204, vẫn 100; 100 + E1 lưu lại một tin đã bỏ → 409, dòng đó vẫn `deleted_at` khác `NULL`; 100 → E2 một tin → E1 tin mới → 204 | biên + âm |
| T8 | Trần đếm cả tin không còn mở: 100 dòng trong đó 30 tin đã `CLOSED` → E1 tin mới vẫn 409 | âm |
| T9 | Quyền: không token → 401 `UNAUTHENTICATED` cho E1–E4; token HR → 403 cho E1–E4; ứng viên B: E3/E4 không có tin A đã lưu; B gọi E2 tin A đã lưu → 204 nhưng dòng của A **không đổi** | âm |
| T10 | E3 thứ tự: lưu J1, J2, J3 lần lượt (mỗi lần commit riêng) → `[J3, J2, J1]`; hai dòng có `saved_at` bằng nhau (backdate bằng native update) → thứ tự theo `id DESC` | dương + biên |
| T11 | E3 `availability`: 5 tin đang lưu rồi đổi trạng thái qua repository → `OPEN`, `EXPIRED`, `PAUSED`, `CLOSED`, `UNAVAILABLE` (xoá mềm) và `UNAVAILABLE` (về `DRAFT`); JSON chỉ chứa một trong 5 giá trị | dương |
| T12 | E3 `summary`: tin `UNAVAILABLE` → `summary = null`, `title`/`company.name` vẫn đúng; tin `CLOSED` → `summary` đủ 16 field đúng giá trị | dương + âm |
| T13 | E3 `application`: chưa có đơn → `null`; có đơn `PENDING` chu kỳ hiện tại → `{id, status: PENDING}`; đơn `WITHDRAWN` chu kỳ hiện tại → có; đơn ở chu kỳ cũ (tăng `recruitment_cycle` của tin) → `null` | dương + âm |
| T14 | JSON E3/E4 (duyệt đệ quy) không chứa khoá nào trong danh sách cấm mục 4.3 — so nguyên tên khoá | âm |
| T15 | Đồng thời, cùng tin: hai luồng E1 cùng `jobId` chưa lưu, xuất phát cùng lúc (`CyclicBarrier`, khuôn `ApplicationTalentPoolConcurrencyIntegrationTest`) → cả hai 204, đúng 1 dòng | đồng thời |
| T16 | Đồng thời, sát trần: 99 dòng đang lưu; hai luồng E1 hai tin khác nhau cùng lúc → đúng một 204 và một 409 `SAVED_JOB_LIMIT_REACHED`; DB đúng 100 dòng đang lưu. Chạy lặp 20 lần trong test để race lộ ra nếu thiếu khoá | đồng thời + biên |
| T17 | Migration V20 (khuôn `JobCatalogMigrationTest`): chèn tay hai dòng cùng `(candidate_id, job_id)` → vi phạm `uq_saved_job_per_candidate`; `candidate_id` không tồn tại → vi phạm FK; index `idx_saved_jobs_candidate_active` tồn tại với điều kiện `deleted_at IS NULL` | âm |
| T17b | `CASCADE` (Q8): tạo một tin và một ứng viên **không** có dòng con `RESTRICT` nào khác (không đơn, không công ty), ứng viên lưu tin đó và một tin thứ hai. `DELETE FROM jobs WHERE id = tin1` (native) → dòng lưu tin1 biến mất, dòng tin2 còn; `DELETE FROM users WHERE id = ứng viên` → mọi dòng `saved_jobs` của ứng viên biến mất; dòng lưu của ứng viên khác trên cùng tin không bị đụng | dương |
| T18 | FR-U08 không đổi: `ApplicationCandidateDetailControllerIntegrationTest` (đặc biệt T6 `:418-471`) xanh **không sửa dòng nào** sau R-A1 | hồi quy |
| T19 | Resolver theo lô: `JobAvailabilityResolver.of(List.of())` không gọi repository (mock); `of(jobs)` gọi `findOpenJobsByIdIn` **đúng một lần** cho 5 tin | đơn vị |
| T20 | Quyền riêng tư phía HR: ba ứng viên lưu tin J của HR H → H gọi `GET /api/hr/jobs` (`JobOwnerController.java:43`), `GET /api/hr/jobs/{J}` (`:53`), `GET /api/hr/dashboard/stats` (`DashboardController.java:23`) → 200; JSON (duyệt đệ quy mọi khoá) không có khoá nào chứa chuỗi con `saved`, `bookmark`, `favorite` (không phân biệt hoa/thường); JSON của cả ba **giống hệt** so với lúc chưa ai lưu (so bằng nhau sau khi bỏ khoá thời gian `updatedAt`) | âm |
| T21 | Canh nguồn, không Spring context (khuôn `AiOutputConvertersSourceGuardTest`): quét `src/main/java`, mọi file **ngoài** `com/recruitment/savedjob/` không chứa `com.recruitment.savedjob` (import hay tên đầy đủ) và không chứa chuỗi `saved_jobs` (native SQL), bỏ dòng comment. Dự án chia gói theo tính năng, không có gói `hr/` — luật "chỉ `savedjob/` biết `savedjob/`" phủ mọi controller `/api/hr/**` (job/, dashboard/, jobapplication/…). Đối chứng: thư mục `savedjob/` tồn tại và có ít nhất một file chứa `saved_jobs` | âm |

Không stub `ChatModel`/`EmbeddingModel` mới: FR không gọi AI. Bean mock mặc định throw của dự án vẫn bảo đảm không có
lời gọi lén.

### 7.2 Kiểm bằng HTTP — Windows PowerShell 5.1

Nạp seed test (`db/seed/README.md` mục 8). Backend ở `localhost:8080`. Dán **cả khối** vào **một** cửa sổ PowerShell.

```powershell
$base = 'http://localhost:8080'

function Get-Token([string]$Email, [string]$Password) {
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $body).accessToken
}

# In "<ma HTTP> <METHOD> <duong dan> <than loi neu co>".
function Test-Call([string]$Method, [string]$Path, [string]$Token) {
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri "$base$Path" -Headers $headers
        '{0} {1} {2} {3}' -f [int]$r.StatusCode, $Method, $Path, $r.Content
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { 'KHONG KET NOI DUOC {0} {1}' -f $Method, $Path; return }
        $text = $_.ErrorDetails.Message
        if (-not $text) {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $text = $reader.ReadToEnd()
        }
        '{0} {1} {2} {3}' -f [int]$resp.StatusCode, $Method, $Path, $text
    }
}

$tokenUv = Get-Token 'quochuyuv@gmail.com' '12345678'
$tokenHr = Get-Token 'minhdathr@gmail.com' '12345678'

$open   = '/api/candidates/saved-jobs/e2000000-0000-0000-0000-000000000001'  # J1 dang mo
$paused = '/api/candidates/saved-jobs/e2000000-0000-0000-0000-000000000003'  # J3 tam dung (seed-test.sql:157-163)
$none   = '/api/candidates/saved-jobs/00000000-0000-0000-0000-000000000000'

'--- 1. Duong: luu J1 hai lan (mong doi 204 x2), danh sach id co J1'
Test-Call PUT $open $tokenUv
Test-Call PUT $open $tokenUv
Test-Call GET '/api/candidates/saved-jobs/ids' $tokenUv

'--- 2. Am: tin tam dung, tin khong ton tai (mong doi 409 JOB_NOT_SAVEABLE x2, cung body)'
Test-Call PUT $paused $tokenUv
Test-Call PUT $none $tokenUv

'--- 3. Danh sach (mong doi 200, J1 co availability OPEN)'
Test-Call GET '/api/candidates/saved-jobs' $tokenUv

'--- 4. Bo luu hai lan + bo luu tin chua luu (mong doi 204 x3)'
Test-Call DELETE $open $tokenUv
Test-Call DELETE $open $tokenUv
Test-Call DELETE $none $tokenUv

'--- 5. Am: HR (mong doi 403 x2), khong token (mong doi 401 x2)'
Test-Call GET '/api/candidates/saved-jobs' $tokenHr
Test-Call PUT $open $tokenHr
Test-Call GET '/api/candidates/saved-jobs' $null
Test-Call PUT $open $null
```

Mong đợi: nhóm 1 hai dòng `204` rồi `200 … ["e2000000-…-000000000001"]`; nhóm 2 hai dòng `409 … JOB_NOT_SAVEABLE` cùng
câu; nhóm 3 `200` có `"availability":"OPEN"`; nhóm 4 ba dòng `204`; nhóm 5 hai `403`, hai `401 … UNAUTHENTICATED`.

**Bắt buộc ở đợt code cuối:** chạy thật khối trên, dán **nguyên văn** output vào báo cáo. Lệnh sai cú pháp hoặc kết quả
khác "Mong đợi" vì lệnh viết sai thì dừng, đề xuất sửa mục này, chờ duyệt lại.

### 7.3 Frontend

- `npm run build` + `npm run lint` sạch.
- Tìm `saved` trong `frontend/src/features/jobs/types.ts` và `frontend/src/features/jobs/api.ts` → 0 dòng (R-B4).
- Không lồng nút trong liên kết (R-B3): ở `/candidate` (đăng nhập ứng viên, trang có cả gợi ý và danh sách), chạy
  `document.querySelectorAll('a button, a [role=button]').length` trong DevTools console → `0`; và
  `document.querySelectorAll('article button[aria-pressed]').length` → bằng số thẻ việc làm đang hiện.
- Tìm `jobAvailabilityLabels` trong `frontend/src/features/candidateApplicationDetail` → 0 file (đã chuyển về
  `features/jobs/`, R-A5); tìm `JOB_AVAILABILITY_LABELS` trong `frontend/src` → chỉ ở file dùng chung và nơi import.
- Tìm `api/hr|/hr/|totalScore|rank|score|explanation` trong `frontend/src/features/savedJobs` và
  `frontend/src/pages/CandidateSavedJobsPage.tsx` → 0 dòng.
- Tìm `POST.*candidates/applications|createApplication` trong `features/savedJobs` → 0 dòng (R-V8).

### 7.4 Soát tay (desktop ≥ `lg`, `md` 768px và điện thoại 375px) — `quochuyuv@gmail.com` / `12345678`

**Thẻ việc làm sau khi đổi `<Link>` → `<article>` (R-B3) — kiểm ở đủ 4 nơi dùng `JobCard`:**

| # | Nơi dùng (route) | Chỗ gọi `JobCard` | Tài khoản |
|---|---|---|---|
| N1 | Danh sách Việc làm — `/` (khách) và `/candidate` (ứng viên) | `features/jobs/JobList.tsx:78` (qua `JobBoard`, `PublicJobListPage.tsx:7`, `CandidateJobListPage.tsx:12`) | khách; `quochuyuv@gmail.com` |
| N2 | Kết quả tìm bằng mô tả FR-U13 — `/candidate`, chế độ "Tìm bằng mô tả" | `features/jobSearch/NaturalLanguageResults.tsx:87-92` | `quochuyuv@gmail.com` |
| N3 | Khối "Gợi ý cho bạn" FR-U15 — `/candidate` (trang 1, chưa lọc) | `features/jobs/RecommendedJobs.tsx:143` | `quochuyuv@gmail.com` |
| N4 | Khối gợi ý ở Bảng tin — `/candidate/dashboard` | `features/jobs/RecommendedJobs.tsx:143` (qua `CandidateHomePage.tsx:130`) | `quochuyuv@gmail.com` |

Ở **mỗi** nơi N1–N4 (N1 kiểm cả khách lẫn ứng viên), mọi dòng dưới phải đúng:

| Thao tác | Mong đợi |
|---|---|
| Bấm chuột vào vùng trống của thẻ, vào logo, vào dòng lương | Mở `/jobs/{id}` của đúng thẻ đó |
| Tab tới tiêu đề tin, nhấn Enter | Mở `/jobs/{id}`; khi tiêu đề có focus, vòng focus bao cả thẻ |
| Tab tiếp tới nút Lưu (ngay sau tiêu đề), nhấn Enter; làm lại với Space | Chỉ đổi trạng thái lưu (ứng viên) hoặc sang `/login` (khách); **không** mở `/jobs/{id}`; URL không đổi trước khi API trả (ứng viên) |
| Bấm chuột vào nút Lưu | Như dòng trên; trang tin **không** mở, kể cả mở trong tab mới (chuột giữa/Ctrl+bấm vào nút không mở tab) |
| Ứng viên: bấm Lưu ở một nơi, sang nơi khác có cùng tin | Thẻ cùng tin ở N1–N4 đều hiện "Đã lưu" (một nguồn `GET /saved-jobs/ids`) |
| DevTools console: `document.querySelectorAll('a button, a [role=button]').length` | `0` |

**Các màn hình khác:**

| Cần thấy | Thao tác | Mong đợi |
|---|---|---|
| Đồng bộ giữa các nơi | Lưu ở `/jobs/:id` rồi quay lại `/candidate` và `/candidate/dashboard` | Thẻ J1 hiện "Đã lưu" ở cả danh sách, gợi ý, Bảng tin |
| Một query cho cả trang (B6) | `/candidate`, DevTools tab Network, tải lại trang rồi bấm Lưu/Bỏ lưu 3 thẻ khác nhau | Đúng **một** `GET /api/candidates/saved-jobs/ids` lúc tải trang; mỗi lần bấm đúng một `PUT`/`DELETE`, **không** có `GET …/ids` theo sau; nút đổi ngay khi bấm |
| Hoàn lại khi lỗi (B6) | Bấm Lưu một tin, HR đóng tin đó ở tab khác trước khi bấm (hoặc DevTools chặn request `PUT`) | Nút đổi sang "Đã lưu" rồi trở về "chưa lưu", câu lỗi hiện dưới nút |
| Khách | Đăng xuất, `/`, bấm Lưu | Sang `/login`; đăng nhập xong về trang cũ, tin **chưa** được lưu |
| Không request khi không phải ứng viên (B5) | Mở `/` và `/jobs/{J1}` khi **chưa đăng nhập**; rồi đăng nhập `minhdathr@gmail.com` (HR) và mở lại hai trang | DevTools tab Network (lọc `saved-jobs`): **0** request ở cả hai vai trò |
| HR | `minhdathr@gmail.com`, mở `/` và `/jobs/:id` | Không có nút Lưu |
| Trang Tin đã lưu | Menu "Tin đã lưu" | Mục menu sáng; "Đang lưu n/100 tin"; tin mới lưu ở trên |
| Tình trạng | Seed demo (đợt 7) có 1 tin quá hạn | Nhãn "Đã hết hạn nộp", nút Ứng tuyển khoá + câu lý do, không liên kết sang tin |
| Đã ứng tuyển | Lưu tin đã có đơn A1 | "Đã ứng tuyển" + badge + "Xem đơn" mở `/candidate/applications/{A1}` |
| Bỏ lưu tin không còn mở | Bấm "Bỏ lưu" ở tin quá hạn | Hộp xác nhận nói rõ không lưu lại được (Q5); xác nhận → tin rời danh sách, số đếm giảm |
| Rỗng | Bỏ lưu hết | Câu rỗng + liên kết "Tìm việc làm" |
| Menu ở `md` | Cửa sổ rộng 768px và 1023px, mọi trang ứng viên | `document.documentElement.scrollWidth === document.documentElement.clientWidth` (không cuộn ngang); 6 mục menu, chuông, Đăng xuất nhìn thấy trọn, không chồng lên nhau (UI.md mục 8) — sai thì dừng, đề xuất sửa UI.md |

## 8. AI hay làm sai (dành cho người code)

- Xoá cứng khi bỏ lưu (`DELETE FROM saved_jobs`, `repository.delete(...)`) thay vì đặt `deleted_at` (R-S5).
- Tạo dòng mới mỗi lần lưu lại thay vì dùng lại dòng cũ; bỏ `UNIQUE (candidate_id, job_id)` "cho dễ lưu lại" (R-S6).
- Thêm cờ `saved`/`isSaved` vào `JobSummaryResponse`, `JobDetailResponse` hay API `/api/public/**` (R-B4).
- Tính hạn nộp bằng `LocalDate.now()` hoặc viết lại `status = 'OPEN' AND deadline >= …` ở Java/SQL mới thay vì dùng
  `findOpenJobById`/`findOpenJobsByIdIn` (R-S2, R-A2); test biên dùng `LocalDate.now()` thay cho `CURRENT_DATE` của DB.
- Dùng nguyên `availabilityOf` theo từng tin trong vòng lặp danh sách (N+1, mục 0.b3); hoặc viết bản thứ hai của thứ tự
  xét thay vì một hàm dùng chung (R-A2).
- Đổi JSON của FR-U08 khi tách `JobAvailability` (đổi tên khoá, đổi chuỗi enum) hoặc sửa test FR-U08 cho xanh (T18).
- Chặn trần chỉ bằng `count()` ở service không khoá; hoặc khoá `FOR UPDATE` (xung đột khoá FK của `INSERT` khác) thay
  vì `FOR NO KEY UPDATE`; hoặc dùng `SELECT … FOR UPDATE SKIP LOCKED` (R-L3).
- Dịch vi phạm `uq_saved_job_per_candidate` thành 204 trong `GlobalExceptionHandler` để "chữa" race thay vì khoá (R-L4).
- Truyền `Instant.now()` của JVM vào `saved_at`/`deleted_at` (R-S8).
- Trả 404 cho tin nháp nhưng 409 cho tin đã đóng — lộ sự tồn tại của tin nháp (R-S3).
- Dựng form ứng tuyển thứ hai trong trang Tin đã lưu hoặc gọi thẳng `POST /api/candidates/applications` (R-V8).
- Hiện liên kết sang `/jobs/:id` cho tin không `OPEN` (dẫn tới trang "Không tìm thấy", R-V5).
- Trả lương/ngành/khu vực/hạn nộp của tin `UNAVAILABLE` (R-V6).
- Tô màu nhãn tình trạng theo tốt/xấu (xanh cho Đang mở, đỏ cho Đã đóng) hoặc tạo bộ nhãn thứ hai thay vì
  `JobAvailabilityBadge` (R-A5, R-G5).
- Lồng `<button>` vào trong `<a>`/`<Link>` của `JobCard` rồi "chữa" bằng `e.preventDefault()`/`e.stopPropagation()`
  trên nút (HTML không hợp lệ, trình đọc màn hình đọc sai, chuột giữa vẫn mở tab) — nút phải là anh em của liên kết
  (R-B3).
- Ngược lại: khi thêm bất kỳ `onClick` điều hướng nào lên `<article>` (thay cho liên kết phủ), quên
  `e.stopPropagation()` ở nút Lưu → bấm Lưu mở luôn trang tin; hoặc quên `type="button"`/`preventDefault` khi nút nằm
  trong `<form>`. Cách đúng là **không** gắn `onClick` lên `<article>`; cả thẻ bấm được nhờ lớp phủ của liên kết tiêu đề.
- Xếp tin đã lưu theo độ phù hợp/embedding, hiện "% phù hợp", hay dùng `saved_jobs` trong gợi ý (R-G2, R-G4).
- Thêm endpoint HR đếm lượt lưu (R-G3).
- Đổi FK của `saved_jobs` về `RESTRICT` "cho giống `job_applications`" (Q8 đã chốt `CASCADE`), hoặc thêm câu
  `DELETE FROM saved_jobs` thừa vào `reset-test-data.sql`/`seed-demo-structural-1b.sql`; quên liệt kê `saved_jobs` trong
  `TRUNCATE` của `reset-demo-db.sql` (mục 9.1).
- Thêm field/khoá lượt lưu vào DTO phía HR, hoặc import `savedjob/` từ package khác (T20, T21).
- Bọc lớp test so `saved_at` bằng `@Transactional` cấp lớp (mọi `now()` bằng nhau, T5/T10 không xác định).
- *(B5)* Gọi `GET /saved-jobs/ids` cho khách hoặc HR (thiếu `enabled` theo role) → 401/403 rơi vào console, dư request.
- *(B6)* Gọi API theo từng thẻ (mỗi `JobCard` một query/`GET` riêng) thay vì một query `['saved-job-ids']` dùng chung;
  refetch/`invalidateQueries(['saved-job-ids'])` sau mỗi lần bấm; cập nhật lạc quan mà quên hoàn lại khi API lỗi (thẻ
  hiện "Đã lưu" cho tin không được lưu); hoặc hoàn lại bằng cách refetch thay vì trả bản cũ đã ghi.
- *(B4)* Viết lệnh Maven có `-D` không bọc nháy kép trong PowerShell (`.\mvnw.cmd test -Dtest=A,B`) — PowerShell tách
  dấu phẩy/dấu chấm, Maven nhận sai tham số.

## 9. Kế hoạch đợt code

Mỗi đợt: dừng → báo cáo diff → chờ duyệt → một commit. Giữa các đợt backend chạy `.\mvnw.cmd test-compile` và lớp test
liên quan; full suite `.\mvnw.cmd test` chạy ở đợt cuối. Frontend: `npm run build` + `npm run lint` sau mỗi đợt có sửa
frontend. Trước khi code: rebase `feat/fr-u16-saved-jobs` lên `main` mới nhất (sau nhánh perf, kế hoạch mục "Thứ tự
làm").

*(B4)* Trong PowerShell, mọi lệnh Maven có `-D` bọc nháy kép **cả cụm**: `.\mvnw.cmd test "-Dtest=A,B,C"`,
`.\mvnw.cmd test "-Dtest=SavedJob*"`.

| Đợt | Nội dung | Kiểm |
|---|---|---|
| 1 | Plan Mode: đối chiếu đặc tả đã duyệt với code (mục 0, số dòng có thể lệch sau rebase); in SQL mục 4.4 ra chat để duyệt; chốt tên file | — |
| 2 | Tách tình trạng tin: `job/JobAvailability`, `job/JobAvailabilityResolver` (một + theo lô), `ApplicationService` dùng resolver (R-A1, R-A2); tách hàm dựng `JobSummaryResponse` thành `job/JobSummaryMapper` static (mục 4.3, L1, L2); sửa cơ học một đối số ở `ApplicationServiceTest.java:71` (L1); T18, T19 + hồi quy FR-U08, U02/C02, U15, U13 | `.\mvnw.cmd test "-Dtest=JobAvailabilityResolverTest,ApplicationCandidateDetailControllerIntegrationTest,CandidateApplicationCrossEndpointAccessIntegrationTest,ApplicationServiceTest,ApplicationIntegrationTest,JobPublicIntegrationTest,JobPublicServiceTest,JobRecommendationCandidateControllerIntegrationTest,JobRecommendationCandidateServiceTest,JobSearchIntegrationTest,JobSearchResponseKeysIntegrationTest,JobSearchSignatureTest"` |
| 3 | V20 + entity `SavedJob` (L4) + repository (L5) + **dọn dữ liệu mục 9.1** + T17, T17b | `.\mvnw.cmd test "-Dtest=SavedJobMigrationTest"`; chạy `reset-test-data.sql` trên DB có seed test |
| 4 | Service + controller E1–E4, hai exception + handler; T1–T16, T20 (L8), T21; T16 lặp 20 vòng (L9) | `.\mvnw.cmd test "-Dtest=SavedJob*"` |
| 5 | Frontend dùng chung: `JobAvailabilityBadge` + nhãn chuyển về `features/jobs/` (U08 dùng bản chung, giao diện không đổi); `features/savedJobs/` (api, queries, `SaveJobButton`); đổi cấu trúc `JobCard` (R-B3) + nút ở `/jobs/:id` | build + lint; 7.3 |
| 6 | Trang `/candidate/saved-jobs` + route + mục menu | build + lint; 7.3 |
| 7 | Đợt cuối: tầng seed demo (3–4 tin đã lưu cho ứng viên demo, 1 tin hạn `CURRENT_DATE - 1` lúc nạp — kế hoạch mục "Seed demo"); full `.\mvnw.cmd test`; khối 7.2 (dán output); soát tay 7.4; `srs-guard`; `walkthrough`; tài liệu chung mục 10; trạng thái `ĐÃ HOÀN THÀNH` | full suite; 7.2; 7.3; 7.4 |

### 9.1 Dọn dữ liệu với khoá `ON DELETE CASCADE` (đợt 3)

`saved_jobs.candidate_id` và `saved_jobs.job_id` khai `ON DELETE CASCADE` (mục 4.1, Q8): `DELETE FROM jobs`/`users` tự
xoá dòng lưu tin trỏ tới, không lỗi FK. Đợt thêm V20 xử lý các chỗ dọn dữ liệu như sau:

| Chỗ | Việc phải làm |
|---|---|
| `db/seed/reset-test-data.sql` mục 5–6 (`:132-147`) | **Không** thêm câu xoá. `DELETE FROM jobs` (`:142`) và `DELETE FROM users` (`:147`) tự xoá theo `CASCADE` mọi dòng `saved_jobs` trỏ tới tin/người dùng test, kể cả khi ứng viên demo lưu tin test hoặc ứng viên test lưu tin demo |
| `db/seed/seed-demo-structural-1b.sql` mục 1 (`:87-106`) | **Không** thêm câu xoá. `DELETE FROM jobs` (`:104`)/`users` (`:106`) dải `d1b00000-%` tự xoá dòng lưu tin trỏ tới |
| `db/seed/reset-demo-db.sql:81-122` | Thêm `saved_jobs` vào danh sách `TRUNCATE` (nhóm mới "Tin đã lưu (FR-U16, V20)") theo quy ước liệt kê tường minh (comment `:89-91`), dù `CASCADE` vẫn xoá nếu thiếu tên. Sửa tiền đề "Can DB o V19 tro len" (`:86`) thành **V20** và số bảng ở comment `:81` (30 → 31) cho khớp. FR-C09 nâng tiếp lên V21 ở nhánh của nó (FR-U16 merge trước, lúc đó DB mới ở V20) |
| Tầng seed demo mới (đợt 7) | Tự xoá dải id của chính nó trước khi chèn (idempotent), như 1b |

Kiểm ở đợt 3: trên DB có seed test, lưu tay một tin test bằng ứng viên demo và một tin demo bằng ứng viên test, rồi chạy
`reset-test-data.sql` → không lỗi; `SELECT count(*) FROM saved_jobs` trỏ tới tin hoặc người dùng test = 0; dòng lưu tin
demo của ứng viên demo còn nguyên. `reset-demo-db.sql` → `saved_jobs` rỗng.

## 10. Tài liệu dùng chung — checklist đợt cuối (chưa sửa ở đợt đặc tả)

Bảng "Tài liệu chung phải sửa" của kế hoạch **không có dòng D3**; mọi việc của FR-U16 thuộc "Đợt cuối U16", trừ
`docs/UI_GUIDE.md` mục 7 sửa sớm theo Q6. Đợt 7 làm:

- [ ] `docs/features/UV/U16/REQUIREMENT.md`, `UI.md`: `ĐÃ HOÀN THÀNH <ngày>`.
- [ ] `docs/SRS.md:60` (mục 0): FR-U16 "Đã hoàn thành"; câu mở đầu `:14-15` "37 đã hoàn thành và 2 chưa hoàn thành
  (FR-C09, FR-U16)" → "38 đã hoàn thành và 1 chưa hoàn thành (FR-C09)" (nếu FR-C09 chưa xong lúc đó).
- [ ] `docs/ROADMAP.md:1219`: tick `feat/fr-u16-saved-jobs`.
- [ ] `CLAUDE.md` §3: thêm `savedjob/` (FR-U16) vào danh sách package.
- [x] `docs/UI_GUIDE.md` mục 7: **đã sửa trong commit duyệt đặc tả** (Q6) — dòng `★/candidate/saved-jobs` và FR-U16 ở
  các dòng `/`, `/jobs/:id`, `/candidate`, `/candidate/dashboard`. Đợt cuối chỉ soát lại cho khớp code.
- [ ] `docs/ER.md`: thêm `saved_jobs` (35 → 36 bảng; FR-C09 đưa lên 37).
- [ ] `db/seed/README.md`: tiền đề "DB ở V20", tầng seed demo mới, kịch bản thử lưu tin.
- [ ] `docs/DEMO.md`: kịch bản lưu tin.
- [ ] `docs/features/README.md` mục 20: thêm khối "Phạm vi thực tế" như các FR đã xong (nếu có lệch so với mô tả định
  hướng).

## 11. Đã chốt 30/10/2026

| # | Câu hỏi | Quyết định |
|---|---|---|
| Q1 | Trần 100: giữ không, chốt ở đâu? | **Giữ 100.** Khoá hàng `users` `FOR NO KEY UPDATE` rồi đếm trong cùng transaction (R-L3); test đồng thời ở 99 tin (T16). Không trigger |
| Q2 | UUID không tồn tại ở E1 | **409 `JOB_NOT_SAVEABLE`**, như mọi tin không mở (R-S3) — không lộ tin nháp có tồn tại hay không |
| Q3 | Tin bị gỡ/đóng/tạm dừng còn hiện không, bấm vào thì sao? | **Còn hiện cho tới khi ứng viên bỏ lưu.** Mọi tình trạng khác `OPEN`: không liên kết sang trang tin (R-V5), nút Ứng tuyển khoá + câu lý do. `UNAVAILABLE` chỉ hiện tên tin, công ty (R-V6). Có đơn ở chu kỳ hiện tại thì "Xem đơn" vẫn mở được |
| Q4 | E3 "một truy vấn" hay 4 câu cố định? | **4 câu cố định, không N+1** (mục 4.4) — không viết lại điều kiện "tin mở" |
| Q5 | Bỏ lưu tin không còn mở có hỏi xác nhận? | **Chỉ hỏi khi `availability ≠ OPEN`** (UI.md mục 4e); tin đang mở bỏ lưu ngay |
| Q6 | Sửa bảng route UI_GUIDE mục 7 khi nào? | **Trong commit duyệt đặc tả này**, như tiền lệ FR-U08 mục 10 — thêm `★/candidate/saved-jobs` và FR-U16 ở 4 dòng `/`, `/jobs/:id`, `/candidate`, `/candidate/dashboard` |
| Q7 | Thông báo khi tin đã lưu sắp hết hạn/bị đóng? | **Không, ngoài phạm vi** (mục 6) |
| Q8 | FK `ON DELETE` cho `saved_jobs` | **`ON DELETE CASCADE` cho cả `candidate_id` và `job_id`.** Dấu trang không có giá trị lưu vết, mất theo bản ghi cha là đúng nghĩa (khác FR-C09 là dữ liệu quyết định nên giữ `RESTRICT`). Đã kiểm không có quy ước bắt buộc `RESTRICT` (mục 0.e). Hệ quả: mục 4.1, 9.1, T17b |

**Bổ sung khi duyệt (30/10/2026)**

| # | Nội dung | Ghi ở |
|---|---|---|
| B1 | `JobCard` đổi `<Link>` → `<article>`: soát tay đủ 4 nơi dùng thẻ (chuột, Tab + Enter ở tiêu đề, Tab + Enter/Space ở nút Lưu, bấm nút không mở trang tin) | Mục 7.4; mục 8 (lồng `<button>` trong `<a>`, quên `stopPropagation`/`preventDefault`) |
| B2 | Quyền riêng tư: HR không biết ai lưu tin, không thấy số lượt lưu; không endpoint `/api/hr/**` đọc `saved_jobs` | Mục 6; T20 (JSON API HR), T21 (canh nguồn — dự án không có gói `hr/`, luật viết thành "chỉ `savedjob/` tham chiếu `savedjob`") |
| B3 | Không AI, không xếp hạng, không điểm phù hợp; chỉ sắp `saved_at DESC, id DESC` | Đầu file, mục 1, R-G1, R-G2, R-V1, mục 5 |

**Đã chốt sau Plan Mode đợt 1 (30/10/2026)**

| # | Điểm | Quyết định |
|---|---|---|
| L1 | `ApplicationServiceTest.java:71` và `JobPublicServiceTest.java:37,53` tạo service bằng `new` | `JobAvailabilityResolver` là bean (R-A1); **sửa cơ học đúng một đối số** ở `ApplicationServiceTest.java:71` (`new JobAvailabilityResolver(jobRepository)`), không đổi khẳng định nào (tiền lệ FR-U15 R-H7). `JobSummaryMapper` là hàm static (khuôn `JobCatalogFields.of`) → constructor `JobPublicService` giữ nguyên, `JobPublicServiceTest` không sửa |
| L2 | Tên hàm dựng DTO thẻ tin | `job/JobSummaryMapper` (final class): `static JobSummaryResponse toSummary(Job, Company, CatalogRegistry)`, `static CompanyRef toCompanyRef(Company)`; thân chuyển nguyên từ `JobPublicService.toRef`/`toSummary` |
| L3 | Tầng seed demo mới | `db/seed/seed-demo-structural-3.sql`, nạp sau 2b, dải id `d3000000-%`; tự tạo **một** tin quá hạn (hạn `CURRENT_DATE - 1`) thay vì sửa tin của tầng khác; lưu 3 tin demo đang mở cho ứng viên demo. FR-C09 thêm phần của mình vào cùng file |
| L4 | Ánh xạ entity `SavedJob` | `savedAt`: `@Generated(event = INSERT)` + `insertable = false, updatable = false` (khuôn `ApplicationStatusHistory.java:50-52`); `deletedAt`: `insertable = false, updatable = false` — mọi lần ghi qua câu native mục 4.4 |
| L5 | Câu khoá `users` (R-L3) | Native trong `SavedJobRepository`, trả `Optional<UUID>`, không `@Modifying`, chỉ gọi trong `@Transactional` ghi; không có hàng → `IllegalStateException` |
| L6 | Vòng focus của thẻ (UI.md 4a) | `has-[a:focus-visible]:ring-2`, không `focus-within` (tránh khoanh thẻ khi focus ở nút Lưu) |
| L7 | Hướng phụ thuộc frontend | `features/jobs/JobCard` → `features/savedJobs/SaveJobButton`; `savedJobs` dùng badge/kiểu/định dạng của `features/jobs` nhưng **không** import `JobCard` |
| L8 | T20 so JSON | Lấy JSON trước và sau khi lưu trong cùng test, so **toàn bộ**; chỉ loại khoá nào thật sự tự đổi theo thời gian (khi đó báo lại) |
| L9 | T16 lặp 20 vòng | Giữ 20; mỗi vòng một ứng viên mới, 99 dòng chèn bằng `JdbcTemplate.batchUpdate` |
| B4 | Lệnh Maven trong PowerShell | Bọc nháy kép cả cụm `-D` (mục 9) |
| B5 | Query ids theo vai trò | Chỉ chạy khi `CANDIDATE` (R-B2); 7.4 có bước kiểm Network |
| B6 | Một query cho cả trang, cập nhật lạc quan | R-B4, R-B5, R-B6 (thay câu "không cập nhật lạc quan" của bản `f514a03`); UI.md mục 6, 10; mục 8 |