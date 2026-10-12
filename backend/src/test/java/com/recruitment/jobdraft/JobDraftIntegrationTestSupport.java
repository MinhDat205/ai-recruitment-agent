package com.recruitment.jobdraft;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.user.UserRepository;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// FR-H11 - helper dung chung cho cac lop test tich hop cua jobdraft/. Cung cau hinh context voi
// MessageDraftIntegrationTestSupport (Testcontainers + LlmTestConfiguration + MockMvc + profile test) -> dung chung
// context cache. KHONG @Transactional cap lop: J1 goi K3 (K3/facade nem IllegalStateException neu luong request dang co
// transaction). Du lieu moi test tu tao, email duy nhat.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class JobDraftIntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepository userRepository;

    // prefix <= 27 ky tu.
    static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    static String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }

    static String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    String login(String email) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "accessToken");
    }

    String register(String role, String email) throws Exception {
        mockMvc
                .perform(post("/api/auth/register/" + role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"%s","phone":"0900000000"}
                                """.formatted(email, uniqueName("Nguoi Dung"))))
                .andExpect(status().isCreated());
        return login(email);
    }

    UUID userIdOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    void createCompany(String token) throws Exception {
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(uniqueName("Cong ty"))))
                .andExpect(status().isCreated());
    }

    // Tao tin DRAFT bang duong thu cong cua FR-H02 (khong qua FR-H11).
    UUID createDraftJob(String token) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "job": {"title":"Tin kiem thu K4","description":"Mo ta cong viec"},
                                  "interviewTemplate": {
                                    "subject":"Thu moi phong van",
                                    "body":"Kinh moi ban tham gia phong van.",
                                    "senderName":"Phong Nhan Su"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(extractJsonField(result.getResponse().getContentAsString(), "id"));
    }
}
