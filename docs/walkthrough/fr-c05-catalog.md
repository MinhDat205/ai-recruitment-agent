# Walkthrough — FR-C05 · Danh mục dùng chung và chuẩn hoá dữ liệu

> BẢN NHÁP — ghi chú tích luỹ qua từng đợt. Đợt cuối viết đầy đủ 8 mục bằng skill `walkthrough`.

## 4. Quyết định thiết kế (ghi chú theo đợt)

### Đợt 1 — danh mục, bộ khớp, chuyển dữ liệu Job cũ

- **V9 tắt trigger `trg_jobs_updated_at` trong lúc chuyển mã** (`V9__normalize_job_catalog_codes.java`).
  `jobs.updated_at` được trả cho HR (`JobOwnerResponse.updatedAt`); nếu để trigger chạy, mọi Job cũ
  sẽ hiện như "vừa cập nhật" dù HR không sửa gì. Lệnh tắt và bật lại nằm trong cùng transaction của
  migration (Flyway bọc Java migration trong transaction vì `canExecuteInTransaction()` = `true`;
  `ALTER TABLE` của Postgres có transaction), nên V9 lỗi thì rollback cả lệnh tắt trigger. Kiểm bằng
  `JobCatalogMigrationTest.v9DoesNotTouchUpdatedAt` và `updatedAtTriggerIsReenabledAfterV9`.
- **V9 là bất biến.** Java migration không có checksum (`BaseJavaMigration.getChecksum()` trả
  `null`, xác nhận bằng bytecode `flyway-core-12.4.0`; `flyway_schema_history.checksum` của version 9
  là `NULL`, kiểm ở `JobCatalogMigrationTest.v9IsAppliedAsJavaMigration`). Flyway không phát hiện được
  nếu class bị sửa sau khi đã áp — không sửa V9; cần đổi dữ liệu thì viết migration mới.

### Đợt 2 — phía Job ở backend, API danh mục, seed

- **Danh mục nạp một lần vào bộ nhớ** (`CatalogRegistry`, bean ở `CatalogConfig`): đọc bảng V8 bằng
  chính `CatalogJdbcLoader` mà V9 dùng, gắn `@DependsOnDatabaseInitialization` để dựng sau Flyway.
  Tra nhãn, kiểm mã, bộ khớp đều dùng registry này — không query DB mỗi request.
- **API danh mục ở `GET /api/public/catalogs`** (đổi từ `/api/catalogs` trong đặc tả để theo quy ước
  `/api/public/**` của `SecurityConfig`; REQUIREMENT mục 4, 7 và UI.md mục 5 đã sửa theo, giữ ĐÃ DUYỆT).
- **Lỗi theo khuôn exception + `GlobalExceptionHandler` + `ErrorResponse`**, không dùng
  `FormattedErrorCode` (interface đó chỉ cho mã lỗi ghi xuống cột lỗi của job nền):
  `JobCatalogIncompleteException` → 409 `JOB_CATALOG_INCOMPLETE`, `InvalidCatalogCodeException` → 400
  `INVALID_CATALOG_CODE`. 409 có hai câu: mở tin, và sửa tin đang mở (bổ sung vào R-J5, UI.md mục 7).
- **Guard một hàm** `JobOwnerService.isCatalogComplete`: gọi ở `changeStatus` (mọi lần sang OPEN, sau
  bước kiểm rubric) và ở `update` (Job đang OPEN: chặn khi trước thoả mà sau không thoả).
- **Kiểm mã đặt ở đầu `applyRequest`**, trước mọi setter, để lỗi 400 không để lại entity sửa nửa chừng.
- **Response Job bỏ `category`/`location`**, thay bằng 6 field `categoryCode, categoryLabel,
  locationCode, locationLabel, legacyCategory, legacyLocation` (tính ở một chỗ: `JobCatalogFields`).
- **Tìm kiếm C02** khớp `ILIKE` với nhãn của mã (qua `EXISTS`, giữ `SELECT *` để map entity) hoặc giá
  trị cũ, kể cả Job đã có mã. Pattern giữ nguyên cách hiện có (`"%" + raw.trim() + "%"`, không thoát
  `%`/`_`) — không thêm cách mới trong C05.
