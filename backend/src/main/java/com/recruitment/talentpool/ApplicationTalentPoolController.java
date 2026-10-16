package com.recruitment.talentpool;

import com.recruitment.talentpool.dto.ApplicationTalentPoolsRequest;
import com.recruitment.talentpool.dto.ApplicationTalentPoolsResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-H15 P6, P7 (REQUIREMENT muc 4.3). Body hong -> TalentPoolExceptionAdvice (400 INVALID_TALENT_POOL_SELECTION).
@RestController
@RequestMapping("/api/hr/applications/{id}/talent-pools")
public class ApplicationTalentPoolController {

    private final ApplicationTalentPoolService service;

    public ApplicationTalentPoolController(ApplicationTalentPoolService service) {
        this.service = service;
    }

    @GetMapping
    public ApplicationTalentPoolsResponse get(Authentication authentication, @PathVariable UUID id) {
        return service.get(ownerId(authentication), id);
    }

    @PutMapping
    public ApplicationTalentPoolsResponse replace(
            Authentication authentication, @PathVariable UUID id, @RequestBody ApplicationTalentPoolsRequest request) {
        return service.replace(ownerId(authentication), id, request);
    }

    private static UUID ownerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
