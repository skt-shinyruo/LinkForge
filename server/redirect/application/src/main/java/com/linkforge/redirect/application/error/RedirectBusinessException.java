package com.linkforge.redirect.application.error;

/**
 * Redirect 对外可预期的业务失败。
 *
 * <p>接口层负责把其中的 {@link RedirectErrorCode} 转为 JSON 或 HTML 响应。不能把 Redis、数据库或
 * URL 解析等基础设施异常包装成该异常，否则会错误地向调用者承诺稳定业务语义。</p>
 */
public class RedirectBusinessException extends RuntimeException {

    private final RedirectErrorCode errorCode;

    /**
     * 使用错误码的默认安全消息创建异常。
     *
     * @param errorCode 要映射到 HTTP 的稳定错误码
     */
    public RedirectBusinessException(RedirectErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用调用者提供的公开消息创建异常；调用方不得传入内部异常细节。
     *
     * @param errorCode 要映射到 HTTP 的稳定错误码
     * @param message 可安全展示给调用者的消息
     */
    public RedirectBusinessException(RedirectErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 返回接口层状态映射所需的稳定错误码。
     *
     * @return 当前业务错误码
     */
    public RedirectErrorCode getErrorCode() {
        return errorCode;
    }
}
