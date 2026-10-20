# AI Recruitment Agent

Nền tảng tuyển dụng có AI hỗ trợ cho hai nhóm người dùng: **Nhà tuyển dụng (HR)** và **Ứng viên**. HR đăng tin,
tự cấu hình bộ tiêu chí (rubric) và trọng số, xem điểm từng tiêu chí kèm trích dẫn từ CV rồi tự ra quyết định.
Ứng viên tải lên hoặc tự dựng CV, tìm việc (kể cả bằng câu mô tả tự nhiên), nhận gợi ý việc làm và gợi ý cải thiện
CV, theo dõi đơn và trao đổi với HR. Hệ thống có 37 mã chức năng, tất cả đã hoàn thành.

**Nguyên tắc AI cốt lõi** (đầy đủ ở [CLAUDE.md](CLAUDE.md) §2 và [docs/SRS.md](docs/SRS.md)):

- AI **chỉ** chấm từng tiêu chí riêng lẻ, kèm giải thích và evidence trích **nguyên văn** từ CV; evidence không
  khớp nguyên văn thì cả lượt chấm dừng và không tính điểm.
- AI **không** tính tổng điểm, **không** xếp hạng — Backend làm bằng công thức trọng số tường minh.
- AI **không** gán nhãn Đạt / Không đạt và **không** quyết định — HR quyết định trên từng hồ sơ.
- AI không viết lại, tóm tắt hay dịch nội dung CV khi trích xuất. CV chỉ được AI phân tích khi ứng viên đã đồng ý.

## Tính năng theo vai trò

Danh sách và trạng thái đầy đủ: [docs/SRS.md](docs/SRS.md) mục 0. Cột "AI": **LLM** = gọi mô hình ngôn ngữ;
**Embedding** = vector ngữ nghĩa; **nền** = chạy ở job nền; **đồng bộ** = gọi trong request (ngoại lệ K3).

**Chung (8)**

| Mã | Chức năng | AI |
|---|---|---|
| FR-C01 | Tài khoản và phân quyền (JWT, RBAC) | Không |
| FR-C02 | Duyệt thông tin công khai | Không |
| FR-C03 | Thông báo web và email | Không |
| FR-C04 | Trích xuất CV thành dữ liệu có cấu trúc | LLM, nền |
| FR-C05 | Danh mục dùng chung, chuẩn hoá dữ liệu | Chỉ qua bước trích xuất của FR-C04 |
| FR-C06 | Nhắn tin theo đơn ứng tuyển | Không |
| FR-C07 | AI soạn nháp tin nhắn | LLM, đồng bộ |
| FR-C08 | Hỏi đáp CV có trích dẫn | LLM, đồng bộ |

**HR (15)**

| Mã | Chức năng | AI |
|---|---|---|
| FR-H01 | Hồ sơ doanh nghiệp | Không |
| FR-H02 | Tin tuyển dụng và mẫu giấy mời | Không |
| FR-H03 | Rubric: tiêu chí và trọng số | Không |
| FR-H04 | Chấm điểm từng tiêu chí kèm evidence | LLM, nền |
| FR-H05 | Tổng điểm có trọng số và xếp hạng | Không — Backend tính |
| FR-H06 | Giải thích điểm số | LLM, nền |
| FR-H07 | Pipeline và quyết định tuyển dụng | Không — HR quyết định |
| FR-H08 | Dashboard, thống kê, lịch sử đánh giá | Không |
| FR-H09 | Trang hồ sơ đơn ứng tuyển | Không (hiển thị kết quả đã lưu) |
| FR-H10 | Câu hỏi sàng lọc theo tin | Không |
| FR-H11 | AI tạo bản nháp tin tuyển dụng | LLM, đồng bộ |
| FR-H12 | Giấy mời phỏng vấn nhiều khung giờ | Không |
| FR-H14 | So sánh 2–3 ứng viên | Không |
| FR-H15 | Kho ứng viên | Không |
| FR-H16 | Chế độ ẩn danh | Không |

