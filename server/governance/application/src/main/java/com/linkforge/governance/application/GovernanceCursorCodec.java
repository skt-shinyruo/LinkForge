package com.linkforge.governance.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/** 审批与审计共用的版本化、不透明 keyset cursor 编解码器。 */
final class GovernanceCursorCodec {

    private static final String PREFIX = "v1.";

    private GovernanceCursorCodec() {
    }

    static String encode(LocalDateTime createdAtUtc, long id) {
        String payload = createdAtUtc + "|" + id;
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    static Cursor decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String normalized = raw.trim();
            if (!normalized.startsWith(PREFIX)) {
                throw new IllegalArgumentException("unsupported cursor version");
            }
            String payload = new String(
                    Base64.getUrlDecoder().decode(normalized.substring(PREFIX.length())),
                    StandardCharsets.UTF_8
            );
            int separator = payload.lastIndexOf('|');
            if (separator <= 0 || separator == payload.length() - 1) {
                throw new IllegalArgumentException("invalid cursor payload");
            }
            LocalDateTime createdAtUtc = LocalDateTime.parse(payload.substring(0, separator));
            long id = Long.parseLong(payload.substring(separator + 1));
            if (id <= 0L) {
                throw new IllegalArgumentException("invalid cursor id");
            }
            return new Cursor(createdAtUtc, id);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分页游标无效");
        }
    }

    record Cursor(LocalDateTime createdAtUtc, long id) {
    }
}
