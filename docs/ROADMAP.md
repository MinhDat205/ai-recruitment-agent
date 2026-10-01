# Roadmap — tiến độ triển khai

Bảng theo dõi tiến độ. Đặc tả 18 FR đã hoàn thành ở `docs/SRS.md`; đặc tả từng FR bổ sung ở
`docs/features/<nhóm>/<mã>/`; quyết định thiết kế của mỗi nhánh ở `docs/walkthrough/`; quy trình
làm việc ở `CLAUDE.md` §6.

`docs/PHASES.md` đã gỡ ngày 29/09/2026; các chỗ nhắc tới `PHASES.md` bên dưới là ghi chép lịch sử.

Nguyên tắc: **1 mã FR = 1 nhánh = 1 phiên Claude Code.** Xong nhánh nào chạy được nhánh đó rồi
mới sang nhánh kế. Không nhảy sang phần AI khi phần CRUD nền chưa chạy — AI không có dữ liệu vào
và không có chỗ ghi kết quả ra.

---

## Phase 0 — Khởi tạo ✅ HOÀN THÀNH

- [x] Repo, cấu trúc thư mục, `.gitattributes`, `.gitignore`, `.env.example`
- [x] Docker Compose: PostgreSQL 17 + pgvector, MailHog, MinIO
- [x] Schema đầy đủ 18 bảng qua Flyway (`V1__init_schema.sql`)
- [x] Backend Spring Boot 4.1 + Spring AI 2.0 + Java 25 — khởi động được
- [x] Frontend Vite + React 19 + Tailwind + design token
- [x] CI GitHub Actions, đã push lên GitHub
- [x] Tài liệu: SRS, TECH_STACK, UI_GUIDE, DOCKER, ONBOARDING, PHASES, WORKFLOW

**Đã đạt:** `docker compose up -d` + backend + frontend chạy được, database có 18 bảng.

---

## Phase A — Nền tảng tài khoản

- [x] **A1** `feat/fr-c01-auth` — FR-C01 · Đăng ký/đăng nhập 2 role, BCrypt, JWT, RBAC
- [x] **A2** `feat/fr-c02-public-browse` — FR-C02 · Trang công khai: danh sách job, chi tiết, hồ sơ doanh nghiệp
- [x] `chore/shadcn-setup` — cài shadcn/ui trước khi vào Phase B

**Xong khi:** ứng viên gọi API của HR → **403** (kiểm bằng curl, không qua UI).

## Phase B — HR dựng chiến dịch

- [X] **B1** `feat/fr-h01-company` — FR-H01 · Hồ sơ doanh nghiệp
- [X] **B2** `feat/fr-h02-jobs` — FR-H02 · CRUD tin tuyển dụng + mẫu giấy mời phỏng vấn
- [X] **B3** `feat/fr-h03-rubric` — FR-H03 · Tiêu chí + trọng số, thang điểm mặc định/tuỳ chọn
- [x] `fix/rubric-guard` — siết guard rubric đủ 100% cho mọi đường vào OPEN + map trùng tên tiêu chí sang 409 (sửa nợ kỹ thuật B2/B3, không gắn mã FR)

**Xong khi:** không lưu được rubric có tổng trọng số ≠ 100%, chặn ở cả UI lẫn API.

## Phase C — Ứng viên nộp đơn

- [x] **C1** `feat/fr-u01-resume` — FR-U01 · Hồ sơ cá nhân, upload nhiều phiên bản CV
- [x] **C2** `feat/fr-u02-apply` — FR-U02 · Ứng tuyển + consent bắt buộc + chống nộp trùng
- [x] `fix/public-header-auth` — sửa header công khai phản ánh trạng thái đăng nhập (không gắn mã FR)
- [x] **C3** `feat/fr-u03-tracking` — FR-U03 · Theo dõi 5 trạng thái + lịch sử
- [x] **C4** `feat/fr-u06-withdraw` — FR-U06 · Rút đơn (soft state)

**Xong khi:** nộp trùng job trong cùng chu kỳ bị chặn ở **tầng DB**, không chỉ ở UI.

## Phase D — AI đọc và chấm

- [X] **D1** `feat/fr-c04-parsing` — FR-C04 · Trích xuất CV → JSON, chạy nền, validate schema
- [X] **D2** `feat/fr-h04-scoring` — FR-H04 · Chấm **từng tiêu chí riêng** + evidence
  - Bắt buộc: khi tạo lượt chấm đầu tiên (`scoring_runs`) phải set `rubrics.is_locked = true`.
    Hiện chưa có đường nào trong ứng dụng đặt cờ này; guard mở lại tin ở
    `JobOwnerService.changeStatus` (nhánh `fix/rubric-guard`) dựa vào cờ đó để bỏ qua kiểm đủ
    100% — nếu D2 không cài, HR có job đã chấm sẽ kẹt không mở lại được chu kỳ tuyển dụng mới.
- [X] **D3** `feat/fr-h05-aggregate` — FR-H05 · Tổng hợp có trọng số + xếp hạng (Java thuần)
- [X] **D4** `feat/fr-h06-explain` — FR-H06 · Báo cáo giải thích, mọi luận điểm có evidence
  - Đã thêm nút "Xem CV gốc" cho HR (`/api/hr/applications/{id}/resume/download`) — không có mã FR
    nào giao việc này rõ ràng (đã đọc lại SRS/PHASES xác nhận khoảng trống), xếp vào D4 thay vì E1
    vì lý do: không có CV gốc thì không đối chiếu được evidence trong báo cáo AI với văn bản thật,
    đúng nguyên tắc Explainable AI của FR-H06. Chi tiết lập luận ở walkthrough `fr-h06-explain.md`
    mục 4h.

**Xong khi:** unit test `ScoreAggregator` pass; đổi trọng số → thứ hạng đổi đúng công thức; mở bất
kỳ tiêu chí nào cũng thấy evidence trích từ CV; không tồn tại cột/field nào tên `verdict`, `label`,
`isQualified`, `passed`.

## Phase E — Quyết định & thông báo

