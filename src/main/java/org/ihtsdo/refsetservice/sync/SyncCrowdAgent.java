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
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncDatabaseHandler;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncCrowdAgent extends SyncAgent {

    private static Logger logger = LoggerFactory.getLogger(SyncCrowdAgent.class);

    private static final int EDITION_SHORTNAME = 1;

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

            logger.info("Finished syncing SyncCrowdAgent");
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