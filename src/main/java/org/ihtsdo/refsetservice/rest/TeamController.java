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
import javax.ws.rs.NotFoundException;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.ResultListUser;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.ihtsdo.refsetservice.terminologyservice.UserService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
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
 * Controller for /team endpoints.
 */
@RestController
@Api(tags = "Team endpoints")
@SuppressWarnings("javadoc")
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class TeamController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(TeamController.class);

    /** Search teams API notes. */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    /** The config properties. */
    private static final Properties PROPERTIES = PropertyUtility.getProperties();

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /**
     * Returns the team.
     *
     * @param id the id of the team
     * @param includeMembers the include members
     * @return the team
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/team/{id}")
    public @ResponseBody ResponseEntity<Team> getTeam(@PathVariable(value = "id") final String id, @QueryParam(value = "includeMembers") final boolean includeMembers) throws Exception {

        logger.info("Get team: {}", id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try {
            final Team team = TeamService.getTeam(id, includeMembers);
            return new ResponseEntity<>(team, HttpStatus.OK);

        } catch (final NotFoundException nfe) {

            logger.error("Error getting team. Id {} not found", id);
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

        } catch (final Exception e) {
            logger.error("Error getting team.  Id: {}", id);
            handleException(e);
            return null;
        }
    }

    /**
     * Search teams.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @param includeMembers the include members
     * @param onlyUsersTeams return only the teams the user is a member off or has permission to admin
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get teams search results", response = ResultList.class, notes = API_NOTES)
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
    @RequestMapping(method = RequestMethod.GET, value = "/team/search", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<Team>> getTeams(final SearchParameters searchParameters, final BindingResult bindingResult,
        @QueryParam(value = "includeMembers") final boolean includeMembers, @QueryParam(value = "onlyUsersTeams") final boolean onlyUsersTeams) throws Exception {

        logger.info("Search teams includeMembers: {} ; searchParameters: {}", includeMembers, ModelUtility.toJson(searchParameters));
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            final ResultList<Team> results = TeamService.searchTeams(authUser, searchParameters, includeMembers, onlyUsersTeams);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {
            logger.error("Error searching teams.  Search criteria: {} ", searchParameters.toString());
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
    @SuppressWarnings("rawtypes")
    @ApiOperation(value = "Add project", response = Project.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Organization successfully created"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/team", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity addTeam(@RequestBody final Team team) throws Exception {

        logger.info("Add team: {}", team);

        try {
            // TODO check permissions, fail if not authorized.
            final User authUser = SecurityService.getUserFromSession();
            if (authUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
            }

            if (team == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing team");
            }

            try {
                team.validateAdd();
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
            }

            final Team t = TeamService.createTeam(authUser, team);
            return ResponseEntity.status(HttpStatus.CREATED).body(t);

        } catch (final Exception e) {
            logger.error("Error adding team.  Team: {}", team.toString());
            handleException(e);
            return null;
        }
    }

    /**
     * Update the team.
     *
     * @param id the id of the team
     * @param team the team
     * @return the response entity
     * @throws Exception the exception
     */
    @SuppressWarnings("rawtypes")
    @ApiOperation(value = "Update team", response = Team.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Team successfully updated"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PutMapping(value = "/team/{id}", consumes = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity updateTeam(@PathVariable(value = "id") final String id, @RequestBody final Team team) throws Exception {

        logger.info("Update team: {}", team);
        // TODO check permissions, fail if not authorized.
        try {

            final User authUser = SecurityService.getUserFromSession();
            if (authUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
            }

            if (team == null || !org.apache.commons.lang3.StringUtils.equals(id, team.getId())) {
                final String errorMessage = "Team is null or team id does not match id in URL.";
                logger.error(errorMessage);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
            }

            try {
                team.validateUpdate(null);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
            }

            final Team t = TeamService.updateTeam(authUser, team);
            return new ResponseEntity<>(t, HttpStatus.OK);

        } catch (final NotFoundException nfe) {

            logger.error("Error updating team. Id {} not found", id);
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

        } catch (final Exception e) {
            logger.error("Error updating team. Team: {}", team);
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
    @ApiOperation(value = "Get the users for the specified team", response = ResultListUser.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Team identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataTypeClass = String.class, paramType = "path") // ,
    })
    @RecordMetric
    @RequestMapping(value = "/team/{id}/users", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<Object> getOrganizationUsers(@PathVariable final String id) throws Exception {

        logger.info("Get team users. Id: {}", id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try {
            final ResultListUser users = TeamService.getTeamUsers(authUser, id);
            return new ResponseEntity<>(users, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Adds the user to the team.
     *
     * @param teamId the team id
     * @param email the user email
     * @return the response entity
     * @throws Exception the exception
     */
    @PostMapping("/team/{teamId}/member")
    public @ResponseBody ResponseEntity<String> addUserToTeam(@PathVariable final String teamId, final String email) throws Exception {

        logger.info("Add user {} to team: {}", email, teamId);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try {

            final Team team = TeamService.addUserToTeam(authUser, teamId, email);

            // add user to crowd groups
            // crowd.unit.test.skip=true
            if (PROPERTIES.getProperty("crowd.unit.test.skip") == null || !"true".equalsIgnoreCase(PROPERTIES.getProperty("crowd.unit.test.skip"))) {
                final Organization organization = team.getOrganization();
                final User user = UserService.getUserByEmail(email);

                logger.info("CALLING CROWD API");
                final String teamsQuery = "teams:" + teamId;
                final SearchParameters searchParameters = new SearchParameters();
                searchParameters.setQuery(teamsQuery);
                final ResultList<Project> projectList = ProjectService.searchProjects(authUser, searchParameters);

                if (projectList != null && projectList.getItems() != null) {
                    for (Project project : projectList.getItems()) {
                        for (String role : team.getRoles()) {
                            final String groupName = CrowdGroupNameAlgorithm.generateCrowdGroupName(organization.getEdition().getShortName(), project.getCrowdProjectId(), role);
                            CrowdAPIClient.addMembership(groupName, user.getUserName());
                        }
                    }
                }
            } else {
                logger.info("SKIP CALLING CROWD API");
            }

            return new ResponseEntity<>(HttpStatus.CREATED);

        } catch (final NotFoundException nfe) {

            return new ResponseEntity<>(nfe.getMessage(), HttpStatus.NOT_FOUND);

        } catch (final Exception e) {

            logger.error("Error adding user: {} to team: {}", email, teamId);
            handleException(e);
            return null;
        }
    }

    /**
     * Removes the user from team.
     *
     * @param teamId the team id
     * @param userId the user id
     * @return the response entity
     * @throws Exception the exception
     */
    @DeleteMapping("/team/{teamId}/member/{userId}")
    public @ResponseBody ResponseEntity<Void> removeUserFromTeam(@PathVariable final String teamId, @PathVariable final String userId) throws Exception {

        logger.info("Remove user {} from team: {}", userId, teamId);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try {

            final Team team = TeamService.removeUserFromTeam(authUser, teamId, userId);
            final User user = UserService.getUser(userId, false);

            // remove user from crowd groups
            // crowd.unit.test.skip=true
            if (PROPERTIES.getProperty("crowd.unit.test.skip") == null || !"true".equalsIgnoreCase(PROPERTIES.getProperty("crowd.unit.test.skip"))) {
                logger.info("CALLING CROWD API");
                final Organization organization = team.getOrganization();
                final String teamsQuery = "teams:" + teamId;
                final SearchParameters searchParameters = new SearchParameters();
                searchParameters.setQuery(teamsQuery);
                final ResultList<Project> projectList = ProjectService.searchProjects(authUser, searchParameters);

                if (projectList != null && projectList.getItems() != null) {
                    for (Project project : projectList.getItems()) {
                        for (String role : team.getRoles()) {
                            final String groupName = CrowdGroupNameAlgorithm.generateCrowdGroupName(organization.getEdition().getShortName(), project.getCrowdProjectId(), role);
                            CrowdAPIClient.deleteMembership(groupName, user.getUserName());
                        }
                    }
                }
            } else {
                logger.info("SKIP CALLING CROWD API");
            }

            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final NotFoundException nfe) {

            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

        } catch (final Exception e) {
            logger.error("Error removing user: {} from team: {}", userId, teamId);
            handleException(e);
            return null;
        }
    }

    /**
     * Adds the role to the team.
     *
     * @param teamId the team id
     * @param role the role
     * @return the response entity
     * @throws Exception the exception
     */
    @PostMapping("/team/{teamId}/role/{role}")
    public @ResponseBody ResponseEntity<Void> addRoleToTeam(@PathVariable final String teamId, @PathVariable final String role) throws Exception {

        logger.info("Add role {} to team {}", role, teamId);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try {

            TeamService.addRoleToTeam(authUser, teamId, role);
            return new ResponseEntity<>(HttpStatus.CREATED);

        } catch (final Exception e) {
            logger.error("Error adding role: {} to team: {}", role, teamId);
            handleException(e);
            return null;
        }
    }

    /**
     * Remove the role from the team.
     *
     * @param teamId the team id
     * @param role the role
     * @return the response entity
     * @throws Exception the exception
     */
    @DeleteMapping("/team/{teamId}/role/{role}")
    public @ResponseBody ResponseEntity<Void> removeRoleFromTeam(@PathVariable final String teamId, @PathVariable final String role) throws Exception {

        logger.info("Remove role {} from team: {}", role, teamId);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try {

            TeamService.removeRoleFromTeam(authUser, teamId, role);
            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            logger.error("Error removing role: {} from team: {}", role, teamId);
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
    @ApiOperation(value = "Inactivate a team")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Inactivate specified team"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @DeleteMapping(value = "/team/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable("id") final String id) throws Exception {

        logger.info("Inactivate team: {}", id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try {

            TeamService.inactivateTeam(authUser, id);
            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final NotFoundException nfe) {

            logger.error("Error inactivating team. Id {} not found", id);
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

        } catch (final Exception e) {
            logger.error("Error inactivating team.  Id: {}", id);
            handleException(e);
            return null;
        }
    }
}