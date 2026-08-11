package com.linkforge.shortlink.infrastructure.persistence.mapper;

import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 短链读侧与搜索统计 SQL 映射。
 *
 * <p>租户控制面查询必须显式携带 {@code tenantId}；按域名或未分域 code 的方法只供仓储按既定 scope
 * 规则组合。方法名中的 {@code Active} 仅表示排除已归档短链（域名查询还要求域名状态为 ACTIVE），
 * 不检查 enabled、expiresAt 或短链 lifecycle，最终可跳转性由 Redirect 上下文判断。</p>
 *
 * <p>搜索总数和列表复用同一个动态过滤片段；创建时间统计采用 UTC 半开区间，防止相邻窗口重复计数。</p>
 */
@Mapper
public interface ShortLinkQueryMapper {

    ShortLinkEntity findByTenantIdAndId(long tenantId, long id);

    ShortLinkEntity findUnscopedByCode(String code);

    ShortLinkEntity findByDomainIdAndCode(@Param("domainId") long domainId, @Param("code") String code);

    ShortLinkEntity findActiveByCode(String code);

    ShortLinkEntity findActiveUnscopedByCode(String code);

    ShortLinkEntity findActiveByHostnameAndCode(@Param("hostname") String hostname, @Param("code") String code);

    ShortLinkEntity findActiveByLegacyBaseHostAndCode(@Param("baseHost") String baseHost, @Param("code") String code);

    List<ShortLinkEntity> listByTenantIdAndIds(@Param("tenantId") long tenantId, @Param("ids") List<Long> ids);

    List<Long> listIdsByTenantIdAndApplicationId(@Param("tenantId") long tenantId, @Param("applicationId") long applicationId);

    List<Long> listIdsByTenantIdAndDomainId(@Param("tenantId") long tenantId, @Param("domainId") long domainId);

    long countCreatedByTenantIdAndApplicationIdAndCreatedAtRange(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("fromInclusiveUtc") LocalDateTime fromInclusiveUtc,
            @Param("toExclusiveUtc") LocalDateTime toExclusiveUtc
    );

    long countSearch(ShortLinkSearchParam param);

    List<ShortLinkEntity> listSearch(ShortLinkSearchParam param);

    List<ShortLinkEntity> listSearchAfter(ShortLinkSearchParam param);
}
