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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Represents parameters for a taxonomy call.
 */
@JsonInclude(Include.NON_EMPTY)
public class TaxonomyParameters {

    /**  The Constant SNOMED_ROOT_CONCEPT_ID. */
    private static final String SNOMED_ROOT_CONCEPT_ID = "138875005";

    /** The starting concept ID (exclusive - get the children of this concept not the concept itself). */
    private String startingConceptId = SNOMED_ROOT_CONCEPT_ID;

    /** The language to return. */
    private String language;

    /** The depth - how many levels of children or parents to retrieve. */
    private Integer depth;

    /** Should children be returned. If false then parents will be returned */
    private Boolean returnChildren = true;

    /** Should the starting concept be looked up and returned with the children under it. If false then only children will be returned */
    private Boolean returnStartingConcept = false;

    /**
     * Instantiates an empty {@link TaxonomyParameters}.
     */
    public TaxonomyParameters() {

        // n/a
    }

    /**
     * Instantiates a {@link TaxonomyParameters} from the specified parameters.
     *
     * @param other the other
     */
    public TaxonomyParameters(final TaxonomyParameters other) {

        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final TaxonomyParameters other) {

        startingConceptId = other.getStartingConceptId();
        language = other.getLanguage();
        depth = other.getDepth();
        returnChildren = other.getReturnChildren();
        returnStartingConcept = other.getReturnStartingConcept();
    }

    /**
     * Returns the starting concept ID (exclusive - get the children of this concept not the concept itself).
     *
     * @return the starting concept ID
     */
    public String getStartingConceptId() {

        return startingConceptId;
    }

    /**
     * Sets the starting concept ID.
     *
     * @param startingConceptId the starting concept ID (exclusive - get the children of this concept not the concept itself)
     */
    public void setStartingConceptId(final String startingConceptId) {

        this.startingConceptId = startingConceptId;
    }

    /**
     * Returns the language.
     *
     * @return the language
     */
    public String getLanguage() {

        return language;
    }

    /**
     * Sets the language.
     *
     * @param language the language
     */
    public void setLanguage(final String language) {

        this.language = language;
    }

    /**
     * Returns the depth - how many levels of children or parents to retrieve.
     *
     * @return the depth
     */
    public Integer getDepth() {

        return depth;
    }

    /**
     * Sets the depth - how many levels of children or parents to retrieve.
     *
     * @param depth the depth - how many levels of children or parents to retrieve
     */
    public void setDepth(final Integer depth) {

        this.depth = depth;
    }

    /**
     * Should children be returned. If false then parents will be returned
     *
     * @return the return children flag
     */
    public Boolean getReturnChildren() {

        return returnChildren;
    }

    /**
     * Sets returnChildren flag.
     *
     * @param returnChildren Should children be returned. If false then parents will be returned
     */
    public void setReturnChildren(final Boolean returnChildren) {

        this.returnChildren = returnChildren;
    }

    /**
     * Should the starting concept be returned. If false then only children will be returned.
     *
     * @return the return starting concept flag
     */
    public Boolean getReturnStartingConcept() {

        return returnStartingConcept;
    }

    /**
     * Sets returnStartingConcept flag.
     *
     * @param returnStartingConcept Should the starting concept be returned. If false then only children will be returned.
     */
    public void setReturnStartingConcept(final Boolean returnStartingConcept) {

        this.returnStartingConcept = returnStartingConcept;
    }

    /**
     * Sets the sort ascending.
     *
     * @param obj the obj
     * @return true, if successful
     */
    @Override
    public boolean equals(final Object obj) {

        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final TaxonomyParameters other = (TaxonomyParameters) obj;

        if (startingConceptId == null) {

            if (other.startingConceptId != null) {
                return false;
            }

        } else if (!startingConceptId.equals(other.startingConceptId)) {
            return false;
        }

        if (language == null) {

            if (other.language != null) {
                return false;
            }

        } else if (!language.equals(other.language)) {
            return false;
        }

        if (depth == null) {

            if (other.depth != null) {
                return false;
            }

        } else if (!depth.equals(other.depth)) {
            return false;
        }

        if (returnChildren != other.returnChildren) {
            return false;
        }

        if (returnStartingConcept != other.returnStartingConcept) {
            return false;
        }

        return true;
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((startingConceptId == null) ? 0 : startingConceptId.hashCode());
        result = prime * result + ((language == null) ? 0 : language.hashCode());
        result = prime * result + ((depth == null) ? 0 : depth.hashCode());
        result = prime * result + (returnChildren ? 1 : 0);
        result = prime * result + (returnStartingConcept ? 1 : 0);
        return result;
    }

    /**
     * To string.
     *
     * @return the string
     */
    /* see superclass */
    @Override
    public String toString() {

        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }
}
