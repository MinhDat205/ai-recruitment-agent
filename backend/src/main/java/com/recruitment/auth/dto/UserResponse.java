package com.recruitment.auth.dto;

import com.recruitment.user.User;
import java.time.Instant;
import java.util.UUID;

// Khong bao gio chua passwordHash.
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String role,
        String phone,
        String avatarUrl,
        boolean isActive,
        boolean emailVerified,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.isActive(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }
}
