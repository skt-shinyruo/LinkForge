package com.linkforge.app.observability;

import com.linkforge.foundation.observability.OperationalMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** 将模块运行指标适配到应用统一的 Micrometer registry。 */
@Component
public class MicrometerOperationalMetrics implements OperationalMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<MeterKey, AtomicLong> gauges = new ConcurrentHashMap<>();

    public MicrometerOperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void increment(String name, String... tags) {
        add(name, 1L, tags);
    }

    @Override
    public void add(String name, long amount, String... tags) {
        if (amount <= 0) {
            return;
        }
        Counter.builder(name).tags(tags).register(registry).increment(amount);
    }

    @Override
    public void record(String name, Duration duration, String... tags) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        Timer.builder(name).tags(tags).register(registry).record(duration);
    }

    @Override
    public void set(String name, long value, String... tags) {
        MeterKey key = new MeterKey(name, List.of(tags == null ? new String[0] : tags));
        gauges.computeIfAbsent(key, ignored -> {
            AtomicLong state = new AtomicLong();
            Gauge.builder(name, state, AtomicLong::get).tags(tags).register(registry);
            return state;
        }).set(value);
    }

    private record MeterKey(String name, List<String> tags) {
    }
}
