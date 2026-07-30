package com.linkforge.shortlink.domain.event;

/**
 * 短链聚合内部产生的最小领域事件信号。
 *
 * <p>公共字段是在业务操作发生时捕获的路由身份快照，用于让应用层选择发布主题和缓存范围；它不是跨上下文的
 * wire contract，也不包含完整短链状态。应用层可使用事件时间和聚合最终状态组装公开事件，再通过事务 outbox 保证
 * 持久化与异步投递。对象出现在聚合缓冲区中不代表已经发布。</p>
 *
 * <p>同一聚合的事件由聚合按操作发生顺序记录，并由 {@code ShortLink.pullDomainEvents()} 以该顺序取出。接口不承诺
 * 不同聚合之间的全局顺序。</p>
 */
public interface ShortLinkDomainEvent {

    /** 事件所属短链 ID。 */
    long linkId();

    /** 事件所属租户 ID。 */
    long tenantId();

    /** 事件发生时的可选域名 ID；未绑定自定义域名时为空。 */
    Long domainId();

    /** 事件发生时的短码文本；短码在聚合生命周期内不可变。 */
    String code();
}