- **Frontend tạm không lưu được ngành nghề/tỉnh thành cho tới đợt 3**: form cũ vẫn gửi
  `category`/`location`. Spring Boot 4.1 TẮT `FAIL_ON_UNKNOWN_PROPERTIES` (bytecode
  `JacksonAutoConfiguration$AbstractMapperBuilderCustomizer` gọi `disable(FAIL_ON_UNKNOWN_PROPERTIES)`),
  nên hai field đó bị bỏ qua lặng lẽ: HR vẫn lưu được tin (không có ngành/tỉnh), nhưng không mở được
  tin mới cho tới khi form gửi mã (đợt 3). Không đổi cấu hình Jackson. Chấp nhận vì nhánh chưa merge.
  Hệ quả phụ trong cùng khoảng đợt 2–3: lưu form sửa của một Job **DRAFT/PAUSED/CLOSED** đang có mã sẽ
  ghi `null` đè lên mã (form không gửi `categoryCode`/`locationCode`); Job **OPEN** thì bị guard chặn 409.
  Seed demo tạo 6 job DRAFT có mã — nếu thử sửa bằng form cũ trước đợt 3 thì phải nạp lại seed.
- **Ghi chú cho đợt 3 (frontend):** `JobRequest.categoryCode`/`locationCode` chỉ có `@Size(max = 40)`,
  không coi chuỗi rỗng là "không chọn" — gửi `""` sẽ bị 400 `INVALID_CATALOG_CODE` vì `""` không có
  trong danh mục. Mục "Bỏ chọn" của combobox phải gửi `null`, không gửi `""`.
- **Seed**: `dev-seed.sql` và `seed-demo-structural.sql` ghi `category_code`/`location_code`, cột cũ
  để NULL; `reset-demo-db.sql` TRUNCATE thêm `resume_reparse_requests`.

### Đợt 3 — frontend phía Job

File: `index.css` (token `m3-outline`), `docs/UI_GUIDE.md` (mục 1c, 6, 7); mới `features/catalog/`
(`types`, `api`, `queries`, `normalize`, `CatalogCombobox`), `features/jobs/catalogDisplay.ts`,
`CatalogField.tsx`, `JobLocationCell.tsx`, `UnnormalizedBadge.tsx`; sửa `features/jobs/types.ts`,
`ownerTypes.ts`, `JobCard.tsx`, `pages/HrJobCreatePage.tsx`, `HrJobEditPage.tsx`, `HrJobListPage.tsx`,
`PublicJobDetailPage.tsx`. Không đụng `HeroSearch.tsx`, không sửa backend.

- **Kiểu dữ liệu bỏ hẳn `category`/`location`** ở `JobSummary`, `JobDetail`, `JobOwnerResponse` và
  `JobOwnerRequest` → `tsc -b` là chốt chặn chỗ sót (build sạch). `JobSearchParams.location/category`
  giữ nguyên: đó là ô tìm kiếm tự do của FR-C02, không phải field của Job.
- **Payload gửi `null` tường minh** cho `categoryCode`/`locationCode` khi chưa chọn (không `""`, không bỏ
  field) — form giữ giá trị `string | null`, zod `z.string().nullable()`.
- **`CatalogCombobox`**: nút trigger `role="combobox"` + popover (Radix, `popover.tsx`) chứa ô tìm kiếm và
  `role="listbox"`; bàn phím ↑/↓/Enter trên ô tìm (`aria-activedescendant`), Esc do Radix đóng và trả
  focus về trigger; ↓ trên trigger mở popover. "Bỏ chọn" là dòng cuối của listbox (điều hướng được bằng
  phím, luôn có mặt kể cả khi không có mục khớp). Chọn bằng `mousedown` để giữ focus ở ô tìm.
  Không cho nhập tự do. Popover rộng bằng ô qua `--radix-popover-trigger-width`.
- **Lọc chỉ trên nhãn** (bỏ dấu, không phân biệt hoa/thường, `normalizeForSearch`). API không trả bí danh
  nên gõ "HCM" hay tên tỉnh cũ ("Bình Dương") KHÔNG ra kết quả trong combobox — đúng phạm vi UI.md mục 5
  ("tìm trên nhãn"); ghi nhận để U07 cân nhắc nếu cần.
