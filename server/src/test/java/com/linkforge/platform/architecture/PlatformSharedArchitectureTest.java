package com.linkforge.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class PlatformSharedArchitectureTest {

    /**
     * In the pre-monolith multi-module layout, this test lived in the "platform-shared" module, so the
     * classpath only contained shared classes and importing {@code com.linkforge} was safe.
     *
     * <p>After merging into a single Maven module, importing {@code com.linkforge} would pull in the
     * whole monolith (API/Edge/runtime), causing false positives. Keep the guardrail by narrowing the
     * scan scope to the shared-only packages.
     */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                    "com.linkforge.platform.config",
                    "com.linkforge.platform.id",
                    "com.linkforge.platform.tx",
                    "com.linkforge.platform.util",
                    "com.linkforge.analytics.service"
            );

    private static final String[] SHARED_PACKAGES = {
            "com.linkforge.platform.config..",
            "com.linkforge.platform.id..",
            "com.linkforge.platform.tx..",
            "com.linkforge.platform.util..",
            "com.linkforge.analytics.service.."
    };

    private static final String SPRING_COMPONENT = "org.springframework.stereotype.Component";
    private static final String SPRING_SERVICE = "org.springframework.stereotype.Service";
    private static final String SPRING_REPOSITORY = "org.springframework.stereotype.Repository";
    private static final String SPRING_CONFIGURATION = "org.springframework.context.annotation.Configuration";
    private static final String SPRING_REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String SPRING_CONTROLLER = "org.springframework.stereotype.Controller";
    private static final String SPRING_REST_CONTROLLER_ADVICE = "org.springframework.web.bind.annotation.RestControllerAdvice";
    private static final String SPRING_CONTROLLER_ADVICE = "org.springframework.web.bind.annotation.ControllerAdvice";

    /**
     * Guardrail: platform-shared is a core SSOT library and must not become an implicit runtime module.
     * <p>
     * Rationale: if platform defines runtime beans, both API and Edge can be affected implicitly via
     * component scanning, making boundaries blurry and changes risky.
     *
     * <p>Important: detect by annotation <em>type names</em> (strings), so the test keeps compiling even
     * after we slim down platform dependencies in later tasks.
     */
    @Test
    void platform_shared_should_not_define_runtime_spring_beans() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage(SHARED_PACKAGES)
                .should()
                .beAnnotatedWith(SPRING_COMPONENT)
                .orShould()
                .beAnnotatedWith(SPRING_SERVICE)
                .orShould()
                .beAnnotatedWith(SPRING_REPOSITORY)
                .orShould()
                .beAnnotatedWith(SPRING_CONFIGURATION)
                .orShould()
                .beAnnotatedWith(SPRING_REST_CONTROLLER)
                .orShould()
                .beAnnotatedWith(SPRING_CONTROLLER)
                .orShould()
                .beAnnotatedWith(SPRING_REST_CONTROLLER_ADVICE)
                .orShould()
                .beAnnotatedWith(SPRING_CONTROLLER_ADVICE)
                .because("platform-shared must be a pure shared library (no runtime Spring beans)");
        rule.check(CLASSES);
    }

    @Test
    void platform_shared_should_not_depend_on_web_or_redis_packages() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage(SHARED_PACKAGES)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "jakarta.servlet..",
                        "org.springframework.web..",
                        "org.springframework.data.redis.."
                );
        rule.check(CLASSES);
    }
}
