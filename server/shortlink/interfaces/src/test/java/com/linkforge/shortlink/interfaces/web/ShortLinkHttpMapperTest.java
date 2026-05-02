package com.linkforge.shortlink.interfaces.web;

import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.shortlink.application.*;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExport;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExportRow;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkCreateHttpRequest;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkHttpResponse;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkPageHttpResponse;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkUpdateHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkHttpMapperTest {

    private ShortLinkCsvHttpMapper csvHttpMapper;

    @BeforeEach
    void setUp() {
        csvHttpMapper = new ShortLinkCsvHttpMapper();
    }

    @Test
    void toCreateRequest_shouldTranslateHttpRequest() {
        ShortLinkCreateHttpRequest httpRequest = new ShortLinkCreateHttpRequest(
                "https://example.com/source",
                "launch note",
                Instant.parse("2026-03-18T09:10:11Z"),
                true,
                "launch",
                Set.of("marketing", "spring"),
                302,
                true,
                "https://example.com/unavailable",
                "ALLOWLIST",
                List.of("utm_*", "ref"),
                11L,
                12L,
                "ACTIVE"
        );

        assertThat(ShortLinkHttpMapper.toCreateRequest(httpRequest)).isEqualTo(
                new CreateLinkRequest(
                        "https://example.com/source",
                        "launch note",
                        Instant.parse("2026-03-18T09:10:11Z"),
                        true,
                        "launch",
                        Set.of("marketing", "spring"),
                        302,
                        true,
                        "https://example.com/unavailable",
                        "ALLOWLIST",
                        List.of("utm_*", "ref"),
                        11L,
                        12L,
                        "ACTIVE"
                )
        );
    }

    @Test
    void toUpdateRequest_shouldTranslateHttpRequest() {
        ShortLinkUpdateHttpRequest httpRequest = new ShortLinkUpdateHttpRequest(
                "https://example.com/updated",
                "updated note",
                Instant.parse("2026-04-01T01:02:03Z"),
                true,
                false,
                Set.of("ops"),
                301,
                true,
                false,
                "https://example.com/fallback",
                "DROP",
                true,
                List.of("utm_campaign"),
                "DRAFT"
        );

        assertThat(ShortLinkHttpMapper.toUpdateRequest(httpRequest)).isEqualTo(
                new UpdateLinkRequest(
                        "https://example.com/updated",
                        "updated note",
                        Instant.parse("2026-04-01T01:02:03Z"),
                        true,
                        false,
                        Set.of("ops"),
                        301,
                        true,
                        false,
                        "https://example.com/fallback",
                        "DROP",
                        true,
                        List.of("utm_campaign"),
                        "DRAFT"
                )
        );
    }

    @Test
    void toLinkResponse_shouldTranslateApplicationDtoToHttpContract() {
        LinkDto link = new LinkDto(
                42L,
                7L,
                11L,
                12L,
                "ACTIVE",
                "launch",
                "https://lnk.forge/launch",
                "https://example.com/source",
                "launch note",
                true,
                Instant.parse("2026-04-01T01:02:03Z"),
                null,
                302,
                true,
                "https://example.com/unavailable",
                "ALLOWLIST",
                List.of("utm_*"),
                List.of("marketing"),
                Instant.parse("2026-03-18T09:10:11Z")
        );

        assertThat(ShortLinkHttpMapper.toLinkResponse(link)).isEqualTo(new ShortLinkHttpResponse(
                42L,
                7L,
                11L,
                12L,
                "ACTIVE",
                "launch",
                "https://lnk.forge/launch",
                "https://example.com/source",
                "launch note",
                true,
                Instant.parse("2026-04-01T01:02:03Z"),
                null,
                302,
                true,
                "https://example.com/unavailable",
                "ALLOWLIST",
                List.of("utm_*"),
                List.of("marketing"),
                Instant.parse("2026-03-18T09:10:11Z")
        ));
    }

    @Test
    void toPageResponse_shouldMapItemsToHttpContractAndPreservePagination() {
        LinkDto link = new LinkDto(
                42L,
                7L,
                11L,
                12L,
                "ACTIVE",
                "launch",
                "https://lnk.forge/launch",
                "https://example.com/source",
                "launch note",
                true,
                Instant.parse("2026-04-01T01:02:03Z"),
                null,
                302,
                true,
                "https://example.com/unavailable",
                "ALLOWLIST",
                List.of("utm_*"),
                List.of("marketing"),
                Instant.parse("2026-03-18T09:10:11Z")
        );
        PageResult<LinkDto> result = new PageResult<>(List.of(link), 11L, 2, 5);

        assertThat(ShortLinkHttpMapper.toPageResponse(result)).isEqualTo(
                new ShortLinkPageHttpResponse<>(
                        List.of(new ShortLinkHttpResponse(
                                42L,
                                7L,
                                11L,
                                12L,
                                "ACTIVE",
                                "launch",
                                "https://lnk.forge/launch",
                                "https://example.com/source",
                                "launch note",
                                true,
                                Instant.parse("2026-04-01T01:02:03Z"),
                                null,
                                302,
                                true,
                                "https://example.com/unavailable",
                                "ALLOWLIST",
                                List.of("utm_*"),
                                List.of("marketing"),
                                Instant.parse("2026-03-18T09:10:11Z")
                        )),
                        11L,
                        2,
                        5
                )
        );
    }

    @Test
    void parseImportRows_shouldTokenizeCsvIntoApplicationRows() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "links.csv",
                "text/csv",
                """
                originalUrl,code,expiresAt,note,tags
                https://example.com/1,code-1,2026-03-10T12:00:00Z,launch,"marketing,spring"
                https://example.com/2,,,,
                """.getBytes()
        );

        assertThat(csvHttpMapper.parse(file)).containsExactly(
                new ShortLinkCsvImportRow(
                        1L,
                        "https://example.com/1",
                        "code-1",
                        "2026-03-10T12:00:00Z",
                        "launch",
                        "marketing,spring"
                ),
                new ShortLinkCsvImportRow(
                        2L,
                        "https://example.com/2",
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    void parseImportRows_shouldPreserveScopeColumnsForRoundTrip() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "links.csv",
                "text/csv",
                """
                applicationId,domainId,hostname,originalUrl,code,expiresAt,note,tags
                2001,3001,go.example.test,https://example.com/1,code-1,2026-03-10T12:00:00Z,launch,"marketing,spring"
                """.getBytes()
        );

        assertThat(csvHttpMapper.parse(file)).containsExactly(
                new ShortLinkCsvImportRow(
                        1L,
                        "2001",
                        "3001",
                        "go.example.test",
                        "https://example.com/1",
                        "code-1",
                        "2026-03-10T12:00:00Z",
                        "launch",
                        "marketing,spring"
                )
        );
    }

    @Test
    void parseImportRows_shouldPreserveMalformedRowTokensForApplicationValidation() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "links.csv",
                "text/csv",
                """
                originalUrl,code,expiresAt,note,tags
                https://example.com/1,code-1,not-a-date,launch,"marketing,spring"
                ,code-2,2026-03-10T12:00:00Z,missing-url,
                """.getBytes()
        );

        assertThat(csvHttpMapper.parse(file)).containsExactly(
                new ShortLinkCsvImportRow(
                        1L,
                        "https://example.com/1",
                        "code-1",
                        "not-a-date",
                        "launch",
                        "marketing,spring"
                ),
                new ShortLinkCsvImportRow(
                        2L,
                        null,
                        "code-2",
                        "2026-03-10T12:00:00Z",
                        "missing-url",
                        null
                )
        );
    }

    @Test
    void writeExport_shouldEncodeCsvResponseHeadersAndRows() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ShortLinkCsvExport export = new ShortLinkCsvExport(List.of(
                new ShortLinkCsvExportRow(
                        42L,
                        2001L,
                        3001L,
                        "go.example.test",
                        "launch",
                        "https://example.com/source",
                        "launch note",
                        true,
                        Instant.parse("2026-03-18T09:10:11Z"),
                        List.of("marketing", "spring")
                )
        ));

        csvHttpMapper.write(export, response);

        assertThat(response.getHeader("Content-Type")).isEqualTo("text/csv; charset=utf-8");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"links.csv\"");
        assertThat(response.getContentAsString()).contains("id,applicationId,domainId,hostname,code,originalUrl,note,enabled,expiresAt,tags");
        assertThat(response.getContentAsString()).contains("42,2001,3001,go.example.test,launch,https://example.com/source,launch note,true,2026-03-18T09:10:11Z,\"marketing,spring\"");
    }
}
