package org.ihtsdo.refsetservice.sync.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;

public class SyncStatistics {

    // orgName to Organization Map
    private final Map<String, Organization> organizationsAdded = new HashMap<>();

    private final Set<Organization> organizationsRemoved = new HashSet<>();

    private final Set<Organization> organizationsUnchanged = new HashSet<>();

    private final Set<Organization> organizationsRecreated = new HashSet<>();

    // Editions
    private final Set<Edition> editionsAdded = new HashSet<>();

    private final Set<Edition> editionsRemoved = new HashSet<>();;

    private final Set<Edition> editionsUnchanged = new HashSet<>();

    private final Set<Edition> editionsRecreated = new HashSet<>();;

    // Refsets
    private final Set<Refset> refsetVersionsAdded = new HashSet<>();

    private final Set<Refset> refsetVersionsRemoved = new HashSet<>();;

    private final Set<Refset> refsetVersionsUnchanged = new HashSet<>();

    private final Set<Refset> refsetVersionsRecreated = new HashSet<>();;

    private int organizationsSynced = 0;

    private int editionsSynced = 0;

    private int refsetVersionsSynced = 0;

    // Processing Only as we don't sync these with Snowstorm
    private final Set<Project> projectsProcessed = new HashSet<>();;

    private final Set<Team> teamsProcessed = new HashSet<>();;

    public String printStatistics() {

        StringBuffer buf = new StringBuffer();

        buf.append("*********    Syncing Results (Added/Removed/Recreated/Unchanged from the total Synced)    *************" + System.getProperty("line.separator"));

        buf.append("Out of Organizations " + organizationsSynced + " Processed: " + organizationsAdded.size() + "/" + organizationsRemoved.size() + " / " + organizationsRecreated.size() + " / "
                + organizationsUnchanged.size() + System.getProperty("line.separator"));

        buf.append("Out of Editions " + editionsSynced + " Processed: " + editionsAdded.size() + " / " + editionsRemoved.size() + " / " + editionsRecreated.size() + " / " + editionsUnchanged.size()
                + System.getProperty("line.separator"));
        buf.append("Out of Refset Version Pairs " + refsetVersionsSynced + " Processed: " + refsetVersionsAdded.size() + " / " + refsetVersionsRemoved.size() + " / " + refsetVersionsRecreated.size()
                + " / " + refsetVersionsUnchanged.size() + System.getProperty("line.separator"));
        buf.append("Out of Projects " + projectsProcessed.size() + " Processed " + System.getProperty("line.separator"));
        buf.append("Out of Teams " + teamsProcessed.size() + " Processed");

        return buf.toString();
    }

    public void clearStatistics() {

        organizationsAdded.clear();
        organizationsUnchanged.clear();
        organizationsRemoved.clear();

        editionsAdded.clear();
        editionsUnchanged.clear();
        editionsRemoved.clear();

        refsetVersionsAdded.clear();
        refsetVersionsUnchanged.clear();
        refsetVersionsRemoved.clear();

        organizationsSynced = 0;
        editionsSynced = 0;
        refsetVersionsSynced = 0;

        projectsProcessed.clear();
        teamsProcessed.clear();

    }

    public Map<String, Organization> getOrganizationsAdded() {

        return organizationsAdded;
    }

    public Set<Organization> getOrganizationsRemoved() {

        return organizationsRemoved;
    }

    public Set<Organization> getOrganizationsUnchanged() {

        return organizationsUnchanged;
    }

    public Set<Organization> getOrganizationsRecreated() {

        return organizationsRecreated;
    }

    public Set<Edition> getEditionsAdded() {

        return editionsAdded;
    }

    public Set<Edition> getEditionsRemoved() {

        return editionsRemoved;
    }

    public Set<Edition> getEditionsUnchanged() {

        return editionsUnchanged;
    }

    public Set<Edition> getEditionsRecreated() {

        return editionsRecreated;
    }

    public Set<Refset> getRefsetVersionsAdded() {

        return refsetVersionsAdded;
    }

    public Set<Refset> getRefsetVersionsRemoved() {

        return refsetVersionsRemoved;
    }

    public Set<Refset> getRefsetVersionsUnchanged() {

        return refsetVersionsUnchanged;
    }

    public Set<Refset> getRefsetVersionsRecreated() {

        return refsetVersionsRecreated;
    }

    public Set<Project> getProjectsProcessed() {

        return projectsProcessed;
    }

    public Set<Team> getTeamsProcessed() {

        return teamsProcessed;
    }

    public int getOrganizationsSynced() {
        return organizationsSynced;
    }

    public int getEditionsSynced() {
        return editionsSynced;
    }

    public int getRefsetVersionsSynced() {
        return refsetVersionsSynced;
    }

    public void incrementOrganizationsSynced() {
        organizationsSynced++;
    }

    public void incrementEditionsSynced() {
        editionsSynced++;
    }

    public void incrementRefsetVersionsSynced() {
        refsetVersionsSynced++;
    }
}
