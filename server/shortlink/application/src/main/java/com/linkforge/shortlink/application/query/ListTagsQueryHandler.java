package com.linkforge.shortlink.application.query;

import com.linkforge.shortlink.application.ShortLinkService.TagDto;
import com.linkforge.shortlink.application.port.TagRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ListTagsQueryHandler {

    private final TagRepository tagRepository;

    public ListTagsQueryHandler(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public List<TagDto> handle(long tenantId) {
        List<TagRepository.Tag> tags = tagRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream().map(t -> new TagDto(t.id(), t.name())).toList();
    }
}
