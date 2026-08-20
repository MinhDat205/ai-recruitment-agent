package com.recruitment.jobapplication;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.ApplicationNotWithdrawableException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
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

    public ApplicationService(
            JobApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository statusHistoryRepository,
            JobRepository jobRepository,
            ResumeRepository resumeRepository,
            ApplicationStatusRecorder applicationStatusRecorder,
            ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.jobRepository = jobRepository;
        this.resumeRepository = resumeRepository;
        this.applicationStatusRecorder = applicationStatusRecorder;
        this.eventPublisher = eventPublisher;
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

    private static ApplicationHistoryEntryResponse toHistoryResponse(ApplicationStatusHistory h) {
        return new ApplicationHistoryEntryResponse(h.getId(), h.getFromStatus(), h.getToStatus(), h.getNote(), h.getChangedAt());
    }
}
