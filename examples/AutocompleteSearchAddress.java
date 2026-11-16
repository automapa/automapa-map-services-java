import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.SearchAddressRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Autocomplete.searchAddress() - search for address objects.
 *
 * <p>Usage (credentials from the environment, e.g. {@code cp .env.example .env} and fill it):
 *
 * <pre>
 *   make examples
 * </pre>
 *
 * <p>What this program does:
 *
 * <ul>
 *   <li>searches for address objects matching a city and a street prefix,
 *   <li>the {@code source} argument defines the field being searched: {@code place}, {@code street},
 *       {@code number} or {@code zipcode},
 *   <li>prints the street, city and postal code of each match,
 *   <li>handles errors via AutomapaException.
 * </ul>
 */
public final class AutocompleteSearchAddress {

    private AutocompleteSearchAddress() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Autocomplete.searchAddress() - address search ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("city", "Warszawa");
        query.put("street", "Marsza\u0142k"); // prefix match
        query.put("zipcode", "00-004");
        query.put("number", "");

        try {
            ApiClient client = ApiClient.create(config);
            ApiResponse response = client.autocomplete()
                    .searchAddress(SearchAddressRequest.builder(query, "zipcode")
                            .limit(5)
                            // .selected(List.of("city", "zipcode")) // optional: fields already confirmed
                            .build());

            Object items = response.dataAsMap().get("items");
            if (!(items instanceof List) || ((List<?>) items).isEmpty()) {
                System.out.println("No results found.");
                return;
            }

            List<?> results = (List<?>) items;
            System.out.println("Results: " + results.size());
            System.out.println();
            for (Object entry : results) {
                Map<?, ?> item = (Map<?, ?>) entry;
                System.out.println("Street      : " + Objects.toString(item.get("street"), "n/a"));
                System.out.println("City        : " + Objects.toString(item.get("city"), "n/a"));
                System.out.println("Postal code : " + Objects.toString(item.get("zipcode"), "n/a"));
                System.out.println();
            }
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }
}
