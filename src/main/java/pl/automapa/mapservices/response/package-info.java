/**
 * Response model of the SDK: {@link pl.automapa.mapservices.response.ApiResponse} wraps a decoded API response and
 * {@link pl.automapa.mapservices.response.ResponseParser} turns an HTTP response into an {@code ApiResponse} or the
 * matching {@link pl.automapa.mapservices.exception.AutomapaException}.
 *
 * <p>Responses are plain {@code Map}/{@code List} structures decoded from JSON; there are no typed response models.
 */
package pl.automapa.mapservices.response;
