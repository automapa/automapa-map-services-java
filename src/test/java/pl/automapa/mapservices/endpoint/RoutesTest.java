package pl.automapa.mapservices.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.request.MatrixRequest;
import pl.automapa.mapservices.request.MatrixType;
import pl.automapa.mapservices.request.OptimizeBy;
import pl.automapa.mapservices.request.OptimizeQueueRequest;
import pl.automapa.mapservices.request.OptimizeRequest;
import pl.automapa.mapservices.request.OptimizeType;
import pl.automapa.mapservices.request.Point;
import pl.automapa.mapservices.request.QueuePoint;
import pl.automapa.mapservices.request.RouteRequest;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.support.Fixtures;
import pl.automapa.mapservices.support.MockHttpClient;

class RoutesTest {

    /** Session id from the {@code session_generate} fixture. */
    private static final String SESSION_ID = "qPPdEpdtpb";

    private static final Point WARSAW = new Point(21.0073642, 52.2297);
    private static final Point GDANSK = new Point(18.6282, 54.352);
    private static final Point POZNAN = new Point(16.9252, 52.4064);
    private static final Point KRAKOW = new Point(19.9448, 50.0647);

    private MockHttpClient mockHttp;
    private ApiClient client;

    @BeforeEach
    void setUp() {
        mockHttp = new MockHttpClient();
        client = new ApiClient(Config.builder("test-key", "test-pass").build(), mockHttp);
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate"));
    }

    private void assertProtectedCall(String method) {
        HttpRequest request = mockHttp.lastRequest();
        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/Routes/" + method);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers()).containsEntry("Session-Id", SESSION_ID);
    }

    // --- route ---

