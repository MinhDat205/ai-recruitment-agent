# Stack công nghệ — AI Recruitment Agent

Kiến trúc: **monorepo, 2 ứng dụng** (`backend/` Spring Boot + `frontend/` React), 1 PostgreSQL.
Không tách microservice. Không thêm service Python.

---

## Backend

| Thành phần | Chọn | Ghi chú |
|---|---|---|
| JDK | Java 25 LTS | Spring Boot 4.1 hỗ trợ 17 đến 26 |
| Framework | Spring Boot 4.1.x | Bản stable hiện tại; 3.5 đã EOL từ 30/06/2026 |
| Build | Maven (dùng `mvnw` wrapper) | Commit wrapper để máy khác chạy được ngay |
| Security | Spring Security 7 + JWT (jjwt) | BCrypt cho mật khẩu, `@PreAuthorize` cho RBAC |
| Data | Spring Data JPA + Hibernate | `ddl-auto: validate`, KHÔNG dùng `update` |
| Migration | **Flyway** | Schema là file SQL có version — Claude Code sửa được, và là bằng chứng audit |
| AI | Spring AI 2.0.0 | `ChatClient`, `EmbeddingModel`, `PgVectorStore` |
| Đọc file CV | Apache PDFBox (PDF) + Apache POI (DOCX) | Hoặc Apache Tika cho cả hai |
| Validate output LLM | Spring AI `BeanOutputConverter` + Bean Validation | Ép LLM trả JSON đúng schema |
| Job nền | Bảng trạng thái + `@Scheduled` poller + `@Async` | Không cần Redis/RabbitMQ |
| Email | Spring Mail (SMTP) | Dev dùng MailHog trong docker-compose |
| API doc | springdoc-openapi | Swagger UI để test nhanh |
| Test | JUnit 5 + Testcontainers | Testcontainers chạy Postgres thật, không H2 |

## Frontend

| Thành phần | Chọn |
|---|---|
| Build | Vite + React 19 + TypeScript |
| Router | React Router 7 |
| Data fetching | TanStack Query |
| Form | React Hook Form + Zod |
| UI | Tailwind CSS + shadcn/ui |
| Chart (FR-H08) | Recharts |

Dùng Vite chứ không dùng Next.js: bạn đã có backend Spring Boot rồi, SSR và API routes của Next.js
sẽ thành lớp thứ hai thừa thãi, và AI rất hay viết logic nghiệp vụ nhầm vào đó.

## Hạ tầng

| Thành phần | Chọn |
|---|---|
| Database | PostgreSQL 17 + extension `pgvector` |
| Vector search | pgvector (index HNSW, cosine) — không dùng Pinecone/Qdrant |
| File CV | Dev: thư mục local. Prod: MinIO / S3 / Cloudflare R2 (private bucket + presigned URL) |
| Dev env | Docker Compose (postgres + mailhog) |
| CI | GitHub Actions (build + test) |

---

## Ghi chú phiên bản

Chốt: **Spring Boot 4.1.x + Spring AI 2.0.0 + Java 25.** Cả ba đều là bản chính thức, còn được hỗ trợ.

Bối cảnh (để hiểu tại sao tài liệu cũ trên mạng hay mâu thuẫn nhau):
- Spring Boot 3.5 hết hỗ trợ OSS ngày 30/06/2026 → `start.spring.io` không còn cho sinh project với 3.5.
- Spring AI 1.1.x bám theo Boot 3.5 và **không chạy được** trên Boot 4 — đừng nhặt ví dụ 1.x về dùng.
- Spring AI 2.0.0 GA ngày 12/06/2026, dựng trên Boot 4.0/4.1 + Spring Framework 7.

Hệ quả cần nhớ khi vibe code:
- **Jackson 3, không phải Jackson 2.** Ví dụ cũ dùng `com.fasterxml.jackson.*` sẽ sai package.
- **JSpecify null-safety** được bật toàn bộ codebase Spring AI — kiểu nullable bị siết ở compile time.
- **MCP dùng Streamable HTTP**, SSE transport đã bị thay thế.
- Mọi tutorial Spring AI viết trước tháng 6/2026 gần như chắc chắn là API 1.x. Kiểm tra version trước khi copy.

## Ranh giới trách nhiệm (khớp SRS)

```
Frontend (React)
   │  gọi REST API, không chứa logic nghiệp vụ
   ▼
Spring Boot
   ├── auth, user, company, job, rubric, jobapplication   ← CRUD + RBAC + validate
   ├── scoring/ScoreAggregator.java                        ← FR-H05: TÍNH TỔNG + XẾP HẠNG (Java thuần, có unit test)
   └── ai/                                                 ← chỉ gọi LLM và parse kết quả
          ├── ResumeParsingService     FR-C04
          ├── CriterionScoringService  FR-H04  (chấm TỪNG tiêu chí)
          ├── ExplanationService       FR-H06
          ├── JobRecommendationService FR-U04  (embedding + cosine)
          └── CvImprovementService     FR-U05
```

Quy tắc bất biến: `ai/` **không được** import `ScoreAggregator`, và không có class nào trong `ai/`
được trả về nhãn đậu/rớt. Nếu Claude Code sinh ra code vi phạm, đó là bug — không phải "tối ưu".