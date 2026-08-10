package com.recruitment.job;

import com.recruitment.common.dto.PageResponse;
import com.recruitment.job.dto.JobDetailResponse;
import com.recruitment.job.dto.JobSummaryResponse;
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

    @GetMapping
    public PageResponse<JobSummaryResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return jobPublicService.search(keyword, location, category, page, size);
    }

    @GetMapping("/{id}")
    public JobDetailResponse detail(@PathVariable UUID id) {
        return jobPublicService.getDetail(id);
    }
}
