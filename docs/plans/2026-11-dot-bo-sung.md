# Kế hoạch đợt bổ sung 11/2026: tăng tốc chấm điểm, lưu tin, yêu cầu xem xét lại

29/10/2026 · Bản xuất từ tài liệu kế hoạch, đọc cùng `docs/plans/2026-10-28-tang-toc-cham-diem.md`.

## Phạm vi và quyết định đã chốt

Đợt này làm 4 hạng mục trên 4 nhánh từ `main` (`bee3c79`): viết xong tài liệu trước, code từ 30/10. Chỉ FR-U16 và FR-C09 cần migration (V20, V21). Tổng số mã yêu cầu tăng từ 37 lên 39; nút chấm tất cả là phần mở rộng của FR-H04, không thêm mã.

| Hạng mục | Mã | Nhánh | Migration | Đặc tả |
| --- | --- | --- | --- | --- |
| Tăng tốc chấm điểm (A + B) | Tối ưu, không mã | `perf/scoring-latency` | Không | Không; theo kế hoạch 28/10 + mục sửa đổi bên dưới |
| Nút chấm tất cả trong tin | FR-H04 mở rộng | `feat/fr-h04-bulk-scoring` | Không | `docs/features/HR/H04/REQUIREMENT.md` + `UI.md` (thư mục mới) |
| Lưu tin tuyển dụng | FR-U16 (mới) | `feat/fr-u16-saved-jobs` | V20 | `docs/features/UV/U16/` |
| Yêu cầu xem xét lại | FR-C09 (mới) | `feat/fr-c09-reconsideration` | V21 | `docs/features/CHUNG/C09/` |

**Quyết định ngày 29/10**

- Giữ đúng 5 trạng thái đơn. Yêu cầu xem xét lại nằm ở bảng riêng; ứng viên thấy "Bị từ chối · Đang chờ xem xét lại".
- HR chấp nhận yêu cầu = **Mời phỏng vấn** qua hộp giấy mời FR-H12 (`REJECTED → INTERVIEW_INVITED`); hoặc giữ quyết định từ chối, kèm phản hồi hoặc không.
- Chỉ đơn bị từ chối ở vòng hồ sơ (`PENDING → REJECTED`) được gửi yêu cầu.
- Hạn gửi là 7 ngày kể từ lúc bị từ chối. Tin hết hạn nộp, tạm dừng hoặc đã đóng vẫn gửi được nếu còn trong 7 ngày.
- FR-U16 và FR-C09 không dùng AI.

**Quyết định ngày 28/10 (giữ nguyên)**: hạ log `org.hibernate.SQL` về `INFO`; nút chấm tất cả chỉ chấm đơn `PENDING` chưa có lượt `DONE`, thuộc chu kỳ hiện tại, trần 50 đơn mỗi lần bấm.

Kế hoạch này là định hướng, không phải đặc tả. Khi mâu thuẫn, `REQUIREMENT.md`/`UI.md` đã duyệt thắng.

## Sửa đổi kế hoạch tăng tốc chấm điểm (28/10)

Kế hoạch 28/10 giữ nguyên, trừ 5 điểm dưới đây. Claude Code đọc cả kế hoạch 28/10 lẫn mục này ở Plan Mode.

