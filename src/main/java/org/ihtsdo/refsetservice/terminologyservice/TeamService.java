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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.ResultListUser;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.TeamType;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.UserRole;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Class TeamService.
 */
public class TeamService extends BaseService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(TeamService.class);

    /** The name prefix for organization level teams. */
    private static final String ORGANIZATION_LEVEL_TEAM_PREFIX = "Administrator(s) for organization ";

    /** The organization level team description. */
    private static final String ORGANIZATION_LEVEL_TEAM_DESCRIPTION = "'s dedicated ADMIN Team to manage their projects, members, and teams with.";

    /** The crowd unit test skip. */
    private static String crowdUnitTestSkip;

    static {
        crowdUnitTestSkip = PropertyUtility.getProperty("crowd.unit.test.skip");
    }

    /**
     * Creates the team. (Required are name, description, organization, primaryContactEmail and roles); Members (Users) can be included but must already exist in the
     * application. Do not use setMemberList or setUserRoles. These are not persisted and are meant to return additional information to the user interface.
     *
     * @param authUser the auth user
     * @param team the team
     * @return the team
     * @throws Exception the exception
     */
    public static Team createTeam(final User authUser, final Team team) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Team newTeam = new Team(team);
            checkEditPermissions(authUser, newTeam);
            validateTeamData(service, newTeam, true);

            service.setModifiedBy(authUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            Team addedTeam = service.add(team);
            service.add(AuditEntryHelper.addTeamEntry(team));
            service.commit();

            setUserRoles(authUser, newTeam, newTeam.getUserRoles());

            return addedTeam;
        }
    }

    /**
     * Validate team data.
     *
     * @param service the Terminology Service
     * @param team the team
     * @param isNew is this a new team
     * @throws Exception the exception
     */
    public static void validateTeamData(final TerminologyService service, final Team team, final boolean isNew) throws Exception {

        final boolean isOrganizationTeam = isOrganizationTeam(team);

        if (!StringUtility.isEmpty(team.getName())) {

            String query = "(name: " + QueryParserBase.escape(team.getName()) + ") AND organizationId: " + team.getOrganizationId();

            if (!isNew) {
                query += " AND !(id: " + team.getId() + ")";
            } else {

                final ResultList<Team> results = service.find(query, null, Team.class, null);

                if (results.getTotal() > 0) {
                    final String message = "There is already a team with that name in this Organization";
                    LOG.error(message);
                    throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, message);
                }
            }

        }

        if (team.getRoles().isEmpty() && isNew) {

            final String message = "A new team must have at least one role associated with it";
            LOG.error(message);
            throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, message);

        } else if (!team.getRoles().isEmpty()) {

            if (isOrganizationTeam && !team.getRoles().contains(User.ROLE_ADMIN)) {

                LOG.warn("An organization level team must include the admin role, adding it to team");
                team.getRoles().add(User.ROLE_ADMIN);
            }
        }

        if (team.getMembers().isEmpty() && !isNew && isOrganizationTeam) {

            final String message = "This team must have at least one member assigned to it";
            LOG.error(message);
            throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, message);
        }
    }

    /**
     * Returns the team. If includeMembers is true, the returning object will include a list of Users.
     * 
     * @param id the id
     * @param includeMembers the boolean to include members Include full User object of team members
     * @return the team
     * @throws Exception the exception
     */
    public static Team getTeam(final String id, final boolean includeMembers) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Team team = service.findSingle("id: " + id + " AND active:true", Team.class, null);

            if (team == null) {

                final String message = "Unable to find team for id " + id + ".";
                LOG.error(message);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
            }

            if (includeMembers) {

                for (final String userId : team.getMembers()) {

                    final ResultList<User> users = service.find("id:" + userId, null, User.class, null);

                    if (users != null && users.getItems() != null) {

                        for (final User user : users.getItems()) {

                            final SearchParameters sp = new SearchParameters();
                            sp.setQuery("members:" + user.getId());
                            final ResultList<Team> teamsResultList = TeamService.searchTeams(user, sp);

                            if (teamsResultList != null && teamsResultList.getItems() != null) {
                                user.getTeams().addAll(teamsResultList.getItems());
                            }
                        }

                        team.getMemberList().addAll(users.getItems());
                    }
                }
            }

            setUserRoles(SecurityService.getUserFromSession(), team, team.getUserRoles());

            return team;
        }
    }

    /**
     * Update the team. Required are name, description, organization, primaryContactEmail and roles); Members (Users) can be included but must already exist in the
     * application. Do not use setMemberList or setUserRoles. These are not persisted and are meant to return additional information to the user interface.
     *
     *
     * @param authUser the auth user
     * @param team the team
     * @return the team
     * @throws Exception the exception
     */
    public static Team updateTeam(final User authUser, final Team team) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Team existingTeam = getTeam(team.getId(), true);

            checkEditPermissions(authUser, team);
            validateTeamData(service, team, false);

            existingTeam.patchFrom(team);

            service.setModifiedBy(authUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            Team updatedTeam = service.update(existingTeam);
            service.add(AuditEntryHelper.updateTeamEntry(updatedTeam));
            service.commit();

            return updatedTeam;
        }
    }

    /**
     * In-activate team. Set active to false.
     *
     * @param user the user
     * @param teamId the team id
     * @throws Exception the exception
     */
    public static Team inactivateTeam(final User user, final String teamId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // Find the object
            Team team = getTeam(teamId, true);

            checkEditPermissions(user, team);

            if (isOrganizationTeam(team)) {

                final String message = "You can not inactivate this team.";
                LOG.error(message);
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, message);
            }

            for (final User teamMember : team.getMemberList()) {

                team = removeUserFromTeam(service, user, team, teamMember);
            }

            team.setActive(false);
            final Team updatedTeam = service.update(team);
            service.add(AuditEntryHelper.changeTeamStatusEntry(updatedTeam));

            final List<Project> teamProjects = getTeamProjects(updatedTeam);

            for (final Project teamProject : teamProjects) {

                teamProject.getTeams().remove(updatedTeam.getId());
                service.update(teamProject);
            }

            service.commit();

            return updatedTeam;
        }
    }

    /**
     * Search Teams.
     *
     * @param authUser the auth user
     * @param searchParameters the search parameters
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<Team> searchTeams(final User authUser, final SearchParameters searchParameters) throws Exception {

        return searchTeams(authUser, searchParameters, false, false, false);
    }

    /**
     * Search Teams.
     *
     * @param user the user
     * @param searchParameters the search parameters
     * @param includeMembers Include members List<Users> in memberList.
     * @param onlyUsersTeams return only the teams the user is a member off or has permission to admin
     * @param hideOrganizationTeams the hide organization teams
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<Team> searchTeams(final User user, final SearchParameters searchParameters, final boolean includeMembers, final boolean onlyUsersTeams,
        final boolean hideOrganizationTeams) throws Exception {

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

            searchParameters.setActiveOnly(true);

            if (query != null && !query.equals("")) {
                query = IndexUtility.addWildcardsToQuery(query, Team.class);
            }

            final ResultList<Team> results = service.find(query, pfs, Team.class, null);
            final ResultList<Team> resultsToReturn = new ResultList<>();

            for (final Team team : results.getItems()) {

                final String systemUserList = PropertyUtility.getProperty("refset.service.system.accounts");
                Set<String> systemUserSet = new HashSet<>();
                if (StringUtils.isNotBlank(systemUserList)) {
                    systemUserSet = new HashSet<>(Arrays.asList(systemUserList.split(",")));
                }

                // if only the user's teams should be returned then make sure the user is an
                // admin or a member of the team
                if ((onlyUsersTeams && !canUserViewTeam(user, team, false)) || (hideOrganizationTeams && isOrganizationTeam(team))) {
                    continue;
                }

                if (includeMembers) {

                    for (final String userId : team.getMembers()) {

                        final User member = service.findSingle("id:" + userId, User.class, null);
                        if (member == null || systemUserSet.contains(member.getUserName())) {
                            continue;
                        }

                        member.setTeams(new HashSet<Team>(getUserTeams(user, member, team.getOrganizationId(), true)));
                        team.getMemberList().add(member);
                    }
                }

                setUserRoles(user, team, team.getUserRoles());
                resultsToReturn.getItems().add(team);
            }

            resultsToReturn.setTimeTaken(System.currentTimeMillis() - start);
            resultsToReturn.setTotalKnown(true);
            resultsToReturn.setTotal(resultsToReturn.getItems().size());

            // LOG.debug("TEAM SEARCH resultsToReturn: " + resultsToReturn);

            return resultsToReturn;
        }
    }

    /**
     * Returns the teams a user can is either a member of or can admin, depending on flags.
     *
     * @param user the user making the call
     * @param teamUser the user to look up teams for
     * @param organizationId the organization id to limit the teams to, or null for all teams
     * @param onlyMemberOf return only teams the user is a member of, not ones they can admin
     * @return the user's teams
     * @throws Exception the exception
     */
    public static List<Team> getUserTeams(final User user, final User teamUser, final String organizationId, final boolean onlyMemberOf) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            String query = "";

            if (organizationId != null) {
                query = "organizationId: " + organizationId;
            }

            final ResultList<Team> results = service.find(query, null, Team.class, null);
            final List<Team> teamList = new ArrayList<>();

            for (final Team team : results.getItems()) {

                if (canUserViewTeam(teamUser, team, onlyMemberOf)) {
                    teamList.add(team);
                }
            }

            return teamList;
        }
    }

    /**
     * Adds the user to team.
     *
     * @param user the user
     * @param teamId the team id
     * @param email the email
     * @return the team
     * @throws Exception the exception
     */
    public static Team addUserToTeam(final User user, final String teamId, final String email) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());

            final Team team = getTeam(teamId, true);

            return addUserToTeam(service, user, team, email);
        }
    }

    /**
     * Adds the user to team.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param team the team
     * @param email the email
     * @return the team
     * @throws Exception the exception
     */
    public static Team addUserToTeam(final TerminologyService service, final User user, final Team team, final String email) throws Exception {

        final User userToAdd = service.findSingle("email:" + email, User.class, null);

        if (userToAdd == null) {

            final String message = "User with " + email + " does not exist.";
            LOG.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        return addUserToTeam(service, user, team, userToAdd);
    }

    /**
     * Adds the user to team.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param team the team
     * @param userToAdd the user to add to the team
     * @return the team
     * @throws Exception the exception
     */
    public static Team addUserToTeam(final TerminologyService service, final User user, final Team team, final User userToAdd) throws Exception {

        final Organization organization = team.getOrganization();
        final Edition edition = EditionService.getEditionForOrganization(organization.getId());
        final Set<User> organizationMembers = organization.getMembers();

        if (!organizationMembers.contains(userToAdd)) {

            final String message = "User with " + userToAdd.getEmail() + " is not a member of organization " + organization.getName() + ".";
            LOG.error(message);
            throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, message);
        }

        if (team.getMembers() != null && team.getMembers().contains(userToAdd.getId())) {

            final String message = "User with " + userToAdd.getEmail() + " is already a member of team " + team.getName() + ".";
            LOG.error(message);
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }

        team.getMembers().add(userToAdd.getId());

        service.beginTransaction();
        final Team updatedTeam = service.update(team);
        service.add(AuditEntryHelper.addUserToTeamEntry(updatedTeam, userToAdd));
        service.commit();

        setUserRoles(userToAdd, updatedTeam, updatedTeam.getUserRoles());

        // add user to crowd groups
        if (crowdUnitTestSkip == null || !"true".equalsIgnoreCase(crowdUnitTestSkip)) {

            LOG.info("CALLING CROWD API");
            final String teamsQuery = "teams:" + updatedTeam.getId();
            final SearchParameters searchParameters = new SearchParameters();
            searchParameters.setQuery(teamsQuery);
            final ResultList<Project> projectList = ProjectService.searchProjects(user, searchParameters);

            if (projectList != null && projectList.getItems() != null) {

                for (final Project project : projectList.getItems()) {

                    CrowdAPIClient.addGroup(project.getEdition().getShortName(), project.getName(), project.getDescription(), true, false);

                    for (final String role : updatedTeam.getRoles()) {

                        final String groupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(project.getEdition().getShortName(), project.getCrowdProjectId(), role);
                        CrowdAPIClient.addMembership(groupName, userToAdd.getUserName());
                    }
                }
            }

            if (team.getType().equalsIgnoreCase(TeamType.ORGANIZATION.getText())) {

                CrowdAPIClient.addGroup(edition.getShortName(), "all", "Organization Administrators", false, true);

                final String groupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(edition.getShortName(), "all", "admin");

                CrowdAPIClient.addMembership(groupName, userToAdd.getUserName());

            }

        } else {
            LOG.info("SKIP CALLING CROWD API");
        }

        return updatedTeam;
    }

    /**
     * Removes the user from team.
     *
     * @param authUser the auth user
     * @param teamId the team ID
     * @param userId the user ID to remove
     * @return the team
     * @throws Exception the exception
     */
    public static Team removeUserFromTeam(final User authUser, final String teamId, final String userId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(authUser.getUserName());

            final Team team = getTeam(teamId, true);

            return removeUserFromTeam(service, authUser, team, userId);
        }
    }

    /**
     * Removes the user from team.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param team the team
     * @param userId the user ID to remove
     * @return the team
     * @throws Exception the exception
     */
    public static Team removeUserFromTeam(final TerminologyService service, final User user, final Team team, final String userId) throws Exception {

        final User userToRemove = service.get(userId, User.class);

        if (userToRemove == null) {

            final String message = "Unable to find user for id " + userId + ".";
            LOG.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        return removeUserFromTeam(service, user, team, userToRemove);
    }

    /**
     * Removes the user from team.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param team the team
     * @param userToRemove the user to remove
     * @return the team
     * @throws Exception the exception
     */
    public static Team removeUserFromTeam(final TerminologyService service, final User user, final Team team, final User userToRemove) throws Exception {

        // The user being removed has at least one reference set in Edit or Review
        // assigned to them
        // As the admin, you are able to un-assign the reference set(s) first before
        // inactivating user.
        final List<Project> projectsForTeam = getTeamProjects(team);
        if (projectsForTeam != null && !projectsForTeam.isEmpty()) {

            final SearchParameters sp = new SearchParameters();
            final String projectIds = "(" + projectsForTeam.stream().map(Project::getId).collect(Collectors.joining(" OR ", "projectId: ", "")) + ")";
            sp.setQuery("assignedUser: " + userToRemove.getUserName() + " AND versionStatus:IN DEVELOPMENT AND (workflowStatus: IN_EDIT OR workflowStatus: IN_REVIEW) AND " + projectIds);
            final ResultList<Refset> refsets = service.find(sp.getQuery(), null, Refset.class, null);

            if (!refsets.getItems().isEmpty()) {
                final String message = "User " + userToRemove.getName() + " has a reference set \"In Edit\" or \"In Review\" assigned to them. As the admin, you are able "
                        + " to un-assign the reference set(s) first before inactivating user.";
                LOG.error(message);
                throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, message);
            }
        }
        if (team.getMembers() != null) {

            if (team.getMembers().contains(userToRemove.getId())) {
                team.getMembers().remove(userToRemove.getId());
            } else {

                final String message = "User " + userToRemove.getUserName() + " is not a member of team " + team.getName() + ".";
                LOG.error(message);
                throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, message);
            }
        }

        validateTeamData(service, team, false);

        service.setModifiedBy(user.getUserName());

        Team updatedTeam = service.update(team);
        service.add(AuditEntryHelper.removeUserFromTeamEntry(updatedTeam, userToRemove));

        // remove user from crowd groups
        if (crowdUnitTestSkip == null || !"true".equalsIgnoreCase(crowdUnitTestSkip)) {

            LOG.info("CALLING CROWD API");
            final String teamsQuery = "teams:" + team.getId();
            final SearchParameters searchParameters = new SearchParameters();
            searchParameters.setQuery(teamsQuery);
            final ResultList<Project> projectList = ProjectService.searchProjects(user, searchParameters);

            if (projectList != null && projectList.getItems() != null) {
                for (final Project project : projectList.getItems()) {
                    for (final String role : updatedTeam.getRoles()) {
                        final String groupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(project.getEdition().getShortName(), project.getCrowdProjectId(), role);
                        CrowdAPIClient.deleteMembership(groupName, userToRemove.getUserName());
                    }
                }
            }

            final Edition edition = EditionService.getEditionForOrganization(updatedTeam.getOrganization().getId());

            if (updatedTeam.getType().equalsIgnoreCase(TeamType.ORGANIZATION.getText())) {

                final String groupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(edition.getShortName(), "all", "admin");
                CrowdAPIClient.deleteMembership(groupName, userToRemove.getUserName().replace(" ", "%20"));

            }

        } else {
            LOG.info("SKIP CALLING CROWD API");
        }

        return updatedTeam;
    }

    /**
     * Adds the role to team.
     *
     * @param authUser the auth user
     * @param teamId the team id
     * @param role the role
     * @throws Exception the exception
     */
    public static Team addRoleToTeam(final User authUser, final String teamId, final String role) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // find team
            final Team team = getTeam(teamId, true);

            checkEditPermissions(authUser, team);

            if (StringUtils.isBlank(role) && !UserRole.getAllRoles().contains(UserRole.valueOf(role))) {

                final String message = "Role " + role + " does not exist.";
                LOG.error(message);
                throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, message);
            }

            if (team.getRoles().contains(role.toUpperCase())) {

                final String message = "Role " + role + " is already a exists for team " + teamId + ".";
                LOG.info(message);
                throw new ResponseStatusException(HttpStatus.CONFLICT, message);
            }

            team.getRoles().add(UserRole.valueOf(role).toString());

            service.setModifiedBy(authUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final Team updatedTeam = service.update(team);
            service.add(AuditEntryHelper.addRoleToTeamEntry(updatedTeam, role));
            service.commit();

            // add user to crowd groups if team is assigned to projects.
            if (crowdUnitTestSkip == null || !"true".equalsIgnoreCase(crowdUnitTestSkip)) {
                LOG.info("CALLING CROWD API from ProjectService updateMemberships");

                final List<Project> projects = getTeamProjects(updatedTeam);

                if (projects != null) {
                    for (final Project project : projects) {

                        if (updatedTeam.getMemberList() != null) {
                            for (final User user : updatedTeam.getMemberList()) {

                                final String groupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(project.getEdition().getShortName(), project.getCrowdProjectId(), role);
                                CrowdAPIClient.addMembership(groupName, user.getUserName());
                            }
                        }
                    }
                }
            }

            return updatedTeam;
        }
    }

    /**
     * Removes the role from team.
     *
     * @param authUser the auth user
     * @param teamId the team id
     * @param role the role
     * @throws Exception the exception
     */
    public static Team removeRoleFromTeam(final User authUser, final String teamId, final String role) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Team team = getTeam(teamId, true);

            checkEditPermissions(authUser, team);

            if (StringUtils.isBlank(role) && !Arrays.asList(UserRole.values()).contains(role.toUpperCase())) {

                final String message = "Role " + role + " does not exist.";
                LOG.error(message);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
            }

            if (!team.getRoles().contains(role.toUpperCase())) {

                final String message = "Role " + role + " does not exist for team " + teamId + ".";
                LOG.error(message);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
            }

            team.getRoles().remove(UserRole.valueOf(role).toString());
            validateTeamData(service, team, false);

            service.setModifiedBy(authUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final Team updatedTeam = service.update(team);
            service.add(AuditEntryHelper.removeRoleFromTeamEntry(team, role));
            service.commit();

            // remove users from team if team assigned to projects.
            if (crowdUnitTestSkip == null || !"true".equalsIgnoreCase(crowdUnitTestSkip)) {
                LOG.info("CALLING CROWD API from ProjectService updateMemberships");

                final List<Project> projects = getTeamProjects(team);
                if (projects != null) {
                    for (final Project project : projects) {
                        if (team != null && team.getMemberList() != null) {
                            for (final User user : team.getMemberList()) {
                                final String groupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(project.getEdition().getShortName(), project.getCrowdProjectId(), role);
                                CrowdAPIClient.deleteMembership(groupName, user.getUserName());
                            }
                        }
                    }
                }
            }
            
            return updatedTeam;
        }
    }

    /**
     * Returns the team users.
     *
     * @param authUser the auth user
     * @param teamId the team id
     * @return the team users
     * @throws Exception the exception
     */
    public static ResultListUser getTeamUsers(final User authUser, final String teamId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Team team = getTeam(teamId, true);
            final ResultListUser users = new ResultListUser();

            for (final String userId : team.getMembers()) {

                final User u = service.get(userId, User.class);
                users.getItems().add(u);
            }

            users.setTotal(users.getItems().size());

            return users;
        }
    }

    /**
     * Returns the team users.
     *
     * @param team the team
     * @return the team projects
     * @throws Exception the exception
     */
    public static List<Project> getTeamProjects(final Team team) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<Project> allOrganizationProjects = service.find("active: true AND organizationId: " + team.getOrganizationId(), null, Project.class, null);
            final List<Project> projects = new ArrayList<>();

            for (final Project organizationProject : allOrganizationProjects.getItems()) {

                if (organizationProject.getTeams().contains(team.getId())) {
                    projects.add(organizationProject);
                }
            }

            return projects;
        }
    }

    /**
     * Check if this is a special organization level team.
     *
     * @param team the team
     * @return is this a special organization level team
     * @throws Exception the exception
     */
    public static boolean isOrganizationTeam(final Team team) throws Exception {

        return TeamType.ORGANIZATION.getText().equalsIgnoreCase(team.getType());
    }

    /**
     * Throw an exception if a user can't edit a team.
     *
     * @param user the user
     * @param team the team
     * @throws Exception the exception
     */
    public static void checkEditPermissions(final User user, final Team team) throws Exception {

        if (!canUserEditTeam(user, team)) {

            final String message = "User does not have permission to edit this team.";
            LOG.error(message);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        }

    }

    /**
     * Check if a user can edit a team.
     *
     * @param user the user
     * @param team the team
     * @return can the user edit the team
     * @throws Exception the exception
     */
    public static boolean canUserEditTeam(final User user, final Team team) throws Exception {

        final Organization organization = team.getOrganization();
        final boolean isOrganizationAdmin = user.doesUserHavePermission(User.ROLE_ADMIN, organization);

        return (isOrganizationAdmin || (team.getRoles().contains(User.ROLE_ADMIN) && team.getMembers().contains(user.getId())));
    }

    /**
     * Check if a user can view a team.
     *
     * @param user the user
     * @param team the team
     * @param onlyMemberOf return only teams the user is a member of, not ones they can admin
     * @return can the user view the team
     * @throws Exception the exception
     */
    public static boolean canUserViewTeam(final User user, final Team team, final boolean onlyMemberOf) throws Exception {

        final Organization organization = team.getOrganization();
        final boolean isOrganizationAdmin = user.doesUserHavePermission(User.ROLE_ADMIN, organization);

        return ((!onlyMemberOf && isOrganizationAdmin) || team.getMembers().contains(user.getId()));
    }

    /**
     * set the list of roles a user has for a team.
     *
     * @param user the user
     * @param team the team
     * @param roles the role list to populate
     * @return the list of roles for the team
     * @throws Exception the exception
     */
    public static List<String> setUserRoles(final User user, final Team team, final List<String> roles) throws Exception {

        if (canUserEditTeam(user, team)) {
            roles.add(User.ROLE_ADMIN);
        }

        if (canUserViewTeam(user, team, false)) {
            roles.add(User.ROLE_VIEWER);
        }

        return roles;
    }

    /**
     * Calculates and returns the name of the organization admin team.
     *
     * @param organization the organization the admin team is for
     * @return the name of the organization admin team
     */
    public static String generateOrganizationTeamName(final Organization organization) {

        return ORGANIZATION_LEVEL_TEAM_PREFIX + organization.getName();
    }

    /**
     * Calculates and returns the description of the organization admin team.
     *
     * @param organization the organization the admin team is for
     * @return the description of the organization admin team
     */
    public static String getOrganizationTeamDescription(final Organization organization) {

        return organization.getName() + ORGANIZATION_LEVEL_TEAM_DESCRIPTION;
    }
}
