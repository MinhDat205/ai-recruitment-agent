package com.recruitment.cvbuilder;

import com.recruitment.ai.citation.VerbatimQuoteVerifier;
import com.recruitment.ai.cvrephrase.CvRephraseService;
import com.recruitment.common.exception.CvBuilderConcurrentCreateException;
import com.recruitment.common.exception.CvBuilderRenderFailedException;
import com.recruitment.common.exception.CvBuilderTooLargeException;
import com.recruitment.common.exception.CvNotFromBuilderException;
import com.recruitment.common.exception.InvalidCvRephraseRequestException;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.cvbuilder.dto.CvBuilderDocumentResponse;
import com.recruitment.cvbuilder.dto.CvBuilderRequest;
import com.recruitment.cvbuilder.dto.CvRephraseRequest;
import com.recruitment.cvbuilder.dto.CvRephraseResponse;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeExtractionException;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.resume.ResumeService;
import com.recruitment.resume.TextExtractor;
import com.recruitment.resume.dto.ResumeResponse;
import com.recruitment.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

// FR-U12 facade B1-B4. KHONG mo transaction (CLAUDE.md muc 3c): chuan hoa, kiem, dung PDF, trich van ban va luu file deu
// nam NGOAI transaction; ghi DB la MOT transaction ngan cua CvBuilderStateService.create (R-W2). B1-B3 khong goi AI
// (R-W8); B4 goi AI CHI qua CvRephraseService (K3), khong doc/ghi DB (R-A5).
// Thu tu B1 (R-W1): chuan hoa (R-F3) -> kiem (R-F4-R-F12) -> dung PDF -> R-P9 -> trich raw_text + K2 luc chay (R-P8) ->
// luu file -> transaction R-W2. B3 dung o R-P9 (R-B3): khong trich van ban, khong luu file, khong ghi DB.
@Service
public class CvBuilderService {

    private static final Logger log = LoggerFactory.getLogger(CvBuilderService.class);

    // R-W1 - cung thu muc/khoa voi upload (ResumeService): resumes/<uuid>.pdf.
    static final String RESUME_SUBDIRECTORY = "resumes";
    // R-W5 - race CV dau tien (V3).
    static final String PRIMARY_CONSTRAINT = "uq_resume_primary_per_candidate";
    // R-W7 - "CV " + fullName + ".pdf", ca chuoi <= 255 (file_name VARCHAR(255)).
    static final String FILE_NAME_PREFIX = "CV ";
    static final String FILE_NAME_SUFFIX = ".pdf";
    static final int FILE_NAME_MAX_LENGTH = 255;
    // R-A2 - do dai doan mo ta B4 (String.length() sau R-F3), ca hai dau deu hop le.
    static final int REPHRASE_MIN_LENGTH = 20;
    static final int REPHRASE_MAX_LENGTH = 800;

    private final CvBuilderFormNormalizer normalizer;
    private final CvBuilderFormValidator validator;
    private final CvPdfRenderer renderer;
    private final TextExtractor textExtractor;
    private final StorageService storageService;
    private final CvBuilderStateService stateService;
    private final CvBuilderDocumentRepository documentRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeService resumeService;
    private final CvRephraseService rephraseService;
    // R-P9 - nguong qua constructor: bean Spring dung nguong upload, test don vi truyen nguong nho (T16b).
    private final long maxPdfBytes;

    @Autowired
    public CvBuilderService(
            CvBuilderFormNormalizer normalizer,
            CvBuilderFormValidator validator,
            CvPdfRenderer renderer,
            TextExtractor textExtractor,
            StorageService storageService,
            CvBuilderStateService stateService,
            CvBuilderDocumentRepository documentRepository,
            ResumeRepository resumeRepository,
            ResumeService resumeService,
            CvRephraseService rephraseService) {
        this(normalizer, validator, renderer, textExtractor, storageService, stateService, documentRepository,
                resumeRepository, resumeService, rephraseService, ResumeService.MAX_RESUME_SIZE_BYTES);
    }

    CvBuilderService(
            CvBuilderFormNormalizer normalizer,
            CvBuilderFormValidator validator,
            CvPdfRenderer renderer,
            TextExtractor textExtractor,
            StorageService storageService,
            CvBuilderStateService stateService,
            CvBuilderDocumentRepository documentRepository,
            ResumeRepository resumeRepository,
            ResumeService resumeService,
            CvRephraseService rephraseService,
            long maxPdfBytes) {
        this.normalizer = normalizer;
        this.validator = validator;
        this.renderer = renderer;
        this.textExtractor = textExtractor;
        this.storageService = storageService;
        this.stateService = stateService;
        this.documentRepository = documentRepository;
        this.resumeRepository = resumeRepository;
        this.resumeService = resumeService;
        this.rephraseService = rephraseService;
        this.maxPdfBytes = maxPdfBytes;
    }

