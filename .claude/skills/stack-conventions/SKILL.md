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

- `ddl-auto: validate`. Entity phải khớp chính xác schema trong `V1__init_schema.sql`.
  App không khởi động được nếu lệch — đây là cơ chế bảo vệ, không phải lỗi cần né.
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
- Ghi lại `model` và `prompt_version` vào DB cho mọi lần gọi — phục vụ audit ở FR-H08.
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

## Chạy nền

Không có Redis, không có message queue, không dùng `@Async` (không xuất hiện trong code thật —
xác nhận bằng `rg "@Async" backend/src/main/java`, 0 kết quả). Job nền chạy theo mô hình:
bảng trạng thái (`resumes.parse_status`, `scoring_runs.status`) + `@Scheduled` poller, khớp đúng
`ResumeParsingScheduler` (D1) và `ScoringRunScheduler` (D2) — cả hai chỉ dùng `@Scheduled`.

Không gọi LLM đồng bộ trong request của người dùng — thời gian phản hồi LLM tính bằng chục giây.

## Cấu trúc package

Chia theo **tính năng**, không theo tầng. Cây thật hiện tại (sau D2, `com.recruitment.*`):

```
com.recruitment/
├── auth/  user/  company/  job/  interviewtemplate/  rubric/
├── resume/  jobapplication/  scoring/  storage/  common/
└── ai/
    ├── client/     — cấu hình ChatClient/ChatModel (AnthropicChatModelConfig, *ChatClientConfig)
    └── criterion/  — chấm từng tiêu chí rubric (FR-H04, D2)
```

Lưu ý dễ đoán sai:
- **Việc trích xuất CV → JSON (FR-C04, D1) nằm trong package `resume/`**
  (`ResumeParsingOrchestrator`, `ResumeParsingService`, `ResumeParsedPayload`...), **không phải**
  một subpackage `ai/parsing/` như sơ đồ dự kiến ban đầu — logic đó gắn với tính năng "hồ sơ CV"
  hơn là tách riêng theo "AI". Đừng tạo `ai/parsing/` khi động vào D1.
- `ai/prompt/` không phải package Java — prompt là resource file ở
  `backend/src/main/resources/ai/prompt/*.st` (xem mục Spring AI 2.0 ở trên).
- `ai/explanation/` (D4), `ai/recommendation/` (F1), `ai/improvement/` (F2) **chưa tồn tại** — đó
  là dự kiến cho các nhánh chưa làm, không phải cây thật. Khi làm tới, cân nhắc lại việc tách
  subpackage riêng dưới `ai/` hay gộp vào package tính năng (như `resume/` đã làm), tuỳ mức độ gắn
  kết với logic nghiệp vụ xung quanh — không mặc định phải tách.

Không tạo `controllers/`, `services/`, `repositories/` ở cấp gốc.

## Test

- Testcontainers với **Postgres thật** (`pgvector/pgvector:pg17`, xem
  `backend/src/test/java/com/recruitment/TestcontainersConfiguration.java`) — **không dùng H2**.
  Schema có JSONB, trigger, partial unique index... H2 không mô phỏng đúng được.
- Mock LLM ở tầng `ChatModel` (không bọc interface riêng), default-answer **throw** cho mọi method
  chưa được stub tường minh (mẫu `LlmTestConfiguration`, package `resume`, dùng lại được cho mọi
  test khác cần mock LLM — kể cả ở `ai/criterion`) — test nào quên `Mockito.doReturn(...).when(...)`
  thì đỏ ngay, thay vì âm thầm gọi Anthropic thật qua `ANTHROPIC_API_KEY` giả trong
  `application-test.yml`.
- Job nền (`@Scheduled`) tắt qua `@ConditionalOnProperty` đọc từ `application-test.yml`
  (`app.resume-parsing.enabled: false`, `app.scoring.enabled: false`) — tránh scheduler tự tick
  gây nhiễu các `@SpringBootTest` khác đang dùng chung context cache. Test gọi thẳng
  `orchestrator.processOne(id)`, không chờ scheduler tick.

## Frontend

- React 19 + Vite + TypeScript. **Không phải Next.js** — không dùng Server Actions, không dùng
  file-based routing của Next.
- Router: `react-router` v7 (import thẳng từ `react-router`, không bắt buộc qua `react-router-dom`).
- Data fetching: TanStack Query. Form: React Hook Form + Zod.
- Gọi API bằng đường dẫn tương đối `/api/...` — Vite proxy sang `localhost:8080`.
  Không hardcode `http://localhost:8080` trong code.
- Style: Tailwind v4 với token khai báo ở `@theme` trong `frontend/src/index.css`.
  Không tạo `tailwind.config.js`.
