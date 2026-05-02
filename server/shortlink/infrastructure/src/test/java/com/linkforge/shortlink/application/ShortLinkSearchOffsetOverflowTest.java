package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.query.ExportShortLinksCsvQueryHandler;
import com.linkforge.shortlink.application.query.SearchShortLinksQueryHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ShortLinkSearchOffsetOverflowTest {

    @Test
    void search_shouldRejectHugePageToAvoidOffsetOverflowAndSlowQuery() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        SearchShortLinksQueryHandler handler = new SearchShortLinksQueryHandler(
                shortLinkRepository,
                mock(LinkTagRepository.class),
                mock(ShortLinkDtoMapper.class)
        );

        int page = Integer.MAX_VALUE;
        int size = 2;
        assertThatThrownBy(() -> handler.handle(
                1L,
                new ShortLinkSearchQuery(false, null, null, null, null),
                new PageQuery(page, size)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                })
                .hasMessageContaining("分页参数过大");

        verifyNoInteractions(shortLinkRepository);
    }

    @Test
    void exportCsv_shouldRejectHugePageToAvoidOffsetOverflowAndSlowQuery() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        ExportShortLinksCsvQueryHandler handler = new ExportShortLinksCsvQueryHandler(
                shortLinkRepository,
                mock(LinkTagRepository.class)
        );

        int page = Integer.MAX_VALUE;
        int size = 2;
        assertThatThrownBy(() -> handler.handle(
                1L,
                new ShortLinkSearchQuery(false, null, null, null, null),
                new PageQuery(page, size)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                })
                .hasMessageContaining("分页参数过大");

        verifyNoInteractions(shortLinkRepository);
    }
}
