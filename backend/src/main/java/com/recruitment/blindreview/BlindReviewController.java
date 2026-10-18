package com.recruitment.blindreview;

import com.recruitment.blindreview.dto.BlindReviewRequest;
import com.recruitment.blindreview.dto.BlindReviewResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-H16 B1 (REQUIREMENT muc 4.3). /api/hr/** da bi SecurityConfig chan hasRole("HR") o filter chain; quyen so huu Job
// kiem o BlindReviewService. Body hong -> BlindReviewExceptionAdvice (400 INVALID_BLIND_REVIEW_REQUEST). Day la duong
// DUY NHAT doi cong tac (R-B2) - PUT /api/hr/jobs/{id} khong dung toi cot nay.
@RestController
@RequestMapping("/api/hr/jobs/{jobId}/blind-review")
public class BlindReviewController {

    private final BlindReviewService service;

    public BlindReviewController(BlindReviewService service) {
        this.service = service;
    }

    @PatchMapping
    public BlindReviewResponse update(
            Authentication authentication, @PathVariable UUID jobId, @RequestBody BlindReviewRequest request) {
        return service.update(UUID.fromString(authentication.getName()), jobId, request);
    }
}
