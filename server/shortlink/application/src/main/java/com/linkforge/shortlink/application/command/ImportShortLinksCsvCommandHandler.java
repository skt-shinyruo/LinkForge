package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.security.TenantGuard;
import com.linkforge.shortlink.application.ShortLinkService.CreateLinkRequest;
import com.linkforge.shortlink.application.ShortLinkService.ImportResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ImportShortLinksCsvCommandHandler {

    private final CreateShortLinkCommandHandler createHandler;
    private final TenantGuard tenantGuard;
    private final TransactionTemplate importRowTx;

    public ImportShortLinksCsvCommandHandler(
            CreateShortLinkCommandHandler createHandler,
            TenantGuard tenantGuard,
            PlatformTransactionManager transactionManager
    ) {
        this.createHandler = createHandler;
        this.tenantGuard = tenantGuard;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.importRowTx = tx;
    }

    public ImportResult handle(long tenantId, long createdBy, InputStream inputStream) {
        tenantGuard.requireCurrentTenant(tenantId);
        if (inputStream == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV 输入流不能为空");
        }

        List<String> errors = new ArrayList<>();
        int success = 0;
        int failed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord r : parser) {
                long recordNumber = r.getRecordNumber();
                try {
                    String originalUrl = r.get("originalUrl");
                    String code = safeGet(r, "code");
                    String expiresAt = safeGet(r, "expiresAt");
                    String note = safeGet(r, "note");
                    String tags = safeGet(r, "tags");

                    LocalDateTime exp = parseDateTime(expiresAt);
                    Set<String> tagSet = splitTags(tags);
                    CreateLinkRequest req = new CreateLinkRequest(
                            originalUrl,
                            note,
                            exp,
                            null,
                            code,
                            tagSet,
                            null,
                            null,
                            null,
                            null,
                            null
                    );
                    importRowTx.executeWithoutResult(status -> createHandler.handle(tenantId, createdBy, req));
                    success++;
                } catch (Exception e) {
                    failed++;
                    errors.add("line " + recordNumber + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV 解析失败");
        }

        return new ImportResult(success, failed, errors);
    }

    private static String safeGet(CSVRecord r, String key) {
        try {
            return r.isMapped(key) ? r.get(key) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String raw) {
        String s = normalizeNullable(raw);
        if (s == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "expiresAt 格式错误（需 ISO-8601 LocalDateTime，例如 2026-03-10T12:00:00）"
            );
        }
    }

    private static Set<String> splitTags(String raw) {
        String s = normalizeNullable(raw);
        if (s == null) {
            return Set.of();
        }
        String[] parts = s.split(",");
        Set<String> out = new HashSet<>();
        for (String p : parts) {
            String n = normalizeNullable(p);
            if (n != null && !n.isBlank()) {
                out.add(n);
            }
        }
        return out;
    }

    private static String normalizeNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}

