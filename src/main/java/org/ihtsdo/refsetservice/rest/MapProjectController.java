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
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.MapProjectService;
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
 * Controller for /mapproject endpoints.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class MapProjectController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapProjectController.class);

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /** Search mapProjects API note. */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    /**
     * Update mapProject.
     *
     * @param id the id
     * @param mapProject the mapProject
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.POST, value = "/mapproject", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Create mapping project.  This call requires authentication with the correct role.", tags = {
        "project"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Create specified mapping project"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    }, requestBody = @RequestBody(description = "Map Project to create", required = true, content = {
        @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MapProject.class))
    }))
    @RecordMetric
    public @ResponseBody ResponseEntity<MapProject> createMapProject(@org.springframework.web.bind.annotation.RequestBody final MapProject mapProject)
        throws Exception {

        // final User authUser = authorizeUser(request);

        try {
            mapProject.validateAdd();
        } catch (final Exception e) {
            LOG.error("Project validation failed = " + mapProject.toString());
            throw new RestException(false, 417, "Expectation failed", "Validation failed = " + mapProject.toString());
        }

        try (final TerminologyService service = new TerminologyService()) {

            final User tempUser = new User("testUser", "testPassword", "testRole", "testOrganization", "testEmail", null);

            service.setModifiedBy(tempUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final MapProject mapProj = MapProjectService.createMapProject(service, tempUser, mapProject);

            service.commit();

            return ResponseEntity.status(HttpStatus.CREATED).body(mapProj);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }

    }

    /**
     * Returns a specific mapProject.
     *
     * @param id the mapProject ID
     * @param includeMembers the include members
     * @return the mapProject
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapproject/{id}", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get mapProject.  This call requires authentication with the correct role.", tags = {
        "project"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found")
    })
    @Parameters({
        @Parameter(name = "id", description = "Project id, e.g. &lt;uuid&gt;", required = true),
        @Parameter(name = "includeMembers", description = "Include mapProject's members (users)", required = false, example = "false")
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<MapProject> getMapProject(@PathVariable(value = "id") final String id,
        @RequestParam(value = "includeMembers", defaultValue = "false") final boolean includeMembers) throws Exception {

        LOG.info("Project: id: " + id);
        //authorizeUser(request);

        try (final TerminologyService service = new TerminologyService()) {
            final MapProject mapProject = MapProjectService.getMapProject(service, id, includeMembers);
            return new ResponseEntity<>(mapProject, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

    }

    /**
     * Returns the teams assigned to a mapProject.
     *
     * @param id the mapProject ID
     * @return the mapProject teams
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapproject/{id}/teams", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get teams for mapProject.  This call requires authentication with the correct role.", tags = {
        "project"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found")
    })
    @Parameters({
        @Parameter(name = "id", description = "Map Project id, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<ResultList<Team>> getMapProjectTeams(@PathVariable(value = "id") final String id) throws Exception {

        LOG.info("Map Project: id: " + id);
        authorizeUser(request);

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<Team> results = MapProjectService.getMapProjectTeams(service, id);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }

    }

    /**
     * Search Map Projects.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @param includeMembers the include members
     * @param includeModuleNames Include names of modules for the edition
     * @param includeTeamDetails the include team details
     * @return the string
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapproject/search", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Find mapProjects. This call requires authentication with the correct role.", description = API_NOTES, tags = {
        "project"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Expectation failed")
    })
    // @ModelAttribute API params documented in SearchParameter
    @Parameters({
        @Parameter(name = "includeMembers", description = "Include map mapProject's members (users)", required = false, example = "false"),
        @Parameter(name = "includeModuleNames", description = "Include names of modules for the edition", required = false, example = "false"),
        @Parameter(name = "includeTeamDetails", description = "Include id and name of assigned teams", required = false, example = "false"),
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<ResultList<MapProject>> getMapProjects(@ModelAttribute final SearchParameters searchParameters,
        final BindingResult bindingResult, @RequestParam(value = "includeMembers", defaultValue = "false") final boolean includeMembers,
        @RequestParam(value = "includeModuleNames", required = false, defaultValue = "false") final Boolean includeModuleNames,
        @RequestParam(value = "icludeTeamDetails", required = false, defaultValue = "false") final Boolean includeTeamDetails) throws Exception {

        authorizeUser(request);

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        // final boolean addModuleName = includeModuleNames != null &&
        // includeModuleNames;
        // final boolean addTeamDetails = includeTeamDetails != null &&
        // includeTeamDetails;

        try (final TerminologyService service = new TerminologyService()) {

            final User tempUser = new User("testUser", "testPassword", "testRole", "testOrganization", "testEmail", null);

            // final User authUser = authorizeUser(request);
            final ResultList<MapProject> results = MapProjectService.searchMapProjects(service, tempUser, searchParameters);

            if (results == null || results.getItems() == null || results.getItems().isEmpty()) {
                return new ResponseEntity<>(results, HttpStatus.OK);
            }

            // if (includeMembers || addTeamDetails || addModuleName) {
            //
            // for (final MapProject mapProject : results.getItems()) {
            //
            // if (addModuleName) {
            // mapProject.getEdition().setModuleNames(MapProjectService.getModuleNames(mapProject.getEdition()));
            // }
            //
            // final Set<User> members = new HashSet<>();
            //
            // for (final String teamId : mapProject.getTeams()) {
            //
            // final Team team = TeamService.getTeam(teamId, includeMembers);
            //
            // if (includeMembers) {
            // members.addAll(team.getMemberList());
            // }
            //
            // if (addTeamDetails) {
            // mapProject.getTeamDetails().add(new IdName(team.getId(),
            // team.getName()));
            // }
            // }
            //
            // if (includeMembers) {
            // mapProject.getMemberList().addAll(members);
            // }
            // }
            // }

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }

    }

    /**
     * Update mapProject.
     *
     * @param id the id
     * @param mapProject the mapProject
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.PUT, value = "/mapproject/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Update mapProject.  This call requires authentication with the correct role.", tags = {
        "project"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Update specified mapProject"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    }, requestBody = @RequestBody(description = "Map Project to update", required = true, content = {
        @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MapProject.class))
    }))
    @Parameters({
        @Parameter(name = "id", description = "Map Project id, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<MapProject> updateMapProject(@PathVariable(value = "id") final String id,
        @org.springframework.web.bind.annotation.RequestBody final MapProject mapProject) throws Exception {

        // final User authUser = authorizeUser(request);

        if (mapProject == null || !StringUtils.equals(id, mapProject.getId())) {

            final String errorMessage = "Map project is null or mapProject id does not match id in URL.";
            throw new RestException(false, 404, "Not found", errorMessage);
        }

        try {

            mapProject.validateUpdate(null);
        } catch (final Exception e) {
            throw new RestException(false, 417, "Expectation failed", "Project validation failed = " + mapProject.getId());
        }

        try (final TerminologyService service = new TerminologyService()) {

            final User tempUser = new User("testUser", "testPassword", "testRole", "testOrganization", "testEmail", null);

            service.setModifiedBy(tempUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final MapProject mapProj = MapProjectService.updateMapProject(service, tempUser, id, mapProject);

            service.commit();

            return ResponseEntity.status(HttpStatus.OK).body(mapProj);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }

    }

    /**
     * Logical delete (inactivate) the mapProject.
     *
     * @param id the id
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.DELETE, value = "/mapproject/{id}")
    @Operation(summary = "Inactivate mapProject.  This call requires authentication with the correct role.", tags = {
        "project"
    }, responses = {
        @ApiResponse(responseCode = "202", description = "Successfully inactivated mapProject"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Not Found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "id", description = "Project id, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public ResponseEntity<Void> deleteMapProject(@PathVariable("id") final String id) throws Exception {

        // final User authUser = authorizeUser(request);

        try (final TerminologyService service = new TerminologyService()) {

            final User tempUser = new User("testUser", "testPassword", "testRole", "testOrganization", "testEmail", null);

            service.setModifiedBy(tempUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            MapProjectService.getMapProject(service, id, false);
            MapProjectService.inactivateMapProject(service, tempUser, id);

            service.commit();
            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final NotFoundException nfe) {
            return new ResponseEntity<>(new HttpHeaders(), HttpStatus.NOT_FOUND);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

    }
}
