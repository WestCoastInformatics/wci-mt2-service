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

    private static final int TEAM_NAME = 2;

    private static final int ROLE = 3;

    public void sync() throws Exception {
        logger.info("Starting sync of CrowdAgent");
        final Set<String> uniqueUsers = new HashSet<>();

        // Get data from Crowd

        try (final TerminologyService service = new TerminologyService()) {
            final Map<String, Set<String>> crowdGroupMembers = CrowdAPIClient.getAllGroupsMembers();
            final Set<String> crowdGroups = crowdGroupMembers.keySet();

            logger.debug("zzz-1: createOrUpdateUsers");
            crowdGroups.stream().forEach(group -> uniqueUsers.addAll(crowdGroupMembers.get(group)));
            Map<String, User> userMap = createOrUpdateUsers(uniqueUsers);
            final List<User> dbUsers = service.getAll(User.class);
            logger.debug("zzz-1: Out with dbUsers.size(): " + dbUsers.size());

            logger.debug("zzz-2: addRemoveUsersToOrganizations");
            addRemoveUsersToOrganizations(crowdGroupMembers, userMap);
            final List<Organization> dbOrganizations = service.getAll(Organization.class);
            List<Organization> orgsWithUsers = dbOrganizations.stream().filter(o -> !o.getMembers().isEmpty()).collect(Collectors.toList());
            String s = orgsWithUsers.isEmpty() ? "null" : orgsWithUsers.iterator().next().getName() + " including " + orgsWithUsers.iterator().next().getMembers().iterator().next();
            logger.debug("zzz-2: Out with: " + orgsWithUsers.size() + " with first entry: " + s);

            logger.debug("zzz-3: createOrUpdateTeams");
            createOrUpdateTeams(crowdGroups);
            List<Team> dbTeams = service.getAll(Team.class);
            logger.debug("zzz-3: Out with dbTeams.size: " + dbTeams.size());

            logger.debug("zzz-4: addRemoveTeamMembers");
            addRemoveTeamMembers(crowdGroupMembers, userMap);
            dbTeams = service.getAll(Team.class);
            List<Team> teamsWithUsers = dbTeams.stream().filter(t -> !t.getMembers().isEmpty() || !t.getMembers().isEmpty()).collect(Collectors.toList());
            s = teamsWithUsers.isEmpty() ? "null" : teamsWithUsers.iterator().next().getName() + " including " + teamsWithUsers.iterator().next().getMembers().iterator().next();
            logger.debug("zzz-4: Out with: " + teamsWithUsers.size() + " with first entry: " + s);

            logger.debug("zzz-5: Done");
        }
    }

    // Important: If issues arise in missing or unexpected members of team, first place to look is CROWD for inconsistencies across members in teams
    private void addRemoveTeamMembers(Map<String, Set<String>> crowdGroupMembersMap, Map<String, User> userMap) throws Exception {

        Map<String, Map<String, Set<String>>> dbOrganizationToTeamUsernamesMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {
            SyncDatabaseHandler.initializeService(service);

            final List<Edition> dbEditions = service.getAll(Edition.class);

            for (Edition edition : dbEditions) {

                final List<Team> dbTeams = OrganizationService.getOrganizationTeams(service, edition.getOrganizationId()).getItems();

                if (!dbOrganizationToTeamUsernamesMap.containsKey(edition.getOrganizationId())) {
                    dbOrganizationToTeamUsernamesMap.put(edition.getOrganizationId(), new HashMap<>());
                }

                for (Team dbTeam : dbTeams) {

                    if (!dbOrganizationToTeamUsernamesMap.get(edition.getOrganizationId()).containsKey(dbTeam.getId())) {
                        dbOrganizationToTeamUsernamesMap.get(edition.getOrganizationId()).put(dbTeam.getId(), new HashSet<>());
                    }

                    for (String crowdGroup : crowdGroupMembersMap.keySet()) {

                        final String[] groupCoordinates = crowdGroup.split("-");
                        final String crowdEditionName = groupCoordinates[EDITION_SHORTNAME];
                        final String crowdTeamName = groupCoordinates[TEAM_NAME];

                        // Match on team and edition to ensure proper handling of similarly named teams across editions.
                        if (crowdTeamName.equals(dbTeam.getName()) && crowdEditionName.equals(editionShortNameToCrowdCodeSystem(edition.getShortName()))) {

                            final Set<String> crowdMembersUsernames = crowdGroupMembersMap.get(crowdGroup);

                            dbOrganizationToTeamUsernamesMap.get(edition.getOrganizationId()).get(dbTeam.getId());
                            dbOrganizationToTeamUsernamesMap.get(edition.getOrganizationId()).get(dbTeam.getId()).addAll(crowdMembersUsernames);
                        }
                    }
                }
            }

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
                    logger.info("remove local users from organization {}'s team: {}: " + removeLocally, organization.getName(), team.getName());

                    for (User user : removeLocally) {
                        OrganizationService.removeUserFromOrganization(service, SecurityService.getUserFromSession(), user.getId(), organizationId);

                    }

                    // Identify and add users to RT2 team
                    Set<String> addLocally = new HashSet<String>(dbOrganizationToTeamUsernamesMap.get(organizationId).get(teamId));
                    localMembers.stream().filter(u -> dbOrganizationToTeamUsernamesMap.get(organizationId).get(teamId).contains(u.getUserName())).forEach(us -> addLocally.remove(us.getUserName()));

                    // Add users to RT2 team
                    logger.info("add local users to organization {}: " + addLocally, organization.getName());

                    for (String username : addLocally) {

                        OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), organizationId, userMap.get(username));
                    }
                }
            }
        }
    }

    // Important: If issues arise in missing or unexpected aspects of a users, first place to look is CROWD for inconsistencies across members in users
    private Map<String, User> createOrUpdateUsers(Set<String> uniqueUsers) throws Exception {
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
    private void createOrUpdateTeams(Set<String> allCrowdTeamRoleStrings) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            // Edition, to Team, to Roles
            final Map<String, Map<String, Set<String>>> organizationGroupRolesMap = new HashMap<>();
            final Map<String, Set<String>> organizationGroupCrowdStringMap = new HashMap<>();

            final List<Edition> dbEditions = service.getAll(Edition.class);

            // Sort crowdGroups by edition/groupName/Set<Role>
            for (String crowdTeamRoleString : allCrowdTeamRoleStrings) {
                if (isTesting() && !crowdTeamRoleString.startsWith("rt2-" + editionShortNameToCrowdCodeSystem(testingEditionShortName) + "-")) {
                    continue;
                    // TODO: Change to throw new Exception("No edition in DB for editionShortName: " + crowdCodeSystem);
                }

                final String[] groupCoordinates = crowdTeamRoleString.split("-");

                final String crowdCodeSystem = groupCoordinates[EDITION_SHORTNAME];
                final String crowdGroupName = groupCoordinates[TEAM_NAME];
                final String crowdGroupRole = groupCoordinates[ROLE];
                // Identify crowd teams & edition's teams in DB (via Org)

                List<Edition> editions = dbEditions.stream().filter(e -> editionShortNameToCrowdCodeSystem(e.getShortName()).equals(crowdCodeSystem)).collect(Collectors.toList());

                if (editions.size() != 1) {
                    throw new Exception("Can't have multiple editions matching group: " + crowdTeamRoleString + " with: " + editions);
                }
                final Edition edition = editions.iterator().next();
                final Organization organization = edition.getOrganization();

                if (!organizationGroupRolesMap.containsKey(organization.getId())) {
                    organizationGroupRolesMap.put(organization.getId(), new HashMap<>());
                    organizationGroupCrowdStringMap.put(organization.getId(), new HashSet<>());
                }

                if (!organizationGroupRolesMap.get(organization.getId()).containsKey(crowdGroupName)) {
                    organizationGroupRolesMap.get(organization.getId()).put(crowdGroupName, new HashSet<>());
                    organizationGroupCrowdStringMap.get(organization.getId()).add(crowdTeamRoleString);
                }

                organizationGroupRolesMap.get(organization.getId()).get(crowdGroupName).add(crowdGroupRole);
            }

            for (String organizationId : organizationGroupRolesMap.keySet()) {

                Organization organization = service.get(organizationId, Organization.class);

                final Set<String> crowdCodeSystemGroupNames = organizationGroupRolesMap.get(organizationId).keySet();
                final Set<String> crowdTeamRoleString = organizationGroupCrowdStringMap.get(organizationId);
                final Set<String> dbOrganizationGroupNames = new HashSet<>();
                final Map<String, Team> teamMap = new HashMap<>();

                OrganizationService.getOrganizationTeams(service, organization.getId()).getItems().stream().forEach(team -> {
                    dbOrganizationGroupNames.add(team.getName());
                    teamMap.put(team.getName(), team);
                });

                // Make sure RT2 team exists as expected in crowd

                // First, Identify what teams to remove from RT2 and do so
                Set<String> removeLocally = new HashSet<String>(dbOrganizationGroupNames);
                removeLocally.removeAll(crowdCodeSystemGroupNames);

                for (String teamToRemove : removeLocally) {
                    TeamService.inactivateTeam(SecurityService.getUserFromSession(), teamMap.get(teamToRemove).getId());
                }

                List<String> teamsToAdd = crowdCodeSystemGroupNames.stream().filter(crowdTeamToAdd -> !dbOrganizationGroupNames.contains(crowdTeamToAdd)).collect(Collectors.toList());
                for (String teamName : teamsToAdd) {

                    final Team newTeam = new Team();
                    newTeam.setDescription("Team created in Crowd brought over to RT2 " + organization.getName());
                    newTeam.setName(teamName);
                    newTeam.setPrimaryContactEmail(organization.getPrimaryContactEmail());
                    newTeam.setOrganization(organization);
                    newTeam.getRoles().addAll(organizationGroupRolesMap.get(organizationId).get(teamName));

                    final Team createdTeam = TeamService.createTeam(SecurityService.getUserFromSession(), newTeam);
                    teamMap.put(teamName, createdTeam);
                }

                // Then make sure RT2 roles match Crowd
                List<String> inBoth = crowdCodeSystemGroupNames.stream().filter(crowdTeamToAdd -> dbOrganizationGroupNames.contains(crowdTeamToAdd)).collect(Collectors.toList());

                for (String teamName : inBoth) {
                    if (!organizationGroupRolesMap.get(organizationId).get(teamName).equals(teamMap.get(teamName).getRoles())) {
                        teamMap.get(teamName).getRoles().clear();
                        teamMap.get(teamName).getRoles().addAll(organizationGroupRolesMap.get(organizationId).get(teamName));
                    }
                }
            }
        }
    }

    // Important: If issues arise in missing or unexpected members of organizations, first place to look is CROWD for inconsistencies across members in organizations
    private void addRemoveUsersToOrganizations(Map<String, Set<String>> crowdGroupMembers, Map<String, User> userMap) throws Exception {

        Map<String, Set<String>> dbOrganizationUsernamesMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {
            SyncDatabaseHandler.initializeService(service);

            final Map<String, Set<String>> groupEditionToUpdateMap = identifyGroupEditionsToProcess(crowdGroupMembers.keySet());

            for (String crowdGroup : groupEditionToUpdateMap.keySet()) {

                final String[] groupCoordinates = crowdGroup.split("-");
                final String crowdEditionName = groupCoordinates[EDITION_SHORTNAME];

                for (String editionId : groupEditionToUpdateMap.get(crowdGroup)) {

                    Edition edition = service.get(editionId, Edition.class);

                    // Update in case Org aspects of edition have been changed since edition added
                    if (crowdEditionName.equals(editionShortNameToCrowdCodeSystem(edition.getShortName()))) {

                        if (!dbOrganizationUsernamesMap.containsKey(edition.getOrganizationId())) {
                            dbOrganizationUsernamesMap.put(edition.getOrganizationId(), new HashSet<>());
                        }

                        final Set<String> crowdMembersUsernames = crowdGroupMembers.get(crowdGroup);
                        dbOrganizationUsernamesMap.get(edition.getOrganizationId()).addAll(crowdMembersUsernames);

                    }
                }
            }

            for (String organizationId : dbOrganizationUsernamesMap.keySet()) {
                Organization organization = service.get(organizationId, Organization.class);

                Map<String, User> userIdMap = new HashMap<>();

                // Initialize user information er team (to get latest from previous team updates)
                for (User user : organization.getMembers()) {
                    user = service.get(user.getId(), User.class);
                    userMap.put(user.getUserName(), user);
                    userIdMap.put(user.getId(), user);
                }

                // TODO: Handle Updated user in crowd (say email)
                final List<User> localMembers = new ArrayList<>();
                organization.getMembers().stream().forEach(user -> localMembers.add(userIdMap.get(user.getId())));

                // Identify and remove users from RT2 organization
                Set<User> removeLocally = new HashSet<User>(localMembers);
                localMembers.stream().filter(u -> dbOrganizationUsernamesMap.get(organizationId).contains(u.getUserName())).forEach(u -> removeLocally.remove(u));

                // Remove users from RT2 organization
                logger.info("remove local users from organization {}: " + removeLocally, organization.getName());

                for (User user : removeLocally) {
                    OrganizationService.removeUserFromOrganization(service, SecurityService.getUserFromSession(), user.getId(), organizationId);

                }

                // Identify and add users to RT2 organization
                Set<String> addLocally = new HashSet<String>(dbOrganizationUsernamesMap.get(organizationId));
                localMembers.stream().filter(u -> dbOrganizationUsernamesMap.get(organizationId).contains(u.getUserName())).forEach(us -> addLocally.remove(us.getUserName()));

                // Add users to RT2 organization
                logger.info("add local users to organization {}: " + addLocally, organization.getName());

                for (String username : addLocally) {

                    OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), organizationId, userMap.get(username));
                }

            }

        }

    }

    private Map<String, Set<String>> identifyGroupEditionsToProcess(Set<String> crowdGroups) throws Exception {
        final Map<String, Set<String>> editionsToUpdate = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {

            List<Edition> dbEditions = service.getAll(Edition.class);

            final Map<String, Edition> dbEditionMap = new HashMap<>();
            dbEditions.stream().forEach(e -> dbEditionMap.put(editionShortNameToCrowdCodeSystem(e.getShortName()), e));

            for (String group : crowdGroups) {
                editionsToUpdate.put(group, new HashSet<>());

                final String[] groupCoordinates = group.split("-");

                // Identify Edition(s) to process
                if ("all".equals(groupCoordinates[EDITION_SHORTNAME])) {

                    editionsToUpdate.get(group).addAll(dbEditions.stream().map(Edition::getId).collect(Collectors.toList()));
                } else if (dbEditionMap.containsKey(groupCoordinates[EDITION_SHORTNAME].toLowerCase())) {

                    editionsToUpdate.get(group).add(dbEditionMap.get(groupCoordinates[EDITION_SHORTNAME]).getId());
                }

            }

        }

        return editionsToUpdate;
    }

    private String editionShortNameToCrowdCodeSystem(String editionShortName) {
        return editionShortName.toLowerCase().replace("-", "");
    }
}