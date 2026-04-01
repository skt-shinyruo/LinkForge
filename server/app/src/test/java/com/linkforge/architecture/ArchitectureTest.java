package com.linkforge.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.linkforge");

    @Test
    void interfaces_should_not_depend_on_repositories() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..interfaces..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..repo..");
        rule.check(CLASSES);
    }

    @Test
    void interfaces_should_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..interfaces..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..");
        rule.check(CLASSES);
    }

    @Test
    void only_application_or_infrastructure_should_access_repositories() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackages("..application..", "..infrastructure..", "..repo..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..repo..");
        rule.check(CLASSES);
    }

    @Test
    void controllers_should_reside_in_interfaces_layer() {
        classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAnyPackage("..interfaces..")
                .check(CLASSES);

        classes()
                .that().areAnnotatedWith(Controller.class)
                .should().resideInAnyPackage("..interfaces..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void application_and_domain_should_not_depend_on_interfaces() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..application..", "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..interfaces..");
        rule.check(CLASSES);
    }

    @Test
    void accounts_application_should_not_depend_on_accounts_infrastructure() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge.accounts.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.linkforge.accounts.infrastructure..");
        rule.check(CLASSES);
    }

    @Test
    void accounts_application_should_not_depend_on_redis_or_security_crypto() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge.accounts.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.data.redis..",
                        "org.springframework.security.crypto.."
                );
        rule.check(CLASSES);
    }

    @Test
    void application_and_domain_should_not_depend_on_web_or_servlet() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..application..", "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "jakarta.servlet..",
                        "org.springframework.web..",
                        "org.springframework.http.."
                );
        rule.check(CLASSES);
    }

    @Test
    void domain_should_not_depend_on_outer_layers() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..application..", "..infrastructure..", "..interfaces..");
        rule.check(CLASSES);
    }

    @Test
    void redirect_bounded_context_should_not_use_jdbc_or_sql_packages() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge.redirect..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.jdbc..",
                        "javax.sql..",
                        "java.sql.."
                );
        rule.check(CLASSES);
    }

    @Test
    void bounded_contexts_should_not_depend_on_each_other_directly() {
        noClasses()
                .that().resideInAnyPackage("com.linkforge.accounts..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.linkforge.shortlink..",
                        "com.linkforge.redirect..",
                        "com.linkforge.analytics.."
                )
                .check(CLASSES);

        noClasses()
                .that().resideInAnyPackage("com.linkforge.shortlink..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.linkforge.accounts..",
                        "com.linkforge.redirect..",
                        "com.linkforge.analytics.."
                )
                .check(CLASSES);

        noClasses()
                .that().resideInAnyPackage("com.linkforge.redirect..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.linkforge.accounts..", "com.linkforge.shortlink..", "com.linkforge.analytics..")
                .check(CLASSES);

        noClasses()
                .that().resideInAnyPackage("com.linkforge.analytics..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.linkforge.accounts..",
                        "com.linkforge.shortlink..",
                        "com.linkforge.redirect.."
                )
                .check(CLASSES);
    }

    @Test
    void accounts_should_not_depend_on_platform_application() {
        noClasses()
                .that().resideInAnyPackage("com.linkforge.accounts..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.linkforge.platform.application..")
                .check(CLASSES);
    }

    @Test
    void shortlink_should_not_depend_on_platform_or_governance_application() {
        noClasses()
                .that().resideInAnyPackage("com.linkforge.shortlink..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.linkforge.platform.application..",
                        "com.linkforge.governance.application.."
                )
                .check(CLASSES);
    }

    @Test
    void analytics_should_not_depend_on_governance_application() {
        noClasses()
                .that().resideInAnyPackage("com.linkforge.analytics..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.linkforge.governance.application..")
                .check(CLASSES);
    }

    @Test
    void governance_should_not_depend_on_accounts() {
        noClasses()
                .that().resideInAnyPackage("com.linkforge.governance..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.linkforge.accounts..")
                .check(CLASSES);
    }

    @Test
    void governance_service_source_should_not_import_accounts_roles() throws Exception {
        // ArchUnit can miss constant-only dependencies after javac inlines static final String fields.
        // Keep this source-level guard until governance stops importing accounts-domain Roles.
        Path governanceService = Path.of(
                "governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java"
        );
        if (!Files.exists(governanceService)) {
            governanceService = Path.of(
                    "../governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java"
            );
        }
        assertThat(Files.exists(governanceService)).isTrue();
        String source = Files.readString(governanceService);
        assertThat(source).doesNotContain("import com.linkforge.accounts.domain.Roles;");
    }

    @Test
    void foundation_should_not_depend_on_bounded_contexts() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge.foundation..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.linkforge.accounts..",
                        "com.linkforge.shortlink..",
                        "com.linkforge.redirect..",
                        "com.linkforge.analytics..",
                        "com.linkforge.platform..",
                        "com.linkforge.governance.."
                );
        rule.check(CLASSES);
    }

    @Test
    void contracts_should_not_depend_on_spring_or_runtime_jakarta_packages() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge.contract..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "jakarta.persistence..",
                        "org.springframework.data.."
                );
        rule.check(CLASSES);
    }
}
