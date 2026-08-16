package com.linkforge.contract.shortlink;

import com.linkforge.contract.redirect.LinkMeta;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shortlink 向 Redirect、Analytics 和 Governance 发布的只读权威契约。
 *
 * <p>这是跨上下文读取短链事实的唯一权威入口，不执行 Redirect 的最终可用性、额度或风险决策。实现遇到
 * 数据库/网络故障必须向上传播，不能折算为 {@link Optional#empty()}；空结果仅表达在查询语义中未找到、
 * 已不可见或输入无效。该端口不面向最终用户授权：携带 {@code tenantId} 的调用方必须已经建立可信的
 * 上下文边界，不能把本端口当作跨租户授权判定器。</p>
 */
public interface ShortLinkReadPort {

    /**
     * 按 host 与大小写敏感 code 查找可参与跳转决策的权威元数据。
     *
     * <p>生产实现会去除 host/code 首尾空白、将 host 转为小写并移除端口，但不会改变 code 的大小写；空 host
     * 只用于 legacy/unscoped 兼容路径，不能让自定义域名回退到其他 scope。返回视图仍可能是 disabled、已过期
     * 或非 ACTIVE lifecycle，Redirect 必须继续判定。该读取不写缓存、不记录访问，也不保证调用后短链状态不会
     * 再变化。</p>
     *
     * @param host 请求 host；{@code null} 或空白值表示 legacy/unscoped 兼容查询，不能据此推断默认域名
     * @param code 大小写敏感短码；{@code null} 或空白值返回空结果
     * @return 非 {@code null} 的权威事实视图；未找到、已归档或输入无效时为 {@link Optional#empty()}
     * @throws RuntimeException 数据源或网络失败；实现不得将此类失败伪装成空结果
     */
    Optional<LinkMeta> findRedirectMetaByHostAndCode(String host, String code);

    /**
     * 在租户边界内查询链接的应用/域名归属。
     *
     * <p>归属是资源范围事实，已归档、禁用或非 ACTIVE 生命周期不改变其可查询性；物理删除、不存在或不属于
     * {@code tenantId} 的链接返回空。legacy 链接允许两个归属字段均为 {@code null}，这不等价于查无此链接。</p>
     *
     * @param tenantId 可信调用边界传入的租户 ID
     * @param linkId 要查询的全局短链 ID
     * @return 非 {@code null} 的归属视图；租户不匹配或链接不存在时为 {@link Optional#empty()}
     * @throws RuntimeException 数据源或网络失败；实现不得将此类失败伪装成空结果
     */
    Optional<ShortLinkOwnership> findOwnership(long tenantId, long linkId);

    /**
     * 批量返回租户范围内存在的链接摘要，供控制面和报表补全使用。
     *
     * <p>输入 {@code null} 或空列表时返回空 Map。缺失、重复或不属于 {@code tenantId} 的 ID 不产生占位项；
     * 每个 key 必须等于其摘要的 {@code linkId}。摘要描述读取时仍存在的主表记录（包括归档记录），不替代
     * 删除事件携带的历史快照。</p>
     *
     * @param tenantId 可信调用边界传入的租户 ID
     * @param linkIds 待查询的短链 ID，可为 {@code null}
     * @return 非 {@code null} 的按 linkId 索引的摘要 Map；无可见结果时为空
     * @throws RuntimeException 数据源或网络失败；实现不得返回部分结果并吞掉失败
     */
    Map<Long, ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds);

    /**
     * Analytics/Governance 使用的最小归属视图。
     *
     * <p>两个字段对 legacy 链接均可为空；可空归属必须与 {@link Optional} 的查无结果区分。新应用级链路
     * 通常同时携带二者，但消费者不能把该业务规则套用到历史数据。</p>
     *
     * @param applicationId 所属应用；legacy 数据可为空
     * @param domainId 所属域名；legacy 数据可为空
     */
    record ShortLinkOwnership(Long applicationId, Long domainId) {
    }

    /**
     * 报表补全使用的链接摘要。
     *
     * <p>{@code deleted=true} 表示只保留的历史展示快照，通常来自事件投影而非 {@link #listSummaries(long, List)}；
     * {@code shortUrl} 为 {@code null} 时只表示调用方使用了旧构造器或无法生成 URL，不能当作链接不存在。</p>
     *
     * @param linkId 全局短链 ID
     * @param code 大小写敏感短码
     * @param shortUrl 可展示的短链 URL；可为空
     * @param originalUrl 目标 URL 的历史或当前展示值
     * @param deleted 是否为删除后保留的历史快照
     */
    record ShortLinkSummary(long linkId, String code, String shortUrl, String originalUrl, boolean deleted) {
        /**
         * 构造未提供 {@code shortUrl} 的兼容摘要。
         *
         * @param linkId 全局短链 ID
         * @param code 大小写敏感短码
         * @param originalUrl 目标 URL 的历史或当前展示值
         * @param deleted 是否为删除后保留的历史快照
         */
        public ShortLinkSummary(long linkId, String code, String originalUrl, boolean deleted) {
            this(linkId, code, null, originalUrl, deleted);
        }
    }
}
