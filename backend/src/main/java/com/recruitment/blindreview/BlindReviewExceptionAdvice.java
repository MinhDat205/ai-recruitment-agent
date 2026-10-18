package com.recruitment.blindreview;

import com.recruitment.common.exception.ErrorResponse;
import com.recruitment.common.exception.InvalidBlindReviewRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// FR-H16 B1 (REQUIREMENT muc 4.3) - body khong doc duoc (thieu body, JSON hong, enabled sai kieu) -> 400
// INVALID_BLIND_REVIEW_REQUEST. Khuon TalentPoolExceptionAdvice: gioi han theo controller, @Order(HIGHEST_PRECEDENCE).
// Body loi KHONG chua ex.getMessage() (co the lap lai noi dung nguoi dung gui); chi tiet chi o log.debug.
@RestControllerAdvice(assignableTypes = BlindReviewController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BlindReviewExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(BlindReviewExceptionAdvice.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Body yeu cau bat/tat che do an danh khong doc duoc", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INVALID_BLIND_REVIEW_REQUEST", new InvalidBlindReviewRequestException().getMessage()));
    }
}