- [X] **E1** `feat/fr-h07-pipeline` — FR-H07 · Pipeline, mời phỏng vấn, xác nhận kết quả
  - Máy trạng thái (`PATCH /api/hr/applications/{id}/status`) chặn cứng đường tắt đặt thẳng
    `INTERVIEW_INVITED` — trạng thái này bắt buộc phải kèm lịch hẹn thật (đúng nghĩa "Đã mời phỏng
    vấn (có lịch hẹn)" trong SRS), chỉ đi được qua `POST .../interview-invitation`. Chi tiết lập
    luận ở walkthrough `fr-h07-pipeline.md` mục 4b.
  - Giấy mời phỏng vấn lưu nguyên văn nội dung HR đã gửi (`interview_invitations.rendered_content`),
    không render lại và không FK ngược về `interview_templates` — HR sửa mẫu sau này không làm đổi
    nội dung đã gửi, cùng tinh thần `rubric_snapshot` (D2). Chi tiết walkthrough mục 4c/4e.
- [x] **E2** `feat/fr-c03-notification` — FR-C03 · Thông báo web + email
  - Spring Events (`@TransactionalEventListener(AFTER_COMMIT)` cho 3 sự kiện publish trong transaction
    nghiệp vụ, `@EventListener` thường cho sự kiện chấm điểm xong publish ngoài transaction ở
    `AggregationOrchestrator`) + poller gửi email riêng, tách hoàn toàn khỏi transaction ghi chính.
    Chi tiết lập luận + lỗi thật gặp lúc chạy test (Spring chặn `@Transactional` mặc định trên
    method AFTER_COMMIT) ở walkthrough `fr-c03-notification.md` mục 4a/4b.

**Xong khi:** không tồn tại bất kỳ đường code nào tự động chuyển trạng thái đậu/rớt.

## Phase F — Gợi ý & thống kê

- [x] **F1** `feat/fr-u04-recommend` — FR-U04 · Embedding + cosine similarity, gợi ý việc làm
  - `MIN_SIMILARITY_SCORE = 0.40` chốt từ đo thực nghiệm thật (2 CV × 7 job, `OpenAI
    text-embedding-3-small` thật) — không đoán số. Truy vấn similarity hai bước, vector truyền như
    tham số cố định (không `JOIN ... ON true`) — đã xác nhận bằng `EXPLAIN ANALYZE` thật rằng
    planner CÓ THỂ chọn index HNSW (474ms khi ép, so với 0.41ms Seq Scan mà planner tự chọn ở quy
    mô 7 hàng hiện tại). Cố ý giữ nguyên không thêm gate consent (FR-U02) cho luồng sinh embedding
    CV: consent trong SRS nhắm tới việc bên thứ ba (HR) dùng AI ra quyết định về ứng viên, còn F1
    chỉ phục vụ chính ứng viên và không sinh nội dung mới — thêm gate sẽ khiến ứng viên chưa từng
    ứng tuyển (đúng nhóm cần gợi ý nhất) không bao giờ thấy gợi ý. Chi tiết đầy đủ ở walkthrough
    `fr-u04-recommend.md` mục 4.
- [x] **F2** `feat/fr-u05-cv-improve` — FR-U05 · Gợi ý cải thiện CV
  - Cố ý bỏ vế "kết quả đánh giá trước đó" của SRS FR-U05 — PHASES.md cấm lộ điểm/rubric/nhận xét
    nội bộ của HR cho ứng viên, hai văn bản mâu thuẫn nhau, ưu tiên PHASES.md. Ba lớp phòng thủ độc
    lập chống rò rỉ dữ liệu chấm điểm (chữ ký service, prompt gửi LLM, response trả ứng viên) — mỗi
    lớp có test riêng. Chi tiết lập luận đầy đủ ở walkthrough `fr-u05-cv-improve.md` mục 4a/4b.
- [x] **F3** `feat/fr-h08-dashboard` — FR-H08 · Dashboard, lọc theo điểm, tra cứu lịch sử đánh giá
  - Phễu chuyển đổi đếm theo `application_status_history` (đã từng đạt trạng thái), không đếm theo
    `job_applications.status` hiện tại — đơn được mời phỏng vấn rồi rút đơn vẫn tính vào "đã từng
    được mời". Nhánh lọc theo điểm tiêu chí dẫn dắt câu SQL từ `criterion_scores` để ép Postgres
    dùng đúng `idx_criterion_scores_filter` (dẫn dắt từ `scoring_runs` sẽ vô tình chọn index khác).
    Không có cột "Hạng" ở danh sách ứng viên toàn công ty — FR-H05 chỉ định nghĩa xếp hạng trong
    phạm vi một chiến dịch. Chi tiết đầy đủ + các quyết định gây tranh luận ở walkthrough
    `fr-h08-dashboard.md` mục 4.
- [x] `fix/candidate-empty-states` — phân biệt bốn trạng thái rỗng của khối gợi ý việc
  làm (chưa có CV / đang phân tích / phân tích thất bại / đã phân tích nhưng không job
  nào đạt `MIN_SIMILARITY_SCORE = 0.40`). Trước đó gộp tất cả thành một thông báo "Hãy
  tải CV lên", khiến ca demo ngưỡng lọc của FR-U04 (Bùi Ngọc Mai nhận danh sách rỗng
  đúng thiết kế) trông như hệ thống chưa chạy. Không gắn mã FR.
  - Nợ kỹ thuật: frontend suy ra "CV đã sẵn sàng cho gợi ý việc làm" từ
    `parseStatus = DONE`, trong khi điều kiện thật của F1 là
    `resume_parsed_data.embedding IS NOT NULL`. Hai điều kiện lệch nhau trong khoảng
    giữa lúc D1 parse xong và nhịp poll kế tiếp của `ResumeEmbeddingScheduler`. Trong
    cửa sổ đó, ứng viên có CV `DONE` nhưng chưa có embedding sẽ thấy thông báo "chưa
    có vị trí nào đủ phù hợp" thay vì "đang phân tích" — sai thông điệp, không sai
    chức năng. Cách sửa dứt điểm: thêm field `hasEmbedding` vào `ResumeResponse`; hoãn
    vì phải đụng backend và chạy lại toàn bộ test suite, ngoài phạm vi một nhánh sửa
    hiển thị.
- [x] `feat/candidate-dashboard-ui` — thiết kế lại trang bảng tin ứng viên: thanh nav
  ngang dùng chung cho toàn khu vực ứng viên (`CandidateLayout`, áp cho cả 4 route
  `/candidate`, `/candidate/profile`, `/candidate/applications`,
  `/candidate/notifications` — trước đó 3 trang sau dùng `PublicLayout`, mỗi trang một
  kiểu điều hướng khác nhau), ba thẻ tóm tắt (CV chính, đơn ứng tuyển, gợi ý phù hợp),
  và lối tắt sang F2 (gợi ý cải thiện CV) — trước đó chưa có lối vào F2 từ bảng tin
  (bảng CV trong trang Hồ sơ đã có sẵn nút Gợi ý cải thiện CV). Không gắn mã FR, không
  tạo endpoint backend mới, không sửa `backend/src`.
  - Nợ kỹ thuật: khối gợi ý việc làm không hiển thị độ tương đồng vì `GET
    /candidates/job-recommendations` trả `JobSummaryResponse` — cùng DTO với danh sách
    job công khai, không có field điểm. `JobRecommendation.similarityScore` chỉ tồn
    tại trong entity nội bộ, chưa từng serialize ra ngoài. Hệ quả: ứng viên không phân
    biệt được vị trí phù hợp cao với phù hợp vừa, và phần tính toán của F1/FR-U04
    không hiển thị được ra giao diện. Cách sửa: thêm DTO riêng cho gợi ý việc làm có
    kèm `similarityScore`; hoãn vì phải đụng backend và chạy lại test suite, ngoài
    phạm vi một nhánh sửa giao diện. Nếu làm sau này: badge PHẢI dùng một màu trung
    tính duy nhất, không tô màu theo ngưỡng, không gán nhãn phân loại.
  - Nợ kỹ thuật: khối gợi ý việc làm không có trang "xem đầy đủ" để dẫn tới — `GET
    /candidates/job-recommendations` đã trả về toàn bộ gợi ý đã cache (không phân
    trang), giới hạn duy nhất nằm ở lúc *ghi* cache
    (`JobRecommendationCacheService.TOP_N = 10`), nên danh sách hiển thị trên bảng tin
    đã là đầy đủ. Link cạnh tiêu đề khối vì vậy trỏ về danh sách việc làm công khai
    (`/`) với nhãn nói rõ đích đến ("Tìm thêm việc làm khác") thay vì nhãn "Xem tất cả"
    (sẽ gây hiểu lầm đây là cùng một danh sách gợi ý đầy đủ hơn, trong khi thực chất là
    một danh sách khác — mọi job đang mở, không lọc theo ngưỡng tương đồng).
  - Phát hiện khi kiểm thử giao diện với dữ liệu demo đầy đủ (29/08/2026) —
    **`MIN_SIMILARITY_SCORE = 0.40` (F1/FR-U04) không lọc được như thiết kế.** Đo trên
    8 CV × 6 job thật (`OpenAI text-embedding-3-small`): toàn bộ 28 cặp có gợi ý nằm
    trong dải hẹp 0.402–0.720, và những cặp hoàn toàn không liên quan vẫn vượt ngưỡng
    — CV Nhân sự với job QA Engineer 0.432, CV DevOps với job Marketing 0.423. Ngưỡng
    0.40 ban đầu chốt từ cỡ mẫu 2 CV × 7 job, quá nhỏ để thấy được sàn tương đồng.

    Nguyên nhân: `text-embedding-3-small` trên văn bản tiếng Việt cùng thể loại (CV
    đối chiếu mô tả công việc) luôn cho nền tương đồng quanh 0.40 vì chung ngôn ngữ
    và chung cấu trúc văn bản, không phải vì chung nội dung chuyên môn. Sàn thực tế
    là ~0.40 chứ không phải 0, nên một ngưỡng tuyệt đối đặt tại đó gần như không loại
    được gì.

    **Điều VẪN đúng và là kết quả chính của F1:** xếp hạng chính xác 7/7 ứng viên —
    mỗi người đều có job đúng ngành đứng đầu danh sách (Java 0.600, DevOps 0.636,
    QA 0.720, Kế toán 0.555, Marketing 0.614, Sales 0.605). Thứ tự tương đồng phản
    ánh đúng chuyên môn; chỉ có phép cắt theo ngưỡng tuyệt đối là không hiệu quả.

    Chưa sửa. Không nâng ngưỡng lên 0.50 — đó là số chọn cho vừa ý ca demo, không phải
    đo đạc, và sẽ làm bảng tin của hầu hết ứng viên gần như trống (Kế toán/Marketing/
    Sales mỗi người chỉ còn 1 gợi ý). Hướng đúng: lọc theo khoảng cách tương đối so với
    điểm cao nhất của chính ứng viên đó (ví dụ giữ các job trong biên độ 0.10 dưới đỉnh)
    thay vì một hằng số tuyệt đối dùng chung cho mọi CV. Hoãn vì phải đụng backend,
    chạy lại test suite và sinh lại toàn bộ cache `job_recommendations`.
  - **Cập nhật (30/08/2026, nhánh `feat/candidates-scoring-action`): route `/candidate` đổi ý
    nghĩa** — không còn render Bảng tin, mà render `CandidateJobListPage.tsx` (nội dung Việc làm,
    mirror `PublicJobListPage.tsx` nhưng bọc `CandidateLayout`) — đây cũng là trang candidate thấy
    đầu tiên sau đăng nhập (`LoginForm.tsx`/`ProtectedRoute.tsx` redirect role CANDIDATE tới
    `/candidate`). Bảng tin dời sang `/candidate/dashboard`, nội dung giữ nguyên như mô tả ở trên,
    vẫn là một tab trong `CandidateLayout` (`NAV_ITEMS`), giờ đứng thứ 2 sau "Việc làm".

