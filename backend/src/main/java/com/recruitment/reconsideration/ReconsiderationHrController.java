package com.recruitment.reconsideration;

import com.recruitment.reconsideration.dto.HrReconsiderationListResponse;
import com.recruitment.reconsideration.dto.HrReconsiderationResponse;
import com.recruitment.reconsideration.dto.ReconsiderationDeclineBody;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// FR-C09 H1, H2. /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain (ung vien -> 403); quyen so huu
// tin/don kiem o service (R-D5). Chap nhan KHONG co endpoint rieng: la I2 cua FR-H12 (R-D1).
@RestController
public class ReconsiderationHrController {

    private final ReconsiderationHrService service;

    public ReconsiderationHrController(ReconsiderationHrService service) {
        this.service = service;
    }

    // H1 - status la chuoi tu do: thieu hoac gia tri la -> OPEN (khong 400).
    @GetMapping("/api/hr/jobs/{jobId}/reconsideration-requests")
    public ResponseEntity<HrReconsiderationListResponse> list(
            Authentication authentication,
            @PathVariable UUID jobId,
            @RequestParam(name = "status", required = false) String status) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(service.list(ownerId, jobId, status));
    }

    // H2 - body khong bat buoc: thieu body, thieu khoa hay response null deu la "khong nhap phan hoi" (R-V1).
    @PostMapping("/api/hr/applications/{id}/reconsideration-request/decline")
    public ResponseEntity<HrReconsiderationResponse> decline(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody(required = false) ReconsiderationDeclineBody body) {
        UUID ownerId = UUID.fromString(authentication.getName());
        String response = body == null ? null : body.response();
        return ResponseEntity.ok(service.decline(ownerId, id, response));
    }
}
