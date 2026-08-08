package com.recruitment.company;

import com.recruitment.company.dto.CompanyPublicResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/companies")
public class CompanyPublicController {

    private final CompanyPublicService companyPublicService;

    public CompanyPublicController(CompanyPublicService companyPublicService) {
        this.companyPublicService = companyPublicService;
    }

    @GetMapping("/{id}")
    public CompanyPublicResponse detail(@PathVariable UUID id) {
        return companyPublicService.getDetail(id);
    }
}
