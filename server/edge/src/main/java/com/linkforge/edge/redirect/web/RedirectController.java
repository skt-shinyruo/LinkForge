package com.linkforge.edge.redirect.web;

import com.linkforge.platform.config.AppProperties;
import com.linkforge.platform.api.BusinessException;
import com.linkforge.platform.api.ErrorCode;
import com.linkforge.platform.web.RequestId;
import com.linkforge.platform.web.VisitInfo;
import com.linkforge.edge.web.EdgeRiskControlFilter;
import com.linkforge.redirect.service.LinkMeta;
import com.linkforge.edge.redirect.service.RedirectService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/r")
public class RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    private final RedirectService redirectService;
    private final AppProperties appProperties;

    public RedirectController(RedirectService redirectService, AppProperties appProperties) {
        this.redirectService = redirectService;
        this.appProperties = appProperties;
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> redirect(@PathVariable("code") String code, HttpServletRequest request) {
        long startNs = System.nanoTime();
        VisitInfo visitInfo = request == null ? null : (VisitInfo) request.getAttribute(EdgeRiskControlFilter.ATTR_VISIT_INFO);
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

            UnavailableReason unavailable = unavailableReason(meta);
            if (unavailable != null) {
                if (html) {
                    ResponseEntity<?> resp = handleUnavailableHtml(code, meta, unavailable);
                    logUnavailable(startNs, code, unavailable, visitInfo);
                    return resp;
                }
                // 非浏览器调用方保持 JSON 错误结构（由 GlobalExceptionHandler 处理）
                throw new BusinessException(unavailable.toErrorCode());
            }

            // 预览页：仅浏览器请求展示，且未确认时不写统计
            if (html && meta.previewEnabled() && !confirmed) {
                ResponseEntity<String> resp = ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(renderPreviewHtml(meta, request));
                logPreview(startNs, code, meta, visitInfo);
                return resp;
            }

            // 确认跳转后再计数
            redirectService.recordVisitIfAvailable(meta, visitInfo);

            int statusCode = resolveStatusCode(meta);
            HttpStatus status = statusCode == 301 ? HttpStatus.MOVED_PERMANENTLY : HttpStatus.FOUND;

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(buildFinalRedirectUrl(meta, request)));

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
        } catch (BusinessException e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            if (isHtmlRequest(request) && e.getErrorCode() == ErrorCode.LINK_NOT_FOUND) {
                // 浏览器体验：短码不存在时返回 404 HTML 或跳转至全局落地页
                ResponseEntity<?> resp = handleNotFoundHtml(code);
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
        int global = appProperties.getRedirect().getDefaultStatusCode();
        return global == 301 ? 301 : 302;
    }

    private static UnavailableReason unavailableReason(LinkMeta meta) {
        if (meta == null) {
            return UnavailableReason.NOT_FOUND;
        }
        if (!meta.enabled()) {
            return UnavailableReason.DISABLED;
        }
        if (meta.expiresAt() != null && meta.expiresAt().isBefore(LocalDateTime.now())) {
            return UnavailableReason.EXPIRED;
        }
        return null;
    }

    private ResponseEntity<?> handleNotFoundHtml(String code) {
        String landing = trimToNull(appProperties.getRedirect().getNotFoundLandingUrl());
        if (isHttpUrl(landing)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(landing))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(renderUnavailableHtml("短链不存在", "你访问的短链不存在或已被删除。", code));
    }

    private ResponseEntity<?> handleUnavailableHtml(String code, LinkMeta meta, UnavailableReason reason) {
        if (reason == UnavailableReason.NOT_FOUND) {
            return handleNotFoundHtml(code);
        }

        // gone（禁用/过期）：优先按链接配置落地页，其次全局落地页，否则内置 410 HTML
        String perLink = meta == null ? null : trimToNull(meta.unavailableLandingUrl());
        if (isHttpUrl(perLink)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(perLink))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }
        String landing = trimToNull(appProperties.getRedirect().getGoneLandingUrl());
        if (isHttpUrl(landing)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(landing))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }

        String title = reason == UnavailableReason.DISABLED ? "短链已禁用" : "短链已过期";
        String msg = reason == UnavailableReason.DISABLED
                ? "该短链已被禁用，请联系链接发布方获取最新链接。"
                : "该短链已过期，请联系链接发布方获取最新链接。";
        return ResponseEntity.status(HttpStatus.GONE)
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(renderUnavailableHtml(title, msg, code));
    }

    private String buildFinalRedirectUrl(LinkMeta meta, HttpServletRequest request) {
        if (meta == null) {
            return null;
        }
        String originalUrl = meta.originalUrl();
        if (request == null) {
            return originalUrl;
        }

        String mode = resolveQueryForwardMode(meta);
        if ("OFF".equals(mode)) {
            return originalUrl;
        }

        Map<String, String[]> params = request.getParameterMap();
        if (params == null || params.isEmpty()) {
            return originalUrl;
        }

        List<String> allowlist = resolveQueryForwardAllowlist(meta);
        if ("ALLOWLIST".equals(mode) && allowlist.isEmpty()) {
            return originalUrl;
        }

        List<String> reserved = resolveReservedParams();

        UriComponentsBuilder b;
        try {
            b = UriComponentsBuilder.fromUriString(originalUrl);
        } catch (Exception e) {
            return originalUrl;
        }

        Set<String> existingKeys = new HashSet<>();
        try {
            existingKeys.addAll(b.build().getQueryParams().keySet());
        } catch (Exception ignored) {
            // 解析失败时保持“尽量不破坏”，继续尝试追加（可能无现有 key 保护）
        }

        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (matchesAny(name, reserved)) {
                continue;
            }
            if ("ALLOWLIST".equals(mode) && !matchesAny(name, allowlist)) {
                continue;
            }
            // 冲突策略：目标 URL 优先（已有同名参数时不覆盖）
            if (existingKeys.contains(name)) {
                continue;
            }
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                b.queryParam(name);
                continue;
            }
            List<String> safeValues = new ArrayList<>();
            for (String v : values) {
                if (v != null) {
                    safeValues.add(v);
                }
            }
            if (safeValues.isEmpty()) {
                b.queryParam(name);
            } else {
                b.queryParam(name, safeValues.toArray());
            }
        }

        try {
            return b.build().toUriString();
        } catch (Exception e) {
            return originalUrl;
        }
    }

    private String resolveQueryForwardMode(LinkMeta meta) {
        String raw = trimToNull(meta == null ? null : meta.queryForwardMode());
        if (raw == null) {
            raw = trimToNull(appProperties.getRedirect().getQueryForwardMode());
        }
        if (raw == null) {
            return "OFF";
        }
        String t = raw.trim().toUpperCase();
        return ("OFF".equals(t) || "ALLOWLIST".equals(t) || "ALL".equals(t)) ? t : "OFF";
    }

    private List<String> resolveQueryForwardAllowlist(LinkMeta meta) {
        // allowlist：全局 + per-link 组合（去重）
        Set<String> set = new HashSet<>();

        var global = appProperties.getRedirect().getQueryForwardAllowlist();
        if (global != null) {
            for (String p : global) {
                String v = trimToNull(p);
                if (v != null) {
                    set.add(v);
                }
            }
        }

        String perLinkRaw = trimToNull(meta == null ? null : meta.queryForwardAllowlist());
        if (perLinkRaw != null) {
            String[] parts = perLinkRaw.split(",");
            for (String p : parts) {
                String v = trimToNull(p);
                if (v != null) {
                    set.add(v);
                }
            }
        }

        return set.isEmpty() ? List.of() : new ArrayList<>(set);
    }

    private List<String> resolveReservedParams() {
        var raw = appProperties.getRedirect().getQueryForwardReservedParams();
        Set<String> set = new HashSet<>();
        if (raw != null) {
            for (String p : raw) {
                String v = trimToNull(p);
                if (v != null) {
                    set.add(v);
                }
            }
        }
        // 安全兜底：内部参数默认不透传
        set.add("__lf_confirm");
        set.add("__lf_preview");
        return new ArrayList<>(set);
    }

    private String renderPreviewHtml(LinkMeta meta, HttpServletRequest request) {
        String host = null;
        try {
            host = URI.create(meta.originalUrl()).getHost();
        } catch (Exception ignored) {
        }

        String confirmHref = buildConfirmHref(request);
        String title = "即将跳转";
        String msg = host == null ? "即将跳转到目标站点，是否继续？" : ("即将跳转到站点：" + host);

        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <meta name="robots" content="noindex,nofollow" />
                  <title>%s</title>
                  <style>
                    body { font-family: -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Arial,"Noto Sans",sans-serif; margin:0; background:#fafafa; color:#111; }
                    .wrap { max-width: 720px; margin: 48px auto; padding: 0 16px; }
                    .card { background:#fff; border:1px solid #eee; border-radius: 12px; padding: 20px; }
                    .sub { color:#666; margin-top: 8px; }
                    a.btn { display:inline-block; background:#111; color:#fff; padding: 10px 14px; border-radius: 10px; text-decoration:none; }
                    a.btn.secondary { background:#444; }
                    .row { display:flex; gap: 10px; align-items:center; margin-top: 16px; flex-wrap: wrap; }
                    .mono { font-family: ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,"Liberation Mono","Courier New",monospace; font-size: 12px; }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <div class="card">
                      <h1>%s</h1>
                      <div class="sub">%s</div>
                      <div class="row">
                        <a class="btn" href="%s" rel="noreferrer">继续访问</a>
                        <a class="btn secondary" href="javascript:history.back()">返回</a>
                      </div>
                      <div class="sub mono" style="margin-top:14px;">requestId: %s</div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(title),
                escapeHtml(title),
                escapeHtml(msg),
                escapeHtml(confirmHref),
                escapeHtml(RequestId.get())
        );
    }

    private static String buildConfirmHref(HttpServletRequest request) {
        String path = request == null ? null : request.getRequestURI();
        if (path == null || path.isBlank()) {
            path = "/";
        }

        UriComponentsBuilder b = UriComponentsBuilder.fromPath(path);
        if (request != null) {
            Map<String, String[]> params = request.getParameterMap();
            if (params != null) {
                for (Map.Entry<String, String[]> entry : params.entrySet()) {
                    String name = entry.getKey();
                    if (name == null || name.isBlank() || "__lf_confirm".equals(name)) {
                        continue;
                    }
                    String[] values = entry.getValue();
                    if (values == null || values.length == 0) {
                        b.queryParam(name);
                        continue;
                    }
                    for (String v : values) {
                        if (v == null) {
                            b.queryParam(name);
                        } else {
                            b.queryParam(name, v);
                        }
                    }
                }
            }
        }
        b.queryParam("__lf_confirm", "1");
        return b.build().toUriString();
    }

    private String renderUnavailableHtml(String title, String message, String code) {
        String t = escapeHtml(title);
        String msg = escapeHtml(message);
        String c = escapeHtml(code == null ? "" : code);
        String rid = escapeHtml(RequestId.get());
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <meta name="robots" content="noindex,nofollow" />
                  <title>%s</title>
                  <style>
                    body { font-family: -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Arial,"Noto Sans",sans-serif; margin:0; background:#fafafa; color:#111; }
                    .wrap { max-width: 720px; margin: 48px auto; padding: 0 16px; }
                    .card { background:#fff; border:1px solid #eee; border-radius: 12px; padding: 20px; }
                    .sub { color:#666; margin-top: 8px; }
                    a.btn { display:inline-block; background:#111; color:#fff; padding: 10px 14px; border-radius: 10px; text-decoration:none; }
                    .mono { font-family: ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,"Liberation Mono","Courier New",monospace; font-size: 12px; }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <div class="card">
                      <h1>%s</h1>
                      <div class="sub">%s</div>
                      <div class="sub mono" style="margin-top:14px;">code: %s</div>
                      <div class="sub mono">requestId: %s</div>
                      <div style="margin-top:16px;">
                        <a class="btn" href="javascript:history.back()">返回</a>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(t, t, msg, c, rid);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }

    private static boolean matchesAny(String name, List<String> patterns) {
        if (name == null || name.isBlank() || patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String p : patterns) {
            String v = trimToNull(p);
            if (v == null) {
                continue;
            }
            if (v.endsWith("*")) {
                String prefix = v.substring(0, v.length() - 1);
                if (!prefix.isBlank() && name.startsWith(prefix)) {
                    return true;
                }
            } else if (name.equals(v)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI u = URI.create(url.trim());
            String scheme = u.getScheme();
            return scheme != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (Exception e) {
            return false;
        }
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }

    private void logUnavailable(long startNs, String code, UnavailableReason reason, VisitInfo visitInfo) {
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

    private enum UnavailableReason {
        NOT_FOUND,
        DISABLED,
        EXPIRED;

        ErrorCode toErrorCode() {
            return switch (this) {
                case NOT_FOUND -> ErrorCode.LINK_NOT_FOUND;
                case DISABLED -> ErrorCode.LINK_DISABLED;
                case EXPIRED -> ErrorCode.LINK_EXPIRED;
            };
        }
    }
}
