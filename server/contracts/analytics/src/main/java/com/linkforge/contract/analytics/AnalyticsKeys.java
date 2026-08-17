package com.linkforge.contract.analytics;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Redis keys shared by redirect aggregation, quota reservation and analytics flush. */
public final class AnalyticsKeys {

    private static final String MARKER_VERSION = "v2";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private AnalyticsKeys() {
    }

    public static String statsDirtyMarkerV2Key(LocalDate day) {
        return dirtyMarkerKey("link", day);
    }

    public static String statsDirtyMarkerV2FirstSeenKey(LocalDate day) {
        return dirtyMarkerFirstSeenKey("link", day);
    }

    public static String scopeDirtyMarkerV2Key(LocalDate day) {
        return dirtyMarkerKey("scope", day);
    }

    public static String scopeDirtyMarkerV2FirstSeenKey(LocalDate day) {
        return dirtyMarkerFirstSeenKey("scope", day);
    }

    public static String dirtyMarkerClaimCursorKey(String markerKey) {
        return markerKey + ":claim:cursor";
    }

    public static String dirtyMarkerClaimOverflowKey(String markerKey) {
        return markerKey + ":claim:overflow";
    }

    public static String dirtyLinkMember(long tenantId, long linkId) {
        return tenantId + ":" + linkId;
    }

    public static String pvKey(long tenantId, long linkId, LocalDate day) {
        return "stats:pv:" + tenantId + ":" + linkId + ":" + DAY.format(day);
    }

    public static String uvKey(long tenantId, long linkId, LocalDate day) {
        return "stats:uv:" + tenantId + ":" + linkId + ":" + DAY.format(day);
    }

    public static String tenantScopeUvKey(long tenantId, LocalDate day) {
        return "stats:scope:uv:tenant:" + tenantId + ":" + DAY.format(day);
    }

    public static String applicationScopeUvKey(long tenantId, long applicationId, LocalDate day) {
        return "stats:scope:uv:application:" + tenantId + ":" + applicationId + ":" + DAY.format(day);
    }

    public static String domainScopeUvKey(long tenantId, long domainId, LocalDate day) {
        return "stats:scope:uv:domain:" + tenantId + ":" + domainId + ":" + DAY.format(day);
    }

    public static String tenantScopeMember(long tenantId) {
        return "tenant:" + tenantId + ":0";
    }

    public static String applicationScopeMember(long tenantId, long applicationId) {
        return "application:" + tenantId + ":" + applicationId;
    }

    public static String domainScopeMember(long tenantId, long domainId) {
        return "domain:" + tenantId + ":" + domainId;
    }

    public static String applicationClickQuotaKey(long tenantId, long applicationId, LocalDate monthStartUtc) {
        return "quota:click:application:" + tenantId + ":" + applicationId + ":" + MONTH.format(monthStartUtc);
    }

    public static String projectionDedupKey(String requestId) {
        return "stats:projection:dedup:" + requestId;
    }

    private static String dirtyMarkerKey(String kind, LocalDate day) {
        return "stats:dirty:" + MARKER_VERSION + ":" + kind + ":" + DAY.format(day);
    }

    private static String dirtyMarkerFirstSeenKey(String kind, LocalDate day) {
        return "stats:dirty:" + MARKER_VERSION + ":" + kind + ":first-seen:" + DAY.format(day);
    }
}
