# AI Recruitment Agent

> File này là "bộ nhớ" mà Claude Code đọc tự động mỗi phiên. Giữ nó NGẮN và ĐÚNG.
> Khi có quyết định kiến trúc mới, cập nhật file này ngay (hoặc dùng `#` trong Claude Code).

## 1. Bối cảnh

Nền tảng tuyển dụng có AI hỗ trợ, 2 loại tài khoản: **HR** và **Ứng viên (Candidate)**.
Đặc tả gốc: `docs/SRS.md` — mọi thay đổi hành vi phải bám theo mã yêu cầu (FR-C / FR-H / FR-U).

## 2. Nguyên tắc bất di bất dịch (KHÔNG được vi phạm)

- AI **chỉ** chấm điểm từng tiêu chí riêng lẻ + giải thích, kèm evidence trích từ CV (FR-H04, FR-H06).
- AI **không** tính tổng điểm, **không** xếp hạng. Backend làm việc đó bằng công thức trọng số tường minh (FR-H05).
- AI **không** gán nhãn Đạt / Không đạt / Cần xem xét. HR tự quyết định (FR-H07).
- Mọi giải thích phải kiểm chứng được từ nội dung CV — không bịa.
- **AI không được viết lại, tóm tắt, hay dịch nội dung CV.** Trích xuất phải giữ nguyên văn và
  nguyên ngôn ngữ gốc — nếu D1 diễn giải lại thì evidence ở D2 là lời của LLM, không phải lời
  trong CV, và cả nguyên tắc evidence sụp từ gốc.
- Ứng viên phải tick consent trước khi CV được AI phân tích (FR-U02).
- Rút đơn = đổi trạng thái (soft state), KHÔNG hard delete (FR-U06).
- Vòng đời đơn ứng tuyển đúng 5 trạng thái: `PENDING` → `INTERVIEW_INVITED` → `HIRED` | `REJECTED`;
  và `WITHDRAWN` có thể xảy ra bất kỳ lúc nào trước kết quả cuối.

## 2b. Ngữ nghĩa `scoring_runs.finished_at` (D3 cần biết)

Hiện chỉ nằm trong comment code (`ScoringRun.java`/`ScoringRunStateService`) — D3 (FR-H05, tổng
hợp điểm) phải đọc đúng vòng đời này:

| status | finished_at | Ý nghĩa |
|---|---|---|
| `PENDING` | `NULL` | Chưa claim |
| `RUNNING` | `NULL` | Đang chấm (đã claim, chưa xong tiêu chí cuối) |
| `RUNNING` | khác `NULL` | D2 đã chấm xong toàn bộ tiêu chí, chờ D3 tổng hợp |
| `FAILED` | khác `NULL` | Dừng hẳn, không xử lý tiếp |

D3 nhặt lượt cần tổng hợp bằng: `status = 'RUNNING' AND finished_at IS NOT NULL AND total_score
IS NULL`.

## 3. Stack

Monorepo: `backend/` (Spring Boot) + `frontend/` (React) + 1 PostgreSQL. Không microservice.

- Backend: Java 25, Spring Boot 4.1.x, Spring Security 7 + JWT, Spring Data JPA
- Migration: Flyway (`backend/src/main/resources/db/migration`) — Flyway là nguồn sự thật của schema,
  `ddl-auto: validate`
- AI: Spring AI 2.0.0 — `ChatClient` (Anthropic), `EmbeddingModel` (OpenAI), `PgVectorStore`
- Đọc CV: Apache PDFBox 3.0.3 (PDF) + Apache POI 5.4.0 (DOCX)
- Database: PostgreSQL 17 + pgvector, index HNSW cosine, `vector(1536)`
- Frontend: Vite + React 19 + TypeScript, TanStack Query, React Hook Form + Zod, Tailwind + shadcn/ui
- Job nền: bảng trạng thái + `@Scheduled` poller. Không Redis, không MQ.
- Test: JUnit 5 + Testcontainers (Postgres thật, không H2)

Chia package theo TÍNH NĂNG, không theo tầng: `auth/`, `user/`, `company/`, `job/`,
`interviewtemplate/`, `rubric/`, `resume/`, `jobapplication/`, `scoring/`, `storage/`, `common/`,
`ai/client/`, `ai/criterion/`. Mỗi mã FR nằm gọn trong một package (`ai/explanation/` v.v. sẽ thêm
khi tới D4/F1/F2 — chưa tồn tại, đừng tạo trước).

