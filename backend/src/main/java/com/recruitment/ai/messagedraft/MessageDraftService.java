package com.recruitment.ai.messagedraft;

import com.recruitment.ai.sync.SyncAiCaller;
import com.recruitment.aicontext.ContextViewer;
import com.recruitment.aicontext.ConversationContext;
import com.recruitment.aicontext.ConversationContext.InterviewSchedule;
import com.recruitment.aicontext.ConversationContext.RecentMessage;
import com.recruitment.messaging.MessageSenderRole;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

// FR-C07 - soan nhap tin nhan bang AI. Doc lap voi tang persistence: khong repository, khong entity, khong
// @Transactional (mau CvImprovementService). Nhan ngu canh K1 da gom san (du lieu THO - L7) + tinh huong + giong van +
// muc dich da chuan hoa; dung user message theo R-I3/R-I5 roi goi K3 (SyncAiCaller). Khong luu gi (R-D1).
//
// R-I2: system message = CHI file prompt + {format} (K3 tu dien {format}) - systemParams rong, khong chuoi nguoi dung
// nao vao system message. R-I3: moi chuoi nguoi dung nhap nam trong the co dinh va da thay < > thanh ‹ ›.
@Service
public class MessageDraftService {

    private static final Logger log = LoggerFactory.getLogger(MessageDraftService.class);

    // Gan lien ten file prompt (message-draft-v1.st) - khai MOT CHO DUY NHAT.
    static final String PROMPT_VERSION = "message-draft-v1";

    // R-D6 - khop gioi han tin cua FR-C06 (R-M3).
    static final int MAX_DRAFT_LENGTH = 4000;
    // R-I5
    static final int MAX_MESSAGE_CODE_POINTS = 1000;
    static final String TRUNCATION_MARK = "…";
    static final String ATTACHMENT_ONLY = "(tệp đính kèm)";
    static final String NO_INTERVIEW = "chưa có lịch";

    // Muc 5 - gio phong van theo gio Viet Nam, khong theo mui gio server/UTC.
    private static final DateTimeFormatter INTERVIEW_TIME =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final String HR_LABEL = "Nhà tuyển dụng";
    private static final String CANDIDATE_LABEL = "Ứng viên";

    private final ChatClient chatClient;
    private final Resource promptResource;
    private final SyncAiCaller syncAiCaller;

    public MessageDraftService(
            ChatClient messageDraftChatClient,
            @Value("classpath:ai/prompt/" + PROMPT_VERSION + ".st") Resource promptResource,
            SyncAiCaller syncAiCaller) {
        this.chatClient = messageDraftChatClient;
        this.promptResource = promptResource;
        this.syncAiCaller = syncAiCaller;
    }

    // customPurpose: chi khac null khi scenario = CUSTOM (facade da chuan hoa xuong dong va kiem R-S4). Loi K3 noi len
    // nguyen AiSyncFailedException (GlobalExceptionHandler -> 502/503/504).
    public String draft(ConversationContext context, DraftScenario scenario, DraftTone tone, String customPurpose) {
        String userMessage = buildUserMessage(context, scenario, tone, customPurpose);
        log.debug("User message soan nhap ({}): {}", PROMPT_VERSION, userMessage);

        String draft = syncAiCaller
                .call(
                        chatClient,
                        promptResource,
                        Map.of(),
                        userMessage,
                        MessageDraftPayload.class,
                        MessageDraftService::isValidDraft)
                .entity()
                .draft();
        String normalized = normalizeLineBreaks(draft).strip();
        log.debug("Ban nhap AI tra ve: {}", normalized);
        return normalized;
    }

    // R-D6 - hop le khi draft khac rong sau khi bo khoang trang hai dau, va sau khi doi CRLF -> LF dai <= 4000
    // (String.length()). Cham tran token do K3 kiem (R-K3-9). false -> K3 coi la output hong, thu lai 1 lan.
    static boolean isValidDraft(MessageDraftPayload payload) {
        if (payload == null || payload.draft() == null) {
            return false;
        }
        String normalized = normalizeLineBreaks(payload.draft());
        return !normalized.isBlank() && normalized.length() <= MAX_DRAFT_LENGTH;
    }

    static String buildUserMessage(
            ConversationContext context, DraftScenario scenario, DraftTone tone, String customPurpose) {
        StringBuilder message = new StringBuilder();
        boolean hrWrites = context.viewer() == ContextViewer.HR;

        message.append("<ngu_canh>\n");
        message.append("Người soạn: ").append(hrWrites ? HR_LABEL : CANDIDATE_LABEL).append('\n');
        message.append("Người nhận: ").append(hrWrites ? CANDIDATE_LABEL : HR_LABEL).append('\n');
        message.append("Họ tên ứng viên: ").append(sanitize(context.candidateName())).append('\n');
        message.append("Vị trí ứng tuyển: ").append(sanitize(context.jobTitle())).append('\n');
        message.append("Công ty: ").append(sanitize(context.companyName())).append('\n');
        message.append("Trạng thái đơn: ").append(context.applicationStatus().labelVi()).append('\n');
        message.append("Lịch phỏng vấn: ").append(interviewLine(context.interview())).append('\n');
        message.append("</ngu_canh>\n\n");

        message.append("<tin_gan_day>\n");
        for (RecentMessage recent : context.recentMessages()) {
            message.append("<tin vai_tro=\"").append(roleLabel(recent.senderRole())).append("\">")
                    .append(messageText(recent))
                    .append("</tin>\n");
        }
        message.append("</tin_gan_day>\n\n");

        // Ma co dinh do he thong chon (khong phai du lieu nguoi dung) - nam ngoai cac the du lieu.
        message.append("Tình huống: ").append(scenario.name()).append('\n');
        message.append("Giọng văn: ").append(tone.name()).append('\n');

        // R-S4 - customPurpose chi vao prompt khi CUSTOM; tinh huong khac bo qua.
        if (scenario == DraftScenario.CUSTOM && customPurpose != null) {
            message.append("\n<muc_dich>").append(sanitize(customPurpose)).append("</muc_dich>\n");
        }
        return message.toString();
    }

    private static String interviewLine(InterviewSchedule interview) {
        if (interview == null) {
            return NO_INTERVIEW;
        }
        String time = INTERVIEW_TIME.format(interview.scheduledAt());
        if (interview.location() == null || interview.location().isBlank()) {
            return time;
        }
        return time + ", địa điểm: " + sanitize(interview.location());
    }

    // Nhan theo vai tro, KHONG ten nguoi (muc 5; C06 R-G2 - khong ho ten HR).
    private static String roleLabel(MessageSenderRole role) {
        return role == MessageSenderRole.HR ? HR_LABEL : CANDIDATE_LABEL;
    }

    // R-I5 - tin chi co tep -> "(tep dinh kem)", khong ten tep; tin dai hon 1000 code point -> 1000 code point dau + "…".
    private static String messageText(RecentMessage recent) {
        String text = recent.text();
        if (text == null || text.isBlank()) {
            return recent.hasAttachment() ? ATTACHMENT_ONLY : "";
        }
        if (text.codePointCount(0, text.length()) > MAX_MESSAGE_CODE_POINTS) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_MESSAGE_CODE_POINTS)) + TRUNCATION_MARK;
        }
        return sanitize(text);
    }

    // R-I3 - du lieu khong tu dong the duoc. Chi ap cho ban gui AI, khong doi du lieu da luu.
    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('<', '‹').replace('>', '›');
    }

    private static String normalizeLineBreaks(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
