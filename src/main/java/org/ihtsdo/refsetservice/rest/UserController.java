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

import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.S3ConnectionWrapper;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
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
import org.springframework.web.server.ResponseStatusException;

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

    /**  Search users API notes. */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    /** The request. */
    @Autowired
    HttpServletRequest request;

    /**
     * Returns the user.
     *
     * @param id the id of the user
     * @param includeMembers the include members
     * @return the user
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/user/{id}")
    public @ResponseBody ResponseEntity<User> getUser(@PathVariable(value = "id") final String id, @QueryParam(value = "includeMembers") final boolean includeMembers) throws Exception {

        try {
            logger.info("Get user: {}", id);
            // TODO check permissions, fail if not authorized.
            final User authUser = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final User user = service.get(id, User.class);

                if (user == null) {
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find user for " + id + ".");
                }
                if (includeMembers) {
                    user.getOrganizations();
                } else {
                    if (user.getOrganizations() != null && !user.getOrganizations().isEmpty()) {
                        user.getOrganizations().clear();
                    }
                }

                return new ResponseEntity<>(user, HttpStatus.OK);
            }
        } catch (final Exception e) {
            handleException(e);
            return null;
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
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PutMapping(value = "/user/{id}", consumes = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<User> updateUser(@PathVariable(value = "id") final String id, @RequestBody final User user) throws Exception {

        logger.info("Update user: {}", user);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            // Find the user
            final User original = service.get(user.getId(), User.class);

            if (original == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find user for " + user.getId() + ".");
            }

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // Apply changes
            original.patchFrom(user);

            // Update
            service.update(original);
            service.commit();

            return new ResponseEntity<>(original, HttpStatus.OK);

        } catch (final Exception e) {
            logger.error("Error updating user.  Id: {}", id, e);
            handleException(e);
            return null;
        }
    }

    /**
     * Search users.
     *
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
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataType = "string", paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataType = "int", paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataType = "int", paramType = "query", defaultValue = "0")
        // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/user/search", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<User>> getUsers(final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        logger.info("Search users: {}", ModelUtility.toJson(searchParameters));
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            final ResultList<User> results = RefsetService.searchUsers(authUser, searchParameters);

            for (User user : results.getItems()) {
                user.getOrganizations().clear();
            }

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {
            logger.error("Error searching organizations.  Search criteria: {} ", searchParameters.toString());
            handleException(e);
            return null;
        }
    }

    /**
     * Adds the user icon.
     *
     * @param userId the user id
     * @param inputFile the input file
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Add icon for user")
    @ApiResponses(value = {
        @ApiResponse(code = 202, message = "Saved icon for user"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/user/{userId}/icon")
    public ResponseEntity<Void> addUserIcon(@PathVariable("userId") final String userId, @RequestParam("file") MultipartFile inputFile) throws Exception {

        logger.info("Add icon for user: {}.", userId);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            // find user record, return 404 if not found
            final User user = service.get(userId, User.class);
            if (user == null) {
                throw new RestException(false, 404, "Not found", "Unable to find user for " + userId);
            }

            // ensure file exists
            if (inputFile == null) {
                throw new RestException(false, 417, "Failed expectation", "Uploaded file is null");
            }

            // check for file
            final String fileName = inputFile.getOriginalFilename();
            if (fileName == null) {
                throw new RestException(false, 417, "Failed expectation", "Uploaded file has null filename");
            }

            // check file size
            if (inputFile.getSize() > 2000000) {
                throw new RestException(false, 413, "Failed expectation", "File size must be less than 2 MB");
            }

            final String extension = FileUtility.getFileExtension(StringUtils.cleanPath(fileName)).toLowerCase();

            if (!extension.contentEquals("jpg") && !extension.contentEquals("png") && !extension.contentEquals("svg")) {
                throw new RestException(false, 417, "Failed expectation", "Format must be .png or .jpg " + fileName);
            }

            final String storageDirectory = PropertyUtility.getProperty("refset.user.icon.file.dir");
            final String uri = storageDirectory + userId + "." + extension;

            logger.debug("Add user icon uploadUri = " + uri);

            try (InputStream is = inputFile.getInputStream()) {
                S3ConnectionWrapper.uploadToS3(uri, is);
            }

            final String iconUri = PropertyUtility.getProperty("refset.user.icon.url.prefix") + userId + "." + extension;

            user.setIconUri(iconUri);

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();
            service.update(user);
            service.commit();

            // Return the object
            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            logger.error("Trying to add user icon for user " + userId, e);
            handleException(e);
            return null;
        }

    }

    
    /**
     * Update user icon.
     *
     * @param userId the user id
     * @param inputFile the input file
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Update icon for user", response = User.class)
    @ApiResponses(value = {
        @ApiResponse(code = 202, message = "Updated icon for user"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PutMapping(value = "/user/{userId}/icon")
    public ResponseEntity<Void> updateUserIcon(@PathVariable("userId") final String userId, @RequestParam("file") MultipartFile inputFile) throws Exception {

        logger.info("Add icon for user: {}.", userId);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            // find user record, return 404 if not found
            final User user = service.get(userId, User.class);
            if (user == null) {
                throw new RestException(false, 404, "Not found", "Unable to find user for " + userId);
            }

            // ensure file exists
            if (inputFile == null) {
                throw new RestException(false, 417, "Failed expectation", "Uploaded file is null");
            }

            // check for file
            final String fileName = inputFile.getOriginalFilename();
            if (fileName == null) {
                throw new RestException(false, 417, "Failed expectation", "Uploaded file has null filename");
            }

            // check file size
            if (inputFile.getSize() > 2000000) {
                throw new RestException(false, 413, "Failed expectation", "File size must be less than 2 MB");
            }

            final String extension = FileUtility.getFileExtension(StringUtils.cleanPath(fileName)).toLowerCase();

            if (!extension.contentEquals("jpg") && !extension.contentEquals("png") && !extension.contentEquals("svg")) {
                throw new RestException(false, 417, "Failed expectation", "Format must be .png or .jpg " + fileName);
            }

            final String storageDirectory = PropertyUtility.getProperty("refset.user.icon.file.dir");
            final String uri = storageDirectory + userId + "." + extension;

            logger.debug("Adding user icon upload URI " + uri);

            try (InputStream is = inputFile.getInputStream()) {
                S3ConnectionWrapper.uploadToS3(uri, is);
            }

            final String iconUri = PropertyUtility.getProperty("refset.user.icon.url.prefix") + userId + "." + extension;

            user.setIconUri(iconUri);

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();
            service.update(user);
            service.commit();

            // Return the object
            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            logger.error("Trying to add user icon for organiation " + userId, e);
            handleException(e);
            return null;
        }

    }

}
