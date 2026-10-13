# Walkthrough — FR-H11: AI tạo tin tuyển dụng từ ngôn ngữ tự nhiên (`feat/fr-h11-ai-job-draft`)

## 1. Mục tiêu

HR mô tả vị trí cần tuyển bằng lời thường. Hệ thống gọi AI để gợi ý ba thứ: bản nháp tin (tiêu đề, mô tả, yêu cầu,
quyền lợi, lương, địa điểm, loại hình, hình thức, ngành nghề, hạn nộp), tiêu chí đánh giá hồ sơ (chỉ tên và mô tả) và
câu hỏi sàng lọc. HR xem từng gợi ý, điền vào biểu mẫu, giữ hoặc bỏ từng tiêu chí/câu hỏi, **tự nhập trọng số**, rồi bấm
"Lưu nháp". Không có gì được lưu trước lúc đó; tin luôn ở trạng thái Nháp. Lúc lưu, hệ thống ghi lại phần nào của tin
đến từ gợi ý AI, model nào, prompt phiên bản nào, sinh lúc nào (thành phần dùng chung K4, FR này xây lần đầu).

## 2. Các file đã tạo/sửa

**Backend — K4 (`aigeneration/`, dùng chung cho H11/H13/H15)**

| File | Vai trò |
|---|---|
| `aigeneration/AiGenerationStamp.java` | Record `@Embeddable` 3 giá trị K4 (model, phiên bản prompt, thời điểm sinh); bảng của FR nhúng 3 cột này |
| `aigeneration/AiGenerationTokenCodec.java` | Ký và kiểm token K4 (JWT HMAC, khoá dẫn xuất từ `JWT_SECRET`, hạn 7 ngày) |
| `db/migration/V15__job_ai_generations.sql` | Bảng `job_ai_generations` (≤ 3 dòng/tin, bất biến, `UNIQUE (job_id, part)`) |
| `jobdraft/JobAiGeneration.java`, `JobAiGenerationRepository.java` | Entity `@Immutable` + repository của bảng trên |

**Backend — lớp AI (`ai/jobdraft/`, `ai/client/`, prompt)**

| File | Vai trò |
|---|---|
| `ai/jobdraft/JobAiDraftService.java` | 3 method `draftJob`/`draftCriteria`/`draftQuestions`, mỗi method đúng một lần gọi K3; dựng user message có thẻ cô lập |
| `ai/jobdraft/JobDraftOutputValidators.java` | Điều kiện làm hỏng output (độ dài, lương, tiền tệ, ≤ 6 tiêu chí, ≤ 5 câu, trùng) |
| `ai/jobdraft/*Payload.java`, `DraftEmploymentType`, `DraftWorkMode`, `IndustryOption`, `JobAiDraftResult` | Schema JSON cố định AI trả về — không có trọng số, điểm, `required` |
| `ai/client/JobAiDraftChatClientConfig.java` | Bean `jobAiDraftChatClient`, trần 800 token đầu ra |
| `resources/ai/prompt/job-draft-{job,criteria,questions}-v1.st` | Ba prompt, chỉ tham số `{format}` (+ `{industries}` ở JOB) |

**Backend — điều phối và API (`jobdraft/`, sửa file có sẵn)**

| File | Vai trò |
|---|---|
| `jobdraft/JobAiDraftController.java` | J1 `POST /api/hr/job-ai-drafts` (200), J2 `POST /api/hr/job-ai-drafts/save` (201) |
| `jobdraft/JobAiDraftFacade.java` | J1: kiểm đầu vào, kiểm transaction, chạy các phần song song, R-P4, R-M, ký token. J2: kiểm tiêu chí/câu hỏi/token trước transaction |
| `jobdraft/JobAiDraftSaveService.java` | J2: một `@Transactional` gọi lại `JobOwnerService.create`, `RubricOwnerService.addCriterion`, `ScreeningQuestionOwnerService.add`, ghi dòng K4 |
| `jobdraft/JobAiDraftExceptionAdvice.java` | Body JSON hỏng của J1/J2 → 400 `INVALID_JOB_DRAFT_REQUEST`, chỉ áp cho controller này |
| `jobdraft/dto/*`, `PartStatus`, `JobDraftField`, `FieldNeed`, `JobDraftPart` | DTO J1/J2 và enum |
| `common/exception/InvalidJobDraftRequestException`, `InvalidJobDraftSaveException`, `InvalidAiGenerationTokenException` + `GlobalExceptionHandler` | Mã lỗi 400 kèm câu tiếng Việt cố định |
| `screening/ScreeningQuestionRules.java` (mới) + `ScreeningQuestionOwnerService` (sửa) | Tách quy tắc kiểm câu hỏi FR-H10 thành lớp dùng chung; FR-H10 gọi lại, hành vi không đổi |
| `ratelimit/RateLimitFilter.java` (sửa) | Thêm đường dẫn J1 (so khớp chính xác) vào nhóm `ai-sync` |

