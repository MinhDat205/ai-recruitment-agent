# FR-H16 — Chế độ ẩn danh

> Trạng thái: ĐÃ DUYỆT 18/10/2026
>
> Làm rõ sau Plan Mode 19/10/2026: L1–L4 (L1: R-O1, R-O2, 7.2 nhóm 7; L2: R-O2; L3: R-B3, mục 4.2; L4: R-A4, mục 4.2,
> 7.3). Làm rõ trong đợt 2 (19/10/2026): L5 (R-B3). Làm rõ trong
> đợt 3a (19/10/2026): L6 (mục 4.2). Làm rõ trong đợt 4a (19/10/2026): L7 (R-T1) — chi tiết ở mục 11. Mục 11 thắng chỗ viết cũ nếu lệch.

- Nhóm: HR
- Tóm tắt: HR bật/tắt chế độ ẩn danh cho từng Job. Khi một đơn của Job đó đang ẩn danh, **backend** không trả họ tên,
  liên hệ, tên tệp, CV gốc và thư giới thiệu cho HR; nội dung văn bản được thay chuỗi theo các giá trị danh tính đã biết;
  tên hiển thị là nhãn ổn định "Ứng viên #XXXXXXXX". Đơn tự bỏ ẩn danh khi HR gửi giấy mời phỏng vấn. **Không gọi AI
  mới, không đổi đầu vào chấm điểm, không đổi điểm hay hạng.**
- Phụ thuộc (SRS `docs/SRS.md:110`): FR-H09, FR-H14, FR-C08 — **Đã hoàn thành** (`docs/SRS.md:37`, `:41`, `:28`).
  Phụ thuộc thực tế thêm (ROADMAP `docs/ROADMAP.md:1104-1107`, đối chiếu mục 0): FR-C03, FR-C06, FR-C07, FR-H08 (màn
  `/hr/candidates`), FR-H10, FR-H12, FR-H15 — đều **Đã hoàn thành** (`docs/SRS.md:23`, `:26`, `:27`, `:36`, `:38`,
  `:40`, `:42`).
- Nhánh: `feat/fr-h16-blind`.
- Ghi đè: FR-H09 R-V1 (tải CV gốc) và R-L (thư giới thiệu) khi đơn đang ẩn danh (`docs/ROADMAP.md:1108-1110`).

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

Đối chiếu trên `feat/fr-h16-blind` ngày 18/10/2026 (cây làm việc chỉ có `_survey.md` chưa theo dõi). Nguồn khảo sát:
`_survey.md` (22 bề mặt, 7 câu hỏi mở) — mọi mục dùng dưới đây đã mở lại đúng file:dòng; chỗ survey sai ghi ở (c).

### (a) Hiện trạng

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | DTO HR có `candidateName` | 7 DTO: danh sách theo Job, tìm kiếm `/hr/candidates`, E1, cột so sánh, mục kho (P8), hộp thư HR, preview giấy mời | `ApplicationHrListItemResponse.java:30`, `ApplicationSearchItemResponse.java:32`, `ApplicationHrDetailResponse.java:19`, `ApplicationComparisonResponse.java:44`, `TalentPoolEntryResponse.java:11`, `ConversationHrResponse.java:9`, `InterviewInvitationPreviewResponse.java:7` |
| a2 | Field danh tính khác trả cho HR | E1 `resumeFileName`, `coverLetter`; E2 `data.contact` (`fullName`, `email`, `phone`, `address`, `linkedin`); tin nhắn `attachment.fileName` | `ApplicationHrDetailResponse.java:22`, `:25`; `ResumeParsedPayload.java:54`; `MessageResponse.java:17` |
| a3 | Không HR DTO nào trả avatar, ngày sinh, email/SĐT tài khoản ứng viên | Đúng. `users.phone`, `users.avatar_url`, `candidate_profiles.date_of_birth` chỉ có ở schema và phía chủ tài khoản | `V1__init_schema.sql:14-38`; grep DTO dưới `backend/src/main/java` |
| a4 | Tải CV gốc phía HR | `GET /api/hr/applications/{id}/resume/download`, tên tệp ở `Content-Disposition` | `ResumeHrController.java:32-42` |
| a5 | Tên tệp CV builder chứa họ tên | `"CV " + fullName + ".pdf"` | `CvBuilderService.java:49` (comment), `:50-51` (hằng `FILE_NAME_PREFIX`/`SUFFIX`), `:287-290` (`fileName(..)`), gọi ở `:128` |
| a6 | Placeholder giấy mời | `{{candidateName}}`, `{{jobTitle}}`, `{{companyName}}`; preview render họ tên thật; gửi lưu **nguyên văn** subject/content HR gửi lên, không render lại | `InterviewInvitationService.java:58-60`, `:97-121`, `:124-145`, `:367-371`; `InterviewInvitationSendRequest.java` (`@Size(max = 255)` subject, `10000` content) |
| a7 | Gửi giấy mời đổi trạng thái qua đúng một đường | `sendInvitation` → `ApplicationStatusService.changeStatus(…, INTERVIEW_INVITED)` → ghi `application_status_history` trong cùng transaction | `InterviewInvitationService.java:139`; `ApplicationStatusService.java:83` |
| a8 | Tiền lệ "đơn đã từng được mời" | Có dòng `application_status_history` với `to_status = 'INTERVIEW_INVITED'`: `JobRepository` dùng `LEFT JOIN LATERAL (SELECT 1 … LIMIT 1)`, `JobApplicationRepository` dùng `EXISTS` | `JobRepository.java:346-349`; `JobApplicationRepository.java:127-129` |
| a9 | Ghi chú lịch sử trạng thái | Đúng **3** lời gọi `ApplicationStatusRecorder.record(…)`, cả 3 truyền `note = null`; không đường nào cho HR nhập note | `ApplicationService.java:115`, `:143`; `ApplicationStatusService.java:83`; `ApplicationStatusRecorder.java:25-35` |
| a10 | Thông báo gửi HR ghép họ tên ứng viên lúc phát sinh sự kiện, lưu cứng vào `notifications.body` | 4 loại: nộp đơn, rút đơn, tin nhắn mới (kèm đoạn trích), ứng viên chọn khung giờ | `NotificationContentBuilder.java:52-63`, `:76-93`, `:116-129`; `NotificationEventListener.java:89-138`, `:161-185` |
| a11 | Email gửi HR = `body` (hoặc `email_body`) của thông báo | Đúng | `NotificationMailOrchestrator.java:62-70` |
| a12 | Đoạn trích tin nhắn cắt ở Java từ thân tin đầy đủ | `MessageExcerpt.of(body)` ở hộp thư (`:147`, `:166`) và ở sự kiện gửi (`:249`); sự kiện mang đoạn trích đã cắt (không có thân đầy đủ) và cờ `hasAttachment`; `excerpt = null` khi tin chỉ có tệp | `MessageService.java:147`, `:166`, `:249`; `MessageSentEvent.java:8-9` |
| a13 | Bong bóng tin nhắn phía HR không hiện họ tên | Nhãn "Ứng viên" | `MessageBubble.tsx:9-11` (`counterpartLabel`), `:42` |
| a14 | C07: K1 nạp họ tên ứng viên, prompt bảo AI gọi theo họ tên | `ConversationContext.candidateName` ← `candidate.getFullName()`; prompt dòng 4, 34, 35 | `ConversationContext.java` (record); `ConversationContextAssembler.java:90-98` (họ tên `:92`); `MessageDraftService.java:31`, `:100`; `message-draft-v1.st:4`, `:34-35` |
| a15 | C08: K1 không chứa họ tên; câu trả lời và trích dẫn có thể chứa họ tên vì lấy từ `raw_text` | Đúng | `CvQaContext.java` (comment đầu record); `CvQaResponse.java:13-21` |
| a16 | D2 chấm trên `raw_text` nguyên văn | `criterionScoringService.score(criterion, rawText)` | `ScoringRunOrchestrator.java:69-94` |
| a17 | D4 chỉ đọc `criterion_scores` (tên tiêu chí, trọng số, điểm, `reasoning`) | Không đọc `raw_text`/`data` | `ScoreExplanationOrchestrator.java:66-95` |
| a18 | Danh sách `/hr/candidates` không có lọc/sắp theo tên | Controller map `/api/hr/candidates`; tham số: `jobId`, `status`, khoảng điểm, tiêu chí, `page`, `size` (mặc định 10, tối đa 50) | `ApplicationSearchController.java:31-38` |
| a19 | Màn hình CV đã trích xuất khi `contact` null | Hiện "Không trích xuất được thông tin liên hệ" — sai nghĩa khi ẩn danh | `ResumeParsedDataView.tsx:34-36` |
| a20 | Trang sửa Job | 5 tab, tab "Thông tin tin tuyển dụng" là form `PUT /hr/jobs/{id}` (`JobRequest`) | `HrJobEditPage.tsx:54`, `:99`, `:500-504`; `JobOwnerController.java:59`, `:66` |
| a21 | `components/ui` không có Switch | Có `checkbox`, `dialog`, `button`, `card`… | `frontend/src/components/ui/` |
| a22 | Migration lớn nhất | `V18__cv_builder_documents.sql` ⇒ file mới **`V19__blind_review.sql`** | `ls … \| sort -V` ngày 18/10/2026 |
| a23 | Seed test có đơn đã mời kèm lịch sử `INTERVIEW_INVITED` | A2, A4, A9 có dòng lịch sử | `db/seed/seed-test.sql:412` (A4), `:429` (A9), `:434` (A2) |
| a24 | E2 phía HR: hình dạng response | Không có `rawText`/`model`/`promptVersion`. Ngoài `data` còn hai chuỗi văn bản CV là **bản sao** của payload: `currentTitle` ← `payload.currentTitle()`, `locationText` ← `payload.locationText()`; `industry`/`location` là mã + nhãn danh mục. Mapper dùng chung hai phía | `ResumeParsedDataResponse.java:9-23`; `ResumeParsedDataResponseMapper.java:21-33`; `ResumeHrService.java:93-98` |
| a25 | Endpoint dùng ở 7.2 nhóm 6, 7, 9 | Gửi tin hai phía: `POST`, `@RequestParam` `body` (+ `file`), không khai `consumes` — nhận `application/x-www-form-urlencoded`, trả 201 `MessageResponse`. Đọc tin hai phía: `GET` → `MessageThreadResponse(canSend, olderMessagesHidden, unreadCount, messages)`, `messages` cũ trước mới sau. Hộp thư HR: `PageResponse` (`items`), `size` mặc định 20, tối đa 50 (hằng ở `MessageService.java:54-55`, không ở controller). Thông báo: `NotificationPageResponse(page, unreadCount)`, `page.items` mới trước. Gửi giấy mời: `POST …/interview-invitation`, JSON `@Valid InterviewInvitationSendRequest(slots[{scheduledAt, location}], subject, content)`, 201. Danh sách giấy mời: `InterviewInvitationListResponse(applicationId, maxSlots, invitations)` | `MessageHrController.java:34-50`; `MessageCandidateController.java:35-50`; `MessageThreadResponse.java:6-10`; `MessageService.java:54-55`, `:185-187`, `:193-206` (`buildThread`); `ConversationHrController.java:24-30`; `NotificationController.java:27-34`; `NotificationPageResponse.java:8`; `NotificationRepository.java:14`; `InterviewInvitationController.java:44-52`, `:66-70`; `InterviewInvitationSendRequest.java`; `InterviewInvitationListResponse.java:8-9`; `PageResponse.java:7` |

