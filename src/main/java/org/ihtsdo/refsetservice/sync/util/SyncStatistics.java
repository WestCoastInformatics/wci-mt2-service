/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.sync.util;

/**
 * The Class SyncStatistics.
 */
public class SyncStatistics {

    /** The code systems synced. */

    // Terminology Server Code Systems
    private int codeSystemsSynced = 0;

    /** The code systems filtered. */
    private int codeSystemsFiltered = 0;

    /** The organizations added. */

    // Organizations
    private int organizationsAdded = 0;

    /** The organizations inactivated. */
    private int organizationsInactivated = 0;

    /** The organizations activated. */
    private int organizationsReactivated = 0;

    private int organizationsModified = 0;

    // Editions
    /** The editions added. */
    private int editionsAdded = 0;

    /** The editions inactivated. */
    private int editionsInactivated = 0;;

    /** The editions activated. */
    private int editionsReactivated = 0;

    /** The editions modified. */
    private int editionsModified = 0;;

    /** The edition organization map changed. */
    private int editionOrganizationMapChanged = 0;

    // Refsets Ids
    private int refsetIdsAdded = 0;

    /** The refset ids inactivated. */
    private int refsetIdsInactivated = 0;;

    /** The refset ids activated. */
    private int refsetIdsActivated = 0;

    /** The refset ids synced. */
    private int refsetIdsSynced = 0;

    // Refsets Versions
    /** The refset versions added. */
    private int refsetVersionsAdded = 0;

    /** The refset versions inactivated. */
    private int refsetVersionsInactivated = 0;;

    /** The refset versions activated. */
    private int refsetVersionsActivated = 0;

    /** The refset versions modified. */
    private int refsetVersionsModified = 0;

    /** The refset versions synced. */
    private int refsetVersionsSynced = 0;

    // Projects
    /** The projects added. */
    private int projectsAdded = 0;

    /** The projects modified. */
    private int projectsModified;

    // Teams
    /** The teams added. */
    private int teamsAdded = 0;

    /** The Constant CHANGED. */
    public static final String CHANGED = "Changed";

    /** The Constant UNCHANGED. */
    public static final String UNCHANGED = "Unchanged";

