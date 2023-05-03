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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrowdClientAbstract {

    /** The logger. */
    private static final Logger logger = LoggerFactory.getLogger(CrowdClientAbstract.class);

    /** Base URL for the Crowd API including HTTPS */
    protected static String BASE_URL;

    /** User name for authentication to Crowd API */
    private static String USER_NAME;

    /** User's password for authentication to Crowd API */
    private static String PASSWORD;

    /** initialization of required params */
    static {
        // make sure Crowd URL includes the context root
        BASE_URL = StringUtils.trim(PropertyUtility.getProperty("crowd.baseUrl"));
        USER_NAME = StringUtils.trim(PropertyUtility.getProperty("crowd.username"));
        PASSWORD = StringUtils.trim(PropertyUtility.getProperty("crowd.password"));
    }

    /** The accept. */
    private static final String ACCEPT_DEFAULT = MediaType.APPLICATION_JSON;

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
     * Calls a Crowd URL and returns the response.
     *
     * @param url The Crowd URL to call
     * @return the response
     * @throws Exception the exception
     */
    protected static Response get(final String url) throws Exception {
        return get(url, ACCEPT_DEFAULT);
    }

    /**
     * Calls a Crowd URL and returns the response.
     *
     * @param url The Crowd URL to call
     * @return the response
     * @throws Exception the exception
     */
    protected static Response get(final String url, final String mediaType) throws Exception {

        try {
            final Client client = ClientBuilder.newClient();
            final HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(USER_NAME, PASSWORD);
            client.register(feature);
            final WebTarget target = client.target(url);

            logger.debug("CROWD API GET Url: {}", url);

            final Response response = target.request(mediaType).get();
            return response;

        } catch (Exception e) {
            logger.error("CROWD GET ERROR url: {} ", url, e);
            throw e;
        }
    }

    /**
     * HTTP Post.
     *
     * @param url URL to post.
     * @param entity Payload to post.
     * @return Response the response
     * @throws Exception the exception
     */
    protected static Response post(final String url, final String entity) throws Exception {

        try {
            final Client client = ClientBuilder.newClient();
            final HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(USER_NAME, PASSWORD);
            client.register(feature);
            final WebTarget target = client.target(url);
            final Builder builder = target.request(ACCEPT_DEFAULT);

            logger.debug("CROWD API POST Url: {}", url);

            final Response response = builder.post(Entity.json(entity));
            return response;

        } catch (Exception e) {
            logger.error("CROWD POST ERROR url: {}  entity: {}", url, entity, e);
            throw e;
        }
    }

    /**
     * HTTP Delete.
     *
     * @param url the url
     * @return the response
     * @throws Exception the exception
     */
    protected static Response delete(final String url) throws Exception {

        try {
            final Client client = ClientBuilder.newClient();
            final HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(USER_NAME, PASSWORD);
            client.register(feature);
            final WebTarget target = client.target(url);
            final Builder builder = target.request(ACCEPT_DEFAULT);

            logger.debug("CROWD API DELETE Url: {}", url);

            final Response response = builder.delete();

            return response;

        } catch (Exception e) {
            logger.error("CROWD DELETE ERROR url: {}", url, e);
            throw e;
        }
    }

    /**
     * Url Encode a string.
     *
     * @param toEncode the to encode
     * @return the string
     * @throws UnsupportedEncodingException
     */
    protected static String urlEncode(final String stringToEncode) throws UnsupportedEncodingException {

        if (StringUtils.isBlank(stringToEncode)) {
            return stringToEncode;
        }
        return URLEncoder.encode(stringToEncode, StandardCharsets.UTF_8.toString());
    }
}
