package com.linkforge.app.startup;

import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.foundation.config.CorsProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.config.StartupValidation;
import com.linkforge.foundation.runtime.startup.StartupCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用组合根的启动期配置门禁。
 *
 * <p>Foundation 基础检查与各上下文 {@link StartupCheck} 的结果会被收集后一次性失败，避免只因装配顺序不同
 * 而遗漏错误。strict 模式由 {@code prod} profile 或 {@code app.strict-config=true} 触发；检查器只能追加
 * 错误，最终异常由本类统一抛出。</p>
 */
@Component
public class AppStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppStartupValidator.class);

    private final Environment env;
    private final CoreProperties coreProperties;
    private final IdProperties idProperties;
    private final List<StartupCheck> startupChecks;

    public AppStartupValidator(
            Environment env,
            CoreProperties coreProperties,
            IdProperties idProperties,
            List<StartupCheck> startupChecks
    ) {
        this.env = env;
        this.coreProperties = coreProperties;
        this.idProperties = idProperties;
        this.startupChecks = startupChecks == null ? List.of() : List.copyOf(startupChecks);
    }

    /**
     * 执行所有已注册启动检查。
     *
     * @throws IllegalStateException 存在任意配置错误时，消息包含所有已收集条目
     */
    @Override
    public void run(ApplicationArguments args) {
        boolean strict = env.acceptsProfiles(Profiles.of("prod"))
                || env.getProperty("app.strict-config", Boolean.class, false);

        List<String> errors = new ArrayList<>();

        StartupValidation.validateIdBasics(idProperties, strict, log, errors);

        // 管理 API 的 shortUrl 拼接需要公开 baseUrl，不能从单个请求的 Host 头兜底。
        if (StartupValidation.isBlank(coreProperties == null ? null : coreProperties.getBaseUrl())) {
            errors.add("app.base-url 不能为空（用于拼接 shortUrl）");
        }

        for (StartupCheck startupCheck : startupChecks) {
            startupCheck.validate(strict, errors);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("启动配置校验失败: " + String.join("; ", errors));
        }
    }
}
