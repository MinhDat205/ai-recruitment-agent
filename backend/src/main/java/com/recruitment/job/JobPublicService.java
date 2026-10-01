package com.recruitment.job;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.common.dto.PageResponse;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.dto.CompanyRef;
import com.recruitment.job.dto.JobDetailResponse;
import com.recruitment.job.dto.JobSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class JobPublicService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final CatalogRegistry catalogRegistry;

    public JobPublicService(
            JobRepository jobRepository, CompanyRepository companyRepository, CatalogRegistry catalogRegistry) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.catalogRegistry = catalogRegistry;
    }

    public PageResponse<JobSummaryResponse> search(
            String keyword, String location, String category, Integer page, Integer size) {
        // Pageable KHONG duoc mang Sort: native query da tu ORDER BY, them Sort se sinh SQL sai.
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));

        Page<Job> jobPage = jobRepository.searchPublicJobs(
                toPattern(keyword), toPattern(location), toPattern(category), pageable);

        Map<UUID, Company> companiesById = loadCompanies(jobPage.getContent());

        return PageResponse.from(jobPage, job -> toSummary(job, companiesById.get(job.getCompanyId())));
    }

    public JobDetailResponse getDetail(UUID id) {
        Job job = jobRepository.findOpenJobById(id).orElseThrow(() -> new JobNotFoundException(id));
        Company company = companyRepository.findById(job.getCompanyId()).orElse(null);
        return toDetail(job, company);
    }

    // Dung cho JobRecommendationCandidateService (Dot 5, F1): hydrate day du JobSummaryResponse tu
    // danh sach jobId da co san trong cache job_recommendations, GIU NGUYEN thu tu jobIds dau vao
    // (da sap theo similarity_score DESC tu tang goi). findOpenJobsByIdIn tu loc lai OPEN/deleted_at/
    // deadline nen mot job vua dong/het han/bi xoa giua hai lot lam moi cache se tu dong bien mat
    // khoi ket qua, khong loi, khong can xu ly rieng.
    public List<JobSummaryResponse> getByIds(List<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        List<Job> jobs = jobRepository.findOpenJobsByIdIn(jobIds);
        Map<UUID, Job> jobsById = jobs.stream().collect(Collectors.toMap(Job::getId, job -> job));
        Map<UUID, Company> companiesById = loadCompanies(jobs);

        return jobIds.stream()
                .map(jobsById::get)
                .filter(Objects::nonNull)
                .map(job -> toSummary(job, companiesById.get(job.getCompanyId())))
                .toList();
    }

    private Map<UUID, Company> loadCompanies(List<Job> jobs) {
        List<UUID> companyIds = jobs.stream().map(Job::getCompanyId).distinct().toList();
        return companyRepository.findByIdIn(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c));
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

    private String toPattern(String raw) {
        return (raw == null || raw.isBlank()) ? null : "%" + raw.trim() + "%";
    }

    private CompanyRef toRef(Company c) {
        return c == null ? null : new CompanyRef(c.getId(), c.getName(), c.logoUrlWithCacheBust());
    }

    private JobSummaryResponse toSummary(Job j, Company c) {
        JobCatalogFields catalog = JobCatalogFields.of(j, catalogRegistry);
        return new JobSummaryResponse(
                j.getId(),
                j.getTitle(),
                catalog.categoryCode(),
                catalog.categoryLabel(),
                catalog.locationCode(),
                catalog.locationLabel(),
                catalog.legacyCategory(),
                catalog.legacyLocation(),
                j.getEmploymentType(),
                j.getWorkMode(),
                j.getSalaryMin(),
                j.getSalaryMax(),
                j.getSalaryCurrency(),
                j.getDeadline(),
                j.getPublishedAt(),
                toRef(c));
    }

    private JobDetailResponse toDetail(Job j, Company c) {
        JobCatalogFields catalog = JobCatalogFields.of(j, catalogRegistry);
        return new JobDetailResponse(
                j.getId(),
                j.getTitle(),
                j.getDescription(),
                j.getRequirements(),
                catalog.categoryCode(),
                catalog.categoryLabel(),
                catalog.locationCode(),
                catalog.locationLabel(),
                catalog.legacyCategory(),
                catalog.legacyLocation(),
                j.getEmploymentType(),
                j.getWorkMode(),
                j.getSalaryMin(),
                j.getSalaryMax(),
                j.getSalaryCurrency(),
                j.getDeadline(),
                j.getPublishedAt(),
                j.getCreatedAt(),
                toRef(c));
    }
}