    // B1 - tao CV moi (ca khi "sua": luon CV moi, R-W6).
    public ResumeResponse create(UUID candidateId, CvBuilderRequest request) {
        Built built = buildChecked(request);
        String rawText = extractVerified(built.form(), built.pdf());

        String filename = UUID.randomUUID() + ".pdf";
        String key = RESUME_SUBDIRECTORY + "/" + filename;
        // L16 - publicUrl (/uploads/resumes/<uuid>.pdf) chi dung de xoa khi rollback; resumes.file_url ghi KHOA (key).
        String publicUrl = storageService.store(RESUME_SUBDIRECTORY, filename, new ByteArrayInputStream(built.pdf()));
        UUID resumeId;
        try {
            resumeId = stateService.create(
                    candidateId,
                    built.template(),
                    built.form(),
                    built.versionLabel(),
                    key,
                    fileName(built.form().contact().fullName()),
                    built.pdf().length,
                    rawText);
        } catch (RuntimeException e) {
            // R-W3 - transaction da rollback, khong con hang nao; xoa file vua luu roi nem tiep.
            deleteOrphan(publicUrl, key);
            if (e instanceof DataIntegrityViolationException violation && violates(violation, PRIMARY_CONSTRAINT)) {
                throw new CvBuilderConcurrentCreateException();
            }
            throw e;
        }
        return resumeService.getMine(candidateId, resumeId);
    }

    // B2 - doc lai form da luu. CV nguoi khac va CV khong ton tai CUNG 404 (khuon findByIdAndCandidateId).
    public CvBuilderDocumentResponse getDocument(UUID candidateId, UUID resumeId) {
        Resume resume = resumeRepository
                .findByIdAndCandidateId(resumeId, candidateId)
                .orElseThrow(() -> new ResumeNotFoundException(resumeId));
        CvBuilderDocument document =
                documentRepository.findByResumeId(resumeId).orElseThrow(CvNotFromBuilderException::new);
        return new CvBuilderDocumentResponse(
                resumeId, document.getTemplate(), resume.getVersionLabel(), document.getFormData(),
                document.getCreatedAt());
    }

    // B3 - xem truoc: kiem nhu B1 toi het R-P9 (R-B3), tra bytes PDF.
    public byte[] preview(CvBuilderRequest request) {
        return buildChecked(request).pdf();
    }

    // B4 - R-A2: chuan hoa R-F3 (o nhieu dong) -> rong -> do dai -> ky tu R-F4 (khong kiem glyph: chua dung PDF) ->
    // kind. Moi loi 400 dung TRUOC khi goi AI. Loi K3 noi len nguyen (502/503/504 - R-A3).
    public CvRephraseResponse rephrase(CvRephraseRequest request) {
        String text = normalizer.multiLine(request.text());
        if (text == null) {
            throw InvalidCvRephraseRequestException.emptyText();
        }
        if (text.length() < REPHRASE_MIN_LENGTH) {
            throw InvalidCvRephraseRequestException.textTooShort();
        }
        if (text.length() > REPHRASE_MAX_LENGTH) {
            throw InvalidCvRephraseRequestException.textTooLong();
        }
        if (CvBuilderFormValidator.hasDisallowedCharacter(text, true)) {
            throw InvalidCvRephraseRequestException.disallowedCharacter();
        }
        if (request.kind() == null) {
            throw InvalidCvRephraseRequestException.missingKind();
        }
        return new CvRephraseResponse(rephraseService.rephrase(request.kind(), text));
    }

    // R-F3 -> R-F4-R-F12 -> dung PDF -> R-P9.
    private Built buildChecked(CvBuilderRequest request) {
        CvBuilderForm form = normalizer.normalize(request.form());
        String versionLabel = normalizer.singleLine(request.versionLabel());
        validator.validate(request.template(), form, versionLabel);
        byte[] pdf;
        try {
            pdf = renderer.render(request.template(), form);
        } catch (IOException e) {
            // L7 - khong co handler chung cho IOException; chi tiet loi PDFBox o log.debug.
            log.debug("Dung PDF CV builder loi", e);
            throw new CvBuilderRenderFailedException(e);
        }
        if (pdf.length > maxPdfBytes) {
            throw new CvBuilderTooLargeException();
        }
        return new Built(request.template(), form, versionLabel, pdf);
    }

