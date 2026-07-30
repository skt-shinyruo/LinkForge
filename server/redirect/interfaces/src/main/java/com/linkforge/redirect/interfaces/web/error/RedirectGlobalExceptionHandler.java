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

/**
 * Redirect Controller 后阶段的异常到 HTTP 映射。
 *
 * <p>它与 Filter 使用的 {@link RedirectErrorResponseWriter} 分工：前者处理已经进入 MVC 的业务、校验和
 * 未预期异常，后者处理请求前风控拒绝。未预期异常只返回安全默认消息，完整异常保留在服务端日志。</p>
 */
@RestControllerAdvice(basePackages = "com.linkforge.redirect")
public class RedirectGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RedirectGlobalExceptionHandler.class);

    /**
     * 保留 Redirect 业务异常的稳定错误码和语义 HTTP 状态。
     */
    @ExceptionHandler(RedirectBusinessException.class)
    public ResponseEntity<RedirectErrorResponse> handleBusiness(RedirectBusinessException ex) {
        RedirectErrorCode ec = ex.getErrorCode();
        HttpStatus status = mapToHttpStatus(ec);
        RedirectErrorResponse body = new RedirectErrorResponse(ec.getCode(), ex.getMessage(), RequestId.get());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 将 MVC 参数校验失败收敛为安全的 400 响应。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RedirectErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getAllErrors().isEmpty()
                ? RedirectErrorCode.BAD_REQUEST.getDefaultMessage()
                : ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        RedirectErrorResponse body = new RedirectErrorResponse(RedirectErrorCode.BAD_REQUEST.getCode(), msg, RequestId.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 将方法级约束失败收敛为安全的 400 响应。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RedirectErrorResponse> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().isEmpty()
                ? RedirectErrorCode.BAD_REQUEST.getDefaultMessage()
                : ex.getConstraintViolations().iterator().next().getMessage();
        RedirectErrorResponse body = new RedirectErrorResponse(RedirectErrorCode.BAD_REQUEST.getCode(), msg, RequestId.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 防止未预期异常的实现细节泄露给跳转客户端。
     */
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
