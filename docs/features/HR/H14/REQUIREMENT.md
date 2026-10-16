# FR-H14 — So sánh song song 2–3 ứng viên

> Trạng thái: ĐÃ DUYỆT 16/10/2026
>
> Mục 11 đã chốt (Q1–Q11 và S1–S6, 15/10/2026); `[Q…]`/`[S…]` trong thân là tham chiếu tới câu đã chốt.
>
> Sửa sau Plan Mode đợt 1 (16/10/2026): L1 (13 câu SQL), L2 (số dòng mục 0), chốt tên method và DTO.

- Nhóm: HR
- Tóm tắt: Ở tab "Ứng viên" của trang sửa tin, HR tick chọn 2–3 đơn của cùng một Job, cùng chu kỳ tuyển dụng, đã có
  lượt chấm hoàn tất → bấm "So sánh" → trang mới `/hr/jobs/:id/compare?ids=…` hiện bảng cột song song: tổng điểm, hạng
  (FR-H05), điểm từng tiêu chí kèm evidence (FR-H04), số năm kinh nghiệm (FR-C05), kỹ năng, học vấn, tóm tắt điểm
  mạnh/yếu (FR-H06), câu trả lời sàng lọc (FR-H10/U09). Mỗi cột có nút "Mở hồ sơ" (FR-H09) và — khi đơn đang
  `PENDING` — nút "Mời phỏng vấn" mở **đúng** hộp thoại có sẵn của FR-H07/H12. **Chỉ đọc**: không gọi AI, không chấm
  lại, không lưu gì, không đánh dấu người thắng.
- Phụ thuộc: FR-H05, FR-H06, FR-H09 — cả ba **Đã hoàn thành** (`docs/SRS.md:33`, `:34`, `:37`). Phụ thuộc ngầm vì hiển
  thị câu trả lời sàng lọc: FR-H10, FR-U09 — **Đã hoàn thành** (`docs/SRS.md:38`, `:52`; `docs/ROADMAP.md`, lưu ý dưới
  mục `feat/fr-h14-compare`). Hộp thoại mời phỏng vấn: FR-H07, FR-H12 — **Đã hoàn thành**.