**Frontend**

| File | Vai trò |
|---|---|
| `features/jobDraft/api.ts`, `queries.ts`, `types.ts` | Một hàm J1 (timeout 35 s), một hàm J2; kiểu khớp DTO backend |
| `features/jobDraft/useJobAiDraft.ts` | State chế độ AI: trạng thái từng phần, gợi ý/đã giữ, token đã dùng, giá trị AI đã điền |
| `features/jobDraft/AiDraftInputPanel.tsx` | Khối "Tạo tin bằng AI" (nhập mô tả, dòng trạng thái từng phần, lỗi, "Thử lại") |
| `features/jobDraft/PostingSuggestionBlock.tsx`, `MissingFieldsCard.tsx`, `AiSuggestedMark.tsx`, `OverwriteFieldsDialog.tsx` | Khối xem trước bản nháp tin, khối "Cần bổ sung", dấu "Do AI gợi ý", hộp thoại ghi đè |
| `features/jobDraft/CriteriaSuggestionStep.tsx`, `ScreeningSuggestionStep.tsx` | Bước 3 (tiêu chí, ô trọng số trống) và bước 4 (câu hỏi) |
| `features/jobDraft/jobDraftLabels.ts`, `jobDraftValidation.ts` | Chữ hiển thị (UI.md mục 7) và hàm kiểm phía trình duyệt |
| `pages/HrJobCreatePage.tsx` (sửa) | Chế độ AI 5 bước bên cạnh luồng thủ công 3 bước; sửa lỗi `Select` Loại hình/Hình thức |

## 3. Luồng chính

### 3.1 J1 — sinh bản nháp (một request, ba phần song song)

1. HR nhập mô tả, bấm "Tạo bản nháp". `useJobAiDraft.generate(['JOB','CRITERIA','QUESTIONS'])` gửi **một**
   `POST /api/hr/job-ai-drafts` với `{description, parts}`. Trong lúc chờ, mọi nút sinh bị khoá.
2. `RateLimitFilter` phân loại theo đường dẫn (không đọc body) → trừ **1 lượt** nhóm `ai-sync` của HR (sức chứa 5,
   nạp 2 lượt/phút), bất kể 1 hay 3 phần.
3. `JobAiDraftController.generate` lấy `userId` từ JWT, gọi `JobAiDraftFacade.generate`.
4. Facade, **trên luồng request**: kiểm HR có công ty (404), mô tả (rỗng/> 3000 → 400), `parts` (rỗng/trùng/null →
   400), rồi kiểm `TransactionSynchronizationManager.isActualTransactionActive()` — đang có transaction thì ném
   `IllegalStateException` trước khi gọi AI.
5. Facade mở `Executors.newVirtualThreadPerTaskExecutor()` (tạo theo request, đóng bằng try-with-resources) và nộp mỗi
   phần một tác vụ. `userId` truyền tham số vào tác vụ (luồng phụ không có `SecurityContext`).
6. Mỗi tác vụ gọi `JobAiDraftService.draftX` → `SyncAiCaller.call` (K3): lời gọi `ChatModel` thật chạy trên executor
   luồng thường của K3, hạn 15 s, thử lại tối đa 1 lần khi output hỏng (validator ở bước này là
   `JobDraftOutputValidators`).
7. Thành công: facade xử lý kết quả (phần JOB: bỏ mã ngành lạ/`OTHER`, kiểm địa danh có nằm trong mô tả rồi ánh xạ
   tỉnh bằng `CatalogRegistry.matchProvince`, bỏ hạn nộp đã qua, tính `missingFields`), lấy `model` từ metadata
   response, `promptVersion` từ hằng, `generatedAt` từ `Clock`, **ký token K4** riêng cho phần đó. Lỗi K3
   (`AiSyncFailedException`) → ô `FAILED` với mã + câu cố định. Exception khác nổi lên thành 500.
