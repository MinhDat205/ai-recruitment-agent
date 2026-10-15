package com.recruitment.ai.cvqa;

import com.recruitment.ai.citation.VerbatimQuoteVerifier;
import com.recruitment.ai.cvqa.CvQaPayload.Citation;
import com.recruitment.ai.sync.SyncAiCaller;
import com.recruitment.aicontext.ContextViewer;
import com.recruitment.aicontext.CvQaContext;
import com.recruitment.aicontext.CvQaContext.JobPosting;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

// FR-C08 - hoi dap CV co trich dan. Doc lap voi tang persistence: khong repository, khong entity, khong mo transaction
// (mau MessageDraftService). Nhan ngu canh K1 da gom san (du lieu THO) + cau hoi da chuan hoa (R-H1, facade lam); dung
// user message theo R-I3, goi K3 (SyncAiCaller) voi ham kiem R-O2/R-O3. Khong luu gi (R-N1), khong import cvqa/.
//
// R-I2: system message = CHI file prompt + {format} (K3 tu dien) + {viewer} (chuoi co dinh chon theo ContextViewer) -
// khong chuoi du lieu nao vao system message. R-K2-7: moi phep so khop trich dan voi CV di qua K2
// (VerbatimQuoteVerifier) - lop nay KHONG tu so khop van ban CV.
@Service
public class CvQaService {

    private static final Logger log = LoggerFactory.getLogger(CvQaService.class);

    // Gan lien ten file prompt (cv-qa-v1.st) - khai MOT CHO DUY NHAT, chi dung nap resource (khong luu o dau).
    static final String PROMPT_VERSION = "cv-qa-v1";

    // R-A3 - cung nguong va chien luoc CriterionScoringService.MAX_PROMPT_CHARS (60% dau + 40% cuoi), cai rieng o day,
    // KHONG import ai/criterion/ (tien le trung lap co chu dich H04 <-> D1).
    static final int MAX_PROMPT_CHARS = 45_000;
    // Dong dau luoc PHAI trung nguyen van dong "[...phần giữa CV đã được lược bớt do độ dài...]" trong
    // ai/prompt/cv-qa-v1.st (prompt bao AI dong nay khong phai noi dung CV) - sua mot noi thi sua ca hai.
    static final String TRUNCATION_MARKER = "\n\n[...phần giữa CV đã được lược bớt do độ dài...]\n\n";

    // R-O2, R-O3
    static final int MAX_ANSWER_LENGTH = 800;
    static final int MIN_CITATIONS = 1;
    static final int MAX_CITATIONS = 5;
    static final int MIN_QUOTE_LENGTH = 15;
    static final int MAX_QUOTE_LENGTH = 200;

    // R-I2 - gia tri {viewer}, chon theo ContextViewer, khong lay tu request.
    static final String HR_VIEWER = "nhà tuyển dụng";
    static final String CANDIDATE_VIEWER = "ứng viên";
    // R-I3 - jobs.requirements null.
    static final String NO_REQUIREMENTS = "(không có)";

    // R-O2 buoc 1, S3 - tap ly do hop le theo phia.
    private static final Set<CvQaRefusalReason> HR_REFUSALS = EnumSet.of(
            CvQaRefusalReason.PERSONAL_INFO, CvQaRefusalReason.HIRING_JUDGEMENT, CvQaRefusalReason.OUT_OF_SCOPE);
    private static final Set<CvQaRefusalReason> CANDIDATE_REFUSALS = EnumSet.of(
            CvQaRefusalReason.HIRING_JUDGEMENT, CvQaRefusalReason.CV_IMPROVEMENT, CvQaRefusalReason.OUT_OF_SCOPE);

    private final ChatClient chatClient;
    private final Resource promptResource;
    private final SyncAiCaller syncAiCaller;

    public CvQaService(
            ChatClient cvQaChatClient,
            @Value("classpath:ai/prompt/" + PROMPT_VERSION + ".st") Resource promptResource,
            SyncAiCaller syncAiCaller) {
        this.chatClient = cvQaChatClient;
        this.promptResource = promptResource;
        this.syncAiCaller = syncAiCaller;
    }

