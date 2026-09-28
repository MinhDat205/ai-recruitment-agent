---
name: stack-conventions
description: Quy ước và cạm bẫy khi viết code cho stack Spring Boot 4.1 + Spring AI 2.0 + Java 25 + React/Vite của dự án này. Dùng khi viết hoặc sửa code backend Java, cấu hình Maven, tích hợp LLM/embedding, hoặc khi gặp lỗi liên quan tên starter, Jackson, hay API Spring AI.
---

# Quy ước stack — Spring Boot 4.1 + Spring AI 2.0

**Cảnh báo chung:** phần lớn ví dụ Spring Boot và Spring AI trên internet viết trước tháng 06/2026,
tức là Boot 3.x và Spring AI 1.x. Chúng **không tương thích** với dự án này. Khi tham khảo ví dụ,
kiểm tra version trước.

## Spring Boot 4 đã đổi tên starter

| Boot 3 (SAI) | Boot 4 (ĐÚNG) |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | starter `-test` riêng cho từng module: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`... |
| (Flyway qua auto-config) | `spring-boot-starter-flyway` |

Xem `backend/pom.xml` để biết danh sách thật đang dùng. Không thêm starter mới nếu chưa kiểm tra tên.

## Jackson 3 — nhưng annotation module vẫn ở package cũ

Spring AI 2.0 và Boot 4 dùng Jackson 3. `core` và `databind` đã chuyển sang `tools.jackson.*`,
nhưng annotation module **cố ý giữ nguyên** `com.fasterxml.jackson.annotation.*` (xác minh trong
`jackson-databind-3.x.pom` — không phải suy đoán).

**`@JsonIgnoreProperties` import từ `com.fasterxml.jackson.annotation.*` là ĐÚNG, không phải lỗi
cần sửa.** Đối chiếu import thật trong `ResumeParsedPayload`, `RubricSnapshot`,
`CriterionScorePayload` nếu còn nghi ngờ. Chỉ import `com.fasterxml.jackson.*` cho `core`/`databind`
(`ObjectMapper`, `JsonNode`...) mới là API cũ cần đổi sang `tools.jackson.*` — kiểm tra lại import
thật trong project trước khi viết, đừng suy ra từ "Jackson 3 nên mọi thứ đổi package" là sai.

## Java 25

- Dùng được record, sealed interface, pattern matching cho switch, text block.
- Ưu tiên `record` cho DTO thay vì class + Lombok `@Data`.
- Nếu build lỗi `Unsupported class file major version 69` do thư viện bên thứ ba chưa hỗ trợ:
  báo người dùng, đề xuất hạ `<java.version>` xuống 21. Không tự sửa pom.

## JPA

- `ddl-auto: validate`. Entity phải khớp chính xác schema **tổng hợp từ toàn bộ file migration**
  trong `backend/src/main/resources/db/migration/` (không chỉ `V1__init_schema.sql` — mỗi FR bổ
  sung có thể đã thêm bảng/cột ở migration sau đó). App không khởi động được nếu lệch — đây là cơ
  chế bảo vệ, không phải lỗi cần né.
- `open-in-view: false`. Không lazy load ngoài transaction. Dùng `JOIN FETCH` hoặc DTO projection.
- Kiểu cột đặc biệt trong schema này:
  - `JSONB` → map bằng `@JdbcTypeCode(SqlTypes.JSON)`
  - `vector(1536)` → không map trực tiếp qua JPA, dùng native query hoặc `PgVectorStore`
  - Tất cả khoá chính là `UUID` với default `gen_random_uuid()`

## Spring AI 2.0

- Chat: `ChatClient` (Anthropic). Embedding: `EmbeddingModel` (OpenAI, 1536 chiều).
- Vector store: `PgVectorStore`, đã cấu hình sẵn trong `application.yml`.
- **Prompt đặt trong `backend/src/main/resources/ai/prompt/`** (resource, không phải package
  Java), file riêng có đánh version trong tên (`resume-parse-v1.st`, `criterion-score-v1.st`).
  Không hardcode chuỗi prompt giữa business logic.
- Output LLM **luôn** phải validate theo schema. Dùng `BeanOutputConverter` + Bean Validation.
  Parse fail → retry một lần → ghi trạng thái `FAILED`, không để ngoại lệ làm sập luồng.
- Ghi lại `model` và `prompt_version` vào DB cho mọi lần gọi — phục vụ audit ở FR-H08, và là siêu
  dữ liệu bắt buộc K4 (CLAUDE.md §3d) cho mọi kết quả AI của FR bổ sung.
- Dùng `.call().responseEntity(converter)`, **không phải** `.entity(converter)` — `entity()` vứt
  mất `ChatResponse`, mà cột `model`/`token_usage` (NOT NULL ở `resume_parsed_data`) cần đọc từ đó.
- `BeanOutputConverter` dùng constructor 1 tham số `BeanOutputConverter(Class<T>)`. Overload 2
  tham số đòi `tools.jackson.databind.json.JsonMapper`, không phải `ObjectMapper` cũ.
- `toolCallingManager` đã bị gỡ khỏi Spring AI 2.0; tool execution chuyển sang `ToolCallingAdvisor`
  ở tầng `ChatClient`.

### Hai auto-config bị exclude trong `application.yml` — đừng gỡ

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration
```

