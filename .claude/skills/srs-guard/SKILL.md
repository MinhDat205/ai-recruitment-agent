---
name: srs-guard
description: Soát codebase tìm vi phạm các nguyên tắc thiết kế bắt buộc trong SRS của dự án AI Recruitment Agent (AI không được gán nhãn đậu/rớt, backend tính tổng điểm, không hard delete, consent bắt buộc, và các nguyên tắc bổ sung cho 21 FR mới). Dùng khi người dùng yêu cầu "kiểm tra vi phạm SRS", "soát ranh giới AI", "review trước khi merge", hoặc trước khi merge BẤT KỲ nhánh FR nào (cũ hay bổ sung).
---

# Soát vi phạm nguyên tắc SRS

15 nguyên tắc dưới đây là ràng buộc cứng của dự án. Vi phạm là lỗi nghiêm trọng, không phải góp ý
phong cách. Nguyên tắc 1-9 LUÔN soát, bất kể nhánh nào. Nguyên tắc 10-15 chỉ soát khi nhánh đang
review chạm FR liên quan; nếu không chạm, đánh dấu **"chưa có gì để soát"** (khác "đã soát, sạch").

Với mỗi mục: chạy lệnh tìm kiếm, đọc kết quả, đánh giá **có vi phạm thật không** (có thể là dương
tính giả), rồi báo cáo theo mẫu ở cuối.

## 1. AI không được gán nhãn đậu/rớt (FR-H05, FR-H07)

```bash
rg -i "verdict|isQualified|is_qualified|passed|rejected_by_ai|recommendation_label|suitability" --type java --type ts --type sql
```

Vi phạm nếu: tồn tại trường lưu kết luận đạt/không đạt do hệ thống sinh ra.
Không vi phạm nếu: đó là `job_applications.status` do HR đổi tay.

## 2. AI không tính tổng điểm (FR-H04 vs FR-H05)

```bash
rg "total_score|totalScore" backend/src/main/java/com/recruitment/ai/
rg "ScoreAggregator" backend/src/main/java/com/recruitment/ai/
```

Vi phạm nếu: package `ai/` ghi vào `total_score`, hoặc import `ScoreAggregator`.
Tổng điểm chỉ được tính trong `scoring/ScoreAggregator.java` bằng Java thuần.

## 3. Chấm từng tiêu chí riêng lẻ (FR-H04)

Đọc `backend/src/main/java/com/recruitment/ai/criterion/`.

Vi phạm nếu: một lần gọi LLM trả về điểm của nhiều tiêu chí cùng lúc.
Đúng: mỗi tiêu chí một lần gọi (`CriterionScoringService.score(criterion, rawText)` nhận đúng MỘT
`RubricSnapshot.CriterionSnapshot`), input là `raw_text` của CV + đúng một tiêu chí — **không phải
CV JSON** (`resume_parsed_data.data`, vì nếu D1 đã diễn giải lại nội dung khi trích xuất, evidence
trích từ JSON sẽ là lời của LLM chứ không phải lời trong CV gốc, làm sụp nguyên tắc evidence kiểm
chứng được — CLAUDE.md mục 2). Kiểm chứng evidence cũng luôn dùng `raw_text` đầy đủ, không phải
bản đã cắt hay CV JSON.

## 4. Không có ngưỡng phân loại ỨNG VIÊN (FR-H05)

```bash
rg -i "if.*score.*>=|threshold|nguong|phan loai" --type java --type ts
```

Vi phạm nếu: có ngưỡng chia ỨNG VIÊN thành nhóm (đạt/cần xem xét/không đạt), hoặc frontend đổi màu
điểm theo ngưỡng (ví dụ điểm cao màu xanh, điểm thấp màu đỏ).
Đúng: thanh tiến trình đơn sắc, số điểm không đổi màu.

**Không vi phạm — ngưỡng lọc VIỆC LÀM khác ngưỡng phân loại ỨNG VIÊN**: hằng số
`JobRecommendationCacheService.MIN_SIMILARITY_SCORE` lọc **việc làm** cho FR-U04, không phải điểm
một ứng viên, nên không vi phạm. Nhánh CV của FR-U15 giữ đúng ngưỡng này — không vi phạm. Nhánh
**hồ sơ** của FR-U15 (ứng viên CHƯA có CV) KHÔNG áp ngưỡng tương đồng theo đặc tả — code nhánh này
có ngưỡng tương đồng là **lệch đặc tả**, đưa vào "Cần xem lại", không tự kết luận vi phạm.

