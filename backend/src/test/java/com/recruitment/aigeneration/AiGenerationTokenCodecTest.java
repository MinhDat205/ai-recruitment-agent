package com.recruitment.aigeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.auth.JwtService;
import com.recruitment.common.exception.InvalidAiGenerationTokenException;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// FR-H11 dot 2 - T18 + bien han token (R-K4-2, R-K4-4, muc 12 L4, L5). Test don vi, khong Spring.
class AiGenerationTokenCodecTest {

    private static final String SECRET = "test-only-secret-khong-dung-that-toi-thieu-32-ky-tu";
    private static final Instant ISSUED_AT = Instant.parse("2026-10-13T03:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final String PURPOSE = "FR-H11/JOB";
    private static final AiGenerationStamp STAMP =
            new AiGenerationStamp("claude-sonnet-4-6", "job-draft-job-v1", Instant.parse("2026-10-13T02:59:58.123Z"));

    private static AiGenerationTokenCodec codecAt(Instant now) {
        return new AiGenerationTokenCodec(SECRET, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static String issueAt(Instant now) {
        return codecAt(now).issue(USER, PURPOSE, STAMP);
    }

    @Test
    void issueThenVerify_returnsStampFromToken() {
        String token = issueAt(ISSUED_AT);

        AiGenerationStamp stamp = codecAt(ISSUED_AT).verify(token, USER, PURPOSE);

        assertThat(stamp).isEqualTo(STAMP);
    }

    @Test
    void verify_expiryBoundary_sevenDays() {
        String token = issueAt(ISSUED_AT);
        Instant expiry = ISSUED_AT.plus(AiGenerationTokenCodec.TOKEN_TTL);

        assertThat(codecAt(expiry.minusSeconds(1)).verify(token, USER, PURPOSE)).isEqualTo(STAMP);
        // jjwt 0.12.6: token con hieu luc DEN DUNG thoi diem exp (het han khi "bay gio" > exp).
        assertThat(codecAt(expiry).verify(token, USER, PURPOSE)).isEqualTo(STAMP);
        assertThatThrownBy(() -> codecAt(expiry.plusSeconds(1)).verify(token, USER, PURPOSE))
                .isInstanceOf(InvalidAiGenerationTokenException.class);
    }

    @Test
    void verify_rejectsOtherUser() {
        String token = issueAt(ISSUED_AT);

        assertThatThrownBy(() -> codecAt(ISSUED_AT).verify(token, UUID.randomUUID(), PURPOSE))
                .isInstanceOf(InvalidAiGenerationTokenException.class);
    }

    @Test
    void verify_rejectsOtherPurpose() {
        String token = issueAt(ISSUED_AT);

        assertThatThrownBy(() -> codecAt(ISSUED_AT).verify(token, USER, "FR-H11/CRITERIA"))
                .isInstanceOf(InvalidAiGenerationTokenException.class);
    }

    @Test
    void verify_rejectsTamperedToken() {
        String token = issueAt(ISSUED_AT);
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        // Doi mot ky tu o phan payload (giu chu ky cu) - vd model khac.
        String[] parts = token.split("\\.");
        String forgedPayload = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "xx." + parts[2];

        AiGenerationTokenCodec codec = codecAt(ISSUED_AT);
        assertThatThrownBy(() -> codec.verify(tampered, USER, PURPOSE))
                .isInstanceOf(InvalidAiGenerationTokenException.class);
        assertThatThrownBy(() -> codec.verify(forgedPayload, USER, PURPOSE))
                .isInstanceOf(InvalidAiGenerationTokenException.class);
    }

    @Test
    void verify_rejectsNullBlankAndGarbage() {
        AiGenerationTokenCodec codec = codecAt(ISSUED_AT);

        assertThatThrownBy(() -> codec.verify(null, USER, PURPOSE)).isInstanceOf(InvalidAiGenerationTokenException.class);
        assertThatThrownBy(() -> codec.verify("  ", USER, PURPOSE)).isInstanceOf(InvalidAiGenerationTokenException.class);
        assertThatThrownBy(() -> codec.verify("abc.def.ghi", USER, PURPOSE))
                .isInstanceOf(InvalidAiGenerationTokenException.class);
    }

    @Test
    void verify_rejectsWrongTypeEvenWithDerivedKey() {
        String token = Jwts.builder()
                .subject(USER.toString())
                .claim("typ", "access")
                .claim("purpose", PURPOSE)
                .claim("model", "m")
                .claim("pv", "p")
                .claim("gat", ISSUED_AT.toEpochMilli())
                .issuedAt(Date.from(ISSUED_AT))
                .expiration(Date.from(ISSUED_AT.plus(Duration.ofDays(1))))
                .signWith(AiGenerationTokenCodec.deriveKey(SECRET))
                .compact();

        assertThatThrownBy(() -> codecAt(ISSUED_AT).verify(token, USER, PURPOSE))
                .isInstanceOf(InvalidAiGenerationTokenException.class);
    }

    @Test
    void verify_rejectsMissingMetadataClaims() {
        String token = Jwts.builder()
                .subject(USER.toString())
                .claim("typ", AiGenerationTokenCodec.TOKEN_TYPE)
                .claim("purpose", PURPOSE)
                .claim("pv", "p")
                .issuedAt(Date.from(ISSUED_AT))
                .expiration(Date.from(ISSUED_AT.plus(Duration.ofDays(1))))
                .signWith(AiGenerationTokenCodec.deriveKey(SECRET))
                .compact();

        assertThatThrownBy(() -> codecAt(ISSUED_AT).verify(token, USER, PURPOSE))
                .isInstanceOf(InvalidAiGenerationTokenException.class);
    }

    // L5 - khoa K4 khac khoa dang nhap: access token cua JwtService khong qua duoc codec, token K4 khong qua duoc
    // JwtService.parse (cung JWT_SECRET).
    @Test
    void keysAreSeparatedFromLoginJwt() {
        JwtService jwtService = new JwtService(SECRET, 3_600_000L, 604_800_000L);
        User user = new User();
        user.setId(USER);
        user.setRole(Role.HR);
        String accessToken = jwtService.generateAccessToken(user);
        String k4Token = codecAt(Instant.now()).issue(USER, PURPOSE, STAMP);

        assertThatThrownBy(() -> codecAt(Instant.now()).verify(accessToken, USER, PURPOSE))
                .isInstanceOf(InvalidAiGenerationTokenException.class);
        assertThatThrownBy(() -> jwtService.parse(k4Token)).isInstanceOf(JwtException.class);
    }

    @Test
    void derivedKey_is256BitsAndDiffersFromRawSecret() {
        byte[] derived = AiGenerationTokenCodec.deriveKey(SECRET).getEncoded();

        assertThat(derived).hasSize(32);
        assertThat(derived).isNotEqualTo(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
