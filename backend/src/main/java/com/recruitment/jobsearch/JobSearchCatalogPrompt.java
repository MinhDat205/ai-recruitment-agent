package com.recruitment.jobsearch;

import com.recruitment.catalog.CatalogEntry;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.resume.ResumeParsingService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// FR-U13 R-A2 - hai chuoi {provinceCatalog}, {industryCatalog} cua job-search-parse-v1.st, dung MOT lan luc khoi tao bean
// tu CatalogRegistry (danh muc C05 co dinh, chi doi qua migration). Du lieu he thong, KHONG lay tu request.
// Moi muc mot dong "MA: Nhan (còn gọi: bi danh 1, bi danh 2)" theo sort_order; muc khong co bi danh: "MA: Nhan". Thu tu
// bi danh giu nguyen thu tu registry tra ve (CatalogJdbcLoader - phu thuoc collation, muc 13.5/L9). Nganh bo OTHER
// (hang ResumeParsingService.EXCLUDED_INDUSTRY_CODE - khuon U12 R-F5).
@Component
public class JobSearchCatalogPrompt {

    static final String ALIAS_PREFIX = " (còn gọi: ";

    private final String provinceCatalog;
    private final String industryCatalog;

    public JobSearchCatalogPrompt(CatalogRegistry catalogRegistry) {
        this.provinceCatalog = format(catalogRegistry.provinces());
        this.industryCatalog = format(catalogRegistry.industries().stream()
                .filter(entry -> !ResumeParsingService.EXCLUDED_INDUSTRY_CODE.equals(entry.code()))
                .toList());
    }

    public String provinceCatalog() {
        return provinceCatalog;
    }

    public String industryCatalog() {
        return industryCatalog;
    }

    static String format(List<CatalogEntry> entries) {
        return entries.stream().map(JobSearchCatalogPrompt::line).collect(Collectors.joining("\n"));
    }

    static String line(CatalogEntry entry) {
        String head = entry.code() + ": " + entry.label();
        if (entry.aliases().isEmpty()) {
            return head;
        }
        return head + ALIAS_PREFIX + String.join(", ", entry.aliases()) + ")";
    }
}
