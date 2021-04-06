
package org.ihtsdo.refsetservice.util;

import java.nio.charset.Charset;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * REST call utilities.
 */
public class RESTUtils {

    /** The Constant log. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(RESTUtils.class);

    /** The username. */
    private String username;

    /** The password. */
    private String password;

    /** The read timeout. */
    // private Duration readTimeout;

    /** The connect timeout. */
    // private Duration connectTimeout;

    /**
     * Instantiates an empty {@link RESTUtils}.
     */
    public RESTUtils() {
        // n/a
    }

    /**
     * Instantiates a {@link RESTUtils} from the specified parameters.
     *
     * @param username the username
     * @param password the password
     * @param readTimeout the read timeout
     * @param connectTimeout the connect timeout
     */
    public RESTUtils(final String username, final String password, final long readTimeout,
            final long connectTimeout) {
        this.username = username;
        this.password = password;
        // this.readTimeout = Duration.ofSeconds(readTimeout);
        // this.connectTimeout = Duration.ofSeconds(connectTimeout);
        // builder = new RestTemplateBuilder().basicAuthentication(username,
        // password)
        // .setReadTimeout(this.readTimeout).setConnectTimeout(this.connectTimeout);
    }

    /**
     * Run SPARQL.
     *
     * @param query the query
     * @param restURL the rest URL
     * @return the string
     */
    public String runSPARQL(final String query, final String restURL) {

        final RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(username, password));
        restTemplate.getMessageConverters().add(0,
                new StringHttpMessageConverter(Charset.forName("UTF-8")));
        final MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("query", query);
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Arrays.asList(new MediaType("application", "sparql-results+json")));
        final HttpEntity<?> entity = new HttpEntity<Object>(body, headers);
        final String results = restTemplate.postForObject(restURL, entity, String.class);
        return results;
    }

}
