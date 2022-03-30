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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;

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

    /** Search users API notes */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    /** The request. */
    @Autowired
    HttpServletRequest request;

    /**
     * Returns the user.
     *
     * @param id the id of the user
     * @return the user
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/user/{id}")
    public @ResponseBody ResponseEntity<User> getUser(@PathVariable(value = "id") final String id) throws Exception {

        try {
            logger.info("Get user: {}", id);
            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final User loginUser = service.get(id, User.class);
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
    @PutMapping("/user/{id}")
    public @ResponseBody ResponseEntity<User> updateUser(@PathVariable(value = "id") final String id, @RequestBody final User user) throws Exception {

        try {
            logger.info("Update user: {}", user);
            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User authUser = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                // Find the user
                final User original = service.get(user.getId(), User.class);

                if (original == null) {
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find user for " + user.getId() + ".");
                }

                service.setModifiedBy(user.getId());
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                // Apply changes
                original.patchFrom(user);

                // Update
                service.update(original);
                service.commit();

                return new ResponseEntity<>(original, HttpStatus.OK);
            }
        } catch (final Exception e) {
            logger.error("Error updating user.  Id: {}", id);
            handleException(e);
            return null;
        }
    }
}
