package com.recruitment.reconsideration;

import com.recruitment.reconsideration.dto.CandidateReconsiderationResponse;
import com.recruitment.reconsideration.dto.ReconsiderationRequestBody;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-C09 C1. /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o tang filter chain (HR -> 403, khach ->
// 401); quyen so huu don kiem o service (404 cho ca don nguoi khac).
@RestController
@RequestMapping("/api/candidates/applications/{id}/reconsideration-request")
public class ReconsiderationCandidateController {

    private final ReconsiderationCandidateService service;

    public ReconsiderationCandidateController(ReconsiderationCandidateService service) {
        this.service = service;
    }

    // Body khong bat buoc: thieu body, thieu khoa reason hay reason null deu la "khong nhap ly do" (R-V1).
    @PostMapping
    public ResponseEntity<CandidateReconsiderationResponse> request(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody(required = false) ReconsiderationRequestBody body) {
        UUID candidateId = UUID.fromString(authentication.getName());
        String reason = body == null ? null : body.reason();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(candidateId, id, reason));
    }
}