8. Đóng executor = chờ mọi phần xong; trả **200** `{job, criteria, questions}` — phần không yêu cầu là `null`. Không
   ghi bảng nào.
9. Frontend cập nhật từng phần riêng: phần thành công thay gợi ý chưa chọn, phần lỗi giữ gợi ý cũ và hiện "Thử lại".

### 3.2 HR dùng gợi ý trên biểu mẫu (chỉ frontend)

- "Điền vào biểu mẫu": ghi các trường AI trả khác `null` vào form; nếu có trường HR đã sửa tay thì hỏi trước
  (`OverwriteFieldsDialog`). Ghi lại token JOB là "đã dùng".
- Bước 3/4: "Giữ" chuyển gợi ý xuống danh sách đã giữ (ghi lại token của lần sinh đó); ô trọng số trống, HR tự nhập.

### 3.3 J2 — lưu nháp

1. "Lưu nháp": kiểm Zod của FR-H02 + trọng số mọi tiêu chí đã giữ + tổng ≤ 100; lỗi thì chuyển tới bước đầu tiên có
   lỗi. Hợp lệ → `POST /api/hr/job-ai-drafts/save` kèm 3 token nguyên văn (`null` nếu phần đó không dùng).
2. Controller: `@Valid` cho `job`, `interviewTemplate` (như `POST /api/hr/jobs`) → facade `save`.
3. Facade, **trước transaction**: công ty (404) → danh sách tiêu chí (≤ 10, tên, trọng số null/ngoài [0.01, 100],
   trùng tên, tổng > 100) → câu hỏi (≤ 10, loại null, quy tắc FR-H10 qua `ScreeningQuestionRules`) → từng token
   (`AiGenerationTokenCodec.verify` với `sub` = HR đang gọi, `purpose` đúng ô). Sai ở đâu dừng ở đó, chưa ghi gì.
   Ghép quyền lợi vào cuối mô tả (`"\n\nQuyền lợi:\n"`).
4. `JobAiDraftSaveService.save` (một transaction): `JobOwnerService.create` (Job + Rubric + Mẫu giấy mời, `DRAFT`) →
   `entityManager.flush()` → `addCriterion` từng tiêu chí → `ScreeningQuestionOwnerService.add` từng câu (tăng
   `jobs.screening_revision`) → `saveAndFlush` từng dòng `job_ai_generations` với giá trị **lấy từ token**.
5. Lỗi ở bất kỳ bước nào trong transaction → rollback toàn bộ. Thành công → 201 `JobOwnerResponse`, frontend chuyển
   `/hr/jobs/{id}/edit?tab=rubric&created=1`.

## 4. Quyết định thiết kế

**K4 ký token lúc sinh, không lưu gì trước khi HR bấm Lưu.**
- Đã chọn: khi một phần thành công, backend ký model/phiên bản prompt/thời điểm sinh vào một JWT riêng của phần đó;
  J2 kiểm chữ ký rồi ghi đúng các giá trị trong token.
- Khác: (a) client gửi lại model/promptVersion dạng trường thường — giả mạo được; (b) lưu bản ghi "phiên sinh" ở
  server lúc J1 — trái quy tắc "không lưu gì trước khi bấm Lưu" (REQUIREMENT mục 2, mục 6) và cần dọn rác.
- Vì sao: vừa không lưu gì, vừa không tin client. Khoá dẫn xuất `HMAC-SHA256(JWT_SECRET, "k4-ai-generation-token-v1")`
  nên không thêm biến môi trường và token K4 không dùng làm token đăng nhập được (hai lớp chặn: khác khoá, và filter chỉ
  nhận claim `type = access`). Test: `AiGenerationTokenCodecTest`, `AiGenerationTokenBearerIntegrationTest`.

**Một request, các phần chạy song song bằng virtual thread.**
- Đã chọn: J1 nhận `parts`; mỗi phần một lần gọi K3, chạy song song trên executor virtual thread tạo theo request.
- Khác: (a) gộp 3 phần vào một lần gọi — đầu ra ~1800–2200 token, 23–37 s, vượt hạn 15 s (REQUIREMENT mục 5.1);
  (b) ba endpoint/ba request — mỗi lần tạo tốn 3 lượt `ai-sync`, lần "Tạo lại" thứ hai trong một phút bị 429 một phần,
  biểu mẫu nửa cũ nửa mới; (c) chạy tuần tự — chờ tới ~90 s.
