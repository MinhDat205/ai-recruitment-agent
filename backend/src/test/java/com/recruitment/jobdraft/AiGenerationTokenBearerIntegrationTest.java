package com.recruitment.jobdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.recruitment.aigeneration.AiGenerationStamp;
import com.recruitment.aigeneration.AiGenerationTokenCodec;
import com.recruitment.common.exception.ErrorResponse;
import com.recruitment.common.exception.GlobalExceptionHandler;
import com.recruitment.common.exception.InvalidAiGenerationTokenException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MvcResult;

// FR-H11 dot 2 - rui ro (c) / muc 12 L5: token K4 (ky bang khoa dan xuat tu JWT_SECRET) KHONG dung duoc lam token dang
// nhap; access token that van dang nhap duoc (JwtService khong doi). Kem anh xa loi INVALID_AI_GENERATION_TOKEN.
class AiGenerationTokenBearerIntegrationTest extends JobDraftIntegrationTestSupport {

    @Autowired
    AiGenerationTokenCodec codec;

    @Autowired
    GlobalExceptionHandler globalExceptionHandler;

    @Test
    void k4TokenAsBearer_isRejected401_whileAccessTokenStillWorks() throws Exception {
        String email = uniqueEmail("h11-k4-bearer");
        String accessToken = register("hr", email);
        createCompany(accessToken);
        UUID userId = userIdOf(email);
        String k4Token = codec.issue(
                userId, JobDraftPart.JOB.tokenPurpose(),
                new AiGenerationStamp("claude-sonnet-4-6", "job-draft-job-v1", Instant.now()));

        MvcResult withK4 = mockMvc.perform(get("/api/hr/jobs").header("Authorization", "Bearer " + k4Token))
                .andReturn();
        MvcResult withAccess = mockMvc.perform(get("/api/hr/jobs").header("Authorization", "Bearer " + accessToken))
                .andReturn();

        assertThat(withK4.getResponse().getStatus()).isEqualTo(401);
        assertThat(withK4.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("UNAUTHENTICATED");
        assertThat(withAccess.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void accessTokenIsNotAcceptedAsK4Token() throws Exception {
        String email = uniqueEmail("h11-k4-access");
        String accessToken = register("hr", email);

        assertThatThrownBy(
                        () -> codec.verify(accessToken, userIdOf(email), JobDraftPart.JOB.tokenPurpose()))
                .isInstanceOf(InvalidAiGenerationTokenException.class);
    }

    @Test
    void invalidTokenException_mapsTo400WithFixedMessage() {
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleInvalidAiGenerationToken(new InvalidAiGenerationTokenException());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("INVALID_AI_GENERATION_TOKEN");
        assertThat(response.getBody().message())
                .isEqualTo("Thông tin phiên tạo bằng AI không hợp lệ hoặc đã hết hạn. Vui lòng tạo lại gợi ý.");
    }
}
