package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.automapa.mapservices.json.Json;

class QueuePointTest {

    @Test
    void keepsIdLongitudeAndLatitude() {
        QueuePoint point = new QueuePoint("Warszawa", 21.0073642, 52.2297);

        assertThat(point.id()).isEqualTo("Warszawa");
        assertThat(point.x()).isEqualTo(21.0073642);
        assertThat(point.y()).isEqualTo(52.2297);
    }

    @Test
    void toMapReturnsIdXYInApiOrder() {
        Map<String, Object> map = new QueuePoint("Gdansk", 18.6282, 54.352).toMap();

        assertThat(new ArrayList<>(map.keySet())).containsExactly("id", "x", "y");
        assertThat(map)
                .containsEntry("id", "Gdansk")
                .containsEntry("x", 18.6282)
                .containsEntry("y", 54.352);
    }

    @Test
    void writesAsJsonObject() {
        assertThat(Json.stringify(new QueuePoint("Krakow", 19.9448, 50.0647)))
                .isEqualTo("{\"id\":\"Krakow\",\"x\":19.9448,\"y\":50.0647}");
    }

    @Test
    void toJsonValueIsTheMap() {
        QueuePoint point = new QueuePoint("A", 1.5, 2.5);

        assertThat(point.toJsonValue()).isEqualTo(point.toMap());
    }

    @Test
    void nullIdIsRejected() {
        assertThatThrownBy(() -> new QueuePoint(null, 21.0, 52.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QueuePoint")
                .hasMessageContaining("id is required but was null")
                .hasMessageContaining("new QueuePoint(\"Warszawa\"");
    }

    @Test
    void blankIdIsRejected() {
        assertThatThrownBy(() -> new QueuePoint("  ", 21.0, 52.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id is required but was empty");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteX(double x) {
        assertThatThrownBy(() -> new QueuePoint("A", x, 52.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QueuePoint")
                .hasMessageContaining("x (longitude)")
                .hasMessageContaining("finite");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteY(double y) {
        assertThatThrownBy(() -> new QueuePoint("A", 21.0, y))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("y (latitude)")
                .hasMessageContaining("finite");
    }

    @Test
    void equalsByIdAndCoordinates() {
        assertThat(new QueuePoint("A", 1.0, 2.0))
                .isEqualTo(new QueuePoint("A", 1.0, 2.0))
                .isNotEqualTo(new QueuePoint("B", 1.0, 2.0));
    }
}
