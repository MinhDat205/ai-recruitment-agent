package com.recruitment.ai.explanation;

import com.recruitment.scoring.CriterionScoreExplanationInput;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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

// Doc lap hoan toan voi tang persistence: khong repository, khong entity, khong @Transactional -
// cung nguyen tac voi CriterionScoringService (ke hoach D2). Input la
// List<CriterionScoreExplanationInput> (package scoring/, CHI la record - khong phai entity, khong
// import ResumeParsedPayload/rawText tu package resume/) + output la ScoreExplanationResult tra ve,
// khong tu ghi gi vao DB. Package ai/ TUYET DOI khong import scoring/ScoreAggregator, khong doc/ghi
// total_score - viec do la cua D3 (FR-H05), khong phai D4 (FR-H06).
//
// KHAC D2 mot diem quan trong, DOC KY truoc khi tuong day la vi pham nguyen tac "cham tung tieu chi
// rieng le" cua FR-H04: CriterionScoringService.score() nhan DUNG MOT tieu chi, MOT lan goi LLM =
// MOT tieu chi (bat buoc theo FR-H04 - cam gop nhieu tieu chi de "tiet kiem token", xem srs-guard
// SKILL.md muc 3). ScoreExplanationService.explain() o day nhan CA DANH SACH tieu chi da cham cua
// MOT luot, MOT lan goi LLM cho CA luot - day la hai bai toan khac han ban chat, khong phai cung mot
// quy tac ap dung sai cho: D2 CHAM DIEM tung tieu chi doc lap (moi tieu chi can mot danh gia rieng,
// khong bi anh huong boi cac tieu chi khac, do la ly do FR-H04 cam gop). D4 TONG HOP LAI nhung gi D2
// da cham xong thanh MOT bao cao doc duoc cho ca lot - ban chat cua tong hop la doc CA danh sach
// cung mot luc; tach rieng tung tieu chi thanh N lan goi se KHONG tong hop duoc gi ca (mat kha nang
// lien ket/so sanh giua cac tieu chi voi nhau, dung thu ma mot "bao cao tong" can co).
@Service
public class ScoreExplanationService {

    private static final Logger log = LoggerFactory.getLogger(ScoreExplanationService.class);

    // Gan lien ten file prompt (score-explanation-v1.st) - khai MOT CHO DUY NHAT, dung ca khi load
    // resource ben duoi lan khi tra ve trong ScoreExplanationResult de Dot 3 ghi cot
    // score_explanations.prompt_version. Mau CriterionScoringService.PROMPT_VERSION.
    static final String PROMPT_VERSION = "score-explanation-v1";

    private final ChatClient chatClient;
    private final Resource promptResource;
    private final String configuredModel;