| # | Trong kế hoạch 28/10 | Sửa thành | Lý do |
| --- | --- | --- | --- |
| 1 | A: `spring.task.scheduling.pool.size: 6` | `pool.size: 8` | Có 5 job gọi LLM (chấm, giải thích, trích xuất, trích xuất lại, gợi ý sửa CV) và 3 job embedding. Với 6 luồng, bước tổng hợp nằm trên đường HR đang chờ vẫn có thể xếp hàng. Job nền không giữ kết nối khi chờ LLM nên pool Hikari 10 không phải giới hạn thật |
| 2 | A: rà các cặp job cùng ghi một bảng | Thêm cặp **reaper ↔ worker** của cùng bảng: `ScoringRunScheduler`, `ResumeParsingScheduler`, `ResumeReparseScheduler` | Trước đây reaper và vòng xử lý dùng chung 1 luồng nên không bao giờ chồng nhau trong một JVM. Lượt chạy quá `stale-timeout` 15 phút sẽ bị reap khi worker còn sống, rơi vào kịch bản "worker zombie" mà ROADMAP ghi là giới hạn đã biết. Plan Mode tính trần thời gian một lượt (số tiêu chí × 2 lần gọi × 30 s, cộng phần SDK tự thử lại) rồi chọn: giữ dưới stale-timeout, hoặc ghi rõ là chấp nhận |
| 3 | A: chặn race embedding | Theo tiền lệ `CandidateProfileRepository.updateEmbeddingIfUnchanged`: thêm `ResumeParsedDataRepository.updateEmbeddingIfUnchanged(id, embeddingText, expectedParsedAt)` | Cùng khuôn, cùng tên với code đã có; ROADMAP đã ghi nợ đúng race này. Cặp tính kinh nghiệm ↔ trích xuất lại đã an toàn vì `writeExperienceIfPending` là UPDATE có điều kiện |
| 4 | Kỳ vọng 50–60 s | Ghi "giảm phần chờ luồng và chờ nhịp quét", ước lượng khoảng 60–65 s | Sàn = khoảng 44 s chấm tiêu chí + thời gian gọi LLM giải thích. Con số 24 s cũ của bước giải thích lẫn cả thời gian chờ nhịp quét |
| 5 | Nút chấm tất cả: "Chưa có lượt `DONE` (chưa chấm, hoặc lượt gần nhất `FAILED`)" | "Không có lượt `DONE` nào". Frontend dùng `totalScore === null` và lọc `recruitmentCycle === job.recruitmentCycle` | Câu cũ tự mâu thuẫn: một đơn có thể đã `DONE`, chấm lại thì `FAILED`. Danh sách ứng viên của tin hiện chứa đơn của mọi chu kỳ |

## FR-U16 — Lưu tin tuyển dụng

Ứng viên lưu tin để xem lại ở trang "Tin đã lưu", chỉ ứng tuyển được tin còn mở và bỏ lưu bất kỳ lúc nào. Không dùng AI; thêm một bảng (V20), package `savedjob/`.

**Luồng người dùng**

1. Ứng viên bấm biểu tượng Lưu trên thẻ tin (`JobCard`, dùng ở danh sách Việc làm, kết quả tìm bằng mô tả FR-U13, gợi ý FR-U15) hoặc ở trang chi tiết `/jobs/:id`.
2. Mở "Tin đã lưu": mục mới trong menu ứng viên, route `/candidate/saved-jobs`, tin lưu gần nhất ở trên.
3. Tin còn mở có nút "Ứng tuyển" dẫn về `/jobs/:id/apply` (luồng hiện có: chọn CV, câu hỏi sàng lọc, consent). Tin không còn mở thì nút bị khoá, kèm câu lý do.
4. Bấm "Bỏ lưu" ở thẻ tin hoặc ở trang Tin đã lưu, tin rời khỏi danh sách.

**Quy tắc nghiệp vụ**

| Quy tắc | Nội dung |
| --- | --- |
| R1 Ai được lưu | Chỉ tài khoản ứng viên. HR không thấy nút; khách bấm thì chuyển sang đăng nhập, theo mẫu `ApplyButton` |
| R2 Tin được lưu | Chỉ tin đang hiển thị công khai (`findOpenJobById`: `OPEN`, chưa xoá mềm, chưa quá hạn). Tin khác → 409 |
| R3 Lưu lặp | Idempotent: lưu tin đã lưu trả thành công, không tạo dòng mới |
| R4 Bỏ lưu | Soft delete: đặt `deleted_at`. Lưu lại cùng tin thì xoá `deleted_at` và cập nhật `saved_at`. Mỗi cặp ứng viên–tin đúng một dòng |
| R5 Tình trạng tin | Backend tính, tái dùng `availabilityOf` + `JobAvailability` của FR-U08 (5 giá trị `OPEN`, `EXPIRED`, `PAUSED`, `CLOSED`, `UNAVAILABLE`), tách thành thành phần dùng chung. Không viết lại điều kiện ngày |
| R6 Ứng tuyển | Chỉ tin `OPEN` có nút Ứng tuyển. Backend `apply` đã chặn tin không mở bằng `findOpenJobById`; kho lưu không thêm đường ứng tuyển mới |
| R7 Đã ứng tuyển | Ứng viên đã có đơn ở chu kỳ hiện tại của tin → thay nút bằng "Đã ứng tuyển" + liên kết `/candidate/applications/:id` |
| R8 Tin đã gỡ | Vẫn hiện tên tin và công ty với nhãn "Không còn đăng", không liên kết sang trang chi tiết; vẫn bỏ lưu được |
| R9 Trần | Tối đa 100 tin đang lưu; vượt → 409 kèm câu tiếng Việt có dấu |
| R10 Giao diện | Nhãn tình trạng tin dùng một kiểu trung tính cho cả 5 giá trị, tái dùng nhãn của FR-U08; không đỏ/xanh |

