package pl.automapa.mapservices.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonParseTest {

    @Test
    void parsesObjectIntoLinkedHashMapKeepingKeyOrder() {
        Object value = Json.parse("{\"b\":1,\"a\":2,\"c\":3}");

        assertThat(value).isInstanceOf(LinkedHashMap.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        assertThat(map.keySet()).containsExactly("b", "a", "c");
        assertThat(map).containsEntry("b", 1L).containsEntry("a", 2L).containsEntry("c", 3L);
    }

    @Test
    void parsesArrayIntoArrayList() {
        Object value = Json.parse("[1,\"two\",true,null,[],{}]");

        assertThat(value).isInstanceOf(ArrayList.class);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) value;
        assertThat(list).containsExactly(1L, "two", true, null, List.of(), Map.of());
    }

    @Test
    void parsesEmptyContainers() {
        assertThat(Json.parse("{}")).isEqualTo(Map.of());
        assertThat(Json.parse("[]")).isEqualTo(List.of());
        assertThat(Json.parse("{ }")).isEqualTo(Map.of());
        assertThat(Json.parse("[ ]")).isEqualTo(List.of());
    }

    @Test
    void parsesScalarsAtTopLevel() {
        assertThat(Json.parse("\"text\"")).isEqualTo("text");
        assertThat(Json.parse("true")).isEqualTo(Boolean.TRUE);
        assertThat(Json.parse("false")).isEqualTo(Boolean.FALSE);
        assertThat(Json.parse("null")).isNull();
        assertThat(Json.parse("42")).isEqualTo(42L);
    }

    @Test
    void parsesIntegersAsLong() {
        assertThat(Json.parse("0")).isEqualTo(0L);
        assertThat(Json.parse("-0")).isEqualTo(0L);
        assertThat(Json.parse("-17")).isEqualTo(-17L);
        assertThat(Json.parse("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
        assertThat(Json.parse("-9223372036854775808")).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void parsesIntegersOutOfLongRangeAsBigInteger() {
        assertThat(Json.parse("9223372036854775808")).isEqualTo(new BigInteger("9223372036854775808"));
        assertThat(Json.parse("-9223372036854775809")).isEqualTo(new BigInteger("-9223372036854775809"));
        assertThat(Json.parse("123456789012345678901234567890"))
                .isEqualTo(new BigInteger("123456789012345678901234567890"));
    }

    @Test
    void parsesFractionsAndExponentsAsDouble() {
        assertThat(Json.parse("52.2297")).isEqualTo(52.2297);
        assertThat(Json.parse("-21.0122")).isEqualTo(-21.0122);
        assertThat(Json.parse("1e3")).isEqualTo(1000.0);
        assertThat(Json.parse("1E3")).isEqualTo(1000.0);
        assertThat(Json.parse("1.5e+2")).isEqualTo(150.0);
        assertThat(Json.parse("25e-1")).isEqualTo(2.5);
        assertThat(Json.parse("-0.0")).isEqualTo(-0.0);
    }

    @Test
    void acceptsRfc8259WhitespaceEverywhere() {
        Object value = Json.parse(" \t\n\r{ \"a\" \n:\t[ 1 ,\r2 ] , \"b\" : null }\n ");

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("a", List.of(1L, 2L));
        expected.put("b", null);
        assertThat(value).isEqualTo(expected);
    }

    @Test
    void decodesAllSimpleEscapes() {
        assertThat(Json.parse("\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t\"")).isEqualTo("\" \\ / \b \f \n \r \t");
    }

    @Test
    void decodesUnicodeEscapesIncludingSurrogatePairs() {
        assertThat(Json.parse("\"\\u0041\\u017c\\u00F3\"")).isEqualTo("A\u017c\u00f3");
        assertThat(Json.parse("\"\\ud83d\\ude00\"")).isEqualTo("\uD83D\uDE00");
        assertThat(Json.parse("\"\\u0000\"")).isEqualTo("\u0000");
    }

    @Test
    void keepsNonAsciiCharactersLiterally() {
        assertThat(Json.parse("\"Za\u017c\u00f3\u0142\u0107 g\u0119\u015bl\u0105 ja\u017a\u0144 \uD83D\uDE00\""))
                .isEqualTo("Za\u017c\u00f3\u0142\u0107 g\u0119\u015bl\u0105 ja\u017a\u0144 \uD83D\uDE00");
    }

    @Test
    void lastDuplicateKeyWins() {
        assertThat(Json.parse("{\"a\":1,\"a\":2}")).isEqualTo(Map.of("a", 2L));
    }

    @Test
    void acceptsNestingUpToTheDepthLimit() {
        String json = "[".repeat(Json.MAX_DEPTH) + "]".repeat(Json.MAX_DEPTH);

        assertThat(Json.parse(json)).isInstanceOf(List.class);
    }

    @Test
    void rejectsNestingDeeperThanTheLimit() {
        String json = "[".repeat(Json.MAX_DEPTH + 1) + "]".repeat(Json.MAX_DEPTH + 1);

        assertThatThrownBy(() -> Json.parse(json))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("nesting depth exceeds " + Json.MAX_DEPTH)
                .satisfies(e -> assertThat(((JsonException) e).getOffset()).isEqualTo(Json.MAX_DEPTH));
    }

    @Test
    void rejectsTrailingGarbageAfterRootValue() {
        assertThatThrownBy(() -> Json.parse("{\"a\":1} x"))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("offset 8")
                .hasMessageContaining("expected end of input")
                .hasMessageContaining("found 'x'")
                .satisfies(e -> assertThat(((JsonException) e).getOffset()).isEqualTo(8));
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> Json.parse(null))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("null");
    }

    @Test
    void reportsUnexpectedEndOfInput() {
        assertThatThrownBy(() -> Json.parse("{\"a\":"))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("offset 5")
                .hasMessageContaining("expected a value")
                .hasMessageContaining("found end of input");
    }

    @Test
    void reportsEmptyInput() {
        assertThatThrownBy(() -> Json.parse("  "))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("expected a value")
                .hasMessageContaining("found end of input");
    }

    @Test
    void reportsMissingSeparatorInObject() {
        assertThatThrownBy(() -> Json.parse("{\"a\":1 \"b\":2}"))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("offset 7")
                .hasMessageContaining("expected ',' or '}'");
    }

    @Test
    void reportsMissingColonInObject() {
        assertThatThrownBy(() -> Json.parse("{\"a\" 1}"))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("offset 5")
                .hasMessageContaining("expected ':'");
    }

    @Test
    void reportsMissingSeparatorInArray() {
        assertThatThrownBy(() -> Json.parse("[1 2]"))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("offset 3")
                .hasMessageContaining("expected ',' or ']'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{a:1}", "{1:1}", "{,}", "{\"a\":1,}"})
    void rejectsInvalidObjectKeys(String json) {
        assertThatThrownBy(() -> Json.parse(json))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("expected a string object key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,]", "[,1]", "[1,,2]"})
    void rejectsMissingArrayElements(String json) {
        assertThatThrownBy(() -> Json.parse(json))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("expected a value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tru", "nul", "fals", "True", "NULL", "undefined", "NaN", "Infinity", "'a'"})
    void rejectsInvalidLiterals(String json) {
        assertThatThrownBy(() -> Json.parse(json))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("offset 0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "-", "+1", "1.", ".5", "1e", "1e+", "-a", "1.e3", "00"})
    void rejectsMalformedNumbers(String json) {
        assertThatThrownBy(() -> Json.parse(json)).isInstanceOf(JsonException.class);
    }

    @Test
    void reportsWhichDigitIsMissingInNumber() {
        assertThatThrownBy(() -> Json.parse("1.x"))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("offset 2")
                .hasMessageContaining("expected a digit");
    }

    @Test
    void rejectsUnterminatedString() {
        assertThatThrownBy(() -> Json.parse("\"abc"))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("offset 4")
                .hasMessageContaining("unterminated string");
    }

    @Test
    void rejectsRawControlCharacterInString() {
        assertThatThrownBy(() -> Json.parse("\"a\nb\""))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("offset 2")
                .hasMessageContaining("control character U+000A")
                .hasMessageContaining("must be escaped");
    }

    @Test
    void rejectsUnknownEscape() {
        assertThatThrownBy(() -> Json.parse("\"\\x\""))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("offset 2")
                .hasMessageContaining("invalid escape");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\\u12\"", "\"\\u12G4\"", "\"\\u", "\"\\u\uff11234\""})
    void rejectsMalformedUnicodeEscape(String json) {
        assertThatThrownBy(() -> Json.parse(json))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("4 hexadecimal digits");
    }

    @Test
    void rejectsEscapeAtEndOfInput() {
        assertThatThrownBy(() -> Json.parse("\"\\"))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("unterminated string");
    }

    @Test
    void showsControlCharactersReadablyInFoundPart() {
        assertThatThrownBy(() -> Json.parse("[1\u0001]"))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("found U+0001");
    }

    @Test
    void parseErrorHasNoCause() {
        assertThatThrownBy(() -> Json.parse("x"))
                .isInstanceOf(JsonException.class)
                .hasNoCause()
                .hasMessageStartingWith("Invalid JSON at offset 0:");
    }
}
