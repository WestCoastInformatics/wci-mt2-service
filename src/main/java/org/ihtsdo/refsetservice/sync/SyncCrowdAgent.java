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
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncDatabaseHandler;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncCrowdAgent extends SyncAgent {

    private static Logger logger = LoggerFactory.getLogger(SyncCrowdAgent.class);

    private static final int EDITION_SHORTNAME = 1;

    private static final int PROJECT_NAME = 2;

    private static final int ROLE = 3;

    public void sync() throws Exception {
        logger.info("Starting sync of CrowdAgent");
        final Set<String> uniqueUsers = new HashSet<>();

        // Get data from Crowd

        try (final TerminologyService service = new TerminologyService()) {
            final Map<String, Set<String>> crowdProjectMembers = CrowdAPIClient.getAllGroupsMembers();
            final Set<String> crowdProjects = crowdProjectMembers.keySet();

            logger.debug("zzz-1: createOrUpdateUsers");
            logger.debug("crowdProjects {}", crowdProjects);
            logger.debug("crowdProjectMembers {}", crowdProjectMembers);
            crowdProjects.stream().forEach(group -> uniqueUsers.addAll(crowdProjectMembers.get(group)));
            Map<String, User> userMap = processUsers(uniqueUsers);
            final List<User> dbUsers = service.getAll(User.class);
            logger.debug("zzz-1: Out with dbUsers.size(): " + dbUsers.size());

            logger.debug("zzz-2: addRemoveUsersToOrganizations");
            assignUsersToOrganizations(crowdProjectMembers, userMap);
            final List<Organization> dbOrganizations = service.getAll(Organization.class);
            List<Organization> orgsWithUsers = dbOrganizations.stream().filter(o -> !o.getMembers().isEmpty()).collect(Collectors.toList());
            String s = orgsWithUsers.isEmpty() ? "null" : orgsWithUsers.iterator().next().getName() + " including " + orgsWithUsers.iterator().next().getMembers().iterator().next();
            logger.debug("zzz-2: Out with a total users in all orgs {} ", orgsWithUsers.size());

            logger.debug("zzz-3: createOrUpdateTeams");
            processTeams(crowdProjects);
            List<Team> dbTeams = service.getAll(Team.class);
            logger.debug("zzz-3: Out with dbTeams.size: " + dbTeams.size());

            logger.debug("zzz-4: addRemoveTeamMembers");
            assignTeamMembers(crowdProjectMembers, userMap);
            dbTeams = service.getAll(Team.class);
            List<Team> teamsWithUsers = dbTeams.stream().filter(t -> !t.getMembers().isEmpty() || !t.getMembers().isEmpty()).collect(Collectors.toList());
            s = teamsWithUsers.isEmpty() ? "null" : teamsWithUsers.iterator().next().getName() + " including " + teamsWithUsers.iterator().next().getMembers().iterator().next();
            logger.debug("zzz-4: Out with: " + teamsWithUsers.size() + " with first entry: " + s);

            logger.debug("zzz-5: Done");
        }
    }

    // Important: If issues arise in missing or unexpected members of team, first place to look is CROWD for inconsistencies across members in teams
    private void assignTeamMembers(Map<String, Set<String>> crowdGroupMembersMap, Map<String, User> userMap) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            Map<String, Map<String, Set<String>>> dbOrganizationToTeamUsernamesMap = identifyOrganizationTeams(service, crowdGroupMembersMap, userMap);

            SyncDatabaseHandler.initializeService(service);

            for (String organizationId : dbOrganizationToTeamUsernamesMap.keySet()) {
                final Organization organization = service.get(organizationId, Organization.class);

                for (String teamId : dbOrganizationToTeamUsernamesMap.get(organizationId).keySet()) {

                    Map<String, User> userIdMap = new HashMap<>();

                    // Initialize user information er team (to get latest from previous team updates)
                    for (User user : organization.getMembers()) {
                        user = service.get(user.getId(), User.class);
                        userMap.put(user.getUserName(), user);
                        userIdMap.put(user.getId(), user);
                    }

                    final Team team = service.get(teamId, Team.class);

                    // TODO: Handle Updated user in crowd (say email)
                    final List<User> localMembers = new ArrayList<>();
                    team.getMembers().stream().forEach(userId -> localMembers.add(userIdMap.get(userId)));

                    // Identify and remove users from RT2 organization
                    Set<User> removeLocally = new HashSet<User>(localMembers);
                    localMembers.stream().filter(u -> dbOrganizationToTeamUsernamesMap.get(organizationId).get(teamId).contains(u.getUserName())).forEach(u -> removeLocally.remove(u));

                    // Remove users from RT2 organization
                    for (User user : removeLocally) {

                        logger.info("remove local users from organization {}'s team: {}: " + removeLocally, organization.getName(), team.getName());
                        OrganizationService.removeUserFromOrganization(service, SecurityService.getUserFromSession(), user.getId(), organizationId);
                    }

                    // Identify and add users to RT2 team
                    Set<String> addLocally = new HashSet<String>(dbOrganizationToTeamUsernamesMap.get(organizationId).get(teamId));
                    localMembers.stream().filter(u -> dbOrganizationToTeamUsernamesMap.get(organizationId).get(teamId).contains(u.getUserName())).forEach(us -> addLocally.remove(us.getUserName()));

                    // Add users to RT2 team
                    for (String username : addLocally) {

                        logger.info("add local users to organization {}: " + addLocally, organization.getName());
                        OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), organizationId, userMap.get(username));
                    }
                }
            }
        }
    }

    private Map<String, Map<String, Set<String>>> identifyOrganizationTeams(TerminologyService service, Map<String, Set<String>> crowdGroupMembersMap, Map<String, User> userMap) throws Exception {
        Map<String, Map<String, Set<String>>> dbOrganizationToTeamUsernamesMap = new HashMap<>();

        final List<Edition> dbEditions = readDbAllEditions(service);

        Map<Edition, List<Team>> editionTeamMap = new HashMap<>();

        for (Edition dbEdition : dbEditions) {

            final List<Team> dbTeams = OrganizationService.getOrganizationTeams(service, dbEdition.getOrganizationId()).getItems();

            editionTeamMap.put(dbEdition, dbTeams);
        }

        for (String crowdGroup : crowdGroupMembersMap.keySet()) {

            final String[] groupCoordinates = crowdGroup.split("-");

            final String crowdCodeSystem = groupCoordinates[EDITION_SHORTNAME];
            final String crowdGroupName = groupCoordinates[PROJECT_NAME];

            for (Edition dbEdition : editionTeamMap.keySet()) {

                final List<Team> dbTeams = new ArrayList<>();

                if ("all".equals(crowdCodeSystem) || getEditionShortNameToCrowdCodeSystem(dbEdition.getShortName()).equals(crowdCodeSystem)) {
                    dbTeams.addAll(editionTeamMap.get(dbEdition));
                }

                for (Team dbTeam : dbTeams) {
                    // Match on team and edition to ensure proper handling of similarly named teams across editions.
                    if (crowdGroupName.equals(dbTeam.getName()) && crowdGroupName.equals(getEditionShortNameToCrowdCodeSystem(dbEdition.getShortName()))) {

                        if (!dbOrganizationToTeamUsernamesMap.containsKey(dbEdition.getOrganizationId())) {
                            dbOrganizationToTeamUsernamesMap.put(dbEdition.getOrganizationId(), new HashMap<>());
                        }

                        if (!dbOrganizationToTeamUsernamesMap.get(dbEdition.getOrganizationId()).containsKey(dbTeam.getId())) {
                            dbOrganizationToTeamUsernamesMap.get(dbEdition.getOrganizationId()).put(dbTeam.getId(), new HashSet<>());
                        }

                        final Set<String> crowdMembersUsernames = crowdGroupMembersMap.get(crowdGroup);

                        dbOrganizationToTeamUsernamesMap.get(dbEdition.getOrganizationId()).get(dbTeam.getId());
                        dbOrganizationToTeamUsernamesMap.get(dbEdition.getOrganizationId()).get(dbTeam.getId()).addAll(crowdMembersUsernames);
                    }
                }
            }
        }

        return dbOrganizationToTeamUsernamesMap;
    }

    // Important: If issues arise in missing or unexpected aspects of a users, first place to look is CROWD for inconsistencies across members in users
    private Map<String, User> processUsers(Set<String> uniqueUsers) throws Exception {
        // Create or update users based on Crowd values
        Map<String, User> userMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {

            List<User> dbUsers = service.getAll(User.class);

            for (String crowdUsername : uniqueUsers) {

                User crowdUser = CrowdAPIClient.getUser(crowdUsername);

                List<User> matchingUsers = dbUsers.stream().filter(u -> u.getUserName().equals(crowdUsername)).collect(Collectors.toList());

                User rt2User = null;

                if (matchingUsers == null || matchingUsers.isEmpty()) {

                    // Create user
                    rt2User = utilities.getUser(crowdUser.getName(), crowdUsername, crowdUser.getEmail(), crowdUser.getRoles());
                    logger.info("Added new user found on Crowd: " + rt2User);

                } else if (matchingUsers.size() == 1) {

                    // User already exists. Check for changes.
                    // Note: Roles defined via group name and will be done later
                    boolean changeMade = false;
                    User dbUser = matchingUsers.iterator().next();

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
                        logger.info("Updated existing user based on changes in Crowd: " + rt2User);
                    } else {

                        // No changes, but still need to add user to map
                        rt2User = service.get(dbUser.getId(), User.class);
                    }

                } else {
                    throw new Exception("Only permitted one user in database to have username:" + crowdUsername);
                }

                userMap.put(crowdUsername, rt2User);
            }
        }

        return userMap;
    }

    // Important: If issues arise in missing or unexpected aspects of a team, first place to look is CROWD for inconsistencies across members in teams
    private void processTeams(Set<String> allCrowdTeamRoleStrings) throws Exception {
        try (final TerminologyService service = new TerminologyService()) {

            Map<String, Map<String, Set<String>>> organizationGroupRolesMap = identifyOrganizationTeams(service, allCrowdTeamRoleStrings);

            for (String organizationId : organizationGroupRolesMap.keySet()) {

                Organization organization = service.get(organizationId, Organization.class);

                final Set<String> crowdCodeSystemGroupNames = organizationGroupRolesMap.get(organizationId).keySet();
                final Set<String> dbOrganizationGroupNames = new HashSet<>();
                final Map<String, Team> teamMap = new HashMap<>();

                OrganizationService.getOrganizationTeams(service, organization.getId()).getItems().stream().forEach(team -> {
                    dbOrganizationGroupNames.add(team.getName());
                    teamMap.put(team.getName(), team);
                });

                logger.debug("aaa-8");
                logger.debug("organization {} ", organization.getName());
                logger.debug("dbOrganizationGroupNames {} ", dbOrganizationGroupNames);
                logger.debug("crowdCodeSystemGroupNames {} ", crowdCodeSystemGroupNames);

                // Make sure RT2 team exists as expected in crowd

                // First, Identify what teams to remove from RT2 and do so
                Set<String> removeLocally = new HashSet<String>(dbOrganizationGroupNames);
                removeLocally.removeAll(crowdCodeSystemGroupNames);

                for (String teamToRemove : removeLocally) {
                    logger.debug("aaa-9");

                    final Team removedTeam = TeamService.inactivateTeam(SecurityService.getUserFromSession(), teamMap.get(teamToRemove).getId());
                    teamMap.put(teamToRemove, removedTeam);
                }

                logger.debug("removeLocally {} ", removeLocally);

                List<String> teamsToAdd = crowdCodeSystemGroupNames.stream().filter(crowdTeamToAdd -> !dbOrganizationGroupNames.contains(crowdTeamToAdd)).collect(Collectors.toList());
                for (String teamName : teamsToAdd) {
                    logger.debug("aaa-10");

                    final Team newTeam = new Team();
                    newTeam.setDescription("Team created in Crowd brought over to RT2 " + organization.getName());
                    newTeam.setName(teamName);
                    newTeam.setPrimaryContactEmail(organization.getPrimaryContactEmail());
                    newTeam.setOrganization(organization);
                    newTeam.setRoles(organizationGroupRolesMap.get(organizationId).get(teamName));

                    final Team createdTeam = TeamService.createTeam(SecurityService.getUserFromSession(), newTeam);
                    teamMap.put(teamName, createdTeam);
                }

                if (!teamsToAdd.isEmpty() || !removeLocally.isEmpty()) {
                    logger.debug("aaa-11");
                    logger.debug("teamsToAdd {} ", teamsToAdd);
                    logger.debug("removeLocally {} ", removeLocally);
                }

                // Then make sure RT2 roles match Crowd
                List<String> inBoth = crowdCodeSystemGroupNames.stream().filter(crowdTeamToAdd -> dbOrganizationGroupNames.contains(crowdTeamToAdd)).collect(Collectors.toList());

                logger.debug("aaa-12");
                logger.debug("inBoth {} ", inBoth);

                for (String teamName : inBoth) {
                    if (!organizationGroupRolesMap.get(organizationId).get(teamName).equals(teamMap.get(teamName).getRoles())) {

                        logger.info("Updated organization {}'s team {}", organizationId, teamName);
                        teamMap.get(teamName).setRoles(organizationGroupRolesMap.get(organizationId).get(teamName));
                        final Team updatedTeam = service.update(teamMap.get(teamName));
                        teamMap.put(teamName, updatedTeam);
                    }
                }

            }
        }
        logger.debug("aaa-13 -- DONE WITH TEAMS");

    }

    private Map<String, Map<String, Set<String>>> identifyOrganizationTeams(TerminologyService service, Set<String> allCrowdTeamRoleStrings) throws Exception {
        logger.debug("aaa crowdGroups {}", allCrowdTeamRoleStrings);

        // Edition, to Team, to Roles
        final Map<String, Map<String, Set<String>>> organizationGroupRolesMap = new HashMap<>();
        final Map<String, Set<String>> organizationGroupCrowdStringMap = new HashMap<>();
        final Map<String, Set<String>> organizationIgnoredGroupsMap = new HashMap<>();

        final Set<Organization> dbOrganizations = new HashSet<>();
        final List<Edition> dbEditions = readDbAllEditions();
        dbEditions.stream().forEach(e -> dbOrganizations.add(e.getOrganization()));

        // Sort crowdGroups by edition/groupName/Set<Role>
        for (String crowdTeamRoleString : allCrowdTeamRoleStrings) {
            // if (isTesting() && !crowdTeamRoleString.startsWith("rt2-" + editionShortNameToCrowdCodeSystem(testingEditionShortName) + "-")) {
            // continue;
            // // TODO: Change to throw new Exception("No edition in DB for editionShortName: " + crowdCodeSystem);
            // }
            logger.debug("aaa-1");
            logger.debug("crowdTeamRoleString {} ", crowdTeamRoleString);

            final String[] groupCoordinates = crowdTeamRoleString.split("-");

            final String crowdCodeSystem = groupCoordinates[EDITION_SHORTNAME];
            final String crowdGroupName = groupCoordinates[PROJECT_NAME];
            final String crowdGroupRole = groupCoordinates[ROLE];

            if ("all".equals(crowdCodeSystem)) {
                logger.debug("aaa-2");

                // Add team to all orgs
                dbOrganizations.forEach(o -> identifyTeamMembers(o, organizationGroupRolesMap, organizationGroupCrowdStringMap, crowdGroupName, crowdTeamRoleString, crowdGroupRole));

            } else {
                // Specific edition specified
                logger.debug("aaa-3");

                // Identify crowd teams & edition's teams in DB (via Org)
                List<Edition> editions = dbEditions.stream().filter(e -> getEditionShortNameToCrowdCodeSystem(e.getShortName()).equals(crowdCodeSystem)).collect(Collectors.toList());

                if (editions.size() == 0) {
                    if (!organizationIgnoredGroupsMap.containsKey(crowdCodeSystem)) {
                        organizationIgnoredGroupsMap.put(crowdCodeSystem, new HashSet<>());
                    }
                    organizationIgnoredGroupsMap.get(crowdCodeSystem).add(crowdGroupRole);

                } else if (editions.size() > 1) {
                    logger.error("Too many editions for editions.size(): " + editions.size());
                } else {
                    logger.debug("aaa-5");
                    identifyTeamMembers(editions.iterator().next().getOrganization(), organizationGroupRolesMap, organizationGroupCrowdStringMap, crowdGroupName, crowdTeamRoleString, crowdGroupRole);
                }

                logger.debug("aaa-6");
            }

            logger.debug("aaa-7");
        }

        logger.info("Ignoring these code systems {} and all their respective groups {}", organizationIgnoredGroupsMap.keySet(), organizationIgnoredGroupsMap);

        return organizationGroupRolesMap;
    }

    private void identifyTeamMembers(Organization organization, Map<String, Map<String, Set<String>>> organizationGroupRolesMap, Map<String, Set<String>> organizationGroupCrowdStringMap,
        String crowdGroupName, String crowdTeamRoleString, String crowdGroupRole) {

        // logger.debug("bbb-1");
        // logger.debug("organization {} ", organization.getName());

        if (!organizationGroupRolesMap.containsKey(organization.getId())) {
            organizationGroupRolesMap.put(organization.getId(), new HashMap<>());
            organizationGroupCrowdStringMap.put(organization.getId(), new HashSet<>());
            logger.debug("bbb-2 with organization {} ", organization.getName());

        }

        if (!organizationGroupRolesMap.get(organization.getId()).containsKey(crowdGroupName)) {
            organizationGroupRolesMap.get(organization.getId()).put(crowdGroupName, new HashSet<>());
            organizationGroupCrowdStringMap.get(organization.getId()).add(crowdTeamRoleString);
            logger.debug("bbb-3 with organization {} ", organization.getName());
        }

        // logger.debug("bbb-4");
        organizationGroupRolesMap.get(organization.getId()).get(crowdGroupName).add(crowdGroupRole);
    }

    // Important: If issues arise in missing or unexpected members of organizations, first place to look is CROWD for inconsistencies across members in organizations
    private void assignUsersToOrganizations(Map<String, Set<String>> crowdGroupMembers, Map<String, User> dbUserMap) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            SyncDatabaseHandler.initializeService(service);

            final Map<String, Set<String>> organizationGroupsMap = identifyOrganizationGroups(crowdGroupMembers.keySet());
            logger.debug("qqq1 - groupEditionToUpdateMap {} ", organizationGroupsMap);

            final Map<String, Set<String>> dbOrganizationUsernamesMap = identifyOrganizationUsers(service, organizationGroupsMap, crowdGroupMembers);
            logger.debug("qqq2 - dbOrganizationUsernamesMap {} ", dbOrganizationUsernamesMap);

            for (String organizationId : dbOrganizationUsernamesMap.keySet()) {
                final Organization organization = service.get(organizationId, Organization.class);
                Map<String, User> dbUserIdMap = new HashMap<>();

                // Initialize user information per team (to get latest from previous team updates)
                for (User user : organization.getMembers()) {
                    user = service.get(user.getId(), User.class);
                    dbUserMap.put(user.getUserName(), user);
                    dbUserIdMap.put(user.getId(), user);
                }

                // TODO: Handle Updated user in crowd (say email)
                final List<User> localMembers = new ArrayList<>();
                organization.getMembers().stream().forEach(user -> localMembers.add(dbUserIdMap.get(user.getId())));

                // Identify and remove users from RT2 organization
                Set<User> removeLocally = new HashSet<User>(localMembers);
                localMembers.stream().filter(u -> dbOrganizationUsernamesMap.get(organizationId).contains(u.getUserName())).forEach(u -> removeLocally.remove(u));

                // Remove users from RT2 organization
                for (User user : removeLocally) {

                    logger.info("remove local users {} from organization {}: ", removeLocally, organization.getName());
                    OrganizationService.removeUserFromOrganization(service, SecurityService.getUserFromSession(), user.getId(), organization.getId());
                }

                // Identify and add users to RT2 organization
                Set<String> addLocally = new HashSet<String>(dbOrganizationUsernamesMap.get(organizationId));
                localMembers.stream().filter(u -> dbOrganizationUsernamesMap.get(organizationId).contains(u.getUserName())).forEach(us -> addLocally.remove(us.getUserName()));

                // Add users to RT2 organization
                for (String username : addLocally) {

                    logger.info("add local users {} to organization {}: ", addLocally, organization.getName());
                    OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), organization.getId(), dbUserMap.get(username));
                }
            }
        }
    }

    private Map<String, Set<String>> identifyOrganizationUsers(TerminologyService service, Map<String, Set<String>> organizationGroupsMap, Map<String, Set<String>> crowdGroupMembersMap)
        throws Exception {

        final Map<String, Set<String>> retMap = new HashMap<>();
        organizationGroupsMap.keySet().stream().forEach(o -> retMap.put(o, new HashSet<>()));

        logger.debug("qqq3 organizationGroupsMap {} ", organizationGroupsMap);
        for (String organizationId : organizationGroupsMap.keySet()) {
            logger.debug("qqq3 organization {} ", organizationId);
            logger.debug("qqq3 organizationGroupsMap.get(organization) {} ", organizationGroupsMap.get(organizationId));

            for (String group : organizationGroupsMap.get(organizationId)) {

                final String crowdEdition = group.split("-")[EDITION_SHORTNAME];

                // Update in case Org aspects of edition have been changed since edition added
                if ("all".equals(crowdEdition)) {
                    retMap.keySet().stream().forEach(o -> retMap.get(o).addAll(crowdGroupMembersMap.get(group)));

                } else {
                    retMap.get(organizationId).addAll(crowdGroupMembersMap.get(group));
                }
            }
        }

        return retMap;
    }

    private Map<String, Set<String>> identifyOrganizationGroups(Set<String> crowdGroups) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Map<String, Set<String>> retMap = new HashMap<>();
            List<Organization> dbOrganizations = service.getAll(Organization.class);
            dbOrganizations.stream().forEach(o -> retMap.put(o.getId(), new HashSet<>()));

            final Map<String, Edition> dbEditionMap = new HashMap<>();
            readDbAllEditions().stream().forEach(e -> dbEditionMap.put(getEditionShortNameToCrowdCodeSystem(e.getShortName()), e));

            logger.debug("ppp3 - dbEditionMap {} ", dbEditionMap);

            for (String group : crowdGroups) {
                logger.debug("ppp4 - group {} ", group);

                final String[] groupCoordinates = group.split("-");
                final String crowdCodeSystem = groupCoordinates[EDITION_SHORTNAME];

                // Identify Edition(s) to process
                if ("all".equals(crowdCodeSystem)) {

                    // Ensure all orgs have group
                    logger.debug("ppp51");
                    dbOrganizations.stream().forEach(o -> retMap.get(o.getId()).add(group));
                    logger.debug("ppp52");

                } else if (dbEditionMap.containsKey(crowdCodeSystem)) {

                    retMap.get(dbEditionMap.get(crowdCodeSystem).getOrganization().getId()).add(group);
                }

            }

            return retMap;
        }
    }

    private String getEditionShortNameToCrowdCodeSystem(String editionShortName) {
        return editionShortName.toLowerCase().replace("-", "");
    }
}