**Dữ liệu (V20)**: bảng `saved_jobs(id, candidate_id → users, job_id → jobs, saved_at, deleted_at)`, `UNIQUE (candidate_id, job_id)`, index `(candidate_id, saved_at DESC) WHERE deleted_at IS NULL`.

**API đề xuất** (Plan Mode chốt tên)

- `PUT /api/candidates/saved-jobs/{jobId}`: lưu.
- `DELETE /api/candidates/saved-jobs/{jobId}`: bỏ lưu, thực chất là đặt `deleted_at`, idempotent.
- `GET /api/candidates/saved-jobs`: danh sách kèm `availability` và đơn ở chu kỳ hiện tại; một truy vấn, không N+1.
- `GET /api/candidates/saved-jobs/ids`: id các tin đang lưu, để thẻ tin tô biểu tượng. Không thêm cờ `saved` vào API công khai của tin.

**Ngoài phạm vi**: HR xem ai đã lưu tin; nhắc hạn nộp; dùng tin đã lưu cho gợi ý FR-U04/U15; thư mục, ghi chú, chia sẻ tin.

**Kiểm thử chính**: lưu tin `OPEN` → thành công, tin `EXPIRED`/`PAUSED`/`CLOSED`/đã xoá → 409; lưu hai lần vẫn một dòng; bỏ lưu rồi lưu lại vẫn một dòng; biên trần 99/100/101; quyền: khách 401, HR 403, không đọc được tin đã lưu của người khác; tình trạng tin theo `CURRENT_DATE` của DB (hạn nộp hôm qua, hôm nay, ngày mai).

**AI hay làm sai**: xoá cứng khi bỏ lưu; thêm cờ `saved` vào API công khai; tính hạn nộp bằng `LocalDate.now()`; dựng form ứng tuyển thứ hai trong kho lưu; tô màu tình trạng tin theo tốt/xấu.

## FR-C09 — Yêu cầu xem xét lại đơn bị từ chối

Ứng viên có đơn bị từ chối ở vòng hồ sơ được gửi một yêu cầu xem xét lại trong 7 ngày; HR mời phỏng vấn hoặc giữ quyết định từ chối. Đơn vẫn đúng 5 trạng thái; yêu cầu nằm ở bảng riêng (V21), package `reconsideration/`. Không dùng AI.

```text
Đơn ứng tuyển (vẫn đúng 5 trạng thái; chỉ vẽ phần liên quan)
  PENDING --HR từ chối--> REJECTED ==cạnh mới, chỉ qua C09==> INTERVIEW_INVITED
                             |                                        ^
                             | ứng viên gửi, lý do tuỳ chọn           | cùng transaction
                             | từ vòng hồ sơ · ≤ 7 ngày · 1 lần       |
                             v                                        |
Yêu cầu xem xét lại          OPEN --HR "Mời phỏng vấn" (H12)--> ACCEPTED
(bảng mới, V21)                \
                                --HR "Giữ từ chối"-------------> DECLINED (đơn vẫn REJECTED)
```

Cạnh `REJECTED → INTERVIEW_INVITED` là thay đổi duy nhất ở vòng đời đơn: nó chỉ chạy khi HR chấp nhận yêu cầu, trong cùng transaction với giấy mời.

**Luồng ứng viên**