### (b) Quyết định

Mọi dòng Q1–Q12 (kèm R-C2 của C07) **đã chốt 18/10/2026**.

Quy tắc đã có ở SRS/README được **giữ nguyên, không chọn lại**: bật theo Job; che ở backend bằng bộ che dùng chung;
thay đúng các giá trị liên hệ đã biết; không dùng AI nhận diện nhân thân (`docs/SRS.md:194`); không đổi đầu vào chấm
điểm (`docs/SRS.md:193`); tự bỏ ẩn danh khi gửi giấy mời; ghi người và thời điểm bật/tắt; Lưu vào kho chỉ mở sau khi bỏ
ẩn danh; không ẩn danh email/tin nhắn gửi ứng viên (`docs/features/README.md:809-845`).

| # | Câu hỏi | Khuyến nghị | Lý do | Đánh đổi |
|---|---|---|---|---|
| Q1 | (survey 1) Evidence, `reasoning`, giải thích, trích dẫn C08 có thể chứa danh tính | **Đã chốt 18/10/2026.** Thay chuỗi bằng bộ che R-M, áp **sau** K2 và chỉ ở tầng dựng response HR; DB giữ nguyên | Đúng phương án README; không đụng D2/K2 | Không bảo đảm che hết (mục 6b) |
| Q2 | (survey 2) Nội dung tự do ứng viên nhập | **Đã chốt 18/10/2026.** Thư giới thiệu: **ẩn toàn bộ** (R-V3, theo ghi đè H09 R-L). Câu trả lời sàng lọc, tin nhắn: thay chuỗi | Thư giới thiệu là đoạn tự giới thiệu dài, rủi ro cao, không phải dữ liệu đánh giá; câu trả lời sàng lọc là dữ liệu HR cần để đánh giá | Mất nội dung thư giới thiệu trong giai đoạn ẩn danh; câu trả lời sàng lọc vẫn có thể lọt danh tính viết khác dạng |
| Q3 | (survey 3) Tên tệp và tệp | **Đã chốt 18/10/2026.** CV gốc: 409 khi tải; `resumeFileName` = null. Tệp **ứng viên** gửi trong tin nhắn: `fileName` = null, tải 409. Tệp HR gửi: giữ nguyên | Tệp ứng viên gửi có thể chính là CV — để mở thì R-V1 vô nghĩa | HR không xem được giấy tờ đã đề nghị bổ sung cho tới khi bỏ ẩn danh |
| Q4 | (survey 4) Màn hình gộp nhiều Job | **Đã chốt 18/10/2026.** Che **theo từng dòng**: mỗi đơn xét theo Job của chính nó (R-S1) | Một định nghĩa duy nhất, không phụ thuộc màn hình | Một màn có thể lẫn tên thật và nhãn |
| Q5 | (survey 5) Kho ứng viên | **Đã chốt 18/10/2026.** P8 che theo dòng như Q4; P7 **thêm** vào kho khi đơn đang ẩn danh → 409; gỡ luôn được | README "Lưu vào kho chỉ mở sau khi bỏ ẩn danh" phải chốt ở backend | Đơn đã lưu từ trước rồi Job mới bật ẩn danh vẫn ở kho, hiện nhãn |
| Q6 | (survey 6) Preview giấy mời lộ tên | **Đã chốt 18/10/2026.** Đơn đang ẩn danh: preview **giữ nguyên chữ** `{{candidateName}}`, `candidateName` = nhãn; lúc gửi backend thay `{{candidateName}}` bằng họ tên thật rồi mới lưu | HR không thấy tên trước khi bấm gửi; ứng viên vẫn nhận thư đúng tên | Đổi hành vi "lưu nguyên văn" của H12 cho riêng trường hợp đơn đang ẩn danh; độ dài phải kiểm lại sau khi thay (R-I3) |
| Q7 | (survey 7) Thông báo/email | **Đã chốt 18/10/2026.** Áp cho thông báo **mới**: họ tên → nhãn; tin nhắn mới của ứng viên → **bỏ đoạn trích**. Thông báo cũ giữ nguyên trạng | Nội dung lưu cứng lúc phát sinh (a10); sự kiện chỉ mang đoạn trích đã cắt (a12) nên che chuỗi trên đoạn trích có thể lọt nửa tên; email rời hệ thống là chỗ rủi ro nhất | HR mất phần xem trước tin trong thông báo/email; thông báo đã tạo trước khi bật vẫn có tên |
| Q8 | Nhãn thay tên | **Đã chốt 18/10/2026.** `"Ứng viên #" + 8 ký tự hex cuối của id đơn, viết hoa` (vd `Ứng viên #00000001`), tính tại chỗ, không lưu | Ổn định ở mọi màn (cùng id đơn), không cần cột/backfill; id đơn đã nằm trên URL nên không lộ thêm gì | Không bảo đảm duy nhất tuyệt đối (2³² giá trị; trùng chỉ gây nhầm hiển thị, liên kết vẫn dùng id đầy đủ); hai đơn của cùng ứng viên ở hai Job có hai nhãn khác nhau |
| Q9 | Ai bật/tắt; tắt rồi bật lại; lưu vết | **Đã chốt 18/10/2026.** Chỉ HR sở hữu công ty của Job (một công ty một HR); bật/tắt được ở mọi trạng thái Job chưa xoá; **bật lại được**; mỗi lần đổi giá trị ghi một dòng `job_blind_review_changes` (người, thời điểm, giá trị); gửi lại cùng giá trị không ghi | Đúng README; bật lại cần cho chu kỳ tuyển mới của cùng Job | HR có thể tắt để xem rồi bật lại — chỉ phát hiện được qua lưu vết; không có màn xem lưu vết (mục 6) |
| Q10 | Lưu trạng thái "đã bỏ ẩn danh" của đơn ở đâu | **Đã chốt 18/10/2026.** **Không thêm cột**: đơn đã bỏ ẩn danh ⇔ có dòng lịch sử `to_status = 'INTERVIEW_INVITED'` (a7, a8). Người/thời điểm = `changed_by`/`changed_at` của dòng đó | Đúng nghĩa "đã gửi giấy mời"; tự đúng với dữ liệu cũ và seed (a23), không cần backfill | Không có đường bỏ ẩn danh từng đơn ngoài gửi giấy mời (Q11) |
| Q11 | "HR có thể tắt chủ động" (README) | **Đã chốt 18/10/2026.** Hiểu là **tắt công tắc của Job**; không có nút bỏ ẩn danh cho từng đơn | Tối giản; công tắc theo Job là quy tắc đã có | Muốn xem danh tính một đơn phải tắt cho cả Job |
| Q12 | Frontend biết đơn đang ẩn danh bằng gì | **Đã chốt 18/10/2026.** Thêm `boolean identityMasked` vào danh sách theo Job và E1 — hai chỗ duy nhất cần khoá nút/đổi câu | Không suy ngược từ chuỗi nhãn | Các DTO khác chỉ đổi giá trị `candidateName` |

### (c) Chỗ survey sai hoặc thiếu

| # | Survey | Thực tế |
|---|---|---|
| c1 | Mục 14: `note` ở tab Lịch sử "có thể chứa tên ứng viên" | `note` luôn null: đúng 3 điểm ghi, cả 3 truyền `null` (a9). ROADMAP `:366-367` ghi "cả 4 điểm ghi" — code hiện có **3** lời gọi `record(…)` (gửi giấy mời đi qua `ApplicationStatusService.java:83`, không phải điểm ghi riêng). Kết luận ROADMAP (an toàn, chưa có note tự do) vẫn đúng |
| c2 | Câu hỏi 6: placeholder `{{candidate_name}}` | Là `{{candidateName}}` (`InterviewInvitationService.java:58`) |
| c3 | Mục 21–22, câu hỏi 7: `INTERVIEW_SLOT_CONFIRMED` lộ tên trong lúc ẩn danh | Ứng viên chỉ chọn khung sau khi đã có giấy mời ⇒ đơn đã bỏ ẩn danh (Q10) — không phải bề mặt của giai đoạn ẩn danh |
| c4 | Mục 13: danh sách giấy mời (`renderedContent`) là bề mặt cần che | Giấy mời chỉ tồn tại sau khi gửi ⇒ đơn đã bỏ ẩn danh. Chỉ preview (trước khi gửi) cần xử lý |
| c5 | Mục 13: `InterviewInvitationDialog.tsx:136,144`, `InterviewInvitationTab.tsx:96` hiện tên | Các dòng đó nạp subject/content và hiện thư đã gửi; tên ở tiêu đề hộp thoại là `:170`, `:177`; tab truyền tên ở `:128` |
| c6 | Mục 2.4 "6 DTO chính" | Danh sách kê 7 (a1) |
| c7 | Không nhắc | Đoạn trích ở sự kiện tin nhắn đã bị cắt trước khi tới listener (a12) — che chuỗi trên đó có thể lọt nửa tên (cơ sở của Q7) |
| c8 | Không nhắc | Dữ liệu cũ: đơn đã mời trước khi có H16 phải được coi là đã bỏ ẩn danh — Q10 xử lý mà không cần backfill |

## 1. Mục đích

