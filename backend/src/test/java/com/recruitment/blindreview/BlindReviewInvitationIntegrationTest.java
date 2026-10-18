package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.interviewtemplate.InterviewTemplate;
import com.recruitment.interviewtemplate.InterviewTemplateRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// FR-H16 T13 - giay moi (R-I1..R-I4): preview giu {{candidateName}}, gui lan dau thay ho ten that roi kiem do dai (R-I3,
// bien 254/255/256 va 9999/10000/10001), don khong an danh luu nguyen van nhu H12. @Transactional: moi @Test rollback
// rieng; test khong so sanh thoi diem. Loi 400 nem trong transaction cua request - trang thai don va bang giay moi doc lai
// bang JDBC trong cung transaction (chua ghi gi truoc khi nem).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlindReviewInvitationIntegrationTest extends BlindReviewIntegrationTestSupport {

    private static final String PLACEHOLDER = "{{candidateName}}";
    // Ho ten DAI hon placeholder (31 > 17 ky tu): subject/content tho hop le (@Size) ma sau khi thay moi vuot gioi han.
    private static final String LONG_NAME = "Nguyễn Thị Phương Thảo Quốc Huy";
    private static final String COMPANY = "Cong ty Mau H16";

    @Autowired
    private InterviewTemplateRepository interviewTemplateRepository;

    private record Scenario(Hr hr, UUID jobId, String jobTitle, App app) {
    }

    private Scenario scenario(boolean blindReview, String fullName) throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, blindReview);
        InterviewTemplate template = new InterviewTemplate();
        template.setJobId(jobId);
        template.setCompanyName(COMPANY);
        template.setSubject("Thư mời phỏng vấn — " + PLACEHOLDER);
        template.setBody("Chào " + PLACEHOLDER + ",\nMời bạn phỏng vấn vị trí {{jobTitle}} tại {{companyName}}.");
        template.setSenderName("Phong Nhan Su");
        interviewTemplateRepository.saveAndFlush(template);
        App app = application(jobId, candidate(fullName, "0900000111"), null);
        return new Scenario(hr, jobId, jobRepository.findById(jobId).orElseThrow().getTitle(), app);
    }

    private static String sendJson(String subject, String content) {
        Instant slot = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        return JSON.writeValueAsString(Map.of(
                "subject", subject,
                "content", content,
                "slots", List.of(Map.of("scheduledAt", slot.toString(), "location", "Văn phòng"))));
    }

    private MvcResult send(Scenario s, String subject, String content) throws Exception {
        return mockMvc.perform(post("/api/hr/applications/" + s.app().id() + "/interview-invitation")
                        .header("Authorization", "Bearer " + s.hr().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendJson(subject, content)))
                .andReturn();
    }

    // JdbcTemplate khong kich hoat flush cua Hibernate: flush truoc moi lan doc thang DB.
    private String status(App app) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT status FROM job_applications WHERE id = ?", String.class, app.id());
    }

    private int invitationCount(App app) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_invitations WHERE application_id = ?", Integer.class, app.id());
    }

    private Map<String, Object> storedInvitation(App app) {
        entityManager.flush();
        return jdbcTemplate.queryForMap(
                "SELECT subject, rendered_content FROM interview_invitations WHERE application_id = ?", app.id());
    }

    // ---- Preview (R-I1) ----

    @Test
    void previewOfMaskedApplicationKeepsPlaceholderAndUsesAlias() throws Exception {
        Scenario s = scenario(true, "Quốc Huy");

        MvcResult result = getAs(s.hr().token(), "/api/hr/applications/" + s.app().id() + "/interview-invitation/preview");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode preview = body(result);
        assertThat(preview.get("candidateName").asString()).isEqualTo(CandidateAlias.of(s.app().id()));
        assertThat(preview.get("subject").asString()).isEqualTo("Thư mời phỏng vấn — " + PLACEHOLDER);
        assertThat(preview.get("content").asString())
                .isEqualTo("Chào " + PLACEHOLDER + ",\nMời bạn phỏng vấn vị trí " + s.jobTitle() + " tại " + COMPANY + ".");
        assertNoLeak(text(result), s.app().candidate());
    }

    @Test
    void previewWhenJobDisabledRendersRealName() throws Exception {
        Scenario s = scenario(false, "Quốc Huy");

        JsonNode preview = getJson(s.hr().token(), "/api/hr/applications/" + s.app().id() + "/interview-invitation/preview");

        assertThat(preview.get("candidateName").asString()).isEqualTo("Quốc Huy");
        assertThat(preview.get("subject").asString()).isEqualTo("Thư mời phỏng vấn — Quốc Huy");
    }

    // ---- Gui lan dau (R-I2, R-I4) ----

    @Test
    void sendingMaskedApplicationStoresRealNameAndUnmasks() throws Exception {
        Scenario s = scenario(true, "Quốc Huy");

        MvcResult result = send(s, "Thư mời phỏng vấn — " + PLACEHOLDER, "Chào " + PLACEHOLDER + ", hẹn gặp " + PLACEHOLDER + ".");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> stored = storedInvitation(s.app());
        assertThat(stored.get("subject")).isEqualTo("Thư mời phỏng vấn — Quốc Huy");
        assertThat(stored.get("rendered_content")).isEqualTo("Chào Quốc Huy, hẹn gặp Quốc Huy.");
        assertThat(text(result)).doesNotContain(PLACEHOLDER);
        assertThat(status(s.app())).isEqualTo("INTERVIEW_INVITED");
        // Sau khi gui (dong lich su INTERVIEW_INVITED cung transaction): don het an danh.
        JsonNode e1 = getJson(s.hr().token(), "/api/hr/applications/" + s.app().id());
        assertThat(e1.get("candidateName").asString()).isEqualTo("Quốc Huy");
        assertThat(e1.get("identityMasked").asBoolean()).isFalse();
    }

    @Test
    void sendingUnmaskedApplicationStoresVerbatimAsH12() throws Exception {
        Scenario s = scenario(false, "Quốc Huy");

        assertThat(send(s, "Thư mời — " + PLACEHOLDER, "Chào " + PLACEHOLDER + ".").getResponse().getStatus())
                .isEqualTo(201);

        Map<String, Object> stored = storedInvitation(s.app());
        assertThat(stored.get("subject")).isEqualTo("Thư mời — " + PLACEHOLDER);
        assertThat(stored.get("rendered_content")).isEqualTo("Chào " + PLACEHOLDER + ".");
    }

    @Test
    void sendingMaskedApplicationWithoutPlaceholderStoresAsIs() throws Exception {
        // Khong chan HR xoa cho {{candidateName}} (UI.md 4f): khong co gi de thay.
        Scenario s = scenario(true, "Quốc Huy");

        assertThat(send(s, "Thư mời phỏng vấn", "Chào bạn.").getResponse().getStatus()).isEqualTo(201);

        assertThat(storedInvitation(s.app()).get("subject")).isEqualTo("Thư mời phỏng vấn");
    }

    // ---- Do dai sau khi thay (R-I3) ----

    private static String subjectOfRenderedLength(int length) {
        return "a".repeat(length - LONG_NAME.length()) + PLACEHOLDER;
    }

    @Test
    void renderedSubjectBoundary() throws Exception {
        for (int length : new int[] {254, 255}) {
            Scenario s = scenario(true, LONG_NAME);
            MvcResult ok = send(s, subjectOfRenderedLength(length), "Chào " + PLACEHOLDER + ".");
            assertThat(ok.getResponse().getStatus()).as("subject %d", length).isEqualTo(201);
            assertThat(((String) storedInvitation(s.app()).get("subject")).length()).isEqualTo(length);
        }

        Scenario s = scenario(true, LONG_NAME);
        String rawSubject = subjectOfRenderedLength(256);
        assertThat(rawSubject.length()).isLessThanOrEqualTo(255);
        assertRenderedTooLong(s, send(s, rawSubject, "Chào " + PLACEHOLDER + "."));
    }

    @Test
    void renderedContentBoundary() throws Exception {
        for (int length : new int[] {9999, 10000}) {
            Scenario s = scenario(true, LONG_NAME);
            MvcResult ok = send(s, "Thư mời", "b".repeat(length - LONG_NAME.length()) + PLACEHOLDER);
            assertThat(ok.getResponse().getStatus()).as("content %d", length).isEqualTo(201);
            assertThat(((String) storedInvitation(s.app()).get("rendered_content")).length()).isEqualTo(length);
        }

        Scenario s = scenario(true, LONG_NAME);
        assertRenderedTooLong(s, send(s, "Thư mời", "b".repeat(10001 - LONG_NAME.length()) + PLACEHOLDER));
    }

    private void assertRenderedTooLong(Scenario s, MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).get("error").asString()).isEqualTo("INVITATION_RENDERED_TOO_LONG");
        assertThat(body(result).get("message").asString())
                .isEqualTo("Tiêu đề hoặc nội dung thư mời vượt giới hạn sau khi điền họ tên ứng viên.");
        assertThat(status(s.app())).isEqualTo("PENDING");
        assertThat(invitationCount(s.app())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM application_status_history WHERE application_id = ? AND to_status = 'INTERVIEW_INVITED'",
                        Integer.class, s.app().id()))
                .isZero();
        assertNoLeak(text(result), s.app().candidate());
    }

    @Test
    void longUnmaskedContentIsNotRendered() throws Exception {
        // Don khong an danh: khong thay, khong kiem lai - noi dung tho hop le @Size van luu nguyen van.
        Scenario s = scenario(false, LONG_NAME);

        MvcResult result = send(s, subjectOfRenderedLength(256), "Chào " + PLACEHOLDER + ".");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(storedInvitation(s.app()).get("subject")).isEqualTo(subjectOfRenderedLength(256));
    }
}
