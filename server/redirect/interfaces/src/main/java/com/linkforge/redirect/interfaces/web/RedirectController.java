package com.linkforge.redirect.interfaces.web;

import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.redirect.application.error.RedirectBusinessException;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.foundation.web.VisitInfo;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.redirect.application.RedirectUrlBuilder;
import com.linkforge.redirect.application.RedirectService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/r")
public class RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    private final RedirectService redirectService;
    private final RedirectProperties redirectProperties;
    private final RedirectUrlBuilder redirectUrlBuilder;
    private final RedirectAvailabilityPolicy availabilityPolicy;
    private final RedirectHtmlPageRenderer htmlPageRenderer;

    public RedirectController(
            RedirectService redirectService,
            RedirectProperties redirectProperties,
            RedirectUrlBuilder redirectUrlBuilder,
            RedirectAvailabilityPolicy availabilityPolicy,
            RedirectHtmlPageRenderer htmlPageRenderer
    ) {
        this.redirectService = redirectService;
        this.redirectProperties = redirectProperties;
        this.redirectUrlBuilder = redirectUrlBuilder;
        this.availabilityPolicy = availabilityPolicy;
        this.htmlPageRenderer = htmlPageRenderer;
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> redirect(@PathVariable("code") String code, HttpServletRequest request) {
        long startNs = System.nanoTime();
        VisitInfo visitInfo = request == null ? null : (VisitInfo) request.getAttribute(RedirectRiskControlFilter.ATTR_VISIT_INFO);
        if (visitInfo == null && request != null) {
            // 兜底：即使 Filter 未生效，也至少保证有 IP/UA 进入统计与日志
            visitInfo = new VisitInfo(
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    request.getHeader("Referer"),
                    request.getHeader("Accept-Language"),
                    Map.of()
            );
        }
        try {
            boolean html = isHtmlRequest(request);
            boolean confirmed = hasConfirmParam(request);

            LinkMeta meta = redirectService.resolve(code);

            RedirectAvailabilityPolicy.UnavailableReason unavailable = availabilityPolicy.unavailableReason(meta);
            if (unavailable != null) {
                if (html) {
                    ResponseEntity<?> resp = htmlPageRenderer.renderUnavailable(code, meta, unavailable);
                    logUnavailable(startNs, code, unavailable, visitInfo);
                    return resp;
                }
                // 非浏览器调用方保持 JSON 错误结构（由 RedirectGlobalExceptionHandler 处理）
                throw new RedirectBusinessException(unavailable.toErrorCode());
            }

            // 预览页：仅浏览器请求展示，且未确认时不写统计
            if (html && meta.previewEnabled() && !confirmed) {
                ResponseEntity<String> resp = htmlPageRenderer.renderPreview(meta, request);
                logPreview(startNs, code, meta, visitInfo);
                return resp;
            }

            // 确认跳转后再计数
            redirectService.recordVisitIfAvailable(meta, visitInfo);

            int statusCode = resolveStatusCode(meta);
            HttpStatus status = statusCode == 301 ? HttpStatus.MOVED_PERMANENTLY : HttpStatus.FOUND;

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectUrlBuilder.buildFinalRedirectUrl(meta, request == null ? null : request.getParameterMap())));

            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.debug(
                    "redirect ok: code={}, tenantId={}, linkId={}, status={}, latencyMs={}, requestId={}, ip={}",
                    code,
                    meta.tenantId(),
                    meta.id(),
                    status.value(),
                    latencyMs,
                    RequestId.get(),
                    visitInfo == null ? null : visitInfo.ip()
            );
            return new ResponseEntity<>(headers, status);
        } catch (RedirectBusinessException e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            if (isHtmlRequest(request) && e.getErrorCode() == RedirectErrorCode.LINK_NOT_FOUND) {
                ResponseEntity<?> resp = htmlPageRenderer.renderNotFound(code);
                log.info(
                        "redirect not found (html): code={}, latencyMs={}, requestId={}, ip={}",
                        code,
                        latencyMs,
                        RequestId.get(),
                        visitInfo == null ? null : visitInfo.ip()
                );
                return resp;
            }
            log.info(
                    "redirect rejected: code={}, errCode={}, latencyMs={}, requestId={}, ip={}",
                    code,
                    e.getErrorCode() == null ? null : e.getErrorCode().name(),
                    latencyMs,
                    RequestId.get(),
                    visitInfo == null ? null : visitInfo.ip()
            );
            throw e;
        }
    }

    private static boolean isHtmlRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return false;
        }
        String a = accept.toLowerCase();
        return a.contains("text/html") || a.contains("application/xhtml+xml");
    }

    private static boolean hasConfirmParam(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        return request.getParameter("__lf_confirm") != null;
    }

    private int resolveStatusCode(LinkMeta meta) {
        Integer v = meta == null ? null : meta.redirectStatusCode();
        if (v != null && (v == 301 || v == 302)) {
            return v;
        }
        int global = redirectProperties.getDefaultStatusCode();
        return global == 301 ? 301 : 302;
    }

    private void logUnavailable(long startNs, String code, RedirectAvailabilityPolicy.UnavailableReason reason, VisitInfo visitInfo) {
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info(
                "redirect unavailable (html): code={}, reason={}, latencyMs={}, requestId={}, ip={}",
                code,
                reason == null ? null : reason.name(),
                latencyMs,
                RequestId.get(),
                visitInfo == null ? null : visitInfo.ip()
        );
    }

    private void logPreview(long startNs, String code, LinkMeta meta, VisitInfo visitInfo) {
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info(
                "redirect preview: code={}, tenantId={}, linkId={}, latencyMs={}, requestId={}, ip={}",
                code,
                meta == null ? null : meta.tenantId(),
                meta == null ? null : meta.id(),
                latencyMs,
                RequestId.get(),
                visitInfo == null ? null : visitInfo.ip()
        );
    }
}
