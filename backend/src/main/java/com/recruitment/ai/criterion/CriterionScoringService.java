package com.recruitment.ai.criterion;

import com.anthropic.errors.AnthropicServiceException;
import com.recruitment.rubric.ScaleLevelDescription;
import com.recruitment.scoring.RubricSnapshot;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

// Doc lap hoan toan voi tang persistence: khong repository, khong entity, khong @Transactional.
// Input la tham so truyen vao (RubricSnapshot.CriterionSnapshot + rawText), output la
// CriterionScoringResult tra ve - khong tu ghi gi vao DB. Package ai/ TUYET DOI khong import
// scoring/ScoreAggregator, khong ghi total_score - viec do la cua D3 (FR-H05), khong phai D2.
//
// MOT lan goi score() = MOT tieu chi. Cam goi gop nhieu tieu chi trong mot lan de "tiet kiem
// token" - day la vi pham truc tiep nguyen tac FR-H04 (xem srs-guard SKILL.md muc 3), khong phai
// toi uu duoc chap nhan.
@Service
public class CriterionScoringService {

    private static final Logger log = LoggerFactory.getLogger(CriterionScoringService.class);

    // Nguong do dai rawText truoc khi dua vao prompt - trung lap co chu dich voi
    // ResumeParsingService.MAX_PROMPT_CHARS (xem Q3, ke hoach D2): khong import tu package resume
    // de tranh phu thuoc cheo D1<->D2. Kiem chung evidence luon thuc hien voi rawText DAY DU (xem
    // verifyEvidence ben duoi), khong phai ban da cat - chien luoc cat giu dau+cuoi nen moi quote
    // LLM thuc su trich tu ban da cat van la substring cua ban day du.
    static final int MAX_PROMPT_CHARS = 45_000;

    private static final String TRUNCATION_MARKER = "\n\n[...phan giua CV da duoc luoc bot do do dai...]\n\n";

    // Gan lien ten file prompt (criterion-score-v1.st) - khai MOT CHO DUY NHAT, dung ca khi load
    // resource ben duoi lan khi tra ve trong CriterionScoringResult de Dot 4 ghi cot
    // scoring_runs.prompt_version.
    static final String PROMPT_VERSION = "criterion-score-v1";

    private static final List<String> VALID_SECTIONS =
            List.of("contact", "education", "experience", "skills", "certifications", "projects");

    // Thang diem mac dinh dung chung khi criterion khong co scaleDescription rieng (FR-H03: HR
    // khong bat buoc mo ta thang diem cho tung tieu chi). Dat ngay trong class nay (khong tach
    // file/resource rieng) vi chi dung o dung MOT cho - xay dung prompt - giong tinh than
    // MAX_PROMPT_CHARS/TRUNCATION_MARKER cua ResumeParsingService.
    //
    // Viet DINH TINH (thap nhat/giua/cao nhat), KHONG hardcode so cu the theo thang 1-5: max_score
    // co the khac 5 (schema chi CHECK max_score > 0, HR duoc chon bat ky so nguyen duong). %d duoc
    // dien bang chinh maxScore cua tung tieu chi truoc khi dua vao prompt (xem
    // buildScaleDescriptionText), dam bao AI noi suy diem theo dung thang toi da CUA TUNG tieu chi
    // thay vi gia dinh co dinh la 5. Day la quyet dinh anh huong truc tiep chat luong cham - da nhac
    // rieng AI "noi suy theo chat luong bang chung, khong chi dua vao co duoc nhac ten hay khong" o
    // dong cuoi de tranh cham may moc theo tan suat tu khoa.
    //
    // Menh de loai tru ngay trong cau "noi suy" (khong chi dua vao "Evidence rules" o phan sau
    // template chinh): {scaleDescription} duoc render SOM HON "Evidence rules" trong prompt cuoi
    // cung (xem criterion-score-v1.st, {scaleDescription} o dong 10-11, quy tac MUST score=0 o
    // dong sau) - neu chi dua vao quy tac o xa, mo hinh doc tuan tu se thay cau "noi suy" TRUOC khi
    // thay quy tac MUST manh nhat. Nhac lai ngay tai day, dung tin tuong thu tu doc se tu lien ket
    // dung.
    static final String DEFAULT_SCALE_DESCRIPTION_TEMPLATE =
            """
            Tiêu chí này không có thang mô tả riêng do HR cung cấp - áp dụng thang mặc định sau, \
            trải đều từ 0 đến %1$d (điểm tối đa của tiêu chí này):
            - 0 điểm: CV không có bất kỳ dấu vết nào liên quan đến tiêu chí này.
            - Gần mức thấp nhất: chỉ đề cập sơ sài, không có bằng chứng cụ thể hoặc chỉ nêu tên mà \
            không mô tả gì thêm.
            - Mức giữa: có bằng chứng cụ thể nhưng ở quy mô nhỏ, thời gian ngắn, hoặc mức độ tham \
            gia hạn chế.
            - Gần mức cao nhất: có bằng chứng rõ ràng, quy mô đáng kể, hoặc được nhắc tới nhiều lần \
            trong CV.
            - Điểm tối đa (%1$d): bằng chứng mạnh, vai trò chủ chốt, kinh nghiệm sâu và nhất quán.
            Hãy nội suy điểm số cụ thể dựa trên mức độ và chất lượng bằng chứng thực tế tìm thấy, \
            không chỉ dựa vào việc tiêu chí có được "nhắc tên" hay không - nhưng chỉ nội suy khi ĐÃ \
            có ít nhất một bằng chứng thật trong CV; nếu hoàn toàn không tìm thấy bằng chứng nào \
            liên quan, phải chấm đúng 0 điểm và để evidence rỗng, không được suy đoán hay ước \
            lượng.\
            """;

