package com.recruitment.auth;

import com.recruitment.auth.dto.AuthResponse;
import com.recruitment.auth.dto.LoginRequest;
import com.recruitment.auth.dto.RefreshRequest;
import com.recruitment.auth.dto.RegisterCandidateRequest;
import com.recruitment.auth.dto.RegisterHrRequest;
import com.recruitment.auth.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/candidate")
    public ResponseEntity<UserResponse> registerCandidate(@Valid @RequestBody RegisterCandidateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerCandidate(request));
    }

    @PostMapping("/register/hr")
    public ResponseEntity<UserResponse> registerHr(@Valid @RequestBody RegisterHrRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerHr(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return authService.me(userId);
    }
}
