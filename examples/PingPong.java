import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: PingPong.ping() - connectivity and session check.
 *
 * <p>Usage (credentials from the environment, e.g. {@code cp .env.example .env} and fill it):
 *
 * <pre>
 *   make examples
 *   java -cp target/classes examples/PingPong.java
 * </pre>
 *
 * <p>What this program does:
 *
 * <ul>
 *   <li>builds the client from AUTOMAPA_API_KEY and AUTOMAPA_API_PASSWORD ({@code Config.fromEnvironment()}),
 *   <li>opens an API session (salt, hashed password, session id),
 *   <li>sends "ping" and prints the reply ("pong").
 * </ul>
 */
public final class PingPong {

    private PingPong() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== PingPong.ping() - connectivity and session check ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        try {
            ApiClient client = ApiClient.create(config);
            client.openSession();
            System.out.println("Session     : opened");

            ApiResponse response = client.pingPong().ping("ping");
            System.out.println("HTTP status : " + response.status());
            System.out.println("Reply       : " + response.dataAsMap().get("msg"));
            // The full API response is always available via response.raw().
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }
}
