package pl.automapa.mapservices.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.automapa.mapservices.exception.AuthenticationException;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.exception.ServerException;
import pl.automapa.mapservices.exception.SessionException;
import pl.automapa.mapservices.exception.UnknownResponseException;
import pl.automapa.mapservices.exception.ValidationException;
import pl.automapa.mapservices.http.HttpResponse;
import pl.automapa.mapservices.json.JsonException;
import pl.automapa.mapservices.support.Fixtures;

class ResponseParserTest {

    @Test
    void parsesSuccessfulResponse() {
        HttpResponse http = new HttpResponse(200, "{\"result\":{\"city\":\"Warszawa\",\"x\":21.01}}");

        ApiResponse response = ResponseParser.parse(http, Map.of("service", "Geocoder", "method", "geocode"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data()).isEqualTo(Map.of("city", "Warszawa", "x", 21.01));
        assertThat(response.meta()).containsEntry("service", "Geocoder");
    }

    @Test
    void parsesFixtureKeepingFullRawBody() {
        String body = Fixtures.text("geocode_success.json");

        ApiResponse response = ResponseParser.parse(new HttpResponse(200, body));

        assertThat(response.raw()).isEqualTo(Fixtures.load("geocode_success.json"));
        assertThat(response.meta()).isEmpty();
    }

    @Test
    void rawAlwaysContainsUnknownTopLevelFields() {
        HttpResponse http =
                new HttpResponse(200, "{\"result\":{\"city\":\"Gdansk\"},\"unknownTopLevelField\":\"kept\"}");

        ApiResponse response = ResponseParser.parse(http, Map.of());

        assertThat(response.raw()).containsEntry("unknownTopLevelField", "kept").containsKey("result");
    }

    @Test
    void acceptsAnySuccessStatus() {
        assertThat(ResponseParser.parse(new HttpResponse(204, "{}")).status()).isEqualTo(204);
        assertThat(ResponseParser.parse(new HttpResponse(299, "{\"a\":1}")).data())
                .isEqualTo(Map.of("a", 1L));
    }

    @Test
    void nullMetaBecomesEmpty() {
        assertThat(ResponseParser.parse(new HttpResponse(200, "{}"), null).meta())
                .isEmpty();
    }

    @Test
    void throwsAuthenticationExceptionOn401() {
        HttpResponse http = new HttpResponse(401, "{\"message\":\"Invalid API key\"}");

        assertThatThrownBy(() -> ResponseParser.parse(http))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Invalid API key")
                .hasMessageContaining("HTTP 401");
    }

    @Test
    void throwsServerExceptionOn500() {
        HttpResponse http = new HttpResponse(500, "{\"message\":\"Internal server error\"}");

        assertThatThrownBy(() -> ResponseParser.parse(http)).isInstanceOf(ServerException.class);
    }

    @Test
    void mapsErrorFixturesThroughExceptionMapper() {
        assertThatThrownBy(() -> ResponseParser.parse(new HttpResponse(401, Fixtures.text("errors/auth_error"))))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("[code: AUTH_FAILED]");
        assertThatThrownBy(() -> ResponseParser.parse(new HttpResponse(400, "{\"message\":\"Missing city\"}")))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ResponseParser.parse(new HttpResponse(403, Fixtures.text("errors/session_error"))))
                .isInstanceOf(SessionException.class);
    }

    @Test
    void errorStatusWithEmptyJsonObjectStillMapsByStatus() {
        AutomapaException error =
                catchThrowableOfType(AutomapaException.class, () -> ResponseParser.parse(new HttpResponse(503, "{}")));

        assertThat(error).isInstanceOf(ServerException.class);
        assertThat(error.getStatusCode()).isEqualTo(503);
    }

    @Test
    void throwsUnknownResponseExceptionOnMalformedBody() {
        HttpResponse http = new HttpResponse(200, "not-valid-json{{{");

        UnknownResponseException error =
                catchThrowableOfType(UnknownResponseException.class, () -> ResponseParser.parse(http));

        assertThat(error.getMessage()).startsWith("Non-JSON response (HTTP 200): not-valid-json{{{");
        assertThat(error.getStatusCode()).isEqualTo(200);
        assertThat(error.getCause()).isInstanceOf(JsonException.class);
    }

