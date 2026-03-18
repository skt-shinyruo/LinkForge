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
 * Bootstrap-layer startup validation.
 *
 * <p>Rationale: keep configuration guardrails in the executable {@code app} module,
 * instead of scattering them inside domain bounded contexts.</p>
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

    @Override
    public void run(ApplicationArguments args) {
        boolean strict = env.acceptsProfiles(Profiles.of("prod"))
                || env.getProperty("app.strict-config", Boolean.class, false);

        List<String> errors = new ArrayList<>();

        StartupValidation.validateIdBasics(idProperties, strict, log, errors);

        // API shortlink requires baseUrl for shortUrl composition.
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