**Soát frontend cụ thể hơn** (chỗ vi phạm khó thấy nhất, vì trông như thiết kế đẹp): tìm mọi
component badge/màu liên quan tới điểm hoặc tiến độ chấm, đọc trực tiếp biểu thức **chọn màu**
(class/token màu, không phải toàn bộ component) — biểu thức đó có nhận `score` hay giá trị số liên
quan tới điểm làm tham số quyết định màu không. Chọn màu chỉ phụ thuộc **trạng thái xử lý**
(PENDING/RUNNING/FAILED...) thì không vi phạm, kể cả khi cùng chỗ có hiển thị con số (dương tính
giả hay gặp: label "Đã chấm 3/5 tiêu chí" — số `3/5` nằm trong text hiển thị, không phải biểu thức
chọn màu). Với FR-U13/U15/H14: "lý do khớp" hiển thị dạng chip trung tính liệt kê điều kiện, không
%, không đổi màu theo giá trị (UI_GUIDE mục 4) — vi phạm nếu tô màu hoặc gắn nhãn "phù hợp".

## 5. Mọi điểm/trích dẫn phải có evidence kiểm chứng được (FR-H06, FR-C08, FR-H13)

```bash
rg "evidence" backend/src/main/java/com/recruitment/
```

Vi phạm nếu: `criterion_scores.evidence` được ghi mảng rỗng **khi `score` khác 0**, hoặc UI hiển
thị điểm mà không mở ra được đoạn trích từ CV.

**Không vi phạm:** evidence rỗng khi `score = 0` — đây là trường hợp hợp lệ đã chốt ở D2 (CV thực
sự không có thông tin liên quan tới tiêu chí thì phải chấm đúng 0 điểm và để evidence rỗng, không
được bịa). Quy tắc chính xác là **evidence rỗng CHỈ hợp lệ khi và chỉ khi `score = 0`** — rỗng mà
`score` khác 0 mới là vi phạm.

Quy tắc này được thực thi ở **tầng Java**, không chỉ nằm trong prompt — kiểm ở
`CriterionScoringService.validate()` (`if (evidence.isEmpty() && score != 0) throw
CriterionScoringErrorCode.EVIDENCE_MISSING_WITH_NONZERO_SCORE`). Đường ghi DB duy nhất
(`ScoringRunStateService.recordCriterionScore`) chỉ nhận kết quả đã qua `validate()` — soát ở đây
là soát code Java thật, không phải suy luận qua nội dung prompt.

**Mở rộng cho FR-C08/FR-H13**: mọi trích dẫn CV hiển thị ở hai chức năng này phải qua cùng bộ kiểm
dùng chung K2 (CLAUDE.md §3d, tách từ `validate()` trên) — khi K2 tồn tại, vi phạm nếu C08/H13 tự
viết lại phép so khớp riêng thay vì gọi K2.

## 6. Không hard delete, trừ ngoại lệ quyền riêng tư đã chốt (FR-U06, FR-H15)

```bash
rg "\.delete\(|deleteById|DELETE FROM" --type java --type sql
```

Vi phạm nếu: xoá bản ghi `job_applications`, `resumes`, `scoring_runs`, `criterion_scores`.
Đúng: đổi `status` sang `WITHDRAWN`, hoặc set `deleted_at` với `jobs`.
Dương tính giả thường gặp: xoá `rubric_criteria` khi HR sửa rubric — cái này được phép.

**Ngoại lệ hợp lệ (FR-H15/FR-U08)**: khi ứng viên rút đồng ý lưu hồ sơ, mục tương ứng trong kho
ứng viên (kèm ghi chú/tóm tắt AI) phải **xoá hẳn** — yêu cầu quyền riêng tư, không phải vi phạm
soft-delete. `job_applications`/`resumes`/`scoring_runs` gốc KHÔNG bị xoá theo hành động này.

## 7. Consent bắt buộc, không tick sẵn (FR-U02)

```bash
rg -i "ai_consent|aiConsent|consent" --type java --type ts
```

(`--type ts` của ripgrep đã gồm cả `.tsx` — xác nhận bằng `rg --type-list | grep '^ts:'`. Không có
type `tsx` riêng; thêm `--type tsx` sẽ làm lệnh báo lỗi "unrecognized file type".)

