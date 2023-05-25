/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a comparison between two refsets.
 * 
 */
public class RefsetMemberComparison extends AbstractHasModified {

    /** The internal ID of the active refset. */
    private String activeRefsetInternalId;

    /** The internal ID of the comparison refset. */
    private String comparisonRefsetInternalId;

    /** The refset ID of the active refset. */
    private String activeRefsetId;

    /** The refset ID of the comparison refset. */
    private String comparisonRefsetId;

    /** The refset name of the active refset. */
    private String activeRefsetName;

    /** The refset name of the comparison refset. */
    private String comparisonRefsetName;

    /** A list of maps the members with keys of code, name, hasChildren, active, memberOfRefset, definitionExceptionType, and membership. */
    private List<Map<String, String>> items = new ArrayList<>();

    /** A list of members unique to the active refset. */
    private List<String> activeRefsetDistinctMembers = new ArrayList<>();

    /** A list of members unique to the active refset. */
    private List<String> comparisonRefsetDistinctMembers = new ArrayList<>();

    /** The member total of the active refset. */
    private int activeRefsetMemberTotal;

    /** The number of member unique to the active refset. */
    private int activeRefsetDistinctMembersCount;

    /** The member total of the comparison refset. */
    private int comparisonRefsetMemberTotal;

    /** The number of member unique to the comparison refset. */
    private int comparisonRefsetDistinctMembersCount;

    /**
     * Instantiates an empty {@link RefsetMemberComparison}.
     */
    public RefsetMemberComparison() {

        setActive(false);
    }

    /**
     * Instantiates a {@link RefsetMemberComparison} from the specified parameters.
     *
     * @param other the other
     */
    public RefsetMemberComparison(final RefsetMemberComparison other) {

        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final RefsetMemberComparison other) {

        super.populateFrom(other);
        activeRefsetInternalId = other.getActiveRefsetInternalId();
        comparisonRefsetInternalId = other.getComparisonRefsetInternalId();
        activeRefsetId = other.getActiveRefsetId();
        comparisonRefsetId = other.getComparisonRefsetId();
        activeRefsetName = other.getActiveRefsetName();
        comparisonRefsetName = other.getComparisonRefsetName();
        items = other.getItems();
        activeRefsetDistinctMembers = other.getActiveRefsetDistinctMembers();
        comparisonRefsetDistinctMembers = other.getComparisonRefsetDistinctMembers();
        activeRefsetMemberTotal = other.getActiveRefsetMemberTotal();
        activeRefsetDistinctMembersCount = other.getActiveRefsetDistinctMembersCount();
        comparisonRefsetMemberTotal = other.getComparisonRefsetMemberTotal();
        comparisonRefsetDistinctMembersCount = other.getComparisonRefsetDistinctMembersCount();
    }

    /**
     * Returns the internal ID of the active refset.
     *
     * @return the active refset internal ID
     */
    public String getActiveRefsetInternalId() {

        return activeRefsetInternalId;
    }

    /**
     * Sets the internal ID of the active refset.
     *
     * @param refsetInternalId the active refset internal id
     */
    public void setActiveRefsetInternalId(final String refsetInternalId) {

        this.activeRefsetInternalId = refsetInternalId;
    }

    /**
     * Returns the refset ID of the comparison refset.
     *
     * @return the comparison refset ID
     */
    public String getComparisonRefsetInternalId() {

        return comparisonRefsetInternalId;
    }

    /**
     * Sets the refset ID of the comparison refset.
     *
     * @param comparisonRefsetInternalId the comparison refset ID
     */
    public void setComparisonRefsetInternalId(final String comparisonRefsetInternalId) {

        this.comparisonRefsetInternalId = comparisonRefsetInternalId;
    }

    /**
     * Returns the refset ID of the active refset.
     *
     * @return the active refset ID
     */
    public String getActiveRefsetId() {

        return activeRefsetId;
    }

    /**
     * Sets the refset ID of the active refset.
     *
     * @param refsetId the active refset ID
     */
    public void setActiveRefsetId(final String refsetId) {

        this.activeRefsetId = refsetId;
    }

    /**
     * Returns the refset ID of the comparison refset.
     *
     * @return the comparison refset ID
     */
    public String getComparisonRefsetId() {

        return comparisonRefsetId;
    }

    /**
     * Sets the refset ID of the comparison refset.
     *
     * @param comparisonRefsetId the comparison refset ID
     */
    public void setComparisonRefsetId(final String comparisonRefsetId) {

        this.comparisonRefsetId = comparisonRefsetId;
    }

