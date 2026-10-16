# FR-H15 — Kho ứng viên

> Trạng thái: ĐÃ DUYỆT 16/10/2026
>
> Phạm vi rút gọn ngày 16/10/2026 (ghi đè mọi mô tả H15 cũ trong `docs/SRS.md`, `docs/ROADMAP.md`,
> `docs/features/README.md` mục 16). Quyết định đã chốt ở mục 0(b); `[Qn]` trong thân là tham chiếu tới câu đã chốt.
>
> Làm rõ sau Plan Mode 16/10/2026: L1–L3 (L1: R-P5, T16, mục 8; L2: T3; L3: `UI.md` mục 5a) — chi tiết ở mục 11. Mục 11
> thắng chỗ viết cũ nếu lệch.

- Nhóm: HR
- Tóm tắt: HR tạo, đổi tên, xoá các kho ứng viên của công ty mình; lưu một đơn ứng tuyển vào một hoặc nhiều kho và gỡ
  ra. Chỉ lưu được đơn mà ứng viên đang đồng ý lưu hồ sơ (FR-U09). Từ kho, HR mở lại trang hồ sơ đơn có sẵn (FR-H09)
  để xem CV, đánh giá AI hiện có (FR-H04/H06), trao đổi và soạn nháp bằng AI (FR-C06/C07). **Không gọi AI, không chấm
  lại, không có ghi chú, thẻ, bộ lọc hay tìm kiếm.**
- Phụ thuộc: FR-H09, FR-U09 — **Đã hoàn thành** (`docs/SRS.md:37`, `:52`). Màn hình được dẫn tới: FR-C06, FR-C07 —
  **Đã hoàn thành** (`docs/SRS.md:26`, `:27`).
- Nhánh: `feat/fr-h15-talent-pool` (tạo từ `main` tại `f4757b4`).
- Mở rộng: `ApplicationService.revokeTalentPoolConsent` (FR-U09) thêm bước gỡ mục khỏi mọi kho **trong chính
  transaction** rút đồng ý; header trang `/hr/applications/:id` thêm nút "Lưu vào kho"; `HrLayout` thêm mục điều hướng.

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

Đối chiếu trên `feat/fr-h15-talent-pool` tại `f4757b4` (16/10/2026, cây làm việc sạch).

### (a) Hiện trạng

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | Cờ đồng ý lưu hồ sơ (U09) | Ba cột trên `job_applications`: `talent_pool_consent` (BOOLEAN NOT NULL DEFAULT FALSE), `talent_pool_consent_at`, `talent_pool_consent_revoked_at`; CHECK `chk_talent_pool_consent_state` ba trạng thái | `V14__talent_pool_consent.sql:11-27` |
| a2 | Trạng thái đồng ý do backend suy | `NOT_GIVEN` / `ACTIVE` / `REVOKED`; `ACTIVE` ⇔ `talent_pool_consent = TRUE` (`of()` chỉ xét cờ, không xét `revoked_at`). CHECK V14 buộc `revoked_at IS NOT NULL` ⇒ cờ `FALSE`, nên `talent_pool_consent = TRUE` ⇔ `talent_pool_consent = TRUE AND talent_pool_consent_revoked_at IS NULL` trên mọi hàng hợp lệ | `TalentPoolConsentState.java:12-17`; CHECK `V14__talent_pool_consent.sql:17-27` |
| a3 | Entity map ba cột chỉ đọc; rút đồng ý **vẫn** đặt cờ `FALSE` | `updatable = false` chỉ chặn `save()` của entity. Rút đồng ý không qua entity mà qua native `UPDATE … SET talent_pool_consent = FALSE, talent_pool_consent_revoked_at = :revokedAt WHERE … AND talent_pool_consent = TRUE` (`@Modifying`) — sau khi rút, cờ là `FALSE` | `JobApplication.java:61-69`; `JobApplicationRepository.java:36-51`; `ApplicationService.java:235` |
| a4 | Đồng ý chỉ có chiều rút, một chiều, không đồng ý lại | Đúng | `docs/features/README.md:322`; `UV/U09/REQUIREMENT.md:480` (đồng ý lại ngoài phạm vi) |
| a5 | Rút đồng ý: một `UPDATE` có điều kiện trong `@Transactional`; chỗ H15 chen vào đã được đánh dấu | Đúng | `ApplicationService.java:225-245` (comment `:233-234` "H15 se them viec xoa khoi kho vao CHINH transaction nay"); `JobApplicationRepository.java:37-51` |
| a6 | Nợ kỹ thuật ROADMAP giao cho H15 | Gỡ mục khỏi mọi kho trong chính transaction rút đồng ý; chặn thêm đơn không `ACTIVE` | `docs/ROADMAP.md:884-885`; `UV/U09/REQUIREMENT.md:17-18`, `:225-226` |
| a7 | HR chưa thấy trạng thái đồng ý ở đâu | E1 không có field đồng ý; U09 Q13 để H15 tự quyết | `ApplicationHrDetailResponse.java` (10 field, không có consent); `UV/U09/REQUIREMENT.md:479` |
| a8 | Một công ty đúng một HR | `uq_company_per_owner (owner_id)` ⇒ "HR thuộc công ty" = `companies.owner_id` | `V2__company_unique_owner.sql:1-2` |
| a9 | Quy ước quyền phía HR | Công ty trước (404 `COMPANY_NOT_FOUND`) → bản ghi (404 …`_NOT_FOUND`) → bản ghi thuộc công ty khác **403** (`AccessDeniedException`) | `HrApplicationAccess.java:17-20`, `:42-56`; `JobOwnerService.java:200-208` |
| a10 | Tầng filter | `/api/hr/**` → `hasRole("HR")` | `SecurityConfig.java:53` |
| a11 | Quy ước phân trang | Tham số `page` (0-based), `size`, đều `required = false`; `page` null/âm → 0; `size` null/< 1 → 10, > 50 → 50; response `PageResponse<T>(items, page, size, totalElements, totalPages)` | `JobOwnerController.java:43-50`; `JobOwnerService.java:38-39`, `:272-281`; `ApplicationSearchService.java:31-32`; `PageResponse.java:7` |
| a12 | Xoá Job là xoá mềm; đơn vẫn còn; trang H09 vẫn mở được | `JobOwnerService.delete` chỉ đặt `deleted_at`; `job_applications.job_id` `ON DELETE RESTRICT`; `HrApplicationAccess.loadOwned` dùng `findById`, không lọc `deleted_at` | `JobOwnerService.java:186-192`; `V1__init_schema.sql:208`; `HrApplicationAccess.java:49-51` |
| a13 | Trao đổi của đơn `WITHDRAWN` chỉ đọc | 409 `CONVERSATION_READ_ONLY` khi gửi | `CHUNG/C06/REQUIREMENT.md:493` (T9) |
| a14 | Quy ước xoá | Xoá mềm `deleted_at` xuyên suốt (CLAUDE.md §4); tiền lệ `ScreeningQuestionOwnerService.java:89`. Ngoại lệ xoá cứng có sẵn: `RubricOwnerService.java:92` | Như cột trái |
| a15 | Ràng buộc "chỉ một" chốt ở DB, dịch sang 409 | `GlobalExceptionHandler.handleDataIntegrityViolation` so tên constraint | `GlobalExceptionHandler.java:468-481` |
| a16 | Migration lớn nhất | `V16__interview_invitation_slots.sql` ⇒ file mới **`V17__talent_pools.sql`** | `backend/src/main/resources/db/migration/` (kiểm `ls \| sort -V` ngày 16/10/2026) |
| a17 | Trang H09: route, header, tab | `/hr/applications/:id` trong nhóm `RequireCompany`; header `ApplicationDetailHeader` (tên, badge, ngày nộp, `ApplicationActionBar`); 7 tab, `?tab=` gồm `cv`, `explanation`, `cvqa`, `screening`, `invitation`, `history`, `messages` | `App.tsx:89-106`; `ApplicationDetailHeader.tsx:26-48`; `HrApplicationDetailPage.tsx:153-159` |
| a18 | Soạn nháp bằng AI (C07) nằm ở tab Trao đổi | Đúng | `docs/UI_GUIDE.md:362` ("FR-C07 (soạn bằng AI ở tab Trao đổi)") |
| a19 | Route đã dành sẵn | `★/hr/talent-pools`, `★/hr/talent-pools/:id` | `docs/UI_GUIDE.md:363` |
| a20 | Seed demo không có đơn nào đồng ý lưu hồ sơ | Chỉ `seed-test.sql` có đơn A1 `ACTIVE` | `db/seed/seed-test.sql:361-369`; `grep talent_pool_consent db/seed/seed-demo-*.sql` rỗng |

