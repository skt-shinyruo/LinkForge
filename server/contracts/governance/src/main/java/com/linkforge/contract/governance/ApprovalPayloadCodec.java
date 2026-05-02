package com.linkforge.contract.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class ApprovalPayloadCodec {

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private ApprovalPayloadCodec() {
    }

    public static String write(Object payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("approval payload serialization failed", ex);
        }
    }

    public static <T> T read(String payload, Class<T> payloadType) {
        try {
            return OBJECT_MAPPER.readValue(payload, payloadType);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("approval payload deserialization failed", ex);
        }
    }
}
