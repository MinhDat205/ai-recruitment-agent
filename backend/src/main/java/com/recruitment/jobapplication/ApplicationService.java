package com.recruitment.jobapplication;

import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.dto.ApplicationCreateRequest;
import com.recruitment.jobapplication.dto.ApplicationResponse;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;

    public ApplicationService(
            JobApplicationRepository applicationRepository,
            JobRepository jobRepository,
            ResumeRepository resumeRepository) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.resumeRepository = resumeRepository;
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
        return toResponse(applicationRepository.saveAndFlush(application));
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
