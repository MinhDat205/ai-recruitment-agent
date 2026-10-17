package com.recruitment.cvbuilder;

import com.recruitment.resume.ResumeParsingStateService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-U12 R-W2 - MOT transaction ngan, goi SAU khi da dung PDF, trich raw_text va luu file (CLAUDE.md muc 3c: bean ghi
// rieng, facade CvBuilderService khong @Transactional). Ba hang resumes + resume_parsed_data + cv_builder_documents cung
// commit hoac cung rollback; nem bat ky exception nao -> khong con hang nao cua lan nay (noi goi xoa file - R-W3).
@Service
public class CvBuilderStateService {

    private final ResumeParsingStateService resumeParsingStateService;
    private final CvBuilderDocumentRepository documentRepository;
    private final CvBuilderPayloadMapper payloadMapper;

    public CvBuilderStateService(
            ResumeParsingStateService resumeParsingStateService,
            CvBuilderDocumentRepository documentRepository,
            CvBuilderPayloadMapper payloadMapper) {
        this.resumeParsingStateService = resumeParsingStateService;
        this.documentRepository = documentRepository;
        this.payloadMapper = payloadMapper;
    }

    // form DA chuan hoa va DA qua validator; payload suy ra xac dinh tu chinh form nay (R-M1), khong nhan rieng.
    // Tra id CV moi.
    @Transactional
    public UUID create(
            UUID candidateId,
            CvTemplate template,
            CvBuilderForm form,
            String versionLabel,
            String fileUrl,
            String fileName,
            long fileSize,
            String rawText) {
        UUID resumeId = resumeParsingStateService.insertBuiltResume(
                candidateId, fileUrl, fileName, fileSize, versionLabel, rawText, payloadMapper.toPayload(form));

        CvBuilderDocument document = new CvBuilderDocument();
        document.setResumeId(resumeId);
        document.setTemplate(template);
        document.setFormData(form);
        documentRepository.saveAndFlush(document);
        return resumeId;
    }
}
