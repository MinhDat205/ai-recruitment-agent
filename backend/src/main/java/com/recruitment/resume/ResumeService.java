package com.recruitment.resume;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.catalog.dto.CatalogResponse;
import com.recruitment.common.exception.InvalidResumeFileException;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.common.exception.ResumeParsedDataNotFoundException;
import com.recruitment.common.exception.ResumeReparseInProgressException;
import com.recruitment.common.exception.ResumeReparseNotAllowedException;
import com.recruitment.common.exception.ResumeRetryNotAllowedException;
import com.recruitment.resume.dto.ResumeParsedDataResponse;
import com.recruitment.resume.dto.ResumeReparseStatusResponse;
import com.recruitment.resume.dto.ResumeResponse;
import com.recruitment.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final StorageService storageService;
    private final ResumeParsingStateService resumeParsingStateService;
    private final ResumeReparseRequestRepository resumeReparseRequestRepository;
    private final CatalogRegistry catalogRegistry;

    public ResumeService(
            ResumeRepository resumeRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            StorageService storageService,
            ResumeParsingStateService resumeParsingStateService,
            ResumeReparseRequestRepository resumeReparseRequestRepository,
            CatalogRegistry catalogRegistry) {
        this.resumeRepository = resumeRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.storageService = storageService;
        this.resumeParsingStateService = resumeParsingStateService;
        this.resumeReparseRequestRepository = resumeReparseRequestRepository;
        this.catalogRegistry = catalogRegistry;
    }

    public List<ResumeResponse> listMine(UUID candidateId) {
        return toResponses(resumeRepository.findByCandidateIdOrderByUploadedAtDesc(candidateId));
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

    // Kiem quyen so huu dung pattern downloadMine: 404 neu khong phai chu, khong phai 403 - tranh
    // lo resume cua nguoi khac co ton tai hay khong. Con PENDING/PROCESSING/FAILED (chua co hang
    // resume_parsed_data) cung tra 404 rieng (RESUME_PARSED_DATA_NOT_FOUND) - KHONG tra 200 voi
    // body rong, de frontend khong phai tu doan "rong vi chua xong" hay "rong vi loi".
    public ResumeParsedDataResponse getParsedData(UUID candidateId, UUID resumeId) {
        resumeRepository
                .findByIdAndCandidateId(resumeId, candidateId)
                .orElseThrow(() -> new ResumeNotFoundException(resumeId));
        ResumeParsedData data = resumeParsedDataRepository
                .findByResumeId(resumeId)
                .orElseThrow(() -> new ResumeParsedDataNotFoundException(resumeId));
        return toParsedDataResponse(data);
    }

    // FR-C05 R-R1..R-R3 - ung vien yeu cau trich xuat lai CV schema cu cua chinh minh. Chi TAO yeu cau
    // PENDING; job nen (ResumeReparseScheduler) moi goi LLM - khong co loi goi LLM dong bo nao o day.
    // Rate limit theo userId nam o RateLimitFilter (nhom llm-action). Thu tu kiem theo R-R2:
    // 404 (khong phai CV cua minh, mau downloadMine) -> 409 chua DONE hoac khong phai v1 -> 409 dang co
    // yeu cau PENDING/RUNNING. resumes.parse_status KHONG doi (giu DONE).
    @Transactional
    public ResumeResponse requestReparse(UUID candidateId, UUID resumeId) {
        Resume resume =
                resumeRepository
                        .findByIdAndCandidateId(resumeId, candidateId)
                        .orElseThrow(() -> new ResumeNotFoundException(resumeId));
        if (resume.getParseStatus() != ParseStatus.DONE) {
            throw ResumeReparseNotAllowedException.notParsedYet();
        }
        ResumeParsedData data =
                resumeParsedDataRepository.findByResumeId(resumeId).orElseThrow(ResumeReparseNotAllowedException::notParsedYet);
        if (!ResumeSchemaVersions.isV1(data.getPromptVersion())) {
            throw ResumeReparseNotAllowedException.alreadyLatest();
        }
        if (resumeReparseRequestRepository.existsByResumeIdAndStatusIn(
                resumeId, List.of(ResumeReparseRequestStatus.PENDING, ResumeReparseRequestStatus.RUNNING))) {
            throw new ResumeReparseInProgressException();
        }

        ResumeReparseRequest request = new ResumeReparseRequest();
        request.setResumeId(resumeId);
        request.setStatus(ResumeReparseRequestStatus.PENDING);
        try {
            resumeReparseRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            // Race hai yeu cau cung luc (bam dup, hai tab): uq_resume_reparse_request_active (V8) chan
            // mot - tra cung 409 nhu nhanh kiem truoc. Vi pham khac khong phai loi nay -> nem lai.
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains("uq_resume_reparse_request_active")) {
                throw new ResumeReparseInProgressException();
            }
            throw e;
        }
        return toResponse(resume);
    }

    // Muc 3b con sot cua ke hoach Dot 3/4 (chore/hardening) - duong thu lai THU CONG cho candidate
    // khi CV da FAILED han (het so lan thu tu dong o Dot 4, hoac loi moi truong khong phai loi SDK
    // vi du thieu ANTHROPIC_API_KEY luc chay). Kiem so huu truoc (404, dung pattern
    // downloadMine/getParsedData), roi kiem parseStatus == FAILED (409, thong bao ro) TRUOC KHI goi
    // UPDATE co dieu kien - lop kiem nay chi de tra loi som, than thien; chot chan that su la dieu
    // kien WHERE parse_status = 'FAILED' trong ResumeParsingStateService.retry (xem CLAUDE.md muc
    // 4). Rowcount 0 tu do (race hiem: CV vua bi mot luong khac doi khoi FAILED giua hai buoc nay)
    // nem CUNG mot loai loi 409 - khong phan biet nguyen nhan, ca hai deu bao nguoi dung tai lai
    // trang la du. KHONG @Transactional o method nay: ghi that su nam trong transaction rieng, ngan
    // cua resumeParsingStateService.retry - hai lan doc o day (truoc va sau) la doc thuan, khong
    // can bao boc chung mot transaction voi buoc ghi.
    public ResumeResponse retry(UUID candidateId, UUID resumeId) {
        Resume resume =
                resumeRepository
                        .findByIdAndCandidateId(resumeId, candidateId)
                        .orElseThrow(() -> new ResumeNotFoundException(resumeId));
        if (resume.getParseStatus() != ParseStatus.FAILED) {
            throw new ResumeRetryNotAllowedException();
        }
        if (!resumeParsingStateService.retry(resumeId)) {
            throw new ResumeRetryNotAllowedException();
        }
        return toResponse(resumeRepository.findById(resumeId).orElseThrow());
    }

    private ResumeParsedDataResponse toParsedDataResponse(ResumeParsedData data) {
        ResumeParsedPayload payload = data.getData();
        return new ResumeParsedDataResponse(
                data.getResumeId(),
                payload,
                data.getParsedAt(),
                ResumeSchemaVersions.of(data.getPromptVersion()),
                payload.currentTitle(),
                catalogItem(data.getIndustryCode(), catalogRegistry.industryLabel(data.getIndustryCode())),
                catalogItem(data.getRegionCode(), catalogRegistry.provinceLabel(data.getRegionCode())),
                payload.locationText(),
                toExperience(data));
    }

    private static CatalogResponse.Item catalogItem(String code, String label) {
        return code == null || label == null ? null : new CatalogResponse.Item(code, label);
    }

    // null khi chua tinh (job nen chua toi). years do backend quy doi (R-E8).
    private static ResumeParsedDataResponse.Experience toExperience(ResumeParsedData data) {
        if (data.getExperienceComputedAt() == null) {
            return null;
        }
        Integer months = data.getExperienceMonths();
        return new ResumeParsedDataResponse.Experience(
                months,
                months == null ? null : ExperienceCalculator.toYears(months),
                data.getExperienceEntriesCounted(),
                data.getExperienceEntriesSkipped(),
                ExperienceCalculator.referenceMonth(data.getExperienceComputedAt()).toString());
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

    private ResumeResponse toResponse(Resume resume) {
        return toResponses(List.of(resume)).get(0);
    }

    // FR-C05 - schemaVersion va yeu cau trich xuat lai gan nhat: hai cau truy van cho CA danh sach, khong
    // truy van tung CV.
    private List<ResumeResponse> toResponses(List<Resume> resumes) {
        if (resumes.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = resumes.stream().map(Resume::getId).toList();
        Map<UUID, String> promptVersions = resumeParsedDataRepository.findPromptVersionsByResumeIds(ids).stream()
                .collect(Collectors.toMap(
                        ResumeParsedDataRepository.PromptVersionView::getResumeId,
                        ResumeParsedDataRepository.PromptVersionView::getPromptVersion));
        Map<UUID, ResumeReparseRequest> latestReparse = resumeReparseRequestRepository.findLatestByResumeIds(ids).stream()
                .collect(Collectors.toMap(ResumeReparseRequest::getResumeId, Function.identity()));
        return resumes.stream()
                .map(r -> toResponse(r, promptVersions.get(r.getId()), latestReparse.get(r.getId())))
                .toList();
    }

    private static ResumeResponse toResponse(Resume r, String promptVersion, ResumeReparseRequest reparse) {
        Integer schemaVersion = r.getParseStatus() == ParseStatus.DONE ? ResumeSchemaVersions.of(promptVersion) : null;
        ResumeReparseStatusResponse reparseStatus =
                reparse == null ? null : new ResumeReparseStatusResponse(reparse.getStatus(), reparse.getErrorMessage());
        return new ResumeResponse(
                r.getId(),
                r.getFileName(),
                r.getFileType(),
                r.getFileSize(),
                r.getVersionLabel(),
                r.isPrimary(),
                r.getParseStatus(),
                r.getParseError(),
                r.getUploadedAt(),
                schemaVersion,
                reparseStatus);
    }
}
