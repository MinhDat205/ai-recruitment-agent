package com.recruitment.jobsearch;

import com.recruitment.jobsearch.dto.JobSearchRequest;
import com.recruitment.jobsearch.dto.JobSearchResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-U13 S1 (muc 4.3). /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o filter chain. Khong co ban ghi
// nao de so huu: dau vao la cau trong body, dau ra la viec lam cong khai OPEN. candidateId (subject JWT) chi dung lam khoa
// rate limit ai-sync o RateLimitFilter (doc tu SecurityContext) - truy van khong nhan candidateId nen controller khong
// doc Authentication (khuon B4 cua U12). Khong GET, khong luu.
@RestController
@RequestMapping(JobSearchCandidateController.PATH)
public class JobSearchCandidateController {

    static final String PATH = "/api/candidates/job-search";

    private final JobSearchService jobSearchService;

    public JobSearchCandidateController(JobSearchService jobSearchService) {
        this.jobSearchService = jobSearchService;
    }

    @PostMapping
    public JobSearchResponse search(@RequestBody JobSearchRequest request) {
        return jobSearchService.search(request);
    }
}
