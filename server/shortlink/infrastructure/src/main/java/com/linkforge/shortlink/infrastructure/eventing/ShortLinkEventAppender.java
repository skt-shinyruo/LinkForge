package com.linkforge.shortlink.infrastructure.eventing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.contract.shortlink.ShortLinkEventTypes;
import com.linkforge.contract.shortlink.event.ShortLinkArchivedV1;
import com.linkforge.contract.shortlink.event.ShortLinkCreatedV1;
import com.linkforge.contract.shortlink.event.ShortLinkDeletedV1;
import com.linkforge.contract.shortlink.event.ShortLinkRestoredV1;
import com.linkforge.contract.shortlink.event.ShortLinkUpdatedV1;
import com.linkforge.foundation.eventing.IntegrationEventStore;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ShortLinkEventAppender {

    private static final String PRODUCER = "shortlink";
    private static final String AGGREGATE_TYPE = "shortlink";

    private final IntegrationEventStore store;
    private final ObjectMapper objectMapper;
    private final ShortLinkEventFactory factory;

    public ShortLinkEventAppender(
            IntegrationEventStore store,
            ObjectMapper objectMapper,
            ShortLinkEventFactory factory
    ) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.factory = factory;
    }

    public void appendCreated(ShortLinkEntity e, Instant occurredAtUtc) {
        String eventId = UUID.randomUUID().toString();
        ShortLinkCreatedV1 event = factory.created(e, occurredAtUtc, eventId);
        append(e, occurredAtUtc, eventId, ShortLinkEventTypes.SHORT_LINK_CREATED_V1, event);
    }

    public void appendUpdated(ShortLinkEntity e, Instant occurredAtUtc) {
        String eventId = UUID.randomUUID().toString();
        ShortLinkUpdatedV1 event = factory.updated(e, occurredAtUtc, eventId);
        append(e, occurredAtUtc, eventId, ShortLinkEventTypes.SHORT_LINK_UPDATED_V1, event);
    }

    public void appendArchived(ShortLinkEntity e, Instant occurredAtUtc) {
        String eventId = UUID.randomUUID().toString();
        ShortLinkArchivedV1 event = factory.archived(e, occurredAtUtc, eventId);
        append(e, occurredAtUtc, eventId, ShortLinkEventTypes.SHORT_LINK_ARCHIVED_V1, event);
    }

    public void appendRestored(ShortLinkEntity e, Instant occurredAtUtc) {
        String eventId = UUID.randomUUID().toString();
        ShortLinkRestoredV1 event = factory.restored(e, occurredAtUtc, eventId);
        append(e, occurredAtUtc, eventId, ShortLinkEventTypes.SHORT_LINK_RESTORED_V1, event);
    }

    public void appendDeleted(ShortLinkEntity e, Instant occurredAtUtc) {
        String eventId = UUID.randomUUID().toString();
        ShortLinkDeletedV1 event = factory.deleted(e, occurredAtUtc, eventId);
        append(e, occurredAtUtc, eventId, ShortLinkEventTypes.SHORT_LINK_DELETED_V1, event);
    }

    private void append(ShortLinkEntity e, Instant occurredAtUtc, String eventId, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize integration event payload: " + eventType, ex);
        }

        store.append(
                eventId,
                PRODUCER,
                eventType,
                e.getTenantId(),
                AGGREGATE_TYPE,
                e.getId(),
                occurredAtUtc,
                json
        );
    }
}

