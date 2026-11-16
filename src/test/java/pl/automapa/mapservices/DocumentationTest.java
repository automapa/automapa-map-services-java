package pl.automapa.mapservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.automapa.mapservices.adapter.GoogleGeocodeAdapter;
import pl.automapa.mapservices.endpoint.Autocomplete;
import pl.automapa.mapservices.endpoint.Geocoder;
import pl.automapa.mapservices.endpoint.PingPong;
import pl.automapa.mapservices.endpoint.Road;
import pl.automapa.mapservices.endpoint.RoadPermit;
import pl.automapa.mapservices.endpoint.Routes;
import pl.automapa.mapservices.endpoint.Session;

/** Guards README.md and CHANGELOG.md: required sections, compiling snippets, documented methods and examples. */
class DocumentationTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    private static final List<String> REQUIRED_HEADINGS = List.of(
            "## Getting started with your `.env` file",
            "## 1. Overview",
            "## 2. Installation",
            "## 3. Client configuration",
            "## 4. Session handling",
            "## 5. Available services",
            "## 6. Examples",
            "## 7. Additional parameters (`extra`)",
            "## 8. Low-level `call()` and `request()`",
            "## 9. Response formats: `native` vs `google`",
            "## 10. Google format limitations",
            "## 11. Error handling",
            "## Thread safety",
            "## License");

    private static final String DEPRECATED_SENTENCE = "The deprecated Speed service is not supported.";

    private static final Pattern JAVA_BLOCK = Pattern.compile("```java\\n(.*?)```", Pattern.DOTALL);

    /** Endpoint accessors that are not API methods, so the services table does not list them. */
    private static final Set<String> ENDPOINT_ACCESSORS = Set.of("RoadPermit.environment");

    private static final Pattern ROAD_PERMIT_TYPE = Pattern.compile("`(RoadPermit\\w*)");

    private static final Pattern CLIENT_METHOD = Pattern.compile("\\bclient\\.(\\w+)\\(");

    private static final Pattern EXAMPLE_REFERENCE = Pattern.compile("examples/([A-Za-z0-9]+\\.java)");

    /** Imports available to every README snippet, so snippets may omit them. */
    private static final String SNIPPET_IMPORTS = String.join(
            "\n",
            "import java.time.Duration;",
            "import java.util.*;",
            "import pl.automapa.mapservices.*;",
            "import pl.automapa.mapservices.adapter.*;",
            "import pl.automapa.mapservices.endpoint.*;",
            "import pl.automapa.mapservices.exception.*;",
            "import pl.automapa.mapservices.http.*;",
            "import pl.automapa.mapservices.request.*;",
            "import pl.automapa.mapservices.response.*;",
            "import pl.automapa.mapservices.session.*;");

    private static String readme() throws IOException {
        return Files.readString(ROOT.resolve("README.md"), StandardCharsets.UTF_8);
    }

    private static List<String> javaSnippets(String markdown) {
        List<String> snippets = new ArrayList<>();
        Matcher matcher = JAVA_BLOCK.matcher(markdown);
        while (matcher.find()) {
            snippets.add(matcher.group(1));
        }
        return snippets;
    }

    @Test
    void readmeHasRequiredSectionsInOrder() throws IOException {
        List<String> headings = readme().lines()
                .filter(line -> line.startsWith("## "))
                .map(String::strip)
                .toList();

        assertThat(headings).containsSubsequence(REQUIRED_HEADINGS);
    }

    @Test
    void gettingStartedListsDockerTargets() throws IOException {
        assertThat(readme())
                .contains("cp .env.example .env")
                .contains("make test\n")
                .contains("make test-integration")
                .contains("make test-unit")
                .contains("make check")
                .contains("make examples");
    }

    @Test
    void readmeMentionsDeprecatedSpeedServiceOnlyInOneSentence() throws IOException {
        String readme = readme();

        assertThat(readme).containsOnlyOnce(DEPRECATED_SENTENCE);
        String rest = readme.replace(DEPRECATED_SENTENCE, "");
        assertThat(rest).doesNotContainPattern("\\bSpeed\\b");
    }

    @Test
    void installationUsesProjectCoordinates() throws Exception {
        PomModel pom = PomModel.load(ROOT.resolve("pom.xml"));
        String groupId = pom.projectText("groupId");
        String artifactId = pom.projectText("artifactId");
        String release = pom.projectVersion().replace("-SNAPSHOT", "");

        assertThat(readme())
                .contains("<groupId>" + groupId + "</groupId>")
                .contains("<artifactId>" + artifactId + "</artifactId>")
                .contains("<version>" + release + "</version>")
                .contains("\"" + groupId + ":" + artifactId + ":" + release + "\"")
                .contains("Java 17");
    }

    @Test
    void installationDownloadsTheJarFromGitHubReleases() throws Exception {
        PomModel pom = PomModel.load(ROOT.resolve("pom.xml"));
        String artifactId = pom.projectText("artifactId");
        String release = pom.projectVersion().replace("-SNAPSHOT", "");
        String jar = artifactId + "-" + release + ".jar";

        assertThat(readme())
                .contains(pom.projectText("url") + "/releases/download/v" + release + "/" + jar)
                .contains("mvn install:install-file -Dfile=" + jar)
                .contains("mavenLocal()")
                .doesNotContain("Maven Central");
    }

    @Test
    void everyEndpointMethodHasReadmeExample() throws IOException {
        String code = String.join("\n", javaSnippets(readme()));

        Set<String> missing = new TreeSet<>();
        for (Class<?> type : List.of(
                Session.class,
                PingPong.class,
                Geocoder.class,
                Autocomplete.class,
                Routes.class,
                Road.class,
                RoadPermit.class,
                GoogleGeocodeAdapter.class)) {
            for (Method method : type.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (Modifier.isPublic(modifiers)
                        && !Modifier.isStatic(modifiers)
                        && !code.contains("." + method.getName() + "(")) {
                    missing.add(type.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(missing).as("endpoint methods without a README example").isEmpty();
    }

    @Test
    void servicesTableListsEveryEndpointMethod() throws IOException {
        String readme = readme();

        for (Class<?> type : List.of(
                Session.class,
                PingPong.class,
                Geocoder.class,
                Autocomplete.class,
                Routes.class,
                Road.class,
                RoadPermit.class)) {
            String row = readme.lines()
                    .filter(line -> line.startsWith("| `" + type.getSimpleName() + "`"))
                    .findFirst()
                    .orElse("");
            Set<String> methods = Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()))
                    .map(Method::getName)
                    .filter(name -> !ENDPOINT_ACCESSORS.contains(type.getSimpleName() + "." + name))
                    .collect(Collectors.toCollection(TreeSet::new));

            assertThat(methods)
                    .as("services table row of " + type.getSimpleName())
                    .allSatisfy(name -> assertThat(row).contains("`" + name + "()`"));
        }
    }

    @Test
    void roadPermitSectionExplainsEnvironmentsAndGeoJson() throws IOException {
        String section = section(readme(), "### RoadPermit", "\n## ");

        assertThat(section)
                .contains("client.roadPermit(RoadPermitEnvironment.UAT)")
                .contains("`client.roadPermit()`")
                .contains("production")
                .contains("`[lon, lat]`")
                .contains("closed")
                .contains("`tr_tonnage`");
    }

    @Test
    void readmeExplainsKeysWithAccessToSomeServicesOnly() throws IOException {
        String section = section(readme(), "### API keys with access to some services only", "\n---");

        assertThat(section)
                .contains("separate `ApiClient`")
                .contains("AUTOMAPA_API_REQUIRED_ACCESS")
                .contains("make test-integration")
                .contains("make examples");
    }

    @Test
    void roadPermitTypesNamedInReadmeExist() throws IOException {
        Set<String> missing = new TreeSet<>();
        Matcher matcher = ROAD_PERMIT_TYPE.matcher(readme());
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!classExists("pl.automapa.mapservices.endpoint." + name)
                    && !classExists("pl.automapa.mapservices.request." + name)) {
                missing.add(name);
            }
        }

        assertThat(missing).as("RoadPermit types named in README").isEmpty();
    }

    @Test
    void clientMethodsNamedInReadmeExist() throws IOException {
        Set<String> declared =
                Arrays.stream(ApiClient.class.getMethods()).map(Method::getName).collect(Collectors.toSet());
        Set<String> missing = new TreeSet<>();
        Matcher matcher = CLIENT_METHOD.matcher(readme());
        while (matcher.find()) {
            if (!declared.contains(matcher.group(1))) {
                missing.add(matcher.group(1));
            }
        }

        assertThat(missing).as("ApiClient methods named in README").isEmpty();
    }

    @Test
    void readmeListsExactlyTheExistingExamples() throws IOException {
        Set<String> referenced = new TreeSet<>();
        Matcher matcher = EXAMPLE_REFERENCE.matcher(readme());
        while (matcher.find()) {
            referenced.add(matcher.group(1));
        }
        Set<String> existing;
        try (Stream<Path> files = Files.list(ROOT.resolve("examples"))) {
            existing = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        assertThat(referenced).isEqualTo(existing);
    }

    @Test
    void everyJavaSnippetCompilesAgainstTheSdk(@TempDir Path out) throws IOException {
        List<String> snippets = javaSnippets(readme());
        assertThat(snippets).isNotEmpty();

        List<JavaFileObject> sources = new ArrayList<>();
        for (int i = 0; i < snippets.size(); i++) {
            sources.add(snippetSource(i + 1, snippets.get(i)));
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StringWriter diagnostics = new StringWriter();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            List<String> options = List.of(
                    "-proc:none",
                    "--release",
                    "17",
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-d",
                    out.toString());
            boolean compiled = compiler.getTask(diagnostics, files, null, options, null, sources)
                    .call();

            assertThat(compiled)
                    .as("README java snippets must compile:%n%s", diagnostics)
                    .isTrue();
        }
    }

    @Test
    void changelogFollowsKeepAChangelog() throws Exception {
        String changelog = Files.readString(ROOT.resolve("CHANGELOG.md"), StandardCharsets.UTF_8);
        PomModel pom = PomModel.load(ROOT.resolve("pom.xml"));
        String version = pom.projectVersion();
        String url = pom.projectText("url");

        assertThat(changelog)
                .startsWith("# Changelog")
                .contains("https://keepachangelog.com/en/1.1.0/")
                .contains("https://semver.org/spec/v2.0.0.html")
                .contains("## [Unreleased]")
                .contains("0.1.0")
                .contains("### Added")
                .doesNotContainPattern("\\bSpeed\\b");
        assertThat(version).as("pom.xml version of a release").doesNotEndWith("-SNAPSHOT");
        assertThat(changelog)
                .containsPattern("\\n## \\[" + Pattern.quote(version) + "\\] - \\d{4}-\\d{2}-\\d{2}\\n")
                .contains("[Unreleased]: " + url + "/compare/v" + version + "...HEAD")
                .contains("[" + version + "]: " + url + "/releases/tag/v" + version);
        String release = section(changelog, "## [" + version + "]", "\n## [");
        String added = section(release, "### Added", "\n### ");
        String changed = section(release, "### Changed", "\n### ");

        assertThat(added)
                .contains("`RoadPermit` (`list`, `add`, `overwrite`, `remove`)")
                .contains("RoadPermitEnvironment");
        assertThat(changed).contains("make examples").contains("AUTOMAPA_API_REQUIRED_ACCESS");
    }

    /** The text from {@code heading} up to the next {@code end} marker (or the end of the text); empty if absent. */
    private static String section(String text, String heading, String end) {
        int start = text.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int stop = text.indexOf(end, start + heading.length());
        return stop < 0 ? text.substring(start) : text.substring(start, stop);
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, DocumentationTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Wraps a README snippet into a compilation unit: its {@code import} lines go to the header, the rest becomes the
     * body of a method that receives a ready {@code client} unless the snippet creates one itself.
     */
    private static JavaFileObject snippetSource(int number, String snippet) {
        List<String> imports = new ArrayList<>();
        List<String> body = new ArrayList<>();
        snippet.lines().forEach(line -> (line.startsWith("import ") ? imports : body).add(line));
        String bodyText = String.join("\n", body);
        String parameter =
                Pattern.compile("\\bApiClient client\\s*=").matcher(bodyText).find()
                        ? "ApiClient unusedClient"
                        : "ApiClient client";
        String className = "ReadmeSnippet" + number;
        String source = String.join("\n", imports) + "\n" + SNIPPET_IMPORTS + "\n"
                + "@SuppressWarnings(\"all\")\n"
                + "class " + className + " {\n"
                + "  static void snippet(" + parameter + ") throws Exception {\n"
                + bodyText + "\n"
                + "  }\n"
                + "}\n";
        return new SimpleJavaFileObject(
                URI.create("string:///" + className + JavaFileObject.Kind.SOURCE.extension),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
    }
}
