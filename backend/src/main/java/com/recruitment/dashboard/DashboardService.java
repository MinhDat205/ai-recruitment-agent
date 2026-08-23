package com.recruitment.dashboard;

import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.dashboard.dto.DashboardStatsResponse;
import com.recruitment.job.JobPerformanceView;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.FunnelCountsView;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.jobapplication.StatusCountView;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

// KHONG @Transactional: chi DOC, ba truy van doc lap khong can atomic voi nhau - cung tinh than
// voi ApplicationOwnerService (D3/D4). Doc cheo sang JobApplicationRepository/JobRepository dung
// mau ApplicationOwnerService doc cheo sang ScoringRunRepository/CriterionScoreRepository.
@Service
public class DashboardService {

    private final CompanyRepository companyRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;

    public DashboardService(
            CompanyRepository companyRepository,
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository) {
        this.companyRepository = companyRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
    }

    public DashboardStatsResponse getStats(UUID ownerId) {
        Company company = requireOwnCompany(ownerId);

        Map<ApplicationStatus, Long> statusBreakdown = toStatusBreakdown(
                jobApplicationRepository.countByStatusForCompany(company.getId()));

        FunnelCountsView funnelView = jobApplicationRepository.countFunnelForCompany(company.getId());
        DashboardStatsResponse.ConversionFunnel funnel = new DashboardStatsResponse.ConversionFunnel(
                funnelView.getTotalApplications(), funnelView.getEverInvited(), funnelView.getEverHired());

        List<DashboardStatsResponse.JobPerformanceItem> jobPerformance = jobRepository
                .findJobPerformanceForCompany(company.getId())
                .stream()
                .map(DashboardService::toJobPerformanceItem)
                .toList();

        // totalApplications lay tu funnelView (cung dieu kien WHERE voi statusBreakdown - company
        // scope, loai job xoa mem), khong tu cong lai statusBreakdown de tranh hai nguon so co the
        // lech nhau khi co sua doi rieng le sau nay.
        return new DashboardStatsResponse(
                funnelView.getTotalApplications(), statusBreakdown, funnel, jobPerformance);
    }

    // Luon tra du 5 key ApplicationStatus (0 neu cong ty chua co don o trang thai do) - GROUP BY
    // o tang SQL chi tra ve status THUC SU co don, khong tu dien day 5 gia tri.
    private Map<ApplicationStatus, Long> toStatusBreakdown(List<StatusCountView> rows) {
        Map<ApplicationStatus, Long> breakdown = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            breakdown.put(status, 0L);
        }
        for (StatusCountView row : rows) {
            // getStatus() la String (khong phai ApplicationStatus) - xem comment trong
            // StatusCountView ve gioi han projection cua native query.
            breakdown.put(ApplicationStatus.valueOf(row.getStatus()), row.getCount());
        }
        return breakdown;
    }

    private static DashboardStatsResponse.JobPerformanceItem toJobPerformanceItem(JobPerformanceView v) {
        return new DashboardStatsResponse.JobPerformanceItem(
                v.getJobId(),
                v.getTitle(),
                JobStatus.valueOf(v.getStatus()),
                v.getRecruitmentCycle(),
                v.getTotalApplications(),
                v.getScoredApplications(),
                v.getAverageScore(),
                v.getEverInvitedCount(),
                v.getEverHiredCount());
    }

    // Mau y het JobOwnerService/ApplicationOwnerService.requireOwnCompany.
    private Company requireOwnCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
    }
}
