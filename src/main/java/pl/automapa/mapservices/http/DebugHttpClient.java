package pl.automapa.mapservices.http;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decorator that prints every request and response passing through another {@link HttpClient}, for diagnosing
 * calls to the API.
 *
 * <p>Each request is printed as a {@code === REQUEST ===} block (method and URL, headers, body) before it is sent, and
 * each response as a {@code === RESPONSE (<status>) ===} block (headers, body) after it is received. Secrets are
 * masked in the printed text only: the value of the {@code Session-Id} header and of every JSON {@code pass} field is
 * replaced with {@code ***}. The request handed to the wrapped client and the returned response are not changed.
 * When the wrapped client throws, the exception is propagated and no response block is printed.
 *
 * <p>The decorator is thread safe when the wrapped client is; blocks of concurrent calls may interleave.
 */
public final class DebugHttpClient implements HttpClient {

    private static final String MASK = "***";
    private static final String SESSION_HEADER = "Session-Id";
    private static final Pattern PASS_FIELD =
            Pattern.compile("(\"pass\"\\s*:\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"|[^\\s,}\\]]+)");

    private final HttpClient inner;
    private final PrintStream out;

    /**
     * Creates the decorator printing to {@link System#err}.
     *
     * @param inner the client that sends the requests
     * @throws IllegalArgumentException when {@code inner} is {@code null}
     */
    public DebugHttpClient(HttpClient inner) {
        this(inner, System.err);
    }

    /**
     * Creates the decorator printing to the given stream.
     *
     * @param inner the client that sends the requests
     * @param out the stream receiving the debug output
     * @throws IllegalArgumentException when {@code inner} or {@code out} is {@code null}
     */
    public DebugHttpClient(HttpClient inner, PrintStream out) {
        if (inner == null) {
            throw new IllegalArgumentException("DebugHttpClient inner client is required; wrap a transport, e.g."
                    + " new DebugHttpClient(new JdkHttpClient(Duration.ofSeconds(30)))");
        }
        if (out == null) {
            throw new IllegalArgumentException(
                    "DebugHttpClient out stream is required; pass e.g. System.err or use new DebugHttpClient(inner)");
        }
        this.inner = inner;
        this.out = out;
    }

    /**
     * Prints the request, sends it through the wrapped client, prints the response and returns it unchanged.
     *
     * @param request the request to send
     * @return the response of the wrapped client
     */
    @Override
    public HttpResponse send(HttpRequest request) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("=== REQUEST ===");
        lines.add(request.method() + " " + request.url());
        appendHeadersAndBody(lines, request.headers(), request.body());
        lines.add("===============");
        print(lines);

        HttpResponse response = inner.send(request);

        lines.clear();
        lines.add("");
        lines.add("=== RESPONSE (" + response.statusCode() + ") ===");
        appendHeadersAndBody(lines, response.headers(), response.body());
        lines.add("=====================");
        print(lines);
        return response;
    }

    private static void appendHeadersAndBody(List<String> lines, Map<String, String> headers, String body) {
        headers.forEach(
                (name, value) -> lines.add(name + ": " + (SESSION_HEADER.equalsIgnoreCase(name) ? MASK : value)));
        if (!body.isEmpty()) {
            lines.add("");
            lines.add(maskPass(body));
        }
    }

    private static String maskPass(String body) {
        return PASS_FIELD.matcher(body).replaceAll("$1\"" + Matcher.quoteReplacement(MASK) + "\"");
    }

    private void print(List<String> lines) {
        lines.add("");
        out.print(String.join("\n", lines));
        out.flush();
    }
}
