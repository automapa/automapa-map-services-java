package pl.automapa.mapservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the quality gate configuration: pinned tools, Checkstyle rules, coverage threshold and make targets. */
class QualityGateSentinelTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void qualityPluginsArePinnedInPom() throws Exception {
        String pom = Files.readString(ROOT.resolve("pom.xml"));

        assertThat(pom)
                .contains("<artifactId>spotless-maven-plugin</artifactId>")
                .contains("<artifactId>maven-checkstyle-plugin</artifactId>")
                .contains("<artifactId>jacoco-maven-plugin</artifactId>")
                .contains("<artifactId>maven-javadoc-plugin</artifactId>")
                .contains("<palantirJavaFormat>");
        for (String property : List.of(
                "spotless-maven-plugin.version",
                "palantir-java-format.version",
                "maven-checkstyle-plugin.version",
                "checkstyle.version",
                "jacoco-maven-plugin.version",
                "maven-javadoc-plugin.version")) {
            assertThat(Pattern.compile("<" + Pattern.quote(property) + ">\\d+(\\.\\d+)+</")
                            .matcher(pom)
                            .find())
                    .as("property %s must pin an exact version", property)
                    .isTrue();
        }
    }

    @Test
    void jacocoEnforcesLineCoverageOfAtLeast85Percent() throws Exception {
        String pom = Files.readString(ROOT.resolve("pom.xml"));

        assertThat(pom)
                .contains("<counter>LINE</counter>")
                .contains("<minimum>0.85</minimum>")
                .as("coverage must be measured from scratch on every run")
                .contains("<append>false</append>");
    }

    @Test
    void javadocFailsOnDoclintProblems() throws Exception {
        String pom = Files.readString(ROOT.resolve("pom.xml"));

        assertThat(pom)
                .contains("<doclint>all</doclint>")
                .contains("<failOnWarnings>true</failOnWarnings>")
                .as("javadoc must not skip doclint because of stale data")
                .contains("<staleDataPath combine.self=\"override\"/>");
    }

    @Test
    void checkstyleConfigContainsRequiredRules() throws Exception {
        String config = Files.readString(ROOT.resolve("config/checkstyle/checkstyle.xml"));

        assertThat(config)
                .contains("\"AvoidStarImport\"")
                .contains("\"UnusedImports\"")
                .contains("\"EmptyCatchBlock\"")
                .contains("\"TypeName\"")
                .contains("\"MethodName\"")
                .contains("\"ConstantName\"")
                .contains("\"PackageName\"")
                .contains("name=\"severity\" value=\"error\"");
        assertThat(Files.readString(ROOT.resolve("pom.xml"))).contains("<failOnViolation>true</failOnViolation>");
    }

    @Test
    void makefileExposesQualityGateTargets() throws Exception {
        String makefile = Files.readString(ROOT.resolve("Makefile"));

        for (String target :
                List.of("format", "format-check", "lint", "typecheck", "javadoc", "check", "examples", "test-unit")) {
            assertThat(Pattern.compile("(?m)^" + Pattern.quote(target) + ":.*## ")
                            .matcher(makefile)
                            .find())
                    .as("Makefile target %s with a description", target)
                    .isTrue();
        }
        assertThat(makefile)
                .contains("CHECK PASSED")
                .contains("CHECK FAILED:")
                .contains("format-check lint typecheck javadoc test-unit");
    }
}
