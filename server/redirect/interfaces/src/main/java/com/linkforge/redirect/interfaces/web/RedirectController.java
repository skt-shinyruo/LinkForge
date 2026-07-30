package com.linkforge.redirect.interfaces.web;

import com.linkforge.redirect.application.RedirectResolution;
import com.linkforge.redirect.application.RedirectService;
import com.linkforge.redirect.application.ResolveRedirectRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redirect 流量面的 HTTP 入口。
 *
 * <p>此 Controller 保持薄层：请求事实由 mapper 提取，业务状态由 RedirectService 决定，响应形式由 writer
 * 负责。不要在这里绕过预览、额度或访问记录顺序。</p>
 */
@RestController
@RequestMapping("/r")
public class RedirectController {

    private final RedirectService redirectService;
    private final RedirectHttpRequestMapper redirectHttpRequestMapper;
    private final RedirectHttpResponseWriter redirectHttpResponseWriter;

    public RedirectController(
            RedirectService redirectService,
            RedirectHttpRequestMapper redirectHttpRequestMapper,
            RedirectHttpResponseWriter redirectHttpResponseWriter
    ) {
        this.redirectService = redirectService;
        this.redirectHttpRequestMapper = redirectHttpRequestMapper;
        this.redirectHttpResponseWriter = redirectHttpResponseWriter;
    }

    /**
     * 解析并响应 {@code GET /r/{code}}。
     *
     * <p>接受 HTML 的客户端可能得到预览或落地页；其他客户端得到 redirect 或结构化业务错误。请求在
     * 正常部署中已由 {@link RedirectRiskControlFilter} 处理。</p>
     */
    @GetMapping("/{code}")
    public ResponseEntity<?> redirect(@PathVariable("code") String code, HttpServletRequest request) {
        ResolveRedirectRequest appRequest = redirectHttpRequestMapper.fromHttp(code, request);
        RedirectResolution resolution = redirectService.resolve(appRequest);
        return redirectHttpResponseWriter.write(resolution, request);
    }
}
