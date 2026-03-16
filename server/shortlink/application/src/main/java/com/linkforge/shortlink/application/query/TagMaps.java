package com.linkforge.shortlink.application.query;

import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.domain.ShortLink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TagMaps {

    private TagMaps() {
    }

    static Map<Long, List<String>> loadTagsByLinkIds(LinkTagRepository linkTagRepository, Collection<ShortLink> links) {
        if (links == null || links.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>();
        for (ShortLink e : links) {
            if (e == null) {
                continue;
            }
            ids.add(e.id());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<LinkTagRepository.LinkTagName> rows = linkTagRepository.findTagNamesByLinkIds(ids);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> map = new HashMap<>();
        for (LinkTagRepository.LinkTagName r : rows) {
            if (r == null || r.tagName() == null) {
                continue;
            }
            map.computeIfAbsent(r.linkId(), k -> new ArrayList<>()).add(r.tagName());
        }
        return map;
    }
}

