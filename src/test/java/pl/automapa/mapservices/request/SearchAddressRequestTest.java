package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchAddressRequestTest {

    private static Map<String, Object> query() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("city", "Warszawa");
        query.put("street", "Dom");
        query.put("number", "");
        query.put("zipcode", "");
        return query;
    }

    @Test
    void queryAndSourceWithoutOptionalFields() {
        Map<String, Object> params =
                SearchAddressRequest.builder(query(), "street").build().toParams();

        assertThat(params).containsExactly(Map.entry("query", query()), Map.entry("source", "street"));
        assertThat(params.get("query")).isInstanceOf(Map.class);
        assertThat(new java.util.ArrayList<Object>(((Map<?, ?>) params.get("query")).keySet()))
                .containsExactly("city", "street", "number", "zipcode");
    }

    @Test
    void limitAndSelectedFollowTheOrderOfTheApi() {
        Map<String, Object> params = SearchAddressRequest.builder(query(), "street")
                .selected(List.of("city"))
                .limit(5)
                .build()
                .toParams();

        assertThat(params)
                .containsExactly(
                        Map.entry("query", query()),
                        Map.entry("source", "street"),
                        Map.entry("limit", 5),
                        Map.entry("selected", List.of("city")));
    }

    @Test
    void nullOptionalFieldsAreLeftOut() {
        Map<String, Object> params = SearchAddressRequest.builder(query(), "street")
                .limit(5)
                .limit(null)
                .selected(List.of("city"))
                .selected(null)
                .build()
                .toParams();

        assertThat(params).containsOnlyKeys("query", "source");
    }

    @Test
    void queryAndSelectedAreCopied() {
        Map<String, Object> query = query();
        List<String> selected = new ArrayList<>(List.of("city"));
        SearchAddressRequest.Builder builder =
                SearchAddressRequest.builder(query, "street").selected(selected);
        query.put("city", "Krakow");
        selected.add("street");

        Map<String, Object> params = builder.build().toParams();
        assertThat(params.get("query")).isEqualTo(query());
        assertThat(params).containsEntry("selected", List.of("city"));
    }

    @Test
    void namedParamsOverrideExtra() {
        Map<String, Object> params = SearchAddressRequest.builder(query(), "street")
                .extra("source", "OVERRIDDEN")
                .extra("futureParam", "value")
                .build()
                .toParams();

        assertThat(params)
                .containsExactly(
                        Map.entry("source", "street"), Map.entry("futureParam", "value"), Map.entry("query", query()));
    }

    @Test
    void nullQueryIsRejected() {
        assertThatThrownBy(() -> SearchAddressRequest.builder(null, "street"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SearchAddressRequest.builder")
                .hasMessageContaining("query is required but was null");
    }

    @Test
    void emptyQueryIsRejected() {
        assertThatThrownBy(() -> SearchAddressRequest.builder(Map.of(), "street"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query is required but was empty");
    }

    @Test
    void nullSourceIsRejected() {
        assertThatThrownBy(() -> SearchAddressRequest.builder(query(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source is required but was null")
                .hasMessageContaining("street");
    }

    @Test
    void blankSourceIsRejected() {
        assertThatThrownBy(() -> SearchAddressRequest.builder(query(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source is required but was empty");
    }
}
