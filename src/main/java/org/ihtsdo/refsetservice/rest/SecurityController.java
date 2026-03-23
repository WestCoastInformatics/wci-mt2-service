/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.handler.EntraAuthorizationCodeExchange;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Controller for authentication and user end points.
 * 
 * @author Nuno
 *
 */
@Hidden
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class SecurityController extends BaseController {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(SecurityController.class);

    /** Cryptographic RNG for OAuth2 {@code state} values on {@link #entraOAuthLogin(HttpServletRequest)}. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /**
     * Returns the user.
     *
     * @param userName the user name
     * @param request the request
     * @return the user
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.POST, value = "/authenticate/{userName}")
    @Operation(summary = "Authorize the user. Requires logging in to IMS first and sending the appropriate cookie", tags = {
        "security"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successful authorization, payload contains user object"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @Parameters({
        @Parameter(name = "userName", description = "User name to authenicate", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<User> authenticate(@PathVariable(value = "userName") final String userName, final HttpServletRequest request)
        throws Exception {

        try (final SecurityService securityService = new SecurityService()) {

            final User user = securityService.authenticate(userName);

            if (user == null || user.getAuthToken() == null) {
                throw new Exception("Unable to authenticate user");
            }

            request.getSession().setAttribute(SecurityService.SESSION_USER_OBJECT_KEY, user);
            return new ResponseEntity<>(user, new HttpHeaders(), HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * OAuth2 redirect target after Entra sign-in: exchanges {@code code} for tokens, validates the JWT, runs {@link SecurityService#authenticateWithEntraBearerToken(String)},
     * stores the {@link User} in the HTTP session, clears OAuth {@code state} from the session, and redirects the browser to the post-login URL with a query outcome
     * ({@code entra_login=success} or {@code entra_error=...}).
     *
     * @param code authorization code from Entra (query); required unless {@code error} is set
     * @param error Entra error code when authorization failed
     * @param errorDescription optional human-readable error detail from Entra
     * @param state OAuth2 state; must equal session {@link SecurityService#SESSION_ENTRA_OAUTH_STATE_KEY} when login was started via {@code GET /authenticate/login}
     * @param httpRequest current request (session and servlet context)
     * @return HTTP 302 {@code Location} to the resolved post-login base URL with an appended query flag (never returns a body)
     * @throws Exception rethrown from services if an unexpected failure escapes (normally errors yield a redirect with {@code entra_error})
     */
    @GetMapping(value = "/authenticate/callback")
    public ResponseEntity<Void> entraOAuthCallback(@RequestParam(required = false) final String code,
        @RequestParam(name = "error", required = false) final String error,
        @RequestParam(name = "error_description", required = false) final String errorDescription, @RequestParam(required = false) final String state,
        final HttpServletRequest httpRequest) throws Exception {

        final String postLogin = resolvePostLoginRedirectUrl();
        if (!"ENTRAID".equalsIgnoreCase(StringUtils.trimToEmpty(PropertyUtility.getProperty("security.handler")))) {
            LOG.warn("entraOAuthCallback invoked but security.handler is not ENTRAID");
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appendQuery(postLogin, "entra_callback=disabled"))).build();
        }

        if (StringUtils.isNotBlank(error)) {
            LOG.warn("Entra OAuth error={} description={}", error, errorDescription);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appendQuery(postLogin, "entra_error=" + urlEncode(error)))).build();
        }

        if (StringUtils.isBlank(code)) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appendQuery(postLogin, "entra_error=missing_code"))).build();
        }

        final Object expectedState =
            httpRequest.getSession(false) != null ? httpRequest.getSession(false).getAttribute(SecurityService.SESSION_ENTRA_OAUTH_STATE_KEY) : null;
        if (expectedState != null) {
            if (!StringUtils.equals(String.valueOf(expectedState), state)) {
                LOG.warn("Entra OAuth state mismatch");
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appendQuery(postLogin, "entra_error=state_mismatch"))).build();
            }
        }

        final String redirectUri = PropertyUtility.getProperty("security.handler.ENTRAID.redirect.uri");
        if (StringUtils.isBlank(redirectUri) || "none".equalsIgnoreCase(redirectUri)) {
            LOG.error("security.handler.ENTRAID.redirect.uri is not configured (required for OAuth callback)");
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appendQuery(postLogin, "entra_error=config"))).build();
        }

        final String tokenEndpoint = PropertyUtility.getProperty("security.handler.ENTRAID.token.endpoint");
        final String clientId = PropertyUtility.getProperty("security.handler.ENTRAID.client.id");
        final String clientSecret = PropertyUtility.getProperty("security.handler.ENTRAID.client.secret");
        if (StringUtils.isAnyBlank(tokenEndpoint, clientId, clientSecret) || "none".equalsIgnoreCase(clientId)) {
            LOG.error("Entra token endpoint, client id, or secret not configured");
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appendQuery(postLogin, "entra_error=config"))).build();
        }

        final String jwtFromEntra;
        try {
            jwtFromEntra = EntraAuthorizationCodeExchange.exchangeCodeForJwt(tokenEndpoint, clientId, clientSecret, code.trim(), redirectUri.trim());
        } catch (final Exception ex) {
            LOG.warn("Entra authorization code exchange failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appendQuery(postLogin, "entra_error=token_exchange"))).build();
        }

        try (final SecurityService securityService = new SecurityService()) {
            final User user = securityService.authenticateWithEntraBearerToken(jwtFromEntra);
            if (user == null || user.getAuthToken() == null) {
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appendQuery(postLogin, "entra_error=no_user"))).build();
            }
            httpRequest.getSession(true).setAttribute(SecurityService.SESSION_USER_OBJECT_KEY, user);
            httpRequest.getSession().removeAttribute(SecurityService.SESSION_ENTRA_OAUTH_STATE_KEY);
            LOG.info("Entra OAuth callback: session established for user={}", user.getUserName());
        } catch (final Exception ex) {
            LOG.warn("Entra OAuth callback login failed: {}", ex.toString());
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appendQuery(postLogin, "entra_error=login"))).build();
        }

        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appendQuery(postLogin, "entra_login=success"))).build();
    }

    /**
     * Starts the Entra authorization code flow for browser clients: generates a cryptographically random {@code state}, stores it under
     * {@link SecurityService#SESSION_ENTRA_OAUTH_STATE_KEY}, builds the Microsoft authorize URL (same {@code redirect_uri} as configured for the callback), and returns
     * HTTP 302 to Entra. SPAs may omit this endpoint if they initiate OAuth themselves with an identical registered {@code redirect_uri}.
     *
     * @param httpRequest used to create or access the HTTP session for {@code state} storage
     * @return HTTP 302 to the Entra authorize endpoint, or 404 if {@code security.handler} is not {@code ENTRAID}, or 503 if required Entra properties are missing
     * @throws Exception if building the redirect URL fails unexpectedly
     */
    @GetMapping(value = "/authenticate/login")
    public ResponseEntity<Void> entraOAuthLogin(final HttpServletRequest httpRequest) throws Exception {

        if (!"ENTRAID".equalsIgnoreCase(StringUtils.trimToEmpty(PropertyUtility.getProperty("security.handler")))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        final String authorizationEndpoint = PropertyUtility.getProperty("security.handler.ENTRAID.authorization.endpoint");
        final String clientId = PropertyUtility.getProperty("security.handler.ENTRAID.client.id");
        final String redirectUri = PropertyUtility.getProperty("security.handler.ENTRAID.redirect.uri");
        final String scope = PropertyUtility.getProperty("security.handler.ENTRAID.scopes");
        if (StringUtils.isAnyBlank(authorizationEndpoint, clientId, redirectUri) || "none".equalsIgnoreCase(clientId)) {
            LOG.error("Entra authorize URL cannot be built: missing authorization.endpoint, client.id, or redirect.uri");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        final String scopeResolved = StringUtils.isNotBlank(scope) ? scope : "openid profile email";
        final byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        final String oauthState = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        httpRequest.getSession(true).setAttribute(SecurityService.SESSION_ENTRA_OAUTH_STATE_KEY, oauthState);

        final String url = EntraAuthorizationCodeExchange.buildAuthorizeUrl(authorizationEndpoint, clientId, redirectUri.trim(), scopeResolved, oauthState);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /**
     * Resolves the base URL for browser redirects after Entra OAuth callback: {@code security.handler.ENTRAID.post.login.redirect.uri}, then {@code app.url.root},
     * then {@code "/"}. Trailing slash is removed so a single query string can be appended.
     *
     * @return non-blank base URL without a trailing {@code /}
     */
    private static String resolvePostLoginRedirectUrl() {

        String url = PropertyUtility.getProperty("security.handler.ENTRAID.post.login.redirect.uri");
        if (StringUtils.isBlank(url) || "none".equalsIgnoreCase(url)) {
            url = PropertyUtility.getProperty("app.url.root");
        }
        if (StringUtils.isBlank(url) || "none".equalsIgnoreCase(url)) {
            return "/";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Appends one {@code name=value} fragment to a URL, using {@code ?} or {@code &} depending on whether {@code base} already contains a query string.
     *
     * @param base absolute or relative URL (may already include {@code ?…})
     * @param queryPair unencoded {@code key=value} pair (caller must encode values if needed)
     * @return {@code base} with the pair appended
     */
    private static String appendQuery(final String base, final String queryPair) {

        final String sep = base.contains("?") ? "&" : "?";
        return base + sep + queryPair;
    }

    /**
     * Percent-encodes a string for safe use in a query parameter value; spaces are encoded as {@code %20} (not {@code +}).
     *
     * @param s raw string (must not be {@code null})
     * @return UTF-8 application/x-www-form-urlencoded–style encoding
     */
    private static String urlEncode(final String s) {

        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Logout the authenticated user.
     *
     * @param userName the user name
     * @return the user
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.POST, value = "/logout/{userName}")
    @Operation(summary = "Log out the authenticated user. This call requires authentication", tags = {
        "security"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successful logout")
    })
    @Parameters({
        @Parameter(name = "userName", description = "User name to log out", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Void> logout(@PathVariable(value = "userName", required = true) final String userName) throws Exception {

        try (final SecurityService securityService = new SecurityService()) {

            final String authToken = getJwt(request);
            securityService.logout(userName, authToken);

            return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }
}