- **Nhãn ô dùng `Label` shadcn cũ** (giữ đồng bộ với các ô cũ trong form); phần mới (combobox, dòng gợi ý,
  dòng "Giá trị cũ", nhãn "Chưa chuẩn hoá", khung cảnh báo) dùng token `m3-*` theo ngoại lệ UI.md mục 1.
- **Dòng "Giá trị cũ"** hiện khi Job có `legacy*` VÀ ô trong form đang trống; HR chọn mã (chưa lưu) thì
  dòng ẩn. Không tự chọn mã đoán từ giá trị cũ.
- **Khung cảnh báo 4b** tính từ dữ liệu ĐÃ LƯU (`job.status === 'OPEN' && !isCatalogComplete(job)`), không
  từ giá trị đang sửa; không gắn `role="status"` (nội dung tĩnh, tránh bị đọc như thông báo động).
- **Cột Địa điểm HR**: "Chưa có dữ liệu" thay cho "—" cũ (UI_GUIDE mục 4 "Dữ liệu thiếu").
- **Trang chi tiết công khai** chỉ đổi chip tỉnh/thành (trang này vốn không hiện ngành nghề) — không thêm
  chip mới.
- **Lỗi 409 "Mở tin"/"Mở lại"**: không sửa `JobRowActions` — cả hai nút đi qua cùng
  `useChangeHrJobStatusMutation`, lỗi hiện bằng `extractErrorMessage` đọc `data.message` của
  `ErrorResponse` backend.
- **UI_GUIDE mục 7**: gắn FR-C05 cho `/`, `/jobs/:id`, `/candidate`, `/candidate/dashboard`, `/hr/jobs`,
  `/hr/jobs/new`, `/hr/jobs/:id/edit`; `/candidate/profile` gắn ở đợt làm phần CV.
- Kiểm tra: `npm run build` + `npm run lint` sạch; đã grep CSS build xác nhận các class mới
  (`border-m3-outline`, `w-(--radix-popover-trigger-width)`, `bg-m3-primary/8`, `opacity-38`...) có sinh ra.
  Chưa soát tay giao diện (dồn về đợt 6).

### Đợt 4 — backend CV: schema v2, số tháng kinh nghiệm, trích xuất lại

File (backend, không migration mới, không sửa frontend):
- Mới: `resources/ai/prompt/resume-parse-v2.st`; `common/ClockConfig.java`;
  `common/exception/ResumeReparseNotAllowedException.java`, `ResumeReparseInProgressException.java`;
  `resume/ExperienceCalculator.java`, `ResumeParsedDataEnricher.java`, `ResumeSchemaVersions.java`,
  `ResumeExperienceStateService.java`, `ResumeExperienceScheduler.java`, `ResumeReparseRequest.java`,
  `ResumeReparseRequestStatus.java`, `ResumeReparseRequestRepository.java`, `ResumeReparseStateService.java`,
  `ResumeReparseOrchestrator.java`, `ResumeReparseScheduler.java`, `dto/ResumeReparseStatusResponse.java`.
- Sửa: `ResumeParsedPayload` (+3 field), `ResumeParsedData` (+6 cột), `ResumeParsedDataRepository`,
  `ResumeParsingService` (v2 + danh sách ngành), `ResumeParsingStateService.markDone`, `ResumeService`,
  `ResumeCandidateController`, `dto/ResumeResponse`, `dto/ResumeParsedDataResponse`,
  `CvImprovementOrchestrator.buildResumeText`, `ResumeEmbeddingOrchestrator` (chỉ comment),
  `RateLimitFilter`, `GlobalExceptionHandler`, `application.yml`, `application-test.yml`.
- Không đụng: `ScoringRunOrchestrator`, `criterion_scores`, `score_explanations`, cách D2 đọc `raw_text`,
  luồng tải lên (ngoài prompt v2 + `markDone` gọi enricher).

Quyết định:
- **Ba field mới đặt cuối `ResumeParsedPayload`** (`currentTitle`, `industryCode`, `locationText`). 16 chỗ
  `new ResumeParsedPayload(...)` trong 13 file test cũ thêm `null, null, null` — không thêm constructor phụ
  6 tham số (tránh constructor thứ hai trên record mà Jackson/BeanOutputConverter dùng để sinh schema).