## 3b. Bẫy đã trả giá — đọc trước khi động vào Spring AI

Tutorial Spring AI viết trước 06/2026 là API 1.x, KHÔNG dùng được trên Boot 4. Đừng copy.
Khi cần signature chính xác, `javap` trên jar thật trong `~/.m2` — đừng viết theo trí nhớ.

**Jackson 3 — annotation vẫn ở package cũ.** `core`/`databind` đã chuyển sang `tools.jackson.*`,
nhưng annotation module **cố ý giữ nguyên** `com.fasterxml.jackson.annotation.*` —
`@JsonIgnoreProperties` import từ `com.fasterxml` là ĐÚNG, không phải lỗi. Đầy đủ:
`stack-conventions/SKILL.md` mục "Jackson 3 — nhưng annotation module vẫn ở package cũ".

**Hai auto-config bị exclude trong `application.yml`, đừng gỡ:** `AnthropicChatAutoConfiguration`
bind lỗi vào `ThinkingConfigParam` khi khởi động; `OpenAiChatAutoConfiguration` khiến starter OpenAI
tự khai thêm một bean `ChatModel` thứ hai, gây `NoUniqueBeanDefinitionException` khi có nơi tiêu thụ
`ChatClient.Builder`. Đầy đủ + khối YAML: `stack-conventions/SKILL.md` mục "Hai auto-config bị
exclude trong `application.yml` — đừng gỡ".

**API đã đổi ở 2.0:**
- `toolCallingManager` bị gỡ; tool execution chuyển sang `ToolCallingAdvisor` ở tầng `ChatClient`.
- Dùng `.call().responseEntity(converter)` chứ không phải `.entity(converter)` — `entity()` vứt mất
  `ChatResponse`, mà `resume_parsed_data.model` là NOT NULL và cần cả `token_usage`.
- `BeanOutputConverter` dùng constructor 1 tham số `BeanOutputConverter(Class<T>)` — overload 2
  tham số đòi `tools.jackson.databind.json.JsonMapper`, không phải `ObjectMapper` cũ.

## 3c. Bẫy đã trả giá — transaction và job nền

**Self-invocation phá `@Transactional`.** Spring dùng proxy: method `@Transactional` bị gọi bằng
`this.method()` từ trong cùng bean sẽ không mở transaction nào — method ghi phải nằm ở bean riêng,
được inject vào (`ResumeParsingStateService`/`ScoringRunStateService`).

**Không giữ transaction quanh lời gọi LLM** — giữ connection suốt lời gọi LLM sẽ cạn pool khi nhiều
bản ghi chờ xử lý cùng lúc. Mẫu đúng: transaction ngắn để claim → gọi LLM ngoài transaction →
transaction ngắn để ghi kết quả.

**Claim bản ghi bằng `UPDATE` có điều kiện**, không dùng `SELECT FOR UPDATE SKIP LOCKED` (giữ
transaction mở trong lúc chờ LLM). `@Modifying(clearAutomatically = true)` là bắt buộc.

Đầy đủ (mẫu code, câu SQL claim thật): `fr-implement/SKILL.md` mục "Job nền và transaction"; comment
trong `ResumeParsingStateService.java`/`ScoringRunStateService.java`.

## 4. Quy ước code

- **Comment trong code: tiếng Việt không dấu.** Chuỗi hiển thị cho người dùng (thông báo lỗi,
  nhãn UI): **tiếng Việt có dấu**. Chat với tôi: tiếng Việt có dấu.
- Tên class, method, biến, tên test: tiếng Anh.
- Mọi endpoint phải kiểm tra role (RBAC) **và quyền sở hữu bản ghi** — `@PreAuthorize("hasRole")`
  là chưa đủ. Không tin vào việc UI đã ẩn nút.
- Prompt gửi LLM đặt trong `backend/src/main/resources/ai/prompt/`, đánh version trong tên file
  (`resume-parse-v1.st`). Không hardcode giữa business logic. Hằng số `PROMPT_VERSION` khai một
  chỗ duy nhất, gắn liền tên file.
- Output của LLM luôn parse qua `BeanOutputConverter` theo schema record cố định, retry 1 lần khi
  JSON hỏng, rồi mới `FAILED`.
