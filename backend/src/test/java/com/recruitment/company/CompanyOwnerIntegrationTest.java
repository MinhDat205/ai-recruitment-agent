package com.recruitment.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompanyOwnerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    private String registerAndLoginHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Nha Tuyen Dung","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc
                .perform(post("/api/auth/register/hr").contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult =
                mockMvc
                        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                        .andExpect(status().isOk())
                        .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    private MvcResult createCompany(String token, String name) throws Exception {
        String body = """
                {"name":"%s"}
                """.formatted(name);
        return mockMvc
                .perform(
                        post("/api/hr/companies")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
    }

    @Test
    void createTwiceForSameHr_returnsConflict() throws Exception {
        String token = registerAndLoginHr("hr-dup");

        MvcResult first = createCompany(token, "Cong ty A");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult second = createCompany(token, "Cong ty A lan 2");
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(second.getResponse().getContentAsString()).contains("COMPANY_ALREADY_EXISTS");
    }

    @Test
    void hrA_updateHrBCompany_returnsForbidden() throws Exception {
        String tokenA = registerAndLoginHr("hr-a");
        String tokenB = registerAndLoginHr("hr-b");

        MvcResult companyBResult = createCompany(tokenB, "Cong ty B");
        assertThat(companyBResult.getResponse().getStatus()).isEqualTo(201);
        String companyBId = extractJsonField(companyBResult.getResponse().getContentAsString(), "id");

        String updateBody = """
                {"name":"Bi HR khac doi ten"}
                """;
        mockMvc
                .perform(
                        put("/api/hr/companies/" + companyBId)
                                .header("Authorization", "Bearer " + tokenA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadFakeExeRenamedToPng_isRejected() throws Exception {
        String token = registerAndLoginHr("hr-logo");
        MvcResult companyResult = createCompany(token, "Cong ty Logo");
        String companyId = extractJsonField(companyResult.getResponse().getContentAsString(), "id");

        // Noi dung khong phai magic bytes cua PNG/JPEG/WEBP, chi doi duoi file - phai bi tu choi.
        MockMultipartFile fakeLogo =
                new MockMultipartFile("file", "virus.png", "image/png", "khong-phai-anh-that".getBytes());

        mockMvc
                .perform(
                        multipart("/api/hr/companies/" + companyId + "/logo")
                                .file(fakeLogo)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMine_beforeAnyCompanyCreated_returnsNotFound() throws Exception {
        String token = registerAndLoginHr("hr-none");

        mockMvc
                .perform(get("/api/hr/companies/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
