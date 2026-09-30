package com.recruitment.resume;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Trich xuat lai CV schema cu (FR-C05 R-R5). KHONG method nao @Transactional (CLAUDE.md muc 3c): claim
// ngan -> doc raw_text DA LUU (transaction doc rieng cua repository) -> goi LLM NGOAI transaction ->
// ghi ket qua trong transaction ngan (ResumeReparseStateService).
// Khac ResumeParsingOrchestrator o ba cho co chu dich:
// - Dau vao la resume_parsed_data.raw_text da luu - KHONG doc lai file, KHONG chay lai TextExtractor
//   (R-R5, R-R6: raw_text khong doi thi evidence cua moi luot cham cu van khop).
// - resumes.parse_status khong bi doc/ghi o bat ky buoc nao (giu DONE - CV khong bien khoi form ung
//   tuyen, cham diem van tao duoc).
// - That bai chi danh dau yeu cau FAILED; resume_parsed_data khong bi cham.
@Component
public class ResumeReparseOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ResumeReparseOrchestrator.class);

    private final ResumeReparseStateService stateService;
    private final ResumeReparseRequestRepository requestRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final ResumeParsingService resumeParsingService;

    public ResumeReparseOrchestrator(
            ResumeReparseStateService stateService,
            ResumeReparseRequestRepository requestRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            ResumeParsingService resumeParsingService) {
        this.stateService = stateService;
        this.requestRepository = requestRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.resumeParsingService = resumeParsingService;
    }

    public void processOne(UUID requestId) {
        if (!stateService.claim(requestId)) {
            return;
        }
        try {
            doProcess(requestId);
        } catch (RuntimeException e) {
            // Luoi an toan cuoi (mau ResumeParsingOrchestrator.processOne): loi khong luong truoc sau khi
            // claim phai chuyen thanh FAILED, khong thi yeu cau ket o RUNNING va partial unique index
            // chan moi yeu cau moi cho CV nay. Chi ghi ma loi chuan hoa, chi tiet o log.debug.
            log.debug("Loi khong luong truoc khi trich xuat lai CV: requestId={}", requestId, e);
            stateService.markFailed(requestId, ResumeParsingErrorCode.LLM_ERROR);
        }
    }

    private void doProcess(UUID requestId) {
        ResumeReparseRequest request = requestRepository.findById(requestId).orElseThrow();
        UUID resumeId = request.getResumeId();
        ResumeParsedData parsedData = resumeParsedDataRepository.findByResumeId(resumeId).orElseThrow();

        ResumeParsingResult result;
        try {
            result = resumeParsingService.parse(resumeId, parsedData.getRawText());
        } catch (ResumeParsingFailedException e) {
            if (e.errorCode() == ResumeParsingErrorCode.LLM_TEMPORARILY_UNAVAILABLE) {
                stateService.markTemporaryFailure(requestId, e.errorCode());
            } else {
                stateService.markFailed(requestId, e.errorCode());
            }
            return;
        }

        stateService.markDone(requestId, parsedData.getId(), result);
    }
}
