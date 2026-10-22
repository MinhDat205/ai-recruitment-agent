# Walkthrough — `fix/llm-json-control-chars`

Nhánh sửa lỗi chấm điểm thất bại `LLM_INVALID_JSON` với CV DOCX có tab stop, lộ ra khi test tay trên `main` ngày
21/10/2026. Không thêm chức năng, không đổi prompt, không đổi D1 (trích xuất, `raw_text`), không đổi seed, không có
migration, không đổi frontend.

## 1. Triệu chứng

HR bấm "Chấm điểm hồ sơ" cho một đơn vị trí hành chính văn phòng. Kết quả: "Chấm điểm thất bại — `LLM_INVALID_JSON`:
AI trả về dữ liệu không đúng định dạng sau khi đã thử lại". Bấm lại thì lỗi y hệt.

## 2. Bằng chứng

**DB** (chỉ đọc):

- Đơn `ba22e3f3-…` có 3 lượt `FAILED` liên tiếp (06:35, 06:36, 06:37), cùng mã lỗi, mỗi lượt chạy khoảng 37 giây. Con
  số đó ứng với 2 lần gọi LLM trả lời đủ dài; không khớp với timeout 30 giây hay với việc output bị cắt ở trần 4096
  token (trần mặc định `AnthropicChatOptions.DEFAULT_MAX_TOKENS`, kiểm bằng `javap`).
- Cả 3 lượt đều có 0 dòng `criterion_scores`. Vòng lặp dừng ngay ở tiêu chí lỗi, nên lượt chấm hỏng ở tiêu chí đầu
  tiên, "Kinh nghiệm hành chính văn phòng".
- CV thật là **DOCX**, không phải PDF như ghi chú test tay ban đầu. `raw_text` dài 2468 ký tự, không có `"`, `\` hay
  ký tự điều khiển nào khác, nhưng có **5 ký tự TAB**. Đó là tab stop của Word, nằm giữa chức danh và mốc thời gian,
  ví dụ `Nhân viên Hành chính<TAB>03/2023 – nay`. Dòng này đúng là thứ mà tiêu chí kinh nghiệm sẽ trích dẫn.
- So trên toàn DB: **13/13 lượt DONE trên CV PDF không có tab nào**; cả 3 lượt `LLM_INVALID_JSON` đều trên CV DOCX có
  tab. Có một lượt DONE trên một CV DOCX khác cũng có tab (đơn `dbc998d7`); evidence đã lưu của lượt đó chứa tab thật,
  tức lần ấy LLM đã escape đúng thành `\t`. Lỗi phụ thuộc vào việc LLM có escape hay không, không xảy ra 100%.

**jshell trên jar thật** (jackson-databind 3.1.4; mapper giống hệt mapper mà `BeanOutputConverter` tự dựng):

```
RAW TAB:     StreamReadException | Illegal unquoted character ((CTRL-CHAR, code 9)): has to be escaped using backslash to be included in string value
ESCAPED TAB: OK
```

**Nguyên nhân.** Prompt yêu cầu trích dẫn "character for character". LLM chép nguyên văn ký tự TAB vào chuỗi JSON mà
không escape. Mapper mặc định của `BeanOutputConverter` không bật `ALLOW_UNESCAPED_CONTROL_CHARS`, nên ném
`JacksonException` ở cả 2 lần gọi, ra `LLM_INVALID_JSON` (`CriterionScoringService.java:160`).

Output thô của LLM chỉ được ghi ở `log.debug`, mà mức log mặc định là INFO, nên không có log của lần lỗi thật. Phần
xác nhận dựa trên tương quan ở DB và phép thử jshell ở trên.

## 3. Cách sửa

Đã chốt **không** chuẩn hoá `raw_text` ở D1. Làm vậy sẽ phá nguyên tắc giữ nguyên văn CV (CLAUDE.md mục 2) và làm lệch
tầng 2/2b của seed. Thay vào đó, parser của output AI được nới ra, và sửa ở **một chỗ dùng chung**:

- `ai/client/AiOutputConverters` (mới): `forType(Class<T>)` trả `new BeanOutputConverter<>(type, AI_OUTPUT_MAPPER)`.
  Mapper này **giống hệt** mapper mặc định của `BeanOutputConverter.getJsonMapper()` (kiểm bằng `javap -c`
  spring-ai-model 2.0.0: `addModules(JacksonUtils.instantiateAvailableModules())` +
  `disable(FAIL_ON_UNKNOWN_PROPERTIES)`), chỉ thêm `JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS`.
- Overload `(Class, JsonMapper)` truyền `null` cho cleaner, nên vẫn dùng `createDefaultTextCleaner()` (vẫn gỡ được
  khối ```` ```json ````). Schema, tức `{format}` trong prompt, sinh từ `JsonSchemaGenerator.generateForType(type)` và
  không phụ thuộc mapper, nên **prompt gửi đi không đổi**.
