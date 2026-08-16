package com.linkforge.shortlink.infrastructure.persistence.mapper;

import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短链命令侧 SQL 映射。
 *
 * <p>更新把租户、主键和聚合变化前版本放在同一条 SQL 的条件中，并保存聚合已经推进的新版本；物理删除
 * 接收显式期望版本。影响行数为零代表不存在或乐观并发冲突，适配器不会先查后写。</p>
 */
@Mapper
public interface ShortLinkCommandMapper {

    int insert(ShortLinkEntity entity);

    int update(ShortLinkEntity entity);

    int deleteByTenantIdAndIdAndVersion(long tenantId, long id, long version);
}
