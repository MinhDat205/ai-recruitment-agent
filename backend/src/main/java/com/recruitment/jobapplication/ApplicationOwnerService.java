package com.recruitment.jobapplication;

import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.scoring.LatestScoringRunView;
import com.recruitment.scoring.ScoringRunRepository;
import com.recruitment.scoring.ScoringRunStatus;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

// KHONG @Transactional: chi DOC, nhieu repository doc lap khong can atomic voi nhau - cung tinh
// than voi ScoringRunService. Doc cheo sang ScoringRunRepository (package scoring) de lay trang
// thai lot cham gan nhat, giong cach JobOwnerService da doc cheo sang RubricRepository (da chot o
// Q5, ke hoach D2).
@Service
public class ApplicationOwnerService {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ScoringRunRepository scoringRunRepository;

    public ApplicationOwnerService(
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            JobApplicationRepository jobApplicationRepository,
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            ScoringRunRepository scoringRunRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.scoringRunRepository = scoringRunRepository;
    }

    public List<ApplicationHrListItemResponse> listApplications(UUID ownerId, UUID jobId) {
        Job job = loadOwnedJob(jobId, ownerId);
        List<JobApplication> applications = jobApplicationRepository.findByJobIdOrderByAppliedAtDesc(job.getId());
        if (applications.isEmpty()) {
            return List.of();
        }

        // Ba lan tra cuu HANG LOAT (khong phai tung don mot trong vong lap) - tranh N+1 giong tinh
        // than ScoringRunService.listScoringRuns/countScoredCriteriaByRun.
        Map<UUID, ParseStatus> parseStatusByResumeId = resumeRepository
                .findAllById(applications.stream().map(JobApplication::getResumeId).toList())
                .stream()
                .collect(Collectors.toMap(Resume::getId, Resume::getParseStatus));

        Map<UUID, String> candidateNameById = userRepository
                .findAllById(applications.stream().map(JobApplication::getCandidateId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        Map<UUID, LatestScoringRunView> latestRunByApplicationId = scoringRunRepository
                .findLatestByApplicationIdIn(applications.stream().map(JobApplication::getId).toList())
                .stream()
                .collect(Collectors.toMap(LatestScoringRunView::getApplicationId, v -> v));

        return applications.stream()
                .map(app -> toResponse(app, parseStatusByResumeId, candidateNameById, latestRunByApplicationId))
                .toList();
    }

    private ApplicationHrListItemResponse toResponse(
            JobApplication application,
            Map<UUID, ParseStatus> parseStatusByResumeId,
            Map<UUID, String> candidateNameById,
            Map<UUID, LatestScoringRunView> latestRunByApplicationId) {
        LatestScoringRunView latestRun = latestRunByApplicationId.get(application.getId());
        return new ApplicationHrListItemResponse(
                application.getId(),
                candidateNameById.get(application.getCandidateId()),
                parseStatusByResumeId.get(application.getResumeId()),
                application.getAppliedAt(),
                latestRun == null ? null : latestRun.getId(),
                latestRun == null ? null : ScoringRunStatus.valueOf(latestRun.getStatus()),
                latestRun == null ? null : latestRun.getFinishedAt());
    }

    // Mau ownership giong het JobOwnerService.loadOwned/RubricOwnerService.loadOwnedRubric.
    private Job loadOwnedJob(UUID jobId, UUID ownerId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        Company company = requireOwnCompany(ownerId);
        if (!job.getCompanyId().equals(company.getId())) {
            throw new AccessDeniedException("Khong co quyen truy cap danh sach ung vien cua tin tuyen dung nay");
        }
        return job;
    }

    private Company requireOwnCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
    }
}
