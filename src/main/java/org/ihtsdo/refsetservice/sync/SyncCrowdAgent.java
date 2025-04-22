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
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncProjectMetadata;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The Class SyncCrowdAgent.
 */
public class SyncCrowdAgent extends SyncAgent {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncCrowdAgent.class);

    /** The Constant ORGANIZATION_CROWD_ID. */
    private static final int ORGANIZATION_CROWD_ID = 1;

    /** The Constant EDITION_SHORTNAME. */
    private static final int EDITION_SHORTNAME = 2;

    /** The Constant PROJECT_NAME. */
    private static final int PROJECT_NAME = 3;

    /** The Constant IGNORED_SYNC_KEYWORD. */
    private static final String ALL_CROWD_KEYWORD = "all";

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

        LOG.info("Starting syncing of Crowd");

        initializeSync(service);

        if (filteredCodeSystems.isEmpty()) {
            service.rollback();
            return;
        }

        final Set<String> uniqueCrowdUsers = new HashSet<>();

        // Get data from Crowd (as rule-to-users map)
        final Map<String, Set<String>> crowdRulesMembersMap = CrowdAPIClient.getAllCrowdRuleMembers();

        // Identify changes to existing users and/or add new users information (from CROWD)
        crowdRulesMembersMap.keySet().stream().forEach(group -> uniqueCrowdUsers.addAll(crowdRulesMembersMap.get(group)));

        // EditionId to Rules
        final Map<String, Set<String>> crowdEditionRulesMap = identifyOrganizationEditionRules(service, crowdRulesMembersMap.keySet());

        // Identify and cache users to add
        final Map<String, User> usernameMap = preProcessUsers(service, uniqueCrowdUsers);

        // OrgId to member usernames
        final Map<String, Set<String>> crowdOrganizationUsernamesMap =
            identifyCrowdOrganizationUsersFromEditions(service, crowdRulesMembersMap, crowdEditionRulesMap);

        // Assign users to orgs and admin teams
        assignUsersToOrganizations(service, usernameMap, crowdOrganizationUsernamesMap);

        // Add new projects
        addNewProjects(service, crowdRulesMembersMap, usernameMap, crowdEditionRulesMap);

        service.commit();

        LOG.info("Finished syncing SyncCrowdAgent");
    }

    private void initializeSync(TerminologyService service) throws Exception {

        updateCodeSystemsToSync(service);

        userIdMap.clear();

        service.beginTransaction();
    }

    /**
     * Update code systems to sync.
     *
     * @param service the service
     * @throws Exception the exception
     */
    private void updateCodeSystemsToSync(final TerminologyService service) throws Exception {

        final List<Project> allProjects = service.getAll(Project.class);
        Set<JsonNode> updatedFilteredCodeSystems = new HashSet<>();
        Set<JsonNode> ignoredFilteredCodeSystems = new HashSet<>();

        for (JsonNode codeSystem : filteredCodeSystems) {

            String codeSystemShortName = codeSystem.get("shortName").asText();

            if (allProjects.stream().noneMatch(p -> p.getEdition().getShortName().equals(codeSystemShortName))) {
                updatedFilteredCodeSystems.add(codeSystem);
            } else {
                ignoredFilteredCodeSystems.add(codeSystem);
            }
        }

        filteredCodeSystems = updatedFilteredCodeSystems;

        // Log the findings
        final Set<String> syncingCodeSystems = new HashSet<>();
        final Set<String> ignoredCodeSystems = new HashSet<>();
        filteredCodeSystems.stream().forEach(cs -> syncingCodeSystems.add(cs.get("shortName").asText()));
        ignoredFilteredCodeSystems.stream().forEach(cs -> ignoredCodeSystems.add(cs.get("shortName").asText()));
        LOG.info("Syncing code systems against Crowd: " + syncingCodeSystems);
        LOG.info("Not Syncing against Crowd for the following code systems as they have been synced against Crowd once already: " + ignoredCodeSystems);
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
    private void assignUsersToOrganizations(final TerminologyService service, final Map<String, User> usernameMap,
        final Map<String, Set<String>> crowdOrganizationUsernamesMap) throws Exception {

        // Important: If issues arise in missing or unexpected members of organizations, first place to look is CROWD for inconsistencies across members in
        // organizations

        final List<Organization> dbOrganizations = readDbOrganizations(service);

        // Identify users to add
        for (final Organization dbOrganization : dbOrganizations) {

            Organization addedOrganization = dbOrganization;
            LOG.info("add local users (including admin ones) to organization {}: {} {}", addedOrganization.getName(),
                crowdOrganizationUsernamesMap.get(addedOrganization.getId()), crowdOrganizationUsernamesMap.get(ALL_CROWD_KEYWORD));

            for (final String username : crowdOrganizationUsernamesMap.get(addedOrganization.getId())) {

                addedOrganization = OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), addedOrganization.getId(),
                    usernameMap.get(username));
            }

            for (final String username : crowdOrganizationUsernamesMap.get(ALL_CROWD_KEYWORD)) {

                addedOrganization = OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), addedOrganization.getId(),
                    usernameMap.get(username));
            }

        }

        LOG.info("also added 'all' local users {} to all above organizations: ", crowdOrganizationUsernamesMap.get(ALL_CROWD_KEYWORD));
    }

    /**
     * Read db organizations.
     *
     * @param service the service
     * @return the list
     * @throws Exception the exception
     */
    public List<Organization> readDbOrganizations(final TerminologyService service) throws Exception {

        return service.getAll(Organization.class);
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
    private Map<String, Set<String>> identifyCrowdOrganizationUsersFromEditions(final TerminologyService service,
        final Map<String, Set<String>> crowdRulesMembersMap, final Map<String, Set<String>> crowdEditionRulesMap) throws Exception {

        // crowdEditionRulesMap: edition id , Set<group name>
        // crowdRulesMembersMap: group name, Set<username>

        crowdRulesMembersMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // combine all groups from editions.
        final Set<String> combinedCrowdSet = crowdEditionRulesMap.values().stream().flatMap(Set::stream).collect(Collectors.toSet());

        // convert to organization Set<group name>
        final Map<String, Set<String>> organizationCrowdGroupMap =
            combinedCrowdSet.stream().collect(Collectors.groupingBy(s -> s.split("-")[1], Collectors.toSet()));

        // org id and list of usernames
        final Map<String, Set<String>> retMap = new HashMap<>();
        final List<Organization> allDbOrganizations = readDbOrganizations(service);
        allDbOrganizations.stream().forEach(o -> retMap.put(o.getId(), new HashSet<>()));

        final Set<String> adminUsers = new HashSet<>();
        if (organizationCrowdGroupMap.containsKey(ALL_CROWD_KEYWORD)) {
            for (final String groupName : organizationCrowdGroupMap.get(ALL_CROWD_KEYWORD)) {
                final Set<String> users = crowdRulesMembersMap.get(groupName);
                if (users != null) {
                    adminUsers.addAll(users);
                }
            }
        }
        retMap.put(ALL_CROWD_KEYWORD, adminUsers);

        for (final Organization organization : allDbOrganizations) {

            if (OrganizationService.getOrganizationEditions(service, organization.getId()).getItems().stream()
                .anyMatch(e -> shortNamesToSync.contains(e.getShortName()))) {

                final String orgCrowdName = organization.getCrowdId();
                final Set<String> groups = organizationCrowdGroupMap.get(orgCrowdName);
                if (groups == null) {
                    continue;
                }
                for (final String groupName : groups) {

                    final Set<String> orgUsers = crowdRulesMembersMap.get(groupName);
                    if (orgUsers != null) {
                        retMap.get(organization.getId()).addAll(orgUsers);
                    }
                }
            }
        }

        for (final String o : retMap.keySet()) {
            LOG.info("ORGANIZATIION {}, members: {}", o, retMap.get(o));
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

        final Map<String, Set<String>> editionIdToRuleReturnMap = new HashMap<>();
        final Set<String> editionsToSync = new HashSet<>();
        final Map<String, String> editionCrowdIdToEditionIdMap = new HashMap<>();
        final Map<String, Set<String>> crowdOrganizationIdToEditionIdMap = new HashMap<>();
        final Set<String> ignoredRules = new HashSet<>();

        final Map<String, Edition> dbShortNameEditionMap = new HashMap<>();
        final Map<String, Edition> dbIdEditionMap = new HashMap<>();
        final List<Edition> allEditions = readDbAllEditions(service);

        for (final Edition edition : allEditions) {

            dbShortNameEditionMap.put(edition.getShortName(), edition);

            dbIdEditionMap.put(edition.getId(), edition);
        }

        // Determine which editions are relevant and prepare return map for their organizations
        for (final Edition edition : dbShortNameEditionMap.values()) {

            if (filteredCodeSystems.stream().anyMatch(cs -> cs.get("shortName").asText().equals(edition.getShortName()))) {

                // Add to editions to sync
                editionsToSync.add(edition.getId());

                // Map the crowdId to editionId
                final String editionCrowdId = CrowdGroupNameAlgorithm.getEditionString(edition.getShortName());

                editionCrowdIdToEditionIdMap.put(editionCrowdId, edition.getId());

                final String organizationCrowdId = edition.getOrganization().getCrowdId();

                // Prepare for rules where for edition = "all" while organization specified
                if (!crowdOrganizationIdToEditionIdMap.containsKey(organizationCrowdId)) {
                    crowdOrganizationIdToEditionIdMap.put(organizationCrowdId, new HashSet<>());
                }

                crowdOrganizationIdToEditionIdMap.get(organizationCrowdId).add(edition.getId());
            }
        }

        if (editionsToSync.isEmpty()) {
            // Already synced every code system in the filteredCodeSystems collection
            return new HashMap<>();
        }

        // Must also apply the "all" rules to any edition being encountered for first time
        editionsToSync.stream().forEach(id -> editionIdToRuleReturnMap.put(id, new HashSet<>()));

        // organize the rules into three categories each stored in their own collection
        Set<String> applyToAll = new HashSet<>();
        Map<String, Set<String>> applyToOrgMap = new HashMap<>();
        Map<String, Set<String>> applyToEditionMap = new HashMap<>();

        for (final String rule : crowdRules) {

            final String[] groupCoordinates = rule.split("-");
            final String editionCrowdIdInRule = groupCoordinates[EDITION_SHORTNAME];
            final String organizationCrowdIdInRule = groupCoordinates[ORGANIZATION_CROWD_ID];
            // Identify Edition(s) to process using the "all" ignored_sync_keyword differently

            if (ALL_CROWD_KEYWORD.equals(editionCrowdIdInRule) && ALL_CROWD_KEYWORD.equals(organizationCrowdIdInRule)) {
                // Apply to all editions across all organizations
                applyToAll.add(rule);

            } else if (ALL_CROWD_KEYWORD.equals(editionCrowdIdInRule) && !ALL_CROWD_KEYWORD.equals(organizationCrowdIdInRule)) {
                // Apply to all editions within specified organization if they are part of the expected editions
                if (editionCrowdIdToEditionIdMap.containsKey(editionCrowdIdInRule)) {
                    if (!applyToOrgMap.containsKey(organizationCrowdIdInRule)) {
                        applyToOrgMap.put(organizationCrowdIdInRule, new HashSet<>());
                    }

                    applyToOrgMap.get(organizationCrowdIdInRule).add(rule);
                } else {
                    // Record we ignored this rule
                    ignoredRules.add(rule);
                }

            } else if (ALL_CROWD_KEYWORD.equals(editionCrowdIdInRule) && !ALL_CROWD_KEYWORD.equals(organizationCrowdIdInRule)) {
                LOG.error("Cannot have rule with all-SNOMEDCT-NL defining the org/edition: " + rule);

            } else {
                // Specific organization & edition specified. Handle specific rule
                if (editionCrowdIdToEditionIdMap.containsKey(editionCrowdIdInRule)) {
                    final String editionId = editionCrowdIdToEditionIdMap.get(editionCrowdIdInRule);
                    Edition edition = dbIdEditionMap.get(editionId);

                    if (organizationCrowdIdInRule.equals(CrowdGroupNameAlgorithm.getCrowdIdFromOrganizationName(edition.getOrganizationName()))) {
                        // Only process below if this is a rule we intend on syncing
                        if (!applyToEditionMap.containsKey(editionCrowdIdInRule)) {
                            applyToEditionMap.put(editionCrowdIdInRule, new HashSet<>());
                        }

                        applyToEditionMap.get(editionCrowdIdInRule).add(rule);
                    } else {
                        // Record we ignored this rule
                        ignoredRules.add(rule);
                    }
                }
            }
        }

        // Add the following rules to all editions in return map
        applyToAll.stream().forEach(rule -> editionIdToRuleReturnMap.keySet().stream().forEach(retMapKey -> editionIdToRuleReturnMap.get(retMapKey).add(rule)));

        // Add the following rules to the orgnaization's editions in return map
        applyToOrgMap.keySet().stream().forEach(organizationCrowdIdInRule -> crowdOrganizationIdToEditionIdMap.get(organizationCrowdIdInRule).stream()
            .forEach(editionId -> editionIdToRuleReturnMap.get(editionId).addAll(applyToOrgMap.get(organizationCrowdIdInRule))));

        // Add the following rules to the orgnaization's editions in return map
        applyToEditionMap.keySet().stream().forEach(editionCrowdIdInRule -> editionIdToRuleReturnMap.get(editionCrowdIdToEditionIdMap.get(editionCrowdIdInRule))
            .addAll(applyToEditionMap.get(editionCrowdIdInRule)));

        return editionIdToRuleReturnMap;

    }

    // Returns a map from an edition to a set of projects representing projects added to rt2 during sync
    /**
     * Adds the new projects.
     *
     * @param service the service
     * @param crowdGroupMembersMap the crowd group members map
     * @param userMap the user map
     * @param organizationEditionRulesMap the organization edition rules map
     * @return the map
     * @throws Exception the exception
     */
    // Important: If issues arise in missing or unexpected aspects of a project, first place to look is CROWD for inconsistencies across members in projects
    private Map<String, Set<Project>> addNewProjects(final TerminologyService service, final Map<String, Set<String>> crowdGroupMembersMap,
        final Map<String, User> userMap, final Map<String, Set<String>> organizationEditionRulesMap) throws Exception {

        final Map<String, Set<Project>> addedProjectMap = new HashMap<>();

        LOG.info("Adding projects based on updates to CROWD-defined projects {}", organizationEditionRulesMap);
        getUtilities().getPropertyReader().parseRttData();

        for (final String editionId : organizationEditionRulesMap.keySet()) {

            final Edition edition = service.get(editionId, Edition.class);

            final Set<SyncProjectMetadata> rttProjectData = getUtilities().getPropertyReader().getProjectData();

            final Set<String> editionRules = organizationEditionRulesMap.get(editionId);

            Map<String, Map<String, String>> crowdProjectNameProjectCache = new HashMap<>();

            for (final String rule : editionRules) {

                final String[] groupCoordinates = rule.split("-");
                final String crowdProjectName = groupCoordinates[PROJECT_NAME];

                // Don't create projects for 'all' projects within edition
                if (!ALL_CROWD_KEYWORD.equals(crowdProjectName)) {

                    String projectName = null;
                    String projectDescription = null;

                    if (!crowdProjectNameProjectCache.containsKey(crowdProjectName)) {

                        // Try finding project info from RTT
                        List<SyncProjectMetadata> rttProjectInfo =
                            rttProjectData.stream().filter(m -> m.getEditionShortName().equals(edition.getShortName())).collect(Collectors.toList());

                        for (SyncProjectMetadata singleProjectInfo : rttProjectInfo) {

                            if (singleProjectInfo.getCrowdId().equals(crowdProjectName)) {

                                projectName = singleProjectInfo.getName();
                                projectDescription = singleProjectInfo.getDescription();

                                LOG.info("Adding new project '{}' to {} based on RTT info has crowd rule '{}' with description {}", projectName,
                                    edition.getName(), crowdProjectName, projectDescription);
                            }

                        }

                        // Nothing on RTT. Just create project based on crowd given name
                        if (projectName == null) {

                            projectName = crowdProjectName;
                            projectDescription = "Default description for crowd-defined project: " + projectName;
                            LOG.info("New project '{}' to {} based on crowd rule {} with default description: {}", projectName, edition.getName(),
                                crowdProjectName, projectDescription);
                        }
                        crowdProjectNameProjectCache.put(crowdProjectName, new HashMap<>());
                        crowdProjectNameProjectCache.get(crowdProjectName).put(projectName, projectDescription);

                    }

                    Map<String, String> projectInfo = crowdProjectNameProjectCache.get(crowdProjectName);

                    // Only add if the project-name hasn't yet been added to the edition
                    final String projectNameToCreate = projectInfo.keySet().iterator().next();
                    final String projectDescriptionToCreate = projectInfo.get(projectNameToCreate);

                    if (!addedProjectMap.containsKey(editionId)
                        || addedProjectMap.get(editionId).stream().noneMatch(p -> projectNameToCreate.equals(p.getName()))) {

                        final Project newProject =
                            getDbHandler().addProject(service, projectNameToCreate, projectDescriptionToCreate, edition, crowdProjectName);

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
}
