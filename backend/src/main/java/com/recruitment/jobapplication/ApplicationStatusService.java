package com.recruitment.jobapplication;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.ApplicationStatusConflictException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InvalidApplicationStatusTransitionException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.dto.ApplicationResponse;
import com.recruitment.notification.ApplicationStatusChangedEvent;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-H07 (E1) - HR doi trang thai don ung tuyen thu cong. Day CHI la mot UPDATE status theo dung
// bang chuyen tiep co dinh ben duoi - KHONG doc scoring_runs/criterion_scores o dau trong class
// nay, khong co nguong diem, khong tu dong hoa: moi lan doi trang thai xuat phat tu MOT request
// HR goi len (xem CLAUDE.md muc 2 va muc 7).
@Service
public class ApplicationStatusService {

    // Chi hai trang thai nguon co the chuyen tiep boi HR. WITHDRAWN/HIRED/REJECTED khong phai key
    // -> tu dong bi tu choi khi la trang thai hien tai (bao gom ca REJECTED - trang thai cuoi,
    // khong quay lai duoc INTERVIEW_INVITED). WITHDRAWN khong nam trong bat ky tap dich nao - HR
    // khong the tu dat don ve WITHDRAWN qua endpoint nay (chi ung vien lam duoc, xem ApplicationService.withdraw).
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
            ApplicationStatus.PENDING, Set.of(ApplicationStatus.INTERVIEW_INVITED, ApplicationStatus.REJECTED),
            ApplicationStatus.INTERVIEW_INVITED, Set.of(ApplicationStatus.HIRED, ApplicationStatus.REJECTED));

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationStatusRecorder applicationStatusRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public ApplicationStatusService(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            ApplicationStatusRecorder applicationStatusRecorder,
            ApplicationEventPublisher eventPublisher) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.applicationStatusRecorder = applicationStatusRecorder;
        this.eventPublisher = eventPublisher;
    }

    // Doi trang thai bang UPDATE co dieu kien tren ca id LAN trang thai goc (updateStatusIfCurrent,
    // xem JobApplicationRepository) thay vi doc-roi-save khong dieu kien - chot chan lost-update
    // that: hai request PATCH gan nhu dong thoi (double-click, hai tab HR) cung doc duoc oldStatus,
    // chi mot trong hai duoc phep ghi (Dot 2, chore/hardening).
    //
    // clearAutomatically=true tren updateStatusIfCurrent xoa persistence context - object
    // "application" trong tay tu day tro di la STALE (van mang oldStatus, khong tu cap nhat qua
    // UPDATE nam ngoai ORM). KHONG doc lai status/updatedAt tu no: dung thang bien oldStatus/newStatus
    // da co san cho recorder/event, va BAT BUOC findById MOI de dung response - lan doc nay chac
    // chan la SELECT that tu DB (khong con gi trong persistence context de tra ve nham ban cu).
    @Transactional
    public ApplicationResponse changeStatus(UUID ownerId, UUID applicationId, ApplicationStatus newStatus) {
        JobApplication application = loadOwnedApplication(applicationId, ownerId);
        ApplicationStatus oldStatus = application.getStatus();

        Set<ApplicationStatus> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(oldStatus, Set.of());
        if (!allowedTargets.contains(newStatus)) {
            throw new InvalidApplicationStatusTransitionException(oldStatus.name(), newStatus.name());
        }

        int updated = jobApplicationRepository.updateStatusIfCurrent(applicationId, oldStatus, newStatus);
        if (updated == 0) {
            throw new ApplicationStatusConflictException();
        }

        // ownerId = users.id cua HR dang dang nhap (JwtService.subject, xem ApplicationStatusRecorder)
        // - dung field changed_by de FR-H08 (lich su audit) tra ra dung nguoi thao tac.
        applicationStatusRecorder.record(applicationId, oldStatus, newStatus, ownerId, null);

        // FR-C03: publish TRONG transaction chinh - NotificationEventListener xu ly sau
        // AFTER_COMMIT (chi khi UPDATE nay that su commit thanh cong). jobId/candidateId doc tu
        // "application" (object cu) la an toan - hai truong nay bat bien, khong bi UPDATE tren doi.
        eventPublisher.publishEvent(new ApplicationStatusChangedEvent(
                applicationId, application.getJobId(), application.getCandidateId(), oldStatus, newStatus));

        JobApplication refreshed = jobApplicationRepository.findById(applicationId).orElseThrow();
        return toResponse(refreshed);
    }

    // Mau y het ScoringRunService.loadOwnedApplication/ResumeHrService.loadOwnedApplication, NHUNG
    // doi thu tu: requireOwnCompany chay TRUOC khi tra job_applications/jobs (khac ban goc, dung
    // khuon ScoringRunAuditService.loadOwnedApplication - Dot 2, chore/hardening). Ly do: HR chua
    // tao ho so cong ty phai nhan dung 404 COMPANY_NOT_FOUND, khong phai 404
    // APPLICATION_NOT_FOUND/JOB_NOT_FOUND gay hieu nham (nguyen nhan that la thieu cong ty, khong
    // phai don/job khong ton tai) - va tranh doc thua hai bang do khi da biet chac se loi.
    private JobApplication loadOwnedApplication(UUID applicationId, UUID ownerId) {
        Company company = requireOwnCompany(ownerId);
        JobApplication application = jobApplicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        Job job = jobRepository
                .findById(application.getJobId())
                .orElseThrow(() -> new JobNotFoundException(application.getJobId()));
        if (!job.getCompanyId().equals(company.getId())) {
            throw new AccessDeniedException("Khong co quyen doi trang thai don ung tuyen nay");
        }
        return application;
    }

    private Company requireOwnCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
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
}
