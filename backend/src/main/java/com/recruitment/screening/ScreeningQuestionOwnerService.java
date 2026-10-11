package com.recruitment.screening;

import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InvalidScreeningOrderException;
import com.recruitment.common.exception.InvalidScreeningQuestionException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.ScreeningQuestionLimitReachedException;
import com.recruitment.common.exception.ScreeningQuestionNotFoundException;
import com.recruitment.common.exception.ScreeningQuestionViolation;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.screening.dto.ScreeningQuestionListResponse;
import com.recruitment.screening.dto.ScreeningQuestionOrderRequest;
import com.recruitment.screening.dto.ScreeningQuestionRequest;
import com.recruitment.screening.dto.ScreeningQuestionResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-H10 S1-S5. Moi thao tac ghi theo DUNG thu tu R-Q10:
//   (1) kiem quyen (cong ty truoc, R-A2) -> (2) kiem dau vao (R-Q4b) -> (3) incrementRevision: cau UPDATE vua tang
//   jobs.screening_revision vua khoa hang jobs toi commit -> (4) dem/tim/kiem tap id/ghi.
// Loi o buoc 4 nem exception -> rollback ca lan tang revision (R-Q11). Loi buoc 1-2 xay ra truoc khi tang.
// Sau buoc 3 KHONG dung lai entity Job da nap o buoc 1 (R-Q10a) - cau UPDATE da clear persistence context.
@Service
public class ScreeningQuestionOwnerService {

    static final int MAX_QUESTIONS = 10;
    private static final int MAX_QUESTION_TEXT_LENGTH = 300;
    private static final int MIN_OPTIONS = 2;
    private static final int MAX_OPTIONS = 10;
    private static final int MAX_OPTION_LENGTH = 100;

    private final ScreeningQuestionRepository questionRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;

    public ScreeningQuestionOwnerService(
            ScreeningQuestionRepository questionRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository) {
        this.questionRepository = questionRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public ScreeningQuestionListResponse list(UUID ownerId, UUID jobId) {
        requireOwnedJob(ownerId, jobId);
        return toListResponse(jobId);
    }

    @Transactional
    public ScreeningQuestionResponse add(UUID ownerId, UUID jobId, ScreeningQuestionRequest request) {
        requireOwnedJob(ownerId, jobId);
        NormalizedQuestion normalized = normalize(request);

        questionRepository.incrementRevision(jobId);

        // Dem SAU khi giu khoa: transaction cho o cau UPDATE tren se thay cau vua them cua transaction truoc
        // (READ COMMITTED - moi cau lenh lay snapshot moi), nen hai tab them cung luc khong vuot 10 cau (T5).
        if (questionRepository.countByJobIdAndDeletedAtIsNull(jobId) >= MAX_QUESTIONS) {
            throw new ScreeningQuestionLimitReachedException();
        }
        ScreeningQuestion question = new ScreeningQuestion();
        question.setJobId(jobId);
        question.setDisplayOrder(questionRepository.findMaxActiveDisplayOrder(jobId) + 1);
        apply(question, normalized);
        return toResponse(questionRepository.saveAndFlush(question));
    }

    @Transactional
    public ScreeningQuestionResponse update(
            UUID ownerId, UUID jobId, UUID questionId, ScreeningQuestionRequest request) {
        requireOwnedJob(ownerId, jobId);
        NormalizedQuestion normalized = normalize(request);

        questionRepository.incrementRevision(jobId);

        ScreeningQuestion question = loadActiveQuestion(jobId, questionId);
        apply(question, normalized);
        return toResponse(questionRepository.saveAndFlush(question));
    }

    // Xoa mem (R-Q8): chi dat deleted_at, KHONG DELETE FROM - ban chup cu van tro duoc toi cau nay (R-S3).
    @Transactional
    public void delete(UUID ownerId, UUID jobId, UUID questionId) {
        requireOwnedJob(ownerId, jobId);

        questionRepository.incrementRevision(jobId);

        ScreeningQuestion question = loadActiveQuestion(jobId, questionId);
        question.setDeletedAt(Instant.now());
        questionRepository.saveAndFlush(question);
    }

    @Transactional
    public ScreeningQuestionListResponse reorder(UUID ownerId, UUID jobId, ScreeningQuestionOrderRequest request) {
        requireOwnedJob(ownerId, jobId);

        questionRepository.incrementRevision(jobId);

        // Kiem tap id SAU khi khoa: so voi tap cau dang hoat dong tai dung thoi diem ghi (R-Q6).
        List<ScreeningQuestion> active = questionRepository.findByJobIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(jobId);
        List<UUID> requestedIds = request.questionIds();
        Set<UUID> requestedSet = new HashSet<>(requestedIds);
        Map<UUID, ScreeningQuestion> activeById =
                active.stream().collect(Collectors.toMap(ScreeningQuestion::getId, Function.identity()));
        if (requestedIds.size() != active.size()
                || requestedSet.size() != requestedIds.size()
                || !activeById.keySet().equals(requestedSet)) {
            throw new InvalidScreeningOrderException();
        }

        for (int i = 0; i < requestedIds.size(); i++) {
            activeById.get(requestedIds.get(i)).setDisplayOrder(i + 1);
        }
        questionRepository.saveAllAndFlush(active);
        return toListResponse(jobId);
    }

    // Khuon da lam cung o chore/hardening (ApplicationOwnerService.loadOwnedJob): cong ty cua HR TRUOC (404
    // COMPANY_NOT_FOUND), roi Job (404 JOB_NOT_FOUND), roi Job thuoc cong ty khac -> 403 (AccessDeniedException ->
    // JsonAccessDeniedHandler). Khong tra entity Job ra ngoai: sau incrementRevision khong ai duoc dung lai no.
    private void requireOwnedJob(UUID ownerId, UUID jobId) {
        Company company = companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        if (!job.getCompanyId().equals(company.getId())) {
            throw new AccessDeniedException("Khong co quyen truy cap cau hoi sang loc cua tin tuyen dung nay");
        }
    }

    private ScreeningQuestion loadActiveQuestion(UUID jobId, UUID questionId) {
        return questionRepository
                .findByIdAndJobIdAndDeletedAtIsNull(questionId, jobId)
                .orElseThrow(ScreeningQuestionNotFoundException::new);
    }

    // R-Q3, R-Q4, R-Q4b - kiem null TRUOC khi trim (khong bao gio NullPointerException), trim TRUOC khi do do dai,
    // kiem theo dung thu tu bang R-Q4b va dung o loi dau tien. Do dai dem theo code point de khop VARCHAR cua
    // Postgres (String.length() dem UTF-16, ky tu ngoai BMP bi dem 2).
    private static NormalizedQuestion normalize(ScreeningQuestionRequest request) {
        String text = request.questionText() == null ? "" : request.questionText().strip();
        if (text.isEmpty()) {
            throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.QUESTION_TEXT_EMPTY);
        }
        if (codePoints(text) > MAX_QUESTION_TEXT_LENGTH) {
            throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.QUESTION_TEXT_TOO_LONG);
        }

