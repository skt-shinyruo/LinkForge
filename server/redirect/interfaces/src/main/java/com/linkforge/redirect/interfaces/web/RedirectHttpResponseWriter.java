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
