package com.linkforge.redirect.application;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.foundation.config.RedirectProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 构造最终跳转地址并执行 query 转发策略。
 *
 * <p>模式优先级为短链级配置、全局配置、默认 {@code OFF}。{@code ALLOWLIST} 合并全局和短链 allowlist；
 * 目标地址已有的参数优先，不能被请求参数覆盖。{@code __lf_confirm} 与 {@code __lf_preview} 无论配置
 * 如何都不会向目标站转发。</p>
 *
 * <p>本类采用 best-effort：会使追加部分超过上限的参数及后续参数被丢弃；解析、合并或最终长度检查失败时
 * 返回原始目标 URL。它不验证目标 URL 的业务合法性，因为该不变量由短链写侧负责。</p>
 */
@Component
public class RedirectUrlBuilder {

    private final RedirectProperties redirectProperties;
    private static final int MAX_APPENDED_QUERY_LEN = 2048;
    private static final int MAX_FINAL_URL_LEN = 4096;

    /**
     * 创建 URL builder。
     *
     * @param redirectProperties 全局 query 转发默认配置；可为 {@code null}，此时只使用短链配置与安全默认值
     */
    public RedirectUrlBuilder(RedirectProperties redirectProperties) {
        this.redirectProperties = redirectProperties;
    }

