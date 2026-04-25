package com.linkforge.shortlink.infrastructure.persistence.mapper;

import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

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
}
