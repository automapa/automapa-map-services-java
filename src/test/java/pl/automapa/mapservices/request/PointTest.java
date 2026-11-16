package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.automapa.mapservices.json.Json;

class PointTest {

    @Test
    void keepsLongitudeAsXAndLatitudeAsY() {
        Point point = new Point(21.0073642, 52.18288);

        assertThat(point.x()).isEqualTo(21.0073642);
        assertThat(point.y()).isEqualTo(52.18288);
    }

    @Test
    void toListReturnsXThenY() {
        assertThat(new Point(21.0073642, 52.18288).toList()).containsExactly(21.0073642, 52.18288);
    }

    @Test
    void writesAsJsonArray() {
        assertThat(Json.stringify(new Point(21.0073642, 52.18288))).isEqualTo("[21.0073642,52.18288]");
        assertThat(Json.stringify(Map.of("point", new Point(18.5, 54.0)))).isEqualTo("{\"point\":[18.5,54.0]}");
    }

    @Test
    void toJsonValueIsTheList() {
        Point point = new Point(1.5, 2.5);

        assertThat(point.toJsonValue()).isEqualTo(List.of(1.5, 2.5));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteX(double x) {
        assertThatThrownBy(() -> new Point(x, 52.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("x (longitude)")
                .hasMessageContaining("finite");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteY(double y) {
        assertThatThrownBy(() -> new Point(21.0, y))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("y (latitude)")
                .hasMessageContaining("finite");
    }

    @Test
    void equalsByCoordinates() {
        assertThat(new Point(1.0, 2.0)).isEqualTo(new Point(1.0, 2.0)).isNotEqualTo(new Point(2.0, 1.0));
    }
}
