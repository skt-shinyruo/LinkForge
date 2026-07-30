package com.linkforge.analytics.infrastructure.job;

/**
 * Redis Stream consumer group 创建中的兼容性错误识别。
 *
 * <p>Spring Data Redis 可能将 Redis 的 {@code BUSYGROUP} 包装在多层异常中；已有 group 是正常并发启动
 * 情况，不应使任务失败。其他错误仍由调用方按本轮失败处理。</p>
 */
final class RedisStreamGroupErrors {

    private RedisStreamGroupErrors() {
    }

    /** 返回异常链是否表示 consumer group 已存在。 */
    static boolean isBusyGroup(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("busygroup")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