    // R-P8 - raw_text trich lai tu chinh file PDF vua dung (Q-U3) + K2 luc chay (O2). Log warn CHI ghi duong dan o, khong
    // ghi gia tri (noi dung CV la du lieu ca nhan).
    private String extractVerified(CvBuilderForm form, byte[] pdf) {
        String rawText;
        try {
            rawText = textExtractor.extract(new ByteArrayInputStream(pdf), ResumeFileType.PDF);
        } catch (ResumeExtractionException e) {
            log.debug("Trich van ban tu PDF CV builder loi", e);
            throw new CvBuilderRenderFailedException(e);
        }
        if (rawText == null || rawText.isBlank()) {
            log.warn("CV builder: raw_text trich tu PDF rong (R-P8)");
            throw new CvBuilderRenderFailedException();
        }
        for (PrintedValue value : printedValues(form)) {
            if (!VerbatimQuoteVerifier.isVerbatim(value.text(), rawText)) {
                log.warn("CV builder: raw_text khong chua nguyen van o {} (R-P8)", value.path());
                throw new CvBuilderRenderFailedException();
            }
        }
        return rawText;
    }

    // Moi chuoi van ban cua form duoc in ra PDF (R-P8): moi o chu, phan tu skills/technologies, moc, "Hiện tại" khi
    // current, GPA da dinh dang. KHONG gom industryCode (khong in - O1) va versionLabel (ngoai form). Duong dan theo
    // ten field JSON, chi so mang tinh tu 0.
    static List<PrintedValue> printedValues(CvBuilderForm form) {
        List<PrintedValue> values = new ArrayList<>();
        CvBuilderForm.Contact c = form.contact();
        if (c != null) {
            add(values, "contact.fullName", c.fullName());
            add(values, "contact.email", c.email());
            add(values, "contact.phone", c.phone());
            add(values, "contact.address", c.address());
            add(values, "contact.linkedin", c.linkedin());
        }
        add(values, "currentTitle", form.currentTitle());
        add(values, "locationText", form.locationText());
        for (int i = 0; i < form.education().size(); i++) {
            CvBuilderForm.Education e = form.education().get(i);
            String p = "education[" + i + "].";
            add(values, p + "school", e.school());
            add(values, p + "degree", e.degree());
            add(values, p + "major", e.major());
            add(values, p + "startDate", e.startDate());
            add(values, p + "endDate", e.endDate());
            add(values, p + "gpa", e.gpa() == null ? null : CvPdfRenderer.formatGpa(e.gpa()));
        }
        for (int i = 0; i < form.experience().size(); i++) {
            CvBuilderForm.Experience e = form.experience().get(i);
            String p = "experience[" + i + "].";
            add(values, p + "company", e.company());
            add(values, p + "title", e.title());
            add(values, p + "startDate", e.startDate());
            add(values, p + "endDate", e.endDate());
            add(values, p + "current", Boolean.TRUE.equals(e.current()) ? CvPdfLabels.CURRENT : null);
            add(values, p + "description", e.description());
        }
        for (int i = 0; i < form.skills().size(); i++) {
            add(values, "skills[" + i + "]", form.skills().get(i));
        }
        for (int i = 0; i < form.certifications().size(); i++) {
            CvBuilderForm.Certification x = form.certifications().get(i);
            String p = "certifications[" + i + "].";
            add(values, p + "name", x.name());
            add(values, p + "issuer", x.issuer());
            add(values, p + "issueDate", x.issueDate());
        }
        for (int i = 0; i < form.projects().size(); i++) {
            CvBuilderForm.Project x = form.projects().get(i);
            String p = "projects[" + i + "].";
            add(values, p + "name", x.name());
            add(values, p + "description", x.description());
            for (int j = 0; j < x.technologies().size(); j++) {
                add(values, p + "technologies[" + j + "]", x.technologies().get(j));
            }
        }
        return values;
    }

    private static void add(List<PrintedValue> values, String path, String text) {
        if (text != null) {
            values.add(new PrintedValue(path, text));
        }
    }

    // R-W7 - fullName bat buoc (validator) va <= 100 ky tu nen thuc te khong bao gio phai cat; giu cat de chac cot 255.
    static String fileName(String fullName) {
        int maxName = FILE_NAME_MAX_LENGTH - FILE_NAME_PREFIX.length() - FILE_NAME_SUFFIX.length();
        String name = fullName.length() > maxName ? fullName.substring(0, maxName) : fullName;
        return FILE_NAME_PREFIX + name + FILE_NAME_SUFFIX;
    }

    // R-W3 - best-effort, xoa bang DUNG gia tri store() tra ve (L16). Log warn chi ghi khoa file.
    private void deleteOrphan(String publicUrl, String key) {
        try {
            storageService.delete(publicUrl);
        } catch (UncheckedIOException e) {
            log.warn("CV builder: khong xoa duoc file mo coi {}", key);
            log.debug("Loi xoa file mo coi {}", key, e);
        }
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraint);
    }

    // Form + mau + ten phien ban DA chuan hoa va DA kiem, kem bytes PDF da qua R-P9.
    private record Built(CvTemplate template, CvBuilderForm form, String versionLabel, byte[] pdf) {
    }

    // Mot chuoi duoc in + duong dan o (chi de log, khong bao gio ghi gia tri).
    record PrintedValue(String path, String text) {
    }
}