    /**
     * Returns the refset name of the active refset.
     *
     * @return the active refset name
     */
    public String getActiveRefsetName() {

        return activeRefsetName;
    }

    /**
     * Sets the refset name of the active refset.
     *
     * @param refsetName the active refset name
     */
    public void setActiveRefsetName(final String refsetName) {

        this.activeRefsetName = refsetName;
    }

    /**
     * Returns the refset name of the comparison refset.
     *
     * @return the comparison refset name
     */
    public String getComparisonRefsetName() {

        return comparisonRefsetName;
    }

    /**
     * Sets the refset name of the comparison refset.
     *
     * @param comparisonRefsetName the comparison refset name
     */
    public void setComparisonRefsetName(final String comparisonRefsetName) {

        this.comparisonRefsetName = comparisonRefsetName;
    }

    /**
     * Returns the list of maps of the members with keys of code, name, hasChildren, active, memberOfRefset, definitionExceptionType, and membership (values of
     * Both, Active Refset, Comparison Refset).
     *
     * @return the members
     */
    public List<Map<String, String>> getItems() {

        if (items == null) {
            items = new ArrayList<>();
        }

        return items;
    }

    /**
     * Sets the list of maps of the members with keys of code, name, hasChildren, active, memberOfRefset, definitionExceptionType, and membership (values of
     * Both, Active Refset, Comparison Refset).
     *
     * @param members the members
     */
    public void setItems(final List<Map<String, String>> members) {

        this.items = members;
    }

    /**
     * Returns the list of members unique to the active refset.
     *
     * @return the unique active refset members
     */
    public List<String> getActiveRefsetDistinctMembers() {

        if (activeRefsetDistinctMembers == null) {
            activeRefsetDistinctMembers = new ArrayList<>();
        }

        return activeRefsetDistinctMembers;
    }

    /**
     * Sets the list of members unique to the active refset.
     *
     * @param activeRefsetDistinctMembers the unique active refset members
     */
    public void setActiveRefsetDistinctMembers(final List<String> activeRefsetDistinctMembers) {

        this.activeRefsetDistinctMembers = activeRefsetDistinctMembers;
    }

    /**
     * Returns the list of members unique to the comparison refset.
     *
     * @return the unique comparison refset members
     */
    public List<String> getComparisonRefsetDistinctMembers() {

        if (comparisonRefsetDistinctMembers == null) {
            comparisonRefsetDistinctMembers = new ArrayList<>();
        }

        return comparisonRefsetDistinctMembers;
    }

    /**
     * Sets the list of members unique to the comparison refset.
     *
     * @param comparisonRefsetDistinctMembers the unique comparison refset members
     */
    public void setComparisonRefsetDistinctMembers(final List<String> comparisonRefsetDistinctMembers) {

        this.comparisonRefsetDistinctMembers = comparisonRefsetDistinctMembers;
    }

    /**
     * Returns the member total of the active refset.
     *
     * @return the active refset member total
     */
    public int getActiveRefsetMemberTotal() {

        return activeRefsetMemberTotal;
    }

    /**
     * Sets the member total of the active refset.
     *
     * @param activeRefsetMemberTotal the active refset member total
     */
    public void setActiveRefsetMemberTotal(final int activeRefsetMemberTotal) {

        this.activeRefsetMemberTotal = activeRefsetMemberTotal;
    }

    /**
     * Returns the number of members unique to the active refset.
     *
     * @return the active refset unique member count
     */
    public int getActiveRefsetDistinctMembersCount() {

        return activeRefsetDistinctMembersCount;
    }

    /**
     * Sets the number of members unique to the active refset.
     *
     * @param activeRefsetDistinctMembersCount the active refset unique member count
     */
    public void setActiveRefsetDistinctMembersCount(final int activeRefsetDistinctMembersCount) {

        this.activeRefsetDistinctMembersCount = activeRefsetDistinctMembersCount;
    }

    /**
     * Returns the member total of the comparison refset.
     *
     * @return the comparison refset member total
     */
    public int getComparisonRefsetMemberTotal() {

        return comparisonRefsetMemberTotal;
    }

    /**
     * Sets the member total of the comparison refset.
     *
     * @param comparisonRefsetMemberTotal the comparison refset member total
     */
    public void setComparisonRefsetMemberTotal(final int comparisonRefsetMemberTotal) {

        this.comparisonRefsetMemberTotal = comparisonRefsetMemberTotal;
    }

