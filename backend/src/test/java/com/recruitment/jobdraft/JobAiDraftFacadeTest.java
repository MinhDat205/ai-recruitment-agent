package com.recruitment.jobdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.recruitment.ai.jobdraft.CriteriaDraftPayload;
import com.recruitment.ai.jobdraft.DraftEmploymentType;
import com.recruitment.ai.jobdraft.DraftWorkMode;
import com.recruitment.ai.jobdraft.JobAiDraftResult;
import com.recruitment.ai.jobdraft.JobAiDraftService;
import com.recruitment.ai.jobdraft.JobDraftPayload;
import com.recruitment.ai.jobdraft.QuestionsDraftPayload;
import com.recruitment.aigeneration.AiGenerationTokenCodec;
import com.recruitment.catalog.CatalogEntry;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.jobdraft.dto.JobAiDraftRequest;
import com.recruitment.jobdraft.dto.JobDraft;
import com.recruitment.jobdraft.dto.JobDraft.MissingField;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// FR-H11 (unit, khong Spring): T4 - han nop theo bean Clock co dinh va gio Viet Nam (R-P4); T12 phan "exception khong
// phai cua K3 noi len ca request" (R-K3H-5) - JobAiDraftService gia nem IllegalStateException o phan JOB.
class JobAiDraftFacadeTest {

    private static final String SECRET = "test-only-secret-khong-dung-that-toi-thieu-32-ky-tu";
    // 2026-10-12T17:30Z = 00:30 ngay 13/10/2026 gio Viet Nam (UTC van la 12/10) - "hom nay" phai la 13/10.
    private static final Instant NOW = Instant.parse("2026-10-12T17:30:00Z");
    private static final LocalDate TODAY_VN = LocalDate.of(2026, 10, 13);

    private static JobAiDraftFacade facade(JobAiDraftService service, Clock clock) {
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        doReturn(Optional.of(new Company())).when(companyRepository).findByOwnerId(any());
        CatalogRegistry registry = new CatalogRegistry(
                List.of(new CatalogEntry("IT_SOFTWARE", "Công nghệ thông tin - Phần mềm", List.of())),
                List.of(new CatalogEntry("DA_NANG", "Đà Nẵng", List.of())));
        return new JobAiDraftFacade(companyRepository, registry, service, new AiGenerationTokenCodec(SECRET, clock),
                clock, null); // J1 khong dung JobAiDraftSaveService
    }

    private static JobDraftPayload payloadWithDeadline(LocalDate deadline) {
        return new JobDraftPayload("Lập trình viên", "Phát triển API", "Java", "Lương tháng 13", "IT_SOFTWARE",
                "Đà Nẵng", DraftEmploymentType.FULL_TIME, DraftWorkMode.ONSITE, new BigDecimal("15000000"),
                new BigDecimal("20000000"), "VND", deadline);
    }

    // ---- T4 ----

    @Test
    void deadline_yesterdayNulled_todayAndTomorrowKept_inVietnamTime() {
        JobAiDraftFacade facade = facade(mock(JobAiDraftService.class), Clock.fixed(NOW, ZoneOffset.UTC));
        String description = "Tuyển lập trình viên tại Đà Nẵng";

        JobDraft yesterday = facade.toJobDraft(payloadWithDeadline(TODAY_VN.minusDays(1)), description);
        JobDraft today = facade.toJobDraft(payloadWithDeadline(TODAY_VN), description);
        JobDraft tomorrow = facade.toJobDraft(payloadWithDeadline(TODAY_VN.plusDays(1)), description);

        assertThat(yesterday.deadline()).isNull();
        assertThat(yesterday.missingFields()).containsExactly(new MissingField(JobDraftField.DEADLINE, FieldNeed.OPTIONAL));
        assertThat(today.deadline()).isEqualTo(TODAY_VN);
        assertThat(today.missingFields()).isEmpty();
        assertThat(tomorrow.deadline()).isEqualTo(TODAY_VN.plusDays(1));
        assertThat(tomorrow.missingFields()).isEmpty();
    }

    // ---- T12 phan exception ngoai K3 ----

    @Test
    void nonK3ExceptionInOnePart_propagates_notSwallowedIntoFailedCell() {
        JobAiDraftService service = mock(JobAiDraftService.class);
        doThrow(new IllegalStateException("loi lap trinh o phan JOB")).when(service).draftJob(anyString(), anyList());
        doReturn(new JobAiDraftResult<>(new CriteriaDraftPayload(List.of()), "claude-test", "job-draft-criteria-v1"))
                .when(service).draftCriteria(anyString());
        doReturn(new JobAiDraftResult<>(new QuestionsDraftPayload(List.of()), "claude-test", "job-draft-questions-v1"))
                .when(service).draftQuestions(anyString());
        JobAiDraftFacade facade = facade(service, Clock.systemUTC());
        JobAiDraftRequest request = new JobAiDraftRequest(
                "Tuyển lập trình viên", List.of(JobDraftPart.JOB, JobDraftPart.CRITERIA, JobDraftPart.QUESTIONS));

        assertThatThrownBy(() -> facade.generate(UUID.randomUUID(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("loi lap trinh o phan JOB");
    }
}
