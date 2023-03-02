package org.ihtsdo.refsetservice.sync.util;

import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Team;

public class SyncStatistics {

    // Code Systems
    private int codeSystemsSynced = 0;

    private int codeSystemsFiltered = 0;

    // Orgs
    private int organizationsAdded = 0;

    private int organizationsInactivated = 0;

    private int organizationsActivated = 0;

    private int organizationsUnchanged = 0;

    private int organizationsModified = 0;;

    private int organizationsActivatedAndModified = 0;;

    // Editions
    private int editionsAdded = 0;

    private int editionsInactivated = 0;;

    private int editionsActivated = 0;

    private int editionsUnchanged = 0;

    private int editionsModified = 0;;

    private int editionsActivatedAndModified = 0;;

    private int editionOrganizationMapChanged = 0;

    // Refsets
    private int refsetIdsAdded = 0;

    private int refsetIdsInactivated = 0;;

    private int refsetIdsActivated = 0;

    private int refsetIdsSynced = 0;

    private int refsetIdsActivatedAndModified = 0;

    private int refsetVersionsAdded = 0;

    private int refsetVersionsInactivated = 0;;

    private int refsetVersionsActivated = 0;

    private int refsetVersionsUnchanged = 0;

    private int refsetVersionsModified = 0;

    private int refsetVersionsActivatedAndModified = 0;

    private int refsetVersionsSynced = 0;

    // Processing Only as we don't sync these with Snowstorm
    private final Set<Project> projectsProcessed = new HashSet<>();;

    private final Set<Team> teamsProcessed = new HashSet<>();

    public static final String CHANGED = "Changed";

    public static final String UNCHANGED = "Unchanged";

