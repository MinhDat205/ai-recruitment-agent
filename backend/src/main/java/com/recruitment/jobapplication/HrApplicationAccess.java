package com.recruitment.jobapplication;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

// FR-H09 R-Q3 - method nap don co kiem quyen DUNG CHUNG cho moi endpoint moi cua trang ho so don
// (E1, E3, E4, E5). E2 dung method co san cua ResumeHrService (R-Q3, khong doi).
//
// Thu tu va ma loi y het ApplicationStatusService.loadOwnedApplication (R-Q2): cong ty cua HR truoc
// (404 COMPANY_NOT_FOUND), roi don (404 APPLICATION_NOT_FOUND), roi Job cua don thuoc cong ty khac
// -> 403 (AccessDeniedException -> JsonAccessDeniedHandler). Giu dung quy uoc 403 cua nhom route
// /api/hr/applications/{id}/** (xem ResumeHrService.loadOwnedApplication) - KHONG doi sang 404.
//
// Cac service cu (ScoringRunService, ApplicationStatusService...) van giu ban rieng cua chung -
// R-Q4: FR-H09 khong dong vao kiem quyen cua endpoint co san.
@Component
public class HrApplicationAccess {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;

    public HrApplicationAccess(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
    }

    // Tra kem Job da nap (khong bat noi goi tra lai lan nua) - E1 can tieu de tin, E3/E4 can Job
    // de tinh hang tren toan bo don cua tin.
    public OwnedApplication loadOwned(UUID ownerId, UUID applicationId) {
        Company company = companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
        JobApplication application = jobApplicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        Job job = jobRepository
                .findById(application.getJobId())
                .orElseThrow(() -> new JobNotFoundException(application.getJobId()));
        if (!job.getCompanyId().equals(company.getId())) {
            throw new AccessDeniedException("Khong co quyen truy cap don ung tuyen nay");
        }
        return new OwnedApplication(application, job);
    }

    public record OwnedApplication(JobApplication application, Job job) {
    }
}
