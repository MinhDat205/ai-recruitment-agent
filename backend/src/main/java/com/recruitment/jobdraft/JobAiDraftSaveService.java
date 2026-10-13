package com.recruitment.jobdraft;

import com.recruitment.aigeneration.AiGenerationStamp;
import com.recruitment.job.JobOwnerService;
import com.recruitment.job.dto.JobCreateRequest;
import com.recruitment.job.dto.JobOwnerResponse;
import com.recruitment.rubric.RubricOwnerService;
import com.recruitment.rubric.dto.RubricCriterionRequest;
import com.recruitment.screening.ScreeningQuestionOwnerService;
import com.recruitment.screening.dto.ScreeningQuestionRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-H11 J2 R-L1 - ghi nguyen khoi trong MOT transaction, bean rieng (facade goi vao - khong tu goi trong cung bean,
// CLAUDE.md muc 3c). Goi NGUYEN cac service co san, khong chep logic: JobOwnerService.create (Job + Rubric + Mau giay
// moi, luon DRAFT), RubricOwnerService.addCriterion, ScreeningQuestionOwnerService.add; roi cac dong K4. Moi kiem dau
// vao da chay o facade truoc khi vao day (R-L4); kiem ben trong cac service van chay (phong thu chieu sau) - no o dau
// thi rollback ca khoi, khong co tin "luu do". Khong goi AI, khong doi trang thai tin (R-L3).
@Service
public class JobAiDraftSaveService {

    private final JobOwnerService jobOwnerService;
    private final RubricOwnerService rubricOwnerService;
    private final ScreeningQuestionOwnerService screeningQuestionOwnerService;
    private final JobAiGenerationRepository jobAiGenerationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public JobAiDraftSaveService(
            JobOwnerService jobOwnerService,
            RubricOwnerService rubricOwnerService,
            ScreeningQuestionOwnerService screeningQuestionOwnerService,
            JobAiGenerationRepository jobAiGenerationRepository) {
        this.jobOwnerService = jobOwnerService;
        this.rubricOwnerService = rubricOwnerService;
        this.screeningQuestionOwnerService = screeningQuestionOwnerService;
        this.jobAiGenerationRepository = jobAiGenerationRepository;
    }

    // stamps: chi cac phan co token hop le (da verify o facade) - gia tri lay tu token, khong tu client (R-K4-2).
    @Transactional
    public JobOwnerResponse save(UUID ownerId, PreparedDraft draft) {
        JobOwnerResponse job = jobOwnerService.create(ownerId, draft.job());
        // L2 - create dung save() khong flush; incrementRevision cua cau hoi la native UPDATE + clear() (khong
        // flushAutomatically). Flush tuong minh de INSERT Job/Rubric/Mau giay moi chay TRUOC, ke ca khi 0 tieu chi.
        entityManager.flush();

        // R-L6 - thu tu request; displayOrder da dat 0..n-1 o facade.
        for (RubricCriterionRequest criterion : draft.criteria()) {
            rubricOwnerService.addCriterion(ownerId, job.id(), criterion);
        }
        // FR-H10 tu danh display_order 1..m va tang screening_revision moi cau.
        for (ScreeningQuestionRequest question : draft.questions()) {
            screeningQuestionOwnerService.add(ownerId, job.id(), question);
        }
        // R-K4-6 - mot dong moi phan da dung. createdAt cua entity null sau save (DB tu dien) - khong doc o day.
        for (Map.Entry<JobDraftPart, AiGenerationStamp> stamp : draft.stamps().entrySet()) {
            jobAiGenerationRepository.saveAndFlush(new JobAiGeneration(job.id(), stamp.getKey(), stamp.getValue()));
        }
        return job;
    }

    // Du lieu da kiem va chuan hoa o facade (quyen loi da ghep vao mo ta - R-P7; ten/mo ta tieu chi da strip - L7).
    public record PreparedDraft(
            JobCreateRequest job,
            List<RubricCriterionRequest> criteria,
            List<ScreeningQuestionRequest> questions,
            Map<JobDraftPart, AiGenerationStamp> stamps) {
    }
}
