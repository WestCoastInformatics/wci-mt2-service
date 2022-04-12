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

import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.UserRole;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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

    /** The request. */
    @Autowired
    HttpServletRequest request;

    /**
     * Returns the team.
     *
     * @param id the id of the team
     * @return the team
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/team/{id}")
    public @ResponseBody ResponseEntity<Team> getTeam(@PathVariable(value = "id") final String id) throws Exception {

        logger.info("Get team: {}", id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {
            final Team team = service.findSingle("id: " + id + " AND active:true", Team.class, null);

            if (team == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find team for id " + id + ".");
            }

            return new ResponseEntity<>(team, HttpStatus.OK);

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
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get teams search results", response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataType = "string", paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataType = "int", paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataType = "int", paramType = "query", defaultValue = "0")
        // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/team/search", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<Team>> getTeams(final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        logger.info("Search teams: {}", ModelUtility.toJson(searchParameters));
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            final ResultList<Team> results = RefsetService.searchTeams(authUser, searchParameters);
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
    @PostMapping("/team")
    public @ResponseBody ResponseEntity<Team> addTeam(@RequestBody final Team team) throws Exception {

        logger.info("Add team: {}", team);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            final Team t = (Team) team;

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.add(t);
            service.commit();

            final HttpHeaders headers = new HttpHeaders();
            return new ResponseEntity<>(team, headers, HttpStatus.CREATED);

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
    @ApiOperation(value = "Update team", response = Team.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Team successfully updated"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PutMapping(value = "/team/{id}", consumes = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<Team> updateTeam(@PathVariable(value = "id") final String id, @RequestBody final Team team) throws Exception {

        logger.info("Update team: {}", team);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            // Find the team
            final Team original = service.get(team.getId(), Team.class);

            if (original == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find team for " + team.getId() + ".");
            }

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // Apply changes
            original.patchFrom(team);

            // Update
            service.update(original);
            service.commit();

            return new ResponseEntity<>(original, HttpStatus.OK);

        } catch (final Exception e) {
            logger.error("Error upadting team.  Team: {}", team.toString());
            handleException(e);
            return null;
        }
    }

    /**
     * Adds the user to the team.
     *
     * @param teamId the team id
     * @param userId the user id
     * @return the response entity
     * @throws Exception the exception
     */
    @PostMapping("/team/{teamId}/member/{userId}")
    public @ResponseBody ResponseEntity<Void> addUserToTeam(@PathVariable final String teamId, @PathVariable final String userId) throws Exception {

        logger.info("Add user {} to team: {}", userId, teamId);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            final Team team = service.get(teamId, Team.class);
            if (team == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Unable to find team for " + teamId + ".");
            }

            final User member = service.get(userId, User.class);
            if (member == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Unable to find user for " + userId + ".");
            }

            if (team.getMembers() != null) {
                if (team.getMembers().contains(userId)) {
                    throw new RestException(false, HttpStatus.CONFLICT, "Conflict", "User " + userId + " is already a memeber of team " + teamId + ".");
                }
            }

            team.getMembers().add(userId);

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.update(team);
            service.commit();

            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            logger.error("Error adding user: {} to team: {}", userId, teamId);
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

        try (final TerminologyService service = new TerminologyService()) {

            // find team
            final Team team = service.get(teamId, Team.class);
            if (team == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Unable to find team for " + teamId + ".");
            }

            final User member = service.get(userId, User.class);
            if (member == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Unable to find user for " + userId + ".");
            }

            if (team.getMembers() != null) {
                if (team.getMembers().contains(userId)) {
                    team.getMembers().remove(userId);
                } else {
                    throw new RestException(false, HttpStatus.CONFLICT, "Conflict", "User " + userId + " is already a memeber of team " + teamId + ".");
                }
            }

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.update(team);
            service.commit();

            return new ResponseEntity<>(HttpStatus.ACCEPTED);

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

        try (final TerminologyService service = new TerminologyService()) {

            // find team
            final Team team = service.get(teamId, Team.class);
            if (team == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Unable to find team for " + teamId + ".");
            }

            if (StringUtils.isBlank(role) && !UserRole.allRoles.contains(role.toUpperCase())) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Role " + role + " does not exist.");
            }

            if (team.getRoles().contains(role.toUpperCase())) {
                throw new RestException(false, HttpStatus.CONFLICT, "Conflict", "Role " + role + " is already a exists for team " + teamId + ".");
            }

            team.getRoles().add(UserRole.valueOf(role).toString());

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.update(team);
            service.commit();

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

        try {
            logger.info("Remove role {} from team: {}", role, teamId);
            // TODO check permissions, fail if not authorized.
            final User authUser = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                // find team
                final Team team = service.get(teamId, Team.class);
                if (team == null) {
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Unable to find team for " + teamId + ".");
                }

                if (StringUtils.isBlank(role) && !Arrays.asList(UserRole.values()).contains(role.toUpperCase())) {
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Role " + role + " does not exist.");
                }

                if (!team.getRoles().contains(role.toUpperCase())) {
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Role " + role + " does not exist for team " + teamId + ".");
                }

                team.getRoles().remove(UserRole.valueOf(role).toString());

                service.setModifiedBy(authUser.getId());
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                service.update(team);
                service.commit();

                return new ResponseEntity<>(HttpStatus.ACCEPTED);
            }
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

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // Find the object
            final Team team = service.get(id, Team.class);

            if (team == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Unable to find team for id:" + id);
            }

            team.setActive(false);
            service.update(team);
            service.commit();
            // Return the status
            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            logger.error("Error inactivating team.  Id: {}", id);
            handleException(e);
            return null;
        }
    }
}