**Ứng viên (14)**

| Mã | Chức năng | AI |
|---|---|---|
| FR-U01 | Hồ sơ cá nhân, tải lên nhiều phiên bản CV | Không (kích hoạt FR-C04) |
| FR-U02 | Tìm việc và ứng tuyển (đồng ý cho AI phân tích) | Không |
| FR-U03 | Theo dõi trạng thái đơn | Không |
| FR-U04 | Gợi ý việc làm | Embedding, nền |
| FR-U05 | Gợi ý cải thiện CV | LLM, nền |
| FR-U06 | Rút đơn (đổi trạng thái, không xoá) | Không |
| FR-U07 | Bộ lọc tìm việc nâng cao | Không |
| FR-U08 | Trang chi tiết đơn ứng tuyển | Không |
| FR-U09 | Trả lời sàng lọc, đồng ý lưu hồ sơ | Không |
| FR-U10 | Chọn khung giờ phỏng vấn | Không |
| FR-U12 | Tạo CV bằng form (CV builder) | LLM, đồng bộ (chỉ gợi ý diễn đạt) |
| FR-U13 | Tìm việc bằng ngôn ngữ tự nhiên | LLM và embedding, đồng bộ |
| FR-U14 | Hồ sơ nghề nghiệp và mong muốn | Embedding, nền |
| FR-U15 | Gợi ý việc làm theo hồ sơ | Dùng embedding có sẵn, không gọi AI mới |

FR-H13 và FR-U11 đã loại khỏi phạm vi; mã không dùng lại.

## Kiến trúc và công nghệ

Monorepo: `backend/` (Spring Boot) + `frontend/` (React) + một PostgreSQL. Không microservice. Lý do chọn từng
công nghệ: [docs/TECH_STACK.md](docs/TECH_STACK.md).

- **Backend:** Java 25, Spring Boot 4.1, Spring Security 7 + JWT, Spring Data JPA; Flyway là nguồn sự thật của
  schema (`ddl-auto: validate`). Package chia theo tính năng (mỗi mã FR gọn trong một package).
- **AI:** Spring AI 2.0 — Anthropic cho LLM, OpenAI cho embedding, pgvector (HNSW, cosine, 1536 chiều).
- **Frontend:** Vite + React 19 + TypeScript, TanStack Query, React Hook Form + Zod, Tailwind + shadcn/ui.
- **Job nền:** bảng trạng thái + `@Scheduled` poller (không Redis, không MQ). Claim bản ghi bằng `UPDATE` có điều
  kiện, gọi AI ngoài transaction, tự thử lại khi lỗi tạm thời.
- **Ngoại lệ gọi AI đồng bộ (K3):** chỉ FR-C07, FR-C08, FR-H11, FR-U12, FR-U13 — có thời gian chờ tối đa, rate limit
  theo người dùng, không giữ transaction khi chờ AI, lỗi trả mã chuẩn hoá. Mọi chỗ khác gọi AI qua job nền.
- **Sơ đồ dữ liệu:** [docs/ER.md](docs/ER.md) — 35 bảng, xuất từ catalog Postgres (Flyway V19).

## Chạy dự án

Yêu cầu: JDK 25, Node.js 20.19+ hoặc 22.12+, Docker Desktop đang chạy. Không cần cài Maven (dùng `mvnw.cmd`).
Các lệnh dưới đây dành cho PowerShell 5.1, chạy từ gốc repo.

