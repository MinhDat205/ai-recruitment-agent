# Walkthrough: chore/seed-test — Bộ dữ liệu test độc lập để soát giao diện

## 1. Mục tiêu

Có sẵn dữ liệu ở đủ mọi trạng thái giao diện mà không phải bấm tay:
- tin ở 4 trạng thái;
- đơn ở 5 trạng thái, kèm lịch sử và giấy mời;
- CV chờ trích xuất, CV thất bại, CV đã trích xuất;
- lượt chấm đang chờ và lượt chấm thất bại;
- thông báo đã đọc và chưa đọc;
- hồ sơ khai đủ, hồ sơ bỏ qua onboarding, người dùng chưa có hồ sơ.

Bộ test độc lập với hai tầng demo. Nó nạp được trên DB chỉ có schema, và nạp chung với
demo không va chạm (UUID tiền tố `e0…`–`ec…`, email và tên công ty riêng). **Chỉ để soát
giao diện, không nạp khi demo cho hội đồng.**

## 2. Các file

- `db/seed/seed-test.sql`: SQL viết tay, idempotent. Ngoại lệ duy nhất là dòng
  `resume_parsed_data` của R3, là output thật của pipeline (mục 4).
- `db/seed/reset-test-data.sql`: chỉ xoá dữ liệu của 5 user test và công ty test, kể cả dòng
  phát sinh qua UI. Có hai chốt chặn: biến `-v TEST_SEED_CONFIRM` và tên DB.
- `db/seed/install-test-files.ps1`: sinh 5 PDF ngay trong script (không commit PDF nào) và
  chép CV DOCX của R3.
- `db/seed/test-files/cv-mau-quoc-huy.docx`: CV hư cấu tiếng Việt có dấu, 1 trang, đầu trang
  ghi "CV MẪU – DỮ LIỆU TEST".
- `db/seed/README.md`: thêm mục 8 (cách nạp, tài khoản, bảng soát, lượt gọi API ngoài).
- `docs/ROADMAP.md`: thêm một dòng đã tick ở mục "Chuẩn bị" của Giai đoạn 2.

Không sửa backend, frontend, migration, hay hai file seed demo.

## 3. Quyết định thiết kế

**Đóng băng trạng thái chờ bằng `next_attempt_at = 2099`.** Scheduler trích xuất và
scheduler chấm điểm chỉ nhặt dòng có `next_attempt_at IS NULL OR <= now()`. Bộ test không
seed `PROCESSING`/`RUNNING` vì stale-claim reaper sẽ thu hồi chúng.

**Ba lượt OpenAI là chấp nhận, không chặn.** Hai tin OPEN chưa có `job_embeddings`, và
hồ sơ khai đủ có `embedding` NULL. Hai loại dữ liệu này không có cột trạng thái chờ để
đóng băng, và quy định cấm làm giả embedding.

**R3 không phải CV chính.** `ResumeEmbeddingScheduler` chỉ quét `is_primary = TRUE`, nên
CV đã trích xuất không gây lượt gọi OpenAI.

**Mốc thời gian tuyệt đối.** Thứ tự sự kiện trong lịch sử đơn luôn nhất quán bất kể ngày
nạp. Chỉ hạn nộp của tin chưa đóng và lịch phỏng vấn sắp tới là tính tương đối, để luôn
còn hạn và luôn ở tương lai.

**Thông báo khớp 1-1 với sự kiện thật.** Tổng cộng 16 dòng, theo đúng
`NotificationContentBuilder`, đều `SKIPPED`. Không có `SCORING_FINISHED` vì không có lượt
chấm DONE.

**Sáu tin chứ không phải bốn.** Ràng buộc `uq_application_per_cycle` buộc phải có ít nhất
5 tin không phải DRAFT để Quốc Huy có đơn ở đủ 5 trạng thái.

**Tin chưa chuẩn hoá để ở trạng thái PAUSED.** R-J3 chặn mọi đường tạo hoặc mở tin thiếu
mã danh mục. Trạng thái này chỉ tồn tại ở tin cũ do V9 chuyển sang.

## 4. Vì sao R3 có output AI thật (phương án 6b)

`ScoringRunService.createScoringRun` đòi CV có `parse_status = DONE` **và** có dòng
`resume_parsed_data`. Vì vậy một lượt chấm, dù đang chờ hay thất bại, không thể nằm trên
CV chưa trích xuất. Đã chọn chạy pipeline thật đúng một lần thay vì viết tay output trích
xuất:
- DB lúc chạy chỉ có user Quốc Huy và R3; mọi scheduler gọi OpenAI hoặc SMTP đều tắt.
- Kết quả: 1 lượt Anthropic, `claude-sonnet-4-6`, `resume-parse-v2`, 5 246 token, kinh nghiệm
  74 tháng / 2 mục.
- Dòng kết quả được `pg_dump` ra và ghép nguyên văn vào seed bằng script, không gõ lại.
- Nội dung trích xuất giữ nguyên văn và nguyên ngôn ngữ của CV.

Dòng này phải chạy lại khi schema `resume_parsed_data` hoặc phiên bản prompt đổi.