**Xong khi:** đơn đã rút vẫn được đếm đúng trong thống kê.

---

## Hoàn thiện trước bảo vệ

- [x] `chore/hardening` — **HOÀN THÀNH** (7 đợt, xem `docs/walkthrough/chore-hardening.md` để hiểu
  luồng và quyết định thiết kế đầy đủ). Tóm tắt việc đã làm:
  - Đợt 2: sửa lost-update thật ở `ApplicationStatusService.changeStatus` (UPDATE có điều kiện thay
    `findById`+`save` không điều kiện); thêm khoá cuối `id` cho 3 query `ORDER BY` thiếu tie-break
    (`ScoringRunRepository`); đảo thứ tự kiểm quyền sở hữu công ty trước khi tra tài nguyên ở
    `ApplicationStatusService`/`ApplicationOwnerService`.
  - Đợt 4: retry-with-backoff tự động cho lỗi LLM tạm thời (mạng/timeout/429/5xx, phân biệt với lỗi
    vĩnh viễn 401/400/403/404/422) cho D1 (`resumes`) và D2 (`scoring_runs`), dùng chung
    `LlmRetryPolicy`; stale-claim reaper phục hồi job nền kẹt do JVM restart giữa chừng; xử lý đúng
    race giữa worker "zombie" và worker vừa claim lại (`uq_score_per_criterion`); migration
    `V7__llm_retry_backoff.sql`.
  - Đợt 5: rate limit in-memory bằng Bucket4j — theo IP cho endpoint xác thực, theo `userId` cho 3
    endpoint tốn LLM; kho bucket bounded chống OOM.
  - Đợt 6: Việt hoá có dấu 14 chuỗi lỗi hiển thị cho người dùng; lọc CV `FAILED` khỏi form ứng
    tuyển; sửa tận gốc cảnh báo Radix Select uncontrolled→controlled; căn phải cột số.
  - Đợt 7: hoàn thiện mục 3b còn sót của kế hoạch gốc — endpoint `PATCH
    /api/candidates/resumes/{id}/retry` cho candidate tự thử lại thủ công một CV `FAILED` hẳn, nút
    "Phân tích lại" trong `ResumeList.tsx`.
  - **V6 (`cv_improvement_requests`, F2/FR-U05) chưa từng được áp cho DB dev cho tới khi chạy V7**
    (chore/hardening) — `flyway_schema_history` cho thấy V5 áp ngày 2026-08-18, còn V6 và V7 cùng áp
    một lượt vào 2026-08-24 (log Flyway: "Migrating schema... to version 6" rồi "7" liên tiếp trong
    cùng lần khởi động). Tức là F2 đã đánh dấu xong trong ROADMAP nhưng **chưa từng chạy thật trên
    DB dev này** suốt khoảng thời gian đó — không phải lỗi của `chore/hardening`, nhưng là phát hiện
    phụ khi kiểm Flyway cho V7. **Cần kiểm thử tay lại toàn bộ luồng FR-U05 trước bảo vệ**, và kiểm
    cùng cách (`SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3`)
    trên mọi máy khác đang chạy dự án — DB dev khác có thể cũng đang thiếu V6 mà chưa ai để ý.
  - **`java.util.UUID.compareTo()` KHÔNG cùng ngữ nghĩa với `ORDER BY id` của Postgres trên cột
    `uuid`** — Java so sánh hai `long` có dấu (`mostSigBits`/`leastSigBits`), Postgres so sánh 16
    byte không dấu. Hai thứ tự này cho kết quả khác nhau tuỳ giá trị UUID cụ thể. **Không được** dùng
    `UUID.compareTo()` để dự đoán hay tái tạo thứ tự một truy vấn `ORDER BY id` — muốn biết thứ tự
    thật, đọc lại từ chính repository/truy vấn đó. Phát hiện khi Đợt 2 xoá workaround sort-lại-bằng-Java
    trong `ScoringRunAuditService.listAudit` (dùng đúng `UUID.compareTo()` để mô phỏng `id DESC`)
    và test `ScoringRunAuditControllerIntegrationTest.listAudit_tiedCreatedAt_...` vỡ vì test đó
    cũng đoán thứ tự bằng `UUID.compareTo()` — tức workaround cũ đang **che giấu** đúng sai lệch này
    (test pass "may" trước đó, không phải pass đúng). Đã sửa test lấy thứ tự mong đợi từ repository
    thật, và thêm test tương tự cho `ScoringRunService.listScoringRuns` (khoảng trống chưa có test
    tie-break trước Đợt 2). Tiền lệ xử lý đúng đã có sẵn từ trước ở
    `CvImprovementRequestRepositoryTest.findByStatusOrderByRequestedAtAscIdAsc_returnsOldestFirst`
    (F2) — không phải phát hiện đầu tiên, nhưng chưa được áp dụng nhất quán ở D2/D3.
  - D4: `app.explanation.max-attempts=3` không có nút "thử lại ngay" riêng cho việc sinh báo cáo
    giải thích khi đã `FAILED` — HR phải tạo một lượt chấm điểm mới cho đơn đó để có cơ hội thử lại.
  - Cố ý không xây tầng tổng hợp/cảnh báo chi phí token — ngoài phạm vi đồ án, không phải bỏ sót.
    Cột `scoring_runs.token_usage`/`resume_parsed_data.token_usage` vẫn được ghi đầy đủ như hiện
    tại, chỉ không có gì đọc/tổng hợp từ đó.
  - ~~`ResumeParsingErrorCode.LLM_TIMEOUT` hiện không có đường code nào tạo ra được~~ — **đã xử lý
    ở Đợt 4 (`chore/hardening`)**: xoá hẳn, thay bằng `LLM_TEMPORARILY_UNAVAILABLE` (bao quát cả
    mạng/timeout lẫn 429/5xx, không chỉ riêng timeout) và `LLM_RETRY_EXHAUSTED` (mã cuối khi hết số
    lần thử tự động). Xác nhận bằng `javap` trên `anthropic-java-core-2.40.1.jar`/
    `openai-java-core-4.39.1.jar` thật: `AnthropicIoException`/`AnthropicRetryableException` (mạng/
    timeout thuần, SDK tự retry nội bộ rồi vẫn hết hạn) và `RateLimitException`(429)/
    `InternalServerException`(5xx) đều map về mã tạm thời; áp dụng nhất quán cho D1, D2, D4, F1, F2.
  - **Rate limit (Đợt 5, `chore/hardening`) tắt trong test profile** (`app.rate-limit.enabled=false`)
    — `RateLimitBucketStore` dùng CHUNG một map trong-nhớ cho toàn bộ Spring context, mà ~20 file
    test hiện có gọi THẬT `POST /api/auth/login` qua MockMvc trên context cache dùng chung (hàng
    trăm lần trong cả bộ test) — bật lên sẽ làm vỡ hàng loạt test không liên quan gì đến rate limit
    chỉ vì dùng chung một bucket cho `"auth:127.0.0.1"`. **Hệ quả**: 13 test hiện có
    (`RateLimitBucketStoreTest`/`RateLimitFilterTest`) phủ đúng logic tiêu thụ token/refill/trần
    `max-tracked-keys` ở mức đơn vị (tự "new" instance bằng tay, không qua Spring), nhưng KHÔNG có
    test tự động nào chứng minh `RateLimitFilter` nằm ĐÚNG VỊ TRÍ trong chain thật của Spring
    Security và thực sự chặn được request đi qua HTTP thật — bằng chứng duy nhất cho điều đó là một
    lượt kiểm thử tay (20 request `POST /api/auth/login` sai mật khẩu liên tiếp, request thứ 11 trả
    `429` kèm `Retry-After: 6`, xem báo cáo Đợt 5). Cách phủ được bằng test tự động: dựng một
    `@SpringBootTest` riêng với context riêng (`@TestPropertySource(properties = "app.rate-limit.enabled=true")`)
    bật rate limit thật để test qua MockMvc — chưa làm vì tốn thêm một context cache riêng trong CI
    cho một tính năng không gắn mã FR nào, cân nhắc lại nếu sau này có sự cố thật liên quan tới rate
    limit.
  - **`Spring Boot ErrorPageFilter` nuốt mất body của response 4xx/5xx CHƯA COMMIT** — phát hiện khi
    kiểm thử tay `RateLimitFilter` (Đợt 5): filter tự ghi `response.getWriter().write(...)` rồi
    `return` mà KHÔNG flush, `ErrorPageFilter` (đặt ngoài chain của Spring Security, bọc toàn bộ)
    thấy status 429 nhưng response chưa commit, tự điều hướng sang xử lý lỗi mặc định và ghi đè mất
    body — client nhận đúng status code nhưng body rỗng, dù log server cho thấy đã ghi. Sửa bằng
    gọi `response.flushBuffer()` NGAY sau khi ghi body, ép response commit trước khi trả về khỏi
    filter. `JsonAuthenticationEntryPoint`/`JsonAccessDeniedHandler` (401/403) không gặp lỗi này vì
    thân của chúng đi qua đường Spring MVC bình thường (`DispatcherServlet` ghi qua
    `HttpMessageConverter`, tự commit trước khi filter chain trả về) — **bẫy này áp dụng cho MỌI
    filter tự ghi thẳng vào `HttpServletResponse` sau này**, không riêng gì rate limit, ghi lại ở
    đây để không lặp lại.
  - ~~Không có đường thử lại cho `resumes.parse_status = FAILED` do lỗi môi trường tạm thời~~ —
    **đã xử lý đầy đủ**: lỗi mạng/timeout/429/5xx tự động thử lại tối đa `max-attempts` lần từ Đợt
    4, không còn đốt ngay thành `FAILED`; **và Đợt 7 hoàn thiện nốt mục 3b còn sót của kế hoạch gốc**
    — endpoint `PATCH /api/candidates/resumes/{id}/retry` (`ResumeCandidateController`) cho trường
    hợp hết hẳn số lần thử tự động hoặc lỗi môi trường không phải lỗi SDK (ví dụ thiếu
    `ANTHROPIC_API_KEY` lúc chạy). Kiểm sở hữu (404) → kiểm `parseStatus == FAILED` (409,
    `RESUME_RETRY_NOT_ALLOWED`) → `UPDATE ... WHERE parse_status = 'FAILED'` reset
    `attempt_count = 0`/`next_attempt_at = NULL`/`claimed_at = NULL`/`parse_error = NULL` (coi thao
    tác thủ công là bắt đầu lại từ đầu, không tính vào số lần thử tự động đã dùng hết). Nút "Phân
    tích lại" trong `ResumeList.tsx`, chỉ hiện khi `FAILED`. Kiểm thử tay bằng tài khoản QA + CV
    thật + LLM thật: CV `FAILED` (mô phỏng hết lượt thử) → bấm nút → về `PENDING` ngay trên UI →
    poller thật nhặt lại trong vòng poll kế tiếp → `DONE` với `resume_parsed_data` ghi đúng, không
    vi phạm `resume_parsed_data_resume_id_key` (đã kiểm invariant: `DONE` là trạng thái cuối, không
    có đường code nào đưa một resume đã `DONE` quay lại `FAILED`, nên `retry` không bao giờ chạm một
    resume đã có sẵn `resume_parsed_data` — xác nhận qua chính việc dựng test thủ công: ép trạng
    thái `FAILED` bằng SQL tay trên một resume ĐÃ có `resume_parsed_data` từ trước tạo ra đúng vi
    phạm unique đó, nhưng đây là trạng thái không thể đạt được qua bất kỳ đường code thật nào, chỉ
    dựng được bằng SQL tay bỏ qua mọi ràng buộc ứng dụng).
  - ~~Không có stale-claim reaper~~ — **đã xử lý ở Đợt 4**: `ResumeParsingScheduler.reapStaleClaims`/
    `ScoringRunScheduler.reapStaleClaims` tự phục hồi bản ghi kẹt do JVM restart giữa chừng (D1
    `PROCESSING`, D2 `RUNNING`/`finished_at NULL`) sau `app.hardening.stale-timeout-ms`. **Giới hạn
    còn lại chưa xử lý**: điều kiện `status = 'RUNNING'` của `markFailed`/`markRetryExhausted` không
    phân biệt được hai worker cùng nhìn thấy `RUNNING` từ CÙNG một lần claim (worker A "zombie" sống
    sót qua hết `stale-timeout-ms` trong khi lượt đã bị reap và claim lại thành công bởi worker B —
    cả hai đều thấy `status = 'RUNNING'` và đều có thể ghi). Chặn triệt để đòi thêm một cột version
    tăng theo từng lần claim (so khớp thêm `started_at = :expectedStartedAt`, tương tự cách
    `attempt_count` đang dùng cho `markTemporaryFailure`) và sửa chữ ký `markFailed` cùng mọi call
    site D2/D3 — vượt phạm vi nhánh `chore/hardening`, để lại cho nhánh sau. D3 (tổng hợp điểm)
    **không** có khoản nợ reaper tương tự — cố ý không claim (xem walkthrough `fr-h05-aggregate`
    mục 4b), nên một lượt tổng hợp dở dang khi JVM crash vẫn nằm trong phạm vi quét của
    `AggregationScheduler`, tự được thử lại ở nhịp poll kế tiếp.
  - Tổng điểm hiển thị ở frontend làm tròn 2 chữ số thập phân (`toFixed(2)`) trong khi cột
    `scoring_runs.total_score` lưu scale 3 (`NUMERIC(6,3)`) — chưa có yêu cầu rõ ràng về độ chính
    xác hiển thị, chọn 2 chữ số cho gọn mắt (D3, `ApplicationsTab.tsx`).
  - `ChatModel.getDefaultOptions()` đã deprecated ở Spring AI 2.0, đang dùng trong mock test của
    cả D1 và D2 — cần thay khi nâng phiên bản.
  - ~~`ResumeParsingErrorCode` (D1) chưa implement `common/FormattedErrorCode`~~ — **đã xử lý ở Đợt
    4 (`chore/hardening`)**: thêm `implements FormattedErrorCode`, `formatted()` đã khớp sẵn chữ ký,
    không đổi hành vi.
  - Phát hiện khi kiểm thử Phase D bằng key thật (19/08/2026):
    - ~~Form ứng tuyển (C2, `frontend/src/features/applications/JobApplyForm.tsx`) cho chọn cả CV có
      `parse_status = FAILED`~~ — **đã xử lý ở Đợt 6 (`chore/hardening`)**: lọc bỏ CV `FAILED` khỏi
      danh sách chọn, thêm chú thích số lượng CV bị ẩn.
  - ~~E1: `ApplicationStatusService.changeStatus` đọc `application.getStatus()` rồi `save()` mà
    không có `WHERE status = :oldStatus` hay `@Version`~~ — **đã xử lý ở Đợt 2 (`chore/hardening`)**:
    đổi sang `JobApplicationRepository.updateStatusIfCurrent` (`UPDATE ... WHERE id = :id AND status
    = :oldStatus`, kiểm rowcount), rowcount 0 → `ApplicationStatusConflictException` (409), đúng
    khuôn mẫu `ScoringRunRepository.finishAggregation` (D3) đã đề xuất từ trước.
  - Phát hiện khi kiểm thử tay nhánh E1 bằng tài khoản thật (20/08/2026):
    - 6 job seed `10000000-0000-0000-0000-00000000000{1..6}` (`db/seed/dev-seed.sql`) không có rubric
      lẫn interview_template; job `11111111-1111-1111-1111-111111111111` có rubric nhưng thiếu
      template. Đây là dữ liệu tạo ngoài `JobOwnerService.create` nên thiếu các bất biến mà B2 đảm
      bảo (Job+Rubric+InterviewTemplate luôn tạo cùng nhau). Xử lý bằng xoá mềm khi làm
      `chore/seed-demo`, không vá bằng INSERT tay.
    - ~~Console cảnh báo `Select is changing from uncontrolled to controlled` (radix-ui)~~ — **đã
      xử lý ở Đợt 6 (`chore/hardening`)**: nguyên nhân thật là pattern `field.value || undefined`
      (biến giá trị rỗng hợp lệ `''` thành `undefined`), không phải thiếu `defaultValues` — sửa cả
      hai ở `HrJobCreatePage.tsx`/`HrJobEditPage.tsx`.
    - ~~Message lỗi 401 `UNAUTHENTICATED` viết tiếng Việt không dấu~~ — **đã xử lý ở Đợt 6
      (`chore/hardening`)**: sửa 14 chỗ (4 điểm đã biết + 10 điểm phát hiện thêm qua rà soát —
      `AuthService` và các `*NotFoundException`/`EmailAlreadyExistsException`).
    - Sidebar HR: mục "Ứng viên" và "Rubric" không có `to` trong `NAV_ITEMS`
      (`frontend/src/components/layout/HrLayout.tsx:15-16`) nên hiển thị mờ, không bấm được. Cố ý ở
      giai đoạn này (hai màn hình đó vào qua job, chưa có trang danh sách toàn cục). Quyết định ở F3
      (FR-H08, dashboard): trỏ về màn hình đó hoặc gỡ hẳn khỏi sidebar.
      **Đã xử lý ở F3**: "Ứng viên" trỏ `/hr/candidates` (trang mới); "Rubric" xóa hẳn khỏi
      `NAV_ITEMS` (rubric thuộc từng job, đã có tab riêng trong `HrJobEditPage`, đặt ở menu cấp cao
      là điều hướng cụt).
  - E2: poller gửi email không claim trước khi gửi — an toàn với một instance, sẽ gửi trùng nếu chạy
    đa instance. Xem walkthrough `fr-c03-notification.md` mục 4d/7.
  - Phát hiện khi làm F3 (FR-H08, `feat/fr-h08-dashboard`):
    - ~~`ScoringRunRepository.findByApplicationIdOrderByCreatedAtDesc` (D2) — derived query `ORDER
      BY created_at DESC` thiếu khóa cuối duy nhất~~ và ~~`findLatestDoneByApplicationIdIn` (D3) —
      `DISTINCT ON (application_id) ORDER BY application_id, created_at DESC` cùng lỗi~~ — **đã xử
      lý ở Đợt 2 (`chore/hardening`)**: thêm `, id DESC`/`, id` tie-break cho cả ba query (gồm cả
      `findLatestByApplicationIdIn`); phát hiện phụ liên quan: `UUID.compareTo()` của Java KHÔNG
      cùng ngữ nghĩa với `ORDER BY id` của Postgres — xem `docs/walkthrough/chore-hardening.md`
      mục 4.
    - ~~Pattern `requireOwnCompany` chạy **sau** khi tra tài nguyên (thay vì trước) ở
      `ApplicationStatusService.loadOwnedApplication` (E1) và `ApplicationOwnerService.loadOwnedJob`
      (D3)~~ — **đã xử lý ở Đợt 2 (`chore/hardening`)**: đảo thứ tự, kiểm quyền sở hữu công ty
      trước khi tra tài nguyên, đúng khuôn `ScoringRunAuditService` (F3) đã làm.
    - Dropdown "Tin tuyển dụng" trong `CandidatesFilterBar` (F3) giới hạn 50 tin — trần
      `JobOwnerService.MAX_SIZE` ở backend, không phải lựa chọn tùy ý ở frontend. Công ty có hơn 50
      tin sẽ không lọc được tin cũ nhất qua dropdown này (đã có chú thích UI báo số lượng bị cắt bớt).
    - ~~Cột số trong `CandidatesTable` và `JobPerformanceTable` (F3) căn trái theo mặc định~~ — **đã
      xử lý ở Đợt 6 (`chore/hardening`)**: căn phải các cột số ở cả hai bảng.
  - Phát sinh khi làm F2 (FR-U05, `feat/fr-u05-cv-improve`):
    - `ApplicationHistoryEntryResponse.note` trả cho ứng viên (F3, endpoint `GET
      /api/candidates/applications/{id}/history`) — hiện an toàn vì cả 4 điểm ghi trong toàn bộ
      codebase đều truyền `null`, chưa có đường nào cho HR nhập `note` tự do. Khi cho HR nhập note
      phải bỏ field này khỏi DTO hoặc tách DTO riêng cho ứng viên. Chưa có integration test HTTP
      phủ endpoint này. Phát hiện khi khảo sát Plan Mode của F2, không phải lỗi do F2 gây ra.
    - Nút "Thử lại" khi trạng thái gợi ý cải thiện CV là `FAILED` chưa kiểm thử tay được với API
      key Anthropic thật — model gần như luôn trả JSON hợp lệ đúng schema, không có cách ép LLM
      thật trả lỗi một cách tin cậy để dựng thủ công tình huống này qua giao diện.
    - Chưa test race condition thật (hai request HTTP đồng thời thật sự) cho
      `uq_cv_improvement_request_active` — test hiện có gọi tuần tự, không phải song song thật.
    - Danh sách 20 job `OPEN` mới nhất gửi cho LLM không lọc theo lĩnh vực ở tầng SQL — việc lọc
      lĩnh vực hoàn toàn do LLM tự đọc và tự loại trong prompt, không dùng semantic search (F1/
      FR-U04 mới có hạ tầng embedding để lọc chính xác theo ngữ nghĩa). Nếu hệ thống có rất nhiều
      job đa dạng lĩnh vực, 20 tin mới nhất có thể không đủ đại diện cho lĩnh vực của một CV cụ thể.
  - Phát sinh khi làm F1 (FR-U04, `feat/fr-u04-recommend`):
    - Không có claim/stale-reaper cho `JobEmbeddingScheduler`/`ResumeEmbeddingScheduler`/
      `JobRecommendationCacheScheduler` — an toàn với một instance (fixedDelay không chạy chồng
      lượt), nhưng nếu triển khai đa instance có thể sinh embedding trùng cho cùng một job/CV ở hai
      lượt poll gần nhau (tốn thêm lời gọi API OpenAI, không sai dữ liệu vì upsert/update cuối cùng
      vẫn nhất quán). Cùng họ với khoản nợ đã ghi ở D1/D2/E2.
    - `resume_parsed_data` không có cột lưu tên model embedding đã dùng cho CV — cột `model` (V1)
      đã bị D1 chiếm dụng cho metadata bước parse. Mất provenance nếu sau này cần audit CV được
      embed bằng model nào (khác `job_embeddings.model` có cột riêng).
    - Danh sách job dùng để tính similarity không ưu tiên/lọc theo `deadline` xa/gần — mọi job
      `OPEN` (kể cả sắp hết hạn) đều được so sánh như nhau trong `findTopMatchingJobs`.
    - Chưa test race condition thật cho `uq_reco` (hai lượt `refreshOne` đồng thời thật sự, ví dụ
      hai instance scheduler chạy trùng) — bug thứ tự flush của Hibernate (đã sửa, thêm
      `jobRecommendationRepository.flush()` giữa `deleteByCandidateId` và `saveAll`) được phát hiện
      qua gọi tuần tự trong test, không phải qua đồng thời thật.
    - `EXPLAIN ANALYZE` xác nhận planner không dùng HNSW ở quy mô hiện tại (7 hàng
      `job_embeddings`) — đúng ở quy mô nhỏ (Seq Scan nhanh hơn), cần đo lại khi dữ liệu production
      đủ lớn để xác nhận planner tự chuyển sang Index Scan như dự đoán, không chỉ khi bị ép bằng
      tay qua `enable_seqscan`.
