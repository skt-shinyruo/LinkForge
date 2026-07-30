package com.linkforge.governance.domain;

/**
 * 审批聚合拒绝非法状态转换时抛出的领域异常。
 *
 * <p>异常只携带稳定的 {@link Reason}，由应用层统一翻译为对外业务错误，避免领域层依赖 HTTP 或接口文案。</p>
 */
public class ApprovalDomainException extends RuntimeException {

    private final Reason reason;

    public ApprovalDomainException(Reason reason) {
        super(reason == null ? null : reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** 审批状态机当前可能拒绝转换的原因。 */
    public enum Reason {
        APPROVAL_NOT_PENDING,
        SELF_APPROVAL,
        APPROVAL_NOT_APPROVED
    }
}