## 5. Sự cố trong lúc làm

1. **Một backend khác (PID 14164) đang chạy chung DB.** Nó nhặt R3 ngay khi tôi nạp và
   làm R3 FAILED với `LLM_ERROR`. Đã dừng sau khi được duyệt.
2. **Lần chạy 6b thứ hai bị 401.**
   - Nguyên nhân: backend đọc `.env` trong thư mục làm việc (`springboot4-dotenv`, kiểm bằng
     `javap`), tức `backend/.env` khi chạy từ `backend/`.
   - Trong file đó, khoá Anthropic cũ (sửa lần cuối 17/08) và khoá OpenAI là chuỗi giữ chỗ.
   - Lần thứ ba nạp inline từ `.env` gốc repo thì thành công.
   - Tổng cộng có 2 lượt Anthropic bị từ chối 401, không tiêu token.
3. **`install-demo-files.ps1` xoá mọi file `.pdf` trong `backend/uploads/resumes/`** rồi dừng
   ở file test đầu tiên nếu DB đã có bộ test. Không sửa script demo; README ghi thứ tự bắt
   buộc: cài file demo trước khi nạp bộ test.

## 6. Kiểm chứng

| Bước | Kết quả |
|---|---|
| a. DB sạch, chỉ bộ test | Đủ số dòng dự kiến. Cả 5 tài khoản đăng nhập được (HTTP 200), sai mật khẩu ra 401. Các API đọc (đơn, CV, giấy mời, tải CV gốc PDF/DOCX, lượt chấm) trả 200; HR gọi API ứng viên ra 403 |
| d. Backend chạy 2 phút | Đúng 3 lượt OpenAI (2 `job_embeddings` + 1 embedding hồ sơ); 0 lượt thất bại, 0 Anthropic, 0 SMTP. Mọi dòng đóng băng giữ nguyên |
| c. Nạp lần hai | 15 câu `INSERT 0 0`, không lỗi, số dòng không đổi |
| b. Demo + test, không bật backend | Số dòng và md5 nội dung của 21 bảng demo giống hệt trước khi nạp bộ test |
| reset-test | Hai chốt chặn đều chặn đúng. Có dòng UI giả lập cắt chéo (đơn demo vào tin test, đơn test vào tin demo, thông báo chéo, hồ sơ tự tạo): dòng test bị xoá hết, md5 demo không đổi, nạp lại bộ test thành công |
| e. `mvnw test` | 770 test, 0 lỗi, 0 bỏ qua |

**Vì sao 770 mà walkthrough `refactor-ui-md3-legacy` ghi 774, dù backend hai nhánh giống hệt?**
- 4 test chênh lệch là của `JobRecommendationCacheServiceTest`. Lớp này đã bị xoá ở commit
  `78924c2` (FR-U15 đợt 4, 03/10/2026).
- File báo cáo cũ của nó (`TEST-…JobRecommendationCacheServiceTest.xml`, `tests="4"`, ghi ngày
  01/10) vẫn nằm trong `backend/target/surefire-reports/`, vì thư mục này không bị xoá giữa các
  lần chạy nếu không `mvn clean`.
- Cộng các file XML trong thư mục ra 770 + 4 = 774. Console Maven của lần chạy này báo đúng 770
  cho 80 lớp.
- Đã đối chiếu: cả 80 lớp có `@Test` trong mã đều có báo cáo mới, không lớp nào thiếu. Mã không có
  `@Disabled`, điều kiện bật/tắt hay test động nào.
- Kết luận: số 774 đã tính cả báo cáo cũ; không có test nào bị bỏ qua hay không được phát hiện.

Trạng thái DB khi bàn giao: demo (tầng 1 + tầng 2 + file PDF) cộng bộ test (kèm file CV test),
backend đã tắt. Lần đầu bật backend trên trạng thái này sẽ phát sinh **7 lượt OpenAI một
lần**: 3 của bộ test và 4 cho hồ sơ demo chưa có embedding.

## 7. Ràng buộc SRS đã giữ

- Không có điểm AI, giải thích, evidence hay embedding nào được viết tay hoặc làm giả.
- Không có cột hay nhãn đạt/không đạt.
- Mọi đơn có `ai_consent = TRUE`.
- Đơn WITHDRAWN là đổi trạng thái kèm lịch sử, không xoá.
- Vòng đời đơn đúng `ALLOWED_TRANSITIONS` của FR-H07: HR chỉ chuyển PENDING → INTERVIEW_INVITED / REJECTED và INTERVIEW_INVITED → HIRED / REJECTED; ứng viên tự rút đơn.

## 8. Nợ / lưu ý

- `backend/.env` đang chứa khoá Anthropic cũ và khoá OpenAI giữ chỗ. Chạy backend theo lệnh
  thường dùng sẽ gặp `LLM_ERROR` (401) cho mọi lượt gọi AI. Người dùng tự cập nhật file
  này; nhánh này không sửa nó.
- `install-demo-files.ps1` xoá file `.pdf` trước khi đối chiếu, và không chịu được DB có CV
  ngoài bộ demo. Đây là hành vi của script demo, chưa sửa.
