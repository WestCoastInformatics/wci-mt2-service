
package org.ihtsdo.refsetservice.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Base controller for error handling.
 */
public class BaseController {

    /** The Constant log. */
    private static final Logger logger = LoggerFactory.getLogger(BaseController.class);

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
}
