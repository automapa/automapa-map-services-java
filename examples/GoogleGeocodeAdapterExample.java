import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.adapter.GoogleGeocodeAdapter;
import pl.automapa.mapservices.exception.AutomapaException;

/**
 * Example: GoogleGeocodeAdapter - drop-in for Google Geocoding API users.
 *
 * <p>Usage (credentials from the environment, e.g. {@code cp .env.example .env} and fill it):
 *
 * <pre>
 *   make examples
 * </pre>
 *
 * <p>What this program does: swaps the Google Geocoding API for Automapa with GoogleGeocodeAdapter, keeping the
 * same input and output format. Three input modes are shown:
 *
 * <ul>
 *   <li>components text - most precise (recommended),
 *   <li>address text - best-effort parse (easiest migration),
 *   <li>address map - Automapa structured format (no parsing needed).
 * </ul>
 */
public final class GoogleGeocodeAdapterExample {

    private GoogleGeocodeAdapterExample() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        GoogleGeocodeAdapter adapter = new GoogleGeocodeAdapter(ApiClient.create(config));
        boolean failed = false;

        // Mode 1: components text (Google components filter format)
        // Equivalent to: ?components=locality:Warszawa|route:Domaniewska|street_number:37
        System.out.println("=== Mode 1: components text ===");
        System.out.println();
        failed |= !run(adapter, Map.of("components", "locality:Warszawa|route:Domaniewska|street_number:37"));
        System.out.println();

        // Mode 2: address text (free-form, best-effort parse)
        // Equivalent to: ?address=Domaniewska+37,+Warszawa
        // "language" and "key" are accepted and ignored - the SDK uses its own credentials.
        System.out.println("=== Mode 2: address text ===");
        System.out.println();
        failed |= !run(adapter, Map.of("address", "Domaniewska 37, Warszawa", "language", "pl", "key", "n/a"));
        System.out.println();

        // Mode 3: address map (Automapa structured format, no parsing needed)
        System.out.println("=== Mode 3: address map ===");
        System.out.println();
        failed |= !run(
                adapter,
                Map.of(
                        "address",
                        Map.of("city", "Gda\u0144sk", "street", "D\u0142uga", "house", "1"),
                        "maxResults",
                        3));

        if (failed) {
            System.exit(1);
        }
    }

    private static boolean run(GoogleGeocodeAdapter adapter, Map<String, ?> params) {
        try {
            printGoogleResult(adapter.geocode(params));
            return true;
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            return false;
        }
    }

    private static void printGoogleResult(Map<String, Object> result) {
        System.out.println("Status : " + result.getOrDefault("status", "n/a"));

        Object results = result.get("results");
        if (!(results instanceof List) || ((List<?>) results).isEmpty()) {
            System.out.println("No results.");
            return;
        }

        Map<?, ?> first = (Map<?, ?>) ((List<?>) results).get(0);
        Map<?, ?> geometry = (Map<?, ?>) first.get("geometry");
        Map<?, ?> location = geometry == null ? Map.of() : (Map<?, ?>) geometry.get("location");
        Object types = first.get("types");

        System.out.println("Address: " + first.get("formatted_address"));
        System.out.println("Lat    : " + location.get("lat"));
        System.out.println("Lng    : " + location.get("lng"));
        System.out.println("Types  : " + (types instanceof List ? String.join(", ", stringList((List<?>) types)) : ""));
    }

    private static List<String> stringList(List<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
