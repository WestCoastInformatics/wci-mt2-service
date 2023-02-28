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

    private int organizationsUnchanged = 0;

    // Editions
    private int editionsAdded = 0;

    private int editionsInactivated = 0;;

    private int editionsActivated = 0;

    private int editionsUnchanged = 0;

    private int editionsModified = 0;;

    private int editionsActivatedAndModified = 0;;

    // Refsets
    private int refsetVersionsAdded = 0;

    private int refsetVersionsInactivated = 0;;

    private int refsetVersionsUnchanged = 0;

    private int refsetVersionsModified = 0;;

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

        buf.append("ORGANIZATIONS Added: " + organizationsAdded + " / Unchanged: " + organizationsUnchanged + System.getProperty("line.separator"));

        buf.append("EDITIONS Added: " + editionsAdded + " / Inactivated: " + editionsInactivated + " / Modified: " + editionsModified + " / Unchanged: " + editionsUnchanged
                + System.getProperty("line.separator"));

        buf.append("REFSET VERSION PAIRs Synced " + refsetVersionsSynced + " Added: " + refsetVersionsAdded + " / Inactivated: " + refsetVersionsInactivated + " / Modified: " + refsetVersionsModified
                + " / Unchanged: " + refsetVersionsUnchanged + System.getProperty("line.separator"));

        buf.append("Projects " + projectsProcessed.size() + " Processed " + System.getProperty("line.separator"));
        buf.append("Teams " + teamsProcessed.size() + " Processed" + System.getProperty("line.separator"));

        return buf.toString();
    }

    public void clearStatistics() {
        codeSystemsSynced = 0;
        codeSystemsFiltered = 0;

        organizationsAdded = 0;
        organizationsInactivated = 0;
        organizationsUnchanged = 0;

        editionsAdded = 0;
        editionsInactivated = 0;
        editionsActivated = 0;
        editionsUnchanged = 0;
        editionsModified = 0;
        editionsActivatedAndModified = 0;

        refsetVersionsAdded = 0;
        refsetVersionsInactivated = 0;
        refsetVersionsUnchanged = 0;
        refsetVersionsModified = 0;
        refsetVersionsSynced = 0;

        projectsProcessed.clear();
        teamsProcessed.clear();

    }

    public int getOrganizationsAdded() {
        return organizationsAdded;
    }

    public int getOrganizationsInactivated() {
        return organizationsInactivated;
    }

    public int getOrganizationsUnchanged() {
        return organizationsUnchanged;
    }

    public int getCodeSystemsSynced() {
        return codeSystemsSynced;
    }

    public int getCodeSystemsFiltered() {
        return codeSystemsFiltered;
    }

    public int getEditionsAdded() {

        return editionsAdded;
    }

    public int getEditionsUnchanged() {

        return editionsUnchanged;
    }

    public int getEditionsRecreated() {

        return editionsModified;
    }

    public int getEditionsActivated() {
        return editionsActivated;
    }

    public int getEditionsActivatedAndModified() {
        return editionsActivatedAndModified;
    }

    public int getRefsetVersionsAdded() {

        return refsetVersionsAdded;
    }

    public int getRefsetVersionsInactivated() {

        return refsetVersionsInactivated;
    }

    public int getRefsetVersionsUnchanged() {

        return refsetVersionsUnchanged;
    }

    public int getRefsetVersionsRecreated() {

        return refsetVersionsModified;
    }

    public int getRefsetVersionsSynced() {
        return refsetVersionsSynced;
    }

    // Increments
    public void incrementRefsetVersionsAdded() {
        refsetVersionsAdded++;
    }

    public void incrementRefsetVersionsInactivated() {
        refsetVersionsInactivated++;
    }

    // SETTERS
    public void setCodeSystemsSynced(int val) {
        codeSystemsSynced = val;
    }

    public void setCodeSystemsFiltered(int val) {
        codeSystemsFiltered = val;
    }

    public void setOrganizationsAdded(int val) {
        organizationsAdded = val;

    }

    public void setOrganizationsInactivated(int val) {
        organizationsInactivated = val;

    }

    public void setOrganizationsUnchanged(int val) {
        organizationsUnchanged = val;

    }

    public void setEditionsAdded(int val) {
        editionsAdded = val;
    }

    public void setEditionsActivated(int val) {
        editionsActivated = val;

    }

    public void setEditionsInactivated(int val) {
        editionsInactivated = val;
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

    public void setRefsetVersionsInactivated(int val) {
        refsetVersionsInactivated = val;
    }

    public void incrementRefsetVersionsUnchanged() {
        refsetVersionsUnchanged++;

    }

    public void incrementRefsetVersionsRecreated() {
        refsetVersionsModified++;
    }

    public void setRefsetVersionsSynced(int val) {
        refsetVersionsSynced = val;
    }

    public void setRefsetVersionsAdded(int val) {
        refsetVersionsAdded = val;
    }

    public Set<Project> getProjectsProcessed() {

        return projectsProcessed;
    }

    public Set<Team> getTeamsProcessed() {

        return teamsProcessed;
    }

}
