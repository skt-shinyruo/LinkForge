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
    private static final String FORBIDDEN_APP_ACCOUNTS_APPLICATION_REFERENCE = "com.linkforge.accounts.application.";
    private static final String FORBIDDEN_APP_ACCOUNTS_INFRASTRUCTURE_REFERENCE = "com.linkforge.accounts.infrastructure.";
    private static final String FORBIDDEN_APP_ACCOUNTS_DOMAIN_ROLES_REFERENCE = "com.linkforge.accounts.domain.Roles";
    private static final List<BoundedContext> BOUNDED_CONTEXTS = List.of(
            new BoundedContext("accounts", "com.linkforge.accounts"),
            new BoundedContext("shortlink", "com.linkforge.shortlink"),
            new BoundedContext("redirect", "com.linkforge.redirect"),
            new BoundedContext("analytics", "com.linkforge.analytics"),
            new BoundedContext("platform", "com.linkforge.platform"),
            new BoundedContext("governance", "com.linkforge.governance")
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
                .and()
                .resideOutsideOfPackages(
                        "com.linkforge.accounts.interfaces",
                        "com.linkforge.shortlink.interfaces",
                        "com.linkforge.redirect.interfaces",
                        "com.linkforge.analytics.interfaces",
                        "com.linkforge.platform.interfaces",
                        "com.linkforge.governance.interfaces"
                )
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
    void controllers_should_not_depend_on_mappers_repositories_or_infrastructure_adapters() {
        ArchRule rule = noClasses()
                .that()
                .areAnnotatedWith(RestController.class)
                .or()
                .areAnnotatedWith(Controller.class)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..infrastructure..",
                        "..persistence.mapper..",
                        "..persistence.repository..",
                        "..repo.."
                );
        rule.check(CLASSES);
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
    void domain_should_not_depend_on_runtime_frameworks_or_persistence_tools() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "jakarta.persistence..",
                        "org.mybatis..",
                        "org.apache.ibatis..",
                        "org.springframework.data..",
                        "org.springframework.security.."
                );
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
    void bounded_context_cross_dependencies_should_not_reach_inner_layers() {
        List<String> violations = new ArrayList<>();
        for (BoundedContext from : BOUNDED_CONTEXTS) {
            for (BoundedContext to : BOUNDED_CONTEXTS) {
                if (from == to) {
                    continue;
                }
                ArchRule edgeRule = noClasses()
                        .that().resideInAnyPackage(from.packagePattern())
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(to.innerLayerPatterns());
                try {
                    edgeRule.check(CLASSES);
                } catch (AssertionError failure) {
                    violations.add(from.name() + " -> " + to.name() + System.lineSeparator() + failure.getMessage());
                }
            }
        }
        assertThat(violations)
                .withFailMessage(
                        "Bounded-context inner-layer dependency violations:%n%n%s",
                        String.join(System.lineSeparator() + System.lineSeparator(), violations)
                )
                .isEmpty();
    }

    @Test
    void non_shortlink_contexts_should_depend_on_shortlink_contracts_only() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage(
                        "com.linkforge.redirect..",
                        "com.linkforge.analytics..",
                        "com.linkforge.platform..",
                        "com.linkforge.governance..",
                        "com.linkforge.accounts..",
                        "com.linkforge.app.."
                )
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.linkforge.shortlink.domain..",
                        "com.linkforge.shortlink.application..",
                        "com.linkforge.shortlink.infrastructure..",
                        "com.linkforge.shortlink.interfaces.."
                );
        rule.check(CLASSES);
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
    void app_security_source_should_not_gain_new_accounts_internal_imports() throws Exception {
        Path securityDir = resolveFromCurrentWorkspace(
                "app/src/main/java/com/linkforge/app/security",
                "server/app/src/main/java/com/linkforge/app/security"
        );

        List<Path> sources;
        try (var stream = Files.walk(securityDir)) {
            sources = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }

        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            String text = Files.readString(source);
            if (text.contains(FORBIDDEN_APP_ACCOUNTS_APPLICATION_REFERENCE)
                    || text.contains(FORBIDDEN_APP_ACCOUNTS_INFRASTRUCTURE_REFERENCE)
                    || text.contains(FORBIDDEN_APP_ACCOUNTS_DOMAIN_ROLES_REFERENCE)) {
                violations.add(source.toString());
            }
        }

        assertThat(violations)
                .withFailMessage("New app/security accounts-internal imports are forbidden: %s", violations)
                .isEmpty();
    }

    @Test
    void shortlink_create_update_application_code_should_dispatch_domain_events_instead_of_publishing_directly() throws Exception {
        Path shortlinkCommandDir = resolveFromCurrentWorkspace(
                "shortlink/application/src/main/java/com/linkforge/shortlink/application/command",
                "server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command"
        );
        Path shortlinkApprovalDir = resolveFromCurrentWorkspace(
                "shortlink/application/src/main/java/com/linkforge/shortlink/application/approval",
                "server/shortlink/application/src/main/java/com/linkforge/shortlink/application/approval"
        );

        List<Path> sources;
        try (var commandStream = Files.walk(shortlinkCommandDir);
             var approvalStream = Files.walk(shortlinkApprovalDir)) {
            sources = java.util.stream.Stream
                    .concat(commandStream, approvalStream)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }

        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            String text = Files.readString(source);
            if (text.contains("ShortLinkEventPublisher")
                    || text.contains(".created(")
                    || text.contains(".updated(")) {
                violations.add(source.toString());
            }
        }

        assertThat(violations)
                .withFailMessage("Shortlink application code must dispatch aggregate domain events instead of directly publishing create/update events: %s", violations)
                .isEmpty();
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
    void contracts_should_not_depend_on_bounded_context_inner_layers() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge.contract..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.linkforge.accounts.domain..",
                        "com.linkforge.accounts.application..",
                        "com.linkforge.accounts.infrastructure..",
                        "com.linkforge.accounts.interfaces..",
                        "com.linkforge.shortlink.domain..",
                        "com.linkforge.shortlink.application..",
                        "com.linkforge.shortlink.infrastructure..",
                        "com.linkforge.shortlink.interfaces..",
                        "com.linkforge.redirect.domain..",
                        "com.linkforge.redirect.application..",
                        "com.linkforge.redirect.infrastructure..",
                        "com.linkforge.redirect.interfaces..",
                        "com.linkforge.analytics.domain..",
                        "com.linkforge.analytics.application..",
                        "com.linkforge.analytics.infrastructure..",
                        "com.linkforge.analytics.interfaces..",
                        "com.linkforge.platform.domain..",
                        "com.linkforge.platform.application..",
                        "com.linkforge.platform.infrastructure..",
                        "com.linkforge.platform.interfaces..",
                        "com.linkforge.governance.domain..",
                        "com.linkforge.governance.application..",
                        "com.linkforge.governance.infrastructure..",
                        "com.linkforge.governance.interfaces.."
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

    private record BoundedContext(String name, String basePackage) {

        String packagePattern() {
            return basePackage + "..";
        }

        String[] innerLayerPatterns() {
            return new String[]{
                    basePackage + ".domain..",
                    basePackage + ".application..",
                    basePackage + ".infrastructure..",
                    basePackage + ".interfaces.."
            };
        }
    }
}
