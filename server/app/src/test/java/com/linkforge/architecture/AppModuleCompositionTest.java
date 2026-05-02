package com.linkforge.architecture;

import com.linkforge.LinkForgeApplication;
import com.linkforge.accounts.application.AccountsApplicationConfig;
import com.linkforge.accounts.infrastructure.AccountsInfrastructureConfig;
import com.linkforge.accounts.interfaces.AccountsInterfacesConfig;
import com.linkforge.accounts.runtime.AccountsRuntimeModule;
import com.linkforge.analytics.application.AnalyticsApplicationConfig;
import com.linkforge.analytics.infrastructure.AnalyticsInfrastructureConfig;
import com.linkforge.analytics.interfaces.AnalyticsInterfacesConfig;
import com.linkforge.analytics.runtime.AnalyticsRuntimeModule;
import com.linkforge.foundation.runtime.FoundationRuntimeModule;
import com.linkforge.foundation.runtime.persistence.FoundationRuntimePersistenceModule;
import com.linkforge.foundation.runtime.security.FoundationRuntimeSecurityModule;
import com.linkforge.foundation.runtime.startup.FoundationRuntimeStartupModule;
import com.linkforge.foundation.runtime.tx.FoundationRuntimeTxModule;
import com.linkforge.foundation.runtime.web.FoundationRuntimeWebModule;
import com.linkforge.governance.application.GovernanceApplicationConfig;
import com.linkforge.governance.infrastructure.GovernanceInfrastructureConfig;
import com.linkforge.governance.interfaces.GovernanceInterfacesConfig;
import com.linkforge.governance.runtime.GovernanceRuntimeModule;
import com.linkforge.platform.application.PlatformApplicationConfig;
import com.linkforge.platform.infrastructure.PlatformInfrastructureConfig;
import com.linkforge.platform.interfaces.PlatformInterfacesConfig;
import com.linkforge.platform.runtime.PlatformRuntimeModule;
import com.linkforge.redirect.application.RedirectApplicationConfig;
import com.linkforge.redirect.infrastructure.RedirectInfrastructureConfig;
import com.linkforge.redirect.interfaces.RedirectInterfacesConfig;
import com.linkforge.redirect.runtime.RedirectRuntimeModule;
import com.linkforge.shortlink.application.ShortlinkApplicationConfig;
import com.linkforge.shortlink.infrastructure.ShortlinkInfrastructureConfig;
import com.linkforge.shortlink.interfaces.ShortlinkInterfacesConfig;
import com.linkforge.shortlink.runtime.ShortlinkRuntimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class AppModuleCompositionTest {

    @Test
    void app_should_import_explicit_context_exports_only() {
        Import importAnnotation = LinkForgeApplication.class.getAnnotation(Import.class);
        assertThat(importAnnotation).isNotNull();
        assertThat(importAnnotation.value()).containsExactlyInAnyOrder(
                FoundationRuntimeModule.class,
                AccountsRuntimeModule.class,
                ShortlinkRuntimeModule.class,
                RedirectRuntimeModule.class,
                AnalyticsRuntimeModule.class,
                PlatformRuntimeModule.class,
                GovernanceRuntimeModule.class
        );
    }

    @Test
    void foundation_runtime_module_should_import_explicit_runtime_modules() {
        assertExplicitImports(
                FoundationRuntimeModule.class,
                FoundationRuntimeWebModule.class,
                FoundationRuntimeSecurityModule.class,
                FoundationRuntimePersistenceModule.class,
                FoundationRuntimeTxModule.class,
                FoundationRuntimeStartupModule.class
        );
    }

    @Test
    void accounts_runtime_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                AccountsRuntimeModule.class,
                AccountsApplicationConfig.class,
                AccountsInfrastructureConfig.class,
                AccountsInterfacesConfig.class
        );
    }

    @Test
    void shortlink_runtime_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                ShortlinkRuntimeModule.class,
                ShortlinkApplicationConfig.class,
                ShortlinkInfrastructureConfig.class,
                ShortlinkInterfacesConfig.class
        );
    }

    @Test
    void redirect_runtime_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                RedirectRuntimeModule.class,
                RedirectApplicationConfig.class,
                RedirectInfrastructureConfig.class,
                RedirectInterfacesConfig.class
        );
    }

    @Test
    void analytics_runtime_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                AnalyticsRuntimeModule.class,
                AnalyticsApplicationConfig.class,
                AnalyticsInfrastructureConfig.class,
                AnalyticsInterfacesConfig.class
        );
    }

    @Test
    void platform_runtime_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                PlatformRuntimeModule.class,
                PlatformApplicationConfig.class,
                PlatformInfrastructureConfig.class,
                PlatformInterfacesConfig.class
        );
    }

    @Test
    void governance_runtime_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                GovernanceRuntimeModule.class,
                GovernanceApplicationConfig.class,
                GovernanceInfrastructureConfig.class,
                GovernanceInterfacesConfig.class
        );
    }

    private static void assertExplicitImports(Class<?> moduleClass, Class<?>... expectedImports) {
        assertThat(moduleClass.getAnnotation(ComponentScan.class)).isNull();

        Import importAnnotation = moduleClass.getAnnotation(Import.class);
        assertThat(importAnnotation).isNotNull();
        assertThat(importAnnotation.value()).containsExactlyInAnyOrder(expectedImports);
    }

}
