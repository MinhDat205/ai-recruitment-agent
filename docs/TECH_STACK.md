# Stack công nghệ — AI Recruitment Agent

Kiến trúc: **monorepo, 2 ứng dụng** (`backend/` Spring Boot + `frontend/` React), 1 PostgreSQL.
Không tách microservice. Không thêm service Python.

---

## Backend

| Thành phần | Chọn | Ghi chú |
|---|---|---|
| JDK | Java 21 LTS | Spring Boot 3.5 chạy tốt trên 17/21; 21 là mặc định an toàn |
| Framework | Spring Boot 3.5.16 | Xem mục "Vấn đề phiên bản" bên dưới |
| Build | Maven (dùng `mvnw` wrapper) | Commit wrapper để máy khác chạy được ngay |
| Security | Spring Security 6 + JWT (jjwt) | BCrypt cho mật khẩu, `@PreAuthorize` cho RBAC |
| Data | Spring Data JPA + Hibernate | `ddl-auto: validate`, KHÔNG dùng `update` |
| Migration | **Flyway** | Schema là file SQL có version — Claude Code sửa được, và là bằng chứng audit |
| AI | Spring AI 1.1.8 | `ChatClient`, `EmbeddingModel`, `PgVectorStore` |
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

## Vấn đề phiên bản — đọc kỹ trước khi chọn

Tính đến 08/2026, Spring Boot và Spring AI đang lệch pha nhau:

- **Spring Boot 4.1** là bản stable mới nhất, nhưng **Spring AI 2.0 vẫn đang ở milestone (M7)** và đã có
  breaking change giữa các milestone (MCP đổi transport, ToolCallAdvisor đổi cách hoạt động).
- **Spring AI 1.1.x** là bản stable, nhưng bám theo **Spring Boot 3.5**, mà 3.5 đã hết hỗ trợ
  mã nguồn mở từ 30/06/2026.

**Khuyến nghị cho dự án này: Spring Boot 3.5.16 + Spring AI 1.1.8.**
Lý do: bạn đang vibe code. API ổn định + nhiều ví dụ trên mạng quan trọng hơn nhiều so với việc chạy
bản mới nhất. Dùng milestone nghĩa là mỗi lần bump version bạn phải sửa code AI đã sinh ra, và
Claude cũng ít dữ liệu về API milestone hơn.

Ghi chú EOL vào README như một "known limitation" — với đồ án thì đây không phải rủi ro thật.
Nếu sau này muốn lên production: chờ Spring AI 2.0 GA rồi nâng cả cặp lên Boot 4.x một lần.

**Phương án thay thế nếu bạn bắt buộc phải dùng Boot 4.1:** bỏ Spring AI, gọi thẳng Anthropic Java SDK
và viết truy vấn pgvector bằng native query. Mất khoảng 200 dòng code tự viết, đổi lại không phụ thuộc
milestone.

---

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