1. Ở trang chi tiết đơn `/candidate/applications/:id` (FR-U08), đơn đủ điều kiện có nút "Yêu cầu xem xét lại" kèm câu cho biết hạn chót (ngày, giờ).
2. Hộp thoại có ô lý do không bắt buộc, tối đa 1000 ký tự → Gửi. Sau khi gửi không sửa, không huỷ.
3. Danh sách đơn và chi tiết đơn hiện "Bị từ chối · Đang chờ xem xét lại". Khi HR quyết xong, chi tiết đơn hiện kết quả và phản hồi (nếu có).

**Luồng HR**

1. Trang tin `/hr/jobs/:id/edit` có tab mới "Yêu cầu xem xét lại", kèm số yêu cầu đang chờ.
2. Mỗi dòng: tên ứng viên (hoặc nhãn "Ứng viên #…" khi ẩn danh), ngày nộp đơn, ngày bị từ chối, ngày gửi yêu cầu, lý do, liên kết "Mở hồ sơ" sang FR-H09.
3. "Mời phỏng vấn" mở đúng hộp giấy mời FR-H12 (1–5 khung giờ). Gửi thành công thì yêu cầu thành `ACCEPTED` và đơn thành `INTERVIEW_INVITED` trong cùng transaction.
4. "Giữ quyết định từ chối" mở hộp thoại phản hồi không bắt buộc (tối đa 1000 ký tự). Yêu cầu thành `DECLINED`, đơn giữ `REJECTED`.
5. Trang hồ sơ đơn FR-H09 hiện khối yêu cầu đang chờ với cùng hai nút, dùng chung hộp thoại với tab.

**Quy tắc nghiệp vụ**

| Quy tắc | Nội dung |
| --- | --- |
| R1 Điều kiện gửi | Đơn của chính ứng viên; trạng thái `REJECTED`; dòng lịch sử chuyển sang `REJECTED` có `from_status = PENDING`; đơn chưa từng có yêu cầu |
| R2 Hạn 7 ngày | Tính từ `changed_at` của dòng lịch sử `PENDING → REJECTED`, đúng 7 × 24 giờ theo giờ DB (`now()`), không theo giờ JVM. Quá hạn → 409 |
| R3 Tình trạng tin | Tin quá hạn nộp, tạm dừng, đã đóng vẫn gửi được. Tin đã xoá mềm: đề xuất chặn (409), chốt ở `spec-review` |
| R4 Một lần | `UNIQUE (application_id)` ở DB; service kiểm trước chỉ để trả 409 sớm. Bị giữ từ chối thì không gửi tiếp |
| R5 Chuyển trạng thái | Thêm đúng một cạnh `REJECTED → INTERVIEW_INVITED`, chỉ qua giấy mời FR-H12 và chỉ khi đơn có yêu cầu `OPEN`. Không thêm cạnh này vào bảng chuyển tiếp chung cho mọi đơn |
| R6 Bấm trùng | Quyết định bằng UPDATE có điều kiện `status = 'OPEN'`; bấm lần hai hoặc mở hai tab → 409 |
| R7 Lý do, phản hồi | Văn bản thuần, lưu nguyên văn, tối đa 1000 ký tự mỗi ô, xử lý giống nội dung tin nhắn FR-C06. Phản hồi là nội dung gửi ứng viên, UI phải ghi rõ, không phải ghi chú nội bộ |
| R8 Ẩn danh (FR-H16) | Đơn đang ẩn: lý do hiển thị cho HR qua `IdentityMasker`, danh sách dùng nhãn "Ứng viên #…". Mời phỏng vấn xong thì đơn hết ẩn theo luật FR-H16 hiện có |
| R9 Phía ứng viên | Chỉ thấy yêu cầu, trạng thái và phản hồi của chính mình; không thấy điểm, rubric, giải thích AI, ghi chú nội bộ |
| R10 Thông báo | Loại mới `RECONSIDERATION_REQUESTED` (tới HR chủ công ty) và `RECONSIDERATION_DECLINED` (tới ứng viên). Chấp nhận dùng thông báo đổi trạng thái sẵn có, không phát thêm. Cột `notifications.type` không có CHECK nên không cần migration cho loại mới |
| R11 Không AI | Không AI gợi ý chấp nhận hay từ chối, không AI soạn phản hồi |

