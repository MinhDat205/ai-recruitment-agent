package com.recruitment.auth;

import com.recruitment.auth.dto.AuthResponse;
import com.recruitment.auth.dto.LoginRequest;
import com.recruitment.auth.dto.RefreshRequest;
import com.recruitment.auth.dto.RegisterCandidateRequest;
import com.recruitment.auth.dto.RegisterHrRequest;
import com.recruitment.auth.dto.UserResponse;
import com.recruitment.common.exception.EmailAlreadyExistsException;
import com.recruitment.user.CandidateProfile;
import com.recruitment.user.CandidateProfileRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            CandidateProfileRepository candidateProfileRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse registerCandidate(RegisterCandidateRequest request) {
        User user = createUser(request.email(), request.password(), request.fullName(), request.phone(), Role.CANDIDATE);
        candidateProfileRepository.save(new CandidateProfile(user.getId()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse registerHr(RegisterHrRequest request) {
        User user = createUser(request.email(), request.password(), request.fullName(), request.phone(), Role.HR);
        return UserResponse.from(user);
    }

    private User createUser(String email, String rawPassword, String fullName, String phone, Role role) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setRole(role);
        return userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user =
                userRepository
                        .findByEmail(request.email())
                        .orElseThrow(() -> new BadCredentialsException("Email hoac mat khau khong dung"));
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Email hoac mat khau khong dung");
        }
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        Claims claims = jwtService.parse(request.refreshToken());
        if (!JwtService.TYPE_REFRESH.equals(jwtService.extractType(claims))) {
            throw new JwtException("Token khong phai la refresh token");
        }
        UUID userId = jwtService.extractUserId(claims);
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new JwtException("Nguoi dung khong con ton tai"));
        if (!user.isActive()) {
            throw new JwtException("Tai khoan da bi vo hieu hoa");
        }
        String newAccessToken = jwtService.generateAccessToken(user);
        return new AuthResponse(
                newAccessToken, request.refreshToken(), "Bearer", jwtService.getAccessExpirationMs());
    }

    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new JwtException("Nguoi dung khong ton tai"));
        return UserResponse.from(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                "Bearer",
                jwtService.getAccessExpirationMs());
    }
}
