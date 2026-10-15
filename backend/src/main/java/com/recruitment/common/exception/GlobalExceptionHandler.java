package com.recruitment.common.exception;

import io.jsonwebtoken.JwtException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// AccessDeniedException va "chua xac thuc" cho path duoc bao ve boi SecurityFilterChain
// (vi du /api/hr/**) da duoc xu ly o tang filter chain bang JsonAuthenticationEntryPoint/
// JsonAccessDeniedHandler - khong roi vao day vi xay ra truoc khi toi DispatcherServlet.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("EMAIL_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("BAD_CREDENTIALS", "Email hoặc mật khẩu không đúng"));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(JwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_TOKEN", "Token không hợp lệ hoặc đã hết hạn"));
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobNotFound(JobNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("JOB_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(CompanyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCompanyNotFound(CompanyNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("COMPANY_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InterviewTemplateNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInterviewTemplateNotFound(InterviewTemplateNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("INTERVIEW_TEMPLATE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(CompanyAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCompanyAlreadyExists(CompanyAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("COMPANY_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidLogoFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLogoFile(InvalidLogoFileException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_LOGO_FILE", ex.getMessage()));
    }

    // Tomcat/Spring chan file vuot app.servlet.multipart.max-file-size (application.yml) TRUOC KHI
    // request toi duoc controller - InvalidResumeFileException/InvalidLogoFileException trong
    // service khong bao gio duoc nem trong truong hop nay. Khong co handler rieng thi loi nay roi
    // ve 500 mac dinh thay vi 400. Dung chung cho ca upload logo (FR-H01) lan upload resume (FR-U01).
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_RESUME_FILE", "File vượt quá dung lượng cho phép"));
    }

    @ExceptionHandler(RubricNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRubricNotFound(RubricNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RUBRIC_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(RubricCriterionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRubricCriterionNotFound(RubricCriterionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RUBRIC_CRITERION_NOT_FOUND", ex.getMessage()));
    }

    // FR-H10 - bon ma loi cua cau hoi sang loc (REQUIREMENT R-Q1, R-Q4b, R-Q6, R-A2).
    @ExceptionHandler(ScreeningQuestionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleScreeningQuestionNotFound(ScreeningQuestionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SCREENING_QUESTION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ScreeningQuestionLimitReachedException.class)
    public ResponseEntity<ErrorResponse> handleScreeningQuestionLimitReached(ScreeningQuestionLimitReachedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("SCREENING_QUESTION_LIMIT_REACHED", ex.getMessage()));
    }

    @ExceptionHandler(InvalidScreeningQuestionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidScreeningQuestion(InvalidScreeningQuestionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_SCREENING_QUESTION", ex.getMessage()));
    }

    @ExceptionHandler(InvalidScreeningOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidScreeningOrder(InvalidScreeningOrderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_SCREENING_ORDER", ex.getMessage()));
    }

    // FR-U09 R-V1 - moi vi pham cau tra loi chan o service, khong roi xuong CHECK cua V13 thanh 500.
    @ExceptionHandler(InvalidScreeningAnswerException.class)
    public ResponseEntity<ErrorResponse> handleInvalidScreeningAnswer(InvalidScreeningAnswerException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_SCREENING_ANSWER", ex.getMessage()));
    }

    // FR-U09 R-K7 - 409: don dung cua ung vien nhung khong con dong y de rut.
    @ExceptionHandler(TalentPoolConsentNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleTalentPoolConsentNotActive(TalentPoolConsentNotActiveException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("TALENT_POOL_CONSENT_NOT_ACTIVE", ex.getMessage()));
    }

    // FR-U09 R-R1, R-R2 - 409: request hop le nhung bo cau hoi da doi giua luc ung vien dang dien.
    @ExceptionHandler(ScreeningQuestionsChangedException.class)
    public ResponseEntity<ErrorResponse> handleScreeningQuestionsChanged(ScreeningQuestionsChangedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SCREENING_QUESTIONS_CHANGED", ex.getMessage()));
    }

    @ExceptionHandler(RubricLockedException.class)
    public ResponseEntity<ErrorResponse> handleRubricLocked(RubricLockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("RUBRIC_LOCKED", ex.getMessage()));
    }

    @ExceptionHandler(RubricWeightExceededException.class)
    public ResponseEntity<ErrorResponse> handleRubricWeightExceeded(RubricWeightExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("RUBRIC_WEIGHT_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(RubricIncompleteException.class)
    public ResponseEntity<ErrorResponse> handleRubricIncomplete(RubricIncompleteException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("RUBRIC_INCOMPLETE", ex.getMessage()));
    }

    @ExceptionHandler(JobCatalogIncompleteException.class)
    public ResponseEntity<ErrorResponse> handleJobCatalogIncomplete(JobCatalogIncompleteException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("JOB_CATALOG_INCOMPLETE", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCatalogCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCatalogCode(InvalidCatalogCodeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_CATALOG_CODE", ex.getMessage()));
    }

    @ExceptionHandler(InvalidJobDeadlineException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJobDeadline(InvalidJobDeadlineException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_JOB_DEADLINE", ex.getMessage()));
    }

    @ExceptionHandler(InvalidResumeFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResumeFile(InvalidResumeFileException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_RESUME_FILE", ex.getMessage()));
    }

    @ExceptionHandler(ResumeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResumeNotFound(ResumeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RESUME_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ResumeParsedDataNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResumeParsedDataNotFound(ResumeParsedDataNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RESUME_PARSED_DATA_NOT_FOUND", ex.getMessage()));
    }

    // 409 - CV khong o trang thai FAILED (muc 3b, chore/hardening), xem ResumeRetryNotAllowedException.
    @ExceptionHandler(ResumeRetryNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleResumeRetryNotAllowed(ResumeRetryNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("RESUME_RETRY_NOT_ALLOWED", ex.getMessage()));
    }

    // FR-C05 R-R2 - hai ly do 409 cua trich xuat lai, ma loi rieng de frontend phan biet neu can.
    @ExceptionHandler(ResumeReparseNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleResumeReparseNotAllowed(ResumeReparseNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("RESUME_REPARSE_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(ResumeReparseInProgressException.class)
    public ResponseEntity<ErrorResponse> handleResumeReparseInProgress(ResumeReparseInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("RESUME_REPARSE_IN_PROGRESS", ex.getMessage()));
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleApplicationNotFound(ApplicationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("APPLICATION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ApplicationNotWithdrawableException.class)
    public ResponseEntity<ErrorResponse> handleApplicationNotWithdrawable(ApplicationNotWithdrawableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("APPLICATION_NOT_WITHDRAWABLE", ex.getMessage()));
    }

    // 400, KHAC voi ApplicationNotWithdrawableException (409): day la HR gui sai buoc chuyen trong
    // request, khong phai "tai nguyen khong con o trang thai cho phep hanh dong" nhu truong hop rut don.
    @ExceptionHandler(InvalidApplicationStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidApplicationStatusTransition(
            InvalidApplicationStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_APPLICATION_STATUS_TRANSITION", ex.getMessage()));
    }

    // 409 - lost-update chan boi updateStatusIfCurrent (Dot 2, chore/hardening), xem
    // ApplicationStatusConflictException.
    @ExceptionHandler(ApplicationStatusConflictException.class)
    public ResponseEntity<ErrorResponse> handleApplicationStatusConflict(ApplicationStatusConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("APPLICATION_STATUS_CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(InvalidInterviewScheduleException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInterviewSchedule(InvalidInterviewScheduleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INTERVIEW_SCHEDULE", ex.getMessage()));
    }

    // FR-H12 R-G2 - 409 (B1), xem InvitationResendNotAllowedException.
    @ExceptionHandler(InvitationResendNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleInvitationResendNotAllowed(InvitationResendNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVITATION_RESEND_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(ResumeNotParsedException.class)
    public ResponseEntity<ErrorResponse> handleResumeNotParsed(ResumeNotParsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("RESUME_NOT_PARSED", ex.getMessage()));
    }

    @ExceptionHandler(ScoringRunInProgressException.class)
    public ResponseEntity<ErrorResponse> handleScoringRunInProgress(ScoringRunInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SCORING_RUN_IN_PROGRESS", ex.getMessage()));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotificationNotFound(NotificationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOTIFICATION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InterviewInvitationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInterviewInvitationNotFound(InterviewInvitationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("INTERVIEW_INVITATION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCandidateSearchFilterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCandidateSearchFilter(InvalidCandidateSearchFilterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_CANDIDATE_SEARCH_FILTER", ex.getMessage()));
    }

    // FR-U07 R-F5/R-S3 - tham so loc Job cong khai sai (workMode/sort/postedWithin la/salary am hoac
    // min>max).
    @ExceptionHandler(InvalidJobFilterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJobFilter(InvalidJobFilterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_JOB_FILTER", ex.getMessage()));
    }

    // FR-U14 R-V2 - sau dedupe van qua gioi han so luong (nganh/khu vuc/ky nang), ky nang sai do dai,
    // hinh thuc lam viec mong muon ngoai tap. Mot ma loi chung, thong diep khac nhau qua getMessage(),
    // dung khuon InvalidJobFilterException o tren.
    @ExceptionHandler(InvalidProfileFieldException.class)
    public ResponseEntity<ErrorResponse> handleInvalidProfileField(InvalidProfileFieldException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_PROFILE_FIELD", ex.getMessage()));
    }

    // FR-U14 R-A1 - chua co CV chinh da phan tich xong de dien tu dong "Dien tu CV".
    @ExceptionHandler(PrimaryResumeNotParsedException.class)
    public ResponseEntity<ErrorResponse> handlePrimaryResumeNotParsed(PrimaryResumeNotParsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("NO_PRIMARY_RESUME_PARSED", ex.getMessage()));
    }

    // FR-C06 R-M1 - tin khong co chu va khong co tep.
    @ExceptionHandler(MessageEmptyException.class)
    public ResponseEntity<ErrorResponse> handleMessageEmpty(MessageEmptyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MESSAGE_EMPTY", ex.getMessage()));
    }

    // FR-C06 R-M3 - noi dung sau chuan hoa qua 4000 ky tu.
    @ExceptionHandler(MessageTooLongException.class)
    public ResponseEntity<ErrorResponse> handleMessageTooLong(MessageTooLongException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MESSAGE_TOO_LONG", ex.getMessage()));
    }

    // FR-C06 R-F2/R-F3 - mot ma loi chung cho tep dinh kem, cau thong bao khac nhau qua getMessage().
    @ExceptionHandler(InvalidMessageAttachmentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMessageAttachment(InvalidMessageAttachmentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_MESSAGE_ATTACHMENT", ex.getMessage()));
    }

    // FR-C06 R-Q4 - tin khong ton tai hoac thuoc don khac.
    @ExceptionHandler(MessageNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotFound(MessageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("MESSAGE_NOT_FOUND", ex.getMessage()));
    }

    // FR-C06 R-F8 - tin khong co tep, hoac tep khong con trong kho.
    @ExceptionHandler(MessageAttachmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMessageAttachmentNotFound(MessageAttachmentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("MESSAGE_ATTACHMENT_NOT_FOUND", ex.getMessage()));
    }

    // FR-C06 R-M4 - 409 (khong phai 400): request hop le nhung don khong con o trang thai cho phep gui tin,
    // cung ly do voi ApplicationNotWithdrawableException.
    @ExceptionHandler(ConversationReadOnlyException.class)
    public ResponseEntity<ErrorResponse> handleConversationReadOnly(ConversationReadOnlyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONVERSATION_READ_ONLY", ex.getMessage()));
    }

    // FR-C07 R-S3 - tinh huong chua dung duoc voi trang thai hien tai cua don.
    @ExceptionHandler(DraftScenarioUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDraftScenarioUnavailable(DraftScenarioUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DRAFT_SCENARIO_UNAVAILABLE", ex.getMessage()));
    }

    // FR-C07 R-S1/R-S4/R-S5 - mot ma loi chung, cau co dinh khac nhau qua getMessage(). Body JSON hong (R-Q3b) KHONG di
    // qua day ma qua MessageDraftExceptionAdvice (chi ap cho hai controller soan nhap).
    @ExceptionHandler(InvalidDraftRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDraftRequest(InvalidDraftRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_DRAFT_REQUEST", ex.getMessage()));
    }

    // FR-C07 R-K3-6 - loi K3 (goi AI dong bo). Body chi gom ma + cau co dinh cua AiSyncErrorCode - KHONG ex.getMessage()
    // cua exception goc hay output tho LLM (exception goc chi duoc SyncAiCaller ghi o log.debug).
    @ExceptionHandler(AiSyncFailedException.class)
    public ResponseEntity<ErrorResponse> handleAiSyncFailed(AiSyncFailedException ex) {
        AiSyncErrorCode errorCode = ex.errorCode();
        HttpStatus status = switch (errorCode) {
            case AI_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case AI_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case AI_INVALID_OUTPUT -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(errorCode.name(), errorCode.message()));
    }

    // FR-H11 R-N2, R-N3 - J1 sai dau vao. Body JSON hong cua J1 KHONG di qua day (JobAiDraftExceptionAdvice, R-A3).
    @ExceptionHandler(InvalidJobDraftRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJobDraftRequest(InvalidJobDraftRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_JOB_DRAFT_REQUEST", ex.getMessage()));
    }

    // FR-H11 R-W3, R-L4 (5)(6) - danh sach tieu chi/cau hoi cua J2 sai, kiem truoc transaction.
    @ExceptionHandler(InvalidJobDraftSaveException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJobDraftSave(InvalidJobDraftSaveException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_JOB_DRAFT_SAVE", ex.getMessage()));
    }

    // FR-H11 R-K4-4 (K4) - token sieu du lieu AI khong hop le: 400, cau co dinh, khong noi ly do cu the.
    @ExceptionHandler(InvalidAiGenerationTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAiGenerationToken(InvalidAiGenerationTokenException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_AI_GENERATION_TOKEN", ex.getMessage()));
    }

    // FR-C08 R-H1 - mot ma loi, hai cau co dinh qua getMessage(). Body JSON hong (R-Q7) KHONG di qua day ma qua
    // CvQaExceptionAdvice (chi ap cho controller hoi dap CV).
    @ExceptionHandler(InvalidCvQaQuestionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCvQaQuestion(InvalidCvQaQuestionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_CV_QA_QUESTION", ex.getMessage()));
    }

    // FR-C08 R-Q3 - CV chua co van ban trich xuat.
    @ExceptionHandler(CvTextNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleCvTextNotAvailable(CvTextNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CV_TEXT_NOT_AVAILABLE", ex.getMessage()));
    }

    // Chi bat vi pham cu the cua tung UNIQUE constraint da biet. Vi pham nao khac phai roi ve 500
    // mac dinh, khong duoc nuot va tra nham 409.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        // uq_company_per_owner (race condition - service da check ton tai truoc, day la chot
        // chan cuoi cung o DB).
        if (message != null && message.contains("uq_company_per_owner")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("COMPANY_ALREADY_EXISTS", "HR này đã có công ty"));
        }
        // uq_application_per_cycle (FR-U02) - chot chan DUY NHAT chong nop trung, khong co
        // SELECT-truoc-INSERT o tang service vi co race condition.
        if (message != null && message.contains("uq_application_per_cycle")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "APPLICATION_DUPLICATE",
                            "Bạn đã ứng tuyển vị trí này trong đợt tuyển hiện tại (đơn đã rút vẫn được tính)"));
        }
        // uq_criterion_name_per_rubric - chot chan cuoi cung o DB, service khong SELECT-truoc-INSERT
        // de tranh race condition khi hai request them tieu chi trung ten cung luc.
        if (message != null && message.contains("uq_criterion_name_per_rubric")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "RUBRIC_CRITERION_DUPLICATE_NAME", "Tên tiêu chí này đã tồn tại trong rubric"));
        }
        // uq_scoring_run_in_progress (V4, D2) - chot chan cuoi cho race HR bam dup nut "Cham diem
        // ho so" hoac mo hai tab (xem V4__scoring_run_in_progress_unique.sql). Dung LAI chinh xac
        // message cua ScoringRunInProgressException (khong viet lai chuoi) de FE nhan duoc cung
        // mot ma loi/cau chu bat ke duong nao (kiem truoc o service, hay chot chan o DB) da bat.
        if (message != null && message.contains("uq_scoring_run_in_progress")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("SCORING_RUN_IN_PROGRESS", new ScoringRunInProgressException().getMessage()));
        }
        // uq_invitation_active (V16, FR-H12 R-F4, R-G6) - chot chan cuoi chong hai giay moi hieu luc cho mot don (vd du
        // lieu ghi tay); luong gui lai binh thuong da tuan tu hoa bang khoa hang don (R-G3) nen khong cham toi day.
        if (message != null && message.contains("uq_invitation_active")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "INTERVIEW_INVITATION_CONFLICT",
                            "Giấy mời của đơn này vừa được thay đổi ở nơi khác. Vui lòng tải lại trang."));
        }
        throw ex;
    }
}
