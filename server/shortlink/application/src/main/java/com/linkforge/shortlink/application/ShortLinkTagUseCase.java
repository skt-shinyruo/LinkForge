package com.linkforge.shortlink.application;

import java.util.List;

/**
 * 租户级标签管理用例边界。
 *
 * <p>方法只接收租户 ID，不执行主体授权；接口层必须先建立可信租户边界并校验相应角色。
 * 标签创建的唯一性和事务由命令处理器负责，列表查询为只读操作。</p>
 */
public interface ShortLinkTagUseCase {

    /**
     * 列出指定租户的标签。
     *
     * @param tenantId 已授权租户
     * @return 标签列表
     */
    List<TagDto> listTags(long tenantId);

    /**
     * 在指定租户内创建标签。
     *
     * @param tenantId 已授权租户
     * @param name 标签名称
     * @return 创建后的标签
     */
    TagDto createTag(long tenantId, String name);
}
