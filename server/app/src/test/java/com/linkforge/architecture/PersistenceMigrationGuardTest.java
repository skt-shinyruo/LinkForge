package com.linkforge.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class PersistenceMigrationGuardTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.linkforge");

    @Test
    void production_code_should_not_depend_on_jakarta_persistence() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..");
        rule.check(CLASSES);
    }

    @Test
    void production_code_should_not_depend_on_spring_data_jpa() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.data.jpa..");
        rule.check(CLASSES);
    }

    @Test
    void production_code_should_not_depend_on_spring_jdbc_core() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.jdbc.core..");
        rule.check(CLASSES);
    }

    @Test
    void shortlink_should_not_depend_on_spring_data_domain() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge.shortlink..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.data.domain..");
        rule.check(CLASSES);
    }
}