- Vì sao: thời gian chờ ≈ phần chậm nhất, mỗi lần tạo đúng 1 lượt, và lỗi một phần không làm hỏng phần khác (luôn
  200, lỗi nằm trong ô). Executor không là bean Spring (bean `Executor` sẽ tắt `applicationTaskExecutor`, C07 L9).
  Test: `JobAiDraftFacadeConcurrencyTest` (3 phần chờ 1 s xong < 2,5 s; executor K3 2 luồng → đúng 1 phần
  `AI_UNAVAILABLE`), `RateLimitFilterTest.doFilter_jobAiDraft_oneRequestIsOneToken_5thPasses_6thRejected`.

**Kiểm transaction trên luồng request trước khi tách luồng.**
- Đã chọn: facade tự kiểm `isActualTransactionActive()` rồi mới tạo virtual thread; facade không `@Transactional`.
- Khác: dựa vào kiểm sẵn có trong `SyncAiCaller.call`.
- Vì sao: kiểm của K3 chạy trên luồng gọi `call` — ở đây là virtual thread, không thấy transaction của luồng request
  (transaction gắn với `ThreadLocal`). Không kiểm ở facade thì một nơi gọi lỡ bọc transaction sẽ giữ kết nối DB suốt
  thời gian chờ AI. Test: `JobAiDraftPartFailureIntegrationTest.facadeInsideTransaction_throwsIllegalState_noModelCall`
  (dùng `TransactionTemplate` thật, 0 lần gọi model).

**Trọng số luôn do HR nhập.**
- Đã chọn: schema AI không có trường trọng số; response J1 không có khoá `weight`; ô trọng số trên giao diện trống;
  J2 chặn tiêu chí chưa có trọng số và báo số thứ tự ("Tiêu chí 2: vui lòng nhập trọng số."), không tự bỏ tiêu chí.
- Khác: để AI gợi ý trọng số; chia đều mặc định; nới DB cho `weight` NULL.
- Vì sao: trọng số quyết định thứ hạng (FR-H05) — đó là quyết định của HR, không phải AI (CLAUDE.md §2). DB đã có
  `CHECK (weight > 0 AND weight <= 100)` nên không nới. Khoá `"weight"` do model tự thêm bị bỏ khi parse vì record
  không có chỗ chứa. Test: `JobAiDraftSignatureTest` (reflection, không thành phần tên trọng số/điểm),
  `JobAiDraftGenerateIntegrationTest.criteriaWithWeightKeys_responseHasNoWeight_andNoRubricRowsWritten`.

**J2 gọi lại service có sẵn trong một transaction, flush ngay sau `create` (L2).**
- Đã chọn: không chép logic tạo Job/tiêu chí/câu hỏi; `flush()` tường minh sau `JobOwnerService.create`.
- Vì sao: `create` dùng `save()` không flush, còn `incrementRevision` của FR-H10 là native `UPDATE` + `clear()`.
  Khi có câu hỏi mà không có tiêu chí, không flush thì INSERT Job có thể chưa chạy lúc `UPDATE jobs`. Test:
  `JobAiDraftSaveIntegrationTest.noCriteria_twoQuestions_allRowsWritten_revisionTwo` — **không được xoá khi refactor**,
  vì nó bảo vệ tương tác giữa FR-H02 (`create`) và FR-H10 (`incrementRevision`), không riêng FR-H11.

**L11 — cổng đo AI thật, chẩn đoán `AI_TIMEOUT`, không ép HTTP/1.1.**
- Gọi riêng từng phần qua backend: 15/15 thành công, trung vị 5,2–7,4 s. Ba phần song song qua backend, 24 request:
  `AI_TIMEOUT` JOB 2/24, CRITERIA 3/24, QUESTIONS 0/24 — mọi phần ≤ 1/5 → cổng đạt. Gọi thẳng Messages API (HTTP/1.1,
  mỗi lời gọi một kết nối) 30 lời gọi: 30/30 HTTP 200, chậm nhất 8,6 s. Token đầu ra thật 270–389 (< 50% trần 800).