    @Test
    void nonJsonErrorKeepsStatusAndOriginalCause() {
        HttpResponse http = new HttpResponse(502, "<html>Bad Gateway</html>");

        UnknownResponseException error =
                catchThrowableOfType(UnknownResponseException.class, () -> ResponseParser.parse(http));

        assertThat(error.getStatusCode()).isEqualTo(502);
        assertThat(error.getCause()).isInstanceOf(JsonException.class);
        assertThat(error.getMessage())
                .startsWith("Non-JSON response (HTTP 502): <html>Bad Gateway</html>")
                .contains("baseUrl");
    }

    @Test
    void emptyBodyIsReportedAsNonJson() {
        UnknownResponseException error = catchThrowableOfType(
                UnknownResponseException.class, () -> ResponseParser.parse(new HttpResponse(503, "")));

        assertThat(error.getMessage()).startsWith("Non-JSON response (HTTP 503): <empty body>");
        assertThat(error.getStatusCode()).isEqualTo(503);
    }

    @Test
    void nonJsonBodySnippetIsLimitedTo200Characters() {
        String body = "<" + "a".repeat(300);

        UnknownResponseException error = catchThrowableOfType(
                UnknownResponseException.class, () -> ResponseParser.parse(new HttpResponse(500, body)));

        assertThat(error.getMessage()).startsWith("Non-JSON response (HTTP 500): " + body.substring(0, 200) + "...");
        assertThat(error.getMessage()).doesNotContain(body.substring(0, 201));
    }

    @Test
    void nonJsonBodyOfExactly200CharactersIsNotTruncated() {
        String body = "<" + "b".repeat(199);

        UnknownResponseException error = catchThrowableOfType(
                UnknownResponseException.class, () -> ResponseParser.parse(new HttpResponse(500, body)));

        assertThat(error.getMessage()).startsWith("Non-JSON response (HTTP 500): " + body + " ");
        assertThat(error.getMessage()).doesNotContain(body + "...");
    }

    @Test
    void snippetDoesNotSplitSurrogatePair() {
        String emoji = Character.toString(0x1F600);
        String body = "<" + "c".repeat(198) + emoji + "tail";

        UnknownResponseException error = catchThrowableOfType(
                UnknownResponseException.class, () -> ResponseParser.parse(new HttpResponse(500, body)));

        assertThat(error.getMessage()).startsWith("Non-JSON response (HTTP 500): " + "<" + "c".repeat(198) + "...");
        assertThat(error.getMessage()).doesNotContain(Character.toString(emoji.charAt(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,2]", "\"text\"", "42", "true", "null"})
    void jsonThatIsNotAnObjectIsRejected(String body) {
        UnknownResponseException error = catchThrowableOfType(
                UnknownResponseException.class, () -> ResponseParser.parse(new HttpResponse(200, body)));

        assertThat(error.getMessage())
                .startsWith("Unexpected JSON response (HTTP 200): expected a JSON object")
                .contains(body);
        assertThat(error.getStatusCode()).isEqualTo(200);
    }

    @Test
    void jsonArrayWithErrorStatusIsRejectedAsUnknown() {
        UnknownResponseException error = catchThrowableOfType(
                UnknownResponseException.class, () -> ResponseParser.parse(new HttpResponse(500, "[]")));

        assertThat(error.getStatusCode()).isEqualTo(500);
        assertThat(error.getMessage()).contains("got a JSON array");
    }

    @Test
    void parsedListResultIsAccessibleAsList() {
        ApiResponse response = ResponseParser.parse(new HttpResponse(200, "{\"result\":[{\"x\":1},{\"x\":2}]}"));

        List<Object> data = response.dataAsList();

        assertThat(data).hasSize(2);
    }

    @Test
    void rejectsNullResponseWithHelpfulMessage() {
        assertThatThrownBy(() -> ResponseParser.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HttpResponse");
    }
}
