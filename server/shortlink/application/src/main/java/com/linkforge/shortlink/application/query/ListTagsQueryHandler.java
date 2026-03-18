package com.linkforge.shortlink.application.query;

import com.linkforge.foundation.runtime.security.TenantGuard;
import com.linkforge.shortlink.application.ShortLinkService.TagDto;
import com.linkforge.shortlink.application.port.TagRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ListTagsQueryHandler {

    private final TagRepository tagRepository;
    private final TenantGuard tenantGuard;

    public ListTagsQueryHandler(TagRepository tagRepository, TenantGuard tenantGuard) {
        this.tagRepository = tagRepository;
        this.tenantGuard = tenantGuard;
    }

    public List<TagDto> handle(long tenantId) {
        tenantGuard.requireCurrentTenant(tenantId);
        List<TagRepository.Tag> tags = tagRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream().map(t -> new TagDto(t.id(), t.name())).toList();
    }
}
