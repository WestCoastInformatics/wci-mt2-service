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
import org.ihtsdo.refsetservice.handler.SecurityServiceHandler;
import org.ihtsdo.refsetservice.model.BrowserLoginCallback;
import org.ihtsdo.refsetservice.model.BrowserLoginException;
import org.ihtsdo.refsetservice.model.RestException;
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
 * Controller for authentication and user end points. Identity-provider specifics live in {@link SecurityServiceHandler}.
 *
 * @author Nuno
 *
 */
@Hidden
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class SecurityController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SecurityController.class);

    /** Query flag after successful browser login (handler-agnostic). */
    private static final String AUTH_LOGIN_SUCCESS = "auth_login=success";

    /** Query flag prefix after failed browser login (handler-agnostic). */
    private static final String AUTH_ERROR_PREFIX = "auth_error=";

    /** Cryptographic RNG for OAuth2 {@code state} values on {@link #authenticateLogin(HttpServletRequest)}. */
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
     * Returns the authenticated session user (including {@code authToken}) for browser clients after identity-provider login redirect. Accepts either an HTTP
     * session user or a valid application JWT in the {@code Authorization: Bearer} header (hybrid local-UI / remote-API handoff).
     *
     * @param httpRequest current request (session and optional Bearer token)
     * @return 200 with {@link User}, or 401 if no authenticated session or token
     * @throws Exception if session access fails unexpectedly
     */
    @GetMapping(value = "/authenticate/session")
    public ResponseEntity<User> authenticateSession(final HttpServletRequest httpRequest) throws Exception {

        try {
            return ResponseEntity.ok(requireAuthenticatedUser(httpRequest));
        } catch (final RestException e) {
            if (e.getError() != null && e.getError().getStatus() == 401) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            throw e;
        }
    }

    /**
     * Identity-provider redirect target after browser sign-in. Delegates to {@link SecurityServiceHandler#completeBrowserLogin(BrowserLoginCallback)}, then
     * stores the MT2 session user and redirects with {@code auth_login=success} or {@code auth_error=...}.
     *
     * @param code authorization code from the IdP (query); required unless {@code error} is set
     * @param error IdP error code when authorization failed
     * @param errorDescription optional human-readable error detail from the IdP
     * @param state OAuth2 state; must equal session {@link SecurityService#SESSION_OAUTH_STATE_KEY} when login used state
     * @param httpRequest current request (session and servlet context)
     * @return HTTP 302 {@code Location} to the post-login base URL with an appended query flag
     * @throws Exception if handler resolution fails unexpectedly
     */
    @GetMapping(value = "/authenticate/callback")
    public ResponseEntity<Void> authenticateCallback(@RequestParam(required = false) final String code,
        @RequestParam(name = "error", required = false) final String error,
        @RequestParam(name = "error_description", required = false) final String errorDescription, @RequestParam(required = false) final String state,
        final HttpServletRequest httpRequest) throws Exception {

        LOG.info("Auth callback: raw query={}, code={}, error={}, state={}", httpRequest.getQueryString(), code, error, state);

        final SecurityServiceHandler handler = SecurityService.getSecurityHandler();
        final String postLogin = resolvePostLoginRedirect(httpRequest, handler);

        if (!handler.supportsBrowserCallback()) {
            LOG.warn("Auth callback invoked but handler does not support browser callback");
            return redirectAuthError(postLogin, "callback_disabled");
        }

        final Object expectedStateObj =
            httpRequest.getSession(false) != null ? httpRequest.getSession(false).getAttribute(SecurityService.SESSION_OAUTH_STATE_KEY) : null;
        final String expectedState = expectedStateObj != null ? String.valueOf(expectedStateObj) : null;

        String authToken = null;
        try (final SecurityService securityService = new SecurityService()) {
            final User idpUser = handler.completeBrowserLogin(new BrowserLoginCallback(code, error, errorDescription, state, expectedState));
            final User user = securityService.authenticateHandlerUser(idpUser);
            if (user == null || user.getAuthToken() == null) {
                clearOAuthReturnUrl(httpRequest);
                return redirectAuthError(postLogin, "no_user");
            }
            httpRequest.getSession(true).setAttribute(SecurityService.SESSION_USER_OBJECT_KEY, user);
            httpRequest.getSession().removeAttribute(SecurityService.SESSION_OAUTH_STATE_KEY);
            clearOAuthReturnUrl(httpRequest);
            authToken = user.getAuthToken();
            LOG.info("Auth callback: session established for user={}", user.getUserName());
        } catch (final BrowserLoginException ex) {
            LOG.warn("Auth callback login failed: {}", ex.getErrorCode());
            clearOAuthReturnUrl(httpRequest);
            return redirectAuthError(postLogin, urlEncode(ex.getErrorCode()));
        } catch (final Exception ex) {
            LOG.warn("Auth callback login failed: {}", ex.toString());
            clearOAuthReturnUrl(httpRequest);
            return redirectAuthError(postLogin, "login");
        }

        final String location = appendQuery(postLogin, AUTH_LOGIN_SUCCESS) + "#auth_token=" + urlEncode(authToken);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    /**
     * Starts browser login via the configured {@link SecurityServiceHandler}.
     *
     * @param returnUrl optional allowlisted UI origin to redirect to after OAuth (scheme+host+port); ignored if not in {@code cors.allowed-origins}
     * @param httpRequest used to create or access the HTTP session for OAuth {@code state} when required
     * @return HTTP 302 to the identity provider, or 503 if required configuration is missing
     * @throws Exception if building the redirect URL fails unexpectedly
     */
    @GetMapping(value = "/authenticate/login")
    public ResponseEntity<Void> authenticateLogin(@RequestParam(required = false) final String returnUrl, final HttpServletRequest httpRequest)
        throws Exception {

        if (StringUtils.isNotBlank(returnUrl)) {
            final String allowedOrigin = resolveAllowlistedReturnUrl(returnUrl);
            if (allowedOrigin != null) {
                httpRequest.getSession(true).setAttribute(SecurityService.SESSION_OAUTH_RETURN_URL_KEY, allowedOrigin);
            } else {
                LOG.warn("Auth login: ignoring non-allowlisted returnUrl");
            }
        }

        final SecurityServiceHandler handler = SecurityService.getSecurityHandler();
        String oauthState = null;
        if (handler.requiresBrowserLoginState()) {
            final byte[] bytes = new byte[24];
            SECURE_RANDOM.nextBytes(bytes);
            oauthState = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            httpRequest.getSession(true).setAttribute(SecurityService.SESSION_OAUTH_STATE_KEY, oauthState);
        }

        final String url;
        try {
            url = handler.buildBrowserLoginUrl(oauthState);
        } catch (final Exception ex) {
            LOG.error("Browser login URL could not be built: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (StringUtils.isBlank(url)) {
            LOG.error("Browser login URL is blank");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /**
     * Starts browser logout: clears MT2 session, then redirects to the handler logout URL.
     *
     * @param httpRequest used to clear or invalidate the HTTP session
     * @return HTTP 302 to the identity provider logout, or 503 when misconfigured
     * @throws Exception if building the redirect URL fails unexpectedly
     */
    @GetMapping(value = "/authenticate/logout")
    public ResponseEntity<Void> authenticateLogout(final HttpServletRequest httpRequest) throws Exception {

        clearBrowserAuthSession(httpRequest);

        final String url = SecurityService.getSecurityHandler().getLogoutUrl();
        if (StringUtils.isBlank(url)) {
            LOG.error("Browser logout URL is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        LOG.info("Auth logout: redirect Location={}", url);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /**
     * Best-effort clear of browser session state used by login (user object, OAuth state, app JWT maps). Does not throw if there is no session.
     *
     * @param httpRequest current request
     */
    private static void clearBrowserAuthSession(final HttpServletRequest httpRequest) {

        final javax.servlet.http.HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return;
        }
        try {
            final Object userObj = session.getAttribute(SecurityService.SESSION_USER_OBJECT_KEY);
            if (userObj instanceof User) {
                final String authToken = ((User) userObj).getAuthToken();
                if (StringUtils.isNotBlank(authToken)) {
                    SecurityService.clearAuthTokenMaps(authToken);
                }
            }
            session.removeAttribute(SecurityService.SESSION_USER_OBJECT_KEY);
            session.removeAttribute(SecurityService.SESSION_OAUTH_STATE_KEY);
            session.removeAttribute(SecurityService.SESSION_OAUTH_RETURN_URL_KEY);
            session.invalidate();
        } catch (final IllegalStateException e) {
            LOG.debug("Auth logout: session already invalid: {}", e.getMessage());
        } catch (final Exception e) {
            LOG.warn("Auth logout: session clear failed: {}", e.getMessage());
        }
    }

    /**
     * Resolves post-login redirect base: allowlisted session {@code returnUrl} if present, otherwise handler post-login URI.
     *
     * @param httpRequest current request
     * @param handler security handler
     * @return normalized base without trailing slash
     * @throws Exception if handler post-login URI cannot be resolved
     */
    private static String resolvePostLoginRedirect(final HttpServletRequest httpRequest, final SecurityServiceHandler handler) throws Exception {

        final javax.servlet.http.HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            final Object returnUrlObj = session.getAttribute(SecurityService.SESSION_OAUTH_RETURN_URL_KEY);
            if (returnUrlObj != null) {
                final String allowed = resolveAllowlistedReturnUrl(String.valueOf(returnUrlObj));
                if (allowed != null) {
                    return normalizePostLoginUrl(allowed);
                }
            }
        }
        return normalizePostLoginUrl(handler.getPostLoginRedirectUri());
    }

    /**
     * Clears the optional OAuth return URL from the session.
     *
     * @param httpRequest current request
     */
    private static void clearOAuthReturnUrl(final HttpServletRequest httpRequest) {

        final javax.servlet.http.HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.removeAttribute(SecurityService.SESSION_OAUTH_RETURN_URL_KEY);
        }
    }

    /**
     * Validates {@code returnUrl} against {@code cors.allowed-origins} and returns the origin (scheme+host+port) only.
     *
     * @param returnUrl candidate absolute URL
     * @return allowlisted origin, or {@code null} if rejected
     */
    public static String resolveAllowlistedReturnUrl(final String returnUrl) {

        if (StringUtils.isBlank(returnUrl)) {
            return null;
        }

        final String candidateOrigin;
        try {
            final URI uri = URI.create(returnUrl.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            if (StringUtils.isNotBlank(uri.getRawUserInfo())) {
                return null;
            }
            candidateOrigin = buildOrigin(uri.getScheme(), uri.getHost(), uri.getPort());
        } catch (final Exception e) {
            return null;
        }

        final String allowedProp = PropertyUtility.getProperty("cors.allowed-origins");
        if (StringUtils.isBlank(allowedProp) || "*".equals(allowedProp.trim())) {
            return null;
        }

        for (final String entry : allowedProp.split(",")) {
            final String allowedOrigin = normalizeAllowedOriginEntry(entry);
            if (allowedOrigin != null && allowedOrigin.equalsIgnoreCase(candidateOrigin)) {
                return candidateOrigin;
            }
        }
        return null;
    }

    /**
     * Normalizes a CORS allowlist entry to scheme://host[:port].
     *
     * @param entry raw CORS origin entry
     * @return normalized origin, or {@code null}
     */
    private static String normalizeAllowedOriginEntry(final String entry) {

        if (StringUtils.isBlank(entry)) {
            return null;
        }
        try {
            String value = entry.trim();
            while (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            final URI uri = URI.create(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return buildOrigin(uri.getScheme(), uri.getHost(), uri.getPort());
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * Builds an origin string.
     *
     * @param scheme http or https
     * @param host host name
     * @param port port or -1 for default
     * @return scheme://host[:port]
     */
    private static String buildOrigin(final String scheme, final String host, final int port) {

        final StringBuilder sb = new StringBuilder();
        sb.append(scheme.toLowerCase()).append("://").append(host.toLowerCase());
        if (port != -1) {
            sb.append(':').append(port);
        }
        return sb.toString();
    }

    /**
     * Normalizes post-login redirect base (strip trailing slash).
     *
     * @param url handler post-login URL
     * @return non-blank base without trailing {@code /}
     */
    private static String normalizePostLoginUrl(final String url) {

        if (StringUtils.isBlank(url) || "none".equalsIgnoreCase(url)) {
            return "/";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Builds a 302 to {@code postLogin} with {@code auth_error=...}.
     *
     * @param postLogin base URL
     * @param errorCode error fragment (already encoded if needed)
     * @return redirect response
     */
    private static ResponseEntity<Void> redirectAuthError(final String postLogin, final String errorCode) {

        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appendQuery(postLogin, AUTH_ERROR_PREFIX + errorCode))).build();
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
     * @return UTF-8 form-urlencoded–style encoding
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
