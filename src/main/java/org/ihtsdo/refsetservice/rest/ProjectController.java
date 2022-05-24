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

import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.AuthContext;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
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
    private static final Properties properties = PropertyUtility.getProperties();

    /** The request. */
    @Autowired
    HttpServletRequest request;

    // There is a duplicate in Refset Controller.
    // /**
    // * Return the project.
    // *
    // * @param id the id
    // * @return the project
    // * @throws Exception the exception
    // */
    // @ApiOperation(value = "Get the project for the specified identifier", response = Project.class)
    // @ApiResponses(value = {
    // @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
    // @ApiResponse(code = 404, message = "Resource not found")
    // })
    // @ApiImplicitParams({
    // @ApiImplicitParam(name = "id", value = "Project identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataTypeClass = String.class, paramType =
    // "path") // ,
    // })
    //
    // @RecordMetric
    // @RequestMapping(value = "/project/{id}", produces = MediaType.APPLICATION_JSON)
    // public @ResponseBody ResponseEntity<Project> getProject(@PathVariable(value = "id") final String id) throws Exception {
    //
    // try {
    // logger.info("Get project: {}", id);
    // // TODO check permissions, fail if not authorized.
    // // final AuthContext context = authorize(request);
    // final User user = SecurityService.getUserFromSession();
    //
    // try (final TerminologyService service = new TerminologyService()) {
    // logger.debug("get project: id: {}", id);
    // final Project project = service.get(id, Project.class);
    // return new ResponseEntity<>(project, HttpStatus.OK);
    // }
    // } catch (final Exception e) {
    // handleException(e);
    // return null;
    // }
    // }

    // There is a duplicate in Refset Controller.
    // /**
    // * Search Projects.
    // *
    // * @param searchParameters the search parameters
    // * @param bindingResult the binding result
    // * @return the string
    // * @throws Exception the exception
    // */
    // @ApiOperation(value = "Get project search results", response = ResultList.class, notes = API_NOTES)
    // @ApiResponses(value = {
    // @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
    // @ApiResponse(code = 404, message = "Resource not found")
    // })
    // @ApiImplicitParams({
    // @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType =
    // "query",
    // defaultValue = ""),
    // @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue =
    // "0"),
    // @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
    // // TODO: activeOnly, sort, sortAscending
    // })
    // @RecordMetric
    // @RequestMapping(method = RequestMethod.GET, value = "/project/search", produces = "application/json")
    // public @ResponseBody ResponseEntity<ResultList<Project>> getProjects(final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {
    //
    // try {
    // logger.info("Get projects: {}", searchParameters);
    // // TODO check permissions, fail if not authorized.
    // // final AuthContext context = authorize(request);
    // final User user = SecurityService.getUserFromSession();
    //
    // // Check to make sure parameters were properly bound to variables.
    // checkBinding(bindingResult);
    //
    // try {
    //
    // logger.debug("getProjects searchParameters: " + ModelUtility.toJson(searchParameters));
    // ResultList<Project> results = RefsetService.searchProjects(user, searchParameters);
    //
    // final HttpHeaders headers = new HttpHeaders();
    // return new ResponseEntity<>(results, headers, HttpStatus.OK);
    //
    // } catch (final ResponseStatusException rse) {
    // throw rse;
    //
    // }
    // } catch (final Exception e) {
    // handleException(e);
    // return null;
    // }
    // }

    /**
     * Add the project.
     *
     * @param project the project
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Add project", response = Project.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Project successfully created"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/project", consumes = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<Project> addProject(@RequestBody final Project project) throws Exception {

        logger.info("Add project: {}", project);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        final AuthContext context = authorize(request);

        try (final TerminologyService service = new TerminologyService()) {

            final Project localProject = (Project) project;

            localProject.setCrowdProjectId(CrowdGroupNameAlgorithm.getProjectString(localProject.getName()));
            service.setModifiedBy(authUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            try {
                localProject.validateAdd(context);
            } catch (final Exception e) {
                logger.error("Project validation failed for add. Message: {}.", e.getMessage(), e);
                return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
            }

            service.add(localProject);
            service.commit();
            // crowd.unit.test.skip=true
            if (properties.getProperty("crowd.unit.test.skip") == null || !"true".equalsIgnoreCase(properties.getProperty("crowd.unit.test.skip"))) {
                logger.info("CALLING CROWD API");

                try {
                    final Organization organization = project.getOrganization();
                    CrowdAPIClient.addGroup(organization.getEdition().getShortName(), localProject.getName(), localProject.getDescription());

                } catch (Exception e) {
                    logger.error("Failed adding Crowd groups. Message: {}.", e.getMessage(), e);
                    return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
                }
            } else {
                logger.info("SKIP CALLING CROWD API");
            }

            // Return the response
            return new ResponseEntity<>(localProject, HttpStatus.CREATED);

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
    @ApiOperation(value = "Update project")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Update specified project"), @ApiResponse(code = 400, message = "Bad Request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 403, message = "Forbidden"), @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PutMapping(value = "/project/{id}", consumes = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<Project> updateProject(@PathVariable(value = "id") final String id, @RequestBody final Project project) throws Exception {

        logger.info("Update project: {}", project);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        final AuthContext context = authorize(request);

        if (project == null || !StringUtils.equals(id, project.getId())) {
            logger.info("Project is null or project id does not match id in URL.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(authUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // Find the project
            final Project original = service.get(id, Project.class);

            if (original == null) {
                logger.info("Unable to find project for id {}.", id);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            try {
                original.validateUpdate(context, project);
            } catch (final Exception e) {
                logger.error("Project validation failed for update. Message: {}.", e.getMessage(), e);
                return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
            }

            // Apply changes
            original.patchFrom(project);

            // Update
            service.update(original);
            service.commit();
            return new ResponseEntity<>(original, HttpStatus.OK);

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
    @ApiOperation(value = "Inactivate project")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Inactivate specified project"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @DeleteMapping(value = "/project/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable("id") final String id) throws Exception {

        logger.info("Inactivate project: {}", id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(authUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // Find the object
            final Project project = service.get(id, Project.class);

            // not found - HttpStatus.NOT_FOUND
            if (project == null) {
                logger.info("Unable to find project for id {}.", id);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            // inactivate projects, clear teams, and inactivate refsets
            project.setActive(false);

            if (project.getTeams() != null && !project.getTeams().isEmpty()) {
                for (String teamId : project.getTeams()) {
                    final Team team = service.get(teamId, Team.class);
                    if (team != null && !team.getMembers().isEmpty()) {
                        team.getMembers().clear();
                        service.update(team);
                    }
                }
                project.getTeams().clear();
            }

            // also inactivate refsets
            final ResultList<Refset> projRefsets = service.find("projectId:" + project.getId() + " AND active:true", null, Refset.class, null);
            if (projRefsets.getItems() != null && !projRefsets.getItems().isEmpty()) {
                for (Refset refset : projRefsets.getItems()) {
                    if (refset != null && !projRefsets.getItems().isEmpty()) {
                        refset.setActive(false);
                        service.update(refset);
                    }
                }
            }

            service.update(project);
            service.commit();
            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            logger.error("Error inactivating project.  Id: {}", id, e);
            handleException(e);
            return null;
        }
    }

}