package pl.automapa.mapservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/** Guards the Maven Central metadata, the release profile and the package verification. */
class PublishingSentinelTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final String REPOSITORY_URL = "https://github.com/automapa/automapa-map-services-java";
    private static final List<String> RELEASE_PLUGINS = List.of(
            "maven-source-plugin", "maven-javadoc-plugin", "maven-gpg-plugin", "central-publishing-maven-plugin");

    @Test
    void pomHasMetadataRequiredByMavenCentral() throws Exception {
        PomModel pom = PomModel.load(ROOT.resolve("pom.xml"));

        assertThat(pom.projectText("name")).isNotBlank();
        assertThat(pom.projectText("description")).isNotBlank();
        assertThat(pom.projectText("url")).isEqualTo(REPOSITORY_URL);

        Element license = PomModel.child(pom.projectElement("licenses"), "license");
        assertThat(PomModel.childText(license, "name")).isEqualTo("MIT License");
        assertThat(PomModel.childText(license, "url")).isNotBlank();

        Element developer = PomModel.child(pom.projectElement("developers"), "developer");
        assertThat(PomModel.childText(developer, "name")).isEqualTo("Automapa sp. z o.o.");
        assertThat(PomModel.childText(developer, "organization")).isEqualTo("Automapa sp. z o.o.");
        assertThat(PomModel.childText(developer, "organizationUrl")).isNotBlank();

        Element scm = pom.projectElement("scm");
        assertThat(PomModel.childText(scm, "url")).isEqualTo(REPOSITORY_URL);
        assertThat(PomModel.childText(scm, "connection")).isEqualTo("scm:git:" + REPOSITORY_URL + ".git");
        assertThat(PomModel.childText(scm, "developerConnection"))
                .isEqualTo("scm:git:ssh://git@github.com/automapa/automapa-map-services-java.git");
    }

    @Test
    void releaseProfileHasPublishingPluginsAndIsNeverActiveByDefault() throws Exception {
        PomModel pom = PomModel.load(ROOT.resolve("pom.xml"));
        Element release = pom.profile("release");

        assertThat(release).as("pom.xml needs a <profile> with id release").isNotNull();
        assertThat(PomModel.child(release, "activation"))
                .as("the release profile runs only with -Prelease")
                .isNull();
        assertThat(PomModel.pluginArtifactIds(PomModel.child(PomModel.child(release, "build"), "plugins")))
                .containsExactlyInAnyOrderElementsOf(RELEASE_PLUGINS);

        Element central = PomModel.plugin(
                PomModel.child(PomModel.child(release, "build"), "plugins"), "central-publishing-maven-plugin");
        Element configuration = PomModel.child(central, "configuration");
        assertThat(PomModel.childText(configuration, "publishingServerId")).isEqualTo("central");
        assertThat(PomModel.childText(configuration, "autoPublish"))
                .as("a deployment must be published by hand in the Central Portal")
                .isEqualTo("false");
    }

    @Test
    void signingAndPublishingPluginsAreOutsideTheDefaultBuild() throws Exception {
        PomModel pom = PomModel.load(ROOT.resolve("pom.xml"));
        List<String> defaultPlugins = PomModel.pluginArtifactIds(pom.buildPlugins());

        assertThat(defaultPlugins).doesNotContain("maven-gpg-plugin", "central-publishing-maven-plugin");
    }

    @Test
    void everyPluginVersionIsPinnedInPluginManagement() throws Exception {
        String pom = Files.readString(ROOT.resolve("pom.xml"));
        PomModel model = PomModel.load(ROOT.resolve("pom.xml"));
        Element management =
                PomModel.child(PomModel.child(model.projectElement("build"), "pluginManagement"), "plugins");
        List<String> managed = PomModel.pluginArtifactIds(management);

        assertThat(managed).containsAll(RELEASE_PLUGINS);
        Matcher version =
                Pattern.compile("<([a-z-]+-plugin)\\.version>([^<]+)</").matcher(pom);
        List<String> versions = new ArrayList<>();
        while (version.find()) {
            versions.add(version.group(1) + "=" + version.group(2));
            assertThat(version.group(2))
                    .as("plugin %s must use a fixed release version", version.group(1))
                    .matches("\\d+(\\.\\d+)*");
        }
        assertThat(versions)
                .anySatisfy(v -> assertThat(v).startsWith("maven-source-plugin="))
                .anySatisfy(v -> assertThat(v).startsWith("maven-gpg-plugin="))
                .anySatisfy(v -> assertThat(v).startsWith("central-publishing-maven-plugin="));
    }

    @Test
    void makePackageBuildsAllJarsAndVerifiesTheirContents() throws Exception {
        String makefile = Files.readString(ROOT.resolve("Makefile"));
        Matcher recipe =
                Pattern.compile("(?ms)^package:[^\\n]*\\n((?:\\t[^\\n]*\\n)+)").matcher(makefile);

        assertThat(recipe.find()).as("Makefile needs a package target").isTrue();
        assertThat(recipe.group(1))
                .containsSubsequence("package", "source:jar-no-fork", "javadoc:jar", "docker/maven/verify-package.sh")
                .doesNotContain("-Prelease")
                .doesNotContain("deploy");
        Path script = ROOT.resolve("docker/maven/verify-package.sh");
        assertThat(script).exists().isExecutable();
        assertThat(Files.readString(script))
                .contains("jar tf")
                .contains("-sources.jar")
                .contains("-javadoc.jar")
                .contains(".env")
                .contains("fixtures/");
    }
}
