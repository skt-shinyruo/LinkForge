package com.linkforge.edge.web.error;

import com.linkforge.platform.web.RequestId;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.linkforge.edge")
public class EdgeGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EdgeGlobalExceptionHandler.class);

    @ExceptionHandler(EdgeBusinessException.class)
    public ResponseEntity<EdgeErrorResponse> handleBusiness(EdgeBusinessException ex) {
        EdgeErrorCode ec = ex.getErrorCode();
        HttpStatus status = mapToHttpStatus(ec);
        EdgeErrorResponse body = new EdgeErrorResponse(ec.getCode(), ex.getMessage(), RequestId.get());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<EdgeErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getAllErrors().isEmpty()
                ? EdgeErrorCode.BAD_REQUEST.getDefaultMessage()
                : ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        EdgeErrorResponse body = new EdgeErrorResponse(EdgeErrorCode.BAD_REQUEST.getCode(), msg, RequestId.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<EdgeErrorResponse> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().isEmpty()
                ? EdgeErrorCode.BAD_REQUEST.getDefaultMessage()
                : ex.getConstraintViolations().iterator().next().getMessage();
        EdgeErrorResponse body = new EdgeErrorResponse(EdgeErrorCode.BAD_REQUEST.getCode(), msg, RequestId.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<EdgeErrorResponse> handleOther(Exception ex) {
        // 避免把敏感信息回传给客户端；详细信息记录在服务端日志
        log.error("Unhandled exception", ex);
        EdgeErrorResponse body = new EdgeErrorResponse(
                EdgeErrorCode.INTERNAL_ERROR.getCode(),
                EdgeErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                RequestId.get()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static HttpStatus mapToHttpStatus(EdgeErrorCode ec) {
        if (ec == EdgeErrorCode.FORBIDDEN) {
            return HttpStatus.FORBIDDEN;
        }
        if (ec == EdgeErrorCode.TOO_MANY_REQUESTS) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (ec == EdgeErrorCode.LINK_NOT_FOUND || ec == EdgeErrorCode.NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        if (ec == EdgeErrorCode.LINK_DISABLED || ec == EdgeErrorCode.LINK_EXPIRED) {
            return HttpStatus.GONE;
        }
        if (ec == EdgeErrorCode.BAD_REQUEST) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ec == EdgeErrorCode.INTERNAL_ERROR) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
