package com.linkforge.contract.api;

/**
 * 跨上下文通用的稳定错误码。
 *
 * <p>领域特有错误应由对应上下文枚举表达。各数值采用 {@code HTTP status * 100} 布局，
 * {@link #getHttpStatus()} 因而通过整除计算状态码；改变已发布数值会同时破坏客户端分支和 HTTP
 * 映射，不能作为常规重构处理。</p>
 */
public enum ErrorCode implements AppErrorCode {

    /** 请求参数、格式或领域前置条件不满足。 */
    BAD_REQUEST(40000, "请求参数错误"),
    /** 未建立认证状态或提供的凭据无效。 */
    UNAUTHORIZED(40100, "未登录或凭证无效"),
    /** 已认证主体缺少访问目标资源所需的权限。 */
    FORBIDDEN(40300, "无权限"),
    /** 资源不存在，或为避免枚举而按不存在处理。 */
    NOT_FOUND(40400, "资源不存在"),
    /** 请求频率或服务施加的配额限制已被拒绝。 */
    TOO_MANY_REQUESTS(42900, "请求过于频繁"),
    /** 依赖或服务暂时不可用，调用方可按自身策略稍后重试。 */
    SERVICE_UNAVAILABLE(50300, "服务不可用"),
    /** 未预期服务端失败；默认消息不会暴露内部原因。 */
    INTERNAL_ERROR(50000, "服务内部错误");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 返回 JSON 错误体中对外稳定的数值码。
     *
     * @return 与当前枚举常量绑定的数值码
     */
    public int getCode() {
        return code;
    }

    /**
     * 返回可直接展示给调用方的默认中文消息。
     *
     * @return 当前常量的稳定默认消息
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 按 {@code code / 100} 返回 HTTP status。
     *
     * <p>该计算依赖本枚举的数值布局；修改 code 的百位及以上部分会改变 HTTP 响应，属于破坏性协议变更。</p>
     *
     * @return 当前数值码对应的 HTTP status
     */
    @Override
    public int getHttpStatus() {
        return code / 100;
    }
}
