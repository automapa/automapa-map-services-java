package pl.automapa.mapservices.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.automapa.mapservices.json.Json;

class ExceptionMapperTest {

    private static final Path ERROR_FIXTURES = Path.of("src", "test", "resources", "fixtures", "errors");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fixture(String name) throws IOException {
        String text = Files.readString(ERROR_FIXTURES.resolve(name), StandardCharsets.UTF_8);
        return (Map<String, Object>) Json.parse(text);
    }

    static Stream<Arguments> statusCodes() {
        return Stream.of(
                Arguments.of(400, ValidationException.class),
                Arguments.of(401, AuthenticationException.class),
                Arguments.of(403, SessionException.class),
                Arguments.of(404, NotFoundException.class),
                Arguments.of(429, RateLimitException.class),
                Arguments.of(500, ServerException.class),
                Arguments.of(503, ServerException.class),
                Arguments.of(599, ServerException.class),
                Arguments.of(418, UnknownResponseException.class),
                Arguments.of(301, UnknownResponseException.class),
                Arguments.of(200, UnknownResponseException.class));
    }

    @ParameterizedTest(name = "HTTP {0} -> {1}")
    @MethodSource("statusCodes")
    void mapsStatusCodeToExceptionType(int status, Class<? extends AutomapaException> expected) {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(status, Map.of());

        assertThat(exception).isExactlyInstanceOf(expected).isInstanceOf(RuntimeException.class);
        assertThat(exception.getStatusCode()).isEqualTo(status);
    }

    @Test
    void authErrorFixtureGivesAuthenticationExceptionWithCredentialsHint() throws IOException {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(401, fixture("auth_error.json"));

        assertThat(exception).isExactlyInstanceOf(AuthenticationException.class);
        assertThat(exception.getMessage())
                .isEqualTo("Invalid API key or credentials [code: AUTH_FAILED] (HTTP 401). "
                        + "Check AUTOMAPA_API_KEY and AUTOMAPA_API_PASSWORD.");
        assertThat(exception.getApiMessage()).isEqualTo("Invalid API key or credentials");
        assertThat(exception.getApiCode()).isEqualTo("AUTH_FAILED");
        assertThat(exception.getStatusCode()).isEqualTo(401);
    }

    @Test
    void sessionErrorFixtureGivesSessionExceptionWithPermissionHint() throws IOException {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(403, fixture("session_error.json"));

        assertThat(exception).isExactlyInstanceOf(SessionException.class);
        assertThat(exception.getMessage())
                .isEqualTo("Session expired or invalid [code: SESSION_INVALID] (HTTP 403). "
                        + "Session expired or the API key has no permission for this service.");
        assertThat(exception.getApiCode()).isEqualTo("SESSION_INVALID");
    }

    @Test
    void rateLimitFixtureGivesRateLimitExceptionWithRetryHint() throws IOException {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(429, fixture("rate_limit.json"));

        assertThat(exception).isExactlyInstanceOf(RateLimitException.class);
        assertThat(exception.getMessage())
                .isEqualTo("Rate limit exceeded [code: RATE_LIMIT] (HTTP 429). Rate limit exceeded; retry later.");
        assertThat(exception.getApiMessage()).isEqualTo("Rate limit exceeded");
    }

    @Test
    void serverErrorFixtureGivesServerExceptionWithRetryHint() throws IOException {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(500, fixture("server_error.json"));

        assertThat(exception).isExactlyInstanceOf(ServerException.class);
        assertThat(exception.getMessage())
                .isEqualTo("Internal server error [code: SERVER_ERROR] (HTTP 500). "
                        + "Automapa API server error; retry later.");
    }

    @ParameterizedTest
    @ValueSource(ints = {502, 503, 504})
    void everyServerErrorStatusGetsServerHint(int status) {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(status, Map.of("message", "Bad gateway"));

        assertThat(exception.getMessage())
                .isEqualTo("Bad gateway (HTTP " + status + "). Automapa API server error; retry later.");
    }

