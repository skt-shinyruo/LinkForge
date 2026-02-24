package com.linkforge.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.linkforge");

    @Test
    void controllers_should_not_depend_on_repositories() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..web..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..repo..");
        rule.check(CLASSES);
    }

    @Test
    void only_services_or_api_layer_should_access_repositories() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackages("..service..", "..api..", "..repo..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..repo..");
        rule.check(CLASSES);
    }
}

