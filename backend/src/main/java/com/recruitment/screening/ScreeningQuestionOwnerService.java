package com.recruitment.screening;

import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InvalidScreeningOrderException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.ScreeningQuestionLimitReachedException;
import com.recruitment.common.exception.ScreeningQuestionNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.screening.dto.ScreeningQuestionListResponse;
import com.recruitment.screening.dto.ScreeningQuestionOrderRequest;
import com.recruitment.screening.dto.ScreeningQuestionRequest;
import com.recruitment.screening.dto.ScreeningQuestionResponse;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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

    static final int MAX_QUESTIONS = ScreeningQuestionRules.MAX_QUESTIONS;

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

    // R-Q3, R-Q4, R-Q4b - quy tac kiem nam o ScreeningQuestionRules (FR-H11 Q15, dung chung); o day chi them required.
    private static NormalizedQuestion normalize(ScreeningQuestionRequest request) {
        ScreeningQuestionRules.NormalizedQuestion checked = ScreeningQuestionRules.normalize(
                request.questionType(), request.questionText(), request.options());
        boolean required = Boolean.TRUE.equals(request.required());
        return new NormalizedQuestion(checked.type(), checked.text(), checked.options(), required);
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
