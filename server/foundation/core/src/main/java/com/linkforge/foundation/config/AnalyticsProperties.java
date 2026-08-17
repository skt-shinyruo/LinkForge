package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime settings for the direct Redis aggregate path. */
@ConfigurationProperties(prefix = "app.analytics")
public class AnalyticsProperties {

    private String salt;
    private long redisKeyTtlDays;
    private int flushBackfillDays = 7;
    private Events events = new Events();

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public long getRedisKeyTtlDays() {
        return redisKeyTtlDays;
    }

    public void setRedisKeyTtlDays(long redisKeyTtlDays) {
        this.redisKeyTtlDays = redisKeyTtlDays;
    }

    public int getFlushBackfillDays() {
        return flushBackfillDays;
    }

    public void setFlushBackfillDays(int flushBackfillDays) {
        this.flushBackfillDays = flushBackfillDays;
    }

    public Events getEvents() {
        return events;
    }

    public void setEvents(Events events) {
        this.events = events;
    }

    /** Keeps the existing config path for the redirect fail-open switch. */
    public static class Events {
        private boolean failOpen = true;

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }
    }
}