### (b) Quyết định — đã chốt 16/10/2026

| # | Câu hỏi | Chốt |
|---|---|---|
| Q1 | Phạm vi lệch README (thiếu đổi tên, xoá kho, gỡ đơn) | Theo phạm vi 16/10/2026: tạo, đổi tên, xoá kho; lưu vào nhiều kho; gỡ khỏi kho. README/SRS/ROADMAP/CLAUDE.md sửa ở đợt cuối (mục 10) |
| Q2 | Xoá cứng hay xoá mềm | **Xoá mềm cả hai**: kho `deleted_at`, mục `removed_at`. Xoá kho không cascade sang mục (mục của kho đã xoá không truy cập được) |
| Q3 | Tên kho | Sau `strip()`: 1–100 ký tự (đếm code point), không trùng (không phân biệt hoa thường) trong các kho chưa xoá của công ty |
| Q4 | Giới hạn số kho | **Không giới hạn** số kho mỗi công ty |
| Q5 | Điều kiện đồng ý | Chỉ thêm được đơn `ACTIVE`; `NOT_GIVEN`/`REVOKED` → 409 `TALENT_POOL_CONSENT_REQUIRED` |
| Q6 | Ứng viên đổi ý | Chỉ có rút (a4). Rút → gỡ mục khỏi mọi kho trong chính transaction rút đồng ý (a5, a6) |
| Q7 | Trạng thái đơn | Lưu được ở **cả 5 trạng thái**; đổi trạng thái không ảnh hưởng mục trong kho |
| Q8 | Job đóng/xoá | Giữ mục. Job bị xoá → danh sách hiện chữ trung tính "Tin đã xoá"; Job đóng không hiện gì thêm |
| Q9 | Một ứng viên nhiều đơn | Kho lưu theo **đơn**; một ứng viên có thể xuất hiện nhiều lần trong một kho, mỗi lần một đơn khác nhau |
| Q10 | Quyền | Theo a9: kho/đơn của công ty khác → **403**; kho không tồn tại hoặc đã xoá → 404 |
| Q11 | HR thấy trạng thái đồng ý ở đâu | **Không sửa E1**; endpoint riêng P6 (mục 4.3) cho hộp thoại; P7 đặt toàn bộ danh sách kho của một đơn trong một transaction |
| Q12 | Điểm/hạng trong danh sách mục | Không hiện (điểm phải kèm evidence; hạng tính theo từng Job — FR-H05). Chỉ dẫn sang H09 |
| Q13 | Điều hướng | Thêm mục thứ 6 "Kho ứng viên" (nhãn ngắn "Kho") vào `HrLayout`; nút "Lưu vào kho" ở header H09, luôn hiện |
| Q14 | Phân trang danh sách mục | Theo a11 (mặc định 10, tối đa 50). Danh sách kho **không** phân trang |
| Q15 | P7 có kho sai hoặc trùng | Cả request bị từ chối, không lưu một phần (R-P3, R-P4) |

## 1. Mục đích

Giúp HR giữ lại ứng viên tiềm năng đã nộp đơn vào công ty, nhóm theo mục đích, để mở lại hồ sơ nhanh cho các đợt tuyển
sau — chỉ với những đơn mà ứng viên đã đồng ý cho lưu.

## 2. Luồng người dùng

1. HR vào "Kho ứng viên" (`/hr/talent-pools`) → "Tạo kho" → nhập tên → Lưu.
2. Ở trang hồ sơ đơn `/hr/applications/:id` → "Lưu vào kho" → hộp thoại hiện danh sách kho, ô nào đã chứa đơn thì được
   tick sẵn → tick/bỏ tick → "Lưu". Đơn không có đồng ý `ACTIVE` → hộp thoại giải thích lý do, không thêm được.
3. Ở `/hr/talent-pools/:id` → xem danh sách đơn đã lưu (mới thêm trước, có phân trang) → "Mở hồ sơ" (tab CV & điểm)
   hoặc "Trao đổi" (tab Trao đổi, có soạn nháp AI của C07) → hoặc "Gỡ khỏi kho".
4. Ở trang chi tiết kho → "Đổi tên" hoặc "Xoá kho" (có hộp thoại xác nhận).
5. Ứng viên rút đồng ý ở `/candidate/applications/:id` (U09, không đổi giao diện) → đơn biến mất khỏi mọi kho.

## 3. Quy tắc nghiệp vụ

### 3.1 Ranh giới AI (R-A)

- **R-A1** H15 không thêm lời gọi AI nào (`ChatClient`, `ChatModel`, `EmbeddingModel`) và không tạo lượt chấm.
- **R-A2** Đánh giá AI, CV, trao đổi, soạn nháp xem bằng cách điều hướng sang trang H09 có sẵn; H15 không trả và không
  vẽ lại điểm, hạng, evidence, giải thích hay tin nhắn.
- **R-A3** Không thêm cột/field tên `verdict`, `label`, `isQualified`, `passed`, `recommendation` (CLAUDE.md §7).

### 3.2 Kho (R-K)

- **R-K1** Kho thuộc đúng một công ty (`company_id`). Thao tác trên kho chỉ dành cho HR sở hữu công ty đó (a8).
- **R-K2** Tên: `strip()` rồi kiểm 1–100 code point; ngoài khoảng → 400 `INVALID_TALENT_POOL_NAME`. Lưu bản đã strip.
- **R-K3** Tên không trùng trong các kho chưa xoá của cùng công ty, so `lower(name)`. Service kiểm trước để trả 409
  `TALENT_POOL_NAME_TAKEN`; chốt chặn thật là partial unique index `uq_talent_pool_name_per_company` (mục 4.1), handler
  dịch vi phạm index sang cùng mã 409.