- **`AnthropicChatAutoConfiguration`** — bind lỗi vào `ThinkingConfigParam` khi khởi động (bug đã
  xác nhận ở Spring AI 2.0.0). `AnthropicChatModel` được khai báo thủ công ở
  `ai/client/AnthropicChatModelConfig` thay cho auto-config này.
- **`OpenAiChatAutoConfiguration`** — starter OpenAI trong dự án này chỉ dùng cho embedding
  (`spring.ai.openai.embedding`, xem `application.yml`), không bao giờ dùng OpenAI làm `ChatModel`.
  Nếu không loại, starter tự động khai thêm một bean `ChatModel` (`openAiChatModel`) song song với
  `anthropicChatModel` — bất kỳ chỗ nào autowire `ChatModel`/`ChatClient.Builder` theo kiểu (ví dụ
  mọi `*ChatClientConfig` trong `ai/client/`) sẽ vỡ `NoUniqueBeanDefinitionException` vì có 2 ứng
  viên mà không bên nào `@Primary`.

Đây không phải cấu hình thừa hay tồn đọng từ lúc setup — gỡ một trong hai dòng exclude sẽ làm app
không khởi động được, hoặc vỡ ngay khi có bean thứ hai tiêu thụ `ChatClient.Builder`. Lỗi này chỉ
lộ ra khi chạy thật cả bộ test, không đoán trước được bằng đọc code.

## Chạy nền và gọi LLM đồng bộ

Không có Redis, không có message queue, không dùng `@Async`. Job nền chạy theo mô hình: bảng
trạng thái + `@Scheduled` poller (xem `resume/ResumeParsingScheduler`, `scoring/ScoringRunScheduler`
và các `*Scheduler` khác trong tree ở dưới — tất cả chỉ dùng `@Scheduled`).

Quy tắc gọi LLM/embedding đồng bộ trong request: xem CLAUDE.md §7 — chỉ được phép cho FR trong
danh sách ngoại lệ K3, với 6 điều kiện bắt buộc (không chép lại ở đây). Khi cần đặt số cho
timeout/số lần retry của một lời gọi đồng bộ như vậy: **xác minh bằng `javap` trên jar Spring
AI/Anthropic SDK/OpenAI SDK thật trong `~/.m2`** để biết đúng tham số/exception SDK trả về (ví dụ
phân loại lỗi tạm thời vs vĩnh viễn), trích bằng chứng trong báo cáo — không đặt số hay đoán tên
tham số theo trí nhớ.

## Cấu trúc package

Chia theo **tính năng**, không theo tầng. Cây thật hiện tại (`com.recruitment.*`):