- jstack lúc treo: luồng `ai-sync-*` chờ header phản hồi của một stream HTTP/2 (`Http2Stream.takeHeaders`) trong
  `RetryingHttpClient.execute`, không ở nhánh backoff; ba lời gọi đi chung một kết nối HTTP/2. Đã loại trừ job nền
  (tắt mọi scheduler vẫn có 2/10 timeout), executor K3, virtual thread, SDK thử lại ngầm.
- Chưa phân định được (i) ghép stream HTTP/2 hay (ii) độ trễ ngẫu nhiên phía Anthropic. Không ép HTTP/1.1 vì phải sửa
  bean `AnthropicChatModel` dùng chung (đụng D1/D2/D4/F2) hoặc thêm bean thứ hai (trái hướng một bean của C07 L1) khi
  chưa có bằng chứng. Dựa vào kết quả theo từng phần + nút "Thử lại" từng phần.

**Kiểm địa danh nằm trong mô tả là hàm riêng của facade, không phải K2 (Q9).** AI chép nguyên văn tên tỉnh vào
`locationText`; backend kiểm chuỗi đã chuẩn hoá có nằm trong mô tả của HR rồi mới ánh xạ bằng matcher chính xác có sẵn
của FR-C05. K2 là bộ kiểm trích dẫn CV — khác mục đích, nên không gộp.

## 5. Ràng buộc đã thực thi

| Mã | Ràng buộc | Thực thi ở đâu |
|---|---|---|
| R-N2, R-N3 | Mô tả ≤ 3000, `parts` hợp lệ, không gọi AI khi sai | `JobAiDraftFacade.normalizedDescription`, `requestedParts` |
| R-A2, R-A3 | Thứ tự kiểm J1; body hỏng → `INVALID_JOB_DRAFT_REQUEST` chỉ ở controller FR-H11 | `JobAiDraftFacade.generate`; `JobAiDraftExceptionAdvice` (`assignableTypes`) |
| R-K3H-1..3 | Một lần K3 mỗi phần; song song; kiểm transaction trên luồng request | `JobAiDraftService`; `JobAiDraftFacade.generate` |
| R-K3H-5, R-N6 | Chỉ lỗi K3 thành ô `FAILED`; luôn 200 | `JobAiDraftFacade.runPart`, `resultOf` |
| R-K3H-6 | J1 = 1 lượt `ai-sync`; `/save` không thuộc nhóm nào | `RateLimitFilter.classify` (`JOB_AI_DRAFT_PATH`) |
| R-P3, R-C2, R-Q2 | Output hỏng → thử lại 1 lần → `AI_INVALID_OUTPUT` | `JobDraftOutputValidators`; `ScreeningQuestionRules.normalize` |
| R-P4, R-P5, R-M | Vô hiệu trường; ánh xạ danh mục; trường thiếu do backend tính | `JobAiDraftFacade.toJobDraft`, `industryCode`, `locationCode` |
| R-I1..I3 | Mô tả HR chỉ ở user message trong thẻ, `<`/`>` đã thay | `JobAiDraftService.buildUserMessage` |
| R-L1, L2 | Một transaction, gọi lại service có sẵn, flush sau `create` | `JobAiDraftSaveService.save` |
| R-L3 | Tin luôn `DRAFT`, J2 không đổi trạng thái | `JobOwnerService.create`; DTO J2 không có trường trạng thái |
| R-L4, R-W2, R-W3 | Kiểm tiêu chí/câu hỏi/token trước transaction, báo số thứ tự | `JobAiDraftFacade.save`, `checkedCriteria`, `checkedQuestions` |
| R-P7 | Quyền lợi ghép vào mô tả ở backend | `JobAiDraftFacade.withBenefits` |
| R-K4-2, R-K4-4 | Token ký, kiểm `sub`/`purpose`/hạn; sai → 400, không ghi gì | `AiGenerationTokenCodec.issue`, `verify`; `JobAiDraftFacade.verifyToken` |
| R-K4-6 | K4 bất biến, ≤ 1 dòng mỗi phần | `@Immutable JobAiGeneration`; `uq_job_ai_generation_part`; CHECK trong V15 |
| SRS nguyên tắc bổ sung | AI không tự đăng tin; không hỏi nhân thân trong prompt | J1 không ghi DB; prompt chỉ câu chung "thông tin cá nhân, nhân thân" (test `promptFiles_noPersonalAttributeKeywords_noWeight`) |