- **R-K4** Đổi tên áp R-K2, R-K3, loại trừ chính kho đó (đổi hoa/thường tên của chính mình được phép).
- **R-K5** Xoá kho: đặt `deleted_at`. Không đụng tới mục. Kho đã xoá: mọi endpoint trả 404 `TALENT_POOL_NOT_FOUND`, không
  xuất hiện ở P1, P6. Tạo kho mới trùng tên kho đã xoá được phép.
- **R-K6** Không giới hạn số kho mỗi công ty `[Q4]`.
- **R-K7** Danh sách kho sắp theo `lower(name)` tăng dần, phụ `id`; không phân trang `[Q14]`. `entryCount` = số mục đang
  hiệu lực (R-M6).

### 3.3 Mục trong kho (R-M)

- **R-M1** Mục là liên kết (kho, đơn). Đơn phải thuộc công ty của kho (qua `jobs.company_id`).
- **R-M2** Một đơn có nhiều nhất một mục đang hiệu lực (`removed_at IS NULL`) trong một kho; chốt bằng partial unique
  `uq_talent_pool_entry_active`.
- **R-M3** Chỉ **thêm** được khi trạng thái đồng ý là `ACTIVE` (a2); khác → 409 `TALENT_POOL_CONSENT_REQUIRED`. **Gỡ**
  luôn được, ở mọi trạng thái đồng ý.
- **R-M4** Thêm được ở cả 5 trạng thái đơn `[Q7]`. Đổi trạng thái đơn (H07, U06) không thêm/gỡ mục.
- **R-M5** Gỡ: đặt `removed_at`. Thêm lại sau khi gỡ tạo dòng mới; dòng cũ giữ nguyên. Đường **ghi** (P7, P9) tìm mục
  theo `removed_at IS NULL` + kho chưa xoá của công ty, **không** lọc đồng ý — để gỡ được mục lệch (mục còn
  `removed_at IS NULL` trong khi đơn đã không `ACTIVE`, ví dụ dữ liệu sửa tay hoặc lỗi trước khi có R-C1).
- **R-M6** "Mục đang hiệu lực" = `removed_at IS NULL` **và** kho chưa xoá **và** đơn đang `ACTIVE`, viết trong SQL là
  `ja.talent_pool_consent = TRUE` — **cùng một điều kiện** với `TalentPoolConsentState.of` (a2, a3); không viết định nghĩa
  `ACTIVE` thứ hai. Điều kiện thứ ba là lưới an toàn: R-C1 đã gỡ mục khi rút, nhưng truy vấn đọc vẫn lọc để không bao giờ
  hiện đơn đã rút đồng ý. **Chỉ áp cho đường đọc:** P1 (`entryCount`), P6 (`contains`), P8. Không áp cho đường ghi
  (tập "hiện có" của R-P1, tìm mục ở P9 — R-M5). Hệ quả: đơn không `ACTIVE` luôn có `contains = false` ở P6.
- **R-M7** Danh sách mục sắp `added_at DESC, id DESC`; phân trang theo a11.
- **R-M8** Job bị xoá mềm hoặc đóng: mục giữ nguyên `[Q8]`; DTO có `jobDeleted = (jobs.deleted_at IS NOT NULL)`.
- **R-M9** Một ứng viên có thể có nhiều mục trong một kho qua nhiều đơn khác nhau `[Q9]`; không gộp.

### 3.4 Đặt danh sách kho của một đơn — P7 (R-P)

- **R-P1** Body `{ "poolIds": [uuid, …] }` = **toàn bộ** tập kho mong muốn chứa đơn. Backend tính: `thêm = poolIds −
  hiện có`, `gỡ = hiện có − poolIds` (hiện có = mục `removed_at IS NULL` trong kho chưa xoá của công ty, **không** lọc
  đồng ý — R-M5, để gỡ được mục lệch). `[]` = gỡ khỏi mọi kho. Đơn không `ACTIVE` gửi đúng tập mục lệch đang có → `thêm`
  rỗng, `gỡ` rỗng → 200, không đổi gì (không 409).
- **R-P2** Thứ tự kiểm, dừng ở lỗi đầu tiên:

  | Bước | Điều kiện | HTTP | Mã |
  |---|---|---|---|
  | 1 | Body thiếu, `poolIds` null, phần tử null, UUID sai | 400 | `INVALID_TALENT_POOL_SELECTION` |
  | 2 | `poolIds` có id trùng (so sau khi parse UUID) | 400 | `INVALID_TALENT_POOL_SELECTION` |
  | 3 | HR chưa có công ty | 404 | `COMPANY_NOT_FOUND` |
  | 4 | Đơn không tồn tại / thuộc công ty khác | 404 / 403 | `APPLICATION_NOT_FOUND` / — (dùng `HrApplicationAccess.loadOwned`) |
  | 5 | Có id kho không tồn tại hoặc đã xoá | 404 | `TALENT_POOL_NOT_FOUND` |
  | 6 | Có id kho thuộc công ty khác | 403 | — |
  | 7 | `thêm` khác rỗng và đồng ý không `ACTIVE` — đọc ba cột đồng ý **từ chính câu `SELECT … FOR SHARE`** của R-C2 (không dùng entity đã nạp ở bước 4, có thể cũ), suy trạng thái theo đúng quy tắc của `TalentPoolConsentState.of` | 409 | `TALENT_POOL_CONSENT_REQUIRED` |

  Bước 5 xét trước bước 6 trên **toàn bộ** danh sách (một id không tồn tại cho 404 dù id khác thuộc công ty khác).
- **R-P3** Lỗi ở bất kỳ bước nào → **không ghi gì** (một transaction, rollback toàn bộ), kể cả phần `gỡ` hợp lệ.
- **R-P4** Không giới hạn độ dài `poolIds` ngoài R-P2 (mọi id phải là kho hợp lệ, không trùng ⇒ tự bị chặn bởi số kho).
- **R-P5** Idempotent: gửi lại cùng tập → không tạo dòng mới, không đặt `removed_at`. Response 200 = hình dạng của P6.
  Thêm mục bằng native `INSERT … ON CONFLICT (pool_id, application_id) WHERE removed_at IS NULL DO NOTHING` (L1): hai P7
  đồng thời cùng thêm một mục (khoá `FOR SHARE` của R-C2 không chặn lẫn nhau) đều 200, đúng một dòng hiệu lực.

### 3.5 Rút đồng ý và tranh chấp (R-C)

- **R-C1** `ApplicationService.revokeTalentPoolConsent`: sau khi `UPDATE` có điều kiện trả 1 dòng, cùng transaction chạy
  một `UPDATE talent_pool_entries SET removed_at = :now WHERE application_id = :id AND removed_at IS NULL`
  (`@Modifying(clearAutomatically = true)`). Nhánh 409 `TALENT_POOL_CONSENT_NOT_ACTIVE` không chạm vào mục. Response,
  trạng thái đơn, lịch sử, thông báo của F4 không đổi (U09 R-K8).
- **R-C2** P7 khoá hàng đơn bằng `SELECT … FROM job_applications WHERE id = :id FOR SHARE` **trước** khi đọc trạng thái
  đồng ý, rồi khoá các kho trong `poolIds` bằng `FOR SHARE` (chặn xoá kho đồng thời). Lý do: khoá `FOR KEY SHARE` mà FK
  tự lấy khi `INSERT` mục **không** xung đột với `UPDATE` cột không phải khoá của R-C1, nên thiếu `FOR SHARE` thì một mục
  có thể chen vào sau khi R-C1 đã gỡ. Khoá giữ tới commit (tiền lệ khoá `FOR SHARE` của U09 R-R3).
