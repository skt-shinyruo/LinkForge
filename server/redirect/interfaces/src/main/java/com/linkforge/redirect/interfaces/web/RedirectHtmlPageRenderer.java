package com.linkforge.redirect.interfaces.web;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.redirect.application.RedirectResolution;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

@Component
public class RedirectHtmlPageRenderer {

    private final RedirectProperties redirectProperties;
    private final RedirectConfirmHrefBuilder confirmHrefBuilder;

    public RedirectHtmlPageRenderer(RedirectProperties redirectProperties, RedirectConfirmHrefBuilder confirmHrefBuilder) {
        this.redirectProperties = redirectProperties;
        this.confirmHrefBuilder = confirmHrefBuilder;
    }

    public ResponseEntity<String> renderPreview(LinkMeta meta, HttpServletRequest request) {
        String host = null;
        try {
            host = URI.create(meta.originalUrl()).getHost();
        } catch (Exception ignored) {
        }

        String confirmHref = confirmHrefBuilder.build(request);
        String title = "即将跳转";
        String message = host == null ? "即将跳转到目标站点，是否继续？" : ("即将跳转到站点：" + host);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body("""
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
                        escapeHtml(message),
                        escapeHtml(confirmHref),
                        escapeHtml(RequestId.get())
                ));
    }

    public ResponseEntity<?> renderNotFound(String code) {
        String landing = trimToNull(redirectProperties.getNotFoundLandingUrl());
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

    public ResponseEntity<?> renderUnavailable(String code, LinkMeta meta, RedirectResolution.UnavailableReason reason) {
        String perLink = meta == null ? null : trimToNull(meta.unavailableLandingUrl());
        if (isHttpUrl(perLink)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(perLink))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }
        String landing = trimToNull(redirectProperties.getGoneLandingUrl());
        if (isHttpUrl(landing)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(landing))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }

        String title = reason == RedirectResolution.UnavailableReason.DISABLED ? "短链已禁用" : "短链已过期";
        String message = reason == RedirectResolution.UnavailableReason.DISABLED
                ? "该短链已被禁用，请联系链接发布方获取最新链接。"
                : "该短链已过期，请联系链接发布方获取最新链接。";
        return ResponseEntity.status(HttpStatus.GONE)
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(renderUnavailableHtml(title, message, code));
    }

    private String renderUnavailableHtml(String title, String message, String code) {
        String safeTitle = escapeHtml(title);
        String safeMessage = escapeHtml(message);
        String safeCode = escapeHtml(code == null ? "" : code);
        String requestId = escapeHtml(RequestId.get());
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
                """.formatted(safeTitle, safeTitle, safeMessage, safeCode, requestId);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
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

    private static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            return scheme != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (Exception e) {
            return false;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
