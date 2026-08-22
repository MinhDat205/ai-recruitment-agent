package com.recruitment.ai.cvimprovement;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

// Doc lap hoan toan voi tang persistence: khong repository, khong entity, khong @Transactional -
// cung nguyen tac voi CriterionScoringService/ScoreExplanationService. Chu ky generate() CHI nhan
// hai String (van ban CV da render + van ban xu huong thi truong) - KHONG nhan entity, KHONG nhan
// repository, KHONG import bat cu thu gi tu package scoring/ (khong ScoringRunRepository,
// CriterionScoreRepository, ScoreExplanationRepository, khong CriterionScoreExplanationInput) - F2
// CO Y khong doc scoring_runs/criterion_scores/score_explanations (xem Plan Mode, rang buoc kien
// truc #1): dua reasoning/score vao prompt roi tin LLM khong nhac lai la dieu KHONG kiem chung duoc,
// rang buoc kiem chung duoc duy nhat la du lieu khong di vao prompt tu dau.
@Service
public class CvImprovementService {

    private static final Logger log = LoggerFactory.getLogger(CvImprovementService.class);

    // Gan lien ten file prompt (cv-improvement-v1.st) - khai MOT CHO DUY NHAT, dung ca khi load
    // resource ben duoi lan khi tra ve trong CvImprovementResult de Dot 4 ghi cot
    // cv_improvement_suggestions.prompt_version. Mau ScoreExplanationService.PROMPT_VERSION.
    static final String PROMPT_VERSION = "cv-improvement-v1";

    // 6 gia tri DUNG y het danh sach liet ke tuong minh trong cv-improvement-v1.st (muc "Your
    // task" -> "sectionSuggestions"). Kiem duoc tap dong nay - KHAC voi
    // ScoreExplanationService.validate() (D4) CO Y KHONG kiem noi dung tu do cua summary/point - vi
    // section la mot GIA TRI THUOC TAP DONG do CHINH TA dinh nghia (khong phai van ban tu do LLM tu
    // sinh), doi chieu duoc chinh xac tung ky tu; con "suggestion"/"reason" la van ban tu do nen chi
    // kiem khong rong/blank, KHONG kiem noi dung - cung ly do D4 da nêu (khong co can cu goc de doi
    // chieu noi dung tu do).
    static final Set<String> ALLOWED_SECTIONS = Set.of(
            "Thông tin liên hệ", "Học vấn", "Kinh nghiệm làm việc", "Kỹ năng", "Chứng chỉ", "Dự án");

    private final ChatClient chatClient;
    private final Resource promptResource;
    private final String configuredModel;

