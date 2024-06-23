package com.maxmind.geoip2;


import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.exception.AuthenticationException;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.exception.HttpException;
import com.maxmind.geoip2.exception.InvalidRequestException;
import com.maxmind.geoip2.exception.OutOfQueriesException;
import com.maxmind.geoip2.exception.PermissionRequiredException;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.model.InsightsResponse;


/**
 * The {@code WebServiceClient} class provides a client API for all the GeoIP2 web service
 * <p>
 * The GeoIP2 web service end points are Country, City, and
 * Insights. Each end point returns a different set of data about an IP
 * address, with Country returning the least data and Insights the most.
 * </p>
 * <p>
 * Each web service end point is represented by a different model class, and
 * these model classes in turn contain multiple Record classes. The record
 * classes have attributes which contain data about the IP address.
 * </p>
 * <p>
 * If the web service does not return a particular piece of data for an IP
 * address, the associated attribute is not populated.
 * </p>
 * <p>
 * The web service may not return any information for an entire record, in which
 * case all of the attributes for that record class will be empty.
 * </p>
 * <h2>Usage</h2>
 * <p>
 * To use the web service API, you must create a new {@code WebServiceClient}
 * using the {@code WebServiceClient.Builder}. You must provide the
 * {@code Builder} constructor your MaxMind {@code accountId} and
 * {@code licenseKey}. To use the GeoLite2 web services instead of GeoIP2, set
 * the {@code host} method on the builder to {@code geolite.info}. You may also
 * set a {@code timeout} or set the {@code locales} fallback order using the
 * methods on the {@code Builder}. After you have created the
 * {@code WebServiceClient}, you may then call the method corresponding to a
 * specific end point, passing it the IP address you want to look up.
 * </p>
 * <p>
 * If the request succeeds, the method call will return a model class for the
 * end point you called. This model in turn contains multiple record classes,
 * each of which represents part of the data returned by the web service.
 * </p>
 * <p>
 * If the request fails, the client class throws an exception.
 * </p>
 * <p>
 * The {@code WebServiceClient} object is safe to share across threads. If you
 * are making multiple requests, the object should be reused so that new
 * connections are not created for each request. Once you have finished making
 * requests, you should close the object to ensure the connections are closed
 * and any resources are promptly returned to the system.
 * </p>
 * <h2>Exceptions</h2>
 * <p>
 * For details on the possible errors returned by the web service itself, see <a
 * href="https://dev.maxmind.com/geoip/docs/web-services?lang=en">the GeoIP2 web
 * service documentation</a>.
 * </p>
 * <p>
 * If the web service returns an explicit error document, this is thrown as a
 * {@link InvalidRequestException}. If some other sort of transport error
 * occurs, this is thrown as a {@link HttpException}. The difference is that the
 * web service error includes an error message and error code delivered by the
 * web service. The latter is thrown when some sort of unanticipated error
 * occurs, such as the web service returning a 500 or an invalid error document.
 * </p>
 * <p>
 * If the web service returns any status code besides 200, 4xx, or 5xx, this
 * also becomes a {@link HttpException}.
 * </p>
 * <p>
 * Finally, if the web service returns a 200 but the body is invalid, the client
 * throws a {@link GeoIp2Exception}.
 * </p>
 */
@SuppressWarnings("unused")
public class WebServiceClient implements GeoIp2Provider, Closeable {
    private final String host;
    private final List<String> locales;
    private final String licenseKey;

    private final int accountId;
    private final boolean useHttps;
    private final int port;

    private final ObjectMapper mapper;
    private final CloseableHttpClient httpClient;

    // David Hsing added on 2022-02-03
    private boolean useProxy = false;