    /**
     * 合并允许转发的请求参数，返回可放入 {@code Location} 的 URL。
     *
     * <p>追加部分最多 2048 字符，触及该上限时保留已接受参数、丢弃当前及后续参数；最终 URL 最多 4096
     * 字符，超限时整体回退原始 URL。原始 query 以原始字符串形式保留，防止已编码的 {@code %} 被二次
     * 编码。</p>
     *
     * @param meta 已验证的短链元数据；为空返回 {@code null}
     * @param requestParams Servlet 参数表；为空或无可转发参数时返回原始地址
     * @return 最终 URL，构造异常或最终长度超限时回退原始地址
     */
    public String buildFinalRedirectUrl(LinkMeta meta, Map<String, String[]> requestParams) {
        if (meta == null) {
            return null;
        }
        String originalUrl = meta.originalUrl();
        if (requestParams == null || requestParams.isEmpty()) {
            return originalUrl;
        }

        String mode = resolveQueryForwardMode(meta);
        if ("OFF".equals(mode)) {
            return originalUrl;
        }

        List<String> allowlist = resolveQueryForwardAllowlist(meta);
        if ("ALLOWLIST".equals(mode) && allowlist.isEmpty()) {
            return originalUrl;
        }

        List<String> reserved = resolveReservedParams();

        Set<String> existingKeys = new HashSet<>();
        try {
            existingKeys.addAll(parseQueryParamKeys(originalUrl));
        } catch (Exception ignored) {
            // 解析失败仍可继续追加，避免损坏原始地址。
        }

        StringBuilder appendRaw = new StringBuilder();
        outer:
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (matchesAny(name, reserved)) {
                continue;
            }
            if ("ALLOWLIST".equals(mode) && !matchesAny(name, allowlist)) {
                continue;
            }
            // 冲突策略：目标 URL 优先，不允许请求参数覆盖既有 key。
            if (existingKeys.contains(name)) {
                continue;
            }
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                int beforeLen = appendRaw.length();
                appendRawParam(appendRaw, name, null);
                if (appendRaw.length() > MAX_APPENDED_QUERY_LEN) {
                    appendRaw.setLength(beforeLen);
                    break outer;
                }
                continue;
            }
            List<String> safeValues = new ArrayList<>();
            for (String v : values) {
                if (v != null) {
                    safeValues.add(v);
                }
            }
            if (safeValues.isEmpty()) {
                int beforeLen = appendRaw.length();
                appendRawParam(appendRaw, name, null);
                if (appendRaw.length() > MAX_APPENDED_QUERY_LEN) {
                    appendRaw.setLength(beforeLen);
                    break outer;
                }
            } else {
                for (String v : safeValues) {
                    int beforeLen = appendRaw.length();
                    appendRawParam(appendRaw, name, v);
                    if (appendRaw.length() > MAX_APPENDED_QUERY_LEN) {
                        appendRaw.setLength(beforeLen);
                        break outer;
                    }
                }
            }
        }

        try {
            String out = appendQueryParams(originalUrl, appendRaw.toString());
            if (out != null && out.length() > MAX_FINAL_URL_LEN) {
                return originalUrl;
            }
            return out;
        } catch (Exception e) {
            return originalUrl;
        }
    }

    private static Set<String> parseQueryParamKeys(String url) {
        if (url == null || url.isBlank()) {
            return Set.of();
        }
        URI uri = URI.create(url.trim());
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        String[] parts = rawQuery.split("&");
        for (String p : parts) {
            if (p == null || p.isBlank()) {
                continue;
            }
            String[] kv = p.split("=", 2);
            String rawKey = kv.length == 0 ? null : kv[0];
            String key = decodeQueryComponent(rawKey);
            if (key != null && !key.isBlank()) {
                out.add(key);
            }
        }
        return out;
    }

    private static void appendRawParam(StringBuilder out, String name, String value) {
        if (out == null || name == null || name.isBlank()) {
            return;
        }
        if (out.length() > 0) {
            out.append('&');
        }
        out.append(encodeQueryComponent(name));
        if (value == null) {
            return;
        }
        out.append('=');
        out.append(encodeQueryComponent(value));
    }

    private static String appendQueryParams(String originalUrl, String rawAppendQuery) {
        if (originalUrl == null || originalUrl.isBlank() || rawAppendQuery == null || rawAppendQuery.isBlank()) {
            return originalUrl;
        }

        // 不从 raw components 重建 URI：那会把现有 %2B 的 % 转成 %25，造成二次编码。
        // 保留原始 URL，仅在 fragment 前插入已安全编码的追加 query。
        String url = originalUrl.trim();

        int hashIdx = url.indexOf('#');
        String fragment = "";
        String beforeFragment = url;
        if (hashIdx >= 0) {
            fragment = url.substring(hashIdx);
            beforeFragment = url.substring(0, hashIdx);
        }

        int qIdx = beforeFragment.indexOf('?');
        if (qIdx < 0) {
            return beforeFragment + "?" + rawAppendQuery + fragment;
        }

        String existingQuery = beforeFragment.substring(qIdx + 1);
        if (existingQuery.isBlank()) {
            return beforeFragment.substring(0, qIdx + 1) + rawAppendQuery + fragment;
        }
        if (existingQuery.endsWith("&")) {
            return beforeFragment + rawAppendQuery + fragment;
        }
        return beforeFragment + "&" + rawAppendQuery + fragment;
    }

    private static String decodeQueryComponent(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }

    private static String encodeQueryComponent(String raw) {
        if (raw == null) {
            return "";
        }
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    private String resolveQueryForwardMode(LinkMeta meta) {
        String raw = trimToNull(meta == null ? null : meta.queryForwardMode());
        if (raw == null && redirectProperties != null) {
            raw = trimToNull(redirectProperties.getQueryForwardMode());
        }
        if (raw == null) {
            return "OFF";
        }
        String t = raw.trim().toUpperCase();
        return ("OFF".equals(t) || "ALLOWLIST".equals(t) || "ALL".equals(t)) ? t : "OFF";
    }

    private List<String> resolveQueryForwardAllowlist(LinkMeta meta) {
        // allowlist：合并全局与短链配置并去重，顺序不是对外契约。
        Set<String> set = new HashSet<>();

        var global = redirectProperties == null ? null : redirectProperties.getQueryForwardAllowlist();
        if (global != null) {
            for (String p : global) {
                String v = trimToNull(p);
                if (v != null) {
                    set.add(v);
                }
            }
        }

        String perLinkRaw = trimToNull(meta == null ? null : meta.queryForwardAllowlist());
        if (perLinkRaw != null) {
            String[] parts = perLinkRaw.split(",");
            for (String p : parts) {
                String v = trimToNull(p);
                if (v != null) {
                    set.add(v);
                }
            }
        }

        return set.isEmpty() ? List.of() : new ArrayList<>(set);
    }

    private List<String> resolveReservedParams() {
        var raw = redirectProperties == null ? null : redirectProperties.getQueryForwardReservedParams();

        Set<String> set = new HashSet<>();
        if (raw != null) {
            for (String p : raw) {
                String v = trimToNull(p);
                if (v != null) {
                    set.add(v);
                }
            }
        }
        // 安全默认：内部确认/预览参数永远不能透传到目标站。
        set.add("__lf_confirm");
        set.add("__lf_preview");
        return new ArrayList<>(set);
    }

    private static boolean matchesAny(String name, List<String> patterns) {
        if (name == null || name.isBlank() || patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String p : patterns) {
            String v = trimToNull(p);
            if (v == null) {
                continue;
            }
            if (v.endsWith("*")) {
                String prefix = v.substring(0, v.length() - 1);
                if (!prefix.isBlank() && name.startsWith(prefix)) {
                    return true;
                }
            } else if (name.equals(v)) {
                return true;
            }
        }
        return false;
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }
}