- Cả 5 chỗ tạo converter đổi sang `AiOutputConverters.forType(...)`: D1 `ResumeParsingService`, D2
  `CriterionScoringService`, D4 `ScoreExplanationService`, F2 `CvImprovementService`, và K3 `SyncAiCaller` (dùng chung
  cho C07, C08, H11, U12, U13).
- K2 `VerbatimQuoteVerifier` **không cần sửa**: `\s+` của Java khớp `[ \t\n\x0B\f\r]`, nên trích dẫn có tab vẫn khớp
  `raw_text` có tab, hoặc có dấu cách ở đúng chỗ đó.
- Chặn tái phát: `AiOutputConvertersSourceGuardTest` quét `src/main/java` và báo lỗi khi có `new BeanOutputConverter`
  ngoài `AiOutputConverters`, kể cả khi viết tên đầy đủ, có kiểu lồng nhau, hoặc dùng raw type. Quy ước mới ghi ở
  CLAUDE.md mục 3b và `stack-conventions/SKILL.md`.

| File | Vai trò |
|---|---|
| `backend/src/main/java/com/recruitment/ai/client/AiOutputConverters.java` | Mới: nơi duy nhất tạo converter cho output AI |
| `ResumeParsingService`, `CriterionScoringService`, `ScoreExplanationService`, `CvImprovementService`, `SyncAiCaller` | Mỗi file thêm 1 import, đổi 1 dòng |
| 5 payload (`CvQa`, `CvRephrase`, `JobDraft`, `JobSearchParse`, `MessageDraft`) | Chỉ sửa comment "constructor 1 tham so" |
| `CLAUDE.md` mục 3b, `.claude/skills/stack-conventions/SKILL.md` | Quy ước: converter output AI luôn tạo qua `AiOutputConverters.forType(...)` |

## 4. Test: đỏ trước, xanh sau

Test được viết trước, chạy trên code chính chưa sửa:

```
CriterionScoringServiceIntegrationTest.score_unescapedTabAndCrlfInJsonStrings_parsesVerbatimWithOneCall » LLM_INVALID_JSON
SyncAiCallerTest.call_unescapedControlCharInJsonString_returnsVerbatim_withOneCall (TAB, CRLF, LF, CR)  » AI_INVALID_OUTPUT x4
ResumeParsingServiceIntegrationTest.parse_unescapedNewlineAndTabInJsonString_parsesVerbatimWithOneCall » LLM_INVALID_JSON
Tests run: 59, Failures: 0, Errors: 6
```

Sau khi sửa: 732 test của các lớp bị ảnh hưởng đều xanh. Full suite: **2111 test xanh**, đúng bằng 2088 (lần chạy gần nhất trên `main`) + 23 test thêm mới.

**Cổng AI thật (21/10/2026)**, trần đã duyệt là Anthropic 12, OpenAI 0. Backend chạy theo lệnh `docs/DEMO.md` mục 2
(mail về MailHog), thêm các cờ tắt job embedding, parsing, cv-improvement và reparse; chỉ chấm điểm và giải thích
chạy. HR gọi `POST /api/hr/applications/ba22e3f3-…/scoring-runs` (201) cho đúng đơn CV DOCX đã FAILED 3 lần:

| Chỉ số | Kết quả |
|---|---|
| Lượt chấm `a1b1892b` | `DONE`, `error_message` rỗng, `attempt_count` 0, `criterion-score-v1`, `claude-sonnet-4-6`, 12 486 token |
| Thời gian chấm (`started_at` → `finished_at`) | 48,4 s (09:46:08 → 09:46:57 UTC) |
| `criterion_scores` | 3/3: kinh nghiệm hành chính 5,00/5 (9 evidence), tin học văn phòng 4,50/5 (5), tổ chức và giao tiếp 4,50/5 (7) |
| `total_score` (D3, backend) | 94,000 = 5/5·40 + 4,5/5·35 + 4,5/5·25 |
| Trích dẫn chứa TAB | 2, cùng ở tiêu chí kinh nghiệm: `Nhân viên Hành chính<TAB>03/2023 – nay⏎Công ty TNHH Thương mại Minh An` và `Nhân viên Lễ tân – Văn thư<TAB>07/2021 – 02/2023⏎Công ty CP Dịch vụ Sao Việt`. Cả hai nằm **nguyên văn** trong `raw_text` (`position(quote in raw_text) > 0`, so khớp thô, chưa chuẩn hoá khoảng trắng) |
| Giải thích (D4) | Có, `score-explanation-v1`, sinh lúc 09:47:11 (14 s sau khi chấm xong); summary 512 ký tự, 3 điểm mạnh, 1 điểm yếu; 0 lượt thử lại |
| Thử lại / lỗi trong log | 0 dòng "khong hop le" (DEBUG `ai.criterion` bật), 0 lỗi LLM |
| Lời gọi AI | Anthropic 4 (3 tiêu chí + 1 giải thích), OpenAI 0 |

