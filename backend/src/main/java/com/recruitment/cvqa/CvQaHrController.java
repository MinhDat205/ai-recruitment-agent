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

// FR-C08 Q1 phia HR. /api/hr/** da bi SecurityConfig chan hasRole("HR") o filter chain; quyen so huu don kiem o
// CvQaFacade qua HrApplicationAccess (R-Q1). Vai tro nguoi hoi = HR suy tu duong dan nay (R-Q5).
@RestController
@RequestMapping("/api/hr/applications/{applicationId}/cv-qa")
public class CvQaHrController {

    private final CvQaFacade cvQaFacade;

    public CvQaHrController(CvQaFacade cvQaFacade) {
        this.cvQaFacade = cvQaFacade;
    }

    // 200 (khong 201) - khong tao gi (muc 4.3).
    @PostMapping
    public CvQaResponse ask(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @RequestBody CvQaRequest request) {
        return cvQaFacade.askAsHr(UUID.fromString(authentication.getName()), applicationId, request);
    }
}