    public String printStatistics() {

        StringBuffer buf = new StringBuffer();

        buf.append(System.getProperty("line.separator") + "*********    Syncing Results    *************" + System.getProperty("line.separator"));

        buf.append("Code systems encountered: " + codeSystemsSynced + ". Syncing " + codeSystemsFiltered + " after filtered them" + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Organizations
        buf.append("*** Organizations --> Added: " + organizationsAdded + " / Inactivated: " + organizationsInactivated + " / Activated: " + organizationsActivated + " / Unchanged: "
                + organizationsUnchanged + " / Modified: " + organizationsModified + " / ActivatedAndModified: " + organizationsActivatedAndModified + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Editions
        buf.append("*** EDITIONS --> Added: " + editionsAdded + " / Inactivated: " + editionsInactivated + " / Activated: " + editionsActivated + " / Unchanged: " + editionsUnchanged + " / Modified: "
                + editionsModified + " / ActivatedAndModified: " + editionsActivatedAndModified + System.getProperty("line.separator"));

        buf.append("*** EDITIONS to ORGANIZATION MAP --> Changes: " + editionOrganizationMapChanged + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Refsets
        buf.append("*** REFSET IDs --> Synced " + refsetIdsSynced + " Added: " + refsetIdsAdded + " / Inactivated: " + refsetIdsInactivated + " / ActivatedAndModified: "
                + refsetIdsActivatedAndModified + " / Activated: " + refsetIdsActivated + System.getProperty("line.separator"));

        buf.append("*** REFSET VERSIONs --> Synced " + refsetVersionsSynced + " Added: " + refsetVersionsAdded + " / Inactivated: " + refsetVersionsInactivated + " / Modified: "
                + refsetVersionsModified + " / Unchanged: " + refsetVersionsUnchanged + System.getProperty("line.separator"));
        buf.append(System.getProperty("line.separator"));

        // Othera
        buf.append("*** Projects --> " + projectsProcessed.size() + " Processed " + System.getProperty("line.separator"));
        buf.append("*** Teams --> " + teamsProcessed.size() + " Processed" + System.getProperty("line.separator"));

        return buf.toString();
    }

    public void clearStatistics() {
        codeSystemsSynced = 0;
        codeSystemsFiltered = 0;

        organizationsAdded = 0;
        organizationsActivated = 0;
        organizationsInactivated = 0;
        organizationsUnchanged = 0;
        organizationsModified = 0;
        organizationsActivatedAndModified = 0;

        editionsAdded = 0;
        editionsInactivated = 0;
        editionsActivated = 0;
        editionsUnchanged = 0;
        editionsModified = 0;
        editionsActivatedAndModified = 0;

        editionOrganizationMapChanged = 0;

        refsetIdsAdded = 0;
        refsetIdsInactivated = 0;
        refsetIdsActivated = 0;
        refsetIdsActivatedAndModified = 0;
        refsetIdsSynced = 0;

        refsetVersionsAdded = 0;
        refsetVersionsInactivated = 0;
        refsetVersionsActivated = 0;
        refsetVersionsUnchanged = 0;
        refsetVersionsModified = 0;
        refsetVersionsActivatedAndModified = 0;
        refsetVersionsSynced = 0;

        projectsProcessed.clear();
        teamsProcessed.clear();

    }

    /** Getters **/
    // Code Systems
    public int getCodeSystemsSynced() {
        return codeSystemsSynced;
    }

    public int getCodeSystemsFiltered() {
        return codeSystemsFiltered;
    }

    // Organizations
    public int getOrganizationsAdded() {
        return organizationsAdded;
    }

    public int getOrganizationsInactivated() {
        return organizationsInactivated;
    }

    public int getOrganizationsActivated() {
        return organizationsActivated;
    }

    public int getOrganizationsUnchanged() {
        return organizationsUnchanged;
    }

    public int getOrganizationsModified() {

        return organizationsModified;
    }

    public int getOrganizationsActivatedAndModified() {
        return organizationsActivatedAndModified;
    }

    // Editions
    public int getEditionsAdded() {

        return editionsAdded;
    }

    public int getEditionsInactivated() {
        return editionsInactivated;
    }

    public int getEditionsActivated() {
        return editionsActivated;
    }

    public int getEditionsUnchanged() {

        return editionsUnchanged;
    }

    public int getEditionsModified() {

        return editionsModified;
    }

    public int getEditionsActivatedAndModified() {
        return editionsActivatedAndModified;
    }

    public int getEditionOrganizationMapChanged() {
        return editionOrganizationMapChanged;
    }

    // Refsets
    public int getRefsetIdsAdded() {

        return refsetIdsAdded;
    }

    public int getRefsetIdsInactivated() {

        return refsetIdsInactivated;
    }

    public int getRefsetIdsActivated() {

        return refsetIdsActivated;
    }

    public int getRefsetIdsActivatedAndModified() {

        return refsetIdsActivatedAndModified;
    }

    public int getRefsetIdsSynced() {
        return refsetIdsSynced;
    }

    public int getRefsetVersionsAdded() {

        return refsetVersionsAdded;
    }

    public int getRefsetVersionsInactivated() {

        return refsetVersionsInactivated;
    }

    public int getRefsetVersionsActivated() {

        return refsetVersionsActivated;
    }

    public int getRefsetVersionsUnchanged() {

        return refsetVersionsUnchanged;
    }

    public int getRefsetVersionsModified() {

        return refsetVersionsModified;
    }

    public int getRefsetVersionsActivatedAndModified() {

        return refsetVersionsActivatedAndModified;
    }

    public int getRefsetVersionsSynced() {

        return refsetVersionsSynced;
    }

    /** Setters **/
    // Code Systems
    public void setCodeSystemsSynced(int val) {
        codeSystemsSynced = val;
    }

    public void setCodeSystemsFiltered(int val) {
        codeSystemsFiltered = val;
    }

    // Organizations
    public void setOrganizationsAdded(int val) {
        organizationsAdded = val;

    }

    public void setOrganizationsInactivated(int val) {
        organizationsInactivated = val;

    }

    public void setOrganizationsActivated(int organizationsActivated) {
        this.organizationsActivated = organizationsActivated;
    }

    public void setOrganizationsUnchanged(int val) {
        organizationsUnchanged = val;

    }

    public void setOrganizationsModified(int organizationsModified) {
        this.organizationsModified = organizationsModified;
    }

    public void setOrganizationsActivatedAndModified(int organizationsActivatedAndModified) {
        this.organizationsActivatedAndModified = organizationsActivatedAndModified;
    }

    // Editions
    public void setEditionsAdded(int val) {
        editionsAdded = val;
    }

    public void setEditionsInactivated(int val) {
        editionsInactivated = val;
    }

    public void setEditionsActivated(int val) {
        editionsActivated = val;

    }

    public void setEditionsUnchanged(int val) {
        editionsUnchanged = val;
    }

    public void setEditionsModified(int val) {
        editionsModified = val;
    }

    public void setEditionsActivatedAndModified(int val) {
        editionsActivatedAndModified = val;

    }

    // Refset Ids
    public void setRefsetIdsAdded(int val) {
        refsetIdsAdded = val;
    }

    public void setRefsetIdsInactivated(int val) {
        refsetIdsInactivated = val;
    }

    public void setRefsetIdsActivated(int val) {
        this.refsetIdsActivated = val;
    }

    public void setRefsetIdsActivatedAndModified(int val) {
        this.refsetIdsActivatedAndModified = val;
    }

    public void setRefsetIdsSynced(int val) {
        this.refsetIdsSynced = val;
    }

    // Refset Version Pairs
    public void incrementRefsetVersionsAdded(int val) {
        refsetVersionsAdded += val;
    }

    public void incrementRefsetVersionsInactivated(int val) {
        refsetVersionsInactivated += val;
    }

    public void incrementRefsetVersionsActivated(int val) {
        this.refsetVersionsActivated += val;
    }

    public void incrementRefsetVersionsUnchanged(int val) {
        this.refsetVersionsUnchanged = +val;
    }

    public void incrementRefsetVersionsModified(int val) {
        this.refsetVersionsModified = +val;
    }

    public void incrementRefsetVersionsActivatedAndModified(int val) {
        this.refsetVersionsActivatedAndModified += val;
    }

    public void incrementRefsetVersionsSynced(int val) {
        refsetVersionsSynced += val;
    }

    // Increments
    public void incrementEditionOrganizationMapChanged() {
        editionOrganizationMapChanged++;

    }

    public void incrementRefsetVersionsUnchanged() {
        refsetVersionsUnchanged++;

    }

    public void incrementRefsetVersionsModified() {
        refsetVersionsModified++;
    }

    public void incrementRefsetVersionsAdded() {
        refsetVersionsAdded++;
    }

    public void incrementRefsetVersionsInactivated() {
        refsetVersionsInactivated++;
    }

    public Set<Project> getProjectsProcessed() {

        return projectsProcessed;
    }

    public Set<Team> getTeamsProcessed() {

        return teamsProcessed;
    }

}
