package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventInsertRow;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisitEventDeadLetterWriterTest {

    @Test
    void write_should_add_and_trim_dlq_stream() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(RecordId.of("0-0"));
        when(streamOps.trim(anyString(), anyLong(), eq(true))).thenReturn(1L);

        VisitEventDeadLetterWriter writer = new VisitEventDeadLetterWriter(redis);

        LinkVisitEventInsertRow row = new LinkVisitEventInsertRow();
        row.setTenantId(1L);
        row.setLinkId(10L);
        row.setRequestId("req-bad");

        writer.write("stats:visit:events", RecordId.of("2-0"), row, new DataIntegrityViolationException("row failed"));

        verify(streamOps).add(any());
        verify(streamOps).trim(eq("stats:visit:events:dlq"), eq(10_000L), eq(true));
    }
}
