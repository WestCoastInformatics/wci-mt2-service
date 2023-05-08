package org.ihtsdo.refsetservice.sync;

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
            logger.debug("zzz-1: Out with: " + dbUsers);

            logger.debug("zzz-2: addRemoveUsersToOrganizations");
            addRemoveUsersToOrganizations(crowdGroupMembers, userMap);
            final List<Organization> dbOrganizations = service.getAll(Organization.class);
            List<Organization> orgsWithUsers = dbOrganizations.stream().filter(o -> !o.getMembers().isEmpty()).collect(Collectors.toList());
            String s = orgsWithUsers.isEmpty() ? "null" : orgsWithUsers.iterator().next().getName() + " including " + orgsWithUsers.iterator().next().getMembers().iterator().next();
            logger.debug("zzz-2: Out with: " + orgsWithUsers.size() + " with first entry: " + s);

            logger.debug("zzz-3: createOrUpdateTeams");
            Map<String, String> editionTeamsMap = createOrUpdateTeams(crowdGroups);
            List<Team> dbTeams = service.getAll(Team.class);
            logger.debug("zzz-3: Out with: " + dbTeams);

            logger.debug("zzz-4: addRemoveTeamMembers");
            addRemoveTeamMembers(editionTeamsMap, crowdGroupMembers, userMap);
            dbTeams = service.getAll(Team.class);
            List<Team> teamsWithUsers = dbTeams.stream().filter(t -> !t.getMembers().isEmpty() || !t.getMemberList().isEmpty()).collect(Collectors.toList());
            s = teamsWithUsers.isEmpty() ? "null" : teamsWithUsers.iterator().next().getName() + " including " + teamsWithUsers.iterator().next().getMembers().iterator().next();
            logger.debug("zzz-4: Out with: " + teamsWithUsers.size() + " with first entry: " + s);

            logger.debug("zzz-5: Done");
        }
    }

    private void addRemoveTeamMembers(Map<String, String> teamToEditionMap, Map<String, Set<String>> crowdGroupMembersMap, Map<String, User> userMap) throws Exception {
        Map<String, Team> dbTeamMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {
            SyncDatabaseHandler.initializeService(service);

            logger.debug("yyy-1");
            final List<Edition> dbEditions = service.getAll(Edition.class);
            final List<Team> dbTeams = service.getAll(Team.class);

            for (Edition edition : dbEditions) {
                logger.debug("yyy-2");
                logger.debug("edition: " + edition.getShortName());

                for (String crowdGroup : crowdGroupMembersMap.keySet()) {
                    logger.debug("yyy-3");
                    logger.debug("crowdGroup: " + crowdGroup);

                    final String[] groupCoordinates = crowdGroup.split("-");
                    final String editionName = groupCoordinates[EDITION_SHORTNAME];
                    final String teamName = groupCoordinates[TEAM_NAME];

                    for (Team dbTeam : dbTeams) {
                        logger.debug("yyy-4");
                        logger.debug("dbTeam: " + dbTeam.getName());
                        logger.debug("dbTeam Members: " + dbTeam.getMembers());
                        logger.debug("dbTeam MemberList: " + dbTeam.getMemberList());

                        // Ensure also matches on expected edition
                        if (teamName.equals(dbTeam.getName()) && teamToEditionMap.get(dbTeam.getId()).equals(editionName)) {
                            logger.debug("yyy-5");
                            logger.debug("MATCH!!! On " + dbTeam.getName());

                            // Identify and remove users from RT2 team
                            Set<String> removeLocally = new HashSet<String>();
                            dbTeam.getMemberList().stream().forEach(u -> removeLocally.add(u.getUserName()));
                            removeLocally.removeAll(crowdGroupMembersMap.get(crowdGroup));

                            for (String username : removeLocally) {
                                logger.debug("     removing username: " + username);
                                dbTeam = TeamService.removeUserFromTeam(service, SecurityService.getUserFromSession(), dbTeam, userMap.get(username));
                            }

                            logger.debug("yyy-5");
                            // Identify and add users from RT2 team
                            Set<String> addLocally = new HashSet<String>(crowdGroupMembersMap.get(crowdGroup));
                            addLocally.removeAll(dbTeamMap.keySet());

                            for (String username : addLocally) {
                                logger.debug("yyy-6"); 
                                logger.debug("dbTeam.getMembers() = " + dbTeam.getMembers());
                                logger.debug("dbTeam.getMemberList() = " + dbTeam.getMemberList());
                                // Avoid trying to add same user twice if listed in multiple crowd groups (as defined per role)
                                if (!dbTeam.getMembers().stream().anyMatch(tid -> userMap.get(username).getId().equals(tid))) {

                                    logger.debug("     adding username: " + username);
                                    dbTeam = TeamService.addUserToTeam(service, SecurityService.getUserFromSession(), dbTeam, userMap.get(username));
                                }
                            }

                            logger.debug("Finished with team having removed - " + removeLocally + " and added " + addLocally);
                        }
                    }
                }
            }
        }
    }

    private Map<String, User> createOrUpdateUsers(Set<String> uniqueUsers) {
        // Create or update users based on Crowd values
        Map<String, User> userMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {

            List<User> dbUsers = service.getAll(User.class);
            dbUsers.stream().forEach(u -> logger.debug("DB User's Username: " + u.getUserName()));

            for (String crowdUsername : uniqueUsers) {

                User crowdUser = CrowdAPIClient.getUser(crowdUsername);

                List<User> matchingUsers = dbUsers.stream().filter(u -> u.getUserName().equals(crowdUsername)).collect(Collectors.toList());

                User rt2User = null;

                if (matchingUsers == null || matchingUsers.isEmpty()) {

                    // Create user
                    rt2User = utilities.getUser(crowdUser.getName(), crowdUsername, crowdUser.getEmail(), crowdUser.getRoles());
                    logger.debug("created user: " + rt2User);

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
                        logger.debug("update user: " + rt2User);
                    } else {
                        logger.debug("no changes to user: " + dbUser);
                    }
                } else {
                    throw new Exception("Only permitted one user in database to have username:" + crowdUsername);
                }

                userMap.put(crowdUsername, rt2User);

            }
        } catch (Exception e) {
            logger.error(e.getStackTrace().toString());
        }

        return userMap;
    }

    private Map<String, String> createOrUpdateTeams(Set<String> crowdGroups) throws Exception {

        Map<String, String> teamToEditionMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {
            final Map<String, Map<String, Set<String>>> editionGroupRolesMap = new HashMap<>();

            // Sort crowdGroups by edition/groupName/Set<Role>
            for (String group : crowdGroups) {

                final String[] groupCoordinates = group.split("-");

                final String crowdCodeSystem = groupCoordinates[EDITION_SHORTNAME];
                final String crowdGroupName = groupCoordinates[TEAM_NAME];
                final String crowdGroupRole = groupCoordinates[ROLE];

                if (!editionGroupRolesMap.containsKey(crowdCodeSystem)) {
                    editionGroupRolesMap.put(crowdCodeSystem, new HashMap<>());
                }

                logger.debug("editionGroupRolesMap: " + editionGroupRolesMap);
                logger.debug("crowdCodeSystem: " + crowdCodeSystem);
                logger.debug("editionGroupRolesMap.get(crowdCodeSystem): " + editionGroupRolesMap.get(crowdCodeSystem));
                if (!editionGroupRolesMap.get(crowdCodeSystem).containsKey(crowdGroupName)) {
                    editionGroupRolesMap.get(crowdCodeSystem).put(crowdGroupName, new HashSet<>());
                }

                editionGroupRolesMap.get(crowdCodeSystem).get(crowdGroupName).add(crowdGroupRole);
            }

            final List<Edition> dbEditions = service.getAll(Edition.class);

            for (String editionShortName : editionGroupRolesMap.keySet()) {

                // Identify crowd teams & edition's teams in DB (via Org)
                List<Edition> editions = dbEditions.stream().filter(e -> e.getShortName().replace("-", "").toLowerCase().equals(editionShortName)).collect(Collectors.toList());
                if (editions.isEmpty()) {
                    logger.debug("No edition in DB for editionShortName: " + editionShortName);
                    continue;

                } else if (editions.size() > 1) {
                    throw new Exception("must have found a zero or one matching edition shortname in the RT2 DB at this point: " + editionShortName);
                }

                final Edition edition = editions.iterator().next();
                Map<String, Team> localTeamMap = new HashMap<>();
                OrganizationService.getOrganizationTeams(service, edition.getOrganizationId()).getItems().stream().forEach(t -> localTeamMap.put(t.getName(), t));

                final Set<String> crowdTeams = editionGroupRolesMap.get(editionShortName).keySet();

                // TODO: Handle Updated team in crowd (say different roles)

                // Identify what to remove from RT2 and do so
                Set<String> removeLocally = new HashSet<String>(localTeamMap.keySet());
                removeLocally.removeAll(crowdTeams);

                for (String teamToRemove : removeLocally) {
                    TeamService.inactivateTeam(SecurityService.getUserFromSession(), localTeamMap.get(teamToRemove).getId());
                }

                // Identify what to add from RT2 and do so
                Set<String> addLocally = new HashSet<String>(crowdTeams);
                addLocally.removeAll(localTeamMap.keySet());

                for (String teamToAdd : addLocally) {

                    final Team crowdTeam = new Team();
                    crowdTeam.setDescription("Team created in Crowd brought over to RT2 " + edition.getOrganization().getName());
                    crowdTeam.setName(teamToAdd);
                    crowdTeam.setPrimaryContactEmail(edition.getOrganization().getPrimaryContactEmail());
                    crowdTeam.setOrganization(edition.getOrganization());
                    crowdTeam.getRoles().addAll(editionGroupRolesMap.get(editionShortName).get(teamToAdd));
                    Team newTeam = TeamService.createTeam(SecurityService.getUserFromSession(), crowdTeam);
                    teamToEditionMap.put(newTeam.getId(), editionShortName);
                }

            }
        }

        return teamToEditionMap;
    }

    private void addRemoveUsersToOrganizations(Map<String, Set<String>> crowdGroupMembers, Map<String, User> userMap) throws Exception {

        final Set<String> organizationsUpdated = new HashSet<>();

        try (final TerminologyService service = new TerminologyService()) {
            SyncDatabaseHandler.initializeService(service);

            final Map<String, Set<Edition>> groupEditionToUpdateMap = identifyGroupEditionsToProcess(crowdGroupMembers.keySet());

            for (String group : groupEditionToUpdateMap.keySet()) {

                for (Edition edition : groupEditionToUpdateMap.get(group)) {

                    if (!organizationsUpdated.contains(edition.getOrganizationName())) {

                        // TODO: Handle Updated user in crowd (say email)
                        final Set<User> localMembers = edition.getOrganization().getMembers();
                        final Set<String> crowdMembersUsernames = crowdGroupMembers.get(group);

                        // Identify what to remove from RT2 and do so
                        Set<User> removeLocally = new HashSet<User>(localMembers);
                        crowdMembersUsernames.stream().forEach(n -> removeLocally.remove(userMap.get(n)));

                        for (User user : removeLocally) {
                            OrganizationService.removeUserFromOrganization(service, SecurityService.getUserFromSession(), user.getId(), edition.getOrganizationId());

                        }

                        // Identify what to add from RT2 and do so
                        Set<String> addLocally = new HashSet<String>(crowdMembersUsernames);
                        localMembers.stream().forEach(u -> addLocally.remove(u.getUserName()));

                        for (String user : addLocally) {
                            try {
                                OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), edition.getOrganizationId(), userMap.get(user).getEmail());
                            } catch (Exception e) {
                                logger.debug("Skipping adding user " + user + " to org: " + edition.getOrganization().getName());
                            }
                        }

                    }

                }
            }

        }
    }

    private Map<String, Set<Edition>> identifyGroupEditionsToProcess(Set<String> crowdGroups) throws Exception {
        final Map<String, Set<Edition>> editionsToUpdate = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {

            List<Edition> dbEditions = service.getAll(Edition.class);
            dbEditions.stream().forEach(e -> logger.debug(e.getName()));

            final Map<String, Edition> dbEditionMap = new HashMap<>();
            dbEditions.stream().forEach(e -> dbEditionMap.put(e.getShortName().replace("-", "").toLowerCase(), e));

            logger.debug("editions: " + dbEditionMap.keySet());

            for (String group : crowdGroups) {
                editionsToUpdate.put(group, new HashSet<>());

                final String[] groupCoordinates = group.split("-");
                logger.debug("groupCoordinates[EDITION_SHORTNAME]: " + groupCoordinates[EDITION_SHORTNAME]);
                // Identify Edition(s) to process
                if ("all".equals(groupCoordinates[EDITION_SHORTNAME])) {

                    editionsToUpdate.get(group).addAll(dbEditions);
                } else if (dbEditionMap.containsKey(groupCoordinates[EDITION_SHORTNAME].toLowerCase())) {

                    editionsToUpdate.get(group).add(dbEditionMap.get(groupCoordinates[EDITION_SHORTNAME]));
                }

            }

        }

        return editionsToUpdate;
    }
}