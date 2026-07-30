package com.linkforge.contract.openapi;

import com.linkforge.contract.api.AppErrorCode;

/**
 * OpenAPI API Key 认证对外暴露的稳定错误码。
 *
 * <p>数值和默认消息是客户端协议的一部分，不能改义、复用或删除。本枚举的 HTTP status 由
 * {@link #getCode()} 的数值布局计算，修改已发布数值会同时破坏客户端分支和认证响应。</p>
 */
public enum OpenApiErrorCode implements AppErrorCode {

    /**
     * 缺失、格式错误、未知、secret 不匹配、未绑定应用或未预期认证失败均统一返回 401。
     *
     * <p>该合并避免向调用方泄漏 API Key 是否存在、格式通过与否或应用绑定状态。</p>
     */
    API_KEY_INVALID(40110, "API Key 无效"),
    /** 已识别且处于禁用状态的 API Key 返回 403。 */
    API_KEY_DISABLED(40310, "API Key 已禁用");

    private final int code;
    private final String defaultMessage;

    OpenApiErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 返回 JSON 错误体中对外稳定的数值码。
     *
     * @return 与当前 OpenAPI 错误常量绑定的数值码
     */
    @Override
    public int getCode() {
        return code;
    }

    /**
     * 返回可安全展示给 OpenAPI 调用方的默认中文消息。
     *
     * @return 当前 OpenAPI 错误常量的默认消息
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 按 {@code code / 100} 返回 HTTP status。
     *
     * <p>该计算依赖 {@code 401xx}/{@code 403xx} 的数值布局；修改 code 的百位及以上部分会改变认证响应，
     * 属于破坏性协议变更。</p>
     *
     * @return 当前 OpenAPI 错误常量对应的 HTTP status
     */
    @Override
    public int getHttpStatus() {
        return code / 100;
    }
}