    @Test
    void messageWithoutCodeHasNoCodeSuffix() {
        AutomapaException exception =
                ExceptionMapper.fromHttpResponse(400, Map.of("message", "Missing parameter: city"));

        assertThat(exception).isExactlyInstanceOf(ValidationException.class);
        assertThat(exception.getMessage()).isEqualTo("Missing parameter: city (HTTP 400)");
        assertThat(exception.getApiMessage()).isEqualTo("Missing parameter: city");
        assertThat(exception.getApiCode()).isNull();
    }

    @Test
    void fallsBackToErrorFieldWhenMessageIsMissing() {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(404, Map.of("error", "Unknown method"));

        assertThat(exception).isExactlyInstanceOf(NotFoundException.class);
        assertThat(exception.getMessage()).isEqualTo("Unknown method (HTTP 404)");
        assertThat(exception.getApiMessage()).isEqualTo("Unknown method");
    }

    @Test
    void fallsBackToErrorFieldWhenMessageIsEmpty() {
        AutomapaException exception =
                ExceptionMapper.fromHttpResponse(400, Map.of("message", "", "error", "Invalid point"));

        assertThat(exception.getMessage()).isEqualTo("Invalid point (HTTP 400)");
        assertThat(exception.getApiMessage()).isEqualTo("Invalid point");
    }

    @Test
    void messageFieldWinsOverErrorField() {
        AutomapaException exception =
                ExceptionMapper.fromHttpResponse(400, Map.of("message", "From message", "error", "From error"));

        assertThat(exception.getApiMessage()).isEqualTo("From message");
    }

    @Test
    void emptyBodyGivesBareHttpStatusPlusHint() {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(429, Map.of());

        assertThat(exception.getMessage()).isEqualTo("HTTP 429. Rate limit exceeded; retry later.");
        assertThat(exception.getApiMessage()).isNull();
        assertThat(exception.getApiCode()).isNull();
    }

    @Test
    void emptyBodyWithoutHintGivesBareHttpStatus() {
        assertThat(ExceptionMapper.fromHttpResponse(418, Map.of()).getMessage()).isEqualTo("HTTP 418");
    }

    @Test
    void nullBodyIsTreatedAsEmpty() {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(401, null);

        assertThat(exception).isExactlyInstanceOf(AuthenticationException.class);
        assertThat(exception.getMessage()).isEqualTo("HTTP 401. Check AUTOMAPA_API_KEY and AUTOMAPA_API_PASSWORD.");
    }

    @Test
    void codeWithoutMessageIsStillReported() {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(400, Map.of("code", "E42"));

        assertThat(exception.getMessage()).isEqualTo("HTTP 400 [code: E42]");
        assertThat(exception.getApiCode()).isEqualTo("E42");
    }

    @Test
    void emptyCodeIsIgnored() {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(400, Map.of("message", "Bad", "code", ""));

        assertThat(exception.getMessage()).isEqualTo("Bad (HTTP 400)");
        assertThat(exception.getApiCode()).isNull();
    }

    @Test
    void blankMessageAndErrorAreTreatedAsMissing() {
        AutomapaException exception = ExceptionMapper.fromHttpResponse(400, Map.of("message", "  ", "error", ""));

        assertThat(exception.getMessage()).isEqualTo("HTTP 400");
        assertThat(exception.getApiMessage()).isNull();
    }

    @Test
    void nonStringFieldsAreConvertedToText() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", null);
        body.put("error", 7L);
        body.put("code", 1001L);

        AutomapaException exception = ExceptionMapper.fromHttpResponse(400, body);

        assertThat(exception.getMessage()).isEqualTo("7 [code: 1001] (HTTP 400)");
        assertThat(exception.getApiMessage()).isEqualTo("7");
        assertThat(exception.getApiCode()).isEqualTo("1001");
    }
}
