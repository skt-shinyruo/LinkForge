package com.linkforge.shortlink.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_QUERY_FORWARD_ALLOWLIST_TOO_LONG;

/**
 * 短链级查询参数透传白名单。
 *
 * <p>白名单按输入顺序去重并保存为不可变列表，忽略空项，最多保留前 50 个不同模式。每项由
 * {@link QueryParamPattern} 校验；逗号分隔的持久化表示最长 1024 个 Java 字符。空白名单序列化为 {@code null}，
 * 以表达“没有短链级白名单项”。</p>
 *
 * <p>序列化格式不支持转义；模式语法本身禁止逗号，因此可以安全地用逗号拆分。该白名单只有在
 * {@link QueryForwardMode#ALLOWLIST} 下参与过滤，是否与全局白名单合并由重定向应用层决定。</p>
 */
public final class QueryForwardAllowlist {

    private static final int MAX_ITEMS = 50;
    private static final int MAX_SERIALIZED_LENGTH = 1024;

    private static final QueryForwardAllowlist EMPTY = new QueryForwardAllowlist(List.of());

    private final List<QueryParamPattern> patterns;

    private QueryForwardAllowlist(List<QueryParamPattern> patterns) {
        this.patterns = patterns == null ? List.of() : List.copyOf(patterns);
        requireSerializedLengthWithinLimit();
    }

    /**
     * 返回共享的空白名单。
     */
    public static QueryForwardAllowlist empty() {
        return EMPTY;
    }

    /**
     * 从外部模式列表创建白名单。
     *
     * <p>每项先去除首尾空白，空项被忽略；使用首次出现的顺序去重，收集到 50 个不同模式后停止读取后续项。
     * 非法模式或最终序列化长度超限会抛出 {@link ShortLinkDomainException}。</p>
     *
     * @param raw 原始模式列表；为空时得到空白名单
     */
    public static QueryForwardAllowlist fromRaw(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }

        Set<String> uniq = new LinkedHashSet<>();
        for (String s : raw) {
            String t = normalizeNullable(s);
            if (t == null) {
                continue;
            }
            uniq.add(t);
            if (uniq.size() >= MAX_ITEMS) {
                break;
            }
        }
        if (uniq.isEmpty()) {
            return empty();
        }

        List<QueryParamPattern> out = new ArrayList<>(uniq.size());
        for (String p : uniq) {
            out.add(QueryParamPattern.of(p));
        }
        return new QueryForwardAllowlist(out);
    }

    /**
     * 解析逗号分隔的持久化值，并复用与外部列表相同的归一化和上限规则。
     *
     * @param raw 持久化文本；空值或纯空白表示空白名单
     */
    public static QueryForwardAllowlist parseSerialized(String raw) {
        String s = normalizeNullable(raw);
        if (s == null) {
            return empty();
        }
        String[] parts = s.split(",");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String t = normalizeNullable(p);
            if (t != null) {
                list.add(t);
            }
        }
        return fromRaw(list);
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    /**
     * 返回按首次出现顺序排列的不可变模式文本列表。
     */
    public List<String> values() {
        if (patterns.isEmpty()) {
            return List.of();
        }
        return patterns.stream().map(QueryParamPattern::value).toList();
    }

    /**
     * 生成逗号分隔的持久化表示；{@code null} 表示没有短链级白名单项。
     */
    public String serializeOrNull() {
        if (patterns.isEmpty()) {
            return null;
        }
        String joined = String.join(",", values());
        return joined.isBlank() ? null : joined;
    }

    private void requireSerializedLengthWithinLimit() {
        String joined = serializeOrNull();
        if (joined == null) {
            return;
        }
        if (joined.length() > MAX_SERIALIZED_LENGTH) {
            throw new ShortLinkDomainException(INVALID_QUERY_FORWARD_ALLOWLIST_TOO_LONG, "queryForwardAllowlist 过长");
        }
    }

    private static String normalizeNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
