package com.recruitment.user;

import com.recruitment.user.dto.CandidateProfileRequest;
import com.recruitment.user.dto.CandidateProfileResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o tang filter chain.
@RestController
@RequestMapping("/api/candidates/profile")
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;

    public CandidateProfileController(CandidateProfileService candidateProfileService) {
        this.candidateProfileService = candidateProfileService;
    }

    @GetMapping("/me")
    public CandidateProfileResponse me(Authentication authentication) {
        return candidateProfileService.getMine(UUID.fromString(authentication.getName()));
    }

    @PutMapping("/me")
    public CandidateProfileResponse update(
            Authentication authentication, @RequestBody CandidateProfileRequest request) {
        return candidateProfileService.update(UUID.fromString(authentication.getName()), request);
    }
}