Vi phạm nếu: frontend đặt `defaultChecked` / `checked={true}` cho checkbox consent,
hoặc backend cho tạo đơn với `ai_consent = false`.

## 8. Snapshot rubric không bị bỏ (FR-H08)

```bash
rg "weight_snapshot|weightSnapshot|rubric_snapshot|rubricSnapshot" --type java
```

Vi phạm nếu: code đọc trọng số trực tiếp từ `rubric_criteria` khi hiển thị lịch sử đánh giá,
thay vì đọc `weight_snapshot` đã lưu. Sửa trọng số sau này sẽ làm hỏng toàn bộ lịch sử.

## 9. Ràng buộc duy nhất phải có chốt chặn ở DB, không chỉ SELECT-trước-INSERT

```bash
rg -i "existsBy|findBy.*IsNull|SELECT.*COUNT" --type java -g "*Service.java" -g "*StateService.java"
```

Vi phạm nếu: một ràng buộc "chỉ được có một X đang hoạt động" (một đơn ứng tuyển đang PENDING cho
mỗi chu kỳ, một lượt chấm đang chạy cho mỗi đơn...) chỉ được kiểm bằng cách SELECT/`existsBy...`
rồi mới INSERT, **không có** unique index/constraint nào ở DB đứng sau làm chốt chặn cuối cùng.
SELECT-trước-INSERT ở tầng service luôn có khe hở race condition (hai request đến gần như đồng
thời đều thấy "chưa tồn tại" trước khi cả hai cùng INSERT).

Đúng: SELECT-trước-INSERT chỉ đóng vai trò trả lỗi sớm, thân thiện (409) — chốt chặn THẬT SỰ phải
là một partial unique index/constraint ở DB. Hai tiền lệ: `uq_application_per_cycle`
(`ApplicationService.apply`), `uq_scoring_run_in_progress` (`ScoringRunService.requireNoRunInProgress`
+ `ScoringRunStateService.create`) — cả hai dùng `saveAndFlush` để buộc INSERT chạy ngay trong
request, cho `DataIntegrityViolationException` nổi lên và `GlobalExceptionHandler` dịch sang 409.

---

## Nguyên tắc bổ sung cho 21 FR mới (10-15)

## 10. Phía ứng viên không nhận dữ liệu nội bộ

```bash
rg -n '"/api/candidates' backend/src/main/java --type java -g "*Controller.java"
```

rồi đọc mọi DTO response được các controller đó trả về, và (khi K1 đã xây — CLAUDE.md §3d) đọc
K1 để xác nhận bộ gom ngữ cảnh AI cho vai trò ứng viên có lọc đúng.

Vi phạm nếu: DTO/ngữ cảnh K1 chứa điểm số, rubric, giải thích AI, ghi chú nội bộ HR, câu hỏi phỏng
vấn, hay dữ liệu kho ứng viên. Không vi phạm nếu là dữ liệu ứng viên tự nhập (hồ sơ, CV, đơn).

## 11. AI không tự gửi/đăng/quyết định

Tìm mọi nơi gọi `ChatClient`/`ChatModel` cho các FR có nội dung AI hướng ra ngoài (C07 soạn nháp
tin nhắn, H11 tạo tin tuyển dụng), đọc xem kết quả có ghi thẳng thành tin nhắn đã gửi/tin tuyển
dụng `OPEN`/đổi trạng thái đơn hay không, mà không qua hành động xác nhận của người dùng.

Vi phạm nếu: có đường code nào tự động chuyển kết quả AI thành trạng thái cuối (gửi/đăng/đổi trạng
thái) mà không có request riêng do người dùng khởi tạo sau khi xem bản nháp. Tin tạo bằng H11
LUÔN phải ở trạng thái `DRAFT` sau khi AI sinh xong.

## 12. Gọi LLM/embedding đồng bộ chỉ trong danh sách ngoại lệ K3

```bash
rg -n "ChatClient|ChatModel|EmbeddingModel" backend/src/main/java/com/recruitment --type java -g "*Controller.java" -g "*Service.java"
```

Với mỗi kết quả nằm trên đường request (gọi được từ một `Controller` không qua job nền/scheduler):
xác nhận FR chứa nó có trong danh sách ngoại lệ K3 ở CLAUDE.md §7 (FR-C07, FR-C08, FR-H11, FR-H13,
FR-H15 tóm tắt, FR-U12, FR-U13), và có đủ: timeout, thử lại tối đa 1 lần, rate limit theo `userId`,
không `@Transactional` bao quanh lời gọi.

