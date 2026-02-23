/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

/**
 * Class to handle making calls to Snowstorm.
 */
public final class SnowstormConnection {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(SnowstormConnection.class);

    /** The authentication url. */
    private static String authUrl;

    /** The user name. */
    private static String userName;

    /** The password. */
    private static String password;

    /** The snowstorm url. */
    private static String baseUrl;

    /** The accept. */
    private static final String ACCEPT = MediaType.APPLICATION_JSON;

    /** The generic user cookie expiration date. */
    private static Date genericUserCookieExpirationDate = null;

    /** The generic user cookie. */
    private static String genericUserCookie;

    /** The default English language acceptance strings. */
    public static final String DEFAULT_ACCECPT_LANGUAGES = "en-X-900000000000509007,en-X-900000000000508004,en";

    /** Static initialization. */
    static {
        baseUrl = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.baseUrl");
        authUrl = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.authUrl");
        userName = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.username");
        password = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.password");
    }

    /**
     * Instantiates an empty {@link SnowstormConnection}.
     */
    private SnowstormConnection() {

        // n/a
    }

    /**
     * Returns the base url.
     *
     * @return the base url
     */
    public static String getBaseUrl() {

        return baseUrl;
    }

    /**
     * Calls a Snowstorm URL and returns the response.
     *
     * @param url The Snowstorm URL to call
     * @param language The language to prefer snowstorm to return descriptions in.
     * @return The Snowstorm response
     * @throws Exception the exception
     */
    public static Response getResponse(final String url, final String language) throws Exception {

        final Client client = getClient(ClientBuilder.newClient());
        final WebTarget target = client.target(url);
        String cookie = getGenericUserCookie(false);
        Response response = null;
        boolean firstRun = true;
        boolean run = true;

        while (run) {
            run = false;

            final Builder builder = target.request(ACCEPT).header(HttpHeaders.ACCEPT_LANGUAGE, language);
            if (cookie != null && !cookie.isEmpty()) {
                builder.header("Cookie", cookie);
            }

            response = builder.get();

            if (firstRun && response.getStatus() == Response.Status.FORBIDDEN.getStatusCode()) {
                run = true;
                firstRun = false;
                cookie = getGenericUserCookie(true);
                // close the response because we're going to make another
                response.close();
            }
        }

        return response;
    }

    /**
     * Calls a Snowstorm URL and returns the response in English.
     *
     * @param url The Snowstorm URL to call
     * @return The Snowstorm response
     * @throws Exception the exception
     */
    public static Response getResponse(final String url) throws Exception {

        return getResponse(url, DEFAULT_ACCECPT_LANGUAGES);
    }

    /**
     * Calls a Snowstorm URL and returns the response.
     *
     * @param url The Snowstorm URL to call
     * @return The Snowstorm response
     * @throws Exception the exception
     */
    @SuppressWarnings("resource")
    public static InputStream getFileDownload(final String url) throws Exception {

        final Client client = getClient(ClientBuilder.newClient());
        final WebTarget target = client.target(url);

        final String cookie = getGenericUserCookie(false);
        final Builder builder = target.request("application/zip").header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_ACCECPT_LANGUAGES);

        if (cookie != null && !cookie.isEmpty()) {
            builder.header("Cookie", cookie);
        }

        // Get the response
        final Response response = builder.get();