## 6. Đã kiểm thử gì

**Tự động.** Full suite backend `.\mvnw.cmd test`: **1162 test, 0 lỗi** (137 lớp). FR-H11 thêm 15 lớp test mới (cùng lớp hỗ trợ trừu tượng `JobDraftIntegrationTestSupport`) và
sửa thêm `RateLimitFilterTest` (+4 ca T19). Frontend: `npm run build` (gồm `tsc -b`) và `npm run lint` sạch.

**Kiểm HTTP mục 7.2 (PowerShell 5.1, backend thật, khoá thật, 13/10/2026).** Nhóm 1 (403, 401), nhóm 3 (429, 201,
204), nhóm 4 (201 `DRAFT`, rubric 60, 1 câu `revision 1`, 409 `RUBRIC_INCOMPLETE`, 204) khớp. Nhóm 5: J1 ba phần 200
trong 8049 ms, ba ô `SUCCEEDED`, lương 15–20 triệu, `IT_SOFTWARE`/`DA_NANG`, không khoá `weight`; J1 chỉ CRITERIA 6856
ms, `job`/`questions` = `null`; J2 có token 201; bảng K4 đúng 3 dòng `job-draft-*-v1`; xoá mềm 204. **Hai dòng lệch:**
nhóm 2 dòng JSON hỏng vào `POST /api/hr/jobs` ra 401 (không phải 400 — hành vi có sẵn của server thật: trang lỗi
`/error` không mở cho request chưa xác thực; body vẫn không chứa `INVALID_JOB_DRAFT_REQUEST`); nhóm 5 dòng "token sửa
1 ký tự" ra 201 vì ký tự cuối đổi `A`→`B` chỉ đổi bit đệm, chữ ký giải mã giống hệt (đã kiểm bằng giải mã base64).
Người dùng duyệt sửa hai dòng này (L13, 13/10/2026); chạy lại đúng hai dòng đã sửa: JSON hỏng vào `/api/hr/jobs` →
`401 … UNAUTHENTICATED`, không chứa `INVALID_JOB_DRAFT_REQUEST`; token lấy từ một lần J1 `parts=['JOB']`, sửa một ký
tự giữa chữ ký (`r` → `A`) → `400 … INVALID_AI_GENERATION_TOKEN`. Cả hai khớp "Mong đợi" mới.
Cùng lỗi "sửa ký tự cuối" có trong hai test của FR này (`AiGenerationTokenCodecTest.verify_rejectsTamperedToken`,
`JobAiDraftSaveRejectionIntegrationTest.tamperedToken_rejected_nothingWritten`) — chập chờn khoảng 4/64 số lần chạy; đã
sửa ở đợt 7 để đổi một ký tự giữa chữ ký, full suite chạy lại 1162/1162.

**Soát tay mục 7.4.** Người dùng đã soát tay đủ các dòng của bảng 7.4 ở desktop, 768px và 375px, cả luồng AI lẫn luồng
thủ công, ngày 13/10/2026: mọi mục đều đúng.

| "Xong khi" (REQUIREMENT mục 7) | Cách nghiệm thu | Kết quả |
|---|---|---|
| T1, T1b, T2, T3, T6(b)(c), T12b, T13, T14 | `JobAiDraftGenerateIntegrationTest` | Đạt |
| T4, T12 (exception ngoài K3) | `JobAiDraftFacadeTest` | Đạt |
| T5, T7, T8 (unit + HTTP), T9, T10/T11 phần J1 | `JobDraftOutputValidatorsTest`, `JobAiDraftValidationIntegrationTest` | Đạt |
| T12 (a)(b)(c), T22 phần transaction | `JobAiDraftPartFailureIntegrationTest` | Đạt |
| T22 thời gian, T23 | `JobAiDraftFacadeConcurrencyTest` | Đạt |
| T6(a), T21 | `JobAiDraftSignatureTest` | Đạt |
| T15, T15b, T16 | `JobAiDraftSaveIntegrationTest` | Đạt |
| T17, T10/T11 phần J2 | `JobAiDraftSaveRejectionIntegrationTest`, `JobAiDraftSaveRollbackIntegrationTest` | Đạt |
| T18 | `AiGenerationTokenCodecTest`, `AiGenerationTokenBearerIntegrationTest` | Đạt |
| T19 | `RateLimitFilterTest` (4 ca mới) | Đạt |
| T20 | `ScreeningQuestionRulesTest` + mọi lớp `screening/` có sẵn không sửa | Đạt |
| Kiểm tĩnh 7.1 | Lệnh tìm ở báo cáo đợt 3–5 và đợt 7 | Đạt |
| 7.2 | Khối PowerShell; 2 dòng sửa theo L13 chạy lại | Đạt |
| 7.3 | Build, lint, lệnh tìm, đọc `api.ts` (L9) | Đạt |
| 7.4 | Soát tay của người dùng | Đạt |