### Cố ý không làm / đánh đổi có chủ đích (`chore/hardening`, không phải bỏ sót)

Giữ riêng mục này để không mất dấu vết quyết định — đây là những chỗ **cố ý** chọn phương án đơn
giản hơn, không phải sơ suất hay việc chưa kịp làm. Chi tiết đầy đủ trong
`docs/walkthrough/chore-hardening.md` mục 4.

- **Không làm presigned URL cho tải file CV** (áp dụng cho cả ứng viên qua `ResumeCandidateController`
  lẫn HR qua `ResumeHrController`) — luồng hiện tại stream file qua app server và kiểm quyền sở hữu
  ở MỖI request, chặt hơn presigned (URL rò rỉ trong TTL truy cập được mà không kiểm lại quyền tại
  thời điểm truy cập). Presigned chỉ đáng làm khi cần giảm tải băng thông app server ở quy mô lớn —
  ngoài phạm vi đồ án. Chưa gây vấn đề ở quy mô hiện tại (`app.storage.type=local`, không S3/MinIO
  thật dù có chạy container MinIO trong `docker-compose`).
- **`org.postgresql:postgresql` đổi từ `<scope>runtime</scope>` sang mặc định (`compile`) ở Đợt 4.**
  Lý do: xử lý race `uq_score_per_criterion` ở `ScoringRunOrchestrator` (mục 4g) cần khớp CHÍNH XÁC
  theo tên ràng buộc qua `PSQLException.getServerErrorMessage().getConstraint()` — đáng tin cậy hơn
  cách khớp chuỗi message tự do mà `GlobalExceptionHandler.handleDataIntegrityViolation` đang dùng
  cho các ràng buộc khác — nhưng `PSQLException` chỉ có mặt trên classpath biên dịch của main code
  nếu bỏ `scope=runtime`. Hệ quả ngoài phạm vi một dòng pom: từ nay main code compile được thẳng với
  API của driver Postgres, không còn hàng rào build nào ngăn code tương lai import trực tiếp
  `org.postgresql.*` ở chỗ không thật sự cần. Giới hạn tự đặt để bù lại: chỉ dùng `PSQLException`
  trong ĐÚNG MỘT helper (`ScoringRunOrchestrator.isUniqueViolation`), không rải ra các service khác.
