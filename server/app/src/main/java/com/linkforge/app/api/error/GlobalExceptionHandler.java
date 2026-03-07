package com.linkforge.app.api.error;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.web.RequestId;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = {"com.linkforge.accounts", "com.linkforge.shortlink", "com.linkforge.analytics"})
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        ApiResponse<Void> body = ApiResponse.error(ec.getCode(), ex.getMessage(), RequestId.get());
        HttpStatus status = mapToHttpStatus(ec);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getAllErrors().isEmpty()
                ? ErrorCode.BAD_REQUEST.getDefaultMessage()
                : ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), msg, RequestId.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().isEmpty()
                ? ErrorCode.BAD_REQUEST.getDefaultMessage()
                : ex.getConstraintViolations().iterator().next().getMessage();
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), msg, RequestId.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        // 避免把敏感信息回传给客户端；详细信息记录在服务端日志
        log.error("Unhandled exception", ex);
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                RequestId.get()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.FORBIDDEN.getCode(),
                ErrorCode.FORBIDDEN.getDefaultMessage(),
                RequestId.get()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    private static HttpStatus mapToHttpStatus(ErrorCode ec) {
        if (ec == ErrorCode.UNAUTHORIZED || ec == ErrorCode.INVALID_CREDENTIALS || ec == ErrorCode.API_KEY_INVALID) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ec == ErrorCode.FORBIDDEN || ec == ErrorCode.TENANT_DISABLED || ec == ErrorCode.USER_DISABLED
                || ec == ErrorCode.API_KEY_DISABLED) {
            return HttpStatus.FORBIDDEN;
        }
        if (ec == ErrorCode.NOT_FOUND || ec == ErrorCode.LINK_NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        if (ec == ErrorCode.BAD_REQUEST || ec == ErrorCode.INVALID_URL) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ec == ErrorCode.TOO_MANY_REQUESTS) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (ec == ErrorCode.CODE_ALREADY_EXISTS) {
            return HttpStatus.CONFLICT;
        }
        if (ec == ErrorCode.LINK_DISABLED || ec == ErrorCode.LINK_EXPIRED) {
            return HttpStatus.GONE;
        }
        if (ec == ErrorCode.INTERNAL_ERROR) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
