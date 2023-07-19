/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class ProjectService.
 */
public class ProjectService extends BaseService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ProjectService.class);

    /** The crowd unit test skip. */
    private static String crowdUnitTestSkip;

    static {
        crowdUnitTestSkip = PropertyUtility.getProperty("crowd.unit.test.skip");
    }

    /**
     * Adds the project.
     *
     * @param user the user
     * @param project the project
     * @return the project
     * @throws Exception the exception
     */
    public static Project addProject(final User user, final Project project) throws Exception {
        // When a project is created, it does not have teams, those are added through update

        try (final TerminologyService service = new TerminologyService()) {

            RefsetService.setProjectPermissions(user, project);
            checkPermissions(user, project);

            // Allow for defining the 
            if (project.getCrowdProjectId() == null || project.getCrowdProjectId().isEmpty()) {
                project.setCrowdProjectId(CrowdGroupNameAlgorithm.getProjectString(project.getName()));
            }
            
            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.add(project);
            service.add(AuditEntryHelper.addProjectEntry(project));
            service.commit();

            // Return the response
            return project;
        }

    }

    /**
     * Returns the project if active.
     *
     * @param projectId the project id
     * @param includeMembers the include members
     * @return the project
     * @throws Exception the exception
     */
    public static Project getProject(final String projectId, final boolean includeMembers) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Project project = service.findSingle("id: " + projectId + " AND active:true", Project.class, null);

            if (project == null) {

                final String errorMessage = "Unable to find project for id " + projectId + ".";
                LOG.info(errorMessage);
                throw new NotFoundException(errorMessage);
            }

            if (includeMembers) {

                final Set<User> members = new HashSet<>();

                for (final String teamId : project.getTeams()) {

                    final Team team = service.get(teamId, Team.class);

                    if (team != null && team.getMembers() != null) {

                        for (final String userId : team.getMembers()) {

                            final User member = service.get(userId, User.class);
                            members.add(member);
                        }

                    }

                }

                project.getMemberList().addAll(members);
            }

            return project;
        }

    }

    /**
     * Returns the project names for edition.
     *
     * @param editionId the edition id
     * @return the project names for edition
     * @throws Exception the exception
     */
    public static Set<String> getProjectNamesForEdition(final String editionId) throws Exception {
        final Set<String> projectNames = new HashSet<>();

        final List<Project> projects = getProjectsForEdition(editionId);

        if (projects != null) {

            projects.stream().forEach(project -> projectNames.add(project.getName()));
        }

        return projectNames;

    }

    public static List<Project> getProjectsForEdition(String editionId) throws Exception {
        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<Project> projects = service.find("edition.id: " + editionId + " AND active:true", null, Project.class, null);

            if (projects != null) {
                return projects.getItems();
            }

            return new ArrayList<Project>();
        }
    }

    /**
     * Returns the team assigned to this project.
     *
     * @param projectId the project ID
     * @return the project teams
     * @throws Exception the exception
     */
    public static ResultList<Team> getProjectTeams(final String projectId) throws Exception {

        final Project project = getProject(projectId, false);
        final ResultList<Team> teams = new ResultList<>();

        for (final String teamId : project.getTeams()) {

            final Team team = TeamService.getTeam(teamId, true);
            teams.getItems().add(team);
        }
        teams.setTotal(teams.getItems().size());
        teams.setTotalKnown(true);

        return teams;
    }

    /**
     * Search Projects.
     *
     * @param user the user
     * @param searchParameters the search parameters
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<Project> searchProjects(final User user, final SearchParameters searchParameters) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final long start = System.currentTimeMillis();
            String query = getQueryForActiveOnly(searchParameters);
            final PfsParameter pfs = new PfsParameter();

            if (searchParameters.getOffset() != null) {

                pfs.setOffset(searchParameters.getOffset());
            }

            if (searchParameters.getLimit() != null) {

                pfs.setLimit(searchParameters.getLimit());
            }

            if (searchParameters.getSortAscending() != null) {

                pfs.setAscending(searchParameters.getSortAscending());
            }

            if (searchParameters.getSort() != null) {

                pfs.setSort(searchParameters.getSort());
            } else {

                pfs.setSort("name");
            }

            if (query != null && !query.equals("")) {

                query = IndexUtility.addWildcardsToQuery(query, Refset.class);
            }

            final ResultList<Project> results = service.find(query, pfs, Project.class, null);
            results.setTimeTaken(System.currentTimeMillis() - start);
            results.setTotalKnown(true);

            final List<Project> projectList = new ArrayList<>(results.getItems());

            for (Project project : projectList) {

                project = RefsetService.setProjectPermissions(user, project);

                if (!project.getRoles().contains(User.ROLE_VIEWER)) {

                    results.getItems().remove(project);
                }

            }

            return results;
        }

    }

    /**
     * Search Projects.
     *
     * @param project the project
     * @return the list of module IDs and names
     * @throws Exception the exception
     */
    public static Map<String, String> getModuleNames(final Project project) throws Exception {

        // Create Snowstorm URL
        final String conceptSearchUrl = SnowstormConnection.getBaseUrl() + project.getEdition().getBranch() + "/concepts/search";
        final String bodyBase = "{\"limit\": 1000, ";
        String bodyConceptIds = "\"conceptIds\":[";
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, String> moduleNames = new HashMap<>();

        for (final String moduleId : project.getEdition().getModules()) {
            bodyConceptIds += "\"" + moduleId + "\",";
        }

        bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",") + "]";

        final String searchBody = bodyBase + bodyConceptIds + "}";
        LOG.debug("getModuleNames URL: " + conceptSearchUrl);
        LOG.debug("getModuleNames BODY: " + searchBody);

        try (final Response response = SnowstormConnection.postResponse(conceptSearchUrl, searchBody)) {

            final String resultString = response.readEntity(String.class);

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception(
                        "call to url '" + conceptSearchUrl + "' for module name lookup wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> iterator = root.get("items").iterator();

            while (iterator != null && iterator.hasNext()) {

                final JsonNode conceptNode = iterator.next();
                final String conceptId = conceptNode.get("conceptId").asText();
                String name = "";

                if (conceptNode.get("fsn") != null && conceptNode.get("fsn").get("term") != null) {
                    name = conceptNode.get("fsn").get("term").asText();
                }

                moduleNames.put(conceptId, name);
            }
        }

        return moduleNames;
    }

    /**
     * Update projects.
     *
     * @param user the user
     * @param projectId the project id
     * @param project the project
     * @return the project
     * @throws Exception the exception
     */
    public static Project updateProjects(final User user, final String projectId, final Project project) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // Find the project
            final Project existingProject = getProject(projectId, true);

            RefsetService.setProjectPermissions(user, existingProject);
            checkPermissions(user, existingProject);

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            updateMemberships(existingProject, existingProject.getTeams(), project.getTeams());

            // Apply changes
            existingProject.patchFrom(project);

            // Update
            service.update(existingProject);
            service.add(AuditEntryHelper.updateProjectEntry(existingProject));
            service.commit();

            return existingProject;
        }

    }

    /**
     * Inactivate project. Also inactivates teams and refsets associated with the project.
     *
     * @param user the user
     * @param projectId the project id
     * @return the project
     * @throws Exception the exception
     */
    public static Project inactivateProject(final User user, final String projectId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // Find the object
            final Project project = getProject(projectId, true);

            RefsetService.setProjectPermissions(user, project);
            checkPermissions(user, project);

            final Set<String> copyOfProjectTeams = (project.getTeams() != null) ? new HashSet<String>(project.getTeams()) : new HashSet<String>();

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // inactivate projects, clear teams, and inactivate refsets
            project.setActive(false);

            updateMemberships(project, copyOfProjectTeams, null);

            if (project.getTeams() != null && !project.getTeams().isEmpty()) {

                for (final String teamId : project.getTeams()) {

                    final Team team = service.get(teamId, Team.class);

                    if (team != null && !team.getMembers().isEmpty()) {

                        team.getMembers().clear();
                        service.update(team);
                    }

                }

                project.getTeams().clear();
            }

            // also inactivate refsets
            final ResultList<Refset> projRefsets = service.find("projectId:" + project.getId() + " AND active:true", null, Refset.class, null);

            if (projRefsets.getItems() != null && !projRefsets.getItems().isEmpty()) {

                for (final Refset refset : projRefsets.getItems()) {

                    if (refset != null && !projRefsets.getItems().isEmpty()) {

                        refset.setBranchPath(RefsetService.getBranchPath(refset));
                        RefsetService.updatedRefsetStatus(service, user, refset, false);
                    }

                }

            }

            final Project updatedProject = service.update(project);
            service.add(AuditEntryHelper.changeProjectStatusEntry(project));
            service.commit();

            return updatedProject;
        }

    }

    /**
     * Check if a user can edit a project.
     *
     * @param user the user
     * @param project the project
     * @throws Exception the exception
     */
    public static void checkPermissions(final User user, final Project project) throws Exception {

        if (!project.getRoles().contains(User.ROLE_ADMIN)) {

            LOG.error("User does not have permission to edit this project.");
            throw new ForbiddenException("User does not have permission to edit this project.");
        }

    }

    /**
     * Add or removes users from Crowd based on addition or removal from teams from a project.
     *
     * @param project the project
     * @param oldTeams the old teams
     * @param newTeams the new teams
     * @throws Exception the exception
     */
    private static void updateMemberships(final Project project, final Set<String> oldTeams, final Set<String> newTeams) throws Exception {

        if (crowdUnitTestSkip == null || !"true".equalsIgnoreCase(crowdUnitTestSkip)) {

            LOG.info("CALLING CROWD API from ProjectService updateMemberships");

            final String organizationName = project.getEdition().getOrganizationName();
            final String editionName = project.getEdition().getShortName();
            final Set<String> copyOfOldTeams = (oldTeams != null) ? new HashSet<String>(oldTeams) : new HashSet<String>();
            final Set<String> copyOfNewTeams = (newTeams != null) ? new HashSet<String>(newTeams) : new HashSet<String>();

            if (oldTeams != null) {

                copyOfNewTeams.removeAll(oldTeams);
            }

            if (copyOfNewTeams != null && !copyOfNewTeams.isEmpty()) {
                for (final String teamId : copyOfNewTeams) {
                    final Team team = TeamService.getTeam(teamId, true);
                    // ignores 400 errors, if the group already exists
                    CrowdAPIClient.addGroup(organizationName, editionName, project.getName(), project.getDescription(), true, false);
                    if (team != null && team.getMemberList() != null) {
                        for (final String role : team.getRoles()) {
                            for (final User user : team.getMemberList()) {
                                final String groupName =
                                    CrowdGroupNameAlgorithm.buildCrowdGroupName(organizationName, editionName, project.getCrowdProjectId(), role);
                                CrowdAPIClient.addMembership(groupName, user.getUserName());
                            }
                        }
                    }
                }
            }

            if (newTeams != null) {
                copyOfOldTeams.removeAll(newTeams);
            }

            if (copyOfOldTeams != null && !copyOfOldTeams.isEmpty()) {
                for (final String teamId : copyOfOldTeams) {
                    final Team team = TeamService.getTeam(teamId, true);
                    if (team != null && team.getMemberList() != null) {
                        for (final String role : team.getRoles()) {
                            for (final User user : team.getMemberList()) {
                                final String groupName =
                                    CrowdGroupNameAlgorithm.buildCrowdGroupName(organizationName, editionName, project.getCrowdProjectId(), role);
                                CrowdAPIClient.deleteMembership(groupName, user.getUserName());
                            }
                        }
                    }
                }
            }
        }
    }
}
