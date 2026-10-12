package com.recruitment.ai.jobdraft;

import com.recruitment.ai.sync.SyncAiCaller;
import com.recruitment.ai.sync.SyncAiResult;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

// FR-H11 - goi y ba phan cua tin tuyen dung tu mo ta cua HR: ban nhap tin (JOB), tieu chi rubric (CRITERIA), cau hoi
// sang loc (QUESTIONS). Moi method = MOT lan SyncAiCaller.call (R-K3H-1), dung chung bean jobAiDraftChatClient. Doc lap
// voi tang persistence: khong repository, khong entity, khong @Transactional, KHONG tu tao luong - chay song song la
// viec cua facade (R-K3H-2). Loi K3 noi len nguyen AiSyncFailedException; facade doi thanh o FAILED theo phan.
//
// R-I2: system message = CHI file prompt cua phan + {format} (K3 tu dien) (+ {industries} o phan JOB - du lieu co dinh
// cua he thong). R-I3: mo ta cua HR CHI nam o user message, trong the <mo_ta_cua_hr>, da thay < > thanh ‹ ›. R-N4/R-N5:
// ca ba phan chi nhan mo ta goc cua HR - khong ten cong ty, khong du lieu khac, khong doc ket qua cua phan khac.
@Service
public class JobAiDraftService {

    private static final Logger log = LoggerFactory.getLogger(JobAiDraftService.class);

    // Gan lien ten file prompt - moi hang khai MOT CHO DUY NHAT.
    public static final String JOB_PROMPT_VERSION = "job-draft-job-v1";
    public static final String CRITERIA_PROMPT_VERSION = "job-draft-criteria-v1";
    public static final String QUESTIONS_PROMPT_VERSION = "job-draft-questions-v1";

    static final String INDUSTRIES_PARAM = "industries";
    // V8: AI khong duoc tra OTHER (R-P5) - danh sach dong trong prompt chi gom cac ma con lai.
    static final String EXCLUDED_INDUSTRY_CODE = "OTHER";

    static final String OPEN_TAG = "<mo_ta_cua_hr>";
    static final String CLOSE_TAG = "</mo_ta_cua_hr>";

    private final ChatClient chatClient;
    private final Resource jobPrompt;
    private final Resource criteriaPrompt;
    private final Resource questionsPrompt;
    private final SyncAiCaller syncAiCaller;

    public JobAiDraftService(
            ChatClient jobAiDraftChatClient,
            @Value("classpath:ai/prompt/" + JOB_PROMPT_VERSION + ".st") Resource jobPrompt,
            @Value("classpath:ai/prompt/" + CRITERIA_PROMPT_VERSION + ".st") Resource criteriaPrompt,
            @Value("classpath:ai/prompt/" + QUESTIONS_PROMPT_VERSION + ".st") Resource questionsPrompt,
            SyncAiCaller syncAiCaller) {
        this.chatClient = jobAiDraftChatClient;
        this.jobPrompt = jobPrompt;
        this.criteriaPrompt = criteriaPrompt;
        this.questionsPrompt = questionsPrompt;
        this.syncAiCaller = syncAiCaller;
    }

    // description: mo ta cua HR da doi CRLF -> LF va da kiem R-N2 o facade. industries: danh muc nganh theo thu tu hien
    // thi (noi goi doi tu CatalogRegistry), ma OTHER bi loai o day.
    public JobAiDraftResult<JobDraftPayload> draftJob(String description, List<IndustryOption> industries) {
        Map<String, Object> params = Map.of(INDUSTRIES_PARAM, industryLines(industries));
        return call(jobPrompt, JOB_PROMPT_VERSION, params, description, JobDraftPayload.class,
                JobDraftOutputValidators::isValidJob);
    }

    public JobAiDraftResult<CriteriaDraftPayload> draftCriteria(String description) {
        return call(criteriaPrompt, CRITERIA_PROMPT_VERSION, Map.of(), description, CriteriaDraftPayload.class,
                JobDraftOutputValidators::isValidCriteria);
    }

    public JobAiDraftResult<QuestionsDraftPayload> draftQuestions(String description) {
        return call(questionsPrompt, QUESTIONS_PROMPT_VERSION, Map.of(), description, QuestionsDraftPayload.class,
                JobDraftOutputValidators::isValidQuestions);
    }

    private <T> JobAiDraftResult<T> call(
            Resource prompt,
            String promptVersion,
            Map<String, Object> params,
            String description,
            Class<T> outputType,
            Predicate<T> validator) {
        String userMessage = buildUserMessage(description);
        log.debug("User message goi y tin tuyen dung ({}): {}", promptVersion, userMessage);
        SyncAiResult<T> result = syncAiCaller.call(chatClient, prompt, params, userMessage, outputType, validator);
        log.debug("Goi y AI tra ve ({}): {}", promptVersion, result.entity());
        return new JobAiDraftResult<>(result.entity(), result.model(), promptVersion);
    }

    // R-I3 - mo ta cua HR la DU LIEU, nam trong the co dinh; moi < > cua mo ta doi thanh ‹ › de khong tu dong the duoc.
    static String buildUserMessage(String description) {
        return OPEN_TAG + "\n" + sanitize(description) + "\n" + CLOSE_TAG;
    }

    // Moi dong "MA: ten nganh"; bo OTHER (R-P5). Chi ap cho ban gui AI.
    static String industryLines(List<IndustryOption> industries) {
        return industries.stream()
                .filter(option -> !EXCLUDED_INDUSTRY_CODE.equals(option.code()))
                .map(option -> option.code() + ": " + option.name())
                .collect(Collectors.joining("\n"));
    }

    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('<', '‹').replace('>', '›');
    }
}
