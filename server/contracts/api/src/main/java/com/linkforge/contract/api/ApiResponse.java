package com.linkforge.contract.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * LinkForge JSON API 的统一响应信封。
 *
 * <p>{@code code=0} 表示成功；业务错误码不要求与 HTTP status 一一对应。{@link JsonInclude.Include#NON_NULL}
 * 只省略本信封中值为 {@code null} 的引用字段，空字符串、空集合和数值字段仍会序列化；该规则不递归改变
 * {@code data} 内对象的字段序列化。{@code requestId} 用于关联服务端日志，不能作为业务幂等键。</p>
 *
 * <p>{@link #ok(Object, String)} 和 {@link #error(int, String, String)} 建立常规成功/失败形状；为了 Jackson
 * 与历史调用方兼容保留的 setter 不校验信封不变量，可能构造 {@code code}、{@code message}、{@code data}
 * 不一致的状态。业务代码应使用工厂方法，且不得把内部异常文本写入 {@code message}。</p>
 *
 * @param <T> 成功响应的数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String requestId;

    /**
     * 创建供 Jackson 反序列化和历史调用方逐字段填充的空信封。
     *
     * <p>初始 {@code code} 为 {@code 0}，其余字段为 {@code null}，并不等同于 {@link #ok(Object, String)}
     * 所构造的规范成功响应。新业务代码应使用工厂方法。</p>
     */
    public ApiResponse() {
    }

    private ApiResponse(int code, String message, T data, String requestId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
    }

    /**
     * 创建规范成功响应。
     *
     * <p>返回对象固定为 {@code code=0} 和 {@code message="ok"}。本方法不复制、校验或序列化
     * {@code data}，也不写日志或设置 HTTP status；{@code data} 或 {@code requestId} 为 {@code null}
     * 时，对应信封字段会因 {@code NON_NULL} 从 JSON 中省略。</p>
     *
     * @param data 成功数据；允许为 {@code null}
     * @param requestId 请求关联 ID；允许为 {@code null}
     * @param <T> 成功数据类型
     * @return 新建的成功信封
     */
    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return new ApiResponse<>(0, "ok", data, requestId);
    }

    /**
     * 创建不含成功数据的错误响应。
     *
     * <p>调用方应传入已发布的非零业务码和可公开展示的消息；本方法为兼容现有调用方不做校验，传入
     * {@code 0} 或 {@code null} 仍会生成语义不完整的信封。它只构造响应体，不设置 HTTP status、
     * 不记录日志，也不创建异常。{@code data} 始终为 {@code null}；{@code message} 或 {@code requestId}
     * 为 {@code null} 时，相应信封字段会从默认 JSON 中省略。</p>
     *
     * @param code 已发布的非零业务码
     * @param message 面向调用方的安全错误消息；允许为 {@code null}
     * @param requestId 请求关联 ID；允许为 {@code null}
     * @param <T> 为保持调用方泛型兼容而保留的数据类型
     * @return 新建的错误信封，其 {@code data} 为 {@code null}
     */
    public static <T> ApiResponse<T> error(int code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, requestId);
    }

    /**
     * 返回业务码。
     *
     * @return 成功信封通常为 {@code 0}；setter 构造的兼容对象不保证该不变量
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置业务码，供 Jackson 或历史逐字段调用方使用。
     *
     * <p>不校验零值、已发布性或与其他字段的一致性；应用层不应借此构造对外响应。</p>
     *
     * @param code 要写入的业务码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 返回面向调用方的消息。
     *
     * @return 当前消息；可以为 {@code null}，不应包含堆栈或敏感信息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置面向调用方的消息，供 Jackson 或历史逐字段调用方使用。
     *
     * <p>不会脱敏、翻译或验证内容，且允许 {@code null}；调用方负责避免凭据、SQL 和内部异常信息泄漏。</p>
     *
     * @param message 要写入的消息；允许为 {@code null}
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 返回成功数据。
     *
     * @return 当前数据；错误信封通常为 {@code null}，兼容 setter 可以改变该约定
     */
    public T getData() {
        return data;
    }

    /**
     * 设置成功数据，供 Jackson 或历史逐字段调用方使用。
     *
     * <p>不复制或校验对象，也不据此调整 {@code code} 或 {@code message}。</p>
     *
     * @param data 要写入的数据；允许为 {@code null}
     */
    public void setData(T data) {
        this.data = data;
    }

    /**
     * 返回请求关联 ID。
     *
     * @return 当前关联 ID；可以为 {@code null}，不能作为幂等键或身份凭据
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 设置请求关联 ID，供 Jackson 或历史逐字段调用方使用。
     *
     * <p>不生成、校验或注册该值，且允许 {@code null}；请求处理基础设施负责其实际关联语义。</p>
     *
     * @param requestId 要写入的关联 ID；允许为 {@code null}
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
