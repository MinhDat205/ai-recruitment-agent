package com.recruitment.job;

import com.recruitment.common.dto.PageResponse;
import com.recruitment.job.dto.JobDetailResponse;
import com.recruitment.job.dto.JobSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/jobs")
public class JobPublicController {

    private final JobPublicService jobPublicService;

    public JobPublicController(JobPublicService jobPublicService) {
        this.jobPublicService = jobPublicService;
    }

    // FR-U07 R-F2 - mo rong tham so, giu nguyen path va ba tham so cu keyword/location/category
    // (R-F1). categoryCode/locationCode (R-N), salaryMin/salaryMax/hideUnlisted (R-S), workMode lap
    // tham so 0..n (R-W), postedWithin (R-T), sort (R-O) - validate va xu ly o JobPublicService.
    // FR-U15 R-H3 - categoryCode/locationCode doi tu String sang List<String> (lap tham so 0..n,
    // cung quy uoc voi workMode) - mot gia tri (?categoryCode=A) van hop le nhu cu.
    @GetMapping
    public PageResponse<JobSummaryResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> categoryCode,
            @RequestParam(required = false) List<String> locationCode,
            @RequestParam(required = false) Integer salaryMin,
            @RequestParam(required = false) Integer salaryMax,
            @RequestParam(required = false) Boolean hideUnlisted,
            @RequestParam(required = false) List<String> workMode,
            @RequestParam(required = false) String postedWithin,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return jobPublicService.search(
                keyword,
                location,
                category,
                categoryCode,
                locationCode,
                salaryMin,
                salaryMax,
                hideUnlisted,
                workMode,
                postedWithin,
                sort,
                page,
                size);
    }

    @GetMapping("/{id}")
    public JobDetailResponse detail(@PathVariable UUID id) {
        return jobPublicService.getDetail(id);
    }
}
