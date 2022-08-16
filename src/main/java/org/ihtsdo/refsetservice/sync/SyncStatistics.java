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

    // Refsets
    private static final Set<Refset> refsetVersionsAdded = new HashSet<>();

    private static final Set<Refset> refsetVersionsUnchanged = new HashSet<>();

    private static final Set<Refset> refsetVersionsSynced = new HashSet<>();

    private static final Set<Refset> refsetVersionsProcessed = new HashSet<>();;

    // Processing Only as we don't sync these with Snowstorm
    private static final Set<Project> projectsProcessed = new HashSet<>();;

    private static final Set<Team> teamsProcessed = new HashSet<>();;

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

    public static void clearStatistics() {

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
