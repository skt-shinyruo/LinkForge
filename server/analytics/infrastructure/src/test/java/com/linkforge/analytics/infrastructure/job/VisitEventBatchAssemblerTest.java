package com.linkforge.analytics.infrastructure.job;

import com.linkforge.foundation.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisitEventBatchAssemblerTest {

    @Test
    void assemble_should_build_rows_and_collect_ack_only_records() {
        VisitEventBatchAssembler assembler = new VisitEventBatchAssembler(new SnowflakeIdGenerator(1L, 1L));

        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> valid = mock(MapRecord.class);
        when(valid.getId()).thenReturn(RecordId.of("1-0"));
        when(valid.getValue()).thenReturn((Map) Map.of(
                "tenantId", "1",
                "linkId", "10",
                "requestId", "req-ok",
                "ts", "1710000000000",
                "uaRaw", "ua"
        ));

        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> invalid = mock(MapRecord.class);
        when(invalid.getId()).thenReturn(RecordId.of("2-0"));
        when(invalid.getValue()).thenReturn((Map) Map.of(
                "tenantId", "-1",
                "linkId", "0",
                "requestId", "req-bad",
                "ts", "1710000000000"
        ));

        VisitEventBatchAssembler.Batch batch = assembler.assemble(List.of(valid, invalid));

        assertThat(batch.items()).hasSize(1);
        assertThat(batch.ackAlways()).containsExactly(RecordId.of("2-0"));

        VisitEventBatchAssembler.IngestItem item = batch.items().get(0);
        assertThat(item.recordId()).isEqualTo(RecordId.of("1-0"));
        assertThat(item.row().getTenantId()).isEqualTo(1L);
        assertThat(item.row().getLinkId()).isEqualTo(10L);
        assertThat(item.row().getRequestId()).isEqualTo("req-ok");
        assertThat(item.row().getOccurredAt()).isEqualTo(LocalDateTime.of(2024, 3, 9, 16, 0));
    }
}
