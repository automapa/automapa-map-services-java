import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.OptimizeBy;
import pl.automapa.mapservices.request.OptimizeQueueRequest;
import pl.automapa.mapservices.request.QueuePoint;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Routes.optimizeQueue() + optimizeQueueResult() - asynchronous optimisation.
 *
 * <p>Usage (credentials from the environment, e.g. {@code cp .env.example .env} and fill it):
 *
 * <pre>
 *   make examples
 * </pre>
 *
 * <p>What this program does:
 *
 * <ol>
 *   <li>submits an asynchronous optimisation job for 6 points (optimizeQueue),
 *   <li>receives a requestId and polls the API every 2 seconds (optimizeQueueResult),
 *   <li>once the result is ready, prints the optimal sequence of point ids.
 * </ol>
 *
 * <p>When to use async instead of sync: for more than 10 points (sync optimize() supports max 10, async
 * optimizeQueue() up to 50). The SDK does not poll by itself - the loop below does.
 */
public final class RoutesOptimizeAsync {

    private static final int MAX_ATTEMPTS = 15;
    private static final long POLL_INTERVAL_MILLIS = 2000;

    private RoutesOptimizeAsync() {}

    /**
     * Runs the example.
     *
     * @param args not used
     * @throws InterruptedException when the pause between the polls is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Routes.optimizeQueue() + optimizeQueueResult() - async optimisation ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Each point needs a unique id - the API returns the order as a list of these ids.
        List<QueuePoint> points = List.of(
                new QueuePoint("Warsaw", 21.0073642, 52.2297),
                new QueuePoint("Gdansk", 18.6282, 54.3520),
                new QueuePoint("Poznan", 16.9252, 52.4064),
                new QueuePoint("Krakow", 19.9448, 50.0647),
                new QueuePoint("Wroclaw", 17.0385, 51.1079),
                new QueuePoint("Bialystok", 23.1688, 53.1325));

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("avoidTolls", false);
        route.put("start", "NOW");
        route.put("avoidFerries", true);
        route.put("avoidHighways", false);

        try {
            ApiClient client = ApiClient.create(config);

            // Step 1: submit the optimisation job.
            ApiResponse queued = client.routes()
                    .optimizeQueue(OptimizeQueueRequest.builder(points, OptimizeBy.TIME) // TIME or DISTANCE
                            .fixedEnd(false)
                            .route(route)
                            .object(Map.of("type", "car"))
                            .build());

            String requestId = String.valueOf(queued.dataAsMap().get("requestId"));
            System.out.println("Optimisation job submitted.");
            System.out.println("requestId : " + requestId);
            System.out.println();

            // Step 2: poll every 2 seconds.
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                Thread.sleep(POLL_INTERVAL_MILLIS);

                Map<String, Object> data =
                        client.routes().optimizeQueueResult(requestId).dataAsMap();
                boolean ready = isReady(data.get("resultReady"));
                System.out.printf("Attempt %d/%d: %s%n", attempt, MAX_ATTEMPTS, ready ? "ready" : "in progress...");

                if (ready) {
                    List<?> sequence = (List<?>) data.get("optimizedSequence");
                    StringBuilder text = new StringBuilder();
                    for (Object id : sequence) {
                        if (text.length() > 0) {
                            text.append(" -> ");
                        }
                        text.append(id);
                    }
                    System.out.println();
                    System.out.println("Optimal id sequence: " + text);
                    return;
                }
            }

            System.out.println();
            System.out.println("Attempt limit reached. Check the result later using:");
            System.out.println("  client.routes().optimizeQueueResult(\"" + requestId + "\");");
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }

    /** The API documents resultReady as 0/1 and answers with a boolean; both are accepted. */
    private static boolean isReady(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof Number number && number.intValue() == 1);
    }
}
