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
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The Class SyncCrowdAgent.
 */
public class SyncCrowdAgent extends SyncAgent {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncCrowdAgent.class);

    /** The Constant EDITION_SHORTNAME. */
    private static final int ORGANIZATION_SHORTNAME = 1;

    private static final int EDITION_SHORTNAME = 2;

    /** The Constant PROJECT_NAME. */
    private static final int PROJECT_NAME = 3;

    private static final String IGNORED_SYNC_KEYWORD = "all";

    private final Map<String, User> userIdMap = new HashMap<>();

    private Set<JsonNode> FILTERED_CODE_SYSTEMS;

    public SyncCrowdAgent(Set<JsonNode> filteredCodeSystems) {

        this.FILTERED_CODE_SYSTEMS = filteredCodeSystems;
    }

    /* see superclass */
    @Override
    public void syncComponent(final TerminologyService service) throws Exception {

        LOG.info("Starting CrowdAgent sync()");

        final Set<String> uniqueCrowdUsers = new HashSet<>();
        userIdMap.clear();

        // Get data from Crowd (as rule-to-users map)
        final Map<String, Set<String>> crowdRulesMembersMap = CrowdAPIClient.getAllCrowdRuleMembers();
        LOG.debug("FFF {}", crowdRulesMembersMap.keySet());
        // Identify changes to existing users and/or add new users information (from CROWD)
        crowdRulesMembersMap.keySet().stream().forEach(group -> uniqueCrowdUsers.addAll(crowdRulesMembersMap.get(group)));

        final Map<String, User> usernameMap = processUsers(service, uniqueCrowdUsers);

        // EditionId to Rules
        final Map<String, Set<String>> crowdEditionRulesMap = identifyEditionRules(service, crowdRulesMembersMap.keySet());

        // OrgId to member usernames
        final Map<String, Set<String>> crowdOrganizationUsernamesMap = identifyCrowdOrganizationUsersFromEditions(service, crowdRulesMembersMap, crowdEditionRulesMap);

        assignUsersToOrganizations(service, usernameMap, crowdOrganizationUsernamesMap);

        assignUsersToAdminTeams(service, usernameMap, crowdEditionRulesMap);

        addNewProjects(service, crowdRulesMembersMap, usernameMap, crowdEditionRulesMap);

        LOG.info("Finished syncing SyncCrowdAgent");
    }

    /**
     * Assign users to admin teams.
     *
     * @param service the service
     * @param userMap the user map
     * @param crowdEditionRulesMap the filtered edition rules map
     * @throws Exception the exception
     */
    private void assignUsersToAdminTeams(final TerminologyService service, final Map<String, User> userMap, final Map<String, Set<String>> crowdEditionRulesMap) throws Exception {

        LOG.info("Adding users to admin teams based on updates to CROWD-defined roles  {}", crowdEditionRulesMap);

        Set<User> usersToAdd = new HashSet<>();

        for (String userName : SyncAgent.getAdminUsernames()) {

            usersToAdd.add(getUtilities().getUser(service, userName));
        }

        List<Organization> dbOrganizations = readDbOrganizations(service);

        for (Organization dbOrganization : dbOrganizations) {

            // Setup return map of organizations to crowd users
            Team adminTeam = OrganizationService.getActiveOrganizationAdminTeam(service, dbOrganization.getId());

            if (adminTeam == null) {

                adminTeam = getDbHandler().createAdminOrganizationTeam(service, dbOrganization);
            }

            for (String userId : adminTeam.getMembers()) {

                usersToAdd.add(userIdMap.get(userId));
            }

            for (User adminUser : usersToAdd) {

                if (SyncAgent.getAdminUsernames().stream().noneMatch(username -> adminUser.getUserName().equals(username))) {

                    TeamService.addUserToTeam(service, SecurityService.getUserFromSession(), adminTeam, adminUser);
                }

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

            userIdMap.put(rt2User.getId(), rt2User);
            userMap.put(crowdUsername, rt2User);
        }

        return userMap;
    }

    /**
     * Assign users to organizations.
     *
     * @param service the service
     * @param dbUserMap the db user map
     * @param crowdEditionRulesMap the filtered edition to rules map
     * @return the sets the
     * @throws Exception the exception
     */
    // Important: If issues arise in missing or unexpected members of organizations, first place to look is CROWD for inconsistencies across members in
    // organizations
    private void assignUsersToOrganizations(final TerminologyService service, final Map<String, User> usernameMap, final Map<String, Set<String>> crowdOrganizationUsernamesMap) throws Exception {

        final List<Organization> dbOrganizations = readDbOrganizations(service);

        // Identify users to add
        for (final Organization dbOrganization : dbOrganizations) {

            Organization addedOrganization = dbOrganization;

            for (final String username : crowdOrganizationUsernamesMap.get(addedOrganization.getId())) {

                LOG.info("add local users {} to organization {}: ", crowdOrganizationUsernamesMap.get(addedOrganization.getId()), addedOrganization.getName());

                addedOrganization = OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), addedOrganization.getId(), usernameMap.get(username));
            }

            for (String username : crowdOrganizationUsernamesMap.get(IGNORED_SYNC_KEYWORD)) {

                addedOrganization = OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), addedOrganization.getId(), usernameMap.get(username));
            }

        }

        LOG.info("also added 'all' local users {} to all above organizations: ", crowdOrganizationUsernamesMap.get(IGNORED_SYNC_KEYWORD));
    }

    /**
     * Identify crowd organization users from editions.
     *
     * @param service the service
     * @param crowdRulesMembersMap the crowd rules members map
     * @param crowdEditionRulesMap the filtered edition rules map
     * @return the map of orgId to members
     * @throws Exception the exception
     */
    private Map<String, Set<String>> identifyCrowdOrganizationUsersFromEditions(final TerminologyService service, final Map<String, Set<String>> crowdRulesMembersMap,
        final Map<String, Set<String>> crowdEditionRulesMap) throws Exception {

        final Map<String, Set<String>> retMap = new HashMap<>();

        final List<Organization> allDbOrganizations = readDbOrganizations(service);
        allDbOrganizations.stream().forEach(o -> retMap.put(o.getId(), new HashSet<>()));
        retMap.put(IGNORED_SYNC_KEYWORD, new HashSet<>());
        retMap.get(IGNORED_SYNC_KEYWORD).addAll(SyncAgent.getAdminUsernames());

        for (final String editionId : crowdEditionRulesMap.keySet()) {

            // Assign each rule to the correct organization
            for (final String rule : crowdEditionRulesMap.get(editionId)) {

                final String[] groupCoordinates = rule.split("-");
                final String ruleOrganization = groupCoordinates[ORGANIZATION_SHORTNAME];
                final String ruleEdition = groupCoordinates[EDITION_SHORTNAME];

                if (IGNORED_SYNC_KEYWORD.equals(ruleOrganization) || IGNORED_SYNC_KEYWORD.equals(ruleEdition)) {

                    allDbOrganizations.stream().forEach(o -> retMap.get(o.getId()).addAll(crowdRulesMembersMap.get(rule)));
                } else {

                    final Edition edition = service.get(editionId, Edition.class);
                    final String organizationId = edition.getOrganizationId();

                    retMap.get(organizationId).addAll(crowdRulesMembersMap.get(rule));

                    // Update in case Org aspects of edition have been changed since edition added
                    retMap.get(edition.getOrganizationId()).addAll(crowdRulesMembersMap.get(rule));
                }

            }

        }

        LOG.debug("GGG4: {}", retMap);

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

        final Map<String, Edition> dbNameEditionMap = new HashMap<>();
        final Map<String, Edition> dbIdEditionMap = new HashMap<>();
        readDbAllEditions(service).stream().forEach(e -> {

            dbNameEditionMap.put(getEditionShortNameToEdition(e.getShortName()), e);
            dbIdEditionMap.put(e.getId(), e);
        });

        // Determine which editions are relevant and prepare return map for their organizations
        for (final Edition edition : dbNameEditionMap.values()) {

            if (FILTERED_CODE_SYSTEMS.stream().anyMatch(cs -> cs.get("shortName").asText().equals(edition.getShortName()))) {

                editionsToSync.add(edition.getId());
            }

        }

        editionsToSync.add(IGNORED_SYNC_KEYWORD);
        editionsToSync.stream().forEach(id -> retMap.put(id, new HashSet<>()));

        // For each rule in crowd
        for (final String rule : crowdRules) {

            final String[] groupCoordinates = rule.split("-");
            final String ruleEdition = groupCoordinates[EDITION_SHORTNAME];

            // Identify Edition(s) to process using the "all" ignored_sync_keyword differently
            String editionId = null;

            if (IGNORED_SYNC_KEYWORD.equals(ruleEdition)) {

                editionId = ruleEdition;
            } else if (dbNameEditionMap.containsKey(ruleEdition)) {

                editionId = dbNameEditionMap.get(ruleEdition).getId();
            }

            if (editionsToSync.contains(editionId)) {

                retMap.get(editionId).add(rule);
            }

        }

        LOG.debug("EEE4 {}", retMap);

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

        final Map<String, Set<String>> crowdEditionProjectNamessMap = identifyCrowdEditionProjects(service, crowdGroupMembersMap.keySet());
        LOG.info("Adding projects based on updates to CROWD-defined projects {}", crowdEditionProjectNamessMap);

        for (final String editionId : crowdEditionProjectNamessMap.keySet()) {

            final Edition edition = service.get(editionId, Edition.class);
            Map<String, String> uatEditionProjectInfo = getUtilities().getPropertyReader().getUatEditionProjectInfoMap(edition.getShortName());

            final Set<String> crowdProjectIds = crowdEditionProjectNamessMap.get(editionId);

            for (final String crowdProjectId : crowdProjectIds) {

                LOG.info("Adding new project just found on crowd for first time {} in {}", crowdProjectId, edition.getName());

                final Project newProject =
                    getDbHandler().addProject(service, uatEditionProjectInfo.get(crowdProjectId), "Default description for crowd-defined project: " + crowdProjectId, edition, crowdProjectId);

                if (!addedProjectMap.containsKey(editionId)) {

                    addedProjectMap.put(editionId, new HashSet<>());
                }

                addedProjectMap.get(editionId).add(newProject);
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

            if (isTesting() && testingEditionShortName != null && !testingEditionShortName.isEmpty()
                && !group.startsWith("rt2-" + getEditionShortNameToEdition(getDeveloperTestingEditionShortName()) + "-")) {

                continue;
            }

            final String[] groupCoordinates = group.split("-");

            final String editionShortName = groupCoordinates[EDITION_SHORTNAME];

            // Specific edition specified
            // Identify crowd projects & edition's projectss in DB (via Org)
            final List<Edition> editions = dbEditions.stream().filter(e -> getEditionShortNameToEdition(e.getShortName()).equals(editionShortName)).collect(Collectors.toList());

            if (editions.size() == 1) {

                final String editionId = editions.iterator().next().getId();

                if (!crowdEditionProjectsMap.containsKey(editionId)) {

                    crowdEditionProjectsMap.put(editionId, new HashSet<>());
                }

                final String crowdProject = groupCoordinates[PROJECT_NAME];
                crowdEditionProjectsMap.get(editions.iterator().next().getId()).add(crowdProject);
            } else if (getUtilities().getPropertyReader().getCodeSystemsToIgnore().stream().noneMatch(s -> editionShortName.equals(getEditionShortNameToEdition(s)))) {

                // Group does not reference an ignored code system
                LOG.debug("editions.size(): " + editions.size());
                LOG.debug("editions: " + editions);
                LOG.error("Unexpected number of editions (expected 1) for {}: {} ", editionShortName, editions.size());
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
