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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for authentication and user end points.
 * 
 * @author Nuno
 *
 */
@Api(tags = "security", description = "Endpoints for authentication and logout")
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class SecurityController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SecurityController.class);

    /**
     * Returns the user.
     *
     * @param userName the user name
     * @param request the request
     * @return the user
     * @throws Exception the exception
     */
    @PostMapping("/authenticate/{userName}")
    @ApiOperation(value = "Authorize the user. Requires logging in to IMS first and sending the appropriate cookie", response = User.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successful authorization, payload contains user object"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "userName", value = "User name to authenicate", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<User> authenticate(@PathVariable(value = "userName") final String userName, final HttpServletRequest request)
        throws Exception {

        LOG.info("RESTful call POST (Security): authentication for username = {}", userName);

        try (final SecurityService securityService = new SecurityService()) {

            final User user = securityService.authenticate(userName);

            if (user == null || user.getAuthToken() == null) {
                throw new Exception("Unable to authenticate user");
            }

            LOG.debug("******** SESSION USER: " + ModelUtility.toJson(user));
            request.getSession().setAttribute(SecurityService.SESSION_USER_OBJECT_KEY, user);
            return new ResponseEntity<>(user, new HttpHeaders(), HttpStatus.OK);

        } catch (final Exception e) {
            return handleException(e);
        }
    }

    /**
     * Logout the authenticated user.
     *
     * @param userName the user name
     * @return the user
     * @throws Exception the exception
     */
    @PostMapping("/logout/{userName}")
    @ApiOperation(value = "Log out the authenticated user. This call requires authentication", response = Void.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successful logout"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "userName", value = "User name to log out", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Void> logout(@PathVariable(value = "userName", required = true) final String userName) throws Exception {

        LOG.info("RESTful call POST (Security): logout for userName = {}", userName);

        try (final SecurityService securityService = new SecurityService()) {

            securityService.logout(userName);

            return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);

        } catch (final Exception e) {
            return handleException(e);
        }
    }
}
