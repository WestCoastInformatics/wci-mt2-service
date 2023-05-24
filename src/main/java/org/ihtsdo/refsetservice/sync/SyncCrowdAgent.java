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
import org.ihtsdo.refsetservice.sync.util.SyncDatabaseHandler;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncCrowdAgent extends SyncAgent {

    private static Logger logger = LoggerFactory.getLogger(SyncCrowdAgent.class);

    private static final int EDITION_SHORTNAME = 1;

    private static final int PROJECT_NAME = 2;

    public void sync() throws Exception {
        logger.info("Starting sync of CrowdAgent");
        final Set<String> uniqueUsers = new HashSet<>();

        // Get data from Crowd

        try (final TerminologyService service = new TerminologyService()) {
            final Map<String, Set<String>> crowdGroupMembersMap = CrowdAPIClient.getAllGroupsMembers();

            // Identify changes to existing users and/or add new users information (from CROWD)
            crowdGroupMembersMap.keySet().stream().forEach(group -> uniqueUsers.addAll(crowdGroupMembersMap.get(group)));

            Map<String, User> userMap = processUsers(uniqueUsers);

            final Map<String, Set<String>> organizationGroupsMap = identifyOrganizationGroups(crowdGroupMembersMap.keySet());

            assignUsersToOrganizations(crowdGroupMembersMap, userMap, organizationGroupsMap);

            assignUsersToAdminTeams(userMap);

            addNewProjects(crowdGroupMembersMap, userMap, organizationGroupsMap);

            logger.info("Finished syncing SyncCrowdAgent");
        }
    }

    private void assignUsersToAdminTeams(Map<String, User> userMap) throws Exception {
        try (TerminologyService service = new TerminologyService()) {
            SyncDatabaseHandler.initializeService(service);

            List<Organization> dbOrganizations = readDbOrganizations();

            Set<User> adminUsers = new HashSet<>();
            for (String userName : SyncAgent.getAdminUsernames()) {
                adminUsers.add(utilities.getUser(userName));
            }

            for (Organization organization : dbOrganizations) {

                Team adminTeam = OrganizationService.getOrganizationAdminTeam(service, organization.getId());

                for (User user : adminUsers) {
                    adminTeam = TeamService.addUserToTeam(service, SecurityService.getUserFromSession(), adminTeam, user);
                }

            }

        }

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

    // Important: If issues arise in missing or unexpected members of organizations, first place to look is CROWD for inconsistencies across members in organizations
    private Set<Organization> assignUsersToOrganizations(Map<String, Set<String>> crowdGroupMembersMap, Map<String, User> dbUserMap, Map<String, Set<String>> organizationGroupsMap) throws Exception {

        Set<Organization> updatedOrganizations = new HashSet<>();

        try (final TerminologyService service = new TerminologyService()) {
            SyncDatabaseHandler.initializeService(service);

            final Map<String, Set<String>> dbOrganizationUsernamesMap = identifyCrowdOrganizationUsers(service, crowdGroupMembersMap, organizationGroupsMap);

            for (String organizationId : dbOrganizationUsernamesMap.keySet()) {

                final Map<String, User> dbUserIdMap = new HashMap<>();
                final Organization organization = service.get(organizationId, Organization.class);

                // Initialize user information per organization
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
                    final Organization removedOrganization = OrganizationService.removeUserFromOrganization(service, SecurityService.getUserFromSession(), user.getId(), organization.getId());
                    updatedOrganizations.add(removedOrganization);
                }

                // Identify and add users to RT2 organization
                Set<String> addLocally = new HashSet<String>(dbOrganizationUsernamesMap.get(organizationId));
                localMembers.stream().filter(u -> dbOrganizationUsernamesMap.get(organizationId).contains(u.getUserName())).forEach(us -> addLocally.remove(us.getUserName()));

                // Add users to RT2 organization
                for (String username : addLocally) {

                    logger.info("add local users {} to organization {}: ", addLocally, organization.getName());
                    final Organization addedOrganization = OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), organization.getId(), dbUserMap.get(username));
                    updatedOrganizations.add(addedOrganization);
                }
            }
        }

        return updatedOrganizations;
    }

    private Map<String, Set<String>> identifyCrowdOrganizationUsers(TerminologyService service, Map<String, Set<String>> crowdGroupMembersMap, Map<String, Set<String>> organizationGroupsMap)
        throws Exception {

        final Map<String, Set<String>> retMap = new HashMap<>();
        organizationGroupsMap.keySet().stream().forEach(o -> retMap.put(o, new HashSet<>()));

        for (String organizationId : organizationGroupsMap.keySet()) {

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

    private Map<String, Set<String>> identifyOrganizationGroups(Set<String> crowdProjects) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Map<String, Set<String>> retMap = new HashMap<>();
            List<Organization> dbOrganizations = readDbOrganizations();
            dbOrganizations.stream().forEach(o -> retMap.put(o.getId(), new HashSet<>()));

            final Map<String, Edition> dbEditionMap = new HashMap<>();
            readDbAllEditions().stream().forEach(e -> dbEditionMap.put(getEditionShortNameToEdition(e.getShortName()), e));

            for (String group : crowdProjects) {

                final String[] groupCoordinates = group.split("-");
                final String crowdCodeSystem = groupCoordinates[EDITION_SHORTNAME];

                // Identify Edition(s) to process
                if ("all".equals(crowdCodeSystem)) {

                    // Ensure all orgs have group
                    dbOrganizations.stream().forEach(o -> retMap.get(o.getId()).add(group));

                } else if (dbEditionMap.containsKey(crowdCodeSystem)) {

                    retMap.get(dbEditionMap.get(crowdCodeSystem).getOrganization().getId()).add(group);
                }

            }

            return retMap;
        }
    }

    // Returns a map from an edition to a set of projects representing projects added to rt2 during sync
    // Important: If issues arise in missing or unexpected aspects of a project, first place to look is CROWD for inconsistencies across members in projects
    private Map<String, Set<Project>> addNewProjects(Map<String, Set<String>> crowdGroupMembersMap, Map<String, User> userMap, Map<String, Set<String>> organizationGroupsMap) throws Exception {

        final Map<String, Set<Project>> addedProjectMap = new HashMap<>();
        final Map<String, Edition> dbEditionMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {
            readDbAllEditions().stream().forEach(e -> dbEditionMap.put(getEditionShortNameToEdition(e.getShortName()), e));

            Map<String, Set<String>> editionProjectsMap = identifyCrowdEditionProjects(service, crowdGroupMembersMap.keySet());

            for (String editionId : editionProjectsMap.keySet()) {

                Edition edition = service.get(editionId, Edition.class);
                Set<String> projects = editionProjectsMap.get(editionId);

                Set<String> editionProjectsNames = ProjectService.getProjectNamesForEdition(edition.getId());

                for (String projectName : projects) {

                    if (!editionProjectsNames.contains(projectName)) {
                        final Project newProject = dbHandler.addProject(projectName, "Default description for crowd-defined project: " + projectName, edition);

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

    // Returns a map from an editionId to a set of project names representing projects to add
    private Map<String, Set<String>> identifyCrowdEditionProjects(TerminologyService service, Set<String> crowdGroups) throws Exception {

        // Organization to list of Projects
        final Map<String, Set<String>> crowdEditionProjectsMap = new HashMap<>();

        final List<Edition> dbEditions = readDbAllEditions();

        // Sort crowdProjects by edition/groupName/Set<Role>
        for (String group : crowdGroups) {

            if (isTesting() && !group.startsWith("rt2-" + getEditionShortNameToEdition(testingEditionShortName) + "-")) {
                continue;
            }

            final String[] groupCoordinates = group.split("-");

            final String crowdCodeSystem = groupCoordinates[EDITION_SHORTNAME];
            final String crowdProject = groupCoordinates[PROJECT_NAME];

            if ("all".equals(crowdCodeSystem)) {

                // Add project to all orgs
                for (Edition edition : dbEditions) {
                    if (!crowdEditionProjectsMap.containsKey(edition.getId())) {
                        crowdEditionProjectsMap.put(edition.getId(), new HashSet<>());
                    }

                    crowdEditionProjectsMap.get(edition.getId()).add(crowdProject);
                }

            } else {

                // Specific edition specified
                // Identify crowd projects & edition's projectss in DB (via Org)
                List<Edition> editions = dbEditions.stream().filter(e -> getEditionShortNameToEdition(e.getShortName()).equals(crowdCodeSystem)).collect(Collectors.toList());

                if (editions.size() == 1) {
                    String editionId = editions.iterator().next().getId();

                    if (!crowdEditionProjectsMap.containsKey(editionId)) {
                        crowdEditionProjectsMap.put(editionId, new HashSet<>());
                    }

                    crowdEditionProjectsMap.get(editions.iterator().next().getId()).add(crowdProject);
                } else {
                    logger.error("Unexpected number of editions (expected 1) for {}: {} ", crowdCodeSystem, editions.size());
                }
            }
        }

        final Map<String, Set<String>> organizationProjectsToUpdateMap = new HashMap<>();

        for (String editionId : crowdEditionProjectsMap.keySet()) {
            Edition edition = service.get(editionId, Edition.class);
            List<Project> dbEditionProjects = OrganizationService.getOrganizationProjects(service, edition.getOrganizationId()).getItems();

            for (String crowdProjectName : crowdEditionProjectsMap.get(editionId)) {
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

    private String getEditionShortNameToEdition(String editionShortName) {
        return editionShortName.toLowerCase().replace("-", "");
    }
}