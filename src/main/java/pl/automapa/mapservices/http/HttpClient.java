package pl.automapa.mapservices.http;

/**
 * Transport used by the SDK to send HTTP requests.
 *
 * <p>Implementations <b>must be thread safe</b>: a single {@code ApiClient} may call {@link #send} from many threads
 * at the same time.
 */
public interface HttpClient {

    /**
     * Sends the request and returns the response, whatever its status code is.
     *
     * @param request the request to send
     * @return the response received from the server
     * @throws pl.automapa.mapservices.exception.NetworkException when no response is received (connection, DNS, TLS
     *     or timeout problems)
     */
    HttpResponse send(HttpRequest request);
}
