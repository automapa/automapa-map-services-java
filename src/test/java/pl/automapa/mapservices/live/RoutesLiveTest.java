package pl.automapa.mapservices.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.exception.ValidationException;
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

/** Routes service ({@code route}, {@code matrix}, {@code optimize*}) on the live API (suite {@code routes}). */
class RoutesLiveTest extends LiveApiTestBase {

    private static final Point WARSAW = new Point(21.0073642, 52.2297);
    private static final Point GDANSK = new Point(18.6282, 54.352);
    private static final Point POZNAN = new Point(16.9252, 52.4064);
    private static final Point KRAKOW = new Point(19.9448, 50.0647);

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration POLL_LIMIT = Duration.ofSeconds(90);

    @Override
    protected String suiteName() {
        return "routes";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static double number(Object value) {
        assertThat(value).isInstanceOf(Number.class);
        return ((Number) value).doubleValue();
    }

    @Test
    void routeWarsawToGdanskReturnsLengthAndEta() {
        // The API fills "eta" only when route.desc is true (docs: API_ROUTES.md, route).
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("type", "short");
        route.put("traffic", true);
        route.put("desc", true);
        ApiResponse response = callLive(() -> buildApiClient()
                .routes()
                .route(RouteRequest.builder(List.of(WARSAW, GDANSK), route, Map.of("type", "car"))
                        .build()));

        assertThat(response.status()).isEqualTo(200);
        Map<String, Object> data = response.dataAsMap();
        assertThat(number(data.get("length"))).as("route length in metres").isGreaterThan(0);
        assertThat(number(data.get("eta"))).as("route eta in minutes").isGreaterThan(0);
        assertThat((List<?>) data.get("desc")).as("manoeuvres for desc=true").isNotEmpty();
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void matrixOfFourCitiesReturnsPairs() {
        ApiResponse response = callLive(() -> buildApiClient()
                .routes()
                .matrix(MatrixRequest.builder(List.of(WARSAW, GDANSK, POZNAN, KRAKOW), MatrixType.TIME)
                        .build()));

        assertThat(response.status()).isEqualTo(200);
        List<Object> rows = response.dataAsList();
        assertThat(rows).as("matrix should return at least one pair").isNotEmpty();
        Map<String, Object> first = map(rows.get(0));
        assertThat(first).containsKeys("points", "length", "duration");
        assertThat(String.valueOf(first.get("points"))).matches("\\d+-\\d+");
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void matrixWithFewerThanFourPointsIsRejectedByTheApi() {
        assertThatThrownBy(() -> callLive(() -> buildApiClient()
                        .routes()
                        .matrix(MatrixRequest.builder(List.of(WARSAW, GDANSK), MatrixType.DISTANCE)
                                .build())))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("At least 4 different points");
    }

    @Test
    void optimizeOfFourCitiesReturnsOrderAndSections() {
        ApiResponse response = callLive(() -> buildApiClient()
                .routes()
                .optimize(OptimizeRequest.builder(List.of(WARSAW, GDANSK, POZNAN, KRAKOW), OptimizeType.TIME)
                        .fixedEnd(false)
                        .object(Map.of("type", "car"))
                        .build()));

        assertThat(response.status()).isEqualTo(200);
        Map<String, Object> data = response.dataAsMap();
        List<String> order = Arrays.asList(String.valueOf(data.get("order")).split(","));
        assertThat(order)
                .as("order is a permutation of the point indexes")
                .containsExactlyInAnyOrder("0", "1", "2", "3");
        assertThat(order.get(0)).as("the start point stays first").isEqualTo("0");
        assertThat((List<?>) data.get("sections")).as("one section per point").hasSize(4);
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void optimizeQueueResultIsReadyWithinTheLimit() {
        ApiClient client = buildApiClient();
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("avoidTolls", false);
        route.put("start", "NOW");
        ApiResponse queued = callLive(() -> client.routes()
                .optimizeQueue(OptimizeQueueRequest.builder(
                                List.of(
                                        new QueuePoint("Warsaw", WARSAW.x(), WARSAW.y()),
                                        new QueuePoint("Gdansk", GDANSK.x(), GDANSK.y()),
                                        new QueuePoint("Poznan", POZNAN.x(), POZNAN.y()),
                                        new QueuePoint("Krakow", KRAKOW.x(), KRAKOW.y())),
                                OptimizeBy.TIME)
                        .fixedEnd(false)
                        .route(route)
                        .object(Map.of("type", "car"))
                        .build()));

        assertThat(queued.status()).isEqualTo(200);
        String requestId = String.valueOf(queued.dataAsMap().get("requestId"));
        assertThat(requestId).as("requestId of optimizeQueue").isNotBlank().isNotEqualTo("null");

        Map<String, Object> result = pollUntilReady(client, requestId);

        assertThat(new ArrayList<Object>((List<?>) result.get("optimizedSequence")))
                .as("optimizedSequence lists every point id once")
                .containsExactlyInAnyOrder("Warsaw", "Gdansk", "Poznan", "Krakow");
    }

    private Map<String, Object> pollUntilReady(ApiClient client, String requestId) {
        long deadline = System.nanoTime() + POLL_LIMIT.toNanos();
        ApiResponse last = null;
        while (System.nanoTime() < deadline) {
            LiveSupport.sleep(POLL_INTERVAL);
            last = callLive(() -> client.routes().optimizeQueueResult(requestId));
            assertThat(last.status()).isEqualTo(200);
            if (isReady(last.dataAsMap().get("resultReady"))) {
                return last.dataAsMap();
            }
        }
        return fail("optimizeQueueResult did not report resultReady within " + POLL_LIMIT.toSeconds()
                + " s for requestId " + requestId + "; last response: "
                + (last == null ? "none" : last.raw()));
    }

    /** The API documents {@code resultReady} as 0/1 and answers with a boolean; both are accepted. */
    private static boolean isReady(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof Number number && number.intValue() == 1);
    }
}
