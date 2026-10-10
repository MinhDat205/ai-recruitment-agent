package com.recruitment.screening;

import com.recruitment.screening.dto.ScreeningQuestionListResponse;
import com.recruitment.screening.dto.ScreeningQuestionOrderRequest;
import com.recruitment.screening.dto.ScreeningQuestionRequest;
import com.recruitment.screening.dto.ScreeningQuestionResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-H10 S1-S5. /api/hr/** da bi SecurityConfig chan hasRole("HR") o filter chain; quyen so huu (Job thuoc cong ty
// cua HR) kiem o ScreeningQuestionOwnerService (R-A1, R-A2). PUT "/order" (chuoi co dinh) duoc Spring uu tien hon
// PUT "/{questionId}".
@RestController
@RequestMapping("/api/hr/jobs/{jobId}/screening-questions")
public class ScreeningQuestionOwnerController {

    private final ScreeningQuestionOwnerService service;

    public ScreeningQuestionOwnerController(ScreeningQuestionOwnerService service) {
        this.service = service;
    }

    @GetMapping
    public ScreeningQuestionListResponse list(Authentication authentication, @PathVariable UUID jobId) {
        return service.list(ownerId(authentication), jobId);
    }

    @PostMapping
    public ResponseEntity<ScreeningQuestionResponse> add(
            Authentication authentication, @PathVariable UUID jobId, @Valid @RequestBody ScreeningQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(ownerId(authentication), jobId, request));
    }

    @PutMapping("/order")
    public ScreeningQuestionListResponse reorder(
            Authentication authentication,
            @PathVariable UUID jobId,
            @Valid @RequestBody ScreeningQuestionOrderRequest request) {
        return service.reorder(ownerId(authentication), jobId, request);
    }

    @PutMapping("/{questionId}")
    public ScreeningQuestionResponse update(
            Authentication authentication,
            @PathVariable UUID jobId,
            @PathVariable UUID questionId,
            @Valid @RequestBody ScreeningQuestionRequest request) {
        return service.update(ownerId(authentication), jobId, questionId, request);
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> delete(
            Authentication authentication, @PathVariable UUID jobId, @PathVariable UUID questionId) {
        service.delete(ownerId(authentication), jobId, questionId);
        return ResponseEntity.noContent().build();
    }

    private static UUID ownerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