**Chưa test.** Frontend không có test tự động (hộp thoại ghi đè, focus sau "Giữ", stepper compact chỉ soát tay).
Không có test đo tần suất `AI_TIMEOUT` thật — chỉ có số đo một lần ở L11. Không test tải nhiều HR cùng lúc với
executor K3 8 luồng thật (chỉ T23 với 2 luồng).

## 7. Nợ kỹ thuật

- Mỗi request J1 chiếm tới 3/8 luồng executor K3; ba HR bấm cùng lúc thì một phần nhận `AI_UNAVAILABLE` (R-K3H-4).
- Khoảng 1/10–1/5 lần tạo đủ ba phần có một phần `AI_TIMEOUT` khi gọi song song qua cùng kết nối HTTP/2 (L11); chưa
  phân định nguyên nhân. HR phải bấm "Thử lại", tốn thêm 1 lượt `ai-sync`.
- Token K4 còn hạn có thể gửi kèm hai lần lưu khác nhau của cùng HR (R-K4-5) — chỉ lặp, không giả mạo được.
- Lỗi `Select` Loại hình/Hình thức đã sửa ở trang tạo tin; **trang sửa tin `HrJobEditPage` vẫn còn** (ROADMAP mục nợ
  FR-H02).
- Lỗi 400 mặc định của Spring (body hỏng ở endpoint không có advice riêng) trên server thật trả 401 vì `/error` không
  mở cho request chưa xác thực — có từ trước FR-H11, phát hiện khi chạy 7.2.
- Frontend không có test tự động cho chế độ AI.

## 8. Lệch so với đặc tả

Mọi chỗ lệch dưới đây đã ghi vào mục 12 REQUIREMENT.md và được duyệt.

| Chỗ lệch | Duyệt |
|---|---|
| L1–L10: làm rõ sau Plan Mode (flush sau `create` + T15b, `userId` truyền tham số, `Clock` cho token, cách dẫn xuất khoá, prompt không `{}`, `strip()` tên tiêu chí, T22 đo ở facade, tiêu chí L9 cho 7.3) | 13/10/2026 |
| L11: kết quả cổng đo AI thật, chẩn đoán `AI_TIMEOUT`, quyết định không ép HTTP/1.1 | 13/10/2026 |
| L12: dùng lại `LinearProgress` theo 5c (khác màu rãnh ở 5d); dấu AI dưới ô Ngành nghề/Tỉnh thành; thêm `jobDraftValidation.ts` ngoài 5a; 5xx của J1 xử lý như lỗi mạng | 13/10/2026 |
| Các chỗ chọn khi đặc tả không nói rõ và người dùng đã xác nhận giữ: tiền tệ chỉ khoảng trắng → `null`; 0 câu hỏi hợp lệ; so `OTHER` bằng hằng; cách đếm độ dài; mô tả tiêu chí rỗng → `null`; phần tử `null` trong danh sách J2; biên hạn token trong test tích hợp lùi 60 s; token tiêu chí/câu hỏi chỉ gửi khi còn mục đã giữ | Xác nhận trong báo cáo đợt 3, 5, 6 (13/10/2026) |
| L13: mục 7.2 sửa hai dòng — JSON hỏng vào `/api/hr/jobs` mong đợi `401 UNAUTHENTICATED` (trang `/error` không mở cho request chưa xác thực, có từ trước); "token sửa 1 ký tự" đổi ký tự giữa chữ ký thay vì ký tự cuối (2 bit cuối là bit đệm base64url). Chạy lại hai dòng: `401 … UNAUTHENTICATED` không chứa `INVALID_JOB_DRAFT_REQUEST`; `400 … INVALID_AI_GENERATION_TOKEN`. Hai test chập chờn cùng nguyên nhân đã sửa | 13/10/2026 |
