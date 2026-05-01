package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.TagRepository;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.LinkTagPolicy;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class SetLinkTagsCommandHandler {

    private final SnowflakeIdGenerator idGenerator;
    private final TagRepository tagRepository;
    private final LinkTagRepository linkTagRepository;
    private final LinkTagPolicy linkTagPolicy = new LinkTagPolicy();

    public SetLinkTagsCommandHandler(
            SnowflakeIdGenerator idGenerator,
            TagRepository tagRepository,
            LinkTagRepository linkTagRepository
    ) {
        this.idGenerator = idGenerator;
        this.tagRepository = tagRepository;
        this.linkTagRepository = linkTagRepository;
    }

    @Transactional
    public void handle(long tenantId, long linkId, Set<String> tags) {
        linkTagRepository.deleteAllByLinkId(linkId);
        if (tags == null || tags.isEmpty()) {
            return;
        }

        Set<String> normalized;
        try {
            normalized = linkTagPolicy.normalizeAssignment(tags);
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        Map<String, TagRepository.Tag> existing = new HashMap<>();
        for (String name : normalized) {
            TagRepository.Tag t = tagRepository.findByTenantIdAndName(tenantId, name);
            if (t != null) {
                existing.put(name, t);
            }
        }

        for (String name : normalized) {
            TagRepository.Tag t = existing.get(name);
            if (t == null) {
                long id = idGenerator.nextId();
                TagRepository.Tag created = new TagRepository.Tag(id, tenantId, name, null);
                try {
                    tagRepository.insert(created);
                    t = created;
                } catch (DataIntegrityViolationException ex) {
                    // concurrent create -> use existing
                    TagRepository.Tag raced = tagRepository.findByTenantIdAndName(tenantId, name);
                    if (raced != null) {
                        t = raced;
                    } else {
                        throw ex;
                    }
                }
            }
            linkTagRepository.insert(linkId, t.id());
        }
    }
}
