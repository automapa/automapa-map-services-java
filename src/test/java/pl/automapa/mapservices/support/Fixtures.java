package pl.automapa.mapservices.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import pl.automapa.mapservices.json.Json;

/** Loads JSON fixtures from {@code src/test/resources/fixtures} on the test classpath. */
public final class Fixtures {

    private static final String DIRECTORY = "fixtures/";
    private static final String EXTENSION = ".json";

    private Fixtures() {}

    /**
     * Loads and parses a fixture whose root is a JSON object.
     *
     * @param name fixture name relative to the fixtures directory, with or without {@code .json}, e.g.
     *     {@code geocode_success} or {@code errors/auth_error}
     * @return the parsed JSON object
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> load(String name) {
        Object parsed = Json.parse(text(name));
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Fixture " + resource(name) + " is not a JSON object");
        }
        return (Map<String, Object>) parsed;
    }

    /**
     * Returns the raw text of a fixture.
     *
     * @param name fixture name relative to the fixtures directory, with or without {@code .json}
     * @return the file content (UTF-8)
     */
    public static String text(String name) {
        String resource = resource(name);
        try (InputStream in = Fixtures.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture " + resource + " not found on the test classpath; add the"
                        + " file to src/test/resources/" + resource + " (copy it from the PHP SDK tests/Fixtures)");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read fixture " + resource, e);
        }
    }

    private static String resource(String name) {
        return DIRECTORY + (name.endsWith(EXTENSION) ? name : name + EXTENSION);
    }
}
