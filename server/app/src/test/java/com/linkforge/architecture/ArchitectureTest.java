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

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.linkforge");
    private static final String FORBIDDEN_GOVERNANCE_ROLES_REFERENCE = "com.linkforge.accounts.domain.Roles";
    private static final List<BoundedContext> BOUNDED_CONTEXTS = List.of(
            new BoundedContext("accounts", "com.linkforge.accounts.."),
            new BoundedContext("shortlink", "com.linkforge.shortlink.."),
            new BoundedContext("redirect", "com.linkforge.redirect.."),
            new BoundedContext("analytics", "com.linkforge.analytics.."),
            new BoundedContext("platform", "com.linkforge.platform.."),
            new BoundedContext("governance", "com.linkforge.governance..")
    );

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
    void application_should_not_depend_on_stream_or_transport_specific_types() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..application..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.io.InputStream")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.io.OutputStream")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.web.multipart.MultipartFile")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("jakarta.servlet.http.HttpServletRequest")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("jakarta.servlet.http.HttpServletResponse");
        rule.check(CLASSES);
    }

    @Test
    void task1_targeted_application_packages_should_not_depend_on_hidden_runtime_context() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage(
                        "com.linkforge.accounts.application..",
                        "com.linkforge.shortlink.application..",
                        "com.linkforge.platform.application..",
                        "com.linkforge.governance.application..",
                        "com.linkforge.redirect.application.."
                )
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.linkforge.foundation.web..",
                        "com.linkforge.foundation.runtime..",
                        "org.springframework.transaction.support..",
                        "org.springframework.security.core.context.."
                )
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.linkforge.foundation.runtime.security.AuthContext")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.linkforge.foundation.runtime.tx.AfterCommit");
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
        List<String> violations = new ArrayList<>();
        for (BoundedContext from : BOUNDED_CONTEXTS) {
            for (BoundedContext to : BOUNDED_CONTEXTS) {
                if (from == to) {
                    continue;
                }
                ArchRule edgeRule = noClasses()
                        .that().resideInAnyPackage(from.packagePattern())
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(to.packagePattern());
                try {
                    edgeRule.check(CLASSES);
                } catch (AssertionError failure) {
                    violations.add(from.name() + " -> " + to.name() + System.lineSeparator() + failure.getMessage());
                }
            }
        }
        assertThat(violations)
                .withFailMessage(
                        "Bounded-context dependency matrix violations:%n%n%s",
                        String.join(System.lineSeparator() + System.lineSeparator(), violations)
                )
                .isEmpty();
    }

    @Test
    void governance_service_source_should_not_import_accounts_roles() throws Exception {
        // ArchUnit can miss constant-only dependencies after javac inlines static final String fields.
        // Keep this source-level guard until governance stops importing accounts-domain Roles.
        Path governanceService = resolveFromCurrentWorkspace(
                "governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java",
                "server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java"
        );
        String source = Files.readString(governanceService);
        assertThat(source).doesNotContain(FORBIDDEN_GOVERNANCE_ROLES_REFERENCE);
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

    private static Path resolveFromCurrentWorkspace(String... relativePaths) {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path cursor = cwd; cursor != null; cursor = cursor.getParent()) {
            for (String relativePath : relativePaths) {
                Path candidate = cursor.resolve(relativePath).normalize();
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Could not resolve any of " + String.join(", ", relativePaths) + " from " + cwd);
    }

    private record BoundedContext(String name, String packagePattern) {
    }
}
