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

## Jackson 3, không phải Jackson 2

Spring AI 2.0 và Boot 4 dùng Jackson 3. Package đã đổi.
Ví dụ nào dùng `com.fasterxml.jackson.*` là API cũ — kiểm tra lại import thật trong project
trước khi viết.

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
- **Prompt đặt trong `ai/prompt/`**, file riêng có đánh version. Không hardcode chuỗi prompt
  giữa business logic.
- Output LLM **luôn** phải validate theo schema. Dùng `BeanOutputConverter` + Bean Validation.
  Parse fail → retry một lần → ghi trạng thái `FAILED`, không để ngoại lệ làm sập luồng.
- Ghi lại `model` và `prompt_version` vào DB cho mọi lần gọi — phục vụ audit ở FR-H08.

## Chạy nền

Không có Redis, không có message queue. Job nền chạy theo mô hình:
bảng trạng thái (`resumes.parse_status`, `scoring_runs.status`) + `@Scheduled` poller + `@Async`.

Không gọi LLM đồng bộ trong request của người dùng — thời gian phản hồi LLM tính bằng chục giây.

## Cấu trúc package

Chia theo **tính năng**, không theo tầng:

```
com.recruitment/
├── common/  auth/  user/  company/  job/  rubric/
├── resume/  jobapplication/  scoring/  notification/  dashboard/
└── ai/  ├── client/ prompt/ parsing/ criterion/ explanation/ recommendation/ improvement/
```

Không tạo `controllers/`, `services/`, `repositories/` ở cấp gốc.

## Frontend

- React 19 + Vite + TypeScript. **Không phải Next.js** — không dùng Server Actions, không dùng
  file-based routing của Next.
- Router: `react-router` v7 (import thẳng từ `react-router`, không bắt buộc qua `react-router-dom`).
- Data fetching: TanStack Query. Form: React Hook Form + Zod.
- Gọi API bằng đường dẫn tương đối `/api/...` — Vite proxy sang `localhost:8080`.
  Không hardcode `http://localhost:8080` trong code.
- Style: Tailwind v4 với token khai báo ở `@theme` trong `frontend/src/index.css`.
  Không tạo `tailwind.config.js`.
