package com.recruitment.aicontext;

import com.recruitment.aicontext.CvQaContext.JobPosting;
import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.CvTextNotAvailableException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.ResumeParsedDataRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// FR-C08 K1 (R-K1-2) - gom ngu canh hoi dap CV. Goi SAU khi noi goi da kiem quyen (R-Q1 / R-Q2) - o day khong kiem
// quyen. Transaction CHI DOC, NGAN: chi nap du lieu, dong truoc khi noi goi goi AI (CLAUDE.md muc 3c) - bean rieng,
// duoc inject vao noi goi, khong tu goi trong cung bean (self-invocation pha @Transactional).
//
// Phu thuoc DUNG danh sach R-K1-3: jobapplication/, job/, resume/ (chi ResumeParsedDataRepository), common/. CAM
// scoring/, rubric/, ai/, messaging/, interviewinvitation/, screening/, notification/, user/, company/ (T12a kiem bang
// reflection). Khong them method repository moi.
@Component
public class CvQaContextAssembler {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;

    public CvQaContextAssembler(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            ResumeParsedDataRepository resumeParsedDataRepository) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
    }

    // viewer = HR CO DINH - khong nhan tham so vai tro (R-K1-2, CLAUDE.md muc 3d). CV la CV DA NOP vao don
    // (job_applications.resume_id), khong phai CV chinh hien tai (R-Q1). Tin xoa mem/dong van doc duoc (findById khong loc).
    @Transactional(readOnly = true)
    public CvQaContext forHrApplication(UUID applicationId) {
        JobApplication application = jobApplicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        Job job = jobRepository
                .findById(application.getJobId())
                .orElseThrow(() -> new JobNotFoundException(application.getJobId()));
        return new CvQaContext(
                ContextViewer.HR,
                resumeText(application.getResumeId()),
                new JobPosting(job.getTitle(), job.getDescription(), job.getRequirements()));
    }

    // R-Q2 - phia ung vien khong co tin tuyen dung (Q9).
    @Transactional(readOnly = true)
    public CvQaContext forCandidateResume(UUID resumeId) {
        return new CvQaContext(ContextViewer.CANDIDATE, resumeText(resumeId), null);
    }

    // R-Q3 - khong co hang resume_parsed_data (CV PENDING/PROCESSING/FAILED) -> 409. CV dang trich xuat lai (C05) van
    // con raw_text -> doc duoc.
    private String resumeText(UUID resumeId) {
        return resumeParsedDataRepository
                .findByResumeId(resumeId)
                .orElseThrow(CvTextNotAvailableException::new)
                .getRawText();
    }
}