- **Cột lỗi (`parse_error`, `error_message`...) chỉ lưu mã lỗi đã chuẩn hoá** (`EXTRACT_EMPTY`,
  `LLM_INVALID_JSON`...) kèm câu mô tả tiếng Việt cố định. Stack trace và output thô của LLM chỉ
  ghi ở `log.debug` — nội dung CV là dữ liệu cá nhân, không được vào DB hay log mức thường.
- **Enum mã lỗi ghi xuống cột lỗi phải implement `common/FormattedErrorCode`** (`String
  formatted()`); method ghi (`markFailed`...) nhận kiểu đó, KHÔNG nhận `String` tự do — String thì
  trình biên dịch không chặn được việc lỡ truyền `e.getMessage()`/output thô LLM vào cột đi thẳng
  ra API.
- **Ràng buộc "chỉ một X đang hoạt động" phải chốt ở DB, không chỉ SELECT-trước-INSERT.**
  `existsBy...`/đếm ở tầng service chỉ để trả lỗi sớm, thân thiện (409) — chốt chặn thật sự là
  partial unique index/constraint ở DB, bắt được race mà Java bỏ lọt. Tiền lệ:
  `uq_application_per_cycle` (C2), `uq_scoring_run_in_progress` (V4, D2). Dùng `saveAndFlush` để
  buộc INSERT chạy ngay trong request, cho `DataIntegrityViolationException` nổi lên và
  `GlobalExceptionHandler` dịch sang 409.
- Record dùng làm schema JSON: field số dùng wrapper (`Integer`/`Double`), không dùng primitive —
  primitive nhận `0` khi field vắng mặt, xoá mất khác biệt giữa "không tìm thấy" và "bằng 0".
  Field `List` chuẩn hoá `null` → `List.of()` trong compact constructor.
- Soft delete bằng `deleted_at` xuyên suốt, không `DELETE FROM`.
- File nhị phân dùng làm fixture test phải khai `binary -text` trong `.gitattributes`
  (quy tắc `* text=auto eol=lf` sẽ làm hỏng chúng khi commit từ Windows).
- Không commit `.env`, không commit `uploads/`, không log nội dung CV ra console.

## 5. Lệnh hay dùng

```bash
docker compose up -d                 # postgres + mailhog + minio
cd backend && ./mvnw spring-boot:run # API tại http://localhost:8080, Swagger /swagger-ui.html
cd backend && ./mvnw test            # LUÔN chạy đầy đủ, không chỉ class mới
cd frontend && npm run dev           # http://localhost:5173
cd frontend && npm run build         # tsc -b + vite build — bắt buộc trước khi báo xong đợt
cd frontend && npm run lint          # eslint — bắt buộc trước khi báo xong đợt
docker compose down -v               # reset sạch DB khi migration hỏng
```

Validate backend bằng `mvn test`, **không phải** `mvn compile`. Chạy full suite chứ không chỉ class
vừa sửa — xung đột bean và side-effect chéo giữa các `@SpringBootTest` chỉ lộ ra khi chạy đầy đủ.
Validate frontend bằng `npm run build` + `npm run lint` — cặp lệnh này với frontend tương đương vai
trò của `mvn test` với backend, chạy sau mỗi lần sửa code frontend, không chỉ khi CI nhắc.

## 6. Cách tôi muốn làm việc với Claude

- Mỗi lần chỉ làm **một mã FR**, chia thành **5-7 đợt nhỏ**. Đợt đầu luôn là **Plan Mode** (duyệt
  kế hoạch trước khi code); đợt cuối luôn chạy skill `srs-guard` + `walkthrough` + cập nhật
  `docs/ROADMAP.md`. Mỗi đợt kết thúc bằng: dừng → báo cáo diff → chờ tôi duyệt → mới commit.
  Không tự commit.
- **Khi tôi yêu cầu xem nội dung file: dán thẳng vào chat trong code block.** Không gửi link Read
  hay đường dẫn webview — tôi không mở được chúng.
- Trước khi sửa file có sẵn, đọc file đó trước — không đoán.
- Khi cần signature của thư viện, đọc jar/source thật (`javap`, file `.pom`) và **trích dẫn bằng
  chứng** trong báo cáo. Không viết theo trí nhớ.
- Nếu yêu cầu của tôi mâu thuẫn với `docs/SRS.md`, hoặc tôi nói sai về code hiện có, hãy nói ra
  thay vì im lặng làm theo.
- Sau khi code xong, tự chạy lint/test và tự sửa lỗi trước khi báo hoàn thành.
- Test phải có **cả case dương và case âm**, và test biên khi có ngưỡng số
  (ngưỡng−1, đúng ngưỡng, ngưỡng+1).

