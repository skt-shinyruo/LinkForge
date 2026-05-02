package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ShortLinkActorScopeResolverTest {

    @Test
    void resolveCreateForUser_shouldPinBodyApplicationToPathApplication() {
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(applicationScopePort);
        UserActor actor = new UserActor(1L, 99L, "user@example.com", Set.of("TENANT_ADMIN"));

        CreateLinkRequest request = resolver.resolveCreateForUser(
                actor,
                new ScopedCreateLinkRequest(
                        createRequest(2001L),
                        2001L
                )
        );

        assertThat(request.applicationId()).isEqualTo(2001L);
        verify(applicationScopePort).requireApplicationExists(1L, 2001L);
    }

    @Test
    void resolveCreateForUser_shouldRejectPathBodyMismatch() {
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(mock(ApplicationScopePort.class));
        UserActor actor = new UserActor(1L, 99L, "user@example.com", Set.of("TENANT_ADMIN"));

        assertThatThrownBy(() -> resolver.resolveCreateForUser(
                actor,
                new ScopedCreateLinkRequest(createRequest(2002L), 2001L)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void resolveCreateForUser_shouldRejectApplicationScopeForNonTenantAdmin() {
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(mock(ApplicationScopePort.class));
        UserActor actor = new UserActor(1L, 99L, "user@example.com", Set.of("USER"));

        assertThatThrownBy(() -> resolver.resolveCreateForUser(
                actor,
                new ScopedCreateLinkRequest(createRequest(2001L), null)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void resolveBrowseForUser_shouldRejectApplicationFilterForNonTenantAdmin() {
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(mock(ApplicationScopePort.class));
        UserActor actor = new UserActor(1L, 99L, "user@example.com", Set.of("USER"));

        assertThatThrownBy(() -> resolver.resolveBrowseForUser(
                actor,
                new BrowseLinksRequest(false, true, null, null, 2001L, null, 0, 20, 100)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void resolveCreateForApiKey_shouldRequireApplicationWhenActorIsUnscoped() {
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(mock(ApplicationScopePort.class));
        ApiKeyActor actor = new ApiKeyActor(1L, 55L, null);

        assertThatThrownBy(() -> resolver.resolveCreateForApiKey(
                actor,
                new ScopedCreateLinkRequest(createRequest(null), null)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void resolveBrowseForApiKey_shouldUsePrincipalApplicationWhenPresent() {
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(applicationScopePort);
        ApiKeyActor actor = new ApiKeyActor(1L, 55L, 2001L);

        ShortLinkSearchQuery query = resolver.resolveBrowseForApiKey(
                actor,
                new BrowseLinksRequest(false, true, "abc", "tag", null, null, 0, 20, 100)
        );

        assertThat(query.applicationId()).isEqualTo(2001L);
        verifyNoInteractions(applicationScopePort);
    }

    @Test
    void resolveImportForUser_shouldRequirePathApplicationAndDomain() {
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(applicationScopePort);
        UserActor actor = new UserActor(1L, 99L, "user@example.com", Set.of("TENANT_ADMIN"));
        List<ShortLinkCsvImportRow> rows = List.of(
                new ShortLinkCsvImportRow(1L, "https://example.com/source", "launch", null, null, null)
        );

        ShortLinkActorScopeResolver.ImportScope scope = resolver.resolveImportForUser(
                actor,
                new ScopedImportCsvRequest(rows, 2001L, 3001L)
        );

        assertThat(scope.rows()).isSameAs(rows);
        assertThat(scope.applicationId()).isEqualTo(2001L);
        assertThat(scope.domainId()).isEqualTo(3001L);
        verify(applicationScopePort).requireApplicationExists(1L, 2001L);
    }

    @Test
    void resolveImportForUser_shouldRejectMissingDomainWhenPathApplicationIsProvided() {
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(mock(ApplicationScopePort.class));
        UserActor actor = new UserActor(1L, 99L, "user@example.com", Set.of("TENANT_ADMIN"));

        assertThatThrownBy(() -> resolver.resolveImportForUser(
                actor,
                new ScopedImportCsvRequest(List.of(), 2001L, null)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    private static CreateLinkRequest createRequest(Long applicationId) {
        return new CreateLinkRequest(
                "https://example.com",
                "note",
                null,
                true,
                "abc123",
                Set.of("alpha"),
                302,
                false,
                null,
                null,
                List.of(),
                applicationId,
                3001L,
                "ACTIVE"
        );
    }
}
