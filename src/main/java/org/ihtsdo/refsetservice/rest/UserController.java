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

import java.io.File;
import java.nio.file.Files;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for /user endpoints.
 */
@RestController
@Api(tags = "User endpoints")
@SuppressWarnings("javadoc")
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class UserController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(UserController.class);

    /** Search users API notes. */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    /** The local icon file directory. */
    private static final String ICON_URL_PREFIX = "user/icon/";

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /**
     * Returns the user.
     *
     * @param id the id of the user
     * @param includeOrganizations the include organizations
     * @param includeTeams the include teams
     * @return the user
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the user for the specified identifier", response = User.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 403, message = "Forbidden"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "User identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataTypeClass = String.class, paramType = "path") // ,
    })
    @RecordMetric
    @RequestMapping(value = "/user/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<User> getUser(@PathVariable(value = "id") final String id, @QueryParam(value = "includeOrganizations") final boolean includeOrganizations,
        @QueryParam(value = "includeTeams") final boolean includeTeams) throws Exception {

        logger.info("Get user: {}", id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try {

            final User user = UserService.getUser(id, includeTeams);
            return new ResponseEntity<>(user, HttpStatus.OK);

        } catch (final NotFoundException nfe) {
            logger.error("Error getting user. Id {} not found.", id);
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

        } catch (final Exception e) {
            return handleException(e);
        }
    }

    /**
     * Update the user.
     *
     * @param id the id of the user
     * @param user the user
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Update User", response = User.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "User successfully updated"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 415, message = "Unsupported Media Type"),
        @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PutMapping(value = "/user/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<User> updateUser(@PathVariable(value = "id") final String id, @RequestBody final User user) throws Exception {

        logger.info("Update user: {}", user);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        if (user == null || !org.apache.commons.lang3.StringUtils.equals(id, user.getId())) {
            logger.info("User is null or user id does not match id in URL.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {

            final User original = UserService.updateUser(authUser, user);
            return new ResponseEntity<>(original, HttpStatus.OK);

        } catch (final NotFoundException nfe) {
            logger.error("Error getting user. Id {} not found.", id);
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

        } catch (final Exception e) {
            logger.error("Error updating user.  Id: {}", id, e);
            return handleException(e);
        }
    }
    
    
    /**
     * Delete user icon.
     *
     * @param id the id of the user
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Remove User icon", response = User.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "User successfully updated"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 415, message = "Unsupported Media Type"),
        @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @DeleteMapping(value = "/user/{id}/icon", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<User> deleteUserIcon(@PathVariable(value = "id") final String id) throws Exception {

        logger.info("Delete user icon: {}", id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        final User user = UserService.getUser(id, false);
        if (user == null || !org.apache.commons.lang3.StringUtils.equals(id, user.getId())) {
            logger.info("User is null or user id does not match id in URL.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {

            user.setIconUri(null);
            final User original = UserService.updateUser(authUser, user);
            return new ResponseEntity<>(original, HttpStatus.OK);

        } catch (final NotFoundException nfe) {
            logger.error("Error getting user. Id {} not found.", id);
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

        } catch (final Exception e) {
            logger.error("Error updating user.  Id: {}", id, e);
            return handleException(e);
        }
    }

    /**
     * Search users.
     *
     * @param includeOrganizations the include organizations
     * @param includeTeams the include teams
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get users search results", response = ResultList.class, notes = API_NOTES)
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
    @RequestMapping(method = RequestMethod.GET, value = "/user/search", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<User>> getUsers(@QueryParam(value = "includeOrganizations") final boolean includeOrganizations,
        @QueryParam(value = "includeTeams") final boolean includeTeams, final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        logger.info("Search users: {}", ModelUtility.toJson(searchParameters));
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            final ResultList<User> results = UserService.searchUsers(searchParameters);

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
            logger.error("Error searching organizations.  Search criteria: {} ", searchParameters.toString());
            return handleException(e);
        }
    }

    /**
     * Returns the user icon.
     *
     * @param fileName the file name
     * @return the user icon
     * @throws Exception the exception
     */
    @RequestMapping(value = "/user/icon/{fileName}", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Resource> getUserIcon(@PathVariable("fileName") final String fileName) throws Exception {

        try {

            logger.info("GET icon for user {}", fileName);
            final Resource file = FileUtility.getIconFile(fileName);

            return ResponseEntity.ok().header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(file.getFile().toPath())).contentLength(file.contentLength()).body(file);

        } catch (final Exception e) {

            logger.error("Trying to get user icon file " + fileName, e);
            return handleException(e);
        }
    }

    /**
     * Edit the user icon.
     *
     * @param userId the user id
     * @param inputFile the input file
     * @return the response entity with the icon URI
     * @throws Exception the exception
     */
    @ApiOperation(value = "edit icon for user")
    @ApiResponses(value = {
        @ApiResponse(code = 202, message = "Saved icon for user"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/user/{userId}/icon")
    public ResponseEntity<String> editUserIcon(@PathVariable("userId") final String userId, @RequestParam("file") MultipartFile inputFile) throws Exception {

        logger.info("Edit icon for user: {}.", userId);
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try {
            final User user = UserService.getUser(userId, false);
            if (user == null) {
                throw new RestException(false, 404, "Not found", "Unable to find user for " + userId);
            }

            String fileToDelete = "";

            if (user.getIconUri() != null) {
                fileToDelete = user.getIconUri().replace(ICON_URL_PREFIX, "");
            }

            final File file = FileUtility.saveIconFile(inputFile, userId, fileToDelete);
            user.setIconUri(ICON_URL_PREFIX + file.getName());
            UserService.updateUser(authUser, user);

            return new ResponseEntity<>("\"" + user.getIconUri() + "\"", HttpStatus.ACCEPTED);

        } catch (final Exception e) {

            logger.error("Trying to edit user icon for user " + userId, e);
            return handleException(e);
        }
    }

}
