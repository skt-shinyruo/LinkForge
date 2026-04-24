package com.linkforge.shortlink.application.port;

import com.linkforge.shortlink.application.ShortLinkReadService;

import java.util.Optional;

public interface ShortLinkReadRepository {

    Optional<ShortLinkReadService.RedirectLinkMeta> findRedirectMetaByHostAndCode(String host, String code);
}
