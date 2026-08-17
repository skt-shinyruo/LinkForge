package com.linkforge.analytics.interfaces.web;

import com.linkforge.analytics.application.AnalyticsReportingApplicationService;
import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StatsControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.remove(RequestId.MDC_KEY);
    }

    @Test
    void constructor_shouldKeepGovernanceDetailsOutOfControllerDependencies() {
        Constructor<?> constructor = StatsController.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
                .contains(
                        AnalyticsReportingApplicationService.class,
                        AnalyticsQueryService.class
                );
        assertThat(constructor.getParameterTypes())
                .extracting(Class::getName)
                .doesNotContain(
                        "com.linkforge.governance.application.GovernanceService",
                        "com.linkforge.governance.application.GovernanceApprovalRequestService",
                        "com.linkforge.contract.shortlink.ShortLinkReadPort"
                );
    }

    @Test
    void overview_shouldRejectMoreThan366UtcDaysBeforeQuerying() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        StatsController controller = new StatsController(
                queryService,
                mock(AnalyticsReportingApplicationService.class)
        );

        assertThatThrownBy(() -> controller.overview(
                LocalDate.parse("2024-01-01"),
                LocalDate.parse("2025-01-01")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oversizedReportCalls")
    void reportEndpoints_shouldShareThe366UtcDayLimit(
            String endpoint,
            Consumer<StatsController> invocation
    ) {
        StatsController controller = new StatsController(
                mock(AnalyticsQueryService.class),
                mock(AnalyticsReportingApplicationService.class)
        );

        assertThatThrownBy(() -> invocation.accept(controller))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .as(endpoint)
                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    private static Stream<Arguments> oversizedReportCalls() {
        LocalDate from = LocalDate.parse("2024-01-01");
        LocalDate to = LocalDate.parse("2025-01-01");
        return Stream.of(
                Arguments.of("link daily", (Consumer<StatsController>) c -> c.linkDaily(1L, from, to)),
                Arguments.of("application daily", (Consumer<StatsController>) c -> c.applicationOverview(2L, from, to)),
                Arguments.of("domain daily", (Consumer<StatsController>) c -> c.domainOverview(3L, from, to)),
                Arguments.of("tenant top", (Consumer<StatsController>) c -> c.topLinks(from, to, null, null)),
                Arguments.of("application top", (Consumer<StatsController>) c -> c.applicationTopLinks(2L, from, to, null, null)),
                Arguments.of("domain top", (Consumer<StatsController>) c -> c.domainTopLinks(3L, from, to, null, null))
        );
    }

    @Test
    void topLinks_shouldDelegateToAnalyticsReportingApplicationService() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        AnalyticsReportingApplicationService reportingService = mock(AnalyticsReportingApplicationService.class);
        StatsController controller = new StatsController(
                queryService,
                reportingService
        );

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(9L, 1L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                "N/A",
                java.util.List.of()
        ));
        MDC.put(RequestId.MDC_KEY, "req-top");

        LocalDate from = LocalDate.parse("2026-04-01");
        LocalDate to = LocalDate.parse("2026-04-24");
        List<AnalyticsQueryService.TopLinkStat> expected = List.of(
                new AnalyticsQueryService.TopLinkStat(
                        101L,
                        "abc123",
                        "https://sho.rt/abc123",
                        "https://example.com/a",
                        50L,
                        40L,
                        false
                )
        );
        when(reportingService.topLinks(1L, from, to, 10, AnalyticsQueryService.TopSortBy.PV)).thenReturn(expected);

        ApiResponse<List<AnalyticsQueryService.TopLinkStat>> response = controller.topLinks(from, to, null, null);

        assertThat(response.getData()).isEqualTo(expected);
        assertThat(response.getRequestId()).isEqualTo("req-top");
        verify(reportingService).topLinks(1L, from, to, 10, AnalyticsQueryService.TopSortBy.PV);
    }
}
