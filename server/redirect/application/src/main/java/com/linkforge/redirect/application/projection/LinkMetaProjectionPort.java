package com.linkforge.redirect.application.projection;

import com.linkforge.contract.redirect.LinkMeta;

import java.util.Optional;

public interface LinkMetaProjectionPort {
    Optional<LinkMeta> findByCode(String code);
}

