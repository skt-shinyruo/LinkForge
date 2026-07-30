package com.linkforge.accounts.domain;

/**
 * Accounts 持久化与跨层传递使用的稳定状态值。
 *
 * <p>认证链路只把 {@link #STATUS_ACTIVE} 视为可用；空值、未知值以及
 * {@link #STATUS_DISABLED} 都必须按不可用处理，避免新增或损坏状态意外放权。</p>
 */
public final class AccountsConstants {

    /** 允许通过账户状态校验的唯一状态。 */
    public static final String STATUS_ACTIVE = "active";
    /** 明确禁止登录及后续请求的状态。 */
    public static final String STATUS_DISABLED = "disabled";

    private AccountsConstants() {
    }
}
