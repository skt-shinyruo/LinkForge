package com.linkforge.redirect.interfaces.web;

import com.linkforge.foundation.web.VisitInfo;
import com.linkforge.redirect.application.RedirectVisitInput;
import com.linkforge.redirect.application.ResolveRedirectRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将 Servlet 请求收敛为 Redirect 应用层输入。
 *
 * <p>生产的 {@code /r/**} 请求应优先使用风控 filter 已写入的 {@link VisitInfo}，以确保客户端 IP 和
 * tracking 参数经过统一的可信代理、allowlist 与限长规则。属性缺失时的回退只服务于非标准内部调用，
 * 不会自行信任 forwarded headers。</p>
 */
@Component
public class RedirectHttpRequestMapper {

    /**
     * 提取 host、HTML 接受能力、确认标记和访问上下文。
     *
     * <p>host 统一为小写并去除端口，短码本身保持原样，由应用层统一校验。任何存在的
     * {@code __lf_confirm} 参数都视为确认，且该内部参数后续不会被 URL builder 透传。</p>
     */
    public ResolveRedirectRequest fromHttp(String code, HttpServletRequest request) {
        VisitInfo visitInfo = resolveVisitInfo(request);
        return new ResolveRedirectRequest(
                code,
                resolveRequestHost(request),
                isHtmlRequest(request),
                hasConfirmParam(request),
                toRedirectVisitInput(visitInfo)
        );
    }

    private static VisitInfo resolveVisitInfo(HttpServletRequest request) {
        VisitInfo visitInfo = request == null ? null : (VisitInfo) request.getAttribute(RedirectRiskControlFilter.ATTR_VISIT_INFO);
        if (visitInfo == null && request != null) {
            return new VisitInfo(
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    request.getHeader("Referer"),
                    request.getHeader("Accept-Language"),
                    Map.of()
            );
        }
        return visitInfo;
    }

    private static boolean isHtmlRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return false;
        }
        String normalized = accept.toLowerCase();
        return normalized.contains("text/html") || normalized.contains("application/xhtml+xml");
    }

    private static boolean hasConfirmParam(HttpServletRequest request) {
        return request != null && request.getParameter("__lf_confirm") != null;
    }

    private static String resolveRequestHost(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String host = request.getServerName();
        if (host == null || host.isBlank()) {
            host = request.getHeader(HttpHeaders.HOST);
        }
        if (host == null) {
            return null;
        }
        String normalized = host.trim().toLowerCase();
        int colonIndex = normalized.indexOf(':');
        if (colonIndex > 0) {
            normalized = normalized.substring(0, colonIndex);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static RedirectVisitInput toRedirectVisitInput(VisitInfo visitInfo) {
        if (visitInfo == null) {
            return null;
        }
        return new RedirectVisitInput(
                visitInfo.ip(),
                visitInfo.userAgent(),
                visitInfo.referer(),
                visitInfo.acceptLanguage(),
                visitInfo.trackingParams()
        );
    }
}