- Nhánh: `feat/fr-h14-compare` (tạo từ `main` tại `bd08f5e`, 15/10/2026).
- Mở rộng: màn hình danh sách xếp hạng FR-H05/FR-H07 (tab "Ứng viên" của `/hr/jobs/:id/edit` thêm cột chọn và nút "So
  sánh"); `GET /api/hr/jobs/{jobId}/applications` thêm một field đọc `recruitmentCycle` `[Q7]`.
- Bên nhận phần còn lại: **FR-H16** che thông tin nhân thân trên bảng so sánh — tại **một** điểm dựng DTO của H14 (R-B1).

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

Đối chiếu trên `feat/fr-h14-compare` tại `bd08f5e` (15/10/2026; vừa kéo `main` từ `78d12a9` lên `bd08f5e`, cây làm việc
sạch).

### (a) Hiện trạng

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | Danh sách đơn theo Job phía HR: một endpoint, kiểm quyền theo Job | Có | `ApplicationOwnerController.java:17`, `:28-35` (`GET /api/hr/jobs/{jobId}/applications?sort=`); quyền: `ApplicationOwnerService.loadOwnedJob` (`:345-352`) — công ty trước (404 `COMPANY_NOT_FOUND`), Job không tồn tại 404 `JOB_NOT_FOUND`, Job công ty khác 403 |
| a2 | Lấy lượt DONE mới nhất | Một query hàng loạt, `DISTINCT ON (application_id)` theo `created_at DESC, id DESC`, chỉ `status = 'DONE'` | `ScoringRunRepository.findLatestDoneByApplicationIdIn` (`ScoringRunRepository.java:209-220`); dùng ở `ApplicationOwnerService.java:155-158`. Lượt **mới nhất bất kể trạng thái** (chỉ để hiện tiến độ) là query khác: `findLatestByApplicationIdIn` (`:46-56`) |
| a3 | Tổng điểm | Đọc `scoring_runs.total_score` của lượt DONE mới nhất (Backend tính ở FR-H05) | `ApplicationOwnerService.java:223`; `V1__init_schema.sql:264-265` |
| a4 | Hạng 1-2-2-4 tính ở đâu | Java, trong `assignRanks` trên **toàn bộ đơn của Job** | `ApplicationOwnerService.java:309-327` (hạng), `:333-338` (sắp: tổng điểm giảm dần, `null` cuối, phụ `appliedAt` tăng dần); tập đơn = `findByJobIdOrderByAppliedAtDesc(job.getId())` (`:122`, `JobApplicationRepository.java:65`) — **mọi chu kỳ, mọi trạng thái** (kể cả `WITHDRAWN`/`REJECTED`) |
| a5 | Một công thức hạng dùng chung | Có | `rankApplications` (`ApplicationOwnerService.java:121-208`) dùng cho cả danh sách (`:81-90`) và trang hồ sơ đơn (`evaluateApplication`, `:101-116`, FR-H09 Q1); số query cố định (comment `:128-135`), không N+1 |
| a6 | Điểm từng tiêu chí + reasoning + evidence | Từ `criterion_scores` của **chính** lượt DONE, thứ tự theo `rubric_snapshot` của lượt đó, ghép điểm vào tiêu chí **theo tên** trong một lượt | `buildCriterionScores` (`ApplicationOwnerService.java:283-302`); `CriterionScoreItem(criterionNameSnapshot, score, maxScoreSnapshot, weightSnapshot, reasoning, evidence)` (`ApplicationHrListItemResponse.java:54-65`); `EvidenceEntry(quote, section)` (`EvidenceEntry.java`) |
| a7 | Giải thích FR-H06 | Từ `score_explanations` của **cùng** lượt DONE; `summary`, `strengths`/`weaknesses` (`{criterionName, point}`), `metCriteria`/`missingCriteria`; trạng thái `PENDING`/`FAILED` khi lượt DONE chưa có giải thích | `ApplicationOwnerService.java:180-183`, `:230-244`, `:267-274`; `ApplicationHrListItemResponse.java:72-90`; `ExplanationPoint.java:11` (không có trích dẫn) |
| a8 | Danh sách HR **không** có chu kỳ tuyển dụng | Đúng | `ApplicationHrListItemResponse.java:28-44` không có `recruitmentCycle`; `frontend/src/features/scoring/types.ts:72-91` cũng không |
| a9 | Chu kỳ tuyển dụng | `jobs.recruitment_cycle` tăng khi `CLOSED → OPEN`; đơn chép chu kỳ của Job lúc nộp | `V1__init_schema.sql:78`, `:211`, `:222` (`uq_application_per_cycle`); `JobOwnerService.java:175-177`; `ApplicationService.java:95` |
| a10 | Trạng thái đơn | Đúng 5 giá trị | `ApplicationStatus.java` (`PENDING`, `INTERVIEW_INVITED`, `HIRED`, `REJECTED`, `WITHDRAWN`); CHECK `V1__init_schema.sql:212-213` |
| a11 | Rubric bị khoá khi tạo lượt chấm đầu tiên | Có, cùng transaction với `INSERT scoring_runs` | `ScoringRunStateService.create` (`ScoringRunStateService.java:66-90`); sửa rubric khi khoá → 409 `RUBRIC_LOCKED` (`RubricOwnerService.java:122-128`) |
| a12 | Rubric chỉ mở khoá khi Job **chưa từng có** dòng `criterion_scores` nào và không còn lượt đang chạy | Đúng | `unlockRubricIfSafe` (`ScoringRunStateService.java:233-240`) + `ScoringRunRepository.isSafeToUnlock` (`ScoringRunRepository.java:179-198`); mở lại Job (`CLOSED → OPEN`) **không** mở khoá (`JobOwnerService.java:158-177`) |
| a13 | Hệ quả a11–a12: hai đơn của cùng Job có thể được chấm theo hai phiên bản rubric khác nhau không? | **Theo luồng API: không** — lượt DONE nào cũng có `criterion_scores`, nên từ lượt DONE đầu tiên rubric khoá vĩnh viễn và mọi lượt sau chụp cùng một rubric. **Theo dữ liệu thật: có thể** — seed demo đặt `is_locked = FALSE` cho rubric đã có lượt DONE (`db/seed/seed-demo-structural.sql:381-383` …`:396`; lượt DONE ở `seed-demo-ai-output.sql:552-565`), HR demo sửa được rubric rồi chấm đơn khác → hai snapshot khác nhau. Vì vậy H14 vẫn phải ghép tiêu chí an toàn (R-C4, `[Q4]`) | Như cột trái |
| a14 | Khoá ghép tiêu chí giữa hai lượt | `RubricSnapshot.CriterionSnapshot.criterionId` (UUID của `rubric_criteria`, có trong mọi snapshot đã soát) | `RubricSnapshot.java:27-34`; ví dụ snapshot thật `seed-demo-ai-output.sql:552` (`"criterionId": "d4000000-…11"`). `criterion_scores.criterion_id` có thể `NULL` (`ON DELETE SET NULL`, `V1:281`) nên **không** dùng cột đó làm khoá; `uq_score_per_criterion (scoring_run_id, criterion_name_snapshot)` (`V1:290`) chỉ duy nhất **trong một lượt** |
| a15 | CV đã trích xuất phía HR | Đọc theo `job_applications.resume_id` (CV đã nộp, không phải CV chính hiện tại); chưa có dòng → 404 `RESUME_PARSED_DATA_NOT_FOUND` | `ResumeHrService.getParsedDataForApplication` (`ResumeHrService.java:93-99`); map dùng chung `ResumeParsedDataResponseMapper.toResponse` (`ResumeParsedDataResponseMapper.java:21-33`) |
| a16 | Kinh nghiệm (C05) | `experience_months` do backend tính; **cả bốn cột** kinh nghiệm `NULL` = chưa tính; `months` `NULL` = đã tính nhưng không có mục nào đọc được (không lưu 0). Năm do backend quy đổi (`toYears`, 1 chữ số thập phân) | `ResumeParsedData.java:65-78`; `ResumeParsedDataResponseMapper.toExperience` (`:40-51`); `ExperienceCalculator.java:86-88` |
| a17 | Kỹ năng, học vấn | `skills: List<String>`, `education: List<Education(school, degree, major, startDate, endDate, gpa)>`; danh sách `null` → `List.of()`; chuỗi nguyên văn CV | `ResumeParsedPayload.java:19-32` (record), `:61-63` (`Education`), `:38-44` (compact constructor) |
| a18 | Bản chụp sàng lọc | `application_screening_answers`: `question_id` (`NULL` được, `ON DELETE SET NULL`), `display_order`, `*_snapshot`, `answer_text`/`answer_yes_no`; trạng thái `NOT_CAPTURED`/`NO_QUESTIONS`/`CAPTURED` tính từ `screening_captured_at` | `V13__screening_questions.sql:39-74`; `ApplicationScreeningReader.read` (`ApplicationScreeningReader.java:21-47`); `ScreeningCaptureState.java`. Entity `JobApplication` **đã map** `screening_captured_at` (`JobApplication.java:58-59`, FR-U09) — câu nạp đơn theo Job đã có sẵn cột này; comment `ApplicationScreeningAnswerRepository.java:15` ("KHONG them field vao entity") đã cũ |
| a19 | `screening_revision` **không** lưu theo đơn | Đúng — chỉ là cột của `jobs`, tăng ở mọi thao tác ghi bộ câu hỏi | `V13:77`; `ScreeningQuestionOwnerService.java:79-87` (`incrementRevision`). Đơn chỉ có `screening_captured_at` (`V13:74`) |
| a20 | Sửa câu hỏi giữ nguyên `id`, đổi nội dung tại chỗ; sắp lại đổi `display_order` | Đúng | `ScreeningQuestionOwnerService.update` (`:77-87`) + `apply` (`:152-157`: đổi `type`, `text`, `options`, `required`); `reorder` (`:102`). ⇒ Cùng `question_id` có thể mang **nội dung khác nhau** ở hai đơn; cùng `display_order` có thể là **hai câu khác nhau** |
| a21 | DTO sàng lọc phía HR không có `questionId` (cố ý) | Đúng | `ApplicationScreeningResponse.java:10-11`, `:20-27` (FR-H10 mục 4.4) |
| a22 | Tab "Ứng viên" chưa có ô chọn, chưa có nút So sánh | Đúng | `ApplicationsTab.tsx:277-301` (7 cột: Ứng viên, Ngày nộp, Trạng thái CV, Trạng thái đơn, Hạng, Tổng điểm, Thao tác); không `Checkbox` nào |
| a23 | Trang sửa tin nhận `?tab=applications` | Có; `Tabs` không điều khiển (`defaultValue`), đổi tab không ghi URL | `HrJobEditPage.tsx:470` (`VALID_TABS`), `:481-488`, `:533-535`; route `App.tsx:101` |
| a24 | Hộp thoại "Mời phỏng vấn" mở ở đâu, khi nào | **Chỉ** từ thanh thao tác FR-H07 của `/hr/applications/:id`, khi đơn `PENDING`; "Gửi lại giấy mời" (H12) chỉ ở tab "Giấy mời phỏng vấn" khi `INTERVIEW_INVITED` | `ApplicationActionBar.tsx:16-24` (`nextActionsFor`), `:41-45`, `:76-81`; `InterviewInvitationTab.tsx` (mode `resend`) |
| a25 | Hộp thoại dùng lại được ngoài trang hồ sơ đơn | Có — prop tối thiểu `{ id, candidateName }`, `jobId`, `invalidateQueryKeys` | `InterviewInvitationDialog.tsx` (comment "FR-H09 R-A4: prop toi thieu…" và `interface InterviewInvitationDialogProps`); mặc định `mode='invite'` |
| a26 | Hiển thị evidence hiện có | `CriterionRow` (không export) trong `CriterionScoreBreakdown.tsx:26-90`, kiểu trích dẫn đúng UI_GUIDE mục 4; câu "Không tìm thấy trích dẫn nào…" khi rỗng | `CriterionScoreBreakdown.tsx:66-87`, `:97-105` |
| a27 | Quyền phía HR | Tầng filter: `/api/hr/**` → `hasRole("HR")` | `SecurityConfig.java:53-54`. Tầng sở hữu: theo Job (a1) hoặc theo đơn `HrApplicationAccess.loadOwned` (`HrApplicationAccess.java:42-56`) |
| a28 | Migration mới nhất | `V16__interview_invitation_slots.sql` | `backend/src/main/resources/db/migration/` |
| a29 | Seed test không có điểm AI (cố ý) | Đúng | `db/seed/seed-test.sql:21-23`; `db/seed/README.md` mục 8 ("Bộ test cố ý không có điểm AI"); 2 lượt chấm của seed test đều không DONE |
| a30 | Seed demo: Job nào có ≥ 2 đơn có lượt DONE | Chỉ Kỹ sư DevOps (`d2…03`: 2 đơn `PENDING`, 81.0 và 59.5) và Kỹ sư Kiểm thử (`d2…02`: một `PENDING` 83.5, một `WITHDRAWN` 0.0). Không Job nào có 3 đơn DONE; không Job demo nào có câu hỏi sàng lọc | `seed-demo-ai-output.sql:458-466` (đơn), `:551-565` (lượt chấm) |
| a31 | Không có handler cho tham số sai kiểu (`MethodArgumentTypeMismatchException`) | Đúng — UUID sai trong `@RequestParam List<UUID>` sẽ ra 400 mặc định của Spring, không phải `ErrorResponse` của dự án | `GlobalExceptionHandler.java` (không handler nào); tiền lệ tự parse chuỗi: bộ lọc FR-U07 (`INVALID_JOB_FILTER`, `:325`) |
| a32 | (Q8) Thêm câu hỏi sàng lọc vào Job đang `OPEN` đã có đơn | **Được** — `add` chỉ kiểm quyền sở hữu Job và giới hạn 10 câu, không kiểm trạng thái Job hay số đơn | `ScreeningQuestionOwnerService.add` (`ScreeningQuestionOwnerService.java:57-74`), `requireOwnedJob` (`:129-137`). Đơn cũ không bị đổi (bản chụp bất biến, a18) |
| a33 | (Q8) Sửa rubric khi `is_locked = FALSE` | Chỉ hai ràng buộc: rubric không khoá, và tổng trọng số **sau mỗi thao tác** ≤ 100 (service + trigger DB hoãn tới cuối transaction). **Không** ràng buộc trạng thái Job, **không** giới hạn số tiêu chí. Đổi tên dùng `PUT` tiêu chí → **giữ `id`**; trùng tên trong rubric → 409 | `RubricOwnerService.addCriterion` (`RubricOwnerService.java:49-65`), `updateCriterion` (`:67-85`, trừ trọng số cũ rồi cộng mới `:76-78`), `requireNotLocked` (`:124-128`), `requireNotExceeding` (`:130-134`); trigger `check_rubric_weight_sum` (`V1__init_schema.sql:133-152`, chỉ chặn `> 100`); `RubricCriterionRequest` (`weight` 0.01–100) |
| a34 | (Q8) Chấm đơn mới chụp đúng rubric đã sửa và khoá rubric | **Đúng** — tạo lượt đòi tổng trọng số **đúng bằng 100** (`RUBRIC_INCOMPLETE` nếu khác), CV đã trích xuất xong, không có lượt đang chạy; snapshot đọc `rubric_criteria` **sống** lúc tạo lượt, kèm `criterionId` = `id` tiêu chí; khoá rubric cùng transaction | `ScoringRunService.createScoringRun` (`ScoringRunService.java:77-95`), `requireRubricComplete` (`:160-167`), `requireNoRunInProgress` (`:172-178`); `RubricSnapshotMapper.toCriterionSnapshot` (`RubricSnapshotMapper.java:22-30`); `ScoringRunStateService.create` (`ScoringRunStateService.java:66-90`) |
| a35 | (Q8) Rubric DevOps demo đang mở khoá; Job DevOps `OPEN` | Đúng — `is_locked` `FALSE` (`seed-demo-structural.sql:387-388`); Job `d2…03` `OPEN`, `recruitment_cycle` 1, hạn nộp = ngày nạp + 45 ngày (`:299-312`) | Như cột trái. Nộp đơn đòi Job `OPEN`, chưa xoá, chưa quá hạn (`ApplicationService.java:72-74`, `findOpenJobById`) |
| a36 | (Q8) Ứng viên demo chưa nộp vào Job DevOps, có CV trích xuất xong | **`tran.minh.hoang@demo.local`** (`d0…02`): đơn duy nhất ở Job Java (`seed-demo-ai-output.sql:458`); CV `0b716edc-083b-434c-8b3d-8047454c7668` `parse_status = DONE` (`:29`) và có dòng `resume_parsed_data` `87062090-…`. Hai đơn DevOps hiện có là của `le.van.duc` (`d0…03`) và `nguyen.hai.son` (`d0…05`) (`:460`, `:465`) | Như cột trái. Không chọn `bui.ngoc.mai` (cố ý không nộp đơn nào — `db/seed/README.md` mục 6) |
| a37 | (Q8) Hai đơn DevOps cũ sẽ ở trạng thái sàng lọc `NOT_CAPTURED` | Đúng — câu `INSERT` đơn của dump demo không có cột `screening_captured_at` (thêm ở V13) → `NULL` | `seed-demo-ai-output.sql:460`, `:465` (danh sách cột); `V13__screening_questions.sql:74`; `ApplicationScreeningReader.java:24-27` |
| a38 | CV "bị xoá mềm" sau khi chấm | **Không xảy ra trong hệ thống hiện tại**: bảng `resumes` không có cột `deleted_at` (`V1__init_schema.sql:159-172`; migration duy nhất sửa `resumes` sau V1 là `V7__llm_retry_backoff.sql:12`, thêm cột thử lại), không có endpoint xoá CV (`ResumeCandidateController`/`ResumeService` không có `@DeleteMapping`/`delete`), và `job_applications.resume_id` là FK `ON DELETE RESTRICT` (`V1:209`). Ca phòng thủ còn lại là thiếu dòng `resume_parsed_data` — T21 | Như cột trái |
| a39 | Job đã xoá mềm / không `OPEN` | `loadOwnedJob` chỉ `findById`, không lọc `status` hay `deleted_at` (`ApplicationOwnerService.java:345-352`) — danh sách theo Job vẫn đọc được Job `DRAFT`/`PAUSED`/`CLOSED`/đã xoá mềm của công ty mình; `JobOwnerService.loadOwned` cùng cách (`JobOwnerService.java:200-209`). C1 giữ đúng hành vi đó (R-S10) | Như cột trái |
| a40 | Số query của `rankApplications` và tiền lệ đếm query | `rankApplications` chạy **9** câu (`ApplicationOwnerService.java:122`, `:136-192`): đơn theo Job, `resumes`, `users`, lượt mới nhất, lượt DONE mới nhất, `scoring_runs` của lượt DONE, `criterion_scores`, `score_explanations`, `score_explanation_attempts`. Comment `:128-135` ghi "10 query (2 ownership + 8)" nhưng liệt kê 9 mục — comment cũ, thực tế là 2 + 9 = 11. **Không có tiền lệ đếm query** trong test: `rg "getPrepareStatementCount|generate_statistics|QueryCount|datasource-proxy" backend` → 0 dòng | Như cột trái; dùng ở mục 4.6, T30 |

**Không cần migration.** H14 chỉ đọc các bảng đã có (`job_applications`, `scoring_runs`, `criterion_scores`,
`score_explanations`, `score_explanation_attempts`, `resume_parsed_data`, `application_screening_answers`); không lưu lượt
so sánh, không cột mới. Field `recruitmentCycle` thêm vào danh sách (`[Q7]`) đọc từ cột có sẵn `job_applications.recruitment_cycle`.

### (b) Mâu thuẫn và chỗ lệch — đã chốt 15/10/2026

| # | Mâu thuẫn | Đã chốt | Câu |
|---|---|---|---|
| M1 | `docs/SRS.md:93` (FR-H05): "sắp xếp danh sách ứng viên **của cùng một chiến dịch**". Code xếp hạng trên **mọi đơn của Job, mọi chu kỳ, mọi trạng thái** (a4). H14 chỉ so sánh trong một chu kỳ nhưng hạng hiển thị là hạng toàn Job | H14 **không** sửa công thức hạng (FR-H05 đã hoàn thành, một nguồn hạng duy nhất). Hiện hạng nguyên giá trị của danh sách, kèm chú thích "Hạng trong toàn bộ đơn của tin này" `[Q2]`. Việc "chiến dịch" = Job hay = chu kỳ ghi vào ROADMAP như câu hỏi mở cho FR-H05, không xử lý ở H14 | Q2 |
| M2 | Yêu cầu soát tay H14 "với seed test" nhưng seed test **cố ý không có điểm AI** (a29); seed demo chỉ có 2 đơn DONE cùng Job (a30), không có ca 3 cột, không có câu hỏi sàng lọc | Không viết điểm AI giả, không sửa file seed nào (quy ước `db/seed/README.md` mục 8). Soát tay trên seed demo, tự tạo đơn thứ ba + câu hỏi sàng lọc + rubric đã sửa bằng giao diện và một lượt chấm thật (mục 7.4, `[Q8]`) | Q8 |
| M3 | SRS mục 0 và bảng chức năng bổ sung ghi phụ thuộc H14 là FR-H05, FR-H06, FR-H09 (`SRS.md:106`), nhưng README mục 15 yêu cầu hiển thị câu trả lời sàng lọc (H10/U09) | Ghi H10, U09 là phụ thuộc ngầm (cả hai đã hoàn thành — không chặn). Đợt cuối thêm vào cột "Phụ thuộc" của `SRS.md:106` (D1) | — |
| M4 | `CLAUDE.md` §1 nói "39 mã", "21 FR bổ sung"; `docs/SRS.md:13-17` và `docs/features/README.md` mục 0 nói 38 mã / 20 FR (FR-H13 đã loại) | Không thuộc H14; báo ra, không sửa ở nhánh này | — |
| M5 | README mục 15: "Nút Mời phỏng vấn mở đúng hộp thoại của FR-H07". Đơn đang `INTERVIEW_INVITED` thì hộp thoại FR-H07 không mở được (a24); thao tác tương ứng là "Gửi lại giấy mời" (FR-H12) | Bảng so sánh chỉ có "Mời phỏng vấn" khi `PENDING` (đúng `nextActionsFor`); không đặt "Gửi lại giấy mời" `[Q9]` | Q9 |

## 1. Mục đích

Giúp HR đối chiếu trực quan 2–3 ứng viên của cùng một Job trên một màn hình, bằng đúng dữ liệu hệ thống đã có (điểm
Backend tính, điểm từng tiêu chí kèm evidence, giải thích, CV đã trích xuất, câu trả lời sàng lọc), rồi tự ra quyết định
trên từng đơn. Bảng so sánh không đưa ra kết luận nào thay HR.

## 2. Luồng người dùng

1. HR mở tab "Ứng viên" của `/hr/jobs/:id/edit` (danh sách xếp hạng FR-H05).
2. Mỗi dòng có ô chọn. Ô chỉ bật khi đơn đủ điều kiện so sánh (R-L1): đã có lượt chấm hoàn tất `[Q3]`, cùng chu kỳ
   với các đơn đã chọn `[Q7]`. Ô bị khoá có câu lý do.
3. HR tick 2–3 đơn. Khi đủ {MAX_COMPARE}, các ô chưa chọn bị khoá kèm câu lý do `[Q7]`. Thanh chọn phía trên bảng hiện "Đã chọn
   {n}/{MAX_COMPARE}" và nút "So sánh" (bật khi n ∈ {2, 3}).
4. Bấm "So sánh" → điều hướng tới `/hr/jobs/{jobId}/compare?ids={id1},{id2}[,{id3}]` theo **thứ tự HR đã tick** `[Q1]`.
5. Trang so sánh gọi một endpoint (C1), hiện bảng cột song song (UI.md mục 4). HR bấm "Lý giải và trích dẫn" ở từng hàng tiêu
   chí để mở lý giải và evidence của mọi ô trong hàng cùng lúc.
6. Trên mỗi cột: "Mở hồ sơ" → `/hr/applications/{id}`; "Mời phỏng vấn" (chỉ khi đơn `PENDING`) → mở
   `InterviewInvitationDialog` có sẵn. Gửi xong: bảng tải lại, cột đó đổi badge sang "Đã mời phỏng vấn" và mất nút mời.
7. Tải lại trang hoặc dán URL → bảng dựng lại từ `ids` trên URL. URL sai (thiếu, thừa, trùng, đơn không thuộc tin…) →
   câu lỗi tiếng Việt của backend + liên kết "Quay lại danh sách ứng viên".

## 3. Quy tắc nghiệp vụ

### 3.1 Phạm vi và ranh giới AI (R-A)

- **R-A1** H14 **không gọi AI**: không `ChatModel`, không `EmbeddingModel`, không thuộc danh sách K3 (CLAUDE.md §7), không
  tạo lượt chấm, không sinh giải thích mới. Chỉ đọc kết quả có sẵn của FR-H04 (điểm từng tiêu chí, evidence), FR-H05
  (tổng điểm, hạng), FR-H06 (giải thích), FR-C05 (kinh nghiệm), FR-C04 (CV đã trích xuất), FR-H10/U09 (bản chụp sàng lọc).
- **R-A2** Không lưu gì: không bảng, không cột, không ghi log nội dung so sánh. Endpoint C1 là `GET` thuần đọc.
- **R-A3** Không tính thêm bất kỳ chỉ số tổng hợp nào ngoài cái đã có (không "điểm trung bình", không "chênh lệch so với
  người cao nhất", không phần trăm, không xếp hạng lại trong nhóm được chọn). Tổng điểm và hạng là **nguyên giá trị** của
  FR-H05 (R-D3).

### 3.2 Chọn đơn (R-S) — kiểm ở backend, frontend chỉ để báo sớm

- **R-S1** Số đơn: **2 hoặc 3** id **khác nhau**. 0, 1, ≥ 4 id, hay có id trùng → 400.
- **R-S2** Mỗi id phải là UUID hợp lệ; chuỗi `ids` rỗng, có phần tử rỗng (`a,,b`, dấu phẩy cuối `a,b,`) hay sai dạng →
  400. Khoảng trắng quanh từng phần tử được bỏ; UUID viết hoa hay lẫn hoa thường được chấp nhận (`UUID.fromString`), response
  trả dạng chữ thường (T29, T31).
- **R-S3** Mọi đơn phải thuộc **đúng** Job trên đường dẫn. Đơn không tồn tại, đơn thuộc Job khác của cùng công ty, đơn
  của công ty khác → **cùng một** lỗi 400 `INVALID_COMPARE_SELECTION` với cùng một câu — không để lộ đơn có tồn tại hay
  thuộc công ty khác (UI_GUIDE mục 5: 403/404 cùng một thông điệp).
- **R-S4** Mọi đơn phải **cùng `recruitment_cycle`** → khác thì 400.
- **R-S5** Mọi đơn phải có **ít nhất một lượt chấm `DONE`** (cùng định nghĩa lượt DONE mới nhất của a2) → thiếu thì 409
  `COMPARE_APPLICATION_NOT_SCORED`.
- **R-S6** Trạng thái đơn: **cả 5 trạng thái đều được chọn** `[Q3]`; trạng thái chỉ quyết định nút hành động (R-H2),
  không quyết định có được so sánh hay không.
- **R-S7** Thứ tự kiểm (để câu lỗi xác định): quyền Job (a1: 404 `COMPANY_NOT_FOUND` → 404 `JOB_NOT_FOUND` → 403) → R-S2
  → R-S1 (trùng trước, số lượng sau; so trùng sau khi parse UUID, nên khác hoa/thường vẫn là trùng) → R-S3 → R-S4 →
  R-S5. Lỗi đầu tiên gặp được trả về; không gộp nhiều lỗi.
- **R-S8** Câu lỗi (tiếng Việt cố định, không chứa tên ứng viên — để FR-H16 không phải che câu lỗi):

  | Điều kiện | HTTP | `code` | `message` |
  |---|---|---|---|
  | R-S2: thiếu `ids`, phần tử rỗng, không phải UUID | 400 | `INVALID_COMPARE_SELECTION` | "Danh sách đơn cần so sánh không hợp lệ." |
  | R-S1: id trùng | 400 | `INVALID_COMPARE_SELECTION` | "Mỗi đơn chỉ được chọn một lần." |
  | R-S1: số id ∉ {2, 3} | 400 | `INVALID_COMPARE_SELECTION` | "Chọn từ 2 đến 3 đơn để so sánh." |
  | R-S3 | 400 | `INVALID_COMPARE_SELECTION` | "Có đơn không thuộc tin tuyển dụng này." |
  | R-S4 | 400 | `INVALID_COMPARE_SELECTION` | "Chỉ so sánh được các đơn cùng một chu kỳ tuyển dụng." |
  | R-S5 | 409 | `COMPARE_APPLICATION_NOT_SCORED` | "Có đơn chưa có lượt chấm điểm hoàn tất. Chỉ so sánh được các đơn đã chấm xong." |

  Mã lỗi theo `common/FormattedErrorCode` không áp dụng ở đây (không có cột lỗi nào được ghi — CLAUDE.md §4 chỉ ràng buộc
  enum ghi xuống cột lỗi).
- **R-S9** Frontend **không** tự sửa URL (không bỏ id trùng, không cắt bớt id thứ tư): luôn gửi nguyên `ids` lên backend
  và hiện câu backend trả về. Kiểm ở tab "Ứng viên" (R-L) chỉ để báo sớm; backend là chốt chặn duy nhất.
- **R-S10** Trạng thái Job không chặn so sánh: Job `DRAFT`/`OPEN`/`PAUSED`/`CLOSED` hay đã xoá mềm (`deleted_at` khác
  `NULL`) của công ty mình đều so sánh được, đúng như danh sách theo Job (a39). Chỉ quyền sở hữu Job chặn (R-S7).

### 3.3 Cột và thứ tự (R-O)

- **R-O1** Một cột = một đơn. Thứ tự cột = **đúng thứ tự `ids` trên URL** `[Q1]`, mà tab "Ứng viên" ghi theo thứ tự HR
  tick. Backend trả `columns` theo đúng thứ tự đó; frontend không sắp lại (không theo hạng, điểm, tên, ngày nộp).
- **R-O2** Không có thao tác sắp xếp/đổi chỗ cột trên trang so sánh. Muốn đổi thứ tự: quay lại tab "Ứng viên" chọn lại.

### 3.4 Dữ liệu từng hàng (R-D)

Mọi giá trị đọc ở **lượt DONE mới nhất** của đơn (a2) — điểm, tiêu chí, evidence, giải thích cùng một lượt (giữ R-D3/R-D6
của FR-H09). CV đã trích xuất đọc theo `job_applications.resume_id` (a15).

| # | Hàng | Nguồn | Thiếu dữ liệu |
|---|---|---|---|
| R-D1 | Đầu cột: tên ứng viên, badge trạng thái đơn, ngày nộp, thời điểm chấm (`finished_at` của lượt DONE) | `users.full_name`, `job_applications`, `scoring_runs` | — (luôn có) |
| R-D2 | Tổng điểm | `scoring_runs.total_score` của lượt DONE mới nhất | Không xảy ra (R-S5) |
| R-D3 | Hạng | **Đúng** giá trị `rank` của danh sách (a4, a5) — tính bằng chính `rankApplications`, không tính lại trong nhóm được chọn `[Q2]` | Không xảy ra (đơn có lượt DONE luôn có hạng) |
| R-D4 | Mỗi tiêu chí: điểm / điểm tối đa, trọng số; mở rộng: lý giải + evidence nguyên văn | `criterion_scores` của lượt DONE (a6) | Ô của đơn không có tiêu chí đó (R-C4) → "Chưa có dữ liệu" |
| R-D5 | Số năm kinh nghiệm | `resume_parsed_data.experience_months` + năm quy đổi (a16) | Không có dòng `resume_parsed_data`, chưa tính (`experience_computed_at` `NULL`) hoặc `months` `NULL` → "Chưa có dữ liệu". **Cố ý khác FR-C05** (`C05/UI.md:190`, `:220`): không "Đang tính…", không câu số mục bỏ qua, không dòng cơ sở "Tính trên …/… mục…" — trang so sánh không tự tải lại, dòng cơ sở làm ô dài thêm ở cả 3 cột, HR xem chi tiết qua "Mở hồ sơ" (S1) |
| R-D6 | Kỹ năng | `data.skills` nguyên văn, đúng thứ tự trong CV | Danh sách rỗng hoặc không có dòng trích xuất → "Chưa có dữ liệu" |
| R-D7 | Học vấn | `data.education`: mỗi mục "bằng cấp · chuyên ngành · trường · thời gian" ghép từ các trường khác `null`, chuỗi nguyên văn; **không** hiện `gpa` `[Q10]` | Rỗng → "Chưa có dữ liệu"; trường `null` trong một mục thì bỏ phần đó, không ghi "null" |
| R-D8 | Tóm tắt điểm mạnh/yếu | `score_explanations` của lượt DONE: `summary`, `strengths`, `weaknesses` (mỗi điểm kèm tên tiêu chí). **Không** hiện `metCriteria`/`missingCriteria` (R-W4) | `explanationStatus = PENDING` → "Báo cáo tổng hợp của AI đang được xử lý."; `FAILED` → "Không tạo được báo cáo tổng hợp của AI cho lượt chấm này." (giữ nguyên câu của `ExplanationReport.tsx:76-78`) |
| R-D9 | Câu trả lời sàng lọc | `application_screening_answers` (a18), ghép hàng theo R-Q | Theo R-Q4 |
| R-D10 | Ghi chú lượt chấm mới `[Q6]` | Đơn có lượt **mới hơn** lượt DONE đang hiện mà chưa xong (`PENDING`, hoặc `RUNNING` chưa có `total_score`) → cờ `newerRunInProgress = true`; lượt mới nhất `FAILED` không bật cờ | Không phải dữ liệu thiếu; chỉ là câu ghi chú trung tính ở đầu cột |

- **R-D11** Chuỗi lấy từ CV (kỹ năng, học vấn, evidence) hiện **nguyên văn, nguyên ngôn ngữ** — không dịch, không tóm tắt,
  không chuẩn hoá chữ hoa/thường (CLAUDE.md §2).
- **R-D12** Kinh nghiệm, kỹ năng, học vấn là dữ liệu **CV hiện tại** của bản trích xuất (có thể đã được trích xuất lại
  theo FR-C05 R-R5 sau khi chấm). Điểm, evidence là của **lúc chấm**. Bảng không cố hoà hai mốc; đầu cột ghi "Chấm lúc
  {t}" để HR biết mốc của phần điểm.
- **R-D13** Evidence của tổng điểm và hạng (S2): tổng điểm và hạng là số Backend tính từ điểm từng tiêu chí (FR-H05);
  evidence của chúng **là** evidence của các hàng tiêu chí trên cùng trang, mở bằng nút "Lý giải và trích dẫn" của từng
  hàng — giống danh sách theo Job hiện có (tổng điểm ở danh sách, evidence ở hồ sơ đơn). Hàng Tổng điểm/Hạng không có nút
  mở rộng riêng. Với cách này, yêu cầu "không có điểm nào hiển thị mà không mở ra được evidence" (CLAUDE.md §8, UI_GUIDE
  mục 4) được coi là đã đáp ứng.

### 3.5 Ghép hàng tiêu chí (R-C) `[Q4]`

- **R-C1** Khoá ghép một tiêu chí giữa các cột = `criterionId` trong `rubric_snapshot` của **lượt DONE** của từng đơn
  (a14). **Không** ghép theo vị trí (thứ tự) và **không** theo `criterion_scores.criterion_id` (có thể `NULL`).
- **R-C2** Trong một lượt: điểm gắn với tiêu chí của snapshot theo tên (giữ đúng cách `buildCriterionScores` đang làm,
  a6). Tiêu chí snapshot có `criterionId` `null` (dữ liệu bất thường) → khoá dự phòng là tên `criterionName` của snapshot,
  và chỉ ghép với tiêu chí cùng tên cũng `criterionId` `null`.
- **R-C3** Thứ tự hàng tiêu chí: theo thứ tự trong snapshot của **cột 1**; tiêu chí chỉ có ở cột sau thêm vào cuối theo
  thứ tự snapshot của cột đó.
- **R-C4** Tiêu chí có ở cột này mà không có ở cột kia → hàng vẫn hiện; ô thiếu ghi "Chưa có dữ liệu" kèm câu "Đơn này
  được chấm theo phiên bản rubric không có tiêu chí này." — **không** loại hàng, **không** loại đơn.
- **R-C5** Cùng `criterionId` nhưng tên, trọng số hay điểm tối đa khác nhau giữa các cột → mỗi ô hiện tên/trọng số/điểm tối
  đa **của chính cột đó**; tiêu đề hàng dùng tên ở cột đầu tiên có tiêu chí; hàng có cờ `rubricVersionsDiffer = true` →
  giao diện hiện câu "Các đơn được chấm theo phiên bản rubric khác nhau ở tiêu chí này." Không quy đổi điểm về cùng thang.
- **R-C6** Có bất kỳ hàng nào lệch phiên bản (R-C4 hoặc R-C5) → response có `rubricVersionsDiffer = true` ở gốc; đầu
  trang hiện một khối lưu ý trung tính (UI.md 4a).

### 3.6 Ghép hàng câu hỏi sàng lọc (R-Q) `[Q5]`

- **R-Q1** Một hàng = một câu hỏi **giống hệt nhau** giữa các đơn: cùng `question_id` (khác `NULL`) **và** cùng
  `question_type_snapshot`, `question_text_snapshot`, `options_snapshot` (so nguyên văn). `required_snapshot` khác nhau
  không tách hàng. **Không** ghép theo `display_order` (a20) và không dựa vào `screening_revision` (không lưu theo đơn,
  a19).
- **R-Q2** Cùng `question_id` nhưng nội dung khác (HR đã sửa câu giữa hai lần nộp) → **hai hàng riêng**, mỗi hàng ghi câu
  hỏi nguyên văn của bản chụp; ô của đơn không có hàng đó ghi "Không có câu hỏi này lúc ứng viên nộp đơn." `question_id`
  `NULL` → hàng riêng của đơn đó.
- **R-Q3** Thứ tự hàng: theo `display_order` của **cột 1**; hàng chỉ có ở cột sau thêm vào cuối theo `display_order` của
  cột đó.
- **R-Q4** Ô sàng lọc theo trạng thái bản chụp của đơn (a18), đúng câu của FR-H10/U09 (`screeningLabels.ts:65-77`):

  | Trường hợp | Ô hiện |
  |---|---|
  | Đơn `NOT_CAPTURED` | Một câu cho cả khối: "Đơn này được nộp trước khi hệ thống ghi nhận câu trả lời sàng lọc, nên không có câu trả lời để hiển thị." |
  | Đơn `NO_QUESTIONS` | Một câu cho cả khối: "Tin tuyển dụng chưa có câu hỏi sàng lọc vào thời điểm ứng viên nộp đơn ({t})." |
  | `CAPTURED`, có hàng, không trả lời | "Ứng viên không trả lời câu này." |
  | `CAPTURED`, không có hàng đó | "Không có câu hỏi này lúc ứng viên nộp đơn." |
  | Có trả lời | `YES_NO`: "Có"/"Không"; `SINGLE_CHOICE`/`SHORT_TEXT`: nguyên văn `answer_text` |

- **R-Q5** Không đơn nào `CAPTURED` → khối sàng lọc chỉ có các câu trạng thái theo cột; không hàng câu hỏi nào.
- **R-Q6** Câu trả lời sàng lọc chỉ để tham khảo: không tô, không đếm số câu "Có", không so khớp với "đáp án mong muốn"
  (FR-H10 không có khái niệm đáp án).

### 3.7 Không đánh dấu người thắng (R-W)

- **R-W1** Không nhãn, màu, biểu tượng hay kiểu chữ nào đánh dấu cột/ô **cao nhất**, **thấp nhất**, "tốt hơn", "phù hợp
  hơn"; không in đậm giá trị lớn nhất; không thanh tiến trình tương đối giữa các cột; không mũi tên ↑↓; không icon ✓ ✗.
- **R-W2** Làm nổi bật khác biệt **theo hàng, không theo ô**: backend tính cờ `valuesDiffer` cho từng hàng (true khi giá
  trị **không** giống nhau ở mọi cột). Giao diện tô **cả hàng** bằng nền trung tính và thêm chữ "Khác nhau" ở tiêu đề
  hàng — mọi ô trong hàng cùng một kiểu, nên không ô nào được tôn lên. Hàng áp dụng: tổng điểm, hạng, từng tiêu chí (so
  `score`), kinh nghiệm (so `months`), câu sàng lọc `YES_NO`/`SINGLE_CHOICE` (so câu trả lời). Hàng văn bản (kỹ năng, học
  vấn, giải thích, `SHORT_TEXT`) **không** có cờ. Ô thiếu dữ liệu tính là **một giá trị riêng**: ô tiêu chí `null` (R-C4),
  kinh nghiệm `null`, ô sàng lọc khác `ANSWERED` (`NOT_ANSWERED`, `NOT_ASKED`, `NOT_CAPTURED`, `NO_QUESTIONS` — mỗi trạng
  thái một giá trị) đặt cạnh một ô có giá trị → `valuesDiffer = true`; mọi ô cùng thiếu theo cùng một kiểu → `false`.
- **R-W3** DTO (C1) và mọi record con **không** có field tên `verdict`, `label`, `isQualified`, `passed`,
  `recommendation` (CLAUDE.md §7); cũng không `winner`, `best`, `top`, `highlight`, `isHighest`.
- **R-W4** Không hiện `metCriteria`/`missingCriteria` của FR-H06 trên bảng so sánh: đặt cạnh nhau theo cột chúng thành một
  bảng tick đạt/không đạt trá hình. Hai danh sách vẫn ở tab "Giải thích" của trang hồ sơ đơn (FR-H06/H09 không đổi).
- **R-W5** Không có ô/nút "Chọn ứng viên này", "Đề xuất", hay hành động nào áp lên **nhiều** đơn cùng lúc. Quyết định vẫn
  bấm trên **từng** đơn (FR-H07).

### 3.8 Hành động trên cột (R-H)

- **R-H1** "Mở hồ sơ" luôn có → `/hr/applications/{applicationId}` (cùng tab trình duyệt; nút Back quay về đúng URL so sánh).
- **R-H2** "Mời phỏng vấn" chỉ khi đơn `PENDING` — cùng điều kiện `nextActionsFor` (`ApplicationActionBar.tsx:16-24`). Mở
  **đúng** `InterviewInvitationDialog` (`mode` mặc định `'invite'`, prop `{ id, candidateName }`, `jobId`) — không viết
  hộp thoại mới, không sửa hộp thoại. Không đặt "Từ chối", "Trúng tuyển", "Gửi lại giấy mời" trên bảng `[Q9]`.
- **R-H3** Gửi lời mời thành công → làm mới: dữ liệu so sánh (C1), danh sách theo Job, các khoá của trang hồ sơ đơn đó
  (`applicationDetailInvalidateKeys`). Cột giữ nguyên vị trí; badge đổi, nút mời biến mất.
- **R-H4** Đơn đã đổi trạng thái ở nơi khác (vd đã bị từ chối) khi HR bấm gửi → lỗi có sẵn của FR-H07 hiện trong hộp
  thoại (`INVALID_APPLICATION_STATUS_TRANSITION`/`APPLICATION_STATUS_CONFLICT`), không xử lý riêng ở H14.

### 3.9 Tab "Ứng viên" — chọn đơn (R-L)

- **R-L1** Thêm cột ô chọn ở đầu bảng `ApplicationsTab`. Ô **bật** khi: `totalScore !== null` (đơn có lượt DONE — R-S5)
  **và** (chưa chọn đơn nào **hoặc** `recruitmentCycle` bằng chu kỳ của đơn chọn đầu tiên) `[Q7]` **và** (đã chọn < 3
  **hoặc** chính đơn đó đang được chọn).
- **R-L2** Ô khoá có câu lý do (UI.md mục 7), gắn bằng `aria-describedby`, không chỉ `title`.
- **R-L3** Lựa chọn giữ trong state của tab, theo `applicationId`, giữ **thứ tự tick**; không mất khi danh sách tự tải lại
  (polling tiến độ chấm) hay khi đổi "Sắp xếp theo". Rời trang/tải lại tab thì lựa chọn mất (chỉ trang so sánh giữ trên
  URL — R-U1).
- **R-L4** Thanh chọn: "Đã chọn {n}/{MAX_COMPARE}", nút "Bỏ chọn" (n ≥ 1), nút "So sánh" (bật khi n ∈ {2, 3}). Không thay đổi các cột,
  nút, sắp xếp, polling đang có của bảng.

### 3.10 URL (R-U)

- **R-U1** Route `/hr/jobs/:id/compare?ids={uuid},{uuid}[,{uuid}]` — `ids` một tham số, phân tách bằng dấu phẩy, giữ thứ
  tự. Tải lại trang, mở lại từ lịch sử trình duyệt, dán URL → dựng lại đúng bảng.
- **R-U2** Trang không ghi gì thêm lên URL (không trạng thái mở rộng ô, không cuộn).
- **R-U3** "Quay lại danh sách ứng viên" → `/hr/jobs/:id/edit?tab=applications`.

### 3.10b Làm mới dữ liệu (R-R)

- **R-R1** Trang so sánh **không** tự tải lại định kỳ. Dữ liệu là ảnh tại thời điểm tải; đơn có lượt mới đang chạy hiện
  ghi chú R-D10, HR tự bấm "Tải lại" để xem kết quả mới khi lượt đó xong `[Q6]`.
- **R-R2** Lượt mới xong (DONE) sau khi trang đã tải → lần tải lại tiếp theo hiện lượt mới (đúng quy tắc "DONE mới nhất").

### 3.11 Ranh giới với FR-H16 (R-B)

- **R-B1** Toàn bộ dữ liệu của bảng (tên, evidence, lý giải, giải thích, kỹ năng, học vấn, câu trả lời sàng lọc) được
  dựng ở **một** method backend duy nhất (`ApplicationComparisonService.compare`) thành **một** DTO
  (`ApplicationComparisonResponse`). Frontend không gọi thêm endpoint nào để lấy dữ liệu hiển thị (E1–E5 của FR-H09,
  `/resume/parsed`, `/screening-answers` **không** được gọi từ trang so sánh). FR-H16 chỉ cần che tại method này.
- **R-B2** Việc che thuộc FR-H16 — ngoài phạm vi H14 (mục 6).
- **R-B3** Danh sách field của `ApplicationComparisonResponse` (mục 4.4) mà FR-H16 phải xử lý — để H16 không phải đọc lại
  code:

  | Field | Loại | H16 cần làm |
  |---|---|---|
  | `columns[].candidateName` | Định danh trực tiếp | Thay bằng "Ứng viên #mã" |
  | `criteria[].cells[].reasoning` | Văn bản AI, có thể nhắc tên/liên hệ | Thay chuỗi các giá trị liên hệ đã biết |
  | `criteria[].cells[].evidence[].quote` | Trích nguyên văn CV | Thay chuỗi (evidence mục `contact` có thể chứa nguyên email/điện thoại) |
  | `explanations[].summary`, `explanations[].strengths[].point`, `explanations[].weaknesses[].point` | Văn bản AI | Thay chuỗi |
  | `profiles[].skills[]` | Chuỗi nguyên văn CV | Thay chuỗi (hiếm khi chứa thông tin nhân thân) |
  | `profiles[].education[].school`, `.degree`, `.major` | Chuỗi nguyên văn CV | Thay chuỗi; H16 tự quyết có che tên trường hay không |
  | `screening.rows[].cells[].answerText` khi `questionType = SHORT_TEXT` | Câu ứng viên tự gõ | Thay chuỗi |

  **Không** cần che: `jobId`, `jobTitle`, `recruitmentCycle`, `applicationId`, `status`, `appliedAt`, `scoringRunId`,
  `scoredAt`, `newerRunInProgress`, mọi số (`totalScore`, `rank`, `score`, `maxScore`, `weight`, `months`, `years`),
  `criterionName` (do HR đặt), `evidence[].section`, `questionText`/`options` (do HR đặt), `answerText` của
  `SINGLE_CHOICE` (là một phương án do HR đặt), `answerYesNo`, các cờ `valuesDiffer`/`rubricVersionsDiffer`, `states`,
  `capturedAt`, `education[].startDate`/`.endDate`. Câu lỗi R-S8 không chứa tên (đã cố ý). Hộp thoại "Mời phỏng vấn" nhận
  `candidateName` từ DTO này, còn nội dung thư do preview FR-H07 render ở backend — H16 tự bỏ ẩn danh khi mời
  (README mục 20), không thuộc H14.

## 4. Dữ liệu & quyền truy cập

### 4.1 Migration

Không. Lý do ở mục 0 (a28 và đoạn "Không cần migration").

### 4.2 Package và file backend

Package mới `comparison/` `[Q11]` (CLAUDE.md §3: "mỗi mã FR nằm gọn trong một package"): H14 đọc chéo bốn package
(`jobapplication/`, `scoring/`, `resume/`, `screening/`), đặt vào `jobapplication/` sẽ làm package đó phụ thuộc
`screening/`. Đợt cuối thêm `comparison/` vào danh sách package ở `CLAUDE.md` §3 (D6).

| File | Thay đổi |
|---|---|
| `comparison/ApplicationComparisonController.java` (mới) | C1. Nhận `ids` là `String` (`@RequestParam(required = false)`), **không** `List<UUID>` — tự parse để trả đúng R-S8 (a31) |
| `comparison/ApplicationComparisonService.java` (mới) | Điểm dựng DTO duy nhất (R-B1): kiểm R-S theo R-S7, gọi bốn nguồn hàng loạt, ghép R-C, R-Q, tính `valuesDiffer`, `rubricVersionsDiffer`, `newerRunInProgress`. **Không** `@Transactional` (kể cả `readOnly`) — cùng quy ước `ApplicationOwnerService.java:40`: chỉ đọc, các repository độc lập; JSON đọc qua `@JdbcTypeCode` trong cùng câu SELECT nên không cần transaction |
| `comparison/CompareSelectionParser.java` (mới) | Parse + kiểm R-S1, R-S2 (đơn vị, không cần Spring) |
| `comparison/dto/ApplicationComparisonResponse.java` (mới) | Mục 4.4 |
| `jobapplication/ApplicationOwnerService.java` | Đổi `loadOwnedJob(UUID jobId, UUID ownerId)` sang `public` (không thêm method mới — một quy tắc sở hữu, giữ thứ tự 404 `COMPANY_NOT_FOUND` → 404 `JOB_NOT_FOUND` → 403). Thêm `public Map<UUID, ApplicationComparisonSource> evaluateApplications(Job job, Collection<UUID> applicationIds)`: gọi `rankApplications(job)` **đúng một lần** cho mỗi request (một công thức hạng — a5), trả map chỉ gồm id thuộc Job (id thiếu → R-S3 ở `comparison/`). `RankableRow` thêm `resumeId`, `recruitmentCycle`, `screeningCapturedAt`, `rubricCriteria` (đọc từ dữ liệu đã nạp, không thêm query). `listApplications`/`evaluateApplication` không đổi hành vi; `ApplicationOwnerServiceTest` và `ApplicationOwnerControllerIntegrationTest` xanh không sửa khẳng định |
| `jobapplication/ApplicationComparisonSource.java` (mới, public record) | Dữ liệu một đơn cho H14, rút từ `RankableRow`: `UUID applicationId`, `String candidateName`, `ApplicationStatus status`, `Instant appliedAt`, `UUID resumeId`, `int recruitmentCycle`, `Instant screeningCapturedAt`, `UUID scoringRunId` (lượt DONE mới nhất, `null` nếu chưa có), `Instant scoredAt`, `BigDecimal totalScore`, `Integer rank`, `ScoringRunStatus latestRunStatus` (lượt mới nhất bất kể trạng thái — nguồn `newerRunInProgress`), `List<ApplicationHrListItemResponse.CriterionScoreItem> criterionScores`, `List<RubricSnapshot.CriterionSnapshot> rubricCriteria` (snapshot của lượt DONE, để ghép theo `criterionId`), `ApplicationHrListItemResponse.ExplanationStatus explanationStatus`, `ApplicationHrListItemResponse.Explanation explanation`. Đặt ở `jobapplication/` để không package nào import ngược `comparison/`. **`ApplicationEvaluation` không đổi**, JSON của E3/E4 (FR-H09) không đổi |
| `jobapplication/dto/ApplicationHrListItemResponse.java` | Thêm `int recruitmentCycle` `[Q7]` (field đọc, thêm vào cuối; không đổi field cũ) |
| `resume/ResumeParsedDataRepository.java` | Thêm `List<ResumeParsedData> findByResumeIdIn(Collection<UUID>)` |
| `screening/ApplicationScreeningAnswerRepository.java` | Thêm `findByApplicationIdInOrderByApplicationIdAscDisplayOrderAsc(Collection<UUID>)`. **Không** thêm câu đọc `screening_captured_at`: giá trị lấy từ entity `JobApplication` đã nạp ở `rankApplications` (a18) |
| `screening/ScreeningAnswerSnapshot.java` (mới, public record) | Bản đọc **có** `questionId` cho H14 ghép R-Q1; chỉ dùng nội bộ backend — `questionId` **không** ra API (giữ quyết định FR-H10 mục 4.4, a21) |
| `screening/ApplicationScreeningReader.java` | Thêm `public Map<UUID, ScreeningBundle> readMany(Map<UUID, Instant> capturedAtByApplicationId)` (trạng thái + bản chụp theo đơn, 1 query; `capturedAt` do nơi gọi truyền từ entity đã nạp, `null` = `NOT_CAPTURED`); `read(...)` cũ không đổi |
| `common/exception/InvalidCompareSelectionException.java`, `CompareApplicationNotScoredException.java` (mới) + hai nhánh trong `GlobalExceptionHandler` | R-S8 |

Không sửa: `scoring/` (chỉ đọc qua `ApplicationOwnerService`), `interviewinvitation/`, `rubric/`, `ai/**`, `SecurityConfig`
(route nằm dưới `/api/hr/**`, đã `hasRole("HR")`).

### 4.3 Endpoint

| # | Endpoint | Mô tả | Thành công | Lỗi |
|---|---|---|---|---|
| C1 | `GET /api/hr/jobs/{jobId}/applications/compare?ids={uuid},{uuid}[,{uuid}]` | Dữ liệu bảng so sánh (R-B1) | 200 `ApplicationComparisonResponse` | 401 (không token); 403 (token ứng viên — filter chain; Job công ty khác — `loadOwnedJob`); 404 `COMPANY_NOT_FOUND`; 404 `JOB_NOT_FOUND`; 400 `INVALID_COMPARE_SELECTION` (R-S8); 409 `COMPARE_APPLICATION_NOT_SCORED` |
| — | `GET /api/hr/jobs/{jobId}/applications` | Có sẵn; thêm field `recruitmentCycle` vào mỗi phần tử `[Q7]` | 200 | Không đổi |

`/compare` là đường dẫn tĩnh dưới `ApplicationOwnerController` (`/api/hr/jobs/{jobId}/applications`) nhưng ở controller
khác; không trùng mapping nào hiện có (route `GET …/applications` không có biến con). Plan Mode kiểm bằng
`MSYS_NO_PATHCONV=1 rg -n '@RequestMapping\("/api/hr/jobs' backend/src/main` — trong Git Bash phải có
`MSYS_NO_PATHCONV=1`: thiếu nó, MSYS đổi đoạn `/api/hr/jobs` thành đường dẫn Windows và `rg` âm thầm ra 0 dòng (đã thử
16/10/2026, cả nháy đơn lẫn nháy kép). Kết quả 16/10/2026: 5 controller (`JobOwnerController` `/api/hr/jobs`,
`InterviewTemplateOwnerController`, `ScreeningQuestionOwnerController`, `ApplicationOwnerController`
`/api/hr/jobs/{jobId}/applications`, `RubricOwnerController`); không controller nào có `/compare`.

Không endpoint ghi nào. Không rate limit (đọc thuần, không AI — giống E3/E4 của FR-H09).

### 4.4 DTO

```java
// C1 - DTO DUY NHAT cua bang so sanh (R-B1, diem che cua FR-H16). Khong field verdict/label/isQualified/passed/
// recommendation/winner/best/top/highlight (R-W3).
record ApplicationComparisonResponse(
        UUID jobId, String jobTitle, int recruitmentCycle,
        boolean rubricVersionsDiffer,                 // R-C6
        List<Column> columns,                         // dung thu tu ids (R-O1), 2..3 phan tu
        Row totalScore,                               // R-D2
        IntRow rank,                                  // R-D3 [Q2]
        List<CriterionRow> criteria,                  // R-C
        ExperienceRow experience,                     // R-D5
        List<ProfileCell> profiles,                   // R-D6, R-D7 - theo thu tu columns
        List<ExplanationCell> explanations,           // R-D8 - theo thu tu columns
        Screening screening) {                        // R-Q

    record Column(UUID applicationId, String candidateName, ApplicationStatus status, Instant appliedAt,
                  UUID scoringRunId, Instant scoredAt,          // luot DONE moi nhat
                  boolean newerRunInProgress) {}                // R-D10 [Q6]

    // Gia tri so theo cot; valuesDiffer = khong giong nhau o moi cot (R-W2)
    record Row(List<BigDecimal> values, boolean valuesDiffer) {}    // tong diem (so sanh bang compareTo)
    record IntRow(List<Integer> values, boolean valuesDiffer) {}    // hang (FR-H05), cung kieu Integer voi danh sach

    record CriterionRow(String key,                     // criterionId (hoac "name:" + ten, R-C2) - chi de React key
                        String criterionName,           // ten o cot dau tien co tieu chi (R-C5)
                        boolean rubricVersionsDiffer,   // R-C4/R-C5
                        boolean valuesDiffer,
                        List<CriterionCell> cells) {}   // theo thu tu columns; phan tu null = khong co tieu chi (R-C4)
    record CriterionCell(String criterionName, BigDecimal score, int maxScore, BigDecimal weight,
                         String reasoning, List<EvidenceEntry> evidence) {}

    record ExperienceRow(List<Experience> values, boolean valuesDiffer) {}
    record Experience(Integer months, BigDecimal years) {}   // ca hai null = "Chua co du lieu" (R-D5)

    record ProfileCell(boolean parsedDataAvailable, List<String> skills, List<EducationItem> education) {}
    record EducationItem(String degree, String major, String school, String startDate, String endDate) {} // khong gpa [Q10]

    record ExplanationCell(String summary, List<ExplanationPoint> strengths, List<ExplanationPoint> weaknesses,
                           ApplicationHrListItemResponse.ExplanationStatus status) {}  // khong met/missingCriteria (R-W4)

    record Screening(List<ScreeningCaptureState> states, List<Instant> capturedAt,   // theo thu tu columns
                     List<ScreeningRow> rows) {}
    record ScreeningRow(ScreeningQuestionType questionType, String questionText, List<String> options,
                        boolean valuesDiffer,                    // chi YES_NO/SINGLE_CHOICE; SHORT_TEXT luon false
                        List<ScreeningCell> cells) {}
    record ScreeningCell(ScreeningCellState state, String answerText, Boolean answerYesNo) {}
    enum ScreeningCellState { ANSWERED, NOT_ANSWERED, NOT_ASKED, NOT_CAPTURED, NO_QUESTIONS }
}
```

- Mọi `List` chuẩn hoá `null` → `List.of()` trong compact constructor, **trừ** `CriterionRow.cells` (phần tử `null` có
  nghĩa — R-C4). Số từ DB giữ `BigDecimal` như DTO hiện có.
- Không `questionId`, không `criterionId` thô ngoài `CriterionRow.key`; không `rawText`, `model`, `promptVersion`,
  `tokenUsage`, `contact` (thông tin liên hệ không có trong bảng — chỉ tên ở `Column`).
- Đã chốt ở Plan Mode 16/10/2026: hạng dùng `IntRow` (`Integer` như `rank` của danh sách), tổng điểm giữ `Row`
  (`BigDecimal`); `profiles`/`explanations` **không** gộp vào `Column` (giữ đúng đường dẫn field R-B3 cho FR-H16).
  `CriterionRow.cells` dựng bằng `Collections.unmodifiableList(new ArrayList<>(…))` vì `List.of()`/`List.copyOf()` ném lỗi
  với phần tử `null`. Ràng buộc không đổi: **một** DTO, **một** method dựng.

### 4.5 Seed

**Không sửa file seed nào** (`seed-test.sql`, `seed-demo-structural.sql`, `seed-demo-ai-output.sql`, `reset-*.sql`,
`db/seed/README.md`) ở H14 `[Q8]`. Không viết điểm AI giả (a29). Dữ liệu cho soát tay (đơn thứ ba, câu hỏi sàng lọc,
rubric đã sửa, lượt chấm thật) được tạo **bằng giao diện** trên DB dev đã nạp seed demo, theo các bước ở mục 7.4, rồi dọn
bằng `reset-demo-db.sql` + nạp lại hai tầng seed demo.

### 4.6 Số query của C1 (cố định, không N+1)

Đường thành công (2–3 đơn hợp lệ) chạy **đúng 13** câu SQL, **không phụ thuộc** số đơn của Job hay số đơn được chọn:

| # | Câu | Nguồn |
|---|---|---|
| 1–2 | Công ty theo chủ, Job theo id | `loadOwnedJob` (a1) |
| 3–11 | 9 câu hàng loạt của `rankApplications` (a40) | `ApplicationOwnerService` — dùng nguyên, một công thức hạng |
| 12 | `resume_parsed_data` theo danh sách `resume_id` của các đơn được chọn | `ResumeParsedDataRepository.findByResumeIdIn` (mới) |
| 13 | `application_screening_answers` của các đơn được chọn | `ApplicationScreeningAnswerRepository` (mới) |

`newerRunInProgress` suy từ dữ liệu câu 6–7 (lượt mới nhất khác lượt DONE mới nhất và có trạng thái `PENDING`/`RUNNING`) —
không thêm query. R-S3/R-S4/R-S5 kiểm trên danh sách đơn đã nạp ở câu 3 — không thêm query. `screening_captured_at`
cũng đọc từ entity đã nạp ở câu 3 (a18) — không thêm query (bản trước có một câu native riêng; bỏ ở Plan Mode 16/10/2026). Đường lỗi dừng sớm hơn nên ít
câu hơn. Lượng dữ liệu đọc tỉ lệ với số đơn của Job (giống danh sách theo Job, a5) — chấp nhận, vì hạng bắt buộc tính trên
toàn Job (R-D3). Con số 13 là **tài liệu thiết kế**, không phải khẳng định cứng của test: T30 chỉ khẳng định số câu
lệnh **không đổi** theo số đơn của Job (không N+1) và in con số đo được trong thông báo của assert (S4). Plan Mode đổi
thiết kế làm đổi con số thì sửa bảng này cùng đợt.

**Plan Mode (đợt 1) đã kiểm 16/10/2026:** cả 13 câu đều qua Hibernate (Spring Data derived/`findAllById`/`@Query`
native); không entity nào trên đường C1 có quan hệ JPA (không lazy-load ẩn); JSON đọc qua `@JdbcTypeCode` cùng câu
SELECT. Yêu cầu gốc: kiểm cả các câu có đi qua Hibernate không — thống kê Hibernate (`getPrepareStatementCount`)
không đếm câu chạy bằng `JdbcTemplate` hay JDBC trực tiếp (tiền lệ trong dự án: `JobEmbedding.java:20` ghi rõ phần vector
dùng `JdbcTemplate` riêng). Nếu có câu không qua Hibernate thì **dừng và báo** cách đếm thay thế, không tự chọn.

## 5. AI

Không dùng AI (R-A1). Không prompt mới, không đổi prompt, không K1–K4. Giải thích FR-H06 và lý giải FR-H04 hiện **nguyên
văn** như ở trang hồ sơ đơn; H14 không sinh, không tóm tắt lại, không cắt ngắn bằng AI.

## 6. Ngoài phạm vi

- **Che thông tin nhân thân / ẩn danh** trên bảng so sánh — FR-H16, tại method dựng DTO duy nhất (R-B1).
- So sánh giữa các Job, giữa các chu kỳ; so sánh > 3 đơn; so sánh đơn chưa chấm xong.
- Chấm lại, tạo lượt chấm, tạo giải thích từ trang so sánh (dùng trang hồ sơ đơn).
- Lưu, chia sẻ, xuất (PDF/CSV), in bảng so sánh; ghi chú trên bảng.
- Tự sắp/đổi chỗ cột; "chỉ hiện hàng khác nhau"; tô theo ô.
- Từ chối / Trúng tuyển / Gửi lại giấy mời từ bảng so sánh (`[Q9]`).
- Giữ lựa chọn ở tab "Ứng viên" trên URL hay qua lần tải lại (R-L3).
- Sửa công thức hạng để tính theo chu kỳ (M1 — câu hỏi mở của FR-H05).
- Tự tải lại định kỳ trang so sánh (R-R1).
- Hiện `gpa`, chứng chỉ, dự án, chức danh hiện tại, ngành, khu vực trên bảng (`[Q10]`).

## 7. Xong khi

### 7.1 Test backend (Testcontainers, full `./mvnw test` xanh)

Lớp mới `ApplicationComparisonControllerIntegrationTest` (HTTP, `@Transactional` cấp lớp được — không khẳng định thứ tự
thời gian giữa các bản ghi; ca cần phân biệt "lượt mới hơn" dùng backdate bằng native update — CLAUDE.md §3c), lớp đơn vị
`CompareSelectionParserTest`, và các lớp có sẵn được kể tên. Không test nào gọi API LLM (không stub nào cần — H14 không
chạm `ChatModel`; mock `ChatModel` mặc định của suite vẫn ném lỗi nếu có ai gọi).

| # | Test | Loại |
|---|---|---|
| T1 | 2 đơn DONE cùng Job, cùng chu kỳ → 200; `columns` đúng thứ tự `ids`; đảo thứ tự `ids` → `columns` đảo theo (R-O1); mỗi cột có `candidateName`, `status`, `appliedAt`, `scoringRunId` = id lượt DONE mới nhất, `scoredAt` = `finished_at` của lượt đó (R-D1); `jobTitle`, `recruitmentCycle` đúng | dương |
| T2 | 3 đơn → 200, 3 cột (biên trên) | dương, biên |
| T3 | **Biên số đơn**: 1 id → 400 "Chọn từ 2 đến 3 đơn để so sánh."; 4 id → 400 cùng câu; 0 id (`ids=` rỗng) và thiếu tham số → 400 "Danh sách đơn cần so sánh không hợp lệ."; 2 và 3 id → 200 (T1, T2) | biên + âm |
| T4 | R-S2: `ids=abc,<uuid>`, `ids=<uuid>,,<uuid>`, `ids=<uuid>,` → 400 `INVALID_COMPARE_SELECTION` "Danh sách đơn cần so sánh không hợp lệ." (body là `ErrorResponse` của dự án, không phải lỗi mặc định Spring) | âm |
| T5 | R-S1 trùng: `ids=a,a` và `ids=a,b,a` → 400 "Mỗi đơn chỉ được chọn một lần." (kể cả khi số id khác nhau = 2) | âm |
| T6 | R-S3: một id không tồn tại; một id thuộc Job khác cùng công ty; một id thuộc công ty khác → **cùng** 400 `INVALID_COMPARE_SELECTION` "Có đơn không thuộc tin tuyển dụng này." (so nguyên body — ba ca giống hệt) | âm |
| T7 | R-S4: hai đơn cùng Job khác `recruitment_cycle` → 400 "Chỉ so sánh được các đơn cùng một chu kỳ tuyển dụng." | âm |
| T8 | R-S5: một đơn chỉ có lượt `FAILED`; một đơn chỉ có lượt `PENDING`; một đơn chưa có lượt nào; một đơn có lượt `RUNNING` đã `finished_at` nhưng `total_score` `NULL` (chờ D3) → 409 `COMPARE_APPLICATION_NOT_SCORED` | âm |
| T9 | R-S7 thứ tự kiểm: `ids` vừa 4 phần tử vừa có đơn khác Job → câu của R-S1 (số lượng); vừa khác chu kỳ vừa chưa chấm → câu R-S4 | âm |
| T10 | Quyền: HR công ty khác → 403; Job không tồn tại → 404 `JOB_NOT_FOUND`; HR chưa có công ty → 404 `COMPANY_NOT_FOUND` (kể cả khi Job không tồn tại); token ứng viên → 403; không token → 401. Đặt trong lớp mới `ApplicationComparisonControllerIntegrationTest` (C1 là endpoint theo Job, không thuộc nhóm `/api/hr/applications/{id}/**` mà `HrApplicationCrossEndpointAccessIntegrationTest` lặp qua) | âm |
| T11 | R-S6 `[Q3]`: đơn `WITHDRAWN` + đơn `PENDING` (cả hai DONE) → 200, `status` đúng từng cột; tương tự `REJECTED`, `HIRED`, `INTERVIEW_INVITED` | dương |
| T12 | Tổng điểm, hạng (R-D2, R-D3): Job có 4 đơn DONE điểm 80, 70, 70, 60 và một đơn chưa chấm → so sánh đơn 70 (thứ hai) và 60 → `rank.values` = [2, 4] — **đúng bằng** `rank` của `GET …/applications` cho hai đơn đó (gọi cả hai endpoint trong test, so từng giá trị); không tính lại thành [1, 2] | dương |
| T13 | Hạng tính trên toàn Job kể cả chu kỳ khác (M1): Job có đơn chu kỳ 1 điểm 90 và hai đơn chu kỳ 2 điểm 80, 70 → so sánh hai đơn chu kỳ 2 → hạng [2, 3] (khớp danh sách) | dương |
| T14 | Lượt DONE mới nhất: đơn có lượt DONE cũ (điểm 50) và lượt DONE mới hơn (70, backdate `created_at` bằng native update) → cột hiện 70, tiêu chí + evidence + giải thích của **lượt 70**; đơn có DONE rồi một lượt `FAILED` mới hơn → vẫn hiện lượt DONE, `newerRunInProgress = false` | dương |
| T15 | `newerRunInProgress` (R-D10): DONE rồi lượt mới `PENDING` → `true`; DONE rồi lượt mới `RUNNING` chưa `finished_at` → `true`; `RUNNING` đã `finished_at`, `total_score` `NULL` → `true`; không lượt mới → `false` | dương + biên |
| T16 | Ghép tiêu chí R-C1: hai lượt cùng rubric nhưng **thứ tự tiêu chí trong snapshot khác nhau** (sửa JSON snapshot bằng native update) → hàng ghép đúng theo `criterionId`, không theo vị trí; thứ tự hàng theo snapshot cột 1 (R-C3) | dương |
| T17 | R-C4: snapshot cột 2 thiếu một tiêu chí, thêm một tiêu chí mới → hàng tiêu chí thiếu có `cells[1] == null`, hàng mới có `cells[0] == null` và nằm cuối; cả hai hàng `rubricVersionsDiffer = true`; gốc `rubricVersionsDiffer = true` | dương |
| T18 | R-C5: cùng `criterionId`, khác trọng số và tên giữa hai snapshot → một hàng, mỗi ô giữ tên/trọng số/điểm tối đa của cột mình, `rubricVersionsDiffer = true`; cùng hết → `false`, gốc `false` | dương |
| T19 | R-C2 dự phòng: tiêu chí snapshot có `criterionId` `null` ở cả hai cột cùng tên → một hàng; `null` một bên → hai hàng | biên |
| T20 | `valuesDiffer` (R-W2): tổng điểm 70.000 và 70.000 → `false`; 70.000 và 70.500 → `true`; tiêu chí điểm 3 và 3 → `false`; 3 và 4 → `true`; kinh nghiệm 24 và 24 tháng → `false`; 24 và `null` → `true` | biên |
| T21 | Kinh nghiệm (R-D5): `experience_months` 30 → `{30, 2.5}`; đã tính nhưng `months` `NULL` → `{null, null}`; chưa tính (bốn cột `NULL`) → `{null, null}`; đơn **không có** dòng `resume_parsed_data` (xoá dòng bằng SQL trong test) → `{null, null}`, `parsedDataAvailable = false`, **vẫn** 200 (không loại đơn) | dương + âm |
| T22 | Kỹ năng/học vấn (R-D6, R-D7, R-D11): chuỗi có dấu và tiếng Anh giữ nguyên văn, đúng thứ tự; mục học vấn có `major` `null` → `EducationItem.major` `null` (không chuỗi "null"); JSON không có khoá `gpa`; đọc theo `resume_id` của đơn chứ không CV chính hiện tại (ứng viên đổi CV chính sau khi nộp → vẫn hiện CV đã nộp) | dương |
| T23 | Giải thích (R-D8, R-W4): lượt có giải thích → `summary`/`strengths`/`weaknesses` đúng; JSON **không** có khoá `metCriteria`/`missingCriteria`; lượt DONE chưa có giải thích, `attempt_count` < max → `status = PENDING`; ≥ max → `FAILED` | dương + biên |
| T24 | Sàng lọc R-Q1/R-Q2: hai đơn trả lời cùng bộ câu → hàng ghép đủ, đúng câu trả lời; HR sửa nội dung câu 2 giữa hai lần nộp (cùng `question_id`) → hai hàng riêng, ô thiếu `NOT_ASKED`; HR đổi thứ tự câu giữa hai lần nộp (`display_order` khác) → vẫn ghép đúng theo `question_id` + nội dung; câu có `question_id` `NULL` → hàng riêng | dương + âm |
| T25 | Sàng lọc R-Q4: đơn `NOT_CAPTURED` + đơn `CAPTURED` → `states` đúng, ô của đơn `NOT_CAPTURED` trạng thái `NOT_CAPTURED`; `NO_QUESTIONS` tương tự; câu không trả lời → `NOT_ANSWERED`; mọi đơn `NOT_CAPTURED` → `rows` rỗng (R-Q5); `YES_NO` "Có"/"Không" khác nhau → `valuesDiffer = true`, `SHORT_TEXT` khác nhau → `false` | dương + biên |
| T26 | R-W3: duyệt toàn bộ cây JSON của response (đệ quy, đọc bằng `ObjectMapper`) → không khoá nào thuộc {`verdict`, `label`, `isQualified`, `passed`, `recommendation`, `winner`, `best`, `top`, `highlight`, `isHighest`, `questionId`, `rawText`, `contact`, `metCriteria`, `missingCriteria`, `gpa`} | âm |
| T27 | R-A1, R-A2: sau một lần gọi C1 — số dòng `scoring_runs`, `criterion_scores`, `score_explanations`, `notifications` không đổi; `ChatModel`/`EmbeddingModel` mock không được gọi (`verifyNoInteractions`) | âm |
| T28 | Danh sách (a8, `[Q7]`): `GET …/applications` có `recruitmentCycle` đúng từng đơn; mọi khẳng định có sẵn của `ApplicationOwnerControllerIntegrationTest`, `ApplicationOwnerServiceTest`, `ApplicationHrDetailControllerIntegrationTest` (E3/E4) xanh **không sửa** | dương |
| T29 | `CompareSelectionParserTest` (đơn vị): `null`, `""`, `" "`, `"a"`, `"<uuid>"`, `"<uuid>,<uuid>"`, `"<uuid>, <uuid>"` (khoảng trắng quanh phần tử — chấp nhận, bỏ khoảng trắng hai đầu), `"<UUID viết hoa>,<uuid>"` (chấp nhận), `"<uuid>,<uuid>,<uuid>,<uuid>"`, trùng hoa/thường cùng UUID → trùng | biên + âm |
| T30 | **Không N+1** (mục 4.6, S4). Không tạo Spring context riêng: bật `spring.jpa.properties.hibernate.generate_statistics: true` trong cấu hình test dùng chung `backend/src/test/resources/application-test.yml` (79 dòng, chưa có khối `spring:` — thêm khối mới sau dòng 79), kèm `logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener: WARN` để test không in thống kê mỗi phiên. Test nằm trong `ApplicationComparisonControllerIntegrationTest`: lấy `Statistics` từ `EntityManagerFactory.unwrap(SessionFactory.class)`, `clear()` → gọi C1 với Job có 3 đơn → đọc `getPrepareStatementCount()` = n3; `clear()` → gọi C1 (cùng 2 đơn được chọn) sau khi thêm vào Job thành 30 đơn → n30. **Khẳng định duy nhất: n3 == n30**; thông báo của assert in cả hai con số (kỳ vọng thiết kế 13, mục 4.6). Không assert cứng 13 | biên |
| T31 | `ids` qua HTTP (R-S2, R-S7): khoảng trắng quanh phần tử (`%20<a>%20,%20<b>`) → 200; UUID viết hoa toàn bộ / lẫn hoa thường → 200, `columns[].applicationId` trả dạng chữ thường; `ids=<a>,<b>,` (dấu phẩy cuối) → 400 "Danh sách đơn cần so sánh không hợp lệ."; `ids=<a>,<a viết hoa>` → 400 "Mỗi đơn chỉ được chọn một lần." | biên + âm |
| T32 | Trạng thái Job (R-S10): cùng hai đơn DONE, đặt Job lần lượt `DRAFT`, `PAUSED`, `CLOSED` (native update) → 200; Job đã xoá mềm (`deleted_at` khác `NULL`) → 200; HR công ty khác với Job `CLOSED` → vẫn 403 | dương + âm |
| T33 | Hoà điểm (R-D3, R-W2): Job có 4 đơn 80, 70, 70, 60 → so sánh **hai đơn 70** → `rank.values` = [2, 2], `rank.valuesDiffer = false`, `totalScore.valuesDiffer = false`; so sánh 80 và một đơn 70 → [1, 2], `true` | biên |
| T34 | Điểm 0 (R-D4): tiêu chí `score = 0` với `evidence = []` → ô có `score` 0 (không `null`), JSON có khoá `"evidence":[]`; cột kia cùng tiêu chí điểm 0 → `valuesDiffer = false`; điểm 0 và 1 → `true` | biên |
| T35 | Trích xuất lại sau khi chấm (R-D12): sau lượt DONE, cập nhật `resume_parsed_data` của đơn (đổi `data.skills`, `experience_months`, như FR-C05 R-R5) → C1 trả kỹ năng/kinh nghiệm **mới**; `criteria[].cells[].evidence`, `totalScore`, `scoredAt` **giữ nguyên** của lượt DONE | dương |

Kiểm tĩnh (đợt cuối, kỳ vọng như ghi):

```bash
rg -n "ChatModel|ChatClient|EmbeddingModel|VectorStore" backend/src/main/java/com/recruitment/comparison   # 0 dong (R-A1)
rg -n -i "verdict|isQualified|passed|recommendation|winner|isHighest|highlight" backend/src/main/java/com/recruitment/comparison   # 0 dong (R-W3)
rg -n "metCriteria|missingCriteria|getGpa|gpa\(" backend/src/main/java/com/recruitment/comparison   # 0 dong (R-W4, Q10)
rg -n "@PostMapping|@PutMapping|@PatchMapping|@DeleteMapping|save\(|saveAndFlush\(" backend/src/main/java/com/recruitment/comparison   # 0 dong (R-A2)
rg -n "import com.recruitment.comparison" backend/src/main/java --glob "!**/comparison/**"   # 0 dong (khong package nao phu thuoc nguoc vao comparison)
```

### 7.2 Kiểm bằng HTTP — Windows PowerShell 5.1

Nạp seed demo (hai tầng, `db/seed/README.md` mục 1) + seed test (mục 8.1). Backend ở `localhost:8080`. Dán cả khối vào
**một** cửa sổ PowerShell. Bộ hàm `Get-Token` / `Invoke-Api` / `Test-Call` chép **nguyên văn** từ FR-H12 mục 7.2 (vốn chép
từ FR-H11): gửi body dạng byte UTF-8, đọc response theo UTF-8 — câu lỗi và tên ứng viên có dấu phải đọc đúng. C1 là
`GET` nên không gửi body; hàm vẫn dùng nguyên để đọc response UTF-8.

```powershell
$base = 'http://localhost:8080'

function Get-Token([string]$Email, [string]$Password) {
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $body).accessToken
}

# Gui body dang byte UTF-8 (PowerShell 5.1 ma hoa chuoi Body theo ISO-8859-1 -> vo chu tieng Viet).
# $Json: $null (khong body) | chuoi (gui nguyen van, dung cho JSON hong) | hashtable (ConvertTo-Json -Depth 8).
# Tra ve doi tuong @{ Status; Text } - Text doc theo UTF-8 (C07 L13).
function Invoke-Api([string]$Method, [string]$Path, [string]$Token, $Json) {
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ UseBasicParsing = $true; Method = $Method; Uri = "$base$Path"; Headers = $headers }
    if ($null -ne $Json) {
        $payload = if ($Json -is [string]) { $Json } else { ConvertTo-Json -InputObject $Json -Depth 8 }
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = [Text.Encoding]::UTF8.GetBytes($payload)
    }
    try {
        $r = Invoke-WebRequest @params
        return @{ Status = [int]$r.StatusCode; Text = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray()) }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return @{ Status = 0; Text = 'KHONG KET NOI DUOC' } }
        $text = $_.ErrorDetails.Message
        if (-not $text) {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8)
            $text = $reader.ReadToEnd()
        }
        return @{ Status = [int]$resp.StatusCode; Text = $text }
    }
}

function Test-Call([string]$Method, [string]$Path, [string]$Token, $Json) {
    $r = Invoke-Api $Method $Path $Token $Json
    '{0} {1} {2} {3}' -f $r.Status, $Method, $Path, $r.Text
}

$tokenDemo = Get-Token 'hr@demo.local' 'Demo1234'
$tokenTest = Get-Token 'minhdathr@gmail.com' '12345678'
$tokenUv   = Get-Token 'tran.minh.hoang@demo.local' 'Demo1234'

$devops = '/api/hr/jobs/d2000000-0000-0000-0000-000000000003/applications/compare'   # Ky su DevOps (demo)
$qa     = '/api/hr/jobs/d2000000-0000-0000-0000-000000000002/applications/compare'   # Ky su Kiem thu (demo)
$java   = '/api/hr/jobs/d2000000-0000-0000-0000-000000000001/applications/compare'   # Senior Java (demo)
$d1 = '31447cc8-8702-442e-8fbc-9dd320afef02'   # DevOps, PENDING, DONE 81.0
$d2 = 'bb83d9cd-cb26-4ef3-9970-1f9b347deba4'   # DevOps, PENDING, DONE 59.5
$q1 = 'a611e169-fa6c-4b6f-a1fd-da36503ae0ac'   # QA, PENDING, DONE 83.5
$q2 = '1029b885-ddae-4417-8e0c-8284d6198f79'   # QA, WITHDRAWN, DONE 0.0
$j1 = '3ec35ae2-c8ae-4100-a819-3b59f6948707'   # Java, PENDING, DONE 67.5
$j2 = '967d5c84-1519-4a70-8bed-6252781ac052'   # Java, PENDING, chi co luot FAILED
$nx = '00000000-0000-0000-0000-000000000000'   # khong ton tai

'--- 1. Duong: 2 don DevOps (mong doi 200), doc lai: thu tu cot theo ids, ten co dau doc dung'
$r = Invoke-Api 'GET' "$devops`?ids=$d1,$d2" $tokenDemo $null
$o = $r.Text | ConvertFrom-Json
'{0} cot={1} cot1={2} cot2={3} hang={4}' -f $r.Status, $o.columns.Count, $o.columns[0].applicationId, $o.columns[1].applicationId, ($o.rank.values -join ',')
'--- 2. Duong: dao thu tu ids (mong doi 200, cot1 = d2)'
$r = Invoke-Api 'GET' "$devops`?ids=$d2,$d1" $tokenDemo $null
'{0} cot1={1}' -f $r.Status, ($r.Text | ConvertFrom-Json).columns[0].applicationId
'--- 3. Duong: don WITHDRAWN (mong doi 200, status WITHDRAWN o cot 2) [Q3]'
$r = Invoke-Api 'GET' "$qa`?ids=$q1,$q2" $tokenDemo $null
'{0} trangthai={1}' -f $r.Status, ((($r.Text | ConvertFrom-Json).columns | ForEach-Object { $_.status }) -join ',')
'--- 4. Am: 1 don, 4 don, trung, rong, sai dinh dang (mong doi 400 INVALID_COMPARE_SELECTION x6)'
Test-Call 'GET' "$devops`?ids=$d1" $tokenDemo $null
Test-Call 'GET' "$devops`?ids=$d1,$d2,$q1,$q2" $tokenDemo $null
Test-Call 'GET' "$devops`?ids=$d1,$d1" $tokenDemo $null
Test-Call 'GET' "$devops`?ids=" $tokenDemo $null
Test-Call 'GET' "$devops" $tokenDemo $null
Test-Call 'GET' "$devops`?ids=abc,$d1" $tokenDemo $null
'--- 5. Am: don cua Job khac, don khong ton tai (mong doi 400, CUNG mot cau x2)'
Test-Call 'GET' "$devops`?ids=$d1,$q1" $tokenDemo $null
Test-Call 'GET' "$devops`?ids=$d1,$nx" $tokenDemo $null
'--- 6. Am: don chua cham xong (mong doi 409 COMPARE_APPLICATION_NOT_SCORED)'
Test-Call 'GET' "$java`?ids=$j1,$j2" $tokenDemo $null
'--- 7. Am: HR cong ty test goi Job demo (403); ung vien (403); khong token (401)'
Test-Call 'GET' "$devops`?ids=$d1,$d2" $tokenTest $null
Test-Call 'GET' "$devops`?ids=$d1,$d2" $tokenUv $null
Test-Call 'GET' "$devops`?ids=$d1,$d2" $null $null
'--- 8. Duong: danh sach co recruitmentCycle [Q7] (mong doi 200, True)'
$r = Invoke-Api 'GET' '/api/hr/jobs/d2000000-0000-0000-0000-000000000003/applications' $tokenDemo $null
'{0} co recruitmentCycle: {1}' -f $r.Status, $r.Text.Contains('"recruitmentCycle":1')
```

Mong đợi: 1 `200 cot=2 cot1=31447cc8-… cot2=bb83d9cd-… hang=…` (hạng đúng bằng cột Hạng của tab "Ứng viên" Job DevOps
trên giao diện); 2 `200 cot1=bb83d9cd-…`; 3 `200 trangthai=PENDING,WITHDRAWN`; 4 sáu dòng `400 … INVALID_COMPARE_SELECTION`
với lần lượt các câu "Chọn từ 2 đến 3 đơn để so sánh." ×2, "Mỗi đơn chỉ được chọn một lần.", "Danh sách đơn cần so sánh
không hợp lệ." ×3 (chữ có dấu đọc đúng — vỡ dấu thì dừng và báo); 5 hai dòng `400` **giống hệt nhau** "Có đơn không thuộc
tin tuyển dụng này."; 6 `409 … COMPARE_APPLICATION_NOT_SCORED`; 7 `403`, `403`, `401 … UNAUTHENTICATED`; 8 `200 … True`.
Không thay đổi dữ liệu nên không cần reset sau khi chạy. **Bắt buộc ở đợt cuối:** chạy thật, dán nguyên văn output; lệnh
sai cú pháp thì dừng, đề xuất sửa mục này, chờ duyệt lại.

### 7.3 Frontend

- `npm run build` + `npm run lint` sạch.
- `rg -n "dangerouslySetInnerHTML" frontend/src` → 0 dòng.
- Trang so sánh không gọi endpoint nào khác ngoài C1 (R-B1):
  `rg -n "getApplicationHrDetail|getApplicationScores|getApplicationExplanation|/resume/parsed|screening-answers" frontend/src/features/comparison frontend/src/pages/HrJobComparePage.tsx` → 0 dòng.
- Không đánh dấu người thắng (R-W1) — chỉ quét file H14 viết mới (UI.md mục 5a):
  `rg -n -i "Math\.max|Math\.min|reduce\(|font-(bold|semibold)|highest|winner|best|m3-tertiary|bg-m3-error|bg-m3-primary-container|m3-warning" frontend/src/features/comparison frontend/src/pages/HrJobComparePage.tsx`
  → 0 dòng (`text-m3-error` **không** nằm trong mẫu: icon `AlertCircle` của trạng thái lỗi dùng nó — UI.md 5d; nền
  `m3-primary-container` của trích dẫn nằm trong `EvidenceQuoteList` ở `features/scoring/`, ngoài phạm vi quét); khớp nhầm
  thì báo cáo đợt liệt kê **từng dòng** kèm lý do, không tự sửa mẫu.
- Không hardcode giới hạn ngoài **một** hằng số: `rg -n "\b3\b" frontend/src/features/comparison/compareLimits.ts` → đúng
  1 dòng khai `MAX_COMPARE = 3`; `ApplicationsTab.tsx` import hằng số đó (không viết số 3 trần cho giới hạn chọn).
- `git diff --stat main -- frontend/src/features/interviewinvitation frontend/src/features/applicationDetail/ApplicationActionBar.tsx frontend/src/components/ui`
  → 0 file (R-H2: dùng nguyên hộp thoại; không sửa thanh thao tác; không sửa `components/ui` — `Checkbox` shadcn đã có
  sẵn ở `frontend/src/components/ui/checkbox.tsx`).
- Không tự tải lại (R-R1), không sắp lại cột (R-O1, R-O2):
  `rg -n "refetchInterval|\.sort\(|toSorted\(|sortBy" frontend/src/features/comparison frontend/src/pages/HrJobComparePage.tsx`
  → 0 dòng.
- Tên file theo `UI.md` mục 5.

### 7.4 Soát tay (desktop ≥ `lg`, 768px, 375px)

Dữ liệu `[Q8]`: DB dev đã nạp **seed demo hai tầng** (`db/seed/README.md` mục 1), **không** sửa file seed nào (mục 4.5).
Backend chạy với khoá Anthropic thật (bước B4 gọi AI thật). Khối 7.2 chạy **trước** phần B (giả định đúng dữ liệu demo
gốc: Job DevOps có 2 đơn) hoặc sau khi dọn dẹp. Ba nhóm theo đúng thứ tự: A (dữ liệu demo gốc) → B (dựng ca 3 cột) → C
(soát trên ca 3 cột) → dọn dẹp.

**A. Trên dữ liệu demo gốc** — tài khoản `hr@demo.local` / `Demo1234`.

| Trạng thái cần thấy | Nơi | Mong đợi |
|---|---|---|
| Ô chọn khoá vì chưa chấm | Tab "Ứng viên" Job Senior Java | Đơn chỉ có lượt `FAILED` (Nguyễn Thị Thu Hà) có ô khoá + câu "Chưa có lượt chấm hoàn tất nên chưa so sánh được."; đơn DONE chọn được; chọn một đơn → nút "So sánh" khoá kèm "Chọn ít nhất 2 đơn để so sánh." |
| Chọn 2, so sánh | Tab "Ứng viên" Job Kỹ sư DevOps → tick đơn 59.5 (Lê Văn Đức) trước, rồi đơn 81.0 (Nguyễn Hải Sơn) → "So sánh" | URL `…/compare?ids=bb83d9cd-…,31447cc8-…`; cột 1 là đơn tick trước (điểm thấp) — không bị sắp theo điểm; không màu/đậm/biểu tượng nào ở cột điểm cao |
| Hàng "Khác nhau" | Trang so sánh ở trên | Hàng Tổng điểm, Hạng tô nền trung tính **cả hàng** + chữ "Khác nhau"; hàng tiêu chí có điểm bằng nhau không tô |
| Evidence | Bấm "Lý giải và trích dẫn" ở một hàng tiêu chí | Mọi ô của hàng cùng mở; lý giải + trích dẫn kiểu FR-H06 (viền trái, nền `m3-primary-container`); ô điểm 0 (nếu có) hiện "Không tìm thấy trích dẫn nào trong CV cho tiêu chí này." |
| Dữ liệu thiếu | Hàng kinh nghiệm/kỹ năng/học vấn | Mục nào thiếu ghi "Chưa có dữ liệu", không ẩn hàng, không ẩn cột |
| Sàng lọc chưa có | Khối "Câu trả lời sàng lọc" | Cả hai cột câu `NOT_CAPTURED` ("Đơn này được nộp trước khi hệ thống ghi nhận câu trả lời sàng lọc…") — a37; không hàng câu hỏi nào (R-Q5) |
| Đơn `WITHDRAWN` | Job Kỹ sư Kiểm thử, chọn 2 đơn | Badge "Đã rút đơn" (bảng màu trung tính), chỉ "Mở hồ sơ", không "Mời phỏng vấn" |
| URL sai | Sửa tay `ids` còn 1 id; thêm id thứ tư; lặp lại một id; thay một id bằng id đơn Job Kiểm thử | Câu lỗi backend đúng R-S8 (lặp id: "Mỗi đơn chỉ được chọn một lần.") + liên kết "Quay lại danh sách ứng viên" về đúng tab; URL **không** bị trang tự sửa (R-S9) |
| Tải lại | F5 trên trang so sánh | Bảng dựng lại đúng thứ tự cột |
| Giữ lựa chọn | Tab "Ứng viên" Job DevOps: tick 2 đơn → đổi "Sắp xếp theo" sang "Ngày nộp" → đổi lại | Hai ô vẫn được chọn, "Đã chọn 2/3" không đổi; bấm "So sánh" ra đúng thứ tự tick (R-L3) |
| Mở rộng hàng | Trang so sánh: mở/đóng "Lý giải và trích dẫn" | URL không đổi (R-U2); hàng Tổng điểm/Hạng không có nút mở rộng (R-D13) |
| Điểm 0 | Job Kỹ sư Kiểm thử, đơn `WITHDRAWN` (tổng 0.00) | Ô tiêu chí 0 điểm mở ra "Không tìm thấy trích dẫn nào trong CV cho tiêu chí này." (R-D4, T34) |

**B. Dựng ca 3 cột** (dữ liệu thật, tạo bằng giao diện; đối chiếu khả thi ở mục 0, a32–a37):

1. **B1 — Câu hỏi sàng lọc.** `hr@demo.local` → `/hr/jobs/d2000000-0000-0000-0000-000000000003/edit?tab=screening` →
   thêm câu `YES_NO` "Bạn có thể bắt đầu làm việc trong vòng 2 tuần không?" và câu `SHORT_TEXT` "Bạn đã vận hành cụm
   Kubernetes ở quy mô bao nhiêu node?" (a32). Hai đơn cũ không đổi (bản chụp bất biến).
2. **B2 — Sửa rubric** (tab "Rubric chấm điểm" cùng Job; rubric đang mở khoá — a35). Làm **đúng thứ tự** để tổng trọng
   số không vượt 100 sau mỗi thao tác (a33):
   - Sửa tiêu chí "Kỹ năng mềm & giao tiếp" (`criterionId` `d4000000-0000-0000-0000-000000000034`): đổi tên thành "Kỹ
     năng giao tiếp & phối hợp xử lý sự cố", trọng số 15 → 10 (tổng 95). Sửa tại chỗ nên **giữ `criterionId`** → ra ca
     R-C5.
   - Thêm tiêu chí mới "Viết tài liệu vận hành", trọng số 5 (tổng 100) → ra ca R-C4.
3. **B3 — Nộp đơn.** Đăng nhập `tran.minh.hoang@demo.local` / `Demo1234` (a36) → `/jobs/d2000000-0000-0000-0000-000000000003/apply`
   → chọn CV hiện có, tick đồng ý AI, trả lời câu `YES_NO` "Có" và câu ngắn "Khoảng 20 node." → nộp.
4. **B4 — Chấm điểm.** Đăng nhập lại `hr@demo.local` → tab "Ứng viên" Job DevOps → đơn của Trần Minh Hoàng → "Chấm
   điểm hồ sơ" (gọi Anthropic thật: 5 tiêu chí + 1 giải thích). Chờ tới khi có Tổng điểm. Lượt `FAILED` (vd
   `EVIDENCE_NOT_VERIFIED`) thì bấm chấm lại; rubric vẫn khoá vì Job đã có `criterion_scores` (a12). Lượt mới chụp rubric
   đã sửa ở B2 và khoá rubric (a34).

Kết quả B: Job DevOps có **3 đơn DONE** cùng chu kỳ 1. Hai đơn cũ (`31447cc8-…` Nguyễn Hải Sơn, `bb83d9cd-…` Lê Văn Đức):
sàng lọc `NOT_CAPTURED`, snapshot rubric **cũ** (4 tiêu chí). Đơn mới (Trần Minh Hoàng): sàng lọc `CAPTURED`, snapshot
**mới** (5 tiêu chí, một tiêu chí đổi tên + trọng số). Nếu bước nào không làm được như trên thì dừng soát tay và báo, không
tự đổi cách dựng dữ liệu.

**C. Trên ca 3 cột** — `hr@demo.local`; tab "Ứng viên" Job DevOps, tick theo thứ tự Nguyễn Hải Sơn → Lê Văn Đức → Trần
Minh Hoàng → "So sánh".

| Trạng thái cần thấy | Nơi | Mong đợi |
|---|---|---|
| Ô chọn thứ 4 | Tab "Ứng viên" sau khi tick 3 đơn | Thanh chọn "Đã chọn 3/3" + "Đã chọn đủ 3 đơn."; Job chỉ có 3 đơn nên kiểm thêm: bỏ một đơn → ô đó mở lại |
| 3 cột | Trang so sánh | Desktop: ba cột dữ liệu chênh rộng không quá 1px; cột tiêu đề hàng 176px (≥ `lg`) / 128px (768px); không có thanh cuộn ngang ở `<body>`; thứ tự cột đúng thứ tự tick; 768px: chữ trong ô xuống dòng; Hạng là hạng toàn Job, có chú thích `[Q2]` |
| Lưu ý phiên bản rubric | Trang so sánh | Khối lưu ý trung tính đầu trang. Hàng "Kỹ năng mềm & giao tiếp" (tên ở cột 1): cột 1–2 trọng số 15%, cột 3 trọng số 10% kèm "Tên trong rubric của đơn: Kỹ năng giao tiếp & phối hợp xử lý sự cố" và câu lưu ý hàng (R-C5). Hàng "Viết tài liệu vận hành" nằm **cuối** nhóm tiêu chí: cột 1–2 "Chưa có dữ liệu" + "Đơn này được chấm theo phiên bản rubric không có tiêu chí này.", cột 3 có điểm, trọng số 5% (R-C3, R-C4) |
| Sàng lọc `CAPTURED` | Khối "Câu trả lời sàng lọc" | Hai hàng câu hỏi theo câu chữ của đơn mới; cột 3 "Có" và "Khoảng 20 node."; cột 1–2 câu `NOT_CAPTURED` **gộp dọc** qua hai hàng; hàng `YES_NO` có "Khác nhau", hàng câu ngắn không (R-W2) |
| Mời phỏng vấn | Cột Trần Minh Hoàng (`PENDING`) → "Mời phỏng vấn" | Đúng hộp thoại FR-H12 (nhiều khung giờ); gửi xong cột đổi badge "Đã mời phỏng vấn", mất nút; cột không đổi chỗ; focus về tên ứng viên cột đó |
| 375px | Trang so sánh 3 cột (và 2 cột ở nhóm A); tab "Ứng viên" | Không cuộn ngang toàn trang; bố cục xếp theo hàng (UI.md 4c); ô chọn có vùng chạm ≥ 48px; thanh chọn nằm trong luồng trang, không `fixed`/`sticky`; cuộn tới cuối bảng thấy đủ dòng cuối |

**Giới hạn đã biết của soát tay:** hai điều kiện sau **không có dữ liệu để soát tay** và frontend không có test tự
động: (1) khoá ô thứ 4 khi đã chọn đủ 3 (R-L1) — sau bước B, Job DevOps chỉ có đúng 3 đơn DONE; (2) khoá ô khác chu kỳ với
đơn chọn đầu (R-L1) — seed demo chỉ có chu kỳ 1. Backend vẫn chặn hai trường hợp này: R-S1 (4 đơn → 400, test **T3**, thứ
tự kiểm ở **T9**) và R-S4 (khác chu kỳ → 400, test **T7**).

**Dọn dẹp (bắt buộc sau khi soát xong):** chạy `db/seed/reset-demo-db.sql`, rồi nạp lại **hai tầng** seed demo theo đúng
thứ tự ở `db/seed/README.md` mục 1 (`seed-demo-structural.sql` → `seed-demo-ai-output.sql`, cách nạp ở mục 2). Kiểm lại
bằng `db/seed/README.md` mục 7.

### 7.5 Truy vết quy tắc → kiểm thử

Mỗi mã R ở mục 3 được kiểm ít nhất một cách. "7.2 nhóm n" = dòng của khối PowerShell; "7.4 A/C" = nhóm soát tay; "7.1 tĩnh"
/ "7.3" = lệnh `rg`/`git diff`. Quy tắc không có cách kiểm tự động ghi lý do ở cột cuối.

| Quy tắc | Test backend (7.1) | HTTP (7.2) | Frontend tĩnh (7.3) / soát tay (7.4) | Ghi chú |
|---|---|---|---|---|
| R-A1 | T27 | — | 7.1 tĩnh (`ChatModel…` 0 dòng) | — |
| R-A2 | T27 | nhóm 1–8 không đổi dữ liệu | 7.1 tĩnh (không `@PostMapping`/`save`) | — |
| R-A3 | T12, T26, T33 | nhóm 1 (hạng khớp danh sách) | 7.3 (`Math.max`/`reduce` 0 dòng) | — |
| R-S1 | T2, T3, T5, T9, T29, T31 | nhóm 4 | 7.4 A "URL sai" | Khoá ô thứ 4 ở frontend: giới hạn đã biết (7.4) |
| R-S2 | T4, T29, T31 | nhóm 4 | 7.4 A "URL sai" | — |
| R-S3 | T6 (âm), T1 (dương) | nhóm 5 | 7.4 A "URL sai" | — |
| R-S4 | T7 (âm), T1, T13 (dương) | — (demo chỉ chu kỳ 1) | — | Khoá ô khác chu kỳ ở frontend: giới hạn đã biết (7.4) |
| R-S5 | T8 (âm), T1, T14, T15 (dương) | nhóm 6 | 7.4 A "Ô chọn khoá vì chưa chấm" | — |
| R-S6 | T11 | nhóm 3 | 7.4 A "Đơn `WITHDRAWN`" | — |
| R-S7 | T9, T10, T31 | nhóm 4, 7 | — | — |
| R-S8 | T3–T8 (so nguyên câu) | nhóm 4–6 | 7.4 A "URL sai" | — |
| R-S9 | — | — | 7.4 A "URL sai" (URL không bị sửa) | Frontend không có test tự động |
| R-S10 | T32 | — | — | — |
| R-O1 | T1 | nhóm 1, 2 | 7.3 (`.sort(` 0 dòng); 7.4 A "Chọn 2" | — |
| R-O2 | — | — | 7.3 (`.sort(`/`sortBy` 0 dòng); 7.4 A | Quy tắc giao diện |
| R-D1 | T1 | nhóm 1 | 7.4 A | — |
| R-D2 | T12, T14 | nhóm 1 | 7.4 A "Hàng Khác nhau" | — |
| R-D3 | T12, T13, T33 | nhóm 1 | 7.4 C "3 cột" | — |
| R-D4 | T14, T16, T34 | — | 7.4 A "Evidence", "Điểm 0" | — |
| R-D5 | T21, T35 | — | 7.4 A "Dữ liệu thiếu" | — |
| R-D6 | T22, T35 | — | 7.4 A "Dữ liệu thiếu" | — |
| R-D7 | T22, T26 (không `gpa`) | — | 7.4 A "Dữ liệu thiếu" | — |
| R-D8 | T23 | — | 7.4 A | — |
| R-D9 | T24, T25 | — | 7.4 A "Sàng lọc chưa có", 7.4 C "Sàng lọc `CAPTURED`" | — |
| R-D10 | T15 | — | — | Không dựng được lượt đang chạy trên demo mà không gọi AI; chỉ kiểm bằng test |
| R-D11 | T22 | — | 7.4 A | — |
| R-D12 | T35 | — | — | Trích xuất lại cần gọi AI; chỉ kiểm bằng test |
| R-D13 | — | — | 7.4 A "Mở rộng hàng" | Quy tắc hiển thị |
| R-C1 | T16 | — | 7.4 C "Lưu ý phiên bản rubric" | — |
| R-C2 | T19 | — | — | Dữ liệu bất thường, không tạo được qua giao diện |
| R-C3 | T16, T17 | — | 7.4 C (hàng mới nằm cuối) | — |
| R-C4 | T17 | — | 7.4 C | — |
| R-C5 | T18 | — | 7.4 C | — |
| R-C6 | T17, T18 | — | 7.4 C (khối lưu ý) | — |
| R-Q1 | T24 | — | 7.4 C | — |
| R-Q2 | T24 | — | — | Cần sửa câu hỏi giữa hai lần nộp có chấm thật; chỉ kiểm bằng test |
| R-Q3 | T24 | — | 7.4 C | — |
| R-Q4 | T25 | — | 7.4 A, C | — |
| R-Q5 | T25 | — | 7.4 A "Sàng lọc chưa có" | — |
| R-Q6 | T26 | — | 7.4 C | — |
| R-W1 | — | — | 7.3 (`Math.max`, `font-bold`… 0 dòng); 7.4 A "Chọn 2" | Quy tắc hiển thị |
| R-W2 | T20, T25, T33, T34 | — | 7.4 A "Hàng Khác nhau", 7.4 C | — |
| R-W3 | T26 | — | 7.1 tĩnh | — |
| R-W4 | T23, T26 | — | 7.1 tĩnh | — |
| R-W5 | T27 (không ghi gì) | — | 7.1 tĩnh (không endpoint ghi) | — |
| R-H1 | — | — | 7.4 A, C | Quy tắc giao diện |
| R-H2 | — | — | 7.3 (`git diff` hộp thoại = 0 file); 7.4 A "Đơn `WITHDRAWN`", 7.4 C "Mời phỏng vấn" | — |
| R-H3 | — | — | 7.4 C "Mời phỏng vấn" | Frontend không có test tự động |
| R-H4 | Test có sẵn của FR-H07 (`ApplicationStatus*Test`, `InterviewInvitationControllerIntegrationTest`) | — | — | Hành vi có sẵn, H14 không đổi |
| R-L1 | T28 (`recruitmentCycle`) | nhóm 8 | 7.4 A "Ô chọn khoá vì chưa chấm" | Ô thứ 4 / khác chu kỳ: giới hạn đã biết (7.4) |
| R-L2 | — | — | 7.4 A | Quy tắc giao diện |
| R-L3 | — | — | 7.4 A "Giữ lựa chọn" | Frontend không có test tự động |
| R-L4 | T28 (danh sách không đổi khẳng định cũ) | — | 7.4 A | — |
| R-U1 | — | — | 7.4 A "Tải lại" | — |
| R-U2 | — | — | 7.4 A "Mở rộng hàng" | — |
| R-U3 | — | — | 7.4 A "URL sai" | — |
| R-R1 | — | — | 7.3 (`refetchInterval` 0 dòng) | — |
| R-R2 | T14 | — | — | — |
| R-B1 | T30 (một endpoint, số query không đổi theo số đơn) | — | 7.3 (không gọi E3/E4/`/resume/parsed`/`/screening-answers`) | — |
| R-B2 | — | — | — | Ngoài phạm vi (FR-H16) |
| R-B3 | T26 (khoá JSON khớp mục 4.4) | — | — | Danh sách cho FR-H16; không có hành vi để kiểm ở H14 |

## 8. AI hay làm sai (dành cho người code)

- Gọi lại AI để "tóm tắt khác biệt", "so sánh ứng viên", hay sinh câu nhận xét giữa các cột; thêm prompt `compare-*.st`
  (R-A1). H14 **không** có AI.
- Tạo lượt chấm mới hoặc gọi `ScoringRunService` khi đơn chưa DONE "cho đủ dữ liệu" (R-S5: phải trả 409).
- Xếp hạng lại trong nhóm được chọn (hạng 1..n của nhóm) hay tính lại tổng điểm từ `criterion_scores` ở H14 — hạng và
  tổng điểm chỉ đến từ `rankApplications` của FR-H05 (R-D2, R-D3, T12).
- Viết một hàm xếp hạng thứ hai (copy `assignRanks`) trong `comparison/` thay vì gọi `ApplicationOwnerService` (a5).
- Sắp cột theo hạng/tổng điểm "cho dễ nhìn", hay đặt cột điểm cao nhất bên trái (R-O1).
- Tô xanh ô cao nhất, đỏ ô thấp nhất, in đậm giá trị lớn nhất, thêm icon cúp/sao/↑, thanh tiến trình so tương đối giữa
  các cột, hay tô **ô** khác biệt thay vì cả **hàng** (R-W1, R-W2).
- Thêm field `winner`, `best`, `isHighest`, `recommendation`… vào DTO; hay `diffFromMax`, `percentOfTop` (R-A3, R-W3).
- Hiện `metCriteria`/`missingCriteria` thành hàng có dấu ✓/✗ theo cột (R-W4).
- Ghép tiêu chí theo vị trí (`index`) hoặc theo `criterion_scores.criterion_id`; ghép câu sàng lọc theo `display_order`
  hay theo `screening_revision` (R-C1, R-Q1, a19, a20).
- Loại hàng tiêu chí/câu hỏi chỉ có ở một đơn, hoặc loại đơn thiếu dữ liệu trích xuất khỏi bảng (R-C4, R-D5, nguyên tắc
  bổ sung "dữ liệu thiếu phải hiện rõ là thiếu").
- Quy đổi điểm về cùng thang khi `maxScore` khác nhau (R-C5).
- Dùng `@RequestParam List<UUID> ids`: UUID sai ra 400 mặc định của Spring, không có `code`/câu tiếng Việt (a31, T4).
- Trả 403/404 riêng cho đơn thuộc công ty khác/không tồn tại trong `ids` — lộ việc đơn có tồn tại (R-S3, T6).
- Đưa tên ứng viên vào câu lỗi (R-S8: FR-H16 sẽ phải che cả câu lỗi).
- Frontend gọi thêm E3/E4/`/resume/parsed`/`/screening-answers` cho từng cột (N+1 và phá điểm che duy nhất — R-B1).
- Frontend tự bỏ id trùng, cắt id thứ tư, hay tự sắp `ids` trước khi gọi API (R-S9).
- Đặt lựa chọn ở tab "Ứng viên" thành điều kiện ẩn hàng, hay đổi sắp xếp/cột/polling hiện có của bảng (R-L4).
- Viết hộp thoại mời mới, sửa `InterviewInvitationDialog`/`ApplicationActionBar`, hay đặt "Từ chối"/"Trúng tuyển"/"Gửi
  lại giấy mời" trên cột (R-H2, `[Q9]`).
- Thêm polling cho trang so sánh (R-R1).
- Dịch, viết hoa/thường lại, cắt ngắn kỹ năng/học vấn/evidence (R-D11).
- Trả `questionId` ra API "để frontend ghép" — ghép ở backend (R-Q1, a21).
- Hiện `gpa` (`[Q10]`).
- Thêm migration "để lưu lượt so sánh" hay cache (R-A2, mục 4.1).
- Gọi repository trong vòng lặp từng đơn/từng cột (N+1) thay vì câu hàng loạt; hay gọi `evaluateApplication` (một đơn)
  nhiều lần — mỗi lần chạy lại toàn bộ `rankApplications` (mục 4.6, T30).
- Lọc Job theo `status = OPEN` hay `deleted_at IS NULL` ở C1 "cho an toàn" — lệch với danh sách theo Job (R-S10, T32).

## 9. Kế hoạch đợt code

Mỗi đợt: code xong → chạy kiểm ở cột Kiểm → dừng → báo cáo diff → chờ duyệt → một commit. Đợt 2–3:
`.\mvnw.cmd test-compile` rồi chỉ các lớp test của đợt bằng `-Dtest`; đợt 4–5: `npm run build` + `npm run lint`; đợt 6:
full `.\mvnw.cmd test` đúng một lần + build + lint.

| Đợt | Nội dung | Kiểm |
|---|---|---|
| 1 | Plan Mode: đối chiếu lại mục 0 trên HEAD lúc đó; chốt tên method trên `ApplicationOwnerService`, hình dạng chính xác DTO mục 4.4; kiểm cả 13 query của mục 4.6 đều qua Hibernate (có câu không qua → dừng, báo cách đếm thay thế); chốt danh sách file | — |
| 2 | Backend nguồn dữ liệu: `ApplicationOwnerService.evaluateApplications` (dùng `rankApplications`) + sửa comment `:128-135` (D7) + sửa comment `ApplicationScreeningAnswerRepository.java:15` (D7), `ApplicationComparisonSource` (record mới), `recruitmentCycle` ở danh sách `[Q7]`, `findByResumeIdIn`, `ApplicationScreeningReader.readMany` + repository; T12, T13, T14 (phần nguồn), T28 | test-compile; `-Dtest` `ApplicationOwner*Test`, `ApplicationHrDetailControllerIntegrationTest`, `ApplicationScreening*Test`, lớp mới |
| 3 | Backend C1: `comparison/` (parser, service, controller, DTO), hai exception + handler; R-S, R-C, R-Q, R-W2, R-D10; T1–T11, T15–T27, T29–T35; bật thống kê Hibernate trong `application-test.yml` (T30, S4) | test-compile; `-Dtest` lớp mới + `HrApplicationCrossEndpointAccessIntegrationTest` |
| 4 | Frontend tab "Ứng viên": cột ô chọn, thanh chọn, điều kiện khoá + câu lý do, hằng `MAX_COMPARE`, kiểu `recruitmentCycle`; route `/hr/jobs/:id/compare` (trang khung gọi C1, trạng thái tải/lỗi) | build + lint |
| 5 | Frontend trang so sánh: bảng desktop/medium, bố cục compact, ô tiêu chí mở rộng evidence (tách `EvidenceQuoteList` khỏi `CriterionScoreBreakdown`, giao diện trang hồ sơ đơn không đổi — UI.md 5a), giải thích, sàng lọc, hàng "Khác nhau", khối lưu ý rubric, nút "Mở hồ sơ"/"Mời phỏng vấn" (hộp thoại có sẵn) | build + lint; lệnh 7.3 |
| 6 | Đợt cuối: full `.\mvnw.cmd test`, khối 7.2 (dán output), kiểm tĩnh 7.1, 7.3, soát tay 7.4, `srs-guard`, `walkthrough`, trạng thái `ĐÃ HOÀN THÀNH`, tài liệu mục 10, tick `docs/ROADMAP.md` | full `mvnw test` (một lần); build + lint; 7.2; 7.3 |

## 10. Tài liệu dùng chung sẽ sửa (ở đợt cuối, không sửa trong commit đặc tả)

| # | File | Thay đổi |
|---|---|---|
| D1 | `docs/SRS.md` | Dòng 41: `Chưa đặc tả` → `Đã hoàn thành`; dòng 106 (bảng chức năng bổ sung): thêm FR-H10, FR-U09 vào cột Phụ thuộc (M3); **giữ tên theo SRS "So sánh 2–3 ứng viên"** ở cả dòng 41 và dòng 106 (đã chốt 15/10/2026), không đổi theo tên README |
| D2 | `docs/ROADMAP.md` (mục `feat/fr-h14-compare`) | Tick + tóm tắt; ghi giới hạn đã biết: hạng là hạng toàn Job mọi chu kỳ (M1 — câu hỏi mở cho FR-H05); kinh nghiệm/kỹ năng theo bản trích xuất hiện tại còn điểm theo lúc chấm (R-D12); lựa chọn ở tab "Ứng viên" không giữ qua tải lại (R-L3); **giới hạn soát tay**: khoá ô thứ 4 và khoá ô khác chu kỳ (R-L1) không có dữ liệu để soát tay và frontend không có test tự động — backend chặn qua R-S1 (T3, T9) và R-S4 (T7) (mục 7.4). **Nợ (S3)**: "Tab Giải thích của FR-H09 (ExplanationTab.tsx/ExplanationReport.tsx) chưa có nhãn Do AI tạo — sửa ở nhánh riêng" |
| D3 | `docs/UI_GUIDE.md` mục 7 | Dòng `/hr/jobs/:id/edit`: bỏ ★ ở `FR-H14` (đã làm), mô tả "Ứng viên (chọn 2–3 đơn → So sánh)" giữ; dòng `★/hr/jobs/:id/compare`: bỏ ★ "Mới" → "Hiện có". Mục 4 dòng "So sánh ứng viên (H14)": thêm "khác biệt tô theo hàng, không theo ô" |
| D4 | `docs/features/README.md` mục 15 | "Nút Mời phỏng vấn mở đúng hộp thoại của FR-H07" → thêm "(chỉ khi đơn Chờ duyệt; hộp thoại nhiều khung giờ của FR-H12)" (M5); "chỉ các đơn … đã chấm xong" giữ; thêm "Trạng thái đơn không giới hạn việc chọn" (`[Q3]`) |
| D5 | `docs/features/HR/H16/REQUIREMENT.md` (khung) | Ghi chú một dòng: "Bảng so sánh H14 dựng ở `ApplicationComparisonService.compare` — điểm che duy nhất (H14 R-B1)" |
| D6 | `CLAUDE.md` §3 | Thêm `comparison/` (FR-H14) vào danh sách package `[Q11]` |
| D7 | `ApplicationOwnerService.java:128-135` (comment code, **sửa ở đợt 2**, cùng lúc thêm `evaluateApplications` — không chờ đợt cuối) | Comment ghi "10 query (2 ownership + 8)" nhưng liệt kê và chạy 9 câu trong `rankApplications`, tổng 11 (a40) — sửa thành "11 query (2 ownership + 9)". Chỉ sửa comment, không đổi code. **Ý thứ hai (cũng sửa ở đợt 2):** comment `ApplicationScreeningAnswerRepository.java:15` ("KHONG them field vao entity JobApplication") đã cũ, vì entity đã map `screening_captured_at` từ FR-U09 (a18) — sửa comment cho đúng, **không** đổi code `findScreeningCapturedAt` (FR-H10 vẫn dùng) |

## 11. Quyết định — Đã chốt 15/10/2026

Mọi câu đã chốt 15/10/2026. Q1–Q7, Q9–Q11 chốt đúng phương án đã đề xuất; Q8 chốt phương án (a) có mở rộng. Phần thân
viết theo cột "Đã chốt"; cột "Phương án đã loại / đánh đổi" giữ lại để tra lý do.

| # | Câu hỏi | Đã chốt | Phương án đã loại / đánh đổi | Mục bị ảnh hưởng |
|---|---|---|---|---|
| Q1 | Thứ tự cột theo thứ tự HR chọn hay theo hạng? | **Đã chốt:** **Thứ tự HR tick** (= thứ tự `ids` trên URL). Không ngầm gợi ý "cột trái là người tốt nhất"; HR tự kiểm soát; URL tái lập đúng bảng | (b) Theo hạng/tổng điểm giảm dần: dễ đọc nhưng chính là "sắp xếp lại cột để ngầm gợi ý" — trái ràng buộc của nhiệm vụ. (c) Theo ngày nộp tăng dần: trung tính, xác định, nhưng HR không đổi được thứ tự và thứ tự trên URL mất ý nghĩa | R-O1, R-O2, 2.4, T1, 7.4 |
| Q2 | Có hiện hàng hạng / tổng điểm không? | **Đã chốt:** **Hiện cả hai**, đúng giá trị FR-H05 (một nguồn), số trung tính như cột Hạng/Tổng điểm của danh sách, kèm chú thích "Hạng trong toàn bộ đơn của tin này" (M1) | (b) Chỉ tổng điểm, bỏ hạng: tránh hiểu nhầm "hạng toàn Job ≠ hạng trong nhóm", nhưng lệch với danh sách và trang hồ sơ đơn (đều hiện hạng). (c) Bỏ cả hai, chỉ điểm tiêu chí: giảm cảm giác "bảng xếp hạng", nhưng HR mất con số chính của FR-H05 và phải mở danh sách song song | R-D2, R-D3, R-W2, T12, T13, UI.md 4 |
| Q3 | Đơn `REJECTED`/`WITHDRAWN` có được chọn không? | **Đã chốt:** **Được** (cả 5 trạng thái). Dữ liệu vẫn giữ (soft state, FR-U06), danh sách vẫn hiện các đơn này; trạng thái chỉ quyết định nút hành động. Hữu ích khi HR muốn so lại với người đã từ chối/rút | (b) Chỉ `PENDING` + `INTERVIEW_INVITED`: bảng gọn quanh quyết định đang mở; thêm một lỗi 400 và một lý do khoá ô — nhưng đơn bị loại khỏi so sánh chỉ vì trạng thái gần với "lọc ứng viên". (c) Cho chọn nhưng mặc định ẩn: thêm trạng thái giao diện, ít giá trị | R-S6, R-L1, T11, 7.2 nhóm 3, D4 |
| Q4 | Ghép tiêu chí khi `rubric_snapshot` khác nhau? | **Đã chốt:** **Ghép theo `criterionId` của snapshot**; tiêu chí thiếu ở một cột vẫn hiện hàng với "Chưa có dữ liệu"; khác tên/trọng số/điểm tối đa → mỗi ô giữ giá trị của mình + câu lưu ý hàng + khối lưu ý đầu trang. Theo luồng API trường hợp này không xảy ra (a13), nhưng seed demo cho phép | (b) Chặn so sánh (409 `COMPARE_RUBRIC_MISMATCH`) khi snapshot khác: đơn giản, không bao giờ hiện bảng lệch; nhưng loại đơn vì lý do kỹ thuật HR không sửa được. (c) Ghép theo tên: hỏng khi HR đổi tên tiêu chí; (d) theo vị trí: sai hẳn khi đổi thứ tự | R-C1–R-C6, T16–T19, UI.md 4a |
| Q5 | Ghép câu hỏi sàng lọc khi revision khác nhau? | **Đã chốt:** **Một hàng khi cùng `question_id` và cùng nội dung bản chụp** (loại, câu chữ, phương án); khác nội dung → hai hàng, ô thiếu "Không có câu hỏi này lúc ứng viên nộp đơn." `screening_revision` không lưu theo đơn (a19) nên không dùng được | (b) Chỉ theo `question_id`, hiện câu chữ của cột 1: gọn hơn nhưng có thể ghép hai câu đã bị sửa nghĩa (a20) — câu trả lời đặt dưới câu hỏi ứng viên không hề thấy. (c) Không ghép: mỗi cột tự liệt kê danh sách câu hỏi của mình: đúng tuyệt đối nhưng mất tác dụng so sánh theo hàng | R-Q1–R-Q6, T24, T25 |
| Q6 | Đơn có lượt chấm mới đang chạy? | **Đã chốt:** **Vẫn so sánh**, hiện lượt DONE mới nhất (như danh sách), đầu cột ghi chú trung tính "Đang có lượt chấm mới; bảng hiện kết quả lượt hoàn tất lúc {t}." Không tự tải lại; HR bấm "Tải lại" | (b) Chặn (409) cho tới khi lượt mới xong: không bao giờ hiện số "cũ", nhưng HR bị khoá vì thao tác của chính mình, và ô chọn ở danh sách phải thêm điều kiện. (c) Tự polling như danh sách: tốn request, bảng tự thay số khi HR đang đọc | R-D10, R-R1, R-R2, T15, UI.md 6 |
| Q7 | Giới hạn chọn ở `ApplicationsTab` | **Đã chốt:** **Khoá ô thứ 4** (khi đã 3 đơn, ô chưa chọn khoá kèm câu "Đã chọn đủ 3 đơn."); ô của đơn chưa chấm xong và đơn khác chu kỳ với đơn chọn đầu cũng khoá kèm lý do. Để biết chu kỳ, danh sách thêm field đọc `recruitmentCycle` (thay đổi cộng thêm của endpoint FR-H05) | (b) Cho tick thoải mái, báo lỗi khi bấm "So sánh": không đổi DTO danh sách, nhưng HR chỉ biết sai sau khi bấm. (c) Không thêm `recruitmentCycle`, chỉ để backend chặn khác chu kỳ (R-S4): ít đổi code nhất; HR gặp lỗi khi Job đã mở lại nhiều chu kỳ | R-L1, R-L2, mục 4.2, 4.3, T28, 7.2 nhóm 8, UI.md 4d |
| Q8 | Dữ liệu soát tay (M2)? | **Đã chốt: (a) có mở rộng** — seed demo + dữ liệu tạo bằng giao diện + một lượt chấm thật; **không sửa file seed nào**. Trên DB dev đã nạp demo: (1) HR `hr@demo.local` thêm vào Job Kỹ sư DevOps một câu `YES_NO` và một câu `SHORT_TEXT`; (2) sửa rubric Job DevOps: đổi tên một tiêu chí có sẵn (giữ `criterionId` → ca R-C5) và thêm một tiêu chí mới, cân lại trọng số (→ ca R-C4); (3) `tran.minh.hoang@demo.local` trả lời sàng lọc và nộp đơn; (4) HR chấm đơn mới. Kết quả: Job DevOps có 3 đơn DONE — hai đơn cũ `NOT_CAPTURED`, snapshot rubric cũ; đơn mới `CAPTURED`, snapshot mới. Khả thi đã đối chiếu (a32–a37). Dọn: `reset-demo-db.sql` + nạp lại hai tầng seed demo | (b) Thêm vào `seed-test.sql` lượt DONE + `criterion_scores` viết tay: soát được mọi ca không tốn API — nhưng **trái** quy ước "bộ test không có điểm AI viết tay hay làm giả" (`db/seed/README.md` mục 8). (c) Chỉ soát 2 cột bằng demo, ca 3 cột/sàng lọc/lệch rubric chỉ kiểm bằng test: không tốn API, nhưng bố cục 3 cột ở 375px không được nhìn tận mắt. Đánh đổi của (a): tốn khoảng 6 lượt gọi Anthropic mỗi lần dựng; lượt chấm có thể `FAILED` (`EVIDENCE_NOT_VERIFIED`) và phải chấm lại; phải dọn DB sau khi soát | 7.4, 4.5, a32–a37 |
| Q9 | Đơn `INTERVIEW_INVITED`: có nút "Gửi lại giấy mời" trên cột? | **Đã chốt:** **Không**; chỉ "Mở hồ sơ" (gửi lại ở tab "Giấy mời phỏng vấn" của hồ sơ đơn). Cột chỉ có hành động "mời" theo đúng README; tránh gọi thêm I1 cho mỗi cột | (b) Có, mở `InterviewInvitationDialog` `mode='resend'`: cần giấy mời hiệu lực (I1) cho cột → gọi thêm endpoint, phá R-B1 hoặc phải nhét giấy mời vào DTO so sánh | R-H2, M5, 6 |
| Q10 | Học vấn có hiện `gpa` không? Có thêm chức danh hiện tại/ngành/chứng chỉ? | **Đã chốt:** **Không `gpa`** (con số dễ thành tiêu chí lọc ngầm ngoài rubric); không thêm hàng nào ngoài README (điểm, tiêu chí, kinh nghiệm, kỹ năng, học vấn, giải thích, sàng lọc) | (b) Hiện `gpa` nguyên văn như tab CV đã trích xuất: đầy đủ hơn, nhưng đặt cạnh nhau theo cột thì thành phép so điểm GPA. (c) Thêm chứng chỉ/dự án: bảng dài, ngoài mô tả README | R-D7, mục 6, T22, T26 |
| Q11 | Package backend | **Đã chốt:** **Package mới `comparison/`**, sửa CLAUDE.md §3 ở đợt cuối | (b) Đặt trong `jobapplication/`: không thêm package, nhưng `jobapplication/` phải import `screening/` (hiện `screening/` import `jobapplication/` — `ApplicationScreeningHrService.java:3`) → phụ thuộc hai chiều | 4.2, D6, kiểm tĩnh 7.1 |

**Năm điểm quyết trong thân — đã chốt 15/10/2026:**
- Endpoint là `GET` một lần (C1), `ids` dạng chuỗi phân tách dấu phẩy (R-U1, a31).
- Khác biệt tô **theo hàng**, cờ do backend tính (R-W2).
- Không hiện `metCriteria`/`missingCriteria` (R-W4).
- Bố cục 375px xếp theo hàng, không bảng cuộn ngang (UI.md 4c).
- Mã lỗi: một mã 400 `INVALID_COMPARE_SELECTION` cho mọi lỗi lựa chọn + 409 `COMPARE_APPLICATION_NOT_SCORED` (R-S8).

### 11.1 Chốt sau spec-review 15/10/2026 (S1–S6)

| # | Điểm | Đã chốt | Lý do / đánh đổi | Mục áp dụng |
|---|---|---|---|---|
| S1 | Cách hiện kinh nghiệm lệch FR-C05 (`C05/UI.md:190`, `:220`) | **Giữ cách đơn giản của R-D5**: mọi trường hợp thiếu đều hiện "Chưa có dữ liệu"; không dòng cơ sở, không "Đang tính…"; DTO `Experience(months, years)` không đổi. Cố ý khác FR-C05 | Trang so sánh không tự tải lại (nên "Đang tính…" không tự đổi); dòng cơ sở làm ô dài thêm ở cả 3 cột; HR xem chi tiết qua "Mở hồ sơ". Đánh đổi: HR không phân biệt được "chưa tính" với "không đọc được mốc" ngay trên bảng | R-D5, mục 4.4 |
| S2 | Hàng Tổng điểm/Hạng không tự mở evidence | **Chấp nhận**: evidence của tổng điểm và hạng là evidence các hàng tiêu chí trên cùng trang (R-D13), giống danh sách theo Job; coi yêu cầu CLAUDE.md §8 / UI_GUIDE mục 4 là đã đáp ứng | Thêm nút mở rộng riêng cho hàng tổng điểm sẽ lặp lại đúng nội dung các hàng tiêu chí | R-D13 |
| S3 | Nhãn "Do AI tạo" cho giải thích FR-H06 | **Giữ ở H14** đúng UI_GUIDE mục 4. Không sửa H09 ở nhánh này; ghi nợ ở D2 | Hai màn hình tạm thời không đồng nhất (tab Giải thích của H09 chưa có nhãn) cho tới khi nhánh riêng sửa | UI.md 4a, 5d; D2 |
| S4 | Cách chạy T30 (đếm query) | **Người dùng chốt 15/10/2026:** không tạo Spring context riêng; bật `hibernate.generate_statistics` trong `backend/src/test/resources/application-test.yml` dùng chung (thêm khối `spring:` sau dòng 79), tắt log thống kê bằng mức `WARN` cho `StatisticalLoggingSessionEventListener`. T30 chỉ khẳng định số câu lệnh khi Job có 3 đơn **bằng** khi Job có 30 đơn; con số 13 (14 trước Plan Mode 16/10/2026) chỉ in trong thông báo assert và giữ làm tài liệu ở 4.6. Plan Mode kiểm cả 13 câu qua Hibernate; có câu không qua thì dừng và báo | Lớp riêng có `@SpringBootTest(properties=…)`: tách context, làm full suite chậm hơn. Assert cứng số câu: vỡ mỗi khi Plan Mode/đợt sau đổi số câu dù không có N+1. Đánh đổi của cách đã chốt: thống kê Hibernate bật cho mọi test (chi phí nhỏ, không đổi hành vi) | 4.6, T30, 7.5, 9 |
| S5 | Chú thích hàng Hạng | **Người dùng chốt 15/10/2026:** hiện sẵn thành chữ ngay dưới nhãn "Hạng", không dùng tooltip | Tooltip không mở được bằng chạm và trình đọc màn hình dễ bỏ qua; đánh đổi: hàng Hạng cao hơn một dòng | UI.md 4a, mục 7 |
| S6 | So sánh trên Job đã xoá mềm (R-S10) | **Người dùng chốt 15/10/2026:** Job đã xoá mềm (và Job `DRAFT`/`PAUSED`/`CLOSED`) vẫn so sánh được, cho khớp danh sách theo Job hiện có (a39). Muốn chặn Job đã xoá mềm thì thuộc FR-H05 (chặn cả danh sách), không làm ở H14 | Chặn riêng ở C1: lệch với danh sách — HR thấy đơn ở tab "Ứng viên" nhưng bấm "So sánh" lại bị chặn | R-S10, T32 |
