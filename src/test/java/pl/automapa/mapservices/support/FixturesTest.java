package pl.automapa.mapservices.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FixturesTest {

    @Test
    void loadsFixtureByNameWithOrWithoutExtension() {
        Map<String, Object> withoutExtension = Fixtures.load("session_salt");
        Map<String, Object> withExtension = Fixtures.load("session_salt.json");

        assertThat(withoutExtension).isEqualTo(withExtension).containsKey("result");
    }

    @Test
    void loadsFixturesFromSubdirectories() {
        assertThat(Fixtures.load("errors/rate_limit")).isNotEmpty();
    }

    @Test
    void returnsRawText() {
        assertThat(Fixtures.text("session_salt")).startsWith("{").contains("result");
    }

    @Test
    void missingFixtureFailsWithReadableMessage() {
        assertThatThrownBy(() -> Fixtures.load("does_not_exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixtures/does_not_exist.json")
                .hasMessageContaining("src/test/resources/fixtures");
    }
}
