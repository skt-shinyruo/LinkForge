package com.linkforge.governance.application;

import java.util.List;

/** Governance 列表的有界 keyset 分页结果。 */
public record GovernancePageResult<T>(
        List<T> items,
        boolean hasMore,
        String nextCursor
) {

    public GovernancePageResult {
        items = List.copyOf(items);
    }
}
