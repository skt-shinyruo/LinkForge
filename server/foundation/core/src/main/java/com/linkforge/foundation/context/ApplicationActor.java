package com.linkforge.foundation.context;

/**
 * 进入应用服务的已认证参与者最小身份。
 *
 * <p>所有 actor 都带租户边界，但不携带 HTTP/Spring Security 对象。应用服务必须先使用该租户值约束资源查询，
 * 再根据具体 actor 类型、角色或 API Key scope 做操作授权。</p>
 */
public sealed interface ApplicationActor permits UserActor, ApiKeyActor {

    /** 返回经过认证链路确认的所属租户 ID。 */
    long tenantId();
}
