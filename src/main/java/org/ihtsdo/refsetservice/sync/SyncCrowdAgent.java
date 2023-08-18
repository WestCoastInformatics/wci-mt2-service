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
import org.ihtsdo.refsetservice.sync.util.SyncProjectMetadata;
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
    private static final int ORGANIZATION_NAME = 1;

    /** The Constant EDITION_SHORTNAME. */
    private static final int EDITION_SHORTNAME = 2;

    /** The Constant PROJECT_NAME. */
    private static final int PROJECT_NAME = 3;

    /** The Constant IGNORED_SYNC_KEYWORD. */
    private static final String IGNORED_SYNC_KEYWORD = "all";

    /** The user id map. */
    private final Map<String, User> userIdMap = new HashMap<>();

    /** The filtered code systems. */
    private Set<JsonNode> filteredCodeSystems;

    /**
     * Instantiates a {@link SyncCrowdAgent} from the specified parameters.
     *
     * @param filteredCodeSystems the filtered code systems
     */
    public SyncCrowdAgent(final Set<JsonNode> filteredCodeSystems) {

        this.filteredCodeSystems = filteredCodeSystems;
    }

    /* see superclass */
    @Override
    public void syncComponent(final TerminologyService service) throws Exception {

        LOG.info("Starting CrowdAgent sync()");

        final Set<String> uniqueCrowdUsers = new HashSet<>();
        userIdMap.clear();

        // Get data from Crowd (as rule-to-users map)
        final Map<String, Set<String>> crowdRulesMembersMap = CrowdAPIClient.getAllCrowdRuleMembers();

        // Identify changes to existing users and/or add new users information (from CROWD)
        crowdRulesMembersMap.keySet().stream().forEach(group -> uniqueCrowdUsers.addAll(crowdRulesMembersMap.get(group)));

        // EditionId to Rules
        final Map<String, Set<String>> organizationEditionRulesMap = identifyOrganizationEditionRules(service, crowdRulesMembersMap.keySet());

        // Identify and cache users to add
        final Map<String, User> usernameMap = preProcessUsers(service, uniqueCrowdUsers);

        // OrgId to member usernames
        final Map<String, Set<String>> crowdOrganizationUsernamesMap = identifyCrowdOrganizationUsersFromEditions(service, crowdRulesMembersMap, organizationEditionRulesMap);

        // Assign users to orgs and admin teams
        assignUsersToOrganizations(service, usernameMap, crowdOrganizationUsernamesMap);

        assignUsersToAdminTeams(service, usernameMap, organizationEditionRulesMap);

        // Add new projects
        addNewProjects(service, crowdRulesMembersMap, usernameMap, organizationEditionRulesMap);

        // TODO: Add Teams? How?
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

        // LOG.info("Adding users to admin teams based on updates to CROWD-defined roles {}", crowdEditionRulesMap);

        final Set<User> usersToAdd = new HashSet<>();

        for (String userName : SyncAgent.getAdminUsernames()) {

            usersToAdd.add(getUtilities().getUser(service, userName));
        }

        List<Organization> dbOrganizations = readDbOrganizations(service);

        for (Organization dbOrganization : dbOrganizations) {

            // Setup return map of organizations to crowd users
            Team adminTeam = getDbHandler().getUtilities().getOrCreateAdminOrganizationTeam(service, dbOrganization);

            for (String userId : adminTeam.getMembers()) {

                usersToAdd.add(userIdMap.get(userId));
            }

            for (User adminUser : usersToAdd) {

                if (SyncAgent.getAdminUsernames().stream().noneMatch(username -> adminUser.getUserName().equals(username))) {

                    // Don't try adding if already member of team
                    if (adminTeam.getMembers() != null && adminTeam.getMembers().contains(adminUser.getId())) {

                        TeamService.addUserToTeam(service, SecurityService.getUserFromSession(), adminTeam, adminUser);
                    }

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
    private Map<String, User> preProcessUsers(final TerminologyService service, final Set<String> uniqueUsers) throws Exception {

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
     * @param usernameMap the username map
     * @param crowdOrganizationUsernamesMap the crowd organization usernames map
     * @throws Exception the exception
     */
    private void assignUsersToOrganizations(final TerminologyService service, final Map<String, User> usernameMap, final Map<String, Set<String>> crowdOrganizationUsernamesMap) throws Exception {

        // Important: If issues arise in missing or unexpected members of organizations, first place to look is CROWD for inconsistencies across members in
        // organizations

        final List<Organization> dbOrganizations = readDbOrganizations(service);

        // Identify users to add
        for (final Organization dbOrganization : dbOrganizations) {

            Organization addedOrganization = dbOrganization;
            LOG.info("add local users (including admin ones) to organization {}: {} {}", addedOrganization.getName(), crowdOrganizationUsernamesMap.get(addedOrganization.getId()),
                crowdOrganizationUsernamesMap.get(IGNORED_SYNC_KEYWORD));

            for (final String username : crowdOrganizationUsernamesMap.get(addedOrganization.getId())) {

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
                final String ruleOrganization = groupCoordinates[ORGANIZATION_NAME];
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
    private Map<String, Set<String>> identifyOrganizationEditionRules(final TerminologyService service, final Set<String> crowdRules) throws Exception {

        final Map<String, Set<String>> retMap = new HashMap<>();
        final Set<String> editionsToSync = new HashSet<>();
        final Map<String, Set<String>> organizationNameToEditionIdMap = new HashMap<>();
        final Set<String> ignoredRules = new HashSet<>();

        final Map<String, Edition> dbNameEditionMap = new HashMap<>();
        final Map<String, Edition> dbIdEditionMap = new HashMap<>();
        readDbAllEditions(service).stream().forEach(e -> {

            dbNameEditionMap.put(getEditionShortNameToEdition(e.getShortName()), e);
            dbIdEditionMap.put(e.getId(), e);
        });

        // Determine which editions are relevant and prepare return map for their organizations
        for (final Edition edition : dbNameEditionMap.values()) {

            if (filteredCodeSystems.stream().anyMatch(cs -> cs.get("shortName").asText().equals(edition.getShortName()))) {

                // Only sync if no projects in database. This signifies first time processing a code system and thus must rely upon Crowd to define users & projects
                if (OrganizationService.getOrganizationProjects(service, edition.getOrganizationId()).size() == 0) {

                    // Add to editions to sync
                    editionsToSync.add(edition.getId());

                    final String organizationRuleName = getOrganizationNameToOrganizationRule(edition.getOrganizationName());

                    // Prepare for rules where for edition = "all" while organization specified
                    if (!organizationNameToEditionIdMap.containsKey(edition.getOrganization().getName())) {

                        organizationNameToEditionIdMap.put(organizationRuleName, new HashSet<>());
                    }

                    organizationNameToEditionIdMap.get(organizationRuleName).add(edition.getId());
                }

            }

        }

        // Must also apply the "all" rules to any edition being encountered for first time
        editionsToSync.stream().forEach(id -> retMap.put(id, new HashSet<>()));

        // For each rule in crowd
        for (final String rule : crowdRules) {

            final String[] groupCoordinates = rule.split("-");
            final String ruleEdition = groupCoordinates[EDITION_SHORTNAME];
            final String ruleOrganization = groupCoordinates[ORGANIZATION_NAME];
            // Identify Edition(s) to process using the "all" ignored_sync_keyword differently

            boolean ruleIgnored = true;

            if (!IGNORED_SYNC_KEYWORD.equals(ruleEdition)) {

                if (IGNORED_SYNC_KEYWORD.equals(ruleOrganization)) {

                    throw new Exception("Can't have a rule with 'rt2-IGNORED_SYNC_KEYWORD-<edition>-' starting the crowd rule");
                } else if (dbNameEditionMap.containsKey(ruleEdition)) {

                    String editionId = dbNameEditionMap.get(ruleEdition).getId();

                    if (editionsToSync.contains(editionId)) {

                        ruleIgnored = false;
                        retMap.get(editionId).add(rule);
                    }

                }

            } else {

                // edition is IGNORED_SYNC_KEYWORD
                if (IGNORED_SYNC_KEYWORD.equals(ruleOrganization)) {

                    // All-All (org/edition rules) so apply rule to all organizations' editions
                    organizationNameToEditionIdMap.keySet().stream().forEach(orgName -> organizationNameToEditionIdMap.get(orgName).stream().forEach(eId -> retMap.get(eId).add(rule)));
                    ruleIgnored = false;
                } else if (organizationNameToEditionIdMap.containsKey(ruleOrganization)) {

                    // Identify the organization's editions and apply the rule to all of them
                    organizationNameToEditionIdMap.get(ruleOrganization).stream().forEach(eId -> retMap.get(eId).add(rule));
                    ruleIgnored = false;
                }

            }

            if (ruleIgnored) {

                ignoredRules.add(rule);
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
     * @param f the organization groups map
     * @return the map
     * @throws Exception the exception
     */
    // Important: If issues arise in missing or unexpected aspects of a project, first place to look is CROWD for inconsistencies across members in projects
    private Map<String, Set<Project>> addNewProjects(final TerminologyService service, final Map<String, Set<String>> crowdGroupMembersMap, final Map<String, User> userMap,
        final Map<String, Set<String>> organizationEditionRulesMap) throws Exception {

        final Map<String, Set<Project>> addedProjectMap = new HashMap<>();

        LOG.info("Adding projects based on updates to CROWD-defined projects {}", organizationEditionRulesMap);
        getUtilities().getPropertyReader().parseRttData();

        for (final String editionId : organizationEditionRulesMap.keySet()) {

            final Edition edition = service.get(editionId, Edition.class);

            final Set<SyncProjectMetadata> rttProjectData = getUtilities().getPropertyReader().getProjectData();

            final Set<String> editionRules = organizationEditionRulesMap.get(editionId);

            for (final String rule : editionRules) {

                final String[] groupCoordinates = rule.split("-");
                final String crowdProjectName = groupCoordinates[PROJECT_NAME];

                // TODO: Handle rules such as rt2-all-all-all-admin
                if (!IGNORED_SYNC_KEYWORD.equals(crowdProjectName)) {

                    String projectName = null;
                    String projectDescription = null;

                    // Try finding project info from RTT
                    List<SyncProjectMetadata> rttProjectInfo = rttProjectData.stream().filter(m -> m.getEditionShortName().equals(edition.getShortName())).collect(Collectors.toList());

                    for (SyncProjectMetadata singleProjectInfo : rttProjectInfo) {

                        if (singleProjectInfo.getCrowdId().equals(crowdProjectName)) {

                            projectName = singleProjectInfo.getName();
                            projectDescription = singleProjectInfo.getDescription();

                            LOG.info("Adding new project '{}' to {} based on RTT info has crowd rule '{}' with description {}", projectName, edition.getName(), crowdProjectName, projectDescription);
                        }

                    }

                    // Nothing on RTT. Just create project based on crowd given name
                    if (projectName == null) {

                        projectName = crowdProjectName;
                        projectDescription = "Default description for crowd-defined project: " + projectName;
                        LOG.info("Adding new project '{}' to {} based on crowd rule {} with default description: ", projectName, edition.getName(), crowdProjectName, projectDescription);
                    }

                    // Only add if the project-name hasn't yet been added to the edition
                    final String testName = projectName;

                    if (!addedProjectMap.containsKey(editionId) || addedProjectMap.get(editionId).stream().noneMatch(p -> testName.equals(p.getName()))) {

                        final Project newProject = getDbHandler().addProject(service, projectName, projectDescription, edition, crowdProjectName);

                        if (!addedProjectMap.containsKey(editionId)) {

                            addedProjectMap.put(editionId, new HashSet<>());
                        }

                        addedProjectMap.get(editionId).add(newProject);
                    }

                }

            }

        }

        return addedProjectMap;
    }

    /**
     * Returns the edition short name to edition.
     *
     * @param editionShortName the edition short name
     * @return the edition short name to edition
     */
    private String getOrganizationNameToOrganizationRule(final String name) {

        return name.toLowerCase().replaceAll(" ", "").replaceAll("-", "");
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
