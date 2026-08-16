package com.linkforge;

import com.linkforge.LinkForgeApplication;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.redirect.application.RedirectResolution;
import com.linkforge.redirect.application.ResolveRedirectRequest;
import com.linkforge.redirect.application.RedirectService;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RedirectExperienceIntegrationTest extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        // 避免启动期严格校验失败（以及减少 log 噪音）
        r.add("app.redirect.cache-ttl-seconds", () -> "60");
        r.add("app.redirect.default-status-code", () -> "302");

        // 预览页内部参数默认不透传（逗号分隔 List 绑定）
        r.add("app.redirect.query-forward-reserved-params", () -> "__lf_confirm,__lf_preview");
    }

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RedirectService redirectService;

    @Test
    void should_return_404_html_when_link_not_found_and_accept_html() throws Exception {
        when(redirectService.resolve(any(ResolveRedirectRequest.class)))
                .thenAnswer(invocation -> {
                    ResolveRedirectRequest request = invocation.getArgument(0);
                    return RedirectResolution.notFound(request.code(), request.htmlRequest());
                });

        mockMvc.perform(get("/r/missing").header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        verify(redirectService).resolve(any(ResolveRedirectRequest.class));
    }

    @Test
    void should_return_json_when_link_not_found_and_accept_not_html() throws Exception {
        when(redirectService.resolve(any(ResolveRedirectRequest.class)))
                .thenAnswer(invocation -> {
                    ResolveRedirectRequest request = invocation.getArgument(0);
                    return RedirectResolution.notFound(request.code(), request.htmlRequest());
                });

        mockMvc.perform(get("/r/missing").header(HttpHeaders.ACCEPT, "application/json"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(RedirectErrorCode.LINK_NOT_FOUND.getCode()));

        verify(redirectService).resolve(any(ResolveRedirectRequest.class));
    }

    @Test
    void should_return_410_html_when_link_disabled_and_accept_html() throws Exception {
        LinkMeta meta = new LinkMeta(
                        1L,
                        1L,
                        "abc",
                        "https://example.com",
                        false,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null
                );
        when(redirectService.resolve(any(ResolveRedirectRequest.class)))
                .thenReturn(RedirectResolution.unavailable(
                        "abc",
                        true,
                        meta,
                        RedirectResolution.UnavailableReason.DISABLED
                ));

        mockMvc.perform(get("/r/abc").header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void should_return_preview_html_when_preview_enabled_and_not_confirmed() throws Exception {
        LinkMeta meta = new LinkMeta(
                        1L,
                        1L,
                        "abc",
                        "https://example.com/landing",
                        true,
                        null,
                        null,
                        true,
                        null,
                        null,
                        null,
                        null
                );
        when(redirectService.resolve(any(ResolveRedirectRequest.class)))
                .thenReturn(RedirectResolution.preview("abc", true, meta));

        MvcResult r = mockMvc.perform(get("/r/abc").header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn();

        String body = r.getResponse().getContentAsString();
        assertThat(body).contains("__lf_confirm=1");
    }

    @Test
    void should_redirect_after_confirm_and_forward_allowed_query_only() throws Exception {
        LinkMeta meta = new LinkMeta(
                        1L,
                        1L,
                        "abc",
                        "https://example.com/path?existing=1",
                        true,
                        null,
                        301,
                        true,
                        null,
                        "ALLOWLIST",
                        "utm_*",
                        null
                );
        when(redirectService.resolve(any(ResolveRedirectRequest.class)))
                .thenReturn(RedirectResolution.redirect("abc", true, meta));

        MvcResult r = mockMvc.perform(
                        get("/r/abc")
                                .queryParam("__lf_confirm", "1")
                                .queryParam("utm_source", "x")
                                .queryParam("foo", "bar")
                                .header(HttpHeaders.ACCEPT, "text/html")
                )
                .andExpect(status().isMovedPermanently())
                .andReturn();

        String location = r.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotBlank();
        assertThat(location).contains("existing=1");
        assertThat(location).contains("utm_source=x");
        assertThat(location).doesNotContain("foo=bar");
        assertThat(location).doesNotContain("__lf_confirm");
    }

    @Test
    void should_not_override_existing_query_param_in_original_url() throws Exception {
        LinkMeta meta = new LinkMeta(
                        1L,
                        1L,
                        "abc",
                        "https://example.com/path?utm_source=old",
                        true,
                        null,
                        null,
                        false,
                        null,
                        "ALL",
                        null,
                        null
                );
        when(redirectService.resolve(any(ResolveRedirectRequest.class)))
                .thenReturn(RedirectResolution.redirect("abc", true, meta));

        MvcResult r = mockMvc.perform(
                        get("/r/abc")
                                .queryParam("utm_source", "new")
                                .header(HttpHeaders.ACCEPT, "text/html")
                )
                .andExpect(status().isFound())
                .andReturn();

        String location = r.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).contains("utm_source=old");
        assertThat(location).doesNotContain("utm_source=new");
    }

    @Test
    void should_return_410_json_when_link_expired_and_accept_not_html() throws Exception {
        LinkMeta meta = new LinkMeta(
                        1L,
                        1L,
                        "abc",
                        "https://example.com",
                        true,
                        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                        null,
                        false,
                        null,
                        null,
                        null,
                        null
                );
        when(redirectService.resolve(any(ResolveRedirectRequest.class)))
                .thenReturn(RedirectResolution.unavailable(
                        "abc",
                        false,
                        meta,
                        RedirectResolution.UnavailableReason.EXPIRED
                ));

        mockMvc.perform(get("/r/abc").header(HttpHeaders.ACCEPT, "application/json"))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(RedirectErrorCode.LINK_EXPIRED.getCode()));
    }
}
