
package org.ihtsdo.refsetservice.rest;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.ihtsdo.refsetservice.model.AuthContext;
import org.ihtsdo.refsetservice.model.RestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.server.ResponseStatusException;

/**
 * Base controller for error handling.
 */
@CrossOrigin(origins = {
        "http://localhost:4200", "http://localhost:8888", "http://local.ihtsdotools.org:8888",
        "https://dev-rt2.ihtsdotools.org", "https://uat-rt2.ihtsdotools.org", "https://rt2.ihtsdotools.org"
}, allowCredentials = "true")
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
        if (e instanceof ResponseStatusException || e instanceof RestException) {
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
     * Authorize.
     *
     * @param request the request
     * @return the auth context
     * @throws Exception the exception
     */
    public AuthContext authorize(final HttpServletRequest request) throws Exception {
        // TODO finish authorize logic
        return null;
    }
}
