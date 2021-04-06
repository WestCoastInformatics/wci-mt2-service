
package org.ihtsdo.refsetservice.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utilities for handling the "include" flag, and converting EVSConcept to
 * Concept.
 */
@Component
public final class TerminologyUtils {

    /** The Constant logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(TerminologyUtils.class);

    /**
     * Instantiates an empty {@link TerminologyUtils}.
     */
    private TerminologyUtils() {
        // n/a
    }

    // TBD - mostly an example of injecting a component utility class

}
