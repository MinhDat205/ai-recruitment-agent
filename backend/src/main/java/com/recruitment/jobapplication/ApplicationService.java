package com.recruitment.jobapplication;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.ApplicationNotWithdrawableException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobCatalogFields;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.dto.ApplicationCandidateDetailResponse;
import com.recruitment.jobapplication.dto.ApplicationCandidateDetailResponse.JobAvailability;
import com.recruitment.jobapplication.dto.ApplicationCreateRequest;
import com.recruitment.jobapplication.dto.ApplicationHistoryEntryResponse;
import com.recruitment.jobapplication.dto.ApplicationResponse;
import com.recruitment.jobapplication.dto.ApplicationSummaryResponse;
import com.recruitment.notification.ApplicationSubmittedEvent;
import com.recruitment.notification.ApplicationWithdrawnEvent;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final ApplicationStatusRecorder applicationStatusRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final CompanyRepository companyRepository;
    private final CatalogRegistry catalogRegistry;

    public ApplicationService(
            JobApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository statusHistoryRepository,
            JobRepository jobRepository,
            ResumeRepository resumeRepository,
            ApplicationStatusRecorder applicationStatusRecorder,
            ApplicationEventPublisher eventPublisher,
            CompanyRepository companyRepository,
            CatalogRegistry catalogRegistry) {
        this.applicationRepository = applicationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.jobRepository = jobRepository;
        this.resumeRepository = resumeRepository;
        this.applicationStatusRecorder = applicationStatusRecorder;
        this.eventPublisher = eventPublisher;
        this.companyRepository = companyRepository;
        this.catalogRegistry = catalogRegistry;
    }

    @Transactional
    public ApplicationResponse apply(UUID candidateId, ApplicationCreateRequest request) {
        // findOpenJobById da loc status='OPEN' AND deleted_at IS NULL AND deadline chua qua -
        // job khong thoa (vd DRAFT) coi nhu khong ton tai voi ung vien, giong het public browsing.
        Job job = jobRepository
                .findOpenJobById(request.jobId())
                .orElseThrow(() -> new JobNotFoundException(request.jobId()));

        // findByIdAndCandidateId: CV khong thuoc ve candidate dang dang nhap -> 404, khong tin
        // resumeId tu client.
        Resume resume = resumeRepository
                .findByIdAndCandidateId(request.resumeId(), candidateId)
                .orElseThrow(() -> new ResumeNotFoundException(request.resumeId()));

        JobApplication application = new JobApplication();
        application.setJobId(job.getId());
        application.setCandidateId(candidateId);
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(job.getRecruitmentCycle());
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(request.aiConsent());
        application.setAiConsentAt(Instant.now());
        application.setCoverLetter(request.coverLetter());

        // saveAndFlush: bat INSERT no ngay tai day thay vi hoan toi luc commit, de
        // DataIntegrityViolationException cua uq_application_per_cycle noi len trong pham vi
        // request va GlobalExceptionHandler bat duoc, tra 409 xac dinh.
        JobApplication saved = applicationRepository.saveAndFlush(application);

        // Dong lich su dau tien cua don: NULL -> PENDING, changed_by = chinh candidate (tu tao
        // don). Cung transaction voi viec tao don (goi bean khac, khong phai self-invocation).
        applicationStatusRecorder.record(saved.getId(), null, ApplicationStatus.PENDING, candidateId, null);

        // FR-C03: publish TRONG transaction chinh - NotificationEventListener xu ly sau
        // AFTER_COMMIT, nguoi nhan la HR so huu cong ty cua job (suy tu jobId o listener).
        eventPublisher.publishEvent(new ApplicationSubmittedEvent(saved.getId(), job.getId(), candidateId));

        return toResponse(saved);
    }

    @Transactional
    public ApplicationResponse withdraw(UUID candidateId, UUID applicationId) {
        // findByIdAndCandidateId: don khong ton tai HOAC khong thuoc ve candidate dang dang
        // nhap deu tra ve 404 giong nhau, cung pattern voi getMyApplicationHistory.
        JobApplication application = applicationRepository
                .findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        ApplicationStatus fromStatus = application.getStatus();
        if (fromStatus != ApplicationStatus.PENDING && fromStatus != ApplicationStatus.INTERVIEW_INVITED) {
            throw new ApplicationNotWithdrawableException();
        }

        application.setStatus(ApplicationStatus.WITHDRAWN);
        JobApplication saved = applicationRepository.save(application);

        applicationStatusRecorder.record(saved.getId(), fromStatus, ApplicationStatus.WITHDRAWN, candidateId, null);

        // FR-C03: publish TRONG transaction chinh - nguoi nhan la HR so huu cong ty cua job,
        // KHONG phai candidate (chinh candidate la nguoi vua thuc hien hanh dong nay).
        eventPublisher.publishEvent(new ApplicationWithdrawnEvent(saved.getId(), saved.getJobId(), candidateId));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ApplicationSummaryResponse> getMyApplications(UUID candidateId) {
        return applicationRepository.findSummariesByCandidateId(candidateId).stream()
                .map(ApplicationService::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationHistoryEntryResponse> getMyApplicationHistory(UUID candidateId, UUID applicationId) {
        // Don khong ton tai HOAC khong thuoc ve candidateId dang dang nhap deu tra ve cung mot
        // 404 - khong duoc phan biet, tranh lo su ton tai cua don nguoi khac.
        JobApplication application = applicationRepository
                .findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        return statusHistoryRepository.findByApplicationIdOrderByChangedAtAsc(application.getId()).stream()
                .map(ApplicationService::toHistoryResponse)
                .toList();
    }

    // FR-U08 E1 - chi tiet MOT don cua chinh ung vien. Chi doc job_applications, jobs, companies, resumes
    // (R-D1): KHONG doc scoring_runs/criterion_scores/score_explanations/interview_invitations/lich su.
    @Transactional(readOnly = true)
    public ApplicationCandidateDetailResponse getMyApplicationDetail(UUID candidateId, UUID applicationId) {
        // R-Q2: don khong ton tai HOAC cua nguoi khac deu cung 404 APPLICATION_NOT_FOUND - cung pattern
        // getMyApplicationHistory/withdraw, KHONG theo 403 cua endpoint giay moi.
        JobApplication application = applicationRepository
                .findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        // findById KHONG loc deleted_at/status (Job khong co @SQLRestriction): don vao tin da dong/xoa mem van
        // mo duoc (R-D4). Ba dong duoi luon ton tai: job_id/resume_id la FK ON DELETE RESTRICT (V1), companies
        // khong co xoa - thieu la loi du lieu, khong phai truong hop nghiep vu.
        Job job = jobRepository
                .findById(application.getJobId())
                .orElseThrow(() -> new IllegalStateException("Khong tim thay tin cua don: " + applicationId));
        Company company = companyRepository
                .findById(job.getCompanyId())
                .orElseThrow(() -> new IllegalStateException("Khong tim thay cong ty cua tin: " + job.getId()));
        // R-D2: CV DA NOP vao don (job_applications.resume_id), KHONG phai CV chinh hien tai.
        Resume resume = resumeRepository
                .findById(application.getResumeId())
                .orElseThrow(() -> new IllegalStateException("Khong tim thay CV cua don: " + applicationId));

        JobCatalogFields catalog = JobCatalogFields.of(job, catalogRegistry);
        ApplicationCandidateDetailResponse.JobInfo jobInfo = new ApplicationCandidateDetailResponse.JobInfo(
                job.getId(),
                job.getTitle(),
                company.getId(),
                company.getName(),
                availabilityOf(job),
                catalog.categoryCode(),
                catalog.categoryLabel(),
                catalog.locationCode(),
                catalog.locationLabel(),
                catalog.legacyCategory(),
                catalog.legacyLocation(),
                job.getEmploymentType(),
                job.getWorkMode(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.getDeadline());
        ApplicationCandidateDetailResponse.ResumeInfo resumeInfo = new ApplicationCandidateDetailResponse.ResumeInfo(
                resume.getId(), resume.getFileName(), resume.getParseStatus(), resume.getParseError());

        return new ApplicationCandidateDetailResponse(
                application.getId(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getUpdatedAt(),
                application.getCoverLetter(),
                jobInfo,
                resumeInfo);
    }

    // R-D4 - xet theo thu tu, dung o dong dau tien khop. OPEN dung CHINH findOpenJobById (cung dieu kien voi
    // trang tin cong khai, ke ca han nop theo CURRENT_DATE cua DB) - KHONG tu viet dieu kien ngay o Java.
    private JobAvailability availabilityOf(Job job) {
        if (job.getDeletedAt() != null) {
            return JobAvailability.UNAVAILABLE;
        }
        if (jobRepository.findOpenJobById(job.getId()).isPresent()) {
            return JobAvailability.OPEN;
        }
        return switch (job.getStatus()) {
            // status OPEN, chua xoa nhung findOpenJobById rong -> chi con ly do qua han nop.
            case OPEN -> JobAvailability.EXPIRED;
            case PAUSED -> JobAvailability.PAUSED;
            case CLOSED -> JobAvailability.CLOSED;
            case DRAFT -> JobAvailability.UNAVAILABLE;
        };
    }

    private static ApplicationResponse toResponse(JobApplication a) {
        return new ApplicationResponse(
                a.getId(),
                a.getJobId(),
                a.getResumeId(),
                a.getRecruitmentCycle(),
                a.getStatus(),
                a.getAiConsentAt(),
                a.getCoverLetter(),
                a.getAppliedAt(),
                a.getUpdatedAt());
    }

    private static ApplicationSummaryResponse toSummaryResponse(ApplicationSummaryView v) {
        return new ApplicationSummaryResponse(
                v.getId(),
                v.getJobId(),
                v.getJobTitle(),
                v.getCompanyName(),
                ApplicationStatus.valueOf(v.getStatus()),
                v.getAppliedAt(),
                v.getUpdatedAt());
    }

    // Package-private (khong private): ApplicationHrDetailService (FR-H09 E5) dung lai dung cach map
    // nay cho lich su phia HR - mot DTO, mot cach map cho ca hai phia.
    static ApplicationHistoryEntryResponse toHistoryResponse(ApplicationStatusHistory h) {
        return new ApplicationHistoryEntryResponse(h.getId(), h.getFromStatus(), h.getToStatus(), h.getNote(), h.getChangedAt());
    }
}
