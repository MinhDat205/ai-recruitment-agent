package com.recruitment.cvbuilder;

import com.recruitment.cvbuilder.dto.CvBuilderDocumentResponse;
import com.recruitment.cvbuilder.dto.CvBuilderRequest;
import com.recruitment.cvbuilder.dto.CvRephraseRequest;
import com.recruitment.cvbuilder.dto.CvRephraseResponse;
import com.recruitment.resume.dto.ResumeResponse;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-U12 B1-B4 (muc 4.3). /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o filter chain; candidateId
// lay tu JWT. Quyen so huu CV (B2) kiem o CvBuilderService qua findByIdAndCandidateId - CV nguoi khac va CV khong ton
// tai CUNG 404. Khong PUT/PATCH/DELETE: sua CV = tao CV moi bang B1 (R-W6). B4 khong doc/ghi du lieu nao cua ung vien
// (R-A5) nen khong can candidateId; rate limit ai-sync theo userId o RateLimitFilter (R-A8).
@RestController
@RequestMapping(CvBuilderCandidateController.BASE_PATH)
public class CvBuilderCandidateController {

    static final String BASE_PATH = "/api/candidates/cv-builder";
    // B4 - CvBuilderExceptionAdvice so khop DUNG duong dan nay de chon ma loi body hong.
    static final String REPHRASE_SUBPATH = "/rephrase";
    static final String REPHRASE_PATH = BASE_PATH + REPHRASE_SUBPATH;

    // R-B3 - ten file co dinh cua ban xem truoc.
    static final String PREVIEW_FILE_NAME = "xem-truoc-cv.pdf";

    private final CvBuilderService cvBuilderService;

    public CvBuilderCandidateController(CvBuilderService cvBuilderService) {
        this.cvBuilderService = cvBuilderService;
    }

    // B1 - 201 ResumeResponse cua CV vua tao.
    @PostMapping("/resumes")
    public ResponseEntity<ResumeResponse> create(Authentication authentication, @RequestBody CvBuilderRequest request) {
        ResumeResponse response = cvBuilderService.create(UUID.fromString(authentication.getName()), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // B2 - form, mau, ten phien ban da luu cua CV dung tu form.
    @GetMapping("/resumes/{resumeId}")
    public CvBuilderDocumentResponse getDocument(Authentication authentication, @PathVariable UUID resumeId) {
        return cvBuilderService.getDocument(UUID.fromString(authentication.getName()), resumeId);
    }

    // B3 - PDF xem truoc, khong luu (R-B3). no-store: ban xem truoc khong duoc nam trong cache trinh duyet/proxy.
    @PostMapping("/preview")
    public ResponseEntity<byte[]> preview(@RequestBody CvBuilderRequest request) {
        byte[] pdf = cvBuilderService.preview(request);
        ContentDisposition disposition = ContentDisposition.inline().filename(PREVIEW_FILE_NAME).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }

    // B4 - goi y dien dat mot doan mo ta (K3). Response chi co doan goi y, khong luu gi (R-A5).
    @PostMapping(REPHRASE_SUBPATH)
    public CvRephraseResponse rephrase(@RequestBody CvRephraseRequest request) {
        return cvBuilderService.rephrase(request);
    }
}
