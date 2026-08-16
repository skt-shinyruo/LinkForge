package com.linkforge.contract.analytics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Analytics Redis key 契约（跨模块 SSOT）。
 *
 * <p>Redirect 访问流 appender、PV/UV 投影、flush consumer、明细 consumer 和额度适配器会共同读写这些
 * key。所有 {@link LocalDate} 参数都是由 UTC instant 切分得到的统计日；本类不做时区转换或参数校验。</p>
 *
 * <p>当前生产者推进 V2 generation marker Hash；field 是稳定 member，value 是累计变更代数。flush 先读取
 * 当前聚合快照，落库后仅在 generation 未变化时删除 field，因此并发新写不会被旧 flush 清除。旧 dirty Stream
 * 仅用于滚动升级 dual-read 和回滚写兼容。两种信号都表示“刷新当前累计值”，不是访问增量或 active-set
 * membership；下列前缀、分隔符和 member 形状都是 Redis wire contract。</p>
 */
public final class AnalyticsKeys {

    /** 当前 versioned dirty marker 的 Redis wire 版本。 */
    public static final String DIRTY_MARKER_WIRE_VERSION = "v2";

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private AnalyticsKeys() {
    }

    /**
     * 返回链接 PV/UV 日聚合的 legacy dirty Stream：{@code stats:dirty:flush:{yyyyMMdd}}。
     *
     * <p>仅供兼容读和显式回滚写。当前生产者使用 {@link #statsDirtyMarkerV2Key(LocalDate)}；legacy member
     * 仍按 {@link #dirtyLinkMember(long, long)} 读取当前 PV counter 和 UV HLL 后写入日表。</p>
     *
     * @param day 由 UTC instant 切分得到的非空统计日；本方法不校验日期范围
     * @return 固定前缀和 {@code yyyyMMdd} 组成的 Redis Stream key
     */
    public static String statsDirtyStreamKey(LocalDate day) {
        return "stats:dirty:flush:" + DAY.format(day);
    }

    /**
     * 返回链接聚合的 V2 dirty marker Hash。
     *
     * <p>field 使用 {@link #dirtyLinkMember(long, long)}，value 是每次累计值变化后递增的 generation。
     * legacy Stream 在滚动兼容期仍由 {@link #statsDirtyStreamKey(LocalDate)} 命名。</p>
     */
    public static String statsDirtyMarkerV2Key(LocalDate day) {
        return dirtyMarkerV2Key("link", day);
    }

    /** 返回链接 marker 首次变脏时间 Hash；field 与 generation Hash 相同，value 为 epoch millis。 */
    public static String statsDirtyMarkerV2FirstSeenKey(LocalDate day) {
        return dirtyMarkerV2FirstSeenKey("link", day);
    }

    /**
     * 返回链接维度统计的 legacy dirty Stream：{@code stats:dirty:dim:{yyyyMMdd}}。
     *
     * <p>仅供兼容读和显式回滚写；当前生产者使用 {@link #dimDirtyMarkerV2Key(LocalDate)}。它与基础信号使用
     * 同一 {@code tenantId:linkId} member，但 consumer 会扫描维度 Hash/HLL，不能把二者混用。</p>
     *
     * @param day 由 UTC instant 切分得到的非空统计日；本方法不校验日期范围
     * @return 固定前缀和 {@code yyyyMMdd} 组成的 Redis Stream key
     */
    public static String dimDirtyStreamKey(LocalDate day) {
        return "stats:dirty:dim:" + DAY.format(day);
    }

    /** 返回维度聚合的 V2 dirty marker Hash；field 使用链接 member，value 为 generation。 */
    public static String dimDirtyMarkerV2Key(LocalDate day) {
        return dirtyMarkerV2Key("dim", day);
    }

    /** 返回维度 marker 首次变脏时间 Hash。 */
    public static String dimDirtyMarkerV2FirstSeenKey(LocalDate day) {
        return dirtyMarkerV2FirstSeenKey("dim", day);
    }

