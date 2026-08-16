package com.linkforge.shortlink.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应用月度发链用量的 MyBatis 映射。
 *
 * <p>named lock 是 MySQL 连接级 advisory lock，不参与事务提交或回滚。调用方必须在同一
 * 事务绑定连接上完成获取、用量初始化、条件递增和释放，不能将这些方法拆到不同事务或异步
 * 线程。用量行以租户、应用和 UTC 月初组成唯一键。</p>
 */
@Mapper
public interface ApplicationLinkQuotaUsageMapper {

    /**
     * 等待获取指定月份的连接级 named lock。
     *
     * @return MySQL {@code GET_LOCK} 的结果：{@code 1} 表示成功，{@code 0} 表示超时，
     *         {@code null} 表示发生错误
     */
    Integer acquireMonthlyLinkUsageLock(@Param("lockName") String lockName, @Param("timeoutSeconds") int timeoutSeconds);

    /**
     * 在当前连接上释放 named lock；该操作不会由事务结束自动代替。
     *
     * @return MySQL {@code RELEASE_LOCK} 的原始结果
     */
    Integer releaseMonthlyLinkUsageLock(@Param("lockName") String lockName);

    /**
     * 当月度用量行尚不存在时，以指定 UTC 半开时间区间内的历史短链数量建立基线。
     *
     * <p>该方法必须在持有对应 named lock 时调用；唯一键和 {@code INSERT IGNORE} 使已有
     * 用量行保持不变，避免部署配额功能后把既有短链漏算。</p>
     *
     * @return MyBatis 报告的插入行数；行已存在时通常为 {@code 0}
     */
    int ensureMonthlyLinkUsage(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("monthStartUtc") LocalDate monthStartUtc,
            @Param("fromInclusiveUtc") LocalDateTime fromInclusiveUtc,
            @Param("toExclusiveUtc") LocalDateTime toExclusiveUtc
    );

    /**
     * 仅在当前用量严格小于上限时原子递增一次。
     *
     * @return 成功占用名额时为 {@code 1}，额度已满或目标行不存在时为 {@code 0}
     */
    int incrementMonthlyLinkUsage(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("monthStartUtc") LocalDate monthStartUtc,
            @Param("monthlyLinkLimit") long monthlyLinkLimit
    );

}
