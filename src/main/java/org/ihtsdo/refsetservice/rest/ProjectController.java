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
import javax.ws.rs.QueryParam;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for /project endpoints.
 */
@RestController
@Api(tags = "projects", description = "Endpoints for creating, retrieving, updating, and deleting projects.")
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
     * @param id the project ID
     * @param includeMembers the include members
     * @return the project
     * @throws Exception the exception
     */
    @SuppressWarnings("rawtypes")
    @ApiOperation(value = "Get project.  This call requires authentication with the correct role.", response = Project.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Project id, e.g. &lt;uuid&gt;", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "includeMembers", value = "Include project's members (users)", required = false, dataTypeClass = Boolean.class,
            paramType = "query", defaultValue = "false")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/project/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity getProject(@PathVariable(value = "id") final String id,
        @QueryParam(value = "includeMembers") final boolean includeMembers) throws Exception {

        LOG.info("Project: id: " + id);
        authorizeUser();

        try {

            final Project project = ProjectService.getProject(id, includeMembers);
            return new ResponseEntity<>(project, HttpStatus.OK);

        } catch (final NotFoundException npe) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(npe.getMessage());

        } catch (final Exception e) {

            LOG.error("Error fetching project.  Id: {}", id, e);
            return handleException(e);
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
    @ApiOperation(value = "Get teams for project.  This call requires authentication with the correct role.", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Project id, e.g. &lt;uuid&gt;", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/project/{id}/teams", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity getProjectTeams(@PathVariable(value = "id") final String id) throws Exception {

        LOG.info("Project: id: " + id);
        authorizeUser();

        try {

            final ResultList<Team> results = ProjectService.getProjectTeams(id);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Search Projects.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @param includeMembers the include members
     * @param includeModuleNames Include names of modules for the edition
     * @param includeTeamDetails the include team details
     * @return the string
     * @throws Exception the exception
     */
    @SuppressWarnings("unchecked")
    @ApiOperation(value = "Find projects. This call requires authentication with the correct role.", response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    // @ModelAttribute API params documented in SearchParameter
    @ApiImplicitParams({
        @ApiImplicitParam(name = "includeMembers", value = "Include project's members (users)", required = false, dataTypeClass = Boolean.class,
            paramType = "query", defaultValue = "false"),
        @ApiImplicitParam(name = "includeModuleNames", value = "Include names of modules for the edition", required = false, dataTypeClass = Boolean.class,
            paramType = "query", defaultValue = "false"),
        @ApiImplicitParam(name = "includeTeamDetails", value = "Include id and name of assigned teams", required = false, dataTypeClass = Boolean.class,
            paramType = "query", defaultValue = "false"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/project/search", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<Project>> getProjects(@ModelAttribute final SearchParameters searchParameters,
        final BindingResult bindingResult, @QueryParam(value = "includeMembers") final boolean includeMembers,
        @RequestParam(required = false) final Boolean includeModuleNames, @RequestParam(required = false) final Boolean includeTeamDetails) throws Exception {

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

            return handleException(e);
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
    @ApiOperation(value = "Add project.  This call requires authentication with the correct role.", response = Project.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Successfully create project"), @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"),
        @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "project", value = "Project object", required = true, dataTypeClass = Project.class, paramType = "body")
    })
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

                return ResponseEntity.status(HttpStatus.CONFLICT).body("A project with the name " + project.getName() + " already exists for this edition.");
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

                    CrowdAPIClient.addGroup(organizationName, editionName, localProject.getName(), localProject.getDescription(), true, false);

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

            LOG.error("Error adding project. {}", project == null ? null : project.toString(), e);
            return handleException(e);
        }

    }

    /**
     * Update project.
     *
     * @param id the id
     * @param project the project
     * @return the response entity
     * @throws Exception the exception
     */
    @SuppressWarnings("rawtypes")
    @ApiOperation(value = "Update project.  This call requires authentication with the correct role.", response = Project.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Update specified project"), @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Not Found"),
        @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Project id, e.g. &lt;uuid&gt;", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "project", value = "Project object", required = true, dataTypeClass = Project.class, paramType = "body")
    })
    @RecordMetric
    @PutMapping(value = "/project/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity updateProject(@PathVariable(value = "id") final String id, @RequestBody final Project project) throws Exception {

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

            LOG.error("Error updating project.  Id: {}", id, e);
            return handleException(e);
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
    @ApiOperation(value = "Inactivate project.  This call requires authentication with the correct role.")
    @ApiResponses(value = {
        @ApiResponse(code = 202, message = "Successfully inactivated project"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Project id, e.g. &lt;uuid&gt;", required = true, dataTypeClass = String.class, paramType = "path")
    })
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

            LOG.error("Error inactivating project.  Id: {}", id, e);
            return handleException(e);
        }

    }
}