    public ScoreExplanationService(
            ChatClient scoreExplanationChatClient,
            @Value("classpath:ai/prompt/" + PROMPT_VERSION + ".st") Resource promptResource,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}") String configuredModel) {
        this.chatClient = scoreExplanationChatClient;
        this.promptResource = promptResource;
        this.configuredModel = configuredModel;
    }

    // Goi LLM DUNG MOT lan cho CA danh sach tieu chi da cham cua mot luot, retry DUNG 1 lan khi
    // response khong hop le - "khong hop le" gom CA JSON hong (JacksonException) LAN cac vi pham
    // phat hien o validate() SAU KHI da parse JSON thanh cong (summary rong, criterionName khong
    // thuoc tap da gui). Ca hai loai deu retry GOI LAI LLM TU DAU. Loi khac (mang, timeout, loi
    // API...) KHONG retry, map thang ve LLM_ERROR ngay lan dau - mau CriterionScoringService.score().
    public ScoreExplanationResult explain(List<CriterionScoreExplanationInput> criteria) {
        Set<String> criterionNames =
                criteria.stream().map(CriterionScoreExplanationInput::criterionName).collect(Collectors.toSet());
        String criteriaText = buildCriteriaText(criteria);
        BeanOutputConverter<ScoreExplanationPayload> converter = new BeanOutputConverter<>(ScoreExplanationPayload.class);

        ResponseEntity<ChatResponse, ScoreExplanationPayload> result;
        try {
            result = callAndValidate(criteriaText, criterionNames, converter);
        } catch (JacksonException | ScoreExplanationFailedException firstAttemptError) {
            log.debug("LLM tra response khong hop le o lan goi dau, thu lai lan 2", firstAttemptError);
            result = retryAfterInvalidResponse(criteriaText, criterionNames, converter);
        } catch (RuntimeException firstAttemptError) {
            log.debug("Loi khi goi LLM (lan 1)", firstAttemptError);
            log.warn(
                    "Goi LLM that bai khi sinh bao cao giai thich: model={}, loai={}",
                    configuredModel,
                    firstAttemptError.getClass().getSimpleName());
            throw new ScoreExplanationFailedException(ScoreExplanationErrorCode.LLM_ERROR, firstAttemptError);
        }

        return toResult(result);
    }

    private ResponseEntity<ChatResponse, ScoreExplanationPayload> retryAfterInvalidResponse(
            String criteriaText, Set<String> criterionNames, BeanOutputConverter<ScoreExplanationPayload> converter) {
        try {
            return callAndValidate(criteriaText, criterionNames, converter);
        } catch (JacksonException secondAttemptError) {
            log.debug("LLM tra JSON khong hop le ca lan 2, dung lai", secondAttemptError);
            throw new ScoreExplanationFailedException(ScoreExplanationErrorCode.LLM_INVALID_JSON, secondAttemptError);
        } catch (ScoreExplanationFailedException secondAttemptError) {
            log.debug("Response van khong hop le ca lan 2, dung lai: ma={}", secondAttemptError.errorCode(), secondAttemptError);
            throw secondAttemptError;
        } catch (RuntimeException secondAttemptError) {
            log.debug("Loi khi goi LLM (lan 2)", secondAttemptError);
            log.warn(
                    "Goi LLM that bai khi sinh bao cao giai thich: model={}, loai={}",
                    configuredModel,
                    secondAttemptError.getClass().getSimpleName());
            throw new ScoreExplanationFailedException(ScoreExplanationErrorCode.LLM_ERROR, secondAttemptError);
        }
    }

    private ResponseEntity<ChatResponse, ScoreExplanationPayload> callAndValidate(
            String criteriaText, Set<String> criterionNames, BeanOutputConverter<ScoreExplanationPayload> converter) {
        ResponseEntity<ChatResponse, ScoreExplanationPayload> result = callLlm(criteriaText, converter);
        validate(result.entity(), criterionNames);
        return result;
    }

    // Khac CriterionScoringService.callLlm: khong co tham so rieng le nao can noi vao system
    // template (khong criterionName/maxScore don le - dau vao la CA mot danh sach). Danh sach tieu
    // chi duoc render thanh van ban roi gui qua .user(...), giong vai tro cua rawText o D2 (tai lieu
    // can tong hop, khong phai chi dan). System message chi con lai huong dan tinh + {format}.
    private ResponseEntity<ChatResponse, ScoreExplanationPayload> callLlm(
            String criteriaText, BeanOutputConverter<ScoreExplanationPayload> converter) {
        return chatClient
                .prompt()
                .system(spec -> spec.text(promptResource).param("format", converter.getFormat()))
                .user(criteriaText)
                .call()
                .responseEntity(converter);
    }

    private static String buildCriteriaText(List<CriterionScoreExplanationInput> criteria) {
        StringBuilder text = new StringBuilder();
        for (CriterionScoreExplanationInput c : criteria) {
            text.append("- Tiêu chí \"")
                    .append(c.criterionName())
                    .append("\" (trọng số ")
                    .append(c.weightSnapshot())
                    .append("%, thang điểm tối đa ")
                    .append(c.maxScoreSnapshot())
                    .append("): đạt ")
                    .append(c.score())
                    .append(" điểm. Nhận định đã chấm: ")
                    .append(c.reasoning())
                    .append('\n');
        }
        return text.toString();
    }

    // Kiem theo DUNG thu tu da chot (Q2, ke hoach D4 dot duyet lai): summary khong rong/blank -> moi
    // criterionName trong strengths/weaknesses phai thuoc DUNG tap ten tieu chi da gui cho LLM (tap
    // dong). Bat ky buoc nao truot deu nem ScoreExplanationFailedException voi dung ma loi cua buoc
    // do - noi goi (explain()/retryAfterInvalidResponse()) coi day la "response khong hop le", cung
    // nhom xu ly voi JacksonException.
    //
    // CO Y KHONG kiem gi khac len noi dung tu do cua summary/point (khong cam ky tu ngoac kep, khong
    // gioi han do dai) - ca hai deu DA CAN NHAC va DA LOAI (xem ke hoach D4, dot duyet lai Q2): ca
    // hai chi bat duoc HINH THUC cua van ban (co ky tu ngoac kep hay khong, dai hay ngan), khong bat
    // duoc HANH VI can chan (co phai noi dung chep/dien giai lai tu CV hay khong). D2 kiem chung
    // duoc evidence vi co rawText lam CAN CU DOI CHIEU that (so khop substring) - D4 CO Y KHONG co
    // rawText (khong doc lai CV, xem CriterionScoreExplanationInput) nen khong co can cu goc nao de
    // doi chieu noi dung tu do. Day la GIOI HAN DA BIET cua thiet ke D4, khong phai lo sot - ghi ra
    // thay vi dung mot phep kiem trong ra co ve chat che nhung thuc chat bat sai thu.
    private void validate(ScoreExplanationPayload payload, Set<String> criterionNames) {
        if (payload.summary() == null || payload.summary().isBlank()) {
            throw new ScoreExplanationFailedException(ScoreExplanationErrorCode.SUMMARY_BLANK);
        }
        for (ScoreExplanationPayload.CriterionPoint point : payload.strengths()) {
            if (point.criterionName() == null || !criterionNames.contains(point.criterionName())) {
                throw new ScoreExplanationFailedException(ScoreExplanationErrorCode.CRITERION_NAME_MISMATCH);
            }
        }
        for (ScoreExplanationPayload.CriterionPoint point : payload.weaknesses()) {
            if (point.criterionName() == null || !criterionNames.contains(point.criterionName())) {
                throw new ScoreExplanationFailedException(ScoreExplanationErrorCode.CRITERION_NAME_MISMATCH);
            }
        }
    }

    // Cot score_explanations.model la fallback "gia tri thay the khi metadata thieu" - dung
    // configuredModel (doc tu spring.ai.anthropic.chat.options.model) neu SDK khong tra kem model
    // trong metadata, cung mau CriterionScoringService.toResult/ResumeParsingService.toResult.
    private ScoreExplanationResult toResult(ResponseEntity<ChatResponse, ScoreExplanationPayload> result) {
        ChatResponse chatResponse = result.response();
        String model = chatResponse.getMetadata().getModel();
        if (model == null || model.isBlank()) {
            model = configuredModel;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        Integer tokenUsage = usage == null ? null : usage.getTotalTokens();
        return new ScoreExplanationResult(result.entity(), model, tokenUsage, PROMPT_VERSION);
    }
}