Vi phạm nếu: một service/controller gọi `ChatClient`/`ChatModel`/`EmbeddingModel` trực tiếp trong
luồng request mà FR đó KHÔNG thuộc danh sách K3, hoặc thuộc danh sách nhưng thiếu một trong 4 điều
kiện trên.

## 13. Không lọc/hỏi/suy đoán thông tin nhân thân

```bash
rg -i "gender|dateOfBirth|date_of_birth|marital|religion|tuoi|gioi_tinh" backend/src/main/java frontend/src --type java --type ts
rg -i "gender|dateOfBirth|date_of_birth|ngày sinh|giới tính|tuổi|hôn nhân|tôn giáo" backend/src/main/resources/ai/prompt
```

Lệnh 1 tìm field nhân thân trong code (kỳ vọng ra `CandidateProfile.dateOfBirth` và DTO liên quan
— dữ liệu candidate tự quản lý, FR-U01, KHÔNG vi phạm miễn KHÔNG bị đọc ở `ai/`,
`jobrecommendation/`, hay logic lọc/tìm kiếm). Lệnh 2 soát riêng prompt gửi LLM — **kỳ vọng 0 kết
quả tuyệt đối**, không có "trường hợp cho phép" nào ở đây.

Vi phạm nếu: lệnh 2 ra kết quả, hoặc field từ lệnh 1 bị dùng làm điều kiện lọc/bị hỏi ở form sàng
lọc (H10). Dương tính giả đã gặp: `tuoi` khớp cả comment không dấu viết "TƯƠI" (mới, không phải
"tuổi") — đọc ngữ cảnh dòng khớp trước khi kết luận.

## 14. Đồng ý lưu hồ sơ (FR-U09, FR-H15)

Khi FR-U09/FR-H15 đã có code: soát bằng cùng khuôn nguyên tắc 7 — tìm field đồng ý cho công ty lưu
hồ sơ (tên cụ thể tuỳ lúc code, tra theo REQUIREMENT.md của FR-U09).

Vi phạm nếu: giá trị mặc định là `true`/tick sẵn ở frontend, hoặc backend cho thêm đơn vào kho
ứng viên (FR-H15) khi đơn đó chưa có đồng ý. Rút đồng ý sau đó phải xoá khỏi MỌI kho đã lưu (xem
nguyên tắc 6, ngoại lệ hard-delete hợp lệ).
Chưa có code cho hai FR này → đánh dấu "chưa có gì để soát".

## 15. Giao diện không phán quyết cho chức năng mới

Đọc component hiển thị gợi ý/tìm kiếm theo ngôn ngữ tự nhiên (U13), gợi ý theo hồ sơ (U15), và so
sánh ứng viên (H14) khi đã có code frontend.

Vi phạm nếu: hiển thị % phù hợp, nhãn "phù hợp"/"không phù hợp", tô màu theo ngưỡng ở kết quả gợi
ý/tìm kiếm, hoặc đánh dấu người thắng trong màn hình so sánh ứng viên. Nội dung do AI tạo (bản
nháp, tóm tắt, câu hỏi...) phải có nhãn "Do AI tạo" kèm icon — thiếu nhãn này cũng là vi phạm
(UI_GUIDE mục 4).
Chưa có code frontend cho ba FR này → đánh dấu "chưa có gì để soát".

---

## Mẫu báo cáo

```
## Kết quả soát SRS — nhánh <tên nhánh>

### Vi phạm (phải sửa trước khi merge)
- [Nguyên tắc N] <mô tả> — `<file>:<dòng>`
  Cách sửa: <đề xuất cụ thể>

### Cần xem lại (nghi ngờ, cần người quyết định)
- ...

### Đã kiểm tra, không vi phạm
- Nguyên tắc 1, 2, 4, 6 ✓

### Chưa có gì để soát (nhánh này không chạm FR liên quan)
- Nguyên tắc 14, 15 (FR-U09/H15/U13/U15/H14 chưa có code)
```

Nếu không tìm thấy vi phạm nào, nói rõ đã kiểm tra hết **15 mục** và liệt kê từng mục — đừng chỉ
nói "không có vấn đề gì". Với báo cáo gửi người dùng (khác với nội dung skill này), trích bằng
chứng cụ thể theo `file:dòng` khi tìm thấy vi phạm hoặc cần xem lại.
