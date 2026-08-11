package com.linkforge.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTestSuiteGuardTest {

    @Test
    void integration_tests_should_be_isolated_behind_it_profile() throws Exception {
        Element project = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(resolveFromCurrentWorkspace("../pom.xml", "server/pom.xml").toFile())
                .getDocumentElement();

        assertThat(childTexts(project, "modules", "module"))
                .as("Default mvn test must remain a Docker-free unit suite")
                .doesNotContain("integration-tests");

        List<String> profileIdsWithIntegrationTests = childElements(project, "profiles").stream()
                .flatMap(profiles -> childElements(profiles, "profile").stream())
                .filter(profile -> childTexts(profile, "modules", "module").contains("integration-tests"))
                .map(profile -> childText(profile, "id"))
                .toList();

        assertThat(profileIdsWithIntegrationTests)
                .as("The it profile must include runtime/cross-context integration tests")
                .containsExactly("it");
    }

    @Test
    void executable_jar_should_not_replace_the_thin_reactor_artifact() throws Exception {
        Element project = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(resolveFromCurrentWorkspace("pom.xml", "server/app/pom.xml").toFile())
                .getDocumentElement();

        Element springBootPlugin = childElements(project, "build").stream()
                .flatMap(build -> childElements(build, "plugins").stream())
                .flatMap(plugins -> childElements(plugins, "plugin").stream())
                .filter(plugin -> "spring-boot-maven-plugin".equals(childText(plugin, "artifactId")))
                .findFirst()
                .orElseThrow();

        String classifier = childElements(springBootPlugin, "configuration").stream()
                .map(configuration -> childText(configuration, "classifier"))
                .findFirst()
                .orElse("");

        assertThat(classifier)
                .as("Integration tests need the thin jar; the executable Boot jar must be attached separately")
                .isEqualTo("exec");
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

    private static List<String> childTexts(Element parent, String containerName, String childName) {
        return childElements(parent, containerName).stream()
                .flatMap(container -> childElements(container, childName).stream())
                .map(Node::getTextContent)
                .map(String::trim)
                .toList();
    }

    private static String childText(Element parent, String childName) {
        return childElements(parent, childName).stream()
                .findFirst()
                .map(Node::getTextContent)
                .map(String::trim)
                .orElse("");
    }

    private static List<Element> childElements(Element parent, String childName) {
        List<Element> matches = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && childName.equals(element.getTagName())) {
                matches.add(element);
            }
        }
        return matches;
    }
}
