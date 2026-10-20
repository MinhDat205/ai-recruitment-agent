package com.recruitment.ai.jobsearch;

import com.recruitment.ai.sync.SyncAiCaller;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

// FR-U13 R-A1 - tach dieu kien tu MOT cau tim viec qua K3: dung mot loi goi SyncAiCaller.call (thu lai toi da 1 lan chi
// khi output hong - hanh vi K3, khong them thu lai o day). Doc lap voi tang persistence: khong repository, khong mo
// transaction (K3 tu chan khi luong goi dang co transaction). Khong luu gi, khong K4, khong log cau (R-L).
//
// R-P1: system message = CHI file prompt + {format} (K3 tu dien) + {provinceCatalog} + {industryCatalog} (chuoi he thong
// co dinh do jobsearch/ dung tu CatalogRegistry - R-A2) - cau cua ung vien KHONG vao system message. R-P2: cau nam trong
// khoi <cau_tim_kiem> cua user message, < > doi thanh ‹ ›. Khong import jobsearch/, job/, resume/, catalog/ (muc 4.2).
@Service
public class JobSearchParseService {

    // Gan lien ten file prompt (job-search-parse-v2.st) - khai MOT CHO DUY NHAT, chi dung nap resource (khong luu o dau).
    // v2 (FR-U13 L57): khong co luong thi bo truong salary; chi co mot moc thi bo truong moc con lai.
    static final String PROMPT_VERSION = "job-search-parse-v2";

    private final ChatClient chatClient;
    private final Resource promptResource;
    private final SyncAiCaller syncAiCaller;

    public JobSearchParseService(
            ChatClient jobSearchChatClient,
            @Value("classpath:ai/prompt/" + PROMPT_VERSION + ".st") Resource promptResource,
            SyncAiCaller syncAiCaller) {
        this.chatClient = jobSearchChatClient;
        this.promptResource = promptResource;
        this.syncAiCaller = syncAiCaller;
    }

    // q: DA chuan hoa R-I2 va DA qua R-I3 o facade. Tra payload DA doi nguoc ‹ › (R-P2) - dung chuoi validator da kiem.
    // Loi K3 noi len nguyen AiSyncFailedException (GlobalExceptionHandler -> 502/503/504) - KHONG bat o day.
    public JobSearchParsePayload parse(String q, String provinceCatalog, String industryCatalog) {
        JobSearchParsePayload payload = syncAiCaller
                .call(
                        chatClient,
                        promptResource,
                        Map.of("provinceCatalog", provinceCatalog, "industryCatalog", industryCatalog),
                        buildUserMessage(q),
                        JobSearchParsePayload.class,
                        candidate -> JobSearchParseValidator.isValid(candidate, q))
                .entity();
        return JobSearchParseValidator.unsanitize(payload);
    }

    // R-P2 - user message dung trong Java (khuon U12 CvRephraseService): the mo + xuong dong + cau da doi < > + xuong
    // dong + the dong.
    static String buildUserMessage(String q) {
        return "<cau_tim_kiem>\n" + sanitize(q) + "\n</cau_tim_kiem>";
    }

    // R-P2 - du lieu khong tu dong the duoc. Chi ap cho ban gui AI.
    static String sanitize(String value) {
        return value.replace('<', '‹').replace('>', '›');
    }
}
