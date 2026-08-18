package com.recruitment.resume;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain (da doc lai xac nhan,
// khong doan) - ung vien goi route nay nhan 403 truoc khi toi duoc controller. Quyen so huu (don
// ung tuyen phai thuoc job cua cong ty HR dang dang nhap) duoc kiem rieng o ResumeHrService, giong
// pattern ScoringRunHrController.
@RestController
@RequestMapping("/api/hr/applications/{applicationId}/resume")
public class ResumeHrController {

    private final ResumeHrService resumeHrService;

    public ResumeHrController(ResumeHrService resumeHrService) {
        this.resumeHrService = resumeHrService;
    }

    // Mau y het ResumeCandidateController.download (ContentDisposition.attachment().filename(...,
    // UTF_8) tu ma hoa dung RFC 5987/6266 cho ten file co dau tieng Viet/ky tu la, kem fallback ASCII).
    @GetMapping("/download")
    public ResponseEntity<Resource> download(Authentication authentication, @PathVariable UUID applicationId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        ResumeDownload download = resumeHrService.downloadForApplication(ownerId, applicationId);
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(download.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }
}
