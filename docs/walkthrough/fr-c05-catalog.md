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

## 7. Nợ kỹ thuật (ghi chú theo đợt — đợt cuối đưa vào ROADMAP)

- **Test chập chờn, có từ trước C05:**
  `ScoringRunOrchestratorTest.processOne_temporaryErrorOnSecondCriterion_returnsToPendingThenResumesSkippingAlreadyScoredCriteria`
  phụ thuộc thời gian thực — gọi lại `processOne` "ngay" và kỳ vọng claim bị từ chối vì
  `next_attempt_at` còn ở tương lai, nhưng backoff trong cấu hình test chỉ 50ms
  (`application-test.yml`, `app.hardening.llm.backoff-ms`). Khi máy chậm (chạy full suite) quá 50ms
  trôi qua, claim thành công, test đỏ (`expected: PENDING but was: RUNNING`, dòng 411). Gặp ở đợt 1
  (545 test, 1 đỏ); chạy riêng class xanh 6/6; chạy lại full suite xanh 545/545. Không sửa trong C05
  (ngoài phạm vi, không đụng `scoring/`).
