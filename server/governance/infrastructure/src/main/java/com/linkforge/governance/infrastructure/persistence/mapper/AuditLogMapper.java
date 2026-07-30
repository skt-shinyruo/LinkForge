package com.linkforge.governance.infrastructure.persistence.mapper;

import com.linkforge.governance.infrastructure.persistence.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 追加式审计日志的 SQL 映射边界。
 *
 * <p>映射不提供 update/delete，避免通过常规仓储路径修改既有审计证据。</p>
 */
@Mapper
public interface AuditLogMapper {

    /** 插入一条审计记录；返回受影响行数，约束或数据库错误直接上抛。 */
    int insert(AuditLogEntity entity);

    /** 返回租户全部审计记录，按创建时间和 ID 倒序；当前接口不分页。 */
    List<AuditLogEntity> listByTenantId(@Param("tenantId") long tenantId);
}