**Dữ liệu (V21)**: bảng `application_reconsideration_requests(id, application_id UNIQUE → job_applications, reason, status OPEN|ACCEPTED|DECLINED, hr_response, requested_at, decided_at, decided_by → users)`. Có thêm `job_id` để lọc theo tin hay không: Plan Mode quyết.

**API đề xuất** (Plan Mode chốt tên)

- `POST /api/candidates/applications/{id}/reconsideration-request`, body `{ reason? }`.
- `GET /api/hr/jobs/{jobId}/reconsideration-requests?status=OPEN`.
- `POST /api/hr/applications/{id}/reconsideration-request/decline`, body `{ response? }`.
- Chấp nhận: dùng `POST /api/hr/applications/{id}/interview-invitation` hiện có; backend nhận ra đơn `REJECTED` có yêu cầu `OPEN`.
- Chi tiết đơn phía ứng viên (FR-U08) và hồ sơ đơn phía HR (FR-H09) trả thêm khối `reconsideration`.

**Phải sửa cùng commit đặc tả** (nếu không, Plan Mode sẽ dừng vì mâu thuẫn): `CLAUDE.md` §2 câu vòng đời đơn; `docs/SRS.md` dòng FR-H07 và câu "Vòng đời ứng tuyển gồm 5 trạng thái" ở mục nguyên tắc. Cả hai giữ "5 trạng thái", chỉ nêu thêm ngoại lệ của FR-C09.

**Ngoài phạm vi**: yêu cầu cho đơn bị từ chối sau phỏng vấn; ứng viên sửa hoặc huỷ yêu cầu; AI hỗ trợ; thống kê yêu cầu trên dashboard FR-H08. Funnel "đã từng mời" của FR-H08 tự tính cả đơn được mời lại; đặc tả ghi rõ điều này.

**Kiểm thử chính**: biên hạn 7 ngày (−1 s, đúng hạn, +1 s, theo giờ DB); đơn bị từ chối sau phỏng vấn → 409; gửi lần hai → 409; quyền (ứng viên khác 404, HR 403, HR công ty khác 403); gửi giấy mời cho đơn `REJECTED` không có yêu cầu `OPEN` → vẫn lỗi chuyển trạng thái như cũ; hai quyết định đồng thời; ẩn danh: lý do chứa họ tên bị che; response phía ứng viên không có trường nội bộ.

**AI hay làm sai**: thêm giá trị thứ 6 vào `ApplicationStatus`; thêm `REJECTED → INTERVIEW_INVITED` vào `ALLOWED_TRANSITIONS` cho mọi đơn; tính 7 ngày bằng `LocalDate` hoặc giờ JVM; chặn "một lần" chỉ bằng `exists` ở service; lộ họ tên trong lý do khi ẩn danh; phát hai thông báo khi chấp nhận.

## Tài liệu chung phải sửa

Hôm nay sửa phần đăng ký FR (đợt D1) và đặc tả (D2–D4); phần mô tả code, sơ đồ dữ liệu và seed sửa ở đợt cuối của từng nhánh.

