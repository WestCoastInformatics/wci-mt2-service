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
            final Map<String, Set<String>> crowdGroupMembersMap = CrowdAPIClient.getAllGroupsMembers();
            final Set<String> crowdProjects = crowdGroupMembersMap.keySet();
4
            final Map<String, Set<String>> organizationGroupsMap = identifyOrganizationGroups(crowdGroupMembersMap.keySet());
            logger.debug("zzz-0 - groupEditionToUpdateMap {} ", organizationGroupsMap);

            // Identify & add users
            logger.debug("zzz-1: processUsers");
            crowdProjects.stream().forEach(group -> uniqueUsers.addAll(crowdGroupMembersMap.get(group)));
            Map<String, User> userMap = processUsers(uniqueUsers);
            logger.debug("zzz-1: Out with dbUsers.size(): " + userMap.keySet().size());

            // Add users to Orgs
            logger.debug("zzz-2: assignUsersToOrganizations");
            Set<Organization> updatedOrganizations = assignUsersToOrganizations(crowdGroupMembersMap, userMap, organizationGroupsMap);
            logger.debug("zzz-2: Out with updatedOrganizations size {}", updatedOrganizations.size());

            // Identify & add projects
            logger.debug("zzz-3: addNewProjects");
            Map<String, Set<Project>> newOrganizationProjectMap = addNewProjects(crowdGroupMembersMap, userMap, organizationGroupsMap);
            logger.debug("zzz-3: Out with newOrganizationProjectMap.size: " + newOrganizationProjectMap.size());

            logger.debug("zzz-99: Done");
        }
    }

    private List<Project> assignUsersToProjects(Map<String, Project> updatedGroupProjectMap, Map<String, Set<String>> crowdGroupMembersMap, Map<String, User> userMap) throws Exception {

        List<Project> retList = new ArrayList<>();
        try (final TerminologyService service = new TerminologyService()) {

            Map<String, Set<String>> organizationProjectRolesMap = identifyCrowdEditionProjects(service, crowdGroupMembersMap.keySet());

            for (String group : updatedGroupProjectMap.keySet()) {
                Project project = updatedGroupProjectMap.get(group);
                Set<String> members = crowdGroupMembersMap.get(group);

                List<User> crowdMembers = new ArrayList<>();
                members.stream().forEach(m -> crowdMembers.add(userMap.get(m)));

                project.setMemberList(crowdMembers);
                // project.setRoles(new ArrayList<>(organizationProjectRolesMap.get(project.getEdition().getOrganizationId()).get(project.getName())));

                final Project updatedProject = ProjectService.updateProjects(SecurityService.getUserFromSession(), project.getId(), project);

                retList.add(updatedProject);
            }
        }

        return retList;
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

                if ("all".equals(crowdCodeSystem) || getEditionShortNameToCrowd(dbEdition.getShortName()).equals(crowdCodeSystem)) {
                    dbTeams.addAll(editionTeamMap.get(dbEdition));
                }

                for (Team dbTeam : dbTeams) {
                    // Match on team and edition to ensure proper handling of similarly named teams across editions.
                    if (crowdGroupName.equals(dbTeam.getName()) && crowdGroupName.equals(getEditionShortNameToCrowd(dbEdition.getShortName()))) {

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

    // Returns a map from an edition to a set of projects representing projects added to rt2 during sync
    // Important: If issues arise in missing or unexpected aspects of a project, first place to look is CROWD for inconsistencies across members in projects
    private Map<String, Set<Project>> addNewProjects(Map<String, Set<String>> crowdGroupMembersMap, Map<String, User> userMap, Map<String, Set<String>> organizationGroupsMap) throws Exception {
        final Map<String, Set<Project>> addedProjectMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {

            Map<String, Set<String>> editionProjectsMap = identifyCrowdEditionProjects(service, crowdGroupMembersMap.keySet());

            final Map<String, Edition> dbEditionMap = new HashMap<>();

            readDbAllEditions().stream().forEach(e -> dbEditionMap.put(getEditionShortNameToCrowd(e.getShortName()), e));

            for (String editionId : editionProjectsMap.keySet()) {

                Edition edition = service.get(editionId, Edition.class);
                Set<String> projects = editionProjectsMap.get(editionId);

                for (String projectName : projects) {

                    List<User> projectUsers = identifyCrowdProjectUsers(service, edition, projectName, crowdGroupMembersMap, userMap);

                    final Project newProject = dbHandler.addProject(projectName, "Default name for crowd-defined project: " + projectName, edition, projectUsers);

                    if (!addedProjectMap.containsKey(editionId)) {
                        addedProjectMap.put(editionId, new HashSet<>());
                    }

                    addedProjectMap.get(editionId).add(newProject);
                }
            }
        }
        return addedProjectMap;
    }

    private List<User> identifyCrowdProjectUsers(TerminologyService service, Edition edition, String projectName, Map<String, Set<String>> crowdGroupMembersMap, Map<String, User> userMap) {
        String rulePrefix = "rt2-" + getEditionShortNameToCrowd(edition.getShortName()) + "-" + projectName;

        List<User> projectUsers = new ArrayList<>();

        for (String crowdRule : crowdGroupMembersMap.keySet()) {
            if (crowdRule.startsWith(rulePrefix)) {
                crowdGroupMembersMap.get(crowdRule).stream().forEach(username -> projectUsers.add(userMap.get(username)));
            }
        }
        return projectUsers;
    }

    private Set<Project> assignProjectsToOrganization(Edition edition, String group, TerminologyService service, Map<String, Set<String>> organizationProjectMap, Map<String, Project> dbProjectMap,
        Map<String, User> userMap, Map<String, Set<String>> crowdGroupMembersMap) throws Exception {
        final Set<Project> updatedProjects = new HashSet<>();
        final Set<String> crowdOrganizationProjectNames = organizationProjectMap.get(edition.getOrganizationId());

        logger.debug("bbb-2");
        logger.debug("group {} ", group);
        logger.debug("edition {} ", edition.getName());
        logger.debug("crowdOrganizationProjectNames {} ", crowdOrganizationProjectNames);

        OrganizationService.getOrganizationProjects(service, edition.getOrganizationId()).getItems().stream().forEach(project -> dbProjectMap.put(project.getName(), project));

        logger.debug("bbb-3");
        logger.debug("dbProjectMap {} ", dbProjectMap);

        // Make sure RT2 project exists as expected in crowd

        // First, Identify what projects to remove from RT2 and do so
        Set<String> removeLocally = new HashSet<String>(dbProjectMap.keySet());
        removeLocally.removeAll(crowdOrganizationProjectNames);
        logger.debug("bbb-debug");
        logger.debug("removeLocally {}", removeLocally);
        logger.debug("dbProjectMap {}", dbProjectMap);
        List<Project> dbProjects = service.getAll(Project.class);
        logger.debug("dbProjects {}", dbProjects);

        for (String projectToInactivate : removeLocally) {
            logger.debug("bbb-4");

            final Project inactivatedProject = ProjectService.inactivateProject(SecurityService.getUserFromSession(), dbProjectMap.get(projectToInactivate).getId());
            updatedProjects.add(inactivatedProject);
        }

        logger.debug("removeLocally {} ", removeLocally);

        List<String> projectsToAdd = crowdOrganizationProjectNames.stream().filter(crowdProjectToAdd -> !dbProjectMap.containsKey(crowdProjectToAdd)).collect(Collectors.toList());
        for (String projectName : projectsToAdd) {
            logger.debug("bbb-5");

            final Project newProject = dbHandler.addProject(projectName, "Project " + projectName + " created in Crowd brought over to RT2 for edition ", edition);
            updatedProjects.add(newProject);
        }

        if (!projectsToAdd.isEmpty() || !removeLocally.isEmpty()) {
            logger.debug("bbb-6");
            logger.debug("projectsToAdd {} ", projectsToAdd);
            logger.debug("removeLocally {} ", removeLocally);
        }

        // Nothing to inactivate... just replace roles & members
        List<String> inBoth = crowdOrganizationProjectNames.stream().filter(name -> dbProjectMap.containsKey(name)).collect(Collectors.toList());
        logger.debug("bbb-7");
        logger.debug("updatedProjects {} ", updatedProjects);
        logger.debug("inBoth {} ", inBoth);

        for (String projectName : inBoth) {
            Set<String> dbMembers = new HashSet<>();

            Project project = dbProjectMap.get(projectName);

            Set<String> crowdMembers = crowdGroupMembersMap.get(group);
            crowdMembers.stream().forEach(u -> dbMembers.add(userMap.get(u).getName()));

            boolean projectUpdated = false;

            if (!crowdMembers.equals(dbMembers)) {
                project.getMemberList().clear();
                dbMembers.stream().forEach(m -> project.getMemberList().add(userMap.get(m)));
                logger.debug("bbb-8 updating members");

                projectUpdated = true;
            }
            /* TODO: Mimic Members when implementing Roles */
            // Set<String> dbRoles = new HashSet<>();
            // Set<String> rolesToPersist = new HashSet<>();

            // Set<String> crowdRoles = organizationProjectRolesMap.get(organization.getId()).get(projectName);
            // dbRoles.addAll(project.getRoles());

            // project.setRoles(new ArrayList<>(organizationProjectRolesMap.get(project.getEdition().getOrganizationId()).get(project.getName())));

            if (projectUpdated) {
                logger.debug("bbb-9 updating project");
                final Project updatedProject = ProjectService.updateProjects(SecurityService.getUserFromSession(), project.getId(), project);

                updatedProjects.add(updatedProject);
            }

        }

        return updatedProjects;
    }

    /* Null represents "ALL" use case */
    private Edition getCrowdEdition(String group, Map<String, Edition> dbEditionMap) throws Exception {
        logger.error("group {} ", group);
        final String[] groupCoordinates = group.split("-");

        final String crowdCodeSystem = groupCoordinates[EDITION_SHORTNAME];
        logger.error("crowdCodeSystem {} ", crowdCodeSystem);

        List<String> matchingEditions = dbEditionMap.keySet().stream().filter(e -> getEditionShortNameToCrowd(dbEditionMap.get(e).getShortName()).equals(crowdCodeSystem)).collect(Collectors.toList());

        if ("all".equals(crowdCodeSystem)) {
            return null;
        }

        logger.error("dbEditionMap {} ", dbEditionMap);
        logger.error("matchingEditions {} ", matchingEditions);

        if (matchingEditions.size() == 1) {

            return dbEditionMap.get(matchingEditions.iterator().next());
        }

        throw new Exception("Unexpected number of editions for " + crowdCodeSystem + " in getCrowdEdition() for editions.size(): " + matchingEditions.size());
    }

    // Returns a map from an editionId to a set of project names representing projects to add
    private Map<String, Set<String>> identifyCrowdEditionProjects(TerminologyService service, Set<String> crowdGroups) throws Exception {
        logger.debug("aaa crowdProjects {}", crowdGroups);

        // Edition, to Team, to Roles
        final Map<String, Set<String>> crowdOrganizationProjectsMap = new HashMap<>();

        final List<Project> dbProjects = service.getAll(Project.class);
        final Set<Organization> dbOrganizations = new HashSet<>();
        final List<Edition> dbEditions = readDbAllEditions();
        dbEditions.stream().forEach(e -> dbOrganizations.add(e.getOrganization()));

        // Sort crowdProjects by edition/groupName/Set<Role>
        for (String group : crowdGroups) {
            // if (isTesting() && !crowdTeamRoleString.startsWith("rt2-" + editionShortNameToCrowdCodeSystem(testingEditionShortName) + "-")) {
            // continue;
            // // TODO: Change to throw new Exception("No edition in DB for editionShortName: " + crowdCodeSystem);
            // }
            logger.debug("aaa-1");
            logger.debug("group {} ", group);

            final String[] groupCoordinates = group.split("-");

            final String crowdCodeSystem = groupCoordinates[EDITION_SHORTNAME];
            final String crowdProject = groupCoordinates[PROJECT_NAME];

            if ("all".equals(crowdCodeSystem)) {
                logger.debug("aaa-2");

                // Add project to all orgs
                for (Organization organization : dbOrganizations) {
                    if (!crowdOrganizationProjectsMap.containsKey(organization.getId())) {
                        crowdOrganizationProjectsMap.put(organization.getId(), new HashSet<>());
                    }

                    crowdOrganizationProjectsMap.get(organization.getId()).add(crowdProject);
                }

            } else {
                // Specific edition specified
                logger.debug("aaa-3");

                // Identify crowd teams & edition's teams in DB (via Org)
                List<Edition> editions = dbEditions.stream().filter(e -> getEditionShortNameToCrowd(e.getShortName()).equals(crowdCodeSystem)).collect(Collectors.toList());

                if (editions.size() == 1) {
                    crowdOrganizationProjectsMap.get(editions.iterator().next().getOrganizationId()).add(crowdProject);
                } else {
                    logger.error("Unexpected number of editions (expected 1): " + editions.size());
                }
            }
        }

        final Map<String, Set<String>> organizationProjectsToUpdateMap = new HashMap<>();

        for (String organizationId : crowdOrganizationProjectsMap.keySet()) {
            List<Project> dbOrganizationProjects = OrganizationService.getOrganizationProjects(service, organizationId).getItems();

            for (String crowdProjectName : crowdOrganizationProjectsMap.get(organizationId)) {
                if (dbOrganizationProjects.stream().noneMatch(p -> p.getName().equals(crowdProjectName))) {

                    if (!organizationProjectsToUpdateMap.containsKey(organizationId)) {
                        organizationProjectsToUpdateMap.put(organizationId, new HashSet<>());
                    }

                    organizationProjectsToUpdateMap.get(organizationId).add(crowdProjectName);
                }
            }
        }

        logger.debug("aaa-4 - out");

        return organizationProjectsToUpdateMap;
    }

    private void identifyProjectRoles(Organization organization, Map<String, Map<String, Set<String>>> organizationProjectRolesMap, String crowdGroupName, String crowdTeamRoleString,
        String crowdGroupRole) {

        if (!organizationProjectRolesMap.containsKey(organization.getId())) {
            organizationProjectRolesMap.put(organization.getId(), new HashMap<>());
        }

        if (!organizationProjectRolesMap.get(organization.getId()).containsKey(crowdGroupName)) {
            organizationProjectRolesMap.get(organization.getId()).put(crowdGroupName, new HashSet<>());
        }

        organizationProjectRolesMap.get(organization.getId()).get(crowdGroupName).add(crowdGroupRole);
    }

    // Important: If issues arise in missing or unexpected members of organizations, first place to look is CROWD for inconsistencies across members in organizations
    private Set<Organization> assignUsersToOrganizations(Map<String, Set<String>> crowdGroupMembersMap, Map<String, User> dbUserMap, Map<String, Set<String>> organizationGroupsMap) throws Exception {

        Set<Organization> updatedOrganizations = new HashSet<>();

        try (final TerminologyService service = new TerminologyService()) {
            SyncDatabaseHandler.initializeService(service);

            final Map<String, Set<String>> dbOrganizationUsernamesMap = identifyCrowdOrganizationUsers(service, crowdGroupMembersMap, organizationGroupsMap);
            logger.debug("qqq2 - dbOrganizationUsernamesMap {} ", dbOrganizationUsernamesMap);

            for (String organizationId : dbOrganizationUsernamesMap.keySet()) {

                final Map<String, User> dbUserIdMap = new HashMap<>();
                final Organization organization = service.get(organizationId, Organization.class);

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
            List<Organization> dbOrganizations = service.getAll(Organization.class);
            dbOrganizations.stream().forEach(o -> retMap.put(o.getId(), new HashSet<>()));

            final Map<String, Edition> dbEditionMap = new HashMap<>();
            readDbAllEditions().stream().forEach(e -> dbEditionMap.put(getEditionShortNameToCrowd(e.getShortName()), e));

            logger.debug("ppp3 - dbEditionMap {} ", dbEditionMap);

            for (String group : crowdProjects) {
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

    private String getEditionShortNameToCrowd(String editionShortName) {
        return editionShortName.toLowerCase().replace("-", "");
    }
}