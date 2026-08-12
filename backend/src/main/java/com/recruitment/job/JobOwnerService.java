package com.recruitment.job;

import com.recruitment.common.dto.PageResponse;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InvalidJobDeadlineException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.RubricIncompleteException;
import com.recruitment.common.exception.RubricNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.interviewtemplate.InterviewTemplate;
import com.recruitment.interviewtemplate.InterviewTemplateRepository;
import com.recruitment.interviewtemplate.dto.InterviewTemplateRequest;
import com.recruitment.job.dto.JobCreateRequest;
import com.recruitment.job.dto.JobOwnerResponse;
import com.recruitment.job.dto.JobRequest;
import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricCriterionRepository;
import com.recruitment.rubric.RubricRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobOwnerService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final InterviewTemplateRepository interviewTemplateRepository;

    public JobOwnerService(
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            RubricRepository rubricRepository,
            RubricCriterionRepository rubricCriterionRepository,
            InterviewTemplateRepository interviewTemplateRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.rubricRepository = rubricRepository;
        this.rubricCriterionRepository = rubricCriterionRepository;
        this.interviewTemplateRepository = interviewTemplateRepository;
    }

    // Job, Rubric va InterviewTemplate phai duoc tao cung mot transaction: khong duoc ton tai
    // duong code nao tao ra Job ma thieu Rubric hoac thieu Mau giay moi phong van di kem
    // (kiem chung o test). FR-H02: "khi dang tin, HR dong thoi tao Mau Giay moi Phong van".
    @Transactional
    public JobOwnerResponse create(UUID ownerId, JobCreateRequest request) {
        Company company = requireOwnCompany(ownerId);

        Job job = new Job();
        job.setCompanyId(company.getId());
        job.setCreatedBy(ownerId);
        job.setStatus(JobStatus.DRAFT);
        job.setRecruitmentCycle(1);
        applyRequest(job, request.job());
        job = jobRepository.save(job);

        Rubric rubric = new Rubric();
        rubric.setJobId(job.getId());
        rubric.setLocked(false);
        rubric = rubricRepository.save(rubric);

        InterviewTemplate template = new InterviewTemplate();
        template.setJobId(job.getId());
        // company_name la anh chup ten cong ty tai thoi diem tao, khong doi theo ho so cong ty
        // ve sau - giong tinh than rubric_snapshot/weight_snapshot da dung trong scoring_runs.
        template.setCompanyName(company.getName());
        applyInterviewTemplateRequest(template, request.interviewTemplate());
        template = interviewTemplateRepository.save(template);

        return toResponse(job, rubric.getId(), template.getId());
    }

    public PageResponse<JobOwnerResponse> listMine(UUID ownerId, JobStatus status, Integer page, Integer size) {
        Company company = requireOwnCompany(ownerId);
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));

        Page<Job> jobPage = status == null
                ? jobRepository.findByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDesc(company.getId(), pageable)
                : jobRepository.findByCompanyIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(
                        company.getId(), status, pageable);

        return PageResponse.from(
                jobPage, job -> toResponse(job, findRubricId(job.getId()), findInterviewTemplateId(job.getId())));
    }

    public JobOwnerResponse getMine(UUID ownerId, UUID jobId) {
        Job job = loadOwned(jobId, ownerId);
        return toResponse(job, findRubricId(job.getId()), findInterviewTemplateId(job.getId()));
    }

    @Transactional
    public JobOwnerResponse update(UUID ownerId, UUID jobId, JobRequest request) {
        Job job = loadOwned(jobId, ownerId);
        applyRequest(job, request);
        job = jobRepository.save(job);
        return toResponse(job, findRubricId(job.getId()), findInterviewTemplateId(job.getId()));
    }

    // Tang recruitment_cycle CHI KHI mo lai tuyen dung mot job da CLOSED (khong phai moi lan
    // doi status bat ky) - C2 dung cot nay de cho phep ung vien cu nop lai o chu ky moi.
    @Transactional
    public JobOwnerResponse changeStatus(UUID ownerId, UUID jobId, JobStatus newStatus) {
        Job job = loadOwned(jobId, ownerId);
        JobStatus oldStatus = job.getStatus();

        // Chi kiem tra khi MO MOI (DRAFT) hoac MO LAI sau khi da CLOSED - hai truong hop nay rubric
        // co the da bi sua sau lan cuoi kiem tra. KHONG kiem PAUSED -> OPEN: day chi la tam dung
        // trong cung dot tuyen dung, rubric co the da bi khoa (is_locked, sau lan cham dau tien)
        // nen HR khong con cach nao sua lai cho du 100% - chan ca truong hop nay se ket cung HR.
        if ((oldStatus == JobStatus.DRAFT || oldStatus == JobStatus.CLOSED) && newStatus == JobStatus.OPEN) {
            requireRubricComplete(job.getId());
        }
        if (oldStatus == JobStatus.CLOSED && newStatus == JobStatus.OPEN) {
            job.setRecruitmentCycle(job.getRecruitmentCycle() + 1);
        }
        if (newStatus == JobStatus.OPEN && job.getPublishedAt() == null) {
            job.setPublishedAt(Instant.now());
        }
        job.setStatus(newStatus);
        job = jobRepository.save(job);
        return toResponse(job, findRubricId(job.getId()), findInterviewTemplateId(job.getId()));
    }

    // Xoa mem: chi set deleted_at, KHONG DELETE FROM jobs - hang phai con nguyen sau khi xoa.
    @Transactional
    public void delete(UUID ownerId, UUID jobId) {
        Job job = loadOwned(jobId, ownerId);
        job.setDeletedAt(Instant.now());
        jobRepository.save(job);
    }

    private Company requireOwnCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
    }

    private Job loadOwned(UUID jobId, UUID ownerId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        Company company = requireOwnCompany(ownerId);
        if (!job.getCompanyId().equals(company.getId())) {
            // AccessDeniedException chuan cua Spring Security -> JsonAccessDeniedHandler tra 403 JSON,
            // giong pattern cua CompanyOwnerService.
            throw new AccessDeniedException("Khong co quyen truy cap tin tuyen dung nay");
        }
        return job;
    }

    // Chi kiem tra khi mo tin (DRAFT -> OPEN): rubric chua du 100% trong so thi khong duoc mo,
    // vi AI se cham diem theo rubric nay ngay khi co ung vien nop don (D2 phu thuoc B3).
    private void requireRubricComplete(UUID jobId) {
        Rubric rubric = rubricRepository.findByJobId(jobId).orElseThrow(() -> new RubricNotFoundException(jobId));
        BigDecimal total = rubricCriterionRepository.sumWeightByRubricId(rubric.getId());
        if (total.compareTo(new BigDecimal("100")) != 0) {
            throw new RubricIncompleteException(total);
        }
    }

    private UUID findRubricId(UUID jobId) {
        return rubricRepository.findByJobId(jobId).map(Rubric::getId).orElse(null);
    }

    private UUID findInterviewTemplateId(UUID jobId) {
        return interviewTemplateRepository.findByJobId(jobId).map(InterviewTemplate::getId).orElse(null);
    }

    private void applyInterviewTemplateRequest(InterviewTemplate template, InterviewTemplateRequest request) {
        template.setSubject(request.subject());
        template.setBody(request.body());
        template.setSenderName(request.senderName());
        template.setSenderTitle(request.senderTitle());
        template.setAddress(request.address());
    }

    private void applyRequest(Job job, JobRequest request) {
        if (request.deadline() != null && request.deadline().isBefore(LocalDate.now())) {
            throw new InvalidJobDeadlineException();
        }
        job.setTitle(request.title());
        job.setDescription(request.description());
        job.setRequirements(request.requirements());
        job.setCategory(request.category());
        job.setLocation(request.location());
        job.setEmploymentType(request.employmentType());
        job.setWorkMode(request.workMode());
        job.setSalaryMin(request.salaryMin());
        job.setSalaryMax(request.salaryMax());
        // salaryCurrency co DEFAULT 'VND' o DB, nhung Hibernate insert gia tri tuong minh (ke ca null)
        // se ghi de default do - phai tu ap dung fallback o tang service.
        job.setSalaryCurrency(request.salaryCurrency() == null ? "VND" : request.salaryCurrency());
        job.setDeadline(request.deadline());
    }

    private int safePage(Integer page) {
        return (page == null || page < 0) ? 0 : page;
    }

    private int safeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private JobOwnerResponse toResponse(Job j, UUID rubricId, UUID interviewTemplateId) {
        return new JobOwnerResponse(
                j.getId(),
                j.getCompanyId(),
                j.getTitle(),
                j.getDescription(),
                j.getRequirements(),
                j.getCategory(),
                j.getLocation(),
                j.getEmploymentType(),
                j.getWorkMode(),
                j.getSalaryMin(),
                j.getSalaryMax(),
                j.getSalaryCurrency(),
                j.getStatus(),
                j.getRecruitmentCycle(),
                j.getDeadline(),
                j.getPublishedAt(),
                j.getDeletedAt(),
                j.getCreatedAt(),
                j.getUpdatedAt(),
                rubricId,
                interviewTemplateId);
    }
}