    // question: da qua R-H1 (doi CRLF, trim, 1-500 ky tu) va bo chan tu khoa R-G o facade. Loi K3 noi len nguyen
    // AiSyncFailedException (GlobalExceptionHandler -> 502/503/504) - KHONG bat o day, KHONG thu lai them (R-A2).
    public CvQaAnswer answer(CvQaContext context, String question) {
        String rawText = context.resumeText();
        ContextViewer viewer = context.viewer();
        String userMessage = buildUserMessage(context, question);
        log.debug("User message hoi dap CV ({}): {}", PROMPT_VERSION, userMessage);

        CvQaPayload payload = syncAiCaller
                .call(
                        chatClient,
                        promptResource,
                        Map.of("viewer", viewerLabel(viewer)),
                        userMessage,
                        CvQaPayload.class,
                        candidate -> isValidPayload(candidate, viewer, rawText))
                .entity();
        log.debug("Output hoi dap CV hop le: {}", payload);
        return toAnswer(payload, rawText);
    }

    // R-O2/R-O3 - ham kiem truyen cho K3; false -> output hong, K3 thu lai 1 lan. HAM TONG (L7): khong bao gio nem
    // exception - SyncAiCaller goi validator ngoai try, exception o day thanh 500 thay vi thu lai/502. Phan tu Citation
    // null hay quote null = hong. rawText la raw_text DAY DU (khong phai ban da cat R-A3, khong phai ban da doi < >).
    static boolean isValidPayload(CvQaPayload payload, ContextViewer viewer, String rawText) {
        if (payload == null || viewer == null || rawText == null) {
            return false;
        }
        List<Citation> citations = payload.citations();
        if (payload.refused() != null) {
            CvQaRefusalReason reason = refusalReasonOf(payload.refused());
            return reason != null
                    && allowedRefusals(viewer).contains(reason)
                    && citations.isEmpty()
                    && !Boolean.TRUE.equals(payload.notFound());
        }
        if (Boolean.TRUE.equals(payload.notFound())) {
            return citations.isEmpty();
        }
        if (payload.answer() == null) {
            return false;
        }
        // strip() (khong phai trim()): bo ca khoang trang Unicode nhu U+2003 - answer chi gom khoang trang la rong (L22).
        String answer = normalizeLineBreaks(payload.answer()).strip();
        if (answer.isEmpty() || answer.length() > MAX_ANSWER_LENGTH) {
            return false;
        }
        if (citations.size() < MIN_CITATIONS || citations.size() > MAX_CITATIONS) {
            return false;
        }
        for (Citation citation : citations) {
            if (!isValidCitation(citation, rawText)) {
                return false;
            }
        }
        return true;
    }

    // R-O3 - mot trich dan: doi nguoc ‹ › (R-I3) TRUOC khi kiem; do dai sau chuan hoa khoang trang trong [15, 200];
    // khop nguyen van qua K2 tren raw_text day du.
    private static boolean isValidCitation(Citation citation, String rawText) {
        if (citation == null || citation.quote() == null) {
            return false;
        }
        String quote = unsanitize(citation.quote());
        int length = VerbatimQuoteVerifier.normalizeWhitespace(quote).length();
        if (length < MIN_QUOTE_LENGTH || length > MAX_QUOTE_LENGTH) {
            return false;
        }
        return VerbatimQuoteVerifier.isVerbatim(quote, rawText);
    }

    // Chi goi voi payload DA qua isValidPayload (K3 chi tra entity hop le).
    static CvQaAnswer toAnswer(CvQaPayload payload, String rawText) {
        if (payload.refused() != null) {
            return CvQaAnswer.refused(refusalReasonOf(payload.refused()));
        }
        boolean truncated = isTruncated(rawText);
        if (Boolean.TRUE.equals(payload.notFound())) {
            return CvQaAnswer.notFound(truncated);
        }
        String answer = normalizeLineBreaks(payload.answer()).strip();
        return CvQaAnswer.answered(answer, distinctQuotes(payload.citations()), truncated);
    }

