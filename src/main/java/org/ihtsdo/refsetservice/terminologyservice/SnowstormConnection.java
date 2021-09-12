package org.ihtsdo.refsetservice.terminologyservice;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to handle making calls to Snowstorm.
 */
public class SnowstormConnection {

    /** The authentication url. */
    private static String AUTH_URL;

    /** The user name. */
    public static String USER_NAME;

    /** The password. */
    public static String PASSWORD;

    /** The snowstorm url. */
    public static String BASE_URL;

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
        AUTH_URL = PropertyUtility.getProperty("snowstorm.authUrl");
        USER_NAME = PropertyUtility.getProperty("snowstorm.username");
        PASSWORD = PropertyUtility.getProperty("snowstorm.password");
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
        final Response response = target.request(ACCEPT)
                .header("Accept-Language", "en-X-900000000000509007,en-X-900000000000508004,en")
                .header("Cookie", getGenericUserCookie())
                .get();

        return response;
    }
    
    /**
     * Calls a Snowstorm URL and returns the response.
     *
     * @param url The Snowstorm URL to call
     * @return The Snowstorm response
     * @throws Exception the exception
     */
    public static InputStream getFileDownload(final String url) throws Exception {
        
        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(url);
        final InputStream response = target.request("application/zip")
                .header("Accept-Language", "en-X-900000000000509007,en-X-900000000000508004,en")
                .header("Cookie", getGenericUserCookie())
                .get(InputStream.class);

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
                .header("Accept-Language", "en-X-900000000000509007,en-X-900000000000508004,en")
                .header("Cookie", getGenericUserCookie());
        
        Response response = builder.post(Entity.json(entity));
        
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
    public static Response putResponse(final String url, String entity) throws Exception {
        
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(url);
        Builder builder = target.request(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "en-X-900000000000509007,en-X-900000000000508004,en")
                .header("Cookie", getGenericUserCookie());
        
        Response response = builder.put(Entity.json(entity));
        
        return response;
    }
    
    /**
     * Calls a Snowstorm DELETE URL and returns the response.
     *
     * @param url The Snowstorm URL to call
     * @return The Snowstorm response
     * @throws Exception the exception
     */
    public static Response deleteResponse(final String url) throws Exception {

        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(url);
        final Response response = target.request(ACCEPT)
                .header("Accept-Language", "en-X-900000000000509007,en-X-900000000000508004,en")
                .header("Cookie", getGenericUserCookie())
                .delete();

        return response;
    }

    /**
     * Gets the generic user cookie.
     *
     * @return the generic user cookie
     * @throws Exception the exception
     */
    public static String getGenericUserCookie() throws Exception {

        // if there is no auth configured then skip this
        if (AUTH_URL.equals("none")) {
            return "";
        }
        
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
}


