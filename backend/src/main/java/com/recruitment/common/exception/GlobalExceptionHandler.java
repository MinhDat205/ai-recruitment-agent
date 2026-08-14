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
                .body(new ErrorResponse("BAD_CREDENTIALS", "Email hoac mat khau khong dung"));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(JwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_TOKEN", "Token khong hop le hoac da het han"));
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
        throw ex;
    }
}
