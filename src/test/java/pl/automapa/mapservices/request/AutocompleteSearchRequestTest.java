package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutocompleteSearchRequestTest {

    @Test
    void queryOnlyWithoutTypes() {
        Map<String, Object> params = AutocompleteSearchRequest.builder("Warszawa Domaniewska 37")
                .build()
                .toParams();

        assertThat(params).containsExactly(Map.entry("query", "Warszawa Domaniewska 37"));
    }

    @Test
    void typesAreSentAfterQuery() {
        Map<String, Object> params = AutocompleteSearchRequest.builder("Warszawa")
                .types(List.of("place", "poi"))
                .build()
                .toParams();

        assertThat(params).containsExactly(Map.entry("query", "Warszawa"), Map.entry("types", List.of("place", "poi")));
    }

    @Test
    void nullTypesAreLeftOut() {
        Map<String, Object> params = AutocompleteSearchRequest.builder("Warszawa")
                .types(List.of("place"))
                .types(null)
                .build()
                .toParams();

        assertThat(params).containsOnlyKeys("query");
    }

    @Test
    void typesListIsCopied() {
        List<String> types = new ArrayList<>(List.of("place"));
        AutocompleteSearchRequest.Builder builder =
                AutocompleteSearchRequest.builder("Warszawa").types(types);
        types.add("poi");

        assertThat(builder.build().toParams()).containsEntry("types", List.of("place"));
    }

    @Test
    void namedQueryOverridesExtraAndExtraKeepsItsPosition() {
        Map<String, Object> params = AutocompleteSearchRequest.builder("Warszawa")
                .extra("futureParam", "value")
                .extra("query", "OVERRIDDEN")
                .extra("debugMode", true)
                .build()
                .toParams();

        assertThat(params)
                .containsExactly(
                        Map.entry("futureParam", "value"),
                        Map.entry("query", "Warszawa"),
                        Map.entry("debugMode", true));
    }

    @Test
    void formatGoesToTheParams() {
        Map<String, Object> params = AutocompleteSearchRequest.builder("Warszawa")
                .format("google")
                .build()
                .toParams();

        assertThat(params).containsEntry("format", "google").containsEntry("query", "Warszawa");
    }

    @Test
    void nullQueryIsRejected() {
        assertThatThrownBy(() -> AutocompleteSearchRequest.builder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AutocompleteSearchRequest.builder")
                .hasMessageContaining("query is required but was null")
                .hasMessageContaining("builder(\"Warszawa Domaniewska\")");
    }

    @Test
    void blankQueryIsRejected() {
        assertThatThrownBy(() -> AutocompleteSearchRequest.builder(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query is required but was empty");
    }
}
