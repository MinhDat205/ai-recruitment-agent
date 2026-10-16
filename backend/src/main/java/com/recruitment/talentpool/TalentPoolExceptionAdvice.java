package com.recruitment.talentpool;

import com.recruitment.common.exception.ErrorResponse;
import com.recruitment.common.exception.InvalidTalentPoolNameException;
import com.recruitment.common.exception.InvalidTalentPoolSelectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;

// FR-H15 (REQUIREMENT muc 4.2) - body khong doc duoc (JSON hong, thieu body, sai kieu, phan tu khong phai UUID) cua hai
// controller H15 -> 400 voi ma theo endpoint: P7 (ApplicationTalentPoolController) INVALID_TALENT_POOL_SELECTION, P2/P4
// (TalentPoolOwnerController) INVALID_TALENT_POOL_NAME. Khuon CvQaExceptionAdvice: gioi han theo controller,
// @Order(HIGHEST_PRECEDENCE). Body loi KHONG chua ex.getMessage() (co the lap lai noi dung nguoi dung gui); chi tiet chi
// o log.debug.
@RestControllerAdvice(assignableTypes = {TalentPoolOwnerController.class, ApplicationTalentPoolController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TalentPoolExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(TalentPoolExceptionAdvice.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HandlerMethod handler) {
        log.debug("Body yeu cau kho ung vien khong doc duoc", ex);
        if (ApplicationTalentPoolController.class.isAssignableFrom(handler.getBeanType())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(
                            "INVALID_TALENT_POOL_SELECTION", new InvalidTalentPoolSelectionException().getMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_TALENT_POOL_NAME", new InvalidTalentPoolNameException().getMessage()));
    }
}
