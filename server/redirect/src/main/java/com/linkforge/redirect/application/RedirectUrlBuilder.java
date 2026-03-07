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
 * Redirect target URL builder.
 *
 * <p>Encapsulates query-forward policy (mode/allowlist/reserved params) so controllers stay thin.</p>
 */
@Component
public class RedirectUrlBuilder {

    private final RedirectProperties redirectProperties;

    public RedirectUrlBuilder(RedirectProperties redirectProperties) {
        this.redirectProperties = redirectProperties;
    }

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
            // Keep a best-effort behavior: try to append without breaking existing URL.
        }

        StringBuilder appendRaw = new StringBuilder();
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
            // Conflict policy: target URL wins (do not overwrite existing params)
            if (existingKeys.contains(name)) {
                continue;
            }
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                appendRawParam(appendRaw, name, null);
                continue;
            }
            List<String> safeValues = new ArrayList<>();
            for (String v : values) {
                if (v != null) {
                    safeValues.add(v);
                }
            }
            if (safeValues.isEmpty()) {
                appendRawParam(appendRaw, name, null);
            } else {
                for (String v : safeValues) {
                    appendRawParam(appendRaw, name, v);
                }
            }
        }

        try {
            return appendQueryParams(originalUrl, appendRaw.toString());
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

        URI uri = URI.create(originalUrl.trim());

        String existing = uri.getRawQuery();
        String merged;
        if (existing == null || existing.isBlank()) {
            merged = rawAppendQuery;
        } else {
            merged = existing + "&" + rawAppendQuery;
        }

        try {
            URI out = new URI(
                    uri.getScheme(),
                    uri.getRawAuthority(),
                    uri.getRawPath(),
                    merged,
                    uri.getRawFragment()
            );
            return out.toString();
        } catch (Exception e) {
            return originalUrl;
        }
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
        // allowlist: global + per-link merged (de-duplicated)
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
        // Safety default: internal params must never be forwarded
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
