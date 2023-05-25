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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Represents parameters for a taxonomy call.
 */
@JsonInclude(Include.NON_EMPTY)
public class ConceptLookupParameters {

    /** The single concept request. */
    private boolean singleConceptRequest = false;

    /** The get descriptions. */
    private boolean getDescriptions = false;

    /** The get parents. */
    private boolean getParents = false;

    /** The get children. */
    private boolean getChildren = false;

    /** The get role groups. */
    private boolean getRoleGroups = false;

    /** The get membership information. */
    private boolean getMembershipInformation = false;

    /** The get fsn. */
    private boolean getFsn = false;

    /** The non default preferred terms. */
    private List<String> nonDefaultPreferredTerms = new ArrayList<>();

    /**
     * Instantiates an empty {@link ConceptLookupParameters}.
     */
    public ConceptLookupParameters() {

        // n/a
    }

    /**
     * Indicates whether or not single concept request is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public boolean isSingleConceptRequest() {

        return singleConceptRequest;
    }

    /**
     * Sets the single concept request.
     *
     * @param singleConceptRequest the single concept request
     */
    public void setSingleConceptRequest(final boolean singleConceptRequest) {

        this.singleConceptRequest = singleConceptRequest;
    }

    /**
     * Indicates whether or not returns the descriptions is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public boolean isGetDescriptions() {

        return getDescriptions;
    }

    /**
     * Sets the returns the descriptions.
     *
     * @param getDescriptions the returns the descriptions
     */
    public void setGetDescriptions(final boolean getDescriptions) {

        this.getDescriptions = getDescriptions;
    }

    /**
     * Indicates whether or not returns the parents is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public boolean isGetParents() {

        return getParents;
    }

    /**
     * Sets the returns the parents.
     *
     * @param getParents the returns the parents
     */
    public void setGetParents(final boolean getParents) {

        this.getParents = getParents;
    }

    /**
     * Indicates whether or not returns the children is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public boolean isGetChildren() {

        return getChildren;
    }

    /**
     * Sets the returns the children.
     *
     * @param getChildren the returns the children
     */
    public void setGetChildren(final boolean getChildren) {

        this.getChildren = getChildren;
    }

    /**
     * Indicates whether or not returns the fsn is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public boolean isGetFsn() {

        return getFsn;
    }

    /**
     * Sets the returns the fsn.
     *
     * @param getFsn the returns the fsn
     */
    public void setGetFsn(final boolean getFsn) {

        this.getFsn = getFsn;
    }

    /**
     * Indicates whether or not returns the role groups is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public boolean isGetRoleGroups() {

        return getRoleGroups;
    }

    /**
     * Sets the returns the role groups.
     *
     * @param getRoleGroups the returns the role groups
     */
    public void setGetRoleGroups(final boolean getRoleGroups) {

        this.getRoleGroups = getRoleGroups;
    }

    /**
     * Indicates whether or not returns the membership information is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public boolean isGetMembershipInformation() {

        return getMembershipInformation;
    }

    /**
     * Sets the returns the membership information.
     *
     * @param getMembershipInformation the returns the membership information
     */
    public void setGetMembershipInformation(final boolean getMembershipInformation) {

        this.getMembershipInformation = getMembershipInformation;
    }

    /**
     * Returns the non default preferred terms.
     *
     * @return the non default preferred terms
     */
    public List<String> getNonDefaultPreferredTerms() {

        return nonDefaultPreferredTerms;
    }

    /**
     * Sets the non default preferred terms.
     *
     * @param nonDefaultPreferredTerms the non default preferred terms
     */
    public void setNonDefaultPreferredTerms(final List<String> nonDefaultPreferredTerms) {

        this.nonDefaultPreferredTerms = nonDefaultPreferredTerms;
    }

    /**
     * Instantiates a {@link ConceptLookupParameters} from the specified parameters.
     *
     * @param other the other
     */
    public ConceptLookupParameters(final ConceptLookupParameters other) {

        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final ConceptLookupParameters other) {

        singleConceptRequest = other.isSingleConceptRequest();
        getDescriptions = other.isGetDescriptions();
        getParents = other.isGetParents();
        getChildren = other.isGetChildren();
        getFsn = other.isGetFsn();
        getRoleGroups = other.isGetRoleGroups();
        getMembershipInformation = other.isGetMembershipInformation();
        nonDefaultPreferredTerms = other.getNonDefaultPreferredTerms();
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

        final ConceptLookupParameters other = (ConceptLookupParameters) obj;

        if (nonDefaultPreferredTerms == null) {

            if (other.nonDefaultPreferredTerms != null) {
                return false;
            }

        } else if (!nonDefaultPreferredTerms.equals(other.nonDefaultPreferredTerms)) {
            return false;
        }

        if (singleConceptRequest != other.singleConceptRequest) {
            return false;
        }

        if (getDescriptions != other.getDescriptions) {
            return false;
        }

        if (getParents != other.getParents) {
            return false;
        }

        if (getChildren != other.getChildren) {
            return false;
        }

        if (getFsn != other.getFsn) {
            return false;
        }

        if (getRoleGroups != other.getRoleGroups) {
            return false;
        }

        if (getMembershipInformation != other.getMembershipInformation) {
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
        result = prime * result + ((nonDefaultPreferredTerms == null) ? 0 : nonDefaultPreferredTerms.hashCode());
        result = prime * result + (singleConceptRequest ? 1 : 0);
        result = prime * result + (getDescriptions ? 1 : 0);
        result = prime * result + (getParents ? 1 : 0);
        result = prime * result + (getChildren ? 1 : 0);
        result = prime * result + (getFsn ? 1 : 0);
        result = prime * result + (getRoleGroups ? 1 : 0);
        result = prime * result + (getMembershipInformation ? 1 : 0);
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
