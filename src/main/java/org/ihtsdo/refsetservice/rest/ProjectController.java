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

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.IdName;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
 * Controller for /project endpoints.
 */
@RestController
@OpenAPIDefinition(info = @Info(title = "Project Controller", version = "1.0.0", description = "Endpoints for creating, retrieving, updating, and deleting projects."), tags = {
		@Tag(name = "project", description = "Artifact service endpoints") }, servers = {
				@Server(description = "Current Instance", url = "/") })
public class ProjectController extends BaseController {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(ProjectController.class);

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
	@SuppressWarnings("rawtypes")
	@Operation(summary = "Get project.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Project id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "includeMembers", description = "Include project's members (users)", required = false, example = "false") })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/project/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity getProject(@PathVariable(value = "id") final String id,
			@RequestParam(value = "includeMembers", defaultValue = "false") final boolean includeMembers)
			throws Exception {

		LOG.info("Project: id: " + id);
		authorizeUser();

		try {

			final Project project = ProjectService.getProject(id, includeMembers);
			return new ResponseEntity<>(project, HttpStatus.OK);

		} catch (final NotFoundException npe) {

			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(npe.getMessage());

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

	@SuppressWarnings("rawtypes")
	@Operation(summary = "Get teams for project.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })

	@Parameters({ @Parameter(name = "id", description = "Project id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/project/{id}/teams", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity getProjectTeams(@PathVariable(value = "id") final String id) throws Exception {

		LOG.info("Project: id: " + id);
		authorizeUser();

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
	@Operation(summary = "Find projects. This call requires authentication with the correct role.", description = API_NOTES, responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	// @ModelAttribute API params documented in SearchParameter
	@Parameters({
			@Parameter(name = "includeMembers", description = "Include project's members (users)", required = false, example = "false"),
			@Parameter(name = "includeModuleNames", description = "Include names of modules for the edition", required = false, example = "false"),
			@Parameter(name = "includeTeamDetails", description = "Include id and name of assigned teams", required = false, example = "false"), })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/project/search", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<Project>> getProjects(
			@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult,
			@RequestParam(value = "includeMembers", defaultValue = "false") final boolean includeMembers,
			@RequestParam(value = "includeModuleNames", required = false, defaultValue = "false") final Boolean includeModuleNames,
			@RequestParam(value = "icludeTeamDetails", required = false, defaultValue = "false") final Boolean includeTeamDetails)
			throws Exception {

		authorizeUser();

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		final boolean addModuleName = includeModuleNames != null && includeModuleNames;
		final boolean addTeamDetails = includeTeamDetails != null && includeTeamDetails;

		try {

			LOG.debug("getProjects searchParameters: " + ModelUtility.toJson(searchParameters));

			final User user = SecurityService.getUserFromSession();
			final ResultList<Project> results = ProjectService.searchProjects(user, searchParameters);

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
	@SuppressWarnings("rawtypes")
	@Operation(summary = "Add project.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "201", description = "Successfully create project"),
			@ApiResponse(responseCode = "400", description = "Bad Request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "409", description = "Conflict"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "project", description = "Project object", required = true) })
	@RecordMetric
	@PostMapping(value = "/project", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity addProject(@RequestBody final Project project) throws Exception {

		LOG.info("Add project: {}", project);
		final User authUser = authorizeUser();

		try {

			if (project == null) {

				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing project");
			}

			final Set<String> projectNames = ProjectService.getProjectNamesForEdition(project.getEdition().getId());

			if (projectNames.contains(project.getName())) {

				return ResponseEntity.status(HttpStatus.CONFLICT)
						.body("A project with the name " + project.getName() + " already exists for this edition.");
			}

			try {

				project.validateAdd();
			} catch (final Exception e) {

				final String errorMessage = "Project validation failed for add. Message: " + e.getMessage();
				LOG.error(errorMessage, e);
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
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
					LOG.error(errorMessage, e);
					return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body("");
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
	@SuppressWarnings("rawtypes")
	@Operation(summary = "Update project.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Update specified project"),
			@ApiResponse(responseCode = "400", description = "Bad Request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Project id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "project", description = "Project object", required = true) })
	@RecordMetric
	@PutMapping(value = "/project/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity updateProject(@PathVariable(value = "id") final String id,
			@RequestBody final Project project) throws Exception {

		LOG.info("Update project: {}", project);
		final User authUser = authorizeUser();

		if (project == null || !org.apache.commons.lang3.StringUtils.equals(id, project.getId())) {

			final String errorMessage = "Project is null or project id does not match id in URL.";
			LOG.error(errorMessage);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
		}

		try {

			project.validateUpdate(null);
		} catch (final Exception e) {

			LOG.error("Bad request for project update.", e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
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
	@SuppressWarnings("rawtypes")
	@Operation(summary = "Inactivate project.  This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "202", description = "Successfully inactivated project"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Project id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	@DeleteMapping(value = "/project/{id}", consumes = MediaType.APPLICATION_JSON)
	public ResponseEntity deleteProject(@PathVariable("id") final String id) throws Exception {

		LOG.info("Inactivate project: {}", id);
		final User authUser = authorizeUser();

		ProjectService.getProject(id, false);

		try {

			ProjectService.inactivateProject(authUser, id);
			return new ResponseEntity<>(HttpStatus.ACCEPTED);

		} catch (final NotFoundException nfe) {

			LOG.error("Error getting team. Id {} not found", id);
			return new ResponseEntity<>(new HttpHeaders(), HttpStatus.NOT_FOUND);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}

	}
}
