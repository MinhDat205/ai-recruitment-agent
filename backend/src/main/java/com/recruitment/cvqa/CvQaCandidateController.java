package com.recruitment.cvqa;

import com.recruitment.cvqa.dto.CvQaRequest;
import com.recruitment.cvqa.dto.CvQaResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-C08 Q2 phia ung vien. /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o filter chain; quyen so
// huu CV kiem o CvQaFacade qua findByIdAndCandidateId - CV nguoi khac va CV khong ton tai CUNG 404 (R-Q2). Vai tro
// nguoi hoi = ung vien suy tu duong dan nay (R-Q5).
@RestController
@RequestMapping("/api/candidates/resumes/{resumeId}/cv-qa")
public class CvQaCandidateController {

    private final CvQaFacade cvQaFacade;

    public CvQaCandidateController(CvQaFacade cvQaFacade) {
        this.cvQaFacade = cvQaFacade;
    }

    // 200 (khong 201) - khong tao gi (muc 4.3).
    @PostMapping
    public CvQaResponse ask(
            Authentication authentication,
            @PathVariable UUID resumeId,
            @RequestBody CvQaRequest request) {
        return cvQaFacade.askAsCandidate(UUID.fromString(authentication.getName()), resumeId, request);
    }
}
