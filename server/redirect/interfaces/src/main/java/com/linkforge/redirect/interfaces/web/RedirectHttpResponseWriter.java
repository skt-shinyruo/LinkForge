package com.linkforge.redirect.interfaces.web;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.redirect.application.RedirectResolution;
import com.linkforge.redirect.application.RedirectUrlBuilder;
import com.linkforge.redirect.application.error.RedirectBusinessException;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * 将 {@link RedirectResolution} 映射为实际 HTTP 响应。
 *
 * <p>应用层不决定 HTML 与 JSON 的协议形态，本类根据 resolution 和 Accept 偏好选择预览/错误页面或
 * 抛出业务异常。真正重定向时由 {@link RedirectUrlBuilder} 在写入 Location 前合并允许的 query 参数。</p>
 */
@Component
public class RedirectHttpResponseWriter {

    private final RedirectProperties redirectProperties;
    private final RedirectUrlBuilder redirectUrlBuilder;
    private final RedirectHtmlPageRenderer htmlPageRenderer;

    public RedirectHttpResponseWriter(
            RedirectProperties redirectProperties,
            RedirectUrlBuilder redirectUrlBuilder,
            RedirectHtmlPageRenderer htmlPageRenderer
    ) {
        this.redirectProperties = redirectProperties;
        this.redirectUrlBuilder = redirectUrlBuilder;
        this.htmlPageRenderer = htmlPageRenderer;
    }

    /**
     * 生成最终 HTTP 响应。
     *
     * <p>{@code null} resolution 是程序错误而不是未找到，必须以 500 暴露；{@code NOT_FOUND}、
     * {@code UNAVAILABLE} 的非 HTML 分支会抛业务异常，交由 Redirect 专属异常处理器序列化。</p>
     */
    public ResponseEntity<?> write(RedirectResolution resolution, HttpServletRequest request) {
        if (resolution == null) {
            throw new RedirectBusinessException(RedirectErrorCode.INTERNAL_ERROR);
        }
        return switch (resolution.kind()) {
            case PREVIEW -> htmlPageRenderer.renderPreview(resolution.meta(), request);
            case NOT_FOUND -> writeNotFound(resolution);
            case UNAVAILABLE -> writeUnavailable(resolution);
            case REDIRECT -> writeRedirect(resolution.meta(), request);
        };
    }

    private ResponseEntity<?> writeNotFound(RedirectResolution resolution) {
        if (resolution.htmlRequest()) {
            return htmlPageRenderer.renderNotFound(resolution.code());
        }
        throw new RedirectBusinessException(RedirectErrorCode.LINK_NOT_FOUND);
    }

    private ResponseEntity<?> writeUnavailable(RedirectResolution resolution) {
        if (resolution.htmlRequest()) {
            return htmlPageRenderer.renderUnavailable(
                    resolution.code(),
                    resolution.meta(),
                    resolution.unavailableReason()
            );
        }
        throw new RedirectBusinessException(resolution.unavailableReason().toErrorCode());
    }

    private ResponseEntity<?> writeRedirect(LinkMeta meta, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        // URL builder 在异常或超限时回退原始目标 URL，避免输出半合并地址。
        headers.setLocation(URI.create(redirectUrlBuilder.buildFinalRedirectUrl(
                meta,
                request == null ? null : request.getParameterMap()
        )));
        return new ResponseEntity<>(headers, resolveStatus(meta));
    }

    private HttpStatus resolveStatus(LinkMeta meta) {
        Integer value = meta == null ? null : meta.redirectStatusCode();
        if (value != null && value == 301) {
            return HttpStatus.MOVED_PERMANENTLY;
        }
        if (value != null && value == 302) {
            return HttpStatus.FOUND;
        }
        return redirectProperties.getDefaultStatusCode() == 301
                ? HttpStatus.MOVED_PERMANENTLY
                : HttpStatus.FOUND;
    }
}