        // Check if the response was successful
        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            String errorMsg = "Failed to download file. Status: " + response.getStatus();
            LOG.error(errorMsg);
            throw new LocalException(errorMsg);
        }

        // Read the entity as an InputStream so we don't depend on a byte[] MessageBodyReader
        final InputStream inputStream = response.readEntity(InputStream.class);

        return inputStream;
    }

    /**
     * Post response.
     *
     * @param url the url
     * @param entity the entity
     * @return the response
     * @throws Exception the exception
     */
    public static Response postResponse(final String url, final String entity) throws Exception {

        final Client client = getClient(ClientBuilder.newClient());
        final WebTarget target = client.target(url);
        final Builder builder = target.request(MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_ACCECPT_LANGUAGES);

        final String cookie = getGenericUserCookie(false);
        if (cookie != null && !cookie.isEmpty()) {
            builder.header("Cookie", cookie);
        }

        // Convert the JSON string to a Map so JacksonJsonProvider can serialize it as JSON
        final Map<String, Object> entityMap = jsonToMap(entity);

        // #region agent log
        try {
            final Map<String, Object> data = new HashMap<>();
            data.put("url", url);
            data.put("entityClass", entityMap != null ? entityMap.getClass().getName() : "null");
            data.put("clientClass", client.getClass().getName());
            data.put("runId", "post-fix-map");

            final Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("sessionId", "d7a125");
            logEntry.put("hypothesisId", "H1");
            logEntry.put("location", "SnowstormConnection.java:193");
            logEntry.put("message", "postResponse before POST");
            logEntry.put("data", data);
            logEntry.put("timestamp", System.currentTimeMillis());

            final String json = ThreadLocalMapper.get().writeValueAsString(logEntry);
            java.nio.file.Files.write(java.nio.file.Paths.get("debug-d7a125.log"),
                (json + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (final Exception e) {
            // ignore instrumentation failures
        }
        // #endregion agent log

        // Send Map so JacksonJsonProvider can write application/json correctly
        final Response response = builder.post(Entity.entity(entityMap, MediaType.APPLICATION_JSON));

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
    public static Response putResponse(final String url, final String entity) throws Exception {

        final Client client = getClient(ClientBuilder.newClient());
        final WebTarget target = client.target(url);
        final Builder builder = target.request(MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_ACCECPT_LANGUAGES);

        final String cookie = getGenericUserCookie(false);
        if (cookie != null && !cookie.isEmpty()) {
            builder.header("Cookie", cookie);
        }

        final Response response = builder.put(Entity.entity(entity, MediaType.APPLICATION_JSON));

        return response;
    }

    /**
     * Calls a Snowstorm DELETE URL and returns the response.
     *
     * @param url The Snowstorm URL to call
     * @param entity the entity
     * @return The Snowstorm response
     * @throws Exception the exception
     */
    public static Response deleteResponse(final String url, final String entity) throws Exception {

        final Client client = getClient(ClientBuilder.newClient());
        final WebTarget target = client.target(url);
        final Builder builder = target.request(ACCEPT).header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_ACCECPT_LANGUAGES);

        final String cookie = getGenericUserCookie(false);
        if (cookie != null && !cookie.isEmpty()) {
            builder.header("Cookie", cookie);
        }

        Response response;

        // TODO: we shouldn't return a response here and leave it open
        // we should get its payload and return that and then make sure the response is closed.
        if (entity == null) {
            response = builder.delete();
        } else {
            response = builder.build("DELETE", Entity.entity(entity, MediaType.APPLICATION_JSON)).invoke(Response.class);
        }

        return response;
    }

    /**
     * Gets the generic user cookie.
     *
     * @param forceReload the force reload
     * @return the generic user cookie
     * @throws Exception the exception
     */
    public static String getGenericUserCookie(final boolean forceReload) throws Exception {

        // if there is no auth configured then skip this
        if ("none".equals(authUrl)) {
            return "";
        }

        if (forceReload) {
            genericUserCookie = null;
        }

        // Check if the generic user cookie is expired and needs to be cleared
        // and re-read
        if (genericUserCookieExpirationDate == null || new Date().after(genericUserCookieExpirationDate)) {

            genericUserCookie = null;

            // Set the new expiration date for tomorrow
            final Calendar now = Calendar.getInstance();
            now.add(Calendar.HOUR, 24);
            genericUserCookieExpirationDate = now.getTime();
        }

        if (genericUserCookie != null) {
            return genericUserCookie;
        }

        // Login the generic user, then save and return the cookie
        final Client client = getClient(ClientBuilder.newClient());
        final WebTarget target = client.target(authUrl + "authenticate");
        final Builder builder = target.request(MediaType.APPLICATION_JSON);

        // Create a map for the login credentials
        final Map<String, String> loginData = new HashMap<>();
        loginData.put("login", userName);
        loginData.put("password", password);

        try (final Response response = builder.post(Entity.entity(loginData, MediaType.APPLICATION_JSON))) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new LocalException("Authentication of generic user failed. " + " Status: " + Integer.toString(response.getStatus()) + ". Error: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            final Map<String, NewCookie> genericUserCookies = response.getCookies();
            final StringBuilder sb = new StringBuilder();

            for (final String key : genericUserCookies.keySet()) {

                sb.append(genericUserCookies.get(key));
                sb.append(";");
            }

            genericUserCookie = sb.toString();

        } catch (final Exception e) {
            LOG.error("Authentication of generic user failed. {}", e.getMessage(), e);
            throw new LocalException("Authentication of generic user failed. " + e.getMessage());
        }

        return genericUserCookie;
    }

    /**
     * Configures a client with all necessary providers for JSON processing.
     *
     * @param client the client to configure
     * @return the configured client
     */
    public static Client getClient(final Client client) {
        // RESTEasy default client already has InputStreamProvider and ByteArrayProvider; only add JSON
        client.register(JacksonJsonProvider.class);
        return client;
    }

    /**
     * Converts a JSON string to a Map that can be passed to Entity.entity().
     *
     * @param jsonString the JSON string to convert
     * @return the Map representation of the JSON
     * @throws Exception if there is an error parsing the JSON
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonToMap(String jsonString) throws Exception {

        return ThreadLocalMapper.get().readValue(jsonString, Map.class);
    }

    /**
     * Helper method to safely read a response entity as a String.
     *
     * @param response the response to read
     * @return the response body as a string
     * @throws Exception if there is an error reading the response
     */
    public static String readEntityAsString(Response response) throws Exception {

        try {
            // Read as byte array (using ByteArrayProvider) and convert to String once
            byte[] bytes = response.readEntity(byte[].class);
            return new String(bytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            LOG.error("Could not read entity as byte array: {}", e.getMessage());
            throw new LocalException("Could not read response entity: " + e.getMessage());
        }
    }
}
