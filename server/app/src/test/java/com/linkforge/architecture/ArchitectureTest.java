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
                .resideInAnyPackage("com.linkforge.shortlink..", "com.linkforge.redirect..", "com.linkforge.analytics..")
                .check(CLASSES);

        noClasses()
                .that().resideInAnyPackage("com.linkforge.shortlink..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.linkforge.accounts..", "com.linkforge.redirect..", "com.linkforge.analytics..")
                .check(CLASSES);

        noClasses()
                .that().resideInAnyPackage("com.linkforge.redirect..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.linkforge.accounts..", "com.linkforge.shortlink..", "com.linkforge.analytics..")
                .check(CLASSES);

        noClasses()
                .that().resideInAnyPackage("com.linkforge.analytics..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.linkforge.accounts..", "com.linkforge.shortlink..", "com.linkforge.redirect..")
                .check(CLASSES);
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
                        "com.linkforge.analytics.."
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
