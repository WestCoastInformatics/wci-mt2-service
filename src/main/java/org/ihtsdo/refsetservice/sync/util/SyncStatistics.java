package org.ihtsdo.refsetservice.sync.util;

import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Team;

public class SyncStatistics {

    // Code Systems
    private static int codeSystemsSynced = 0;

    private static int codeSystemsFiltered = 0;

    // Orgs
    private int organizationsAdded = 0;

    private static int organizationsUnchanged = 0;

    // Editions
    private static int editionsAdded = 0;

    private static int editionsRemoved = 0;;

    private static int editionsUnchanged = 0;

    private static int editionsRecreated = 0;;

    // Refsets
    private static int refsetVersionsAdded = 0;

    private static int refsetVersionsRemoved = 0;;

    private static int refsetVersionsUnchanged = 0;

    private static int refsetVersionsRecreated = 0;;

    private static int refsetVersionsSynced = 0;

    // Processing Only as we don't sync these with Snowstorm
    private static final Set<Project> projectsProcessed = new HashSet<>();;

    private static final Set<Team> teamsProcessed = new HashSet<>();

    public static final String CHANGED = "Changed";

    public static final String UNCHANGED = "Unchanged";

    public String printStatistics() {

        StringBuffer buf = new StringBuffer();

        buf.append(System.getProperty("line.separator") + "*********    Syncing Results    *************" + System.getProperty("line.separator"));

        buf.append("Code systems encountered: " + codeSystemsSynced + ". Syncing " + codeSystemsFiltered + " after filtered them" + System.getProperty("line.separator"));

        buf.append("ORGANIZATIONS Added: " + organizationsAdded + " / Unchanged: " + organizationsUnchanged + System.getProperty("line.separator"));

        buf.append("EDITIONS Added: " + editionsAdded + " / Removed: " + editionsRemoved + " / Recreated: " + editionsRecreated + " / Unchanged: " + editionsUnchanged
                + System.getProperty("line.separator"));

        buf.append("REFSET VERSION PAIRs Synced " + refsetVersionsSynced + " Added: " + refsetVersionsAdded + " / Removed: " + refsetVersionsRemoved + " / Recreated: " + refsetVersionsRecreated
                + " / Unchanged: " + refsetVersionsUnchanged + System.getProperty("line.separator"));

        buf.append("Projects " + projectsProcessed.size() + " Processed " + System.getProperty("line.separator"));
        buf.append("Teams " + teamsProcessed.size() + " Processed" + System.getProperty("line.separator"));

        return buf.toString();
    }

    public void clearStatistics() {
        codeSystemsSynced = 0;
        codeSystemsFiltered = 0;

        organizationsAdded = 0;
        organizationsUnchanged = 0;

        editionsAdded = 0;
        editionsRemoved = 0;
        editionsUnchanged = 0;
        editionsRecreated = 0;

        refsetVersionsAdded = 0;
        refsetVersionsRemoved = 0;
        refsetVersionsUnchanged = 0;
        refsetVersionsRecreated = 0;
        refsetVersionsSynced = 0;

        projectsProcessed.clear();
        teamsProcessed.clear();

    }

    public int getOrganizationsAdded() {
        return organizationsAdded;
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

        return editionsRecreated;
    }

    public int getRefsetVersionsAdded() {

        return refsetVersionsAdded;
    }

    public int getRefsetVersionsRemoved() {

        return refsetVersionsRemoved;
    }

    public int getRefsetVersionsUnchanged() {

        return refsetVersionsUnchanged;
    }

    public int getRefsetVersionsRecreated() {

        return refsetVersionsRecreated;
    }

    public int getRefsetVersionsSynced() {
        return refsetVersionsSynced;
    }

    // Increments
    public void setCodeSystemsSynced(int val) {
        codeSystemsSynced = val;
    }

    public void setCodeSystemsFiltered(int val) {
        codeSystemsFiltered = val;
    }

    public void incrementOrganizationsAdded() {
        organizationsAdded++;
    }

    public void incrementOrganizationsUnchanged() {
        organizationsUnchanged++;
    }

    public void incrementRefsetVersionsAdded() {
        refsetVersionsAdded++;
    }

    public void incrementRefsetVersionsRemoved() {
        refsetVersionsRemoved++;
    }

    // SETTERS
    public void setEditionsAdded(int val) {
        editionsAdded = val;
    }

    public void setEditionsRemoved(int val) {
        editionsRemoved = val;
    }

    public void setEditionsUnchanged(int val) {
        editionsUnchanged = val;
    }

    public void setEditionsRecreated(int val) {
        editionsRecreated = val;
    }

    public void setRefsetVersionsRemoved(int val) {
        refsetVersionsRemoved = val;
    }

    public void incrementRefsetVersionsUnchanged() {
        refsetVersionsUnchanged++;

    }

    public void incrementRefsetVersionsRecreated() {
        refsetVersionsRecreated++;
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