- **R-C3** Không mở transaction quanh lời gọi ngoài (H15 không có lời gọi ngoài).

### 3.6 Phía ứng viên (R-U)

- **R-U1** Ứng viên không thấy kho, tên kho hay việc đơn có nằm trong kho. Không endpoint ứng viên nào đổi response; E1
  của U08/U09 giữ đúng bộ khoá (test hiện có `ApplicationCandidateDetailControllerIntegrationTest` vẫn pass).
- **R-U2** Không thông báo (C03) cho bất kỳ bên nào khi thêm/gỡ/xoá kho.

## 4. Dữ liệu & quyền truy cập

### 4.1 Migration `V17__talent_pools.sql`

```sql
-- FR-H15 - Kho ung vien (REQUIREMENT.md muc 4.1). Xoa mem ca hai bang (Q2).
CREATE TABLE talent_pools (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL CHECK (char_length(name) BETWEEN 1 AND 100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- NULL co chu dich: kho chua xoa.
    deleted_at  TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_talent_pool_name_per_company
    ON talent_pools (company_id, lower(name)) WHERE deleted_at IS NULL;
-- Ham set_updated_at() o V1__init_schema.sql:362-367; dang ky trigger cho bang moi theo mau V13:32-34.
CREATE TRIGGER trg_talent_pools_updated_at BEFORE UPDATE ON talent_pools
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE talent_pool_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pool_id         UUID NOT NULL REFERENCES talent_pools(id) ON DELETE RESTRICT,
    application_id  UUID NOT NULL REFERENCES job_applications(id) ON DELETE RESTRICT,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- NULL co chu dich: muc dang hieu luc.
    removed_at      TIMESTAMPTZ,
    CHECK (removed_at IS NULL OR removed_at >= added_at)
);
CREATE UNIQUE INDEX uq_talent_pool_entry_active
    ON talent_pool_entries (pool_id, application_id) WHERE removed_at IS NULL;
-- R-C1 go theo don; R-M7 liet ke theo kho.
CREATE INDEX idx_talent_pool_entries_application ON talent_pool_entries (application_id) WHERE removed_at IS NULL;
CREATE INDEX idx_talent_pool_entries_pool_added  ON talent_pool_entries (pool_id, added_at DESC, id DESC)
    WHERE removed_at IS NULL;
```

`talent_pools.company_id ON DELETE CASCADE` giống `jobs.company_id` (`V1__init_schema.sql:63`). `ON DELETE RESTRICT` ở
bảng mục: không đường nào xoá cứng kho hay đơn. `updated_at` do trigger `set_updated_at()` của V1 cập nhật
(`V1__init_schema.sql:359-378`; bảng mới tự đăng ký trigger như `V13__screening_questions.sql:32-34`); entity map cột
này chỉ đọc. `talent_pool_entries` không có `updated_at` (chỉ `removed_at` đổi một lần), không đăng ký trigger — giống
`application_messages` (`V12__application_messages.sql:2-3`). Không sửa V1–V16.

### 4.2 Package và file backend

Package mới `talentpool/` (thêm vào CLAUDE.md §3 ở đợt cuối, D5):

| File | Vai trò |
|---|---|
| `TalentPool.java`, `TalentPoolEntry.java` | Entity |
| `TalentPoolRepository.java`, `TalentPoolEntryRepository.java` | Query: danh sách kho kèm `entryCount`, khoá `FOR SHARE`, trang mục (native, một câu + câu đếm), gỡ theo đơn (R-C1) |
| `TalentPoolOwnerService.java` | P1–P5, P8 |
| `ApplicationTalentPoolService.java` | P6, P7 (R-P, R-C2) |
| `TalentPoolNameRules.java` | R-K2 (strip + đếm code point) |
| `TalentPoolOwnerController.java` | `/api/hr/talent-pools/**` |
| `ApplicationTalentPoolController.java` | `/api/hr/applications/{id}/talent-pools` |
| `TalentPoolExceptionAdvice.java` | `@RestControllerAdvice(assignableTypes = {TalentPoolOwnerController.class, ApplicationTalentPoolController.class})` + `@Order(Ordered.HIGHEST_PRECEDENCE)`, theo mẫu `CvQaExceptionAdvice.java:20-39`: bắt `HttpMessageNotReadableException` (JSON hỏng, sai kiểu, UUID sai) → 400 với mã theo endpoint — P2/P4 `INVALID_TALENT_POOL_NAME`, P7 `INVALID_TALENT_POOL_SELECTION`; body lỗi không chứa `ex.getMessage()`, chi tiết chỉ ở `log.debug` |
| `dto/…` | Mục 4.4 |

Sửa file có sẵn:
- `ApplicationService.java`: R-C1, sửa comment `:233-234`.
- `TalentPoolConsentState.java`: `of(JobApplication)` (hiện package-private, `:12`) đổi thành `public`; thêm overload
  `public static TalentPoolConsentState of(boolean consent, Instant revokedAt)` chứa **quy tắc duy nhất**, bản nhận entity
  gọi lại bản này. P7 dùng overload với ba cột đọc từ câu `SELECT … FOR SHARE` (R-P2 bước 7); P6 dùng bản nhận entity.
  Hành vi U09 không đổi.
- `GlobalExceptionHandler.java`: 6 mã mục 4.3 + nhánh `uq_talent_pool_name_per_company` trong
  `handleDataIntegrityViolation`; `common/exception/`: exception mới.
Phụ thuộc vòng package `jobapplication` ↔ `talentpool` (R-C1 gọi repository mục; P6/P7 dùng `HrApplicationAccess`) chấp
nhận được — cùng một ứng dụng Spring, không phải module.

### 4.3 Endpoint

Mọi endpoint dưới `/api/hr/**`: không token → 401; token ứng viên → 403 (a10). Quyền sở hữu theo a9/R-K1.

| # | Method, path | Body | Thành công | Lỗi riêng |
|---|---|---|---|---|
| P1 | `GET /api/hr/talent-pools` | — | 200 `List<TalentPoolResponse>` | 404 `COMPANY_NOT_FOUND` |
| P2 | `POST /api/hr/talent-pools` | `{ name }` | 201 `TalentPoolResponse` | 400 `INVALID_TALENT_POOL_NAME`, 409 `TALENT_POOL_NAME_TAKEN` |
| P3 | `GET /api/hr/talent-pools/{id}` | — | 200 `TalentPoolResponse` | 404 `TALENT_POOL_NOT_FOUND`, 403 |
| P4 | `PATCH /api/hr/talent-pools/{id}` | `{ name }` | 200 `TalentPoolResponse` | như P2 + P3 |
| P5 | `DELETE /api/hr/talent-pools/{id}` | — | 204 | như P3 |
| P6 | `GET /api/hr/applications/{id}/talent-pools` | — | 200 `ApplicationTalentPoolsResponse` | 404 `APPLICATION_NOT_FOUND`, 403 |
| P7 | `PUT /api/hr/applications/{id}/talent-pools` | `{ poolIds }` | 200 `ApplicationTalentPoolsResponse` | R-P2 |
| P8 | `GET /api/hr/talent-pools/{id}/entries?page=&size=` | — | 200 `PageResponse<TalentPoolEntryResponse>` | như P3 |
| P9 | `DELETE /api/hr/talent-pools/{id}/entries/{applicationId}` | — | 204 | như P3; 404 `TALENT_POOL_ENTRY_NOT_FOUND` khi đơn không có mục `removed_at IS NULL` trong kho (không lọc đồng ý — R-M5) |

