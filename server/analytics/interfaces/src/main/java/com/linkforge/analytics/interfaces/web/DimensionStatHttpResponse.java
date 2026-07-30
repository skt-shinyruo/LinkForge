package com.linkforge.analytics.interfaces.web;

/**
 * 一个短链维度值在查询日期范围内的聚合统计 HTTP 响应。
 *
 * <p>{@code ratio} 的分母是同一 {@code type} 维度的总 PV，而不是该短链全部访问 PV。多日
 * {@code uv} 由日 UV 聚合而来，跨日或重复投影都会使其不等同于严格的区间去重人数。
 *
 * @param value 经过采集规范化后存储的维度值
 * @param pv 该维度值的访问次数
 * @param uv 该维度值的近似唯一访问量
 * @param ratio 该维度值 PV 占当前维度总 PV 的比例；无分母时为 {@code 0.0}
 */
public record DimensionStatHttpResponse(String value, long pv, long uv, double ratio) {
}
