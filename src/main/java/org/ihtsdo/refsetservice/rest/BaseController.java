
package org.ihtsdo.refsetservice.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.ihtsdo.refsetservice.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.server.ResponseStatusException;

/**
 * Base controller for error handling.
 */
public class BaseController {

    /** The Constant log. */
    private static Logger logger = LoggerFactory.getLogger(BaseController.class);

    /**
     * Handle exception.
     *
     * @param e the e
     * @throws Exception the exception
     */
    public void handleException(final Exception e) throws Exception {
        if (e instanceof ResponseStatusException) {
            throw e;
        }

        logger.error("Unexpected error", e);
        final String errorMessage =
                "Unexpected error occurred in the system. Please contact info@snomed.org";
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, errorMessage);
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

                final String errorMessage = "ERROR " + bindingResult.getObjectName() + " = "
                        + error.getField() + ", " + error.getCode();
                logger.error(errorMessage);
                errorMessages.add(errorMessage);
            }

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.join("\n ", errorMessages));
        }
    }
    
    /**
     * Get the user from the session.
     *
     * @return the user from the session or null
     * @throws Exception the exception
     */
    protected User getUserFromSession() throws Exception {
        
        User user = new User("testUser", "Test User", "tuser@testuser.com", new HashSet<String>(Arrays.asList("rt-all-user", "rt-all-author", "rt-all-reviewer")));
        return user;
    }
    
    /**
     * Set the user into the session.
     *
     * @param the user to set in the session
     * @throws Exception the exception
     */
    protected void setUserInSession(final User user) throws Exception {
        
        // code to set the user in the session
    }
}
