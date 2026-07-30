package com.linkforge.app.api.error;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.AppErrorCode;
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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 管理 API 的统一异常到 {@link ApiResponse} 映射。
 *
 * <p>业务异常保持其公开错误码和 HTTP 状态；Bean Validation、缺参数和 Spring 拒绝访问映射为稳定的 400/403。
 * 未预期异常只记录服务端日志并返回通用 500，避免泄漏堆栈、SQL 或凭据信息。该 advice 不覆盖 Redirect 的
 * HTML/重定向响应链路。</p>
 */
@RestControllerAdvice(basePackages = {
        "com.linkforge.accounts",
        "com.linkforge.shortlink",
        "com.linkforge.analytics",
        "com.linkforge.platform",
        "com.linkforge.governance"
})
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 保留业务层已定义的公开错误码与消息。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        AppErrorCode ec = ex.getErrorCode();
        ApiResponse<Void> body = ApiResponse.error(ec.getCode(), ex.getMessage(), RequestId.get());
        HttpStatus status = mapToHttpStatus(ec);
        return ResponseEntity.status(status).body(body);
    }

    /** 返回首个 Bean Validation 错误，避免暴露内部绑定对象。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getAllErrors().isEmpty()
                ? ErrorCode.BAD_REQUEST.getDefaultMessage()
                : ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), msg, RequestId.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** 映射方法参数约束异常为 400。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().isEmpty()
                ? ErrorCode.BAD_REQUEST.getDefaultMessage()
                : ex.getConstraintViolations().iterator().next().getMessage();
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), msg, RequestId.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** 映射缺失请求参数为 400，并仅回显参数名。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        String parameterName = ex == null ? null : ex.getParameterName();
        String msg = parameterName == null || parameterName.isBlank()
                ? ErrorCode.BAD_REQUEST.getDefaultMessage()
                : "缺少必填参数: " + parameterName;
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), msg, RequestId.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** 记录完整异常但向客户端隐藏内部原因。 */
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

    /** 将 Spring 方法安全拒绝转换为稳定的 403 响应。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.FORBIDDEN.getCode(),
                ErrorCode.FORBIDDEN.getDefaultMessage(),
                RequestId.get()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    private static HttpStatus mapToHttpStatus(AppErrorCode ec) {
        if (ec == null) {
            return HttpStatus.BAD_REQUEST;
        }
        int status = ec.getHttpStatus();
        try {
            return HttpStatus.valueOf(status);
        } catch (Exception e) {
            return HttpStatus.BAD_REQUEST;
        }
    }
}