Giảm thiên lệch vô thức khi HR duyệt hồ sơ của một Job: trong giai đoạn sàng lọc, HR chỉ thấy năng lực chuyên môn,
không thấy họ tên, liên hệ, CV gốc và thư giới thiệu của ứng viên.

## 2. Luồng người dùng

1. HR mở `/hr/jobs/:id/edit` → tab "Thông tin tin tuyển dụng" → khối "Chế độ ẩn danh" → "Bật ẩn danh" → xác nhận.
2. Ở tab Ứng viên, `/hr/candidates`, `/hr/applications/:id`, `/hr/jobs/:id/compare`, `/hr/messages`, `/hr/talent-pools/:id`:
   đơn đang ẩn danh hiện "Ứng viên #XXXXXXXX"; không tải được CV gốc; không thấy tên tệp, thư giới thiệu, thông tin liên hệ.
3. HR đọc điểm, evidence, giải thích, câu trả lời sàng lọc, hỏi đáp CV, trao đổi — danh tính đã biết được thay bằng "[đã ẩn]".
4. HR bấm "Mời phỏng vấn" → hộp thoại hiện thư với chỗ `{{candidateName}}` → Gửi → đơn bỏ ẩn danh; mọi màn hiện tên thật.
5. HR có thể "Tắt ẩn danh" cho cả Job bất kỳ lúc nào, và bật lại.

## 3. Quy tắc nghiệp vụ

### 3.1 Ranh giới AI (R-A)

- **R-A1** H16 không thêm lời gọi AI nào (`ChatClient`, `ChatModel`, `EmbeddingModel`), không thêm lượt chấm.
- **R-A2** Bật/tắt ẩn danh không đổi đầu vào D2 (`raw_text`), đầu vào D4 (`criterion_scores`), điểm, tổng điểm, hạng,
  nội dung lưu trong `criterion_scores`, `score_explanations`, `resume_parsed_data` (`docs/SRS.md:193`).
- **R-A3** Bộ che chạy **sau** mọi kiểm K2 (FR-H04, FR-C08) và chỉ trên dữ liệu trả cho HR; không bao giờ ghi kết quả đã
  che xuống DB.
- **R-A4** Không dùng AI để nhận diện danh tính (`docs/SRS.md:194`). Package `ai/` và `scoring/` không import
  `blindreview/`; audit chấm điểm (trong `scoring/`) che qua interface `scoring/ScoringAuditTextFilter` do `blindreview/`
  cài đặt (L4).
- **R-A5** Không thêm cột/field tên `verdict`, `label`, `isQualified`, `passed`, `recommendation` (CLAUDE.md §7).

### 3.2 Công tắc của Job (R-B)

- **R-B1** Cột `jobs.blind_review_enabled` (mặc định `FALSE`). Đọc qua `JobOwnerResponse.blindReviewEnabled`; không có
  trong DTO công khai hay phía ứng viên.
- **R-B2** Chỉ đổi qua B1 (mục 4.3). HR sở hữu công ty của Job; Job chưa xoá mềm; mọi `JobStatus`. `PUT /api/hr/jobs/{id}`
  (`JobRequest`) và tạo Job không đụng cột này (entity map `insertable = false, updatable = false`).
- **R-B3** Thứ tự: `JobOwnerService.loadOwned` có sẵn, mở từ `private` sang `public`, không đổi thân hàm (L3)
  (`JobOwnerService.java:200-209`: không tồn tại → 404
  `JOB_NOT_FOUND`, công ty khác → 403; hàm này **không** lọc `deleted_at`) → Job có `deleted_at` → 404 `JOB_NOT_FOUND`
  (kiểm tường minh, không suy từ số dòng của câu dưới) → đổi bằng một câu `UPDATE jobs SET blind_review_enabled = :enabled WHERE id = :id AND deleted_at IS NULL AND
  blind_review_enabled <> :enabled` (`@Modifying(clearAutomatically = true)`); 1 dòng → cùng transaction ghi một dòng
  `job_blind_review_changes (job_id, enabled, changed_by, changed_at)`; 0 dòng (đã đúng giá trị) → 200, không ghi.
  Câu UPDATE đổi giá trị làm `jobs.updated_at` đổi theo trigger `set_updated_at` — chấp nhận (L5).
- **R-B4** Bật lại sau khi tắt được phép, không giới hạn số lần `[Q9]`.
- **R-B5** Lưu vết chỉ ghi, không có endpoint đọc (mục 6).

### 3.3 Trạng thái ẩn danh của đơn (R-S)

- **R-S1** Đơn **đang ẩn danh** ⇔ `jobs.blind_review_enabled = TRUE` của Job của đơn **và** `NOT EXISTS (SELECT 1 FROM
  application_status_history ash WHERE ash.application_id = ja.id AND ash.to_status = 'INTERVIEW_INVITED')` `[Q10]`.
- **R-S2** Một định nghĩa duy nhất ở `BlindReviewResolver` (một method cho một đơn, một method cho tập id chạy **một**
  câu SQL); mọi service HR gọi nó, không viết điều kiện thứ hai.
- **R-S3** Bỏ ẩn danh chỉ xảy ra khi gửi giấy mời (a7) hoặc khi Job tắt công tắc; một khi có dòng lịch sử
  `INTERVIEW_INVITED` thì đơn không bao giờ ẩn danh lại, kể cả khi Job bật lại `[Q10]`. Không có thao tác bỏ ẩn danh từng
  đơn `[Q11]`.
- **R-S4** Tính tại mỗi request, không cache. Trạng thái đơn khác (`REJECTED` từ `PENDING`, `WITHDRAWN`) không bỏ ẩn danh.

### 3.4 Nhãn thay tên (R-N)

- **R-N1** Nhãn = `"Ứng viên #"` + 8 ký tự hex cuối của `application.id` (bỏ dấu `-`), viết hoa. Hàm thuần
  `CandidateAlias.of(UUID)`, không lưu `[Q8]`.
- **R-N2** Mọi field `candidateName` của a1 nhận nhãn khi đơn đang ẩn danh; field vẫn có mặt, không null.
- **R-N3** Thêm `boolean identityMasked` vào `ApplicationHrListItemResponse` và `ApplicationHrDetailResponse` `[Q12]`.

### 3.5 Bộ che chuỗi (R-M)

- **R-M1** Nguồn giá trị của một đơn: `users.full_name`, `users.email`, `users.phone` của ứng viên; `contact.fullName`,
  `email`, `phone`, `address`, `linkedin` trong `resume_parsed_data.data` của CV của đơn (nếu có). Không thêm nguồn khác.
- **R-M2** Mỗi giá trị: `strip()`, chuẩn hoá NFC; bỏ qua nếu null hoặc **ít hơn 3 code point**. Văn bản đầu vào cũng
  chuẩn hoá NFC. So khớp chuỗi con, **không phân biệt hoa thường** (Unicode), giá trị được quote (không phải regex); giá
  trị dài thay trước.
- **R-M3** Chuỗi thay thế cố định `"[đã ẩn]"`.
- **R-M4** Áp lên, khi đơn đang ẩn danh: `criterionScores[].reasoning`, `evidence[].quote`; `explanation.summary`,
  `strengths[].point`, `weaknesses[].point` (danh sách theo Job, E3, E4, audit `/hr/candidates/{id}/audit/scoring-runs`,
  so sánh); E2 mọi chuỗi trong `data.education`, `data.experience`, `data.skills`, `data.certifications`,
  `data.projects`, `data.currentTitle`, `data.locationText` **và** hai bản sao ngoài `data` là `currentTitle`,
  `locationText` (a24); `answerText` của câu sàng lọc `SHORT_TEXT` (E của H10, ô sàng lọc của so sánh) — `answerText` của `SINGLE_CHOICE` và `answerYesNo` giữ nguyên (phương án do HR đặt, H14 R-B3); ô kỹ năng/học vấn của so sánh;
  `answer` và `citations[].quote` của C08 phía HR; `body` của **mọi** tin ở `GET /api/hr/applications/{id}/messages`;
  `lastMessageExcerpt` của hộp thư HR; `draft` của C07 phía HR.
- **R-M5** Đoạn trích hộp thư: che **thân tin đầy đủ** rồi mới `MessageExcerpt.of` (a12).
- **R-M6** Tên tiêu chí, tên Job, tên công ty, nhãn trạng thái không đi qua bộ che (dữ liệu của HR).

### 3.6 Ghi đè FR-H09 (R-V)

- **R-V1** Tải CV gốc khi đơn đang ẩn danh → 409 `IDENTITY_MASKED`, không đọc tệp.
- **R-V2** E1 `resumeFileName` = null.
- **R-V3** E1 `coverLetter` = null (ẩn toàn bộ, không thay chuỗi) `[Q2]`.
- **R-V4** E2 `data.contact` = null. Các field khác của E2 (a24): `resumeId`, `parsedAt`, `schemaVersion`, `industry`,
  `location` (mã + nhãn danh mục C05), `experience` (số tháng/năm) giữ nguyên; `currentTitle`, `locationText` qua bộ che
  (R-M4). Che bằng bản sao response ở `ResumeHrService`, **không** sửa `ResumeParsedDataResponseMapper` dùng chung với
  phía ứng viên.

### 3.7 Trao đổi (R-T)

- **R-T1** Tin nhắn: R-M4. Nhãn người gửi của bong bóng giữ nguyên (a13). Response 201 của M2 phía HR (tin HR vừa
  gửi) trả nguyên văn; mọi lần đọc lại qua `GET` thì đã che (L7).
- **R-T2** Tệp do **ứng viên** gửi: `attachment.fileName` = null (`fileType`, `fileSize` giữ); tải → 409
  `IDENTITY_MASKED`. Tệp HR gửi: không đổi `[Q3]`.
- **R-T3** Hộp thư `/api/hr/messages/conversations`: `candidateName` nhãn theo từng dòng; `lastMessageExcerpt` theo R-M5.

### 3.8 Giấy mời (R-I)

- **R-I1** Preview khi đơn đang ẩn danh: render `{{jobTitle}}`, `{{companyName}}` như cũ, **giữ nguyên** chữ
  `{{candidateName}}`; `candidateName` = nhãn `[Q6]`.
- **R-I2** Gửi lần đầu (`POST …/interview-invitation`): nếu đơn đang ẩn danh tại lúc gửi (R-S1, đọc trong transaction
  gửi), thay mọi `{{candidateName}}` trong subject/content của request bằng `users.full_name` rồi mới lưu. Đơn không ẩn
  danh: lưu nguyên văn như H12 (không đổi).
