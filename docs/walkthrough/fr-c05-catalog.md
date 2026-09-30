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

## 7. Nợ kỹ thuật (ghi chú theo đợt — đợt cuối đưa vào ROADMAP)

- **Test chập chờn, có từ trước C05:**
  `ScoringRunOrchestratorTest.processOne_temporaryErrorOnSecondCriterion_returnsToPendingThenResumesSkippingAlreadyScoredCriteria`
  phụ thuộc thời gian thực — gọi lại `processOne` "ngay" và kỳ vọng claim bị từ chối vì
  `next_attempt_at` còn ở tương lai, nhưng backoff trong cấu hình test chỉ 50ms
  (`application-test.yml`, `app.hardening.llm.backoff-ms`). Khi máy chậm (chạy full suite) quá 50ms
  trôi qua, claim thành công, test đỏ (`expected: PENDING but was: RUNNING`, dòng 411). Gặp ở đợt 1
  (545 test, 1 đỏ); chạy riêng class xanh 6/6; chạy lại full suite xanh 545/545. Không sửa trong C05
  (ngoài phạm vi, không đụng `scoring/`).
