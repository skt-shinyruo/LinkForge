package com.linkforge.accounts.application;

import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.accounts.infrastructure.persistence.entity.ApiKeyEntity;
import com.linkforge.accounts.infrastructure.persistence.repo.ApiKeyRepository;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.TenantGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    @Test
    void authenticate_shouldNotUpdateLastUsedAt_whenWithinThrottleWindow() {
        ApiKeyRepository repo = mock(ApiKeyRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setLastUsedUpdateIntervalSeconds(300);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                repo,
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

        when(repo.findById(123L)).thenReturn(Optional.of(e));
        when(encoder.matches("secret", "hash")).thenReturn(true);

        service.authenticate("lfk_123_secret");

        verify(repo, never()).save(any());
    }

    @Test
    void authenticate_shouldUpdateLastUsedAt_whenMissing() {
        ApiKeyRepository repo = mock(ApiKeyRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setLastUsedUpdateIntervalSeconds(300);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                repo,
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

        when(repo.findById(123L)).thenReturn(Optional.of(e));
        when(encoder.matches("secret", "hash")).thenReturn(true);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.authenticate("lfk_123_secret");

        ArgumentCaptor<ApiKeyEntity> captor = ArgumentCaptor.forClass(ApiKeyEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getLastUsedAt()).isNotNull();
    }

    @Test
    void authenticate_shouldUpdateLastUsedAt_whenOlderThanThrottleWindow() {
        ApiKeyRepository repo = mock(ApiKeyRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setLastUsedUpdateIntervalSeconds(300);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                repo,
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

        when(repo.findById(123L)).thenReturn(Optional.of(e));
        when(encoder.matches("secret", "hash")).thenReturn(true);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.authenticate("lfk_123_secret");

        verify(repo).save(any());
    }

    @Test
    void authenticate_shouldNotUpdateLastUsedAt_whenThrottleDisabled() {
        ApiKeyRepository repo = mock(ApiKeyRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                repo,
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

        when(repo.findById(123L)).thenReturn(Optional.of(e));
        when(encoder.matches("secret", "hash")).thenReturn(true);

        service.authenticate("lfk_123_secret");

        verify(repo, never()).save(any());
    }
}
