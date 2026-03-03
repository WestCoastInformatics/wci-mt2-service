/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;

import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class MapProjectService.
 */
public class MapProjectService extends BaseService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapProjectService.class);

    /** The terminology handler. */
    private static TerminologyServerHandler terminologyHandler;

    static {

        // Instantiate terminology handler
        try {
            String key = "terminology.handler";
            String handlerName = PropertyUtility.getProperty(key);
            if (handlerName.isEmpty()) {
                throw new Exception("terminology.handler expected and does not exist.");
            }

            terminologyHandler = HandlerUtility.newStandardHandlerInstanceWithConfiguration(key, handlerName, TerminologyServerHandler.class);

        } catch (Exception e) {
            LOG.error("Failed to initialize terminology.handler - serious error", e);
            terminologyHandler = null;
        }
    }

    /**
     * Creates the map project.
     *
     * @param service the service
     * @param user the user
     * @param mapProject the map project
     * @return the map project
     * @throws Exception the exception
     */
    public static MapProject createMapProject(final TerminologyService service, final User user, final MapProject mapProject) throws Exception {
        // When a mapProject is created, it does not have teams, those are added
        // through
        // update

        // RefsetService.setProjectPermissions(user, mapProject);
        // checkPermissions(user, mapProject);

        // TODO: Check user permissions when not using Crowd

        service.add(mapProject);
        service.add(AuditEntryHelper.addMapProjectEntry(mapProject));

        // Return the response
        return mapProject;

    }

    /**
     * Returns the mapProject if active.
     *
     * @param service the service
     * @param mapProjectId the mapProject id
     * @param includeMembers the include members
     * @return the mapProject
     * @throws Exception the exception
     */
    public static MapProject getMapProject(final TerminologyService service, final String mapProjectId, final boolean includeMembers) throws Exception {

        final MapProject mapProject = service.findSingle("id: " + mapProjectId + " AND active:true", MapProject.class, null);

        if (mapProject == null) {

            final String errorMessage = "Unable to find mapProject for id " + mapProjectId + ".";
            LOG.info(errorMessage);
            throw new NotFoundException(errorMessage);
        }

        // if (includeMembers) {
        //
        // final Set<User> members = new HashSet<>();
        //
        // for (final String teamId : mapProject.getTeams()) {
        //
        // final Team team = service.get(teamId, Team.class);
        //
        // if (team != null && team.getMembers() != null) {
        //
        // for (final String userId : team.getMembers()) {
        //
        // final User member = service.get(userId, User.class);
        // members.add(member);
        // }
        //
        // }
        //
        // }
        //
        // project.getMemberList().addAll(members);
        // }

        return mapProject;

    }

    /**
     * Returns the mapProject names for edition.
     *
     * @param editionId the edition id
     * @return the mapProject names for edition
     * @throws Exception the exception
     */
    public static Set<String> getMapProjectNamesForEdition(final String editionId) throws Exception {

        final Set<String> mapProjectNames = new HashSet<>();

        final List<MapProject> mapProjects = getMapProjectsForEdition(editionId);

        if (mapProjects != null) {

            mapProjects.stream().forEach(project -> mapProjectNames.add(project.getName()));
        }

        return mapProjectNames;

    }

    /**
     * Returns the mapProjects for edition.
     *
     * @param editionId the edition id
     * @return the mapProjects for edition
     * @throws Exception the exception
     */
    public static List<MapProject> getMapProjectsForEdition(final String editionId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<MapProject> mapProjects = service.find("edition.id: " + editionId + " AND active:true", null, MapProject.class, null);

            if (mapProjects != null) {
                return mapProjects.getItems();
            }

            return new ArrayList<MapProject>();
        }
    }

    /**
     * Returns the team assigned to this mapProject.
     *
     * @param service the service
     * @param mapProjectId the mapProject ID
     * @return the mapProject teams
     * @throws Exception the exception
     */
    public static ResultList<Team> getMapProjectTeams(final TerminologyService service, final String mapProjectId) throws Exception {

        final MapProject mapProject = getMapProject(service, mapProjectId, false);
        final ResultList<Team> teams = new ResultList<>();

        // for (final String teamId : mapProject.getTeams()) {
        //
        // final Team team = TeamService.getTeam(teamId, true);
        // teams.getItems().add(team);
        // }
        teams.setTotal(teams.getItems().size());
        teams.setTotalKnown(true);

        return teams;
    }

    /**
     * Search MapProjects.
     *
     * @param service the service
     * @param user the user
     * @param searchParameters the search parameters
     * @return the list of mapProjects
     * @throws Exception the exception
     */
    public static ResultList<MapProject> searchMapProjects(final TerminologyService service, final User user, final SearchParameters searchParameters)
        throws Exception {

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

        final ResultList<MapProject> results = service.find(query, pfs, MapProject.class, null);
        results.setTimeTaken(System.currentTimeMillis() - start);
        results.setTotalKnown(true);

        final List<MapProject> mapProjectList = new ArrayList<>(results.getItems());

        for (final MapProject mapProject : mapProjectList) {

            // TODO: required for MT2?
            // final Organization organization =
            // OrganizationService.getOrganization(service, user,
            // mapProject.getOrganizationId(), true);
            // if (organization.isAffiliate()
            // && !organization.getMembers().stream().anyMatch(m ->
            // m.getId().equals(user.getId()))) {
            // results.getItems().remove(mapProject);
            // continue;
            // }

            // TODO: determine permissions without Crowd
            // mapProject = RefsetService.setProjectPermissions(user, mapProject);

            if (!mapProject.getRoles().contains(User.ROLE_VIEWER)) {

                results.getItems().remove(mapProject);
            }

        }

        return results;

    }

    /**
     * Gets the module names.
     *
     * @param edition the edition
     * @return the module names
     * @throws Exception the exception
     */
    public static Map<String, String> getModuleNames(final Edition edition) throws Exception {

        return terminologyHandler.getModuleNames(edition);

    }

    /**
     * Update map project.
     *
     * @param service the service
     * @param user the user
     * @param mapProjectId the mapProject id
     * @param mapProject the mapProject
     * @return the mapProject
     * @throws Exception the exception
     */
    public static MapProject updateMapProject(final TerminologyService service, final User user, final String mapProjectId, final MapProject mapProject)
        throws Exception {

        // Find the mapProject
        final MapProject existingProject = getMapProject(service, mapProjectId, true);

        // TODO: determine permissions without Crowd
        // RefsetService.setProjectPermissions(user, existingProject);
        checkPermissions(user, existingProject);

        // TODO: determine permissions without Crowd
        // updateMemberships(existingProject, existingProject.getTeams(),
        // mapProject.getTeams());

        // Apply changes
        existingProject.patchFrom(mapProject);

        // Update
        service.update(existingProject);
        service.add(AuditEntryHelper.updateMapProjectEntry(existingProject));

        return existingProject;

    }

    /**
     * Inactivate mapProject. Also inactivates teams and refsets associated with the mapProject.
     *
     * @param service the service
     * @param user the user
     * @param mapProjectId the mapProject id
     * @return the mapProject
     * @throws Exception the exception
     */
    public static MapProject inactivateMapProject(final TerminologyService service, final User user, final String mapProjectId) throws Exception {

        // Find the object
        final MapProject mapProject = getMapProject(service, mapProjectId, true);

        // RefsetService.setProjectPermissions(user, mapProject);
        // checkPermissions(user, mapProject);

        // final Set<String> copyOfProjectTeams = (mapProject.getTeams() != null)
        // ? new HashSet<String>(mapProject.getTeams())
        // : new HashSet<String>();


        // // inactivate mapProjects, clear teams, and inactivate refsets
        // project.setActive(false);
        //
        // updateMemberships(project, copyOfProjectTeams, null);
        //
        // if (project.getTeams() != null && !project.getTeams().isEmpty()) {
        //
        // for (final String teamId : mapProject.getTeams()) {
        //
        // final Team team = service.get(teamId, Team.class);
        //
        // if (team != null && !team.getMembers().isEmpty()) {
        //
        // team.getMembers().clear();
        // service.update(team);
        // }
        //
        // }
        //
        // project.getTeams().clear();
        // }
        //
        // // also inactivate refsets
        // final ResultList<Refset> projRefsets = service.find("projectId:" +
        // mapProject.getId() + " AND active:true",
        // null, Refset.class, null);
        //
        // if (projRefsets.getItems() != null &&
        // !projRefsets.getItems().isEmpty()) {
        //
        // for (final Refset refset : projRefsets.getItems()) {
        //
        // if (refset != null && !projRefsets.getItems().isEmpty()) {
        //
        // refset.setBranchPath(RefsetService.getBranchPath(refset));
        // RefsetService.updatedRefsetStatus(service, user, refset, false);
        // }
        //
        // }
        //
        // }

        final MapProject updatedProject = service.update(mapProject);
        service.add(AuditEntryHelper.changeMapProjectStatusEntry(mapProject));

        return updatedProject;

    }

    /**
     * Check if a user can edit a mapProject.
     *
     * @param user the user
     * @param mapProject the mapProject
     * @throws Exception the exception
     */
    public static void checkPermissions(final User user, final MapProject mapProject) throws Exception {

        if (!mapProject.getRoles().contains(User.ROLE_ADMIN)) {

            LOG.error("User does not have permission to edit this mapProject.");
            throw new ForbiddenException("User does not have permission to edit this mapProject.");
        }

    }

    // /**
    // * Add or removes users from Crowd based on addition or removal from teams
    // from
    // * a mapProject.
    // *
    // * @param mapProject the mapProject
    // * @param oldTeams the old teams
    // * @param newTeams the new teams
    // * @throws Exception the exception
    // */
    // private static void updateMemberships(final MapProject mapProject, final
    // Set<String> oldTeams, final Set<String> newTeams)
    // throws Exception {
    //
    // if (crowdUnitTestSkip == null ||
    // !"true".equalsIgnoreCase(crowdUnitTestSkip)) {
    //
    // LOG.info("CALLING CROWD API from MapProjectService updateMemberships");
    //
    // final String organizationName =
    // mapProject.getEdition().getOrganizationName();
    // final String editionName = mapProject.getEdition().getShortName();
    // final Set<String> copyOfOldTeams = (oldTeams != null) ? new
    // HashSet<String>(oldTeams)
    // : new HashSet<String>();
    // final Set<String> copyOfNewTeams = (newTeams != null) ? new
    // HashSet<String>(newTeams)
    // : new HashSet<String>();
    //
    // if (oldTeams != null) {
    //
    // copyOfNewTeams.removeAll(oldTeams);
    // }
    //
    // if (!copyOfNewTeams.isEmpty()) {
    // for (final String teamId : copyOfNewTeams) {
    // final Team team = TeamService.getTeam(teamId, true);
    // // ignores 400 errors, if the group already exists
    // CrowdAPIClient.addGroup(organizationName, editionName,
    // mapProject.getName(), mapProject.getDescription(),
    // true, false);
    // if (team != null && team.getMemberList() != null) {
    // for (final String role : team.getRoles()) {
    // for (final User user : team.getMemberList()) {
    // final String groupName =
    // CrowdGroupNameAlgorithm.buildCrowdGroupName(organizationName,
    // editionName, mapProject.getCrowdProjectId(), role);
    // CrowdAPIClient.addMembership(groupName, user.getUserName());
    // }
    // }
    // }
    // }
    // }
    //
    // if (newTeams != null) {
    // copyOfOldTeams.removeAll(newTeams);
    // }
    //
    // if (!copyOfOldTeams.isEmpty()) {
    // for (final String teamId : copyOfOldTeams) {
    // final Team team = TeamService.getTeam(teamId, true);
    // if (team != null && team.getMemberList() != null) {
    // for (final String role : team.getRoles()) {
    // for (final User user : team.getMemberList()) {
    // final String groupName =
    // CrowdGroupNameAlgorithm.buildCrowdGroupName(organizationName,
    // editionName, mapProject.getCrowdProjectId(), role);
    // CrowdAPIClient.deleteMembership(groupName, user.getUserName());
    // }
    // }
    // }
    // }
    // }
    // }
    // }
}
