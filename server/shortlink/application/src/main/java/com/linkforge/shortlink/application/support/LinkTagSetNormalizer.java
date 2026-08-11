package com.linkforge.shortlink.application.support;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;

import java.util.LinkedHashSet;
import java.util.Set;

/** 短链标签全量替换语义的唯一规范化边界。 */
public final class LinkTagSetNormalizer {

    private static final int MAX_TAGS_PER_LINK = 20;

    private LinkTagSetNormalizer() {
    }

    public static Set<String> normalize(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : tags) {
            String tag = normalizeNullable(raw);
            if (tag == null) {
                continue;
            }
            if (tag.length() > 64) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名过长: " + tag);
            }
            normalized.add(tag);
            if (normalized.size() >= MAX_TAGS_PER_LINK) {
                break;
            }
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