**1. Biến môi trường.** Backend đọc file `.env` trong thư mục làm việc, tức `backend\.env` khi chạy từ `backend\`.
Tạo từ mẫu rồi điền giá trị thật (file đã nằm trong `.gitignore`, không commit):

```powershell
Copy-Item .env.example backend\.env
```

| Biến | Dùng cho |
|---|---|
| `ANTHROPIC_API_KEY` | LLM (trích xuất, chấm điểm, giải thích, soạn nháp…) |
| `OPENAI_API_KEY` | Embedding |
| `JWT_SECRET` | Ký JWT, tối thiểu 32 ký tự ngẫu nhiên |

**2. Hạ tầng** (PostgreSQL + pgvector, MailHog, MinIO):

```powershell
docker compose up -d
```

**3. Backend** (Flyway tự chạy migration khi khởi động):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Chạy backend **không gọi AI** (tắt cả 8 scheduler gọi AI) — dùng khi demo trên dữ liệu đã nạp hoặc soát giao
diện. Các chức năng AI đồng bộ (K3) vẫn gọi AI khi người dùng bấm.

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.job-embedding.enabled=false --app.candidate-profile-embedding.enabled=false --app.resume-embedding.enabled=false --app.resume-parsing.enabled=false --app.scoring.enabled=false --app.explanation.enabled=false --app.cv-improvement.enabled=false --app.resume-reparse.enabled=false"
```

**4. Frontend** (cửa sổ PowerShell khác):

```powershell
cd frontend
npm install
npm run dev
```

| Dịch vụ | Địa chỉ |
|---|---|
| Frontend | http://localhost:5173 |
| API | http://localhost:8080 |
| MailHog (xem email đã gửi) | http://localhost:8025 |
| MinIO Console (chạy kèm, backend hiện lưu file trên đĩa) | http://localhost:9001 |

Reset sạch database khi migration hỏng (xoá toàn bộ dữ liệu):

```powershell
docker compose down -v
docker compose up -d
```

## Dữ liệu demo

Bộ demo gồm 2 công ty, 12 tin đang mở, 9 ứng viên, điểm và giải thích do AI thật sinh sẵn — nạp không cần gọi AI.
Nạp theo **đúng thứ tự** ở [db/seed/README.md](db/seed/README.md) mục 1 (DB phải ở V19 trở lên; khởi động backend
một lần với lệnh "không gọi AI" ở trên để Flyway chạy xong).

| Tài khoản | Vai trò | Mật khẩu |
|---|---|---|
| `hr@demo.local` | HR — công ty có tin, đơn, điểm, giải thích | `Demo1234` |
| `hr.nganha@demo.local` | HR — công ty thứ hai | `Demo1234` |
| `tran.minh.hoang@demo.local` | Ứng viên | `Demo1234` |
| `bui.ngoc.mai@demo.local` | Ứng viên (có CV dựng bằng CV builder) | `Demo1234` |

Đủ 11 tài khoản demo: [db/seed/README.md](db/seed/README.md) mục 4. Mật khẩu công khai có chủ đích, chỉ cho máy demo
cục bộ.

> **Không nạp bộ test (`seed-test.sql`, [db/seed/README.md](db/seed/README.md) mục 8) khi demo cho hội đồng** — bộ
> test là ma trận trạng thái để soát giao diện, cố ý không có điểm AI.

## Kiểm thử

```powershell
cd backend
.\mvnw.cmd test
```

```powershell
cd frontend
npm run build
npm run lint
```

- Backend: JUnit 5 + Testcontainers (PostgreSQL thật, cần Docker đang chạy). Luôn chạy full suite. Test không gọi
  API AI thật — `ChatModel`/`EmbeddingModel` được mock.
- Frontend: `npm run build` (`tsc -b` + `vite build`) và `npm run lint` (ESLint). Chưa có test tự động.
- CI ([.github/workflows/ci.yml](.github/workflows/ci.yml)) chạy trên push/PR vào `main`: backend
  `mvnw clean verify`, frontend `npm ci` + `npm run build`.

## Cấu trúc

