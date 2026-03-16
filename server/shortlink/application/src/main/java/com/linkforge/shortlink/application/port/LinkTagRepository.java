package com.linkforge.shortlink.application.port;

import java.util.Collection;
import java.util.List;

public interface LinkTagRepository {

    int insert(long linkId, long tagId);

    int deleteAllByLinkId(long linkId);

    List<String> findTagNamesByLinkId(long linkId);

    List<LinkTagName> findTagNamesByLinkIds(Collection<Long> linkIds);

    record LinkTagName(long linkId, String tagName) {
    }
}

