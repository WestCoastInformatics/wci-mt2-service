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

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.server.ResponseStatusException;

/**
 * Base controller for error handling.
 */
@CrossOrigin(origins = {
    "http://localhost:4200", "http://localhost:8888", "http://local.ihtsdotools.org:8888", "https://dev-rt2.ihtsdotools.org", "https://uat-rt2.ihtsdotools.org",
    "https://rt2.ihtsdotools.org"
}, allowCredentials = "true")
public class BaseController {

    /** The Constant log. */
    private static final Logger LOG = LoggerFactory.getLogger(BaseController.class);

    /**
     * Handle exception.
     *
     * @param exception the e
     * @return the ResponseEntity
     * @throws Exception the exception
     */
    @SuppressWarnings("rawtypes")
    public ResponseEntity handleException(final Exception exception) throws Exception {

        if (exception instanceof ResponseStatusException) {

            final ResponseStatusException responseStatusException = (ResponseStatusException) exception;
            return ResponseEntity.status(responseStatusException.getRawStatusCode()).body(responseStatusException.getReason());

        } else if (exception instanceof RestException) {

            final RestException restException = (RestException) exception;
            final String message = (restException.getError().getMessage() != null ? restException.getError().getMessage(): restException.getMessage());            
            return ResponseEntity.status(restException.getError().getStatus()).body(message);

        } else {

            LOG.error("Unexpected error", exception);
            final String errorMessage = "Unexpected error occurred in the system. Please contact info@snomed.org";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
        }
    }

    /**
     * Check to make sure parameters were properly bound to variables.
     *
     * @param bindingResult the binding result
     * @throws Exception the exception
     */
    public void checkBinding(final BindingResult bindingResult) throws Exception {

        // Check whether or not parameter binding was successful
        if (bindingResult.hasErrors()) {

            final List<FieldError> errors = bindingResult.getFieldErrors();
            final List<String> errorMessages = new ArrayList<>();

            for (final FieldError error : errors) {

                final String errorMessage = "ERROR " + bindingResult.getObjectName() + " = " + error.getField() + ", " + error.getCode();
                LOG.error(errorMessage);
                errorMessages.add(errorMessage);
            }

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("\n ", errorMessages));
        }
    }

    /**
     * Authorize.
     *
     * @return the user
     * @throws Exception the exception
     */
    public User authorizeUser() throws Exception {

        final User authUser = SecurityService.getUserFromSession();

        if (authUser == null || (authUser.getId() == null && !PropertyUtility.getProperty("springProfiles").toLowerCase().contains("test"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return authUser;
    }

}
