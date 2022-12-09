/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.ResultListUser;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
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

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(OrganizationService.class);

    /** The config properties. */
    private static final Properties PROPERTIES = PropertyUtility.getProperties();

    /** The Constant EMAIL_SUBJECT. */
    private static final String EMAIL_SUBJECT = "SNOMED International Refset Tool - ";
    
    /** The Constant INVITE_ACTION. */
    private static final String INVITE_ACTION = "Invite";
    
    /** The Constant INVITE_ACCEPTED. */
    private static final String INVITE_ACCEPTED = "Invite accepted";

    /** The Constant INVITE_DECLINED. */
    private static final String INVITE_DECLINED = "Invite declined";

    /**
     * Creates the organization.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param organization the organization
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization createOrganization(final TerminologyService service, final User user, final Organization organization) throws Exception {

        checkEditPermissions(user, null);

        SearchParameters organizationsParameters = new SearchParameters();

        List<Organization> organizationList = OrganizationService.searchOrganizations(service, user, organizationsParameters, false).getItems();

        if (organizationList.size() > 0) {

            final String errorMessage = "There is already an organization tied to that edition.";
            logger.error(errorMessage);
            throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, errorMessage);
        }

        organizationsParameters.setQuery("name:" + organization.getName());

        organizationList = OrganizationService.searchOrganizations(service, user, organizationsParameters, false).getItems();

        if (organizationList.size() > 0) {

            final String errorMessage = "There is already an organization with that name.";
            logger.error(errorMessage);
            throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, errorMessage);
        }

        final User userToAdd = service.findSingle("id:" + user.getId(), User.class, null);

        final Organization newOrganization = new Organization();
        newOrganization.populateFrom(organization);
        newOrganization.getMembers().add(userToAdd);

        service.add(newOrganization);
        service.add(AuditEntryHelper.newOrganizationEntry(newOrganization));

        // create admin team when creating an organization
        final Team adminTeam = new Team();
        adminTeam.setDescription("Application users which can administrator organization " + organization.getName());
        adminTeam.setName("Administrator(s) for organization " + organization.getName());
        adminTeam.setPrimaryContactEmail(organization.getPrimaryContactEmail());
        adminTeam.getMembers().add(user.getId());
        adminTeam.setOrganization(newOrganization);
        adminTeam.getRoles().add(User.ROLE_ADMIN);

        TeamService.createTeam(user, adminTeam);

        setRoles(user, newOrganization, newOrganization.getRoles());

        if (PROPERTIES.getProperty("crowd.unit.test.skip") == null || !"true".equalsIgnoreCase(PROPERTIES.getProperty("crowd.unit.test.skip"))) {
            logger.info("CALLING CROWD API from OrganizationService createOrganization");

            try {
                // TODO: Tim Whalen for Permissions
                // final String crowdGroupName = CrowdAPIClient.addAdminGroup(newOrganization.getEdition().getShortName(), "Organization Administrator(s)");
                // CrowdAPIClient.addMembership(crowdGroupName, user.getUserName());

            } catch (Exception e) {

                final String errorMessage = "Failed adding Crowd groups. Message: " + e.getMessage();
                logger.error(errorMessage, e);
                throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, errorMessage);
            }

        } else {
            logger.info("SKIP CALLING CROWD API");
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
    public static Organization getOrganization(final TerminologyService service, final User user, final String id, final boolean includeMembers) throws Exception {

        final Organization organization = service.findSingle("id: " + id + " AND active:true", Organization.class, null);

        if (organization == null) {

            final String message = "Unable to find organization for id " + id + ".";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

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

            final String message = "Unable to find organization for id " + organization.getId() + ".";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        checkEditPermissions(user, originalOrganization);

        originalOrganization.patchFrom(organization);

        service.update(originalOrganization);
        service.add(AuditEntryHelper.updateOrganizationEntry(originalOrganization));

        return originalOrganization;
    }

    /**
     * Inactivate organization.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param organizationId the organization id
     * @return the list
     * @throws Exception the exception
     */
    public static void inactivateOrganization(final TerminologyService service, final User user, final String organizationId) throws Exception {

        // Find the object
        final Organization organization = getOrganization(service, user, organizationId, false);

        if (organization == null) {

            final String message = "Unable to find organization for id " + organizationId + ".";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        checkEditPermissions(user, organization);

        // inactivate projects, clear teams, and inactivate refsets
        final ResultList<Project> orgProjects = service.find("organization.id:" + organizationId + " AND active:true", null, Project.class, null);

        if (orgProjects.getItems() != null && !orgProjects.getItems().isEmpty()) {
            for (Project project : orgProjects.getItems()) {
                project.setActive(false);
                if (project.getTeams() != null) {
                    for (String teamId : project.getTeams()) {
                        final Team team = service.get(teamId, Team.class);
                        if (team != null && !team.getMembers().isEmpty()) {
                            team.getMembers().clear();
                            service.update(team);
                        }
                    }
                }
                service.update(project);

                final ResultList<Refset> projRefsets = service.find("projectId:" + project.getId() + " AND active:true", null, Refset.class, null);
                if (projRefsets.getItems() != null && !projRefsets.getItems().isEmpty()) {
                    for (Refset refset : projRefsets.getItems()) {
                        if (refset != null && !projRefsets.getItems().isEmpty()) {
                            refset.setActive(false);
                            service.update(refset);
                        }
                    }
                }
            }
        }

        organization.setActive(false);
        service.update(organization);
        service.add(AuditEntryHelper.inactivateOrganizationEntry(organization));

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
    public static ResultList<Organization> searchOrganizations(final TerminologyService service, final User user, final SearchParameters searchParameters, final boolean includeMembers)
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
            query = IndexUtility.addWildcardsToQuery(query, Organization.class);
        }

        final ResultList<Organization> results = service.find(query, pfs, Organization.class, null);

        final ResultList<Organization> resultsWithPermissions = new ResultList<>();

        for (Organization organization : results.getItems()) {

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
     * @param organizationId the organization id
     * @param includeTeams the include teams
     * @return the organization users
     * @throws Exception the exception
     */
    public static ResultListUser getOrganizationUsers(final TerminologyService service, final String organizationId, final boolean includeTeams) throws Exception {

        final Organization organization = service.findSingle("id: " + organizationId + " AND active:true", Organization.class, null);

        if (organization == null) {

            final String message = "Unable to find organization for id " + organizationId + ".";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        final ResultListUser usersResultList = new ResultListUser();
        usersResultList.getItems().addAll(organization.getMembers());

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
    public static ResultList<Team> getOrganizationTeams(final TerminologyService service, final String organizationId) throws Exception {

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
    public static Team getOrganizationAdminTeam(final TerminologyService service, final String organizationId) throws Exception {

        final ResultList<Team> teams = getOrganizationTeams(service, organizationId);
        
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
     * @param user the user
     * @param organizationId the organization id
     * @param email the email
     * @throws Exception the exception
     */
    public static void addUserToOrganization(final TerminologyService service, final User authUser, final String organizationId, final String email) throws Exception {

        final User userToAdd = service.findSingle("email:" + email, User.class, null);

        if (userToAdd == null) {

            final String message = "Unable to find user for email " + email + ".";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        // must return members in order to add another member.
        final Organization organization = OrganizationService.getOrganization(service, authUser, organizationId, true);

        if (organization == null) {

            final String message = "Unable to find organization for " + organizationId + ".";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        checkEditPermissions(authUser, organization);

        organization.getMembers().add(userToAdd);
        service.add(AuditEntryHelper.addUserToOrganizationEntry(organization, userToAdd));

        service.update(organization);

        final Edition edition = EditionService.getEditionForOrganization(organizationId);
        final String crowdGroupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(edition.getShortName(), "all", User.ROLE_VIEWER);
        CrowdAPIClient.addGroup(edition.getShortName(), "all", "Organization user", false);
        CrowdAPIClient.addMembership(crowdGroupName, userToAdd.getUserName());
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
    public static Organization removeUserFromOrganization(final TerminologyService service, final User authUser, final String userId, final String organizationId) throws Exception {

        // Find the user
        final User userToRemove = service.get(userId, User.class);
        final Organization organization = service.get(organizationId, Organization.class);

        if (userToRemove == null) {

            final String message = "Unable to find user for id " + userId + ".";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        if (organization == null) {

            final String message = "Unable to find organization for id " + organizationId + ".";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        checkEditPermissions(authUser, organization);

        organization.getMembers().remove(userToRemove);
        service.update(organization);
        service.add(AuditEntryHelper.removeUserFromOrganizationEntry(organization, userToRemove));

        removeUserFromTeams(service, organizationId, userToRemove, authUser);

        final Edition edition = EditionService.getEditionForOrganization(organizationId);
        final String crowdGroupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(edition.getShortName(), "all", User.ROLE_VIEWER);
        CrowdAPIClient.deleteMembership(crowdGroupName, userToRemove.getUserName());

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
    public static void updateOrganizationIcon(final TerminologyService service, final User user, final String organizationId, final String iconUrlPrefix, final String fileName) throws Exception {

        // find user record, return 404 if not found
        final Organization organization = service.get(organizationId, Organization.class);

        if (organization == null) {

            final String message = "Unable to find organization for id " + organizationId + ".";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
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

        if (user.doesUserHavePermission(User.ROLE_AUTHOR, organization)) {

            roles.add(User.ROLE_AUTHOR);
            giveViewerRole = true;
        }

        if (user.doesUserHavePermission(User.ROLE_REVIEWER, organization)) {

            roles.add(User.ROLE_REVIEWER);
            giveViewerRole = true;
        }

        if (user.doesUserHavePermission(User.ROLE_ADMIN, organization)) {

            roles.add(User.ROLE_ADMIN);
            giveViewerRole = true;
        }

        if (user.doesUserHavePermission(User.ROLE_VIEWER, organization) || giveViewerRole) {

            roles.add(User.ROLE_VIEWER);
        }

        return roles;
    }

    /**
     * Throw an exception if a user can't edit an organization.
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

            final String message = "User does not have permission to perform this Organization action.";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
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

        final Organization organization = null;
        return user.doesUserHavePermission(User.ROLE_ADMIN, organization);
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
    public static void inviteUserToOrganization(final User authUser, final String organizationId, final String recipientEmail, final String additionalMessage) throws Exception {

        if (StringUtils.isBlank(recipientEmail)) {

            throw new Exception("Recipient must have an email address to invite to Refset.");
        }

        // TODO: move this URL to properties.
        final String accountSetupUrl = "https://confluence.ihtsdotools.org/display/ILS/Confluence+User+Accounts";

        try (final TerminologyService service = new TerminologyService()) {

            final Organization organization = getOrganization(service, authUser, organizationId, true);

            final User crowdUser = CrowdAPIClient.findUserByEmail(recipientEmail.trim());
            final boolean isCrowdMember = (crowdUser != null);

            // TODO: Determine needs of hasMembership based on approach implemented
            if (isCrowdMember) {
                final Set<String> memberships = CrowdAPIClient.getMembershipsForUser(crowdUser.getUserName());
                // final boolean hasMemberships = (memberships != null) ? memberships.stream().anyMatch(m -> m.startsWith("rt2-")) : false;

                // Ensure not already members of the organization
                if (organization.getMembers().stream().anyMatch(u -> u.getId().equals(crowdUser.getId()))) {
                    throw new Exception("User: " + crowdUser.getUserName() + " is already a member of organization: " + organization.getName());
                }
            }

            final String queryString = "requester=" + authUser.getId() + "&recipientEmail=" + URLEncoder.encode(recipientEmail, "UTF-8");

            final String acceptUrl = PROPERTIES.getProperty("app.url.root") + "/refsetservice/organization/" + organizationId + "/response?acceptance=true&" + queryString;
            final String declineUrl = PROPERTIES.getProperty("app.url.root") + "/refsetservice/organization/" + organizationId + "/response?acceptance=false&" + queryString;

            final String BUTTON = "<table style='width: 100%; padding-right: 50px; padding-left: 50px'><tr><td>"
                + "  <table style='padding: 0'><tr><td style='border-radius: 2px; background-color: #c3e7fe'>"
                + "    <a href='{{BUTTION_LINK}}' target='_blank' style='padding: 8px 12px; border: 1px solid #c3e7fe;border-radius: 2px;font-family: Helvetica, Arial, sans-serif;font-size: 14px; color: #000000;text-decoration: none;font-weight:bold;display: inline-block;'>"
                + "      {{BUTTON_TEXT}}" + "</a></td></tr></table></td></tr></table>";

            final StringBuffer emailBody = new StringBuffer();
            emailBody.append("<html>");
            emailBody.append("<body style='font-family: Segoe UI, Tahoma, Geneva, Verdana, sans-serif;'>");
            emailBody.append("<div>");

            emailBody.append("    <span>Hello ").append((isCrowdMember) ? crowdUser.getName() : "").append(",</span><br/><br/>");

            // Main invite
            emailBody.append("    <span>").append(authUser.getName()).append(" would like to invite you to work with the Organization '").append(organization.getName())
                .append("' in order to participate in the reference set modeling project with the RT2 tool.</span><br/><br/>");
            emailBody.append("    <span>To accept this invitation, and alert ").append(authUser.getName()).append(" of your acceptance, please click the button below.</span><br/><br/>");

            // Additional Information
            if (!StringUtils.isBlank(additionalMessage)) {

                emailBody.append("In addition, they have included the additional message:").append("<br/><br/>");
                emailBody.append(additionalMessage).append("<br/><br/>");
            }

            // accept
            emailBody.append("    <span style='width: 300px; display: inline-block'>").append(BUTTON.replace("{{BUTTION_LINK}}", acceptUrl).replace("{{BUTTON_TEXT}}", "Accept Invitation"))
                .append("</span>");

            // decline
            emailBody.append("    <span style='width: 300px; display: inline-block'>").append(BUTTON.replace("{{BUTTION_LINK}}", declineUrl).replace("{{BUTTON_TEXT}}", "Decline Invitation"))
                .append("</span>");

            if (!isCrowdMember) {

                emailBody.append("    <span><a href='").append(accountSetupUrl).append("' target='_blank'></a></span><br/><br/>");
            }

            emailBody.append("    <br/><br/>");
            // Warning
            emailBody.append("    <span>If you do not wish to accept the invitation, or this email was received in error, you can safely ignore it.</span><br/><br/>");

            // Signature
            emailBody.append("    <span>Thank you,</span><br/>");
            emailBody.append("    <span>The SNOMED CT Reference Set Tool Team</span>");
            emailBody.append("</div>");
            emailBody.append("</body>");
            emailBody.append("</html>");

            final String action = INVITE_ACTION;
            final Set<String> recipients = new HashSet<>(Arrays.asList(recipientEmail.trim()));
            EmailUtility.sendEmail(EMAIL_SUBJECT + action, authUser.getEmail(), recipients, emailBody.toString());

            logger.info("INVITE request - from {} to {} for organization {}", authUser.getEmail(), recipients, organizationId);

            AuditEntryHelper.sendOrganizationInvite(organization, authUser, recipientEmail.trim());

        }

    }

    /**
     * Process organization invitation.
     *
     * @param organizationId the organization id
     * @param acceptance the acceptance
     * @param requesterId the requester id
     * @param recipientEmail the recipient email
     * @throws Exception the exception
     */
    public static void processOrganizationInvitation(final String organizationId, final boolean acceptance, final String requesterId, final String recipientEmail) throws Exception {

        final User memberUser = CrowdAPIClient.findUserByEmail(recipientEmail.trim());
        final boolean isMember = (memberUser != null);
        final StringBuffer emailBody = new StringBuffer();

        final String BUTTON = "<table style='width: 100%; padding-right: 50px; padding-left: 50px'><tr><td>"
            + "  <table style='padding: 0'><tr><td style='border-radius: 2px; background-color: #c3e7fe'>"
            + "    <a href='{{BUTTION_LINK}}' target='_blank' style='padding: 8px 12px; border: 1px solid #c3e7fe;border-radius: 2px;font-family: Helvetica, Arial, sans-serif;font-size: 14px; color: #000000;text-decoration: none;font-weight:bold;display: inline-block;'>"
            + "      {{BUTTON_TEXT}}" + "</a></td></tr></table></td></tr></table>";

        try (final TerminologyService service = new TerminologyService()) {

            final User requesterUser = UserService.getUser(requesterId, false);
            if (requesterUser == null) {
                logger.error("Requester not found: {}", requesterId);
            }
            service.setModifiedBy(requesterUser.getUserName());

            logger.info("Requester is: {}", requesterUser);
            final Organization organization = getOrganization(service, requesterUser, organizationId, true);

            // if rejected, send notification to requester
            if (!acceptance) {

                emailBody.append("<html>");
                emailBody.append("<body style='font-family: Segoe UI, Tahoma, Geneva, Verdana, sans-serif;'>");
                emailBody.append("<div>");

                emailBody.append("    <span>Hello, ").append(requesterUser.getName()).append("</span><br/><br/>");

                // Main invite
                emailBody.append("    <span>").append(isMember ? memberUser.getName() : recipientEmail).append(" has declined your invitation to join ").append(organization.getName())
                    .append(" as a collaborator.</span><br/><br/>");

                // Go to app
                emailBody.append("    <span style='width: 400px; display: inline-block'>")
                    .append(BUTTON.replace("{{BUTTION_LINK}}", PROPERTIES.getProperty("app.url.root")).replace("{{BUTTON_TEXT}}", "Go to the Reference Set Tool")).append("</span>");

                emailBody.append("</div>");
                emailBody.append("</body>");
                emailBody.append("</html>");

                final String action = INVITE_DECLINED;

                // TODO: what should the from email be?
                final Set<String> recipients = new HashSet<>(Arrays.asList(requesterUser.getEmail()));
                logger.info("REFSET INVITE declined - from {} to {}", requesterUser.getEmail(), recipients);
                EmailUtility.sendEmail(EMAIL_SUBJECT + action, requesterUser.getEmail(), recipients, emailBody.toString());

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
                emailBody.append("    <span>").append(memberUser.getName()).append("has been added to ").append(organization.getName()).append(" as a <b>Viewer</b>.</span><br/><br/>");

                // Warning
                emailBody.append("    <span>Additional permissions can be configured through the SNOMED CT Reference Set Tool</span><br/><br/>");

                // Go to app
                emailBody.append("    <span style='width: 400px; display: inline-block'>")
                    .append(BUTTON.replace("{{BUTTION_LINK}}", PROPERTIES.getProperty("app.url.root")).replace("{{BUTTON_TEXT}}", "Go to the Reference Set Tool")).append("</span>");

                emailBody.append("</div>");
                emailBody.append("</body>");
                emailBody.append("</html>");

                final String action = INVITE_ACCEPTED;

                // TODO: what should the from email be?
                final Set<String> recipients = new HashSet<>(Arrays.asList(requesterUser.getEmail()));
                logger.info("REFSET INVITE accepted - from {} to {}", requesterUser.getEmail(), recipients);
                EmailUtility.sendEmail(EMAIL_SUBJECT + action, requesterUser.getEmail(), recipients, emailBody.toString());

            }

            AuditEntryHelper.responseForOrganizationInvite(organization, requesterUser, recipientEmail.trim(), acceptance);
        }

    }

    /**
     * Removes the user from crowd group belonging to the organization.
     *
     * @param service the service
     * @param organizationId the organization id
     * @param userToRemove the user to remove
     * @param authUser the auth user
     */
    private static void removeUserFromTeams(final TerminologyService service, final String organizationId, final User userToRemove, final User authUser) {

        if (PROPERTIES.getProperty("crowd.unit.test.skip") == null || !"true".equalsIgnoreCase(PROPERTIES.getProperty("crowd.unit.test.skip"))) {
            logger.info("CALLING CROWD API from ProjectService updateMemberships");

            try {
                // teams associated with projects for removal from crowd too.
                final ResultList<Project> projects = getOrganizationProjects(service, organizationId);
                if (projects != null && projects.getItems() != null) {
                    for (final Project project : projects.getItems()) {
                        for (final String teamId : project.getTeams()) {
                            final Team team = TeamService.getTeam(teamId, true);
                            TeamService.removeUserFromTeam(authUser, teamId, userToRemove.getId());
                            for (final String role : team.getRoles()) {
                                try {
                                    final String groupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(project.getEdition().getShortName(), project.getCrowdProjectId(), role);
                                    CrowdAPIClient.deleteMembership(groupName, userToRemove.getUserName());
                                } catch (Exception e) {
                                    logger.error("ERROR removing user {} from team {} for organization {}.", userToRemove.getUserName(), team.getId(), organizationId, e);
                                }
                            }
                        }
                    }
                }

                // teams not associated with project that are would not be in crowd.
                final ResultList<Team> orgTeams = OrganizationService.getOrganizationTeams(service, organizationId);
                if (orgTeams != null && orgTeams.getItems() != null)
                    for (final Team team : orgTeams.getItems()) {
                        if (team.getMembers() != null && team.getMembers().contains(userToRemove.getId())) {
                            TeamService.removeUserFromTeam(authUser, team.getId(), userToRemove.getId());
                        }
                    }

            } catch (Exception e) {
                logger.error("ERROR removing user {} from CROWD groups.", userToRemove.getUserName(), e);
            }
        }
    }
}
