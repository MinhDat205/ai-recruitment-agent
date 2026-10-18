package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.interviewtemplate.InterviewTemplate;
import com.recruitment.interviewtemplate.InterviewTemplateRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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
import org.springframework.transaction.annotation.Transactional;

// FR-H16 T17 - phia ung vien KHONG doi (R-U1): E1 cua U08, tin nhan, hop thu, CV da trich xuat, giay moi khi Job bat ->
// giong het khi Job tat (so toan bo chuoi JSON). C07/C08 phia ung vien kiem o T14/T15. @Transactional: moi @Test rollback
// rieng; chi mot tin nhan nen created_at trung khong anh huong thu tu.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlindReviewCandidateSideIntegrationTest extends BlindReviewIntegrationTestSupport {

    @Autowired
    private InterviewTemplateRepository interviewTemplateRepository;

    private Map<String, String> candidateViews(App app) throws Exception {
        String token = app.candidate().token();
        Map<String, String> views = new LinkedHashMap<>();
        for (String path : List.of(
                "/api/candidates/applications/" + app.id(),
                "/api/candidates/applications/" + app.id() + "/messages",
                "/api/candidates/messages/conversations?size=50",
                "/api/candidates/resumes/" + app.resumeId() + "/parsed")) {
            var result = getAs(token, path);
            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
            views.put(path, text(result));
        }
        return views;
    }

    @Test
    void candidateResponsesAreIdenticalWhenJobToggled() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, false);
        App app = application(jobId, candidate("Quốc Huy", "0900000111"), "Thư giới thiệu của Quốc Huy.");
        parsedData(app.resumeId(), "QUỐC HUY", """
                {"contact": {"fullName": "QUỐC HUY", "email": "quochuy.t17@test.local"}, "education": [],
                 "experience": [{"company": "Quốc Huy Studio", "title": "Java"}], "skills": [], "certifications": [],
                 "projects": [], "currentTitle": "Kỹ sư - Quốc Huy", "locationText": "Đà Nẵng"}
                """);
        mockMvc.perform(post("/api/hr/applications/" + app.id() + "/messages")
                .header("Authorization", "Bearer " + hr.token())
                .param("body", "Chào Quốc Huy, mời bạn bổ sung hồ sơ."));

        Map<String, String> off = candidateViews(app);
        setBlindReview(jobId, true);
        Map<String, String> on = candidateViews(app);

        assertThat(on).isEqualTo(off);
        assertThat(on.values()).noneMatch(json -> json.contains(HIDDEN) || json.contains(CandidateAlias.PREFIX));
        assertThat(on.get("/api/candidates/applications/" + app.id() + "/messages")).contains("Chào Quốc Huy");
    }

    @Test
    void candidateInvitationShowsRealNameAfterMaskedSend() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, true);
        InterviewTemplate template = new InterviewTemplate();
        template.setJobId(jobId);
        template.setCompanyName("Cong ty T17");
        template.setSubject("Thư mời — {{candidateName}}");
        template.setBody("Chào {{candidateName}}.");
        template.setSenderName("Phong Nhan Su");
        interviewTemplateRepository.saveAndFlush(template);
        App app = application(jobId, candidate("Quốc Huy", "0900000111"), null);
        Instant slot = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        var sent = mockMvc.perform(post("/api/hr/applications/" + app.id() + "/interview-invitation")
                        .header("Authorization", "Bearer " + hr.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of(
                                "subject", "Thư mời — {{candidateName}}",
                                "content", "Chào {{candidateName}}.",
                                "slots", List.of(Map.of("scheduledAt", slot.toString(), "location", "Văn phòng"))))))
                .andReturn();
        assertThat(sent.getResponse().getStatus()).isEqualTo(201);
        String path = "/api/candidates/applications/" + app.id() + "/interview-invitation";

        String on = text(getAs(app.candidate().token(), path));
        setBlindReview(jobId, false);
        String off = text(getAs(app.candidate().token(), path));

        assertThat(on).isEqualTo(off).contains("Thư mời — Quốc Huy").contains("Chào Quốc Huy.").doesNotContain("{{");
    }
}
