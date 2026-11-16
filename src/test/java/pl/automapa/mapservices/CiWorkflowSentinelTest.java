package pl.automapa.mapservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Guards the GitHub Actions workflow: pinned actions, JDK matrix and the same goals as {@code make check}. */
class CiWorkflowSentinelTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Path WORKFLOW = ROOT.resolve(".github/workflows/ci.yml");
    private static final Pattern USES = Pattern.compile("^\\s*(?:-\\s+)?uses:\\s*(\\S+)(.*)$", Pattern.MULTILINE);
    private static final Pattern PINNED_ACTION =
            Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_./-]+)?@[0-9a-f]{40}");

    @Test
    void githubDirectoryIsTheOnlyExceptionFromHiddenDirectoriesRule() throws Exception {
        List<String> exceptions = Files.readAllLines(ROOT.resolve(".gitignore")).stream()
                .map(String::strip)
                .filter(line -> line.startsWith("!"))
                .collect(Collectors.toList());

        assertThat(Files.readAllLines(ROOT.resolve(".gitignore"))).contains(".*/");
        assertThat(exceptions).containsExactly("!.github/");
    }

    @Test
    void everyActionIsPinnedToFullCommitShaWithVersionComment() throws Exception {
        String workflow = Files.readString(WORKFLOW);
        Matcher uses = USES.matcher(workflow);
        int count = 0;
        while (uses.find()) {
            count++;
            assertThat(uses.group(1))
                    .as("action must be pinned to a 40-character commit SHA")
                    .matches(PINNED_ACTION);
            assertThat(uses.group(2))
                    .as("pinned action %s needs a version comment", uses.group(1))
                    .matches("\\s+#\\s*v\\d+(\\.\\d+)*\\s*");
        }
        assertThat(count)
                .as("workflow must use at least checkout and setup-java")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void workflowRunsOnPushAndPullRequestForTemurinMatrix() throws Exception {
        String workflow = Files.readString(WORKFLOW);

        assertThat(workflow)
                .containsPattern("(?m)^on:\\s*$")
                .containsPattern("(?m)^  push:")
                .containsPattern("(?m)^  pull_request:")
                .containsPattern("java:\\s*\\[\\s*'?17'?\\s*,\\s*'?21'?\\s*,\\s*'?25'?\\s*]")
                .contains("distribution: temurin")
                .contains("actions/checkout@")
                .contains("actions/setup-java@");
    }

    @Test
    void workflowRunsTheSameMavenGoalsAsMakeCheck() throws Exception {
        String workflow = Files.readString(WORKFLOW);
        String makefile = Files.readString(ROOT.resolve("Makefile"));

        assertThat(makefile).contains("CHECK_STEPS := format-check lint typecheck javadoc test-unit");
        assertThat(workflow)
                .containsSubsequence(
                        "spotless:check",
                        "checkstyle:check",
                        "test-compile",
                        "docker/maven/compile-examples.sh",
                        "javadoc:javadoc",
                        "mvn -B --no-transfer-progress test");
    }

    @Test
    void workflowHasNoSecretsAndNoLiveApiTests() throws Exception {
        String workflow = Files.readString(WORKFLOW);

        assertThat(workflow)
                .doesNotContain("secrets.")
                .doesNotContain("AUTOMAPA_API")
                .doesNotContain("-Plive")
                .doesNotContain("live-api")
                .doesNotContain(".env")
                .contains("contents: read");
    }
}
