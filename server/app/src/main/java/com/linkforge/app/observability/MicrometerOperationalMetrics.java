package com.linkforge.app.observability;

import com.linkforge.foundation.observability.OperationalMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
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
        String[] safeTags = validateTags(tags);
        Counter.builder(name).tags(safeTags).register(registry).increment(amount);
    }

    @Override
    public void record(String name, Duration duration, String... tags) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        String[] safeTags = validateTags(tags);
        Timer.builder(name).tags(safeTags).register(registry).record(duration);
    }

    @Override
    public void set(String name, long value, String... tags) {
        String[] safeTags = validateTags(tags);
        MeterKey key = new MeterKey(name, List.copyOf(Arrays.asList(safeTags)));
        gauges.computeIfAbsent(key, ignored -> {
            AtomicLong state = new AtomicLong();
            Gauge.builder(name, state, AtomicLong::get).tags(safeTags).register(registry);
            return state;
        }).set(value);
    }

    private static String[] validateTags(String[] tags) {
        if (tags == null || tags.length == 0) {
            return new String[0];
        }
        if (tags.length % 2 != 0) {
            throw new IllegalArgumentException("metric tags must be key/value pairs");
        }
        return Arrays.copyOf(tags, tags.length);
    }

    private record MeterKey(String name, List<String> tags) {
    }
}
