package com.recruitment.jobdraft;

import com.recruitment.common.exception.ErrorResponse;
import com.recruitment.common.exception.InvalidJobDraftRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// FR-H11 R-A3 - body JSON hong hoac gia tri enum la (parts, va o J2) -> 400 INVALID_JOB_DRAFT_REQUEST. Pham vi DUNG
// controller cua FR-H11 (assignableTypes) - POST /api/hr/jobs va moi endpoint khac giu 400 mac dinh (T11); KHONG dua
// handler nay vao GlobalExceptionHandler. @Order(HIGHEST_PRECEDENCE) - mau C07 L8.
@RestControllerAdvice(assignableTypes = JobAiDraftController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JobAiDraftExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(JobAiDraftExceptionAdvice.class);

    // Body loi KHONG chua ex.getMessage() (co the lap lai noi dung nguoi dung gui).
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Body yeu cau tao tin bang AI khong doc duoc", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INVALID_JOB_DRAFT_REQUEST", InvalidJobDraftRequestException.unreadableBody().getMessage()));
    }
}
