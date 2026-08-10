package com.recruitment.company;

import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.company.dto.CompanyPublicResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CompanyPublicService {

    private final CompanyRepository companyRepository;

    public CompanyPublicService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public CompanyPublicResponse getDetail(UUID id) {
        Company company = companyRepository.findById(id).orElseThrow(() -> new CompanyNotFoundException(id));
        return toResponse(company);
    }

    private CompanyPublicResponse toResponse(Company c) {
        return new CompanyPublicResponse(
                c.getId(),
                c.getName(),
                c.getLogoUrl(),
                c.getDescription(),
                c.getCompanySize(),
                c.getIndustry(),
                c.getWebsite(),
                c.getContactEmail(),
                c.getContactPhone(),
                c.getAddress(),
                c.getCreatedAt());
    }
}
