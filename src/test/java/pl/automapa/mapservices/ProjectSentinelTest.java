package pl.automapa.mapservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/** Guards project-wide invariants: pinned images, no runtime dependencies, ignored secrets. */
class ProjectSentinelTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void dockerComposeDoesNotUseLatestTag() throws Exception {
        String compose = Files.readString(ROOT.resolve("docker-compose.yaml"));

        assertThat(compose).doesNotContainIgnoringCase(":latest").doesNotContainIgnoringCase("latest@");
    }

    @Test
    void everyDockerImageIsPinnedByDigest() throws Exception {
        List<String> imageLines = Files.readAllLines(ROOT.resolve("docker-compose.yaml")).stream()
                .map(String::trim)
                .filter(line -> line.startsWith("image:"))
                .toList();

        assertThat(imageLines).isNotEmpty();
        Pattern pinned = Pattern.compile("image:\\s*[^\\s:@]+:[^\\s:@]+@sha256:[0-9a-f]{64}");
        assertThat(imageLines)
                .allSatisfy(line -> assertThat(pinned.matcher(line).matches())
                        .as("image must be pinned by tag and sha256 digest: %s", line)
                        .isTrue());
    }

    @Test
    void everyDependencyHasTestScope() throws Exception {
        List<Element> dependencies = PomModel.load(ROOT.resolve("pom.xml")).dependencies();

        assertThat(dependencies).isNotEmpty();
        assertThat(dependencies)
                .allSatisfy(dependency -> assertThat(PomModel.childText(dependency, "scope"))
                        .as(
                                "runtime dependencies are forbidden; add <scope>test</scope> to %s",
                                PomModel.childText(dependency, "artifactId"))
                        .isEqualTo("test"));
    }

    @Test
    void envFileIsIgnoredByGit() throws Exception {
        List<String> lines = Files.readAllLines(ROOT.resolve(".gitignore")).stream()
                .map(String::trim)
                .toList();

        assertThat(lines).contains(".env");
    }

    @Test
    void envExampleDocumentsEveryVariableWithoutValuesForSecrets() throws Exception {
        List<String> lines = Files.readAllLines(ROOT.resolve(".env.example"));
        Pattern variable = Pattern.compile("^([A-Z0-9_]+)=(.*)$");

        int variables = 0;
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = variable.matcher(lines.get(i));
            if (!matcher.matches()) {
                continue;
            }
            variables++;
            assertThat(i)
                    .as("variable %s needs a comment above it", matcher.group(1))
                    .isPositive();
            assertThat(lines.get(i - 1))
                    .as("comment above %s", matcher.group(1))
                    .startsWith("#");
            if (matcher.group(1).equals("AUTOMAPA_API_KEY") || matcher.group(1).equals("AUTOMAPA_API_PASSWORD")) {
                assertThat(matcher.group(2))
                        .as("%s must be empty", matcher.group(1))
                        .isEmpty();
            }
        }
        assertThat(variables).isEqualTo(3);
    }

    @Test
    void envExampleListsRoadPermitAccessAndExplainsPartialKeys() throws Exception {
        String envExample = Files.readString(ROOT.resolve(".env.example"));

        assertThat(envExample)
                .containsPattern("(?m)^# Possible values: .*\\broadpermit\\b")
                .contains("only part of the services")
                .contains("suites not listed are skipped");
    }
}
