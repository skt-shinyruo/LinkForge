package com.linkforge.redirect.application.error;

/**
 * Redirect 边缘接口使用的稳定业务错误码。
 *
 * <p>数值是客户端可依赖的协议字段；新增错误码应保持既有数值和默认消息的兼容性。HTTP 状态码不在此处
 * 固化，由 {@code RedirectGlobalExceptionHandler} 根据语义映射。</p>
 */
public enum RedirectErrorCode {

    /** 请求参数不满足 Redirect 接口约束。 */
    BAD_REQUEST(40000, "请求参数错误"),
    /** 请求被访问策略拒绝。 */
    FORBIDDEN(40300, "无权限"),
    /** 通用资源未找到。 */
    NOT_FOUND(40400, "资源不存在"),
    /** 请求频率或应用额度达到上限。 */
    TOO_MANY_REQUESTS(42900, "请求过于频繁"),
    /** 未预期的内部错误。 */
    INTERNAL_ERROR(50000, "服务内部错误"),

    /** 短链已禁用或不处于 ACTIVE 生命周期。 */
    LINK_DISABLED(41001, "短链已禁用"),
    /** 短链已到达 expiresAt。 */
    LINK_EXPIRED(41002, "短链已过期"),
    /** 短码不存在、不可见或输入不合法。 */
    LINK_NOT_FOUND(40410, "短链不存在");

    private final int code;
    private final String defaultMessage;

    RedirectErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 返回 JSON 错误体中的数值代码。
     *
     * @return 对外稳定数值代码
     */
    public int getCode() {
        return code;
    }

    /**
     * 返回可以安全展示给客户端的默认中文消息。
     *
     * @return 默认中文消息
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
