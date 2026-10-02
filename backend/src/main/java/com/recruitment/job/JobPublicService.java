package com.recruitment.job;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.common.dto.PageResponse;
import com.recruitment.common.exception.InvalidCatalogCodeException;
import com.recruitment.common.exception.InvalidJobFilterException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.dto.CompanyRef;
import com.recruitment.job.dto.JobDetailResponse;
import com.recruitment.job.dto.JobSummaryResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    // FR-U07 R-W1 - cung tap gia tri voi JobRequest.workMode (JobRequest.java:17,
    // @Pattern(regexp = "ONSITE|HYBRID|REMOTE")) va chk constraint work_mode cua V1. Khong co enum
    // dung chung san co trong du an (Job.workMode la String thuong) nen khai lai hang so o day thay
    // vi them mot kieu moi chi vi mot FR.
    private static final Set<String> VALID_WORK_MODES = Set.of("ONSITE", "HYBRID", "REMOTE");

    private static final String UNSET_WORK_MODE_SENTINEL = "__NONE__";

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final CatalogRegistry catalogRegistry;
    private final Clock clock;

    // Chi MOT constructor - Spring tu dung injection khong can @Autowired. Clock la bean co san o
    // ClockConfig.java:14 (Clock.systemUTC()), dung chung voi ResumeExperienceStateService/
    // ResumeParsedDataEnricher, khong tao them bean rieng cho FR nay.
    public JobPublicService(
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            CatalogRegistry catalogRegistry,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.catalogRegistry = catalogRegistry;
        this.clock = clock;
    }

    // Giu chu ky cu (R-F1) de cac noi goi/test cu khong phai sua - uy quyen sang overload day du voi
    // moi tham so moi la "khong loc" (R-F2: tham so moi khong anh huong toi truy van khi khong
    // truyen) va sort mac dinh NEWEST (R-O1).
    public PageResponse<JobSummaryResponse> search(
            String keyword, String location, String category, Integer page, Integer size) {
        return search(
                keyword,
                location,
                category,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                page,
                size);
    }

    // FR-U07: diem vao that su cua GET /api/public/jobs sau khi mo rong tham so (R-F2).
    public PageResponse<JobSummaryResponse> search(
            String keyword,
            String location,
            String category,
            String categoryCode,
            String locationCode,
            Integer salaryMin,
            Integer salaryMax,
            Boolean hideUnlisted,
            List<String> workMode,
            String postedWithin,
            String sort,
            Integer page,
            Integer size) {
        if (categoryCode != null && !catalogRegistry.isIndustry(categoryCode)) {
            throw InvalidCatalogCodeException.industry();
        }
        if (locationCode != null && !catalogRegistry.isProvince(locationCode)) {
            throw InvalidCatalogCodeException.province();
        }

        List<String> workModes = workMode == null ? List.of() : workMode;
        for (String mode : workModes) {
            if (!VALID_WORK_MODES.contains(mode)) {
                throw InvalidJobFilterException.invalidWorkMode();
            }
        }

        JobSortOption sortOption = JobSortOption.fromParam(sort);
        JobPostedWithin postedWithinOption = JobPostedWithin.fromParam(postedWithin);

        // R-S3 - am (tung ve) hoac min > max (khi ca hai cung truyen) deu la 400 INVALID_JOB_FILTER.
        if ((salaryMin != null && salaryMin < 0) || (salaryMax != null && salaryMax < 0)) {
            throw InvalidJobFilterException.negativeSalary();
        }
        if (salaryMin != null && salaryMax != null && salaryMin > salaryMax) {
            throw InvalidJobFilterException.salaryMinGreaterThanMax();
        }

        Instant sinceTimestamp =
                postedWithinOption == null ? null : clock.instant().minus(postedWithinOption.hours(), ChronoUnit.HOURS);

        PublicJobSearchCriteria criteria = new PublicJobSearchCriteria(
                categoryCode,
                locationCode,
                toVnd(salaryMin),
                toVnd(salaryMax),
                Boolean.TRUE.equals(hideUnlisted),
                workModes,
                sinceTimestamp);

        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<Job> jobPage = searchByCriteria(
                toPattern(keyword), toPattern(location), toPattern(category), criteria, sortOption, pageable);

        Map<UUID, Company> companiesById = loadCompanies(jobPage.getContent());

        return PageResponse.from(jobPage, job -> toSummary(job, companiesById.get(job.getCompanyId())));
    }

    // FR-U07 R-Q1 - diem noi dung chung cho FR-U15 sau nay (chua goi toi, chi chuan bi san): nhan
    // criteria (dieu kien "cung") + sort + pageable, tra ve dung MOT truy van (R-O4: chon bien the
    // ORDER BY co dinh bang switch tren enum, khong noi chuoi tham so nguoi dung vao SQL).
    Page<Job> searchByCriteria(
            String titlePattern,
            String locationPattern,
            String categoryPattern,
            PublicJobSearchCriteria criteria,
            JobSortOption sort,
            Pageable pageable) {
        boolean workModesPresent = !criteria.workModes().isEmpty();
        // Postgres khong cho "IN ()" voi danh sach rong - the mot gia tri khong bao gio khop khi
        // khong loc, :workModesPresent = FALSE da bo qua nhanh IN nay truoc khi no anh huong ket qua.
        List<String> workModeParams = workModesPresent ? criteria.workModes() : List.of(UNSET_WORK_MODE_SENTINEL);

        return switch (sort) {
            case NEWEST -> jobRepository.searchPublicJobsSortedByNewest(
                    titlePattern,
                    locationPattern,
                    categoryPattern,
                    criteria.categoryCode(),
                    criteria.locationCode(),
                    criteria.salaryMinVnd(),
                    criteria.salaryMaxVnd(),
                    criteria.hideUnlisted(),
                    workModesPresent,
                    workModeParams,
                    criteria.sinceTimestamp(),
                    pageable);
            case SALARY_DESC -> jobRepository.searchPublicJobsSortedBySalaryDesc(
                    titlePattern,
                    locationPattern,
                    categoryPattern,
                    criteria.categoryCode(),
                    criteria.locationCode(),
                    criteria.salaryMinVnd(),
                    criteria.salaryMaxVnd(),
                    criteria.hideUnlisted(),
                    workModesPresent,
                    workModeParams,
                    criteria.sinceTimestamp(),
                    pageable);
        };
    }

    // R-S1 - dau vao trieu VND (so nguyen), quy doi sang VND truoc khi so voi salary_min/salary_max
    // (NUMERIC(14,2), don vi VND).
    private BigDecimal toVnd(Integer salaryMillions) {
        return salaryMillions == null ? null : BigDecimal.valueOf(salaryMillions).multiply(BigDecimal.valueOf(1_000_000));
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

    // FR-U07 muc 7.9 - thoat ky tu wildcard cua ILIKE truoc khi boc "%...%", neu khong keyword/
    // category/location chua san %, _, \ se bi Postgres hieu nham thanh wildcard thay vi ky tu
    // nguyen van. Postgres dung \ lam escape char mac dinh cua LIKE/ILIKE (khong can them menh de
    // ESCAPE rieng) - nen phai thoat CHINH ky tu escape (\) truoc, roi moi toi % va _, neu lam
    // nguoc thu tu se thoat hong ca hai (vi du \% thoat dung lan 2 lai bien thanh \\%).
    private String toPattern(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String escaped = raw.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
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
