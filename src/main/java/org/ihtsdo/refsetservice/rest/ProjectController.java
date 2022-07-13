/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
@Api(tags = "Project endpoints")
@SuppressWarnings("javadoc")
public class ProjectController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(ProjectController.class);

    /** Search projects API note. */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    /** The config properties. */
    private static final Properties PROPERTIES = PropertyUtility.getProperties();

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /**
     * Returns a specific project.
     *
     * @param projectId the project ID
     * @param includeMembers the include members
     * @return the project
     * @throws Exception the exception
     */

    @SuppressWarnings("rawtypes")
    @ApiOperation(value = "Get the project for the specified ID", response = Project.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "projectId", value = "The ID of the project to return.", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/project/{projectId}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity getProject(@PathVariable(value = "projectId") final String projectId, @QueryParam(value = "includeMembers") final boolean includeMembers) throws Exception {

        logger.info("Project: projectId: " + projectId);
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }

        try {

            final Project project = ProjectService.getProject(projectId, includeMembers);
            return new ResponseEntity<>(project, HttpStatus.OK);

        } catch (final NotFoundException npe) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(npe.getMessage());

        } catch (final Exception e) {
            logger.error("Error fetching project.  Id: {}", projectId, e);
            handleException(e);
            return null;
        }
    }

    /**
     * Search Projects.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @param includeMembers the include members
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get project search results", response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/project/search", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResultList<Project> getProjects(final SearchParameters searchParameters, final BindingResult bindingResult, @QueryParam(value = "includeMembers") final boolean includeMembers)
        throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            logger.debug("getProjects searchParameters: " + ModelUtility.toJson(searchParameters));

            final User user = SecurityService.getUserFromSession();
            final ResultList<Project> results = ProjectService.searchProjects(user, searchParameters);

            if (includeMembers && results != null && results.getItems() != null) {
                for (final Project project : results.getItems()) {
                    final Set<User> members = new HashSet<>();
                    for (final String teamId : project.getTeams()) {
                        final Team team = TeamService.getTeam(teamId, includeMembers);
                        members.addAll(team.getMemberList());
                    }
                    project.getMemberList().addAll(members);
                }
            }

            return results;

        } catch (final ResponseStatusException rse) {
            throw rse;

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
    @ApiOperation(value = "Add project", response = Project.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Project successfully created"), @ApiResponse(code = 400, message = "Bad Request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 403, message = "Forbidden"), @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"),
        @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/project", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity addProject(@RequestBody final Project project) throws Exception {

        logger.info("Add project: {}", project);

        try {

            // TODO check permissions, fail if not authorized.
            final User authUser = SecurityService.getUserFromSession();
            if (authUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
            }

            if (project == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing project");
            }

            final Set<String> projectNames = ProjectService.getProjectNamesForOrganization(project.getOrganizationId());
            if (projectNames.contains(project.getName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("A project with the name " + project.getName() + " already exists for this organization.");
            }

            try {
                project.validateAdd();
            } catch (final Exception e) {
                final String errorMessage = "Project validation failed for add. Message: " + e.getMessage();
                logger.error(errorMessage, e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
            }

            final Project localProject = ProjectService.addProject(authUser, project);

            if (PROPERTIES.getProperty("crowd.unit.test.skip") == null || !"true".equalsIgnoreCase(PROPERTIES.getProperty("crowd.unit.test.skip"))) {
                logger.info("CALLING CROWD API");

                try {
                    final Organization organization = project.getOrganization();
                    CrowdAPIClient.addGroup(organization.getEdition().getShortName(), localProject.getName(), localProject.getDescription());

                } catch (Exception e) {
                    final String errorMessage = "Failed adding Crowd groups. Message: " + e.getMessage();
                    logger.error(errorMessage, e);
                    return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body("");
                }
            } else {
                logger.info("SKIP CALLING CROWD API");
            }

            // Return the response
            return ResponseEntity.status(HttpStatus.CREATED).body(localProject);

        } catch (final Exception e) {
            logger.error("Error adding project. {}", project.toString(), e);
            handleException(e);
            return null;
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
    @ApiOperation(value = "Update project", response = Project.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Update specified project"), @ApiResponse(code = 400, message = "Bad Request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 403, message = "Forbidden"), @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PutMapping(value = "/project/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity updateProject(@PathVariable(value = "id") final String id, @RequestBody final Project project) throws Exception {

        logger.info("Update project: {}", project);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        if (project == null || !org.apache.commons.lang3.StringUtils.equals(id, project.getId())) {
            final String errorMessage = "Project is null or project id does not match id in URL.";
            logger.error(errorMessage);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
        }

        try {
            project.validateUpdate(null);
        } catch (final Exception e) {
            logger.error("Bad request for project update.", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }

        try {

            final Project proj = ProjectService.updateProjects(authUser, id, project);
            return ResponseEntity.status(HttpStatus.OK).body(proj);

        } catch (final Exception e) {
            logger.error("Error updating project.  Id: {}", id, e);
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
    @ApiOperation(value = "Inactivate project")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Inactivate specified project"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @DeleteMapping(value = "/project/{id}", consumes = MediaType.APPLICATION_JSON)
    public ResponseEntity deleteProject(@PathVariable("id") final String id) throws Exception {

        logger.info("Inactivate project: {}", id);
        // TODO check permissions, fail if not authorized.
        final User user = SecurityService.getUserFromSession();
        final Project project = ProjectService.getProject(id, false);
        

        try {

            ProjectService.inactivateProject(user, id);
            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final NotFoundException nfe) {
            logger.error("Error getting team. Id {} not found", id);
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

        } catch (final Exception e) {
            logger.error("Error inactivating project.  Id: {}", id, e);
            handleException(e);
            return null;
        }
    }
}
