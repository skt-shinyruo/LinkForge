package com.linkforge;

import com.linkforge.accounts.runtime.AccountsRuntimeModule;
import com.linkforge.analytics.runtime.AnalyticsRuntimeModule;
import com.linkforge.foundation.runtime.FoundationRuntimeModule;
import com.linkforge.governance.runtime.GovernanceRuntimeModule;
import com.linkforge.platform.runtime.PlatformRuntimeModule;
import com.linkforge.redirect.runtime.RedirectRuntimeModule;
import com.linkforge.shortlink.runtime.ShortlinkRuntimeModule;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.CorsProperties;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.foundation.config.EdgeProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.foundation.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * LinkForge 后端单体的 Spring Boot 入口。
 *
 * <p>它显式组合管理 API 与 Redirect Edge 所需的各上下文运行时模块，并集中启用共享配置绑定。业务上下文
 * 不依赖根包扫描相互发现，新增上下文应通过自身 runtime module 由此处显式导入。</p>
 */
@SpringBootApplication(scanBasePackages = {
        "com.linkforge.app.api",
        "com.linkforge.app.config",
        "com.linkforge.app.scheduling",
        "com.linkforge.app.security",
        "com.linkforge.app.startup"
})
@Import({
        FoundationRuntimeModule.class,
        AccountsRuntimeModule.class,
        ShortlinkRuntimeModule.class,
        RedirectRuntimeModule.class,
        AnalyticsRuntimeModule.class,
        PlatformRuntimeModule.class,
        GovernanceRuntimeModule.class
})
@EnableConfigurationProperties({
        CoreProperties.class,
        IdProperties.class,
        SecurityProperties.class,
        CorsProperties.class,
        RedirectProperties.class,
        AnalyticsProperties.class,
        EdgeProperties.class
})
public class LinkForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkForgeApplication.class, args);
    }
}
