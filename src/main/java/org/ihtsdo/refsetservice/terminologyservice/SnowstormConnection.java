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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.Client;
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
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.plugins.providers.ByteArrayProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

/**
 * Class to handle making calls to Snowstorm.
 */
public final class SnowstormConnection {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SnowstormConnection.class);

    /** The authentication url. */
    private static String authUrl;

    /** The user name. */
    private static String userName;

    /** The password. */
    private static String password;

    /** Snowstorm REST API base URL. */
    private static String restBaseUrl;

    /** Optional FHIR base; when unset or none, {@link #getFhirBaseUrl()} uses {@link #getRestBaseUrl()}. */
    private static String fhirBaseUrl;

    /** How this deployment authenticates to Snowstorm (mutually exclusive). */
    private static SnowstormAuthMode authMode;

    /** The accept. */
    private static final String ACCEPT = MediaType.APPLICATION_JSON;

    /** The generic user cookie expiration date. */
    private static Date genericUserCookieExpirationDate = null;

    /** The generic user cookie. */
    private static String genericUserCookie;

    /** The default English language acceptance strings. */
    public static final String DEFAULT_ACCECPT_LANGUAGES = "en-X-900000000000509007,en-X-900000000000508004,en";

    /** Cached RESTEasy client – created once, reused for all Snowstorm calls. */
    private static volatile Client sharedClient;

    /** Max redirects for Snowstorm GET; RESTEasy does not follow 3xx by default (merge job polls would loop forever on 307). */
    private static final int SNOWSTORM_GET_MAX_REDIRECTS = 16;

    /**
     * The Enum SnowstormAuthMode.
     */
    private enum SnowstormAuthMode {

        /** The none. */
        NONE,
        /** The cookie. */
        COOKIE,
        /** The basic. */
        BASIC
    }

    /** Static initialization. */
    static {
        loadConfigurationFromProperties();
    }

    /**
     * Reload Snowstorm connection settings from {@link PropertyUtility}. Used by integration tests that inject
     * credentials after the JVM starts.
     */
    public static void loadConfigurationFromProperties() {

        restBaseUrl = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.restBaseUrl");
        fhirBaseUrl = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.fhirBaseUrl");
        authUrl = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.authUrl");
        userName = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.username");
        password = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.password");
        final String authTypeProperty = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.authType");
        authMode = resolveSnowstormAuthMode(authTypeProperty, authUrl);
        genericUserCookie = null;
        genericUserCookieExpirationDate = null;
    }

    /**
     * Resolve snowstorm auth mode.
     *
     * @param authTypeProperty the auth type property
     * @param authUrlProperty the auth url property
     * @return the snowstorm auth mode
     */
    private static SnowstormAuthMode resolveSnowstormAuthMode(final String authTypeProperty, final String authUrlProperty) {

        final String configValue = authTypeProperty == null ? "" : authTypeProperty.trim();
        if (!configValue.isEmpty()) {
            switch (configValue.toLowerCase()) {
                case "none":
                    return SnowstormAuthMode.NONE;
                case "cookie":
                    return SnowstormAuthMode.COOKIE;
                case "basic":
                    return SnowstormAuthMode.BASIC;
                default:
                    throw new IllegalArgumentException(
                        "Invalid terminology.handler.SNOMED_SNOWSTORM.authType: \"" + authTypeProperty + "\". Expected cookie, basic, or none.");
            }
        }
        if (isAuthUrlUnsetOrDisabled(authUrlProperty)) {
            return SnowstormAuthMode.NONE;
        }
        return SnowstormAuthMode.COOKIE;
    }

    /**
     * Checks if is auth url unset or disabled.
     *
     * @param url the url
     * @return true, if is auth url unset or disabled
     */
    private static boolean isAuthUrlUnsetOrDisabled(final String url) {

        if (url == null) {
            return true;
        }
        final String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        return "none".equalsIgnoreCase(trimmed);
    }

    /**
     * Snowstorm basic authorization header.
     *
     * @return {@code Authorization: Basic …} value when auth mode is basic; otherwise {@code null}
     * @throws LocalException when basic mode is on but username or password is missing
     */
    private static String snowstormBasicAuthorizationHeader() throws LocalException {

        if (authMode != SnowstormAuthMode.BASIC) {
            return null;
        }
        if (userName == null || userName.isBlank() || password == null || password.isBlank()) {
            throw new LocalException("Snowstorm basic auth is enabled but username or password is missing or blank.");
        }
        final String credentials = userName + ":" + password;
        final String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Apply snowstorm auth headers.
     *
     * @param builder the builder
     * @param sessionCookie the session cookie
     * @throws Exception the exception
     */
    private static void applySnowstormAuthHeaders(final Builder builder, final String sessionCookie) throws Exception {

        final String basicHeader = snowstormBasicAuthorizationHeader();
        if (basicHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, basicHeader);
        }
        if (sessionCookie != null && !sessionCookie.isEmpty()) {
            builder.header("Cookie", sessionCookie);
        }
    }

    /**
     * Instantiates an empty {@link SnowstormConnection}.
     */
    private SnowstormConnection() {

        // n/a
    }

    /**
     * Returns the Snowstorm REST API base URL.
     *
     * @return the REST base url
     */
    public static String getRestBaseUrl() {

        return restBaseUrl;
    }

    /**
     * Same as {@link #getRestBaseUrl()}; kept for call sites that use the older name.
     *
     * @return the REST base url
     */
    public static String getBaseUrl() {

        return restBaseUrl;
    }

    /**
     * Returns the base URL used for FHIR operations ({@code fhir/CodeSystem}, {@code fhir/ValueSet}, etc.). When not configured, this is the same as the REST
     * base URL.
     *
     * @return the FHIR base url
     */
    public static String getFhirBaseUrl() {

        if (fhirBaseUrl == null || fhirBaseUrl.isBlank() || "none".equalsIgnoreCase(fhirBaseUrl.trim())) {
            return restBaseUrl;
        }
        return fhirBaseUrl;
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

        final long requestStartMs = System.currentTimeMillis();
        final Client client = getClient();
        String cookie = "";
        if (authMode == SnowstormAuthMode.COOKIE) {
            cookie = getGenericUserCookie(false);
        }
        boolean firstRun = true;
        String currentUrl = url;

        for (int redirectHop = 0; redirectHop < SNOWSTORM_GET_MAX_REDIRECTS; redirectHop++) {
            Response response = null;
            boolean authRetry = true;
            while (authRetry) {
                authRetry = false;
                final WebTarget target = client.target(currentUrl);
                final Builder builder = target.request(ACCEPT).header(HttpHeaders.ACCEPT_LANGUAGE, language);
                applySnowstormAuthHeaders(builder, cookie);

                response = builder.get();

                if (firstRun && authMode == SnowstormAuthMode.COOKIE && response.getStatus() == Response.Status.FORBIDDEN.getStatusCode()) {
                    authRetry = true;
                    firstRun = false;
                    cookie = getGenericUserCookie(true);
                    response.close();
                }
            }

            final int status = response.getStatus();
            if (isRedirectingStatus(status)) {
                final URI location = response.getLocation();
                if (location == null) {
                    LOG.warn("Snowstorm GET returned {} without Location for {}", status, currentUrl);
                    logSnowstormHttpComplete("GET", currentUrl, null, requestStartMs, status);
                    return response;
                }
                final URI resolved = URI.create(currentUrl).resolve(location);
                LOG.info("Snowstorm GET redirect {} -> {} (hop {})", currentUrl, resolved, redirectHop + 1);
                response.close();
                currentUrl = resolved.toString();
                continue;
            }

            logSnowstormHttpComplete("GET", currentUrl, null, requestStartMs, status);
            return response;
        }

        throw new LocalException("Exceeded " + SNOWSTORM_GET_MAX_REDIRECTS + " redirects for Snowstorm GET starting at " + url);
    }

    private static boolean isRedirectingStatus(final int status) {

        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
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
     * Downloads a file from the given URL. Uses Java HttpClient to bypass RESTEasy, which fails on binary responses when the server omits Content-Type or
     * returns a generic type. Follows redirects (e.g. prod proxies that 307 http→https); HttpClient defaults to NEVER.
     *
     * @param url The Snowstorm archive URL to download
     * @return The file content as an InputStream
     * @throws Exception on download failure
     */
    public static InputStream getFileDownload(final String url) throws Exception {

        final HttpRequest.Builder requestBuilder =
            HttpRequest.newBuilder().uri(URI.create(url)).header("Accept", "application/zip").header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_ACCECPT_LANGUAGES);
        final String basicHeader = snowstormBasicAuthorizationHeader();
        if (basicHeader != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, basicHeader);
        } else if (authMode == SnowstormAuthMode.COOKIE) {
            final String cookie = getGenericUserCookie(false);
            if (!cookie.isEmpty()) {
                requestBuilder.header("Cookie", cookie);
            }
        }
        final HttpRequest request = requestBuilder.GET().build();

        // NORMAL follows 3xx except https→http; needed for client Snowstorm proxies that redirect archive URLs
        final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        final HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            final String errorMsg = "Failed to download file. Status: " + response.statusCode() + " for " + response.uri();
            LOG.error(errorMsg);
            throw new LocalException(errorMsg);
        }

        return new ByteArrayInputStream(response.body());
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

        final long requestStartMs = System.currentTimeMillis();
        final Client client = getClient();
        final WebTarget target = client.target(url);
        final Builder builder = target.request(MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_ACCECPT_LANGUAGES);

        final String cookie = authMode == SnowstormAuthMode.COOKIE ? getGenericUserCookie(false) : "";
        applySnowstormAuthHeaders(builder, cookie);

        // Convert the JSON string to a Map so JacksonJsonProvider can serialize it as JSON
        final Map<String, Object> entityMap = jsonToMap(entity);

        // Send Map so JacksonJsonProvider can write application/json correctly
        final Response response = builder.post(Entity.entity(entityMap, MediaType.APPLICATION_JSON));

        logSnowstormHttpComplete("POST", url, entity, requestStartMs, response.getStatus());
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

        final Client client = getClient();
        final WebTarget target = client.target(url);
        final Builder builder = target.request(MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_ACCECPT_LANGUAGES);

        final String cookie = authMode == SnowstormAuthMode.COOKIE ? getGenericUserCookie(false) : "";
        applySnowstormAuthHeaders(builder, cookie);

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

        final Client client = getClient();
        final WebTarget target = client.target(url);
        final Builder builder = target.request(ACCEPT).header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_ACCECPT_LANGUAGES);

        final String cookie = authMode == SnowstormAuthMode.COOKIE ? getGenericUserCookie(false) : "";
        applySnowstormAuthHeaders(builder, cookie);

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

        if (authMode != SnowstormAuthMode.COOKIE) {
            return "";
        }
        if (isAuthUrlUnsetOrDisabled(authUrl)) {
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
        final Client client = getClient();
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
     * Creates a RESTEasy client with all providers needed for Snowstorm API (DELETE with body, JSON, binary). Uses ResteasyClientBuilderImpl to ensure RESTEasy
     * is used (not Jersey) when both are on the classpath.
     *
     * @param client the client
     * @return the client
     */
    public static Client configClient(final Client client) {

        client.register(ByteArrayProvider.class);
        client.register(JacksonJsonProvider.class);
        return client;
    }

    /**
     * Returns the shared RESTEasy client for Snowstorm calls. Created once, reused for all requests. Uses ResteasyClientBuilderImpl to ensure RESTEasy (with
     * DELETE+body support) is used when Jersey is also on the classpath.
     *
     * @return the client
     */
    public static Client getClient() {

        if (sharedClient == null) {
            synchronized (SnowstormConnection.class) {
                if (sharedClient == null) {
                    sharedClient = configClient(new ResteasyClientBuilderImpl().build());
                }
            }
        }
        return sharedClient;
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

        final long readStartMs = System.currentTimeMillis();
        try {
            final String body = response.readEntity(String.class);
            final long readMs = System.currentTimeMillis() - readStartMs;
            if (readMs > 100) {
                LOG.info("Snowstorm response body read {}ms ({} chars)", readMs, body != null ? body.length() : 0);
            }
            return body;
        } catch (Exception e) {
            LOG.error("Could not read response entity: {}", e.getMessage());
            throw new LocalException("Could not read response entity: " + e.getMessage());
        }
    }

    /**
     * Logs Snowstorm HTTP round-trip timing (status available when the client returns; body read is logged separately if slow).
     *
     * @param method HTTP method
     * @param url request URL
     * @param requestBody optional POST body
     * @param requestStartMs start timestamp
     * @param status HTTP status
     */
    private static void logSnowstormHttpComplete(final String method, final String url, final String requestBody, final long requestStartMs, final int status) {

        final long elapsedMs = System.currentTimeMillis() - requestStartMs;
        final String path = summarizeSnowstormPath(url);
        if (requestBody == null) {
            LOG.info("Snowstorm {} {}ms status={} path={}", method, elapsedMs, status, path);
            return;
        }
        if (requestBody.length() > 500) {
            LOG.info("Snowstorm {} {}ms status={} path={} bodyChars={} bodyPreview={}", method, elapsedMs, status, path, requestBody.length(),
                abbreviateForLog(requestBody, 200));
        } else {
            LOG.info("Snowstorm {} {}ms status={} path={} body={}", method, elapsedMs, status, path, requestBody);
        }
    }

    private static String summarizeSnowstormPath(final String url) {

        if (url == null) {
            return "";
        }
        final String base = restBaseUrl != null ? restBaseUrl : "";
        if (!base.isEmpty() && url.startsWith(base)) {
            return url.substring(base.length());
        }
        final int branchIdx = url.indexOf("/MAIN/");
        if (branchIdx >= 0) {
            return url.substring(branchIdx);
        }
        return abbreviateForLog(url, 120);
    }

    private static String abbreviateForLog(final String value, final int maxLen) {

        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    /**
     * Tries to read the response entity as a String without throwing. Use when handling error responses (e.g. 4xx) where the body may be closed or empty.
     *
     * @param response the response to read
     * @return the response body as a string, or null if the body could not be read
     */
    public static String readEntityAsStringSafe(Response response) {

        try {
            return response.readEntity(String.class);
        } catch (Exception e) {
            LOG.warn("Could not read response entity: {}", e.getMessage());
            return null;
        }
    }
}
