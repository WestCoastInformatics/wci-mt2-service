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

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
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
    "http://localhost:4200", "http://localhost:8888", "http://local.ihtsdotools.org:8888", "https://mt2-demo.westcoastinformatics.com/",
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
     * Authorize.
     *
     * @param request the request
     * @return the user
     * @throws Exception the exception
     */
    public User authorizeUser(final HttpServletRequest request) throws Exception {

        final String jwtToken = getJwt(request);
        // if no jwtToken then user is guest.
        if (StringUtils.isEmpty(jwtToken) || "undefined".equals(jwtToken)) {
            return SecurityService.getUserFromSession();
        }

        final DecodedJWT djwt = JWT.decode(jwtToken);
        JwtUtility.verify(djwt);

        String username = JwtUtility.getOrgId(djwt.getClaims());
        if (StringUtils.isEmpty(username)) {
            username = SecurityService.getUsernameFromJwt(jwtToken);
        }

        User authUser = SecurityService.getUserFromUserName(username);
        if (authUser != null) {
            final String roles = JwtUtility.getRole(djwt.getClaims());
            authUser.getRoles().addAll(Set.of(roles.split(",")));
        }

        if (authUser == null || (authUser.getId() == null && !PropertyUtility.getProperty("springProfiles").toLowerCase().contains("test"))) {

            throw new RestException(false, 401, "Unauthorized", "Unable to find user from session");
        }
        return authUser;
    }

    /**
     * Returns the jwt.
     *
     * @param request the request
     * @return the jwt
     * @throws Exception the exception
     */
    public String getJwt(final HttpServletRequest request) throws Exception {

        // Extract header token -
        final String headerToken = ConfigUtility.getHeaderToken();
        // Replace use of "Bearer " in header token
        String jwt = request.getHeader(headerToken);
        if (ConfigUtility.isEmpty(jwt)) {
            // Extract bearer token
            jwt = request.getHeader("Authorization");
            if (!ConfigUtility.isEmpty(jwt) && jwt.startsWith("Bearer ") && !jwt.equals("Bearer guest")) {
                jwt = jwt.substring(jwt.indexOf(" ") + 1);
            }

            // guest
            else {
                if (!ConfigUtility.isEmpty(jwt) && jwt.equals("Bearer guest")) {
                    throw new Exception("Guest login is not supported when login is enabled");
                } else {
                    throw new Exception("Unexpected authorization token = " + request.getHeader("Authorization") + ", " + headerToken + ", "
                        + request.getHeader(ConfigUtility.getHeaderToken()));
                }
            }
            return jwt;
        } else {
            return jwt.replaceFirst("Bearer ", "");
        }
    }

}
