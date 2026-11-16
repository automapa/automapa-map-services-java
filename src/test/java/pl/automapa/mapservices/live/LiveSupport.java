package pl.automapa.mapservices.live;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.exception.RateLimitException;
import pl.automapa.mapservices.exception.ServerException;
import pl.automapa.mapservices.http.HttpResponse;
import pl.automapa.mapservices.json.Json;
import pl.automapa.mapservices.json.JsonException;

/** Pure helpers of {@link LiveApiTestBase}: environment loading, suite gating, error classification and retry. */
final class LiveSupport {

    /** Pause before the single retry after HTTP 429. */
    static final Duration RATE_LIMIT_PAUSE = Duration.ofSeconds(30);

    private static final int FORBIDDEN = 403;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int FIRST_SERVER_ERROR = 500;
    private static final String NO_PERMISSION = "no permission";
    private static final String EXPORT_PREFIX = "export ";

    /** Waits for a given time; replaced in tests to avoid real sleeping. */
    @FunctionalInterface
    interface Pause {
        /**
         * Waits.
         *
         * @param duration how long
         */
        void sleep(Duration duration);
    }

    private LiveSupport() {}

    /**
     * Parses the lines of a {@code .env} file: {@code KEY=value}, optional {@code export } prefix, comments
     * ({@code #}) and blank lines skipped, lines without {@code =} ignored, matching surrounding quotes removed.
     *
     * @param lines file lines
     * @return variables in file order
     */
    static Map<String, String> parseDotEnv(List<String> lines) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            if (trimmed.startsWith(EXPORT_PREFIX)) {
                trimmed = trimmed.substring(EXPORT_PREFIX.length());
            }
            int separator = trimmed.indexOf('=');
            String key = trimmed.substring(0, separator).trim();
            if (!key.isEmpty()) {
                variables.put(key, unquote(trimmed.substring(separator + 1).trim()));
            }
        }
        return variables;
    }

    /**
     * Merges the process environment with a {@code .env} file: a process variable wins unless it is empty.
     *
     * @param processEnv process environment, usually {@link System#getenv()}
     * @param dotEnv path of the {@code .env} file; a missing file is ignored
     * @return the merged variables
     * @throws UncheckedIOException when the file exists but cannot be read
     */
    static Map<String, String> resolveEnvironment(Map<String, String> processEnv, Path dotEnv) {
        Map<String, String> resolved = new LinkedHashMap<>(processEnv);
        if (!Files.isRegularFile(dotEnv)) {
            return resolved;
        }
        try {
            parseDotEnv(Files.readAllLines(dotEnv, StandardCharsets.UTF_8)).forEach((key, value) -> {
                String current = resolved.get(key);
                if (current == null || current.isEmpty()) {
                    resolved.put(key, value);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + dotEnv + ": " + e.getMessage(), e);
        }
        return resolved;
    }

    /**
     * Splits {@code AUTOMAPA_API_REQUIRED_ACCESS}, e.g. {@code geocoder,pingpong}.
     *
     * @param raw variable value, may be {@code null}
     * @return trimmed, non-empty suite names
     */
    static List<String> requiredAccess(String raw) {
        List<String> suites = new ArrayList<>();
        if (raw == null) {
            return suites;
        }
        for (String part : raw.split(",", -1)) {
            String suite = part.trim();
            if (!suite.isEmpty()) {
                suites.add(suite);
            }
        }
        return suites;
    }

    /**
     * Tells whether a suite must pass: it is named and listed in {@code AUTOMAPA_API_REQUIRED_ACCESS}.
     *
     * @param suite suite name, {@code null} for an ungated suite
     * @param required required suites
     * @return whether the suite is required
     */
    static boolean isSuiteRequired(String suite, List<String> required) {
        return suite != null && required.contains(suite);
    }

    /**
     * Tells whether an exception means that the API key has no permission for the called service.
     *
     * @param error the error
     * @return {@code true} for an HTTP 403 {@link AutomapaException} mentioning {@code no permission}
     */
    static boolean isPermissionDenied(Throwable error) {
        if (!(error instanceof AutomapaException)) {
            return false;
        }
        AutomapaException apiError = (AutomapaException) error;
        return apiError.getStatusCode() == FORBIDDEN
                && (mentionsNoPermission(apiError.getApiMessage()) || mentionsNoPermission(apiError.getMessage()));
    }

    /**
     * Tells whether a raw response means that the API key has no permission for the called service.
     *
     * @param response the response
     * @return {@code true} for HTTP 403 with a JSON {@code message} mentioning {@code no permission}
     */
    static boolean isPermissionDenied(HttpResponse response) {
        if (response.statusCode() != FORBIDDEN) {
            return false;
        }
        try {
            Object body = Json.parse(response.body());
            return body instanceof Map && mentionsNoPermission(String.valueOf(((Map<?, ?>) body).get("message")));
        } catch (JsonException e) {
            return false;
        }
    }

    /**
     * Tells whether an exception is an HTTP 5xx error of the API.
     *
     * @param error the error
     * @return {@code true} for a {@link ServerException} or another {@link AutomapaException} with status 5xx
     */
    static boolean isServerError(Throwable error) {
        return error instanceof ServerException
                || error instanceof AutomapaException
                        && ((AutomapaException) error).getStatusCode() >= FIRST_SERVER_ERROR;
    }

    /**
     * Runs a call and, when it is rate limited (a {@link RateLimitException} or an {@link HttpResponse} with status
     * 429), waits {@link #RATE_LIMIT_PAUSE} and runs it exactly once more.
     *
     * @param call the call
     * @param pause how to wait
     * @param <T> result type
     * @return the result of the first call that was not rate limited, or of the second call
     */
    static <T> T retryOnceOnRateLimit(Supplier<T> call, Pause pause) {
        boolean rateLimited;
        T first = null;
        try {
            first = call.get();
            rateLimited = isRateLimited(first);
        } catch (RateLimitException e) {
            rateLimited = true;
        }
        if (!rateLimited) {
            return first;
        }
        pause.sleep(RATE_LIMIT_PAUSE);
        return call.get();
    }

    /** Pauses the current thread; an interrupt is restored and reported as an {@link IllegalStateException}. */
    static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting before retrying after HTTP 429", e);
        }
    }

    private static boolean mentionsNoPermission(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(NO_PERMISSION);
    }

    private static boolean isRateLimited(Object result) {
        return result instanceof HttpResponse && ((HttpResponse) result).statusCode() == TOO_MANY_REQUESTS;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            if ((first == '"' || first == '\'') && value.charAt(value.length() - 1) == first) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