    /**
     * 返回 V2 marker 扫描 cursor 的内部状态 key。
     *
     * <p>该 key 与 marker 使用相同 TTL，不属于业务数据；由 flush consumer 持久化扫描进度，避免每次调度
     * 从 hash 的第一个 field 重新开始。传入的 marker key 必须由本类的 V2 marker 方法生成。</p>
     */
    public static String dirtyMarkerClaimCursorKey(String markerKey) {
        return markerKey + ":claim:cursor";
    }

    /** 返回 V2 marker 扫描溢出队列的内部状态 key；其 TTL 跟随对应 marker。 */
    public static String dirtyMarkerClaimOverflowKey(String markerKey) {
        return markerKey + ":claim:overflow";
    }

    /**
     * 编码 V2 marker 与 legacy dirty Stream 共享的稳定链接成员标识。
     *
     * <p>wire format 固定为 {@code tenantId:linkId}。成员不包含 host：linkId 全局唯一，tenantId
     * 同时作为消费侧的隔离校验。修改格式必须与现有 V2 Hash field 和历史 Stream 消息保持兼容。</p>
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @param linkId 短链 ID；本方法不验证其是否为正数
     * @return 无转义的 {@code tenantId:linkId} member
     */
    public static String dirtyLinkMember(long tenantId, long linkId) {
        return tenantId + ":" + linkId;
    }

    /**
     * 返回链接每日 PV counter：{@code stats:pv:{tenantId}:{linkId}:{yyyyMMdd}}。
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @param linkId 短链 ID；本方法不验证其是否为正数
     * @param day 由 UTC instant 切分得到的非空统计日
     * @return 每日 PV Redis counter key
     */
    public static String pvKey(long tenantId, long linkId, LocalDate day) {
        return "stats:pv:" + tenantId + ":" + linkId + ":" + DAY.format(day);
    }

    /**
     * 返回链接每日 UV HyperLogLog：{@code stats:uv:{tenantId}:{linkId}:{yyyyMMdd}}。
     *
     * <p>HLL 是近似去重结构，跨日期的估算值不能直接相加得出精确去重人数。</p>
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @param linkId 短链 ID；本方法不验证其是否为正数
     * @param day 由 UTC instant 切分得到的非空统计日
     * @return 每日 UV HyperLogLog key
     */
    public static String uvKey(long tenantId, long linkId, LocalDate day) {
        return "stats:uv:" + tenantId + ":" + linkId + ":" + DAY.format(day);
    }

    /**
     * 返回租户、应用和域名范围 UV 的 legacy dirty Stream：{@code stats:dirty:scope:{yyyyMMdd}}。
     *
     * <p>仅供兼容读和显式回滚写；当前生产者使用 {@link #scopeDirtyMarkerV2Key(LocalDate)}。member 由
     * {@link #tenantScopeMember(long)}、{@link #applicationScopeMember(long, long)} 或
     * {@link #domainScopeMember(long, long)} 编码，consumer 必须按前缀解释范围。</p>
     *
     * @param day 由 UTC instant 切分得到的非空统计日；本方法不校验日期范围
     * @return 固定前缀和 {@code yyyyMMdd} 组成的 Redis Stream key
     */
    public static String scopeDirtyStreamKey(LocalDate day) {
        return "stats:dirty:scope:" + DAY.format(day);
    }

    /** 返回范围聚合的 V2 dirty marker Hash；field 使用 scope member，value 为 generation。 */
    public static String scopeDirtyMarkerV2Key(LocalDate day) {
        return dirtyMarkerV2Key("scope", day);
    }

    /** 返回范围 marker 首次变脏时间 Hash。 */
    public static String scopeDirtyMarkerV2FirstSeenKey(LocalDate day) {
        return dirtyMarkerV2FirstSeenKey("scope", day);
    }

    /**
     * 返回租户范围每日 UV HLL：{@code stats:scope:uv:tenant:{tenantId}:{yyyyMMdd}}。
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @param day 由 UTC instant 切分得到的非空统计日
     * @return 租户范围每日 UV HyperLogLog key
     */
    public static String tenantScopeUvKey(long tenantId, LocalDate day) {
        return "stats:scope:uv:tenant:" + tenantId + ":" + DAY.format(day);
    }

