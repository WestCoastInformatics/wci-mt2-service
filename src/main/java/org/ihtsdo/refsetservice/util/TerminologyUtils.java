
package org.ihtsdo.refsetservice.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utilities for handling the "include" flag, and converting EVSConcept to
 * Concept.
 */
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

    /**
     * Indicates whether or not the query is ECL language.
     *
     * @param query The query string
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isQueryEcl(final String query) {
        
        if (query.contains("<") || query.contains(">") || query.contains("^")) {
            return true;
        } else {
            return false;
        }
    }
}
