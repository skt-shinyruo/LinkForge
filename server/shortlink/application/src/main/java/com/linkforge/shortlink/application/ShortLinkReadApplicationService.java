package com.linkforge.shortlink.application;

import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.shortlink.application.port.ShortLinkReadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ShortLinkReadPort} 的权威读实现，向跳转、统计等上下文发布最小只读视图。
 *
 * <p>该服务不使用跳转缓存，也不判断短链当前是否可跳转；状态、有效期和风险等可用性决策由消费上下文
 * 完成。跳转元数据查询通过只读事务触发读写分离数据源的事务内主库路由，避免把副本延迟暴露为 404
 * 或陈旧跳转；其余控制面查询保持普通读路由。按 {@code tenantId} 查询的方法假定调用方已经建立可信的
 * 跨上下文调用边界，不提供面向最终用户的授权检查。</p>
 */
@Service
public class ShortLinkReadApplicationService implements ShortLinkReadPort {

    private final ShortLinkReadRepository shortLinkReadRepository;

    public ShortLinkReadApplicationService(ShortLinkReadRepository shortLinkReadRepository) {
        this.shortLinkReadRepository = shortLinkReadRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code) {
        return shortLinkReadRepository.findRedirectMetaByHostAndCode(host, code);
    }

    @Override
    public Optional<ShortLinkOwnership> findOwnership(long tenantId, long linkId) {
        return shortLinkReadRepository.findOwnership(tenantId, linkId);
    }

    @Override
    public Map<Long, ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds) {
        return shortLinkReadRepository.listSummaries(tenantId, linkIds);
    }
}
