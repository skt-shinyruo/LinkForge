package com.linkforge.foundation.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class FoundationSharedArchitectureTest {

    /**
     * Foundation is the in-repo technical library shared inside the modular monolith.
     *
     * <p>We intentionally narrow the scan scope to the library-only packages instead of importing the
     * whole `com.linkforge.foundation` namespace, because runtime beans now live under
     * `com.linkforge.foundation.runtime..` and would create false positives for the shared-library guardrails.
     */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                    "com.linkforge.foundation.config",
                    "com.linkforge.foundation.id",
                    "com.linkforge.foundation.tx",
                    "com.linkforge.foundation.util"
            );

    private static final String[] SHARED_PACKAGES = {
            "com.linkforge.foundation.config..",
            "com.linkforge.foundation.id..",
            "com.linkforge.foundation.tx..",
            "com.linkforge.foundation.util.."
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
     * Guardrail: foundation library packages must not become an implicit runtime module.
     *
     * <p>If these shared packages start defining runtime beans, unrelated application modules can pick
     * them up via component scanning and module boundaries become accidental instead of explicit.
     * Runtime-owned foundation beans must live under `com.linkforge.foundation.runtime..` instead.
     *
     * <p>Important: detect by annotation <em>type names</em> (strings), so the test keeps compiling even
     * if foundation stays lean and avoids direct runtime-framework dependencies.
     */
    @Test
    void foundation_library_packages_should_not_define_runtime_spring_beans() {
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
                .because("foundation shared packages must be pure libraries (no runtime Spring beans)");
        rule.check(CLASSES);
    }

    @Test
    void foundation_library_packages_should_not_depend_on_web_or_redis_packages() {
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
