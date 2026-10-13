package com.recruitment.aigeneration;

import com.recruitment.common.exception.InvalidAiGenerationTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// K4 (FR-H11 R-K4-2, muc 12 L4, L5) - ky va kiem token sieu du lieu AI. Dung khi ket qua AI duoc SINH o mot request va
// LUU o request khac: luc sinh backend ky (model, phien ban prompt, thoi diem sinh) thanh token tra kem goi y, khong luu
// gi phia server; luc luu backend kiem chu ky roi moi ghi DUNG gia tri trong token. Client khong sua duoc gia tri nao ma
// khong lam hong chu ky.
//
// purpose do FR goi tu dat (vd "FR-H11/JOB") - K4 KHONG co enum muc dich, FR sau dung lai khong sua file nay (R-K4-1).
//
// Khoa ky (L5): dan xuat tu JWT_SECRET bang HMAC-SHA256 voi nhan rieng cua K4 -> khac khoa access token cua JwtService
// (JwtService KHONG bi sua). Token K4 cung khong co claim "type" nen JwtAuthenticationFilter khong nhan no lam token dang
// nhap, ke ca neu trung khoa - hai lop chan.
//
// Clock (L4): iat/exp khi ky va "bay gio" khi kiem deu lay tu bean Clock, de test co dinh duoc thoi diem.
@Component
public class AiGenerationTokenCodec {

    private static final Logger log = LoggerFactory.getLogger(AiGenerationTokenCodec.class);

    // Q6 - han 7 ngay: HR co the de trang mo lau; token chi khang dinh mot su kien da xay ra.
    static final Duration TOKEN_TTL = Duration.ofDays(7);
    static final String KEY_DERIVATION_LABEL = "k4-ai-generation-token-v1";
    static final String TOKEN_TYPE = "ai-generation";

    static final String CLAIM_TYPE = "typ";
    static final String CLAIM_PURPOSE = "purpose";
    static final String CLAIM_MODEL = "model";
    static final String CLAIM_PROMPT_VERSION = "pv";
    static final String CLAIM_GENERATED_AT = "gat";

    private final SecretKey key;
    private final Clock clock;

    public AiGenerationTokenCodec(@Value("${app.jwt.secret}") String jwtSecret, Clock clock) {
        this.key = deriveKey(jwtSecret);
        this.clock = clock;
    }

    // HMAC-SHA256(key = JWT_SECRET UTF-8, data = nhan K4) -> 32 byte, du 256 bit cho HS256.
    static SecretKey deriveKey(String jwtSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] derived = mac.doFinal(KEY_DERIVATION_LABEL.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(derived);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Khong dan xuat duoc khoa token K4", e);
        }
    }

    public String issue(UUID userId, String purpose, AiGenerationStamp stamp) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYPE, TOKEN_TYPE)
                .claim(CLAIM_PURPOSE, purpose)
                .claim(CLAIM_MODEL, stamp.model())
                .claim(CLAIM_PROMPT_VERSION, stamp.promptVersion())
                .claim(CLAIM_GENERATED_AT, stamp.generatedAt().toEpochMilli())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_TTL)))
                .signWith(key)
                .compact();
    }

    // Tra stamp nam TRONG token. Sai bat ky dieu kien nao (chu ky, han, typ, nguoi goi, muc dich, thieu claim) -> cung mot
    // InvalidAiGenerationTokenException (R-K4-4); ly do cu the chi o log.debug, khong log noi dung token.
    public AiGenerationStamp verify(String token, UUID userId, String purpose) {
        if (token == null || token.isBlank()) {
            throw invalid("token rong");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw invalid("khong parse/kiem duoc chu ky hoac het han: " + e.getClass().getSimpleName());
        }
        if (!TOKEN_TYPE.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw invalid("sai typ");
        }
        if (!userId.toString().equals(claims.getSubject())) {
            throw invalid("khong phai token cua nguoi goi");
        }
        if (!purpose.equals(claims.get(CLAIM_PURPOSE, String.class))) {
            throw invalid("sai muc dich");
        }
        try {
            Long generatedAtMillis = claims.get(CLAIM_GENERATED_AT, Long.class);
            if (generatedAtMillis == null) {
                throw invalid("thieu thoi diem sinh");
            }
            return new AiGenerationStamp(
                    claims.get(CLAIM_MODEL, String.class),
                    claims.get(CLAIM_PROMPT_VERSION, String.class),
                    Instant.ofEpochMilli(generatedAtMillis));
        } catch (IllegalArgumentException | JwtException e) {
            // JwtException o day = RequiredTypeException (claim sai kieu); IllegalArgumentException = stamp rong.
            throw invalid("claim sieu du lieu khong hop le: " + e.getClass().getSimpleName());
        }
    }

    private static InvalidAiGenerationTokenException invalid(String reason) {
        log.debug("Token K4 bi tu choi: {}", reason);
        return new InvalidAiGenerationTokenException();
    }
}
