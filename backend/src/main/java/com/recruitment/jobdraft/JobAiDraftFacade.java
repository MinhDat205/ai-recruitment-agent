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
import com.recruitment.company.CompanyRepository;
import com.recruitment.jobdraft.dto.CriteriaDraft;
import com.recruitment.jobdraft.dto.CriteriaDraft.CriterionSuggestion;
import com.recruitment.jobdraft.dto.JobAiDraftRequest;
import com.recruitment.jobdraft.dto.JobAiDraftResponse;
import com.recruitment.jobdraft.dto.JobDraft;
import com.recruitment.jobdraft.dto.JobDraft.MissingField;
import com.recruitment.jobdraft.dto.PartResult;
import com.recruitment.jobdraft.dto.QuestionsDraft;
import com.recruitment.jobdraft.dto.QuestionsDraft.QuestionSuggestion;
import com.recruitment.screening.ScreeningQuestionRules;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// FR-H11 J1 - dieu phoi sinh ban nhap tin. KHONG @Transactional (R-K3H-3, CLAUDE.md muc 3c): tra cong ty chay trong
// transaction ngan cua repository va dong truoc khi goi AI.
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
    // R-P4 - "hom nay" theo gio Viet Nam, lay tu bean Clock.
    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String WORK_MODE_REMOTE = "REMOTE";

    private final CompanyRepository companyRepository;
    private final CatalogRegistry catalogRegistry;
    private final JobAiDraftService jobAiDraftService;
    private final AiGenerationTokenCodec tokenCodec;
    private final Clock clock;
    // Danh muc nganh bat bien (CatalogRegistry nap mot lan) - doi sang kieu cua ai/jobdraft mot lan.
    private final List<IndustryOption> industryOptions;

    public JobAiDraftFacade(
            CompanyRepository companyRepository,
            CatalogRegistry catalogRegistry,
            JobAiDraftService jobAiDraftService,
            AiGenerationTokenCodec tokenCodec,
            Clock clock) {
        this.companyRepository = companyRepository;
        this.catalogRegistry = catalogRegistry;
        this.jobAiDraftService = jobAiDraftService;
        this.tokenCodec = tokenCodec;
        this.clock = clock;
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
