package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.shortlink.application.ShortLinkService.TagDto;
import com.linkforge.shortlink.application.port.TagRepository;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.LinkTagPolicy;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateTagCommandHandler {

    private final SnowflakeIdGenerator idGenerator;
    private final TagRepository tagRepository;
    private final LinkTagPolicy linkTagPolicy = new LinkTagPolicy();

    public CreateTagCommandHandler(
            SnowflakeIdGenerator idGenerator,
            TagRepository tagRepository
    ) {
        this.idGenerator = idGenerator;
        this.tagRepository = tagRepository;
    }

    @Transactional
    public TagDto handle(long tenantId, String name) {
        String n;
        try {
            n = linkTagPolicy.normalizeName(name);
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        TagRepository.Tag existing = tagRepository.findByTenantIdAndName(tenantId, n);
        if (existing != null) {
            return new TagDto(existing.id(), existing.name());
        }

        long id = idGenerator.nextId();
        TagRepository.Tag tag = new TagRepository.Tag(id, tenantId, n, null);
        try {
            tagRepository.insert(tag);
            return new TagDto(tag.id(), tag.name());
        } catch (DataIntegrityViolationException ex) {
            TagRepository.Tag raced = tagRepository.findByTenantIdAndName(tenantId, n);
            if (raced != null) {
                return new TagDto(raced.id(), raced.name());
            }
            throw ex;
        }
    }
}
