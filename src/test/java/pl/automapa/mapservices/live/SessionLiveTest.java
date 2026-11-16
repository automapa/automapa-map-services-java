package pl.automapa.mapservices.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.session.PasswordHasher;

/** Session service on the live API; the suite is not gated by {@code AUTOMAPA_API_REQUIRED_ACCESS}. */
class SessionLiveTest extends LiveApiTestBase {

    @Test
    void getSaltReturnsNonEmptySalt() {
        Map<String, Object> body =
                assertSuccessResponse(sendRequest("Session", "getSalt", Map.of("key", apiKey()), null));

        assertThat(result(body).get("salt"))
                .isInstanceOf(String.class)
                .asString()
                .isNotEmpty();
    }

    @Test
    void generateSessionReturnsSessionId() {
        assertThat(acquireSession()).isNotBlank();
    }

    @Test
    void getSaltViaFacadeReturnsNonEmptySalt() {
        ApiResponse response =
                withRateLimitRetry(() -> buildApiClient().session().getSalt(apiKey()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.dataAsMap().get("salt"))
                .isInstanceOf(String.class)
                .asString()
                .isNotEmpty();
    }

    @Test
    void generateSessionViaFacadeReturnsSessionId() {
        ApiClient client = buildApiClient();
        String salt = (String) withRateLimitRetry(() -> client.session().getSalt(apiKey()))
                .dataAsMap()
                .get("salt");

        ApiResponse response = withRateLimitRetry(
                () -> client.session().generateSession(apiKey(), PasswordHasher.hash(apiPassword(), salt)));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.dataAsMap().get("sessionId")).asString().isNotBlank();
    }

    @Test
    void loginViaFacadeWithPlainPasswordReturnsSessionId() {
        ApiResponse response =
                withRateLimitRetry(() -> buildApiClient().session().login(apiKey(), apiPassword()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.dataAsMap().get("sessionId")).asString().isNotBlank();
    }

    @Test
    void openSessionViaClientReturnsSessionId() {
        ApiClient client = buildApiClient();

        String sessionId = withRateLimitRetry(client::openSession);

        assertThat(sessionId).isNotBlank();
        assertThat(client.openSession()).as("the open session is kept").isEqualTo(sessionId);
    }

    @Test
    void saltIsUniqueOnEachCall() {
        Map<String, Object> first =
                assertSuccessResponse(sendRequest("Session", "getSalt", Map.of("key", apiKey()), null));
        Map<String, Object> second =
                assertSuccessResponse(sendRequest("Session", "getSalt", Map.of("key", apiKey()), null));

        assertThat(result(first).get("salt"))
                .as("each getSalt call returns a unique salt")
                .isNotEqualTo(result(second).get("salt"));
    }
}