- **R-I3** Sau khi thay, subject > 255 hoặc content > 10000 ký tự → 400 `INVITATION_RENDERED_TOO_LONG`, không ghi gì
  (trạng thái đơn giữ `PENDING`).
- **R-I4** Dòng lịch sử `INTERVIEW_INVITED` ghi trong cùng transaction (a7) ⇒ sau commit đơn hết ẩn danh (R-S1). Gửi lại
  (resend) không đổi: đơn lúc đó luôn đã bỏ ẩn danh.

### 3.9 AI soạn nháp C07 (R-C)

- **R-C1** `ConversationContextAssembler` với `ContextViewer.HR` và đơn đang ẩn danh: `candidateName` = null. Hình dạng
  record giữ nguyên. Phía ứng viên không đổi.
- **R-C2** (Đã chốt 18/10/2026.) Prompt mới `message-draft-v2.st` (bản sao v1, chỉ đổi dòng 4, 34, 35): dòng "Họ tên ứng viên" có thể là
  `(không cung cấp)`; khi đó gọi ứng viên là "Anh/Chị", không lấy họ tên từ `<tin_gan_day>`, không đoán tên.
  `MessageDraftService.PROMPT_VERSION = "message-draft-v2"`; `buildUserMessage` ghi `(không cung cấp)` khi null. Xoá
  `message-draft-v1.st` và sửa các tham chiếu còn lại (mục 4.2).
- **R-C3** `draft` trả cho HR đi qua bộ che (R-M4) khi đơn đang ẩn danh.

### 3.10 Hỏi đáp CV C08 phía HR (R-Q)

- **R-Q1** K2 kiểm trên câu trả lời thô như hiện có; sau khi đạt, `answer` và `citations[].quote` đi qua bộ che (R-A3).
  Phía ứng viên không đổi.

### 3.11 Kho ứng viên (R-K)

- **R-K1** P8: `candidateName` nhãn theo từng dòng (R-S1) `[Q5]`.
- **R-K2** P7: `thêm` khác rỗng và đơn đang ẩn danh → 409 `IDENTITY_MASKED`, chèn làm bước 6b của H15 R-P2 (sau kiểm kho,
  trước kiểm đồng ý). `gỡ` không bị chặn.

### 3.12 Thông báo và email gửi HR (R-O)

- **R-O1** Khi tạo thông báo `APPLICATION_SUBMITTED`, `APPLICATION_WITHDRAWN`, `NEW_MESSAGE` (ứng viên gửi) mà đơn đang
  ẩn danh tại thời điểm listener chạy: họ tên → nhãn. Nhãn thay **cả cụm** `Ứng viên {họ tên}` — builder nhận nhãn đầy
  đủ (`"Ứng viên " + họ tên` hoặc `CandidateAlias.of(id)`), không tự ghép tiền tố "Ứng viên " (L1).
- **R-O2** `NEW_MESSAGE` cho HR của đơn đang ẩn danh: **không có đoạn trích**: "Ứng viên #X đã gửi tin nhắn về đơn ứng
  tuyển vị trí "…"" `[Q7]`. Tin chỉ có tệp (`excerpt = null`): giữ câu tệp đính kèm như hiện có, chỉ thay nhãn —
  "Ứng viên #X đã gửi một tệp đính kèm về đơn ứng tuyển vị trí "…"" (L2). Email dùng cùng `body` (a11).
- **R-O3** Thông báo đã tạo không bị sửa khi bật/tắt; tắt ẩn danh không viết lại thông báo đã dùng nhãn.
- **R-O4** `INTERVIEW_SLOT_CONFIRMED`, `SCORING_FINISHED`: không đổi (c3; không có tên).

### 3.13 Phía ứng viên (R-U)

- **R-U1** Không endpoint ứng viên nào đổi response; ứng viên không biết Job có bật ẩn danh.
- **R-U2** Không thông báo cho bên nào khi bật/tắt.

## 4. Dữ liệu & quyền truy cập

### 4.1 Migration `V19__blind_review.sql`

```sql
-- FR-H16 - Che do an danh theo Job (REQUIREMENT.md muc 4.1). Trang thai an danh cua don KHONG luu: suy tu
-- application_status_history (R-S1).
ALTER TABLE jobs ADD COLUMN blind_review_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Luu vet bat/tat (R-B3). Chi ghi, khong sua, khong xoa.
CREATE TABLE job_blind_review_changes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id      UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    enabled     BOOLEAN NOT NULL,
    changed_by  UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp()
);
CREATE INDEX idx_job_blind_review_changes_job ON job_blind_review_changes (job_id, changed_at DESC);
```

`ON DELETE CASCADE` theo `jobs.company_id` (`V1__init_schema.sql:63`): Job không bị xoá cứng ở đường nghiệp vụ nào.
Không cần index mới cho R-S1: lịch sử đã được truy vấn theo `application_id` ở a8. Không sửa V1–V18.

Seed/reset: `db/seed/reset-test-data.sql` thêm `DELETE FROM job_blind_review_changes` theo `job_id` của bộ test trước khi
xoá Job; `db/seed/reset-demo-db.sql` thêm bảng vào danh sách `TRUNCATE` kèm comment `FR-H16, V19` (tiền lệ H15 mục 4.5).
Không thêm dữ liệu seed.

### 4.2 Package và file backend

Package mới `blindreview/` (thêm vào CLAUDE.md §3 ở đợt cuối):

| File | Vai trò |
|---|---|
| `BlindReviewResolver.java` | R-S1, R-S2 |
| `CandidateAlias.java` | R-N1 |
| `IdentityMasker.java` | R-M2, R-M3 (nhận danh sách giá trị, `mask(String)`; null → null) |
| `IdentityMaskSource.java` | R-M1: nạp giá trị theo tập id đơn (một câu cho `users`, một câu cho `resume_parsed_data`) |
| `JobBlindReviewChange.java`, `JobBlindReviewChangeRepository.java` | Lưu vết |
| `BlindReviewService.java`, `BlindReviewController.java` | B1 |
| `dto/BlindReviewRequest.java`, `dto/BlindReviewResponse.java` | Mục 4.4 |

Thêm (L4): `scoring/ScoringAuditTextFilter.java` (interface, một method `UnaryOperator<String> forApplication(UUID
applicationId)`), `blindreview/BlindReviewScoringAuditFilter.java` (cài đặt bằng resolver + bộ che). Thêm (L6):
`jobapplication/ScoreTextMasking.java` (bản sao đã che của `CriterionScoreItem`/`Explanation`, dùng chung cho danh sách
theo Job, E3/E4).

Sửa file có sẵn (điểm che — người code đối chiếu lại ở Plan Mode): `Job.java`, `JobRepository.java`,
`JobOwnerResponse.java` (+ nơi dựng), `JobOwnerService` (mở `loadOwned` — L3); `ApplicationOwnerService`, `ApplicationHrDetailService`, `ApplicationSearchService`,
`ScoringRunAuditService`, `ApplicationComparisonService` (điểm che duy nhất của H14, H14 R-B1), `ResumeHrService`,
`ApplicationScreeningHrService` (hoặc service tương ứng), `MessageService`, `TalentPoolOwnerService`,
`ApplicationTalentPoolService`, `InterviewInvitationService`, `ConversationContextAssembler`, `MessageDraftFacade`
(che `draft` — R-C3), `CvQaFacade` (`cvqa/`, che sau K2 — R-Q1), `NotificationEventListener`,
`NotificationContentBuilder`; DTO thêm `identityMasked` (R-N3); `GlobalExceptionHandler` + exception mới.

Package `ai/` (R-A4: không import `blindreview/`): `ai/messagedraft/MessageDraftService.java` chỉ đổi `PROMPT_VERSION`,
comment `:30` và ghi `(không cung cấp)` khi `candidateName` null — **trước** `sanitize` vì `sanitize(null)` trả `""`
(`MessageDraftService.java:155-159`); `ai/cvqa/CvQaService.java` không sửa. Prompt `message-draft-v2.st` mới; xoá
`message-draft-v1.st` kéo theo sửa: comment `DraftTone.java:3`, `DraftScenario.java:9`; test
`MessageDraftPromptIntegrationTest.java:238` đọc `message-draft-v2.st`.

### 4.3 Endpoint

Mọi endpoint dưới `/api/hr/**`: không token → 401; token ứng viên → 403; quyền sở hữu theo quy ước H15 a9.

| # | Method, path | Body | Thành công | Lỗi riêng |
|---|---|---|---|---|
| B1 | `PATCH /api/hr/jobs/{id}/blind-review` | `{ "enabled": true\|false }` | 200 `BlindReviewResponse` | 400 `INVALID_BLIND_REVIEW_REQUEST`; 404 `JOB_NOT_FOUND` (không tồn tại hoặc đã xoá); 403 (Job công ty khác) |

Endpoint có sẵn đổi hành vi khi đơn đang ẩn danh: danh sách theo Job, `/hr/candidates`, audit, E1–E4, tải CV, sàng lọc,
so sánh, C08, tin nhắn + tải tệp, hộp thư, C07, preview + gửi giấy mời, P7, P8, `GET /api/hr/jobs/{id}` (thêm field).

Mã lỗi mới:

| Mã | HTTP | Câu |
|---|---|---|
| `IDENTITY_MASKED` | 409 | "Hồ sơ đang ở chế độ ẩn danh. Thao tác này mở lại khi gửi giấy mời phỏng vấn hoặc khi tắt chế độ ẩn danh của tin." |
| `INVALID_BLIND_REVIEW_REQUEST` | 400 | "Yêu cầu bật/tắt chế độ ẩn danh không hợp lệ." |
| `INVITATION_RENDERED_TOO_LONG` | 400 | "Tiêu đề hoặc nội dung thư mời vượt giới hạn sau khi điền họ tên ứng viên." |

B1: body thiếu, `enabled` null, không phải boolean, JSON hỏng → 400 `INVALID_BLIND_REVIEW_REQUEST` (advice theo mẫu
`TalentPoolExceptionAdvice`; body lỗi không chứa `ex.getMessage()`).

### 4.4 DTO

