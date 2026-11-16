package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpeedCheckRequestTest {

    @Test
    void onlyCoordinatesByDefault() {
        Map<String, Object> params =
                SpeedCheckRequest.builder(15.2154636, 52.3118895).build().toParams();

        assertThat(params).containsExactly(Map.entry("x", 15.2154636), Map.entry("y", 52.3118895));
    }

    @Test
    void directionAndDistanceLimitInPhpOrder() {
        Map<String, Object> params = SpeedCheckRequest.builder(15.2154636, 52.3118895)
                .distLimit(75)
                .dir(350)
                .build()
                .toParams();

        assertThat(params)
                .containsExactly(
                        Map.entry("x", 15.2154636),
                        Map.entry("y", 52.3118895),
                        Map.entry("dir", 350),
                        Map.entry("distLimit", 75));
    }

    @Test
    void nullOptionsAreLeftOut() {
        Map<String, Object> params = SpeedCheckRequest.builder(15.0, 52.0)
                .dir(null)
                .distLimit(null)
                .build()
                .toParams();

        assertThat(params.keySet()).containsExactly("x", "y");
    }

    @Test
    void namedOverrideExtraAndExtraIsKept() {
        Map<String, Object> params = SpeedCheckRequest.builder(15.0, 52.0)
                .extra(Map.of("dir", 1))
                .extra("futureParam", "value")
                .dir(350)
                .build()
                .toParams();

        assertThat(params)
                .containsExactly(
                        Map.entry("dir", 350),
                        Map.entry("futureParam", "value"),
                        Map.entry("x", 15.0),
                        Map.entry("y", 52.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteX(double x) {
        assertThatThrownBy(() -> SpeedCheckRequest.builder(x, 52.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SpeedCheckRequest.builder")
                .hasMessageContaining("x (longitude)")
                .hasMessageContaining("finite")
                .hasMessageContaining("SpeedCheckRequest.builder(15.2154636, 52.3118895)");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteY(double y) {
        assertThatThrownBy(() -> SpeedCheckRequest.builder(15.0, y))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("y (latitude)")
                .hasMessageContaining("finite");
    }
}
