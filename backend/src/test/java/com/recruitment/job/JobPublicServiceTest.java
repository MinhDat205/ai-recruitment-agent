package com.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.company.CompanyRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class JobPublicServiceTest {

    // Test nay chi kiem phan trang/pattern - khong can du lieu danh muc that.
    private static final CatalogRegistry EMPTY_CATALOG = new CatalogRegistry(List.of(), List.of());

    @Mock
    private JobRepository jobRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Test
    void search_oversizedSize_isClampedToMax() {
        JobPublicService service = new JobPublicService(jobRepository, companyRepository, EMPTY_CATALOG);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(jobRepository.searchPublicJobs(any(), any(), any(), pageableCaptor.capture()))
                .thenReturn(Page.empty());
        when(companyRepository.findByIdIn(any())).thenReturn(List.of());

        service.search(null, null, null, 0, 100_000);

        assertThat(pageableCaptor.getValue().getPageSize()).isLessThanOrEqualTo(50);
    }

    @Test
    void search_blankKeyword_passesNullPattern() {
        JobPublicService service = new JobPublicService(jobRepository, companyRepository, EMPTY_CATALOG);
        when(jobRepository.searchPublicJobs(isNull(), any(), any(), any())).thenReturn(Page.empty());
        when(companyRepository.findByIdIn(any())).thenReturn(List.of());

        service.search("   ", null, null, null, null);

        verify(jobRepository).searchPublicJobs(isNull(), any(), any(), any());
    }
}