```java
// B1.
public record BlindReviewRequest(Boolean enabled) {}
// enabled = gia tri hien tai sau thao tac; changedAt = changed_at cua dong luu vet moi nhat (null neu chua tung doi).
public record BlindReviewResponse(UUID jobId, boolean enabled, Instant changedAt) {}
```

`JobOwnerResponse` thêm `boolean blindReviewEnabled`. `ApplicationHrListItemResponse`, `ApplicationHrDetailResponse`
thêm `boolean identityMasked`. Không DTO nào thêm field danh tính.

## 5. AI

Không thêm lời gọi AI (R-A1). Đổi một prompt có sẵn: `message-draft-v1.st` → `message-draft-v2.st` (R-C2). Không dùng
K3/K4 mới; K1 đổi giá trị một thành phần (R-C1), không đổi hình dạng; K2 không đổi (R-A3).

## 6. Ngoài phạm vi

- **Survey mục 14** (ghi chú tab Lịch sử): `note` luôn null ở mọi điểm ghi (a9, c1) — không có gì để che. FR nào sau này
  cho HR nhập note thì tự xử lý ẩn danh.
- Danh sách giấy mời đã gửi (`renderedContent`) — chỉ tồn tại sau khi đơn bỏ ẩn danh (c4). Thông báo
  `INTERVIEW_SLOT_CONFIRMED` (c3).
- Dashboard FR-H08 (`/hr`): chỉ có số liệu tổng hợp (survey 2.2, đã kiểm `DashboardStatsResponse`).
- Ẩn danh trong email/tin nhắn gửi **ứng viên** (README `:845`); che đầu vào chấm điểm (`docs/SRS.md:193`); AI nhận diện
  nhân thân (`docs/SRS.md:194`).
- Nút bỏ ẩn danh từng đơn `[Q11]`; màn hình xem lưu vết bật/tắt (R-B5); bật ẩn danh mặc định cho Job mới.
- Sửa lại thông báo đã tạo trước khi bật hoặc sau khi tắt (R-O3).
- Che danh tính trong `raw_text` gửi C08/D2 (C08 cần CV nguyên văn để K2 kiểm).

### 6b. Giới hạn đã biết

- Bộ che chỉ thay **đúng** các giá trị ở R-M1. Không che: tên không dấu ("Nguyen Van An"), chỉ họ hoặc chỉ tên riêng
  ("An", "anh Huy"), tên đảo thứ tự, biệt danh, SĐT viết khác dạng ("0900 000 111" chỉ che được nếu chính chuỗi đó là
  một giá trị nguồn), email/liên kết khác trong CV, tên người thân/người giới thiệu, ảnh hay danh tính suy ra từ nội dung
  (trường, công ty, dự án). Giá trị dưới 3 ký tự không che.
- Giá trị trích xuất sai ở `contact` (C04) thì che sai hoặc thiếu.
- Thông báo đã tạo trước khi bật còn họ tên; thông báo tạo trong lúc ẩn danh giữ nhãn sau khi bỏ ẩn danh (R-O3).
- Nhãn không bảo đảm duy nhất tuyệt đối (Q8).
- Tắt rồi bật lại không xoá được những gì HR đã thấy; chỉ có lưu vết (Q9).
- C07: AI có thể vẫn viết tên lấy từ tin nhắn dưới dạng khác giá trị nguồn — bộ che không bắt được.

## 7. Xong khi

### 7.1 Test backend (Testcontainers, full `./mvnw test` xanh)

Quy ước chung: mọi test "không lộ" kiểm trên **toàn bộ chuỗi JSON response** rằng không chứa họ tên, email, SĐT của tài
khoản và mọi giá trị `contact` của CV (không chỉ kiểm một field).

| T | Nội dung | Loại |
|---|---|---|
| T1 | `CandidateAlias`: đúng định dạng, 8 hex cuối viết hoa; cùng id → cùng nhãn; id khác 8 ký tự cuối → khác nhãn | Unit |
| T2 | `IdentityMasker`: không phân biệt hoa thường ("QUỐC HUY" với giá trị "Quốc Huy"); đầu vào NFD vẫn che; giá trị 2 code point → không che, 3 → che, 4 → che; giá trị null/rỗng/khoảng trắng bỏ qua; giá trị dài thay trước (email chứa họ tên); nhiều lần xuất hiện; ký tự đặc biệt regex (`+84 (90) 000.111`) che đúng; văn bản null → null; "Quốc" đứng riêng không bị che (giới hạn 6b) | Unit |
| T3 | `BlindReviewResolver`: Job tắt → không ẩn; Job bật + `PENDING` → ẩn; `REJECTED` từ `PENDING` → ẩn; `WITHDRAWN` từ `PENDING` → ẩn; có lịch sử `INTERVIEW_INVITED` (kể cả đã `HIRED`/`REJECTED`) → không ẩn; bật lại sau khi đơn đã mời → vẫn không ẩn; bản theo tập id cho kết quả giống bản một đơn | Integration |
| T4 | B1: bật → 200, 1 dòng lưu vết (`enabled = true`, `changed_by` = HR); gửi lại `true` → 200, không thêm dòng; tắt → dòng mới; bật lại → dòng mới; Job công ty khác → 403; không tồn tại / đã xoá mềm → 404; body thiếu, `enabled` null, `"abc"`, JSON hỏng → 400 `INVALID_BLIND_REVIEW_REQUEST`, body lỗi không lặp nội dung gửi; token ứng viên → 403; không token → 401; `GET /api/hr/jobs/{id}` có `blindReviewEnabled`; `PUT /api/hr/jobs/{id}` sau khi bật không làm cờ về `false` | Integration |
| T5 | Danh sách theo Job: đơn ẩn danh có nhãn, `identityMasked = true`, `reasoning`/`evidence.quote`/giải thích đã che (criterion score và giải thích dựng sẵn chứa họ tên, email); đơn đã mời cùng Job hiện tên thật, `identityMasked = false`; Job tắt → mọi đơn tên thật, nội dung nguyên văn; không lộ (quy ước chung) | Integration |
| T6 | E1–E4 đơn ẩn danh: E1 nhãn, `coverLetter = null`, `resumeFileName = null`, `identityMasked = true`; E2 `contact = null`, chuỗi kinh nghiệm chứa địa chỉ đã che, `currentTitle`/`locationText` ở cả `data` lẫn ngoài `data` đã che (dựng CV có hai chuỗi này chứa họ tên), `industry`/`location`/`experience` bằng đúng giá trị khi Job tắt; phía ứng viên gọi E2 của chính CV đó → nguyên văn; E3, E4 đã che; sàng lọc: `answerText` câu `SHORT_TEXT` chứa họ tên → đã che, `answerText` câu `SINGLE_CHOICE` có phương án chứa chuỗi trùng giá trị nguồn → giữ nguyên, `answerYesNo` giữ nguyên; không lộ. Job tắt → E1–E4 như hiện có | Integration |
| T7 | Tải CV gốc đơn ẩn danh → 409 `IDENTITY_MASKED`; Job tắt → 200; đơn đã mời → 200 | Integration |
| T8 | `/hr/candidates` gồm hai Job (một bật, một tắt) của cùng ứng viên: dòng Job bật có nhãn, dòng Job tắt có tên thật; audit đơn ẩn danh: `reasoning`/`evidence` đã che | Integration |
| T9 | So sánh 2 đơn ẩn danh + 1 đơn đã mời: cột nhãn/tên đúng từng cột; ô tiêu chí, giải thích, kỹ năng, học vấn, ô sàng lọc `SHORT_TEXT` đã che; ô `SINGLE_CHOICE`/`YES_NO` giữ nguyên; `totalScore`, `rank` bằng đúng giá trị khi Job tắt | Integration |
| T10 | Kho: P8 dòng ẩn danh có nhãn, dòng khác tên thật; P7 thêm đơn ẩn danh (`ACTIVE`) → 409 `IDENTITY_MASKED`, không dòng mới; P7 gỡ đơn ẩn danh → 200; đơn ẩn danh và `NOT_GIVEN` → 409 `IDENTITY_MASKED` (bước 6b trước bước 7) | Integration |
| T11 | Trao đổi: thân tin của cả HR và ứng viên chứa họ tên → đã che; tệp ứng viên gửi: `fileName = null`, tải → 409; tệp HR gửi: `fileName` giữ, tải 200; hộp thư: nhãn, đoạn trích tính sau khi che (tin dài có họ tên vắt qua điểm cắt → không còn mảnh tên); phía ứng viên `GET` cùng cuộc trao đổi → nguyên văn | Integration |
| T12 | Thông báo: ứng viên nộp đơn vào Job bật → `body` có nhãn, không có họ tên; rút đơn → như trên; ứng viên gửi tin → `body` không có đoạn trích, không có họ tên; email gửi đi (`mailText`) cùng nội dung; Job tắt → như hiện có; thông báo tạo trước khi bật không đổi sau khi bật | Integration |
| T13 | Giấy mời: preview đơn ẩn danh → `candidateName` nhãn, subject/content còn `{{candidateName}}`, `{{jobTitle}}`/`{{companyName}}` đã thay, không lộ; gửi → `interview_invitations.subject/rendered_content` chứa họ tên thật, không còn `{{candidateName}}`; sau commit E1 hiện tên thật; subject sau thay = 255 → 201, 256 → 400 `INVITATION_RENDERED_TOO_LONG`, trạng thái vẫn `PENDING`, không dòng giấy mời; content 10000/10001 tương tự; đơn không ẩn danh có `{{candidateName}}` trong request → lưu nguyên văn (H12 không đổi) | Integration |
| T14 | C07 phía HR, đơn ẩn danh: user message gửi `ChatModel` (bắt bằng mock) có `Họ tên ứng viên: (không cung cấp)` và không chứa họ tên; mock trả bản nháp có họ tên → response đã che; phía ứng viên cùng đơn → user message có họ tên thật; `PROMPT_VERSION = "message-draft-v2"`; test C07 cũ vẫn pass (sửa hằng số mong đợi nếu có) | Integration |
| T15 | C08 phía HR, đơn ẩn danh: mock trả câu trả lời + trích dẫn nguyên văn chứa họ tên (có trong `raw_text`) → K2 đạt, response đã che; trích dẫn **không** khớp CV → vẫn lỗi như cũ (che không làm K2 đạt giả); Job tắt → nguyên văn; phía ứng viên không đổi | Integration |
| T16 | Bất biến chấm điểm (R-A2): cùng đơn, chạy D2 với Job tắt rồi bật (hai lượt) → user message gửi `ChatModel` của từng tiêu chí giống hệt nhau và chứa họ tên trong `raw_text`; `criterion_scores` lưu nguyên văn (có họ tên); D4 cùng đầu vào; `totalScore`, `rank` ở danh sách theo Job bằng nhau khi tắt và bật | Integration |
| T17 | Phía ứng viên không đổi (R-U1): E1 của U08, tin nhắn, CV đã trích xuất, giấy mời phía ứng viên khi Job bật → giống hệt khi Job tắt | Integration |

