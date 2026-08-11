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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConfigUtility;
import org.ihtsdo.refsetservice.util.JwtUtility;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

/**
 * Base controller for error handling.
 */
@CrossOrigin(origins = {
    "http://localhost:4200", "http://localhost:8888", "http://local.ihtsdotools.org:8888", "https://mt2-dev.westcoastinformatics.com/",
    "https://mt2-uat.westcoastinformatics.com/"
}, allowCredentials = "true")
public class BaseController {

    /** The Constant log. */
    private static final Logger LOG = LoggerFactory.getLogger(BaseController.class);

    /**
     * Handle exception.
     *
     * @param exception the e
     * @return the ResponseEntity
     * @throws Exception the exception
     */
    public void handleException(final Exception exception) throws Exception {

        if (exception instanceof LocalException) {
            // Log error and return appropriate RestException
            LOG.error("ERROR (LOCAL): " + exception);
            throw new RestException(true, 500, "Internal Server Error", exception.getMessage());
        } else if (exception instanceof RestException) {
            LOG.error("ERROR (WEB): " + ((RestException) exception).getError());
            throw (RestException) exception;
        } else {
            // Log error and return appropriate RestException
            LOG.error("Internal server error", exception);
            throw new RestException(false, 500, "Internal Server Error", exception.getMessage());
        }
    }

    /**
     * Check to make sure parameters were properly bound to variables.
     *
     * @param bindingResult the binding result
     * @throws Exception the exception
     */
    public void checkBinding(final BindingResult bindingResult) throws Exception {

        // Check whether or not parameter binding was successful
        if (bindingResult.hasErrors()) {

            final List<FieldError> errors = bindingResult.getFieldErrors();
            final List<String> errorMessages = new ArrayList<>();

            for (final FieldError error : errors) {

                final String errorMessage = "ERROR " + bindingResult.getObjectName() + " = " + error.getField() + ", " + error.getCode();
                LOG.error(errorMessage);
                errorMessages.add(errorMessage);
            }

            throw new RestException(false, 417, "Expectation failed", String.join("\n ", errorMessages));
        }
    }

    /**
     * Resolves the request user from HTTP session or {@code Authorization: Bearer} JWT.
     * Same-origin cookie session and local-UI remote-API token handoff both work.
     *
     * @param request the request
     * @return the user (may be guest when neither session nor valid Bearer is present)
     * @throws Exception the exception
     */
    public User authorizeUser(final HttpServletRequest request) throws Exception {

        final User fromRequest = findAuthenticatedUser(request);
        if (fromRequest != null) {
            return fromRequest;
        }
        return SecurityService.getUserFromSession();
    }

    /**
     * Like {@link #authorizeUser(HttpServletRequest)} but rejects guest / missing users.
     *
     * @param request the request
     * @return authenticated non-guest user
     * @throws Exception the exception
     */
    public User requireAuthenticatedUser(final HttpServletRequest request) throws Exception {

        final User user = authorizeUser(request);
        if (user == null || StringUtils.isBlank(user.getUserName()) || SecurityService.GUEST_USERNAME.equals(user.getUserName())) {
            throw new RestException(false, 401, "Unauthorized", "Unauthorized");
        }
        return user;
    }

