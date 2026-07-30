package com.linkforge.redirect.application;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.redirect.application.error.RedirectErrorCode;

/**
 * Redirect 应用层向 HTTP 层交付的不可变决策结果。
 *
 * <p>它刻意不携带最终 {@code Location}：query 转发和 HTTP 状态码是接口层职责。{@code meta} 只会在
 * {@code REDIRECT}、{@code PREVIEW} 和 {@code UNAVAILABLE} 中出现；{@code unavailableReason} 只会在
 * {@code UNAVAILABLE} 中出现。</p>
 */
public record RedirectResolution(
        Kind kind,
        String code,
        boolean htmlRequest,
        LinkMeta meta,
        UnavailableReason unavailableReason
) {

    /**
     * HTTP 层需要渲染的四类结果，而非缓存查询结果。
     */
    public enum Kind {
        /** 全部检查通过，接口层应写出 301/302 Location。 */
        REDIRECT,
        /** HTML 预览尚未确认，不能记录访问或预留额度。 */
        PREVIEW,
        /** 短码非法、负缓存命中或权威读为空。 */
        NOT_FOUND,
        /** 已解析短链但因生命周期、过期或额度不能跳转。 */
        UNAVAILABLE
    }

    /**
     * 已读取到短链但不能跳转的业务原因，决定非 HTML 请求的稳定错误码。
     */
    public enum UnavailableReason {
        /** 短链关闭或生命周期不是 ACTIVE。 */
        DISABLED(RedirectErrorCode.LINK_DISABLED),
        /** expiresAt 已到达或早于当前 UTC 时刻。 */
        EXPIRED(RedirectErrorCode.LINK_EXPIRED),
        /** 原子月度额度预留被拒绝或 fail-closed 故障。 */
        QUOTA_EXCEEDED(RedirectErrorCode.TOO_MANY_REQUESTS);

        private final RedirectErrorCode errorCode;

        UnavailableReason(RedirectErrorCode errorCode) {
            this.errorCode = errorCode;
        }

        /**
         * 映射为接口层可公开的错误码。
         *
         * @return 对应的稳定 Redirect 错误码
         */
        public RedirectErrorCode toErrorCode() {
            return errorCode;
        }
    }

    /**
     * 创建已通过全部检查、可以写 Location 的结果。
     *
     * @param code 已规范化的短码
     * @param htmlRequest 是否接受 HTML
     * @param meta 已验证的短链元数据
     * @return redirect 结果
     */
    public static RedirectResolution redirect(String code, boolean htmlRequest, LinkMeta meta) {
        return new RedirectResolution(Kind.REDIRECT, code, htmlRequest, meta, null);
    }

    /**
     * 创建 HTML 预览结果；调用方尚未确认，不能记录访问或预留额度。
     *
     * @param code 已规范化的短码
     * @param htmlRequest 是否接受 HTML
     * @param meta 已验证且开启预览的短链元数据
     * @return preview 结果
     */
    public static RedirectResolution preview(String code, boolean htmlRequest, LinkMeta meta) {
        return new RedirectResolution(Kind.PREVIEW, code, htmlRequest, meta, null);
    }

    /**
     * 创建未找到结果，不泄露缓存、短码格式或权威读的具体失败原因。
     *
     * @param code 原始或规范化短码，可为 {@code null}
     * @param htmlRequest 是否接受 HTML
     * @return not-found 结果
     */
    public static RedirectResolution notFound(String code, boolean htmlRequest) {
        return new RedirectResolution(Kind.NOT_FOUND, code, htmlRequest, null, null);
    }

    /**
     * 创建已解析短链但不可跳转的结果。
     *
     * @param code 已规范化的短码
     * @param htmlRequest 是否接受 HTML
     * @param meta 已解析的短链元数据
     * @param unavailableReason 禁用、过期或额度拒绝原因
     * @return unavailable 结果
     */
    public static RedirectResolution unavailable(
            String code,
            boolean htmlRequest,
            LinkMeta meta,
            UnavailableReason unavailableReason
    ) {
        return new RedirectResolution(Kind.UNAVAILABLE, code, htmlRequest, meta, unavailableReason);
    }
}
