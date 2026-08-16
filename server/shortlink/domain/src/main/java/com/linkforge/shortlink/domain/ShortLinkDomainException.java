package com.linkforge.shortlink.domain;

/**
 * 短链值校验或聚合不变量被破坏时抛出的领域异常。
 *
 * <p>领域模块不依赖共享 HTTP 错误码。应用层必须依据 {@link #reason()} 将本异常翻译为稳定的业务错误；
 * {@link #field()} 可选地指出出错字段，{@link #getMessage()} 只提供可读上下文，不应代替机器可判定的原因枚举。</p>
 */
public class ShortLinkDomainException extends RuntimeException {

    /** 可供应用层稳定映射的失败类别。 */
    public enum Reason {
        INVALID_TENANT_ID,
        INVALID_LINK_ID,
        INVALID_CODE,
        INVALID_URL,
        NOTE_TOO_LONG,
        INVALID_REDIRECT_STATUS_CODE,
        INVALID_QUERY_FORWARD_MODE,
        INVALID_QUERY_FORWARD_ALLOWLIST_ITEM,
        INVALID_QUERY_FORWARD_ALLOWLIST_TOO_LONG,
        UPDATE_NOT_ALLOWED_WHEN_ARCHIVED,
        DELETE_REQUIRES_ARCHIVE,
        APPROVAL_REQUIRES_ACTIVE_SCOPED_LINK,
        INVALID_OWNERSHIP_SCOPE
    }

    private final Reason reason;
    private final String field;

    /**
     * 创建带字段定位信息的领域异常。
     *
     * @param reason 稳定的失败类别，不应依赖 message 推断
     * @param field 相关输入字段；无法归属单一字段时为空
     * @param message 面向日志或上层翻译的可读说明
     */
    public ShortLinkDomainException(Reason reason, String field, String message) {
        super(message);
        this.reason = reason;
        this.field = field;
    }

    /**
     * 创建不绑定单一输入字段的领域异常。
     */
    public ShortLinkDomainException(Reason reason, String message) {
        this(reason, null, message);
    }

    public Reason reason() {
        return reason;
    }

    public String field() {
        return field;
    }
}
