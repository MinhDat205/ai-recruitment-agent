package com.recruitment.screening;

import com.recruitment.screening.dto.ApplicationScreeningResponse;
import com.recruitment.screening.dto.ScreeningFormResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// FR-U09 phia ung vien. /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o filter chain (R-A1). F1 khong
// can quyen so huu: bo cau hoi cua tin cong khai (R-A3). F3 kiem so huu o ApplicationScreeningCandidateService (R-A2).
@RestController
public class ScreeningCandidateController {

    private final ScreeningCandidateService service;
    private final ApplicationScreeningCandidateService answerService;

    public ScreeningCandidateController(
            ScreeningCandidateService service, ApplicationScreeningCandidateService answerService) {
        this.service = service;
        this.answerService = answerService;
    }

    @GetMapping("/api/candidates/jobs/{jobId}/screening-questions")
    public ScreeningFormResponse getForm(@PathVariable UUID jobId) {
        return service.getForm(jobId);
    }

    @GetMapping("/api/candidates/applications/{applicationId}/screening-answers")
    public ApplicationScreeningResponse getMyAnswers(Authentication authentication, @PathVariable UUID applicationId) {
        return answerService.getMyAnswers(UUID.fromString(authentication.getName()), applicationId);
    }
}
