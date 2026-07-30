package com.linkforge.accounts.application.port;

import java.time.Duration;

/**
 * 租户与用户认证状态的尽力而为缓存端口。
 *
 * <p>缓存保存短 TTL 的认证快照以降低持久层压力，不是账户状态的事实源；命中值仍会在有效期内
 * 直接参与请求判定，因此状态吊销允许存在有界的陈旧窗口。读取对调用方只暴露命中与
 * {@code null} 两类结果，其中 {@code null} 合并表示未命中、过期、载荷无法解析或缓存不可用，
 * 调用方必须回源持久化存储。实现应吸收缓存故障，写入或淘汰失败不得改变数据库事务的结果。</p>
 */
public interface AccountStatusCache {

    /**
     * 用户认证判定所需的最小状态快照。
     *
     * @param tenantId 用户当前所属租户；调用方会校验它与令牌中的租户一致
     * @param status 缓存中的账户状态；调用方只接受 active，未知状态按禁用处理
     * @param tokenVersion 令牌版本；修改密码等吊销操作通过递增该值使旧令牌失效
     */
    record UserAuthState(long tenantId, String status, int tokenVersion) {
    }

    /**
     * 读取租户状态。
     *
     * @return 缓存的状态；未命中、载荷无效或缓存不可用时返回 {@code null}
     */
    String readTenantStatus(long tenantId);

    /**
     * 读取用户认证状态快照。
     *
     * @return 可用于校验的快照；未命中、载荷无效或缓存不可用时返回 {@code null}
     */
    UserAuthState readUserAuthState(long userId);

    /**
     * 以给定 TTL 写入租户状态。调用可能重复执行，实现须允许同值覆盖且不应抛出缓存故障。
     *
     * @param ttl 正数有效期；非正值不应产生可长期存活的缓存项
     */
    void writeTenantStatus(long tenantId, String status, Duration ttl);

    /**
     * 以给定 TTL 写入用户认证快照。调用可能重复执行，实现须允许同值覆盖且不应抛出缓存故障。
     *
     * @param ttl 正数有效期；非正值不应产生可长期存活的缓存项
     */
    void writeUserAuthState(long userId, long tenantId, String status, int tokenVersion, Duration ttl);

    /**
     * 淘汰租户状态。操作应幂等；键不存在或缓存不可用均不影响业务事务。
     */
    void evictTenantStatus(long tenantId);

    /**
     * 淘汰用户认证快照。操作应幂等；键不存在或缓存不可用均不影响业务事务。
     */
    void evictUserStatus(long userId);
}
