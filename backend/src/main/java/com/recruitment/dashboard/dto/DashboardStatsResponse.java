package com.recruitment.dashboard.dto;

import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// GET /api/hr/dashboard/stats (Dot 2, FR-H08). Pham vi TOAN CONG TY cua HR dang dang nhap, KHONG
// phai theo tung job (khac ApplicationHrListItemResponse cua D3/D4). Khong co field nao ten
// verdict/label/isQualified/passed/recommendation (CLAUDE.md muc 7) - day la thong ke thuan tuy,
// khong phai phan quyet.
public record DashboardStatsResponse(
        long totalApplications,
        // Luon du 5 key (ca 5 gia tri ApplicationStatus), 0 neu cong ty chua co don o trang thai
        // do - de frontend khong phai tu suy dien key thieu la 0.
        Map<ApplicationStatus, Long> statusBreakdown,
        ConversionFunnel funnel,
        List<JobPerformanceItem> jobPerformance) {

    // everInvited/everHired dem theo "DA TUNG dat trang thai" doc tu
    // application_status_history.to_status (KHONG phai job_applications.status hien tai) - don
    // duoc moi phong van roi rut don (WITHDRAWN) van tinh la "da tung duoc moi" (FR-U06). Ty le %
    // hien thi tinh o frontend tu ba so tho nay, backend khong tu lam tron.
    public record ConversionFunnel(long appliedTotal, long everInvited, long everHired) {}

    // averageScore la BigDecimal (wrapper, co the null khi scoredApplications = 0 - AVG tren tap
    // rong tra NULL, khong suy dien 0). everInvitedCount/everHiredCount cung nguyen tac "da tung"
    // voi ConversionFunnel, ap dung nhat quan cho ca hai khoi thong ke tren cung dashboard.
    public record JobPerformanceItem(
            UUID jobId,
            String title,
            JobStatus status,
            int recruitmentCycle,
            long totalApplications,
            long scoredApplications,
            BigDecimal averageScore,
            long everInvitedCount,
            long everHiredCount) {}
}
