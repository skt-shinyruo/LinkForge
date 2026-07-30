package com.linkforge.shortlink.application.port;

import com.linkforge.contract.shortlink.ShortLinkReadPort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 为跨上下文只读契约提供事实数据的仓储端口。
 *
 * <p>该端口返回短链当前持久化快照，但不负责判断禁用、过期、生命周期或风险状态下是否允许跳转；Redirect
 * 上下文必须基于返回视图统一作最终决策。所有 {@link Optional}、{@link Map} 和 {@link List} 返回值
 * 都必须非 {@code null}，无结果分别使用空容器表达。</p>
 */
public interface ShortLinkReadRepository {

    /**
     * 按请求 host 与短码查找用于跳转决策的权威元数据。
     *
     * <p>host 可为空以兼容没有主机信息的内部查询；实现负责 host 规范化和历史无 scope 数据回退，但不得让
     * 自定义域名跨 scope 命中其他域名的短链。空白短码返回空结果。</p>
     *
     * @return 命中的完整状态视图；不存在时返回空 {@link Optional}
     */
    Optional<ShortLinkReadPort.RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code);

    /**
     * 读取租户内短链的应用与域名归属。
     *
     * <p>查询必须同时使用 {@code tenantId} 和 {@code linkId}，并包含已归档记录；旧数据未绑定应用或域名时，
     * 归属对象中的对应字段允许为 {@code null}。</p>
     */
    Optional<ShortLinkReadPort.ShortLinkOwnership> findOwnership(long tenantId, long linkId);

    /**
     * 批量读取租户内短链摘要。
     *
     * <p>返回 Map 以 linkId 为键；不存在、重复或不属于该租户的请求 ID 不应产生占位条目。输入为
     * {@code null} 或空列表以及查询无结果时均返回空 Map。</p>
     */
    Map<Long, ShortLinkReadPort.ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds);

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