Mã lỗi mới và câu tiếng Việt cố định:

| Mã | HTTP | Câu |
|---|---|---|
| `INVALID_TALENT_POOL_NAME` | 400 | "Tên kho phải có từ 1 đến 100 ký tự." |
| `TALENT_POOL_NAME_TAKEN` | 409 | "Công ty đã có kho ứng viên với tên này." |
| `TALENT_POOL_NOT_FOUND` | 404 | "Không tìm thấy kho ứng viên." |
| `TALENT_POOL_ENTRY_NOT_FOUND` | 404 | "Hồ sơ này không có trong kho." |
| `INVALID_TALENT_POOL_SELECTION` | 400 | "Danh sách kho không hợp lệ. Mỗi kho chỉ được chọn một lần." |
| `TALENT_POOL_CONSENT_REQUIRED` | 409 | "Ứng viên chưa đồng ý cho công ty lưu hồ sơ của đơn này nên không thể thêm vào kho." |

Body P2/P4 không parse được hoặc `name` null → 400 `INVALID_TALENT_POOL_NAME`; body P7 không parse được (JSON hỏng,
`poolIds` không phải mảng, phần tử không phải UUID) → 400 `INVALID_TALENT_POOL_SELECTION`. Cơ chế: `TalentPoolExceptionAdvice`
(mục 4.2); không dùng `@Valid`/map lỗi của `MethodArgumentNotValidException`.

### 4.4 DTO

```java
// P1-P4. entryCount theo R-M6.
public record TalentPoolResponse(UUID id, String name, long entryCount, Instant createdAt) {}

// P6, P7. consentState la trang thai U09 (a2); pools = MOI kho chua xoa cua cong ty, sap theo R-K7.
public record ApplicationTalentPoolsResponse(TalentPoolConsentState consentState, List<PoolMembership> pools) {
    public record PoolMembership(UUID id, String name, boolean contains) {}
}

// P7.
public record ApplicationTalentPoolsRequest(List<UUID> poolIds) {}

// P8. candidateName lay cung nguon voi E1 cua H09 (ApplicationHrDetailService).
public record TalentPoolEntryResponse(
        UUID applicationId, String candidateName, UUID jobId, String jobTitle, boolean jobDeleted,
        ApplicationStatus applicationStatus, Instant appliedAt, Instant addedAt) {}
```

Không DTO nào chứa điểm, hạng, evidence, giải thích, nội dung CV hay tin nhắn (R-A2).

### 4.5 Seed

Không thêm dữ liệu seed. Soát tay dùng bộ test có sẵn (mục 7.4). **Phải sửa** `db/seed/reset-test-data.sql`: script
xoá đơn bằng `DELETE` tường minh (`:92-99`), FK `talent_pool_entries.application_id ON DELETE RESTRICT` sẽ làm script
lỗi khi bộ test đã có mục trong kho. Thêm, **trước** khối "Con cua job_applications" (`:92`): `DELETE FROM
talent_pool_entries` theo `application_id IN t_apps` hoặc `pool_id` thuộc kho của `t_companies`, rồi `DELETE FROM
talent_pools WHERE company_id IN t_companies`. Đây là script dọn dữ liệu test, không phải đường nghiệp vụ, nên không
trái Q2. `db/seed/reset-demo-db.sql`: `TRUNCATE … CASCADE` đã quét theo mọi FK (`:83-85`), nhưng quy ước của script là
liệt kê tường minh mọi bảng (tiền lệ FR-H12 `:95-96`, FR-C06 `:97-98`) — thêm `talent_pools, talent_pool_entries` vào
danh sách kèm comment `FR-H15, V17`.

## 5. AI

Không dùng AI (R-A1). Không dùng K1–K4.

## 6. Ngoài phạm vi

- Ghi chú, gắn thẻ, AI tóm tắt, bộ lọc, tìm kiếm trong kho (`docs/features/README.md:862`).
- Kho CV công khai; thêm ứng viên chưa nộp đơn vào công ty (`docs/SRS.md` mục 5).
- Liên hệ hàng loạt; thao tác hàng loạt trên nhiều đơn.
- Hiện trạng thái đồng ý ở màn hình HR nào khác ngoài hộp thoại "Lưu vào kho".
- Đồng ý lại sau khi rút (U09).
- Thêm vào kho từ danh sách theo Job, `/hr/candidates`, trang so sánh.
- Che thông tin nhân thân trong danh sách mục (FR-H16 tự quyết, D6).

## 7. Xong khi

### 7.1 Test backend (Testcontainers, full `./mvnw test` xanh)