        List<String> rawOptions = request.options();
        List<String> options = null;
        if (request.questionType() == ScreeningQuestionType.SINGLE_CHOICE) {
            if (rawOptions.size() < MIN_OPTIONS || rawOptions.size() > MAX_OPTIONS) {
                throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.OPTION_COUNT);
            }
            options = new ArrayList<>(rawOptions.size());
            for (String raw : rawOptions) {
                String option = raw == null ? "" : raw.strip();
                if (option.isEmpty()) {
                    throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.OPTION_EMPTY);
                }
                options.add(option);
            }
            for (String option : options) {
                if (codePoints(option) > MAX_OPTION_LENGTH) {
                    throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.OPTION_TOO_LONG);
                }
            }
            Set<String> seen = new HashSet<>();
            for (String option : options) {
                if (!seen.add(option.toLowerCase(Locale.ROOT))) {
                    throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.OPTION_DUPLICATE);
                }
            }
        } else if (!rawOptions.isEmpty()) {
            throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.OPTIONS_NOT_ALLOWED);
        }

        boolean required = Boolean.TRUE.equals(request.required());
        return new NormalizedQuestion(request.questionType(), text, options, required);
    }

    private static int codePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    private static void apply(ScreeningQuestion question, NormalizedQuestion normalized) {
        question.setQuestionType(normalized.type());
        question.setQuestionText(normalized.text());
        question.setOptions(normalized.options());
        question.setRequired(normalized.required());
    }

    private ScreeningQuestionListResponse toListResponse(UUID jobId) {
        List<ScreeningQuestionResponse> questions =
                questionRepository.findByJobIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(jobId).stream()
                        .map(ScreeningQuestionOwnerService::toResponse)
                        .toList();
        return new ScreeningQuestionListResponse(
                jobId, questionRepository.findRevision(jobId), MAX_QUESTIONS, questions);
    }

    private static ScreeningQuestionResponse toResponse(ScreeningQuestion q) {
        return new ScreeningQuestionResponse(
                q.getId(), q.getQuestionType(), q.getQuestionText(), q.getOptions(), q.isRequired(), q.getDisplayOrder());
    }

    // options = null voi SHORT_TEXT/YES_NO (khop chk_screening_question_options).
    private record NormalizedQuestion(ScreeningQuestionType type, String text, List<String> options, boolean required) {
    }
}