| File | Thay đổi | Đợt |
| --- | --- | --- |
| `docs/plans/2026-11-dot-bo-sung.md` (mới) | Bản xuất Markdown của kế hoạch này | D1 |
| `docs/SRS.md` mục 0 | Thêm FR-C09, FR-U16 với trạng thái "Chưa hoàn thành"; 37 → 39 mã; câu mở đầu "cả 37 đã hoàn thành" sửa cho đúng | D1 |
| `docs/SRS.md` bảng chức năng bổ sung | FR-C09 vào bảng của mục 1, FR-U16 vào bảng của mục 3 | D1 |
| `docs/SRS.md` mục 4 | Dòng ảnh hưởng: FR-H07, FR-U03, FR-U08, FR-H09, FR-H12, FR-C03 ← FR-C09; FR-C02, FR-U07, FR-U13, FR-U15 ← FR-U16; FR-H04 ← nút chấm tất cả | D1 |
| `CLAUDE.md` §1 | 37 → 39 mã, 19 → 21 FR bổ sung | D1 |
| `docs/features/README.md` | Phase 2.7 (thứ tự, nhánh, phụ thuộc) + mô tả định hướng FR-U16, FR-C09, FR-H04 mở rộng, theo đúng khuôn các FR cũ | D1 |
| `docs/features/UV/U16/`, `CHUNG/C09/`, `HR/H04/` | Khung `REQUIREMENT.md` + `UI.md` trạng thái `CHƯA ĐẶC TẢ`, theo tiền lệ nhánh `docs/features-structure` | D1 |
| `docs/ROADMAP.md` | Mục "Đợt bổ sung 11/2026" với checkbox 4 nhánh + nhánh tài liệu | D1 |
| `docs/SRS.md` dòng FR-H04 | Một câu trỏ tới `features/HR/H04/` | D2 |
| `CLAUDE.md` §2 + `docs/SRS.md` FR-H07 và mục nguyên tắc | Giữ "5 trạng thái", thêm ngoại lệ cạnh `REJECTED → INTERVIEW_INVITED` chỉ qua FR-C09 | D4 |
| `.claude/skills/srs-guard/SKILL.md` | Tuỳ chọn: nguyên tắc mới "cạnh `REJECTED → INTERVIEW_INVITED` chỉ qua yêu cầu `OPEN`"; "19 FR mới" → 21 | D4 |
| `CLAUDE.md` §3 danh sách package | `savedjob/`, `reconsideration/` | Đợt cuối U16, C09 |
| `docs/UI_GUIDE.md` mục 7 | Màn hình `/candidate/saved-jobs`; tab "Yêu cầu xem xét lại" của trang tin HR | Đợt cuối U16, C09 |
| `docs/ER.md` | Thêm 2 bảng, 35 → 37 bảng | Đợt cuối U16, C09 |
| `db/seed/reset-demo-db.sql` + `db/seed/README.md` | Danh sách bảng đến V21, tiền đề "DB ở V21" | Đợt cuối U16, C09 |
| `db/seed/reset-test-data.sql` | Thêm câu xoá bảng mới trước khi xoá tin, đơn, người dùng test; nếu không, khoá ngoại sẽ chặn reset | Đợt cuối U16, C09 |
| `docs/DEMO.md` | Số đo mới sau nhánh perf; kịch bản lưu tin và xem xét lại | Sau perf; đợt cuối U16, C09 |

## Thứ tự làm, migration, seed demo, chia việc

Làm tuần tự, mỗi nhánh merge xong mới mở nhánh code kế tiếp. Nếu không kịp trước buổi gặp thầy tuần 2 tháng 11 thì cắt nút chấm tất cả trước.

1. **Tài liệu (29–30/10)**: đợt D1 merge vào `main`; D2–D4 commit đặc tả đã duyệt trên nhánh FR của mình, chưa merge.
2. **`perf/scoring-latency`**: đo mốc trước → Plan Mode → đợt A + B → đo mốc sau → merge. Báo Huy trước khi merge vì đổi job embedding (liên quan FR-U04).
3. **`feat/fr-u16-saved-jobs`** (V20): rebase lên `main` mới nhất rồi code. Merge **trước** FR-C09.
4. **`feat/fr-c09-reconsideration`** (V21): rebase lên `main` sau khi U16 đã merge.
5. **`feat/fr-h04-bulk-scoring`**: không migration; làm nếu còn thời gian.

**Migration**: số đã chốt trước để hai nhánh không giành nhau. Flyway không nhận V20 khi DB đã ở V21, nên đổi thứ tự merge thì phải đổi số.

**Seed demo**: không sửa tầng 2/2b (output AI thật, không idempotent). Thêm một tầng cấu trúc mới, nạp sau 2b, không gọi AI:

- 3–4 tin đã lưu cho tài khoản ứng viên demo, có 1 tin quá hạn nộp (hạn = `CURRENT_DATE - 1` lúc nạp).
- 1 đơn bị từ chối ở vòng hồ sơ 2 ngày trước lúc nạp, kèm yêu cầu đang chờ, để HR thao tác trực tiếp khi demo. Thời điểm tính theo `now()` lúc nạp vì hạn 7 ngày.
- `seed-test.sql` (soát tay, không nạp khi demo) thêm đơn bị từ chối 6 ngày 23 giờ, 8 ngày, và bị từ chối sau phỏng vấn.

