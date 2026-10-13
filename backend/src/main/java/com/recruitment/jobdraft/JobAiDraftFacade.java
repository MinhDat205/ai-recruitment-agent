package com.recruitment.jobdraft;

import com.recruitment.ai.jobdraft.CriteriaDraftPayload;
import com.recruitment.ai.jobdraft.IndustryOption;
import com.recruitment.ai.jobdraft.JobAiDraftResult;
import com.recruitment.ai.jobdraft.JobAiDraftService;
import com.recruitment.ai.jobdraft.JobDraftPayload;
import com.recruitment.ai.jobdraft.QuestionsDraftPayload;
import com.recruitment.aigeneration.AiGenerationStamp;
import com.recruitment.aigeneration.AiGenerationTokenCodec;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.catalog.CatalogTextNormalizer;
import com.recruitment.common.exception.AiSyncFailedException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InvalidJobDraftRequestException;
import com.recruitment.common.exception.InvalidJobDraftSaveException;
import com.recruitment.common.exception.InvalidScreeningQuestionException;
import com.recruitment.common.exception.RubricWeightExceededException;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.dto.JobCreateRequest;
import com.recruitment.job.dto.JobOwnerResponse;
import com.recruitment.job.dto.JobRequest;
import com.recruitment.jobdraft.dto.CriteriaDraft;
import com.recruitment.jobdraft.dto.CriteriaDraft.CriterionSuggestion;
import com.recruitment.jobdraft.dto.JobAiDraftRequest;
import com.recruitment.jobdraft.dto.JobAiDraftResponse;
import com.recruitment.jobdraft.dto.JobAiDraftSaveRequest;
import com.recruitment.jobdraft.dto.JobAiDraftSaveRequest.CriterionInput;
import com.recruitment.jobdraft.dto.JobDraft;
import com.recruitment.jobdraft.dto.JobDraft.MissingField;
import com.recruitment.jobdraft.dto.PartResult;
import com.recruitment.jobdraft.dto.QuestionsDraft;
import com.recruitment.jobdraft.dto.QuestionsDraft.QuestionSuggestion;
import com.recruitment.rubric.dto.RubricCriterionRequest;
import com.recruitment.screening.ScreeningQuestionRules;
import com.recruitment.screening.dto.ScreeningQuestionRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// FR-H11 J1 - dieu phoi sinh ban nhap tin; J2 - kiem toan bo dau vao truoc transaction roi giao JobAiDraftSaveService.
// KHONG @Transactional (R-K3H-3, CLAUDE.md muc 3c): tra cong ty chay trong transaction ngan cua repository va dong truoc
// khi goi AI; transaction cua J2 nam o bean rieng JobAiDraftSaveService.
//
// Thu tu (R-A2), tat ca tren LUONG REQUEST: cong ty (404) -> description (R-N2) -> parts (R-N3) -> kiem transaction
// (R-K3H-3) -> moi phan duoc yeu cau chay tren mot virtual thread (R-K3H-2). Moi loi truoc buoc song song khong goi AI.
//
// - R-K3H-3: kiem transaction cua K3 chay tren luong goi SyncAiCaller.call - o day la virtual thread, khong thay
//   transaction cua luong request - nen facade TU kiem tren luong request, TRUOC khi tao virtual thread.
// - L3: SecurityContextHolder la ThreadLocal, khong sang virtual thread - ownerId (tu controller) truyen tham so vao
//   tung phan; luong phu khong doc SecurityContextHolder.
// - R-K3H-2: executor virtual thread tao THEO REQUEST, dong bang try-with-resources (dong = cho moi phan xong); KHONG
//   la bean Spring (C07 L9), khong ForkJoinPool chung. Loi goi ChatModel that van chay tren executor luong thuong cua K3.
// - R-K3H-5: trong mot phan CHI AiSyncFailedException doi thanh o FAILED (ma + cau co dinh); exception khac la loi lap
//   trinh, noi len thanh 500 cho ca request (khong nuot). Khong luu gi (R-L, muc 2).
@Service
public class JobAiDraftFacade {

