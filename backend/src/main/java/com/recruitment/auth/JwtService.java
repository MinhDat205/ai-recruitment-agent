package com.recruitment.auth;

import com.recruitment.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public String generateAccessToken(User user) {
        return generateToken(user, TYPE_ACCESS, accessExpirationMs);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, TYPE_REFRESH, refreshExpirationMs);
    }

    private String generateToken(User user, String type, long expirationMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    // Nem JwtException (het han, sai chu ky...) neu invalid - noi goi bat va tra 401.
    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        return jws.getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public String extractType(Claims claims) {
        return claims.get("type", String.class);
    }
}
