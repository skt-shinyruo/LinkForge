package com.linkforge.contract.api;

/**
 * 携带稳定 {@link AppErrorCode} 的预期业务异常。
 *
 * <p>HTTP 层负责将其转换为响应；{@code errorCode} 必须非空，异常消息必须适合直接展示，不能包含凭据或
 * 内部堆栈。本类型不会脱敏、翻译或截断调用方传入的 {@code message}，构造调用点承担该安全边界。</p>
 */
public class BusinessException extends RuntimeException {

    private final AppErrorCode errorCode;

    /**
     * 使用错误码的默认安全消息创建异常。
     *
     * <p>会立即读取 {@link AppErrorCode#getDefaultMessage()}，不保留 cause，也不产生日志、HTTP 响应或其他
     * 副作用。</p>
     *
     * @param errorCode 已发布的稳定错误码，不能为 {@code null}
     * @throws NullPointerException 当 {@code errorCode} 为 {@code null} 时
     */
    public BusinessException(AppErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用调用方提供的安全业务消息创建异常。
     *
     * <p>{@code message} 会原样交给 HTTP 映射层；本构造器不校验或清洗它。为保持二进制兼容，当前实现也
     * 不主动拒绝 {@code null} 的 {@code errorCode}，但这会构造无法可靠映射的异常，调用方不得传入。</p>
     *
     * @param errorCode 已发布的稳定错误码，不能为 {@code null}
     * @param message 面向调用方的安全消息；允许为 {@code null}，但 HTTP 响应可能因此省略消息字段
     */
    public BusinessException(AppErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 返回用于 HTTP 映射和客户端分支处理的稳定错误码。
     *
     * @return 构造时传入的错误码；合规调用下非 {@code null}
     */
    public AppErrorCode getErrorCode() {
        return errorCode;
    }
}
