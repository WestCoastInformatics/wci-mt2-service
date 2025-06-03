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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.InviteRequest;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.ResultListUser;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.UserRole;
import org.ihtsdo.refsetservice.model.enums.TeamType;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.EmailUtility;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Class OrganizationService.
 */
public class OrganizationService extends BaseService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationService.class);

    /** The Constant EMAIL_SUBJECT. */
    private static final String EMAIL_SUBJECT = "SNOMED International Refset Tool - ";

    /** The Constant INVITE_ACTION. */
    private static final String INVITE_ACTION = "Invite";

    /** The Constant INVITE_ACCEPTED. */
    private static final String INVITE_ACCEPTED = "Invite accepted";

    /** The Constant INVITE_DECLINED. */
    private static final String INVITE_DECLINED = "Invite declined";

    /** The app url root. */
    private static String appUrlRoot;

    /** The crowd unit test skip. */
    private static String crowdUnitTestSkip;

    static {

        appUrlRoot = PropertyUtility.getProperties().getProperty("app.url.root");
        crowdUnitTestSkip = PropertyUtility.getProperty("crowd.unit.test.skip");
    }

    /**
     * Creates the organization.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param organization the organization
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization createAffiliateOrganization(final TerminologyService service, final User user, final Organization organization)
        throws Exception {

        // if this is an affiliate org being created any logged in user can create it
        if (organization.isAffiliate()) {

            if (user.getUserName().equals(SecurityService.GUEST_USERNAME)) {

                final String message = "User does not have permission to perform this Organization action.";
                LOG.error(message);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
            }

        }

        // otherwise the user must have "all_all_admin" permission
        else {

            checkEditPermissions(user, null);

            final String message = "These types of organizations can not be created through this tool.";
            LOG.error(message);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }

        final SearchParameters organizationsParameters = new SearchParameters();
        List<Organization> organizationList = null;

        organizationsParameters.setQuery("name:" + organization.getName());
        organizationList = OrganizationService.searchOrganizations(service, user, organizationsParameters, false).getItems();

        if (organizationList.isEmpty()) {

            final String errorMessage = "There is already an organization with that name.";
            LOG.error(errorMessage);
            throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, errorMessage);
        }

        // User creating organization
        final User userToAdd = service.findSingle("id:" + user.getId(), User.class, null);

        // application.properties admin users
        final Set<User> systemAdminUsersToAdd = new HashSet<>();
        for (final String systemAdminUsername : SecurityService.getSystemAdminUserNames()) {

            systemAdminUsersToAdd.add(SecurityService.getUserFromUserName(service, systemAdminUsername));
        }

        // Create organization
        Organization newOrganization = new Organization();
        newOrganization.populateFrom(organization);
        newOrganization.setCrowdId(CrowdGroupNameAlgorithm.getCrowdIdFromOrganizationName(newOrganization.getName()));
        newOrganization.getMembers().add(userToAdd);

        newOrganization = service.add(newOrganization);
        service.add(AuditEntryHelper.addOrganizationEntry(newOrganization));

        // create admin team when creating an organization
        Team adminTeam = new Team();
        adminTeam.setDescription(TeamService.getOrganizationTeamDescription(organization));
        adminTeam.setName(TeamService.generateOrganizationTeamName(organization));
        adminTeam.setPrimaryContactEmail(organization.getPrimaryContactEmail());
        adminTeam.setOrganization(newOrganization);
        adminTeam.setType(TeamType.ORGANIZATION.getText());

        adminTeam = service.add(adminTeam);
        service.add(AuditEntryHelper.addTeamEntry(adminTeam));

        // set all the roles on the admin team
        for (final UserRole role : UserRole.getAllRoles()) {

            adminTeam = TeamService.addRoleToTeam(service, user, adminTeam.getId(), UserRole.getRoleString(role).toUpperCase());
        }

        // add the user to the admin team
        TeamService.addUserToTeam(service, user, adminTeam, user.getEmail());

        // load the user roles onto the organization
        setRoles(user, newOrganization, newOrganization.getRoles());

        // create the affiliated editions for the organization
        final List<Edition> editionList = EditionService.getAffiliateEditionList();

        for (final Edition edition : editionList) {

            edition.setOrganization(newOrganization);

            edition.setShortName(CrowdGroupNameAlgorithm.generateAffiliateShortName(edition.getShortName(), newOrganization.getId()));
            service.add(edition);
            service.add(AuditEntryHelper.addEditionEntry(edition));
        }

        // Only if all went successfully at this point should the crowd group be created (for the admin team)
        if (crowdUnitTestSkip == null || !"true".equalsIgnoreCase(crowdUnitTestSkip)) {

            final String groupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(organization.getCrowdId(), "all", "all", "admin");

            user.getRoles().add(groupName);

            SecurityService.setUserInSession(service.update(user));

        }

        return newOrganization;
    }
    
    /**
     * Returns the organization.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param id the id
     * @param includeMembers the include members
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization getOrganization(final TerminologyService service, final User user, final String id, final boolean includeMembers)
        throws Exception {

        final Organization organization = service.findSingle("id: " + id, Organization.class, null);

        if (organization == null) {

            final String message = "Unable to get the organization for id " + id + ".";
            LOG.error(message);
            throw new RestException(false, 404, "Not found", message);
        }

        final Organization updatedOrganization = handleMembers(organization, user, includeMembers);

        return updatedOrganization;
    }

    /**
     * Returns the organization.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param id the id
     * @param includeMembers the include members
     * @return the organization
     * @throws Exception the exception
     */
    private static Organization getActiveOrganization(final TerminologyService service, final User user, final String id, final boolean includeMembers)
        throws Exception {

        final Organization organization = service.findSingle("id: " + id + " AND active:true", Organization.class, null);

        if (organization == null) {

            final String message = "Unable to get the organization for id " + id + ".";
            LOG.error(message);
            throw new RestException(false, 404, "Not found", message);
        }

        final Organization updatedOrganization = handleMembers(organization, user, includeMembers);

        return updatedOrganization;
    }

    /**
     * Handle members.
     *
     * @param organization the organization
     * @param user the user
     * @param includeMembers the include members
     * @return the organization
     * @throws Exception the exception
     */
    private static Organization handleMembers(final Organization organization, final User user, final boolean includeMembers) throws Exception {

        if (includeMembers) {

            organization.getMembers();
        } else {

            if (organization.getMembers() != null && !organization.getMembers().isEmpty()) {

                organization.getMembers().clear();
            }

        }

        if (user != null) {

            setRoles(user, organization, organization.getRoles());
        }

        return organization;
    }

    /**
     * Update organization.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param organization the organization
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization updateOrganization(final TerminologyService service, final User user, final Organization organization) throws Exception {

        final Organization originalOrganization = getOrganization(service, user, organization.getId(), false);

        if (originalOrganization == null) {

            final String message = "Unable to find organization for id " + organization.getId() + " in order to updateOrganization.";
            LOG.error(message);
            throw new RestException(false, 404, "Not found", message);
        }

        checkEditPermissions(user, originalOrganization);

        originalOrganization.patchFrom(organization);

        service.update(originalOrganization);
        service.add(AuditEntryHelper.updateOrganizationEntry(originalOrganization));

        return originalOrganization;
    }

    /**
     * Migrate (inactivate or reactivate) organization including organization, projects, teams, and refsets.
     *
     * @param service the service
     * @param user the user
     * @param organizationId the organization id
     * @param organizationStatus the organization status
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization updateOrganizationStatus(final TerminologyService service, final User user, final String organizationId,
        final boolean organizationStatus) throws Exception {

        // Find the object
        final Organization organization = getOrganization(service, user, organizationId, false);

        if (organization == null) {

            final String message = "Unable to find organization for id " + organizationId + " in order to inactivateOrganization.";
            LOG.error(message);
            throw new RestException(false, 404, "Not found", message);
        }

        if (organization.isActive() == organizationStatus) {

            throw new Exception("Attempting to modify status of organization " + organization.getName() + " (" + organizationId + ") " + organizationStatus
                + " but it is already that, so an unexecpted state has arisen .");
        }

        checkEditPermissions(user, organization);

        final ResultList<Edition> editions = service.find("*", null, Edition.class, null);

        if (editions.getItems() != null && !editions.getItems().isEmpty()) {

            for (final Edition edition : editions.getItems()) {

                if (!organizationId.equals(edition.getOrganizationId())) {

                    continue;
                }

                final ResultList<Project> editionProjects = service.find("editionId:" + edition.getId(), null, Project.class, null);

                if (editionProjects.getItems() != null && !editionProjects.getItems().isEmpty()) {

                    for (final Project project : editionProjects.getItems()) {

                        project.setActive(organizationStatus);

                        if (project.getTeams() != null) {

                            for (final String teamId : project.getTeams()) {

                                final Team team = service.get(teamId, Team.class);

                                if (team != null && !team.getMembers().isEmpty()) {

                                    team.setActive(organizationStatus);
                                    service.update(team);
                                }

                            }

                        }

                        service.update(project);

                        final ResultList<Refset> projRefsets =
                            service.find("projectId:" + project.getId() + " AND active:" + organizationStatus, null, Refset.class, null);

                        if (projRefsets.getItems() != null && !projRefsets.getItems().isEmpty()) {

                            for (final Refset refset : projRefsets.getItems()) {

                                if (refset != null && !projRefsets.getItems().isEmpty()) {

                                    refset.setActive(organizationStatus);
                                    service.update(refset);
                                }

                            }

                        }

                    }

                }

                edition.setActive(organizationStatus);
                service.update(edition);
            }

        }

        final ResultList<Team> orgTeams = service.find("organizationId: + " + organizationId + " AND active:" + !organizationStatus, null, Team.class, null);

        if (orgTeams.getItems() != null && !orgTeams.getItems().isEmpty()) {

            for (final Team team : orgTeams.getItems()) {

                if (team != null && !team.getMembers().isEmpty()) {

                    team.setActive(organizationStatus);
                    service.update(team);
                }

            }

        }

        organization.setActive(organizationStatus);

        final Organization updatedOrganization = service.update(organization);
        AuditEntryHelper.changeOrganizationStatusEntry(updatedOrganization);

        return updatedOrganization;
    }

    /**
     * Search Organizations.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param searchParameters the search parameters
     * @param includeMembers the include members
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<Organization> searchOrganizations(final TerminologyService service, final User user, final SearchParameters searchParameters,
        final boolean includeMembers) throws Exception {

        final long start = System.currentTimeMillis();

        String query = getQueryForActiveOnly(searchParameters);

        if (SecurityService.GUEST_USERNAME.equals(user.getUserName())) {
            query += " AND affiliate:false ";
        }

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

            query = IndexUtility.addWildcardsToQuery(query, Organization.class);
        }

        final ResultList<Organization> results = service.find(query, pfs, Organization.class, null);

        final ResultList<Organization> resultsWithPermissions = new ResultList<>();

        for (final Organization organization : results.getItems()) {

            // if user is not a member of an affiliate they cannot view.
            if (organization.isAffiliate() && !organization.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()))) {
                continue;
            }

            setRoles(user, organization, organization.getRoles());

            if (includeMembers) {

                organization.getMembers();
            } else {

                if (organization.getMembers() != null && !organization.getMembers().isEmpty()) {

                    organization.getMembers().clear();
                }

            }

            if (canUserViewOrganization(user, organization)) {

                resultsWithPermissions.getItems().add(organization);
            }

        }

        resultsWithPermissions.setTimeTaken(System.currentTimeMillis() - start);
        resultsWithPermissions.setTotalKnown(true);
        resultsWithPermissions.setTotal(resultsWithPermissions.getItems().size());

        return resultsWithPermissions;
    }

    /**
     * Returns the organization users.
     *
     * @param service the Terminology Service
     * @param organization the organization
     * @param includeTeams the include teams
     * @return the organization users
     * @throws Exception the exception
     */
    public static ResultListUser getOrganizationUsers(final TerminologyService service, final Organization organization, final boolean includeTeams)
        throws Exception {

        final ResultListUser usersResultList = new ResultListUser();
        usersResultList.getItems().addAll(organization.getMembers());

        // remove system users if configured.
        final String systemUserList = PropertyUtility.getProperty("refset.service.system.accounts");

        if (StringUtils.isNotBlank(systemUserList)) {

            final Set<String> systemUserSet = new HashSet<>(Arrays.asList(systemUserList.split(",")));

            if (!usersResultList.getItems().isEmpty() && !systemUserSet.isEmpty()) {

                usersResultList.getItems().removeIf(u -> systemUserSet.contains(u.getUserName()));
            }

        }

        if (includeTeams && !usersResultList.getItems().isEmpty()) {

            for (final User user : usersResultList.getItems()) {

                final SearchParameters sp = new SearchParameters();
                sp.setQuery("members:" + user.getId());
                final ResultList<Team> teamsResultList = TeamService.searchTeams(user, sp);

                if (teamsResultList != null && teamsResultList.getItems() != null) {

                    user.getTeams().addAll(teamsResultList.getItems());
                }

            }

        }

        usersResultList.setTotal(usersResultList.getItems().size());

        return usersResultList;
    }

    /**
     * Returns the organization teams.
     *
     * @param service the Terminology Service
     * @param organizationId the organization id
     * @return the organization teams
     * @throws Exception the exception
     */
    public static ResultList<Team> getActiveOrganizationTeams(final TerminologyService service, final String organizationId) throws Exception {

        final PfsParameter pfs = new PfsParameter();
        final QueryParameter query = new QueryParameter();
        query.setQuery("organizationId:" + organizationId + " AND active:true");

        return service.find(query, pfs, Team.class, null);
    }

    /**
     * Returns the organization admin team.
     *
     * @param service the Terminology Service
     * @param organizationId the organization id
     * @return the organization admin team
     * @throws Exception the exception
     */
    public static Team getActiveOrganizationAdminTeam(final TerminologyService service, final String organizationId) throws Exception {

        final ResultList<Team> teams = getActiveOrganizationTeams(service, organizationId);

        for (final Team team : new ArrayList<Team>(teams.getItems())) {

            if (TeamService.isOrganizationTeam(team)) {

                return team;
            }

        }

        return null;
    }

    /**
     * Returns the organization admin team.
     *
     * @param service the Terminology Service
     * @param organizationId the organization id
     * @return the organization admin team
     * @throws Exception the exception
     */
    public static Team getOrganizationAdminTeam(final TerminologyService service, final String organizationId) throws Exception {

        final Organization organization = getOrganization(service, SecurityService.getUserFromSession(), organizationId, false);

        final ResultList<Team> teams = getOrganizationTeams(service, organization.getId());

        for (final Team team : new ArrayList<Team>(teams.getItems())) {

            if (TeamService.isOrganizationTeam(team)) {

                return team;
            }

        }

        return null;
    }

    /**
     * Returns the organization projects.
     *
     * @param service the Terminology Service
     * @param organizationId the organization id
     * @return the organization projects
     * @throws Exception the exception
     */
    public static ResultList<Project> getOrganizationProjects(final TerminologyService service, final String organizationId) throws Exception {

        final PfsParameter pfs = new PfsParameter();
        final QueryParameter query = new QueryParameter();
        query.setQuery("organizationId:" + organizationId + " AND active:true");

        return service.find(query, pfs, Project.class, null);
    }

    /**
     * Adds the user to organization.
     *
     * @param service the Terminology Service
     * @param authUser the auth user
     * @param organizationId the organization id
     * @param email the email
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization addUserToOrganization(final TerminologyService service, final User authUser, final String organizationId, final String email)
        throws Exception {

        User userToAdd = service.findSingle("email:" + email, User.class, null);

        if (userToAdd == null) {

            // find user in crowd
            final User user = CrowdAPIClient.findUserByEmail(email);

            if (user == null) {

                LOG.error("Unable to find user via Crowd for email " + email + " in order to addUserToOrganization.");
                throw new RestException(false, 404, "Not found",
                    "User not found in IMS. Please make sure you entered their email correctly. If the email address entered is correct,"
                        + " the user being added has never been added to IMS before. Instead of \"Add User\", click the \"Invite to Join\"");
            }

            service.add(user);
            service.update(user);

            userToAdd = service.findSingle("email:" + email, User.class, null);

            if (userToAdd == null) {

                final String message = "Unable to find user via RT2 database for email " + email + " in order to addUserToOrganization via email address.";
                LOG.error(message);
                throw new RestException(false, 404, "Not found", message);
            }

        }

        // must return members in order to add another member.
        final Organization organization = OrganizationService.getOrganization(service, authUser, organizationId, true);

        if (organization == null) {

            final String message = "Unable to find organization for " + organizationId + " in order to addUserToOrganization via email address.";
            LOG.error(message);
            throw new RestException(false, 404, "Not found", message);
        } else if (!organization.isActive()) {

            final String message = "Unable to add users to inactive organization for " + organizationId;
            LOG.error(message);
            throw new RestException(false, 404, "Not found", message);

        }

        checkEditPermissions(authUser, organization);

        organization.getMembers().add(userToAdd);
        service.add(AuditEntryHelper.addUserToOrganizationEntry(organization, userToAdd));

        return service.update(organization);
    }

    /**
     * Adds the user to organization.
     *
     * @param service the Terminology Service
     * @param authUser the auth user
     * @param organizationId the organization id
     * @param userToAdd the user to add
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization addUserToOrganization(final TerminologyService service, final User authUser, final String organizationId, final User userToAdd)
        throws Exception {

        // must return members in order to add another member.
        final Organization organization = OrganizationService.getOrganization(service, authUser, organizationId, true);

        if (organization == null) {

            final String message = "Unable to find organization for " + organizationId + "." + " in order to addUserToOrganization.";
            LOG.error(message);
            throw new RestException(false, 404, "Not found", message);
        } else if (!organization.isActive()) {

            final String message = "Unable to add users to inactive organization for " + organizationId;
            LOG.error(message);
            throw new RestException(false, 404, "Not found", message);
        }

        if (!organization.getMembers().contains(userToAdd)) {

            checkEditPermissions(authUser, organization);

            organization.getMembers().add(userToAdd);
            AuditEntryHelper.addUserToOrganizationEntry(organization, userToAdd);

            return service.update(organization);

        }

        return organization;
    }

    /**
     * Removes the user from organization.
     *
     * @param service the Terminology Service
     * @param authUser the auth user
     * @param userId the user id
     * @param organizationId the organization id
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization removeUserFromOrganization(final TerminologyService service, final User authUser, final String userId,
        final String organizationId) throws Exception {

        // Find the user
        final User userToRemove = service.get(userId, User.class);

        if (userToRemove == null) {

            final String message = "Unable to find user in RT2 database for id " + userId + " in order to removeUserFromOrganization.";
            LOG.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        final Organization organization = service.get(organizationId, Organization.class);

        if (organization == null) {

            final String message = "Unable to find organization in RT2 database for id " + organizationId + " in order to removeUserFromOrganization.";
            LOG.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        // "The user being removed has at least one reference set "In Edit" or "In Review" assigned to them.
        // As the admin, you are able to un-assign the reference set(s) first before inactivating user.
        final ResultList<Project> projectsForOrganization = getOrganizationProjects(service, organizationId);

        if (projectsForOrganization != null && projectsForOrganization.getItems() != null && !projectsForOrganization.getItems().isEmpty()) {

            final SearchParameters sp = new SearchParameters();
            final String projectIds =
                "(" + projectsForOrganization.getItems().stream().map(Project::getId).collect(Collectors.joining(" OR ", "projectId: ", "")) + ")";
            sp.setQuery("assignedUser: " + userToRemove.getUserName()
                + " AND versionStatus:IN DEVELOPMENT AND (workflowStatus: IN_EDIT OR workflowStatus: IN_REVIEW) AND " + projectIds);
            final ResultList<Refset> refsets = service.find(sp.getQuery(), null, Refset.class, null);

            if (!refsets.getItems().isEmpty()) {

                final String message = "User " + userToRemove.getName()
                    + " has a reference set \"In Edit\" or \"In Review\" assigned to them. As the admin, you are able to un-assign the reference set(s)"
                    + " first before inactivating user.";
                LOG.error(message);
                throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, message);
            }

        }

        checkEditPermissions(authUser, organization);

        userToRemove.getRoles().removeIf(u -> u.startsWith(organization.getCrowdId() + "-"));

        service.update(userToRemove);
        organization.getMembers().removeIf(orgUser -> orgUser.getId().equals(userToRemove.getId()));
        service.update(organization);
        service.add(AuditEntryHelper.removeUserFromOrganizationEntry(organization, userToRemove));

        removeUserFromTeams(service, organization, userToRemove, authUser);

        final String crowdGroupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(organization.getCrowdId(), "all", "all", User.ROLE_VIEWER);
        if (CrowdAPIClient.isRt2GroupName(crowdGroupName)) {
            CrowdAPIClient.deleteMembership(crowdGroupName, userToRemove.getUserName().replace(" ", "%20"));
        }

        return organization;
    }

    /**
     * Update organization icon.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param organizationId the organization id
     * @param iconUrlPrefix the icon url prefix
     * @param fileName the file name
     * @throws Exception the exception
     */
    public static void updateOrganizationIcon(final TerminologyService service, final User user, final String organizationId, final String iconUrlPrefix,
        final String fileName) throws Exception {

        // find user record, return 404 if not found
        final Organization organization = service.get(organizationId, Organization.class);

        if (organization == null) {

            final String message = "Unable to find organization for id " + organizationId + " to update the icon.";
            LOG.error(message);
            throw new RestException(false, 404, "Not found", message);
        }

        checkEditPermissions(user, organization);

        organization.setIconUri(iconUrlPrefix + fileName);
        service.add(AuditEntryHelper.updateIconForOrganizationEntry(organization, fileName));
        service.update(organization);
    }

    /**
     * set the list of roles a user has for a organization.
     *
     * @param user the user
     * @param organization the organization
     * @param roles the role list to populate
     * @return the list of roles for the organization
     * @throws Exception the exception
     */
    public static List<String> setRoles(final User user, final Organization organization, final List<String> roles) throws Exception {

        boolean giveViewerRole = false;

        if (user.checkPermission(User.ROLE_AUTHOR, organization.getName(), null, null)) {

            roles.add(User.ROLE_AUTHOR);
            giveViewerRole = true;
        }

        if (user.checkPermission(User.ROLE_REVIEWER, organization.getName(), null, null)) {

            roles.add(User.ROLE_REVIEWER);
            giveViewerRole = true;
        }

        if (user.checkPermission(User.ROLE_ADMIN, organization.getName(), null, null)) {

            roles.add(User.ROLE_ADMIN);
            giveViewerRole = true;
        }

        if (user.checkPermission(User.ROLE_VIEWER, organization.getName(), null, null) || giveViewerRole) {

            roles.add(User.ROLE_VIEWER);
        }

        return roles;
    }

    /**
     * Throw a exception if a user can't edit an organization.
     *
     * @param user the user
     * @param organization the organization
     * @throws Exception the exception
     */
    public static void checkEditPermissions(final User user, final Organization organization) throws Exception {

        boolean canUserEdit = false;

        if (organization == null) {

            canUserEdit = canUserCreateOrganizations(user);
        } else {

            canUserEdit = canUserEditOrganization(user, organization);
        }

        if (!canUserEdit) {

            final String message = "User " + user.getId() + " does not have permission to perform this Organization action on "
                + (organization == null ? "null" : organization.getId());
            throw new RestException(false, 403, "Forbidden", message);
        }

    }

    /**
     * Check if a user can create an organization (must have "all_all_admin").
     *
     * @param user the user
     * @return can the user create an organization
     * @throws Exception the exception
     */
    public static boolean canUserCreateOrganizations(final User user) throws Exception {

        return user.checkPermission(User.ROLE_ADMIN, "all", "all", null);
    }

    /**
     * Check if a user can edit an organization.
     *
     * @param user the user
     * @param organization the organization
     * @return can the user edit the organization
     * @throws Exception the exception
     */
    public static boolean canUserEditOrganization(final User user, final Organization organization) throws Exception {

        if (organization.getRoles().isEmpty()) {

            setRoles(user, organization, organization.getRoles());
        }

        return organization.getRoles().contains(User.ROLE_ADMIN);
    }

    /**
     * Check if a user can view an organization.
     *
     * @param user the user
     * @param organization the organization
     * @return can the user view the organization
     * @throws Exception the exception
     */
    public static boolean canUserViewOrganization(final User user, final Organization organization) throws Exception {

        if (organization.getRoles().isEmpty()) {

            setRoles(user, organization, organization.getRoles());
        }

        return organization.getRoles().contains(User.ROLE_VIEWER);
    }

    /**
     * Invite user to organization.
     *
     * @param authUser the auth user
     * @param organizationId the organization id
     * @param recipientEmail the recipient email
     * @param additionalMessage the additional message
     * @throws Exception the exception
     */
    public static void inviteUserToOrganization(final User authUser, final String organizationId, final String recipientEmail, final String additionalMessage)
        throws Exception {

        if (StringUtils.isBlank(recipientEmail)) {

            throw new Exception("Recipient must have an email address to invite to Refset.");
        }

        // TODO: move this URL to properties.
        final String accountSetupUrl = "https://confluence.ihtsdotools.org/display/ILS/Confluence+User+Accounts";

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedFlag(true);
            service.setModifiedBy(SecurityService.getUserFromSession().getUserName());

            final Organization organization = getActiveOrganization(service, authUser, organizationId, true);

            final User crowdUser = CrowdAPIClient.findUserByEmail(recipientEmail.trim());
            final boolean isCrowdMember = (crowdUser != null);

            if (isCrowdMember) {

                // Ensure not already members of the organization
                if (organization.getMembers().stream().anyMatch(u -> u.getId().equals(crowdUser.getId()))) {

                    throw new Exception("User: " + crowdUser.getUserName() + " is already a member of organization: " + organization.getName());
                }

            }

            // add in invite request
            final InviteRequest request = new InviteRequest();
            request.setAction(INVITE_ACTION);
            request.setActive(true);
            request.setRequester(authUser.getId());
            request.setRecipientEmail(recipientEmail);
            request.setPayload("organization:" + organizationId);

            service.setModifiedBy(SecurityService.getUserFromSession().getUserName());
            service.add(request);

            final String acceptUrl = appUrlRoot + "/invite/response?ir=" + request.getId() + "&r=true";
            final String declineUrl = appUrlRoot + "/invite/response?ir=" + request.getId() + "&r=false";

            final String ahrefStyle = "'padding: 8px 12px; border: 1px solid #c3e7fe;border-radius: 2px;"
                + "font-family: Helvetica, Arial, sans-serif;font-size: 14px; color: #000000;"
                + "text-decoration: none;font-weight:bold;display: inline-block;'";
            final String button = "<table style='width: 100%; padding-right: 50px; padding-left: 50px'><tr><td>"
                + "  <table style='padding: 0'><tr><td style='border-radius: 2px; background-color: #c3e7fe'>"
                + "    <a href='{{BUTTION_LINK}}' target='_blank' style=" + ahrefStyle + ">" + "      {{BUTTON_TEXT}}"
                + "</a></td></tr></table></td></tr></table>";

            final StringBuffer emailBody = new StringBuffer();
            emailBody.append("<html>");
            emailBody.append("<body style='font-family: Segoe UI, Tahoma, Geneva, Verdana, sans-serif;'>");
            emailBody.append("<div>");

            emailBody.append("    <span>Hello ").append((isCrowdMember) ? crowdUser.getName() : "").append(",</span><br/><br/>");

            // Main invite
            emailBody.append("    <span>").append(authUser.getName()).append(" would like to invite you to work with the Organization '")
                .append(organization.getName()).append("' in order to participate in the reference set modeling project with the RT2 tool.</span><br/><br/>");
            emailBody.append("    <span>To accept this invitation, and alert ").append(authUser.getName())
                .append(" of your acceptance, please click the button below.</span><br/><br/>");

            // Additional Information
            if (!StringUtils.isBlank(additionalMessage)) {

                emailBody.append("In addition, they have included the additional message:").append("<br/><br/>");
                emailBody.append(additionalMessage).append("<br/><br/>");
            }

            // accept
            emailBody.append("    <span style='width: 300px; display: inline-block'>")
                .append(button.replace("{{BUTTION_LINK}}", acceptUrl).replace("{{BUTTON_TEXT}}", "Accept Invitation")).append("</span>");

            // decline
            emailBody.append("    <span style='width: 300px; display: inline-block'>")
                .append(button.replace("{{BUTTION_LINK}}", declineUrl).replace("{{BUTTON_TEXT}}", "Decline Invitation")).append("</span>");

            if (!isCrowdMember) {

                emailBody.append("    <span><a href='").append(accountSetupUrl).append("' target='_blank'></a></span><br/><br/>");
            }

            emailBody.append("    <br/><br/>");
            // Warning
            emailBody.append(
                "    <span>If you do not wish to accept the invitation, or this email was received in error, you can safely ignore it.</span><br/><br/>");

            // Signature
            emailBody.append("    <span>Thank you,</span><br/>");
            emailBody.append("    <span>The SNOMED CT Reference Set Tool Team</span>");
            emailBody.append("</div>");
            emailBody.append("</body>");
            emailBody.append("</html>");

            final String action = INVITE_ACTION;
            final Set<String> recipients = new HashSet<>(Arrays.asList(recipientEmail.trim()));
            EmailUtility.sendEmail(EMAIL_SUBJECT + action, recipients, emailBody.toString());

            LOG.info("INVITE request - from {} to {} for organization {}", authUser.getEmail(), recipients, organizationId);

            service.add(AuditEntryHelper.sendOrganizationInvite(organization, authUser, recipientEmail.trim()));
        }

    }

    /**
     * Process organization invitation.
     *
     * @param service the service
     * @param inviteRequest the invite request
     * @param acceptance the acceptance
     * @throws Exception the exception
     */
    public static void processOrganizationInvitation(final TerminologyService service, final InviteRequest inviteRequest, final boolean acceptance)
        throws Exception {

        final User memberUser = CrowdAPIClient.findUserByEmail(inviteRequest.getRecipientEmail());
        final User requesterUser = UserService.getUser(service, inviteRequest.getRequester(), false);

        if (requesterUser == null) {
            LOG.error("Requester not found: {}", inviteRequest.getRequester());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This user does not have permission to perform this action");
        }

        final Set<String> requesterUserPermissions = CrowdAPIClient.getMembershipsForUser(requesterUser.getUserName());
        final Set<String> updatedPermissions = requesterUserPermissions.stream().map(p -> p.replace(APP_PREFIX, "")).collect(Collectors.toSet());
        requesterUser.getRoles().addAll(updatedPermissions);

        service.setModifiedBy(requesterUser.getUserName());
        inviteRequest.setResponse(String.valueOf(acceptance));
        inviteRequest.setResponseDate(new Date());

        service.update(inviteRequest);

        LOG.info("Requester is: {}", requesterUser);

        // get organization from payload
        final Map<String, String> nameValuePairs = new HashMap<>();
        final String[] pairs = inviteRequest.getPayload().split("&");

        for (final String pair : pairs) {

            final String[] keyValue = pair.split(":");
            nameValuePairs.put(keyValue[0], keyValue[1]);
        }

        final String organizationId = nameValuePairs.get("organization");
        final Organization organization = getActiveOrganization(service, requesterUser, organizationId, true);

        final StringBuffer emailBody = new StringBuffer();

        final String ahrefStyle = "'padding: 8px 12px; border: 1px solid #c3e7fe;border-radius: 2px;"
            + "font-family: Helvetica, Arial, sans-serif;font-size: 14px; color: #000000;" + "text-decoration: none;font-weight:bold;display: inline-block;'";
        final String button = "<table style='width: 100%; padding-right: 50px; padding-left: 50px'><tr><td>"
            + "  <table style='padding: 0'><tr><td style='border-radius: 2px; background-color: #c3e7fe'>"
            + "    <a href='{{BUTTION_LINK}}' target='_blank' style=" + ahrefStyle + ">" + "      {{BUTTON_TEXT}}" + "</a></td></tr></table></td></tr></table>";

        // if rejected, send notification to requester
        if (!acceptance) {

            emailBody.append("<html>");
            emailBody.append("<body style='font-family: Segoe UI, Tahoma, Geneva, Verdana, sans-serif;'>");
            emailBody.append("<div>");

            emailBody.append("    <span>Hello, ").append(requesterUser.getName()).append("</span><br/><br/>");

            // Main invite
            emailBody.append("    <span>").append((memberUser != null) ? memberUser.getName() : inviteRequest.getRecipientEmail())
                .append(" has declined your invitation to join ").append(organization.getName()).append(" as a collaborator.</span><br/><br/>");

            // Go to app
            emailBody.append("    <span style='width: 400px; display: inline-block'>")
                .append(button.replace("{{BUTTION_LINK}}", appUrlRoot).replace("{{BUTTON_TEXT}}", "Go to the Reference Set Tool")).append("</span>");

            emailBody.append("</div>");
            emailBody.append("</body>");
            emailBody.append("</html>");

            final String action = INVITE_DECLINED;

            final Set<String> recipients = new HashSet<>(Arrays.asList(requesterUser.getEmail()));
            LOG.info("ORGANIZATION INVITE declined - from {} to {}", requesterUser.getEmail(), recipients);
            EmailUtility.sendEmail(EMAIL_SUBJECT + action, recipients, emailBody.toString());

        }

        if (memberUser == null) {
            LOG.error("Member not found: {}", inviteRequest.getRecipientEmail());
            throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, "This user " + inviteRequest.getRecipientEmail() + " does not exist.");
        }

        // if accepted, add user to org, admin has to add to team and project since we can't determine here which of the project's team to add the user.
        if (acceptance) {

            // add user to org as a viewer, will not error if already a member.
            OrganizationService.addUserToOrganization(service, requesterUser, organization.getId(), memberUser.getEmail());

            emailBody.append("<html>");
            emailBody.append("<body style='font-family: Segoe UI, Tahoma, Geneva, Verdana, sans-serif;'>");
            emailBody.append("<div>");

            emailBody.append("    <span>Hello, ").append(requesterUser.getName()).append("</span><br/><br/>");

            // Main invite
            emailBody.append("    <span>").append(memberUser.getName()).append(" has accepted your invitation to join ").append(organization.getName())
                .append(" as a collaborator.</span><br/><br/>");
            emailBody.append("    <span>").append(memberUser.getName()).append(" has been added to ").append(organization.getName())
                .append(" as a <b>Viewer</b>.</span><br/><br/>");

            // Warning
            emailBody.append("    <span>Additional permissions can be configured through the SNOMED CT Reference Set Tool</span><br/><br/>");

            // Go to app
            emailBody.append("    <span style='width: 400px; display: inline-block'>")
                .append(button.replace("{{BUTTION_LINK}}", appUrlRoot).replace("{{BUTTON_TEXT}}", "Go to the Reference Set Tool")).append("</span>");

            emailBody.append("</div>");
            emailBody.append("</body>");
            emailBody.append("</html>");

            final String action = INVITE_ACCEPTED;

            final Set<String> recipients = new HashSet<>(Arrays.asList(requesterUser.getEmail()));
            LOG.info("ORGANIZATION INVITE accepted - from {} to {}", requesterUser.getEmail(), recipients);
            EmailUtility.sendEmail(EMAIL_SUBJECT + action, recipients, emailBody.toString());

            final String groupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(organization.getCrowdId(), "all", "all", User.ROLE_VIEWER);
            CrowdAPIClient.addMembership(groupName, memberUser.getUserName());

        }

        service.add(AuditEntryHelper.responseForOrganizationInvite(organization, requesterUser, inviteRequest.getRecipientEmail(), acceptance));

    }

    /**
     * Removes the user from crowd group belonging to the organization.
     *
     * @param service the service
     * @param organization the organization
     * @param userToRemove the user to remove
     * @param authUser the auth user
     */
    private static void removeUserFromTeams(final TerminologyService service, final Organization organization, final User userToRemove, final User authUser) {

        if (crowdUnitTestSkip == null || !"true".equalsIgnoreCase(crowdUnitTestSkip)) {

            LOG.info("CALLING CROWD API from ProjectService updateMemberships");

            try {

                // teams associated with projects for removal from crowd too.
                final ResultList<Project> projects = getOrganizationProjects(service, organization.getId());

                if (projects != null && projects.getItems() != null) {

                    for (final Project project : projects.getItems()) {

                        for (final String teamId : project.getTeams()) {

                            final Team team = TeamService.getTeam(authUser, teamId, true);

                            if (team.getMembers() != null && team.getMembers().contains(userToRemove.getId())) {

                                TeamService.removeUserFromTeam(service, authUser, team, userToRemove.getId());
                            }

                        }

                    }

                }

                // teams not associated with project that are would not be in crowd.
                final ResultList<Team> orgTeams = OrganizationService.getOrganizationTeams(service, organization.getId());

                if (orgTeams != null && orgTeams.getItems() != null) {

                    for (final Team team : orgTeams.getItems()) {

                        if (team.getMembers() != null && team.getMembers().contains(userToRemove.getId())) {

                            TeamService.removeUserFromTeam(service, authUser, team, userToRemove.getId());
                        }

                    }

                }

            } catch (final Exception e) {

                LOG.error("ERROR removing user {} from CROWD groups.", userToRemove.getUserName(), e);
            }

        }

    }

    /**
     * Returns the organization editions.
     *
     * @param service the service
     * @param organizationId the organization id
     * @return the organization editions
     * @throws Exception the exception
     */
    public static ResultList<Edition> getOrganizationEditions(final TerminologyService service, final String organizationId) throws Exception {

        final PfsParameter pfs = new PfsParameter();
        final QueryParameter query = new QueryParameter();
        query.setQuery("organizationId:" + organizationId);

        return service.find(query, pfs, Edition.class, null);
    }

    /**
     * Returns the organization teams.
     *
     * @param service the service
     * @param id the id
     * @return the organization teams
     * @throws Exception the exception
     */
    public static ResultList<Team> getOrganizationTeams(final TerminologyService service, final String id) throws Exception {

        final PfsParameter pfs = new PfsParameter();
        final QueryParameter query = new QueryParameter();
        query.setQuery("organizationId:" + id);

        return service.find(query, pfs, Team.class, null);
    }

}