    public CvImprovementService(
            ChatClient cvImprovementChatClient,
            @Value("classpath:ai/prompt/" + PROMPT_VERSION + ".st") Resource promptResource,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}") String configuredModel) {
        this.chatClient = cvImprovementChatClient;
        this.promptResource = promptResource;
        this.configuredModel = configuredModel;
    }

    // Goi LLM DUNG MOT lan cho ca hai khoi van ban, retry DUNG 1 lan khi response khong hop le -
    // "khong hop le" gom CA JSON hong (JacksonException) LAN vi pham phat hien o validate() SAU KHI
    // da parse JSON thanh cong (ca ba list deu rong, hoac mot phan tu sai dinh dang). Ca hai loai
    // deu retry GOI LAI LLM TU DAU. Loi khac (mang, timeout, loi API...) KHONG retry, map thang ve
    // LLM_ERROR ngay lan dau - mau ScoreExplanationService.explain().
    public CvImprovementResult generate(String resumeText, String marketTrendText) {
        String userMessage = buildUserMessage(resumeText, marketTrendText);
        BeanOutputConverter<CvImprovementPayload> converter = new BeanOutputConverter<>(CvImprovementPayload.class);

        ResponseEntity<ChatResponse, CvImprovementPayload> result;
        try {
            result = callAndValidate(userMessage, converter);
        } catch (JacksonException | CvImprovementFailedException firstAttemptError) {
            log.debug("LLM tra response khong hop le o lan goi dau, thu lai lan 2", firstAttemptError);
            result = retryAfterInvalidResponse(userMessage, converter);
        } catch (RuntimeException firstAttemptError) {
            log.debug("Loi khi goi LLM (lan 1)", firstAttemptError);
            log.warn(
                    "Goi LLM that bai khi sinh goi y cai thien CV: model={}, loai={}",
                    configuredModel,
                    firstAttemptError.getClass().getSimpleName());
            throw new CvImprovementFailedException(CvImprovementErrorCode.LLM_ERROR, firstAttemptError);
        }

        return toResult(result);
    }

    private ResponseEntity<ChatResponse, CvImprovementPayload> retryAfterInvalidResponse(
            String userMessage, BeanOutputConverter<CvImprovementPayload> converter) {
        try {
            return callAndValidate(userMessage, converter);
        } catch (JacksonException secondAttemptError) {
            log.debug("LLM tra JSON khong hop le ca lan 2, dung lai", secondAttemptError);
            throw new CvImprovementFailedException(CvImprovementErrorCode.LLM_INVALID_JSON, secondAttemptError);
        } catch (CvImprovementFailedException secondAttemptError) {
            log.debug(
                    "Response van khong hop le ca lan 2, dung lai: ma={}", secondAttemptError.errorCode(), secondAttemptError);
            throw secondAttemptError;
        } catch (RuntimeException secondAttemptError) {
            log.debug("Loi khi goi LLM (lan 2)", secondAttemptError);
            log.warn(
                    "Goi LLM that bai khi sinh goi y cai thien CV: model={}, loai={}",
                    configuredModel,
                    secondAttemptError.getClass().getSimpleName());
            throw new CvImprovementFailedException(CvImprovementErrorCode.LLM_ERROR, secondAttemptError);
        }
    }

    private ResponseEntity<ChatResponse, CvImprovementPayload> callAndValidate(
            String userMessage, BeanOutputConverter<CvImprovementPayload> converter) {
        ResponseEntity<ChatResponse, CvImprovementPayload> result = callLlm(userMessage, converter);
        validate(result.entity());
        return result;
    }

    // Ca hai khoi van ban (CV + xu huong thi truong) gui chung MOT user message, dung mo ta trong
    // prompt ("given, as a separate message, two blocks of information") - System message chi con
    // huong dan tinh + {format}, giong vai tro cua criteriaText o D4.
    private ResponseEntity<ChatResponse, CvImprovementPayload> callLlm(
            String userMessage, BeanOutputConverter<CvImprovementPayload> converter) {
        return chatClient
                .prompt()
                .system(spec -> spec.text(promptResource).param("format", converter.getFormat()))
                .user(userMessage)
                .call()
                .responseEntity(converter);
    }

    private static String buildUserMessage(String resumeText, String marketTrendText) {
        return "CV cua ung vien:\n" + resumeText + "\n\nMau tin tuyen dung dang mo (toi da 20 tin):\n" + marketTrendText;
    }

    // Thu tu kiem: (1) ca ba list rong -> EMPTY_RESULT; (2) tung phan tu missingKeywords khong
    // rong/blank -> INVALID_MISSING_KEYWORD; (3) tung phan tu sectionSuggestions co section thuoc
    // ALLOWED_SECTIONS va suggestion khong rong/blank -> INVALID_SECTION; (4) tung phan tu
    // learningPath co topic VA reason khong rong/blank -> INVALID_LEARNING_PATH_ITEM. Bat ky buoc
    // nao truot deu nem ngay, khong gop chung mot ma loi cho nhieu nguyen nhan khac nhau (xem
    // comment tren ALLOWED_SECTIONS ve ly do section kiem duoc con suggestion/reason thi khong).
    private void validate(CvImprovementPayload payload) {
        if (payload.missingKeywords().isEmpty()
                && payload.sectionSuggestions().isEmpty()
                && payload.learningPath().isEmpty()) {
            throw new CvImprovementFailedException(CvImprovementErrorCode.EMPTY_RESULT);
        }
        for (String keyword : payload.missingKeywords()) {
            if (keyword == null || keyword.isBlank()) {
                throw new CvImprovementFailedException(CvImprovementErrorCode.INVALID_MISSING_KEYWORD);
            }
        }
        for (CvImprovementPayload.SectionSuggestion item : payload.sectionSuggestions()) {
            if (item.section() == null
                    || !ALLOWED_SECTIONS.contains(item.section())
                    || item.suggestion() == null
                    || item.suggestion().isBlank()) {
                throw new CvImprovementFailedException(CvImprovementErrorCode.INVALID_SECTION);
            }
        }
        for (CvImprovementPayload.LearningPathItem item : payload.learningPath()) {
            if (item.topic() == null
                    || item.topic().isBlank()
                    || item.reason() == null
                    || item.reason().isBlank()) {
                throw new CvImprovementFailedException(CvImprovementErrorCode.INVALID_LEARNING_PATH_ITEM);
            }
        }
    }

    // Cot cv_improvement_suggestions.model la fallback "gia tri thay the khi metadata thieu" - dung
    // configuredModel neu SDK khong tra kem model trong metadata, cung mau
    // ScoreExplanationService.toResult/ResumeParsingService.toResult. KHONG doc Usage/tokenUsage -
    // CvImprovementResult khong co truong do (xem comment trong record).
    private CvImprovementResult toResult(ResponseEntity<ChatResponse, CvImprovementPayload> result) {
        ChatResponse chatResponse = result.response();
        String model = chatResponse.getMetadata().getModel();
        if (model == null || model.isBlank()) {
            model = configuredModel;
        }
        return new CvImprovementResult(result.entity(), model, PROMPT_VERSION);
    }
}
