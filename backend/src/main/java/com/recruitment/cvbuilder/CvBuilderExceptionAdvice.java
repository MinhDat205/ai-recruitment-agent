package com.recruitment.cvbuilder;

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

// FR-U12 R-W1 - body khong doc duoc (JSON hong, template la) -> 400 INVALID_CV_BUILDER_REQUEST (khuon CvQaExceptionAdvice).
// Pham vi DUNG controller CV builder (assignableTypes): moi endpoint JSON khac giu nguyen 400 mac dinh. Chi bat MOT loai
// exception; exception khac roi xuong GlobalExceptionHandler. Dot 5 them nhanh B4 (INVALID_CV_REPHRASE_REQUEST, phan
// theo duong dan request).
//
// @Order(HIGHEST_PRECEDENCE): resolver duyet advice theo OrderComparator va lay handler khop dau tien.
@RestControllerAdvice(assignableTypes = CvBuilderCandidateController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CvBuilderExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(CvBuilderExceptionAdvice.class);

    // Muc 4.4 - cau co dinh.
    static final String UNREADABLE_BODY_MESSAGE = "Dữ liệu CV gửi lên không hợp lệ.";

    // Body loi KHONG chua ex.getMessage() (co the lap lai noi dung form nguoi dung gui).
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Body yeu cau CV builder khong doc duoc", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_CV_BUILDER_REQUEST", UNREADABLE_BODY_MESSAGE));
    }
}
