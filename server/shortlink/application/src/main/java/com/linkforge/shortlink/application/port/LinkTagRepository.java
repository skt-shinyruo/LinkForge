package com.linkforge.shortlink.application.port;

import java.util.Collection;
import java.util.List;

/**
 * 短链与标签多对多关联的持久化端口。
 *
 * <p>关联表不携带 tenantId，调用方必须先完成短链和标签的租户归属校验，不能把本端口作为授权边界。
 * 全量替换标签时，删除旧关联与插入新关联必须由应用层包在同一事务中。</p>
 */
public interface LinkTagRepository {

    /**
     * 新增一条短链标签关联。
     *
     * @return 实际插入行数；唯一键或外键冲突应向上抛出
     */
    int insert(long linkId, long tagId);

    /**
     * 删除短链的全部标签关联。
     *
     * @return 实际删除行数；没有关联时返回 {@code 0}
     */
    int deleteAllByLinkId(long linkId);

    /**
     * 读取一条短链的全部标签名称。
     *
     * @return 非 {@code null} 的名称列表；没有关联时返回空列表
     */
    List<String> findTagNamesByLinkId(long linkId);

    /**
     * 批量读取多条短链的标签名称关联。
     *
     * <p>{@code linkIds} 为 {@code null}、空集合或只含非法 ID 时返回空列表。返回值可为同一 linkId
     * 包含多行，不为请求中没有标签的短链生成占位项。</p>
     */
    List<LinkTagName> findTagNamesByLinkIds(Collection<Long> linkIds);

    /**
     * 批量标签查询的一条扁平结果。
     *
     * @param linkId 短链 ID
     * @param tagName 对应标签名称
     */
    record LinkTagName(long linkId, String tagName) {
    }
}
