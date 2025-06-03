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

import java.io.File;
import java.nio.file.Files;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.ihtsdo.refsetservice.terminologyservice.UserService;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Controller for /user endpoints.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class UserController extends BaseController {

	/** The Constant LOG. */
	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(UserController.class);

    /** The request. */
    @Autowired
    private HttpServletRequest request;
    
	/** Search users API notes. */
	private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

	/** The local icon file directory. */
	private static final String ICON_URL_PREFIX = "user/icon/";

	/**
	 * Returns the user.
	 *
	 * @param id                   the id of the user
	 * @param includeOrganizations the include organizations
	 * @param includeTeams         the include teams
	 * @return the user
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/user/{id}", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Get user. This call requires authentication with the correct role.", tags = {
			"user" }, responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden") })
	@Parameters({ @Parameter(name = "id", description = "User id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "includeOrganizations", description = "Include user's organizations", example = "false"),
			@Parameter(name = "includeTeams", description = "Include user's teams", required = false, example = "false") })
	@RecordMetric
	public @ResponseBody ResponseEntity<User> getUser(@PathVariable(value = "id") final String id,
			@RequestParam(value = "includeOrganizations") final boolean includeOrganizations,
			@RequestParam(value = "includeTeams") final boolean includeTeams) throws Exception {

        LOG.info("Get user: {}", id);
        authorizeUser(request);

        try (final TerminologyService service = new TerminologyService()) {

            final User user = UserService.getUser(service, id, includeTeams);
            return new ResponseEntity<>(user, HttpStatus.OK);


		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Update the user.
	 *
	 * @param id      the id of the user
	 * @param userStr the user str
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.PUT, value = "/user/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Update user. This call requires authentication with the correct role.", tags = {
			"user" }, responses = { @ApiResponse(responseCode = "201", description = "Successfully updated user"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Not Found"),
					@ApiResponse(responseCode = "417", description = "Failed Expectation") }, requestBody = @RequestBody(description = "User to update", required = true, content = {
							@Content(mediaType = "application/json", schema = @Schema(implementation = User.class)) }))
	@Parameters({ @Parameter(name = "id", description = "User id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "user", description = "User object", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<User> updateUser(@PathVariable(value = "id") final String id,
			@org.springframework.web.bind.annotation.RequestBody @RequestBody final User user) throws Exception {

        LOG.info("Update user: {}", user);
        final User authUser = authorizeUser(request);

        if (user == null || !org.apache.commons.lang3.StringUtils.equals(id, user.getId())) {
            LOG.info("User is null or user id does not match id in URL.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try (final TerminologyService service = new TerminologyService()) {

            final User updatedUser = UserService.updateUser(service, authUser, user);
            return new ResponseEntity<>(updatedUser, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Delete user icon.
	 *
	 * @param id the id of the user
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.DELETE, value = "/user/{id}/icon", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Delete icon for the user. This call requires authentication with the correct role.", tags = {
			"user" }, responses = {
					@ApiResponse(responseCode = "200", description = "Successfully removed icon for user"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Not Found"),
					@ApiResponse(responseCode = "417", description = "Failed Expectation") })
	@Parameters({ @Parameter(name = "id", description = "User id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<User> deleteUserIcon(@PathVariable(value = "id") final String id)
			throws Exception {

        LOG.info("Delete user icon: {}", id);
        final User authUser = authorizeUser(request);

        try (final TerminologyService service = new TerminologyService()) {

            final User user = UserService.getUser(service, id, false);
            if (user == null || !org.apache.commons.lang3.StringUtils.equals(id, user.getId())) {
                LOG.info("User is null or user id does not match id in URL.");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            user.setIconUri(null);
            final User original = UserService.updateUser(service, authUser, user);

            service.commit();

            return new ResponseEntity<>(original, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Search users.
	 *
	 * @param includeOrganizations the include organizations
	 * @param includeTeams         the include teams
	 * @param searchParameters     the search parameters
	 * @param bindingResult        the binding result
	 * @return the string
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/user/search", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Find users. This call requires authentication with the correct role.", description = API_NOTES, tags = {
			"user" }, responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden") })
	// @ModelAttribute API params documented in SearchParameter
	@Parameters({
			@Parameter(name = "includeOrganizations", description = "Include user's organizations", required = false, example = "false"),
			@Parameter(name = "includeTeams", description = "Include user's teams", required = false, example = "false"),
			@Parameter(name = "query", description = "The value to be searched'", required = false) })
	@RecordMetric
	public @ResponseBody ResponseEntity<ResultList<User>> getUsers(
			@RequestParam(value = "includeOrganizations") final boolean includeOrganizations,
			@RequestParam(value = "includeTeams") final boolean includeTeams,
			@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult)
			throws Exception {

        LOG.info("Search users: {}", ModelUtility.toJson(searchParameters));
        authorizeUser(request);

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<User> results = UserService.searchUsers(service, searchParameters);

            for (final User user : results.getItems()) {

                if (includeTeams) {
                    final SearchParameters sp = new SearchParameters();
                    sp.setQuery("members:" + user.getId());
                    final ResultList<Team> teamsResultList = TeamService.searchTeams(user, sp);
                    if (teamsResultList != null && teamsResultList.getItems() != null) {
                        user.getTeams().addAll(teamsResultList.getItems());
                    }
                }
            }

            return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Returns the user icon.
	 *
	 * @param fileName the file name
	 * @return the user icon
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/user/icon/{fileName}")
	@Operation(summary = "Icon file for the user", tags = { "user" }, responses = {
			@ApiResponse(responseCode = "200", description = "Successfully added icon for user"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden") })
	@Parameters({ @Parameter(name = "filename", description = "File name for user icon.", required = true) })
	public @ResponseBody ResponseEntity<Resource> getUserIcon(@PathVariable("fileName") final String fileName)
			throws Exception {

		try {

			final Resource file = FileUtility.getIconFile(fileName);

			return ResponseEntity.ok()
					.header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
					.header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(file.getFile().toPath()))
					.contentLength(file.contentLength()).body(file);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Edit the user icon.
	 *
	 * @param id        the user id
	 * @param inputFile the input file
	 * @return the response entity with the icon URI
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/user/{id}/icon")
	@Operation(summary = "Update icon for user. This call requires authentication with the correct role.", tags = {
			"user" }, responses = {
					@ApiResponse(responseCode = "202", description = "Successfully updated icon for user"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Not Found"),
					@ApiResponse(responseCode = "417", description = "Failed Expectation") })

	@Parameters({ @Parameter(name = "id", description = "User id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "file", description = "Icon file", required = true) })
	@RecordMetric
	public ResponseEntity<String> editUserIcon(@PathVariable("id") final String id,
			@RequestParam("file") final MultipartFile inputFile) throws Exception {

        LOG.info("Edit icon for user: {}.", id);
        final User authUser = authorizeUser(request);

        try (final TerminologyService service = new TerminologyService()) {

            final User user = UserService.getUser(service, id, false);
            if (user == null) {
                throw new RestException(false, 404, "Not found", "Unable to find user for " + id);
            }

            String fileToDelete = "";

            if (user.getIconUri() != null) {
                fileToDelete = user.getIconUri().replace(ICON_URL_PREFIX, "");
            }

            final File file = FileUtility.saveIconFile(inputFile, id, fileToDelete);
            user.setIconUri(ICON_URL_PREFIX + file.getName());
            UserService.updateUser(service, authUser, user);

            return new ResponseEntity<>("\"" + user.getIconUri() + "\"", HttpStatus.ACCEPTED);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

}
