package com.linkforge.platform.domain;

/**
 * 域名使用授权被拒绝时抛出的领域异常。
 *
 * <p>异常携带结构化 {@link Reason}，应用层应按原因映射稳定的错误码和对外文案，不应依赖
 * {@link #getMessage()} 做分支判断。</p>
 */
public class DomainAuthorizationException extends RuntimeException {

    private final Reason reason;

    public DomainAuthorizationException(Reason reason) {
        super(reason == null ? null : reason.name());
        this.reason = reason;
    }

    /**
     * 返回授权失败的结构化原因。
     *
     * @return 创建异常时传入的原因；当前策略始终传入非空值
     */
    public Reason reason() {
        return reason;
    }

    /** 授权策略可区分的拒绝原因。 */
    public enum Reason {
        DOMAIN_NOT_ACTIVE,
        DEDICATED_DOMAIN_MISMATCH,
        SHARED_DOMAIN_NOT_AUTHORIZED
    }
}
