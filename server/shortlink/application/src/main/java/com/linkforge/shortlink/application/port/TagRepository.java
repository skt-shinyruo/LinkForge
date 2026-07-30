package com.linkforge.shortlink.application.port;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户标签的持久化端口。
 *
 * <p>标签名称的唯一性作用域是租户。查询单条记录沿用可空返回值约定；集合查询不得返回 {@code null}。
 * 插入时的唯一键或其他数据完整性异常必须向上抛出，供应用层处理并发创建竞争。</p>
 */
public interface TagRepository {

    /**
     * 按租户和规范化名称查找标签。
     *
     * @return 找到的标签；参数非法或记录不存在时返回 {@code null}
     */
    Tag findByTenantIdAndName(long tenantId, String name);

    /**
     * 插入标签并返回受影响行数；不会把唯一性冲突转换为成功。
     *
     * @return 成功插入通常为 {@code 1}；空输入可以返回 {@code 0}
     */
    int insert(Tag tag);

    /**
     * 按创建时间倒序列出租户全部标签。
     *
     * @return 非 {@code null} 的标签列表；无结果时返回空列表
     */
    List<Tag> findAllByTenantIdOrderByCreatedAtDesc(long tenantId);

    /**
     * 标签持久化快照。
     *
     * @param id 标签 ID
     * @param tenantId 所属租户 ID
     * @param name 租户内唯一的标签名
     * @param createdAt 创建时间；插入前由数据库生成时可为 {@code null}
     */
    record Tag(long id, long tenantId, String name, LocalDateTime createdAt) {
    }
}
