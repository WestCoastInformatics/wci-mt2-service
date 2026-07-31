/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.handler.EntraIDSecurityServiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Stateless helpers for the OAuth2 authorization code flow against Microsoft Entra ID: token exchange and authorize URL construction. Not a Spring bean; all
 * entry points are static.
 */
public final class EntraAuthorizationCodeExchange {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(EntraAuthorizationCodeExchange.class);

    /** Shared HTTP client for token endpoint calls (connection timeout only; each request sets its own timeout). */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    /**
     * Prevents instantiation; use static methods only.
     */
    private EntraAuthorizationCodeExchange() {

    }

    /**
     * Exchanges an authorization code for tokens by POSTing to the Entra token endpoint ({@code grant_type=authorization_code}). Parses the JSON body and
     * returns the {@code id_token} when present (preferred for validation with app audience); otherwise returns {@code access_token}.
     *
     * @param tokenEndpoint Entra OAuth2 token URL (v2.0)
     * @param clientId registered application (client) id
     * @param clientSecret confidential client secret
     * @param code authorization code from the redirect query string
     * @param redirectUri exact redirect URI used in the authorize request and registered in Entra
     * @return JWT string suitable for {@link EntraIDSecurityServiceHandler#authenticateWithBearerToken(String)}
     * @throws Exception if the HTTP client fails or JSON parsing fails
     * @throws IllegalArgumentException if any required parameter is null or blank
     * @throws IllegalStateException if HTTP status is not 2xx, the body contains {@code error}, or neither token field is present
     */
    public static String exchangeCodeForJwt(final String tokenEndpoint, final String clientId, final String clientSecret, final String code,
        final String redirectUri) throws Exception {

        if (StringUtils.isAnyBlank(tokenEndpoint, clientId, clientSecret, code, redirectUri)) {
            throw new IllegalArgumentException("token exchange: missing required parameter");
        }

        final String form = String.join("&", "grant_type=" + urlEncode("authorization_code"), "client_id=" + urlEncode(clientId),
            "client_secret=" + urlEncode(clientSecret), "code=" + urlEncode(code), "redirect_uri=" + urlEncode(redirectUri));

        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(tokenEndpoint)).timeout(Duration.ofSeconds(30))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(form)).build();

        LOG.debug("Entra token exchange: POST {}", tokenEndpoint);

        final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        final String body = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOG.warn("Entra token exchange failed HTTP {} body (truncated): {}", response.statusCode(),
                body != null && body.length() > 500 ? body.substring(0, 500) + "…" : body);
            throw new IllegalStateException("Entra token endpoint returned HTTP " + response.statusCode());
        }

        final JsonNode root = ThreadLocalMapper.get().readTree(body);
        final String error = text(root, "error");
        if (StringUtils.isNotBlank(error)) {
            final String desc = text(root, "error_description");
            LOG.warn("Entra token exchange error={} description={}", error, desc);
            throw new IllegalStateException("Entra token error: " + error);
        }

        final String idToken = text(root, "id_token");
        if (StringUtils.isNotBlank(idToken)) {
            return idToken;
        }
        final String accessToken = text(root, "access_token");
        if (StringUtils.isNotBlank(accessToken)) {
            return accessToken;
        }
        throw new IllegalStateException("Entra token response contained neither id_token nor access_token");
    }

    /**
     * Reads a string field from a JSON object node; returns {@code null} if the field is missing or JSON null.
     *
     * @param root parsed token response (or error) object
     * @param field property name (e.g. {@code id_token}, {@code error})
     * @return the text value, or {@code null}
     */
    private static String text(final JsonNode root, final String field) {

        final JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        return n.asText(null);
    }

    /**
     * URL-encodes a value for {@code application/x-www-form-urlencoded} query or form bodies (UTF-8).
     *
     * @param s raw string (must not be {@code null})
     * @return encoded string per {@link URLEncoder#encode(String, java.nio.charset.Charset)}
     */
    private static String urlEncode(final String s) {

        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * Builds the Entra OAuth2 authorize URL for a browser GET redirect (response type {@code code}, response mode {@code query}).
     *
     * @param authorizationEndpoint Entra v2.0 authorize endpoint URL
     * @param clientId registered application (client) id
     * @param redirectUri registered redirect URI (must match token exchange and app registration)
     * @param scope space-separated OAuth scopes (e.g. {@code openid profile email})
     * @param state opaque CSRF value to validate on callback
     * @return absolute URL with query parameters (values percent-encoded)
     */
    public static String buildAuthorizeUrl(final String authorizationEndpoint, final String clientId, final String redirectUri, final String scope,
        final String state) {

        return authorizationEndpoint + "?client_id=" + urlEncode(clientId) + "&response_type=code" + "&redirect_uri=" + urlEncode(redirectUri) + "&scope="
            + urlEncode(scope) + "&state=" + urlEncode(state) + "&response_mode=query";
    }

    /**
     * Builds the Entra OIDC logout (end-session) URL for a browser GET redirect.
     *
     * @param logoutEndpoint Entra v2.0 logout endpoint URL (may already include a query string)
     * @param postLogoutRedirectUri optional registered post-logout redirect URI; blank or {@code none} omits redirect params
     * @param clientId optional application (client) id; appended when post-logout redirect is present (helps Entra honor the redirect)
     * @return logout endpoint, optionally with {@code post_logout_redirect_uri} and {@code client_id} percent-encoded
     * @throws IllegalArgumentException if {@code logoutEndpoint} is null or blank
     */
    public static String buildLogoutUrl(final String logoutEndpoint, final String postLogoutRedirectUri, final String clientId) {

        if (StringUtils.isBlank(logoutEndpoint)) {
            throw new IllegalArgumentException("logout endpoint is required");
        }
        if (StringUtils.isBlank(postLogoutRedirectUri) || "none".equalsIgnoreCase(postLogoutRedirectUri.trim())) {
            return logoutEndpoint.trim();
        }
        String url = logoutEndpoint.trim();
        final String sep = url.contains("?") ? "&" : "?";
        url = url + sep + "post_logout_redirect_uri=" + urlEncode(postLogoutRedirectUri.trim());
        if (StringUtils.isNotBlank(clientId) && !"none".equalsIgnoreCase(clientId.trim())) {
            url = url + "&client_id=" + urlEncode(clientId.trim());
        }
        return url;
    }

    /**
     * Builds logout URL without {@code client_id} (tests / callers that only need the redirect param).
     *
     * @param logoutEndpoint Entra logout endpoint
     * @param postLogoutRedirectUri optional post-logout redirect URI
     * @return see {@link #buildLogoutUrl(String, String, String)}
     */
    public static String buildLogoutUrl(final String logoutEndpoint, final String postLogoutRedirectUri) {

        return buildLogoutUrl(logoutEndpoint, postLogoutRedirectUri, null);
    }
}
