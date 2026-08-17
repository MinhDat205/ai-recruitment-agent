package com.recruitment.resume;

import com.anthropic.errors.AnthropicServiceException;
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

@Service
public class ResumeParsingService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParsingService.class);

    // Nguong do dai rawText truoc khi dua vao prompt goi LLM. Khong gioi han o TextExtractor
    // (Luot 2) vi do la tang trich xuat, phai day du de luu DB/audit - cat chi ap dung cho BAN SAO
    // gui LLM, raw_text luu DB van la ban day du.
    static final int MAX_PROMPT_CHARS = 45_000;

    private static final String TRUNCATION_MARKER = "\n\n[...phan giua CV da duoc luoc bot do do dai...]\n\n";

    // Gan lien ten file prompt (resume-parse-v1.st) - khai MOT CHO DUY NHAT, dung ca khi load
    // resource ben duoi lan khi tra ve trong ResumeParsingResult de tang goi (Luot 4) ghi cot
    // resume_parsed_data.prompt_version - khong rai chuoi "v1" o noi khac.
    static final String PROMPT_VERSION = "resume-parse-v1";

    private final ChatClient chatClient;
    private final Resource promptResource;
    private final String configuredModel;

    public ResumeParsingService(
            ChatClient resumeParsingChatClient,
            @Value("classpath:ai/prompt/" + PROMPT_VERSION + ".st") Resource promptResource,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}") String configuredModel) {
        this.chatClient = resumeParsingChatClient;
        this.promptResource = promptResource;
        this.configuredModel = configuredModel;
    }

    // Goi LLM de trich xuat du lieu co cau truc tu rawText, retry DUNG 1 lan khi loi la JSON
    // khong hop le. Da xac nhan qua bytecode that (khong doan ten class):
    // BeanOutputConverter.convert(String) goi thang jsonMapper.readValue(...) khong bat exception
    // nao, va DefaultChatClient.doResponseEntity() cung goi thang converter.convert(...) khong
    // bat gi them - nen loi JSON khong hop le se la tools.jackson.core.JacksonException (unchecked,
    // extends RuntimeException) nem thang ra khoi .call().responseEntity(converter) khong bi boc
    // lai. Loi khac (mang, timeout SDK...) khong retry, map thang ve LLM_ERROR - hien chua xac
    // dinh duoc chinh xac loai exception timeout tu SDK Anthropic thuc te (chi mock o tang
    // ChatModel trong test nen chua cham toi SDK that), nen LLM_TIMEOUT hien khong co duong code
    // nao tao ra duoc - ghi nhan la gioi han da biet, khong tu doan de tranh bat sai loai exception.
    public ResumeParsingResult parse(UUID resumeId, String rawText) {
        String promptText = truncateForPrompt(rawText, resumeId);
        BeanOutputConverter<ResumeParsedPayload> converter = new BeanOutputConverter<>(ResumeParsedPayload.class);

        ResponseEntity<ChatResponse, ResumeParsedPayload> result;
        try {
            result = callLlm(promptText, converter);
        } catch (JacksonException firstAttemptError) {
            log.debug(
                    "LLM tra JSON khong hop le o lan goi dau, thu lai lan 2: resumeId={}",
                    resumeId,
                    firstAttemptError);
            result = retryAfterInvalidJson(resumeId, promptText, converter);
        } catch (RuntimeException firstAttemptError) {
            log.debug("Loi khi goi LLM (lan 1): resumeId={}", resumeId, firstAttemptError);
            log.warn(
                    "Goi LLM that bai cho resumeId={}, model={}, status={}, loai={}",
                    resumeId,
                    configuredModel,
                    extractStatusCode(firstAttemptError),
                    firstAttemptError.getClass().getSimpleName());
            throw new ResumeParsingFailedException(ResumeParsingErrorCode.LLM_ERROR, firstAttemptError);
        }

        return toResult(result);
    }

    private ResponseEntity<ChatResponse, ResumeParsedPayload> retryAfterInvalidJson(
            UUID resumeId, String promptText, BeanOutputConverter<ResumeParsedPayload> converter) {
        try {
            return callLlm(promptText, converter);
        } catch (JacksonException secondAttemptError) {
            log.debug("LLM tra JSON khong hop le ca lan 2, dung lai: resumeId={}", resumeId, secondAttemptError);
            throw new ResumeParsingFailedException(ResumeParsingErrorCode.LLM_INVALID_JSON, secondAttemptError);
        } catch (RuntimeException secondAttemptError) {
            log.debug("Loi khi goi LLM (lan 2): resumeId={}", resumeId, secondAttemptError);
            log.warn(
                    "Goi LLM that bai cho resumeId={}, model={}, status={}, loai={}",
                    resumeId,
                    configuredModel,
                    extractStatusCode(secondAttemptError),
                    secondAttemptError.getClass().getSimpleName());
            throw new ResumeParsingFailedException(ResumeParsingErrorCode.LLM_ERROR, secondAttemptError);
        }
    }

    // AnthropicServiceException (NotFoundException, RateLimitException, ...) mang HTTP status
    // that qua statusCode() va di THANG toi day khong bi boc lai - xac nhan qua javap:
    // khong Exception table nao bao quanh loi goi mang trong AnthropicChatModel.lambda$internalCall$14,
    // DefaultCallResponseSpec, DefaultChatClientUtils (spring-ai 2.0.0). Loi mang thuan (khong
    // nhan duoc response - vd AnthropicIoException, timeout socket) KHONG phai AnthropicServiceException
    // nen khong co statusCode() - `instanceof` la pattern match an toan (khac ep kieu (X) e), tra
    // false va roi xuong return null, KHONG bao gio nem NPE/ClassCastException tai day - xem test
    // extractStatusCode_notAnthropicServiceException_returnsNullWithoutThrowing.
    // Goi tu package-private (khong private) de test truc tiep, giong quy uoc cua truncateForPrompt.
    static Integer extractStatusCode(RuntimeException error) {
        if (error instanceof AnthropicServiceException serviceException) {
            return serviceException.statusCode();
        }
        return null;
    }

    private ResponseEntity<ChatResponse, ResumeParsedPayload> callLlm(
            String promptText, BeanOutputConverter<ResumeParsedPayload> converter) {
        return chatClient
                .prompt()
                .system(spec -> spec.text(promptResource).param("format", converter.getFormat()))
                .user(promptText)
                .call()
                .responseEntity(converter);
    }

    // Cot resume_parsed_data.model la NOT NULL nhung SDK khong dam bao luon dien
    // ChatResponseMetadata.model - dung configuredModel (model doc tu
    // spring.ai.anthropic.chat.options.model, cung property key AnthropicChatModelConfig dang
    // doc, cung gia tri dung de ghi log chan doan o cac nhanh loi ben tren) thay the de khong lam
    // vo insert DB chi vi thieu 1 field metadata phu, quan trong hon nhieu la ket qua parse chinh
    // no. Day la usage DUNG nghia "fallback" (thay the khi metadata thieu) - khac voi nhanh loi
    // LLM, noi configuredModel la model DA GOI, khong phai model du phong.
    private ResumeParsingResult toResult(ResponseEntity<ChatResponse, ResumeParsedPayload> result) {
        ChatResponse chatResponse = result.response();
        String model = chatResponse.getMetadata().getModel();
        if (model == null || model.isBlank()) {
            model = configuredModel;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        Integer tokenUsage = usage == null ? null : usage.getTotalTokens();
        return new ResumeParsingResult(result.entity(), model, tokenUsage, PROMPT_VERSION);
    }

    // Giu dau + cuoi, cat phan giua - KHONG cat cut duoi: muc "Du an" va "Chung chi" (2/6 khoi
    // trong schema) gan nhu luon nam o cuoi CV, cat cut duoi se mat dung hai khoi do. 60% dau
    // (lien he, hoc van, kinh nghiem - thuong nam truoc) + 40% cuoi (du an, chung chi - thuong nam
    // sau). Job nen tu chay voi input nguoi dung tai len - mot file duoc dat ten "CV" nhung thuc
    // chat la tai lieu rat dai (vd luan van) khong duoc phep dot het quota API cho mot lan goi.
    String truncateForPrompt(String rawText, UUID resumeId) {
        if (rawText.length() <= MAX_PROMPT_CHARS) {
            return rawText;
        }
        // Chi ghi resumeId va do dai goc, KHONG ghi noi dung CV - du lieu ca nhan khong duoc vao
        // log o muc thuong.
        log.warn("Cat bot rawText truoc khi goi LLM do vuot nguong: resumeId={}, doDaiGoc={}", resumeId, rawText
                .length());

        int keepLength = MAX_PROMPT_CHARS - TRUNCATION_MARKER.length();
        int headLength = (int) (keepLength * 0.6);
        int tailLength = keepLength - headLength;

        String head = rawText.substring(0, headLength);
        String tail = rawText.substring(rawText.length() - tailLength);
        return head + TRUNCATION_MARKER + tail;
    }
}
