package org.ihtsdo.refsetservice.sync;

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

    // Organizations
    private static final Map<String, Organization> organizationsAdded = new HashMap<>();

    private static final Set<Organization> organizationsUnchanged = new HashSet<>();

    private static final Set<Organization> organizationsSynced = new HashSet<>();

    private static final Set<Organization> organizationsProcessed = new HashSet<>();;

    // Editions
    private static final Set<Edition> editionsAdded = new HashSet<>();

    private static final Set<Edition> editionsUnchanged = new HashSet<>();

    private static final Set<Edition> editionsSynced = new HashSet<>();

    private static final Set<Edition> editionsProcessed = new HashSet<>();;

    // Projects
    private static final Set<Project> projectsAdded = new HashSet<>();

    private static final Set<Project> projectsUnchanged = new HashSet<>();

    private static final Set<Project> projectsSynced = new HashSet<>();

    private static final Set<Project> projectsProcessed = new HashSet<>();;

    // Refsets
    private static final Set<Refset> refsetVersionsAdded = new HashSet<>();

    private static final Set<Refset> refsetVersionsUnchanged = new HashSet<>();

    private static final Set<Refset> refsetVersionsSynced = new HashSet<>();

    private static final Set<Refset> refsetVersionsProcessed = new HashSet<>();;

    // Teams
    private static final Set<Team> teamsAdded = new HashSet<>();

    private static final Set<Team> teamsUnchanged = new HashSet<>();

    private static final Set<Team> teamsSynced = new HashSet<>();

    private static final Set<Team> teamsProcessed = new HashSet<>();;

    public String printStatistics() {

        StringBuffer buf = new StringBuffer();

        buf.append("*********    Syncing Results (Added/Unchanged/Synced)    *************" + System.getProperty("line.separator"));
        buf.append("Out of Organizations " + organizationsProcessed.size() + " Processed: " + organizationsAdded.size() + " / " + organizationsUnchanged.size() + " / " + organizationsSynced.size()
            + System.getProperty("line.separator"));
        buf.append(
            "Out of Editions " + editionsProcessed.size() + " Processed: " + editionsAdded.size() + " / " + editionsUnchanged.size() + " / " + editionsSynced.size() + System.getProperty("line.separator"));
        buf.append(
            "Out of Projects " + projectsProcessed.size() + " Processed: " + projectsAdded.size() + " / " + projectsUnchanged.size() + " / " + projectsSynced.size() + System.getProperty("line.separator"));
        buf.append("Out of Refsets " + refsetVersionsProcessed.size() + " Processed: " + refsetVersionsAdded.size() + " / " + refsetVersionsUnchanged.size() + " / " + refsetVersionsSynced.size()
            + System.getProperty("line.separator"));
        buf.append("Out of Teams " + teamsProcessed.size() + " Processed: " + teamsAdded.size() + " / " + teamsUnchanged.size() + " / " + teamsSynced.size() + System.getProperty("line.separator"));

        return buf.toString();
    }

    public static void clearStatistics() {

        organizationsAdded.clear();
        organizationsUnchanged.clear();
        organizationsSynced.clear();
        organizationsProcessed.clear();

        editionsAdded.clear();
        editionsUnchanged.clear();
        editionsSynced.clear();
        editionsProcessed.clear();

        projectsAdded.clear();
        projectsUnchanged.clear();
        projectsSynced.clear();
        projectsProcessed.clear();

        refsetVersionsAdded.clear();
        refsetVersionsUnchanged.clear();
        refsetVersionsSynced.clear();
        refsetVersionsProcessed.clear();

        teamsAdded.clear();
        teamsUnchanged.clear();
        teamsSynced.clear();
        teamsProcessed.clear();

    }

    public Map<String, Organization> getOrganizationsAdded() {

        return organizationsAdded;
    }

    public Set<Organization> getOrganizationsUnchanged() {

        return organizationsUnchanged;
    }

    public Set<Organization> getOrganizationsSynced() {

        return organizationsSynced;
    }

    public Set<Organization> getOrganizationsProcessed() {

        return organizationsProcessed;
    }

    public Set<Edition> getEditionsAdded() {

        return editionsAdded;
    }

    public Set<Edition> getEditionsUnchanged() {

        return editionsUnchanged;
    }

    public Set<Edition> getEditionsSynced() {

        return editionsSynced;
    }

    public Set<Edition> getEditionsProcessed() {

        return editionsProcessed;
    }

    public Set<Project> getProjectsAdded() {

        return projectsAdded;
    }

    public Set<Project> getProjectsUnchanged() {

        return projectsUnchanged;
    }

    public Set<Project> getProjectsSynced() {

        return projectsSynced;
    }

    public Set<Project> getProjectsProcessed() {

        return projectsProcessed;
    }

    public Set<Refset> getRefsetVersionsAdded() {

        return refsetVersionsAdded;
    }

    public Set<Refset> getRefsetVersionsUnchanged() {

        return refsetVersionsUnchanged;
    }

    public Set<Refset> getRefsetVersionsSynced() {

        return refsetVersionsSynced;
    }

    public Set<Refset> getRefsetVersionsProcessed() {

        return refsetVersionsProcessed;
    }

    public Set<Team> getTeamsAdded() {

        return teamsAdded;
    }

    public Set<Team> getTeamsUnchanged() {

        return teamsUnchanged;
    }

    public Set<Team> getTeamsSynced() {

        return teamsSynced;
    }

    public Set<Team> getTeamsProcessed() {

        return teamsProcessed;
    }
}
