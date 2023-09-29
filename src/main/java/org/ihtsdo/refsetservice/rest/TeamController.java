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

import java.util.Arrays;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.ResultListUser;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.TeamType;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
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
 * Controller for /team endpoints.
 */
@RestController
@OpenAPIDefinition(info = @Info(title = "Team Controller", version = "1.0.0", description = "Endpoints for creating, retrieving, updating, and deleting teams."), tags = {
		@Tag(name = "team", description = "Team service endpoints") }, servers = {
				@Server(description = "Current Instance", url = "/") })
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class TeamController extends BaseController {

	/** The Constant LOG. */
	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(TeamController.class);

	/** Search teams API notes. */
	private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

	/**
	 * Returns the team.
	 *
	 * @param id             the id of the team
	 * @param includeMembers the include members
	 * @return the team
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/team/{id}")
	@Operation(summary = "Get team.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "id", description = "Team id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "includeMembers", description = "Include team's members (users)", required = false, example = "false") })
	public @ResponseBody ResponseEntity<Team> getTeam(@PathVariable(value = "id") final String id,
			@RequestParam(value = "includeMembers") final boolean includeMembers) throws Exception {

		authorizeUser();

		try {

			final Team team = TeamService.getTeam(id, includeMembers);
			return new ResponseEntity<>(team, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Search teams.
	 *
	 * @param searchParameters      the search parameters
	 * @param bindingResult         the binding result
	 * @param includeMembers        the include members
	 * @param onlyUsersTeams        return only the teams the user is a member off
	 *                              or has permission to admin
	 * @param hideOrganizationTeams the hide organization teams
	 * @return the string
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/team/search", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Find teams.  This call requires authentication with the correct role.", description = API_NOTES, responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Expectation failed") })
	// @ModelAttribute API params documented in SearchParameter
	@Parameters({
			@Parameter(name = "includeMembers", description = "Include team's members (users)", required = false, example = "false"),
			@Parameter(name = "onlyUsersTeams", description = "Limit to only user teams", required = false),
			@Parameter(name = "hideOrganizationTeams", description = "Hide organization teams", required = false) })
	@RecordMetric
	public @ResponseBody ResponseEntity<ResultList<Team>> getTeams(
			@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult,
			@RequestParam(value = "includeMembers") final boolean includeMembers,
			@RequestParam(value = "onlyUsersTeams") final boolean onlyUsersTeams,
			@RequestParam(value = "hideOrganizationTeams") final Boolean hideOrganizationTeams) throws Exception {

		final User authUser = authorizeUser();

		boolean noOrganizationTeams = false;

		if (hideOrganizationTeams != null && hideOrganizationTeams) {

			noOrganizationTeams = true;
		}

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		try {

			final ResultList<Team> results = TeamService.searchTeams(authUser, searchParameters, includeMembers,
					onlyUsersTeams, noOrganizationTeams);
			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Adds the team.
	 *
	 * @param team the team to add
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/team", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Add team.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "201", description = "Team successfully created"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "409", description = "Conflict"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation") })
	@Parameters({ @Parameter(name = "team", description = "Team object", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<Team> addTeam(@RequestBody final Team team) throws Exception {

		final User authUser = authorizeUser();

		try {

			if (team == null) {
				throw new RestException(false, 417, "Expectation failed", "Missing team");
			}

			try {

				team.validateAdd();
			} catch (final Exception e) {
				throw new RestException(false, 417, "Expectation failed", "Team failed validation");
			}

			team.setType(TeamType.PPROJECT.getText());
			final Team t = TeamService.createTeam(authUser, team);
			return ResponseEntity.status(HttpStatus.CREATED).body(t);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Update the team.
	 *
	 * @param id   the id of the team
	 * @param team the team
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/team/{id}", consumes = MediaType.APPLICATION_JSON)
	@Operation(summary = "Update team.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "201", description = "Successfully updated team"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation") })
	@Parameters({ @Parameter(name = "id", description = "Team id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "team", description = "Team object", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<Team> updateTeam(@PathVariable(value = "id") final String id,
			@RequestBody final Team team) throws Exception {

		final User authUser = authorizeUser();

		try {

			if (team == null || !org.apache.commons.lang3.StringUtils.equals(id, team.getId())) {
				throw new RestException(false, 417, "Expectation failed",
						"Team is null or team id does not match id in URL.");
			}

			try {

				team.validateUpdate(null);
			} catch (final Exception e) {
				throw new RestException(false, 417, "Expectation failed", "Team failed validation");
			}

			final Team t = TeamService.updateTeam(authUser, team);
			return new ResponseEntity<>(t, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Return users for the team.
	 *
	 * @param id the id
	 * @return the users
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/team/{id}/users", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Get users for team.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "id", description = "Team id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	public ResponseEntity<Object> getOrganizationUsers(@PathVariable final String id) throws Exception {

		final User authUser = authorizeUser();

		try {

			final ResultListUser users = TeamService.getTeamUsers(authUser, id);
			return new ResponseEntity<>(users, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Add the user(s) to the team by semi-colon delimited email address(es).
	 *
	 * @param id     the team id
	 * @param emails the emails
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/team/{id}/member")
	@Operation(summary = "Add user to team.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "201", description = "Successfully added user to team"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "id", description = "Team id, e.g. &lt;uuid&gt;", required = true) })
	public @ResponseBody ResponseEntity<String> addUsersToTeam(@PathVariable final String id, final String emails)
			throws Exception {

		final User authUser = authorizeUser();

		if (StringUtils.isBlank(emails)) {
			throw new RestException(false, 417, "Expecation failed", "Unexpected blank emails");
		}

		try {

			final Team team = TeamService.getTeam(id, false);
			if (team == null) {
				throw new RestException(false, 404, "Not found", "Unable to find team for id = " + id);
			}

			if (emails.contains(";")) {
				for (final String email : Arrays.asList(emails.split(";"))) {
					TeamService.addUserToTeam(authUser, id, email);
				}
			} else {
				TeamService.addUserToTeam(authUser, id, emails);
			}

			return new ResponseEntity<>(HttpStatus.CREATED);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Removes the user from team.
	 *
	 * @param id     the team id
	 * @param userId the user id
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.DELETE, value = "/team/{id}/member/{userId}")
	@Operation(summary = "Delete users from team.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "202", description = "Successfully removed user from team"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "id", description = "Team id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "userId", description = "User id, e.g. &lt;uuid&gt;", required = true) })
	public @ResponseBody ResponseEntity<Void> removeUserFromTeam(@PathVariable final String id,
			@PathVariable final String userId) throws Exception {

		final User authUser = authorizeUser();

		try {
			final Team team = TeamService.getTeam(id, false);
			if (team == null) {
				throw new RestException(false, 404, "Not found", "Unable to find team for id = " + id);
			}

			TeamService.removeUserFromTeam(authUser, id, userId);

			return new ResponseEntity<>(HttpStatus.ACCEPTED);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Adds the role to the team.
	 *
	 * @param id   the team id
	 * @param role the role
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/team/{id}/role/{role}")
	@Operation(summary = "Add role to team.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "201", description = "Successfully added role to team"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "id", description = "Team id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "role", description = "Role to add. One of ADMIN, REVIEWER, VIEWER or AUTHOR.", required = true) })
	public @ResponseBody ResponseEntity<Void> addRoleToTeam(@PathVariable final String id,
			@PathVariable final String role) throws Exception {

		final User authUser = authorizeUser();

		try {

			TeamService.addRoleToTeam(authUser, id, role);
			return new ResponseEntity<>(HttpStatus.CREATED);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Remove the role from the team.
	 *
	 * @param id   the team id
	 * @param role the role
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.DELETE, value = "/team/{id}/role/{role}")
	@Operation(summary = "Delete role from team.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "202", description = "Successfully removed role from team"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation") })
	@Parameters({ @Parameter(name = "id", description = "Team id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "role", description = "Role to add. One of ADMIN, REVIEWER, VIEWER or AUTHOR.", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<Void> removeRoleFromTeam(@PathVariable final String id,
			@PathVariable final String role) throws Exception {

		final User authUser = authorizeUser();

		try {

			TeamService.removeRoleFromTeam(authUser, id, role);
			return new ResponseEntity<>(HttpStatus.ACCEPTED);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Logical delete (inactivate) the team.
	 *
	 * @param id the id
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.DELETE, value = "/team/{id}")
	@Operation(summary = "Inactivate a team.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "202", description = "Successfully inactivated team"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation") })
	@Parameters({ @Parameter(name = "id", description = "Team id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	public ResponseEntity<Void> deleteTeam(@PathVariable("id") final String id) throws Exception {

		final User authUser = authorizeUser();

		try {

			TeamService.inactivateTeam(authUser, id);
			return new ResponseEntity<>(HttpStatus.ACCEPTED);

		} catch (final NotFoundException nfe) {
			throw new RestException(false, 404, "Not found", "Error inactivating team. Id {} not found = " + id);
		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}
}