    /**
     * Returns the number of members unique to the comparison refset.
     *
     * @return the comparison refset unique member count
     */
    public int getComparisonRefsetDistinctMembersCount() {

        return comparisonRefsetDistinctMembersCount;
    }

    /**
     * Sets the number of members unique to the comparison refset.
     *
     * @param comparisonRefsetDistinctMembersCount the comparison refset unique member count
     */
    public void setComparisonRefsetDistinctMembersCount(final int comparisonRefsetDistinctMembersCount) {

        this.comparisonRefsetDistinctMembersCount = comparisonRefsetDistinctMembersCount;
    }

    /**
     * 
     * 
     * /** Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((activeRefsetInternalId == null) ? 0 : activeRefsetInternalId.hashCode());
        result = prime * result + ((comparisonRefsetInternalId == null) ? 0 : comparisonRefsetInternalId.hashCode());
        result = prime * result + ((activeRefsetId == null) ? 0 : activeRefsetId.hashCode());
        result = prime * result + ((comparisonRefsetId == null) ? 0 : comparisonRefsetId.hashCode());
        result = prime * result + ((activeRefsetName == null) ? 0 : activeRefsetName.hashCode());
        result = prime * result + ((comparisonRefsetName == null) ? 0 : comparisonRefsetName.hashCode());
        result = prime * result + ((items == null) ? 0 : items.hashCode());
        result = prime * result + ((activeRefsetDistinctMembers == null) ? 0 : activeRefsetDistinctMembers.hashCode());
        result = prime * result + ((comparisonRefsetDistinctMembers == null) ? 0 : comparisonRefsetDistinctMembers.hashCode());
        result = prime * result + activeRefsetMemberTotal;
        result = prime * result + activeRefsetDistinctMembersCount;
        result = prime * result + comparisonRefsetMemberTotal;
        result = prime * result + comparisonRefsetDistinctMembersCount;
        return result;
    }

    /**
     * Equals.
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

        final RefsetMemberComparison other = (RefsetMemberComparison) obj;

        if (activeRefsetInternalId == null) {
            if (other.activeRefsetInternalId != null) {
                return false;
            }
        } else if (!activeRefsetInternalId.equals(other.activeRefsetInternalId)) {
            return false;
        }

        if (comparisonRefsetInternalId == null) {
            if (other.comparisonRefsetInternalId != null) {
                return false;
            }
        } else if (!comparisonRefsetInternalId.equals(other.comparisonRefsetInternalId)) {
            return false;
        }

        if (activeRefsetId == null) {
            if (other.activeRefsetId != null) {
                return false;
            }
        } else if (!activeRefsetId.equals(other.activeRefsetId)) {
            return false;
        }

        if (comparisonRefsetId == null) {
            if (other.comparisonRefsetId != null) {
                return false;
            }
        } else if (!comparisonRefsetId.equals(other.comparisonRefsetId)) {
            return false;
        }

        if (activeRefsetName == null) {
            if (other.activeRefsetName != null) {
                return false;
            }
        } else if (!activeRefsetName.equals(other.activeRefsetName)) {
            return false;
        }

        if (comparisonRefsetName == null) {
            if (other.comparisonRefsetName != null) {
                return false;
            }
        } else if (!comparisonRefsetName.equals(other.comparisonRefsetName)) {
            return false;
        }

        if (items == null) {
            if (other.items != null) {
                return false;
            }
        } else if (!items.equals(other.items)) {
            return false;
        }

        if (activeRefsetDistinctMembers == null) {
            if (other.activeRefsetDistinctMembers != null) {
                return false;
            }
        } else if (!activeRefsetDistinctMembers.equals(other.activeRefsetDistinctMembers)) {
            return false;
        }

        if (comparisonRefsetDistinctMembers == null) {
            if (other.comparisonRefsetDistinctMembers != null) {
                return false;
            }
        } else if (!comparisonRefsetDistinctMembers.equals(other.comparisonRefsetDistinctMembers)) {
            return false;
        }

        if (activeRefsetMemberTotal != other.activeRefsetMemberTotal) {
            return false;
        }

        if (activeRefsetDistinctMembersCount != other.activeRefsetDistinctMembersCount) {
            return false;
        }

        if (comparisonRefsetMemberTotal != other.comparisonRefsetMemberTotal) {
            return false;
        }

        if (comparisonRefsetDistinctMembersCount != other.comparisonRefsetDistinctMembersCount) {
            return false;
        }

        return true;
    }

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {

        // n/a

    }
}