Mock `ChatModel`/`EmbeddingModel` giữ default-answer throw (CLAUDE.md §7); T14, T15, T16 stub tường minh. T13, T16 đặt ở
lớp không `@Transactional` toàn lớp nếu cần phân biệt thời điểm (CLAUDE.md §3c).

### 7.2 Kiểm bằng HTTP — Windows PowerShell 5.1

Dữ liệu: bộ seed-test (`db/seed/README.md` mục 8.1); backend ở `localhost:8080`. Bộ hàm `Get-Token` / `Invoke-Api` /
`Test-Call` chép **nguyên văn** từ FR-H15 mục 7.2; thêm `Invoke-Form` gửi form UTF-8 cho endpoint tin nhắn. Dán cả khối
vào **một** cửa sổ PowerShell (chạy từ `.ps1` thì file phải UTF-8 có BOM). Khối này gửi tin, gửi giấy mời cho A6 và bật
ẩn danh J1 → chạy xong **bắt buộc** reset bộ test (`db/seed/README.md` mục 8.2 rồi 8.1).

```powershell
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$base = 'http://localhost:8080'

function Get-Token([string]$Email, [string]$Password) {
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $body).accessToken
}

# Chep nguyen van tu FR-H15 muc 7.2.
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

# Form urlencoded UTF-8 (M2 cua C06 nhan application/x-www-form-urlencoded).
function Invoke-Form([string]$Path, [string]$Token, [string]$Body) {
    $bytes = [Text.Encoding]::UTF8.GetBytes('body=' + [Uri]::EscapeDataString($Body))
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$base$Path" -Headers @{ Authorization = "Bearer $Token" } `
            -ContentType 'application/x-www-form-urlencoded; charset=utf-8' -Body $bytes
        return [int]$r.StatusCode
    } catch { return [int]$_.Exception.Response.StatusCode }
}

# Tra ve danh sach chuoi danh tinh bi lo trong $Text (rong = dat).
$secrets = @('Quốc Huy', 'QUỐC HUY', 'quochuyuv@gmail.com', '0900000111', 'quochuy.cv@test.local', '0900 000 111', 'Quận Hải Châu')
function Find-Leak([string]$Text) { $secrets | Where-Object { $Text.ToLower().Contains($_.ToLower()) } }

$tokenHr = Get-Token 'minhdathr@gmail.com' '12345678'
$tokenUv = Get-Token 'quochuyuv@gmail.com' '12345678'
$j1 = 'e2000000-0000-0000-0000-000000000001'
$a1 = 'e8000000-0000-0000-0000-000000000001'   # Quoc Huy -> J1, PENDING, ACTIVE, co thu gioi thieu
$a2 = 'e8000000-0000-0000-0000-000000000002'   # Quoc Huy -> J2 (Job khong bat), INTERVIEW_INVITED
$a6 = 'e8000000-0000-0000-0000-000000000006'   # Le Thi Lan -> J1, PENDING
$a9 = 'e8000000-0000-0000-0000-000000000009'   # Tran Bao Ngoc -> J1, INTERVIEW_INVITED (da bo an danh)

'--- 1. B1 bat J1 (200 enabled True); lap lai (200); body sai (400); GET job co blindReviewEnabled True'
Test-Call 'PATCH' "/api/hr/jobs/$j1/blind-review" $tokenHr @{ enabled = $true }
Test-Call 'PATCH' "/api/hr/jobs/$j1/blind-review" $tokenHr @{ enabled = $true }
Test-Call 'PATCH' "/api/hr/jobs/$j1/blind-review" $tokenHr '{"enabled":"abc"}'
'blindReviewEnabled={0}' -f ((Invoke-Api 'GET' "/api/hr/jobs/$j1" $tokenHr $null).Text | ConvertFrom-Json).blindReviewEnabled

'--- 2. Danh sach J1: A1 nhan "Ung vien #00000001", A9 ten that; khong lo'
$r = Invoke-Api 'GET' "/api/hr/jobs/$j1/applications" $tokenHr $null
$o = $r.Text | ConvertFrom-Json
$o | Where-Object { $_.id -in @($a1, $a9) } | ForEach-Object { '{0} {1} masked={2}' -f $_.id, $_.candidateName, $_.identityMasked }
'Lo: [{0}]' -f ((Find-Leak $r.Text) -join ', ')

'--- 3. E1, E2, sang loc cua A1: nhan, coverLetter/resumeFileName rong, contact rong; khong lo; sang loc giu phuong an'
$r = Invoke-Api 'GET' "/api/hr/applications/$a1" $tokenHr $null
$o = $r.Text | ConvertFrom-Json
'{0} masked={1} coverLetter=[{2}] resumeFileName=[{3}] lo=[{4}]' -f $o.candidateName, $o.identityMasked, $o.coverLetter, $o.resumeFileName, ((Find-Leak $r.Text) -join ', ')
$r = Invoke-Api 'GET' "/api/hr/applications/$a1/resume/parsed" $tokenHr $null
$e2 = $r.Text | ConvertFrom-Json
'E2 {0} contact=[{1}] currentTitle=[{2}] locationText=[{3}] lo=[{4}]' -f $r.Status, $e2.data.contact, $e2.currentTitle, $e2.locationText, ((Find-Leak $r.Text) -join ', ')
$r = Invoke-Api 'GET' "/api/hr/applications/$a1/screening-answers" $tokenHr $null
(($r.Text | ConvertFrom-Json).items) | ForEach-Object { 'Sang loc {0}: [{1}] [{2}]' -f $_.questionType, $_.answerText, $_.answerYesNo }

'--- 4. Tai CV goc A1 (409 IDENTITY_MASKED); A9 (200)'
Test-Call 'GET' "/api/hr/applications/$a1/resume/download" $tokenHr $null
'{0} A9 download' -f (Invoke-Api 'GET' "/api/hr/applications/$a9/resume/download" $tokenHr $null).Status

'--- 5. /hr/candidates: dong A1 nhan, dong A2 (J2) ten that'
$o = (Invoke-Api 'GET' '/api/hr/candidates?size=50' $tokenHr $null).Text | ConvertFrom-Json
$o.items | Where-Object { $_.id -in @($a1, $a2) } | ForEach-Object { '{0} {1}' -f $_.id, $_.candidateName }

'--- 6. Trao doi A1: HR gui tin co ho ten (201); GET messages da che; ung vien xem nguyen van; hop thu HR nhan'
'{0} HR gui' -f (Invoke-Form "/api/hr/applications/$a1/messages" $tokenHr 'Chào Quốc Huy, bạn gửi lại số 0900000111 giúp mình nhé.')
$r = Invoke-Api 'GET' "/api/hr/applications/$a1/messages" $tokenHr $null
'HR thay: {0} | lo=[{1}]' -f (($r.Text | ConvertFrom-Json).messages | Select-Object -Last 1).body, ((Find-Leak $r.Text) -join ', ')
$r = Invoke-Api 'GET' "/api/candidates/applications/$a1/messages" $tokenUv $null
'UV thay: {0}' -f (($r.Text | ConvertFrom-Json).messages | Select-Object -Last 1).body
$r = Invoke-Api 'GET' '/api/hr/messages/conversations?size=50' $tokenHr $null
(($r.Text | ConvertFrom-Json).items | Where-Object applicationId -eq $a1) | ForEach-Object { '{0} | {1}' -f $_.candidateName, $_.lastMessageExcerpt }

'--- 7. Ung vien gui tin A1 -> thong bao HR moi nhat: nhan, khong doan trich, khong ho ten'
'{0} UV gui' -f (Invoke-Form "/api/candidates/applications/$a1/messages" $tokenUv 'Dạ em Quốc Huy đây ạ.')
$r = Invoke-Api 'GET' '/api/notifications?size=1' $tokenHr $null
'{0} | lo=[{1}]' -f (($r.Text | ConvertFrom-Json).page.items | Select-Object -First 1).body, ((Find-Leak $r.Text) -join ', ')

'--- 8. Kho: P7 them A1 (409 IDENTITY_MASKED)'
$p = (Invoke-Api 'POST' '/api/hr/talent-pools' $tokenHr @{ name = ('H16 ' + (Get-Date -Format 'HHmmss')) }).Text | ConvertFrom-Json
Test-Call 'PUT' "/api/hr/applications/$a1/talent-pools" $tokenHr @{ poolIds = @($p.id) }

'--- 9. Preview giay moi A6: nhan + con {{candidateName}}; gui -> E1 A6 ten that, thu da gui co ten that'
$r = Invoke-Api 'GET' "/api/hr/applications/$a6/interview-invitation/preview" $tokenHr $null
$pv = $r.Text | ConvertFrom-Json
'{0} placeholder={1} lo=[{2}]' -f $pv.candidateName, ($pv.content.Contains('{{candidateName}}') -or $pv.subject.Contains('{{candidateName}}')), ($r.Text.Contains('Lê Thị Lan'))
$slot = (Get-Date).ToUniversalTime().AddDays(7).ToString('yyyy-MM-ddTHH:00:00Z')
$r = Invoke-Api 'POST' "/api/hr/applications/$a6/interview-invitation" $tokenHr @{ subject = $pv.subject; content = $pv.content; slots = @(@{ scheduledAt = $slot; location = 'Văn phòng' }) }
'{0} gui giay moi; rendered co ten that={1}, con placeholder={2}' -f $r.Status, $r.Text.Contains('Lê Thị Lan'), $r.Text.Contains('{{candidateName}}')
'E1 A6 sau gui: {0}' -f ((Invoke-Api 'GET' "/api/hr/applications/$a6" $tokenHr $null).Text | ConvertFrom-Json).candidateName
$inv = ((Invoke-Api 'GET' "/api/hr/applications/$a6/interview-invitations" $tokenHr $null).Text | ConvertFrom-Json).invitations
'Thu da gui co ten that={0}' -f (($inv | ConvertTo-Json -Depth 8).Contains('Lê Thị Lan'))

