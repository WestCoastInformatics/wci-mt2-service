package org.ihtsdo.refsetservice.sync.util;

import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Team;

public class SyncStatistics {

    // orgName to Organization Map
    private int organizationsAdded = 0;

    private int organizationsRemoved = 0;

    private int organizationsUnchanged = 0;

    private int organizationsRecreated = 0;

    private int organizationsSynced = 0;

    // Editions
    private int editionsAdded = 0;

    private int editionsRemoved = 0;;

    private int editionsUnchanged = 0;

    private int editionsRecreated = 0;;

    private int editionsSynced = 0;

    // Refsets
    private int refsetVersionsAdded = 0;

    private int refsetVersionsRemoved = 0;;

    private int refsetVersionsUnchanged = 0;

    private int refsetVersionsRecreated = 0;;

    private int refsetVersionsSynced = 0;

    // Processing Only as we don't sync these with Snowstorm
    private final Set<Project> projectsProcessed = new HashSet<>();;

    private final Set<Team> teamsProcessed = new HashSet<>();;

    public String printStatistics() {

        StringBuffer buf = new StringBuffer();

        buf.append("*********    Syncing Results (Added/Removed/Recreated/Unchanged from the total Synced)    *************" + System.getProperty("line.separator"));

        buf.append("Out of Organizations " + organizationsSynced + " Processed: " + organizationsAdded + "/" + organizationsRemoved + " / " + organizationsRecreated + " / " + organizationsUnchanged
                + System.getProperty("line.separator"));

        buf.append("Out of Editions " + editionsSynced + " Processed: " + editionsAdded + " / " + editionsRemoved + " / " + editionsRecreated + " / " + editionsUnchanged
                + System.getProperty("line.separator"));
        buf.append("Out of Refset Version Pairs " + refsetVersionsSynced + " Processed: " + refsetVersionsAdded + " / " + refsetVersionsRemoved + " / " + refsetVersionsRecreated + " / "
                + refsetVersionsUnchanged + System.getProperty("line.separator"));
        buf.append("Out of Projects " + projectsProcessed.size() + " Processed " + System.getProperty("line.separator"));
        buf.append("Out of Teams " + teamsProcessed.size() + " Processed");

        return buf.toString();
    }

    public void clearStatistics() {

        organizationsAdded = 0;
        organizationsRemoved = 0;
        organizationsUnchanged = 0;
        organizationsRecreated = 0;
        organizationsSynced = 0;

        editionsAdded = 0;
        editionsRemoved = 0;
        editionsUnchanged = 0;
        editionsRecreated = 0;
        editionsSynced = 0;

        refsetVersionsAdded = 0;
        refsetVersionsRemoved = 0;
        refsetVersionsUnchanged = 0;
        refsetVersionsRecreated = 0;
        refsetVersionsSynced = 0;

        editionsSynced = 0;
        refsetVersionsSynced = 0;

        projectsProcessed.clear();
        teamsProcessed.clear();

    }

    public int getOrganizationsAdded() {

        return organizationsAdded;
    }

    public int getOrganizationsRemoved() {

        return organizationsRemoved;
    }

    public int getOrganizationsUnchanged() {

        return organizationsUnchanged;
    }

    public int getOrganizationsRecreated() {

        return organizationsRecreated;
    }

    public int getOrganizationsSynced() {
        return organizationsSynced;
    }

    public int getEditionsAdded() {

        return editionsAdded;
    }

    public int getEditionsRemoved() {

        return editionsRemoved;
    }

    public int getEditionsUnchanged() {

        return editionsUnchanged;
    }

    public int getEditionsRecreated() {

        return editionsRecreated;
    }

    public int getEditionsSynced() {
        return editionsSynced;
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

    public void incrementOrganizationsAdded() {
        organizationsAdded++;
    }

    public void incrementOrganizationsRemoved() {
        organizationsRemoved++;
    }

    public void incrementOrganizationsUnchanged() {
        organizationsUnchanged++;
    }

    public void incrementOrganizationsRecreated() {
        organizationsRecreated++;
    }

    public void incrementOrganizationsSynced() {
        organizationsSynced++;
    }

    public void incrementEditionsAdded() {
        editionsAdded++;
    }

    public void incrementEditionsRemoved() {
        editionsRemoved++;
    }

    public void incrementEditionsUnchanged() {
        editionsUnchanged++;
    }

    public void incrementEditionsRecreated() {
        editionsRecreated++;
    }

    public void incrementEditionsSynced() {
        editionsSynced++;
    }

    public void incrementRefsetVersionsAdded() {
        refsetVersionsAdded++;
    }

    public void incrementRefsetVersionsRemoved() {
        refsetVersionsRemoved++;
    }

    public void incrementRefsetVersionsUnchanged() {
        refsetVersionsUnchanged++;
    }

    public void incrementRefsetVersionsRecreated() {
        refsetVersionsRecreated++;
    }

    public void incrementRefsetVersionsSynced() {
        refsetVersionsSynced++;
    }

    public Set<Project> getProjectsProcessed() {

        return projectsProcessed;
    }

    public Set<Team> getTeamsProcessed() {

        return teamsProcessed;
    }
}
