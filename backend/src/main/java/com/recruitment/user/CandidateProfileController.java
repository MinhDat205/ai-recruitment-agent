package com.recruitment.user;

import com.recruitment.user.dto.CandidateProfileRequest;
import com.recruitment.user.dto.CandidateProfileResponse;
import com.recruitment.user.dto.ResumeAutofillResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    // @Valid them o dot 1 (FR-U14 R-V1) de Bean Validation (@Min/@Max/@Size) tren DTO co hieu luc -
    // DTO cu khong co validate nen truoc day khong can.
    @PutMapping("/me")
    public CandidateProfileResponse update(
            Authentication authentication, @Valid @RequestBody CandidateProfileRequest request) {
        return candidateProfileService.update(UUID.fromString(authentication.getName()), request);
    }

    // R-O3(b) - chi danh dau da qua man onboarding, khong doi field nao khac. Idempotent.
    @PatchMapping("/me/skip-onboarding")
    public CandidateProfileResponse skipOnboarding(Authentication authentication) {
        return candidateProfileService.skipOnboarding(UUID.fromString(authentication.getName()));
    }

    // R-A1 - doc du lieu dien san tu CV chinh da DONE, khong goi AI. 409 qua
    // PrimaryResumeNotParsedException khi chua co CV chinh da phan tich xong.
    @GetMapping("/me/autofill-from-resume")
    public ResumeAutofillResponse autofillFromResume(Authentication authentication) {
        return candidateProfileService.autofillFromResume(UUID.fromString(authentication.getName()));
    }
}
