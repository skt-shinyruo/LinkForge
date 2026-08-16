package com.linkforge.shortlink.application.support;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.shortlink.domain.ShortLinkDomainException;

/**
 * 将短链领域失败原因翻译为应用层稳定错误码。
 *
 * <p>领域层不依赖 HTTP 或公共错误契约；应用层在此集中决定客户端可分支的错误码。错误消息沿用领域异常的
 * 可读信息，但客户端逻辑只能依赖错误码，不能解析 message。</p>
 */
public final class ShortLinkDomainExceptions {

    private ShortLinkDomainExceptions() {
    }

    /**
     * 把领域异常转换为可由统一异常处理器输出的业务异常。
     *
     * <p>{@code INVALID_URL} 保留为短链专属 {@link ShortLinkErrorCode#INVALID_URL}；其余当前领域校验和
     * 状态不变量统一映射为 {@link ErrorCode#BAD_REQUEST}。空异常仅作为防御性分支，也返回 BAD_REQUEST。
     * 本方法不会保留原异常为 cause，日志如需领域上下文应在翻译前记录。</p>
     *
     * @param ex 待翻译的领域异常；允许为 {@code null}
     * @return 新建的业务异常，永不返回 {@code null}
     */
    public static BusinessException translate(ShortLinkDomainException ex) {
        if (ex == null) {
            return new BusinessException(ErrorCode.BAD_REQUEST, "domain validation failed");
        }
        return switch (ex.reason()) {
            case INVALID_URL -> new BusinessException(ShortLinkErrorCode.INVALID_URL, ex.getMessage());
            case INVALID_CODE,
                 NOTE_TOO_LONG,
                 INVALID_REDIRECT_STATUS_CODE,
                 INVALID_QUERY_FORWARD_MODE,
                 INVALID_QUERY_FORWARD_ALLOWLIST_ITEM,
                 INVALID_QUERY_FORWARD_ALLOWLIST_TOO_LONG,
                 UPDATE_NOT_ALLOWED_WHEN_ARCHIVED,
                 DELETE_REQUIRES_ARCHIVE,
                 APPROVAL_REQUIRES_ACTIVE_SCOPED_LINK,
                 INVALID_TENANT_ID,
                 INVALID_LINK_ID -> new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        };
    }
}
