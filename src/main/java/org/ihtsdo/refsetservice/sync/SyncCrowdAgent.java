/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class SyncCrowdAgent.
 */
public class SyncCrowdAgent extends SyncAgent {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncCrowdAgent.class);

    /** The Constant EDITION_SHORTNAME. */
    private static final int EDITION_SHORTNAME = 1;

    /** The Constant PROJECT_NAME. */
    private static final int PROJECT_NAME = 2;

    /* see superclass */
    @Override
    public void syncComponent(final TerminologyService service) throws Exception {

        LOG.info("Starting CrowdAgent sync()");

        final Set<String> uniqueUsers = new HashSet<>();

        // Get data from Crowd

        final Map<String, Set<String>> crowdRulesMembersMap = CrowdAPIClient.getAllCrowdRuleMembers();

        // Identify changes to existing users and/or add new users information (from CROWD)
        crowdRulesMembersMap.keySet().stream().forEach(group -> uniqueUsers.addAll(crowdRulesMembersMap.get(group)));

        final Map<String, User> userMap = processUsers(service, uniqueUsers);

        // EditionId to Rules
        final Map<String, Set<String>> filteredEditionRulesMap = identifyEditionRules(service, crowdRulesMembersMap.keySet());

        // TODO: Handle Remove case?
        assignUsersToOrganizations(service, crowdRulesMembersMap, userMap, filteredEditionRulesMap);

        // TODO: Handle Remove case?
        assignUsersToAdminTeams(service, userMap, filteredEditionRulesMap);

        addNewProjects(service, crowdRulesMembersMap, userMap, filteredEditionRulesMap);

        LOG.info("Finished syncing SyncCrowdAgent");
    }

    /**
     * Assign users to admin teams.
     *
     * @param service the service
     * @param userMap the user map
     * @param filteredEditionRulesMap the filtered edition rules map
     * @throws Exception the exception
     */
    private void assignUsersToAdminTeams(final TerminologyService service, final Map<String, User> userMap, final Map<String, Set<String>> filteredEditionRulesMap) throws Exception {

        LOG.info("Adding users to admin teams based on updates to CROWD-defined roles  {}", filteredEditionRulesMap);

        final Set<User> adminUsers = new HashSet<>();

        for (String userName : SyncAgent.getAdminUsernames()) {
            adminUsers.add(getUtilities().getUser(service, userName));
        }

        for (String editionId : filteredEditionRulesMap.keySet()) {
            final Edition edition = service.get(editionId, Edition.class);

            // Setup return map of organizations to crowd users
            Team adminTeam = OrganizationService.getOrganizationAdminTeam(service, edition.getOrganizationId());

            if (adminTeam == null) {
                adminTeam = getDbHandler().createAdminOrganizationTeam(service, edition.getOrganization());
            }

            List<User> usersToAdd = new ArrayList<>();

            for (User user : adminUsers) {
                if (!adminTeam.getMembers().contains(user.getId())) {
                    usersToAdd.add(user);
                }
            }

            for (User adminUser : usersToAdd) {
                TeamService.addUserToTeam(service, SecurityService.getUserFromSession(), adminTeam, adminUser);
            }

        }
    }

    /**
     * Process users.
     *
     * @param service the service
     * @param uniqueUsers the unique users
     * @return the map
     * @throws Exception the exception
     */
    // Important: If issues arise in missing or unexpected aspects of a users, first place to look is CROWD for inconsistencies across members in users
    private Map<String, User> processUsers(final TerminologyService service, final Set<String> uniqueUsers) throws Exception {

        // Create or update users based on Crowd values
        final Map<String, User> userMap = new HashMap<>();

        final List<User> dbUsers = service.getAll(User.class);

        for (final String crowdUsername : uniqueUsers) {

            final User crowdUser = CrowdAPIClient.getUser(crowdUsername);

            final List<User> matchingUsers = dbUsers.stream().filter(u -> u.getUserName().equals(crowdUsername)).collect(Collectors.toList());

            User rt2User = null;

            if (matchingUsers == null || matchingUsers.isEmpty()) {

                // Create user
                rt2User = getUtilities().getUser(service, crowdUser.getName(), crowdUsername, crowdUser.getEmail(), crowdUser.getRoles());
                LOG.info("Added new user found on Crowd: " + rt2User);

            } else if (matchingUsers.size() == 1) {

                // User already exists. Check for changes.
                // Note: Roles defined via group name and will be done later
                boolean changeMade = false;
                final User dbUser = matchingUsers.iterator().next();

                if (!dbUser.getName().equals(crowdUser.getName())) {
                    dbUser.setName(crowdUser.getName());
                    changeMade = true;
                }

                if (!dbUser.getEmail().equals(crowdUser.getEmail())) {
                    dbUser.setEmail(crowdUser.getEmail());
                    changeMade = true;
                }

                if (changeMade) {

                    rt2User = service.update(dbUser);
                    LOG.info("Updated existing user based on changes in Crowd: " + rt2User);
                } else {

                    // No changes, but still need to add user to map
                    rt2User = service.get(dbUser.getId(), User.class);
                }

            } else {
                throw new Exception("Only permitted one user in database to have username:" + crowdUsername);
            }

            userMap.put(crowdUsername, rt2User);
        }

        return userMap;
    }

    /**
     * Assign users to organizations.
     *
     * @param service the service
     * @param crowdRulesMembersMap the crowd rules members map
     * @param dbUserMap the db user map
     * @param filteredEditionRulesMap the filtered edition rules map
     * @return the sets the
     * @throws Exception the exception
     */
    // Important: If issues arise in missing or unexpected members of organizations, first place to look is CROWD for inconsistencies across members in
    // organizations
    private Set<String> assignUsersToOrganizations(final TerminologyService service, final Map<String, Set<String>> crowdRulesMembersMap, final Map<String, User> dbUserMap,
        final Map<String, Set<String>> filteredEditionRulesMap) throws Exception {

        final Set<String> updatedOrganizations = new HashSet<>();

        // Filtered organizationId to Usernames Map
        final Map<String, Set<String>> dbOrganizationUsernamesMap = identifyCrowdOrganizationUsersFromEditions(service, crowdRulesMembersMap, filteredEditionRulesMap);
        LOG.info("Adding users to organizations based on updates to CROWD-defined projects (using the rt2 project-to-edition-to-organizaion hierarchy) {}", dbOrganizationUsernamesMap);

        for (final String organizationId : dbOrganizationUsernamesMap.keySet()) {
            final Map<String, User> dbUserIdMap = new HashMap<>();
            final Organization organization = service.get(organizationId, Organization.class);

            // Initialize user information per organization
            for (final User user : organization.getMembers()) {
                final User dbUser = service.get(user.getId(), User.class);
                dbUserMap.put(user.getUserName(), dbUser);
                dbUserIdMap.put(user.getId(), dbUser);
            }

            final List<User> localMembers = new ArrayList<>();
            organization.getMembers().stream().forEach(user -> localMembers.add(dbUserIdMap.get(user.getId())));

            // Identify and remove users from RT2 organization
            final Set<User> removeLocally = new HashSet<User>(localMembers);
            localMembers.stream().filter(u -> dbOrganizationUsernamesMap.get(organizationId).contains(u.getUserName())).forEach(u -> removeLocally.remove(u));

            // Remove users from RT2 organization
            LOG.info("remove local users {} from organization {}: ", removeLocally, organization.getName());
            for (final User user : removeLocally) {

                if (organization.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()))) {
                    final Organization removedOrganization = OrganizationService.removeUserFromOrganization(service, SecurityService.getUserFromSession(), user.getId(), organization.getId());
                    updatedOrganizations.add(removedOrganization.getId());
                }
            }

            // Identify and add users to RT2 organization
            final Set<String> addLocally = new HashSet<String>(dbOrganizationUsernamesMap.get(organizationId));
            localMembers.stream().filter(u -> dbOrganizationUsernamesMap.get(organizationId).contains(u.getUserName())).forEach(us -> addLocally.remove(us.getUserName()));

            // Add users to RT2 organization
            for (final String username : addLocally) {

                LOG.info("add local users {} to organization {}: ", addLocally, organization.getName());
                if (organization.getMembers().stream().noneMatch(m -> m.getUserName().equals(username))) {
                    final Organization addedOrganization = OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), organization.getId(), dbUserMap.get(username));
                    updatedOrganizations.add(addedOrganization.getId());
                }
            }
        }

        return updatedOrganizations;
    }

    /**
     * Identify crowd organization users from editions.
     *
     * @param service the service
     * @param crowdRulesMembersMap the crowd rules members map
     * @param filteredEditionRulesMap the filtered edition rules map
     * @return the map
     * @throws Exception the exception
     */
    private Map<String, Set<String>> identifyCrowdOrganizationUsersFromEditions(final TerminologyService service, final Map<String, Set<String>> crowdRulesMembersMap,
        final Map<String, Set<String>> filteredEditionRulesMap) throws Exception {

        final Map<String, Set<String>> retMap = new HashMap<>();

        for (final String editionId : filteredEditionRulesMap.keySet()) {
            final Edition edition = service.get(editionId, Edition.class);

            // Setup return map of organizations to crowd users
            if (!retMap.containsKey(edition.getOrganizationId())) {
                retMap.put(edition.getOrganizationId(), new HashSet<>());
            }

            // Assign each rule to the correct organization
            for (final String rule : filteredEditionRulesMap.get(editionId)) {

                final String crowdEdition = rule.split("-")[EDITION_SHORTNAME];

                // Update in case Org aspects of edition have been changed since edition added
                if ("all".equals(crowdEdition)) {
                    retMap.keySet().stream().forEach(organizationId -> retMap.get(organizationId).addAll(crowdRulesMembersMap.get(rule)));

                } else {
                    retMap.get(edition.getOrganizationId()).addAll(crowdRulesMembersMap.get(rule));
                }
            }
        }

        return retMap;
    }

    /**
     * Identify edition rules.
     *
     * @param service the service
     * @param crowdRules the crowd rules
     * @return the map
     * @throws Exception the exception
     */
    // Map of those organizations to sync and their set of crowd rules
    private Map<String, Set<String>> identifyEditionRules(final TerminologyService service, final Set<String> crowdRules) throws Exception {

        final Map<String, Set<String>> retMap = new HashMap<>();
        final Set<String> editionsToSync = new HashSet<>();

        final Map<String, Edition> dbEditionMap = new HashMap<>();
        readDbAllEditions(service).stream().forEach(e -> dbEditionMap.put(getEditionShortNameToEdition(e.getShortName()), e));

        // Determine which editions are relevant and prepare return map for their organizations
        for (final Edition edition : dbEditionMap.values()) {
            if (FILTERED_CODE_SYSTEMS.stream().anyMatch(cs -> cs.get("shortName").asText().equals(edition.getShortName()))) {
                editionsToSync.add(edition.getId());
            }
        }

        editionsToSync.stream().forEach(id -> retMap.put(id, new HashSet<>()));

        // For each rule in crowd
        for (final String rule : crowdRules) {

            final String[] groupCoordinates = rule.split("-");
            final String crowdCodeSystem = groupCoordinates[EDITION_SHORTNAME];

            // Identify Edition(s) to process
            if ("all".equals(crowdCodeSystem)) {

                // Ensure all orgs have group
                editionsToSync.stream().forEach(id -> retMap.get(id).add(rule));

            } else if (dbEditionMap.containsKey(crowdCodeSystem)) {
                // Only handle if edition's organization
                String editionId = dbEditionMap.get(crowdCodeSystem).getId();

                if (editionsToSync.contains(editionId)) {
                    retMap.get(editionId).add(rule);
                }
            }

        }

        return retMap;
    }

    // Returns a map from an edition to a set of projects representing projects added to rt2 during sync
    /**
     * Adds the new projects.
     *
     * @param service the service
     * @param crowdGroupMembersMap the crowd group members map
     * @param userMap the user map
     * @param organizationGroupsMap the organization groups map
     * @return the map
     * @throws Exception the exception
     */
    // Important: If issues arise in missing or unexpected aspects of a project, first place to look is CROWD for inconsistencies across members in projects
    private Map<String, Set<Project>> addNewProjects(final TerminologyService service, final Map<String, Set<String>> crowdGroupMembersMap, final Map<String, User> userMap,
        final Map<String, Set<String>> organizationGroupsMap) throws Exception {

        final Map<String, Set<Project>> addedProjectMap = new HashMap<>();
        final Map<String, Edition> dbEditionMap = new HashMap<>();

        readDbAllEditions(service).stream().forEach(e -> dbEditionMap.put(getEditionShortNameToEdition(e.getShortName()), e));

        final Map<String, Set<String>> editionProjectsMap = identifyCrowdEditionProjects(service, crowdGroupMembersMap.keySet());
        LOG.info("Adding projects based on updates to CROWD-defined projects {}", editionProjectsMap);

        for (final String editionId : editionProjectsMap.keySet()) {

            final Edition edition = service.get(editionId, Edition.class);
            final Set<String> projects = editionProjectsMap.get(editionId);

            final Set<String> editionProjectsNames = ProjectService.getProjectNamesForEdition(edition.getId());

            for (final String projectName : projects) {

                if (!editionProjectsNames.contains(projectName)) {
                    LOG.info("Adding new project just found on crowd for first time {} in {}", projectName, edition.getName());

                    final Project newProject = getDbHandler().addProject(service, projectName, "Default description for crowd-defined project: " + projectName, edition);

                    if (!addedProjectMap.containsKey(editionId)) {
                        addedProjectMap.put(editionId, new HashSet<>());
                    }

                    addedProjectMap.get(editionId).add(newProject);
                }
            }
        }

        return addedProjectMap;
    }

    /**
     * Identify crowd edition projects.
     *
     * @param service the service
     * @param crowdGroups the crowd groups
     * @return the map
     * @throws Exception the exception
     */
    // Returns a map from an editionId to a set of project names representing projects to add
    private Map<String, Set<String>> identifyCrowdEditionProjects(final TerminologyService service, final Set<String> crowdGroups) throws Exception {

        // Organization to list of Projects
        final Map<String, Set<String>> crowdEditionProjectsMap = new HashMap<>();

        final List<Edition> dbEditions = readDbAllEditions(service);

        // Sort crowdProjects by edition/groupName/Set<Role>
        for (final String group : crowdGroups) {

            if (isTesting() && getTestingEditionShortName() != null && !getTestingEditionShortName().isEmpty()
                    && !group.startsWith("rt2-" + getEditionShortNameToEdition(getDeveloperTestingEditionShortName()) + "-")) {
                continue;
            }

            final String[] groupCoordinates = group.split("-");

            final String crowdCodeSystem = groupCoordinates[EDITION_SHORTNAME];
            final String crowdProject = groupCoordinates[PROJECT_NAME];

            if ("all".equals(crowdCodeSystem)) {

                // Add project to all orgs
                for (final Edition edition : dbEditions) {
                    if (!crowdEditionProjectsMap.containsKey(edition.getId())) {
                        crowdEditionProjectsMap.put(edition.getId(), new HashSet<>());
                    }

                    crowdEditionProjectsMap.get(edition.getId()).add(crowdProject);
                }

            } else {

                // Specific edition specified
                // Identify crowd projects & edition's projectss in DB (via Org)
                final List<Edition> editions = dbEditions.stream().filter(e -> getEditionShortNameToEdition(e.getShortName()).equals(crowdCodeSystem)).collect(Collectors.toList());

                if (editions.size() == 1) {
                    final String editionId = editions.iterator().next().getId();

                    if (!crowdEditionProjectsMap.containsKey(editionId)) {
                        crowdEditionProjectsMap.put(editionId, new HashSet<>());
                    }

                    crowdEditionProjectsMap.get(editions.iterator().next().getId()).add(crowdProject);
                } else if (getUtilities().getPropertyReader().getCodeSystemsToIgnore().stream().noneMatch(s -> crowdCodeSystem.equals(getEditionShortNameToEdition(s)))) {
                    // Group does not reference an ignored code system
                    LOG.error("Unexpected number of editions (expected 1) for {}: {} ", crowdCodeSystem, editions.size());
                }
            }
        }

        final Map<String, Set<String>> organizationProjectsToUpdateMap = new HashMap<>();

        for (final String editionId : crowdEditionProjectsMap.keySet()) {
            final Edition edition = service.get(editionId, Edition.class);
            final List<Project> dbEditionProjects = OrganizationService.getOrganizationProjects(service, edition.getOrganizationId()).getItems();

            for (final String crowdProjectName : crowdEditionProjectsMap.get(editionId)) {
                if (dbEditionProjects.stream().noneMatch(p -> p.getName().equals(crowdProjectName))) {

                    if (!organizationProjectsToUpdateMap.containsKey(editionId)) {
                        organizationProjectsToUpdateMap.put(editionId, new HashSet<>());
                    }

                    organizationProjectsToUpdateMap.get(editionId).add(crowdProjectName);
                }
            }
        }

        return organizationProjectsToUpdateMap;
    }

    /**
     * Returns the edition short name to edition.
     *
     * @param editionShortName the edition short name
     * @return the edition short name to edition
     */
    private String getEditionShortNameToEdition(final String editionShortName) {

        return editionShortName.toLowerCase().replace("-", "");
    }
}
