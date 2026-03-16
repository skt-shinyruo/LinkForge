package com.linkforge.redirect.interfaces.web.error;

import com.linkforge.redirect.application.error.RedirectBusinessException;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import com.linkforge.foundation.web.RequestId;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.linkforge.redirect")
public class RedirectGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RedirectGlobalExceptionHandler.class);

    @ExceptionHandler(RedirectBusinessException.class)
    public ResponseEntity<RedirectErrorResponse> handleBusiness(RedirectBusinessException ex) {
        RedirectErrorCode ec = ex.getErrorCode();
        HttpStatus status = mapToHttpStatus(ec);
        RedirectErrorResponse body = new RedirectErrorResponse(ec.getCode(), ex.getMessage(), RequestId.get());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RedirectErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getAllErrors().isEmpty()
                ? RedirectErrorCode.BAD_REQUEST.getDefaultMessage()
                : ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        RedirectErrorResponse body = new RedirectErrorResponse(RedirectErrorCode.BAD_REQUEST.getCode(), msg, RequestId.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RedirectErrorResponse> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().isEmpty()
                ? RedirectErrorCode.BAD_REQUEST.getDefaultMessage()
                : ex.getConstraintViolations().iterator().next().getMessage();
        RedirectErrorResponse body = new RedirectErrorResponse(RedirectErrorCode.BAD_REQUEST.getCode(), msg, RequestId.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RedirectErrorResponse> handleOther(Exception ex) {
        // 避免把敏感信息回传给客户端；详细信息记录在服务端日志
        log.error("Unhandled exception", ex);
        RedirectErrorResponse body = new RedirectErrorResponse(
                RedirectErrorCode.INTERNAL_ERROR.getCode(),
                RedirectErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                RequestId.get()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static HttpStatus mapToHttpStatus(RedirectErrorCode ec) {
        if (ec == RedirectErrorCode.FORBIDDEN) {
            return HttpStatus.FORBIDDEN;
        }
        if (ec == RedirectErrorCode.TOO_MANY_REQUESTS) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (ec == RedirectErrorCode.LINK_NOT_FOUND || ec == RedirectErrorCode.NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        if (ec == RedirectErrorCode.LINK_DISABLED || ec == RedirectErrorCode.LINK_EXPIRED) {
            return HttpStatus.GONE;
        }
        if (ec == RedirectErrorCode.BAD_REQUEST) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ec == RedirectErrorCode.INTERNAL_ERROR) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
