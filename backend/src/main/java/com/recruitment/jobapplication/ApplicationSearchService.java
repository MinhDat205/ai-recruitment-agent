package com.recruitment.jobapplication;

import com.recruitment.common.dto.PageResponse;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InvalidCandidateSearchFilterException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.jobapplication.dto.ApplicationSearchItemResponse;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

// GET /api/hr/candidates (F3, FR-H08) - danh sach ung vien TOAN CONG TY, phan trang THAT o tang
// SQL (khac ApplicationOwnerService cua D3 tra ve List khong phan trang cho MOT job). KHONG
// @Transactional: chi DOC, cung tinh than voi ApplicationOwnerService/DashboardService.
@Service
public class ApplicationSearchService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

    private final CompanyRepository companyRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;

    public ApplicationSearchService(
            CompanyRepository companyRepository,
            JobApplicationRepository jobApplicationRepository,
            UserRepository userRepository,
            ResumeRepository resumeRepository) {
        this.companyRepository = companyRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
    }

    public PageResponse<ApplicationSearchItemResponse> search(
            UUID ownerId,
            UUID jobId,
            ApplicationStatus status,
            BigDecimal minTotalScore,
            BigDecimal maxTotalScore,
            String criterionName,
            BigDecimal minCriterionScore,
            Integer page,
            Integer size) {
        validateFilters(criterionName, minCriterionScore, minTotalScore, maxTotalScore);
        Company company = requireOwnCompany(ownerId);
        // Pageable KHONG duoc mang Sort: native query da tu ORDER BY, them Sort se sinh SQL sai -
        // mau JobPublicService.search.
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));
        String statusParam = status == null ? null : status.name();

        Page<CandidateSearchRow> rows = criterionName == null
                ? jobApplicationRepository.searchCandidates(
                        company.getId(), jobId, statusParam, minTotalScore, maxTotalScore, pageable)
                : jobApplicationRepository.searchCandidatesByCriterion(
                        company.getId(),
                        criterionName,
                        minCriterionScore,
                        jobId,
                        statusParam,
                        minTotalScore,
                        maxTotalScore,
                        pageable);

        // Batch-fetch candidateName/resumeParseStatus cho DUNG cac dong cua trang hien tai (toi da
        // MAX_SIZE dong) - tranh N+1, cung ky thuat voi ApplicationOwnerService (D3/D4) nhung chi
        // tren 1 trang thay vi toan bo don cua 1 job.
        List<CandidateSearchRow> content = rows.getContent();
        Map<UUID, String> candidateNameById = userRepository
                .findAllById(content.stream().map(CandidateSearchRow::getCandidateId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        Map<UUID, ParseStatus> parseStatusByResumeId = resumeRepository
                .findAllById(content.stream().map(CandidateSearchRow::getResumeId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Resume::getId, Resume::getParseStatus));

        return PageResponse.from(rows, row -> toResponse(row, candidateNameById, parseStatusByResumeId));
    }

    public List<String> listCriterionNames(UUID ownerId) {
        Company company = requireOwnCompany(ownerId);
        return jobApplicationRepository.findDistinctCriterionNamesForCompany(company.getId());
    }

    // criterionName/minCriterionScore phai di CUNG NHAU (ca hai hoac khong cai nao) - thieu mot
    // trong hai la yeu cau khong ro rang (loc theo tieu chi ma khong co nguong, hoac co nguong ma
    // khong biet tieu chi nao). minTotalScore > maxTotalScore la khoang rong vo nghia.
    private void validateFilters(
            String criterionName, BigDecimal minCriterionScore, BigDecimal minTotalScore, BigDecimal maxTotalScore) {
        if ((criterionName == null) != (minCriterionScore == null)) {
            throw new InvalidCandidateSearchFilterException(
                    "Phải cung cấp đồng thời criterionName và minCriterionScore, hoặc bỏ trống cả hai");
        }
        if (minTotalScore != null && maxTotalScore != null && minTotalScore.compareTo(maxTotalScore) > 0) {
            throw new InvalidCandidateSearchFilterException("minTotalScore không được lớn hơn maxTotalScore");
        }
    }

    private ApplicationSearchItemResponse toResponse(
            CandidateSearchRow row, Map<UUID, String> candidateNameById, Map<UUID, ParseStatus> parseStatusByResumeId) {
        return new ApplicationSearchItemResponse(
                row.getId(),
                row.getJobId(),
                row.getJobTitle(),
                candidateNameById.get(row.getCandidateId()),
                parseStatusByResumeId.get(row.getResumeId()),
                row.getAppliedAt(),
                // getStatus() la String (khong phai ApplicationStatus) - xem comment trong
                // CandidateSearchRow ve gioi han projection cua native query.
                ApplicationStatus.valueOf(row.getStatus()),
                row.getLatestScoringRunId(),
                row.getTotalScore());
    }

    // Mau y het JobOwnerService/ApplicationOwnerService/DashboardService.requireOwnCompany.
    private Company requireOwnCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
    }

    private int safePage(Integer page) {
        return (page == null || page < 0) ? 0 : page;
    }

    private int safeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