    @Test
    void routeSendsPointsRouteAndObjectWithSession() {
        mockHttp.addResponse("Routes", "route", Fixtures.load("routes_route_success"));
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("type", "short");
        route.put("traffic", true);
        route.put("avoidTolls", true);

        client.routes()
                .route(RouteRequest.builder(List.of(WARSAW, GDANSK), route, Map.of("type", "car"))
                        .build());

        assertProtectedCall("route");
        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("points", List.of(List.of(21.0073642, 52.2297), List.of(18.6282, 54.352))),
                        Map.entry("route", Map.of("type", "short", "traffic", true, "avoidTolls", true)),
                        Map.entry("object", Map.of("type", "car")));
    }

    @Test
    void routeSendsNestedTruckParams() {
        mockHttp.addResponse("Routes", "route", Fixtures.load("routes_route_success"));
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("type", "truck");
        object.put("truckParams", Map.of("truckWeight", 3.5, "euroNormForToll", 5));

        client.routes()
                .route(RouteRequest.builder(List.of(WARSAW, GDANSK), Map.of(), object)
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsEntry("route", Map.of())
                .containsEntry(
                        "object",
                        Map.of("type", "truck", "truckParams", Map.of("truckWeight", 3.5, "euroNormForToll", 5L)));
    }

    @Test
    void routePassesExtraAndNamedParamsWin() {
        mockHttp.addResponse("Routes", "route", Fixtures.load("routes_route_success"));

        client.routes()
                .route(RouteRequest.builder(List.of(WARSAW, GDANSK), Map.of("type", "quick"), Map.of("type", "car"))
                        .extra("object", Map.of("type", "truck"))
                        .extra("customField", "value")
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("object", Map.of("type", "car")),
                        Map.entry("customField", "value"),
                        Map.entry("points", List.of(List.of(21.0073642, 52.2297), List.of(18.6282, 54.352))),
                        Map.entry("route", Map.of("type", "quick")));
    }

    @Test
    void routeReturnsTheResultObject() {
        Map<String, Object> fixture = Fixtures.load("routes_route_success");
        mockHttp.addResponse("Routes", "route", fixture);

        ApiResponse response = client.routes()
                .route(RouteRequest.builder(List.of(WARSAW, GDANSK), Map.of(), Map.of("type", "car"))
                        .build());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.dataAsMap())
                .containsEntry("length", 341618L)
                .containsEntry("eta", 212L)
                .containsEntry("mapurl", "https://mapa.targeo.pl/trasa_api,example")
                .containsKeys("routePoints", "desc", "tolls");
        assertThat((List<?>) response.dataAsMap().get("desc")).hasSize(2);
    }

    @Test
    void routeRejectsNullRequest() {
        assertThatThrownBy(() -> client.routes().route(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Routes.route")
                .hasMessageContaining("RouteRequest.builder");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- matrix ---

    @Test
    void matrixSendsPointsTypeAndAlwaysPreciseAndSimple() {
        mockHttp.addResponse("Routes", "matrix", Fixtures.load("routes_matrix_success"));

        client.routes()
                .matrix(MatrixRequest.builder(List.of(WARSAW, GDANSK, POZNAN, KRAKOW), MatrixType.TIME)
                        .build());

        assertProtectedCall("matrix");
        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry(
                                "points",
                                List.of(
                                        List.of(21.0073642, 52.2297),
                                        List.of(18.6282, 54.352),
                                        List.of(16.9252, 52.4064),
                                        List.of(19.9448, 50.0647))),
                        Map.entry("type", 1L),
                        Map.entry("precise", false),
                        Map.entry("simple", false));
    }

    @Test
    void matrixSendsDistanceTypeAndExplicitFlags() {
        mockHttp.addResponse("Routes", "matrix", Fixtures.load("routes_matrix_success"));

        client.routes()
                .matrix(MatrixRequest.builder(List.of(WARSAW, GDANSK, POZNAN, KRAKOW), MatrixType.DISTANCE)
                        .precise(true)
                        .simple(true)
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsEntry("type", 2L)
                .containsEntry("precise", true)
                .containsEntry("simple", true);
    }

    @Test
    void matrixPassesExtraAndNamedParamsWin() {
        mockHttp.addResponse("Routes", "matrix", Fixtures.load("routes_matrix_success"));

        client.routes()
                .matrix(MatrixRequest.builder(List.of(WARSAW, GDANSK, POZNAN, KRAKOW), MatrixType.TIME)
                        .extra(Map.of("type", 99))
                        .extra("customField", "value")
                        .build());

        Map<String, Object> payload = mockHttp.lastPayload();
        assertThat(payload.keySet()).containsExactly("type", "customField", "points", "precise", "simple");
        assertThat(payload).containsEntry("type", 1L).containsEntry("customField", "value");
    }

    @Test
    void matrixReturnsTheResultArray() {
        Map<String, Object> fixture = Fixtures.load("routes_matrix_success");
        mockHttp.addResponse("Routes", "matrix", fixture);

        ApiResponse response = client.routes()
                .matrix(MatrixRequest.builder(List.of(WARSAW, GDANSK, POZNAN, KRAKOW), MatrixType.TIME)
                        .build());

        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.dataAsList()).hasSize(6);
        assertThat(response.dataAsList().get(0))
                .isEqualTo(Map.of("points", "0-1", "length", 342000L, "duration", 210L));
    }

    @Test
    void matrixRejectsNullRequest() {
        assertThatThrownBy(() -> client.routes().matrix(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Routes.matrix")
                .hasMessageContaining("MatrixRequest.builder");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- optimize ---

    @Test
    void optimizeSendsPointsTypeAndAlwaysFixedEndWithSession() {
        mockHttp.addResponse("Routes", "optimize", Fixtures.load("routes_optimize_success"));

        client.routes()
                .optimize(OptimizeRequest.builder(List.of(WARSAW, GDANSK, POZNAN, KRAKOW), OptimizeType.TIME)
                        .build());

        assertProtectedCall("optimize");
        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry(
                                "points",
                                List.of(
                                        List.of(21.0073642, 52.2297),
                                        List.of(18.6282, 54.352),
                                        List.of(16.9252, 52.4064),
                                        List.of(19.9448, 50.0647))),
                        Map.entry("type", 1L),
                        Map.entry("fixedEnd", true));
    }

    @Test
    void optimizeSendsRouteObjectAndFixedEndFalse() {
        mockHttp.addResponse("Routes", "optimize", Fixtures.load("routes_optimize_success"));
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("type", "truck");
        object.put("truckParams", Map.of("truckWeight", 40.0));

        client.routes()
                .optimize(OptimizeRequest.builder(List.of(WARSAW, KRAKOW), OptimizeType.DISTANCE)
                        .fixedEnd(false)
                        .route(Map.of("avoidTolls", false))
                        .object(object)
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("points", List.of(List.of(21.0073642, 52.2297), List.of(19.9448, 50.0647))),
                        Map.entry("type", 2L),
                        Map.entry("fixedEnd", false),
                        Map.entry("route", Map.of("avoidTolls", false)),
                        Map.entry("object", Map.of("type", "truck", "truckParams", Map.of("truckWeight", 40.0))));
    }

    @Test
    void optimizeOmitsRouteAndObjectWhenNotProvided() {
        mockHttp.addResponse("Routes", "optimize", Fixtures.load("routes_optimize_success"));

        client.routes()
                .optimize(OptimizeRequest.builder(List.of(WARSAW, GDANSK), OptimizeType.TIME)
                        .build());

        assertThat(mockHttp.lastPayload()).doesNotContainKeys("route", "object");
    }

    @Test
    void optimizePassesExtraAndNamedParamsWin() {
        mockHttp.addResponse("Routes", "optimize", Fixtures.load("routes_optimize_success"));

        client.routes()
                .optimize(OptimizeRequest.builder(List.of(WARSAW, GDANSK), OptimizeType.TIME)
                        .extra("fixedEnd", false)
                        .extra("customField", "value")
                        .build());

        Map<String, Object> payload = mockHttp.lastPayload();
        assertThat(payload.keySet()).containsExactly("fixedEnd", "customField", "points", "type");
        assertThat(payload).containsEntry("fixedEnd", true).containsEntry("customField", "value");
    }

    @Test
    void optimizeReturnsOrderAndSections() {
        Map<String, Object> fixture = Fixtures.load("routes_optimize_success");
        mockHttp.addResponse("Routes", "optimize", fixture);

        ApiResponse response = client.routes()
                .optimize(OptimizeRequest.builder(List.of(WARSAW, GDANSK, POZNAN, KRAKOW), OptimizeType.TIME)
                        .build());

        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.dataAsMap()).containsEntry("order", "0,3,2,1").containsKeys("points", "sections");
        assertThat(((String) response.dataAsMap().get("order")).split(",")).hasSize(4);
        assertThat((List<?>) response.dataAsMap().get("sections")).hasSize(4);
    }

    @Test
    void optimizeRejectsNullRequest() {
        assertThatThrownBy(() -> client.routes().optimize(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Routes.optimize")
                .hasMessageContaining("OptimizeRequest.builder");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- optimizeQueue ---

    private static Map<String, Object> queuePoint(String id, double x, double y) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("x", x);
        map.put("y", y);
        return map;
    }

    @Test
    void optimizeQueueSendsIdPointsOptimizeByAndAlwaysFixedEndWithSession() {
        mockHttp.addResponse("Routes", "optimizeQueue", Fixtures.load("routes_optimize_queue_success"));

        client.routes()
                .optimizeQueue(OptimizeQueueRequest.builder(
                                List.of(
                                        new QueuePoint("Warszawa", 21.0073642, 52.2297),
                                        new QueuePoint("Gdansk", 18.6282, 54.352)),
                                OptimizeBy.DISTANCE)
                        .build());

        assertProtectedCall("optimizeQueue");
        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry(
                                "points",
                                List.of(
                                        queuePoint("Warszawa", 21.0073642, 52.2297),
                                        queuePoint("Gdansk", 18.6282, 54.352))),
                        Map.entry("optimizeBy", "distance"),
                        Map.entry("fixedEnd", true));
    }

    @Test
    void optimizeQueueSendsRouteObjectAndFixedEndFalse() {
        mockHttp.addResponse("Routes", "optimizeQueue", Fixtures.load("routes_optimize_queue_success"));

        client.routes()
                .optimizeQueue(OptimizeQueueRequest.builder(
                                List.of(new QueuePoint("A", 21.0, 52.0), new QueuePoint("B", 18.0, 54.0)),
                                OptimizeBy.TIME)
                        .fixedEnd(false)
                        .route(Map.of("start", "NOW"))
                        .object(Map.of("type", "car"))
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsEntry("optimizeBy", "time")
                .containsEntry("fixedEnd", false)
                .containsEntry("route", Map.of("start", "NOW"))
                .containsEntry("object", Map.of("type", "car"));
        assertThat(mockHttp.lastPayload().keySet())
                .containsExactly("points", "optimizeBy", "fixedEnd", "route", "object");
    }

    @Test
    void optimizeQueueOmitsRouteAndObjectWhenNotProvided() {
        mockHttp.addResponse("Routes", "optimizeQueue", Fixtures.load("routes_optimize_queue_success"));

        client.routes()
                .optimizeQueue(OptimizeQueueRequest.builder(List.of(new QueuePoint("A", 21.0, 52.0)), OptimizeBy.TIME)
                        .build());

        assertThat(mockHttp.lastPayload()).doesNotContainKeys("route", "object");
    }

    @Test
    void optimizeQueuePassesExtraAndNamedParamsWin() {
        mockHttp.addResponse("Routes", "optimizeQueue", Fixtures.load("routes_optimize_queue_success"));

        client.routes()
                .optimizeQueue(OptimizeQueueRequest.builder(List.of(new QueuePoint("A", 21.0, 52.0)), OptimizeBy.TIME)
                        .extra("optimizeBy", "distance")
                        .extra("customField", "value")
                        .build());

        Map<String, Object> payload = mockHttp.lastPayload();
        assertThat(payload.keySet()).containsExactly("optimizeBy", "customField", "points", "fixedEnd");
        assertThat(payload).containsEntry("optimizeBy", "time").containsEntry("customField", "value");
    }

    @Test
    void optimizeQueueReturnsRequestId() {
        Map<String, Object> fixture = Fixtures.load("routes_optimize_queue_success");
        mockHttp.addResponse("Routes", "optimizeQueue", fixture);

        ApiResponse response = client.routes()
                .optimizeQueue(OptimizeQueueRequest.builder(List.of(new QueuePoint("A", 21.0, 52.0)), OptimizeBy.TIME)
                        .build());

        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.dataAsMap()).containsEntry("requestId", "opt-req-abc123");
    }

    @Test
    void optimizeQueueRejectsNullRequest() {
        assertThatThrownBy(() -> client.routes().optimizeQueue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Routes.optimizeQueue")
                .hasMessageContaining("OptimizeQueueRequest.builder");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- optimizeQueueResult ---

    @Test
    void optimizeQueueResultSendsRequestIdWithSession() {
        mockHttp.addResponse("Routes", "optimizeQueueResult", Fixtures.load("routes_optimize_queue_result_success"));

        client.routes().optimizeQueueResult("opt-req-abc123");

        assertProtectedCall("optimizeQueueResult");
        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("requestId", "opt-req-abc123"));
    }

    @Test
    void optimizeQueueResultPassesExtraAndRequestIdWins() {
        mockHttp.addResponse("Routes", "optimizeQueueResult", Fixtures.load("routes_optimize_queue_result_success"));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("requestId", "ignored");
        extra.put("customField", "value");

        client.routes().optimizeQueueResult("opt-req-abc123", extra);

        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("requestId", "opt-req-abc123"), Map.entry("customField", "value"));
        assertThat(extra).containsEntry("requestId", "ignored");
    }

    @Test
    void optimizeQueueResultWithNullExtraSendsOnlyRequestId() {
        mockHttp.addResponse("Routes", "optimizeQueueResult", Fixtures.load("routes_optimize_queue_result_success"));

        client.routes().optimizeQueueResult("opt-req-abc123", null);

        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("requestId", "opt-req-abc123"));
    }

    @Test
    void optimizeQueueResultReturnsReadySequence() {
        Map<String, Object> fixture = Fixtures.load("routes_optimize_queue_result_success");
        mockHttp.addResponse("Routes", "optimizeQueueResult", fixture);

        ApiResponse response = client.routes().optimizeQueueResult("opt-req-abc123");

        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.dataAsMap()).containsEntry("resultReady", true);
        assertThat(response.dataAsMap().get("optimizedSequence"))
                .isEqualTo(List.of("Warszawa", "Gdansk", "Poznan", "Wroclaw", "Krakow", "Bialystok"));
    }

    @Test
    void optimizeQueueResultRejectsBlankRequestId() {
        assertThatThrownBy(() -> client.routes().optimizeQueueResult(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Routes.optimizeQueueResult")
                .hasMessageContaining("requestId is required but was empty")
                .hasMessageContaining("optimizeQueue");
        assertThatThrownBy(() -> client.routes().optimizeQueueResult(null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestId is required but was null");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- construction ---

    @Test
    void constructorRejectsNullClient() {
        assertThatThrownBy(() -> new Routes(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("new Routes")
                .hasMessageContaining("client.routes()");
    }
}
