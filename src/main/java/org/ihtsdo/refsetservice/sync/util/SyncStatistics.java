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

    private final Set<Organization> organizationsUnchanged = new HashSet<>();

    private final Set<Organization> organizationsSynced = new HashSet<>();

    private final Set<Organization> organizationsProcessed = new HashSet<>();;

    // Editions
    private final Set<Edition> editionsAdded = new HashSet<>();

    private final Set<Edition> editionsUnchanged = new HashSet<>();

    private final Set<Edition> editionsSynced = new HashSet<>();

    private final Set<Edition> editionsProcessed = new HashSet<>();;

    // Refsets
    private final Set<Refset> refsetVersionsAdded = new HashSet<>();

    private final Set<Refset> refsetVersionsUnchanged = new HashSet<>();

    private final Set<Refset> refsetVersionsSynced = new HashSet<>();

    private final Set<Refset> refsetVersionsProcessed = new HashSet<>();;

    // Processing Only as we don't sync these with Snowstorm
    private final Set<Project> projectsProcessed = new HashSet<>();;

    private final Set<Team> teamsProcessed = new HashSet<>();;

    public String printStatistics() {

        StringBuffer buf = new StringBuffer();

        buf.append("*********    Syncing Results (Added/Unchanged/Synced)    *************" + System.getProperty("line.separator"));
        buf.append("Out of Organizations " + organizationsProcessed.size() + " Processed: " + organizationsAdded.size() + " / " + organizationsUnchanged.size() + " / " + organizationsSynced.size()
            + System.getProperty("line.separator"));
        buf.append("Out of Editions " + editionsProcessed.size() + " Processed: " + editionsAdded.size() + " / " + editionsUnchanged.size() + " / " + editionsSynced.size()
            + System.getProperty("line.separator"));
        buf.append("Out of Refsets " + refsetVersionsProcessed.size() + " Processed: " + refsetVersionsAdded.size() + " / " + refsetVersionsUnchanged.size() + " / " + refsetVersionsSynced.size()
            + System.getProperty("line.separator"));
        buf.append("Out of Projects " + projectsProcessed.size() + " Processed " + System.getProperty("line.separator"));
        buf.append("Out of Teams " + teamsProcessed.size() + " Processed");

        return buf.toString();
    }

    public void clearStatistics() {

        organizationsAdded.clear();
        organizationsUnchanged.clear();
        organizationsSynced.clear();
        organizationsProcessed.clear();

        editionsAdded.clear();
        editionsUnchanged.clear();
        editionsSynced.clear();
        editionsProcessed.clear();

        refsetVersionsAdded.clear();
        refsetVersionsUnchanged.clear();
        refsetVersionsSynced.clear();
        refsetVersionsProcessed.clear();

        projectsProcessed.clear();
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

    public Set<Project> getProjectsProcessed() {

        return projectsProcessed;
    }

    public Set<Team> getTeamsProcessed() {

        return teamsProcessed;
    }
}