- **Prompt v2 = toàn bộ v1 + một khối luật cho ba field.** Danh sách ngành truyền qua tham số
  `{industries}`, dựng ở `ResumeParsingService.formatIndustries` từ `CatalogRegistry.industries()` (bỏ
  `OTHER`, giữ `sort_order`) — không hardcode mã trong Java hay `.st`. `currentTitle` chỉ lấy từ vị trí CV
  đánh dấu đang làm; không có thì null (không lấy vị trí gần nhất, không lấy dòng tiêu đề CV).
- **Một hàm ghi dùng chung** `ResumeParsedDataEnricher.applyExtraction`: kiểm mã ngành (lạ/`OTHER` → null ở
  CẢ JSON lẫn cột), khớp `locationText` → `region_code` qua `CatalogRegistry.matchProvince` (trượt → null,
  giữ chuỗi gốc), tính kinh nghiệm. Gọi từ `ResumeParsingStateService.markDone` (tải lên) và
  `ResumeReparseStateService.markDone` (trích xuất lại) — cả hai trong transaction ghi.
- **`Clock` là bean** (`common/ClockConfig`, `Clock.systemUTC()`); múi giờ `Asia/Ho_Chi_Minh` áp ở
  `ExperienceCalculator.referenceMonth`, không phụ thuộc múi giờ của đồng hồ. Test đơn vị dùng `Clock.fixed`
  ở 15/09/2026 (refMonth 09/2026); test tích hợp chỉ dùng khoảng thời gian hoàn toàn trong quá khứ nên không
  cần thay bean `Clock` (tránh thêm một Spring context).
- **R-E3 đọc đúng các dạng liệt kê, không nới:** tháng một chữ số chỉ ở dạng có `/` (`M/YYYY`, `thang M/YYYY`,
  `thang M nam YYYY`); `MM-YYYY`, `MM.YYYY`, `YYYY-MM`, `YYYY/MM`, `DD/MM/YYYY`, `DD-MM-YYYY` cần đủ 2 chữ số;
  tên tháng tiếng Anh là 3 chữ viết tắt hoặc tên đầy đủ, không dấu chấm (`Sept`, `Jan.` → không đọc được).
  Khớp toàn chuỗi, không tìm chuỗi con.
- **Job nền kinh nghiệm không có cột trạng thái**: "claim" là chính câu ghi
  `UPDATE ... WHERE id = :id AND experience_computed_at IS NULL` (`writeExperienceIfPending`) — rowcount 0
  khi trích xuất lại hoặc vòng quét khác đã ghi trước, nên kết quả tính từ data cũ không đè được data mới.
  Cấu hình `app.resume-experience.*` (30 giây, lô 50); tắt trong test.
- **Ghi trích xuất lại = entity + một câu native**: `data`/`model`/`prompt_version`/`token_usage`/mã/kinh
  nghiệm ghi qua entity (`saveAndFlush`), còn `parsed_at = now()` và `embedding = NULL` qua
  `touchAfterReparse` vì `parsed_at` là cột DB sinh lúc INSERT (không updatable qua entity) và `embedding`
  không map. Hibernate UPDATE toàn bộ cột nên `raw_text` được ghi lại đúng giá trị đã đọc — test so byte.
- **Thêm stale-claim reaper cho trích xuất lại** (`ResumeReparseScheduler.reapStaleClaims`, mẫu
  `ResumeParsingScheduler`), không có trong đề bài đợt: JVM khởi động lại giữa chừng để yêu cầu kẹt ở
  RUNNING thì partial unique index chặn ứng viên gửi yêu cầu mới vĩnh viễn. Dùng chung ngân sách
  `attempt_count`/`LLM_RETRY_EXHAUSTED`, mã `STALE_CLAIM_TIMEOUT`.