| T | Nội dung | Loại |
|---|---|---|
| T1 | `TalentPoolNameRules`: rỗng, chỉ khoảng trắng → lỗi; 1, 100 code point → đạt; 101 → lỗi; strip hai đầu; 100 ký tự có dấu tiếng Việt đạt | Unit |
| T2 | P2: 201, tên đã strip; 0/1/100/101 ký tự (400/201/201/400); trùng khác hoa thường cùng công ty → 409; trùng tên ở công ty khác → 201; trùng tên kho đã xoá → 201; JSON hỏng, `name` là số → 400 `INVALID_TALENT_POOL_NAME`, body lỗi không lặp lại nội dung gửi lên (P2 và P4) | Integration |
| T3 | Chốt DB (L2), hai phần: (a) `INSERT` thẳng bằng JDBC hai kho cùng công ty, cùng `lower(name)`, chưa xoá → `DataIntegrityViolationException` có tên `uq_talent_pool_name_per_company`; (b) gọi thẳng `GlobalExceptionHandler.handleDataIntegrityViolation` với chính exception ở (a) → 409 `TALENT_POOL_NAME_TAKEN` | Integration |
| T4 | P4: đổi tên đạt; đổi hoa thường tên của chính mình → 200; trùng kho khác → 409; kho đã xoá → 404; kho công ty khác → 403 | Integration |
| T5 | P5: 204; sau đó P3/P4/P8/P9 → 404; P1, P6 không còn kho; hàng `talent_pools` còn, `deleted_at` khác null; hàng mục không đổi | Integration |
| T6 | P1: chỉ kho của công ty mình, chưa xoá, sắp theo R-K7; `entryCount` không đếm mục đã gỡ, không đếm mục lệch của đơn không `ACTIVE` (R-M6 đường đọc); HR chưa có công ty → 404 | Integration |
| T7 | P7 thêm khi `ACTIVE`: tạo mục; P6 `contains = true` đúng kho | Integration |
| T8 | P7 thêm khi `NOT_GIVEN` / `REVOKED` → 409, không dòng mới | Integration |
| T9 | P7 chỉ gỡ khi `NOT_GIVEN`/`REVOKED` (mục lệch tạo thẳng bằng SQL; với `REVOKED` đặt cả cờ `FALSE` và `revoked_at` — CHECK V14): `poolIds` bỏ kho đó → 200, `removed_at` được đặt (R-P1 "hiện có" không lọc đồng ý); gửi lại đúng tập mục lệch → 200, không đổi gì; thêm một kho mới → 409, mục lệch giữ nguyên | Integration |
| T10 | P7: `poolIds` null, phần tử null, body rỗng, JSON hỏng, `poolIds` không phải mảng, phần tử không phải UUID (`"abc"`) → 400 `INVALID_TALENT_POOL_SELECTION`; id trùng (khác hoa thường) → 400; `[]` → gỡ mọi kho; mọi ca 400: bảng mục không đổi | Integration |
| T11 | P7 nguyên tử: danh sách có 1 kho hợp lệ + 1 kho không tồn tại → 404; + 1 kho đã xoá → 404; + 1 kho công ty khác → 403; + cả không tồn tại và công ty khác → 404 (R-P2 bước 5 trước 6); mọi ca: bảng mục không đổi, kể cả phần gỡ | Integration |
| T12 | P6/P7 quyền đơn: đơn công ty khác → 403; không tồn tại → 404; HR chưa có công ty → 404 `COMPANY_NOT_FOUND` | Integration |
| T13 | Thêm được ở cả 5 trạng thái đơn (tham số hoá); đổi trạng thái sau khi thêm không gỡ mục | Integration |
| T14 | Idempotent: P7 cùng tập hai lần → số dòng không đổi; gỡ rồi thêm lại → dòng mới, dòng cũ giữ `removed_at` | Integration |
| T15 | R-C1: rút đồng ý gỡ mục ở mọi kho (hai kho, hai công ty không lẫn); rút lần hai 409 không chạm mục; response F4 giữ nguyên hình dạng | Integration |
| T16 | R-C2 tranh chấp (lớp **không** `@Transactional` toàn lớp, CLAUDE.md §3c): luồng A mở transaction, `UPDATE` rút đồng ý chưa commit; luồng B gọi P7 thêm → bị chặn; A commit; B nhận 409, không mục nào hiệu lực. Ca thêm (L1): hai P7 đồng thời cùng thêm một (kho, đơn) `ACTIVE`, phát cùng lúc qua `CountDownLatch` → cả hai 200, đúng 1 dòng `removed_at IS NULL` cho cặp đó | Integration |
| T17 | P8: mặc định size 10; `size=50` → 50; `size=51` → 50; `size=0` → 10; `page=-1` → 0; sắp `added_at DESC`; `jobDeleted = true` khi Job xoá mềm; đơn có mục hiệu lực bị đặt thẳng bằng SQL sang trạng thái `REVOKED` (`talent_pool_consent = FALSE` **và** `talent_pool_consent_revoked_at` khác null — chỉ đặt cờ sẽ vi phạm CHECK V14) mà không qua R-C1 → không hiện ở P8, không đếm ở P1, `contains = false` ở P6 (R-M6); mục lệch đó vẫn gỡ được: P9 → 204, `removed_at` được đặt; dựng lại mục lệch khác, P7 `[]` → 200, `removed_at` được đặt (R-M5) | Integration |
| T18 | P9: 204, `removed_at` đặt; gỡ lần hai → 404 `TALENT_POOL_ENTRY_NOT_FOUND`; đơn chưa từng ở kho → 404; kho đã xoá → 404; kho công ty khác → 403 | Integration |
| T19 | Vai trò: token ứng viên → 403 ở P1–P9; không token → 401 | Integration |
| T20 | Job xoá mềm: mục vẫn ở P8, E1 của H09 cho đơn đó vẫn 200 | Integration |

Mock `ChatModel`/`EmbeddingModel` giữ default-answer throw như hiện có (CLAUDE.md §7); không test nào của H15 stub AI.

### 7.2 Kiểm bằng HTTP — Windows PowerShell 5.1

Khối lệnh viết ở đợt cuối theo hàm trợ giúp của H14 mục 7.2 (gửi UTF-8, đọc UTF-8). Nhóm phải khớp:

| Nhóm | Mong đợi |
|---|---|
| 1 | P2 tạo 2 kho → 201 ×2; P2 trùng tên → 409; tên 101 ký tự → 400 |
| 2 | P7 đơn `ACTIVE` vào 2 kho → 200, P6 `contains` đúng; P8 mỗi kho 1 mục |
| 3 | P7 đơn `NOT_GIVEN` → 409 `TALENT_POOL_CONSENT_REQUIRED` |
| 4 | P7 có id trùng → 400; có id không tồn tại → 404; bảng không đổi (P6 như nhóm 2) |
| 5 | Ứng viên rút đồng ý (F4) → P8 cả hai kho rỗng, P1 `entryCount = 0` |
| 6 | P4 đổi tên → 200; P5 xoá → 204; P3 → 404 |
| 7 | Token ứng viên gọi P1 → 403; không token → 401 |

### 7.3 Frontend

`npm run build` + `npm run lint` sạch. Kiểm tĩnh (0 dòng):

```bash
MSYS_NO_PATHCONV=1 rg -n "ChatClient|ChatModel|EmbeddingModel" backend/src/main/java/com/recruitment/talentpool
MSYS_NO_PATHCONV=1 rg -n -i "verdict|isQualified|passed|recommendation" backend/src/main/java/com/recruitment/talentpool backend/src/main/resources/db/migration/V17__talent_pools.sql frontend/src/features/talentPools
MSYS_NO_PATHCONV=1 rg -n "DELETE FROM" backend/src/main/java/com/recruitment/talentpool
MSYS_NO_PATHCONV=1 rg -n "totalScore|rank|evidence" frontend/src/features/talentPools
```

### 7.4 Soát tay (desktop ≥ `lg`, 768px, 375px)

Dữ liệu: **bộ test** `db/seed/seed-test.sql`, nạp theo `db/seed/README.md` mục 8.1 (nạp được trên DB chỉ có schema
hoặc cùng demo). Seed demo không có đơn nào đồng ý lưu hồ sơ (a20) nên không dùng. Mật khẩu mọi tài khoản test
`12345678` (`seed-test.sql:30`).

| Vai trò | Tài khoản | Căn cứ |
|---|---|---|
| HR (công ty test, sở hữu J1, J2) | `minhdathr@gmail.com` — Minh Đạt | `seed-test.sql:49-51`, `:140` |
| Ứng viên của đơn `ACTIVE` | `quochuyuv@gmail.com` — Quốc Huy | `seed-test.sql:52-54` |

| Đơn | Ứng viên → Job | Trạng thái đơn | Đồng ý | Căn cứ |
|---|---|---|---|---|
| A1 `e8000000-0000-0000-0000-000000000001` | Quốc Huy → J1 "Chuyên viên Chăm sóc khách hàng" | `PENDING` | `ACTIVE` | `seed-test.sql:361-363`, `:371-375`, `:141` |
| A7 `e8000000-0000-0000-0000-000000000007` | Phạm Văn Khoa → J1 | `REJECTED` | `NOT_GIVEN` | `seed-test.sql:354`, `:392` |
| A2 `e8000000-0000-0000-0000-000000000002` | Quốc Huy → J2 "Nhân viên Hành chính văn phòng" | `INTERVIEW_INVITED` | `NOT_GIVEN` | `seed-test.sql:376-378`, `:149` |

