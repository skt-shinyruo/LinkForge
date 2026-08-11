package com.linkforge.accounts.application;

import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 可重复的认证摘要微基准：用于上线前比较 HMAC verifier 与历史慢哈希路径的 CPU 成本。
 *
 * <p>它不把机器相关的绝对耗时作为 CI 门禁，只报告固定工作量是否完整执行；运行时可用
 * {@code -Dgroups=benchmark} 单独筛选该测试并记录外部耗时。</p>
 */
@Tag("benchmark")
class ApiKeySecretCodecBenchmarkTest {

    @Test
    void hmacVerifier_shouldCompleteFixedWorkload() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret("benchmark-jwt-secret-please-change-32-bytes");
        properties.getApiKey().setHmacPepper("benchmark-api-key-pepper");
        AccountsPasswordHasher legacy = new AccountsPasswordHasher() {
            @Override
            public String encode(String raw) {
                return raw;
            }

            @Override
            public boolean matches(String raw, String encoded) {
                return raw.equals(encoded);
            }
        };
        ApiKeySecretCodec codec = new ApiKeySecretCodec(legacy, properties);
        String encoded = codec.encode("benchmark-secret");

        int iterations = 100_000;
        long startedAt = System.nanoTime();
        int matches = 0;
        for (int i = 0; i < iterations; i++) {
            if (codec.matches("benchmark-secret", encoded)) {
                matches++;
            }
        }
        long elapsedNanos = System.nanoTime() - startedAt;

        assertThat(matches).isEqualTo(iterations);
        System.out.printf("api-key HMAC benchmark: iterations=%d, elapsedMs=%.2f%n",
                iterations, elapsedNanos / 1_000_000.0d);
    }
}
