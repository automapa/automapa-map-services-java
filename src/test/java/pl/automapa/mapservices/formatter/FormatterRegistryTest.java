package pl.automapa.mapservices.formatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FormatterRegistryTest {

    private static final Formatter UPPER = (raw, serviceMethod) -> serviceMethod.toUpperCase(java.util.Locale.ROOT);

    @Test
    void newRegistryIsEmpty() {
        FormatterRegistry registry = new FormatterRegistry();

        assertThat(registry.has("native")).isFalse();
        assertThatThrownBy(() -> registry.get("native"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\"native\"")
                .hasMessageContaining("Available: none")
                .hasMessageContaining("register(");
    }

    @Test
    void registeredFormatterCanBeRetrieved() {
        FormatterRegistry registry = new FormatterRegistry();
        NativeFormatter nativeFormatter = new NativeFormatter();

        registry.register("native", nativeFormatter);
        registry.register("upper", UPPER);

        assertThat(registry.has("native")).isTrue();
        assertThat(registry.has("upper")).isTrue();
        assertThat(registry.has("NATIVE")).isFalse();
        assertThat(registry.get("native")).isSameAs(nativeFormatter);
        assertThat(registry.get("upper").format(Map.of(), "Geocoder.geocode")).isEqualTo("GEOCODER.GEOCODE");
    }

    @Test
    void registeringTheSameNameReplacesTheFormatter() {
        FormatterRegistry registry = new FormatterRegistry();
        registry.register("native", new NativeFormatter());

        registry.register("native", UPPER);

        assertThat(registry.get("native")).isSameAs(UPPER);
    }

    @Test
    void unknownFormatterMessageListsAvailableNamesInRegistrationOrder() {
        FormatterRegistry registry = new FormatterRegistry();
        registry.register("native", new NativeFormatter());
        registry.register("upper", UPPER);

        assertThatThrownBy(() -> registry.get("google"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formatter \"google\" is not registered")
                .hasMessageContaining("Available: native, upper");
    }

    @Test
    void hasReturnsFalseForNull() {
        assertThat(new FormatterRegistry().has(null)).isFalse();
    }

    @Test
    void rejectsInvalidRegistration() {
        FormatterRegistry registry = new FormatterRegistry();

        assertThatThrownBy(() -> registry.register(null, UPPER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> registry.register(" ", UPPER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register("custom", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custom");
        assertThat(registry.has("custom")).isFalse();
    }

    @Test
    void getRejectsNullName() {
        assertThatThrownBy(() -> new FormatterRegistry().get(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
