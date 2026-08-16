package com.linkforge.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.linkforge");
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
                .haveFullyQualifiedName("com.linkforge.foundation.runtime.security.AuthContext");
        rule.check(CLASSES);
    }

    @Test
    void infrastructure_should_not_depend_on_runtime_security_context() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.linkforge.foundation.runtime.security..");
        rule.check(CLASSES);
    }

    @Test
    void contracts_should_not_depend_on_foundation_context_security_or_runtime_language() throws Exception {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge.contract..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.linkforge.foundation.context..",
                        "com.linkforge.foundation.security..",
                        "com.linkforge.foundation.runtime.."
                );
        rule.check(CLASSES);

        Path contractsDir = resolveFromCurrentWorkspace(
                "contracts",
                "server/contracts"
        );
        List<Path> poms;
        try (var stream = Files.walk(contractsDir)) {
            poms = stream
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .sorted()
                    .toList();
        }

        List<Path> violations = new ArrayList<>();
        for (Path pom : poms) {
            if (Files.readString(pom).contains("linkforge-foundation")) {
                violations.add(pom);
            }
        }

        assertThat(violations)
                .withFailMessage("Contract modules must not depend on foundation modules: %s", violations)
                .isEmpty();
    }

    @Test
    void shortlink_controllers_should_not_expose_application_link_dto_as_http_contract() {
        List<String> violations = shortlinkControllerMethods().stream()
                .filter(method -> typeMentions(method.getGenericReturnType(), "com.linkforge.shortlink.application.LinkDto"))
                .map(method -> method.getDeclaringClass().getName() + "#" + method.getName())
                .sorted()
                .toList();

        assertThat(violations)
                .withFailMessage("Shortlink controllers must map application LinkDto to HTTP response DTOs: %s", violations)
                .isEmpty();
    }

    @Test
    void controllers_should_not_expose_application_dtos_as_http_contracts() {
        List<String> violations = CLASSES.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class))
                .map(ArchitectureTest::loadClass)
                .flatMap(clazz -> Arrays.stream(clazz.getDeclaredMethods()))
                .filter(ArchitectureTest::isMappedEndpoint)
                .filter(method -> exposesApplicationType(method))
                .map(method -> method.getDeclaringClass().getName() + "#" + method.getName())
                .sorted()
                .toList();

        assertThat(violations)
                .withFailMessage("Controllers must map application results to interface-owned HTTP DTOs: %s", violations)
                .isEmpty();
    }

    @Test
    void application_services_should_not_own_nested_public_dto_records() {
        List<String> violations = CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().contains(".application"))
                .map(ArchitectureTest::loadClass)
                .flatMap(outerClass -> Arrays.stream(outerClass.getDeclaredClasses())
                        .filter(Class::isRecord)
                        .filter(nestedClass -> Modifier.isPublic(nestedClass.getModifiers()))
                        .filter(nestedClass -> nestedClass.getSimpleName().matches(".*(Dto|Result|Request|Command|Info|Created).*"))
                        .map(Class::getName))
                .sorted()
                .toList();

        assertThat(violations)
                .withFailMessage("Application DTO/request/result models must be top-level types: %s", violations)
                .isEmpty();
    }

    @Test
    void shortlink_interfaces_should_depend_on_specific_use_case_interfaces() {
        ArchRule rule = noClasses()
                .that()
                .areAnnotatedWith(RestController.class)
                .and()
                .resideInAnyPackage("com.linkforge.shortlink.interfaces.web..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.linkforge.shortlink.application.ShortLinkService");
        rule.check(CLASSES);
    }

    @Test
    void shortlink_application_should_not_keep_shortlink_service_as_dto_container() {
        assertThat(CLASSES.stream().map(JavaClass::getName))
                .as("ShortLinkService was a large interface plus nested DTO container; use focused use-case interfaces and top-level records")
                .doesNotContain("com.linkforge.shortlink.application.ShortLinkService");
    }

    @Test
    void accounts_domain_should_not_expose_deprecated_roles_alias() {
        assertThat(CLASSES.stream().map(JavaClass::getName))
                .as("Cross-context code must use foundation StandardRoles instead of accounts-owned role aliases")
                .doesNotContain("com.linkforge.accounts.domain.Roles");
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
                        "com.linkforge.accounts.."
                )
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.linkforge.shortlink.domain..",
                        "com.linkforge.shortlink.application..",
                        "com.linkforge.shortlink.infrastructure..",
                        "com.linkforge.shortlink.interfaces..",
                        "com.linkforge.shortlink.runtime.."
                );
        rule.check(CLASSES);
    }

    @Test
    void app_security_should_not_depend_on_accounts_internals() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.linkforge.app.security..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.linkforge.accounts.domain..",
                        "com.linkforge.accounts.application..",
                        "com.linkforge.accounts.infrastructure.."
                );
        rule.check(CLASSES);
    }

    @Test
    void shortlink_application_code_should_publish_shortlink_events_only_through_domain_event_dispatcher() {
        List<String> violations = new ArrayList<>();
        CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith("com.linkforge.shortlink.application"))
                .filter(javaClass -> !javaClass.getPackageName().equals("com.linkforge.shortlink.application.eventing"))
                .filter(javaClass -> !javaClass.getPackageName().equals("com.linkforge.shortlink.application.port"))
                .filter(javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
                        .anyMatch(dependency -> dependency.getTargetClass().getName()
                                .equals("com.linkforge.shortlink.application.port.ShortLinkEventPublisher")))
                .map(JavaClass::getName)
                .sorted()
                .forEach(violations::add);

        assertThat(violations)
                .withFailMessage("Shortlink application code must dispatch aggregate domain events before publishing integration events: %s", violations)
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

    private static List<Method> shortlinkControllerMethods() {
        return CLASSES.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class))
                .filter(javaClass -> javaClass.getPackageName().startsWith("com.linkforge.shortlink.interfaces.web"))
                .map(ArchitectureTest::loadClass)
                .flatMap(clazz -> Arrays.stream(clazz.getDeclaredMethods()))
                .toList();
    }

    private static Class<?> loadClass(JavaClass javaClass) {
        try {
            return Class.forName(javaClass.getName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not load " + javaClass.getName(), e);
        }
    }

    private static boolean typeMentions(Type type, String fullyQualifiedName) {
        return typeMatches(type, clazz -> clazz.getName().equals(fullyQualifiedName));
    }

    private static boolean typeMentionsPackage(Type type, String packageSegment) {
        return typeMatches(type, clazz -> clazz.getName().contains(packageSegment));
    }

    private static boolean typeMatches(Type type, Predicate<Class<?>> classPredicate) {
        if (type == null) {
            return false;
        }
        if (type instanceof Class<?> clazz) {
            return classPredicate.test(clazz);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return typeMatches(parameterizedType.getRawType(), classPredicate)
                    || Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(argument -> typeMatches(argument, classPredicate));
        }
        if (type instanceof GenericArrayType genericArrayType) {
            return typeMatches(genericArrayType.getGenericComponentType(), classPredicate);
        }
        if (type instanceof WildcardType wildcardType) {
            return Arrays.stream(wildcardType.getUpperBounds()).anyMatch(bound -> typeMatches(bound, classPredicate))
                    || Arrays.stream(wildcardType.getLowerBounds()).anyMatch(bound -> typeMatches(bound, classPredicate));
        }
        if (type instanceof TypeVariable<?> typeVariable) {
            return Arrays.stream(typeVariable.getBounds()).anyMatch(bound -> typeMatches(bound, classPredicate));
        }
        return false;
    }

    private static boolean isMappedEndpoint(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(RequestMapping.class);
    }

    private static boolean exposesApplicationType(Method method) {
        return typeMentionsPackage(method.getGenericReturnType(), ".application.");
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
                    basePackage + ".interfaces..",
                    basePackage + ".runtime.."
            };
        }
    }
}
