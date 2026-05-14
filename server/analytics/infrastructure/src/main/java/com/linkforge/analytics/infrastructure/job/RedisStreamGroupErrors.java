package com.linkforge.analytics.infrastructure.job;

final class RedisStreamGroupErrors {

    private RedisStreamGroupErrors() {
    }

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
