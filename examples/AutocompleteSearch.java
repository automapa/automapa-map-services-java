import java.util.List;
import java.util.Map;
import java.util.Objects;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.AutocompleteSearchRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Autocomplete.search() - search for places and POI by phrase.
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
 *   <li>searches for places matching a free-text query,
 *   <li>filters the results by type ({@code place}; omit {@code types} to include {@code poi} results too),
 *   <li>prints the name, city, type and coordinates of each result,
 *   <li>handles errors via AutomapaException.
 * </ul>
 */
public final class AutocompleteSearch {

    private AutocompleteSearch() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Autocomplete.search() - places and POI ===");
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
            ApiResponse response = client.autocomplete()
                    .search(AutocompleteSearchRequest.builder("Warszawa Domaniewska")
                            .types(List.of("place"))
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
                Object coordsValue = item.get("coords");
                Map<?, ?> coords = coordsValue instanceof Map ? (Map<?, ?>) coordsValue : Map.of();
                Object name = item.get("place") != null ? item.get("place") : item.get("name");
                System.out.println("Name   : " + Objects.toString(name, "n/a"));
                System.out.println("City   : " + Objects.toString(item.get("city"), "n/a"));
                System.out.println("Type   : " + Objects.toString(item.get("type"), "n/a"));
                System.out.println("Coords : x=" + Objects.toString(coords.get("x"), "n/a") + ", y="
                        + Objects.toString(coords.get("y"), "n/a"));
                System.out.println();
            }
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }
}
