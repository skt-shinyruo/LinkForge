package com.linkforge.analytics.infrastructure.query;

import com.linkforge.contract.analytics.ApplicationClickUsagePort;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsQueryMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 将平台月度点击使用量端口映射为 Analytics 日表查询。
 *
 * <p>时间窗口采用 {@code [fromInclusiveUtc, toExclusiveUtc)}，与额度计数的整月边界一致。异常不在
 * 此适配器中转换：调用方可按自身策略处理；无效参数返回零避免形成无范围查询。</p>
 */
@Component
public class MybatisApplicationClickUsagePort implements ApplicationClickUsagePort {

    private final AnalyticsQueryMapper queryMapper;

    public MybatisApplicationClickUsagePort(AnalyticsQueryMapper queryMapper) {
        this.queryMapper = queryMapper;
    }

    /** 返回指定 UTC 半开区间内应用所属链接 PV 的总和。 */
    @Override
    public long countApplicationClicks(
            long tenantId,
            long applicationId,
            LocalDate fromInclusiveUtc,
            LocalDate toExclusiveUtc
    ) {
        if (tenantId <= 0 || applicationId <= 0 || fromInclusiveUtc == null || toExclusiveUtc == null
                || !toExclusiveUtc.isAfter(fromInclusiveUtc)) {
            return 0L;
        }
        Long count = queryMapper.countApplicationPv(tenantId, applicationId, fromInclusiveUtc, toExclusiveUtc);
        return count == null ? 0L : count;
    }
}
