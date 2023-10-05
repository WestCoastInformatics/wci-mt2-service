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

import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.IdName;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Controller for /project endpoints.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class ProjectController extends BaseController {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(ProjectController.class);

    /** The request. */
    @Autowired
    private HttpServletRequest request;
    
	/** Search projects API note. */
	private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

	/** The crowd unit test skip. */
	private static String crowdUnitTestSkip;

	static {
		crowdUnitTestSkip = PropertyUtility.getProperty("crowd.unit.test.skip");
	}

	/**
	 * Returns a specific project.
	 *
	 * @param id             the project ID
	 * @param includeMembers the include members
	 * @return the project
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/project/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Get project.  This call requires authentication with the correct role.", tags = {
			"project" }, responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "id", description = "Project id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "includeMembers", description = "Include project's members (users)", required = false, example = "false") })
	@RecordMetric
	public @ResponseBody ResponseEntity<Project> getProject(@PathVariable(value = "id") final String id,
			@RequestParam(value = "includeMembers", defaultValue = "false") final boolean includeMembers)
			throws Exception {

		LOG.info("Project: id: " + id);
		authorizeUser(request);

		try {
			final Project project = ProjectService.getProject(id, includeMembers);
			return new ResponseEntity<>(project, HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}

	}

	/**
	 * Returns the teams assigned to a project.
	 *
	 * @param id the project ID
	 * @return the project teams
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/project/{id}/teams", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Get teams for project.  This call requires authentication with the correct role.", tags = {
			"project" }, responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "id", description = "Project id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<ResultList<Team>> getProjectTeams(@PathVariable(value = "id") final String id)
			throws Exception {

		LOG.info("Project: id: " + id);
		authorizeUser(request);

		try {

			final ResultList<Team> results = ProjectService.getProjectTeams(id);
			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Search Projects.
	 *
	 * @param searchParameters   the search parameters
	 * @param bindingResult      the binding result
	 * @param includeMembers     the include members
	 * @param includeModuleNames Include names of modules for the edition
	 * @param includeTeamDetails the include team details
	 * @return the string
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/project/search", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Find projects. This call requires authentication with the correct role.", description = API_NOTES, tags = {
			"project" }, responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found"),
					@ApiResponse(responseCode = "417", description = "Expectation failed") })
	// @ModelAttribute API params documented in SearchParameter
	@Parameters({
			@Parameter(name = "includeMembers", description = "Include project's members (users)", required = false, example = "false"),
			@Parameter(name = "includeModuleNames", description = "Include names of modules for the edition", required = false, example = "false"),
			@Parameter(name = "includeTeamDetails", description = "Include id and name of assigned teams", required = false, example = "false"), })
	@RecordMetric
	public @ResponseBody ResponseEntity<ResultList<Project>> getProjects(
			@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult,
			@RequestParam(value = "includeMembers", defaultValue = "false") final boolean includeMembers,
			@RequestParam(value = "includeModuleNames", required = false, defaultValue = "false") final Boolean includeModuleNames,
			@RequestParam(value = "icludeTeamDetails", required = false, defaultValue = "false") final Boolean includeTeamDetails)
			throws Exception {

		authorizeUser(request);

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		final boolean addModuleName = includeModuleNames != null && includeModuleNames;
		final boolean addTeamDetails = includeTeamDetails != null && includeTeamDetails;

		try {
			final User authUser = authorizeUser(request);
			final ResultList<Project> results = ProjectService.searchProjects(authUser, searchParameters);

			if (results == null || results.getItems() == null || results.getItems().isEmpty()) {
				return new ResponseEntity<>(results, HttpStatus.OK);
			}

			if (includeMembers || addTeamDetails || addModuleName) {

				for (final Project project : results.getItems()) {

					if (addModuleName) {
						project.getEdition().setModuleNames(ProjectService.getModuleNames(project));
					}

					final Set<User> members = new HashSet<>();

					for (final String teamId : project.getTeams()) {

						final Team team = TeamService.getTeam(teamId, includeMembers);

						if (includeMembers) {
							members.addAll(team.getMemberList());
						}

						if (addTeamDetails) {
							project.getTeamDetails().add(new IdName(team.getId(), team.getName()));
						}
					}

					if (includeMembers) {
						project.getMemberList().addAll(members);
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
	 * Add the project.
	 *
	 * @param project the project
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/project", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Add project.  This call requires authentication with the correct role.", tags = {
			"project" }, responses = { @ApiResponse(responseCode = "201", description = "Successfully create project"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Not Found"),
					@ApiResponse(responseCode = "409", description = "Conflict"),
					@ApiResponse(responseCode = "417", description = "Failed Expectation") }, requestBody = @RequestBody(description = "Project to add", required = true, content = {
							@Content(mediaType = "application/json", schema = @Schema(implementation = Project.class)) }))
	@RecordMetric
	public @ResponseBody ResponseEntity<Project> addProject(
			@org.springframework.web.bind.annotation.RequestBody final Project project) throws Exception {

		final User authUser = authorizeUser(request);

		try {

			if (project == null) {
				throw new RestException(false, 417, "Expectation failed", "Project payload is missing");
			}

			final Set<String> projectNames = ProjectService.getProjectNamesForEdition(project.getEdition().getId());

			if (projectNames.contains(project.getName())) {
				throw new RestException(false, 417, "Expectation failed",
						"A project with the name " + project.getName() + " already exists for this edition.");
			}

			try {

				project.validateAdd();
			} catch (final Exception e) {
				final String errorMessage = "Project validation failed for add. Message: " + e.getMessage();
				throw new RestException(false, 417, "Expectation failed", errorMessage);
			}

			final Project localProject = ProjectService.addProject(authUser, project);

			if (crowdUnitTestSkip == null || !"true".equalsIgnoreCase(crowdUnitTestSkip)) {
				LOG.info("CALLING CROWD API");
				try {

					final String organizationName = project.getEdition().getOrganizationName();
					final String editionName = project.getEdition().getShortName();

					CrowdAPIClient.addGroup(organizationName, editionName, localProject.getName(),
							localProject.getDescription(), true, false);

				} catch (final Exception e) {
					final String errorMessage = "Failed adding Crowd groups. Message: " + e.getMessage();
					throw new RestException(false, 417, "Expectation failed", errorMessage);
				}

			} else {
				LOG.info("SKIP CALLING CROWD API");
			}

			// Return the response
			return ResponseEntity.status(HttpStatus.CREATED).body(localProject);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Update project.
	 *
	 * @param id      the id
	 * @param project the project
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.PUT, value = "/project/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Update project.  This call requires authentication with the correct role.", tags = {
			"project" }, responses = { @ApiResponse(responseCode = "200", description = "Update specified project"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Not Found"),
					@ApiResponse(responseCode = "417", description = "Failed Expectation") }, requestBody = @RequestBody(description = "Project to update", required = true, content = {
							@Content(mediaType = "application/json", schema = @Schema(implementation = Project.class)) }))
	@Parameters({ @Parameter(name = "id", description = "Project id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<Project> updateProject(@PathVariable(value = "id") final String id,
			@org.springframework.web.bind.annotation.RequestBody final Project project) throws Exception {

		final User authUser = authorizeUser(request);

		if (project == null || !org.apache.commons.lang3.StringUtils.equals(id, project.getId())) {

			final String errorMessage = "Project is null or project id does not match id in URL.";
			throw new RestException(false, 404, "Not found", errorMessage);
		}

		try {

			project.validateUpdate(null);
		} catch (final Exception e) {
			throw new RestException(false, 417, "Expectation failed", "Project validation failed = " + project.getId());
		}

		try {

			final Project proj = ProjectService.updateProjects(authUser, id, project);
			return ResponseEntity.status(HttpStatus.OK).body(proj);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Logical delete (inactivate) the project.
	 *
	 * @param id the id
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.DELETE, value = "/project/{id}")
	@Operation(summary = "Inactivate project.  This call requires authentication with the correct role.", tags = {
			"project" }, responses = {
					@ApiResponse(responseCode = "202", description = "Successfully inactivated project"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Not Found"),
					@ApiResponse(responseCode = "417", description = "Failed Expectation") })
	@Parameters({ @Parameter(name = "id", description = "Project id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	public ResponseEntity<Void> deleteProject(@PathVariable("id") final String id) throws Exception {

		final User authUser = authorizeUser(request);

		ProjectService.getProject(id, false);

		try {

			ProjectService.inactivateProject(authUser, id);
			return new ResponseEntity<>(HttpStatus.ACCEPTED);

		} catch (final NotFoundException nfe) {
			return new ResponseEntity<>(new HttpHeaders(), HttpStatus.NOT_FOUND);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}

	}
}
