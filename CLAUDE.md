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
- Ứng viên phải tick consent trước khi CV được AI phân tích (FR-U02).
- Rút đơn = đổi trạng thái (soft state), KHÔNG hard delete (FR-U06).
- Vòng đời đơn ứng tuyển đúng 5 trạng thái: `PENDING` → `INTERVIEW_INVITED` → `HIRED` | `REJECTED`; và `WITHDRAWN` có thể xảy ra bất kỳ lúc nào trước kết quả cuối.

## 3. Stack

Monorepo: `backend/` (Spring Boot) + `frontend/` (React) + 1 PostgreSQL. Không microservice.

- Backend: Java 21, Spring Boot 3.5.16, Spring Security 6 + JWT, Spring Data JPA
- Migration: Flyway (`backend/src/main/resources/db/migration`) — Flyway là nguồn sự thật của schema, `ddl-auto: validate`
- AI: Spring AI 1.1.8 — `ChatClient` (Anthropic), `EmbeddingModel` (OpenAI), `PgVectorStore`
- Đọc CV: Apache PDFBox (PDF) + Apache POI (DOCX)
- Database: PostgreSQL 17 + pgvector, index HNSW cosine, `vector(1536)`
- Frontend: Vite + React 19 + TypeScript, TanStack Query, React Hook Form + Zod, Tailwind + shadcn/ui
- Job nền: bảng trạng thái + `@Scheduled` poller + `@Async`. Không Redis, không MQ.
- Test: JUnit 5 + Testcontainers (Postgres thật, không H2)

Chia package theo TÍNH NĂNG, không theo tầng: `auth/`, `job/`, `rubric/`, `jobapplication/`,
`scoring/`, `ai/...`. Mỗi mã FR nằm gọn trong một package.

## 4. Quy ước code

- Ngôn ngữ code + comment: tiếng Anh. Chat/giải thích với tôi: tiếng Việt.
- Mọi endpoint phải kiểm tra role (RBAC) — không tin vào việc UI đã ẩn nút.
- Prompt gửi LLM đặt trong thư mục riêng (`prompts/` hoặc `ai/prompts/`), không hardcode giữa business logic.
- Output của LLM luôn phải parse theo schema (zod / pydantic) và có fallback khi parse fail.
- Không commit `.env`, không log nội dung CV ra console ở production.

## 5. Lệnh hay dùng

```bash
docker compose up -d                 # postgres + mailhog + minio
cd backend && ./mvnw spring-boot:run # API tại http://localhost:8080, Swagger /swagger-ui.html
cd backend && ./mvnw test            # JUnit + Testcontainers
cd frontend && npm run dev           # http://localhost:5173
docker compose down -v               # reset sạch DB khi migration hỏng
```

## 6. Cách tôi muốn làm việc với Claude

- Việc lớn: dùng **Plan Mode** trước, tôi duyệt plan rồi mới code.
- Mỗi lần chỉ làm **một mã FR**. Xong thì dừng, để tôi review + commit.
- Trước khi sửa file có sẵn, đọc file đó trước — không đoán.
- Nếu yêu cầu của tôi mâu thuẫn với `docs/SRS.md`, hãy nói ra thay vì tự chọn.
- Sau khi code xong, tự chạy lint/test và tự sửa lỗi trước khi báo hoàn thành.

## 7. Ranh giới không được vượt

- Package `ai/` KHÔNG được import `scoring/ScoreAggregator`. AI không tính tổng điểm.
- Không tạo cột/field tên `verdict`, `label`, `isQualified`, `passed` ở bất kỳ entity nào.
- Không đổi `ddl-auto` sang `update`. Mọi thay đổi schema đi qua một file Flyway mới.
- Không xoá cột `weight_snapshot` / `rubric_snapshot` dù trông có vẻ trùng dữ liệu — chúng giữ lịch sử audit.
- Không commit `.env`, không commit thư mục `uploads/`.
