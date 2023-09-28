/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
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

/**
 * Class to handle making calls to Snowstorm.
 */
public final class SnowstormConnection {

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

        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(url);
        String cookie = getGenericUserCookie(false);
        Response response = null;
        boolean firstRun = true;
        boolean run = true;

        while (run) {

            run = false;

            response = target.request(ACCEPT).header("Accept-Language", language).header("Cookie", cookie).get();

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
    public static InputStream getFileDownload(final String url) throws Exception {

        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(url);
        final Response response =
            target.request("application/zip").header("Accept-Language", DEFAULT_ACCECPT_LANGUAGES).header("Cookie", getGenericUserCookie(false)).get();

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

        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(url);
        final Builder builder =
            target.request(MediaType.APPLICATION_JSON).header("Accept-Language", DEFAULT_ACCECPT_LANGUAGES).header("Cookie", getGenericUserCookie(false));

        final Response response = builder.post(Entity.json(entity));

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

        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(url);
        final Builder builder =
            target.request(MediaType.APPLICATION_JSON).header("Accept-Language", DEFAULT_ACCECPT_LANGUAGES).header("Cookie", getGenericUserCookie(false));

        final Response response = builder.put(Entity.json(entity));

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

        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(url);
        Response response;

        if (entity == null) {

            response = target.request(ACCEPT).header("Accept-Language", DEFAULT_ACCECPT_LANGUAGES).header("Cookie", getGenericUserCookie(false)).delete();
        } else {

            response = target.request(ACCEPT).header("Accept-Language", DEFAULT_ACCECPT_LANGUAGES).header("Cookie", getGenericUserCookie(false))
                .build("DELETE", Entity.entity(entity, MediaType.APPLICATION_JSON_TYPE)).invoke(Response.class);
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
        if (authUrl.equals("none")) {
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
        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(authUrl + "authenticate");
        final Builder builder = target.request(MediaType.APPLICATION_JSON);

        try (Response response = builder.post(Entity.json("{ \"login\": \"" + userName + "\", \"password\": \"" + password + "\" }"))) {

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
        }

        return genericUserCookie;
    }
}
