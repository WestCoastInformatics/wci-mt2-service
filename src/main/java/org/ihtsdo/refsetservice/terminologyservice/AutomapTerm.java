/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

/**
 * One source term submitted to the automap service.
 */
public class AutomapTerm {

    /** Target terminology sent to automap. Hardcoded until map-set targets are mapped. */
    public static final String TO_TERMINOLOGY = "SNOMEDCT";

    /** Source term text. */
    private final String term;

    /** Automap entity type. */
    private final String entityType;

    /**
     * Instantiates a new automap term.
     *
     * @param term the source term
     * @param entityType the entity type
     */
    public AutomapTerm(final String term, final String entityType) {

        this.term = term;
        this.entityType = entityType;
    }

    /**
     * Returns the source term.
     *
     * @return the term
     */
    public String getTerm() {

        return term;
    }

    /**
     * Returns the target terminology.
     *
     * @return the target terminology
     */
    public String getToTerminology() {

        return TO_TERMINOLOGY;
    }

    /**
     * Returns the entity type.
     *
     * @return the entity type
     */
    public String getEntityType() {

        return entityType;
    }
}