    static final int MAX_DESCRIPTION_LENGTH = 3000;
    // J2 - Q16; bien trong so va do dai ten cung RubricCriterionRequest cua FR-H03 (b4).
    static final int MAX_SAVED_CRITERIA = 10;
    static final int MAX_CRITERION_NAME_LENGTH = 200;
    static final BigDecimal MIN_WEIGHT = new BigDecimal("0.01");
    static final BigDecimal MAX_WEIGHT = new BigDecimal("100");
    // R-P7
    static final String BENEFITS_HEADING = "\n\nQuyền lợi:\n";
    // R-P4 - "hom nay" theo gio Viet Nam, lay tu bean Clock.
    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String WORK_MODE_REMOTE = "REMOTE";

    private final CompanyRepository companyRepository;
    private final CatalogRegistry catalogRegistry;
    private final JobAiDraftService jobAiDraftService;
    private final AiGenerationTokenCodec tokenCodec;
    private final Clock clock;
    private final JobAiDraftSaveService saveService;
    // Danh muc nganh bat bien (CatalogRegistry nap mot lan) - doi sang kieu cua ai/jobdraft mot lan.
    private final List<IndustryOption> industryOptions;

    public JobAiDraftFacade(
            CompanyRepository companyRepository,
            CatalogRegistry catalogRegistry,
            JobAiDraftService jobAiDraftService,
            AiGenerationTokenCodec tokenCodec,
            Clock clock,
            JobAiDraftSaveService saveService) {
        this.companyRepository = companyRepository;
        this.catalogRegistry = catalogRegistry;
        this.jobAiDraftService = jobAiDraftService;
        this.tokenCodec = tokenCodec;
        this.clock = clock;
        this.saveService = saveService;
        this.industryOptions = catalogRegistry.industries().stream()
                .map(entry -> new IndustryOption(entry.code(), entry.label()))
                .toList();
    }

    public JobAiDraftResponse generate(UUID ownerId, JobAiDraftRequest request) {
        requireCompany(ownerId);
        String description = normalizedDescription(request.description());
        Set<JobDraftPart> parts = requestedParts(request.parts());
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Khong duoc sinh ban nhap tin bang AI khi dang co transaction - kiem tren luong request (R-K3H-3)");
        }