**Chia việc**: Đạt làm cả 4 nhánh; Huy review chéo, vì FR-U16 đụng `JobCard`, trang chi tiết tin, menu ứng viên và FR-C09 đụng FR-U08, FR-H07/H12, thông báo FR-C03, đều là phần Huy làm trước đây.

**Báo cáo**: bổ sung use case và ERD cho FR-U16, FR-C09 trước khi gửi báo cáo cuối cho thầy.

## Các đợt tài liệu và prompt cho Claude Code

Bốn đợt, mỗi đợt một phiên Claude Code, gửi từng prompt một. Không đợt nào sửa code. Nhánh perf không có đợt tài liệu: Plan Mode của nó đọc kế hoạch 28/10 cùng mục sửa đổi ở trên.

| Đợt | Nhánh | Việc | Dừng ở |
| --- | --- | --- | --- |
| D1 | `docs/plan-2026-11` | Thêm file kế hoạch; đăng ký FR-U16, FR-C09, FR-H04 mở rộng vào SRS, `features/README.md`, ROADMAP, `CLAUDE.md` §1; tạo khung đặc tả `CHƯA ĐẶC TẢ` | Báo cáo diff → duyệt → commit → push, PR, merge |
| D2 | `feat/fr-h04-bulk-scoring` | `REQUIREMENT.md` + `UI.md` cho nút chấm tất cả theo kế hoạch 28/10 + sửa đổi số 5; câu trỏ ở SRS FR-H04; chạy `spec-review` | Bạn ghi `ĐÃ DUYỆT <ngày>` → commit đặc tả |
| D3 | `feat/fr-u16-saved-jobs` | Đặc tả FR-U16 theo mục FR-U16 ở trên; `spec-review` | Như D2 |
| D4 | `feat/fr-c09-reconsideration` | Đặc tả FR-C09; sửa `CLAUDE.md` §2, SRS FR-H07 và mục nguyên tắc; tuỳ chọn `srs-guard`; `spec-review` | Như D2 |

D2–D4 tách nhánh từ `main` sau khi D1 đã merge, để file khung và bảng SRS có sẵn. Hết hạn mức Claude Code thì Gemini làm D1 (chỉ sửa tài liệu), Claude Code soát lại.

## Rủi ro và câu hỏi còn mở

Rủi ro lớn nhất là thời gian: 4 nhánh trong khoảng 10 ngày trước buổi gặp thầy.

| Rủi ro | Khả năng | Cách xử lý |
| --- | --- | --- |
| Không kịp 4 nhánh trước buổi gặp thầy | Trung bình | Cắt nút chấm tất cả trước; FR-C09 chưa xong thì trình bày đặc tả đã duyệt |
| Claude Code dừng ở Plan Mode FR-C09 vì mâu thuẫn "5 trạng thái" | Cao nếu quên | Sửa `CLAUDE.md` §2 và SRS FR-H07 ngay trong commit đặc tả D4 |
| Flyway lệch số khi merge sai thứ tự | Thấp | FR-U16 merge trước FR-C09 |
| Reset demo hoặc reset dữ liệu test hỏng vì bảng mới | Trung bình | Cập nhật hai script reset; chạy thử trọn quy trình nạp demo một lần trước buổi gặp |
| Reaper chạy chồng worker sau khi tăng số luồng | Thấp | Sửa đổi số 2 ở mục kế hoạch chấm điểm |
| Hạn 7 ngày lệch vì múi giờ JVM khác DB | Trung bình | Tính bằng `now()` của DB; test biên −1 s, đúng hạn, +1 s |

**Câu hỏi chốt ở `spec-review`** (đã có đề xuất mặc định)

- [ ] FR-C09: tin đã xoá mềm thì chặn gửi yêu cầu? Đề xuất: chặn. Yêu cầu đang chờ khi tin bị gỡ sau đó: HR vẫn xử lý được từ trang hồ sơ đơn nếu trang đó còn mở được — Plan Mode kiểm.
- [ ] FR-C09: ứng viên có được huỷ yêu cầu đang chờ không? Đề xuất: không.
- [ ] FR-U16: trần 100 tin đang lưu có hợp lý không? Đề xuất: giữ.
- [ ] Báo Huy sau khi chốt kế hoạch, trước khi merge nhánh perf (việc còn mở từ 28/10).
