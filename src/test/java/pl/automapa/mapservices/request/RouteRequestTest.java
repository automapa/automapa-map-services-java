package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteRequestTest {

    private static final List<Point> POINTS = List.of(new Point(21.0073642, 52.2297), new Point(18.6282, 54.352));

    @Test
    void pointsRouteAndObjectInApiOrder() {
        Map<String, Object> params = RouteRequest.builder(POINTS, Map.of("type", "short"), Map.of("type", "car"))
                .build()
                .toParams();

        assertThat(params.keySet()).containsExactly("points", "route", "object");
        assertThat(params.get("points")).isEqualTo(List.of(List.of(21.0073642, 52.2297), List.of(18.6282, 54.352)));
        assertThat(params.get("route")).isEqualTo(Map.of("type", "short"));
        assertThat(params.get("object")).isEqualTo(Map.of("type", "car"));
    }

    @Test
    void emptyRouteAndObjectAreSentAsEmptyMaps() {
        Map<String, Object> params =
                RouteRequest.builder(POINTS, Map.of(), Map.of()).build().toParams();

        assertThat(params).containsEntry("route", Map.of()).containsEntry("object", Map.of());
    }

    @Test
    void nestedMapsKeepTheirKeyOrderAndAreCopied() {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("type", "optimal");
        route.put("traffic", true);
        route.put("avoidTolls", false);
        Map<String, Object> truckParams = Map.of("truckWeight", 3.5);
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("type", "truck");
        object.put("truckParams", truckParams);
        List<Point> points = new ArrayList<>(POINTS);

        RouteRequest.Builder builder = RouteRequest.builder(points, route, object);
        route.clear();
        object.clear();
        points.clear();
        Map<String, Object> params = builder.build().toParams();

        assertThat(new ArrayList<Object>(((Map<?, ?>) params.get("route")).keySet()))
                .containsExactly("type", "traffic", "avoidTolls");
        assertThat(new ArrayList<Object>(((Map<?, ?>) params.get("object")).keySet()))
                .containsExactly("type", "truckParams");
        assertThat((List<?>) params.get("points")).hasSize(2);
    }

    @Test
    void namedParamsOverrideExtra() {
        Map<String, Object> params = RouteRequest.builder(POINTS, Map.of("type", "quick"), Map.of("type", "car"))
                .extra("route", "ignored")
                .extra("lang", "en")
                .build()
                .toParams();

        assertThat(params.keySet()).containsExactly("route", "lang", "points", "object");
        assertThat(params.get("route")).isEqualTo(Map.of("type", "quick"));
        assertThat(params).containsEntry("lang", "en");
    }

    @Test
    void formatGoesToTheParams() {
        Map<String, Object> params = RouteRequest.builder(POINTS, Map.of(), Map.of())
                .format("native")
                .build()
                .toParams();

        assertThat(params).containsEntry("format", "native");
    }

    @Test
    void nullPointsAreRejected() {
        assertThatThrownBy(() -> RouteRequest.builder(null, Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RouteRequest.builder")
                .hasMessageContaining("points is required but was null")
                .hasMessageContaining("new Point(");
    }

    @Test
    void emptyPointsAreRejected() {
        assertThatThrownBy(() -> RouteRequest.builder(List.of(), Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points is required but was empty");
    }

    @Test
    void nullPointInsideTheListIsRejectedWithItsIndex() {
        List<Point> points = Arrays.asList(new Point(21.0, 52.0), null);

        assertThatThrownBy(() -> RouteRequest.builder(points, Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points[1] is null");
    }

    @Test
    void nullRouteIsRejected() {
        assertThatThrownBy(() -> RouteRequest.builder(POINTS, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RouteRequest.builder")
                .hasMessageContaining("route is required but was null")
                .hasMessageContaining("Map.of()");
    }

    @Test
    void nullObjectIsRejected() {
        assertThatThrownBy(() -> RouteRequest.builder(POINTS, Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("object is required but was null")
                .hasMessageContaining("\"car\"");
    }
}
