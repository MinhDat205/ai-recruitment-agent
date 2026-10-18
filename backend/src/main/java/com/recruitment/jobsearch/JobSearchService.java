package com.recruitment.jobsearch;

import com.recruitment.ai.citation.VerbatimQuoteVerifier;
import com.recruitment.ai.embedding.EmbeddingResult;
import com.recruitment.ai.embedding.EmbeddingTextFormat;
import com.recruitment.ai.jobsearch.JobSearchParsePayload;
import com.recruitment.ai.jobsearch.JobSearchParseService;
import com.recruitment.ai.sync.SyncEmbeddingCaller;
import com.recruitment.common.exception.InvalidJobSearchRequestException;
import com.recruitment.job.Job;
import com.recruitment.job.JobEmbeddingRepository;
import com.recruitment.job.JobPublicService;
import com.recruitment.job.JobRepository;
import com.recruitment.job.dto.JobSummaryResponse;
import com.recruitment.jobsearch.JobSearchConditionResolver.Resolution;
import com.recruitment.jobsearch.dto.JobSearchInterpretation;
import com.recruitment.jobsearch.dto.JobSearchItem;
import com.recruitment.jobsearch.dto.JobSearchRanking;
import com.recruitment.jobsearch.dto.JobSearchRequest;
import com.recruitment.jobsearch.dto.JobSearchResponse;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

// FR-U13 S1 - facade tim viec bang mo ta. KHONG mo transaction (R-K6): K3 (SyncAiCaller, SyncEmbeddingCaller) tu chan
// khi luong goi dang co transaction; moi cau repository tu chay transaction ngan mac dinh cua Spring Data, SAU khi K3 da
// tra. Thu tu R-E5: chuan hoa R-I2 -> kiem R-I3 -> LLM (R-A) -> R-C -> R-D -> embedding (chi khi co phan mo ta) -> truy
// van -> hydrate -> R-K4 -> R-M. Loi K3 (LLM hoac embedding) noi len nguyen AiSyncFailedException - KHONG roi ve xep
// theo moi nhat (R-E5). Khong luu gi, khong log cau/phan mo ta/doan chu (R-L).
@Service
public class JobSearchService {

    // R-K2 - khai mot cho.
    static final int RESULT_LIMIT = 50;
    // R-I3.
    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 300;

    // Postgres khong nhan "IN ()" rong - cung ky thuat sentinel cua U07/U15 (cap Present = false + danh sach sentinel).
    private static final String UNSET_CODE_SENTINEL = "__NONE__";
    private static final BigDecimal VND_PER_MILLION = BigDecimal.valueOf(1_000_000);

    private final JobSearchParseService parseService;
    private final JobSearchCatalogPrompt catalogPrompt;
    private final JobSearchConditionResolver conditionResolver;
    private final JobSearchMatcher matcher;
    private final SyncEmbeddingCaller syncEmbeddingCaller;
    private final JobRepository jobRepository;
    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final JobPublicService jobPublicService;

    public JobSearchService(
            JobSearchParseService parseService,
            JobSearchCatalogPrompt catalogPrompt,
            JobSearchConditionResolver conditionResolver,
            JobSearchMatcher matcher,
            SyncEmbeddingCaller syncEmbeddingCaller,
            JobRepository jobRepository,
            JobEmbeddingRepository jobEmbeddingRepository,
            JobPublicService jobPublicService) {
        this.parseService = parseService;
        this.catalogPrompt = catalogPrompt;
        this.conditionResolver = conditionResolver;
        this.matcher = matcher;
        this.syncEmbeddingCaller = syncEmbeddingCaller;
        this.jobRepository = jobRepository;
        this.jobEmbeddingRepository = jobEmbeddingRepository;
        this.jobPublicService = jobPublicService;
    }

    public JobSearchResponse search(JobSearchRequest request) {
        String q = normalize(request == null ? null : request.query());
        validate(q);

        JobSearchParsePayload payload =
                parseService.parse(q, catalogPrompt.provinceCatalog(), catalogPrompt.industryCatalog());
        Resolution conditions = conditionResolver.resolve(payload);
        String description = JobSearchDescriptionBuilder.build(q, conditions);

        // R-K1/R-E4 - chi embedding khi co phan mo ta; chu gui embedding la description (chu nguyen van con lai - R-D4).
        JobSearchRanking ranking;
        List<Job> rankedJobs;
        if (description != null) {
            EmbeddingResult embedding = syncEmbeddingCaller.embed(description);
            ranking = JobSearchRanking.DESCRIPTION;
            rankedJobs = findRankedByDescription(conditions, EmbeddingTextFormat.toVectorText(embedding.vector()));
        } else {
            ranking = JobSearchRanking.NEWEST;
            rankedJobs = findNewest(conditions);
        }

        JobSearchInterpretation interpretation = new JobSearchInterpretation(
                conditions.locationCodes(),
                conditions.industryCodes(),
                conditions.salaryMin(),
                conditions.salaryMax(),
                conditions.workModes(),
                description,
                JobSearchDescriptionBuilder.personalInfoIgnored(conditions));
        return new JobSearchResponse(interpretation, ranking, toItems(rankedJobs, ranking, conditions));
    }