    /**
     * Finds an authenticated non-guest user from test attribute, HTTP session, or Bearer JWT.
     *
     * @param request the request
     * @return authenticated user, or null if none
     * @throws Exception the exception
     */
    private User findAuthenticatedUser(final HttpServletRequest request) throws Exception {

        final Object testSessionUser = request.getAttribute(SecurityService.TEST_SESSION_USER_ATTRIBUTE);
        if (testSessionUser instanceof User) {
            final User user = (User) testSessionUser;
            if (isNonGuestUser(user)) {
                return user;
            }
        }

        final HttpSession session = request.getSession(false);
        if (session != null) {
            final Object sessionUser = session.getAttribute(SecurityService.SESSION_USER_OBJECT_KEY);
            if (sessionUser instanceof User) {
                final User user = (User) sessionUser;
                if (isNonGuestUser(user)) {
                    return user;
                }
            }
        }

        final User fromSession = SecurityService.getUserFromSession();
        if (isNonGuestUser(fromSession)) {
            return fromSession;
        }

        final String jwtToken = getBearerTokenOrNull(request);
        if (StringUtils.isEmpty(jwtToken) || "undefined".equals(jwtToken)) {
            return null;
        }

        final DecodedJWT djwt = JWT.decode(jwtToken);
        JwtUtility.verify(djwt);

        String username = JwtUtility.getOrgId(djwt.getClaims());
        if (StringUtils.isEmpty(username)) {
            username = SecurityService.getUsernameFromJwt(jwtToken);
        }

        try (final TerminologyService service = new TerminologyService()) {
            final User authUser = SecurityService.getUserFromUserName(service, username);
            if (authUser != null) {
                final String roles = JwtUtility.getRole(djwt.getClaims());
                if (StringUtils.isNotBlank(roles)) {
                    authUser.getRoles().addAll(Set.of(roles.split(",")));
                }
                authUser.setAuthToken(jwtToken);
            }

            if (authUser == null
                || (authUser.getId() == null && !PropertyUtility.getProperty("springProfiles").toLowerCase().contains("test"))) {
                throw new RestException(false, 401, "Unauthorized", "Unable to find user from session");
            }
            return authUser;
        }
    }

    private static boolean isNonGuestUser(final User user) {

        return user != null && StringUtils.isNotBlank(user.getUserName()) && !SecurityService.GUEST_USERNAME.equals(user.getUserName());
    }

    /**
     * Returns the jwt.
     *
     * @param request the request
     * @return the jwt
     * @throws Exception the exception
     */
    public String getJwt(final HttpServletRequest request) throws Exception {

        final String jwt = getBearerTokenOrNull(request);
        if (!ConfigUtility.isEmpty(jwt)) {
            return jwt;
        }

        final String authHeader = request.getHeader("Authorization");
        if (!ConfigUtility.isEmpty(authHeader) && authHeader.equals("Bearer guest")) {
            throw new Exception("Guest login is not supported when login is enabled");
        }
        if (isAuthDevBypassEnabled()) {
            return null;
        }
        final String headerToken = ConfigUtility.getHeaderToken();
        throw new Exception("Unexpected authorization token = " + authHeader + ", " + headerToken + ", " + request.getHeader(headerToken));
    }

    /**
     * Returns a Bearer token from the configured header or {@code Authorization}, or null if absent.
     *
     * @param request the request
     * @return jwt string without Bearer prefix, or null
     */
    private static String getBearerTokenOrNull(final HttpServletRequest request) throws Exception {

        final String headerToken = ConfigUtility.getHeaderToken();
        String jwt = request.getHeader(headerToken);
        if (!ConfigUtility.isEmpty(jwt)) {
            return jwt.replaceFirst("Bearer ", "");
        }

        jwt = request.getHeader("Authorization");
        if (!ConfigUtility.isEmpty(jwt) && jwt.startsWith("Bearer ") && !jwt.equals("Bearer guest")) {
            return jwt.substring(jwt.indexOf(' ') + 1);
        }
        return null;
    }

    private static boolean isAuthDevBypassEnabled() {
        final String bypass = PropertyUtility.getProperty("auth.dev.bypass");
        if ("true".equalsIgnoreCase(bypass)) {
            return true;
        }
        // Match SecurityService: when bypass is not explicitly false, allow test/dev profiles
        if ("false".equalsIgnoreCase(bypass)) {
            return false;
        }
        final String profiles = PropertyUtility.getProperty("springProfiles");
        return profiles != null
            && (profiles.toLowerCase().contains("dev") || profiles.toLowerCase().contains("test"));
    }

}
