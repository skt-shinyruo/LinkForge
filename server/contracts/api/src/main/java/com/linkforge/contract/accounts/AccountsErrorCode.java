package com.linkforge.contract.accounts;

import com.linkforge.contract.api.AppErrorCode;

/**
 * Accounts 上下文对外稳定的认证与账号状态错误码。
 *
 * <p>数值和默认消息是客户端协议的一部分，不能改义、复用或删除。HTTP status 由每个常量显式保存，
 * 不得从业务码数值推导。</p>
 */
public enum AccountsErrorCode implements AppErrorCode {

    /** 创建初始用户或租户内用户时邮箱已被占用；预检查和数据库唯一约束冲突均返回 400。 */
    EMAIL_ALREADY_EXISTS(40101, 400, "邮箱已存在"),
    /** 登录用户不存在或密码不匹配；两种情况合并为 401，避免暴露账号是否存在。 */
    INVALID_CREDENTIALS(40102, 401, "账号或密码错误"),
    /** 已识别租户不是 active 状态；登录或后续认证请求返回 403。 */
    TENANT_DISABLED(40301, 403, "租户已禁用"),
    /** 已识别用户不是 active 状态；登录或后续 JWT 状态校验返回 403。 */
    USER_DISABLED(40302, 403, "用户已禁用");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    AccountsErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 返回 JSON 错误体中对外稳定的数值码。
     *
     * @return 与当前账户错误常量绑定的数值码
     */
    @Override
    public int getCode() {
        return code;
    }

    /**
     * 返回可安全展示给调用方的默认中文消息。
     *
     * @return 当前账户错误常量的默认消息
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 返回该账户错误显式指定的 HTTP status。
     *
     * <p>{@link #EMAIL_ALREADY_EXISTS} 的业务码以 401 开头但仍固定为 400，因此调用方不得根据数值前缀
     * 推导 status。</p>
     *
     * @return 当前账户错误常量的 HTTP status
     */
    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
