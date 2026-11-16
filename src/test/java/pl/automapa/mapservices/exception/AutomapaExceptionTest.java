package pl.automapa.mapservices.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AutomapaExceptionTest {

    /** Factory of a subclass using the full constructor (message, status, API message, API code). */
    @FunctionalInterface
    interface FullFactory {
        AutomapaException create(String message, int statusCode, String apiMessage, String apiCode);
    }

    static Stream<Arguments> subclasses() {
        return Stream.of(
                Arguments.of(ValidationException.class, (FullFactory) ValidationException::new, (BiFunction<
                                String, Throwable, AutomapaException>)
                        ValidationException::new),
                Arguments.of(AuthenticationException.class, (FullFactory) AuthenticationException::new, (BiFunction<
                                String, Throwable, AutomapaException>)
                        AuthenticationException::new),
                Arguments.of(SessionException.class, (FullFactory) SessionException::new, (BiFunction<
                                String, Throwable, AutomapaException>)
                        SessionException::new),
                Arguments.of(NotFoundException.class, (FullFactory) NotFoundException::new, (BiFunction<
                                String, Throwable, AutomapaException>)
                        NotFoundException::new),
                Arguments.of(RateLimitException.class, (FullFactory) RateLimitException::new, (BiFunction<
                                String, Throwable, AutomapaException>)
                        RateLimitException::new),
                Arguments.of(ServerException.class, (FullFactory) ServerException::new, (BiFunction<
                                String, Throwable, AutomapaException>)
                        ServerException::new),
                Arguments.of(UnknownResponseException.class, (FullFactory) UnknownResponseException::new, (BiFunction<
                                String, Throwable, AutomapaException>)
                        UnknownResponseException::new),
                Arguments.of(NetworkException.class, (FullFactory) NetworkException::new, (BiFunction<
                                String, Throwable, AutomapaException>)
                        NetworkException::new));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subclasses")
    void subclassKeepsStatusAndApiDetails(
            Class<? extends AutomapaException> type,
            FullFactory full,
            BiFunction<String, Throwable, AutomapaException> withCause) {
        AutomapaException exception = full.create("Bad request (HTTP 400)", 400, "Bad request", "E1");

        assertThat(exception).isExactlyInstanceOf(type).isInstanceOf(AutomapaException.class);
        assertThat(Modifier.isFinal(type.getModifiers())).isTrue();
        assertThat(exception.getMessage()).isEqualTo("Bad request (HTTP 400)");
        assertThat(exception.getStatusCode()).isEqualTo(400);
        assertThat(exception.getApiMessage()).isEqualTo("Bad request");
        assertThat(exception.getApiCode()).isEqualTo("E1");
        assertThat(exception.getCause()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subclasses")
    void subclassWithCauseHasNoHttpStatus(
            Class<? extends AutomapaException> type,
            FullFactory full,
            BiFunction<String, Throwable, AutomapaException> withCause) {
        IOException cause = new IOException("Connection refused");

        AutomapaException exception = withCause.apply("Could not reach the API", cause);

        assertThat(exception).isExactlyInstanceOf(type);
        assertThat(exception.getMessage()).isEqualTo("Could not reach the API");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getStatusCode()).isZero();
        assertThat(exception.getApiMessage()).isNull();
        assertThat(exception.getApiCode()).isNull();
    }

    @Test
    void baseExceptionCanBeCreatedWithMessageOnly() {
        AutomapaException exception = new AutomapaException("Something failed");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("Something failed");
        assertThat(exception.getStatusCode()).isZero();
        assertThat(exception.getApiMessage()).isNull();
        assertThat(exception.getApiCode()).isNull();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void baseExceptionSupportsAllConstructors() {
        Throwable cause = new IllegalStateException("inner");

        assertThat(new AutomapaException("m", cause).getCause()).isSameAs(cause);
        AutomapaException full = new AutomapaException("m", 503, "api", "C");
        assertThat(List.of(full.getStatusCode(), full.getApiMessage(), full.getApiCode()))
                .containsExactly(503, "api", "C");
    }

    @Test
    void statusWithCauseConstructorsKeepBoth() {
        Throwable cause = new IllegalStateException("inner");

        AutomapaException base = new AutomapaException("m", 502, cause);
        UnknownResponseException unknown = new UnknownResponseException("u", 500, cause);

        assertThat(List.of(base.getStatusCode(), base.getCause(), unknown.getStatusCode(), unknown.getCause()))
                .containsExactly(502, cause, 500, cause);
        assertThat(unknown.getApiMessage()).isNull();
        assertThat(unknown.getApiCode()).isNull();
    }
}
