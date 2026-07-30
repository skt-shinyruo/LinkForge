package com.linkforge.shortlink.application.query;

import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.domain.ShortLink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把一批已经完成租户隔离和授权的短链转换为 {@code linkId -> 标签名称列表}。
 *
 * <p>标签关联表自身没有 tenant 列，本工具不会再次验证归属；调用方只能传入可信查询得到的聚合。
 * 它把所有非空 linkId 合并为一次仓储调用，跳过空聚合和不完整返回行，并保留仓储提供的标签顺序。</p>
 */
final class TagMaps {

    private TagMaps() {
    }

    /**
     * 批量装载标签；空输入、全空元素或空仓储结果统一返回不可变空映射。
     */
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
