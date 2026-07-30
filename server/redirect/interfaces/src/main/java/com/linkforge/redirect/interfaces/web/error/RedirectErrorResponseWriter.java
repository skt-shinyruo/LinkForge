package com.linkforge.redirect.interfaces.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.foundation.runtime.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 在 Filter 阶段直接写出 Redirect 错误响应。
 *
 * <p>风控拒绝发生在 Controller 前，不能依赖 {@code RestControllerAdvice}。本 writer 根据 Accept 输出安全的
 * HTML 或 JSON，强制 no-store，并保证每个响应都带 requestId；当上游 filter 尚未建立 requestId 时会生成
 * 一个仅用于当前响应的值。</p>
 */
@Component
public class RedirectErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public RedirectErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将已决策的拒绝写到 Servlet 响应。
     *
     * <p>该方法只负责协议编码，不重复执行风险策略。{@code errorCode} 与 {@code message} 必须来自受控
     * 的 Redirect 业务逻辑，调用者不能传入未清洗的异常内容。</p>
     */
    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int httpStatus,
            RedirectErrorCode errorCode,
            String message
    ) throws IOException {
        if (response == null) {
            return;
        }

        String requestId = RequestId.get();
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
            response.setHeader(RequestIdFilter.HEADER_REQUEST_ID, requestId);
        }

        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");

        if (isHtmlRequest(request)) {
            response.setContentType(MediaType.TEXT_HTML_VALUE);
            response.getWriter().write(renderHtml(httpStatus, message, requestId));
            return;
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        RedirectErrorResponse body = new RedirectErrorResponse(errorCode.getCode(), message, requestId);
        response.getWriter().write(objectMapper.writeValueAsString(body));
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

    /**
     * 渲染不含内部诊断信息的最小错误页面。
     */
    private static String renderHtml(int httpStatus, String message, String requestId) {
        String title = httpStatus == 429 ? "请求过于频繁" : "请求被拒绝";
        String msg = message == null || message.isBlank() ? "请求无法处理。" : escapeHtml(message);
        String rid = requestId == null ? "" : escapeHtml(requestId);
        return """
                <!doctype html>
                <html lang="zh-CN">
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%s</title>
                    <style>
                      body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial;max-width:720px;margin:48px auto;padding:0 16px;line-height:1.5}
                      .card{border:1px solid #e5e7eb;border-radius:12px;padding:20px}
                      h1{font-size:20px;margin:0 0 8px}
                      p{margin:8px 0;color:#374151}
                      code{background:#f3f4f6;padding:2px 6px;border-radius:6px}
                    </style>
                  </head>
                  <body>
                    <div class="card">
                      <h1>%s</h1>
                      <p>%s</p>
                      <p>requestId: <code>%s</code></p>
                    </div>
                  </body>
                </html>
                """.formatted(escapeHtml(title), escapeHtml(title), msg, rid);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
