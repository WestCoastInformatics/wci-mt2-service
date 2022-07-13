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
     * @param user the user
     * @param organization the organization
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization createOrganization(final User user, final Organization organization) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Organization org = new Organization();
            org.populateFrom(organization);

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.add(org);
            service.add(AuditEntryHelper.newOrganizationEntry(org));
            service.commit();

            // create admin team when creating an organization
            final Team adminTeam = new Team();
            adminTeam.setDescription("Application users which can administrator organization " + organization.getName());
            adminTeam.setName("Administrator(s) for organization " + organization.getName());
            adminTeam.setPrimaryContactEmail(organization.getPrimaryContactEmail());
            adminTeam.getMemberList().add(user);
            adminTeam.setOrganization(org);
            TeamService.createTeam(user, adminTeam);

            if (PROPERTIES.getProperty("crowd.unit.test.skip") == null || !"true".equalsIgnoreCase(PROPERTIES.getProperty("crowd.unit.test.skip"))) {
                logger.info("CALLING CROWD API");

                try {
                    final String crowdGroupName = CrowdAPIClient.addAdminGroup(org.getEdition().getShortName(), "Organization Administrator(s)");
                    CrowdAPIClient.addMembership(crowdGroupName, user.getUserName());

                } catch (Exception e) {

                    final String errorMessage = "Failed adding Crowd groups. Message: " + e.getMessage();
                    logger.error(errorMessage, e);
                    throw new RestException(false, HttpStatus.EXPECTATION_FAILED, e.getMessage(), "Error creating organization.");
                }

            } else {
                logger.info("SKIP CALLING CROWD API");
            }

            return org;
        }
    }

    /**
     * Returns the organization.
     *
     * @param id the id
     * @param includeMembers the include members
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization getOrganization(final String id, final boolean includeMembers) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            final Organization organization = service.findSingle("id: " + id + " AND active:true", Organization.class, null);

            if (organization == null) {
                logger.info("Unable to find organization for id {}.", id);
                throw new NotFoundException();
            }

            if (includeMembers) {
                organization.getMembers();
            } else {
                if (organization.getMembers() != null && !organization.getMembers().isEmpty()) {
                    organization.getMembers().clear();
                }
            }

            return organization;
        }
    }

    /**
     * Update organization.
     *
     * @param user the user
     * @param organization the organization
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization updateOrganization(final User user, final Organization organization) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Organization original = getOrganization(organization.getId(), false);

            if (original == null) {
                throw new NotFoundException();
            }

            original.patchFrom(organization);

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.update(original);
            service.add(AuditEntryHelper.updateOrganizationEntry(original));
            service.commit();

            return original;
        }
    }

    /**
     * Inactivate organization.
     *
     * @param user the user
     * @param organizationId the organization id
     * @return the list
     * @throws Exception the exception
     */
    public static void inactivateOrganization(final User user, final String organizationId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // Find the object
            final Organization organization = getOrganization(organizationId, false);

            if (organization == null) {
                throw new NotFoundException();
            }

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

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
            service.commit();
        }
    }

    /**
     * Search Organizations.
     *
     * @param user the user
     * @param searchParameters the search parameters
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<Organization> searchOrganizations(final User user, final SearchParameters searchParameters) throws Exception {

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
                query = IndexUtility.addWildcardsToQuery(query, Organization.class);
            }

            final ResultList<Organization> results = service.find(query, pfs, Organization.class, null);
            results.setTimeTaken(System.currentTimeMillis() - start);
            results.setTotalKnown(true);

            return results;
        }

    }

    /**
     * Returns the organization users.
     *
     * @param organizationId the organization id
     * @param includeTeams the include teams
     * @return the organization users
     * @throws Exception the exception
     */
    public static ResultListUser getOrganizationUsers(final String organizationId, final boolean includeTeams) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            final Organization organization = service.findSingle("id: " + organizationId + " AND active:true", Organization.class, null);

            if (organization == null) {
                final String message = "Unable to find organization for " + organizationId + ".";
                logger.error(message);
                throw new NotFoundException(message);
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
    }

    /**
     * Returns the organization teams.
     *
     * @param organizationId the organization id
     * @return the organization teams
     * @throws Exception the exception
     */
    public static ResultList<Team> getOrganizationTeams(final String organizationId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            final QueryParameter query = new QueryParameter();
            query.setQuery("organizationId:" + organizationId + " AND active:true");

            return service.find(query, pfs, Team.class, null);
        }
    }

    /**
     * Returns the organization projects.
     *
     * @param organizationId the organization id
     * @return the organization projects
     * @throws Exception the exception
     */
    public static ResultList<Project> getOrganizationProjects(final String organizationId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            final QueryParameter query = new QueryParameter();
            query.setQuery("organization.id:" + organizationId + " AND active:true");

            return service.find(query, pfs, Project.class, null);
        }
    }

    /**
     * Adds the user to organization.
     *
     * @param user the user
     * @param organizationId the organization id
     * @param email the email
     * @throws Exception the exception
     */
    public static void addUserToOrganization(final User user, final String organizationId, final String email) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final User userToAdd = service.findSingle("email:" + email, User.class, null);

            if (userToAdd == null) {
                final String message = "Unable to find user for email " + email + ".";
                logger.error(message);
                throw new NotFoundException(message);
            }

            final Organization organization = service.get(organizationId, Organization.class);

            if (organization == null) {
                final String message = "Unable to find organization for " + organizationId + ".";
                logger.error(message);
                throw new NotFoundException(message);
            }

            service.setModifiedBy(user.getUserName());
            organization.getMembers().add(userToAdd);
            service.add(AuditEntryHelper.addUserToOrganizationEntry(organization, userToAdd));
            service.update(organization);
        }

    }

    /**
     * Removes the user from organization.
     *
     * @param user the user
     * @param userId the user id
     * @param organizationId the organization id
     * @return the organization
     * @throws Exception the exception
     */
    public static Organization removeUserFromOrganization(final User user, final String userId, final String organizationId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // Find the user
            final User userToRemove = service.get(userId, User.class);
            final Organization organization = service.get(organizationId, Organization.class);

            if (userToRemove == null) {
                final String message = "Unable to find user for id " + userId + ".";
                logger.error(message);
                throw new NotFoundException(message);
            }

            if (organization == null) {
                final String message = "Unable to find organization for id " + organizationId + ".";
                logger.error(message);
                throw new NotFoundException(message);
            }

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            organization.getMembers().remove(userToRemove);
            service.update(organization);
            service.add(AuditEntryHelper.removeUserFromOrganizationEntry(organization, userToRemove));
            service.commit();

            return organization;
        }
    }

    /**
     * Update organization icon.
     *
     * @param user the user
     * @param organizationId the organization id
     * @param iconUrlPrefix the icon url prefix
     * @param fileName the file name
     * @throws Exception the exception
     */
    public static void updateOrganizationIcon(final User user, final String organizationId, final String iconUrlPrefix, final String fileName) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());

            // find user record, return 404 if not found
            final Organization organization = service.get(organizationId, Organization.class);

            if (organization == null) {
                final String message = "Unable to find organization for id " + organizationId + ".";
                logger.error(message);
                throw new NotFoundException(message);
            }

            organization.setIconUri(iconUrlPrefix + fileName);
            service.add(AuditEntryHelper.updateIconForOrganizationEntry(organization, fileName));
            service.update(organization);
        }
    }
}
