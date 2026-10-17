package com.recruitment.cvbuilder;

import com.recruitment.cvbuilder.dto.CvBuilderDocumentResponse;
import com.recruitment.cvbuilder.dto.CvBuilderRequest;
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

// FR-U12 B1-B3 (muc 4.3). /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o filter chain; candidateId
// lay tu JWT. Quyen so huu CV (B2) kiem o CvBuilderService qua findByIdAndCandidateId - CV nguoi khac va CV khong ton
// tai CUNG 404. Khong PUT/PATCH/DELETE: sua CV = tao CV moi bang B1 (R-W6). B4 (goi y dien dat) them o dot 5.
@RestController
@RequestMapping("/api/candidates/cv-builder")
public class CvBuilderCandidateController {

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
}
