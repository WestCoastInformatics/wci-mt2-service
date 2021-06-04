package org.ihtsdo.refsetservice.terminologyservice;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InternetDomainName;

/**
 * Class to handle making calls to Snowstorm.
 */
public class SnowstormConnection {

    /** The auth header. */
    private static String AUTH_HEADER;

    /** The authentication url. */
    private static String AUTH_URL;

    /** The user name. */
    public static String USER_NAME;

    /** The password. */
    public static String PASSWORD;

    /** The snowstorm url. */
    public static String BASE_URL;

    /** The snowstorm url for performing write or update actions. */
    public static String POST_URL;

    /** The accept. */
    private static final String ACCEPT = "application/json";

    /** The logger. */
    private static final Logger logger = LoggerFactory.getLogger(SnowstormConnection.class);

    /** The headers. */
    private static Map<String, String> headers = new HashMap<>();

    /** The generic user cookie expiration date. */
    private static Date genericUserCookieExpirationDate = null;

    /** The generic user cookie. */
    private static String genericUserCookie;

    /** Static initialization. */
    static {

        BASE_URL = PropertyUtility.getProperty("snowstorm.baseUrl");
        POST_URL = PropertyUtility.getProperty("snowstorm.postUrl");
        AUTH_URL = PropertyUtility.getProperty("snowstorm.authUrl");
        USER_NAME = PropertyUtility.getProperty("snowstorm.username");
        PASSWORD = PropertyUtility.getProperty("snowstorm.password");
        AUTH_HEADER = PropertyUtility.getProperty("snowstorm.authHeader");

        if (POST_URL == null) {
            POST_URL = BASE_URL;
        }
    }

    /**
     * Calls a Snowstorm URL and returns the response.
     *
     * @param url The Snowstorm URL to call
     * @return The Snowstorm response
     * @throws Exception the exception
     */
    public static Response getResponse(final String url) throws Exception {

        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(url);
        final Response response = target.request(ACCEPT).header("Authorization", AUTH_HEADER)
                .header("Accept-Language", "en-X-900000000000509007,en-X-900000000000508004,en")
                .header("Cookie",
                        genericUserCookie != null ? genericUserCookie : getGenericUserCookie())
                .get();

        return response;
    }

    /**
     * Post response.
     *
     * @param url the url
     * @param entity the entity
     * @return the response
     * @throws Exception the exception
     */
    public static Response postResponse(final String url, String entity) throws Exception {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(url);
        Builder builder = target.request(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH_HEADER)
                .header("Accept-Language", "en-X-900000000000509007,en-X-900000000000508004,en")
                .header("Cookie",
                        genericUserCookie != null ? genericUserCookie : getGenericUserCookie());

        Response response = builder.post(Entity.json(entity));
        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new LocalException(
                    "Authentication of generic user failed. " + response.toString());
        }
        return response;
    }

    /**
     * Gets the generic user cookie.
     *
     * @return the generic user cookie
     * @throws Exception the exception
     */
    public static String getGenericUserCookie() throws Exception {

        // Check if the generic user cookie is expired and needs to be cleared
        // and re-read
        if (genericUserCookieExpirationDate == null
                || new Date().after(genericUserCookieExpirationDate)) {

            genericUserCookie = null;

            // Set the new expiration date for tomorrow
            Calendar now = Calendar.getInstance();
            now.add(Calendar.HOUR, 24);
            genericUserCookieExpirationDate = now.getTime();
        }

        if (genericUserCookie != null) {
            return genericUserCookie;
        }

        // Login the generic user, then save and return the cookie
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(AUTH_URL + "authenticate");
        Builder builder = target.request(MediaType.APPLICATION_JSON);

        try (Response response = builder.post(Entity.json(
                "{ \"login\": \"" + USER_NAME + "\", \"password\": \"" + PASSWORD + "\" }"))) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new LocalException(
                        "Authentication of generic user failed. " + response.toString());
            }

            Map<String, NewCookie> genericUserCookies = response.getCookies();
            StringBuilder sb = new StringBuilder();

            for (String key : genericUserCookies.keySet()) {

                sb.append(genericUserCookies.get(key));
                sb.append(";");
            }

            genericUserCookie = sb.toString();
        }

        return genericUserCookie;
    }

    /**
     * Returns the cookie header.
     *
     * @return the cookie header
     * @throws MalformedURLException the malformed URL exception
     */
    private static String getCookieHeader() throws MalformedURLException {

        CharSequence domain =
                InternetDomainName.from(new URL(BASE_URL).getHost()).topPrivateDomain().toString();
        final String referer = headers.get("Referer");

        if (referer.contains(domain)) {
            return headers.get("Cookie");
        } else {
            logger.warn("UNEXPECTED referer not matching url domain = " + referer);
        }

        return "";
    }
}
