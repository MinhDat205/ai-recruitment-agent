package com.recruitment.company;

import com.recruitment.company.dto.CompanyRequest;
import com.recruitment.company.dto.CompanyResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain, khong can @PreAuthorize
// them. Quyen so huu tung ban ghi (owner_id) duoc kiem tra rieng o CompanyOwnerService.
@RestController
@RequestMapping("/api/hr/companies")
public class CompanyOwnerController {

    private final CompanyOwnerService companyOwnerService;

    public CompanyOwnerController(CompanyOwnerService companyOwnerService) {
        this.companyOwnerService = companyOwnerService;
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> create(
            Authentication authentication, @Valid @RequestBody CompanyRequest request) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(companyOwnerService.create(ownerId, request));
    }

    @GetMapping("/me")
    public CompanyResponse me(Authentication authentication) {
        return companyOwnerService.getMine(UUID.fromString(authentication.getName()));
    }

    @PutMapping("/{id}")
    public CompanyResponse update(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody CompanyRequest request) {
        return companyOwnerService.update(id, UUID.fromString(authentication.getName()), request);
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompanyResponse uploadLogo(
            Authentication authentication, @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return companyOwnerService.uploadLogo(id, UUID.fromString(authentication.getName()), file);
    }
}