`REVOKED`: không đơn nào có sẵn (`seed-test.sql:363`); tạo ở nhóm C bằng cách Quốc Huy rút đồng ý của A1.

**A. Kho rỗng và đơn `NOT_GIVEN`** — `minhdathr@gmail.com`.

| Trạng thái cần thấy | Nơi | Mong đợi |
|---|---|---|
| Mục điều hướng | Sidebar | Mục "Kho ứng viên" (≥ `lg`), "Kho" trên rail (< `lg`); đang chọn khi ở `/hr/talent-pools` và `/hr/talent-pools/:id` |
| Rỗng | `/hr/talent-pools` | "Chưa có kho ứng viên nào." + nút "Tạo kho" |
| Tạo / trùng tên | Hộp thoại "Tạo kho" | Tạo "Chăm sóc khách hàng tiềm năng" → hiện trong danh sách, "0 hồ sơ"; tạo lại "chăm sóc khách hàng tiềm năng" → câu 409 ngay dưới ô nhập, hộp thoại không đóng |
| Tên dài | Hộp thoại "Tạo kho" | Gõ quá 100 ký tự → câu lỗi tại ô, nút "Lưu" khoá |
| Kho rỗng | `/hr/talent-pools/:id` | "Kho này chưa có hồ sơ nào." + hướng dẫn lưu từ trang hồ sơ đơn |
| `NOT_GIVEN` | `/hr/applications/e8000000-0000-0000-0000-000000000007` (A7) → "Lưu vào kho" | Hộp thoại hiện câu `NOT_GIVEN`; ô kho khoá; không có nút "Lưu" bấm được. Lặp lại với A2: như trên |

**B. Trên đơn `ACTIVE`** — `minhdathr@gmail.com`, đơn A1 `/hr/applications/e8000000-0000-0000-0000-000000000001`.

| Trạng thái cần thấy | Nơi | Mong đợi |
|---|---|---|
| Lưu vào 2 kho | A1 → "Lưu vào kho" | Tạo thêm kho "Hành chính 2027" trước; tick cả hai → "Lưu" → hộp thoại đóng, câu "Đã cập nhật kho ứng viên." ở header; mở lại hộp thoại → hai ô tick sẵn |
| Danh sách mục | `/hr/talent-pools/:id` | Một dòng: "Quốc Huy", "Chuyên viên Chăm sóc khách hàng", badge "Chờ duyệt" trung tính, ngày nộp theo `applied_at` của A1, ngày thêm hôm nay; không có điểm/hạng |
| Điều hướng | "Mở hồ sơ" / "Trao đổi" | Mở A1 tab CV & điểm / `?tab=messages`; nút "Soạn bằng AI" của C07 có mặt; Back trình duyệt về lại kho |
| Gỡ | Kho "Hành chính 2027" → "Gỡ khỏi kho" → xác nhận | Dòng biến mất, "0 hồ sơ"; kho còn lại vẫn chứa A1 |
| Đổi tên / Xoá | Kho "Hành chính 2027" | Đổi tên → tiêu đề đổi; Xoá → xác nhận → về `/hr/talent-pools`, kho không còn; dán lại URL cũ → "Không tìm thấy kho ứng viên." |
| 375px | `/hr/talent-pools`, `/hr/talent-pools/:id`, hộp thoại | Không cuộn ngang toàn trang; rail 80px đủ 6 mục không tràn; danh sách mục xếp dạng thẻ; nút vùng chạm ≥ 48px |

**C. Rút đồng ý → `REVOKED`** (làm cuối cùng, một chiều).

| Trạng thái cần thấy | Nơi | Mong đợi |
|---|---|---|
| Rút | `quochuyuv@gmail.com` → `/candidate/applications/e8000000-0000-0000-0000-000000000001` → "Rút đồng ý" | Khối "Lưu hồ sơ cho đợt tuyển sau" đổi sang trạng thái đã rút (giao diện U09, không đổi) |
| Kho sau khi rút | `minhdathr@gmail.com` tải lại `/hr/talent-pools` | Kho "Chăm sóc khách hàng tiềm năng" "0 hồ sơ"; trang chi tiết rỗng |
| Hộp thoại | A1 → "Lưu vào kho" | Câu `REVOKED`; ô kho khoá |

**Dọn dẹp (bắt buộc):** chạy `db/seed/reset-test-data.sql` (đã sửa ở đợt 2, mục 4.5) rồi nạp lại bộ test
(`db/seed/README.md` mục 8.2).

### 7.5 Truy vết quy tắc → kiểm thử

| Quy tắc | Kiểm |
|---|---|
| R-A1, R-A3 | 7.3 tĩnh |
| R-A2 | 7.3 tĩnh (frontend); DTO mục 4.4 (review) |
| R-K2 | T1, T2 |
| R-K3 | T2, T3 |
| R-K4 | T4 |
| R-K5 | T5 |
| R-K6 | Không kiểm tự động (không có ngưỡng) |
| R-K7 | T6 |
| R-M1, R-M2 | T7, T12, T14 |
| R-M3 | T7, T8, T9 |
| R-M4 | T13 |
| R-M5 | T9, T14, T17, T18 |
| R-M6 | T6, T17 |
| R-M7 | T17 |
| R-M8 | T17, T20 |
| R-M9 | Không test riêng (hệ quả của khoá `(pool_id, application_id)`) |
| R-P1, R-P5 | T7, T9, T10, T14, T16 (L1), T17 |
| R-P2, R-P3 | T10, T11, T12 |
| R-C1 | T15; 7.2 nhóm 5 |
| R-C2 | T16 |
| R-U1 | Test có sẵn của E1 phía ứng viên vẫn pass |
| R-U2 | Review (không tạo thông báo) |

## 8. AI hay làm sai (dành cho người code)

- Hard delete kho hoặc mục (`DELETE FROM`, `repository.delete`) — trái Q2.
- Gỡ mục khi rút đồng ý bằng event/`@TransactionalEventListener`/job nền thay vì **cùng transaction** (R-C1).
- Quên `FOR SHARE` ở P7, tin rằng FK đã khoá đủ (R-C2).
- Kiểm trùng tên bằng `equalsIgnoreCase` ở Java mà không có partial unique index; hoặc index không có `WHERE deleted_at IS NULL`.
- Đếm độ dài tên bằng `String.length()` (UTF-16) thay vì code point.
- P7 lưu phần hợp lệ rồi mới báo lỗi phần sai; hoặc tự bỏ id trùng thay vì 400 (R-P2, R-P3).
- Chặn **gỡ** khi đồng ý không `ACTIVE` (R-M3 chỉ chặn thêm).
- Áp điều kiện đồng ý của R-M6 vào đường ghi (tập "hiện có" của R-P1, tìm mục ở P9) — mục lệch sẽ không gỡ được (R-M5).
- Viết định nghĩa `ACTIVE` thứ hai trong SQL khác `TalentPoolConsentState.of`, hoặc tin rằng `updatable = false` làm cờ còn
  `TRUE` sau khi rút (a3: rút đi qua native `UPDATE`, cờ thành `FALSE`).
