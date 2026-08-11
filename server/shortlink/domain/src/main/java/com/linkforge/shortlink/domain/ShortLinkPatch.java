package com.linkforge.shortlink.domain;

import java.time.LocalDateTime;

/** 已在应用边界完成类型解析的短链部分更新。 */
public record ShortLinkPatch(
        PatchValue<HttpUrl> originalUrl,
        PatchValue<String> note,
        PatchValue<Boolean> enabled,
        PatchValue<LocalDateTime> expiresAtUtc,
        PatchValue<Integer> redirectStatusCode,
        PatchValue<Boolean> previewEnabled,
        PatchValue<HttpUrl> unavailableLandingUrl,
        PatchValue<QueryForwardMode> queryForwardMode,
        PatchValue<QueryForwardAllowlist> queryForwardAllowlist,
        PatchValue<ShortLinkLifecycleState> lifecycleState
) {
    public ShortLinkPatch {
        originalUrl = defaultValue(originalUrl);
        note = defaultValue(note);
        enabled = defaultValue(enabled);
        expiresAtUtc = defaultValue(expiresAtUtc);
        redirectStatusCode = defaultValue(redirectStatusCode);
        previewEnabled = defaultValue(previewEnabled);
        unavailableLandingUrl = defaultValue(unavailableLandingUrl);
        queryForwardMode = defaultValue(queryForwardMode);
        queryForwardAllowlist = defaultValue(queryForwardAllowlist);
        lifecycleState = defaultValue(lifecycleState);
    }

    private static <T> PatchValue<T> defaultValue(PatchValue<T> value) {
        return value == null ? PatchValue.unchanged() : value;
    }
}
