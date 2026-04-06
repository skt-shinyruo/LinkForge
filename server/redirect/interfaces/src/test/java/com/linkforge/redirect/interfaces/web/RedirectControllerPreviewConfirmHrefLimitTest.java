package com.linkforge.redirect.interfaces.web;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.redirect.application.RedirectResolution;
import com.linkforge.redirect.application.ResolveRedirectRequest;
import com.linkforge.redirect.application.RedirectService;
import com.linkforge.redirect.application.RedirectUrlBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedirectControllerPreviewConfirmHrefLimitTest {

    @Test
    void preview_should_fallback_to_minimal_confirm_href_when_query_too_long() {
        RedirectService redirectService = mock(RedirectService.class);

        RedirectProperties props = new RedirectProperties();
        RedirectUrlBuilder urlBuilder = new RedirectUrlBuilder(props);
        RedirectController controller = new RedirectController(
                redirectService,
                new RedirectHttpRequestMapper(),
                new RedirectHttpResponseWriter(
                        props,
                        urlBuilder,
                        new RedirectHtmlPageRenderer(props, new RedirectConfirmHrefBuilder())
                )
        );

        LinkMeta meta = new LinkMeta(
                1L,
                1L,
                "abc123",
                "https://example.com",
                true,
                null,
                null,
                true,
                null,
                "ALL",
                null,
                null
        );
        when(redirectService.resolve(any(ResolveRedirectRequest.class))).thenReturn(
                RedirectResolution.preview("abc123", true, meta)
        );

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/r/abc123");
        req.setRequestURI("/r/abc123");
        req.addHeader("Accept", "text/html");

        // Build a query string that would exceed the confirm-href hard cap.
        for (int i = 0; i < 50; i++) {
            req.addParameter("p" + i, "x".repeat(256));
        }

        ResponseEntity<?> resp = controller.redirect("abc123", req);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);

        String body = (String) resp.getBody();
        assertThat(body).isNotBlank();
        assertThat(body).contains("href=\"/r/abc123?__lf_confirm=1\"");
        assertThat(body).doesNotContain("p0=");
    }
}
