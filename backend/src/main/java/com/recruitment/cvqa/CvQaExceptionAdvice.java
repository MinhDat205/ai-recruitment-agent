package com.recruitment.cvqa;

import com.recruitment.common.exception.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// FR-C08 R-Q7 - body JSON hong -> 400 INVALID_CV_QA_REQUEST (khuon MessageDraftExceptionAdvice cua C07). Pham vi DUNG
// controller hoi dap CV (assignableTypes): moi endpoint JSON khac giu nguyen 400 mac dinh - KHONG dua handler nay vao
// GlobalExceptionHandler. Chi bat MOT loai exception; exception khac roi xuong GlobalExceptionHandler.
//
// L5: dot 3 chi khai CvQaHrController; dot 4 them CvQaCandidateController.
//
// @Order(HIGHEST_PRECEDENCE): resolver duyet advice theo OrderComparator va lay handler khop dau tien - giu advice nay
// dung truoc ke ca khi sau nay GlobalExceptionHandler co handler bat Exception.
@RestControllerAdvice(assignableTypes = {CvQaHrController.class, CvQaCandidateController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CvQaExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(CvQaExceptionAdvice.class);

    // Muc 4.4 - cau co dinh.
    static final String UNREADABLE_BODY_MESSAGE = "Yêu cầu hỏi đáp CV không hợp lệ.";

    // Body loi KHONG chua ex.getMessage() (co the lap lai noi dung body nguoi dung gui, gom ca cau hoi) va khong noi gi
    // ve don/CV.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Body yeu cau hoi dap CV khong doc duoc", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_CV_QA_REQUEST", UNREADABLE_BODY_MESSAGE));
    }
}
