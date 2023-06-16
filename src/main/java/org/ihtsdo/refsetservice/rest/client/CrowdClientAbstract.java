/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.client;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class CrowdClientAbstract.
 */
public abstract class CrowdClientAbstract {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(CrowdClientAbstract.class);

    /** Base URL for the Crowd API including HTTPS. */
    private static String baseUrl;

    /** User name for authentication to Crowd API. */
    private static String username;

    /** User's password for authentication to Crowd API. */
    private static String password;

    /** initialization of required params */
    static {
        // make sure Crowd URL includes the context root
        baseUrl = StringUtils.trim(PropertyUtility.getProperty("crowd.baseUrl"));
        username = StringUtils.trim(PropertyUtility.getProperty("crowd.username"));
        password = StringUtils.trim(PropertyUtility.getProperty("crowd.password"));
    }

    /** The accept. */
    private static final String ACCEPT_DEFAULT = MediaType.APPLICATION_JSON;

    /** The Constant ROLES. */
    @SuppressWarnings("serial")
    protected static final Set<String> ROLES = new HashSet<String>() {

        {
            add("admin");
            add("author");
            add("reviewer");
            add("viewer");
        }
    };

    /**
     * Returns the base url.
     *
     * @return the base url
     */
    protected static String getBaseUrl() {

        return baseUrl;
    }

    /**
     * Calls a Crowd URL and returns the response.
     *
     * @param url the url
     * @return the string
     * @throws Exception the exception
     */

    protected static String get(final String url) throws Exception {

        final HttpClient httpClient = HttpClient.newBuilder().build();
        final HttpRequest request =
            HttpRequest.newBuilder().uri(URI.create(url)).GET().header("Authorization", getBasicAuthHeader()).header("Accept", ACCEPT_DEFAULT).build();

        LOG.debug("CROWD API GET Url: {}", url);

        final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

        if (response.statusCode() == 200) {

            return response.body();

        }

        LOG.error("CROWD GET ERROR url: {} : response code: {}", url, response.statusCode());
        throw new Exception("CROWD GET ERROR url: " + url);

    }

    /**
     * HTTP Post.
     *
     * @param url the url
     * @param entity the entity
     * @return the int HTTP status code
     * @throws Exception the exception
     */
    protected static int post(final String url, final String entity) throws Exception {

        final HttpClient httpClient = HttpClient.newBuilder().build();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(entity))
            .header("Authorization", getBasicAuthHeader()).header("Content-Type", ACCEPT_DEFAULT).build();

        LOG.debug("CROWD API POST Url: {}", url);
        final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

        return response.statusCode();

    }

    /**
     * HTTP Delete.
     *
     * @param url the url
     * @return the int HTTP status code
     * @throws Exception the exception
     */
    protected static int delete(final String url) throws Exception {

        final HttpClient httpClient = HttpClient.newBuilder().build();
        final HttpRequest request =
            HttpRequest.newBuilder().uri(URI.create(url)).DELETE().header("Authorization", getBasicAuthHeader()).header("Accept", ACCEPT_DEFAULT).build();

        LOG.debug("CROWD API DELETE Url: {}", url);

        final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

        return response.statusCode();

    }

    /**
     * Url Encode a string.
     *
     * @param stringToEncode the string to encode
     * @return the string
     * @throws UnsupportedEncodingException the unsupported encoding exception
     */
    protected static String urlEncode(final String stringToEncode) throws UnsupportedEncodingException {

        if (StringUtils.isBlank(stringToEncode)) {
            return stringToEncode;
        }
        return URLEncoder.encode(stringToEncode, StandardCharsets.UTF_8.toString());
    }

    /**
     * Returns the basic auth header.
     *
     * @return the basic auth header
     */
    protected static String getBasicAuthHeader() {

        final String auth = username + ":" + password;
        final byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        final String headerValue = "Basic " + new String(encodedAuth, StandardCharsets.UTF_8);
        return headerValue;
    }
}
