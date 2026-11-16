package pl.automapa.mapservices.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JsonStringifyTest {

    /** Dictionary value the way SDK enums expose their API value. */
    private enum Kind implements JsonValue {
        TIME(1),
        DISTANCE(2);

        private final int apiValue;

        Kind(int apiValue) {
            this.apiValue = apiValue;
        }

        int apiValue() {
            return apiValue;
        }

        @Override
        public Object toJsonValue() {
            return apiValue();
        }
    }

    private enum Plain {
        VALUE
    }

    @Test
    void writesMapInInsertionOrderWithoutSpaces() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("b", 1);
        map.put("a", "x");
        map.put("c", null);

        assertThat(Json.stringify(map)).isEqualTo("{\"b\":1,\"a\":\"x\",\"c\":null}");
    }

    @Test
    void writesEmptyMapAsObjectAndEmptyCollectionAsArray() {
        assertThat(Json.stringify(Map.of())).isEqualTo("{}");
        assertThat(Json.stringify(List.of())).isEqualTo("[]");
    }

    @Test
    void writesNestedStructures() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("route", List.of(List.of(21.0, 52.2), List.of(19.9, 50.06)));
        map.put("params", Map.of("lang", "pl"));

        assertThat(Json.stringify(map))
                .isEqualTo("{\"route\":[[21.0,52.2],[19.9,50.06]],\"params\":{\"lang\":\"pl\"}}");
    }

    @Test
    void writesAnyCollection() {
        assertThat(Json.stringify(new LinkedHashSet<>(List.of("a", "b")))).isEqualTo("[\"a\",\"b\"]");
        assertThat(Json.stringify(new ArrayList<>(Arrays.asList(1, null)))).isEqualTo("[1,null]");
    }

    @Test
    void writesArrays() {
        assertThat(Json.stringify(new Object[] {1, "a", null})).isEqualTo("[1,\"a\",null]");
        assertThat(Json.stringify(new String[] {"x"})).isEqualTo("[\"x\"]");
        assertThat(Json.stringify(new double[] {21.0, 52.5})).isEqualTo("[21.0,52.5]");
        assertThat(Json.stringify(new int[0])).isEqualTo("[]");
    }

    @Test
    void writesNumbers() {
        assertThat(Json.stringify(7)).isEqualTo("7");
        assertThat(Json.stringify(-7L)).isEqualTo("-7");
        assertThat(Json.stringify((short) 3)).isEqualTo("3");
        assertThat(Json.stringify((byte) 4)).isEqualTo("4");
        assertThat(Json.stringify(new BigInteger("123456789012345678901234567890")))
                .isEqualTo("123456789012345678901234567890");
        assertThat(Json.stringify(new BigDecimal("52.229700"))).isEqualTo("52.229700");
        assertThat(Json.stringify(52.2297)).isEqualTo("52.2297");
        assertThat(Json.stringify(1.5f)).isEqualTo("1.5");
        assertThat(Json.stringify(1.0e20)).isEqualTo("1.0E20");
    }

    @Test
    void writtenNumbersParseBack() {
        for (Object number : List.of(1.0e20, 1.0e-7, -0.0, Double.MAX_VALUE, Double.MIN_VALUE, 0.1f)) {
            assertThat(Json.parse(Json.stringify(number))).isInstanceOf(Double.class);
        }
        assertThat(Json.parse(Json.stringify(new BigDecimal("1E+3")))).isEqualTo(1000.0);
    }

    @Test
    void writesBooleansAndNull() {
        assertThat(Json.stringify(true)).isEqualTo("true");
        assertThat(Json.stringify(false)).isEqualTo("false");
        assertThat(Json.stringify(null)).isEqualTo("null");
    }

    @Test
    void escapesQuotesBackslashesAndControlCharacters() {
        assertThat(Json.stringify("\" \\ / \b \f \n \r \t")).isEqualTo("\"\\\" \\\\ / \\b \\f \\n \\r \\t\"");
        assertThat(Json.stringify("\u0000\u0001\u001f")).isEqualTo("\"\\u0000\\u0001\\u001f\"");
    }

    @Test
    void keepsNonAsciiUnescaped() {
        String text = "Za\u017c\u00f3\u0142\u0107 g\u0119\u015bl\u0105 \uD83D\uDE00 \u007f";

        assertThat(Json.stringify(text)).isEqualTo("\"" + text + "\"");
    }

    @Test
    void writesJsonValueThroughItsJsonRepresentation() {
        assertThat(Json.stringify(Kind.DISTANCE)).isEqualTo("2");
        assertThat(Json.stringify(Map.of("type", Kind.TIME))).isEqualTo("{\"type\":1}");
        JsonValue nested = () -> Map.of("by", "time");
        assertThat(Json.stringify(List.of(nested))).isEqualTo("[{\"by\":\"time\"}]");
    }

    @Test
    void rejectsNotFiniteNumbers() {
        assertThatThrownBy(() -> Json.stringify(Double.NaN))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("java.lang.Double")
                .hasMessageContaining("NaN");
        assertThatThrownBy(() -> Json.stringify(List.of(Double.POSITIVE_INFINITY)))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("Infinity");
        assertThatThrownBy(() -> Json.stringify(Float.NEGATIVE_INFINITY))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("java.lang.Float");
    }

    @Test
    void rejectsUnsupportedTypesNamingTheType() {
        assertThatThrownBy(() -> Json.stringify(new Object()))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("java.lang.Object")
                .satisfies(e -> assertThat(((JsonException) e).getOffset()).isEqualTo(-1));
        assertThatThrownBy(() -> Json.stringify(Map.of("n", new AtomicInteger(1))))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("java.util.concurrent.atomic.AtomicInteger");
        assertThatThrownBy(() -> Json.stringify(Plain.VALUE))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining(Plain.class.getName())
                .hasMessageContaining("JsonValue");
        assertThatThrownBy(() -> Json.stringify('c'))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("java.lang.Character");
    }

    @Test
    void rejectsNonStringMapKeys() {
        Map<Object, Object> map = new HashMap<>();
        map.put(1, "a");

        assertThatThrownBy(() -> Json.stringify(map))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("keys must be strings")
                .hasMessageContaining("java.lang.Integer");
    }

    @Test
    void rejectsNullMapKey() {
        Map<String, Object> map = new HashMap<>();
        map.put(null, "a");

        assertThatThrownBy(() -> Json.stringify(map))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("keys must be strings")
                .hasMessageContaining("null");
    }

    @Test
    void rejectsCyclesThroughTheDepthLimit() {
        List<Object> cyclic = new ArrayList<>();
        cyclic.add(cyclic);

        assertThatThrownBy(() -> Json.stringify(cyclic))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("nesting depth exceeds " + Json.MAX_DEPTH)
                .hasMessageContaining("cycle");
    }

    @Test
    void rejectsJsonValueReturningItself() {
        JsonValue[] self = new JsonValue[1];
        self[0] = () -> self[0];

        assertThatThrownBy(() -> Json.stringify(self[0]))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("nesting depth exceeds");
    }
}
