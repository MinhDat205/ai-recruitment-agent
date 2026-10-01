package com.recruitment.catalog;

import com.recruitment.catalog.dto.CatalogResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Cong khai qua quy uoc /api/public/** (SecurityConfig) - khong them quy tac permitAll rieng.
@RestController
@RequestMapping("/api/public/catalogs")
public class CatalogPublicController {

    private final CatalogRegistry catalogRegistry;

    public CatalogPublicController(CatalogRegistry catalogRegistry) {
        this.catalogRegistry = catalogRegistry;
    }

    @GetMapping
    public CatalogResponse list() {
        return CatalogResponse.from(catalogRegistry);
    }
}
