package com.linkforge.contract.redirect;

/**
 * Redirect 缓存发布契约。
 *
 * <p>读取结果必须区分正命中、已确认不存在的负命中与未命中。缓存不是短链事实来源：Redis 读故障、
 * 过期和坏值都应降级为未命中，随后由 Redirect 回源 {@code ShortLinkReadPort}；只有权威读取正常返回
 * 空值后才可发布负缓存。</p>
 *
 * <p>缓存写入和驱逐是优化副作用，不能作为业务写入成功或链接存在性的证据。{@code tryPut} 与
 * {@code tryEvict} 用返回值报告本次缓存动作是否失败，不在端口内安排重试。短链写侧可将驱逐失败转换为
 * outbox 重试；负缓存写入失败则只会增加后续回源次数。无 host 的读取、负缓存和驱逐方法是
 * legacy/unscoped 调用面；不带请求 host 的正缓存写入可由实现根据快照 hostname 选择 key。host 重载为
 * 旧实现提供 source-compatible 委派，生产 host-aware 实现必须覆盖，不能把默认实现误认为具备 host
 * 隔离。</p>
 */
public interface LinkCachePort {

    /**
     * 缓存三态查询结果。
     *
     * <p>合法组合只有 {@code meta!=null/notFound=false}（正命中）、{@code meta==null/notFound=true}
     * （负命中）和 {@code meta==null/notFound=false}（未命中）。record 的 public canonical constructor
     * 为 Java 兼容保留，不会阻止直接构造 {@code meta!=null/notFound=true}；发布者不得产生该组合，也不得
     * 将缓存不可用或反序列化失败标记为负命中。消费者应优先检查 {@link #notFound()}，再处理
     * {@link #hit()}，以保持与 Redirect 的决策顺序一致。</p>
     *
     * @param meta 正命中的短链快照；负命中和未命中必须为 {@code null}
     * @param notFound 是否已由权威读取确认不存在；为 {@code true} 时 {@code meta} 必须为 {@code null}
     */
    record LookupResult(LinkMeta meta, boolean notFound) {
        /**
         * 创建正缓存命中结果。
         *
         * @param meta 非空的短链快照；本工厂不做运行时校验，传入 {@code null} 会得到 miss 形状的结果
         * @return 含给定快照且 {@code notFound=false} 的结果
         */
        public static LookupResult hit(LinkMeta meta) {
            return new LookupResult(meta, false);
        }

        /**
         * 创建已确认不存在的负缓存命中结果。
         *
         * @return {@code meta=null/notFound=true}；该结果会阻止本次 Redirect 回源
         */
        public static LookupResult negativeHit() {
            return new LookupResult(null, true);
        }

        /**
         * 创建未缓存结果。
         *
         * @return {@code meta=null/notFound=false}；调用方应回源权威短链读取端口
         */
        public static LookupResult miss() {
            return new LookupResult(null, false);
        }

        /**
         * 判断结果是否携带正缓存快照。
         *
         * @return {@code meta} 非空时为 {@code true}；该值单独不能纠正直接构造的非法组合
         */
        public boolean hit() {
            return meta != null;
        }
    }

    /**
     * 按大小写敏感 code 查询 legacy/unscoped 缓存。
     *
     * <p>实现应将缓存读异常、损坏值和无 key 统一表达为 {@link LookupResult#miss()}，不得把它们作为
     * {@link LookupResult#negativeHit()} 返回。此方法不校验短码格式，调用方负责在进入缓存前限制输入面。</p>
     *
     * @param code 大小写敏感的短码
     * @return 正命中、负命中或未命中的三态结果
     */
    LookupResult lookup(String code);

