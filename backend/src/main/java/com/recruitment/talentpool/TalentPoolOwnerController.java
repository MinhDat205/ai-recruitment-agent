package com.recruitment.talentpool;

import com.recruitment.common.dto.PageResponse;
import com.recruitment.talentpool.dto.TalentPoolEntryResponse;
import com.recruitment.talentpool.dto.TalentPoolNameRequest;
import com.recruitment.talentpool.dto.TalentPoolResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// FR-H15 P1-P5, P8, P9 (REQUIREMENT muc 4.3). /api/hr/** da bi SecurityConfig chan hasRole("HR") o filter chain; quyen
// so huu kho kiem o TalentPoolOwnerService. Body hong -> TalentPoolExceptionAdvice (400 INVALID_TALENT_POOL_NAME).
@RestController
@RequestMapping("/api/hr/talent-pools")
public class TalentPoolOwnerController {

    private final TalentPoolOwnerService service;

    public TalentPoolOwnerController(TalentPoolOwnerService service) {
        this.service = service;
    }

    @GetMapping
    public List<TalentPoolResponse> list(Authentication authentication) {
        return service.list(ownerId(authentication));
    }

    @PostMapping
    public ResponseEntity<TalentPoolResponse> create(
            Authentication authentication, @RequestBody TalentPoolNameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(ownerId(authentication), request));
    }

    @GetMapping("/{id}")
    public TalentPoolResponse get(Authentication authentication, @PathVariable UUID id) {
        return service.get(ownerId(authentication), id);
    }

    @PatchMapping("/{id}")
    public TalentPoolResponse rename(
            Authentication authentication, @PathVariable UUID id, @RequestBody TalentPoolNameRequest request) {
        return service.rename(ownerId(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
        service.delete(ownerId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/entries")
    public PageResponse<TalentPoolEntryResponse> listEntries(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.listEntries(ownerId(authentication), id, page, size);
    }

    @DeleteMapping("/{id}/entries/{applicationId}")
    public ResponseEntity<Void> removeEntry(
            Authentication authentication, @PathVariable UUID id, @PathVariable UUID applicationId) {
        service.removeEntry(ownerId(authentication), id, applicationId);
        return ResponseEntity.noContent().build();
    }

    private static UUID ownerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
