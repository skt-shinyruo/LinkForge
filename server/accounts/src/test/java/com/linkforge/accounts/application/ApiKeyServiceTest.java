package com.linkforge.accounts.application;

import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.accounts.infrastructure.persistence.entity.ApiKeyEntity;
import com.linkforge.accounts.infrastructure.persistence.mapper.ApiKeyMapper;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.TenantGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    @Test
    void constructor_shouldDependOnApiKeyMapperInsteadOfSpringDataRepository() {
        Constructor<?> constructor = ApiKeyService.class.getDeclaredConstructors()[0];

        assertThat(Arrays.stream(constructor.getParameterTypes()).map(Class::getName))
                .contains("com.linkforge.accounts.infrastructure.persistence.mapper.ApiKeyMapper")
                .doesNotContain("com.linkforge.accounts.infrastructure.persistence.repo.ApiKeyRepository");
    }

    @Test
    void authenticate_shouldNotUpdateLastUsedAt_whenWithinThrottleWindow() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setLastUsedUpdateIntervalSeconds(300);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                mapper,
                encoder,
                mock(TenantGuard.class),
                props
        );

        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(123L);
        e.setTenantId(1L);
        e.setStatus(AccountsConstants.STATUS_ACTIVE);
        e.setKeyHash("hash");
        e.setLastUsedAt(LocalDateTime.now());

        when(mapper.findById(123L)).thenReturn(e);
        when(encoder.matches("secret", "hash")).thenReturn(true);

        service.authenticate("lfk_123_secret");

        verify(mapper, never()).update(any());
    }

    @Test
    void authenticate_shouldUpdateLastUsedAt_whenMissing() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setLastUsedUpdateIntervalSeconds(300);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                mapper,
                encoder,
                mock(TenantGuard.class),
                props
        );

        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(123L);
        e.setTenantId(1L);
        e.setStatus(AccountsConstants.STATUS_ACTIVE);
        e.setKeyHash("hash");
        e.setLastUsedAt(null);

        when(mapper.findById(123L)).thenReturn(e);
        when(encoder.matches("secret", "hash")).thenReturn(true);
        when(mapper.update(any())).thenReturn(1);

        service.authenticate("lfk_123_secret");

        ArgumentCaptor<ApiKeyEntity> captor = ArgumentCaptor.forClass(ApiKeyEntity.class);
        verify(mapper).update(captor.capture());
        assertThat(captor.getValue().getLastUsedAt()).isNotNull();
    }

    @Test
    void authenticate_shouldUpdateLastUsedAt_whenOlderThanThrottleWindow() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setLastUsedUpdateIntervalSeconds(300);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                mapper,
                encoder,
                mock(TenantGuard.class),
                props
        );

        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(123L);
        e.setTenantId(1L);
        e.setStatus(AccountsConstants.STATUS_ACTIVE);
        e.setKeyHash("hash");
        e.setLastUsedAt(LocalDateTime.now().minusSeconds(3600));

        when(mapper.findById(123L)).thenReturn(e);
        when(encoder.matches("secret", "hash")).thenReturn(true);
        when(mapper.update(any())).thenReturn(1);

        service.authenticate("lfk_123_secret");

        verify(mapper).update(any());
    }

    @Test
    void authenticate_shouldNotUpdateLastUsedAt_whenThrottleDisabled() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                mapper,
                encoder,
                mock(TenantGuard.class),
                props
        );

        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(123L);
        e.setTenantId(1L);
        e.setStatus(AccountsConstants.STATUS_ACTIVE);
        e.setKeyHash("hash");
        e.setLastUsedAt(null);

        when(mapper.findById(123L)).thenReturn(e);
        when(encoder.matches("secret", "hash")).thenReturn(true);

        service.authenticate("lfk_123_secret");

        verify(mapper, never()).update(any());
    }
}