```
com.recruitment/
├── auth/  user/  company/  job/  jobapplication/  jobrecommendation/
├── interviewtemplate/  interviewinvitation/  rubric/  resume/
├── scoring/  dashboard/  notification/  ratelimit/  storage/  common/
└── ai/
    ├── client/       — cấu hình ChatClient/ChatModel/EmbeddingModel (AnthropicChatModelConfig, *ChatClientConfig, OpenAiEmbeddingClientConfig)
    ├── criterion/     — chấm từng tiêu chí rubric (FR-H04)
    ├── explanation/   — sinh báo cáo giải thích điểm (FR-H06)
    ├── cvimprovement/ — gọi LLM cho gợi ý cải thiện CV (FR-U05)
    └── embedding/     — gọi EmbeddingModel (dùng chung cho FR-U04 và trích xuất CV)
```

Lưu ý dễ đoán sai — **nguyên tắc chung**: orchestrator/entity/scheduler gắn với một nghiệp vụ cụ
thể đặt ở package TÍNH NĂNG đó, KHÔNG đặt dưới `ai/`; chỉ tầng gọi LLM/embedding thuần (không mang
logic nghiệp vụ) mới nằm trong `ai/*`. Hai ví dụ đã có:
- **Trích xuất CV → JSON (FR-C04) nằm trong package `resume/`** (`ResumeParsingOrchestrator`,
  `ResumeParsingService`, `ResumeParsedPayload`...), không phải một subpackage `ai/parsing/`.
- **Gợi ý cải thiện CV (FR-U05)**: orchestrator/entity (`resume/CvImprovementOrchestrator`,
  `resume/CvImprovementRequest`, `resume/CvImprovementScheduler`...) nằm trong `resume/`; chỉ phần
  gọi LLM thuần (`CvImprovementService`) nằm trong `ai/cvimprovement/`.
- **Gợi ý việc làm (FR-U04)** không có `ai/recommendation/` — toàn bộ nằm trong package tính năng
  riêng `jobrecommendation/` ở cấp gốc (không dưới `ai/`), chỉ gọi `ai/embedding/EmbeddingService`
  khi cần vector hoá.

`ai/prompt/` không phải package Java — prompt là resource file ở
`backend/src/main/resources/ai/prompt/*.st` (xem mục Spring AI 2.0 ở trên).

Không tạo `controllers/`, `services/`, `repositories/` ở cấp gốc.

## Bẫy đã gặp ở Boot 4.1 / Hibernate 7

- **MockMvc autoconfigure đổi package**: `@AutoConfigureMockMvc` import từ
  `org.springframework.boot.webmvc.test.autoconfigure` (Boot 4), không phải
  `org.springframework.boot.test.autoconfigure.web.servlet` (Boot 3). Không có `TestRestTemplate`
  trong dự án — mọi integration test gọi HTTP qua `MockMvc`.
- **Native query projection interface + cột enum**: khai getter kiểu `String` (ví dụ
  `ApplicationSummaryView.getStatus()`, `StatusCountView.getStatus()`, `CandidateSearchRow.getStatus()`),
  KHÔNG khai kiểu enum trực tiếp — Spring Data không tự convert `String` sang enum cho native
  query projection, khai enum ném `ConverterNotFoundException` lúc chạy. Tầng service tự
  `ApplicationStatus.valueOf(...)` sau khi đọc projection (xem `ApplicationSearchService`,
  `ApplicationService`).
- **`created_at`/`updated_at` do DB sinh, không do Java gán**: khai
  `@Generated(event = EventType.INSERT)` (hoặc `{EventType.INSERT, EventType.UPDATE}` cho
  `updated_at`) kèm `@Column(insertable = false, updatable = false)` — mẫu dùng nhất quán ở mọi
  entity (`Job`, `JobApplication`, `ScoringRun`...). Thiếu `insertable/updatable = false` sẽ để
  Hibernate cố gán giá trị Java, đè mất default/trigger của Postgres.