- **Rate limit chọn in-memory (Bucket4j), không Redis — chấp nhận có thể bị bypass.** Trần
  `app.rate-limit.max-tracked-keys` (LRU eviction qua `LinkedHashMap accessOrder`) chặn được OOM,
  nhưng đúng cơ chế đó mở đường khác: tạo hơn 10.000 khoá giả (IP giả/nhiều tài khoản) để đẩy khoá
  của chính mình ra khỏi map — mỗi lần bucket bị evict, hạn mức của khoá đó coi như reset về đầy.
  Đánh đổi cố hữu của rate limit in-memory không có backend chia sẻ, không phải sơ suất — cách chữa
  thật là chuyển sang bucket lưu tập trung ở Redis, ngoài phạm vi "một instance là đủ" của đồ án này.

- [x] `chore/seed-demo` — dữ liệu demo: 1 HR, 6 job có rubric (lệch so với 2 job dự
  kiến ban đầu), 9 CV thật cho 8 ứng viên (lệch so với 8 CV dự kiến ban đầu)
  - Phát hiện khi tạo dữ liệu demo (28/08/2026) — **D2: một tiêu chí trượt guard
    evidence làm hỏng cả lượt chấm.** Tái hiện 2/2 lần với cùng một đơn (CV kế toán
    của Nguyễn Thị Thu Hà nộp job Senior Java Backend Developer, rubric 4 tiêu chí).
    Ba tiêu chí đầu ghi vào `criterion_scores` thành công — trong đó hai tiêu chí có
    `evidence = []` kèm `score = 0`, xác nhận guard **chấp nhận** evidence rỗng khi
    không có gì để trích. Tiêu chí thứ tư "Kỹ năng mềm & giao tiếp" luôn trượt
    `EVIDENCE_NOT_VERIFIED`, khiến `scoring_runs.status = FAILED` và `total_score`
    không được tính, dù ba tiêu chí kia đã có điểm hợp lệ.

    Giả thuyết nguyên nhân (**chưa xác nhận** — câu trích bị từ chối không được ghi
    vào DB và log console đã trôi): LLM nhận CV dưới dạng JSON (`resume_parsed_data.data`),
    nơi các gạch đầu dòng trong `experience[].description` nối nhau bằng `\n` trần;
    guard đối chiếu evidence với `resume_parsed_data.raw_text`, nơi cùng nội dung đó
    mang tiền tố `•  ` và phân tách bằng `\r\n`. Trích **một** gạch đầu dòng thì khớp
    (chuỗi con liên tục ở cả hai bản); trích **hai gạch liền nhau** thì trượt chắc chắn
    vì bản JSON thiếu ký tự bullet xen giữa. Bằng chứng gián tiếp: các quote đã qua
    guard đều thuộc khối liền mạch không bullet (học vấn, chứng chỉ), và chúng dùng
    `\n` trong khi raw_text dùng `\r\n` — tức guard CÓ chuẩn hoá xuống dòng, chỉ không
    chuẩn hoá bullet.

    Phạm vi ảnh hưởng rộng hơn ca demo này: các tiêu chí kiểu "kỹ năng mềm", "làm việc
    nhóm", "tinh thần học hỏi" rất phổ biến trong rubric thật, và nội dung tương ứng
    trong CV gần như luôn nằm rải rác nhiều gạch đầu dòng chứ không thành khối. Lỗi có
    thể xảy ra cả với ứng viên đúng ngành.

    Chưa sửa, và **không nới guard trước**. Thứ tự đề xuất:
    (1) Cho phép chấm lại riêng từng tiêu chí thay vì hỏng cả lượt — giảm hậu quả mà
        không đụng tới tính đúng đắn của guard.
    (2) Dựng test với evidence gộp hai gạch đầu dòng để xác nhận giả thuyết trên.
    (3) Chỉ khi (2) xác nhận: chuẩn hoá cả hai vế trước khi so khớp (bỏ ký tự bullet,
        gộp khoảng trắng liên tiếp). Nới có kiểm soát — tuyệt đối không hạ xuống mức
        chấp nhận diễn giải thay cho trích nguyên văn, vì đó là cốt lõi FR-H06.

    Ghi nhận mặt tích cực: guard đang làm đúng việc của nó. Hệ thống thà báo thất bại
    còn hơn lưu evidence không kiểm chứng được — đúng tinh thần Explainable AI.
  - Hoàn thành (28/08/2026) — lệch có chủ đích so với PHASES.md, lý do chi tiết ở
    `docs/walkthrough/chore-seed-demo.md` mục "Quyết định thiết kế":
    - **6 job thay vì 2**: cần đủ đa ngành để ca demo ngưỡng `MIN_SIMILARITY_SCORE =
      0.40` (FR-U04) thuyết phục — ứng viên Nhân sự phải thấy gợi ý việc làm RỖNG
      thật, không phải vì hệ thống chưa chạy.
    - **9 CV (không phải 8)**: ứng viên Lê Văn Đức upload thêm bản CV thay thế sau
      khi phát hiện bản đầu ghi nhầm tên người khác — diễn biến thật trong lúc kiểm
      thử tay, không phải kế hoạch ban đầu.
    - File vận hành: `db/seed/reset-demo-db.sql`, `db/seed/seed-demo-structural.sql`
      (tầng 1), `db/seed/seed-demo-ai-output.sql` + `db/seed/resumes/` (tầng 2, sinh
      qua `export-ai-output.ps1` từ một lần chạy pipeline AI thật), `db/seed/install-demo-files.ps1`.
      Hướng dẫn đầy đủ: `db/seed/README.md`.
