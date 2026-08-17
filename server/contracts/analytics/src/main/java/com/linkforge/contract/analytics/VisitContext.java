package com.linkforge.contract.analytics;

/** The bounded, untrusted request fields used to derive a daily visitor fingerprint. */
public record VisitContext(String ip, String userAgent) {
}
