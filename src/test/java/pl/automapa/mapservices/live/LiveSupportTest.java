package pl.automapa.mapservices.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.automapa.mapservices.exception.AuthenticationException;
import pl.automapa.mapservices.exception.RateLimitException;
import pl.automapa.mapservices.exception.ServerException;
import pl.automapa.mapservices.exception.SessionException;
import pl.automapa.mapservices.http.HttpResponse;

/** Unit tests of the pure helpers behind {@link LiveApiTestBase}; they never touch the network. */
class LiveSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void parseDotEnvReadsKeyValueLinesAndSkipsCommentsAndBlankLines() {
        Map<String, String> parsed = LiveSupport.parseDotEnv(List.of(
                "# comment",
                "",
                "   ",
                "AUTOMAPA_API_KEY = abc ",
                "export AUTOMAPA_API_PASSWORD=p=a=ss",
                "QUOTED=\"with spaces\"",
                "SINGLE='single'",
                "NO_EQUALS_SIGN",
                "  # indented comment=value",
                "EMPTY="));

        assertThat(parsed)
                .containsExactly(
                        Map.entry("AUTOMAPA_API_KEY", "abc"),
                        Map.entry("AUTOMAPA_API_PASSWORD", "p=a=ss"),
                        Map.entry("QUOTED", "with spaces"),
                        Map.entry("SINGLE", "single"),
                        Map.entry("EMPTY", ""));
    }

    @Test
    void resolveEnvironmentPrefersProcessVariablesAndFillsMissingOnesFromDotEnv() throws IOException {
        Path dotEnv = tempDir.resolve(".env");
        Files.write(dotEnv, List.of("A=from-file", "B=from-file", "C=from-file"), StandardCharsets.UTF_8);
        Map<String, String> process = new HashMap<>();
        process.put("A", "from-process");
        process.put("B", "");

        Map<String, String> resolved = LiveSupport.resolveEnvironment(process, dotEnv);

        assertThat(resolved)
                .containsEntry("A", "from-process")
                .containsEntry("B", "from-file")
                .containsEntry("C", "from-file");
    }

    @Test
    void resolveEnvironmentWithoutDotEnvReturnsProcessVariables() {
        Map<String, String> resolved = LiveSupport.resolveEnvironment(Map.of("A", "1"), tempDir.resolve("missing"));

        assertThat(resolved).containsExactly(Map.entry("A", "1"));
    }

    @Test
    void requiredAccessSplitsTrimsAndDropsEmptyEntries() {
        assertThat(LiveSupport.requiredAccess(" geocoder , pingpong,,road "))
                .containsExactly("geocoder", "pingpong", "road");
        assertThat(LiveSupport.requiredAccess("")).isEmpty();
        assertThat(LiveSupport.requiredAccess(null)).isEmpty();
    }

    @Test
    void suiteIsRequiredOnlyWhenNamedInTheList() {
        List<String> required = List.of("geocoder", "pingpong");

        assertThat(LiveSupport.isSuiteRequired("pingpong", required)).isTrue();
        assertThat(LiveSupport.isSuiteRequired("routes", required)).isFalse();
        assertThat(LiveSupport.isSuiteRequired(null, required)).isFalse();
    }

    @Test
    void permissionDeniedMeansHttp403WithNoPermissionMessage() {
        assertThat(LiveSupport.isPermissionDenied(
                        new SessionException("Forbidden - no permission (HTTP 403)", 403, "no permission", null)))
                .isTrue();
        assertThat(LiveSupport.isPermissionDenied(new SessionException("Session expired", 403, "expired", null)))
                .isFalse();
        assertThat(LiveSupport.isPermissionDenied(
                        new AuthenticationException("no permission", 401, "no permission", null)))
                .isFalse();
        assertThat(LiveSupport.isPermissionDenied(new IllegalStateException("no permission")))
                .isFalse();
    }

    @Test
    void permissionDeniedResponseNeedsStatus403AndNoPermissionMessage() {
        assertThat(LiveSupport.isPermissionDenied(
                        new HttpResponse(403, "{\"message\":\"Forbidden - no permission\"}", Map.of())))
                .isTrue();
        assertThat(LiveSupport.isPermissionDenied(new HttpResponse(403, "{\"message\":\"expired\"}", Map.of())))
                .isFalse();
        assertThat(LiveSupport.isPermissionDenied(new HttpResponse(403, "not json", Map.of())))
                .isFalse();
        assertThat(LiveSupport.isPermissionDenied(new HttpResponse(200, "{\"message\":\"no permission\"}", Map.of())))
                .isFalse();
    }

    @Test
    void serverErrorIsAServerExceptionOrStatus5xx() {
        assertThat(LiveSupport.isServerError(new ServerException("boom", 503, "down", null)))
                .isTrue();
        assertThat(LiveSupport.isServerError(new SessionException("no", 403, "no", null)))
                .isFalse();
    }

    @Test
    void retryOnceOnRateLimitExceptionAfterTheConfiguredPause() {
        List<Duration> pauses = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        String result = LiveSupport.retryOnceOnRateLimit(
                () -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new RateLimitException("Too many requests", 429, "slow down", null);
                    }
                    return "ok";
                },
                pauses::add);

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
        assertThat(pauses).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void retryOnceOnRateLimitedHttpResponse() {
        List<Duration> pauses = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        HttpResponse result = LiveSupport.retryOnceOnRateLimit(
                () -> calls.incrementAndGet() == 1
                        ? new HttpResponse(429, "{}", Map.of())
                        : new HttpResponse(200, "{\"result\":{}}", Map.of()),
                pauses::add);

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(calls).hasValue(2);
        assertThat(pauses).hasSize(1);
    }

    @Test
    void retryOnceOnRateLimitGivesUpAfterTheSecondRateLimit() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> LiveSupport.retryOnceOnRateLimit(
                        () -> {
                            calls.incrementAndGet();
                            throw new RateLimitException("Too many requests", 429, "slow down", null);
                        },
                        pause -> {}))
                .isInstanceOf(RateLimitException.class);
        assertThat(calls).hasValue(2);
    }

    @Test
    void retryOnceOnRateLimitDoesNotRetryOtherResults() {
        List<Duration> pauses = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> LiveSupport.retryOnceOnRateLimit(
                        () -> {
                            calls.incrementAndGet();
                            throw new ServerException("boom", 500, "boom", null);
                        },
                        pauses::add))
                .isInstanceOf(ServerException.class);
        HttpResponse ok = LiveSupport.retryOnceOnRateLimit(() -> new HttpResponse(403, "{}", Map.of()), pauses::add);

        assertThat(ok.statusCode()).isEqualTo(403);
        assertThat(calls).hasValue(1);
        assertThat(pauses).isEmpty();
    }
}
