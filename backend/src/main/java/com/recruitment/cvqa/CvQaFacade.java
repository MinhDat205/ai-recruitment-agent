package com.recruitment.cvqa;

import com.recruitment.ai.cvqa.CvQaAnswer;
import com.recruitment.ai.cvqa.CvQaRefusalReason;
import com.recruitment.ai.cvqa.CvQaService;
import com.recruitment.aicontext.ContextViewer;
import com.recruitment.blindreview.BlindReviewResolver;
import com.recruitment.blindreview.IdentityMaskSource;
import com.recruitment.blindreview.IdentityMasker;
import com.recruitment.aicontext.CvQaContext;
import com.recruitment.aicontext.CvQaContextAssembler;
import com.recruitment.common.exception.InvalidCvQaQuestionException;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.cvqa.dto.CvQaRequest;
import com.recruitment.cvqa.dto.CvQaResponse;
import com.recruitment.jobapplication.HrApplicationAccess;
import com.recruitment.resume.ResumeRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

// FR-C08 - dieu phoi hoi dap CV. KHONG mo transaction (R-A1, CLAUDE.md muc 3c): moi buoc nap du lieu chay trong
// transaction ngan rieng cua repository/K1 va dong truoc khi goi AI - K3 nem IllegalStateException neu lo bi boc
// transaction.
//
// Thu tu R-Q6, dung o loi dau tien; moi loi truoc buoc 5 khong goi ChatModel:
//   1. quyen (R-Q1: HrApplicationAccess.loadOwned - khong cong ty 404, khong co don 404, don cong ty khac 403;
//      R-Q2: ResumeRepository.findByIdAndCandidateId - CV nguoi khac va CV khong ton tai CUNG 404 RESUME_NOT_FOUND);
//   2. cau hoi hop le (R-H1) -> 400 INVALID_CV_QA_QUESTION;
//   3. chan tu khoa (R-G) -> 200 REFUSED, dung - KHONG dung K1, khong doc raw_text;
//   4. dung ngu canh K1 -> 409 CV_TEXT_NOT_AVAILABLE khi thieu van ban (R-Q3);
//   5. goi AI qua K3 (CvQaService) -> kiem output (R-O).
// Body khong doc duoc (buoc 0, R-Q7) xu ly o CvQaExceptionAdvice, truoc khi vao day. Vai tro suy tu controller (duong
// dan), khong nhan tu request (R-Q5).
//
// Khong luu gi, khong doi trang thai don, khong tao thong bao (R-N1, R-N2). Cau hoi khong ghi log muc thuong (R-H3).
@Service
public class CvQaFacade {

    // R-H1
    static final int MAX_QUESTION_LENGTH = 500;

    private final HrApplicationAccess hrApplicationAccess;
    private final ResumeRepository resumeRepository;
    private final CvQaQuestionGuard questionGuard;
    private final CvQaContextAssembler contextAssembler;
    private final CvQaService cvQaService;
    private final BlindReviewResolver blindReviewResolver;
    private final IdentityMaskSource identityMaskSource;

    public CvQaFacade(
            HrApplicationAccess hrApplicationAccess,
            ResumeRepository resumeRepository,
            CvQaQuestionGuard questionGuard,
            CvQaContextAssembler contextAssembler,
            CvQaService cvQaService,
            BlindReviewResolver blindReviewResolver,
            IdentityMaskSource identityMaskSource) {
        this.hrApplicationAccess = hrApplicationAccess;
        this.resumeRepository = resumeRepository;
        this.questionGuard = questionGuard;
        this.contextAssembler = contextAssembler;
        this.cvQaService = cvQaService;
        this.blindReviewResolver = blindReviewResolver;
        this.identityMaskSource = identityMaskSource;
    }

    // Q1 - phia HR. Don moi trang thai, ke ca WITHDRAWN; tin xoa mem/dong van hoi duoc (R-Q1, loadOwned khong loc).
    // ai_consent = TRUE la bat bien DB (chk_consent_true, R-Q4) - khong kiem runtime.
    public CvQaResponse askAsHr(UUID ownerId, UUID applicationId, CvQaRequest request) {
        hrApplicationAccess.loadOwned(ownerId, applicationId);
        String question = normalizeQuestion(request.question());
        Optional<CvQaRefusalReason> refusal = questionGuard.check(question, ContextViewer.HR);
        if (refusal.isPresent()) {
            return CvQaResponse.from(CvQaAnswer.refused(refusal.get()));
        }
        CvQaContext context = contextAssembler.forHrApplication(applicationId);
        CvQaResponse response = CvQaResponse.from(cvQaService.answer(context, question));
        // FR-H16 R-Q1, R-A3 - K2 da kiem trich dan tren cau tra loi THO trong CvQaService; che SAU do, chi o response HR.
        if (!blindReviewResolver.isMasked(applicationId)) {
            return response;
        }
        IdentityMasker masker = identityMaskSource.load(applicationId);
        return new CvQaResponse(
                response.outcome(),
                masker.mask(response.answer()),
                response.citations().stream().map(c -> new CvQaResponse.Citation(masker.mask(c.quote()))).toList(),
                response.refusalReason(),
                response.cvTruncated());
    }

    // Q2 - phia ung vien. Moi CV cua minh co van ban trich xuat, dung trong don hay khong; khong chon JD (R-Q2, Q9).
    // Khong can dong y AI - CV cua chinh minh (R-Q4). Nhan than KHONG chan o R-G phia ung vien (R-G5).
    public CvQaResponse askAsCandidate(UUID candidateId, UUID resumeId, CvQaRequest request) {
        resumeRepository
                .findByIdAndCandidateId(resumeId, candidateId)
                .orElseThrow(() -> new ResumeNotFoundException(resumeId));
        String question = normalizeQuestion(request.question());
        Optional<CvQaRefusalReason> refusal = questionGuard.check(question, ContextViewer.CANDIDATE);
        if (refusal.isPresent()) {
            return CvQaResponse.from(CvQaAnswer.refused(refusal.get()));
        }
        CvQaContext context = contextAssembler.forCandidateResume(resumeId);
        return CvQaResponse.from(cvQaService.answer(context, question));
    }

    // R-H1 - doi \r\n / \r -> \n, strip hai dau (ca khoang trang Unicode nhu U+2003 - L27, khop trim() cua frontend);
    // null/rong sau strip -> "Vui lòng nhập câu hỏi."; dai hon 500 (String.length() sau strip) -> "Câu hỏi tối đa 500
    // ký tự.". 1 va 500 hop le.
    static String normalizeQuestion(String rawQuestion) {
        if (rawQuestion == null) {
            throw InvalidCvQaQuestionException.emptyQuestion();
        }
        String question = rawQuestion.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (question.isEmpty()) {
            throw InvalidCvQaQuestionException.emptyQuestion();
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw InvalidCvQaQuestionException.questionTooLong();
        }
        return question;
    }
}