- **Hai mã lỗi 409 riêng**: `RESUME_REPARSE_NOT_ALLOWED` ("CV này đã có dữ liệu trích xuất mới nhất." — dùng
  cho cả CV v2 lẫn CV chưa DONE, đúng nguyên văn R-R2 lúc đó; **đợt 5 tách câu cho CV chưa DONE**, xem dưới)
  và `RESUME_REPARSE_IN_PROGRESS`. Race vượt bước kiểm:
  service bắt vi phạm `uq_resume_reparse_request_active` và ném lại cùng 409; vi phạm khác ném nguyên.
- **Endpoint trích xuất lại nằm ở `ResumeService`** (cạnh `retry`), trả 202 + `ResumeResponse`. Danh sách CV
  lấy `schemaVersion` và yêu cầu gần nhất bằng 2 câu cho cả danh sách (`findPromptVersionsByResumeIds`,
  `findLatestByResumeIds` với `DISTINCT ON`), không truy vấn từng CV.
- **API `/parsed`**: `industry`/`location` lấy từ CỘT đã qua kiểm (không đọc lại mã AI trong JSON);
  `referenceMonth` dạng `"YYYY-MM"`; `years` là BigDecimal 1 chữ số thập phân; `experience` null khi chưa
  tính. Giữ field `data` cũ. Không thêm endpoint nào cho HR.
- **`buildResumeText`** thêm dòng `Chuc danh hien tai: ...` sau thông tin liên hệ khi có; không thêm mã ngành,
  mã khu vực, số tháng, và không thêm `locationText` (ngoài phạm vi R-C6). Đây là bản duy nhất — F1
  (`ResumeEmbeddingOrchestrator`) gọi lại chính hàm này, không có bản sao thứ hai.
- `RateLimitFilter`: thêm `/api/candidates/resumes/*/reparse` vào nhóm `llm-action` theo userId.

Test đã viết, **chỉ biên dịch (`mvnw -q test-compile` sạch), CHƯA CHẠY** — đợt 6 chạy full suite:
- `ExperienceCalculatorTest` (mới, thuần): 20 ca R-E10 (ca 16 tham số hoá 13 dạng), ca âm đọc mốc, từ "đang
  làm" ở `startDate`, 8 biến thể từ "đang làm", biên làm tròn, múi giờ refMonth, phần tử null — mục 7.6.
- `ResumeParsedDataEnricherTest` (mới, thuần, `Clock.fixed`): mã lạ/`OTHER`/nhãn thay mã → null ở JSON và
  cột; "Bình Dương" → `HO_CHI_MINH`; không khớp → mã null, chuỗi giữ nguyên; kinh nghiệm theo đồng hồ inject;
  không đọc được → null không 0 — mục 7.5.
- `ResumeParsingStateServiceTest` (+5): R-C4 sau `markDone` (mã lạ, `OTHER`), danh mục thật V8, khu vực không
  khớp, kinh nghiệm ghi cùng `markDone` — mục 7.9 R-C4.
- `ResumeExperienceBackfillIntegrationTest` (mới, 7): bản ghi v1 chèn bằng SQL đọc qua Hibernate với field
  mới null; job nền tính và không gọi `ChatModel`/`EmbeddingModel`; null không lưu thành 0; danh sách rỗng;
  không ghi đè bản ghi đã tính; UPDATE có điều kiện trả 0; vòng quét scheduler — mục 7.5, 7.6.
- `ResumeReparseEndpointTest` (mới, 15): 202 + PENDING + parse_status DONE + không gọi AI; FAILED trước không
  chặn; 404 CV người khác; 409 CV v2, CV PENDING, CV FAILED, yêu cầu PENDING, RUNNING; race → DB chặn bằng
  partial unique index; HR → 403; không token → 401; danh sách CV có `schemaVersion`/`reparse`; `/parsed`
  v1 chưa tính, v2 đầy đủ nhãn + năm, đã tính nhưng months null — mục 7.7, 7.8.
- `ResumeReparseOrchestratorTest` (mới, 11): thành công → UPDATE tại chỗ (cùng id, 1 dòng), `raw_text` byte
  không đổi, embedding NULL, parse_status DONE trong lúc gọi LLM, đầu vào LLM đúng `raw_text` đã lưu (file
  không tồn tại), md5 `criterion_scores`/`score_explanations` không đổi; R-C4 sau trích xuất lại; prompt có
  danh sách ngành không có `OTHER`; JSON hỏng → FAILED, md5 cả dòng `resume_parsed_data` không đổi; lỗi LLM
  → chỉ mã chuẩn hoá; claim; backoff (biên max−2, max−1); reaper — mục 7.7.
