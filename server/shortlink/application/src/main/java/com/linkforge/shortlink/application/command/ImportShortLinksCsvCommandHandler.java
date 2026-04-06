package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.tx.RequiresNewTransactionPort;
import com.linkforge.shortlink.application.ShortLinkService.CreatedBy;
import com.linkforge.shortlink.application.ShortLinkService.CreateLinkRequest;
import com.linkforge.shortlink.application.ShortLinkService.ImportResult;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@Component
public class ImportShortLinksCsvCommandHandler {

    private final CreateShortLinkCommandHandler createHandler;
    private final RequiresNewTransactionPort requiresNewTransactionPort;

    public ImportShortLinksCsvCommandHandler(
            CreateShortLinkCommandHandler createHandler,
            RequiresNewTransactionPort requiresNewTransactionPort
    ) {
        this.createHandler = createHandler;
        this.requiresNewTransactionPort = requiresNewTransactionPort;
    }

    public ImportResult handle(long tenantId, CreatedBy createdBy, List<ShortLinkCsvImportRow> rows) {
        if (rows == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV 行不能为空");
        }

        List<String> errors = new ArrayList<>();
        int success = 0;
        int failed = 0;

        for (ShortLinkCsvImportRow row : rows) {
            try {
                CreateLinkRequest req = new CreateLinkRequest(
                        row.originalUrl(),
                        row.note(),
                        parseExpiresAt(row.expiresAt()),
                        null,
                        row.code(),
                        splitTags(row.tags()),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
                requiresNewTransactionPort.run(() -> createHandler.handle(tenantId, createdBy, req));
                success++;
            } catch (Exception e) {
                failed++;
                errors.add("line " + row.rowNumber() + ": " + e.getMessage());
            }
        }

        return new ImportResult(success, failed, errors);
    }

    private static Instant parseExpiresAt(String raw) {
        String normalized = normalizeNullable(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "expiresAt 格式错误（需 ISO-8601 Instant，例如 2026-03-10T12:00:00Z 或 2026-03-10T12:00:00+08:00；或 legacy LocalDateTime 例如 2026-03-10T12:00:00，按 UTC 处理）"
            );
        }
    }

    private static Set<String> splitTags(String raw) {
        String normalized = normalizeNullable(raw);
        if (normalized == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String part : normalized.split(",")) {
            String tag = normalizeNullable(part);
            if (tag != null) {
                out.add(tag);
            }
        }
        return out;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
