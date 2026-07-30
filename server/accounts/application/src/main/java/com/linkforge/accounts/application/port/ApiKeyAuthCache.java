package com.linkforge.accounts.application.port;

/**
 * API Key 认证负缓存与最近使用时间节流令牌端口。
 *
 * <p>当前生产者只写 {@code disabled}，认证链路会把任何已解析的非 {@code active} 状态短路为禁用；
 * 即使读到 {@code active} 或摘要，仍须回源数据库校验状态、应用绑定和 secret。读取返回
 * {@code null} 同时表示未命中、过期、坏载荷或缓存不可用，不能解释为凭证有效。实现应吸收缓存
 * 故障，缓存始终不参与数据库事务的成败。</p>
 */
public interface ApiKeyAuthCache {

    /**
     * 读取兼容格式的缓存项。
     *
     * @return 已解析项；未命中、载荷无效或缓存不可用时返回 {@code null}
     */
    Entry read(long apiKeyId);

    /**
     * 写入短期禁用负缓存。若调用源自状态变更，调用方负责在事务提交后执行；重复写入同一状态应安全。
     *
     * @param applicationId 允许为 {@code null}，用于兼容历史未绑定记录
     * @param ttlSeconds 正数有效期；非正值不应写入
     */
    void putDisabled(long apiKeyId, long tenantId, Long applicationId, long ttlSeconds);

    /**
     * 淘汰认证负缓存。操作应幂等；键不存在或缓存不可用不得影响业务事务。
     */
    void evict(long apiKeyId);

    /**
     * 尝试获取一次 {@code lastUsedAt} 写库资格，通常以带 TTL 的原子 set-if-absent 实现。
     *
     * <p>该令牌只减少审计字段写放大，不是分布式业务锁，也不影响认证结果。</p>
     *
     * @param intervalSeconds 节流窗口秒数；非正值视为不获取
     * @return 明确区分获取成功、窗口内已存在和缓存不可用的三态结果
     */
    LastUsedTokenResult tryAcquireLastUsedToken(long apiKeyId, long intervalSeconds);

    /**
     * 在持有令牌后的数据库写入失败时释放令牌，使后续请求可重试。操作应幂等且尽力而为。
     */
    void releaseLastUsedToken(long apiKeyId);

    /**
     * 缓存载荷的兼容视图。
     *
     * @param tenantId API Key 所属租户
     * @param applicationId 绑定应用；旧版载荷或历史记录可为 {@code null}
     * @param status 缓存时的状态；任何非 {@code active} 值都会形成禁用结论
     * @param secretDigest 兼容旧载荷保留的摘要字段，负缓存中可为空；绝不能包含明文 secret
     */
    record Entry(long tenantId, Long applicationId, String status, String secretDigest) {
    }

    /**
     * 最近使用时间节流令牌的三态结果，避免把 Redis 故障误判为“窗口内已有写入者”。
     */
    enum LastUsedTokenResult {
        /** 已获得本窗口的写库资格。 */
        ACQUIRED,
        /** 窗口内已有令牌，或节流窗口未启用。 */
        NOT_ACQUIRED,
        /** 缓存不可用；调用方可根据数据库时间提示执行降级节流。 */
        CACHE_UNAVAILABLE
    }
}