- Chặn thêm đơn `REJECTED`/`WITHDRAWN` (trái Q7).
- Trả 404 cho kho công ty khác "cho an toàn" — quy ước nhóm route HR là 403 (a9).
- Thêm field đồng ý vào E1 của H09 (trái Q11).
- Hiện điểm/hạng trong danh sách mục; vẽ lại CV hay tin nhắn trong trang kho thay vì dẫn sang H09.
- Thêm thông tin kho vào bất kỳ response nào phía ứng viên.
- Đặt test T16 trong lớp `@Transactional` toàn lớp (hai luồng không thấy nhau, test pass giả).
- Thêm mục bằng `INSERT` thường (hoặc `save()`) rồi để vi phạm `uq_talent_pool_entry_active` khi hai P7 chạy đồng thời
  nổi lên thành 500 — dùng `ON CONFLICT … DO NOTHING` (R-P5, L1).

## 9. Kế hoạch đợt code

Mỗi đợt: code xong → kiểm → dừng → báo cáo diff → chờ duyệt → một commit.

| Đợt | Nội dung | Kiểm |
|---|---|---|
| 1 | Plan Mode: đối chiếu lại mục 0 và dữ liệu 7.4 trên HEAD; chốt danh sách file | — |
| 2 | `V17`, entity, repository, `TalentPoolNameRules`, overload `TalentPoolConsentState.of`, R-C1 trong `ApplicationService` + sửa comment `:233-234`, sửa `db/seed/reset-test-data.sql` và `db/seed/reset-demo-db.sql` (mục 4.5); T1, T3, T15 | test-compile; `-Dtest` lớp của đợt + `ApplicationCandidate*Test` |
| 3 | P1–P9, exception + handler, `TalentPoolExceptionAdvice`, R-P, R-C2; T2, T4–T14, T16–T20 | test-compile; `-Dtest` lớp mới |
| 4 | Frontend: route, mục điều hướng, `/hr/talent-pools`, `/hr/talent-pools/:id` (tạo/đổi tên/xoá kho, danh sách mục, gỡ) | build + lint |
| 5 | Frontend: nút + hộp thoại "Lưu vào kho" ở header H09 | build + lint; 7.3 |
| 6 | Đợt cuối: full `./mvnw test`, 7.2, 7.3, 7.4, `srs-guard`, `walkthrough`, trạng thái `ĐÃ HOÀN THÀNH`, mục 10, tick ROADMAP | full test; build + lint |

## 10. Tài liệu dùng chung sẽ sửa (ở đợt cuối, không sửa trong commit đặc tả)

| # | File | Thay đổi |
|---|---|---|
| D1 | `docs/SRS.md` | Dòng 42: `Chưa đặc tả` → `Đã hoàn thành`; dòng 109: tóm tắt thêm "tạo, đổi tên, xoá kho; gỡ đơn khỏi kho" |
| D2 | `docs/ROADMAP.md` | Dòng 1014: tick + tóm tắt; đóng nợ ở dòng 884-885 (R-C1, R-M3) |
| D3 | `docs/UI_GUIDE.md` mục 7 | Dòng 363: bỏ ★; dòng 362 (`/hr/applications/:id`): bỏ ★ ở route và đổi cột "★Mới" → "Hiện có" (H09 đã hoàn thành), thêm "nút Lưu vào kho (FR-H15)"; ghi chú dòng 370: "nút Thêm vào kho (FR-H15)" → "nút Lưu vào kho (FR-H15 — đã thêm phần của mình)" |
| D4 | `docs/features/README.md`; `docs/features/HR/H09/REQUIREMENT.md` | README mục 16: thêm đổi tên/xoá kho, gỡ đơn, lưu ở mọi trạng thái đơn; dòng 862 cập nhật phạm vi còn lại. Đổi "Thêm vào kho" → "Lưu vào kho" ở README `:281`, `:696`, `:705`, `:828` và H09 REQUIREMENT `:97` (chỉ đổi nhãn) |
| D5 | `CLAUDE.md` §3 | Thêm `talentpool/` (FR-H15) vào danh sách package |
| D6 | `docs/features/HR/H16/REQUIREMENT.md` (khung) | Một dòng: "Danh sách mục trong kho (H15 P8, `TalentPoolEntryResponse.candidateName`) hiện tên ứng viên — H16 tự quyết có che không" |
| D7 | `docs/features/CHUNG/C07/REQUIREMENT.md` | Chỉ sửa câu sai, không viết lại. `:41` (b4): "Ghi chú HR thật sự chỉ có từ FR-H15" → "Hệ thống không có ghi chú HR (FR-H15 rút gọn 16/10/2026 không có ghi chú)". `:181`: "(C08, H13, H15 tự thêm khi tới lượt — Q9)" → "(FR sau cần thì tự thêm — Q9; H13 đã loại, H15 rút gọn không dùng AI)". `:378`: "(C08, H13, H15)" → "(FR sau cần thì tự thêm; H13 đã loại, H15 rút gọn không dùng AI)". Không sửa `:618` (ghi lịch sử) |

## 11. Làm rõ sau Plan Mode (duyệt 16/10/2026)

Đối chiếu lại trên `feat/fr-h15-talent-pool` tại `347f47c` (chỉ khác `f4757b4` ở hai file đặc tả; mục 0 a1–a20 và dữ liệu
7.4 không dòng nào lệch). Mục này thắng chỗ viết cũ ở các mục được dẫn nếu lệch.

- **L1 (R-P5, T16, mục 8) — thêm mục đồng thời.** Khoá `FOR SHARE` của R-C2 chặn rút đồng ý nhưng **không** chặn hai P7
  với nhau (hai khoá chia sẻ không xung đột). Hai P7 cùng thêm một (kho, đơn) đều thấy "hiện có" rỗng, request thứ hai vi
  phạm `uq_talent_pool_entry_active`; handler không ánh xạ constraint này nên thành 500. Chốt: thêm bằng native
  `INSERT … ON CONFLICT (pool_id, application_id) WHERE removed_at IS NULL DO NOTHING` (tiền lệ cú pháp `ON CONFLICT`:
  `ScoreExplanationAttemptRepository.java:41`) — idempotent, khớp R-P5; không thêm mã lỗi. T16 thêm ca hai P7 đồng thời;
  mục 8 thêm dòng tương ứng.
- **L2 (T3) — cách kiểm ánh xạ constraint.** `INSERT` thẳng bằng JDBC không đi qua `GlobalExceptionHandler`, nên câu cũ
  "handler trả 409" không kiểm được bằng một bước. Chốt: tách (a) JDBC → exception mang tên constraint; (b) gọi thẳng
  `handleDataIntegrityViolation` với exception đó → 409 `TALENT_POOL_NAME_TAKEN`. Không đổi hành vi.
- **L3 (`UI.md` mục 5a) — file chuỗi giao diện.** Thêm `frontend/src/features/talentPools/talentPoolLabels.ts` chứa chuỗi
  `UI.md` mục 7, theo mẫu `features/screening/screeningLabels.ts`, `features/comparison/comparisonLabels.ts`. Lệnh 7.3
  quét cả thư mục `features/talentPools` nên không phải sửa.
- Không đổi spec (ghi nhận): không thêm test đếm số câu SQL — P1 đếm bằng subquery trong một câu, P8 một câu + câu đếm,
  không N+1; `candidateName` ở P8 luôn có vì `JOIN users` theo FK `job_applications.candidate_id ON DELETE RESTRICT`
  (`V1__init_schema.sql:209`).
