package com.linkforge.contract.redirect;

import java.util.Optional;

/**
 * Query port for resolving {@link LinkMeta} without leaking storage details across bounded contexts.
 *
 * <p>Ownership: the consumer (e.g. Redirect / Analytics) defines the port; the provider (ShortLink) implements it.</p>
 */
public interface LinkMetaQueryPort {

    /**
     * Resolve a link by short code for the redirect path.
     *
     * <p>Must NOT return archived links (archived links are treated as unavailable by Redirect).</p>
     */
    Optional<LinkMeta> findActiveByCode(String code);

    /**
     * Resolve a link by id for best-effort enrichment (e.g. Analytics reports).
     *
     * <p>May return archived links; if the link has been physically deleted, returns empty.</p>
     */
    Optional<LinkMeta> findById(long tenantId, long linkId);
}

