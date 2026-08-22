package com.recruitment.resume;

import com.recruitment.resume.dto.CvImprovementSuggestionResponse;
import com.recruitment.resume.dto.ResumeParsedDataResponse;
import com.recruitment.resume.dto.ResumeResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o tang filter chain, khong
// can @PreAuthorize them. Quyen so huu tung resume duoc kiem rieng o ResumeService/
// CvImprovementSuggestionService (404 neu sai chu, khong phai 403 - tranh lo resume cua nguoi khac
// co ton tai hay khong).
@RestController
@RequestMapping("/api/candidates/resumes")
public class ResumeCandidateController {

    private final ResumeService resumeService;
    private final CvImprovementSuggestionService cvImprovementSuggestionService;

    public ResumeCandidateController(
            ResumeService resumeService, CvImprovementSuggestionService cvImprovementSuggestionService) {
        this.resumeService = resumeService;
        this.cvImprovementSuggestionService = cvImprovementSuggestionService;
    }

    @GetMapping
    public List<ResumeResponse> listMine(Authentication authentication) {
        return resumeService.listMine(UUID.fromString(authentication.getName()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeResponse> upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "versionLabel", required = false) String versionLabel) {
        ResumeResponse response =
                resumeService.upload(UUID.fromString(authentication.getName()), file, versionLabel);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/primary")
    public ResumeResponse setPrimary(Authentication authentication, @PathVariable UUID id) {
        return resumeService.setPrimary(UUID.fromString(authentication.getName()), id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(Authentication authentication, @PathVariable UUID id) {
        ResumeDownload download = resumeService.downloadMine(UUID.fromString(authentication.getName()), id);
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(download.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }

    @GetMapping("/{id}/parsed")
    public ResumeParsedDataResponse getParsedData(Authentication authentication, @PathVariable UUID id) {
        return resumeService.getParsedData(UUID.fromString(authentication.getName()), id);
    }

    @PostMapping("/{id}/improvement-suggestions")
    public CvImprovementSuggestionResponse requestImprovementSuggestions(
            Authentication authentication, @PathVariable UUID id) {
        return cvImprovementSuggestionService.requestSuggestions(UUID.fromString(authentication.getName()), id);
    }

    @GetMapping("/{id}/improvement-suggestions")
    public CvImprovementSuggestionResponse getImprovementSuggestions(
            Authentication authentication, @PathVariable UUID id) {
        return cvImprovementSuggestionService.getSuggestions(UUID.fromString(authentication.getName()), id);
    }
}
