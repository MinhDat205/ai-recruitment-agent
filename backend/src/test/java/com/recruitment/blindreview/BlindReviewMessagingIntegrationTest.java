package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// FR-H16 T11 - trao doi (R-T1..R-T3, R-M5): than tin phia HR, tep ung vien gui, hop thu HR; phia ung vien khong doi
// (R-U1). @Transactional: moi @Test rollback rieng (thong bao AFTER_COMMIT khong chay - T12 kiem rieng).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlindReviewMessagingIntegrationTest extends BlindReviewIntegrationTestSupport {

    private static final byte[] PDF = "%PDF-1.4 tep gia lap".getBytes(StandardCharsets.UTF_8);

    private record Scenario(Hr hr, UUID jobId, App app) {
    }

    private Scenario scenario(boolean blindReview) throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, blindReview);
        App app = application(jobId, candidate("Quốc Huy", "0900000111"), null);
        return new Scenario(hr, jobId, app);
    }

    private static String hrPath(App app) {
        return "/api/hr/applications/" + app.id() + "/messages";
    }

    private static String candidatePath(App app) {
        return "/api/candidates/applications/" + app.id() + "/messages";
    }

    private MvcResult send(String token, String path, String body) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", "Bearer " + token).param("body", body)).andReturn();
    }

    private UUID sendFile(String token, String path, String fileName) throws Exception {
        MvcResult result = mockMvc.perform(multipart(path)
                        .file(new MockMultipartFile("file", fileName, "application/pdf", PDF))
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(body(result).get("id").asString());
    }

    private static JsonNode last(JsonNode thread) {
        JsonNode messages = thread.get("messages");
        return messages.get(messages.size() - 1);
    }

    private static JsonNode bySender(JsonNode thread, String senderRole) {
        for (JsonNode message : thread.get("messages")) {
            if (message.get("senderRole").asString().equals(senderRole)) {
                return message;
            }
        }
        throw new AssertionError("Khong co tin cua " + senderRole);
    }

    private JsonNode inboxRow(Hr hr, App app) throws Exception {
        for (JsonNode node : getJson(hr.token(), "/api/hr/messages/conversations?size=50").get("items")) {
            if (node.get("applicationId").asString().equals(app.id().toString())) {
                return node;
            }
        }
        throw new AssertionError("Khong co cuoc trao doi " + app.id());
    }

    // ---- Than tin ----

    @Test
    void hrThreadMasksBodiesOfBothSides() throws Exception {
        Scenario s = scenario(true);
        Candidate c = s.app().candidate();
        assertThat(send(s.hr().token(), hrPath(s.app()), "Chào Quốc Huy, bạn gửi lại số 0900000111 giúp mình nhé.")
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(send(c.token(), candidatePath(s.app()), "Dạ em QUỐC HUY đây, email " + c.email() + ".")
                .getResponse().getStatus()).isEqualTo(201);

        MvcResult result = getAs(s.hr().token(), hrPath(s.app()));

        // Hai tin cung transaction co cung created_at (CLAUDE.md muc 3c) - tim theo nguoi gui, khong theo thu tu.
        JsonNode thread = body(result);
        assertThat(thread.get("messages")).hasSize(2);
        assertThat(bySender(thread, "HR").get("body").asString()).isEqualTo("Chào " + HIDDEN + ", bạn gửi lại số "
                + HIDDEN + " giúp mình nhé.");
        assertThat(bySender(thread, "CANDIDATE").get("body").asString())
                .isEqualTo("Dạ em " + HIDDEN + " đây, email " + HIDDEN + ".");
        assertNoLeak(text(result), c);
    }

    @Test
    void candidateSeesOwnConversationVerbatim() throws Exception {
        Scenario s = scenario(true);
        send(s.hr().token(), hrPath(s.app()), "Chào Quốc Huy.");

        JsonNode thread = getJson(s.app().candidate().token(), candidatePath(s.app()));

        assertThat(last(thread).get("body").asString()).isEqualTo("Chào Quốc Huy.");
    }

    @Test
    void hrThreadWhenJobDisabledOrAfterInvitationIsVerbatim() throws Exception {
        Scenario off = scenario(false);
        send(off.hr().token(), hrPath(off.app()), "Chào Quốc Huy.");
        Scenario invited = scenario(true);
        invite(invited.app());
        send(invited.hr().token(), hrPath(invited.app()), "Chào Quốc Huy.");

        assertThat(last(getJson(off.hr().token(), hrPath(off.app()))).get("body").asString()).isEqualTo("Chào Quốc Huy.");
        assertThat(last(getJson(invited.hr().token(), hrPath(invited.app()))).get("body").asString())
                .isEqualTo("Chào Quốc Huy.");
    }

    // ---- Tep ----

    @Test
    void candidateFileHasNoNameForHrAndDownloadIsConflict() throws Exception {
        Scenario s = scenario(true);
        UUID messageId = sendFile(s.app().candidate().token(), candidatePath(s.app()), "CV Quốc Huy.pdf");

        MvcResult thread = getAs(s.hr().token(), hrPath(s.app()));
        JsonNode attachment = last(body(thread)).get("attachment");
        MvcResult download = getAs(s.hr().token(), hrPath(s.app()) + "/" + messageId + "/attachment");

        assertThat(attachment.get("fileName").isNull()).isTrue();
        assertThat(attachment.get("fileType").asString()).isEqualTo("PDF");
        assertThat(attachment.get("fileSize").asLong()).isEqualTo(PDF.length);
        assertNoLeak(text(thread), s.app().candidate());
        assertThat(download.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(download).get("error").asString()).isEqualTo("IDENTITY_MASKED");
        assertThat(download.getResponse().getHeader("Content-Disposition")).isNull();
        // Ung vien van thay va tai duoc tep cua chinh minh.
        JsonNode own = last(getJson(s.app().candidate().token(), candidatePath(s.app()))).get("attachment");
        assertThat(own.get("fileName").asString()).isEqualTo("CV Quốc Huy.pdf");
        assertThat(getAs(s.app().candidate().token(), candidatePath(s.app()) + "/" + messageId + "/attachment")
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void hrFileKeepsNameAndDownloads() throws Exception {
        Scenario s = scenario(true);
        UUID messageId = sendFile(s.hr().token(), hrPath(s.app()), "Mau thong tin bo sung.pdf");

        JsonNode attachment = last(getJson(s.hr().token(), hrPath(s.app()))).get("attachment");
        MvcResult download = getAs(s.hr().token(), hrPath(s.app()) + "/" + messageId + "/attachment");

        assertThat(attachment.get("fileName").asString()).isEqualTo("Mau thong tin bo sung.pdf");
        assertThat(download.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void candidateFileWhenJobDisabledKeepsNameAndDownloads() throws Exception {
        Scenario s = scenario(false);
        UUID messageId = sendFile(s.app().candidate().token(), candidatePath(s.app()), "CV Quốc Huy.pdf");

        JsonNode attachment = last(getJson(s.hr().token(), hrPath(s.app()))).get("attachment");

        assertThat(attachment.get("fileName").asString()).isEqualTo("CV Quốc Huy.pdf");
        assertThat(getAs(s.hr().token(), hrPath(s.app()) + "/" + messageId + "/attachment").getResponse().getStatus())
                .isEqualTo(200);
    }

    // ---- Hop thu ----

    @Test
    void inboxUsesAliasAndExcerptComputedAfterMaskingFullBody() throws Exception {
        Scenario s = scenario(true);
        // Ho ten vat qua diem cat 120 code point cua MessageExcerpt (116 "x" + dau cach + "Quố" = 120): cat truoc roi moi
        // che se de lai manh "Quố" (gia tri nguon "Quốc Huy" khong con nguyen ven de khop).
        String body = "x".repeat(116) + " Quốc Huy gửi thêm thông tin.";
        assertThat(send(s.app().candidate().token(), candidatePath(s.app()), body).getResponse().getStatus())
                .isEqualTo(201);

        JsonNode row = inboxRow(s.hr(), s.app());

        assertThat(row.get("candidateName").asString()).isEqualTo(CandidateAlias.of(s.app().id()));
        String excerpt = row.get("lastMessageExcerpt").asString();
        assertThat(excerpt).doesNotContain("Quố").doesNotContain("Huy").startsWith("x".repeat(116) + " [đ");
        assertNoLeak(row.toString(), s.app().candidate());
    }

    @Test
    void inboxMasksPerRow() throws Exception {
        Hr hr = hr();
        App masked = application(job(hr, true), candidate("Quốc Huy", "0900000111"), null);
        App visible = application(job(hr, false), candidate("Lê Thị Lan", "0900000333"), null);
        send(masked.candidate().token(), candidatePath(masked), "Em là Quốc Huy.");
        send(visible.candidate().token(), candidatePath(visible), "Em là Lê Thị Lan.");

        JsonNode maskedRow = inboxRow(hr, masked);
        JsonNode visibleRow = inboxRow(hr, visible);

        assertThat(maskedRow.get("candidateName").asString()).isEqualTo(CandidateAlias.of(masked.id()));
        assertThat(maskedRow.get("lastMessageExcerpt").asString()).isEqualTo("Em là " + HIDDEN + ".");
        assertThat(visibleRow.get("candidateName").asString()).isEqualTo("Lê Thị Lan");
        assertThat(visibleRow.get("lastMessageExcerpt").asString()).isEqualTo("Em là Lê Thị Lan.");
    }

    @Test
    void candidateInboxIsUnchanged() throws Exception {
        Scenario s = scenario(true);
        send(s.hr().token(), hrPath(s.app()), "Chào Quốc Huy.");

        JsonNode items = getJson(s.app().candidate().token(), "/api/candidates/messages/conversations?size=50").get("items");

        assertThat(items.get(0).get("lastMessageExcerpt").asString()).isEqualTo("Chào Quốc Huy.");
    }
}
