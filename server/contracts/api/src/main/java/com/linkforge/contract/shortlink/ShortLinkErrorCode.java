package com.linkforge.contract.shortlink;

import com.linkforge.contract.api.AppErrorCode;

/**
 * 短链管理接口使用的稳定业务错误码。
 *
 * <p>数值和默认消息是客户端协议的一部分，不能改义、复用或删除。{@link #LINK_DISABLED} 和
 * {@link #LINK_EXPIRED} 为保留的历史管理契约；当前 Redirect 边缘接口使用自身的错误码类型，
 * 调用方不能据此假定跳转链路会抛出本枚举。</p>
 */
public enum ShortLinkErrorCode implements AppErrorCode {

    /** 自定义短码与已有短链冲突；客户端应更换短码后重新提交。 */
    CODE_ALREADY_EXISTS(40901, "短码已存在"),
    /** 乐观锁或 CAS 写入冲突；客户端应重新读取当前状态后决定是否重试，不能盲目重放原命令。 */
    LINK_STALE_WRITE(40902, "短链已被其他请求修改，请刷新后重试"),
    /** 保留的历史管理错误，表示短链被禁用或当前不可用。 */
    LINK_DISABLED(41001, "短链已禁用"),
    /** 保留的历史管理错误，表示短链已到达有效期。 */
    LINK_EXPIRED(41002, "短链已过期"),
    /** 短链不存在，或因权限/租户边界而按不存在处理。 */
    LINK_NOT_FOUND(40410, "短链不存在"),
    /** URL 未通过短链领域的 HTTP(S) 与结构校验。 */
    INVALID_URL(40010, "URL 不合法");

    private final int code;
    private final String defaultMessage;

    ShortLinkErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 返回 JSON 错误体中对外稳定的数值码。
     *
     * @return 与当前短链错误常量绑定的数值码
     */
    @Override
    public int getCode() {
        return code;
    }

    /**
     * 返回可安全展示给调用方的默认中文消息。
     *
     * @return 当前短链错误常量的默认消息
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 按 {@code code / 100} 返回 HTTP status。
     *
     * <p>该计算依赖本枚举的数值布局；修改 code 的百位及以上部分会改变 HTTP 响应，属于破坏性协议变更。</p>
     *
     * @return 当前短链错误常量对应的 HTTP status
     */
    @Override
    public int getHttpStatus() {
        return code / 100;
    }
}
