package com.recruitment.rubric;

import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.RubricCriterionNotFoundException;
import com.recruitment.common.exception.RubricLockedException;
import com.recruitment.common.exception.RubricNotFoundException;
import com.recruitment.common.exception.RubricWeightExceededException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.rubric.dto.RubricCriterionRequest;
import com.recruitment.rubric.dto.RubricCriterionResponse;
import com.recruitment.rubric.dto.RubricResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RubricOwnerService {

    private static final BigDecimal MAX_TOTAL_WEIGHT = new BigDecimal("100");

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository criterionRepository;

    public RubricOwnerService(
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            RubricRepository rubricRepository,
            RubricCriterionRepository criterionRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.rubricRepository = rubricRepository;
        this.criterionRepository = criterionRepository;
    }

    public RubricResponse getRubric(UUID ownerId, UUID jobId) {
        Rubric rubric = loadOwnedRubric(jobId, ownerId);
        return toRubricResponse(rubric);
    }

    @Transactional
    public RubricCriterionResponse addCriterion(UUID ownerId, UUID jobId, RubricCriterionRequest request) {
        Rubric rubric = loadOwnedRubric(jobId, ownerId);
        requireNotLocked(rubric);

        BigDecimal currentTotal = criterionRepository.sumWeightByRubricId(rubric.getId());
        requireNotExceeding(currentTotal.add(request.weight()));

        RubricCriterion criterion = new RubricCriterion();
        criterion.setRubricId(rubric.getId());
        applyRequest(criterion, request);
        // saveAndFlush: bat INSERT no ngay tai day thay vi hoan toi luc commit, de
        // DataIntegrityViolationException cua uq_criterion_name_per_rubric noi len trong pham vi
        // request va GlobalExceptionHandler bat duoc, tra 409 xac dinh.
        criterion = criterionRepository.saveAndFlush(criterion);
        return toResponse(criterion);
    }

    @Transactional
    public RubricCriterionResponse updateCriterion(
            UUID ownerId, UUID jobId, UUID criterionId, RubricCriterionRequest request) {
        Rubric rubric = loadOwnedRubric(jobId, ownerId);
        requireNotLocked(rubric);
        RubricCriterion criterion = loadCriterion(rubric.getId(), criterionId);

        // Tru trong so cu cua chinh tieu chi nay ra khoi tong truoc khi cong trong so moi vao,
        // vi khong lam vay se luon bao "vuot 100%" ngay ca khi sua mot tieu chi da co san.
        BigDecimal currentTotal = criterionRepository.sumWeightByRubricId(rubric.getId());
        BigDecimal newTotal = currentTotal.subtract(criterion.getWeight()).add(request.weight());
        requireNotExceeding(newTotal);

        applyRequest(criterion, request);
        // saveAndFlush: cung ly do voi addCriterion - doi ten trung ten tieu chi khac cung rubric
        // cung vi pham uq_criterion_name_per_rubric.
        criterion = criterionRepository.saveAndFlush(criterion);
        return toResponse(criterion);
    }

    @Transactional
    public void deleteCriterion(UUID ownerId, UUID jobId, UUID criterionId) {
        Rubric rubric = loadOwnedRubric(jobId, ownerId);
        requireNotLocked(rubric);
        RubricCriterion criterion = loadCriterion(rubric.getId(), criterionId);
        criterionRepository.delete(criterion);
    }

    private Rubric loadOwnedRubric(UUID jobId, UUID ownerId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        Company company = requireOwnCompany(ownerId);
        if (!job.getCompanyId().equals(company.getId())) {
            // AccessDeniedException chuan cua Spring Security -> JsonAccessDeniedHandler tra 403 JSON,
            // giong pattern cua JobOwnerService.loadOwned.
            throw new AccessDeniedException("Khong co quyen truy cap rubric cua tin tuyen dung nay");
        }
        return rubricRepository.findByJobId(jobId).orElseThrow(() -> new RubricNotFoundException(jobId));
    }

    private RubricCriterion loadCriterion(UUID rubricId, UUID criterionId) {
        RubricCriterion criterion = criterionRepository
                .findById(criterionId)
                .orElseThrow(() -> new RubricCriterionNotFoundException(criterionId));
        if (!criterion.getRubricId().equals(rubricId)) {
            throw new RubricCriterionNotFoundException(criterionId);
        }
        return criterion;
    }

    private Company requireOwnCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
    }

    // Rubric khoa lai ngay khi co luot cham dau tien (is_locked=true) - moi hanh dong sua doi
    // tieu chi (them/sua/xoa) deu bi chan, muon thay doi thi phai tao rubric version moi.
    private void requireNotLocked(Rubric rubric) {
        if (rubric.isLocked()) {
            throw new RubricLockedException();
        }
    }

    private void requireNotExceeding(BigDecimal total) {
        if (total.compareTo(MAX_TOTAL_WEIGHT) > 0) {
            throw new RubricWeightExceededException(total);
        }
    }

    private void applyRequest(RubricCriterion criterion, RubricCriterionRequest request) {
        criterion.setName(request.name());
        criterion.setDescription(request.description());
        criterion.setScaleDescription(request.scaleDescription());
        criterion.setWeight(request.weight());
        // max_score/display_order co DEFAULT o DB nhung Hibernate insert gia tri tuong minh (ke ca
        // null) se ghi de default do - phai tu ap dung fallback o tang service, giong salaryCurrency
        // trong JobOwnerService.
        criterion.setMaxScore(request.maxScore() == null ? 5 : request.maxScore());
        criterion.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
    }

    private RubricResponse toRubricResponse(Rubric rubric) {
        List<RubricCriterion> criteria = criterionRepository.findByRubricIdOrderByDisplayOrderAsc(rubric.getId());
        BigDecimal totalWeight = criterionRepository.sumWeightByRubricId(rubric.getId());
        return new RubricResponse(
                rubric.getId(),
                rubric.getJobId(),
                rubric.getName(),
                rubric.isLocked(),
                totalWeight,
                criteria.stream().map(this::toResponse).toList());
    }

    private RubricCriterionResponse toResponse(RubricCriterion c) {
        return new RubricCriterionResponse(
                c.getId(),
                c.getRubricId(),
                c.getName(),
                c.getDescription(),
                c.getScaleDescription(),
                c.getWeight(),
                c.getMaxScore(),
                c.getDisplayOrder(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
