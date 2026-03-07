package com.linkforge.contract.redirect;

/**
 * Redirect Cache Port (Published Language).
 *
 * <p>Goal: decouple {@code shortlink} (writer) and {@code redirect} (reader) from concrete cache
 * implementations (Redis, local cache, etc).</p>
 */
public interface LinkCachePort {

    record LookupResult(LinkMeta meta, boolean notFound) {
        public static LookupResult hit(LinkMeta meta) {
            return new LookupResult(meta, false);
        }

        public static LookupResult negativeHit() {
            return new LookupResult(null, true);
        }

        public static LookupResult miss() {
            return new LookupResult(null, false);
        }

        public boolean hit() {
            return meta != null;
        }
    }

    LookupResult lookup(String code);

    boolean tryPut(LinkMeta meta);

    void markNotFound(String code);

    boolean tryEvict(String code);
}

