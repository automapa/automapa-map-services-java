/**
 * Request objects of the endpoint methods, created with builders, e.g.
 * {@code GeocodeRequest.builder("Warszawa").street("Domaniewska").house("37").maxResults(1).build()}.
 *
 * <p>Required values are arguments of {@code builder(...)}, optional ones are builder methods. Every builder also
 * takes {@code extra} parameters sent to the API unchanged; named values override keys of the same name in
 * {@code extra} (see {@link pl.automapa.mapservices.request.ApiRequest#toParams()}).
 *
 * <p>Points use the API order: {@code x} is the longitude and {@code y} the latitude.
 */
package pl.automapa.mapservices.request;