    /**
     * 返回应用范围每日 UV HLL：{@code stats:scope:uv:application:{tenantId}:{applicationId}:{yyyyMMdd}}。
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @param applicationId 应用 ID；本方法不验证归属或其是否为正数
     * @param day 由 UTC instant 切分得到的非空统计日
     * @return 应用范围每日 UV HyperLogLog key
     */
    public static String applicationScopeUvKey(long tenantId, long applicationId, LocalDate day) {
        return "stats:scope:uv:application:" + tenantId + ":" + applicationId + ":" + DAY.format(day);
    }

    /**
     * 返回域名范围每日 UV HLL：{@code stats:scope:uv:domain:{tenantId}:{domainId}:{yyyyMMdd}}。
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @param domainId 域名 ID；本方法不验证归属或其是否为正数
     * @param day 由 UTC instant 切分得到的非空统计日
     * @return 域名范围每日 UV HyperLogLog key
     */
    public static String domainScopeUvKey(long tenantId, long domainId, LocalDate day) {
        return "stats:scope:uv:domain:" + tenantId + ":" + domainId + ":" + DAY.format(day);
    }

    /**
     * 编码 scope V2 marker 与 legacy Stream 的租户成员：{@code tenant:{tenantId}:0}。
     *
     * <p>末尾 {@code 0} 是固定 application/domain 占位，不是实际资源 ID。</p>
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @return 无转义的租户 scope member
     */
    public static String tenantScopeMember(long tenantId) {
        return "tenant:" + tenantId + ":0";
    }

    /**
     * 编码 scope V2 marker 与 legacy Stream 的应用成员：{@code application:{tenantId}:{applicationId}}。
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @param applicationId 应用 ID；本方法不验证归属或其是否为正数
     * @return 无转义的应用 scope member
     */
    public static String applicationScopeMember(long tenantId, long applicationId) {
        return "application:" + tenantId + ":" + applicationId;
    }

    /**
     * 编码 scope V2 marker 与 legacy Stream 的域名成员：{@code domain:{tenantId}:{domainId}}。
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @param domainId 域名 ID；本方法不验证归属或其是否为正数
     * @return 无转义的域名 scope member
     */
    public static String domainScopeMember(long tenantId, long domainId) {
        return "domain:" + tenantId + ":" + domainId;
    }

    /**
     * 返回应用月点击额度 counter：{@code quota:click:application:{tenantId}:{applicationId}:{yyyyMM}}。
     *
     * <p>{@code monthStartUtc} 必须是 UTC 自然月的第一天；本方法仅格式化日期，传入月中日期会生成不同且
     * 不会被额度适配器自动合并的 key。</p>
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @param applicationId 应用 ID；本方法不验证归属或其是否为正数
     * @param monthStartUtc UTC 自然月首日；非空，且月中日期会产生独立 key
     * @return 固定前缀和 {@code yyyyMM} 组成的月额度 counter key
     */
    public static String applicationClickQuotaKey(long tenantId, long applicationId, LocalDate monthStartUtc) {
        return "quota:click:application:" + tenantId + ":" + applicationId + ":" + MONTH.format(monthStartUtc);
    }

    /**
     * 返回维度 PV Hash：{@code stats:dim:pv:{tenantId}:{linkId}:{yyyyMMdd}:{dimType}}。
     *
     * <p>field 为原始归一化后的 {@code dimValue}，value 为 PV counter。{@code dimType} 会 trim、按 JVM
     * 默认 Locale 转小写；null/空白变为 {@code unknown}，冒号变为下划线，故调用方不得依赖未经该规则处理的
     * 原始字符串作为 key 后缀。</p>
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @param linkId 短链 ID；本方法不验证其是否为正数
     * @param day 由 UTC instant 切分得到的非空统计日
     * @param dimType 维度类型；允许为 {@code null} 或空白并归一为 {@code unknown}
     * @return 维度 PV Hash key，维度值本身不是 key 的一部分
     */
    public static String dimPvHashKey(long tenantId, long linkId, LocalDate day, String dimType) {
        String t = normalizeDimType(dimType);
        return "stats:dim:pv:" + tenantId + ":" + linkId + ":" + DAY.format(day) + ":" + t;
    }