    // R-O4 - gop trich dan co normalizeWhitespace giong nhau, giu lan dau, giu thu tu. R-O5 - tra ban da doi nguoc ‹ ›,
    // trim hai dau, khoang trang ben trong giu nhu AI tra.
    static List<String> distinctQuotes(List<Citation> citations) {
        Set<String> seen = new HashSet<>();
        List<String> quotes = new ArrayList<>(citations.size());
        for (Citation citation : citations) {
            String quote = unsanitize(citation.quote());
            if (seen.add(VerbatimQuoteVerifier.normalizeWhitespace(quote))) {
                quotes.add(quote.trim());
            }
        }
        return quotes;
    }

    // R-A3, O8 - tinh tu do dai raw_text DAY DU, khong tu ban da cat.
    static boolean isTruncated(String rawText) {
        return rawText.length() > MAX_PROMPT_CHARS;
    }

    // R-A3 - giu dau + cuoi, cat phan giua; tong do dai ket qua = MAX_PROMPT_CHARS. Chi dung cho ban gui AI.
    static String truncateForPrompt(String rawText) {
        if (!isTruncated(rawText)) {
            return rawText;
        }
        // Chi ghi do dai, KHONG ghi noi dung CV.
        log.debug("Cat bot raw_text truoc khi goi AI hoi dap CV: doDaiGoc={}", rawText.length());
        int keepLength = MAX_PROMPT_CHARS - TRUNCATION_MARKER.length();
        int headLength = (int) (keepLength * 0.6);
        int tailLength = keepLength - headLength;
        return rawText.substring(0, headLength) + TRUNCATION_MARKER + rawText.substring(rawText.length() - tailLength);
    }

    // R-I3 - du lieu dat trong khoi the co dinh; ca nam chuoi (cau hoi, CV, tieu de, mo ta, yeu cau) doi < > thanh ‹ ›.
    // CV cat (R-A3) TRUOC roi moi doi ky tu - dau luoc khong chua < >. Khoi <tin_tuyen_dung> chi co phia HR (job != null).
    static String buildUserMessage(CvQaContext context, String question) {
        StringBuilder message = new StringBuilder();
        message.append("<cau_hoi>\n").append(sanitize(question)).append("\n</cau_hoi>\n\n");
        message.append("<cv>\n").append(sanitize(truncateForPrompt(context.resumeText()))).append("\n</cv>\n");

        JobPosting job = context.job();
        if (job != null) {
            String requirements = job.requirements() == null ? NO_REQUIREMENTS : sanitize(job.requirements());
            message.append("\n<tin_tuyen_dung>\n");
            message.append("<tieu_de>\n").append(sanitize(job.title())).append("\n</tieu_de>\n");
            message.append("<mo_ta>\n").append(sanitize(job.description())).append("\n</mo_ta>\n");
            message.append("<yeu_cau>\n").append(requirements).append("\n</yeu_cau>\n");
            message.append("</tin_tuyen_dung>\n");
        }
        return message.toString();
    }

    static String viewerLabel(ContextViewer viewer) {
        return viewer == ContextViewer.HR ? HR_VIEWER : CANDIDATE_VIEWER;
    }

    // R-I3 - du lieu khong tu dong the duoc. Chi ap cho ban gui AI, khong doi du lieu da luu.
    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('<', '‹').replace('>', '›');
    }

    // R-I3 - doi nguoc cho trich dan cua AI de khop raw_text that. Gioi han da biet (O5): CV co san ‹ › that thi trich
    // dan chua chung khong qua K2.
    static String unsanitize(String value) {
        return value.replace('‹', '<').replace('›', '>');
    }

    // Ten la (khong thuoc enum) -> null; khong dung valueOf de ham kiem khong nem exception (L7).
    private static CvQaRefusalReason refusalReasonOf(String name) {
        for (CvQaRefusalReason reason : CvQaRefusalReason.values()) {
            if (reason.name().equals(name)) {
                return reason;
            }
        }
        return null;
    }

    private static Set<CvQaRefusalReason> allowedRefusals(ContextViewer viewer) {
        return viewer == ContextViewer.HR ? HR_REFUSALS : CANDIDATE_REFUSALS;
    }

    private static String normalizeLineBreaks(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
