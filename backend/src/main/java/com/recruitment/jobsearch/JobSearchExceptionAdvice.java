package com.recruitment.jobsearch;

import com.recruitment.common.exception.ErrorResponse;
import com.recruitment.common.exception.InvalidJobSearchRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// FR-U13 R-I1 - body khong doc duoc (JSON hong, query sai kieu) -> 400 INVALID_JOB_SEARCH_REQUEST (khuon
// CvQaExceptionAdvice). Pham vi DUNG controller tim viec (assignableTypes): moi endpoint JSON khac giu nguyen 400 mac
// dinh. Chi bat MOT loai exception; exception khac roi xuong GlobalExceptionHandler.
//
// @Order(HIGHEST_PRECEDENCE): resolver duyet advice theo OrderComparator va lay handler khop dau tien.
@RestControllerAdvice(assignableTypes = JobSearchCandidateController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JobSearchExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(JobSearchExceptionAdvice.class);

    // Body loi KHONG chua ex.getMessage() (co the lap lai noi dung body, gom ca cau tim viec - R-L2). Exception chi o debug.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Body yeu cau tim viec khong doc duoc", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INVALID_JOB_SEARCH_REQUEST", InvalidJobSearchRequestException.unreadableBody().getMessage()));
    }
}