    /**
     * 维度 UV 计数（HyperLogLog）：每个 dimType + dimValue 对应一个 HLL key。
     *
     * <p>{@code dimValue} 会先 trim；null 视为空串，超过 512 个 UTF-16 code unit 会截断后再取 SHA-256(hex)。因此
     * 仅在截断部分不同的值会落到同一个 key，且原始值不可由 key 恢复。极端的 JCA 不可用路径会退化为
     * {@link String#hashCode()} 的十六进制结果，不能把该故障路径当作与 SHA-256 完全等价的 wire 形状。</p>
     *
     * <p>示例：stats:dim:uv:{tenantId}:{linkId}:{yyyyMMdd}:{dimType}:{dimValueSha256}</p>
     *
     * @param tenantId 租户 ID；本方法不验证其是否为正数
     * @param linkId 短链 ID；本方法不验证其是否为正数
     * @param day 由 UTC instant 切分得到的非空统计日
     * @param dimType 维度类型；按 {@link #dimPvHashKey(long, long, LocalDate, String)} 的规则归一
     * @param dimValue 原始维度值；允许为 {@code null}，会变为空串再哈希
     * @return 带维度类型和哈希后缀的独立 UV HyperLogLog key
     */
    public static String dimUvHllKey(long tenantId, long linkId, LocalDate day, String dimType, String dimValue) {
        String t = normalizeDimType(dimType);
        String v = dimValue == null ? "" : dimValue.trim();
        // 维度 value 参与哈希前做一次防御性长度 cap（避免异常输入导致过高 CPU 开销）
        if (v.length() > 512) {
            v = v.substring(0, 512);
        }
        return "stats:dim:uv:" + tenantId + ":" + linkId + ":" + DAY.format(day) + ":" + t + ":" + sha256Hex(v);
    }

    /**
     * 返回原始访问明细 Stream：{@code stats:visit:events}。
     *
     * <p>这是访问流的入口而非 PV/UV 日表；consumer 可能采样、延迟、重放或丢弃 poison record。修改 key 会使
     * 旧 consumer group 和保留策略无法继续消费历史消息。</p>
     *
     * <p>标准 appender 必写 {@code ts}、{@code tenantId}、{@code linkId} 与 {@code requestId}；可选写入
     * {@code visitorKey}、{@code ipHash}、{@code uaRaw}、{@code uaFamily}、{@code osFamily}、{@code deviceType}、
     * {@code refererDomain}、{@code language}、{@code utmSource}、{@code utmMedium}、{@code utmCampaign}、
     * {@code applicationId}、{@code domainId}、{@code code}。它不写原始 IP 或 {@code originalUrl}。明细消费
     * 以 {@code requestId} 去重，PV/UV 聚合投影也使用该字段确保同一事件重放时不重复计数。</p>
     *
     * @return 固定的访问事件 Redis Stream key
     */
    public static String visitEventStreamKey() {
        return "stats:visit:events";
    }

    /**
     * 返回访问事件聚合投影的幂等标记 key。
     *
     * <p>标记的生命周期由聚合 key TTL 控制；requestId 由访问流 appender 生成，不能使用 Stream record id，
     * 因为 ACK 失败重放时 Stream record id 不会改变但投影进程可能已经重启。</p>
     */
    public static String projectionDedupKey(String requestId) {
        return "stats:projection:dedup:" + requestId;
    }

    private static String dirtyMarkerV2Key(String kind, LocalDate day) {
        return "stats:dirty:" + DIRTY_MARKER_WIRE_VERSION + ":" + kind + ":" + DAY.format(day);
    }

    private static String dirtyMarkerV2FirstSeenKey(String kind, LocalDate day) {
        return "stats:dirty:" + DIRTY_MARKER_WIRE_VERSION + ":" + kind + ":first-seen:" + DAY.format(day);
    }

    private static String normalizeDimType(String dimType) {
        String t = dimType == null ? "unknown" : dimType.trim().toLowerCase();
        if (t.isBlank()) {
            t = "unknown";
        }
        // 维度类型仅用于内部 key，避免出现 ':' 导致歧义
        return t.replace(':', '_');
    }

    private static String sha256Hex(String s) {
        String raw = s == null ? "" : s;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