        Future<PartResult<JobDraft>> job;
        Future<PartResult<CriteriaDraft>> criteria;
        Future<PartResult<QuestionsDraft>> questions;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            job = submitIf(executor, parts.contains(JobDraftPart.JOB), () -> draftJob(ownerId, description));
            criteria = submitIf(executor, parts.contains(JobDraftPart.CRITERIA), () -> draftCriteria(ownerId, description));
            questions = submitIf(
                    executor, parts.contains(JobDraftPart.QUESTIONS), () -> draftQuestions(ownerId, description));
        }
        return new JobAiDraftResponse(resultOf(job), resultOf(criteria), resultOf(questions));
    }

    // ---- J2 - luu nhap ----

    // R-L4 - (1)(2)(3) da xong o filter/controller (@Valid). Day: (4) cong ty -> (5) tieu chi (R-W3) -> (6) cau hoi ->
    // (7) token K4 (R-K4-4); dung o loi dau tien, chua ghi gi. Chi khi qua het moi vao MOT transaction (R-L1).
    public JobOwnerResponse save(UUID ownerId, JobAiDraftSaveRequest request) {
        requireCompany(ownerId);
        List<RubricCriterionRequest> criteria = checkedCriteria(request.criteria());
        List<ScreeningQuestionRequest> questions = checkedQuestions(request.screeningQuestions());
        Map<JobDraftPart, AiGenerationStamp> stamps = new EnumMap<>(JobDraftPart.class);
        verifyToken(stamps, request.jobToken(), ownerId, JobDraftPart.JOB);
        verifyToken(stamps, request.criteriaToken(), ownerId, JobDraftPart.CRITERIA);
        verifyToken(stamps, request.questionsToken(), ownerId, JobDraftPart.QUESTIONS);

        JobCreateRequest job = new JobCreateRequest(withBenefits(request.job(), request.benefits()),
                request.interviewTemplate());
        return saveService.save(ownerId, new JobAiDraftSaveService.PreparedDraft(job, criteria, questions, stamps));
    }

    // R-P7 - ghep quyen loi XAC DINH vao mo ta o backend; benefits rong -> mo ta giu nguyen.
    static JobRequest withBenefits(JobRequest job, String benefits) {
        if (benefits == null || benefits.isBlank()) {
            return job;
        }
        String description = job.description().strip() + BENEFITS_HEADING + benefits.strip();
        return new JobRequest(job.title(), description, job.requirements(), job.categoryCode(), job.locationCode(),
                job.employmentType(), job.workMode(), job.salaryMin(), job.salaryMax(), job.salaryCurrency(),
                job.deadline());
    }

    // R-W3 theo thu tu: > 10 tieu chi; tung tieu chi (so thu tu 1-based): ten rong/> 200, trong so null, ngoai
    // [0.01, 100] (cung bien RubricCriterionRequest - b4), ten trung (so NGUYEN VAN sau khi strip - khop
    // uq_criterion_name_per_rubric); cuoi cung tong > 100 -> RUBRIC_WEIGHT_EXCEEDED (exception co san). L7: gia tri
    // luu cung la gia tri da strip. Tong < 100 van luu duoc (R-W4).
    static List<RubricCriterionRequest> checkedCriteria(List<CriterionInput> inputs) {
        if (inputs.size() > MAX_SAVED_CRITERIA) {
            throw InvalidJobDraftSaveException.tooManyCriteria();
        }
        List<RubricCriterionRequest> criteria = new ArrayList<>(inputs.size());
        Set<String> names = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < inputs.size(); i++) {
            int index = i + 1;
            CriterionInput input = inputs.get(i);
            String name = input == null || input.name() == null ? "" : input.name().strip();
            if (name.isEmpty()) {
                throw InvalidJobDraftSaveException.criterionNameEmpty(index);
            }
            if (name.length() > MAX_CRITERION_NAME_LENGTH) {
                throw InvalidJobDraftSaveException.criterionNameTooLong(index);
            }
            BigDecimal weight = input.weight();
            if (weight == null) {
                throw InvalidJobDraftSaveException.criterionWeightMissing(index);
            }
            if (weight.compareTo(MIN_WEIGHT) < 0 || weight.compareTo(MAX_WEIGHT) > 0) {
                throw InvalidJobDraftSaveException.criterionWeightOutOfRange(index);
            }
            if (!names.add(name)) {
                throw InvalidJobDraftSaveException.criterionNameDuplicate(index);
            }
            total = total.add(weight);
            criteria.add(new RubricCriterionRequest(name, text(input.description()), null, weight, null, i));
        }
        if (total.compareTo(MAX_WEIGHT) > 0) {
            throw new RubricWeightExceededException(total);
        }
        return criteria;
    }

    // R-L4 (6), R-Q4 - <= 10 cau; tung cau: loai null -> cau rieng (J2 khong @Valid tung phan tu), roi quy tac FR-H10
    // qua ScreeningQuestionRules (khong chep so). Gui nguyen request cho ScreeningQuestionOwnerService.add (no tu chuan
    // hoa lai); required giu dung gia tri HR chon.
    static List<ScreeningQuestionRequest> checkedQuestions(List<ScreeningQuestionRequest> questions) {
        if (questions.size() > ScreeningQuestionRules.MAX_QUESTIONS) {
            throw InvalidJobDraftSaveException.tooManyQuestions();
        }
        for (int i = 0; i < questions.size(); i++) {
            int index = i + 1;
            ScreeningQuestionRequest question = questions.get(i);
            if (question == null || question.questionType() == null) {
                throw InvalidJobDraftSaveException.questionTypeMissing(index);
            }
            try {
                ScreeningQuestionRules.normalize(question.questionType(), question.questionText(), question.options());
            } catch (InvalidScreeningQuestionException violation) {
                throw InvalidJobDraftSaveException.questionInvalid(index, violation.violation());
            }
        }
        return List.copyOf(questions);
    }

    // R-K4-4 - null = phan do khong dung; khac null thi phai hop le hoan toan (dung chu ky, typ, han, sub = HR dang goi,
    // purpose = dung o), sai -> 400 INVALID_AI_GENERATION_TOKEN, khong ghi gi. Khong co nhanh "bo qua token sai".
    private void verifyToken(
            Map<JobDraftPart, AiGenerationStamp> stamps, String token, UUID ownerId, JobDraftPart part) {
        if (token != null) {
            stamps.put(part, tokenCodec.verify(token, ownerId, part.tokenPurpose()));
        }
    }

    // ---- kiem dau vao (luong request) ----

    // R-A1 - cung cau 404 voi JobOwnerService.requireOwnCompany. Khong dua gi cua cong ty vao prompt (R-N5).
    private void requireCompany(UUID ownerId) {
        if (companyRepository.findByOwnerId(ownerId).isEmpty()) {
            throw new CompanyNotFoundException("HR chưa tạo hồ sơ công ty");
        }
    }

    // R-N2 - doi CRLF/CR -> LF roi moi do do dai (String.length()).
    static String normalizedDescription(String raw) {
        if (raw == null) {
            throw InvalidJobDraftRequestException.emptyDescription();
        }
        String description = normalizeLineBreaks(raw);
        if (description.isBlank()) {
            throw InvalidJobDraftRequestException.emptyDescription();
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw InvalidJobDraftRequestException.descriptionTooLong();
        }
        return description;
    }

    // R-N3 - null/rong -> 400; phan tu null hoac trung -> 400.
    static Set<JobDraftPart> requestedParts(List<JobDraftPart> raw) {
        if (raw == null || raw.isEmpty()) {
            throw InvalidJobDraftRequestException.noParts();
        }
        Set<JobDraftPart> parts = EnumSet.noneOf(JobDraftPart.class);
        for (JobDraftPart part : raw) {
            if (part == null || !parts.add(part)) {
                throw InvalidJobDraftRequestException.invalidParts();
            }
        }
        return parts;
    }

    // ---- chay song song (virtual thread) ----

    private static <T> Future<PartResult<T>> submitIf(
            ExecutorService executor, boolean requested, Callable<PartResult<T>> part) {
        return requested ? executor.submit(() -> runPart(part)) : null;
    }

    // R-K3H-5 - chi loi K3 thanh o FAILED; exception khac noi len (resultOf nem lai tren luong request).
    private static <T> PartResult<T> runPart(Callable<PartResult<T>> part) throws Exception {
        try {
            return part.call();
        } catch (AiSyncFailedException failure) {
            return PartResult.failed(failure.errorCode());
        }
    }

    // Goi SAU khi executor da dong (moi phan da xong) - get() khong cho.
    private static <T> PartResult<T> resultOf(Future<PartResult<T>> future) {
        if (future == null) {
            return null;
        }
        try {
            return future.get();
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Mot phan sinh ban nhap tin that bai ngoai K3", cause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bi ngat khi lay ket qua sinh ban nhap tin", interrupted);
        }
    }

    // ---- tung phan (chay tren virtual thread; ownerId truyen vao - L3) ----

    private PartResult<JobDraft> draftJob(UUID ownerId, String description) {
        JobAiDraftResult<JobDraftPayload> result = jobAiDraftService.draftJob(description, industryOptions);
        return PartResult.succeeded(toJobDraft(result.payload(), description), sign(ownerId, JobDraftPart.JOB, result));
    }

    private PartResult<CriteriaDraft> draftCriteria(UUID ownerId, String description) {
        JobAiDraftResult<CriteriaDraftPayload> result = jobAiDraftService.draftCriteria(description);
        List<CriterionSuggestion> criteria = result.payload().criteria().stream()
                .map(c -> new CriterionSuggestion(text(c.name()), text(c.description())))
                .toList();
        return PartResult.succeeded(new CriteriaDraft(criteria), sign(ownerId, JobDraftPart.CRITERIA, result));
    }

    // Cau hoi da qua ScreeningQuestionRules o validator (R-Q2) - o day lay lai ban da bo khoang trang.
    private PartResult<QuestionsDraft> draftQuestions(UUID ownerId, String description) {
        JobAiDraftResult<QuestionsDraftPayload> result = jobAiDraftService.draftQuestions(description);
        List<QuestionSuggestion> questions = result.payload().questions().stream()
                .map(q -> {
                    ScreeningQuestionRules.NormalizedQuestion normalized =
                            ScreeningQuestionRules.normalize(q.questionType(), q.questionText(), q.options());
                    List<String> options = normalized.options() == null ? List.of() : normalized.options();
                    return new QuestionSuggestion(normalized.type(), normalized.text(), options);
                })
                .toList();
        return PartResult.succeeded(new QuestionsDraft(questions), sign(ownerId, JobDraftPart.QUESTIONS, result));
    }

    // R-K4-2 - model tu metadata response (K3), promptVersion tu hang cua phan, generatedAt tu Clock. Khong luu gi.
    private String sign(UUID ownerId, JobDraftPart part, JobAiDraftResult<?> result) {
        AiGenerationStamp stamp = new AiGenerationStamp(result.model(), result.promptVersion(), clock.instant());
        return tokenCodec.issue(ownerId, part.tokenPurpose(), stamp);
    }

    // ---- R-P4 (vo hieu truong) + R-M (truong con thieu) ----

    JobDraft toJobDraft(JobDraftPayload payload, String description) {
        String categoryCode = industryCode(payload.industryCode());
        String locationCode = locationCode(payload.locationText(), description);
        String employmentType = payload.employmentType() == null ? null : payload.employmentType().name();
        String workMode = payload.workMode() == null ? null : payload.workMode().name();
        BigDecimal salaryMin = payload.salaryMin();
        BigDecimal salaryMax = payload.salaryMax();
        String salaryCurrency = (salaryMin == null && salaryMax == null) ? null : text(payload.salaryCurrency());
        LocalDate deadline = payload.deadline();
        if (deadline != null && deadline.isBefore(LocalDate.now(clock.withZone(VIETNAM)))) {
            deadline = null;
        }
        String title = text(payload.title());
        String jobDescription = text(payload.description());
        String requirements = text(payload.requirements());
        String benefits = text(payload.benefits());

        // R-M2 - dung thu tu bang.
        List<MissingField> missing = new ArrayList<>();
        addIfMissing(missing, title == null, JobDraftField.TITLE, FieldNeed.SAVE);
        addIfMissing(missing, jobDescription == null, JobDraftField.DESCRIPTION, FieldNeed.SAVE);
        addIfMissing(missing, categoryCode == null, JobDraftField.CATEGORY, FieldNeed.OPEN);
        addIfMissing(missing, locationCode == null && !WORK_MODE_REMOTE.equals(workMode),
                JobDraftField.LOCATION, FieldNeed.OPEN);
        addIfMissing(missing, employmentType == null, JobDraftField.EMPLOYMENT_TYPE, FieldNeed.OPTIONAL);
        addIfMissing(missing, workMode == null, JobDraftField.WORK_MODE, FieldNeed.OPTIONAL);
        addIfMissing(missing, salaryMin == null && salaryMax == null, JobDraftField.SALARY, FieldNeed.OPTIONAL);
        addIfMissing(missing, deadline == null, JobDraftField.DEADLINE, FieldNeed.OPTIONAL);
        addIfMissing(missing, requirements == null, JobDraftField.REQUIREMENTS, FieldNeed.OPTIONAL);
        addIfMissing(missing, benefits == null, JobDraftField.BENEFITS, FieldNeed.OPTIONAL);

        return new JobDraft(title, jobDescription, requirements, benefits, categoryCode, locationCode, employmentType,
                workMode, salaryMin, salaryMax, salaryCurrency, deadline, List.copyOf(missing));
    }

    private static void addIfMissing(List<MissingField> missing, boolean absent, JobDraftField field, FieldNeed need) {
        if (absent) {
            missing.add(new MissingField(field, need));
        }
    }

    // R-P4, R-P5 - chi kiem co/khong trong danh muc; OTHER -> null (V8).
    private String industryCode(String raw) {
        String code = text(raw);
        if (code == null
                || JobAiDraftService.EXCLUDED_INDUSTRY_CODE.equals(code)
                || !catalogRegistry.isIndustry(code)) {
            return null;
        }
        return code;
    }

    // R-P4, R-P5, Q9 - AI phai CHEP NGUYEN VAN dia danh tu mo ta cua HR: sau khi chuan hoa (bo dau, chu thuong, gop
    // khoang trang), locationText phai la chuoi con cua mo ta; roi anh xa bang matcher chinh xac co san cua FR-C05.
    // Khong so gan dung, khong tach dau phay. KHONG phai K2 (K2 la bo kiem trich dan CV) - private, khong dung chung.
    private String locationCode(String locationText, String description) {
        String location = text(locationText);
        if (location == null) {
            return null;
        }
        String normalizedLocation = CatalogTextNormalizer.normalizeText(location);
        String normalizedDescription = CatalogTextNormalizer.normalizeText(description);
        if (normalizedLocation == null
                || normalizedDescription == null
                || !normalizedDescription.contains(normalizedLocation)) {
            return null;
        }
        return catalogRegistry.matchProvince(location);
    }

    // Moi truong van ban: doi CRLF -> LF, bo khoang trang hai dau; rong -> null (R-P4).
    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = normalizeLineBreaks(value).strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String normalizeLineBreaks(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
