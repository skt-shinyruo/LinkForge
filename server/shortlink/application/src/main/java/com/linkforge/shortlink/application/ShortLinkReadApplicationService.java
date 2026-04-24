package com.linkforge.shortlink.application;

import com.linkforge.shortlink.application.port.ShortLinkReadRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ShortLinkReadApplicationService implements ShortLinkReadService {

    private final ShortLinkReadRepository shortLinkReadRepository;

    public ShortLinkReadApplicationService(ShortLinkReadRepository shortLinkReadRepository) {
        this.shortLinkReadRepository = shortLinkReadRepository;
    }

    @Override
    public Optional<RedirectLinkMeta> findRedirectMetaByHostAndCode(String host, String code) {
        return shortLinkReadRepository.findRedirectMetaByHostAndCode(host, code);
    }
}