- `CvImprovementOrchestratorTest` (+2): R-C6 có `currentTitle`, không `IT_SOFTWARE`/`HO_CHI_MINH`; v1 không
  có dòng chức danh — mục 7.9 R-C6.
- `RateLimitFilterTest` (+2), `ResumeParsingServiceTest` (+2: `formatIndustries`, phiên bản v2),
  `ResumeParsePromptTest` (+2: luật prompt v2, file v1 còn).

### Đợt 5 — frontend CV + hai chỉnh sửa từ review đợt 4

File:
- Backend: `common/exception/ResumeReparseNotAllowedException.java` (hai factory `notParsedYet()` /
  `alreadyLatest()`), `resume/ResumeService.java`, test `ResumeReparseEndpointTest.java` (assert câu mới cho CV
  PENDING và CV FAILED).
- Đặc tả (giữ ĐÃ DUYỆT): `REQUIREMENT.md` R-R2 tách câu 409 cho CV chưa DONE; `UI.md` mục 7 thêm dòng "Lỗi 409
  gửi yêu cầu trích xuất lại" (ba câu backend).
- Frontend: sửa `features/resumes/types.ts`, `api.ts`, `queries.ts`, `ResumeList.tsx`,
  `ResumeParsedDataDialog.tsx`, `index.css` (keyframe); mới `features/resumes/LinearProgress.tsx`,
  `ResumeReparseStatus.tsx`, `resumeReparse.ts`, `CareerOverviewSection.tsx`.
- `docs/UI_GUIDE.md`: mục 7 gắn FR-C05 cho `/candidate/profile`; mục 1h thêm dòng về
  `animate-m3-linear-progress`.

Quyết định:
- **409 CV chưa DONE** (PENDING/PROCESSING/FAILED, và ca phòng thủ DONE mà thiếu dòng dữ liệu): cùng mã
  `RESUME_REPARSE_NOT_ALLOWED`, câu "CV chưa phân tích xong, chưa thể cập nhật dữ liệu trích xuất."; CV đã v2 giữ
  "CV này đã có dữ liệu trích xuất mới nhất.".
- **Poll dùng chung `refetchInterval` sẵn có**: `hasResumeStillPolling` thêm điều kiện `isReparseActive`
  (reparse PENDING/RUNNING). Không có ngưỡng "kẹt quá lâu" cho trích xuất lại như D1 — backend có reaper đưa
  yêu cầu kẹt về PENDING rồi FAILED sau số lần thử tối đa, nên poll luôn dừng.
