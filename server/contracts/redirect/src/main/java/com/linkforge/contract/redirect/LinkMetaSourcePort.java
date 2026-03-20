package com.linkforge.contract.redirect;

import java.util.Optional;

/**
 * Authoritative link metadata source for redirect reads.
 */
public interface LinkMetaSourcePort {

    Optional<LinkMeta> findByCode(String code);

    default Optional<LinkMeta> findByHostAndCode(String host, String code) {
        return findByCode(code);
    }
}
