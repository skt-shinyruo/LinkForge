package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.StandardRoles;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.domain.CreatedByType;
import com.linkforge.shortlink.domain.ShortLink;

/**
 * 用户主体访问短链时的统一数据范围策略。
 *
 * <p>租户管理员可访问本租户全部短链。非管理员只可访问自己以用户身份创建且
 * {@code applicationId == null} 的短链；绑定应用的短链始终属于管理作用域。详情检查对跨租户、资源不存在和
 * 无权限三种情况统一使用 {@code LINK_NOT_FOUND}，避免暴露资源是否存在。</p>
 */
public final class ShortLinkUserAccess {

    private ShortLinkUserAccess() {
    }

    /**
     * 为用户浏览查询附加所有者约束。
     *
     * @param actor 已认证用户主体
     * @param query 已完成应用范围解析的查询
     * @return 管理员原查询，或限制为当前用户创建的无应用短链的查询
     */
    public static ShortLinkSearchQuery scopeBrowse(UserActor actor, ShortLinkSearchQuery query) {
        if (isTenantAdmin(actor)) {
            return query;
        }
        return new ShortLinkSearchQuery(
                query.archived(),
                query.enabled(),
                query.keyword(),
                query.tag(),
                query.applicationId(),
                actor.userId(),
                CreatedByType.USER,
                true
        );
    }

    /**
     * 校验用户是否可读取或修改指定聚合。
     *
     * @param actor 已认证用户主体
     * @param link 当前租户内加载的短链聚合
     * @throws BusinessException 主体、租户或所有权不匹配时以 {@code LINK_NOT_FOUND} 拒绝
     */
    public static void requireCanAccess(UserActor actor, ShortLink link) {
        if (actor == null || link == null || actor.tenantId() != link.tenantId()) {
            throw new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND);
        }
        if (isTenantAdmin(actor)) {
            return;
        }
        if (link.applicationId() == null
                && link.createdByType() == CreatedByType.USER
                && link.createdBy() == actor.userId()) {
            return;
        }
        throw new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND);
    }

    /**
     * 判断主体是否携带租户管理员角色；空主体或空角色集合均返回 {@code false}。
     *
     * @param actor 用户主体
     * @return 是否为租户管理员
     */
    public static boolean isTenantAdmin(UserActor actor) {
        return actor != null
                && actor.roles() != null
                && actor.roles().contains(StandardRoles.TENANT_ADMIN);
    }
}