- **Phát hiện "vừa xong"** bằng cách so danh sách mới với danh sách lần trước ngay trong render (mẫu "điều
  chỉnh state khi dữ liệu đổi" của React; eslint-plugin-react-hooks 7 không cho setState trong effect): CV
  từng PENDING/RUNNING nay DONE → vùng `aria-live` của dòng đọc "Đã cập nhật dữ liệu trích xuất." (`sr-only`,
  người nhìn thấy nút và dòng "phiên bản cũ" biến mất). Đồng thời invalidate cache `/parsed` của CV đó
  (effect, không setState) để lần mở dialog sau lấy bản v2.
- **Vùng `aria-live` luôn có mặt** trong ô trạng thái của mọi CV DONE (kể cả khi rỗng) để thay đổi nội dung
  được đọc; chứa câu thất bại của yêu cầu, lỗi khi gửi (409/429, `text-m3-error`) và câu thành công.
- **Lỗi 409/429 "dưới dòng CV"** đặt trong ô trạng thái của chính dòng đó (dưới badge), không thêm hàng bảng
  phụ. Lỗi chung cũ của danh sách (`error`, tải xuống/thử lại) giữ nguyên chỗ.
- **Câu thất bại hiện nguyên `errorMessage` backend** (dạng `MÃ: mô tả`), nhất quán với cách `parseError` đang
  hiện ở cùng ô; không cắt tiền tố mã. `errorMessage` null → câu không có phần lỗi.
- **Nút dùng `Button variant="outline" size="sm"` sẵn có** (cùng kiểu các nút cạnh bên, UI.md mục 5), icon
  `RefreshCw`. Nhãn: "Cập nhật dữ liệu trích xuất" / "Đang cập nhật…" (đang chạy hoặc đang gửi) / "Thử cập nhật
  lại" (FAILED). Không đổi bố cục cột hành động (không thêm `flex-wrap`) — giữ cách bảng hiện tại co lại.
- **Linear progress** là component nhỏ trong `features/resumes/` (chưa đưa lên `components/` vì mới một nơi
  dùng; K3 sau này có thể nâng lên). Keyframe mới `m3-linear-progress` khai trong `@theme` của `index.css`,
  kèm `motion-reduce:animate-none`; ghi vào UI_GUIDE mục 1h.
- **Hiển thị số năm**: `years.toFixed(1).replace('.', ',')`. Backend trả BigDecimal 1 chữ số thập phân nhưng
  `JSON.parse` làm mất số 0 cuối (`2.0` → `2`); `toFixed(1)` chỉ khôi phục số 0 đó, không làm tròn lại, không
  chia tháng.
- **Tổng quan nghề nghiệp**: `dl`; < sm nhãn trên giá trị, ≥ sm lưới `10rem | 1fr`. Tiêu đề `text-m3-label-lg`
  (14px/500, bằng cỡ tiêu đề các mục cũ `text-sm font-medium`). "Chưa có dữ liệu" và "Đang tính…" màu
  `m3-on-surface-variant`, là chữ. Ngành/khu vực lấy `label` backend trả; không nhãn "Do AI tạo", không nền
  khối AI.
- Không đụng các mục cũ của dialog (Thông tin liên hệ…); chúng vẫn dùng token cũ theo ngoại lệ UI.md mục 1.

Kiểm tra: `mvnw -q test-compile` sạch; `npm run build` + `npm run lint` sạch (chỉ cảnh báo chunk > 500 kB có
từ trước); grep CSS build thấy `animate-m3-linear-progress`, `@keyframes m3-linear-progress`, `motion-reduce`,
`bg-m3-primary-container`, `text-m3-error`, `sm:grid-cols-[10rem_1fr]`. Chưa chạy test, chưa soát tay (đợt 6).

## 7. Nợ kỹ thuật (ghi chú theo đợt — đợt cuối đưa vào ROADMAP)

- **Test chập chờn, có từ trước C05:**
  `ScoringRunOrchestratorTest.processOne_temporaryErrorOnSecondCriterion_returnsToPendingThenResumesSkippingAlreadyScoredCriteria`
  phụ thuộc thời gian thực — gọi lại `processOne` "ngay" và kỳ vọng claim bị từ chối vì
  `next_attempt_at` còn ở tương lai, nhưng backoff trong cấu hình test chỉ 50ms
  (`application-test.yml`, `app.hardening.llm.backoff-ms`). Khi máy chậm (chạy full suite) quá 50ms
  trôi qua, claim thành công, test đỏ (`expected: PENDING but was: RUNNING`, dòng 411). Gặp ở đợt 1
  (545 test, 1 đỏ); chạy riêng class xanh 6/6; chạy lại full suite xanh 545/545. Không sửa trong C05
  (ngoài phạm vi, không đụng `scoring/`).
- **Race embedding CV với trích xuất lại (phát hiện ở review đợt 4, chưa sửa):** `ResumeEmbeddingScheduler`
  không claim. Kịch bản: scheduler đọc văn bản v1 khi `embedding` đang NULL → trích xuất lại commit data v2 và
  đặt `embedding = NULL` → scheduler ghi embedding tính từ văn bản v1 đè lên. Kết quả: embedding lệch dữ liệu
  vĩnh viễn (không còn NULL nên không được tính lại). Xác suất thấp (cần trùng cửa sổ vài giây của lời gọi
  embedding), cùng họ nợ "không claim" của F1. Cách chữa: ghi embedding có điều kiện theo `parsed_at` đã đọc
  (`UPDATE ... WHERE id = :id AND parsed_at = :parsedAtRead`) — `touchAfterReparse` đã đổi `parsed_at` nên câu
  ghi muộn sẽ trượt.
