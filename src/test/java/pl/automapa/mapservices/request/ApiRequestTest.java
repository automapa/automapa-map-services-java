package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiRequestTest {

    /** Minimal request with two named parameters, for tests of the base class. */
    static final class SampleRequest extends ApiRequest {

        private final String name;
        private final Integer limit;

        private SampleRequest(Builder builder) {
            super(builder);
            this.name = builder.name;
            this.limit = builder.limit;
        }

        static Builder builder(String name) {
            return new Builder(name);
        }

        @Override
        protected Map<String, Object> namedParams() {
            Map<String, Object> named = new LinkedHashMap<>();
            named.put("name", name);
            named.put("limit", limit);
            return named;
        }

        static final class Builder extends ApiRequest.Builder<Builder> {

            private final String name;
            private Integer limit;

            private Builder(String name) {
                this.name = name;
            }

            Builder limit(Integer limit) {
                this.limit = limit;
                return this;
            }

            @Override
            protected Builder self() {
                return this;
            }

            SampleRequest build() {
                return new SampleRequest(this);
            }
        }
    }

    @Test
    void namedParamsOnlyWithoutExtra() {
        Map<String, Object> params = SampleRequest.builder("a").limit(5).build().toParams();

        assertThat(params).containsExactly(Map.entry("name", "a"), Map.entry("limit", 5));
    }

    @Test
    void nullNamedParamsAreSkipped() {
        assertThat(SampleRequest.builder("a").build().toParams()).containsExactly(Map.entry("name", "a"));
    }

    @Test
    void extraComesFirstAndNamedParamsOverrideItInPlace() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("limit", 99);
        extra.put("future", "value");

        Map<String, Object> params =
                SampleRequest.builder("a").limit(5).extra(extra).build().toParams();

        assertThat(params).containsExactly(Map.entry("limit", 5), Map.entry("future", "value"), Map.entry("name", "a"));
    }

    @Test
    void extraKeyIsKeptWhenNamedParamIsNull() {
        Map<String, Object> params =
                SampleRequest.builder("a").extra("limit", 7).build().toParams();

        assertThat(params).containsExactly(Map.entry("limit", 7), Map.entry("name", "a"));
    }

    @Test
    void extraWithNullValueIsPassedThrough() {
        Map<String, Object> params =
                SampleRequest.builder("a").extra("nothing", null).build().toParams();

        assertThat(params).containsEntry("nothing", null).containsEntry("name", "a");
    }

    @Test
    void extraCallsAccumulateAndLaterValuesWin() {
        Map<String, Object> params = SampleRequest.builder("a")
                .extra(Map.of("one", 1))
                .extra("two", 2)
                .extra("one", 11)
                .build()
                .toParams();

        assertThat(params).containsExactly(Map.entry("one", 11), Map.entry("two", 2), Map.entry("name", "a"));
    }

    @Test
    void extraWithNullMapIsIgnored() {
        assertThat(SampleRequest.builder("a").extra(null).build().toParams()).containsExactly(Map.entry("name", "a"));
    }

    @Test
    void extraMapIsCopiedAndNeverModified() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("limit", 1);
        SampleRequest.Builder builder = SampleRequest.builder("a").limit(5).extra(extra);
        extra.put("late", true);

        Map<String, Object> params = builder.build().toParams();

        assertThat(params).doesNotContainKey("late").containsEntry("limit", 5);
        assertThat(extra).containsEntry("limit", 1);
    }

    @Test
    void extraRejectsNullKey() {
        assertThatThrownBy(() -> SampleRequest.builder("a").extra(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extra")
                .hasMessageContaining("key");
    }

    @Test
    void extraMapRejectsNullKey() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(null, 1);

        assertThatThrownBy(() -> SampleRequest.builder("a").extra(extra))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
    }

    @Test
    void formatSetsTheFormatKeyOfExtra() {
        Map<String, Object> params =
                SampleRequest.builder("a").format("google").build().toParams();

        assertThat(params).containsExactly(Map.entry("format", "google"), Map.entry("name", "a"));
    }

    @Test
    void formatNullRemovesTheFormatKey() {
        Map<String, Object> params =
                SampleRequest.builder("a").format("google").format(null).build().toParams();

        assertThat(params).containsExactly(Map.entry("name", "a"));
    }

    @Test
    void toParamsReturnsANewMutableMapEachTime() {
        SampleRequest request = SampleRequest.builder("a").build();

        Map<String, Object> first = request.toParams();
        first.put("changed", true);

        assertThat(request.toParams()).doesNotContainKey("changed");
    }

    @Test
    void builtRequestIsNotAffectedByLaterBuilderChanges() {
        SampleRequest.Builder builder = SampleRequest.builder("a").extra("one", 1);
        SampleRequest request = builder.build();
        builder.extra("two", 2);

        assertThat(request.toParams()).doesNotContainKey("two");
    }
}
