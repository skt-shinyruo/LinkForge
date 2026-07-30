package com.linkforge.shortlink.application.port;

/**
 * 将短链变更同步为 Redirect 缓存驱逐的副作用端口。
 *
 * <p>驱逐可能同时涉及历史纯短码键和按 host 隔离的键。实现必须允许重复调用：删除不存在的键应视为成功。
 * 任何无法完成的底层操作应抛出运行时异常，使 durable outbox 保留或重新调度该意图，而不是静默返回。</p>
 *
 * <p>应用事务提交后会调用本端口作为 best-effort 快路径，异常会被调用方吞掉以免把已经提交的业务请求
 * 伪装成失败；最终重试责任属于缓存失效 outbox worker。</p>
 */
public interface RedirectCacheSyncPort {

    /**
     * 幂等删除指定短链 scope 对应的所有已知跳转缓存键。
     *
     * @param tenantId 短链所属租户
     * @param domainId 域名 ID；{@code null} 表示历史无域名 scope
     * @param code 待驱逐的短码
     * @throws RuntimeException 本次未能完成全部必要驱逐时抛出，供 outbox 重试
     */
    void evict(long tenantId, Long domainId, String code);
}
