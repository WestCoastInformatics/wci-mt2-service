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
import org.ihtsdo.refsetservice.handler.ImsSecurityServiceHandler;
import org.ihtsdo.refsetservice.handler.SecurityServiceHandler;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for authentication and user end points.
 * 
 * @author Nuno
 *
 */
@OpenAPIDefinition(info = @Info(title = "Security Controller", version = "1.0.0", description = "Endpoints for authentication and logout."), tags = {
		@Tag(name = "security", description = "Security service endpoints") }, servers = {
				@Server(description = "Current Instance", url = "/") })
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class SecurityController extends BaseController {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(SecurityController.class);

	/**
	 * TODO - REMOVE AFTER PERMISSIONS CONVERTED.
	 *
	 * @param request the request
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@GetMapping("/internalSecurity/convertPermissions")
	public @ResponseBody ResponseEntity<String> convertPermissions(final HttpServletRequest request) throws Exception {

		LOG.info("PERMISSION CLEANUP START");

		final User user = authorizeUser();
		try (final SecurityService securityService = new SecurityService()) {

			String results = "Didn't work";

			if (user.getUserName().equals("refset-dev") || user.getUserName().equals("twhalen")) {

				ImsSecurityServiceHandler handler = (ImsSecurityServiceHandler) HandlerUtility
						.newStandardHandlerInstanceWithConfiguration("security.handler", "IMS",
								SecurityServiceHandler.class);
				results = handler.convertRolesForAllUsers();
			}

			LOG.info("PERMISSION CLEANUP FINISH");
			return new ResponseEntity<>(results, new HttpHeaders(), HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Returns the user.
	 *
	 * @param userName the user name
	 * @param request  the request
	 * @return the user
	 * @throws Exception the exception
	 */
	@PostMapping("/authenticate/{userName}")
	@Operation(summary = "Authorize the user. Requires logging in to IMS first and sending the appropriate cookie", responses = {
			@ApiResponse(responseCode = "200", description = "Successful authorization, payload contains user object"),
			@ApiResponse(responseCode = "401", description = "Unauthorized") })
	@Parameters({ @Parameter(name = "userName", description = "User name to authenicate", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<User> authenticate(@PathVariable(value = "userName") final String userName,
			final HttpServletRequest request) throws Exception {

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
			handleException(e);
			return null;
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
	@Operation(summary = "Log out the authenticated user. This call requires authentication", responses = {
			@ApiResponse(responseCode = "200", description = "Successful logout") })
	@Parameters({ @Parameter(name = "userName", description = "User name to log out", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<Void> logout(
			@PathVariable(value = "userName", required = true) final String userName) throws Exception {

		LOG.info("RESTful call POST (Security): logout for userName = {}", userName);

		try (final SecurityService securityService = new SecurityService()) {

			securityService.logout(userName);

			return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}
}
