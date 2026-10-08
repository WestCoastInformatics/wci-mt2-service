/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * HTTP client for the automap mapping task API.
 */
public final class AutomapClient {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(AutomapClient.class);

    /** Default automap base URL. */
    static final String DEFAULT_BASE_URL = "https://automap.terminology.tools";

    /** Login path. */
    private static final String AUTH_PATH = "/auth/token";

    /** Mapping task path. */
    private static final String TASK_PATH = "/api/v1/mapping/task";

    /** How long to wait for a connection. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);

    /** How long to wait for a mapping batch. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);

    /** Refresh the cached token this long before it expires. */
    private static final long TOKEN_EXPIRY_BUFFER_MS = 30_000L;

    /** Values below this are treated as seconds rather than milliseconds. */
    private static final long EXPIRES_IN_SECONDS_THRESHOLD = 1_000_000L;

    /** Epoch values below this are seconds rather than milliseconds. */
    private static final long EPOCH_MILLIS_THRESHOLD = 1_000_000_000_000L;

    /** Shared HTTP client. */
    private static final HttpClient HTTP_CLIENT =
        HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();

    /** Guards the cached access token. */
    private static final Object TOKEN_LOCK = new Object();

    /** Cached bearer token. */
    private static String accessToken;

    /** Epoch millis when the cached token should be refreshed. */
    private static long accessTokenExpiresAtMs;

    /**
     * Instantiates a new automap client.
     */
    private AutomapClient() {

        // n/a
    }

    /**
     * Submits one batch of terms and returns the automap task payload.
     *
     * @param terms the terms
     * @param minConfidence the minimum confidence
     * @return the task response
     * @throws Exception the exception
     */
    public static JsonNode mapTerms(final List<AutomapTerm> terms, final double minConfidence) throws Exception {

        final String body = buildTaskJson(terms, minConfidence);
        String token = accessToken();
        HttpResponse<String> response = post(baseUrl() + TASK_PATH, body, token);
        if (response.statusCode() == HttpStatus.UNAUTHORIZED.value() && token != null) {
            clearAccessToken();
            token = accessToken();
            response = post(baseUrl() + TASK_PATH, body, token);
        }
        if (response.statusCode() == HttpStatus.UNAUTHORIZED.value() || response.statusCode() == HttpStatus.FORBIDDEN.value()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Automap rejected the request. Set automap.username and automap.password when the service requires a login.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Automap mapping task failed. Status " + response.statusCode() + ". " + abbreviate(response.body()));
        }
        final JsonNode task = ThreadLocalMapper.get().readTree(response.body());
        if (task != null && "error".equalsIgnoreCase(task.path("status").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Automap mapping task failed. " + abbreviate(response.body()));
        }
        return task;
    }

    /**
     * Builds the automap task JSON.
     *
     * @param terms the terms
     * @param minConfidence the minimum confidence
     * @return the JSON body
     * @throws Exception the exception
     */
    public static String buildTaskJson(final List<AutomapTerm> terms, final double minConfidence) throws Exception {

        final ObjectNode task = ThreadLocalMapper.get().createObjectNode();
        final ArrayNode termNodes = task.putArray("terms");
        if (terms != null) {
            for (final AutomapTerm term : terms) {
                final ObjectNode termNode = termNodes.addObject();
                termNode.put("term", term.getTerm());
                termNode.put("toTerminology", term.getToTerminology());
                termNode.put("entityType", term.getEntityType());
            }
        }
        task.put("minConfidence", minConfidence);
        return ThreadLocalMapper.get().writeValueAsString(task);
    }

    /**
     * Returns a configured property, or null when it is blank or the placeholder none.
     *
     * @param key the property key
     * @return the value, or null
     */
    static String configured(final String key) {

        final String value = PropertyUtility.getProperty(key);
        if (StringUtils.isBlank(value) || "none".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    /**
     * Returns the automap base URL without a trailing slash.
     *
     * @return the base URL
     */
    static String baseUrl() {

        final String configured = configured("automap.baseUrl");
        final String base = configured == null ? DEFAULT_BASE_URL : configured;
        return StringUtils.removeEnd(base, "/");
    }

    /**
     * Returns a cached bearer token, or null when credentials are not configured.
     *
     * @return the access token, or null
     * @throws Exception the exception
     */
    private static String accessToken() throws Exception {

        final String username = configured("automap.username");
        final String password = configured("automap.password");
        if (username == null || password == null) {
            return null;
        }
        synchronized (TOKEN_LOCK) {
            if (StringUtils.isNotBlank(accessToken) && System.currentTimeMillis() < accessTokenExpiresAtMs) {
                return accessToken;
            }
            final ObjectNode authBody = ThreadLocalMapper.get().createObjectNode();
            authBody.put("grant_type", "username_password");
            authBody.put("username", username);
            authBody.put("password", password);
            final HttpResponse<String> response = post(baseUrl() + AUTH_PATH, ThreadLocalMapper.get().writeValueAsString(authBody), null);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Automap login failed. Status " + response.statusCode() + ". " + abbreviate(response.body()));
            }
            final JsonNode auth = ThreadLocalMapper.get().readTree(response.body());
            final String token = auth.path("access_token").asText(null);
            if (StringUtils.isBlank(token)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Automap login did not return an access token.");
            }
            accessToken = token;
            accessTokenExpiresAtMs = expiresAt(auth);
            return accessToken;
        }
    }

    /**
     * Clears the cached access token.
     */
    private static void clearAccessToken() {

        synchronized (TOKEN_LOCK) {
            accessToken = null;
            accessTokenExpiresAtMs = 0L;
        }
    }

    /**
     * Resolves when a token should be refreshed.
     *
     * @param auth the auth response
     * @return epoch millis
     */
    static long expiresAt(final JsonNode auth) {

        if (auth != null && auth.hasNonNull("expires_on")) {
            long expiresOn = auth.get("expires_on").asLong();
            if (expiresOn > 0 && expiresOn < EPOCH_MILLIS_THRESHOLD) {
                expiresOn = expiresOn * 1000L;
            }
            return Math.max(0L, expiresOn - TOKEN_EXPIRY_BUFFER_MS);
        }
        long expiresIn = auth == null || !auth.hasNonNull("expires_in") ? 3_600_000L : auth.get("expires_in").asLong();
        if (expiresIn > 0 && expiresIn < EXPIRES_IN_SECONDS_THRESHOLD) {
            expiresIn = expiresIn * 1000L;
        }
        return System.currentTimeMillis() + Math.max(0L, expiresIn - TOKEN_EXPIRY_BUFFER_MS);
    }

    /**
     * Posts JSON.
     *
     * @param url the url
     * @param body the body
     * @param token the bearer token, or null
     * @return the response
     * @throws Exception the exception
     */
    private static HttpResponse<String> post(final String url, final String body, final String token) throws Exception {

        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (StringUtils.isNotBlank(token)) {
            request.header("Authorization", "Bearer " + token);
        }
        LOG.info("POST {} bytes={}", url, body == null ? 0 : body.length());
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Shortens a response body for an error message.
     *
     * @param body the body
     * @return the shortened body
     */
    private static String abbreviate(final String body) {

        if (StringUtils.isBlank(body)) {
            return "";
        }
        final String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }
}
