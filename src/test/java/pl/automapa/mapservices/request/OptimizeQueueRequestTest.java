package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OptimizeQueueRequestTest {

    private static final List<QueuePoint> POINTS = List.of(
            new QueuePoint("Warszawa", 21.0073642, 52.2297),
            new QueuePoint("Gdansk", 18.6282, 54.352),
            new QueuePoint("Poznan", 16.9252, 52.4064),
            new QueuePoint("Krakow", 19.9448, 50.0647));

    private static Map<String, Object> point(String id, double x, double y) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("x", x);
        map.put("y", y);
        return map;
    }

    private static final List<Map<String, Object>> POINT_MAPS = List.of(
            point("Warszawa", 21.0073642, 52.2297),
            point("Gdansk", 18.6282, 54.352),
            point("Poznan", 16.9252, 52.4064),
            point("Krakow", 19.9448, 50.0647));

    @Test
    void optimizeByApiValues() {
        assertThat(OptimizeBy.TIME.apiValue()).isEqualTo("time");
        assertThat(OptimizeBy.DISTANCE.apiValue()).isEqualTo("distance");
        assertThat(OptimizeBy.values()).containsExactly(OptimizeBy.TIME, OptimizeBy.DISTANCE);
    }

    @Test
    void pointsAreSentAsIdXYObjectsAndFixedEndIsTrueByDefault() {
        Map<String, Object> params =
                OptimizeQueueRequest.builder(POINTS, OptimizeBy.TIME).build().toParams();

        assertThat(params)
                .containsExactly(
                        Map.entry("points", POINT_MAPS), Map.entry("optimizeBy", "time"), Map.entry("fixedEnd", true));
        Map<?, ?> first = (Map<?, ?>) ((List<?>) params.get("points")).get(0);
        assertThat(new ArrayList<Object>(first.keySet())).containsExactly("id", "x", "y");
    }

    @Test
    void routeAndObjectFollowFixedEndWhenSet() {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("start", "NOW");
        route.put("avoidTolls", false);
        Map<String, Object> params = OptimizeQueueRequest.builder(POINTS, OptimizeBy.DISTANCE)
                .fixedEnd(false)
                .route(route)
                .object(Map.of("type", "car"))
                .build()
                .toParams();

        assertThat(params)
                .containsExactly(
                        Map.entry("points", POINT_MAPS),
                        Map.entry("optimizeBy", "distance"),
                        Map.entry("fixedEnd", false),
                        Map.entry("route", Map.of("start", "NOW", "avoidTolls", false)),
                        Map.entry("object", Map.of("type", "car")));
        assertThat(new ArrayList<Object>(((Map<?, ?>) params.get("route")).keySet()))
                .containsExactly("start", "avoidTolls");
    }

    @Test
    void nullRouteAndObjectAreLeftOut() {
        Map<String, Object> params = OptimizeQueueRequest.builder(POINTS, OptimizeBy.TIME)
                .route(Map.of("start", "NOW"))
                .object(Map.of("type", "car"))
                .route(null)
                .object(null)
                .build()
                .toParams();

        assertThat(params).doesNotContainKeys("route", "object");
    }

    @Test
    void routeAndObjectAndPointsAreCopied() {
        Map<String, Object> route = new LinkedHashMap<>(Map.of("start", "NOW"));
        Map<String, Object> object = new LinkedHashMap<>(Map.of("type", "car"));
        List<QueuePoint> points = new ArrayList<>(POINTS);

        OptimizeQueueRequest.Builder builder = OptimizeQueueRequest.builder(points, OptimizeBy.TIME)
                .route(route)
                .object(object);
        route.clear();
        object.clear();
        points.clear();
        Map<String, Object> params = builder.build().toParams();

        assertThat(params)
                .containsEntry("route", Map.of("start", "NOW"))
                .containsEntry("object", Map.of("type", "car"));
        assertThat((List<?>) params.get("points")).hasSize(4);
    }

    @Test
    void namedParamsOverrideExtra() {
        Map<String, Object> params = OptimizeQueueRequest.builder(POINTS, OptimizeBy.TIME)
                .extra("optimizeBy", "ignored")
                .extra("lang", "en")
                .build()
                .toParams();

        assertThat(params.keySet()).containsExactly("optimizeBy", "lang", "points", "fixedEnd");
        assertThat(params).containsEntry("optimizeBy", "time").containsEntry("lang", "en");
    }

    @Test
    void duplicateIdsAreRejectedWithTheRepeatedId() {
        List<QueuePoint> points = List.of(
                new QueuePoint("Warszawa", 21.0073642, 52.2297),
                new QueuePoint("Gdansk", 18.6282, 54.352),
                new QueuePoint("Warszawa", 21.01, 52.23));

        assertThatThrownBy(() -> OptimizeQueueRequest.builder(points, OptimizeBy.TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OptimizeQueueRequest.builder")
                .hasMessageContaining("\"Warszawa\"")
                .hasMessageContaining("points[0]")
                .hasMessageContaining("points[2]")
                .hasMessageContaining("unique");
    }

    @Test
    void nullPointsAreRejected() {
        assertThatThrownBy(() -> OptimizeQueueRequest.builder(null, OptimizeBy.TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OptimizeQueueRequest.builder")
                .hasMessageContaining("points is required but was null")
                .hasMessageContaining("new QueuePoint(");
    }

    @Test
    void emptyPointsAreRejected() {
        assertThatThrownBy(() -> OptimizeQueueRequest.builder(List.of(), OptimizeBy.TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points is required but was empty");
    }

    @Test
    void nullPointInsideTheListIsRejectedWithItsIndex() {
        List<QueuePoint> points = Arrays.asList(new QueuePoint("A", 21.0, 52.0), null);

        assertThatThrownBy(() -> OptimizeQueueRequest.builder(points, OptimizeBy.TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points[1] is null");
    }

    @Test
    void nullOptimizeByIsRejected() {
        assertThatThrownBy(() -> OptimizeQueueRequest.builder(POINTS, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("optimizeBy is required but was null")
                .hasMessageContaining("OptimizeBy.TIME");
    }
}