'--- 10. B1 tat J1 (200); E1 A1 ten that; token ung vien goi B1 (403); khong token (401)'
Test-Call 'PATCH' "/api/hr/jobs/$j1/blind-review" $tokenHr @{ enabled = $false }
'E1 A1: {0}' -f ((Invoke-Api 'GET' "/api/hr/applications/$a1" $tokenHr $null).Text | ConvertFrom-Json).candidateName
Test-Call 'PATCH' "/api/hr/jobs/$j1/blind-review" $tokenUv @{ enabled = $true }
Test-Call 'PATCH' "/api/hr/jobs/$j1/blind-review" $null @{ enabled = $true }
```

Path, method, Content-Type, tên field body và hình dạng response của nhóm 6, 7, 9 đã đối chiếu với controller/DTO thật
(a25).

| Nhóm | Mong đợi |
|---|---|
| 1 | 200, 200 (không thêm lưu vết — kiểm ở T4), 400 `INVALID_BLIND_REVIEW_REQUEST`; `blindReviewEnabled=True` |
| 2 | A1 `Ứng viên #00000001` `masked=True`; A9 `Trần Bảo Ngọc` `masked=False`; `Lo: []` |
| 3 | `Ứng viên #00000001 masked=True coverLetter=[] resumeFileName=[] lo=[]`; E2 200, `contact=[]`, `currentTitle=[]` (R3 không có chức danh), `locationText=[Đà Nẵng]`, `lo=[]`; sàng lọc: `SHORT_TEXT: [Đầu tháng 11/2026, sau khi bàn giao công việc hiện tại.] []`, `YES_NO: [] [False]`, `SINGLE_CHOICE: [10–14 triệu] []` (nguyên văn — câu không chứa danh tính; ca che `SHORT_TEXT` kiểm ở T6) |
| 4 | 409 `IDENTITY_MASKED`; A9 200 |
| 5 | A1 nhãn; A2 `Quốc Huy` |
| 6 | 201; HR thấy `Chào [đã ẩn], bạn gửi lại số [đã ẩn] giúp mình nhé.`, `lo=[]`; UV thấy nguyên văn; hộp thư: nhãn + đoạn trích đã che |
| 7 | 201; `Ứng viên #00000001 đã gửi tin nhắn về đơn ứng tuyển vị trí "Chuyên viên Chăm sóc khách hàng"`, `lo=[]` |
| 8 | 409 `IDENTITY_MASKED` |
| 9 | `Ứng viên #00000006 placeholder=True lo=False`; `201 gui giay moi; rendered co ten that=True, con placeholder=False`; sau gửi E1 `Lê Thị Lan`; `Thu da gui co ten that=True` |
| 10 | 200; `Quốc Huy`; 403; 401 |

### 7.3 Kiểm tĩnh

`npm run build` + `npm run lint` sạch. Lệnh (0 dòng):

```bash
MSYS_NO_PATHCONV=1 rg -n "ChatClient|ChatModel|EmbeddingModel" backend/src/main/java/com/recruitment/blindreview
MSYS_NO_PATHCONV=1 rg -n "blindreview" backend/src/main/java/com/recruitment/ai backend/src/main/java/com/recruitment/scoring
MSYS_NO_PATHCONV=1 rg -n "implements ScoringAuditTextFilter" backend/src/main/java -g '!**/blindreview/**'
MSYS_NO_PATHCONV=1 rg -n -i "verdict|isQualified|passed|recommendation" backend/src/main/java/com/recruitment/blindreview backend/src/main/resources/db/migration/V19__blind_review.sql
MSYS_NO_PATHCONV=1 rg -n "DELETE FROM" backend/src/main/java/com/recruitment/blindreview
MSYS_NO_PATHCONV=1 rg -n "message-draft-v1" backend/src
MSYS_NO_PATHCONV=1 rg -n "Ứng viên #[0-9A-F]|\[đã ẩn\]" frontend/src -g '!**/blindReview/blindReviewLabels.ts'
```

Dòng 2–3 (L4): `scoring/` chỉ biết interface `ScoringAuditTextFilter`, không import `blindreview/`; cài đặt duy nhất
nằm trong `blindreview/`.

Dòng cuối: frontend không tự che hay tự dựng nhãn — nhãn thật ("Ứng viên #" + hex) và "[đã ẩn]" chỉ đến từ API. Chuỗi
mô tả có "Ứng viên #…" và "(đã ẩn tên)" trong `blindReviewLabels.ts` (`UI.md` mục 7) bị loại trừ và không khớp mẫu. Đã
chạy trên cây 18/10/2026: 0 dòng.

### 7.4 Soát tay (desktop ≥ `lg`, 768px, 375px)

Dữ liệu: bộ test, nạp theo `db/seed/README.md` mục 8.1; HR `minhdathr@gmail.com` / `12345678`. Bật ẩn danh J1 trước.

| Trạng thái cần thấy | Nơi | Mong đợi |
|---|---|---|
| Bật/tắt | `/hr/jobs/e2000000-0000-0000-0000-000000000001/edit` tab Thông tin | Khối "Chế độ ẩn danh" tách khỏi form; bấm "Bật ẩn danh" → hộp thoại xác nhận → câu trạng thái đổi; form tin không bị đánh dấu "chưa lưu" |
| Danh sách | tab Ứng viên J1 | A1, A6, A7 hiện nhãn; A9 tên thật; nút "Xem CV gốc" của dòng ẩn danh khoá kèm lý do; dòng thông tin trung tính "Tin này đang bật chế độ ẩn danh" |
| Hồ sơ đơn | `/hr/applications/e8000000-0000-0000-0000-000000000001` | `h1` nhãn + badge trung tính "Ẩn danh"; khối Hồ sơ đã nộp không có nút tải, có câu ẩn danh; không có thư giới thiệu, có câu ẩn danh; CV đã trích xuất: câu "Thông tin liên hệ được ẩn…" thay vì "Không trích xuất được…"; nút "Lưu vào kho" khoá kèm lý do |
| Trao đổi | tab Trao đổi A1 | Tin có họ tên hiện "[đã ẩn]"; soạn bằng AI không gọi tên ứng viên |
| Giấy mời | A6 → "Mời phỏng vấn" | Tiêu đề hộp thoại có nhãn; nội dung còn `{{candidateName}}`; câu giải thích họ tên tự điền khi gửi; gửi xong → tên thật ở mọi màn |
| Màn gộp | `/hr/candidates`, `/hr/messages`, `/hr/jobs/…/compare`, kho | Dòng/cột của đơn J1 ẩn danh có nhãn; đơn J2 tên thật |
| Thông báo | Chuông | Thông báo mới của A1 không có họ tên, không đoạn trích; thông báo cũ của seed giữ tên |
| 375px | Các màn trên | Nhãn và badge không tràn; khối ẩn danh ở trang sửa Job xếp dọc; không cuộn ngang toàn trang |

**Dọn dẹp (bắt buộc):** reset bộ test (`db/seed/README.md` mục 8.2 rồi 8.1).

### 7.5 Truy vết quy tắc → kiểm thử

| Quy tắc | Kiểm |
|---|---|
| R-A1, R-A4, R-A5 | 7.3 |
| R-A2 | T16 |
| R-A3 | T15, T16 |
| R-B1–R-B4 | T4; 7.2 nhóm 1, 10 |
| R-B5 | Review |
| R-S1–R-S4 | T3; T5; 7.2 nhóm 2, 9 |
| R-N1–R-N3 | T1, T5, T6 |
| R-M1–R-M3, R-M6 | T2, T5, T6 |
| R-M4 | T5, T6, T8, T9, T11, T14, T15 |
| R-M5 | T11 |
| R-V1–R-V4 | T6, T7; 7.2 nhóm 3, 4 |
| R-T1–R-T3 | T11; 7.2 nhóm 6 |
| R-I1–R-I4 | T13; 7.2 nhóm 9 |
| R-C1–R-C3 | T14; 7.3 |
| R-Q1 | T15 |
| R-K1, R-K2 | T10; 7.2 nhóm 8 |
| R-O1–R-O4 | T12; 7.2 nhóm 7 |
| R-U1, R-U2 | T17; 7.2 nhóm 6 |

## 8. AI hay làm sai (dành cho người code)

- Che ở frontend, hoặc chỉ đổi `candidateName` rồi để `coverLetter`, `resumeFileName`, `contact`, tên tệp đính kèm trong
  JSON.
- Che `raw_text` trước khi gửi D2/C08, hoặc che rồi mới chạy K2 — evidence không còn khớp CV, lượt chấm hỏng (R-A2, R-A3).
- Ghi bản đã che xuống `criterion_scores`/`score_explanations` "cho tiện".
- Viết điều kiện ẩn danh lần thứ hai trong từng service/SQL thay vì `BlindReviewResolver` (R-S2); quên đơn đã mời trước
  khi có H16 (dùng cột mới thay vì lịch sử — Q10).
- Gọi resolver từng đơn trong vòng lặp danh sách (N+1) thay vì bản theo tập id.
- Che đoạn trích sau khi cắt (R-M5) — lọt nửa tên.
- Dùng `String.replace`/regex không quote, phân biệt hoa thường, hoặc không chuẩn hoá NFC (R-M2); che cả giá trị 1–2 ký tự.
- Render họ tên vào preview rồi "ẩn trên UI".
- Thay `{{candidateName}}` cả khi đơn không ẩn danh (đổi hành vi H12), hoặc kiểm độ dài trước khi thay (R-I3) → 500.
- Thêm nút bỏ ẩn danh từng đơn, hoặc coi `REJECTED` là bỏ ẩn danh (R-S3, R-S4).
- Sửa prompt v1 tại chỗ thay vì tạo v2 (CLAUDE.md §4: phiên bản trong tên file).
- Để `PUT /api/hr/jobs/{id}` ghi đè `blind_review_enabled` qua entity (R-B2).
- Ghi lưu vết khi gửi lại cùng giá trị, hoặc kiểm-rồi-ghi bằng hai câu thay vì `UPDATE` có điều kiện (R-B3).
- Sửa lại thông báo cũ khi bật/tắt (R-O3).
- Đổi response phía ứng viên (R-U1).

