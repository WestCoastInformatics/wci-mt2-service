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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/**
 * Class to handle making calls to Snowstorm via JDK {@link HttpClient}.
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

    /**
     * Shared JDK client for Snowstorm JSON and archive downloads. NORMAL follows 3xx except https→http (needed for client proxies).
     */
    private static final HttpClient HTTP_CLIENT =
        HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(2)).followRedirects(HttpClient.Redirect.NORMAL).build();

    /** Has connection to Snowstorm. */
    private static boolean HAS_CONNECTION = false;

    /** The Constant CHECK_CONNECTION_INTERVAL. */
    private static final int CHECK_CONNECTION_INTERVAL = 60;

    /** The last connection check. */
    private static LocalDateTime LAST_CONNECTION_CHECK = LocalDateTime.now().minusSeconds(2 * CHECK_CONNECTION_INTERVAL);


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

    /**
     * Reload Snowstorm connection settings from {@link PropertyUtility}. Used by integration tests that inject credentials after the JVM starts.
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
     * Instantiates an empty {@link SnowstormConnection}.
     */
    private SnowstormConnection() {

        // n/a
    }

    /**
     * Check connection.
     *
     * @throws Exception the exception
     */
    public static void checkConnection() throws Exception {

        if (!hasConnection()) {
            LOG.error("Cannot connect to Snowstorm! Check if {} is offline.", getRestBaseUrl());
            throw new Exception("Cannot connect to Snowstorm.");
        }
    }

    /**
     * Checks for connection.
     *
     * @return true, if successful
     */
    private static boolean hasConnection() {

        if (LocalDateTime.now().minusSeconds(CHECK_CONNECTION_INTERVAL).isAfter(LAST_CONNECTION_CHECK)) {
            HAS_CONNECTION = getConnection();
            LAST_CONNECTION_CHECK = LocalDateTime.now();
        }
        return HAS_CONNECTION;
    }

    /**
     * Get connection status.
     *
     * @return true, if successful
     */
    private static boolean getConnection() {

        try {
            ensureConfigured();
            String cookie = "";
            if (authMode == SnowstormAuthMode.COOKIE) {
                cookie = getGenericUserCookie(false);
            }
            boolean firstRun = true;
            while (true) {
                final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(getRestBaseUrl() + "version"))
                    .timeout(Duration.ofSeconds(5)).header("Accept", ACCEPT).GET();
                applySnowstormAuthHeaders(requestBuilder, cookie);
                final SnowstormHttpResponse response =
                    SnowstormHttpResponse.from(HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()));
                if (firstRun && authMode == SnowstormAuthMode.COOKIE && response.getStatus() == Response.Status.FORBIDDEN.getStatusCode()) {
                    firstRun = false;
                    cookie = getGenericUserCookie(true);
                    continue;
                }
                return true;
            }
        } catch (final Exception e) {
            LOG.error("Error connecting to Snowstorm.", e);
            return false;
        }
    }

    /**
     * Unit tests must not call Snowstorm. Live {@code *IntegrationTest*} classes set {@code terminology.handler=SNOMED_SNOWSTORM} and are allowed.
     */
    private static void rejectRemoteCallFromUnitTest() {

        final String profiles = PropertyUtility.getProperty("springProfiles");
        if (profiles == null || !profiles.toLowerCase().contains("test")) {
            return;
        }
        final String handler = PropertyUtility.getProperty("terminology.handler");
        if ("SNOMED_SNOWSTORM".equals(handler)) {
            return;
        }
        throw new IllegalStateException("Unit tests must not call Snowstorm. terminology.handler=" + handler);
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
     * @param requestBuilder the request builder
     * @param sessionCookie the session cookie
     * @throws Exception the exception
     */
    private static void applySnowstormAuthHeaders(final HttpRequest.Builder requestBuilder, final String sessionCookie) throws Exception {

        final String basicHeader = snowstormBasicAuthorizationHeader();
        if (basicHeader != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, basicHeader);
        }
        if (sessionCookie != null && !sessionCookie.isEmpty()) {
            requestBuilder.header("Cookie", sessionCookie);
        }
    }

    /**
     * Returns the Snowstorm REST API base URL.
     *
     * @return the REST base url
     */
    public static String getRestBaseUrl() {

        if (restBaseUrl == null) {
            loadConfigurationFromProperties();
        }
        return restBaseUrl;
    }

    /**
     * Same as {@link #getRestBaseUrl()}; kept for call sites that use the older name.
     *
     * @return the REST base url
     */
    public static String getBaseUrl() {

        return getRestBaseUrl();
    }

    /**
     * Returns the base URL used for FHIR operations ({@code fhir/CodeSystem}, {@code fhir/ValueSet}, etc.). When not configured, this is the same as the REST
     * base URL.
     *
     * @return the FHIR base url
     */
    public static String getFhirBaseUrl() {

        if (restBaseUrl == null) {
            loadConfigurationFromProperties();
        }
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

        rejectRemoteCallFromUnitTest();
        ensureConfigured();
        final long requestStartMs = System.currentTimeMillis();
        String cookie = "";
        if (authMode == SnowstormAuthMode.COOKIE) {
            cookie = getGenericUserCookie(false);
        }
        boolean firstRun = true;
        while (true) {
            final HttpRequest.Builder requestBuilder =
                HttpRequest.newBuilder().uri(URI.create(url)).header("Accept", ACCEPT).header(HttpHeaders.ACCEPT_LANGUAGE, language).GET();
            applySnowstormAuthHeaders(requestBuilder, cookie);
            final SnowstormHttpResponse response = SnowstormHttpResponse.from(HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()));

            if (firstRun && authMode == SnowstormAuthMode.COOKIE && response.getStatus() == Response.Status.FORBIDDEN.getStatusCode()) {
                firstRun = false;
                cookie = getGenericUserCookie(true);
                continue;
            }

            logSnowstormHttpComplete("GET", url, null, requestStartMs, response.getStatus());
            return response;
        }
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
     * Downloads a file from the given URL. Uses Java HttpClient for binary responses. Follows redirects (e.g. prod proxies that 307 http→https).
     *
     * @param url The Snowstorm archive URL to download
     * @return The file content as an InputStream
     * @throws Exception on download failure
     */
    public static InputStream getFileDownload(final String url) throws Exception {

        rejectRemoteCallFromUnitTest();
        ensureConfigured();
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
        final HttpResponse<InputStream> response = HTTP_CLIENT.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        final InputStream body = response.body();

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (body != null) {
                body.close();
            }
            final String errorMsg = "Failed to download file. Status: " + response.statusCode() + " for " + response.uri();
            LOG.error(errorMsg);
            throw new LocalException(errorMsg);
        }

        return body;
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

        rejectRemoteCallFromUnitTest();
        ensureConfigured();
        final long requestStartMs = System.currentTimeMillis();
        final String body = entity == null ? "" : entity;
        final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url)).header("Accept", ACCEPT)
            .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_ACCECPT_LANGUAGES).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        final String cookie = authMode == SnowstormAuthMode.COOKIE ? getGenericUserCookie(false) : "";
        applySnowstormAuthHeaders(requestBuilder, cookie);
        final SnowstormHttpResponse response = SnowstormHttpResponse.from(HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()));
        logSnowstormHttpComplete("POST", url, entity, requestStartMs, response.getStatus());
        return response;
    }

    /**
     * Put response.
     *
     * @param url the url
     * @param entity the entity
     * @return the response
     * @throws Exception the exception
     */
    public static Response putResponse(final String url, final String entity) throws Exception {

        rejectRemoteCallFromUnitTest();
        ensureConfigured();
        final String body = entity == null ? "" : entity;
        final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url)).header("Accept", ACCEPT)
            .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_ACCECPT_LANGUAGES).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        final String cookie = authMode == SnowstormAuthMode.COOKIE ? getGenericUserCookie(false) : "";
        applySnowstormAuthHeaders(requestBuilder, cookie);
        return SnowstormHttpResponse.from(HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()));
    }

    /**
     * Calls a Snowstorm DELETE URL and returns the response. Supports DELETE with a JSON body (member bulk remove).
     *
     * @param url The Snowstorm URL to call
     * @param entity the entity
     * @return The Snowstorm response
     * @throws Exception the exception
     */
    public static Response deleteResponse(final String url, final String entity) throws Exception {

        rejectRemoteCallFromUnitTest();
        ensureConfigured();
        final HttpRequest.Builder requestBuilder =
            HttpRequest.newBuilder().uri(URI.create(url)).header("Accept", ACCEPT).header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_ACCECPT_LANGUAGES);
        if (entity == null) {
            requestBuilder.DELETE();
        } else {
            requestBuilder.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).method("DELETE",
                HttpRequest.BodyPublishers.ofString(entity, StandardCharsets.UTF_8));
        }
        final String cookie = authMode == SnowstormAuthMode.COOKIE ? getGenericUserCookie(false) : "";
        applySnowstormAuthHeaders(requestBuilder, cookie);
        return SnowstormHttpResponse.from(HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()));
    }

    /**
     * Gets the generic user cookie.
     *
     * @param forceReload the force reload
     * @return the generic user cookie
     * @throws Exception the exception
     */
    public static String getGenericUserCookie(final boolean forceReload) throws Exception {

        rejectRemoteCallFromUnitTest();
        ensureConfigured();
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
        final Map<String, String> loginData = new HashMap<>();
        loginData.put("login", userName);
        loginData.put("password", password);
        final String loginJson = ThreadLocalMapper.get().writeValueAsString(loginData);
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(authUrl + "authenticate")).header("Accept", MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).POST(HttpRequest.BodyPublishers.ofString(loginJson, StandardCharsets.UTF_8)).build();

        try {
            final SnowstormHttpResponse response = SnowstormHttpResponse.from(HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()));

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new LocalException("Authentication of generic user failed. " + " Status: " + Integer.toString(response.getStatus()) + ". Error: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            final String cookieHeader = response.toCookieHeader();
            if (cookieHeader.isEmpty()) {
                throw new LocalException("Authentication of generic user failed. No Set-Cookie headers returned.");
            }
            genericUserCookie = cookieHeader.endsWith(";") ? cookieHeader : cookieHeader + ";";

        } catch (final LocalException e) {
            throw e;
        } catch (final Exception e) {
            LOG.error("Authentication of generic user failed. {}", e.getMessage(), e);
            throw new LocalException("Authentication of generic user failed. " + e.getMessage());
        }

        return genericUserCookie;
    }

    /**
     * Ensure configured.
     */
    private static void ensureConfigured() {

        if (restBaseUrl == null || authMode == null) {
            loadConfigurationFromProperties();
        }
    }

    /**
     * Helper method to safely read a response entity as a String.
     *
     * @param response the response to read
     * @return the response body as a string
     * @throws Exception if there is an error reading the response
     */
    public static String readEntityAsString(final Response response) throws Exception {

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
    public static String readEntityAsStringSafe(final Response response) {

        try {
            return response.readEntity(String.class);
        } catch (Exception e) {
            LOG.warn("Could not read response entity: {}", e.getMessage());
            return null;
        }
    }
}
