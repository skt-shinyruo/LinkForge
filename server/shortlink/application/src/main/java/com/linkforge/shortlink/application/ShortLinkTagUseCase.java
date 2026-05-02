package com.linkforge.shortlink.application;

import java.util.List;

public interface ShortLinkTagUseCase {

    List<TagDto> listTags(long tenantId);

    TagDto createTag(long tenantId, String name);
}
