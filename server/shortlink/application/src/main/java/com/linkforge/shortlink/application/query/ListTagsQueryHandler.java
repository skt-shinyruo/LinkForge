package com.linkforge.shortlink.application.query;

import com.linkforge.shortlink.application.TagDto;
import com.linkforge.shortlink.application.port.TagRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 列出租户标签字典的只读 handler。
 *
 * <p>仓储以租户作为隔离条件，并按创建时间倒序返回；本类不认证主体或判断角色，调用方必须保证 tenantId
 * 来自已经认证的请求上下文。仓储返回 {@code null} 或空集合时统一输出不可变空列表。</p>
 */
@Component
public class ListTagsQueryHandler {

    private final TagRepository tagRepository;

    public ListTagsQueryHandler(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /** 返回指定租户的标签 ID 和名称，不跨租户回退。 */
    public List<TagDto> handle(long tenantId) {
        List<TagRepository.Tag> tags = tagRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream().map(t -> new TagDto(t.id(), t.name())).toList();
    }
}