```
├── backend/                 Spring Boot, chia package theo tính năng
│   └── src/main/resources/
│       ├── db/migration/    Flyway V1–V19
│       └── ai/prompt/       Prompt gửi LLM, đánh phiên bản trong tên file
├── frontend/                React + Vite
├── db/seed/                 Dữ liệu demo (4 tầng) và bộ test, kèm hướng dẫn nạp
├── docs/                    Tài liệu (bảng bên dưới)
├── .claude/skills/          Quy trình làm việc với Claude Code
├── .github/workflows/       CI
├── CLAUDE.md                Quy tắc dự án và ngữ cảnh cho công cụ AI hỗ trợ code
├── docker-compose.yml       PostgreSQL + pgvector, MailHog, MinIO
└── .env.example             Mẫu biến môi trường
```

## Tài liệu

| Tài liệu | Nội dung |
|---|---|
| [docs/SRS.md](docs/SRS.md) | Tổng quan 37 mã FR (mục 0), đặc tả đầy đủ 18 FR gốc, nguyên tắc bổ sung |
| [docs/features/README.md](docs/features/README.md) | Thứ tự thực hiện và mô tả định hướng 19 FR bổ sung |
| `docs/features/<nhóm>/<mã>/` | `REQUIREMENT.md` (đặc tả chức năng) và `UI.md` (đặc tả giao diện) của từng FR bổ sung |
| [docs/UI_GUIDE.md](docs/UI_GUIDE.md) | Design token, quy tắc Material Design 3, bản đồ màn hình |
| [docs/TECH_STACK.md](docs/TECH_STACK.md) | Lý do chọn từng công nghệ |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Tiến độ theo nhánh, nợ kỹ thuật, giới hạn đã biết |
| [docs/ER.md](docs/ER.md) | Sơ đồ ER xuất từ database thật |
| [docs/walkthrough/](docs/walkthrough/) | Luồng và quyết định thiết kế của từng nhánh |
| [db/seed/README.md](db/seed/README.md) | Nạp dữ liệu demo/test, tài khoản |
| [CLAUDE.md](CLAUDE.md) | Nguyên tắc bất di bất dịch, quy ước code, ranh giới không được vượt |

## Giới hạn đã biết

Danh sách đầy đủ theo từng nhánh: [docs/ROADMAP.md](docs/ROADMAP.md). Một số giới hạn chính:

- Schema JSON trích xuất CV (FR-C04) đánh dấu mọi field là bắt buộc, có thể đẩy AI điền giá trị cho mục CV không có
  (ROADMAP, Phase D, mục D1).
- Một tiêu chí có evidence không khớp nguyên văn làm hỏng cả lượt chấm; HR phải tạo lượt chấm mới (ROADMAP,
  `chore/seed-demo`).
- Chế độ ẩn danh (FR-H16) chỉ che đúng các giá trị danh tính đã biết (họ tên, email, SĐT…); biến thể viết khác hoặc
  danh tính suy ra từ nội dung không bị che (ROADMAP, `feat/fr-h16-blind`).
- Tin nhắn (FR-C06) không thời gian thực — tự tải lại định kỳ.
- Ngưỡng tương đồng của gợi ý việc làm loại được rất ít; giá trị nằm ở thứ tự xếp hạng (SRS, FR-U04).
- Rate limit lưu trong bộ nhớ một instance, không Redis (ROADMAP, mục "Cố ý không làm").

## Known issues

`npm audit` trong `frontend/` (chạy ngày 20/10/2026): **19 cảnh báo — 1 critical, 14 high, 4 moderate**.

- `axios` 1.19.0 (high) là thư viện chạy trong trình duyệt, khai trực tiếp; cần nâng phiên bản.
- Phần lớn còn lại đến từ gói `shadcn` 4.16.2 (công cụ dòng lệnh, khai trong `dependencies`; mã nguồn chỉ import
  `shadcn/tailwind.css`) qua các phụ thuộc như `@modelcontextprotocol/sdk`, `express`/`proxy-addr` (critical),
  `undici`, `postcss`, `js-yaml`, cùng `eslint` (`brace-expansion`) — công cụ phát triển, không đóng gói vào ứng dụng.
- Chưa chạy `npm audit fix` vì đổi `package-lock.json` là sửa code, cần nhánh riêng và chạy lại build + lint.
