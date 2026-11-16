package pl.automapa.mapservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs {@code docker/maven/run-examples.sh} with bash and checks how {@code AUTOMAPA_API_REQUIRED_ACCESS} selects the
 * examples. Credentials are always removed from the child environment, so the live API is never called.
 */
class RunExamplesScriptTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Path SCRIPT = ROOT.resolve("docker/maven/run-examples.sh");
    private static final String ACCESS = "AUTOMAPA_API_REQUIRED_ACCESS";

    private static final List<String> SYNTHETIC_EXAMPLES = List.of(
            "RoadPermitList.java",
            "RoadPermitLifecycle.java",
            "RoadSpeedCheck.java",
            "RoutesRoute.java",
            "GeocoderGeocode.java",
            "GoogleGeocodeAdapterExample.java",
            "AutocompleteSearch.java",
            "PingPong.java",
            "Misc.java");

    @TempDir
    Path workDir;

    @BeforeEach
    void createSyntheticExamples() throws IOException {
        Path examples = Files.createDirectories(workDir.resolve("examples"));
        for (String name : SYNTHETIC_EXAMPLES) {
            Files.writeString(examples.resolve(name), "");
        }
    }

    @Test
    void roadPermitAccessRunsOnlyRoadPermitAndUnmappedExamples() throws Exception {
        Result result = run(workDir, "roadpermit", true);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
                .contains("RUN examples/RoadPermitList.java")
                .contains("RUN examples/RoadPermitLifecycle.java")
                .contains("RUN examples/Misc.java")
                .contains("SKIP examples/RoadSpeedCheck.java (road not in AUTOMAPA_API_REQUIRED_ACCESS)")
                .contains("SKIP examples/RoutesRoute.java (routes not in AUTOMAPA_API_REQUIRED_ACCESS)")
                .contains("SKIP examples/GeocoderGeocode.java (geocoder not in AUTOMAPA_API_REQUIRED_ACCESS)")
                .contains("SKIP examples/GoogleGeocodeAdapterExample.java"
                        + " (geocoder not in AUTOMAPA_API_REQUIRED_ACCESS)")
                .contains("SKIP examples/AutocompleteSearch.java (autocomplete not in AUTOMAPA_API_REQUIRED_ACCESS)")
                .contains("SKIP examples/PingPong.java (pingpong not in AUTOMAPA_API_REQUIRED_ACCESS)");
    }

    @Test
    void roadAccessDoesNotRunRoadPermitExamples() throws Exception {
        Result result = run(workDir, " road , geocoder ", true);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
                .contains("RUN examples/RoadSpeedCheck.java")
                .contains("RUN examples/GeocoderGeocode.java")
                .contains("RUN examples/GoogleGeocodeAdapterExample.java")
                .contains("RUN examples/Misc.java")
                .contains("SKIP examples/RoadPermitList.java (roadpermit not in AUTOMAPA_API_REQUIRED_ACCESS)")
                .contains("SKIP examples/RoadPermitLifecycle.java (roadpermit not in AUTOMAPA_API_REQUIRED_ACCESS)")
                .contains("SKIP examples/RoutesRoute.java (routes not in AUTOMAPA_API_REQUIRED_ACCESS)");
    }

    @Test
    void emptyOrMissingAccessListRunsEveryExample() throws Exception {
        for (String access : new String[] {null, "", " , "}) {
            Result result = run(workDir, access, true);

            assertThat(result.exitCode()).isZero();
            assertThat(result.output()).as("access list %s", access).doesNotContain("SKIP");
            for (String name : SYNTHETIC_EXAMPLES) {
                assertThat(result.output()).as("access list %s", access).contains("RUN examples/" + name);
            }
        }
    }

    @Test
    void realRoadPermitExamplesAreNotMappedToRoad() throws Exception {
        Result result = run(ROOT, "roadpermit", true);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
                .contains("RUN examples/RoadPermitList.java")
                .contains("RUN examples/RoadPermitLifecycle.java")
                .contains("SKIP examples/RoadSpeedCheck.java (road not in AUTOMAPA_API_REQUIRED_ACCESS)")
                .contains("SKIP examples/PingPong.java (pingpong not in AUTOMAPA_API_REQUIRED_ACCESS)");
    }

    @Test
    void skippedExamplesAreCountedAndDoNotFailTheRun() throws Exception {
        Files.delete(workDir.resolve("examples/Misc.java"));
        Files.delete(workDir.resolve("examples/RoadPermitList.java"));
        Files.delete(workDir.resolve("examples/RoadPermitLifecycle.java"));

        Result result = run(workDir, "roadpermit", false);

        assertThat(result.output())
                .contains("SKIPPED (road not in AUTOMAPA_API_REQUIRED_ACCESS)")
                .contains("=== Summary: 0 passed, 0 failed, 6 skipped ===");
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void failingExampleFailsTheRunAndIsCounted() throws Exception {
        Result result = run(workDir, "pingpong", false);

        assertThat(result.output())
                .contains("FAILED: examples/PingPong.java")
                .contains("=== Summary: 0 passed, 2 failed, 7 skipped ===");
        assertThat(result.exitCode()).isEqualTo(1);
    }

    private static Result run(Path directory, String access, boolean dryRun) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("bash", SCRIPT.toString()).directory(directory.toFile());
        builder.redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.remove(Config.ENV_API_KEY);
        environment.remove(Config.ENV_API_PASSWORD);
        environment.remove("EXAMPLES_DRY_RUN");
        if (dryRun) {
            environment.put("EXAMPLES_DRY_RUN", "1");
        }
        if (access == null) {
            environment.remove(ACCESS);
        } else {
            environment.put(ACCESS, access);
        }
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(process.waitFor(120, TimeUnit.SECONDS)).as("script finished").isTrue();
        return new Result(process.exitValue(), new String(output, StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String output) {}
}
