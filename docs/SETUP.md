# Khởi tạo dự án AI Recruitment Agent

> ## Ghi chú cho Windows / PowerShell
>
> - **Không cần cài Maven.** Project sinh ra đã có `mvnw.cmd`. Mọi chỗ viết `./mvnw` thì gõ `.\mvnw.cmd`.
> - **`curl` trong PowerShell là alias của `Invoke-WebRequest`** và không hiểu `-d`. Dùng khối
>   `Invoke-WebRequest` ở mục 2 thay cho lệnh `curl`.
> - **JDK 25 chạy được với Spring Boot 3.5.16.** Nếu build lỗi `Unsupported class file major version 69`
>   hoặc Lombok crash, sửa `<java.version>25</java.version>` thành `21` trong `pom.xml` — không cần gỡ JDK.
> - **Docker Desktop phải đang chạy** trước khi `docker compose up -d`.
> - **Tạo `.gitattributes` ở gốc repo trước commit đầu tiên**, nếu không Git sẽ đổi `mvnw` sang CRLF và
>   CI trên Linux sẽ không chạy được:
>   ```
>   * text=auto eol=lf
>   *.cmd text eol=crlf
>   *.bat text eol=crlf
>   ```

Làm tuần tự từ trên xuống. Mỗi mục xong đều có cách kiểm tra — đừng bỏ qua bước kiểm tra,
vì lỗi ở bước 3 mà phát hiện ở bước 7 sẽ rất khó tìm.

---

## 0. Kiểm tra máy

```bash
java -version      # cần 21+ (JDK 25 OK)
# KHÔNG cần cài Maven — dùng mvnw/mvnw.cmd sinh ra ở bước 2
node -v            # cần 20+
docker -v
git --version
```

Nếu chưa có JDK 21, cài qua SDKMAN (macOS/Linux) hoặc Adoptium Temurin (Windows).

---

## 1. Tạo thư mục gốc

```bash
mkdir ai-recruitment-agent && cd ai-recruitment-agent
git init -b main
mkdir -p docs/decisions
```

Copy vào đúng chỗ:
- `CLAUDE.md` → gốc
- `docs/SRS.md`, `docs/ROADMAP.md`, `docs/TECH_STACK.md`
- `docker-compose.yml` → gốc

---

## 2. Sinh backend Spring Boot

Cách nhanh nhất, không cần mở trình duyệt:

**PowerShell (Windows):**

```powershell
$url = "https://start.spring.io/starter.zip?type=maven-project&language=java" +
       "&bootVersion=3.5.16&javaVersion=25&groupId=com.recruitment" +
       "&artifactId=backend&name=backend&packageName=com.recruitment" +
       "&dependencies=web,security,data-jpa,validation,postgresql,flyway,mail,lombok,actuator,testcontainers"

Invoke-WebRequest -Uri $url -OutFile backend.zip
Expand-Archive backend.zip -DestinationPath backend
Remove-Item backend.zip
```

**bash (macOS/Linux):**

```bash
curl https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java \
  -d bootVersion=3.5.16 -d javaVersion=25 \
  -d groupId=com.recruitment -d artifactId=backend \
  -d name=backend -d packageName=com.recruitment \
  -d dependencies=web,security,data-jpa,validation,postgresql,flyway,mail,lombok,actuator,testcontainers \
  -o backend.zip

unzip backend.zip -d backend && rm backend.zip
```

Hoặc mở https://start.spring.io và chọn tay cùng các dependency trên.

### Thêm Spring AI vào `backend/pom.xml`

Trong `<properties>`:
```xml
<spring-ai.version>1.1.8</spring-ai.version>
```

Trong `<dependencyManagement>`:
```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-bom</artifactId>
  <version>${spring-ai.version}</version>
  <type>pom</type>
  <scope>import</scope>
</dependency>
```

Trong `<dependencies>`:
```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
<!-- đọc file CV -->
<dependency>
  <groupId>org.apache.pdfbox</groupId>
  <artifactId>pdfbox</artifactId>
  <version>3.0.3</version>
</dependency>
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
  <version>5.4.0</version>
</dependency>
<!-- JWT -->
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.6</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
<!-- Swagger UI -->
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.7.0</version>
</dependency>
```

> Số version có thể đã đổi. Trước khi chạy, nhờ Claude Code kiểm tra bản mới nhất trên Maven Central,
> hoặc chạy `mvn versions:display-dependency-updates`.

### Cấu trúc package — chia theo tính năng, không chia theo tầng

```
backend/src/main/java/com/recruitment/
├── BackendApplication.java
├── common/          config, security, exception, dto chung, audit
├── auth/            login, register, JWT filter          FR-C01
├── user/            user, candidate_profile
├── company/                                              FR-H01
├── job/             job + interview_template             FR-H02
├── rubric/                                               FR-H03
├── resume/          upload + lưu file                    FR-U01
├── jobapplication/  nộp đơn, đổi trạng thái, lịch sử     FR-U02/03/06, FR-H07
├── scoring/         ScoreAggregator (Java thuần)         FR-H05
├── notification/                                         FR-C03
├── dashboard/                                            FR-H08
└── ai/
    ├── client/      cấu hình ChatClient, EmbeddingModel
    ├── prompt/      file .st, đánh version
    ├── parsing/                                          FR-C04
    ├── criterion/                                        FR-H04
    ├── explanation/                                      FR-H06
    ├── recommendation/                                   FR-U04
    └── improvement/                                      FR-U05
```