## 7. Ranh giới không được vượt

- Package `ai/` KHÔNG được import `scoring/ScoreAggregator`. AI không tính tổng điểm.
- Không tạo cột/field tên `verdict`, `label`, `isQualified`, `passed`, `recommendation` ở bất kỳ
  entity nào.
- Không đổi `ddl-auto` sang `update`. Mọi thay đổi schema đi qua một file Flyway mới.
- Không xoá cột `weight_snapshot` / `rubric_snapshot` dù trông có vẻ trùng dữ liệu — chúng giữ lịch
  sử audit.
- Không gọi LLM đồng bộ trong request của người dùng — luôn qua job nền.
- Test KHÔNG được gọi API LLM thật. Mock ở tầng `ChatModel` với default-answer throw để test nào
  quên stub thì đỏ ngay, thay vì âm thầm gọi mạng.
- Không commit `.env`, không commit thư mục `uploads/`.

## 8. Giao diện

Tham chiếu phong cách: `docs/UI_GUIDE.md`. Phong cách chung là job board Việt Nam
(kiểu VietnamWorks / CareerViet / TopCV): sáng, nhiều thẻ (card), thông tin dày, xanh dương làm màu
chủ đạo.

**Chỉ mô phỏng quy ước bố cục và hệ màu — KHÔNG sao chép logo, tên thương hiệu, hay CSS của bất kỳ
trang nào.** Dự án có tên và nhận diện riêng.

Token bắt buộc dùng (khai trong khối `@theme` của `frontend/src/index.css`, KHÔNG hardcode mã màu
trong component):

| Token (biến CSS) | Class Tailwind | Giá trị | Dùng cho |
|---|---|---|---|
| `--color-brand` | `bg-brand`, `text-brand` | `#0078C9` | Màu chính: header, link, nút chính |
| `--color-brand-dark` | `hover:bg-brand-dark` | `#1E5C8B` | Hover, trạng thái active |
| `--color-accent` | `bg-accent` | `#1AC639` | Nút "Ứng tuyển", badge "Mới" |
| `--color-warning` | `text-warning`, `bg-warning` | `#FF5B00` | Hạn nộp gần, tin gấp |
| `--color-danger` | `text-danger`, `bg-danger` | `#E11B3E` | Từ chối, lỗi |
| `--color-ink` | `text-ink` | `#1F2937` | Chữ chính |
| `--color-ink-muted` | `text-ink-muted` | `#6B7280` | Chữ phụ, meta |
| `--color-line` | `border-line` | `#E7E7E9` | Viền thẻ, đường phân cách |
| `--color-surface` | `bg-surface` | `#FFFFFF` | Nền thẻ |
| `--color-canvas` | `bg-canvas` | `#F0F0F0` | Nền trang |

`index.css` còn một lớp alias riêng cho component shadcn (`--border`, `--foreground`,
`--background`...) ánh xạ sang các token trên — chỉ dùng nội bộ trong `components/ui/`, không phải
token để gõ tay ở tầng tính năng.

- Container tối đa 1200px, lưới 12 cột, bo góc thẻ 8px, badge 4px.
- Icon dùng `lucide-react`. Không dùng emoji trong UI.
- Card việc làm: logo vuông 80px bên trái; tiêu đề tối đa 2 dòng (`line-clamp-2`); dưới là tên công
  ty, mức lương (icon tiền), địa điểm (icon ghim), danh sách tag; góc phải là hạn nộp.
- Trang ứng viên = layout công khai (header + hero tìm kiếm + các section thẻ + footer nhiều cột).
- Trang HR = layout quản trị: sidebar trái cố định, bảng dữ liệu dày, không dùng hero.

Ràng buộc riêng của dự án này:
- Màn hình chấm điểm KHÔNG được hiển thị nhãn Đạt/Không đạt hay màu đỏ-vàng-xanh gợi ý phán quyết.
  Chỉ hiện điểm số, thứ hạng — **thứ hạng do Backend tính bằng công thức trọng số tường minh
  (FR-H05), KHÔNG phải do AI sinh ra** (xem mục 2) — và evidence. Quyết định là của HR (FR-H07).
- Badge trạng thái dùng bảng màu **trung tính**, không dùng đỏ/xanh gợi ý tốt–xấu.
- Mọi điểm số hiển thị phải kèm được evidence khi người dùng mở rộng — không có điểm "trần trụi".
