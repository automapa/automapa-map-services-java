package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OptimizeRequestTest {

    private static final List<Point> POINTS = List.of(
            new Point(21.0073642, 52.2297),
            new Point(18.6282, 54.352),
            new Point(16.9252, 52.4064),
            new Point(19.9448, 50.0647));

    private static final List<List<Double>> COORDINATES = List.of(
            List.of(21.0073642, 52.2297),
            List.of(18.6282, 54.352),
            List.of(16.9252, 52.4064),
            List.of(19.9448, 50.0647));

    @Test
    void optimizeTypeApiValues() {
        assertThat(OptimizeType.TIME.apiValue()).isEqualTo(1);
        assertThat(OptimizeType.DISTANCE.apiValue()).isEqualTo(2);
        assertThat(OptimizeType.values()).containsExactly(OptimizeType.TIME, OptimizeType.DISTANCE);
    }

    @Test
    void fixedEndIsAlwaysSentAndTrueByDefault() {
        Map<String, Object> params =
                OptimizeRequest.builder(POINTS, OptimizeType.TIME).build().toParams();

        assertThat(params)
                .containsExactly(Map.entry("points", COORDINATES), Map.entry("type", 1), Map.entry("fixedEnd", true));
    }

    @Test
    void routeAndObjectFollowFixedEndWhenSet() {
        Map<String, Object> params = OptimizeRequest.builder(POINTS, OptimizeType.DISTANCE)
                .fixedEnd(false)
                .route(Map.of("avoidTolls", false))
                .object(Map.of("type", "car"))
                .build()
                .toParams();

        assertThat(params)
                .containsExactly(
                        Map.entry("points", COORDINATES),
                        Map.entry("type", 2),
                        Map.entry("fixedEnd", false),
                        Map.entry("route", Map.of("avoidTolls", false)),
                        Map.entry("object", Map.of("type", "car")));
    }

    @Test
    void nullRouteAndObjectAreLeftOut() {
        Map<String, Object> params = OptimizeRequest.builder(POINTS, OptimizeType.TIME)
                .route(Map.of("avoidTolls", true))
                .object(Map.of("type", "car"))
                .route(null)
                .object(null)
                .build()
                .toParams();

        assertThat(params).doesNotContainKeys("route", "object");
    }

    @Test
    void emptyRouteIsSentAsEmptyMap() {
        Map<String, Object> params = OptimizeRequest.builder(POINTS, OptimizeType.TIME)
                .route(Map.of())
                .build()
                .toParams();

        assertThat(params).containsEntry("route", Map.of());
    }

    @Test
    void routeAndObjectAreCopiedInTheirKeyOrder() {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("avoidTolls", true);
        route.put("avoidFerries", false);
        route.put("avoidHighways", true);
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("type", "truck");
        object.put("truckParams", Map.of("truckWeight", 40.0));
        List<Point> points = new ArrayList<>(POINTS);

        OptimizeRequest.Builder builder =
                OptimizeRequest.builder(points, OptimizeType.TIME).route(route).object(object);
        route.clear();
        object.clear();
        points.clear();
        Map<String, Object> params = builder.build().toParams();

        assertThat(new ArrayList<Object>(((Map<?, ?>) params.get("route")).keySet()))
                .containsExactly("avoidTolls", "avoidFerries", "avoidHighways");
        assertThat(new ArrayList<Object>(((Map<?, ?>) params.get("object")).keySet()))
                .containsExactly("type", "truckParams");
        assertThat((List<?>) params.get("points")).hasSize(4);
    }

    @Test
    void namedParamsOverrideExtra() {
        Map<String, Object> params = OptimizeRequest.builder(POINTS, OptimizeType.TIME)
                .extra("fixedEnd", "ignored")
                .extra("route", Map.of("avoidTolls", true))
                .extra("lang", "en")
                .build()
                .toParams();

        assertThat(params.keySet()).containsExactly("fixedEnd", "route", "lang", "points", "type");
        assertThat(params).containsEntry("fixedEnd", true).containsEntry("lang", "en");
        assertThat(params)
                .as("an unset named route keeps the extra value")
                .containsEntry("route", Map.of("avoidTolls", true));
    }

    @Test
    void formatGoesToTheParams() {
        Map<String, Object> params = OptimizeRequest.builder(POINTS, OptimizeType.TIME)
                .format("native")
                .build()
                .toParams();

        assertThat(params).containsEntry("format", "native");
    }

    @Test
    void nullPointsAreRejected() {
        assertThatThrownBy(() -> OptimizeRequest.builder(null, OptimizeType.TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OptimizeRequest.builder")
                .hasMessageContaining("points is required but was null")
                .hasMessageContaining("new Point(");
    }

    @Test
    void emptyPointsAreRejected() {
        assertThatThrownBy(() -> OptimizeRequest.builder(List.of(), OptimizeType.TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points is required but was empty");
    }

    @Test
    void nullPointInsideTheListIsRejectedWithItsIndex() {
        List<Point> points = Arrays.asList(new Point(21.0, 52.0), null);

        assertThatThrownBy(() -> OptimizeRequest.builder(points, OptimizeType.TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points[1] is null");
    }

    @Test
    void nullTypeIsRejected() {
        assertThatThrownBy(() -> OptimizeRequest.builder(POINTS, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OptimizeRequest.builder")
                .hasMessageContaining("type is required but was null")
                .hasMessageContaining("OptimizeType.TIME");
    }
}
