/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.Objects;

/**
 * The Class SnowstormBranch.
 */
public class BranchInformation {

    /** The edition branch. */
    private String editionBranch;

    /** The refset id. */
    private String refsetId;

    /** The edit branch id. */
    private String editBranchId;

    /** The refset branch id. */
    private String refsetBranchId;

    /** The local set. */
    private boolean localSet;

    /** The branch id. */
    private String branchId;

    /**
     * Instantiates a new snowstorm branch.
     *
     * @param editionBranch the edition branch
     * @param refsetId the refset id
     * @param editBranchId the edit branch id
     * @param refsetBranchId the refset branch id
     * @param localSet the local set
     */
    public BranchInformation(final String editionBranch, final String refsetId, final String editBranchId, final String refsetBranchId, final boolean localSet) {

        this.editionBranch = editionBranch;
        this.refsetId = refsetId;
        this.editBranchId = editBranchId;
        this.refsetBranchId = refsetBranchId;
        this.localSet = localSet;
    }

    /**
     * Instantiates a new branch.
     *
     * @param refset the refset
     */
    public BranchInformation(final Refset refset) {

        this.editionBranch = refset.getEditionBranch();
        this.refsetId = refset.getRefsetId();
        this.editBranchId = refset.getEditBranchId();
        this.refsetBranchId = refset.getRefsetBranchId();
        this.localSet = refset.isLocalSet();
    }

    /**
     * Instantiates a new branch.
     *
     * @param mapSet the map set
     */
    public BranchInformation(final MapSet mapSet) {

        this.editionBranch = mapSet.getEditionBranch();
        this.refsetId = mapSet.getRefSetCode();
        this.editBranchId = mapSet.getEditBranchId();
        this.refsetBranchId = mapSet.getRefsetBranchId();
        this.localSet = mapSet.isLocalSet();
    }
    


    /**
     * Gets the edition branch.
     *
     * @return the edition branch
     */
    public String getEditionBranch() {

        return editionBranch;
    }

    /**
     * Sets the edition branch.
     *
     * @param editionBranch the new edition branch
     */
    public void setEditionBranch(final String editionBranch) {

        this.editionBranch = editionBranch;
    }

    /**
     * Gets the refset id.
     *
     * @return the refset id
     */
    public String getRefsetId() {

        return refsetId;
    }

    /**
     * Sets the refset id.
     *
     * @param refsetId the new refset id
     */
    public void setRefsetId(final String refsetId) {

        this.refsetId = refsetId;
    }

    /**
     * Gets the edits the branch id.
     *
     * @return the edits the branch id
     */
    public String getEditBranchId() {

        return editBranchId;
    }

    /**
     * Sets the edits the branch id.
     *
     * @param editBranchId the new edits the branch id
     */
    public void setEditBranchId(final String editBranchId) {

        this.editBranchId = editBranchId;
    }

    /**
     * Gets the refset branch id.
     *
     * @return the refset branch id
     */
    public String getRefsetBranchId() {

        return refsetBranchId;
    }

    /**
     * Sets the refset branch id.
     *
     * @param refsetBranchId the new refset branch id
     */
    public void setRefsetBranchId(final String refsetBranchId) {

        this.refsetBranchId = refsetBranchId;
    }

    /**
     * Checks if is local set.
     *
     * @return true, if is local set
     */
    public boolean isLocalSet() {

        return localSet;
    }

    /**
     * Sets the local set.
     *
     * @param localSet the new local set
     */
    public void setLocalSet(final boolean localSet) {

        this.localSet = localSet;
    }

    /**
     * @return the branchId
     */
    public String getBranchId() {

        return branchId;
    }

    /**
     * @param branchId the branchId to set
     */
    public void setBranchId(final String branchId) {

        this.branchId = branchId;
    }

    @Override
    public int hashCode() {

        return Objects.hash(branchId, editBranchId, editionBranch, localSet, refsetBranchId, refsetId);
    }

    @Override
    public boolean equals(final Object obj) {

        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final BranchInformation other = (BranchInformation) obj;
        return Objects.equals(branchId, other.branchId) && Objects.equals(editBranchId, other.editBranchId)
            && Objects.equals(editionBranch, other.editionBranch) && localSet == other.localSet && Objects.equals(refsetBranchId, other.refsetBranchId)
            && Objects.equals(refsetId, other.refsetId);
    }

    @Override
    public String toString() {

        return "Branch [editionBranch=" + editionBranch + ", refsetId=" + refsetId + ", editBranchId=" + editBranchId + ", refsetBranchId=" + refsetBranchId
            + ", localSet=" + localSet + ", branchId=" + branchId + "]";
    }

}
