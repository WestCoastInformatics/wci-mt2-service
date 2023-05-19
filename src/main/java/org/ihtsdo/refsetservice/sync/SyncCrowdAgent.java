package org.ihtsdo.refsetservice.sync;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncCrowdAgent extends SyncAgent {

    private static Logger logger = LoggerFactory.getLogger(SyncCrowdAgent.class);

    public void sync() throws Exception {
        logger.info("Starting sync of CrowdAgent");
        final Set<String> uniqueUsers = new HashSet<>();

        // Get data from Crowd

        try (final TerminologyService service = new TerminologyService()) {
            final Map<String, Set<String>> crowdGroupMembersMap = CrowdAPIClient.getAllGroupsMembers();

            // Identify changes to existing users and/or add new users information (from CROWD)
            crowdGroupMembersMap.keySet().stream().forEach(group -> uniqueUsers.addAll(crowdGroupMembersMap.get(group)));

            processUsers(uniqueUsers);

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
}