- PublicHeader không có menu mobile — nav ẩn hẳn dưới 640px (hidden sm:flex), trên điện thoại
  không có đường vào danh sách việc làm. Có sẵn từ A2, phát hiện khi làm fix/ui-nav-job-edit-layout.
- [x] `feat/candidates-scoring-action` — nút "Chấm điểm hồ sơ" ở `/hr/candidates` (F3). Gap-fill
  tiện dụng cho FR-H08, **không gắn mã FR mới** — tái dùng đúng endpoint `POST
  /api/hr/applications/{id}/scoring-runs` và logic disable đã có ở `ApplicationsTab.tsx` (D2),
  trích ra dùng chung (`features/scoring/scoringRules.ts`).
  - `ApplicationSearchItemResponse`: `latestScoringRunId`/`latestScoringRunStatus`/
    `latestScoringRunFinishedAt` lấy từ lượt chấm **mới nhất bất kể trạng thái** (LATERAL
    `latest_run` mới thêm), `totalScore` vẫn lấy từ lượt **DONE mới nhất** (LATERAL `latest_done`
    có sẵn) — hai nguồn khác nhau, **cố ý** (mirror đúng khuôn `LatestScoringRunView`/
    `LatestDoneScoringRunView` đã có ở D3/D4, `ApplicationOwnerService`). Riêng
    `searchCandidatesByCriterion`: `latestScoringRunId` trước đây là lượt **chứa tiêu chí đang
    lọc** (luôn DONE), nay là lượt **mới nhất bất kể tiêu chí** — đổi ngữ nghĩa có chủ đích, đã xác
    nhận không có nơi nào trong frontend đọc field này trước khi đổi.
  - Trang này **cố ý KHÔNG poll tự động** (khác `ApplicationsTab.tsx` của D2) — chỉ có nút "Tải
    lại" thủ công (`CandidatesFilterBar` nhận prop `extraActions`), vì đây là danh sách toàn công
    ty có phân trang, poll 5s sẽ nặng hơn nhiều so với phạm vi một job.
