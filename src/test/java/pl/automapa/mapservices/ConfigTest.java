package pl.automapa.mapservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigTest {

    @Test
    void builderUsesDefaults() {
        Config config = Config.builder("test-key", "test-pass").build();

        assertThat(config.getKey()).isEqualTo("test-key");
        assertThat(config.getPassword()).isEqualTo("test-pass");
        assertThat(config.getBaseUrl()).isEqualTo("https://api.automapa.pl/");
        assertThat(config.getVersion()).isEqualTo("v3");
        assertThat(config.getUserAgent()).isEqualTo("automapa-java-sdk/" + ApiClient.SDK_VERSION);
        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.getDefaultFormat()).isEqualTo("native");
    }

    @Test
    void builderOverridesOptions() {
        Config config = Config.builder("k", "p")
                .baseUrl("https://api.example.test/")
                .version("v9")
                .userAgent("custom/1.0")
                .timeout(Duration.ofSeconds(10))
                .defaultFormat("google")
                .build();

        assertThat(config.getBaseUrl()).isEqualTo("https://api.example.test/");
        assertThat(config.getVersion()).isEqualTo("v9");
        assertThat(config.getUserAgent()).isEqualTo("custom/1.0");
        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.getDefaultFormat()).isEqualTo("google");
    }

    @Test
    void toStringDoesNotRevealPassword() {
        String text = Config.builder("test-key", "very-secret-pass").build().toString();

        assertThat(text).contains("test-key").contains("***").doesNotContain("very-secret-pass");
    }

    @Test
    void requiresKeyAndPassword() {
        assertThatThrownBy(() -> Config.builder(null, "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AUTOMAPA_API_KEY");
        assertThatThrownBy(() -> Config.builder("k", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AUTOMAPA_API_PASSWORD");
    }

    @Test
    void optionsMustNotBeEmpty() {
        Config.Builder builder = Config.builder("k", "p");

        assertThatThrownBy(() -> builder.baseUrl(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.version(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.userAgent("")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/", "https://api.automapa.pl", "HTTPS://API.EXAMPLE.TEST/"})
    void acceptsHttpAndHttpsBaseUrls(String baseUrl) {
        assertThat(Config.builder("k", "p").baseUrl(baseUrl).build().getBaseUrl())
                .isEqualTo(baseUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {"api.automapa.pl", "ftp://api.automapa.pl/", "https:/api.automapa.pl", "//api.automapa.pl"})
    void rejectsBaseUrlWithoutHttpScheme(String baseUrl) {
        assertThatThrownBy(() -> Config.builder("k", "p").baseUrl(baseUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(baseUrl)
                .hasMessageContaining("http://")
                .hasMessageContaining("https://")
                .hasMessageContaining(Config.DEFAULT_BASE_URL);
    }

    @Test
    void rejectsMissingOrNonPositiveTimeout() {
        Config.Builder builder = Config.builder("k", "p");

        assertThatThrownBy(() -> builder.timeout(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout")
                .hasMessageContaining("Duration.ofSeconds(30)");
        assertThatThrownBy(() -> builder.timeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PT0S")
                .hasMessageContaining("Duration.ofSeconds(30)");
        assertThatThrownBy(() -> builder.timeout(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void defaultFormatMustNotBeEmpty() {
        Config.Builder builder = Config.builder("k", "p");

        assertThatThrownBy(() -> builder.defaultFormat(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultFormat")
                .hasMessageContaining("native");
        assertThatThrownBy(() -> builder.defaultFormat(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringShowsSettingsButNotPassword() {
        String text = Config.builder("test-key", "very-secret-pass")
                .timeout(Duration.ofSeconds(5))
                .defaultFormat("google")
                .build()
                .toString();

        assertThat(text)
                .contains("timeout=PT5S")
                .contains("defaultFormat=google")
                .doesNotContain("very-secret-pass");
    }

    @Test
    void fromEnvironmentReadsKeyAndPasswordWithDefaults() {
        Config config = Config.fromEnvironment(Map.of(
                "AUTOMAPA_API_KEY", "env-key",
                "AUTOMAPA_API_PASSWORD", "env-pass",
                "AUTOMAPA_API_REQUIRED_ACCESS", "geocoder"));

        assertThat(config.getKey()).isEqualTo("env-key");
        assertThat(config.getPassword()).isEqualTo("env-pass");
        assertThat(config.getBaseUrl()).isEqualTo(Config.DEFAULT_BASE_URL);
        assertThat(config.getVersion()).isEqualTo(Config.DEFAULT_VERSION);
        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.getDefaultFormat()).isEqualTo("native");
    }

    @Test
    void fromEnvironmentNamesMissingKeyVariableAndHowToFixIt() {
        assertThatThrownBy(() -> Config.fromEnvironment(Map.of("AUTOMAPA_API_PASSWORD", "env-pass")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AUTOMAPA_API_KEY")
                .hasMessageContaining("not set")
                .hasMessageContaining("cp .env.example .env");
    }

    @Test
    void fromEnvironmentNamesEmptyPasswordVariableWithoutRevealingKey() {
        Map<String, String> env = new HashMap<>();
        env.put("AUTOMAPA_API_KEY", "secret-env-key");
        env.put("AUTOMAPA_API_PASSWORD", "  ");

        assertThatThrownBy(() -> Config.fromEnvironment(env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AUTOMAPA_API_PASSWORD")
                .hasMessageContaining("empty")
                .hasMessageContaining("cp .env.example .env")
                .message()
                .doesNotContain("secret-env-key");
    }

    @Test
    void fromEnvironmentRejectsNullMap() {
        assertThatThrownBy(() -> Config.fromEnvironment(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("System.getenv()");
    }

    @Test
    void fromEnvironmentWithoutArgumentsReadsProcessEnvironment() {
        Map<String, String> env = System.getenv();
        boolean configured = notBlank(env.get("AUTOMAPA_API_KEY")) && notBlank(env.get("AUTOMAPA_API_PASSWORD"));

        if (configured) {
            Config config = Config.fromEnvironment();
            // Compare without printing the secrets in a failure message.
            assertThat(config.getKey().equals(env.get("AUTOMAPA_API_KEY"))).isTrue();
            assertThat(config.getPassword().equals(env.get("AUTOMAPA_API_PASSWORD")))
                    .isTrue();
        } else {
            assertThatThrownBy(Config::fromEnvironment)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cp .env.example .env");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