    /**
     * 按规范化 host 与大小写敏感 code 查询缓存。
     *
     * <p>{@code host} 应由调用方归一为小写且不含端口；{@code null} 或空白值表示没有请求 host 的
     * legacy 兼容路径。默认实现刻意忽略 {@code host} 并委派 {@link #lookup(String)}，因此不具备 host
     * 隔离；生产的 host-aware 缓存必须覆盖此方法并使用 host-scoped key。</p>
     *
     * @param host 请求 host；可为 {@code null} 或空白以走 legacy/unscoped 路径
     * @param code 大小写敏感的短码
     * @return 正命中、负命中或未命中的三态结果
     */
    default LookupResult lookup(String host, String code) {
        return lookup(code);
    }

    /**
     * 尝试写入不带请求 host 的正缓存。
     *
     * <p>{@code true} 仅表示实现未报告本次缓存动作失败，不表示快照已持久化，也不表示链接在权威数据源中
     * 存在；无效输入可被实现作为无需写入处理。实现可按 {@code meta.hostname()} 选择 host-scoped key，
     * 也可保留 legacy key，因此调用方不能从本重载推断 key 范围。缓存写失败不应阻断已完成的短链事实读取
     * 或写入。</p>
     *
     * @param meta 要缓存的短链快照
     * @return 实现未报告失败时为 {@code true}，否则为 {@code false}
     */
    boolean tryPut(LinkMeta meta);

    /**
     * 尝试写入 host-scoped 正缓存。
     *
     * <p>默认实现刻意忽略 {@code host} 并委派 {@link #tryPut(LinkMeta)}，因此不能由该默认行为推断
     * key 范围；旧实现也可能按 {@code meta.hostname()} 自行选择 key。生产 host-aware 缓存必须覆盖，避免
     * 同短码的不同域名相互污染。</p>
     *
     * @param host 请求 host；缺失时实现可按其 legacy 规则或快照 hostname 选择兼容 key
     * @param meta 要缓存的短链快照
     * @return 实现未报告失败时为 {@code true}，否则为 {@code false}
     */
    default boolean tryPut(String host, LinkMeta meta) {
        return tryPut(meta);
    }

    /**
     * 尽力写入 legacy/unscoped 的负缓存。
     *
     * <p>只允许在权威读取正常确认不存在后调用。该调用没有失败返回值，生产实现必须隔离缓存写异常；失败
     * 只能导致下一次请求再次回源，不能改变本次的未找到结论，也不能中断跳转主链路。</p>
     *
     * @param code 大小写敏感的短码
     */
    void markNotFound(String code);

    /**
     * 写入 host-scoped 负缓存。
     *
     * <p>默认实现刻意忽略 {@code host} 并委派 {@link #markNotFound(String)}。生产 host-aware 缓存必须
     * 覆盖，避免一个域名的不存在结论遮蔽另一域名。</p>
     *
     * @param host 请求 host；可为 {@code null} 或空白以走 legacy/unscoped 路径
     * @param code 大小写敏感的短码
     */
    default void markNotFound(String host, String code) {
        markNotFound(code);
    }

    /**
     * 尝试驱逐 legacy/unscoped 缓存。
     *
     * <p>操作必须可重复执行，删除不存在的 key 应视为成功。端口只报告本次失败；调用者决定是否将
     * {@code false} 转为异常并交由 durable outbox 重试。</p>
     *
     * @param code 大小写敏感的短码
     * @return 本次驱逐未报告失败时为 {@code true}，否则为 {@code false}
     */
    boolean tryEvict(String code);

    /**
     * 尝试驱逐 host-scoped 缓存。
     *
     * <p>默认实现刻意忽略 {@code host} 并委派 {@link #tryEvict(String)}；host-aware 实现应覆盖并删除
     * 对应 host key。短链写侧会把失败的驱逐交给 outbox 重试，因而该方法必须可重复执行。</p>
     *
     * @param host 请求或已知归属 host；可为 {@code null} 或空白以走 legacy/unscoped 路径
     * @param code 大小写敏感的短码
     * @return 本次驱逐未报告失败时为 {@code true}，否则为 {@code false}
     */
    default boolean tryEvict(String host, String code) {
        return tryEvict(code);
    }
}
