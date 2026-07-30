package com.linkforge.shortlink.infrastructure.persistence.mapper;

import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短链命令侧 SQL 映射。
 *
 * <p>更新与物理删除都把租户、主键和聚合版本放在同一条 SQL 的条件中，影响行数为零代表不存在或
 * 乐观并发冲突；适配器不会先查后写。所有权回填只处理 application/domain scope 同时为空的旧数据，
 * 已有归属不会被覆盖。</p>
 */
@Mapper
public interface ShortLinkCommandMapper {

    int insert(ShortLinkEntity entity);

    int update(ShortLinkEntity entity);

    int deleteByTenantIdAndIdAndVersion(long tenantId, long id, long version);

    int backfillOwnershipByTenant(long tenantId, long applicationId, long domainId);
}
