package com.linkforge.analytics.interfaces.web;

import java.time.LocalDate;

/**
 * 单个 UTC 日期的 PV/UV 聚合 HTTP 响应。
 *
 * <p>该记录对应报表读模型的已落库快照，可能滞后于 Redirect 的实时访问。{@code uv} 来自
 * HyperLogLog，表示近似日去重访问量；不能与其他日期的 {@code uv} 简单相加后声称为区间内
 * 的精确去重人数。
 *
 * @param day UTC 统计日期
 * @param pv 页面访问次数
 * @param uv 近似唯一访问量
 */
public record DailyStatHttpResponse(LocalDate day, long pv, long uv) {
}
