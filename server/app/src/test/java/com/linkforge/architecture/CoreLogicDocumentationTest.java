package com.linkforge.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 核心逻辑目录的轻量架构门禁。
 *
 * <p>该测试只验证“能够定位且已经登记”，文档的准确性和解释质量仍由评审负责。</p>
 */
class CoreLogicDocumentationTest {

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^]]*]\\(([^)]+)\\)");
    private static final Pattern TS_DOCUMENTED_EXPORT = Pattern.compile(
            "(?s)/\\*\\*.*?\\*/\\s*export\\s+(?:default\\s+)?(?:async\\s+)?"
                    + "(?:function|const|class|interface|type|enum)\\b"
    );
    private static final Pattern JAVA_LEADING_DECORATION = Pattern.compile(
            "(?s)\\s*(?:(?:@[\\w.$]+(?:\\s*\\([^;]*?\\))?\\s*)"
                    + "|(?:(?:public|protected|private|abstract|final|sealed|non-sealed|static)\\s+))*"
    );
    private static final Set<String> ALLOWED_CONTEXTS = Set.of(
            "accounts", "platform", "shortlink", "redirect", "analytics", "governance",
            "foundation", "contracts", "cross-context", "frontend"
    );

    private static Path repositoryRoot;
    private static Catalog catalog;

    @BeforeAll
    static void loadCatalog() throws IOException {
        repositoryRoot = findRepositoryRoot(Path.of(System.getProperty("user.dir")).toAbsolutePath());
        Path catalogPath = repositoryRoot.resolve("docs/reference/core-logic-catalog.json");
        catalog = new ObjectMapper().readValue(catalogPath.toFile(), Catalog.class);
    }

    @Test
    void catalogHasValidSchemaUniqueIdsAndResolvableTargets() throws IOException {
        assertThat(catalog.schemaVersion()).isEqualTo(1);
        assertThat(catalog.entries()).isNotEmpty();

        Set<String> ids = new HashSet<>();
        for (Entry entry : catalog.entries()) {
            assertThat(entry.id()).matches(ID);
            assertThat(ids.add(entry.id())).as("duplicate catalog id: %s", entry.id()).isTrue();
            assertThat(entry.context()).as(entry.id()).isNotBlank();
            assertThat(ALLOWED_CONTEXTS).as("unsupported context in %s", entry.id()).contains(entry.context());
            assertThat(entry.sources()).as(entry.id()).isNotEmpty();
            assertThat(entry.document()).as(entry.id()).startsWith("docs/reference/").endsWith(".md");
            assertThat(entry.heading()).as(entry.id()).isNotBlank();

            for (String source : entry.sources()) {
                assertRepositoryRelativePathExists(source, entry.id());
                assertCoreSourceHasLeadingDocumentation(source, entry.id());
            }

            Path document = assertRepositoryRelativePathExists(entry.document(), entry.id());
            String markdown = Files.readString(document);
            Pattern heading = Pattern.compile("(?m)^#{1,6}\\s+" + Pattern.quote(entry.heading()) + "\\s*$");
            Matcher headingMatcher = heading.matcher(markdown);
            int headingCount = 0;
            while (headingMatcher.find()) {
                headingCount++;
            }
            assertThat(headingCount)
                    .as("catalog heading '%s' must occur exactly once in %s", entry.heading(), entry.document())
                    .isEqualTo(1);
        }
    }

    @Test
    void everyPublishedContractSourceIsRegistered() throws IOException {
        Set<String> registered = new LinkedHashSet<>();
        catalog.entries().forEach(entry -> registered.addAll(entry.sources()));

        Path contractsRoot = repositoryRoot.resolve("server/contracts");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(contractsRoot)) {
            paths.filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(repositoryRoot::relativize)
                    .map(Path::toString)
                    .filter(path -> !registered.contains(path))
                    .sorted()
                    .forEach(missing::add);
        }
        assertThat(missing).as("unregistered public contract sources").isEmpty();
    }

    @Test
    void markdownRelativeLinksResolve() throws IOException {
        Path referenceRoot = repositoryRoot.resolve("docs/reference");
        List<String> broken = new ArrayList<>();
        try (Stream<Path> documents = Files.list(referenceRoot)) {
            for (Path document : documents.filter(path -> path.toString().endsWith(".md")).toList()) {
                Matcher matcher = MARKDOWN_LINK.matcher(Files.readString(document));
                while (matcher.find()) {
                    String rawTarget = matcher.group(1).trim();
                    String target = rawTarget.startsWith("<") && rawTarget.endsWith(">")
                            ? rawTarget.substring(1, rawTarget.length() - 1)
                            : rawTarget;
                    int titleSeparator = target.indexOf(" \"");
                    if (titleSeparator >= 0) {
                        target = target.substring(0, titleSeparator);
                    }
                    if (target.isBlank() || target.startsWith("#") || target.startsWith("/")
                            || target.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
                        continue;
                    }
                    int anchor = target.indexOf('#');
                    if (anchor >= 0) {
                        target = target.substring(0, anchor);
                    }
                    if (!target.isBlank() && !Files.exists(document.getParent().resolve(target).normalize())) {
                        broken.add(repositoryRoot.relativize(document) + " -> " + rawTarget);
                    }
                }
            }
        }
        assertThat(broken).as("broken Markdown relative links").isEmpty();
    }

    private static Path assertRepositoryRelativePathExists(String raw, String entryId) {
        Path relative = Path.of(raw);
        assertThat(relative.isAbsolute()).as("absolute path in %s: %s", entryId, raw).isFalse();
        Path resolved = repositoryRoot.resolve(relative).normalize();
        assertThat(resolved.startsWith(repositoryRoot)).as("path escapes repository in %s: %s", entryId, raw).isTrue();
        assertThat(resolved).as("missing path in %s", entryId).exists();
        return resolved;
    }

    private static void assertCoreSourceHasLeadingDocumentation(String raw, String entryId) throws IOException {
        if (!raw.endsWith(".java") && !raw.endsWith(".ts")) {
            return;
        }
        String source = Files.readString(repositoryRoot.resolve(raw));
        if (raw.endsWith(".ts")) {
            assertThat(TS_DOCUMENTED_EXPORT.matcher(source).find())
                    .as("missing TSDoc immediately before an exported core symbol in %s (%s)", raw, entryId)
                    .isTrue();
            return;
        }

        String fileName = Path.of(raw).getFileName().toString();
        String typeName = fileName.substring(0, fileName.length() - ".java".length());
        Pattern declaration = Pattern.compile("\\b(?:class|interface|record|enum)\\s+" + Pattern.quote(typeName) + "\\b");
        Matcher matcher = declaration.matcher(source);
        assertThat(matcher.find()).as("top-level declaration for %s", raw).isTrue();
        int declarationStart = matcher.start();
        int commentStart = source.lastIndexOf("/**", declarationStart);
        int commentEnd = commentStart < 0 ? -1 : source.indexOf("*/", commentStart);
        assertThat(commentStart).as("missing leading Javadoc in %s (%s)", raw, entryId).isGreaterThanOrEqualTo(0);
        assertThat(commentEnd).as("unterminated leading Javadoc in %s", raw).isBetween(commentStart, declarationStart);
        String betweenCommentAndDeclaration = source.substring(commentEnd + 2, declarationStart);
        assertThat(JAVA_LEADING_DECORATION.matcher(betweenCommentAndDeclaration).matches())
                .as("Javadoc must directly document the top-level declaration in %s (%s)", raw, entryId)
                .isTrue();
    }

    private static Path findRepositoryRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.isRegularFile(current.resolve("README.md"))
                    && Files.isRegularFile(current.resolve("server/pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法从 " + start + " 定位 LinkForge 仓库根目录");
    }

    record Catalog(int schemaVersion, List<Entry> entries) {
    }

    record Entry(String id, String context, List<String> sources, String document, String heading) {
    }
}
