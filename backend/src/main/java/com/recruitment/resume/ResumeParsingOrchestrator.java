package com.recruitment.resume;

import com.recruitment.storage.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

// KHONG method nao o day duoc @Transactional - xem CLAUDE.md muc 3c. Neu them @Transactional vao
// processOne(), transaction se giu ket noi suot thoi gian goi LLM (co the toi 30 giay), can pool
// khi co nhieu CV cho xu ly cung luc.
@Component
public class ResumeParsingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ResumeParsingOrchestrator.class);

    private final ResumeParsingStateService stateService;
    private final ResumeRepository resumeRepository;
    private final StorageService storageService;
    private final TextExtractor textExtractor;
    private final ResumeParsingService resumeParsingService;

    public ResumeParsingOrchestrator(
            ResumeParsingStateService stateService,
            ResumeRepository resumeRepository,
            StorageService storageService,
            TextExtractor textExtractor,
            ResumeParsingService resumeParsingService) {
        this.stateService = stateService;
        this.resumeRepository = resumeRepository;
        this.storageService = storageService;
        this.textExtractor = textExtractor;
        this.resumeParsingService = resumeParsingService;
    }

    public void processOne(UUID resumeId) {
        if (!stateService.claim(resumeId)) {
            return;
        }
        try {
            doProcess(resumeId);
        } catch (RuntimeException e) {
            // Luoi an toan cuoi cung: BAT KY loi khong luong truoc nao sau khi da claim (file bien
            // mat khoi storage, markDone() tu nem do race UNIQUE...) deu phai duoc bat va chuyen
            // thanh markFailed - neu khong, resume nay ket vinh vien o PROCESSING vi scheduler chi
            // quet PENDING, khong bao gio nhat lai duoc PROCESSING.
            log.debug("Loi khong luong truoc trong luc xu ly resume: resumeId={}", resumeId, e);
            stateService.markFailed(resumeId, ResumeParsingErrorCode.LLM_ERROR);
        }
    }

    private void doProcess(UUID resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        Resource fileResource =
                storageService
                        .load(resume.getFileUrl())
                        .orElseThrow(() -> new NoSuchElementException("Khong tim thay file da luu: " + resumeId));

        String rawText;
        try (InputStream content = fileResource.getInputStream()) {
            rawText = textExtractor.extract(content, resume.getFileType());
        } catch (IOException e) {
            throw new UncheckedIOException("Khong doc duoc file da luu: " + resumeId, e);
        } catch (ResumeExtractionException e) {
            log.debug("File CV hong, khong doc duoc: resumeId={}", resumeId, e);
            stateService.markFailed(resumeId, ResumeParsingErrorCode.EXTRACT_CORRUPT);
            return;
        }

        if (rawText.isBlank()) {
            stateService.markFailed(resumeId, ResumeParsingErrorCode.EXTRACT_EMPTY);
            return;
        }

        ResumeParsingResult result;
        try {
            result = resumeParsingService.parse(resumeId, rawText);
        } catch (ResumeParsingFailedException e) {
            stateService.markFailed(resumeId, e.errorCode());
            return;
        }

        stateService.markDone(
                resumeId, rawText, result.payload(), result.model(), result.promptVersion(), result.tokenUsage());
    }
}
