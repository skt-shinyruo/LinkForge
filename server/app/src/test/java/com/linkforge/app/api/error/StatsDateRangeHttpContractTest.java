package com.linkforge.app.api.error;

import com.linkforge.analytics.application.AnalyticsExportRequestService;
import com.linkforge.analytics.application.AnalyticsLinkEventsService;
import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.application.AnalyticsReportingApplicationService;
import com.linkforge.analytics.interfaces.web.StatsController;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.runtime.security.PrincipalActorMapper;
import com.linkforge.foundation.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatsDateRangeHttpContractTest {

    private AnalyticsQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(AnalyticsQueryService.class);
        StatsController controller = new StatsController(
                queryService,
                mock(AnalyticsReportingApplicationService.class),
                mock(AnalyticsLinkEventsService.class),
                mock(AnalyticsExportRequestService.class),
                mock(PrincipalActorMapper.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(9L, 1L, "admin@example.com", Set.of("TENANT_ADMIN")),
                "N/A",
                List.of()
        ));
        when(queryService.tenantDaily(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void overview_shouldRejectInvalidDateFormatAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/stats/overview")
                        .param("from", "2026-08-xx")
                        .param("to", "2026-08-16"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        verifyNoInteractions(queryService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRanges")
    void overview_shouldRejectInvalidInclusiveUtcRanges(
            String scenario,
            String from,
            String to
    ) throws Exception {
        mockMvc.perform(get("/api/v1/stats/overview")
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        verifyNoInteractions(queryService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validRanges")
    void overview_shouldAcceptValidInclusiveUtcRanges(
            String scenario,
            String from,
            String to
    ) throws Exception {
        mockMvc.perform(get("/api/v1/stats/overview")
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk());

        verify(queryService).tenantDaily(1L, LocalDate.parse(from), LocalDate.parse(to));
    }

    private static Stream<Arguments> invalidRanges() {
        return Stream.of(
                Arguments.of("start after end", "2026-08-16", "2026-08-15"),
                Arguments.of("367 inclusive days", "2024-01-01", "2025-01-01")
        );
    }

    private static Stream<Arguments> validRanges() {
        return Stream.of(
                Arguments.of("one inclusive day", "2026-08-16", "2026-08-16"),
                Arguments.of("leap-year 366 days", "2024-01-01", "2024-12-31"),
                Arguments.of("cross-year range", "2025-12-31", "2026-01-01")
        );
    }
}
