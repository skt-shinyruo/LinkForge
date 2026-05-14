package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.shortlink.application.command.ArchiveShortLinkCommandHandler;
import com.linkforge.shortlink.application.command.CreateShortLinkCommandHandler;
import com.linkforge.shortlink.application.command.CreateTagCommandHandler;
import com.linkforge.shortlink.application.command.DeleteShortLinkCommandHandler;
import com.linkforge.shortlink.application.command.ImportShortLinksCsvCommandHandler;
import com.linkforge.shortlink.application.command.RestoreShortLinkCommandHandler;
import com.linkforge.shortlink.application.command.UpdateShortLinkCommandHandler;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import com.linkforge.shortlink.application.query.ExportShortLinksCsvQueryHandler;
import com.linkforge.shortlink.application.query.GetShortLinkDetailQueryHandler;
import com.linkforge.shortlink.application.query.ListTagsQueryHandler;
import com.linkforge.shortlink.application.query.SearchShortLinksQueryHandler;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.domain.CreatedByType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShortLinkApplicationServiceTest {

    @Test
    void createForUser_shouldRejectBodyPathApplicationMismatch() {
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        ShortLinkApplicationService service = newService(applicationScopePort);

        CreateLinkRequest createRequest = new CreateLinkRequest(
                "https://example.com/source",
                null,
                null,
                true,
                null,
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                3002L,
                4001L,
                null
        );

        assertThatThrownBy(() -> service.createForUser(
                new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                new ScopedCreateLinkRequest(createRequest, 2001L)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(applicationScopePort).requireApplicationExists(1L, 2001L);
    }

    @Test
    void browseForUser_shouldRequirePathApplicationAndBuildScopedQuery() {
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        SearchShortLinksQueryHandler searchHandler = mock(SearchShortLinksQueryHandler.class);
        ShortLinkApplicationService service = newService(
                applicationScopePort,
                searchHandler,
                mock(CreateShortLinkCommandHandler.class)
        );

        PageResult<LinkDto> expected = new PageResult<>(List.of(), 0L, 0, 20);
        when(searchHandler.handle(eq(1L), any(), any())).thenReturn(expected);

        PageResult<LinkDto> result = service.browseForUser(
                new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                new BrowseLinksRequest(false, true, "launch", "marketing", null, 2001L, 2, 25, 100)
        );

        assertThat(result).isSameAs(expected);
        verify(applicationScopePort).requireApplicationExists(1L, 2001L);

        ArgumentCaptor<ShortLinkSearchQuery> queryCaptor = ArgumentCaptor.forClass(ShortLinkSearchQuery.class);
        verify(searchHandler).handle(eq(1L), queryCaptor.capture(), any());
        assertThat(queryCaptor.getValue()).isEqualTo(new ShortLinkSearchQuery(false, true, "launch", "marketing", 2001L));
    }

    @Test
    void browseForUser_shouldScopeRegularUserToOwnUnscopedLinks() {
        SearchShortLinksQueryHandler searchHandler = mock(SearchShortLinksQueryHandler.class);
        ShortLinkApplicationService service = newService(
                mock(ApplicationScopePort.class),
                searchHandler,
                mock(CreateShortLinkCommandHandler.class)
        );
        PageResult<LinkDto> expected = new PageResult<>(List.of(), 0L, 0, 20);
        when(searchHandler.handle(eq(1L), any(), any())).thenReturn(expected);

        PageResult<LinkDto> result = service.browseForUser(
                new UserActor(1L, 99L, "user@example.com", Set.of("USER")),
                new BrowseLinksRequest(false, true, "launch", null, null, null, 0, 20, 100)
        );

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<ShortLinkSearchQuery> queryCaptor = ArgumentCaptor.forClass(ShortLinkSearchQuery.class);
        verify(searchHandler).handle(eq(1L), queryCaptor.capture(), any());
        assertThat(queryCaptor.getValue()).isEqualTo(new ShortLinkSearchQuery(
                false,
                true,
                "launch",
                null,
                null,
                99L,
                CreatedByType.USER,
                true
        ));
    }

    @Test
    void browseForApiKey_shouldRejectUnauthorizedApplicationScope() {
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        ShortLinkApplicationService service = newService(applicationScopePort);

        assertThatThrownBy(() -> service.browseForApiKey(
                new ApiKeyActor(1L, 88L, 2001L),
                new BrowseLinksRequest(false, true, null, null, null, 3001L, 0, 20, 100)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(applicationScopePort);
    }

    @Test
    void createForApiKey_shouldInjectAuthorizedApplicationIdIntoCreateRequest() {
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        CreateShortLinkCommandHandler createHandler = mock(CreateShortLinkCommandHandler.class);
        ShortLinkApplicationService service = newService(applicationScopePort, mock(SearchShortLinksQueryHandler.class), createHandler);

        LinkDto expected = new LinkDto(
                42L,
                1L,
                2001L,
                4001L,
                "ACTIVE",
                "launch",
                "https://lnk.forge/launch",
                "https://example.com/source",
                null,
                true,
                Instant.parse("2026-04-01T00:00:00Z"),
                null,
                null,
                false,
                null,
                null,
                List.of(),
                List.of(),
                Instant.parse("2026-03-18T09:10:11Z")
        );
        when(createHandler.handle(eq(1L), any(), any())).thenReturn(expected);

        CreateLinkRequest createRequest = new CreateLinkRequest(
                "https://example.com/source",
                null,
                null,
                true,
                "launch",
                Set.of("marketing"),
                null,
                null,
                null,
                null,
                null,
                null,
                4001L,
                null
        );

        assertThat(service.createForApiKey(
                new ApiKeyActor(1L, 88L, 2001L),
                new ScopedCreateLinkRequest(createRequest, null)
        )).isSameAs(expected);

        ArgumentCaptor<CreateLinkRequest> requestCaptor = ArgumentCaptor.forClass(CreateLinkRequest.class);
        verify(createHandler).handle(eq(1L), eq(CreatedBy.apiKey(88L)), requestCaptor.capture());
        assertThat(requestCaptor.getValue().applicationId()).isEqualTo(2001L);
    }

    @Test
    void importCsvForUser_shouldInjectPathApplicationAndSelectedDomainScope() {
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        ImportShortLinksCsvCommandHandler importHandler = mock(ImportShortLinksCsvCommandHandler.class);
        ShortLinkApplicationService service = newService(
                applicationScopePort,
                mock(SearchShortLinksQueryHandler.class),
                mock(CreateShortLinkCommandHandler.class),
                importHandler
        );
        ImportResult expected = new ImportResult(1, 0, List.of());
        when(importHandler.handle(anyLong(), any(), any(), any(), any())).thenReturn(expected);

        List<ShortLinkCsvImportRow> rows = List.of(new ShortLinkCsvImportRow(1L, "https://example.com/source", "launch", null, null, null));

        ImportResult actual = service.importCsv(
                new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                new ScopedImportCsvRequest(rows, 2001L, 3001L)
        );

        assertThat(actual).isSameAs(expected);
        verify(applicationScopePort).requireApplicationExists(1L, 2001L);
        verify(importHandler).handle(
                eq(1L),
                eq(CreatedBy.user(9L)),
                eq(rows),
                eq(2001L),
                eq(3001L)
        );
    }

    private static ShortLinkApplicationService newService(ApplicationScopePort applicationScopePort) {
        return newService(applicationScopePort, mock(SearchShortLinksQueryHandler.class), mock(CreateShortLinkCommandHandler.class));
    }

    private static ShortLinkApplicationService newService(
            ApplicationScopePort applicationScopePort,
            SearchShortLinksQueryHandler searchHandler,
            CreateShortLinkCommandHandler createHandler
    ) {
        return newService(applicationScopePort, searchHandler, createHandler, mock(ImportShortLinksCsvCommandHandler.class));
    }

    private static ShortLinkApplicationService newService(
            ApplicationScopePort applicationScopePort,
            SearchShortLinksQueryHandler searchHandler,
            CreateShortLinkCommandHandler createHandler,
            ImportShortLinksCsvCommandHandler importHandler
    ) {
        return new ShortLinkApplicationService(
                createHandler,
                mock(UpdateShortLinkCommandHandler.class),
                mock(ArchiveShortLinkCommandHandler.class),
                mock(RestoreShortLinkCommandHandler.class),
                mock(DeleteShortLinkCommandHandler.class),
                mock(GetShortLinkDetailQueryHandler.class),
                searchHandler,
                mock(ListTagsQueryHandler.class),
                mock(CreateTagCommandHandler.class),
                importHandler,
                mock(ExportShortLinksCsvQueryHandler.class),
                new ShortLinkActorScopeResolver(applicationScopePort)
        );
    }
}
