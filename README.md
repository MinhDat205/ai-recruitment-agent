# AI Recruitment Agent

Nền tảng tuyển dụng có AI hỗ trợ, phục vụ hai nhóm người dùng: **Nhà tuyển dụng (HR)** và **Ứng viên**.

AI trong hệ thống làm ba việc: trích xuất CV thành dữ liệu có cấu trúc, chấm điểm từng tiêu chí
trong bộ rubric do HR tự cấu hình, và giải thích điểm số kèm trích dẫn từ CV. AI **không** tính tổng
điểm, **không** xếp hạng, và **không** quyết định ứng viên đậu hay rớt — những việc đó thuộc về
Backend (công thức trọng số tường minh) và HR (quyết định cuối cùng).

## Công nghệ

| Lớp | Công nghệ |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring Security 7, Spring Data JPA, Flyway |
| AI | Spring AI 2.0 (Anthropic chat, OpenAI embedding, PgVector store) |
| Database | PostgreSQL 17 + pgvector (HNSW, cosine) |
| Frontend | React 19, Vite, TypeScript, Tailwind CSS, TanStack Query |
| Hạ tầng dev | Docker Compose (PostgreSQL, MailHog, MinIO) |
| Test | JUnit 5, Testcontainers |

## Yêu cầu môi trường

- JDK 25 (tối thiểu 21)
- Node.js 20+
- Docker Desktop đang chạy
- Không cần cài Maven — dùng `mvnw` / `mvnw.cmd` có sẵn trong repo

## Chạy dự án

```bash
cp .env.example .env          # rồi điền API key vào .env
docker compose up -d          # PostgreSQL + MailHog + MinIO

cd backend && ./mvnw spring-boot:run     # Windows: .\mvnw.cmd spring-boot:run
cd frontend && npm install && npm run dev
```

| Dịch vụ | Địa chỉ |
|---|---|
| Frontend | http://localhost:5173 |
| API | http://localhost:8080 |
| MailHog (xem email) | http://localhost:8025 |
| MinIO Console | http://localhost:9001 |

Reset sạch database khi migration hỏng: `docker compose down -v && docker compose up -d`

## Cấu trúc

```
├── backend/            Spring Boot, chia package theo tính năng
├── frontend/           React + Vite
├── docs/
│   ├── SRS.md          Đặc tả yêu cầu chức năng — nguồn sự thật
│   ├── ROADMAP.md      Kế hoạch chia phase theo mã FR
│   ├── TECH_STACK.md   Lý do chọn từng công nghệ
│   ├── UI_GUIDE.md     Design token và quy ước giao diện
│   ├── SETUP.md        Hướng dẫn khởi tạo từ đầu
│   └── decisions/      Ghi lại các quyết định kiến trúc (ADR)
├── CLAUDE.md           Ngữ cảnh cho công cụ AI hỗ trợ code
└── docker-compose.yml
```

## Trạng thái

Đang ở giai đoạn khởi tạo. Chưa triển khai chức năng nào — xem `docs/ROADMAP.md` để biết thứ tự.

## Ghi chú

Dự án dùng Spring Boot 4.1 và Spring AI 2.0. Phần lớn tài liệu Spring AI trên mạng viết trước
tháng 06/2026 là API 1.x (chạy trên Boot 3.5) và **không tương thích** — kiểm tra version trước khi
tham khảo ví dụ.
## Known issues

- `npm audit` báo GHSA-qwww-vcr4-c8h2 (React Router RSC CSRF) là high severity.
  Đây là báo sai: vùng version trong advisory database ghi gộp thành 7.12.0-8.2.0,
  trong khi advisory gốc xác định bản vá là 7.18.2 - đúng bản dự án đang dùng.
  Ngoài ra lỗ hổng chỉ ảnh hưởng các API RSC chưa ổn định; frontend là SPA thuần
  chạy Vite, không dùng RSC, nên đường code đó không tồn tại trong ứng dụng.