- [x] `docs/srs-update` — cập nhật `docs/SRS.md` cho khớp thực tế hệ thống, đối chiếu toàn
  bộ 18 mã FR (FR-C01→C04, FR-H01→H08, FR-U01→U06) với code thật trong `backend/src`/
  `frontend/src`, không chỉ tài liệu (30/08/2026).
  - Xác nhận lại bằng code trong lần rà soát này: `ApplicationService.apply()`
    (`backend/src/main/java/com/recruitment/jobapplication/ApplicationService.java:50-87`)
    không kiểm tra `resume.getParseStatus()` trước khi cho nộp đơn — ứng viên vẫn nộp
    được đơn dù CV đang `PENDING`/`PROCESSING`/`FAILED`. Frontend đã lọc CV `FAILED` khỏi
    form chọn từ `chore/hardening` Đợt 6, nhưng backend không chặn cứng; một client khác
    gọi thẳng API vẫn tạo được đơn với CV chưa/không parse xong. Không phải phát hiện
    mới (đã ghi trong `chore/hardening` phần "Phát hiện khi kiểm thử Phase D bằng key
    thật (19/08/2026)"), chỉ bổ sung bằng chứng file/dòng cụ thể.
  - Rà soát SRS (30/08/2026) phát hiện: ứng viên KHÔNG nhận thông báo khi hồ sơ được AI
    chấm điểm xong. Sự kiện `SCORING_FINISHED` chỉ có listener gửi cho HR
    (`NotificationEventListener`), không có nhánh gửi cho ứng viên. SRS gốc hứa "ứng
    viên nhận thông báo khi hồ sơ được đánh giá hoặc đổi trạng thái" — vế đầu chưa bao
    giờ được triển khai. Đặc tả đã cập nhật cho khớp thực tế. Cân nhắc khi mở rộng: báo
    cho ứng viên rằng hồ sơ đã được đánh giá mà không kèm điểm số hay nhận xét (điểm và
    rubric là thông tin nội bộ của HR, không được lộ cho ứng viên theo nguyên tắc ở
    FR-U05) — nếu làm, nội dung thông báo phải trung tính, không suy ra được kết quả.
- [ ] `docs/final` — README hoàn chỉnh, kịch bản demo, sơ đồ ER xuất từ database thật

---

## Giai đoạn 2 — Chức năng bổ sung

Quy trình mỗi FR: viết REQUIREMENT.md → viết UI.md → tôi duyệt (`ĐÃ DUYỆT`) → code theo
`CLAUDE.md` §6 → đợt cuối: đổi trạng thái `ĐÃ HOÀN THÀNH` ở REQUIREMENT.md và SRS.md, tick dòng
tương ứng ở đây.

**Chuẩn bị**
- [x] `docs/features-structure` — khung đặc tả 21 FR, cập nhật SRS.md (đợt 1); cập nhật CLAUDE.md,
  ROADMAP.md, UI_GUIDE.md (đợt 2)
- [x] `docs/skills-update` — cập nhật skill `fr-implement`, `srs-guard`, `walkthrough` theo quy
  trình mới (đợt 3; thêm skill `spec-review`)
- [x] `chore/ui-md3-foundation` — khai token vai trò MD3 (tiền tố `m3-`) ánh xạ sang token hiện có
  theo UI_GUIDE.md; chọn sắc xanh cho nút Ứng tuyển đạt ≥4.5:1 với chữ trắng; không đổi giao diện
  màn hình cũ. BẮT BUỘC xong trước FR đầu tiên có giao diện. Đã chốt `m3-tertiary` = `#007A3D`
  (5.45:1 với chữ trắng); CSS build trước/sau giống hệt.
- [x] `fix/hr-company-onboarding` — HR chưa có hồ sơ công ty được chuyển tới /hr/company thay vì gặp trang lỗi (không gắn mã FR).

**Phase 2.1 — Nền dữ liệu & gợi ý việc làm**
- [ ] `feat/fr-c05-catalog` — FR-C05 · Danh mục dùng chung và chuẩn hoá dữ liệu
- [ ] `feat/fr-u07-job-filter` — FR-U07 · Bộ lọc tìm việc nâng cao
- [ ] `feat/fr-u14-career-profile` — FR-U14 · Hồ sơ nghề nghiệp và mong muốn công việc
- [ ] `feat/fr-u15-profile-recommend` — FR-U15 · Gợi ý việc làm theo hồ sơ

**Xong khi:** tin/hồ sơ thiếu dữ liệu chuẩn hoá vẫn hiện kèm nhãn, không bị loại âm thầm; ứng viên
chưa có CV nhưng đã khai hồ sơ vẫn nhận gợi ý.

**Phase 2.1b — Đồng bộ giao diện cũ theo MD3 (BẮT BUỘC trước Phase 2.2)**
- [ ] `refactor/ui-md3-legacy` — áp UI_GUIDE.md (token `m3-*`, mục 2 điều hướng, mục 3 component, mục 4 ràng buộc) cho mọi màn hình cũ CHƯA được FR ở Phase 2.1 làm lại. Không đổi hành vi, không sửa backend. Chia đợt theo khu vực: (1) layout, (2) công khai + ứng viên, (3) HR, (4) badge + soát tổng. Danh sách việc:
  - Layout: PublicHeader thêm menu mobile (< sm hiện không có đường vào danh sách việc làm); HrLayout theo navigation drawer ≥ lg / rail < lg; CandidateLayout menu sheet < md.
  - PublicJobDetailPage giữ điều hướng ứng viên khi đã đăng nhập (nếu FR-U07 chưa sửa); PublicCompanyProfilePage.
  - Nút Ứng tuyển (`ApplyButton.tsx`, hiện `bg-accent` + chữ trắng 2.29:1) → `bg-m3-tertiary`; JobApplyPage/JobApplyForm.
  - Card việc làm (nếu FR-U07 chưa làm lại): lương `#008C45` chỉ 4.34:1; hạn nộp chưa ở góc phải card; ô logo trống khi công ty chưa có logo (hiện chữ viết tắt hoặc icon `Building2`).
  - Badge trạng thái đơn (`ApplicationStatusBadge`): bỏ xanh lá "Trúng tuyển" / đỏ "Bị từ chối", chuyển sang bảng màu trung tính; áp cho mọi nơi hiển thị trạng thái.
  - HR: HrHomePage, HrJobListPage, HrCandidatesPage, HrJobEditPage (4 tab, gồm ApplicationsTab và báo cáo giải thích), CompanyProfilePage, HrNotificationsPage.
  - Ứng viên: CandidateApplicationsPage + dòng thời gian lịch sử, CandidateProfilePage (phần FR-U14 chưa đụng), danh sách CV, CvImprovementSuggestionsPage, CandidateNotificationsPage.
  - LoginPage, RegisterPage.
  - Màn hình nào đã được FR ở Phase 2.1 làm lại hoàn toàn theo MD3 thì ghi "đã làm ở FR-xxx" và bỏ qua.

**Xong khi:** mọi route "Hiện có" trong UI_GUIDE mục 7 dùng token `m3-*` (không còn `bg-brand`/`text-ink`/`bg-accent`... trong code tầng tính năng, kiểm bằng rg); chữ trên nền màu đạt ≥ 4.5:1 (có bảng số đo); badge trạng thái không dùng cặp xanh/đỏ; `mvnw test` + `npm run build` + `npm run lint` sạch, `git diff --stat main -- backend` rỗng; soát bằng mắt từng route ở cả khổ desktop và điện thoại. Sau khi xong: cập nhật UI_GUIDE mục 0 — bỏ câu "màn hình cũ giữ nguyên tới nhánh refactor/ui-md3-legacy", token cũ chỉ còn để tương thích.

**Phase 2.2 — Hồ sơ đơn & trao đổi**
> Chỉ bắt đầu khi `refactor/ui-md3-legacy` (Phase 2.1b) đã tick.
- [ ] `feat/fr-h09-application-detail` — FR-H09 · Trang hồ sơ đơn ứng tuyển
- [ ] `feat/fr-u08-application-detail` — FR-U08 · Trang chi tiết đơn ứng tuyển
- [ ] `feat/fr-c06-messaging` — FR-C06 · Nhắn tin theo đơn ứng tuyển
- [ ] `feat/fr-c07-ai-draft` — FR-C07 · AI soạn nháp tin nhắn

**Xong khi:** gọi API đơn/cuộc trao đổi không thuộc về mình bị chặn (kiểm bằng curl, không qua
UI); có test chứng minh ngữ cảnh gửi AI soạn nháp không chứa điểm/rubric.

**Phase 2.3 — Sàng lọc & tạo tin**
- [ ] `feat/fr-h10-screening` — FR-H10 · Câu hỏi sàng lọc theo Job
- [ ] `feat/fr-u09-screening-answer` — FR-U09 · Trả lời sàng lọc và đồng ý lưu hồ sơ
- [ ] `feat/fr-h11-ai-job-draft` — FR-H11 · AI tạo tin tuyển dụng

**Xong khi:** câu trả lời sàng lọc không làm đổi điểm hay thứ hạng; tin tạo bằng AI luôn ở DRAFT;
trọng số rubric gợi ý để trống.

**Phase 2.4 — Lịch phỏng vấn**
- [ ] `feat/fr-h12-multi-slot` — FR-H12 · Giấy mời nhiều khung giờ
- [ ] `feat/fr-u10-pick-slot` — FR-U10 · Chọn khung giờ phỏng vấn

**Xong khi:** đơn chuyển INTERVIEW_INVITED ngay khi gửi giấy mời, không phụ thuộc việc chọn giờ;
giấy mời đã gửi không đổi khi sửa mẫu.

**Phase 2.5 — AI hỗ trợ đánh giá & kho ứng viên**
- [ ] `feat/fr-c08-cv-qa` — FR-C08 · Hỏi đáp CV có trích dẫn
- [ ] `feat/fr-h13-interview-questions` — FR-H13 · Câu hỏi phỏng vấn theo CV
- [ ] `feat/fr-h14-compare` — FR-H14 · So sánh 2–3 ứng viên
- [ ] `feat/fr-h15-talent-pool` — FR-H15 · Kho ứng viên

**Xong khi:** mọi trích dẫn hiển thị đều đã qua K2; FR-H04 chuyển sang dùng K2 mà toàn bộ test cũ
vẫn pass; backend chặn việc thêm đơn chưa đồng ý vào kho.

**Phase 2.6 — Tiện ích ứng viên & ẩn danh**
- [ ] `feat/fr-u11-stats` — FR-U11 · Thống kê ứng tuyển cá nhân
- [ ] `feat/fr-u12-cv-builder` — FR-U12 · Tạo CV (CV builder)
- [ ] `feat/fr-u13-nl-search` — FR-U13 · Tìm việc bằng ngôn ngữ tự nhiên
- [ ] `feat/fr-h16-blind` — FR-H16 · Chế độ ẩn danh

**Xong khi:** bật ẩn danh không đổi đầu vào chấm điểm; CV builder không thêm nội dung ngoài dữ liệu
ứng viên nhập.

---

## Ba nhánh cần review kỹ nhất

Bảng dưới đây là lịch sử của Phase D–E (đã hoàn thành, không đổi). Với FR bổ sung, xem mục
"AI hay làm sai" trong `REQUIREMENT.md` của FR đó.

| Nhánh | AI sẽ muốn làm | Vì sao sai |
|---|---|---|
| **D2** FR-H04 | Gộp tất cả tiêu chí vào một lần gọi LLM cho tiết kiệm token | Vi phạm nguyên tắc "chấm từng tiêu chí riêng lẻ", chất lượng chấm giảm rõ rệt |
| **D3** FR-H05 | Thêm ngưỡng phân loại, tô màu đỏ–vàng–xanh cho điểm | SRS cấm phân loại và gán nhãn — màu theo ngưỡng là phán quyết trá hình |
| **E1** FR-H07 | Thêm "tự động từ chối ứng viên dưới ngưỡng điểm" | Cấm tuyệt đối: quyết định luôn thuộc về HR (human-in-the-loop) |
