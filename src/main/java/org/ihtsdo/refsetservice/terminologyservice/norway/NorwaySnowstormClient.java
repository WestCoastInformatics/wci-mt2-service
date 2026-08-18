/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice.norway;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP client for Norway Snowstorm used by Norway reports. Base URL comes from {@code norway.snowstorm.url}.
 */
public final class NorwaySnowstormClient implements AutoCloseable {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(NorwaySnowstormClient.class);

    /** Default page size used by legacy reports. */
    public static final int DEFAULT_LIMIT = 10000;

    /** Default Norway Snowstorm base URL. */
    private static final String DEFAULT_BASE_URL =
        "https://dailybuild.terminologi.helsedirektoratet.no/snowstorm/snomed-ct";

    /** Max attempts for a single GET (initial try + retries). */
    private static final int MAX_ATTEMPTS = 5;

    /** JSON mapper. */
    private final ObjectMapper mapper = new ObjectMapper();

    /** HTTP client. */
    private final HttpClient httpClient;

    /** Configured base URL (no trailing slash). */
    private final String baseUrl;

    /**
     * Instantiates a new Norway snowstorm client.
     */
    public NorwaySnowstormClient() {

        this.baseUrl = resolveBaseUrl();
        // Long-running paged reports can hit transient connect stalls against the Norway daily build.
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(2)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /**
     * Returns the configured Snowstorm base URL.
     *
     * @return the base URL
     */
    public String getBaseUrl() {

        return baseUrl;
    }

    /**
     * Performs a GET and returns the parsed JSON body. Retries transient connect/timeout/5xx failures.
     *
     * @param absoluteOrRelativeUrl absolute URL, or path beginning with {@code /} relative to the base URL
     * @return JSON document
     * @throws Exception on HTTP or parse failure
     */
    public JsonNode getJson(final String absoluteOrRelativeUrl) throws Exception {

        final String url = absoluteOrRelativeUrl.startsWith("http") ? absoluteOrRelativeUrl : baseUrl + absoluteOrRelativeUrl;
        LOG.info(url);

        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(10)).header("Accept", "application/json").GET()
            .build();

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                final String body = response.body() == null ? "" : response.body();
                final int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return mapper.readTree(body);
                }
                if (isRetryableStatus(status) && attempt < MAX_ATTEMPTS) {
                    LOG.warn("Retryable HTTP {} from Norway Snowstorm (attempt {}/{}): {}", status, attempt, MAX_ATTEMPTS, url);
                    sleepBeforeRetry(attempt);
                    continue;
                }
                throw new LocalException("Unexpected terminology server failure. Message = " + body);
            } catch (final IOException e) {
                lastFailure = e;
                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }
                final String kind = (e instanceof HttpTimeoutException) ? "Timeout" : "IO failure";
                LOG.warn("{} talking to Norway Snowstorm (attempt {}/{}): {}", kind, attempt, MAX_ATTEMPTS, e.toString());
                sleepBeforeRetry(attempt);
            }
        }
        throw new LocalException("Norway Snowstorm request failed after " + MAX_ATTEMPTS + " attempts: " + url, lastFailure);
    }

    /**
     * URL-encodes a searchAfter token for query strings.
     *
     * @param searchAfter the raw searchAfter value
     * @return encoded value, or null if input is blank
     */
    public static String encodeSearchAfter(final String searchAfter) {

        if (StringUtils.isBlank(searchAfter)) {
            return null;
        }
        return URLEncoder.encode(searchAfter, StandardCharsets.UTF_8);
    }

    /**
     * URL-encodes branch path separators for Snowstorm paths.
     *
     * @param branchPath the branch path
     * @return encoded path
     */
    public static String encodeBranch(final String branchPath) {

        return branchPath.replace("/", "%2F");
    }

    /* see superclass */
    @Override
    public void close() {

        // HttpClient does not require explicit close
    }

    /**
     * Indicates whether an HTTP status should be retried.
     *
     * @param status the status
     * @return true if retryable
     */
    private static boolean isRetryableStatus(final int status) {

        return status == 408 || status == 425 || status == 429 || status == 502 || status == 503 || status == 504;
    }

    /**
     * Backs off before the next retry attempt.
     *
     * @param attempt current attempt number (1-based)
     * @throws InterruptedException if interrupted
     */
    private static void sleepBeforeRetry(final int attempt) throws InterruptedException {

        final long delayMs = Math.min(30_000L, 2_000L * attempt);
        Thread.sleep(delayMs);
    }

    /**
     * Resolves Norway Snowstorm base URL from config.
     *
     * @return base URL without trailing slash
     */
    private static String resolveBaseUrl() {

        String configured = null;
        try {
            configured = PropertyUtility.getProperty("norway.snowstorm.url");
        } catch (final Exception e) {
            LOG.debug("norway.snowstorm.url not configured, using default", e);
        }
        if (StringUtils.isBlank(configured) || "none".equalsIgnoreCase(configured)) {
            return DEFAULT_BASE_URL;
        }
        return StringUtils.removeEnd(configured.trim(), "/");
    }
}
