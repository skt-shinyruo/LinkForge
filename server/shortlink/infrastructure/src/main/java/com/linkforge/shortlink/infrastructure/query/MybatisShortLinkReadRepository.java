package com.linkforge.shortlink.infrastructure.query;

import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.shortlink.application.port.ShortLinkReadRepository;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 短链跨上下文只读契约的 MyBatis 实现。
 *
 * <p>该适配器集中处理 redirect 查询的 host scope、旧数据兼容回退，以及控制面批量摘要所需的
 * URL 拼装。它只读取事实数据，不判断过期、禁用、生命周期等最终可跳转性；这些状态会完整映射到
 * {@link ShortLinkReadPort.RedirectLinkView}，由 redirect 上下文统一决策。</p>
 */
@Repository
public class MybatisShortLinkReadRepository implements ShortLinkReadRepository {

    private final ShortLinkQueryMapper queryMapper;
    private final CoreProperties coreProperties;

    public MybatisShortLinkReadRepository(ShortLinkQueryMapper queryMapper, CoreProperties coreProperties) {
        this.queryMapper = queryMapper;
        this.coreProperties = coreProperties;
    }

    /**
     * 按 host 与 code 查询跳转元数据。
     *
     * <p>host 缺失时按 code 查询未归档记录，供不具备 Host 信息的内部调用兼容使用。host 存在时
     * 的查找顺序固定为：</p>
     * <ol>
     *     <li>匹配处于 {@code ACTIVE} 状态的自定义域名与 code；</li>
     *     <li>仅当 host 等于配置的基础域名时，匹配 {@code legacy-{tenantId}.{baseHost}} 旧域名；</li>
     *     <li>仍仅在基础域名上，回退到 {@code domain_id IS NULL} 的旧短链。</li>
     * </ol>
     * <p>自定义 host 绝不回退到无 scope 数据，防止不同域名间串链。就短链状态而言，SQL 只排除
     * 已归档记录；enabled、expiresAt 和 lifecycleState 等可用性条件留给 redirect 服务判定。
     * 旧记录没有 hostname 时用当前基础 host 补全返回视图，但不回写数据库。</p>
     */
    @Override
    public Optional<ShortLinkReadPort.RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code) {
        String normalizedCode = normalizeNullable(code);
        String normalizedHost = normalizeHost(host);
        if (normalizedCode == null) {
            return Optional.empty();
        }
        if (normalizedHost == null) {
            return Optional.ofNullable(toRedirectLinkMeta(queryMapper.findActiveByCode(normalizedCode)));
        }

        ShortLinkEntity row = queryMapper.findActiveByHostnameAndCode(normalizedHost, normalizedCode);
        if (row == null) {
            boolean baseHost = isBaseHost(normalizedHost);
            if (baseHost) {
                row = queryMapper.findActiveByLegacyBaseHostAndCode(normalizedHost, normalizedCode);
            }
            if (row == null && baseHost) {
                row = queryMapper.findActiveUnscopedByCode(normalizedCode);
            }
        }
        if (row == null) {
            return Optional.empty();
        }
        if (row.getHostname() == null || row.getHostname().isBlank()) {
            row.setHostname(normalizedHost);
        }
        return Optional.of(toRedirectLinkMeta(row));
    }

    /**
     * 读取租户内短链的应用与域名归属，用于控制面授权和资源范围判断。
     *
     * <p>tenantId 与 linkId 同时参与 SQL 条件，避免跨租户暴露归属；查询不按归档、启用或生命周期
     * 过滤，因为这些状态不会解除资源所有权。旧数据中的空 applicationId/domainId 会原样保留。</p>
     */
    @Override
    public Optional<ShortLinkReadPort.ShortLinkOwnership> findOwnership(long tenantId, long linkId) {
        return Optional.ofNullable(queryMapper.findByTenantIdAndId(tenantId, linkId))
                .map(row -> new ShortLinkReadPort.ShortLinkOwnership(row.getApplicationId(), row.getDomainId()));
    }

    /**
     * 批量生成租户内短链摘要。
     *
     * <p>SQL 同时按 tenantId 和 ID 集合过滤，缺失或不属于该租户的 ID 不会出现在结果中。返回 Map
     * 以 linkId 为键且不可变；自定义域名短链使用配置基础 URL 的 scheme，旧的无域名短链使用完整
     * {@code core.base-url}，二者都拼接 {@code /r/{code}}。仍存在于主表的行（包括已归档行）都不是
     * 历史删除快照，因此摘要中的 {@code deleted} 固定为 {@code false}。</p>
     */
    @Override
    public Map<Long, ShortLinkReadPort.ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ShortLinkReadPort.ShortLinkSummary> summaries = new LinkedHashMap<>();
        for (ShortLinkEntity row : safeList(queryMapper.listByTenantIdAndIds(tenantId, linkIds))) {
            if (row == null || row.getId() == null) {
                continue;
            }
            summaries.put(row.getId(), new ShortLinkReadPort.ShortLinkSummary(
                    row.getId(),
                    row.getCode(),
                    buildShortUrl(row),
                    row.getOriginalUrl(),
                    false
            ));
        }
        return Map.copyOf(summaries);
    }

    /**
     * 列出租户内属于指定应用的全部短链 ID，结果按 ID 升序且包含已归档记录。
     */
    @Override
    public List<Long> listLinkIdsByApplication(long tenantId, long applicationId) {
        return List.copyOf(safeList(queryMapper.listIdsByTenantIdAndApplicationId(tenantId, applicationId)));
    }

    /**
     * 列出租户内属于指定域名的全部短链 ID，结果按 ID 升序且包含已归档记录。
     */
    @Override
    public List<Long> listLinkIdsByDomain(long tenantId, long domainId) {
        return List.copyOf(safeList(queryMapper.listIdsByTenantIdAndDomainId(tenantId, domainId)));
    }

    private boolean isBaseHost(String host) {
        String baseHost = resolveBaseHost();
        return baseHost != null && baseHost.equalsIgnoreCase(host);
    }

    private String buildShortUrl(ShortLinkEntity row) {
        if (row == null || row.getCode() == null || row.getCode().isBlank()) {
            return null;
        }
        return appendRedirectPath(shortUrlBase(row), row.getCode());
    }

    private String shortUrlBase(ShortLinkEntity row) {
        String hostname = trimToNull(row.getHostname());
        Long domainId = row.getDomainId();
        if (domainId != null && domainId > 0L && hostname != null) {
            return schemeForDomainUrl() + "://" + hostname;
        }
        return configuredBaseUrl();
    }

    private String resolveBaseHost() {
        String baseUrl = coreProperties == null ? null : coreProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            return normalizeHost(uri.getHost());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String configuredBaseUrl() {
        String base = coreProperties == null ? null : coreProperties.getBaseUrl();
        if (base == null) {
            base = "";
        }
        return trimTrailingSlash(base);
    }

    private String schemeForDomainUrl() {
        String base = coreProperties == null ? null : coreProperties.getBaseUrl();
        if (base != null && !base.isBlank()) {
            try {
                String scheme = URI.create(base.trim()).getScheme();
                if (scheme != null && !scheme.isBlank()) {
                    return scheme.toLowerCase();
                }
            } catch (Exception ignored) {
                // 配置 URL 无法解析时使用公开域名的安全默认协议。
            }
        }
        return "https";
    }

    private static String appendRedirectPath(String base, String code) {
        return trimTrailingSlash(base) + "/r/" + code;
    }

    private static String trimTrailingSlash(String base) {
        if (base == null) {
            return "";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String normalizeNullable(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String normalized = host.trim().toLowerCase();
        if (normalized.isBlank()) {
            return null;
        }
        int colonIndex = normalized.indexOf(':');
        if (colonIndex > 0) {
            normalized = normalized.substring(0, colonIndex);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static ShortLinkReadPort.RedirectLinkView toRedirectLinkMeta(ShortLinkEntity row) {
        if (row == null) {
            return null;
        }
        return new ShortLinkReadPort.RedirectLinkView(
                row.getTenantId() == null ? 0L : row.getTenantId(),
                row.getId() == null ? 0L : row.getId(),
                row.getCode(),
                normalizeHost(row.getHostname()),
                row.getOriginalUrl(),
                Boolean.TRUE.equals(row.getEnabled()),
                toInstant(row.getExpiresAt()),
                row.getRedirectStatusCode(),
                Boolean.TRUE.equals(row.getPreviewEnabled()),
                row.getUnavailableLandingUrl(),
                row.getQueryForwardMode(),
                row.getQueryForwardAllowlist(),
                row.getApplicationId(),
                row.getDomainId(),
                row.getLifecycleState()
        );
    }

    private static Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.toInstant(ZoneOffset.UTC);
    }

    private static <T> List<T> safeList(List<T> rows) {
        return rows == null ? List.of() : rows;
    }
}
