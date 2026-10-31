package com.recruitment.jobapplication;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.ApplicationNotWithdrawableException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.common.exception.TalentPoolConsentNotActiveException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobAvailabilityResolver;
import com.recruitment.job.JobCatalogFields;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.dto.ApplicationCandidateDetailResponse;
import com.recruitment.jobapplication.dto.ApplicationCreateRequest;
import com.recruitment.jobapplication.dto.ApplicationHistoryEntryResponse;
import com.recruitment.jobapplication.dto.ApplicationResponse;
import com.recruitment.jobapplication.dto.ApplicationSummaryResponse;
import com.recruitment.jobapplication.dto.TalentPoolConsentResponse;
import com.recruitment.notification.ApplicationSubmittedEvent;
import com.recruitment.notification.ApplicationWithdrawnEvent;
import com.recruitment.reconsideration.ReconsiderationReader;
import com.recruitment.reconsideration.ReconsiderationStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.screening.ScreeningAnswerDraft;
import com.recruitment.screening.ScreeningCaptureService;
import com.recruitment.talentpool.TalentPoolEntryRepository;
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
    private final JobAvailabilityResolver jobAvailabilityResolver;
    private final ResumeRepository resumeRepository;
    private final ApplicationStatusRecorder applicationStatusRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final CompanyRepository companyRepository;
    private final CatalogRegistry catalogRegistry;
    private final ScreeningCaptureService screeningCaptureService;
    private final TalentPoolEntryRepository talentPoolEntryRepository;
    private final ReconsiderationReader reconsiderationReader;

    public ApplicationService(
            JobApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository statusHistoryRepository,
            JobRepository jobRepository,
            JobAvailabilityResolver jobAvailabilityResolver,
            ResumeRepository resumeRepository,
            ApplicationStatusRecorder applicationStatusRecorder,
            ApplicationEventPublisher eventPublisher,
            CompanyRepository companyRepository,
            CatalogRegistry catalogRegistry,
            ScreeningCaptureService screeningCaptureService,
            TalentPoolEntryRepository talentPoolEntryRepository,
            ReconsiderationReader reconsiderationReader) {
        this.applicationRepository = applicationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.jobRepository = jobRepository;
        this.jobAvailabilityResolver = jobAvailabilityResolver;
        this.resumeRepository = resumeRepository;
        this.applicationStatusRecorder = applicationStatusRecorder;
        this.eventPublisher = eventPublisher;
        this.companyRepository = companyRepository;
        this.catalogRegistry = catalogRegistry;
        this.screeningCaptureService = screeningCaptureService;
        this.talentPoolEntryRepository = talentPoolEntryRepository;
        this.reconsiderationReader = reconsiderationReader;
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

        // FR-U09 R-R3, R-V - khoa hang jobs (FOR SHARE), so revision (409 SCREENING_QUESTIONS_CHANGED), kiem cau tra loi
        // (400 INVALID_SCREENING_ANSWER) TRUOC khi INSERT don, de thu tu loi dung R-V3 (409 APPLICATION_DUPLICATE sau).
        List<ScreeningAnswerDraft> screeningDrafts =
                screeningCaptureService.prepare(job.getId(), request.screeningRevision(), request.answers());

        // R-S2, R-K2 - MOT Instant cho moi moc thoi gian cua lan nop: dong y AI, danh dau da chup, dong y luu ho so.
        Instant now = Instant.now();
        boolean talentPoolConsent = Boolean.TRUE.equals(request.talentPoolConsent());

        JobApplication application = new JobApplication();
        application.setJobId(job.getId());
        application.setCandidateId(candidateId);
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(job.getRecruitmentCycle());
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(request.aiConsent());
        application.setAiConsentAt(now);
        application.setCoverLetter(request.coverLetter());
        application.setScreeningCapturedAt(now);
        application.setTalentPoolConsent(talentPoolConsent);
        application.setTalentPoolConsentAt(talentPoolConsent ? now : null);

        // saveAndFlush: bat INSERT no ngay tai day thay vi hoan toi luc commit, de
        // DataIntegrityViolationException cua uq_application_per_cycle noi len trong pham vi
        // request va GlobalExceptionHandler bat duoc, tra 409 xac dinh.
        JobApplication saved = applicationRepository.saveAndFlush(application);

        // Dong lich su dau tien cua don: NULL -> PENDING, changed_by = chinh candidate (tu tao
        // don). Cung transaction voi viec tao don (goi bean khac, khong phai self-invocation).
        applicationStatusRecorder.record(saved.getId(), null, ApplicationStatus.PENDING, candidateId, null);

        // FR-U09 R-S1 - ban chup ghi trong CUNG transaction voi don; 0 cau -> 0 dong (NO_QUESTIONS).
        screeningCaptureService.persist(saved.getId(), screeningDrafts);

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
    // (R-D1): KHONG doc scoring_runs/criterion_scores/score_explanations/interview_invitations. Ngoai le da duyet
    // (FR-C09 M1): doc them application_status_history va application_reconsideration_requests CUA CHINH DON, qua
    // ReconsiderationReader, chi de dung khoi reconsideration (R-C1) - khong tra lich su hay du lieu noi bo nao khac.
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
                jobAvailabilityResolver.of(job), // R-D4 - FR-U16 R-A1 tach ra job/JobAvailabilityResolver
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
                resumeInfo,
                toTalentPoolConsentResponse(application),
                reconsiderationReader.candidateBlock(application.getId(), candidateId));
    }

    // FR-U09 F4 (R-K7, R-K8) - rut dong y luu ho so cua MOT don cua chinh ung vien, o MOI trang thai don.
    @Transactional
    public TalentPoolConsentResponse revokeTalentPoolConsent(UUID candidateId, UUID applicationId) {
        // R-A2: don khong ton tai HOAC cua nguoi khac -> CUNG 404, nem TRUOC khi toi cau UPDATE.
        applicationRepository
                .findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        // UPDATE co dieu kien (talent_pool_consent = TRUE): 0 dong = khong con dong y de rut -> 409. Khong doi status,
        // khong ghi lich su, khong thong bao, khong event (R-K8).
        int updated = applicationRepository.revokeTalentPoolConsentIfActive(applicationId, candidateId, Instant.now());
        if (updated == 0) {
            throw new TalentPoolConsentNotActiveException();
        }

        // FR-H15 R-C1 - go don khoi MOI kho trong CHINH transaction nay, SAU cau UPDATE tren (giu khoa hang don truoc,
        // cung thu tu voi P7 - R-C2). Nhanh 409 o tren khong cham toi muc.
        talentPoolEntryRepository.removeAllActiveByApplicationId(applicationId);

        // clearAutomatically da xoa persistence context: findById la SELECT that, thay gia tri vua ghi.
        JobApplication reloaded = applicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay don vua rut dong y: " + applicationId));
        return toTalentPoolConsentResponse(reloaded);
    }

    private static TalentPoolConsentResponse toTalentPoolConsentResponse(JobApplication application) {
        return new TalentPoolConsentResponse(
                TalentPoolConsentState.of(application),
                application.getTalentPoolConsentAt(),
                application.getTalentPoolConsentRevokedAt());
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
                v.getUpdatedAt(),
                v.getReconsiderationStatus() == null ? null : ReconsiderationStatus.valueOf(v.getReconsiderationStatus()));
    }

    // Package-private (khong private): ApplicationHrDetailService (FR-H09 E5) dung lai dung cach map
    // nay cho lich su phia HR - mot DTO, mot cach map cho ca hai phia.
    static ApplicationHistoryEntryResponse toHistoryResponse(ApplicationStatusHistory h) {
        return new ApplicationHistoryEntryResponse(h.getId(), h.getFromStatus(), h.getToStatus(), h.getNote(), h.getChangedAt());
    }
}