    /**
     * Prints the statistics.
     *
     * @return the string
     */
    public String printStatistics() {

        final StringBuffer buf = new StringBuffer();

        buf.append(System.getProperty("line.separator") + "*********    Syncing Results    *************" + System.getProperty("line.separator"));

        buf.append("Code systems encountered: " + codeSystemsSynced + ". Syncing " + codeSystemsFiltered + " after filtered them" + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Organizations
        buf.append(
            "*** Organizations --> Added: " + organizationsAdded + " / Inactivated: " + organizationsInactivated + " / Activated: " + organizationsReactivated + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Editions
        buf.append("*** EDITIONS --> Added: " + editionsAdded + " / Inactivated: " + editionsInactivated + " / Activated: " + editionsReactivated + " / Modified: " + editionsModified
            + System.getProperty("line.separator"));

        buf.append("*** Reassignment of editions-to-organization map --> Changes: " + editionOrganizationMapChanged + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Refsets
        buf.append("*** Unique REFSET Ids (" + refsetIdsSynced + " Synced" + ") --> Added: " + refsetIdsAdded + " / Inactivated: " + refsetIdsInactivated + " / Activated: " + refsetIdsActivated
            + System.getProperty("line.separator"));

        buf.append("*** REFSET VERSION Pairs (" + refsetVersionsSynced + " Synced" + ") --> Added: " + refsetVersionsAdded + " / Inactivated: " + refsetVersionsInactivated + " / Activated: "
            + refsetVersionsActivated + " / Modified: " + refsetVersionsModified + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Othera
        buf.append("*** Projects --> " + projectsAdded + " Processed " + System.getProperty("line.separator"));
        buf.append("*** Teams --> " + teamsAdded + " Processed" + System.getProperty("line.separator"));

        return buf.toString();
    }

    /**
     * Clear statistics.
     */
    public void clearStatistics() {

        codeSystemsSynced = 0;
        codeSystemsFiltered = 0;

        organizationsAdded = 0;
        organizationsReactivated = 0;
        organizationsInactivated = 0;

        editionsAdded = 0;
        editionsInactivated = 0;
        editionsReactivated = 0;
        editionsModified = 0;

        editionOrganizationMapChanged = 0;

        refsetIdsAdded = 0;
        refsetIdsInactivated = 0;
        refsetIdsActivated = 0;
        refsetIdsSynced = 0;

        refsetVersionsAdded = 0;
        refsetVersionsInactivated = 0;
        refsetVersionsActivated = 0;
        refsetVersionsModified = 0;
        refsetVersionsSynced = 0;

        projectsAdded = 0;
        teamsAdded = 0;

    }

    /**
     * Getters *.
     *
     * @return the code systems synced
     */
    // Code Systems

    public int getCodeSystemsSynced() {

        return codeSystemsSynced;
    }

    /**
     * Returns the code systems filtered.
     *
     * @return the code systems filtered
     */
    public int getCodeSystemsFiltered() {

        return codeSystemsFiltered;
    }

    /**
     * Returns the organizations added.
     *
     * @return the organizations added
     */
    // Organizations
    public int getOrganizationsAdded() {

        return organizationsAdded;
    }

    /**
     * Returns the organizations inactivated.
     *
     * @return the organizations inactivated
     */
    public int getOrganizationsInactivated() {

        return organizationsInactivated;
    }

    /**
     * Returns the organizations activated.
     *
     * @return the organizations activated
     */
    public int getOrganizationsReactivated() {

        return organizationsReactivated;
    }

    /**
     * Returns the editions added.
     *
     * @return the editions added
     */
    // Editions
    public int getEditionsAdded() {

        return editionsAdded;
    }

    /**
     * Returns the editions inactivated.
     *
     * @return the editions inactivated
     */
    public int getEditionsInactivated() {

        return editionsInactivated;
    }

    /**
     * Returns the editions activated.
     *
     * @return the editions activated
     */
    public int getEditionsReactivated() {

        return editionsReactivated;
    }

    /**
     * Returns the editions modified.
     *
     * @return the editions modified
     */
    public int getEditionsModified() {

        return editionsModified;
    }

    /**
     * Returns the edition organization map changed.
     *
     * @return the edition organization map changed
     */
    public int getEditionOrganizationMapChanged() {

        return editionOrganizationMapChanged;
    }

    /**
     * Returns the refset ids added.
     *
     * @return the refset ids added
     */
    // Refsets
    public int getRefsetIdsAdded() {

        return refsetIdsAdded;
    }

    /**
     * Returns the refset ids inactivated.
     *
     * @return the refset ids inactivated
     */
    public int getRefsetIdsInactivated() {

        return refsetIdsInactivated;
    }

    /**
     * Returns the refset ids activated.
     *
     * @return the refset ids activated
     */
    public int getRefsetIdsActivated() {

        return refsetIdsActivated;
    }

    /**
     * Returns the refset ids synced.
     *
     * @return the refset ids synced
     */
    public int getRefsetIdsSynced() {

        return refsetIdsSynced;
    }

    /**
     * Returns the refset versions added.
     *
     * @return the refset versions added
     */
    public int getRefsetVersionsAdded() {

        return refsetVersionsAdded;
    }

    /**
     * Returns the refset versions inactivated.
     *
     * @return the refset versions inactivated
     */
    public int getRefsetVersionsInactivated() {

        return refsetVersionsInactivated;
    }

    /**
     * Returns the refset versions activated.
     *
     * @return the refset versions activated
     */
    public int getRefsetVersionsActivated() {

        return refsetVersionsActivated;
    }

    /**
     * Returns the refset versions modified.
     *
     * @return the refset versions modified
     */
    public int getRefsetVersionsModified() {

        return refsetVersionsModified;
    }

    /**
     * Returns the refset versions synced.
     *
     * @return the refset versions synced
     */
    public int getRefsetVersionsSynced() {

        return refsetVersionsSynced;
    }

    /**
     * Setters *.
     *
     * @param val the code systems synced
     */
    // Code Systems
    public void setCodeSystemsSynced(final int val) {

        codeSystemsSynced = val;
    }

    /**
     * Sets the code systems filtered.
     *
     * @param val the code systems filtered
     */
    public void setCodeSystemsFiltered(final int val) {

        codeSystemsFiltered = val;
    }

    /**
     * Sets the organizations added.
     *
     * @param val the organizations added
     */
    // Organizations
    public void setOrganizationsAdded(final int val) {

        organizationsAdded = val;

    }

    /**
     * Sets the organizations inactivated.
     *
     * @param val the organizations inactivated
     */
    public void setOrganizationsInactivated(final int val) {

        organizationsInactivated = val;

    }

    /**
     * Sets the organizations activated.
     *
     * @param organizationsActivated the organizations activated
     */
    public void setOrganizationsActivated(final int organizationsActivated) {

        this.organizationsReactivated = organizationsActivated;
    }

    // Editions
    /**
     * Sets the editions added.
     *
     * @param val the editions added
     */
    public void setEditionsAdded(final int val) {

        this.editionsAdded = val;
    }

    /**
     * Sets the editions inactivated.
     *
     * @param val the editions inactivated
     */
    public void setEditionsInactivated(final int val) {

        editionsInactivated = val;
    }

    /**
     * Sets the editions activated.
     *
     * @param val the editions activated
     */
    public void setEditionsActivated(final int val) {

        editionsReactivated = val;

    }

    /**
     * Sets the editions modified.
     *
     * @param val the editions modified
     */
    public void setEditionsModified(final int val) {

        editionsModified = val;
    }

    // Refset Ids
    /**
     * Sets the refset ids added.
     *
     * @param val the refset ids added
     */
    public void setRefsetIdsAdded(final int val) {

        this.refsetIdsAdded = val;
    }

    /**
     * Sets the refset version pairs inactivated.
     *
     * @param val the refset version pairs inactivated
     */
    public void setRefsetIdsInactivated(final int val) {

        this.refsetIdsInactivated = val;
    }

    /**
     * Sets the refset ids activated.
     *
     * @param val the refset ids activated
     */
    public void setRefsetIdsActivated(final int val) {

        this.refsetIdsActivated = val;
    }

    /**
     * Sets the refset ids synced.
     *
     * @param val the refset ids synced
     */
    public void setRefsetIdsSynced(final int val) {

        this.refsetIdsSynced = val;
    }

    // Refset Version Pairs
    /**
     * Sets the refset versions added.
     *
     * @param val the refset versions added
     */
    public void setRefsetVersionsAdded(final int val) {

        refsetVersionsAdded = val;
    }

    /**
     * Increment refset versions added.
     */
    public void incrementRefsetVersionsAdded() {

        refsetVersionsAdded++;

    }

    /**
     * Increment refset versions inactivated.
     *
     * @param val the val
     */
    public void incrementRefsetVersionsInactivated(final int val) {

        refsetVersionsInactivated += val;
    }

    /**
     * Sets the refset versions inactivated.
     *
     * @param val the refset versions inactivated
     */
    public void setRefsetVersionsInactivated(final int val) {

        refsetVersionsInactivated = val;
    }

    /**
     * Increment refset versions activated.
     *
     * @param val the val
     */
    public void incrementRefsetVersionsReactivated() {

        this.refsetVersionsActivated++;
    }

    /**
     * 
     * /** Increment refset versions modified.
     *
     * @param val the val
     */
    public void setRefsetVersionsModified(final int val) {

        this.refsetVersionsModified = val;
    }

    // Increments
    /**
     * Increment refset versions synced.
     */
    public void incrementRefsetVersionsSynced() {

        refsetVersionsSynced += 1;
    }

    /**
     * Increment edition organization map changed.
     */
    public void incrementEditionOrganizationMapChanged() {

        editionOrganizationMapChanged++;

    }

    /**
     * Increment refset versions modified.
     */
    public void incrementRefsetVersionsModified() {

        refsetVersionsModified++;
    }

    /**
     * Increment refset versions inactivated.
     */
    public void incrementRefsetVersionsInactivated() {

        refsetVersionsInactivated++;
    }

    /**
     * Returns the projects added.
     *
     * @return the projects added
     */
    public int getProjectsAdded() {

        return projectsAdded;

    }

    /**
     * Returns the teams processed.
     *
     * @return the teams processed
     */
    public int getTeamsProcessed() {

        return teamsAdded;
    }

    /**
     * Sets the teams added.
     *
     * @param val the teams added
     */
    public void setTeamsAdded(final int val) {

        teamsAdded = val;

    }

    /**
     * Increment projects added.
     */
    public void incrementProjectsAdded() {

        projectsAdded++;

    }

    /**
     * Increment projects added.
     */
    public void incrementTeamsAdded() {

        teamsAdded++;

    }

    public void incrementOrganizationsAdded() {

        organizationsAdded++;
    }

    public void incrementEditionsAdded() {

        editionsAdded++;
    }

    public void incrementEditionsModified() {

        editionsModified++;

    }

    public void incrementOrganizationsInactivated() {

        organizationsInactivated++;

    }

    public void incrementOrganizationsReactivated() {

        organizationsReactivated++;
    }

    public void incrementEditionsInactivated() {

        editionsInactivated++;

    }

    public void incrementEditionsReactivated() {

        editionsReactivated++;
    }

    public void incrementOrganizationsModified() {

        organizationsModified++;
    }

    public void incrementProjectsModified() {

        projectsModified++;
    }

}
