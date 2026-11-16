package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.automapa.mapservices.json.Json;

class SpeedCheckPointTest {

    @Test
    void keepsLongitudeLatitudeAndDirection() {
        SpeedCheckPoint point = new SpeedCheckPoint(15.2154636, 52.3118895, 350);

        assertThat(point.x()).isEqualTo(15.2154636);
        assertThat(point.y()).isEqualTo(52.3118895);
        assertThat(point.dir()).isEqualTo(350);
    }

    @Test
    void twoArgumentConstructorHasNoDirection() {
        SpeedCheckPoint point = new SpeedCheckPoint(13.8412285, 52.3111549);

        assertThat(point.dir()).isNull();
        assertThat(point).isEqualTo(new SpeedCheckPoint(13.8412285, 52.3111549, null));
    }

    @Test
    void toMapReturnsXYDirInApiOrder() {
        Map<String, Object> map = new SpeedCheckPoint(15.2154636, 52.3118895, 350).toMap();

        assertThat(new ArrayList<>(map.keySet())).containsExactly("x", "y", "dir");
        assertThat(map)
                .containsEntry("x", 15.2154636)
                .containsEntry("y", 52.3118895)
                .containsEntry("dir", 350);
    }

    @Test
    void toMapLeavesOutNullDirection() {
        Map<String, Object> map = new SpeedCheckPoint(13.8412285, 52.3111549).toMap();

        assertThat(new ArrayList<>(map.keySet())).containsExactly("x", "y");
    }

    @Test
    void writesAsJsonObject() {
        assertThat(Json.stringify(new SpeedCheckPoint(15.2154636, 52.3118895, 350)))
                .isEqualTo("{\"x\":15.2154636,\"y\":52.3118895,\"dir\":350}");
        assertThat(Json.stringify(new SpeedCheckPoint(13.8412285, 52.3111549)))
                .isEqualTo("{\"x\":13.8412285,\"y\":52.3111549}");
    }

    @Test
    void toJsonValueIsTheMap() {
        SpeedCheckPoint point = new SpeedCheckPoint(1.5, 2.5, -1);

        assertThat(point.toJsonValue()).isEqualTo(point.toMap());
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteX(double x) {
        assertThatThrownBy(() -> new SpeedCheckPoint(x, 52.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SpeedCheckPoint")
                .hasMessageContaining("x (longitude)")
                .hasMessageContaining("finite")
                .hasMessageContaining("new SpeedCheckPoint(");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteY(double y) {
        assertThatThrownBy(() -> new SpeedCheckPoint(21.0, y, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("y (latitude)")
                .hasMessageContaining("finite");
    }
}
