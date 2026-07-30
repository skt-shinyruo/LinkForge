package com.linkforge.shortlink.application.port;

/**
 * 持久化跳转缓存失效意图的事务 outbox 端口。
 *
 * <p>调用必须与对应短链写入处于同一数据库事务，使业务状态和缓存失效意图要么同时提交，要么同时回滚。
 * 实现不得把写入失败静默降级；只有 durable outbox 成功落库后，提交后的低延迟缓存驱逐才可视为附加快路径。
 * 后台处理采用至少一次语义，因此同一 scope 的重复入队和重复处理必须能够安全收敛。</p>
 */
public interface RedirectCacheInvalidationOutboxPort {

    /**
     * 在当前事务中登记一个待重试的缓存失效意图。
     *
     * @param tenantId 短链所属租户，必须为正数
     * @param domainId 域名 ID；{@code null} 表示历史无域名 scope
     * @param code 非空短码
     * @throws RuntimeException 意图无法持久化时抛出，调用方应让业务事务回滚
     */
    void enqueue(long tenantId, Long domainId, String code);
}
