package com.linkforge.shortlink.application.port;

import com.linkforge.contract.shortlink.ShortLinkReadPort;

import java.util.List;

/**
 * 在发布的跨上下文读取契约上补充短链内部的 scope ID 查询。
 */
public interface ShortLinkReadRepository extends ShortLinkReadPort {

    /**
     * 列出租户内归属于指定应用的全部短链 ID，包括已归档记录。
     *
     * @return 非 {@code null} 的 ID 列表；无结果时返回空列表
     */
    List<Long> listLinkIdsByApplication(long tenantId, long applicationId);

    /**
     * 列出租户内归属于指定域名的全部短链 ID，包括已归档记录。
     *
     * @return 非 {@code null} 的 ID 列表；无结果时返回空列表
     */
    List<Long> listLinkIdsByDomain(long tenantId, long domainId);
}
