package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Snowflake 节点配置。
 *
 * <p>{@code workerId} 与 {@code datacenterId} 各占 5 bit，合法范围都是 0..31；多实例必须使用
 * 不同组合。默认 1/1 只方便本地运行，strict/prod 启动门禁会拒绝该默认组合。</p>
 */
@ConfigurationProperties(prefix = "app.id")
public class IdProperties {

    /** Snowflake worker 节点号，范围 0..31；默认 1 仅用于本地兼容。 */
    private long workerId = 1;

    /** Snowflake datacenter 节点号，范围 0..31；与 workerId 的组合必须在部署内唯一。 */
    private long datacenterId = 1;

    public long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(long workerId) {
        this.workerId = workerId;
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public void setDatacenterId(long datacenterId) {
        this.datacenterId = datacenterId;
    }
}
