package com.recruitment.job;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.common.dto.PageResponse;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InvalidCatalogCodeException;
import com.recruitment.common.exception.InvalidJobDeadlineException;
import com.recruitment.common.exception.JobCatalogIncompleteException;
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
import java.util.Objects;
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
    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final CatalogRegistry catalogRegistry;

    public JobOwnerService(
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            RubricRepository rubricRepository,
            RubricCriterionRepository rubricCriterionRepository,
            InterviewTemplateRepository interviewTemplateRepository,
            JobEmbeddingRepository jobEmbeddingRepository,
            CatalogRegistry catalogRegistry) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.rubricRepository = rubricRepository;
        this.rubricCriterionRepository = rubricCriterionRepository;
        this.interviewTemplateRepository = interviewTemplateRepository;
        this.jobEmbeddingRepository = jobEmbeddingRepository;
        this.catalogRegistry = catalogRegistry;
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

    // So sanh title/description/categoryCode CU-MOI TRUOC khi applyRequest ghi de len entity (phai
    // luu gia tri cu ra bien rieng, applyRequest se doi truc tiep tren job) - neu MOT trong ba truong
    // nay doi, xoa job_embeddings cu (DELETE thuong, khong phai loi goi AI, an toan nam trong
    // transaction co san). Ca ba truong nay dung ghep thanh text sinh embedding (xem
    // JobEmbeddingOrchestrator.buildEmbeddingText; nganh nghe = nhan cua categoryCode, FR-C05 R-J9) -
    // vector cu se khong con phan anh dung noi dung job neu bat ky truong nao trong ba truong doi. Cot
    // category cu khong con doi qua API nen khong can so. Job quay lai trang thai "chua co embedding"
    // mot cach tu nhien, JobEmbeddingScheduler (dieu kien NOT EXISTS) tu nhat lai o lot poll ke tiep -
    // KHONG goi EmbeddingModel dong bo trong request cua HR (xem Plan Mode F1 muc B).
    @Transactional
    public JobOwnerResponse update(UUID ownerId, UUID jobId, JobRequest request) {
        Job job = loadOwned(jobId, ownerId);
        String oldTitle = job.getTitle();
        String oldDescription = job.getDescription();
        String oldCategoryCode = job.getCategoryCode();
        // FR-C05 R-J4: tinh TRUOC applyRequest. Job dang OPEN chi bi chan khi truoc da thoa R-J3 ma sau
        // khong thoa - Job OPEN cu chua chuan hoa (truoc da khong thoa) van luu duoc sua doi khac (R-J6).
        boolean catalogCompleteBefore = isCatalogComplete(job);

        applyRequest(job, request);
        if (job.getStatus() == JobStatus.OPEN && catalogCompleteBefore && !isCatalogComplete(job)) {
            // Nem trong @Transactional -> rollback, entity da bi applyRequest sua khong duoc ghi.
            throw JobCatalogIncompleteException.forOpenJobUpdate(isRemote(job));
        }
        job = jobRepository.save(job);

        boolean embeddingTextChanged = !Objects.equals(oldTitle, job.getTitle())
                || !Objects.equals(oldDescription, job.getDescription())
                || !Objects.equals(oldCategoryCode, job.getCategoryCode());
        if (embeddingTextChanged) {
            jobEmbeddingRepository.deleteByJobId(jobId);
        }

        return toResponse(job, findRubricId(job.getId()), findInterviewTemplateId(job.getId()));
    }

    // Tang recruitment_cycle CHI KHI mo lai tuyen dung mot job da CLOSED (khong phai moi lan
    // doi status bat ky) - C2 dung cot nay de cho phep ung vien cu nop lai o chu ky moi.
    @Transactional
    public JobOwnerResponse changeStatus(UUID ownerId, UUID jobId, JobStatus newStatus) {
        Job job = loadOwned(jobId, ownerId);
        JobStatus oldStatus = job.getStatus();

        // Kiem tra rubric du 100% khi MO OPEN, tru khi rubric DA KHOA (is_locked, sau lan cham dau
        // tien) - luc do HR khong con cach nao sua lai cho du 100% nen chan se ket cung HR. Job
        // chua tung OPEN thi chua co luot cham nen rubric khong the bi khoa - ap dung cho ca ba
        // duong DRAFT/PAUSED/CLOSED -> OPEN thay vi chi hai duong nhu truoc.
        if (newStatus == JobStatus.OPEN) {
            Rubric rubric =
                    rubricRepository.findByJobId(jobId).orElseThrow(() -> new RubricNotFoundException(jobId));
            if (!rubric.isLocked()) {
                requireRubricComplete(rubric);
            }
            // FR-C05 R-J3/R-J4: kiem SAU rubric (giu nguyen thu tu loi cu cho Job thieu ca hai). Ap cho
            // MOI lan chuyen sang OPEN (DRAFT/PAUSED/CLOSED) - day la duong DUY NHAT vao OPEN (create
            // luon tao DRAFT).
            if (!isCatalogComplete(job)) {
                throw JobCatalogIncompleteException.forOpening(isRemote(job));
            }
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

    private void requireRubricComplete(Rubric rubric) {
        BigDecimal total = rubricCriterionRepository.sumWeightByRubricId(rubric.getId());
        if (total.compareTo(new BigDecimal("100")) != 0) {
            throw new RubricIncompleteException(total);
        }
    }

    // FR-C05 R-J3 - MOT ham kiem duy nhat cho ca changeStatus lan update. Chi xet ma danh muc, khong
    // xet cot cu: Job "chua chuan hoa" (chi co gia tri cu) KHONG thoa.
    private static boolean isCatalogComplete(Job job) {
        return job.getCategoryCode() != null && (job.getLocationCode() != null || isRemote(job));
    }

    private static boolean isRemote(Job job) {
        return "REMOTE".equals(job.getWorkMode());
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
        // FR-C05 R-J5: kiem ma TRUOC moi setter - loi 400 khong duoc de lai entity da sua nua chung.
        // Dung chung cho create va update nen create cung tra 400 voi ma la.
        if (request.categoryCode() != null && !catalogRegistry.isIndustry(request.categoryCode())) {
            throw InvalidCatalogCodeException.industry();
        }
        if (request.locationCode() != null && !catalogRegistry.isProvince(request.locationCode())) {
            throw InvalidCatalogCodeException.province();
        }
        job.setTitle(request.title());
        job.setDescription(request.description());
        job.setRequirements(request.requirements());
        // R-J1: chi ghi ma, KHONG ghi cot category/location cu nua.
        job.setCategoryCode(request.categoryCode());
        job.setLocationCode(request.locationCode());
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
        JobCatalogFields catalog = JobCatalogFields.of(j, catalogRegistry);
        return new JobOwnerResponse(
                j.getId(),
                j.getCompanyId(),
                j.getTitle(),
                j.getDescription(),
                j.getRequirements(),
                catalog.categoryCode(),
                catalog.categoryLabel(),
                catalog.locationCode(),
                catalog.locationLabel(),
                catalog.legacyCategory(),
                catalog.legacyLocation(),
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
