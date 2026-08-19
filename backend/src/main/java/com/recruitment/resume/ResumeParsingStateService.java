package com.recruitment.resume;

import com.recruitment.common.exception.ResumeNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng, tach khoi ResumeParsingOrchestrator - xem CLAUDE.md muc 3c. Self-invocation
// (goi this.method() tu trong cung mot class) khong di qua proxy Spring, nen @Transactional se
// khong mo transaction nao ca. Ca 3 method o day deu ngan, khong bao gio bao quanh loi goi LLM.
@Service
public class ResumeParsingStateService {

    private final ResumeRepository resumeRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;

    public ResumeParsingStateService(
            ResumeRepository resumeRepository, ResumeParsedDataRepository resumeParsedDataRepository) {
        this.resumeRepository = resumeRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
    }

    @Transactional
    public boolean claim(UUID resumeId) {
        return resumeRepository.claimForProcessing(resumeId) == 1;
    }

    // Hai cau ghi (resumes + resume_parsed_data) phai cung thanh cong hoac cung rollback - neu chi
    // ghi duoc resumes.parse_status=DONE ma insert resume_parsed_data loi (vd vi pham UNIQUE do
    // race), trang thai se noi doi la DONE nhung khong co data.
    @Transactional
    public void markDone(
            UUID resumeId,
            String rawText,
            ResumeParsedPayload payload,
            String model,
            String promptVersion,
            Integer tokenUsage) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow(() -> new ResumeNotFoundException(resumeId));
        resume.setParseStatus(ParseStatus.DONE);
        resumeRepository.save(resume);

        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText(rawText);
        data.setData(payload);
        data.setModel(model);
        data.setPromptVersion(promptVersion);
        data.setTokenUsage(tokenUsage);
        resumeParsedDataRepository.save(data);
    }

    @Transactional
    public void markFailed(UUID resumeId, ResumeParsingErrorCode errorCode) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow(() -> new ResumeNotFoundException(resumeId));
        resume.setParseStatus(ParseStatus.FAILED);
        resume.setParseError(errorCode.formatted());
        resumeRepository.save(resume);
    }
}
