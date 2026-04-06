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

    @GetMapping("/{code}")
    public ResponseEntity<?> redirect(@PathVariable("code") String code, HttpServletRequest request) {
        ResolveRedirectRequest appRequest = redirectHttpRequestMapper.fromHttp(code, request);
        RedirectResolution resolution = redirectService.resolve(appRequest);
        return redirectHttpResponseWriter.write(resolution, request);
    }
}
