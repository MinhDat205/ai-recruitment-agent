package com.recruitment.jobdraft;

import com.recruitment.job.dto.JobOwnerResponse;
import com.recruitment.jobdraft.dto.JobAiDraftRequest;
import com.recruitment.jobdraft.dto.JobAiDraftResponse;
import com.recruitment.jobdraft.dto.JobAiDraftSaveRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-H11 - /api/hr/** da bi SecurityConfig chan hasRole("HR") o filter chain (R-A1). Tien to rieng /api/hr/job-ai-drafts,
// KHONG dat duoi /api/hr/jobs/ (trung mau {jobId} cua FR-H10 - muc 0.a10). Nguoi goi lay tu JWT, truyen tham so xuong
// facade (L3). J1 tieu 1 luot ai-sync o RateLimitFilter (R-K3H-6) bat ke so phan.
@RestController
@RequestMapping("/api/hr/job-ai-drafts")
public class JobAiDraftController {

    private final JobAiDraftFacade jobAiDraftFacade;

    public JobAiDraftController(JobAiDraftFacade jobAiDraftFacade) {
        this.jobAiDraftFacade = jobAiDraftFacade;
    }

    // J1 - 200 (khong 201): khong tao gi; loi AI cua tung phan nam trong o cua phan do (R-N6).
    @PostMapping
    public JobAiDraftResponse generate(Authentication authentication, @RequestBody JobAiDraftRequest request) {
        return jobAiDraftFacade.generate(UUID.fromString(authentication.getName()), request);
    }

    // J2 - 201 JobOwnerResponse nhu POST /api/hr/jobs (R-L7). @Valid cho job + interviewTemplate (R-L4 (3)); khong goi AI,
    // khong thuoc nhom rate limit ai-sync (R-K3H-6 - RateLimitFilter so khop chinh xac duong dan J1).
    @PostMapping("/save")
    public ResponseEntity<JobOwnerResponse> save(
            Authentication authentication, @Valid @RequestBody JobAiDraftSaveRequest request) {
        JobOwnerResponse saved = jobAiDraftFacade.save(UUID.fromString(authentication.getName()), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
