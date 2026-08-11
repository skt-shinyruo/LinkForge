package com.linkforge.foundation.observability;

import java.time.Duration;

/**
 * 业务模块报告低基数运行指标的窄端口。
 *
 * <p>标签必须按 key/value 成对传入，且不得包含租户、短链、请求 ID 等高基数字段。组合根负责接入具体
 * 指标系统；测试和未启用观测后端的运行环境可使用 {@link #noop()}。</p>
 */
public interface OperationalMetrics {

    void increment(String name, String... tags);

    void add(String name, long amount, String... tags);

    void record(String name, Duration duration, String... tags);

    void set(String name, long value, String... tags);

    static OperationalMetrics noop() {
        return NoopHolder.INSTANCE;
    }

    final class NoopHolder {
        private static final OperationalMetrics INSTANCE = new OperationalMetrics() {
            @Override
            public void increment(String name, String... tags) {
            }

            @Override
            public void add(String name, long amount, String... tags) {
            }

            @Override
            public void record(String name, Duration duration, String... tags) {
            }

            @Override
            public void set(String name, long value, String... tags) {
            }
        };

        private NoopHolder() {
        }
    }
}
