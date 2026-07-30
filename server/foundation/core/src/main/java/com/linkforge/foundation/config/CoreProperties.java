package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 与具体业务上下文无关的应用基础配置。
 *
 * <p>{@code baseUrl} 是生成管理 API 返回的绝对短链地址时使用的公开根地址。它应是部署后用户实际访问的
 * scheme/host/可选前缀，不应从未经验证的 HTTP Host 头推导；启动门禁要求其非空，但 URL 格式和末尾斜杠
 * 的规范化由使用方负责。</p>
 */
@ConfigurationProperties(prefix = "app")
public class CoreProperties {

    /**
     * 用于拼接绝对短链地址的公开根 URL。
     */
    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
