package org.ihtsdo.refsetservice.sync;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.DiscussionPost;
import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.model.DiscussionType;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncDataInitializer {

    private final Logger logger = LoggerFactory.getLogger(SyncDataInitializer.class);

    private SyncAgentUtilities utilities;

    private static User syncUser = null;

    private static User feedbackInitiatiorUser = null;

    private static User userResponderUser = null;

    private static User developerTestingAdmin = null;

    private static User superUser = null;

    private static final Set<String> allRoles = new HashSet<>();

    private static final String WCI_TESTING_REFSET_CONCEPT_ID = "92535302004";

    private static final String WCI_TESTING_REFSET_NAME = "Default Single WCI Testing Refset";

    private static final String SUPER_USER_NAME = "refset-dev";

    private static final Set<User> adminUsers = new HashSet<>();

    static private Edition developerTestingEdition = null;

    static private Project testingProject = null;

    private static final String FEEDBACK_REFSET_NAME_BASE = "WCI Testing Feedback Refset ";

    private static final String FEEDBACK_REFSET_ID_BASE = "9999999";

    private static final String FEEDBACK_INITIAL_REFSET_ID = "999999901";

    private static final String INTENSIONAL_REFSET_NAME_BASE = "WCI Testing Intensional Refset ";

    private static final String INTENSIONAL_REFSET_ID_BASE = "8888888";

    private static final String INTENSIONAL_INITIAL_REFSET_ID = "888888801";

    private static final String WCI_TESTING_PROJECT_NAME = "WCI Testing Project";

    private static final String WCI_TESTING_PROJECT_DESCRIPTION = "The single project for all WCI testing refsets";

    public SyncDataInitializer() {

        try {

            this.utilities = new SyncAgentUtilities();

            developerTestingAdmin = utilities.getUser("rt2-dev-admin", "rt2-dev-admin", "rt2-dev-admin@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_ADMIN)));
            superUser = utilities.getUser(SUPER_USER_NAME, SUPER_USER_NAME, "refset-dev@westcoastinformatics.com", allRoles);

            adminUsers.add(developerTestingAdmin);
            adminUsers.add(superUser);

            // For Feedback Refset
            feedbackInitiatiorUser = utilities.getUser("feedbackInitiator", "feedbackInitiator", "feedbackInitiator@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_AUTHOR)));
            userResponderUser = utilities.getUser("feedbackResponder", "feedbackResponder", "feedbackResponder@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_AUTHOR)));
        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    public void initialize(Edition edition, List<Edition> allDatabaseEditions, List<Refset> allDatabaseRefsets, Map<String, Project> defaultEditionProjects) throws Exception {

        // Only run this once on DEV and UAT (but never prod). If developerTestingEdition is set, we know that this has already been run
        if (edition != null) {

            logger.debug("Creating testing support and content");

            if (!allDatabaseRefsets.stream().anyMatch(r -> r.getRefsetId().equals(FEEDBACK_INITIAL_REFSET_ID))) {

                // Create developer project and refsets (for DEV only)
                createTestingContent();

                // Create a single Admin team per Org
                createAdminOrganizationTeams(allDatabaseEditions);

            }

        } else {

            logger.debug("Failed to create testing support and content as develeperTestingEdition is null");
        }

    }

    private void createAdminOrganizationTeams(List<Edition> allDatabaseEditions) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            utilities.initializeService(service);

            for (Edition edition : allDatabaseEditions) {

                Set<String> memberIds = new HashSet<>();

                memberIds.addAll(adminUsers.stream().map(User::getId).collect(Collectors.toList()));

                utilities.addTeam(TeamService.generateOrgTeamName(edition.getOrganization()), TeamService.getOrgTeamDescription(edition.getOrganization()), edition.getOrganization(),
                    new HashSet<String>(Arrays.asList(User.ROLE_ADMIN)), memberIds);

                // Finally, add the users to the organizaiton
                edition.getOrganization().getMembers().addAll(adminUsers);
                Edition updatedEdition = service.update(edition);

                printAllValues(updatedEdition);
            }

        }

    }

    public void printResults() throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            // Print out orgs & projects
            final List<Organization> organizations = service.getAll(Organization.class);
            final List<Project> projects = service.getAll(Project.class);

            for (Organization organization : organizations) {

                logger.info("Have Out with org: " + organization.getId() + " (" + organization.getName() + ") with members: ");
                organization.getMembers().stream().forEach(member -> logger.info("   Member: " + member.getName()));
            }

            for (Project project : projects) {

                logger.info("Out with project: " + project.getId() + " (" + project.getName() + ") with teams: ");
                project.getTeams().stream().forEach(team -> logger.info("   Team: " + team));
            }

        }

    }

    private void createTestingContent() throws Exception {

        final Edition developerEdition = getDeveloperTestingEdition();

        try (TerminologyService service = new TerminologyService()) {

            utilities.initializeService(service);

            testingProject = utilities.addProject(WCI_TESTING_PROJECT_NAME, WCI_TESTING_PROJECT_DESCRIPTION, developerTestingEdition);

            utilities.addWCIRefset(getSyncUser(), WCI_TESTING_REFSET_NAME, WCI_TESTING_REFSET_CONCEPT_ID, developerEdition.getTopLevelModule(), utilities.getSdf().parse("2021-07-31 07:00:00.000000"),
                "", testingProject);

            // Create wci testing refsets(for DEV only)
            createTestingRefsets();
        }

    }

    /*
     * Called when creating the first instance of testing refsets
     */

    public void createTestingRefsets() throws Exception {

        final Edition developerEdition = getDeveloperTestingEdition();

        logger.info(" Create Feedback & Intensional refsets for testing (for DEV only)");

        // create new refset with name = Feedback/Intensional Testing Version 1 with July 31 2022 version off International Edition
        Refset intensionalRefset = utilities.addWCIRefset(getSyncUser(), INTENSIONAL_REFSET_NAME_BASE + "1", INTENSIONAL_INITIAL_REFSET_ID, developerEdition.getTopLevelModule(),
            utilities.getSdf().parse("2021-07-31 07:00:00.000000"), "", testingProject);
        addIntensionalContent(intensionalRefset);

        Refset feedbackRefset = utilities.addWCIRefset(getSyncUser(), FEEDBACK_REFSET_NAME_BASE + "1", FEEDBACK_INITIAL_REFSET_ID, developerEdition.getTopLevelModule(),
            utilities.getSdf().parse("2021-07-31 07:00:00.000000"), "", testingProject);

        addFeedbackContent(feedbackRefset);

    }

    /*
     * Called when adding another instance of testing-feedback refset
     */
    public Refset createTestingFeedbackRefset() throws Exception {

        Refset newTestingRefset = createTestingRefset(FEEDBACK_REFSET_NAME_BASE, FEEDBACK_REFSET_ID_BASE);

        addFeedbackContent(newTestingRefset);

        return newTestingRefset;
    }

    /*
     * Called when adding another instance of testing-intensional refset
     */
    public Refset createTestingIntensionalRefset() throws Exception {

        Refset newTestingRefset = createTestingRefset(INTENSIONAL_REFSET_NAME_BASE, INTENSIONAL_REFSET_ID_BASE);

        addIntensionalContent(newTestingRefset);

        return newTestingRefset;
    }

    private Refset createTestingRefset(String testingRefsetName, String testingRefsetId) throws Exception {

        final Project developerTestingProject = getDeveloperTestingProject();
        final Edition developerTestingEdition = getDeveloperTestingEdition();

        try (TerminologyService service = new TerminologyService()) {

            utilities.initializeService(service);
            final List<Refset> projectRefsets = service.find("projectId:" + developerTestingProject.getId() + " AND active:true", null, Refset.class, null).getItems();

            int latestVersion = 0;

            for (Refset projectRefset : projectRefsets) {

                if (projectRefset.getRefsetId().startsWith(testingRefsetId) && projectRefset.getName().startsWith(testingRefsetName)) {

                    final int refsetVersion = Integer.parseInt(projectRefset.getName().substring(testingRefsetName.length()).trim());

                    if (refsetVersion > latestVersion) {

                        latestVersion = refsetVersion;
                    }
                    // Iterate through the refsets, look at the refset name, and find the integer list after the default name.
                    // if keysize = 0, this is first one. So create with RefsetId: based on the testingRefsetId and iteration.
                    // else, if the refset integer is greater than the greatest one seen, make this the new refsetName & refsetId integer

                }

            }

            Refset newTestingRefset;

            if (latestVersion == 0) {

                newTestingRefset = utilities.addWCIRefset(getSyncUser(), testingRefsetName + "1", testingRefsetId + "01", getDeveloperTestingEdition().getTopLevelModule(), new Date(), "",
                    getDeveloperTestingProject());
            } else {

                latestVersion++;
                String tensValue = Integer.toString(latestVersion / 10);
                String onesValue = Integer.toString(latestVersion % 10);

                newTestingRefset = utilities.addWCIRefset(getSyncUser(), testingRefsetName + latestVersion, testingRefsetId + tensValue + onesValue, getDeveloperTestingEdition().getTopLevelModule(),
                    new Date(), "", getDeveloperTestingProject());
            }

            logger.info("Creating new testing refset: newTestingRefset: " + newTestingRefset.getRefsetId() + " - " + newTestingRefset.getName());

            return newTestingRefset;
        }

    }

    private void addIntensionalContent(Refset refset) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            utilities.initializeService(service);

            // Create ecl clause
            final String testClause = "<<716186003 |No known allergy (situation)|";
            final DefinitionClause clause = new DefinitionClause();
            clause.setNegated(false);
            clause.setValue(testClause);
            final DefinitionClause persistedClause = service.add(clause);

            // Set Intensional Refset Infromation
            refset.setType(Refset.INTENSIONAL);
            refset.getDefinitionClauses().add(persistedClause);

            final Refset updatedRefset = service.update(refset);
        }

    }

    private void addFeedbackContent(Refset refset) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            utilities.initializeService(service);

            // add feedback
            DiscussionThread thread = new DiscussionThread();
            thread.setSubject("Testing discussion thread on refset");
            thread.setType(DiscussionType.REFSET.toString());
            thread.setRefsetInternalId(refset.getId());
            thread.setStatus("OPEN");
            thread.setVisibility("VISIBLE");
            thread.setPrivateThread(false);
            thread = service.add(thread);

            service.setModifiedBy(SecurityService.getUserFromSession().getUserName());
            service.setModifiedFlag(true);
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            DiscussionPost post = new DiscussionPost();
            post.setUser(feedbackInitiatiorUser);
            post.setMessage("Topic Header");
            post.setVisibility("VISIBLE");
            post.setPrivatePost(false);
            post = service.add(post);
            thread.getPosts().add(post);

            post = new DiscussionPost();
            post.setUser(userResponderUser);
            post.setMessage("Comment #1");
            post.setVisibility("VISIBLE");
            post.setPrivatePost(false);
            post = service.add(post);
            thread.getPosts().add(post);

            post = new DiscussionPost();
            post.setUser(feedbackInitiatiorUser);
            post.setMessage("Comment #2");
            post.setVisibility("VISIBLE");
            post.setPrivatePost(false);
            post = service.add(post);
            thread.getPosts().add(post);

            // Finalize transaction
            service.update(thread);
            service.commit();
            service.setTransactionPerOperation(true);
        }

        // Create users for testing initial feedback
        try (TerminologyService service = new TerminologyService()) {

            utilities.initializeService(service);

            // Create users and teams, then add to org/project
            Set<String> userRole = new HashSet<>();
            userRole.add(User.ROLE_AUTHOR);
            Set<String> memberIds = new HashSet<>();
            memberIds.add(feedbackInitiatiorUser.getId());
            memberIds.add(userResponderUser.getId());
            adminUsers.stream().forEach(user -> memberIds.add(user.getId()));

            final Team singleFeedbackTeam =
                utilities.addTeam("WCI Feedback Team", "WCI Feedback Testing/Demoing Team with all roles for all WCI members", getDeveloperTestingEdition().getOrganization(), allRoles, memberIds);

            testingProject.getTeams().add(singleFeedbackTeam.getId());
            testingProject = service.update(testingProject);

            getDeveloperTestingEdition().getOrganization().getMembers().add(feedbackInitiatiorUser);
            getDeveloperTestingEdition().getOrganization().getMembers().add(userResponderUser);
            service.update(getDeveloperTestingEdition().getOrganization());
        }

    }

    private void printAllValues(Edition edition) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            final List<Project> orgProjects = service.find("edition.id:" + edition.getId(), null, Project.class, null).getItems();
            final List<Team> teams = service.getAll(Team.class);

            for (Project project : orgProjects) {

                for (String teamId : project.getTeams()) {

                    Team team = teams.stream().filter(t -> t.getId().equals(teamId)).findFirst().orElse(null);

                    if (team == null) {

                        throw new Exception("  Unable to locate team in project " + project.getName() + " for team: " + teamId);
                    }

                }

            }

        }

    }

    private Project getDeveloperTestingProject() throws Exception {

        if (testingProject == null) {

            try (TerminologyService service = new TerminologyService()) {

                List<Project> projects = service.getAll(Project.class);

                for (Project p : projects) {

                    if (p.getName().equals(WCI_TESTING_PROJECT_NAME)) {

                        testingProject = p;
                    }

                }

            }

            if (testingProject == null) {

                throw new Exception("Testing Project doesn't exist. Shouldn't be running this on a non-Production instanace of RT2");
            }

        }

        return testingProject;
    }

    private Edition getDeveloperTestingEdition() throws Exception {

        if (developerTestingEdition == null) {

            try (TerminologyService service = new TerminologyService()) {

                List<Edition> editions = service.getAll(Edition.class);

                for (Edition e : editions) {

                    if (e.getName().toLowerCase().contains("wci")) {

                        developerTestingEdition = e;
                    }

                }

            }

            if (developerTestingEdition == null) {

                throw new Exception("Testing Organization doesn't exist. Shouldn't be running this on a non-Production instance of RT2");
            }

        }

        return developerTestingEdition;
    }

    static User getSyncUser() {

        if (syncUser == null) {

            syncUser = new User();
            syncUser.setName("Migrator");
            syncUser.setUserName("Migrator");
            syncUser.setActive(true);
            syncUser.setEmail("test@wci.com");

            Set<String> roles = new HashSet<>();
            roles.add("all-all-all");
            syncUser.setRoles(roles);
        }

        return syncUser;
    }

    public static Set<User> getAdminUsers() {

        return adminUsers;
    }

    public static List<String> getAdminUserIds() {

        return adminUsers.stream().map(User::getId).collect(Collectors.toList());
    }

}