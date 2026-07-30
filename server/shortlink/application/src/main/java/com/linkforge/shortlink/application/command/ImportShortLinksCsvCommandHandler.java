package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.foundation.tx.RequiresNewTransactionPort;
import com.linkforge.shortlink.application.CreatedBy;
import com.linkforge.shortlink.application.CreateLinkRequest;
import com.linkforge.shortlink.application.ImportResult;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 编排短链 CSV 批量导入，并将每一行转换为独立的创建命令。
 *
 * <p>该类本身不包裹批次事务；每一行通过 {@link RequiresNewTransactionPort} 在新事务中执行，
 * 因而允许部分成功。某行失败只记录行号与错误信息，不会回滚已经提交的其他行，也不会阻止后续行继续处理。
 * 创建时的应用/域名授权、额度、事件和缓存一致性规则均委托给 {@link CreateShortLinkCommandHandler}。
 * 当调用方给出作用域 ID 时，作用域值优先于 CSV 对应列。</p>
 */
@Component
public class ImportShortLinksCsvCommandHandler {

    private final CreateShortLinkCommandHandler createHandler;
    private final RequiresNewTransactionPort requiresNewTransactionPort;
    private final DomainHostnameLookupPort domainHostnameLookupPort;

    public ImportShortLinksCsvCommandHandler(
            CreateShortLinkCommandHandler createHandler,
            RequiresNewTransactionPort requiresNewTransactionPort
    ) {
        this(createHandler, requiresNewTransactionPort, null);
    }

    @Autowired
    public ImportShortLinksCsvCommandHandler(
            CreateShortLinkCommandHandler createHandler,
            RequiresNewTransactionPort requiresNewTransactionPort,
            DomainHostnameLookupPort domainHostnameLookupPort
    ) {
        this.createHandler = createHandler;
        this.requiresNewTransactionPort = requiresNewTransactionPort;
        this.domainHostnameLookupPort = domainHostnameLookupPort;
    }

    /**
     * 按 CSV 每行自带的应用和域名信息导入短链。
     *
     * @param tenantId 当前租户；所有域名解析和创建均限制在该租户内
     * @param createdBy 创建主体，合法性由创建命令统一校验
     * @param rows 已完成语法解析的 CSV 行，不能为 {@code null}
     * @return 成功数、失败数及逐行错误；单行失败不会以异常终止整个批次
     */
    public ImportResult handle(long tenantId, CreatedBy createdBy, List<ShortLinkCsvImportRow> rows) {
        return handle(tenantId, createdBy, rows, null, null);
    }

    /**
     * 在可选应用/域名作用域内逐行导入短链。
     *
     * <p>每行使用新事务，成功行在方法返回前已经独立提交。域名 hostname 的解析结果只在本次调用内缓存；
     * 解析不到域名、字段非法或创建失败都会转换为该行的失败结果。只有批次参数本身非法（例如
     * {@code rows == null}）才会直接抛出业务异常。</p>
     *
     * @param tenantId 当前租户
     * @param createdBy 创建主体
     * @param rows CSV 行
     * @param scopedApplicationId 非空时覆盖所有行的 {@code applicationId}
     * @param scopedDomainId 非空时覆盖所有行的域名字段
     * @return 可部分成功的导入汇总
     */
    public ImportResult handle(
            long tenantId,
            CreatedBy createdBy,
            List<ShortLinkCsvImportRow> rows,
            Long scopedApplicationId,
            Long scopedDomainId
    ) {
        if (rows == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV 行不能为空");
        }

        List<String> errors = new ArrayList<>();
        int success = 0;
        int failed = 0;
        Map<String, Long> domainIdsByHostname = new HashMap<>();

        for (ShortLinkCsvImportRow row : rows) {
            try {
                Long applicationId = scopedApplicationId != null
                        ? scopedApplicationId
                        : parseOptionalId(row.applicationId(), "applicationId");
                Long domainId = scopedDomainId != null
                        ? scopedDomainId
                        : resolveRowDomainId(tenantId, row, domainIdsByHostname);
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
                        applicationId,
                        domainId,
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

    private Long resolveRowDomainId(
            long tenantId,
            ShortLinkCsvImportRow row,
            Map<String, Long> domainIdsByHostname
    ) {
        Long domainId = parseOptionalId(row.domainId(), "domainId");
        if (domainId != null) {
            return domainId;
        }

        String hostname = normalizeHostname(row.hostname());
        if (hostname == null) {
            return null;
        }
        if (domainHostnameLookupPort == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "hostname 对应域名不存在");
        }
        if (domainIdsByHostname.containsKey(hostname)) {
            return domainIdsByHostname.get(hostname);
        }

        Long resolvedDomainId = domainHostnameLookupPort.findDomainIdByHostname(tenantId, hostname)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "hostname 对应域名不存在"));
        domainIdsByHostname.put(hostname, resolvedDomainId);
        return resolvedDomainId;
    }

    private static Long parseOptionalId(String raw, String fieldName) {
        String s = normalizeNullable(raw);
        if (s == null) {
            return null;
        }
        try {
            long value = Long.parseLong(s);
            if (value <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 必须 > 0");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 格式错误");
        }
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

    private static String normalizeHostname(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
