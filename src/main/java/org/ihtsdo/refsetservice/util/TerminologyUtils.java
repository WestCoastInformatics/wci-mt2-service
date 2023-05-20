/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for handling the "include" flag, and converting EVSConcept to Concept.
 */
public final class TerminologyUtils {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(TerminologyUtils.class);

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

        return (query.contains("<") || query.contains(">") || query.contains("^"));
    }
}
