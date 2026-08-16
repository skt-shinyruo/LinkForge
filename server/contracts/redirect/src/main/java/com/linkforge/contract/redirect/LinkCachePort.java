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
 * outbox 重试；负缓存写入失败则只会增加后续回源次数。{@code null} host 表示 legacy/unscoped key。</p>
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
     * 按规范化 host 与大小写敏感 code 查询缓存。
     *
     * <p>{@code host} 应由调用方归一为小写且不含端口；{@code null} 或空白值表示没有请求 host 的
     * legacy 兼容路径。</p>
     *
     * @param host 请求 host；可为 {@code null} 或空白以走 legacy/unscoped 路径
     * @param code 大小写敏感的短码
     * @return 正命中、负命中或未命中的三态结果
     */
    LookupResult lookup(String host, String code);

    /**
     * 尝试写入 host-scoped 正缓存。
     *
     * @param host 请求 host；缺失时实现可按其 legacy 规则或快照 hostname 选择兼容 key
     * @param meta 要缓存的短链快照
     * @return 实现未报告失败时为 {@code true}，否则为 {@code false}
     */
    boolean tryPut(String host, LinkMeta meta);

    /**
     * 写入 host-scoped 负缓存。
     *
     * @param host 请求 host；可为 {@code null} 或空白以走 legacy/unscoped 路径
     * @param code 大小写敏感的短码
     */
    void markNotFound(String host, String code);

    /**
     * 尝试驱逐 host-scoped 缓存。
     *
     * <p>短链写侧会把失败的驱逐交给 outbox 重试，因而该方法必须可重复执行。</p>
     *
     * @param host 请求或已知归属 host；可为 {@code null} 或空白以走 legacy/unscoped 路径
     * @param code 大小写敏感的短码
     * @return 本次驱逐未报告失败时为 {@code true}，否则为 {@code false}
     */
    boolean tryEvict(String host, String code);
}
