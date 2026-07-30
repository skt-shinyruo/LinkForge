package com.linkforge.shortlink.application.query;

import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExport;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExportRow;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.OffsetPagingGuard;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按已解析的搜索 scope 组装一页短链 CSV 导出数据。
 *
 * <p>本 handler 不认证主体，也不自行推导用户或 API Key 的应用范围；调用方必须先完成租户和应用授权，
 * 并把限制写入 {@link ShortLinkSearchQuery}。仓储查询始终以 {@code tenantId} 隔离数据，随后才把本页 linkId
 * 交给没有 tenant 列的标签关联仓储，避免跨租户标签读取。</p>
 *
 * <p>导出采用普通 offset 分页，只返回请求页，不统计总数、不遍历后续页，也不提供一致性快照或流式导出。
 * offset 上限为 {@code 100000}（包含边界）；页大小由上游构造的 {@link PageQuery} 决定。这里返回结构化行，
 * CSV 表头、转义、公式注入防护和字符编码属于传输层编码器职责。</p>
 *
 * <p>标签对本页短链一次批量读取；主机名按不同 {@code domainId} 至多查询一次。历史短链的 domainId 为空、
 * 未装配主机名查询端口或 Platform 查无对应域名时，导出行保留 {@code hostname=null}，不会猜测 base host；
 * 下游查询异常则直接传播。过期时间按 UTC 从 {@link LocalDateTime} 转换为 {@link Instant}。</p>
 */
@Component
public class ExportShortLinksCsvQueryHandler {

    private static final long MAX_EXPORT_OFFSET = 100_000L;

    private final ShortLinkRepository shortLinkRepository;
    private final LinkTagRepository linkTagRepository;
    private final DomainHostnameLookupPort domainHostnameLookupPort;

    @Autowired
    public ExportShortLinksCsvQueryHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository,
            DomainHostnameLookupPort domainHostnameLookupPort
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkTagRepository = linkTagRepository;
        this.domainHostnameLookupPort = domainHostnameLookupPort;
    }

    public ExportShortLinksCsvQueryHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository
    ) {
        this(shortLinkRepository, linkTagRepository, null);
    }

    /**
     * 读取并组装指定页的导出行。
     *
     * <p>{@code query == null} 等价于“未归档、无其他过滤条件”。空结果返回含空 rows 的导出对象。
     * count 与跨页续传由调用方负责；并发写入可能使相邻页之间出现移动或重复。</p>
     *
     * @param tenantId 数据隔离租户；必须与上游完成授权的主体一致
     * @param query 已注入主体 scope 的可选过滤条件
     * @param pageQuery 非空分页参数，offset 超限时拒绝查询
     * @return 当前页的结构化 CSV 导出数据
     */
    public ShortLinkCsvExport handle(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery) {
        long offset = OffsetPagingGuard.requireOffsetWithin(pageQuery, MAX_EXPORT_OFFSET);
        ShortLinkSearchQuery effectiveQuery = query == null ? new ShortLinkSearchQuery(false, null, null, null, null) : query;
        List<ShortLink> links = shortLinkRepository.listSearch(tenantId, effectiveQuery, offset, pageQuery.size());
        Map<Long, List<String>> tags = TagMaps.loadTagsByLinkIds(linkTagRepository, links);
        Map<Long, String> hostnames = new HashMap<>();
        List<ShortLinkCsvExportRow> rows = new ArrayList<>(links.size());
        for (ShortLink link : links) {
            rows.add(new ShortLinkCsvExportRow(
                    link.id(),
                    link.applicationId(),
                    link.domainId(),
                    resolveHostname(tenantId, link.domainId(), hostnames),
                    link.code().value(),
                    link.originalUrl().value(),
                    link.note(),
                    link.enabled(),
                    toInstant(link.expiresAtUtc()),
                    tags.getOrDefault(link.id(), List.of())
            ));
        }
        return new ShortLinkCsvExport(rows);
    }

    /**
     * 解析并在当前页内缓存域名主机名；{@code null} 结果也进入缓存，避免同一缺失域名被重复查询。
     */
    private String resolveHostname(long tenantId, Long domainId, Map<Long, String> hostnames) {
        if (domainId == null || domainHostnameLookupPort == null) {
            return null;
        }
        if (hostnames.containsKey(domainId)) {
            return hostnames.get(domainId);
        }
        String hostname = domainHostnameLookupPort.findDomainHostname(tenantId, domainId).orElse(null);
        hostnames.put(domainId, hostname);
        return hostname;
    }

    private static Instant toInstant(LocalDateTime expiresAtUtc) {
        if (expiresAtUtc == null) {
            return null;
        }
        return expiresAtUtc.toInstant(ZoneOffset.UTC);
    }
}
