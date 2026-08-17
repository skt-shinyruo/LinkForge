package com.linkforge;

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

/**
 * LinkForge 后端单体的 Spring Boot 入口。
 *
 * <p>各上下文的组件都位于同一个应用包树中，使用 Spring 的默认扫描即可完成装配；共享配置绑定仍在
 * 此处集中声明。</p>
 */
@SpringBootApplication
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
