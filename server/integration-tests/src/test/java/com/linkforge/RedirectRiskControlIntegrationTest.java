package com.linkforge;

import com.linkforge.LinkForgeApplication;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.redirect.application.RedirectResolution;
import com.linkforge.redirect.application.RedirectVisitInput;
import com.linkforge.redirect.application.RedirectService;
import com.linkforge.redirect.application.ResolveRedirectRequest;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RedirectRiskControlIntegrationTest extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        // 风控：测试中开启（阈值很低，便于验证 429/403）
        r.add("app.edge.trusted-proxies", () -> "10.0.0.0/8");
        r.add("app.edge.risk-control.enabled", () -> "true");
        r.add("app.edge.risk-control.ip-denylist", () -> "203.0.113.9/32");
        r.add("app.edge.risk-control.rate-limit.enabled", () -> "true");
        r.add("app.edge.risk-control.rate-limit.window-seconds", () -> "60");
        r.add("app.edge.risk-control.rate-limit.ip-max-requests", () -> "1");
        r.add("app.edge.risk-control.bot.enabled", () -> "false");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redis;

    @MockBean
    RedirectService redirectService;

    @BeforeEach
    void setUp() {
        LinkMeta meta = new LinkMeta(
                        1L,
                        1L,
                        "abc",
                        "https://example.com",
                        true,
                        (LocalDateTime) null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        LinkMeta.ACTIVE_LIFECYCLE_STATE
                );
        when(redirectService.resolve(any(ResolveRedirectRequest.class)))
                .thenAnswer(invocation -> {
                    ResolveRedirectRequest request = invocation.getArgument(0);
                    return RedirectResolution.redirect(request.code(), request.htmlRequest(), meta);
                });

        // 避免测试间限流 key 干扰
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void should_ignore_spoofed_forwarded_headers_when_remote_not_trusted() throws Exception {
        mockMvc.perform(
                        get("/r/abc")
                                .with(req -> {
                                    req.setRemoteAddr("198.51.100.10");
                                    return req;
                                })
                                .header("X-Real-IP", "1.2.3.4")
                                .header("X-Forwarded-For", "1.2.3.4")
                                .header("User-Agent", "ua-test")
                )
                .andExpect(status().isFound());

        ArgumentCaptor<ResolveRedirectRequest> cap = ArgumentCaptor.forClass(ResolveRedirectRequest.class);
        verify(redirectService).resolve(cap.capture());
        RedirectVisitInput visitInput = cap.getValue().visitInput();
        assertThat(visitInput).isNotNull();
        assertThat(visitInput.ip()).isEqualTo("198.51.100.10");
    }

    @Test
    void should_trust_x_real_ip_when_remote_trusted() throws Exception {
        mockMvc.perform(
                        get("/r/abc")
                                .with(req -> {
                                    req.setRemoteAddr("10.0.0.5");
                                    return req;
                                })
                                .header("X-Real-IP", "1.2.3.4")
                                .header("X-Forwarded-For", "1.2.3.4, 10.0.0.5")
                                .header("User-Agent", "ua-test")
                )
                .andExpect(status().isFound());

        ArgumentCaptor<ResolveRedirectRequest> cap = ArgumentCaptor.forClass(ResolveRedirectRequest.class);
        verify(redirectService).resolve(cap.capture());
        RedirectVisitInput visitInput = cap.getValue().visitInput();
        assertThat(visitInput).isNotNull();
        assertThat(visitInput.ip()).isEqualTo("1.2.3.4");
    }

    @Test
    void should_return_403_when_ip_in_denylist() throws Exception {
        mockMvc.perform(
                        get("/r/abc")
                                .with(req -> {
                                    req.setRemoteAddr("203.0.113.9");
                                    return req;
                                })
                                .header("User-Agent", "ua-test")
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(redirectService);
    }

    @Test
    void should_return_429_when_rate_limited() throws Exception {
        // 第一次放行
        mockMvc.perform(
                        get("/r/abc")
                                .with(req -> {
                                    req.setRemoteAddr("192.0.2.10");
                                    return req;
                                })
                                .header("User-Agent", "ua-test")
                )
                .andExpect(status().isFound());

        // 第二次同一 IP 在同窗口内触发限流
        mockMvc.perform(
                        get("/r/abc")
                                .with(req -> {
                                    req.setRemoteAddr("192.0.2.10");
                                    return req;
                                })
                                .header("User-Agent", "ua-test")
                )
                .andExpect(status().isTooManyRequests());
    }
}
