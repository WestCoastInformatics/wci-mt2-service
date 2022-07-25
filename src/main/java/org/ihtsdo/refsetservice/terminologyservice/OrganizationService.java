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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.NotFoundException;

import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.ResultListUser;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
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
        organizationsParameters.setQuery("editionId:" + organization.getEditionId());
        
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
            logger.info("CALLING CROWD API");

            try {
                final String crowdGroupName = CrowdAPIClient.addAdminGroup(newOrganization.getEdition().getShortName(), "Organization Administrator(s)");
                CrowdAPIClient.addMembership(crowdGroupName, user.getUserName());

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
        
        setRoles(user, organization, organization.getRoles());

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
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<Organization> searchOrganizations(final TerminologyService service, final User user, final SearchParameters searchParameters, final boolean includeMembers) throws Exception {

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
        query.setQuery("organization.id:" + organizationId + " AND active:true");

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
    public static void addUserToOrganization(final TerminologyService service, final User user, final String organizationId, final String email) throws Exception {

        final User userToAdd = service.findSingle("email:" + email, User.class, null);

        if (userToAdd == null) {
            
            final String message = "Unable to find user for email " + email + ".";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        final Organization organization = service.get(organizationId, Organization.class);

        if (organization == null) {
            
            final String message = "Unable to find organization for " + organizationId + ".";
            logger.error(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }
        
        checkEditPermissions(user, organization);

        organization.getMembers().add(userToAdd);
        service.add(AuditEntryHelper.addUserToOrganizationEntry(organization, userToAdd));
        service.update(organization);
    }

    /**
     * Removes the user from organization.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param userId the user id
     * @param organizationId the organization id
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization removeUserFromOrganization(final TerminologyService service, final User user, final String userId, final String organizationId) throws Exception {

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
        
        checkEditPermissions(user, organization);

        organization.getMembers().remove(userToRemove);
        service.update(organization);
        service.add(AuditEntryHelper.removeUserFromOrganizationEntry(organization, userToRemove));

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
}