Lượt thành công không ghi output thô của LLM, nên cổng không cho biết lần này LLM gửi TAB thô hay đã escape thành
`\t`. Điều cổng chứng minh được: trước khi sửa, đơn này hỏng 6/6 lần gọi; sau khi sửa, cả 3 tiêu chí đều qua ngay lần
đầu, và trích dẫn có TAB lưu đúng nguyên văn. Đường "TAB thô → parse được" do các test đơn vị và tích hợp ở trên khoá.
Sau cổng, demo và bộ test được nạp lại (`db/seed/README.md` mục 1 rồi 8.1), nên lượt `a1b1892b` và đơn `ba22e3f3`
(tạo qua UI, không có trong seed) không còn trong DB.

Test thêm mới, tổng 23 lần chạy:

- `AiOutputConvertersTest` (13):
  - Dương: TAB, CR, LF, CRLF thô parse đúng nguyên văn; `\t` đã escape vẫn đúng; trường thừa vẫn bị bỏ qua; khối
    ```` ```json ```` vẫn được gỡ; list lồng nhau parse đúng; `getFormat()`/`getJsonSchema()` giống hệt converter mặc
    định.
  - Đối chứng: converter mặc định vẫn ném lỗi với TAB thô.
  - Âm: chuỗi không phải JSON, object thiếu ngoặc đóng, dấu `"` thô trong chuỗi vẫn ném `JacksonException`, nên luồng
    thử lại và mã `LLM_INVALID_JSON` giữ nguyên.
- `AiOutputConvertersSourceGuardTest` (1). Đã thử đỏ bằng cách tạm thêm 3 dạng vi phạm vào `MessageDraftService`, rồi
  gỡ. Lần thử đầu, regex bỏ lọt dạng tên đầy đủ `new org.springframework.ai.converter.BeanOutputConverter<>(`; regex đã
  sửa trước khi commit.
- D2 (1): dòng `Nhân viên Hành chính<TAB>03/2023 – nay` lấy từ lỗi thật; quote qua K2 và giữ tab, chỉ 1 lần gọi LLM.
- D1 (1): `description` có LF và TAB thô; giữ nguyên văn, 1 lần gọi.
- K3 (4): TAB, CRLF, LF, CR; trả entity nguyên văn, 1 lần gọi.
- K2 (3): quote có tab khớp tab; quote có dấu cách khớp chỗ có tab; quote có tab nhưng sai năm → không khớp. Ba ca
  này xanh ngay từ đầu (K2 vốn đúng); chúng chỉ ghi nhận hành vi.

## 5. Đặc tả đã lỗi thời so với code

Ba dòng sau còn ghi "constructor 1 tham số". Theo quyết định ngày 21/10/2026, **giữ nguyên** vì là lịch sử của FR đã
hoàn thành; quy tắc hiện hành nằm ở CLAUDE.md mục 3b:

- `docs/features/CHUNG/C07/REQUIREMENT.md:355`
- `docs/features/HR/H11/REQUIREMENT.md:84`. Phần về `FAIL_ON_UNKNOWN_PROPERTIES` bị tắt vẫn đúng, vì mapper dùng chung
  giữ nguyên thiết lập đó.
- `docs/features/HR/H11/REQUIREMENT.md:535`

## 6. Nợ kỹ thuật

- **NUL thô giờ parse được, nhưng Postgres từ chối khi ghi.** `ALLOW_UNESCAPED_CONTROL_CHARS` nhận mọi ký tự dưới 32,
  kể cả `U+0000`. Nếu LLM trả NUL thô trong một trường văn bản tự do (vd `reasoning`), lỗi sẽ xảy ra lúc ghi
  TEXT/JSONB, thay vì được thử lại như trước. Rủi ro rất thấp: `raw_text` không thể chứa NUL nên trích dẫn có NUL đã
  trượt K2, và `\u0000` đã escape vốn lọt qua mapper cũ.
- **K2 dùng `\s`, không khớp NBSP (U+00A0).** Không bật `UNICODE_CHARACTER_CLASS`, nên quote có dấu cách thường ở chỗ
  `raw_text` có NBSP (hoặc ngược lại) sẽ trượt `EVIDENCE_NOT_VERIFIED`. Chưa gặp trên dữ liệu thật.
