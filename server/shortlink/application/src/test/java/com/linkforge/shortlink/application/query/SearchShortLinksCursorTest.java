package com.linkforge.shortlink.application.query;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.domain.ShortLink;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchShortLinksCursorTest {

    @Test
    void cursorQuery_shouldSkipCountFetchOneExtraAndReturnNextCursor() {
        ShortLinkRepository repository = mock(ShortLinkRepository.class);
        LinkTagRepository tags = mock(LinkTagRepository.class);
        ShortLinkDtoMapper mapper = mock(ShortLinkDtoMapper.class);
        SearchShortLinksQueryHandler handler = new SearchShortLinksQueryHandler(repository, tags, mapper);
        ShortLinkSearchQuery query = new ShortLinkSearchQuery(false, null, null, null, null);

        LocalDateTime firstCreatedAt = LocalDateTime.parse("2026-08-10T10:15:30");
        LocalDateTime secondCreatedAt = LocalDateTime.parse("2026-08-10T10:15:29");
        ShortLink first = link(101L, firstCreatedAt);
        ShortLink second = link(100L, secondCreatedAt);
        ShortLink extra = link(99L, LocalDateTime.parse("2026-08-10T10:15:28"));
        LinkDto firstDto = dto(101L);
        LinkDto secondDto = dto(100L);
        String cursor = ShortLinkSearchCursorCodec.encode(LocalDateTime.parse("2026-08-10T10:16:00"), 110L);

        when(repository.listSearchAfter(
                1L,
                query,
                LocalDateTime.parse("2026-08-10T10:16:00"),
                110L,
                3
        )).thenReturn(List.of(first, second, extra));
        when(tags.findTagNamesByLinkIds(anyList())).thenReturn(List.of());
        when(mapper.toDto(eq(first), anyList())).thenReturn(firstDto);
        when(mapper.toDto(eq(second), anyList())).thenReturn(secondDto);

        PageResult<LinkDto> result = handler.handle(1L, query, new PageQuery(0, 2), false, cursor);

        assertThat(result.items()).containsExactly(firstDto, secondDto);
        assertThat(result.total()).isEqualTo(-1L);
        assertThat(result.hasMore()).isTrue();
        ShortLinkSearchCursorCodec.Cursor next = ShortLinkSearchCursorCodec.decode(result.nextCursor());
        assertThat(next.createdAtUtc()).isEqualTo(secondCreatedAt);
        assertThat(next.id()).isEqualTo(100L);
        verify(repository, never()).countSearch(1L, query);
    }

    @Test
    void offsetQueryWithoutTotal_shouldFetchOneExtraWithoutCounting() {
        ShortLinkRepository repository = mock(ShortLinkRepository.class);
        SearchShortLinksQueryHandler handler = new SearchShortLinksQueryHandler(
                repository,
                mock(LinkTagRepository.class),
                mock(ShortLinkDtoMapper.class)
        );
        ShortLinkSearchQuery query = new ShortLinkSearchQuery(false, null, null, null, null);
        when(repository.listSearch(1L, query, 10L, 6)).thenReturn(List.of());

        PageResult<LinkDto> result = handler.handle(1L, query, new PageQuery(2, 5), false, null);

        assertThat(result.total()).isEqualTo(-1L);
        assertThat(result.hasMore()).isFalse();
        verify(repository, never()).countSearch(1L, query);
        verify(repository).listSearch(1L, query, 10L, 6);
    }

    @Test
    void cursorCodec_shouldRejectMalformedOrUnsupportedValues() {
        assertThatThrownBy(() -> ShortLinkSearchCursorCodec.decode("v2.not-supported"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("游标");
        assertThatThrownBy(() -> ShortLinkSearchCursorCodec.decode("v1.not-base64***"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("游标");
    }

    private static ShortLink link(long id, LocalDateTime createdAtUtc) {
        ShortLink link = mock(ShortLink.class);
        when(link.id()).thenReturn(id);
        when(link.createdAtUtc()).thenReturn(createdAtUtc);
        return link;
    }

    private static LinkDto dto(long id) {
        return new LinkDto(
                id,
                1L,
                null,
                null,
                "ACTIVE",
                "code" + id,
                "https://go.example.test/r/code" + id,
                "https://example.test/" + id,
                null,
                true,
                null,
                null,
                302,
                false,
                null,
                null,
                List.of(),
                List.of(),
                null
        );
    }
}
