package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.platform.application.port.ApplicationPolicyRepository;
import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Domain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationProvisioningServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "example.com:443",
            "example.com/path",
            "exa mple.com",
            "bad_host.example.com",
            "*.example.com",
            "localhost",
            "127.0.0.1",
            "::1",
            "example.com."
    })
    void createTenantSharedDomain_shouldRejectInvalidHostnames(String hostname) {
        ApplicationProvisioningService service = newService(mock(DomainRepository.class));

        assertThatThrownBy(() -> service.createTenantSharedDomain(1L, actor(), hostname))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hostname 不合法");
    }

    @Test
    void createTenantSharedDomain_shouldNormalizeHostnameBeforeInsert() {
        DomainRepository domainRepository = mock(DomainRepository.class);
        ApplicationProvisioningService service = newService(domainRepository);

        ApplicationProvisioningService.DomainDto dto = service.createTenantSharedDomain(1L, actor(), " Go.Example.COM ");

        assertThat(dto.hostname()).isEqualTo("go.example.com");
        ArgumentCaptor<Domain> domainCaptor = ArgumentCaptor.forClass(Domain.class);
        verify(domainRepository).insert(domainCaptor.capture());
        assertThat(domainCaptor.getValue().hostname()).isEqualTo("go.example.com");
    }

    private static ApplicationProvisioningService newService(DomainRepository domainRepository) {
        return new ApplicationProvisioningService(
                new SnowflakeIdGenerator(),
                mock(ApplicationRepository.class),
                domainRepository,
                mock(ApplicationQuotaRepository.class),
                mock(ApplicationPolicyRepository.class)
        );
    }

    private static UserActor actor() {
        return new UserActor(1L, 9L, "admin@example.com", Set.of("TENANT_ADMIN"));
    }
}
