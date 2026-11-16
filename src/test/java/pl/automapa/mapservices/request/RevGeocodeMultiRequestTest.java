package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RevGeocodeMultiRequestTest {

    private static final List<Point> POINTS = List.of(new Point(21.0073642, 52.18288), new Point(18.3865894, 54.57206));

    @Test
    void pointsAsListsAndAlwaysParams() {
        Map<String, Object> params =
                RevGeocodeMultiRequest.builder(POINTS).build().toParams();

        assertThat(params.keySet()).containsExactly("points", "params");
        assertThat(params.get("points"))
                .isEqualTo(List.of(List.of(21.0073642, 52.18288), List.of(18.3865894, 54.57206)));
        assertThat(params.get("params")).isEqualTo(Map.of());
    }

    @Test
    void optionsGoToParams() {
        Map<String, Object> params = RevGeocodeMultiRequest.builder(POINTS)
                .snapToBld(true)
                .snapToBldRadius(25)
                .getPostalCode(false)
                .build()
                .toParams();

        assertThat(params.get("params"))
                .isEqualTo(Map.of("snapToBld", true, "snapToBldRadius", 25, "getPostalCode", false));
    }

    @Test
    void pointsListIsCopied() {
        List<Point> points = new ArrayList<>(POINTS);
        RevGeocodeMultiRequest.Builder builder = RevGeocodeMultiRequest.builder(points);
        points.clear();

        assertThat((List<?>) builder.build().toParams().get("points")).hasSize(2);
    }

    @Test
    void namedPointsOverrideExtra() {
        Map<String, Object> params = RevGeocodeMultiRequest.builder(POINTS)
                .extra("points", List.of())
                .extra("future", "x")
                .build()
                .toParams();

        assertThat(params.keySet()).containsExactly("points", "future", "params");
        assertThat((List<?>) params.get("points")).hasSize(2);
    }

    @Test
    void nullPointsAreRejected() {
        assertThatThrownBy(() -> RevGeocodeMultiRequest.builder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RevGeocodeMultiRequest")
                .hasMessageContaining("points is required but was null");
    }

    @Test
    void emptyPointsAreRejected() {
        assertThatThrownBy(() -> RevGeocodeMultiRequest.builder(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points is required but was empty");
    }

    @Test
    void nullPointInsideTheListIsRejectedWithItsIndex() {
        List<Point> points = Arrays.asList(new Point(21.0, 52.0), null);

        assertThatThrownBy(() -> RevGeocodeMultiRequest.builder(points))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points[1]")
                .hasMessageContaining("null");
    }
}
