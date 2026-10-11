package com.recruitment.screening;

import com.recruitment.screening.dto.ApplicationScreeningResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// FR-H10 S6. /api/hr/** da bi SecurityConfig chan hasRole("HR") o filter chain; quyen so huu kiem o
// ApplicationScreeningHrService qua HrApplicationAccess (R-A1, R-A3).
@RestController
public class ApplicationScreeningHrController {

    private final ApplicationScreeningHrService service;

    public ApplicationScreeningHrController(ApplicationScreeningHrService service) {
        this.service = service;
    }

    @GetMapping("/api/hr/applications/{applicationId}/screening-answers")
    public ApplicationScreeningResponse getAnswers(Authentication authentication, @PathVariable UUID applicationId) {
        return service.getAnswers(UUID.fromString(authentication.getName()), applicationId);
    }
}
