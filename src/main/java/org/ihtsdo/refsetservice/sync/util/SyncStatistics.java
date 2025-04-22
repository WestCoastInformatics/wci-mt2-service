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

    // Organizations
    private int organizationsAdded = 0;

    /** The organizations inactivated. */
    private int organizationsInactivated = 0;

    /** The organizations activated. */
    private int organizationsReactivated = 0;

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

    /** The refset ids added. */
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

    private int eclClausesAdded;

    /** The projects modified. */
    private int tagsAdded;

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

        buf.append("Code systems encountered: " + codeSystemsSynced + ". Syncing " + codeSystemsFiltered + " after filtered them"
            + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Organizations
        buf.append("*** Organizations (Synced " + codeSystemsFiltered + " --> Added: " + organizationsAdded + " / Inactivated: " + organizationsInactivated
            + " / Activated: " + organizationsReactivated + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Editions
        buf.append("*** Editions (Synced " + codeSystemsFiltered + " --> Added: " + editionsAdded + " / Inactivated: " + editionsInactivated + " / Activated: "
            + editionsReactivated + " / Modified: " + editionsModified + " / Modified And : " + editionsModified + System.getProperty("line.separator"));

        buf.append("*** Reassignment of editions-to-organization map --> Changes: " + editionOrganizationMapChanged + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Refsets
        buf.append("*** Unique Refset Ids (" + refsetIdsSynced + " Synced" + ") --> Added: " + refsetIdsAdded + " / Inactivated: " + refsetIdsInactivated
            + " / Activated: " + refsetIdsActivated + System.getProperty("line.separator"));

        buf.append("*** Refset Version Pairs (" + refsetVersionsSynced + " Synced" + ") --> Added: " + refsetVersionsAdded + " / Inactivated: "
            + refsetVersionsInactivated + " / Activated: " + refsetVersionsActivated + " / Modified: " + refsetVersionsModified
            + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Othera
        buf.append("*** Projects --> " + projectsAdded + " Added and " + projectsModified + " modified " + System.getProperty("line.separator"));
        buf.append("*** Teams --> " + teamsAdded + " Added" + System.getProperty("line.separator"));
        buf.append("*** From RTT: --> " + eclClausesAdded + " ECL Clauses added and " + tagsAdded + " tags added" + System.getProperty("line.separator"));

        return buf.toString();
    }

    /**
     * Clear statistics.
     */
    public void clearStatistics() {

        /*
         * codeSystemsSynced = 0; codeSystemsFiltered = 0;
         * 
         * organizationsAdded = 0; organizationsReactivated = 0; organizationsInactivated = 0;
         * 
         * editionsAdded = 0; editionsInactivated = 0; editionsReactivated = 0; editionsModified = 0;
         * 
         * editionOrganizationMapChanged = 0;
         * 
         * refsetIdsAdded = 0; refsetIdsInactivated = 0; refsetIdsActivated = 0; refsetIdsSynced = 0;
         * 
         * refsetVersionsAdded = 0; refsetVersionsInactivated = 0; refsetVersionsActivated = 0; refsetVersionsModified = 0; refsetVersionsSynced = 0;
         * 
         * projectsAdded = 0; teamsAdded = 0; eclClausesAdded = 0; tagsAdded = 0;
         */
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

    // Editions
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
     * Sets the refset ids synced.
     *
     * @param val the refset ids synced
     */
    public void setRefsetIdsSynced(final int val) {

        this.refsetIdsSynced = val;
    }

    // Refset Version Pairs
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
     * Increment refset versions activated.
     */
    public void incrementRefsetVersionsReactivated() {

        this.refsetVersionsActivated++;
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

    /**
     * Increment organizations added.
     */
    public void incrementOrganizationsAdded() {

        organizationsAdded++;
    }

    /**
     * Increment editions added.
     */
    public void incrementEditionsAdded() {

        editionsAdded++;
    }

    /**
     * Increment editions modified.
     */
    public void incrementEditionsModified() {

        editionsModified++;

    }

    /**
     * Increment organizations inactivated.
     */
    public void incrementOrganizationsInactivated() {

        organizationsInactivated++;

    }

    /**
     * Increment organizations reactivated.
     */
    public void incrementOrganizationsReactivated() {

        organizationsReactivated++;
    }

    /**
     * Increment editions inactivated.
     */
    public void incrementEditionsInactivated() {

        editionsInactivated++;

    }

    /**
     * Increment editions reactivated.
     */
    public void incrementEditionsReactivated() {

        editionsReactivated++;
    }

    /**
     * Increment projects modified.
     */
    public void incrementProjectsModified() {

        projectsModified++;
    }

    public int getRefsetIdsAdded() {

        return refsetIdsAdded;
    }

}
