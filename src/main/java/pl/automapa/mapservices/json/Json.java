package pl.automapa.mapservices.json;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON codec (RFC 8259) without external dependencies.
 *
 * <p>Parsing maps JSON values to plain Java objects:
 *
 * <ul>
 *   <li>object &rarr; {@link LinkedHashMap}{@code <String, Object>} (key order kept, the last duplicate key wins),
 *   <li>array &rarr; {@link ArrayList}{@code <Object>},
 *   <li>string &rarr; {@link String},
 *   <li>integer &rarr; {@link Long}, or {@link BigInteger} when it does not fit in a {@code long},
 *   <li>number with a fraction or an exponent &rarr; {@link Double},
 *   <li>{@code true}/{@code false} &rarr; {@link Boolean}, {@code null} &rarr; {@code null}.
 * </ul>
 *
 * <p>Writing produces compact JSON without spaces. Supported values: {@link Map} with {@code String} keys,
 * {@link Collection}, arrays, {@link String}, {@link Integer}, {@link Long}, {@link Short}, {@link Byte},
 * {@link BigInteger}, {@link BigDecimal}, {@link Double}, {@link Float}, {@link Boolean}, {@code null} and
 * {@link JsonValue}. Non-ASCII characters are written as is; only {@code "}, {@code \} and control characters
 * are escaped.
 *
 * <p>The class is stateless and thread safe.
 */
public final class Json {

    /** Maximum nesting depth of arrays and objects accepted by {@link #parse} and written by {@link #stringify}. */
    public static final int MAX_DEPTH = 512;

    private static final String SUPPORTED_TYPES = "Map with String keys, Collection, array, String, Integer, Long,"
            + " Short, Byte, BigInteger, BigDecimal, Double, Float, Boolean, null or JsonValue";

    private Json() {}

    /**
     * Parses JSON text.
     *
     * @param json the JSON text; whitespace around the root value is allowed, anything else after it is not
     * @return the parsed value: {@code Map}, {@code List}, {@code String}, {@code Long}, {@code BigInteger},
     *     {@code Double}, {@code Boolean} or {@code null}
     * @throws JsonException when the text is {@code null}, is not valid JSON or nests deeper than {@link #MAX_DEPTH};
     *     the message names the offset, what was expected and what was found
     */
    public static Object parse(String json) {
        if (json == null) {
            throw new JsonException("Cannot parse JSON: the input text is null; pass the response body as a string");
        }
        return new Parser(json).parseDocument();
    }

    /**
     * Writes a Java value as compact JSON.
     *
     * @param value the value to write (see the class description for supported types)
     * @return the JSON text
     * @throws JsonException when the value contains an unsupported type, a non-string map key, a NaN or infinite
     *     number, or nests deeper than {@link #MAX_DEPTH} (e.g. a collection that contains itself)
     */
    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int depth) {
        if (depth > MAX_DEPTH) {
            throw new JsonException("Cannot write JSON: nesting depth exceeds " + MAX_DEPTH
                    + " levels; the value probably contains a cycle (a collection, map or JsonValue that contains"
                    + " itself)");
        }
        if (value == null) {
            out.append("null");
        } else if (value instanceof JsonValue) {
            write(((JsonValue) value).toJsonValue(), out, depth + 1);
        } else if (value instanceof String) {
            writeString((String) value, out);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Number) {
            writeNumber((Number) value, out);
        } else if (value instanceof Map) {
            writeObject((Map<?, ?>) value, out, depth);
        } else if (value instanceof Collection) {
            writeArray(((Collection<?>) value).toArray(), out, depth);
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object[] items = new Object[length];
            for (int i = 0; i < length; i++) {
                items[i] = Array.get(value, i);
            }
            writeArray(items, out, depth);
        } else {
            throw unsupported(value);
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out, int depth) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String)) {
                throw new JsonException("Cannot write JSON: object keys must be strings, got "
                        + (key == null ? "null" : key.getClass().getName() + " (" + key + ")")
                        + "; convert map keys to String before sending");
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString((String) key, out);
            out.append(':');
            write(entry.getValue(), out, depth + 1);
        }
        out.append('}');
    }

    private static void writeArray(Object[] items, StringBuilder out, int depth) {
        out.append('[');
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            write(items[i], out, depth + 1);
        }
        out.append(']');
    }

    private static void writeNumber(Number number, StringBuilder out) {
        if (number instanceof Integer
                || number instanceof Long
                || number instanceof Short
                || number instanceof Byte
                || number instanceof BigInteger
                || number instanceof BigDecimal) {
            out.append(number);
        } else if (number instanceof Double || number instanceof Float) {
            double asDouble = number.doubleValue();
            if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)) {
                throw new JsonException("Cannot write " + number.getClass().getName() + " value " + number
                        + " as JSON: NaN and Infinity are not valid JSON numbers; check the computation that"
                        + " produced it");
            }
            out.append(number);
        } else {
            throw unsupported(number);
        }
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    private static JsonException unsupported(Object value) {
        return new JsonException(
                "Cannot write value of type " + value.getClass().getName() + " as JSON; supported types: "
                        + SUPPORTED_TYPES + " (enums must implement JsonValue)");
    }

    /** Recursive descent parser over a single JSON text; not thread safe, one instance per call. */
    private static final class Parser {

        private final String in;
        private int pos;
        private int depth;

        Parser(String in) {
            this.in = in;
        }

        Object parseDocument() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (pos < in.length()) {
                throw expected("end of input after the root value", pos);
            }
            return value;
        }

        private Object parseValue() {
            if (pos >= in.length()) {
                throw expected("a value", pos);
            }
            char c = in.charAt(pos);
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    return parseLiteral("true", Boolean.TRUE);
                case 'f':
                    return parseLiteral("false", Boolean.FALSE);
                case 'n':
                    return parseLiteral("null", null);
                default:
                    if (c == '-' || isDigit(c)) {
                        return parseNumber();
                    }
                    throw expected("a value", pos);
            }
        }

        private Map<String, Object> parseObject() {
            enterContainer();
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                depth--;
                return map;
            }
            while (true) {
                if (peek() != '"') {
                    throw expected(map.isEmpty() ? "a string object key or '}'" : "a string object key", pos);
                }
                String key = parseString();
                skipWhitespace();
                if (peek() != ':') {
                    throw expected("':' after object key", pos);
                }
                pos++;
                skipWhitespace();
                map.put(key, parseValue());
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == '}') {
                    depth--;
                    return map;
                }
                if (c != ',') {
                    throw expected("',' or '}' after object member", pos - 1);
                }
                skipWhitespace();
            }
        }

        private List<Object> parseArray() {
            enterContainer();
            pos++;
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                depth--;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(parseValue());
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == ']') {
                    depth--;
                    return list;
                }
                if (c != ',') {
                    throw expected("',' or ']' after array element", pos - 1);
                }
            }
        }

        private void enterContainer() {
            depth++;
            if (depth > MAX_DEPTH) {
                throw new JsonException(
                        "Invalid JSON at offset " + pos + ": nesting depth exceeds " + MAX_DEPTH + " levels", pos);
            }
        }

        private String parseString() {
            int start = pos;
            pos++;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= in.length()) {
                    throw error(pos, "unterminated string starting at offset " + start + ", expected '\"'");
                }
                char c = in.charAt(pos);
                if (c == '"') {
                    pos++;
                    return out.toString();
                }
                if (c < 0x20) {
                    throw error(
                            pos,
                            "control character " + codePoint(c) + " in a string must be escaped (e.g. \\n or \\u"
                                    + String.format("%04X", (int) c) + ")");
                }
                if (c == '\\') {
                    pos++;
                    parseEscape(out, start);
                } else {
                    out.append(c);
                    pos++;
                }
            }
        }

        private void parseEscape(StringBuilder out, int stringStart) {
            if (pos >= in.length()) {
                throw error(pos, "unterminated string starting at offset " + stringStart + ", expected an escape");
            }
            char c = in.charAt(pos);
            pos++;
            switch (c) {
                case '"':
                    out.append('"');
                    break;
                case '\\':
                    out.append('\\');
                    break;
                case '/':
                    out.append('/');
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    out.append(parseHex4());
                    break;
                default:
                    throw error(
                            pos - 1,
                            "invalid escape " + describe(pos - 1) + ", expected one of \\\" \\\\ \\/ \\b \\f \\n"
                                    + " \\r \\t \\uXXXX");
            }
        }

        private char parseHex4() {
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = pos + i < in.length() ? hexDigit(in.charAt(pos + i)) : -1;
                if (digit < 0) {
                    throw error(pos, "expected 4 hexadecimal digits after '\\u', found " + describe(pos + i));
                }
                value = value * 16 + digit;
            }
            pos += 4;
            return (char) value;
        }

        private static int hexDigit(char c) {
            if (c >= '0' && c <= '9') {
                return c - '0';
            }
            if (c >= 'a' && c <= 'f') {
                return c - 'a' + 10;
            }
            if (c >= 'A' && c <= 'F') {
                return c - 'A' + 10;
            }
            return -1;
        }

        private Object parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            if (peek() == '0') {
                pos++;
            } else {
                requireDigits();
            }
            boolean integer = true;
            if (peek() == '.') {
                integer = false;
                pos++;
                requireDigits();
            }
            if (peek() == 'e' || peek() == 'E') {
                integer = false;
                pos++;
                if (peek() == '+' || peek() == '-') {
                    pos++;
                }
                requireDigits();
            }
            String text = in.substring(start, pos);
            if (!integer) {
                return Double.parseDouble(text);
            }
            BigInteger big = new BigInteger(text);
            if (big.bitLength() < Long.SIZE) {
                return big.longValue();
            }
            return big;
        }

        private void requireDigits() {
            if (!isDigit(peek())) {
                throw expected("a digit", pos);
            }
            while (isDigit(peek())) {
                pos++;
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!in.startsWith(literal, pos)) {
                String found = in.substring(pos, Math.min(in.length(), pos + literal.length()));
                throw error(pos, "expected '" + literal + "', found '" + found + "'");
            }
            pos += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (pos < in.length()) {
                char c = in.charAt(pos);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    return;
                }
                pos++;
            }
        }

        /** Returns the current character, or {@code 0} at the end of input (never valid at a JSON token). */
        private char peek() {
            return pos < in.length() ? in.charAt(pos) : 0;
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private JsonException expected(String what, int offset) {
            return error(offset, "expected " + what + ", found " + describe(offset));
        }

        private String describe(int offset) {
            if (offset >= in.length()) {
                return "end of input";
            }
            char c = in.charAt(offset);
            return c < 0x20 || c == 0x7f ? codePoint(c) : "'" + c + "'";
        }

        private static String codePoint(char c) {
            return String.format("U+%04X", (int) c);
        }

        private static JsonException error(int offset, String detail) {
            return new JsonException("Invalid JSON at offset " + offset + ": " + detail, offset);
        }
    }
}