- **Filter tự ghi thẳng `HttpServletResponse` phải gọi `flushBuffer()`** ngay sau khi ghi body
  (xem `RateLimitFilter`) — nếu không, `ErrorPageFilter` (bọc ngoài chain Spring Security) thấy
  response chưa commit, tự điều hướng sang xử lý lỗi mặc định và ghi đè mất body: client nhận
  đúng status code nhưng body rỗng dù server đã log là đã ghi.
- **`UUID.compareTo()` của Java KHÔNG cùng ngữ nghĩa với `ORDER BY id` của Postgres** trên cột
  `uuid` (Java so hai `long` có dấu; Postgres so 16 byte không dấu) — không dùng để dự đoán hay
  tái tạo thứ tự một truy vấn `ORDER BY id`. Muốn biết thứ tự thật, đọc lại từ chính
  repository/truy vấn đó (tiền lệ đã sửa: `ScoringRunAuditService`).

## Test

- Testcontainers với **Postgres thật** (`pgvector/pgvector:pg17`, xem
  `backend/src/test/java/com/recruitment/TestcontainersConfiguration.java`) — **không dùng H2**.
  Schema có JSONB, trigger, partial unique index... H2 không mô phỏng đúng được.
- Mock LLM ở tầng `ChatModel` (không bọc interface riêng), default-answer **throw** cho mọi method
  chưa được stub tường minh (mẫu `LlmTestConfiguration`, package `resume`, dùng lại được cho mọi
  test khác cần mock LLM) — test nào quên `Mockito.doReturn(...).when(...)` thì đỏ ngay, thay vì
  âm thầm gọi Anthropic thật qua `ANTHROPIC_API_KEY` giả trong `application-test.yml`.
- Job nền (`@Scheduled`) tắt qua `@ConditionalOnProperty` đọc từ `application-test.yml`. Cờ thật
  hiện có: `app.resume-parsing.enabled`, `app.scoring.enabled`, `app.aggregation.enabled`,
  `app.explanation.enabled`, `app.notification.enabled`, `app.cv-improvement.enabled`,
  `app.job-embedding.enabled`, `app.resume-embedding.enabled`, `app.job-recommendation.enabled`,
  `app.rate-limit.enabled` — tất cả `false` trong test. Test gọi thẳng `orchestrator.processOne(id)`
  (hoặc tương đương), không chờ scheduler tick.
- **FR mới có `@Scheduled` → bắt buộc thêm cờ tắt tương ứng vào `application-test.yml`**, cùng
  khuôn với danh sách trên — thiếu cờ sẽ để scheduler mới tự tick trong `@SpringBootTest` khác
  đang dùng chung context cache, gây nhiễu test không liên quan.

## Frontend

- React 19 + Vite + TypeScript. **Không phải Next.js** — không dùng Server Actions, không dùng
  file-based routing của Next.
- Router: `react-router` v7 (import thẳng từ `react-router`, không bắt buộc qua `react-router-dom`).
- Data fetching: TanStack Query. Form: React Hook Form + Zod.
- Gọi API bằng đường dẫn tương đối `/api/...` — Vite proxy sang `localhost:8080`.
  Không hardcode `http://localhost:8080` trong code.
- Style: Tailwind v4 với token khai báo ở `@theme` trong `frontend/src/index.css`.
  Không tạo `tailwind.config.js`.
- **3 bẫy token hay dính, xem chi tiết `docs/UI_GUIDE.md` mục 1b**: (1) token mới phải khai trong
  khối `@theme` GỐC, không khai trong `@theme inline` (khối shadcn sinh ra, nằm sau nên đè mất);
  (2) Tailwind v4 tham chiếu biến bằng ngoặc tròn `rounded-(--radius-card)`, không phải ngoặc
  vuông; (3) `--accent` ở `:root` dùng chung với nhiều component shadcn (ví dụ `select.tsx` có
  `focus:bg-accent`) — không sửa trực tiếp để đổi màu một nút riêng lẻ.