    private WebServiceClient(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.useHttps = builder.useHttps;
        this.locales = builder.locales;
        this.licenseKey = builder.licenseKey;
        this.accountId = builder.accountId;

        mapper = JsonMapper.builder()
                .disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        RequestConfig.Builder configBuilder = RequestConfig.custom()
                // David Hsing modified on 2022-02-03
                // .setConnectTimeout(builder.connectTimeout)
                // .setSocketTimeout(builder.readTimeout);
                .setConnectTimeout(Timeout.ofMilliseconds(builder.connectTimeout))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(builder.readTimeout));

        if (builder.proxy != null && builder.proxy != Proxy.NO_PROXY) {
            InetSocketAddress address = (InetSocketAddress) builder.proxy.address();
            HttpHost proxyHost = new HttpHost(address.getHostName(), address.getPort());
            configBuilder.setProxy(proxyHost);
            useProxy = true;
        }

        RequestConfig config = configBuilder.build();
        httpClient =
                HttpClientBuilder.create()
                        // David Hsing modified on 2022-02-03
                        // .setMaxConnPerRoute(20)
                        .setUserAgent(userAgent())
                        .setDefaultRequestConfig(config).build();
    }

    /**
     * {@code Builder} creates instances of {@code WebServiceClient} from values set by the methods
     * <p>
     * This example shows how to create a {@code WebServiceClient} object with the {@code Builder}:
     * </p>
     * <p>
     * {@code WebServiceClient client = new WebServiceClient.Builder(12,"licensekey").host("geoip.maxmind.com").build();}
     * </p>
     * <p>
     * Only the values set in the {@code Builder} constructor are required.
     * </p>
     */
    public static final class Builder {
        final int accountId;
        final String licenseKey;

        String host = "geoip.maxmind.com";
        int port = 443;
        boolean useHttps = true;

        int connectTimeout = 3000;
        int readTimeout = 20000;

        List<String> locales = Collections.singletonList("en");
        private Proxy proxy;

        /**
         * @param accountId  Your MaxMind account ID.
         * @param licenseKey Your MaxMind license key.
         */
        public Builder(int accountId, String licenseKey) {
            this.accountId = accountId;
            this.licenseKey = licenseKey;
        }

        /**
         * @param val Timeout in milliseconds to establish a connection to the web service. The default is 3000 (3 seconds)
         *
         * @return Builder object
         */
        public Builder connectTimeout(int val) {
            this.connectTimeout = val;
            return this;
        }

        /**
         * Disables HTTPS to connect to a test server or proxy. The minFraud ScoreResponse and InsightsResponse web services require HTTPS
         *
         * @return Builder object
         */
        public Builder disableHttps() {
            useHttps = false;
            return this;
        }

        /**
         * @param val The host to use. Set this to {@code geolite.info} to use the GeoLite2 web service instead of GeoIP2 Precision
         *
         * @return Builder object
         */
        public Builder host(String val) {
            this.host = val;
            return this;
        }

        /**
         * @param val The port to use
         *
         * @return Builder object
         */
        public Builder port(int val) {
            port = val;
            return this;
        }

        /**
         * @param val List of locale codes to use in name property from most preferred to least preferred
         *
         * @return Builder object
         */
        public Builder locales(List<String> val) {
            this.locales = new ArrayList<>(val);
            return this;
        }

        /**
         * @param val readTimeout in milliseconds to read data from an established connection to the web service. The default is 20000 (20 seconds)
         *
         * @return Builder object
         */
        public Builder readTimeout(int val) {
            this.readTimeout = val;
            return this;
        }

        /**
         * @param val the proxy to use when making this request
         *
         * @return Builder object
         */
        public Builder proxy(Proxy val) {
            this.proxy = val;
            return this;
        }

        /**
         * @return an instance of {@code WebServiceClient} created from the fields set on this builder.
         */
        public WebServiceClient build() {
            return new WebServiceClient(this);
        }
    }

    /**
     * @return A Country model for the requesting IP address
     * @throws GeoIp2Exception if there is an error from the web service
     * @throws IOException     if an IO error happens during the request
     */
    public CountryResponse country() throws IOException, GeoIp2Exception {
        return this.country(null);
    }

    @Override
    public CountryResponse country(InetAddress ipAddress) throws IOException, GeoIp2Exception {
        return this.responseFor("country", ipAddress, CountryResponse.class);
    }

    /**
     * @return A City model for the requesting IP address
     * @throws GeoIp2Exception if there is an error from the web service
     * @throws IOException     if an IO error happens during the request
     */
    public CityResponse city() throws IOException, GeoIp2Exception {
        return this.city(null);
    }

    @Override
    public CityResponse city(InetAddress ipAddress) throws IOException, GeoIp2Exception {
        return this.responseFor("city", ipAddress, CityResponse.class);
    }

    /**
     * @return An Insights model for the requesting IP address
     * @throws GeoIp2Exception if there is an error from the web service
     * @throws IOException     if an IO error happens during the request
     */
    public InsightsResponse insights() throws IOException, GeoIp2Exception {
        return this.insights(null);
    }

    /**
     * @param ipAddress IPv4 or IPv6 address to lookup.
     * @return An insight model for the requested IP address.
     * @throws GeoIp2Exception if there is an error looking up the IP
     * @throws IOException     if there is an IO error
     */
    public InsightsResponse insights(InetAddress ipAddress) throws IOException, GeoIp2Exception {
        return this.responseFor("insights", ipAddress, InsightsResponse.class);
    }

    private <T> T responseFor(String path, InetAddress ipAddress, Class<T> cls) throws IOException, GeoIp2Exception {
        URL url = createUri(path, ipAddress);
        HttpGet request = getResponse(url);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            return handleResponse(response, url, cls);
        }
    }

    private HttpGet getResponse(URL url) throws GeoIp2Exception {
        // David Hsing modified on 2022-02-03
        // Credentials credentials = new UsernamePasswordCredentials(Integer.toString(accountId), licenseKey);
        Credentials credentials = new UsernamePasswordCredentials(Integer.toString(accountId), (licenseKey == null ? null : licenseKey.toCharArray()));

        HttpGet request;
        try {
            request = new HttpGet(url.toURI());
        } catch (URISyntaxException e) {
            throw new GeoIp2Exception("Error parsing request URL", e);
        }
        try {
            // David Hsing modified on 2022-02-03
            // request.addHeader(new BasicScheme().authenticate(credentials, request, null));
            BasicScheme basicScheme = new BasicScheme();
            basicScheme.initPreemptive(credentials);
            String authenticate = basicScheme.generateAuthResponse(HttpHost.create(url.toURI()), request, null);
            String headerKey = useProxy ? HttpHeaders.PROXY_AUTHORIZATION : HttpHeaders.AUTHORIZATION;
            request.addHeader(new BasicHeader(headerKey, authenticate));
        // David Hsing modified on 2022-02-03
        // } catch (org.apache.http.auth.AuthenticationException e) {
        } catch (org.apache.hc.client5.http.auth.AuthenticationException | URISyntaxException e) {
            throw new AuthenticationException("Error setting up request authentication", e);
        }
        request.addHeader("Accept", "application/json");
        return request;
    }

    private <T> T handleResponse(CloseableHttpResponse response, URL url, Class<T> cls) throws GeoIp2Exception, IOException {
        // David Hsing modified on 2022-02-03
        // int status = response.getStatusLine().getStatusCode();
        int status = response.getCode();
        if (status >= 400 && status < 500) {
            this.handle4xxStatus(response, url);
        } else if (status >= 500 && status < 600) {
            throw new HttpException("Received a server error (" + status
                    + ") for " + url, status, url);
        } else if (status != 200) {
            throw new HttpException("Received an unexpected HTTP status ("
                    + status + ") for " + url, status, url);
        }

        InjectableValues inject = new JsonInjector(locales, null, null);

        HttpEntity entity = response.getEntity();
        try {
            return mapper.readerFor(cls).with(inject).readValue(entity.getContent());
        } catch (IOException e) {
            throw new GeoIp2Exception(
                    "Received a 200 response but could not decode it as JSON", e);
        } finally {
            EntityUtils.consume(entity);
        }
    }

    private void handle4xxStatus(HttpResponse response, URL url) throws GeoIp2Exception, IOException {
        // David Hsing modified on 2022-02-03
        // HttpEntity entity = response.getEntity();
        // int status = response.getStatusLine().getStatusCode();
        HttpEntity entity = (response instanceof CloseableHttpResponse) ? ((CloseableHttpResponse) response).getEntity() : null;
        int status = response.getCode();

        // David Hsing modified on 2022-02-03
        // if (entity.getContentLength() == 0L) {
        if (entity == null || entity.getContentLength() == 0L) {
            throw new HttpException("Received a " + status + " error for " + url + " with no body", status, url);
        }

        // David Hsing modified on 2022-02-03
        // String body = EntityUtils.toString(entity, "UTF-8");
        String body = null;
        try {
            body = EntityUtils.toString(entity, "UTF-8");
            Map<String, String> content = mapper.readValue(body, new TypeReference<HashMap<String, String>>() {});
            handleErrorWithJsonBody(content, body, status, url);
        } catch (HttpException e) {
            throw e;
        // David Hsing modified on 2022-02-03
        // } catch (IOException e) {
        } catch (IOException | ParseException e) {
            throw new HttpException("Received a " + status + " error for " + url + " but it did not include the expected JSON body: " + body, status, url);
        }
    }

    private static void handleErrorWithJsonBody(Map<String, String> content, String body, int status, URL url) throws GeoIp2Exception, HttpException {
        String error = content.get("error");
        String code = content.get("code");

        if (error == null || code == null) {
            throw new HttpException("Error response contains JSON but it does not specify code or error keys: " + body, status, url);
        }

        switch (code) {
            case "IP_ADDRESS_NOT_FOUND":
            case "IP_ADDRESS_RESERVED":
                throw new AddressNotFoundException(error);
            case "ACCOUNT_ID_REQUIRED":
            case "ACCOUNT_ID_UNKNOWN":
            case "AUTHORIZATION_INVALID":
            case "LICENSE_KEY_REQUIRED":
            case "USER_ID_REQUIRED":
            case "USER_ID_UNKNOWN":
                throw new AuthenticationException(error);
            case "INSUFFICIENT_FUNDS":
            case "OUT_OF_QUERIES":
                throw new OutOfQueriesException(error);
            case "PERMISSION_REQUIRED":
                throw new PermissionRequiredException(error);
        }
        // These should be fairly rare
        throw new InvalidRequestException(error, code, url);
    }

    private URL createUri(String service, InetAddress ipAddress) throws GeoIp2Exception {
        try {
            return new URIBuilder()
                    .setScheme(useHttps ? "https" : "http")
                    .setHost(host)
                    .setPort(this.port)
                    .setPath("/geoip/v2.1/" + service + "/" + (ipAddress == null ? "me" : ipAddress.getHostAddress()))
                    .build().toURL();
        } catch (MalformedURLException e) {
            throw new GeoIp2Exception("Malformed service URL", e);
        } catch (URISyntaxException e) {
            throw new GeoIp2Exception("Syntax error creating service URL", e);
        }
    }

    private String userAgent() {
        return "GeoIP2/" + getClass().getPackage().getImplementationVersion() + " (Java/" + System.getProperty("java.version") + ")";
    }

    /**
     * Close any open connections and return resources to the system.
     */
    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    @Override
    public String toString() {
        return "WebServiceClient{" +
                ", host='" + host + '\'' +
                ", locales=" + locales +
                ", licenseKey='" + licenseKey + '\'' +
                ", accountId=" + accountId +
                ", useHttps=" + useHttps +
                ", port=" + port +
                ", mapper=" + mapper +
                ", httpClient=" + httpClient +
                '}';
    }
}
