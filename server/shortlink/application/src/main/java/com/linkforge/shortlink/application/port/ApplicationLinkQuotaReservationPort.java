package com.linkforge.shortlink.application.port;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 为应用创建短链预占月度额度的事务端口。
 *
 * <p>实现必须以 {@code tenantId/applicationId/monthStartUtc} 为计数作用域，并在并发请求下保证
 * “检查上限并增加一个用量”是原子操作，不能用先查询再递增的非原子实现。预占应加入创建短链所在的
 * 数据库事务：后续短链写入失败并回滚时，本次用量也必须回滚，避免产生永久占用。</p>
 *
 * <p>该端口采用 fail-closed 的布尔契约。{@code false} 只表示本次没有取得名额，可能是额度已满、
 * 参数非法或底层并发协调未成功；调用方不得据此推断精确故障原因。持久化异常可以向上抛出，由外层事务
 * 统一回滚。</p>
 */
public interface ApplicationLinkQuotaReservationPort {

    /**
     * 尝试在指定 UTC 月份预占一个短链创建名额。
     *
     * <p>{@code fromInclusiveUtc} 与 {@code toExclusiveUtc} 组成 UTC 语义的半开区间
     * {@code [fromInclusiveUtc, toExclusiveUtc)}，用于首次建立月度计数基线。参数使用
     * {@link LocalDateTime} 时不携带时区，调用方必须确保其已经按 UTC 换算。月度上限小于等于零表示
     * 不限额，实现应直接视为预占成功。</p>
     *
     * @param tenantId 租户 ID，必须为正数
     * @param applicationId 应用 ID，必须为正数且属于该租户
     * @param monthStartUtc UTC 月份首日，用作计数桶标识
     * @param fromInclusiveUtc 月度基线统计的包含型起点
     * @param toExclusiveUtc 月度基线统计的排除型终点，必须晚于起点
     * @param monthlyLinkLimit 月度上限；小于等于零表示不限额
     * @return 已原子取得一个名额或当前不限额时返回 {@code true}；未取得名额时返回 {@code false}，
     *         且不得增加已用量
     */
    boolean tryReserveMonthlyLink(
            long tenantId,
            long applicationId,
            LocalDate monthStartUtc,
            LocalDateTime fromInclusiveUtc,
            LocalDateTime toExclusiveUtc,
            long monthlyLinkLimit
    );
}
