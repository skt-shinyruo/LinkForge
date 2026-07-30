package com.linkforge.shortlink.application;

import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;

/**
 * 短链管理查询用例边界。
 *
 * <p>带主体的入口会收敛数据范围：普通用户只能看到自己创建的无应用短链，API Key 只能看到其绑定应用，
 * 且未绑定应用的历史 API Key 直接拒绝。对普通用户详情查询，无权限与不存在均返回
 * {@code LINK_NOT_FOUND} 以防止资源枚举。接受原始 {@code tenantId} 的查询不执行最终用户授权，
 * 仅供已经建立可信租户边界的内部调用方使用。</p>
 */
public interface ShortLinkQueryUseCase {

    /**
     * 按用户角色和所有权浏览短链。
     *
     * @param actor 已认证用户主体
     * @param request 筛选及分页条件
     * @return 用户可见范围内的一页结果
     */
    PageResult<LinkDto> browseForUser(UserActor actor, BrowseLinksRequest request);

    /**
     * 在 API Key 绑定应用内浏览短链。
     *
     * @param actor 已认证且必须绑定应用的 API Key 主体
     * @param request 筛选及分页条件
     * @return 绑定应用内的一页结果
     */
    PageResult<LinkDto> browseForApiKey(ApiKeyActor actor, BrowseLinksRequest request);

    /**
     * 使用可信租户和已构造查询条件执行搜索，不附加主体权限条件。
     *
     * @param tenantId 已授权租户
     * @param query 搜索条件
     * @param pageQuery 分页条件
     * @return 一页搜索结果
     */
    PageResult<LinkDto> search(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery);

    /**
     * 按可信租户读取详情，不执行最终用户所有权检查。
     *
     * @param tenantId 已授权租户
     * @param linkId 短链 ID
     * @return 短链详情
     */
    LinkDto detail(long tenantId, long linkId);

    /**
     * 按用户角色与所有权读取详情；无权限时按不存在处理。
     *
     * @param actor 已认证用户主体
     * @param linkId 短链 ID
     * @return 用户可见的短链详情
     */
    LinkDto detailForUser(UserActor actor, long linkId);
}
