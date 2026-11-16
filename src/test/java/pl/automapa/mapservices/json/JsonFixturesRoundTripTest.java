package pl.automapa.mapservices.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JsonFixturesRoundTripTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    static List<Path> fixtures() throws IOException {
        try (Stream<Path> files = Files.walk(FIXTURES)) {
            return files.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    @Test
    void fixturesIncludeErrorResponsesAndNoDeprecatedServices() throws IOException {
        List<String> names = fixtures().stream()
                .map(path -> FIXTURES.relativize(path).toString().replace('\\', '/'))
                .collect(Collectors.toList());

        assertThat(names)
                .hasSizeGreaterThanOrEqualTo(20)
                .contains("errors/auth_error.json", "errors/rate_limit.json", "geocode_success.json")
                .contains(
                        "road_permit_list_success.json",
                        "road_permit_add_success.json",
                        "road_permit_overwrite_success.json",
                        "road_permit_remove_success.json")
                .noneMatch(name -> name.startsWith("speed_report_"));
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void parseStringifyParseGivesEqualStructure(Path fixture) throws IOException {
        Object first = Json.parse(Files.readString(fixture, StandardCharsets.UTF_8));

        String written = Json.stringify(first);
        Object second = Json.parse(written);

        assertThat(second).isEqualTo(first);
        assertThat(Json.stringify(second)).isEqualTo(written);
    }
}
