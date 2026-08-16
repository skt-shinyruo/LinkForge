package com.linkforge.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linkforge.contract.governance.ApprovalPayloadCodec;
import com.linkforge.contract.governance.ApprovalPayloadTypes;
import com.linkforge.contract.governance.LinkDestinationChangeApprovalPayload;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;
import com.linkforge.contract.shortlink.event.ShortLinkCreatedV1;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 锁定专题文档承诺的跨上下文 wire shape，避免重构时静默改变历史事件或审批记录。
 */
class PublishedContractWireTest {

    private static final JsonMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void shortLinkCreatedV1_shouldKeepPublishedJsonFieldNamesAndUtcInstants() throws Exception {
        ShortLinkPublicSnapshot snapshot = new ShortLinkPublicSnapshot(
                11L,
                22L,
                "AbC123",
                "go.example.test",
                "https://example.test/destination",
                true,
                Instant.parse("2026-07-27T08:30:00Z"),
                302,
                true,
                null,
                "ALLOWLIST",
                List.of("utm_source", "utm_campaign"),
                null,
                33L,
                44L
        );
        ShortLinkCreatedV1 event = new ShortLinkCreatedV1(
                "evt-123",
                Instant.parse("2026-07-27T08:00:00Z"),
                11L,
                22L,
                "AbC123",
                snapshot
        );

        JsonNode payload = JSON.readTree(JSON.writeValueAsString(event));

        assertThat(payload.fieldNames()).toIterable().containsExactly(
                "eventId", "occurredAtUtc", "tenantId", "linkId", "code", "snapshot"
        );
        assertThat(payload.path("eventId").asText()).isEqualTo("evt-123");
        assertThat(payload.path("occurredAtUtc").asText()).isEqualTo("2026-07-27T08:00:00Z");
        assertThat(payload.path("snapshot").path("expiresAtUtc").asText()).isEqualTo("2026-07-27T08:30:00Z");
        assertThat(payload.path("snapshot").path("queryForwardAllowlist"))
                .extracting(JsonNode::asText)
                .containsExactly("utm_source", "utm_campaign");
        assertThat(payload.path("snapshot").path("applicationId").asLong()).isEqualTo(33L);
        assertThat(payload.path("snapshot").path("domainId").asLong()).isEqualTo(44L);
    }

    @Test
    void approvalPayloadCodec_shouldRoundTripV1AndRejectUnknownFields() {
        LinkDestinationChangeApprovalPayload expected = LinkDestinationChangeApprovalPayload.v1(
                22L,
                "https://example.test/next"
        );

        String raw = ApprovalPayloadCodec.write(expected);
        LinkDestinationChangeApprovalPayload restored = ApprovalPayloadCodec.read(
                raw,
                LinkDestinationChangeApprovalPayload.class
        );

        assertThat(restored).isEqualTo(expected);
        assertThat(ApprovalPayloadCodec.read(raw, LinkDestinationChangeApprovalPayload.class).type())
                .isEqualTo(ApprovalPayloadTypes.LINK_DESTINATION_CHANGE);
        assertThatThrownBy(() -> ApprovalPayloadCodec.read(
                "{\"type\":\"linkDestinationChange\",\"version\":1,\"linkId\":22,"
                        + "\"originalUrl\":\"https://example.test/next\",\"unexpected\":true}",
                LinkDestinationChangeApprovalPayload.class
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linkCacheLookupResult_shouldPreserveHitNegativeAndMissSemantics() {
        LinkMeta meta = new LinkMeta(
                22L,
                11L,
                "AbC123",
                "https://example.test/destination",
                true,
                null,
                302,
                false,
                null,
                "OFF",
                null,
                "go.example.test",
                null,
                null,
                LinkMeta.ACTIVE_LIFECYCLE_STATE
        );

        LinkCachePort.LookupResult hit = LinkCachePort.LookupResult.hit(meta);
        LinkCachePort.LookupResult negative = LinkCachePort.LookupResult.negativeHit();
        LinkCachePort.LookupResult miss = LinkCachePort.LookupResult.miss();

        assertThat(hit.hit()).isTrue();
        assertThat(hit.notFound()).isFalse();
        assertThat(hit.meta()).isEqualTo(meta);
        assertThat(negative.hit()).isFalse();
        assertThat(negative.notFound()).isTrue();
        assertThat(negative.meta()).isNull();
        assertThat(miss.hit()).isFalse();
        assertThat(miss.notFound()).isFalse();
        assertThat(miss.meta()).isNull();
    }
}