    // R-I2 - NFC roi gop khoang trang (K2). Khong doi hoa/thuong, khong sua chinh ta. null giu null.
    static String normalize(String query) {
        if (query == null) {
            return null;
        }
        return VerbatimQuoteVerifier.normalizeWhitespace(Normalizer.normalize(query, Normalizer.Form.NFC));
    }

    // R-I3 - loi dau tien; 0 loi goi AI.
    static void validate(String q) {
        if (q == null || q.isEmpty()) {
            throw InvalidJobSearchRequestException.emptyQuery();
        }
        if (q.length() < MIN_QUERY_LENGTH) {
            throw InvalidJobSearchRequestException.queryTooShort();
        }
        if (q.length() > MAX_QUERY_LENGTH) {
            throw InvalidJobSearchRequestException.queryTooLong();
        }
        if (q.codePoints().anyMatch(JobSearchService::isDisallowed)) {
            throw InvalidJobSearchRequestException.disallowedCharacter();
        }
    }

    private static boolean isDisallowed(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.FORMAT;
    }

    // R-Q1/R-Q3 - nhanh DESCRIPTION: khong nguong tuong dong, viec da nop khong bi loai, viec chua co embedding xep cuoi.
    private List<Job> findRankedByDescription(Resolution c, String queryVector) {
        return jobRepository.findRankedForSearch(
                null,
                null,
                null,
                !c.industryCodes().isEmpty(),
                codeParams(c.industryCodes()),
                !c.locationCodes().isEmpty(),
                codeParams(c.locationCodes()),
                toVnd(c.salaryMin()),
                toVnd(c.salaryMax()),
                false,
                !c.workModes().isEmpty(),
                codeParams(c.workModes()),
                null,
                queryVector,
                RESULT_LIMIT);
    }

    // R-K1 - nhanh NEWEST: dung truy van U07 sap moi nhat, trang 0 co 50; khong embedding.
    private List<Job> findNewest(Resolution c) {
        return jobRepository
                .searchPublicJobsSortedByNewest(
                        null,
                        null,
                        null,
                        !c.industryCodes().isEmpty(),
                        codeParams(c.industryCodes()),
                        !c.locationCodes().isEmpty(),
                        codeParams(c.locationCodes()),
                        toVnd(c.salaryMin()),
                        toVnd(c.salaryMax()),
                        false,
                        !c.workModes().isEmpty(),
                        codeParams(c.workModes()),
                        null,
                        PageRequest.of(0, RESULT_LIMIT))
                .getContent();
    }

    // R-K3 - hydrate giu thu tu (getByIds tu loai viec vua dong giua hai cau); R-K4 - danh dau xep theo mo ta; R-M.
    private List<JobSearchItem> toItems(List<Job> rankedJobs, JobSearchRanking ranking, Resolution conditions) {
        List<UUID> orderedIds = rankedJobs.stream().map(Job::getId).toList();
        List<JobSummaryResponse> summaries = jobPublicService.getByIds(orderedIds);
        Map<UUID, Job> jobsById = rankedJobs.stream().collect(Collectors.toMap(Job::getId, Function.identity()));
        Set<UUID> embeddedIds = ranking == JobSearchRanking.DESCRIPTION && !orderedIds.isEmpty()
                ? new HashSet<>(jobEmbeddingRepository.findJobIdsWithEmbeddingIn(orderedIds))
                : Set.of();

        List<JobSearchItem> items = new ArrayList<>();
        // R-K4 - tu viec dau tien KHONG co embedding tro di moi viec la false (ranh gioi lien mot khoi, ke ca khi
        // embedding vua duoc tao giua hai cau). Nhanh NEWEST: embeddedIds rong -> moi viec false.
        boolean stillRanked = true;
        for (JobSummaryResponse summary : summaries) {
            if (!embeddedIds.contains(summary.id())) {
                stillRanked = false;
            }
            Job job = jobsById.get(summary.id());
            items.add(new JobSearchItem(summary, matcher.matchedConditions(job, conditions), stillRanked));
        }
        return items;
    }

    private static List<String> codeParams(List<String> codes) {
        return codes.isEmpty() ? List.of(UNSET_CODE_SENTINEL) : codes;
    }

    // Cung cong thuc toVnd cua U07 (JobPublicService) - trieu VND -> VND; null giu null.
    private static BigDecimal toVnd(Integer millions) {
        return millions == null ? null : BigDecimal.valueOf(millions).multiply(VND_PER_MILLION);
    }
}
