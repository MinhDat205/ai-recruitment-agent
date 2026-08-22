package com.recruitment.resume;

import com.recruitment.ai.cvimprovement.CvImprovementErrorCode;
import com.recruitment.ai.cvimprovement.CvImprovementFailedException;
import com.recruitment.ai.cvimprovement.CvImprovementResult;
import com.recruitment.ai.cvimprovement.CvImprovementService;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

// KHONG @Transactional o day - mau ResumeParsingOrchestrator/ScoreExplanationOrchestrator
// (CLAUDE.md muc 3c): claim ngan (CvImprovementRequestStateService, transaction rieng) -> doc du lieu
// can thiet bang tung repository call doc lap (transaction ngan rieng cho tung call, Spring Data tu
// quan ly) -> goi LLM NGOAI moi transaction (CvImprovementService.generate() co the mat vai giay) ->
// ghi ket qua bang mot transaction NGAN RIENG (CvImprovementRequestStateService).
@Component
public class CvImprovementOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CvImprovementOrchestrator.class);

    static final int MARKET_TREND_JOB_LIMIT = 20;
    static final int MAX_JOB_DESCRIPTION_CHARS = 300;
    static final String NO_OPEN_JOBS_TEXT = "Hiện không có tin tuyển dụng nào đang mở trên hệ thống.";

    static final int MAX_RESUME_TEXT_CHARS = 10_000;
    private static final String RESUME_TEXT_TRUNCATION_MARKER = "\n[...da cat bot do dai...]";

    private final CvImprovementRequestRepository cvImprovementRequestRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final JobRepository jobRepository;
    private final CvImprovementService cvImprovementService;
    private final CvImprovementRequestStateService stateService;

    public CvImprovementOrchestrator(
            CvImprovementRequestRepository cvImprovementRequestRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            JobRepository jobRepository,
            CvImprovementService cvImprovementService,
            CvImprovementRequestStateService stateService) {
        this.cvImprovementRequestRepository = cvImprovementRequestRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.jobRepository = jobRepository;
        this.cvImprovementService = cvImprovementService;
        this.stateService = stateService;
    }

    public void processOne(UUID requestId) {
        if (!stateService.claim(requestId)) {
            return;
        }
        try {
            doProcess(requestId);
        } catch (RuntimeException e) {
            // Luoi an toan cuoi cung, mau ResumeParsingOrchestrator.processOne: bat ky loi khong
            // luong truoc nao sau khi da claim deu phai duoc bat va chuyen thanh markFailed - neu
            // khong, request nay ket vinh vien o RUNNING vi scheduler chi quet PENDING.
            log.debug("Loi khong luong truoc trong luc sinh goi y cai thien CV: requestId={}", requestId, e);
            stateService.markFailed(requestId, CvImprovementErrorCode.LLM_ERROR);
        }
    }

    private void doProcess(UUID requestId) {
        CvImprovementRequest request = cvImprovementRequestRepository.findById(requestId).orElseThrow();
        UUID resumeId = request.getResumeId();

        ResumeParsedData parsedData = resumeParsedDataRepository.findByResumeId(resumeId).orElseThrow();
        String resumeText = buildResumeText(parsedData.getData());

        List<Job> openJobs =
                jobRepository.searchPublicJobs(null, null, null, PageRequest.of(0, MARKET_TREND_JOB_LIMIT)).getContent();
        String marketTrendText = buildMarketTrendText(openJobs);

        CvImprovementResult result;
        try {
            result = cvImprovementService.generate(resumeText, marketTrendText);
        } catch (CvImprovementFailedException e) {
            stateService.markFailed(requestId, e.errorCode());
            return;
        }

        stateService.markDone(requestId, resumeId, result);
    }

    // "Xu huong thi truong" lay toi da 20 job OPEN moi dang nhat (Plan Mode, SUA 1) - JobRepository
    // da co san searchPublicJobs (loc status='OPEN' AND deleted_at IS NULL AND deadline chua qua,
    // ORDER BY created_at DESC). Khong co job OPEN nao (khac voi "co job nhung khac linh vuc" - nhanh
    // do LLM tu xu ly trong prompt) -> truyen mot CAU MO TA CO DINH, KHONG phai chuoi rong: chuoi
    // rong de LLM tu suy dien (co the hieu nham la loi du lieu hoac tu bia noi dung), con cau mo ta
    // ro rang giup LLM ap dung DUNG logic "khong co tin hieu thi truong" ma prompt da mo ta cho nhanh
    // "khong co tin cung linh vuc" - hai tinh huong khac ban chat nhung he qua LLM nen ap dung la
    // GIONG NHAU (khong co can cu thi truong).
    //
    // title/description la NOT NULL o DB (V1__init_schema.sql:65-66: title VARCHAR(200) NOT NULL,
    // description TEXT NOT NULL) - phong null/blank o day la PHONG THU CHIEU SAU, KHONG PHAI truong
    // hop co that: NOT NULL khong chan duoc chuoi RONG/blank, va neu khong phong, truncateDescription(
    // null) se nem NPE - NPE do se roi vao luoi an toan catch (RuntimeException) cua processOne,
    // khien request bi danh FAILED voi LLM_ERROR trong khi nguyen nhan that la du lieu job trong, bao
    // sai nguyen nhan va rat kho truy. Title null/blank thi BO QUA HAN job do khoi van ban - mot tin
    // khong co tieu de khong mang tin hieu thi truong nao. Kiem text.isEmpty() SAU vong lap (khong
    // phai jobs.isEmpty() truoc vong lap): danh sach job co the khong rong nhung toan bo bi bo qua vi
    // thieu title, van phai roi ve NO_OPEN_JOBS_TEXT chu khong tra chuoi rong.
    static String buildMarketTrendText(List<Job> jobs) {
        StringBuilder text = new StringBuilder();
        for (Job job : jobs) {
            String title = job.getTitle();
            if (title == null || title.isBlank()) {
                continue;
            }
            text.append("- ")
                    .append(title)
                    .append(": ")
                    .append(truncateDescription(job.getDescription()))
                    .append('\n');
        }
        if (text.isEmpty()) {
            return NO_OPEN_JOBS_TEXT;
        }
        return text.toString();
    }

    private static String truncateDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        if (description.length() <= MAX_JOB_DESCRIPTION_CHARS) {
            return description;
        }
        return description.substring(0, MAX_JOB_DESCRIPTION_CHARS) + "...";
    }

    // Render ResumeParsedPayload thanh van ban - CO cat do dai bang cach don gian (cat duoi + hau
    // to, KHONG head+tail nhu ResumeParsingService.truncateForPrompt): dau vao la du lieu DA CO CAU
    // TRUC (da qua D1 trich xuat, gioi han boi ResumeParsingService.MAX_PROMPT_CHARS=45000 roi), nen
    // thu tu render CO CHU DICH - contact/hoc van/kinh nghiem/ky nang truoc (quan trong nhat cho so
    // sanh tu khoa), du an sau cung - neu phai cat, cat dung phan it quan trong nhat truoc.
    static String buildResumeText(ResumeParsedPayload payload) {
        StringBuilder text = new StringBuilder();

        ResumeParsedPayload.Contact contact = payload.contact();
        if (contact != null) {
            text.append("Thong tin lien he: ")
                    .append(nullToEmpty(contact.fullName()))
                    .append(", ")
                    .append(nullToEmpty(contact.email()))
                    .append('\n');
        }

        if (!payload.education().isEmpty()) {
            text.append("Hoc van:\n");
            for (ResumeParsedPayload.Education edu : payload.education()) {
                text.append("- ")
                        .append(nullToEmpty(edu.degree()))
                        .append(" ")
                        .append(nullToEmpty(edu.major()))
                        .append(" tai ")
                        .append(nullToEmpty(edu.school()))
                        .append('\n');
            }
        }

        if (!payload.experience().isEmpty()) {
            text.append("Kinh nghiem lam viec:\n");
            for (ResumeParsedPayload.Experience exp : payload.experience()) {
                text.append("- ")
                        .append(nullToEmpty(exp.title()))
                        .append(" tai ")
                        .append(nullToEmpty(exp.company()))
                        .append(": ")
                        .append(nullToEmpty(exp.description()))
                        .append('\n');
            }
        }

        if (!payload.skills().isEmpty()) {
            text.append("Ky nang: ").append(String.join(", ", payload.skills())).append('\n');
        }

        if (!payload.certifications().isEmpty()) {
            text.append("Chung chi:\n");
            for (ResumeParsedPayload.Certification cert : payload.certifications()) {
                text.append("- ")
                        .append(nullToEmpty(cert.name()))
                        .append(" (")
                        .append(nullToEmpty(cert.issuer()))
                        .append(")\n");
            }
        }

        if (!payload.projects().isEmpty()) {
            text.append("Du an:\n");
            for (ResumeParsedPayload.Project project : payload.projects()) {
                text.append("- ")
                        .append(nullToEmpty(project.name()))
                        .append(": ")
                        .append(nullToEmpty(project.description()))
                        .append('\n');
            }
        }

        return truncateResumeText(text.toString());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncateResumeText(String text) {
        if (text.length() <= MAX_RESUME_TEXT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_RESUME_TEXT_CHARS) + RESUME_TEXT_TRUNCATION_MARKER;
    }
}
