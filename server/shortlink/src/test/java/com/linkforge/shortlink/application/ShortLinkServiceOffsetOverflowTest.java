package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.security.TenantGuard;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.infrastructure.outbox.LinkCacheOutboxRepository;
import com.linkforge.shortlink.infrastructure.persistence.mapper.LinkTagMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkCommandMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkSearchParam;
import com.linkforge.shortlink.infrastructure.persistence.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortLinkServiceOffsetOverflowTest {

    @Test
    void search_shouldRejectHugePageToAvoidOffsetOverflowAndSlowQuery() {
        ShortLinkQueryMapper shortLinkQueryMapper = mock(ShortLinkQueryMapper.class);

        ShortLinkService service = new ShortLinkService(
                mock(SnowflakeIdGenerator.class),
                mock(ShortLinkCommandMapper.class),
                shortLinkQueryMapper,
                mock(TagMapper.class),
                mock(LinkTagMapper.class),
                mock(LinkCachePort.class),
                mock(LinkCacheOutboxRepository.class),
                mock(CoreProperties.class),
                mock(UrlValidator.class),
                mock(TenantGuard.class),
                mock(PlatformTransactionManager.class)
        );

        int page = Integer.MAX_VALUE;
        int size = 2;
        assertThatThrownBy(() -> service.search(
                1L,
                new ShortLinkSearchQuery(false, null, null, null),
                new PageQuery(page, size)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                })
                .hasMessageContaining("分页参数过大");

        verifyNoInteractions(shortLinkQueryMapper);
    }

    @Test
    void exportCsv_shouldRejectHugePageToAvoidOffsetOverflowAndSlowQuery() {
        ShortLinkQueryMapper shortLinkQueryMapper = mock(ShortLinkQueryMapper.class);
        when(shortLinkQueryMapper.listSearch(any())).thenReturn(java.util.List.of());

        ShortLinkService service = new ShortLinkService(
                mock(SnowflakeIdGenerator.class),
                mock(ShortLinkCommandMapper.class),
                shortLinkQueryMapper,
                mock(TagMapper.class),
                mock(LinkTagMapper.class),
                mock(LinkCachePort.class),
                mock(LinkCacheOutboxRepository.class),
                mock(CoreProperties.class),
                mock(UrlValidator.class),
                mock(TenantGuard.class),
                mock(PlatformTransactionManager.class)
        );

        int page = Integer.MAX_VALUE;
        int size = 2;
        assertThatThrownBy(() -> service.exportCsv(
                1L,
                new PageQuery(page, size),
                new StringWriter()
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                })
                .hasMessageContaining("分页参数过大");

        verifyNoInteractions(shortLinkQueryMapper);
    }
}
