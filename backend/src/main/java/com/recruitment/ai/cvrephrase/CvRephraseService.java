package com.recruitment.ai.cvrephrase;

import com.recruitment.ai.sync.SyncAiCaller;
import java.text.Normalizer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

// FR-U12 R-A3 - goi y dien dat MOT doan mo ta (kinh nghiem hoac du an) qua K3. Doc lap voi tang persistence: khong
// repository, khong entity, khong mo transaction (K3 tu chan khi luong goi dang co transaction - R-K3-4). Khong luu gi,
// khong K4 (R-A5). Thoi gian cho, thu lai, ma loi, log: DUNG K3 - khong them thu lai o day.
//
// R-I1: system message = CHI file prompt + {format} (K3 tu dien) + {kindLabel} (chuoi co dinh chon theo enum) - doan van
// cua ung vien KHONG vao system message. R-I2: doan van nam trong khoi <doan_van> cua user message, < > doi thanh ‹ ›.
// Khong import cvbuilder/, resume/ (muc 4.2).
@Service
public class CvRephraseService {

    private static final Logger log = LoggerFactory.getLogger(CvRephraseService.class);

    // Gan lien ten file prompt (cv-rephrase-v1.st) - khai MOT CHO DUY NHAT, chi dung nap resource (khong luu o dau).
    static final String PROMPT_VERSION = "cv-rephrase-v1";

    // R-I1 - gia tri {kindLabel}, chon theo CvRephraseKind, khong lay tu request.
    static final String EXPERIENCE_LABEL = "mô tả kinh nghiệm làm việc";
    static final String PROJECT_LABEL = "mô tả dự án";

    private final ChatClient chatClient;
    private final Resource promptResource;
    private final SyncAiCaller syncAiCaller;

    public CvRephraseService(
            ChatClient cvRephraseChatClient,
            @Value("classpath:ai/prompt/" + PROMPT_VERSION + ".st") Resource promptResource,
            SyncAiCaller syncAiCaller) {
        this.chatClient = cvRephraseChatClient;
        this.promptResource = promptResource;
        this.syncAiCaller = syncAiCaller;
    }

    // text: DA chuan hoa R-F3 (o nhieu dong) va DA qua R-A2 o facade. Tra goi y da doi nguoc ‹ › va chuan hoa R-F3 -
    // dung chuoi validator da kiem. Loi K3 noi len nguyen AiSyncFailedException (GlobalExceptionHandler -> 502/503/504)
    // - KHONG bat o day (R-A3).
    public String rephrase(CvRephraseKind kind, String text) {
        String userMessage = buildUserMessage(text);
        log.debug("User message goi y dien dat ({}): {}", PROMPT_VERSION, userMessage);

        CvRephrasePayload payload = syncAiCaller
                .call(
                        chatClient,
                        promptResource,
                        Map.of("kindLabel", kindLabel(kind)),
                        userMessage,
                        CvRephrasePayload.class,
                        candidate -> CvRephraseValidator.isValid(text, suggestionOf(candidate)))
                .entity();
        log.debug("Output goi y dien dat hop le: {}", payload);
        return suggestionOf(payload);
    }

    static String kindLabel(CvRephraseKind kind) {
        return switch (kind) {
            case EXPERIENCE -> EXPERIENCE_LABEL;
            case PROJECT -> PROJECT_LABEL;
        };
    }

    // R-I2 - user message dung trong Java (muc 13.4): the mo + xuong dong + doan da doi < > + xuong dong + the dong.
    static String buildUserMessage(String text) {
        return "<doan_van>\n" + sanitize(text) + "\n</doan_van>";
    }

    // R-I2 - du lieu khong tu dong the duoc. Chi ap cho ban gui AI.
    static String sanitize(String value) {
        return value.replace('<', '‹').replace('>', '›');
    }

    // R-I2 - doi nguoc cho goi y cua AI. Gioi han da biet: doan goc co san ‹ › that se thanh < > trong goi y.
    static String unsanitize(String value) {
        return value.replace('‹', '<').replace('›', '>');
    }

    // out cua R-A4: doi nguoc ‹ › roi chuan hoa R-F3. HAM TONG (goi trong predicate K3): payload/suggestion null -> null.
    static String suggestionOf(CvRephrasePayload payload) {
        if (payload == null || payload.suggestion() == null) {
            return null;
        }
        return normalizeMultiLine(unsanitize(payload.suggestion()));
    }

    // R-F3 cho o nhieu dong: NFC; CRLF, CR -> LF; tab va khoang trang Unicode loai Zs -> dau cach; strip; rong -> null.
    // Cung luat CvBuilderFormNormalizer.multiLine; cai rieng o day vi ai/cvrephrase/ KHONG import cvbuilder/ (muc 4.2).
    static String normalizeMultiLine(String value) {
        String s = Normalizer.normalize(value, Normalizer.Form.NFC);
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            sb.append(ch == '\t' || Character.getType(ch) == Character.SPACE_SEPARATOR ? ' ' : ch);
        }
        String stripped = sb.toString().strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
