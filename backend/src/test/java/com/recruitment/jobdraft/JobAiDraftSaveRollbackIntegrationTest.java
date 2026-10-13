package com.recruitment.jobdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.recruitment.screening.ScreeningQuestionOwnerService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// FR-H11 T17 (phan rollback, R-L1): loi NEM TU BEN TRONG transaction cua J2 (gia lap DataIntegrityViolationException o
// cau hoi cuoi bang spy - qua het kiem truoc o facade) -> rollback CA khoi: khong Job, Rubric, tieu chi, Mau giay moi,
// cau hoi, dong K4 nao con lai. ScreeningQuestionOwnerService la @MockitoSpyBean (goi that, tru lan add thu 2) - lop nay
// vi vay co Spring context RIENG (cung tien le MessageNotificationFailureIntegrationTest).
class JobAiDraftSaveRollbackIntegrationTest extends JobDraftIntegrationTestSupport {

    @MockitoSpyBean
    ScreeningQuestionOwnerService screeningQuestionOwnerService;

    @AfterEach
    void resetSpy() {
        reset(screeningQuestionOwnerService);
    }

    @Test
    void failureOnLastQuestionInsideTransaction_rollsBackWholeDraft() throws Exception {
        String token = hrWithCompany("h11-t17-rollback");
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
                    if (calls.incrementAndGet() == 2) {
                        throw new DataIntegrityViolationException("gia lap loi DB o cau hoi cuoi");
                    }
                    return invocation.callRealMethod();
                })
                .when(screeningQuestionOwnerService)
                .add(any(), any(), any());
        Map<String, Object> body = saveBody();
        body.put("criteria", List.of(criterionInput("Java", null, 30), criterionInput("PostgreSQL", null, 30)));
        body.put("screeningQuestions", List.of(
                questionInput("YES_NO", "Câu một?", List.of(), false),
                questionInput("YES_NO", "Câu hai?", List.of(), true)));
        Map<String, Long> before = rowCounts();

        // Vi pham DB la khong nhan ra duoc -> GlobalExceptionHandler nem lai (server that tra 500); MockMvc nem ra ngoai.
        assertThatThrownBy(() -> postSave(token, body)).hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        assertThat(calls.get()).as("cau 1 da ghi that truoc khi cau 2 no").isEqualTo(2);
        assertThat(rowCounts()).as("rollback ca khoi").isEqualTo(before);
    }
}
