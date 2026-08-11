package com.recruitment.company;

import com.recruitment.common.exception.CompanyAlreadyExistsException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InvalidLogoFileException;
import com.recruitment.company.dto.CompanyRequest;
import com.recruitment.company.dto.CompanyResponse;
import com.recruitment.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CompanyOwnerService {

    private static final String LOGO_SUBDIRECTORY = "logos";
    private static final long MAX_LOGO_SIZE_BYTES = 2L * 1024 * 1024;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_SIGNATURE = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_SIGNATURE = {0x57, 0x45, 0x42, 0x50};

    private final CompanyRepository companyRepository;
    private final StorageService storageService;

    public CompanyOwnerService(CompanyRepository companyRepository, StorageService storageService) {
        this.companyRepository = companyRepository;
        this.storageService = storageService;
    }

    @Transactional
    public CompanyResponse create(UUID ownerId, CompanyRequest request) {
        if (companyRepository.findByOwnerId(ownerId).isPresent()) {
            throw new CompanyAlreadyExistsException(ownerId);
        }
        Company company = new Company();
        company.setOwnerId(ownerId);
        applyRequest(company, request);
        return toResponse(companyRepository.save(company));
    }

    public CompanyResponse getMine(UUID ownerId) {
        Company company =
                companyRepository
                        .findByOwnerId(ownerId)
                        .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
        return toResponse(company);
    }

    @Transactional
    public CompanyResponse update(UUID companyId, UUID ownerId, CompanyRequest request) {
        Company company = loadOwned(companyId, ownerId);
        applyRequest(company, request);
        return toResponse(companyRepository.save(company));
    }

    @Transactional
    public CompanyResponse uploadLogo(UUID companyId, UUID ownerId, MultipartFile file) {
        Company company = loadOwned(companyId, ownerId);
        if (file.isEmpty()) {
            throw new InvalidLogoFileException("File logo đang trống");
        }
        if (file.getSize() > MAX_LOGO_SIZE_BYTES) {
            throw new InvalidLogoFileException("File logo vượt quá 2MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidLogoFileException("Không đọc được file logo");
        }
        String ext =
                detectImageExtension(content)
                        .orElseThrow(
                                () -> new InvalidLogoFileException("File không phải ảnh PNG/JPEG/WEBP hợp lệ"));

        // Xoa file cu truoc khi ghi file moi (vd HR doi dinh dang png -> jpg) de khong con file mo coi.
        if (company.getLogoUrl() != null) {
            storageService.delete(company.getLogoUrl());
        }
        String filename = companyId + "." + ext;
        String publicUrl = storageService.store(LOGO_SUBDIRECTORY, filename, new ByteArrayInputStream(content));
        company.setLogoUrl(publicUrl);
        return toResponse(companyRepository.save(company));
    }

    private Company loadOwned(UUID companyId, UUID ownerId) {
        Company company =
                companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
        if (!company.getOwnerId().equals(ownerId)) {
            // Nem AccessDeniedException chuan cua Spring Security de ExceptionTranslationFilter /
            // JsonAccessDeniedHandler (da cau hinh o SecurityConfig) xu ly thanh 403 JSON, dung
            // co che dang chay cho @PreAuthorize - khong can them exception handler rieng.
            throw new AccessDeniedException("Khong co quyen truy cap cong ty nay");
        }
        return company;
    }

    private void applyRequest(Company company, CompanyRequest request) {
        company.setName(request.name());
        company.setDescription(request.description());
        company.setCompanySize(request.companySize());
        company.setIndustry(request.industry());
        company.setWebsite(request.website());
        company.setContactEmail(request.contactEmail());
        company.setContactPhone(request.contactPhone());
        company.setAddress(request.address());
    }

    private CompanyResponse toResponse(Company c) {
        return new CompanyResponse(
                c.getId(),
                c.getOwnerId(),
                c.getName(),
                c.logoUrlWithCacheBust(),
                c.getDescription(),
                c.getCompanySize(),
                c.getIndustry(),
                c.getWebsite(),
                c.getContactEmail(),
                c.getContactPhone(),
                c.getAddress(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    private static Optional<String> detectImageExtension(byte[] content) {
        if (matchesAt(content, 0, PNG_SIGNATURE)) {
            return Optional.of("png");
        }
        if (matchesAt(content, 0, JPEG_SIGNATURE)) {
            return Optional.of("jpg");
        }
        if (matchesAt(content, 0, RIFF_SIGNATURE) && matchesAt(content, 8, WEBP_SIGNATURE)) {
            return Optional.of("webp");
        }
        return Optional.empty();
    }

    private static boolean matchesAt(byte[] content, int offset, byte[] signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