    private final ChatClient chatClient;
    private final Resource promptResource;
    private final String configuredModel;

    public CriterionScoringService(
            ChatClient criterionScoringChatClient,
            @Value("classpath:ai/prompt/" + PROMPT_VERSION + ".st") Resource promptResource,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}") String configuredModel) {
        this.chatClient = criterionScoringChatClient;
        this.promptResource = promptResource;
        this.configuredModel = configuredModel;
    }

    // Goi LLM de cham DUNG MOT tieu chi, retry DUNG 1 lan khi response khong hop le - "khong hop
    // le" gom CA JSON hong (JacksonException, xem bang chung javap ben duoi) LAN cac vi pham phat
    // hien o validate() SAU KHI da parse JSON thanh cong (score ngoai thang, evidence sai quy tac,
    // section sai, quote khong khop rawText). Ca hai loai deu retry GOI LAI LLM TU DAU (khong phai
    // validate lai chuoi cu). Loi khac (mang, timeout, loi API...) KHONG retry, map thang ve
    // LLM_ERROR ngay lan dau.
    public CriterionScoringResult score(RubricSnapshot.CriterionSnapshot criterion, String rawText) {
        String promptText = truncateForPrompt(rawText, criterion.criterionId());
        String scaleDescriptionText = buildScaleDescriptionText(criterion);
        BeanOutputConverter<CriterionScorePayload> converter = new BeanOutputConverter<>(CriterionScorePayload.class);

        ResponseEntity<ChatResponse, CriterionScorePayload> result;
        try {
            result = callAndValidate(promptText, scaleDescriptionText, criterion, rawText, converter);
        } catch (JacksonException | CriterionScoringFailedException firstAttemptError) {
            log.debug(
                    "LLM tra response khong hop le o lan goi dau, thu lai lan 2: criterionId={}",
                    criterion.criterionId(),
                    firstAttemptError);
            result = retryAfterInvalidResponse(promptText, scaleDescriptionText, criterion, rawText, converter);
        } catch (RuntimeException firstAttemptError) {
            log.debug("Loi khi goi LLM (lan 1): criterionId={}", criterion.criterionId(), firstAttemptError);
            log.warn(
                    "Goi LLM that bai cho criterionId={}, model={}, status={}, loai={}",
                    criterion.criterionId(),
                    configuredModel,
                    extractStatusCode(firstAttemptError),
                    firstAttemptError.getClass().getSimpleName());
            throw new CriterionScoringFailedException(CriterionScoringErrorCode.LLM_ERROR, firstAttemptError);
        }

        return toResult(result);
    }

    private ResponseEntity<ChatResponse, CriterionScorePayload> retryAfterInvalidResponse(
            String promptText,
            String scaleDescriptionText,
            RubricSnapshot.CriterionSnapshot criterion,
            String rawText,
            BeanOutputConverter<CriterionScorePayload> converter) {
        try {
            return callAndValidate(promptText, scaleDescriptionText, criterion, rawText, converter);
        } catch (JacksonException secondAttemptError) {
            log.debug(
                    "LLM tra JSON khong hop le ca lan 2, dung lai: criterionId={}",
                    criterion.criterionId(),
                    secondAttemptError);
            throw new CriterionScoringFailedException(CriterionScoringErrorCode.LLM_INVALID_JSON, secondAttemptError);
        } catch (CriterionScoringFailedException secondAttemptError) {
            log.debug(
                    "Response van khong hop le ca lan 2, dung lai: criterionId={}, ma={}",
                    criterion.criterionId(),
                    secondAttemptError.errorCode(),
                    secondAttemptError);
            throw secondAttemptError;
        } catch (RuntimeException secondAttemptError) {
            log.debug("Loi khi goi LLM (lan 2): criterionId={}", criterion.criterionId(), secondAttemptError);
            log.warn(
                    "Goi LLM that bai cho criterionId={}, model={}, status={}, loai={}",
                    criterion.criterionId(),
                    configuredModel,
                    extractStatusCode(secondAttemptError),
                    secondAttemptError.getClass().getSimpleName());
            throw new CriterionScoringFailedException(CriterionScoringErrorCode.LLM_ERROR, secondAttemptError);
        }
    }

    private ResponseEntity<ChatResponse, CriterionScorePayload> callAndValidate(
            String promptText,
            String scaleDescriptionText,
            RubricSnapshot.CriterionSnapshot criterion,
            String rawText,
            BeanOutputConverter<CriterionScorePayload> converter) {
        ResponseEntity<ChatResponse, CriterionScorePayload> result =
                callLlm(promptText, scaleDescriptionText, criterion, converter);
        validate(result.entity(), criterion, rawText);
        return result;
    }

    private ResponseEntity<ChatResponse, CriterionScorePayload> callLlm(
            String promptText,
            String scaleDescriptionText,
            RubricSnapshot.CriterionSnapshot criterion,
            BeanOutputConverter<CriterionScorePayload> converter) {
        return chatClient
                .prompt()
                .system(spec -> spec.text(promptResource)
                        .param("format", converter.getFormat())
                        .param("criterionName", criterion.name())
                        .param("criterionDescription", descriptionOrFallback(criterion.description()))
                        .param("maxScore", criterion.maxScore())
                        .param("scaleDescription", scaleDescriptionText))
                .user(promptText)
                .call()
                .responseEntity(converter);
    }

    // Kiem theo DUNG thu tu da chot: score trong [0, maxScore] -> evidence rong chi hop le khi
    // score = 0 -> moi section thuoc 6 gia tri -> moi quote la substring cua rawText DAY DU sau
    // chuan hoa whitespace. Bat ky buoc nao truot deu nem CriterionScoringFailedException voi dung
    // ma loi cua buoc do - noi goi (score()/retryAfterInvalidResponse()) coi day la "response
    // khong hop le", cung nhom xu ly voi JacksonException.
    private void validate(CriterionScorePayload payload, RubricSnapshot.CriterionSnapshot criterion, String rawText) {
        Double score = payload.score();
        if (score == null || score < 0 || score > criterion.maxScore()) {
            throw new CriterionScoringFailedException(CriterionScoringErrorCode.SCORE_OUT_OF_RANGE);
        }

        List<CriterionScorePayload.EvidenceQuote> evidence = payload.evidence();
        if (evidence.isEmpty() && score != 0) {
            throw new CriterionScoringFailedException(CriterionScoringErrorCode.EVIDENCE_MISSING_WITH_NONZERO_SCORE);
        }

        for (CriterionScorePayload.EvidenceQuote quote : evidence) {
            if (quote.section() == null || !VALID_SECTIONS.contains(quote.section())) {
                throw new CriterionScoringFailedException(CriterionScoringErrorCode.EVIDENCE_INVALID_SECTION);
            }
        }

        String normalizedRawText = normalizeWhitespace(rawText);
        for (CriterionScorePayload.EvidenceQuote quote : evidence) {
            if (quote.quote() == null || !normalizedRawText.contains(normalizeWhitespace(quote.quote()))) {
                throw new CriterionScoringFailedException(CriterionScoringErrorCode.EVIDENCE_NOT_VERIFIED);
            }
        }
    }

    // Gop moi chuoi khoang trang lien tiep (ke ca xuong dong, tab) thanh mot space, trim hai dau.
    // KHONG lowercase, KHONG bo dau tieng Viet - ha thap yeu cau khop theo huong do de khien mot
    // cau bia noi dung khac nhung na na lot qua, nguy hiem hon la chan nham (xem Q3, ke hoach D2).
    static String normalizeWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private String buildScaleDescriptionText(RubricSnapshot.CriterionSnapshot criterion) {
        List<ScaleLevelDescription> scale = criterion.scaleDescription();
        if (scale == null) {
            return DEFAULT_SCALE_DESCRIPTION_TEMPLATE.formatted(criterion.maxScore());
        }
        StringBuilder text = new StringBuilder("Thang điểm riêng cho tiêu chí này (do HR cung cấp):\n");
        for (ScaleLevelDescription level : scale) {
            text.append("- Mức ").append(level.level()).append(": ").append(level.description()).append('\n');
        }
        return text.toString();
    }

    private static String descriptionOrFallback(String description) {
        return (description == null || description.isBlank())
                ? "(HR không cung cấp mô tả thêm cho tiêu chí này)"
                : description;
    }

    // AnthropicServiceException (NotFoundException, RateLimitException, ...) mang HTTP status that
    // qua statusCode() va di THANG toi day khong bi boc lai - cung bang chung javap da ghi trong
    // ResumeParsingService.extractStatusCode (spring-ai 2.0.0, khong doi giua hai noi goi vi cung
    // mot phien ban SDK). Loi mang thuan (khong nhan duoc response) KHONG phai
    // AnthropicServiceException nen khong co statusCode() - instanceof la pattern match an toan, tra
    // false va roi xuong return null, KHONG bao gio nem NPE/ClassCastException tai day.
    static Integer extractStatusCode(RuntimeException error) {
        if (error instanceof AnthropicServiceException serviceException) {
            return serviceException.statusCode();
        }
        return null;
    }

    // Cot scoring_runs.model la fallback "gia tri thay the khi metadata thieu" - dung configuredModel
    // (doc tu spring.ai.anthropic.chat.options.model) neu SDK khong tra kem model trong metadata,
    // cung mau ResumeParsingService.toResult.
    private CriterionScoringResult toResult(ResponseEntity<ChatResponse, CriterionScorePayload> result) {
        ChatResponse chatResponse = result.response();
        String model = chatResponse.getMetadata().getModel();
        if (model == null || model.isBlank()) {
            model = configuredModel;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        Integer tokenUsage = usage == null ? null : usage.getTotalTokens();
        return new CriterionScoringResult(result.entity(), model, tokenUsage, PROMPT_VERSION);
    }

    // Giu dau + cuoi, cat phan giua - cung chien luoc voi ResumeParsingService.truncateForPrompt
    // (60% dau + 40% cuoi). Job nen tu chay voi input nguoi dung tai len - mot file duoc dat ten
    // "CV" nhung thuc chat la tai lieu rat dai khong duoc phep dot het quota API cho mot lan goi.
    String truncateForPrompt(String rawText, UUID criterionId) {
        if (rawText.length() <= MAX_PROMPT_CHARS) {
            return rawText;
        }
        // Chi ghi criterionId va do dai goc, KHONG ghi noi dung CV - du lieu ca nhan khong duoc vao
        // log o muc thuong.
        log.warn(
                "Cat bot rawText truoc khi goi LLM do vuot nguong: criterionId={}, doDaiGoc={}",
                criterionId,
                rawText.length());

        int keepLength = MAX_PROMPT_CHARS - TRUNCATION_MARKER.length();
        int headLength = (int) (keepLength * 0.6);
        int tailLength = keepLength - headLength;

        String head = rawText.substring(0, headLength);
        String tail = rawText.substring(rawText.length() - tailLength);
        return head + TRUNCATION_MARKER + tail;
    }
}