## 9. Kế hoạch đợt code

Mỗi đợt: code xong → kiểm → dừng → báo cáo diff → chờ duyệt → một commit. Backend chỉ chạy lớp test của đợt (`-Dtest`);
full `./mvnw test` chỉ ở đợt 6. Lập lại sau Plan Mode 19/10/2026 (tách 3a/3b, 4a/4b).

| Đợt | Nội dung | Test (`-Dtest`) |
|---|---|---|
| 1 | Plan Mode: đối chiếu mục 0, điểm che 4.2, 7.2; L1–L4 (mục 11) | — |
| 2 | `V19`; `Job` + `JobRepository` (UPDATE có điều kiện); `blindreview/` (`BlindReviewResolver` bản một đơn + bản theo tập id một câu SQL, `CandidateAlias`, `IdentityMasker`, `IdentityMaskSource`, lưu vết, `BlindReviewService`/`BlindReviewController` B1, DTO, exception + advice); `JobOwnerResponse.blindReviewEnabled`; `JobOwnerService.loadOwned` public (L3); `GlobalExceptionHandler` (`IDENTITY_MASKED`); reset scripts | T1–T4: `CandidateAliasTest`, `IdentityMaskerTest`, `BlindReviewResolverIntegrationTest`, `BlindReviewControllerIntegrationTest`; test cũ của `JobOwnerService`/`JobOwnerController` |
| 3a | Danh sách theo Job (`ApplicationOwnerService`, che sau rank), E1/E3/E4 (`ApplicationHrDetailService`), E2 + tải CV (`ResumeHrService`), sàng lọc (`ApplicationScreeningHrService`); `identityMasked` ở hai DTO | T5–T7: `BlindReviewApplicationListIntegrationTest`, `BlindReviewApplicationDetailIntegrationTest` + test cũ các lớp đó |
| 3b | `/hr/candidates` (`ApplicationSearchService`), audit qua `ScoringAuditTextFilter` (L4), so sánh (`ApplicationComparisonService`), kho P7/P8 | T8–T10, T16: `BlindReviewSearchAuditIntegrationTest`, `BlindReviewComparisonIntegrationTest`, `BlindReviewTalentPoolIntegrationTest`, `BlindReviewScoringInvarianceIntegrationTest` + test cũ các lớp đó |
| 4a | Tin nhắn + tải tệp + hộp thư (`MessageService`), thông báo (`NotificationEventListener`, `NotificationContentBuilder` — L1, L2) | T11, T12: `BlindReviewMessagingIntegrationTest`, `BlindReviewNotificationIntegrationTest` + test cũ `Message*`, `Notification*` |
| 4b | Giấy mời (`InterviewInvitationService`, `INVITATION_RENDERED_TOO_LONG`), C07 (`ConversationContextAssembler`, prompt v2, `MessageDraftFacade`; xoá v1, sửa `MessageDraftPromptIntegrationTest:238`), C08 (`CvQaFacade`) | T13–T15, T17: `BlindReviewInvitationIntegrationTest`, `BlindReviewMessageDraftIntegrationTest`, `BlindReviewCvQaIntegrationTest`, `BlindReviewCandidateSideIntegrationTest` + test cũ `InterviewInvitation*`, `MessageDraft*`, `ConversationContext*`, `CvQa*` |
| 5 | Frontend: khối bật/tắt ở trang sửa Job; tab Ứng viên; trang hồ sơ đơn (header, CV & điểm, Trao đổi, hộp thoại giấy mời, Lưu vào kho) | `npm run build` + `npm run lint`; 7.3 dòng cuối |
| 6 | Đợt cuối: full `./mvnw test`, 7.2, 7.3, 7.4, `srs-guard`, `walkthrough`, trạng thái `ĐÃ HOÀN THÀNH`, mục 10, tick ROADMAP | full test; build + lint |

## 10. Tài liệu dùng chung sẽ sửa (ở đợt cuối, không sửa trong commit đặc tả)

| # | File | Thay đổi |
|---|---|---|
| D1 | `docs/SRS.md` | Dòng 43 `Chưa đặc tả` → `Đã hoàn thành`; dòng 110 cột Phụ thuộc thêm FR-C06, FR-C07, FR-H12, FR-H15 |
| D2 | `docs/ROADMAP.md` | Dòng 1098 tick + tóm tắt; lưu ý `:1104-1110` đóng; `:366-367` sửa "cả 4 điểm ghi" → "cả 3 điểm ghi" (c1) |
| D3 | `docs/UI_GUIDE.md` mục 7 | Dòng 363 bỏ ★ ở FR-H16; thêm FR-H16 vào cột FR của các dòng `/hr/jobs/:id/compare` (`:364`), `/hr/candidates` (`:365`), `/hr/applications/:id` (`:366`), `/hr/messages` (`:368`), `/hr/talent-pools/:id` |
| D4 | `docs/features/README.md` mục 19 | Ghi phạm vi thực tế: thêm `/hr/candidates`, `/hr/messages`, kho, thông báo; tắt chủ động = tắt công tắc Job; thư giới thiệu ẩn toàn bộ |
| D5 | `CLAUDE.md` §3, §3d | Thêm `blindreview/` (FR-H16); K1 ghi: FR-H16 đặt `ConversationContext.candidateName = null` cho HR khi đơn ẩn danh |
| D6 | `docs/features/HR/H09/REQUIREMENT.md`, `CHUNG/C07/REQUIREMENT.md` | Một dòng tham chiếu ghi đè R-V1/R-L (H09); prompt v2 (C07) |

## 11. Làm rõ sau Plan Mode (duyệt 19/10/2026)

Đối chiếu lại trên `feat/fr-h16-blind` tại `cfff5e9`. Mục 0 đã sửa số dòng/mô tả ở a5, a8, a12, a13, a14, a18, a23,
a25 (kết luận không đổi). Lệnh 7.2 khớp controller/DTO thật, không dòng nào phải sửa. Mục này thắng chỗ viết cũ ở các mục
được dẫn nếu lệch.

- **L1 (R-O1, R-O2, 7.2 nhóm 7) — tiền tố lặp trong thông báo.** `NotificationContentBuilder.java:55`, `:62`, `:78` ghép
  `"Ứng viên " + tên`; truyền nhãn R-N1 vào sẽ thành "Ứng viên Ứng viên #00000001 …". Chốt: `forApplicationSubmitted`,
  `forApplicationWithdrawn`, `forNewMessage` (nhánh gửi HR) nhận `candidateLabel` đầy đủ — thường `"Ứng viên " +
  fullName`, ẩn danh `CandidateAlias.of(applicationId)`; listener dựng nhãn, builder bỏ tiền tố. Chữ ký vẫn là `String`;
  nội dung thông báo khi Job tắt giữ nguyên từng chữ.
- **L2 (R-O2) — tin chỉ có tệp.** Ứng viên gửi tin không có chữ (`excerpt = null`, a12) cho đơn ẩn danh: giữ câu "… đã
  gửi một tệp đính kèm …" như hiện có (`NotificationContentBuilder.java:82`), chỉ thay nhãn — câu không chứa danh tính.
- **L3 (R-B3, mục 4.2) — `loadOwned` đang `private`.** `JobOwnerService.java:200` không gọi được từ `blindreview/`.
  Chốt: đổi sang `public`, không đổi thân hàm (vẫn không lọc `deleted_at`; B1 tự kiểm như R-B3).
- **L4 (R-A4, mục 4.2, 7.3) — audit nằm trong `scoring/`.** `ScoringRunAuditService.java:48-77`, `:101-109` phải che
  (R-M4, T8) nhưng 7.3 cấm `blindreview` trong `scoring/`. Chốt: interface `scoring/ScoringAuditTextFilter`
  (`UnaryOperator<String> forApplication(UUID applicationId)`; trả `UnaryOperator.identity()` khi đơn không ẩn danh),
  cài đặt `blindreview/BlindReviewScoringAuditFilter` (resolver + bộ che). `scoring/` không import `blindreview/`; 7.3
  thêm lệnh kiểm cài đặt duy nhất nằm trong `blindreview/`.

### 11.1 Làm rõ trong đợt 2 (duyệt 19/10/2026)

- **L5 (R-B3) — `jobs.updated_at` đổi khi bật/tắt.** Bảng `jobs` có trigger `set_updated_at`
  (`V1__init_schema.sql:369-378`), nên câu UPDATE của B1 (khi đổi giá trị) làm `updated_at` đổi. Đã soát mọi chỗ đọc: duy
  nhất `JobOwnerService.java:309` trả `JobOwnerResponse.updatedAt`; frontend khai field (`features/jobs/ownerTypes.ts:26`)
  nhưng không hiển thị; không truy vấn, sắp xếp, scheduler embedding (`job/JobEmbedding*`), dashboard, tìm việc hay gợi ý
  việc làm nào đọc `jobs.updated_at`. Chốt: chấp nhận, không sửa trigger. Thứ tự kiểm của B1: body (`enabled` null →
  400) trước quyền sở hữu (403/404), theo khuôn H15 R-P2 — HR công ty khác gửi `{}` nhận 400.

### 11.2 Làm rõ trong đợt 3a (duyệt 19/10/2026)

- **L6 (mục 4.2) — helper che kết quả chấm.** Thêm `jobapplication/ScoreTextMasking.java`: dựng bản sao đã che của
  `ApplicationHrListItemResponse.CriterionScoreItem` (`reasoning`, `evidence[].quote`) và `Explanation` (`summary`,
  `strengths[].point`, `weaknesses[].point`); tên tiêu chí, `section`, `metCriteria`/`missingCriteria` giữ nguyên
  (R-M6). Dùng chung cho danh sách theo Job và E3/E4; điểm che khác dùng lại nếu cùng kiểu dữ liệu, không nhân bản logic.

### 11.3 Làm rõ trong đợt 4a (duyệt 19/10/2026)

- **L7 (R-T1) — response của M2 phía HR.** `POST /api/hr/applications/{id}/messages` trả 201 `MessageResponse` của chính
  tin HR vừa gửi (`MessageService.send` → `toResponse`). Chốt: trả nguyên văn, không che — đó là chữ HR tự gõ, không lộ
  thêm thông tin; tên tệp HR gửi vốn giữ nguyên (R-T2). Mọi lần đọc lại qua `GET /api/hr/applications/{id}/messages` đi
  qua bộ che (R-M4).