Lý do chia theo tính năng: khi bạn nói "làm FR-H03", Claude Code chỉ cần đọc một thư mục.
Chia theo tầng (`controllers/`, `services/`, `repositories/`) buộc nó phải mở 3 thư mục cho mỗi việc,
tốn context và dễ sửa nhầm file của tính năng khác.

### Đặt file migration

```bash
mkdir -p backend/src/main/resources/db/migration
# copy V1__init_schema.sql vào đây
```

### `backend/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/recruitment
    username: recruitment
    password: recruitment
  jpa:
    hibernate:
      ddl-auto: validate      # KHÔNG dùng update — Flyway là nguồn sự thật duy nhất
    open-in-view: false
    properties:
      hibernate.format_sql: true
  flyway:
    enabled: true
    baseline-on-migrate: true
  mail:
    host: localhost
    port: 1025
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-small
    vectorstore:
      pgvector:
        initialize-schema: true
        dimensions: 1536
        index-type: HNSW
        distance-type: COSINE_DISTANCE

app:
  jwt:
    secret: ${JWT_SECRET}
    expiration-ms: 86400000
  storage:
    type: local
    local-path: ./uploads

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

Tạo `.env.example` ở gốc (và **đừng** commit `.env`):

```
ANTHROPIC_API_KEY=
OPENAI_API_KEY=
JWT_SECRET=
```

---

## 3. Chạy hạ tầng và kiểm tra migration

```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run     # Windows: cd backend; .\mvnw.cmd spring-boot:run
```

Kiểm tra:
```bash
docker exec -it recruitment-db psql -U recruitment -d recruitment -c '\dt'
```
Phải thấy đủ các bảng và bảng `flyway_schema_history`. Nếu Flyway báo lỗi, sửa file SQL rồi:
```bash
docker compose down -v && docker compose up -d    # xoá sạch volume, chạy lại
```

---

## 4. Sinh frontend

```bash
cd ..   # về gốc repo
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
npm install react-router-dom @tanstack/react-query axios react-hook-form zod @hookform/resolvers recharts
npm install -D tailwindcss @tailwindcss/vite
```

Thêm proxy vào `frontend/vite.config.ts` để gọi backend không bị CORS khi dev:

```ts
server: {
  proxy: { '/api': 'http://localhost:8080' }
}
```

Kiểm tra: `npm run dev` → mở http://localhost:5173

---

## 5. `.gitignore` ở gốc

```gitignore
# Java
backend/target/
*.class
*.jar
!**/mvnw
!**/.mvn/wrapper/*.jar

# Node
frontend/node_modules/
frontend/dist/

# Env & secrets
.env
.env.local
*.pem

# Uploads (CV thật — tuyệt đối không commit)
uploads/
backend/uploads/

# IDE
.idea/
.vscode/
*.iml
.antigravity/

# OS
.DS_Store
```

Kiểm tra trước khi commit lần đầu:
```bash
git status --short | grep -E '\.env|uploads/' && echo "CÓ FILE KHÔNG ĐƯỢC COMMIT"
```

---

## 6. Cập nhật CLAUDE.md rồi chạy /init

Điền phần Stack và Lệnh hay dùng trong `CLAUDE.md`, sau đó mở Claude Code trong Antigravity và chạy:

```
/init
```

Nó sẽ quét cấu trúc thư mục vừa tạo và bổ sung vào `CLAUDE.md`. Đọc lại phần nó thêm vào và
xoá những dòng sai — bước này quyết định chất lượng mọi phiên làm việc sau.

---

## 7. Đẩy lên GitHub

Tạo repo rỗng trên github.com (**không** tick "Add a README" — sẽ gây conflict), rồi:

```bash
cd ai-recruitment-agent

git add .
git commit -m "chore: khởi tạo dự án (Spring Boot + React + PostgreSQL/pgvector)"

git remote add origin git@github.com:<username>/ai-recruitment-agent.git
git push -u origin main
```

Nếu dùng HTTPS thay vì SSH thì đổi remote thành
`https://github.com/<username>/ai-recruitment-agent.git` và đăng nhập bằng Personal Access Token
(mật khẩu tài khoản không còn dùng được cho git push).

Có `gh` CLI thì gọn hơn:
```bash
gh repo create ai-recruitment-agent --private --source=. --remote=origin --push
```

### Ngay sau khi push

1. **Kiểm tra không lộ secret.** Vào tab Code trên GitHub, tìm `.env` và thư mục `uploads/`.
   Nếu lỡ commit API key rồi thì **revoke key đó ngay** — xoá commit không đủ, key đã nằm trong lịch sử.
2. **Bật branch protection** cho `main`: Settings → Branches → yêu cầu pull request trước khi merge.
   Với vibe code, đây là hàng rào cuối cùng ngăn một phiên AI hỏng ghi đè lên nhánh chính.
3. **Tạo `.github/workflows/ci.yml`** chạy `./mvnw test` mỗi lần push. Nhờ Claude Code viết file này.

### Quy ước nhánh từ đây

```bash
git checkout -b feat/fr-c01-auth
# ... vibe code ...
git add . && git commit -m "feat(auth): đăng ký/đăng nhập 2 role + RBAC (FR-C01)"
git push -u origin feat/fr-c01-auth
```

Một nhánh cho một mã FR. Merge xong mới sang FR tiếp theo.
