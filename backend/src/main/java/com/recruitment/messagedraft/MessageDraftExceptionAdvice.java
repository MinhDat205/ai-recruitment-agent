package com.recruitment.messagedraft;

import com.recruitment.common.exception.ErrorResponse;
import com.recruitment.common.exception.InvalidDraftRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// FR-C07 R-Q3b (muc 4.2) - body JSON hong hoac gia tri enum la cua A2 -> 400 INVALID_DRAFT_REQUEST. Pham vi DUNG hai
// controller soan nhap (assignableTypes): moi endpoint JSON khac giu nguyen 400 mac dinh cua Spring - KHONG dua handler
// nay vao GlobalExceptionHandler (muc 0.b7, T8 hoi quy). Chi bat MOT loai exception; exception khac cua hai controller
// van roi xuong GlobalExceptionHandler.
//
// @Order(HIGHEST_PRECEDENCE) (L8): resolver duyet advice theo OrderComparator va lay handler khop dau tien - giu advice
// nay dung truoc ke ca khi sau nay GlobalExceptionHandler co handler bat Exception.
@RestControllerAdvice(assignableTypes = {MessageDraftHrController.class, MessageDraftCandidateController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MessageDraftExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(MessageDraftExceptionAdvice.class);

    // Body loi KHONG chua ex.getMessage() (co the lap lai noi dung body nguoi dung gui) va khong noi gi ve don.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Body yeu cau soan nhap khong doc duoc", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INVALID_DRAFT_REQUEST", InvalidDraftRequestException.unreadableBody().getMessage()));
    }
}
