package com.recruitment.resume;

import com.recruitment.common.exception.InvalidResumeFileException;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.resume.dto.ResumeResponse;
import com.recruitment.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeService {

    private static final String RESUME_SUBDIRECTORY = "resumes";
    private static final long MAX_RESUME_SIZE_BYTES = 10L * 1024 * 1024;

    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] DOCX_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};

    private final ResumeRepository resumeRepository;
    private final StorageService storageService;

    public ResumeService(ResumeRepository resumeRepository, StorageService storageService) {
        this.resumeRepository = resumeRepository;
        this.storageService = storageService;
    }

    public List<ResumeResponse> listMine(UUID candidateId) {
        return resumeRepository.findByCandidateIdOrderByUploadedAtDesc(candidateId).stream()
                .map(ResumeService::toResponse)
                .toList();
    }

    @Transactional
    public ResumeResponse upload(UUID candidateId, MultipartFile file, String versionLabel) {
        if (file.isEmpty()) {
            throw new InvalidResumeFileException("File CV đang trống");
        }
        if (file.getSize() > MAX_RESUME_SIZE_BYTES) {
            throw new InvalidResumeFileException("File CV vượt quá 10MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidResumeFileException("Không đọc được file CV");
        }
        ResumeFileType fileType =
                detectFileType(content)
                        .orElseThrow(
                                () -> new InvalidResumeFileException("Định dạng không hợp lệ, chỉ nhận PDF hoặc DOCX"));

        UUID fileId = UUID.randomUUID();
        String filename = fileId + "." + fileType.name().toLowerCase();
        storageService.store(RESUME_SUBDIRECTORY, filename, new ByteArrayInputStream(content));
        String key = RESUME_SUBDIRECTORY + "/" + filename;

        // CV dau tien cua candidate luon la ban chinh - khong bat ho phai goi setPrimary rieng.
        boolean isFirst = !resumeRepository.existsByCandidateId(candidateId);

        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl(key);
        resume.setFileName(originalFileName(file));
        resume.setFileType(fileType);
        resume.setFileSize(file.getSize());
        resume.setVersionLabel(trimToNull(versionLabel));
        resume.setPrimary(isFirst);
        resume.setParseStatus(ParseStatus.PENDING);
        return toResponse(resumeRepository.save(resume));
    }

    @Transactional
    public ResumeResponse setPrimary(UUID candidateId, UUID resumeId) {
        Resume resume =
                resumeRepository
                        .findByIdAndCandidateId(resumeId, candidateId)
                        .orElseThrow(() -> new ResumeNotFoundException(resumeId));

        // Bo co ban cu truoc, flush ngay de UPDATE nay chay truoc UPDATE ben duoi - trong khoanh
        // khac nao cung khong co 2 dong cung is_primary=true, khong vi pham
        // uq_resume_primary_per_candidate.
        resumeRepository
                .findByCandidateIdAndIsPrimaryTrue(candidateId)
                .filter(old -> !old.getId().equals(resumeId))
                .ifPresent(
                        old -> {
                            old.setPrimary(false);
                            resumeRepository.saveAndFlush(old);
                        });

        resume.setPrimary(true);
        return toResponse(resumeRepository.save(resume));
    }

    public ResumeDownload downloadMine(UUID candidateId, UUID resumeId) {
        Resume resume =
                resumeRepository
                        .findByIdAndCandidateId(resumeId, candidateId)
                        .orElseThrow(() -> new ResumeNotFoundException(resumeId));
        var resource = storageService.load(resume.getFileUrl()).orElseThrow(() -> new ResumeNotFoundException(resumeId));
        MediaType contentType =
                resume.getFileType() == ResumeFileType.PDF
                        ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return new ResumeDownload(resource, resume.getFileName(), contentType);
    }

    private static Optional<ResumeFileType> detectFileType(byte[] content) {
        if (matchesAt(content, PDF_SIGNATURE)) {
            return Optional.of(ResumeFileType.PDF);
        }
        if (matchesAt(content, DOCX_SIGNATURE)) {
            return Optional.of(ResumeFileType.DOCX);
        }
        return Optional.empty();
    }

    private static boolean matchesAt(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String originalFileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "resume" : name;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResumeResponse toResponse(Resume r) {
        return new ResumeResponse(
                r.getId(),
                r.getFileName(),
                r.getFileType(),
                r.getFileSize(),
                r.getVersionLabel(),
                r.isPrimary(),
                r.getParseStatus(),
                r.getParseError(),
                r.getUploadedAt());
    }
}
